package matching

import (
	"context"
	"encoding/json"
	"log"
	"time"

	"github.com/google/uuid"
	"github.com/backroom/ws-signaling/internal/fcm"
	"github.com/backroom/ws-signaling/internal/hub"
	"github.com/backroom/ws-signaling/internal/models"
	redisClient "github.com/backroom/ws-signaling/internal/redis"
)

const (
	// How often to check for pending shares
	matchInterval = 500 * time.Millisecond

	// Preview countdown for listeners
	previewCountdownSeconds = 30

	// How long to wait for listener to accept
	acceptTimeout = 35 * time.Second

	// Minimum delay before re-trying a share that found no listener
	// This prevents the infinite spin loop when no listeners are available
	noListenerRetryDelay = 10 * time.Second

	// Maximum number of retry attempts before giving up
	maxRetryAttempts = 30 // 30 retries × 10 seconds = 5 minutes max

	// How often to clean up stale listeners
	cleanupInterval = 60 * time.Second
)

// Matcher handles matching sharers with listeners
type Matcher struct {
	redis *redisClient.Client
	hub   *hub.Hub
	fcm   *fcm.FCMClient
}

// NewMatcher creates a new matcher
func NewMatcher(redis *redisClient.Client, hub *hub.Hub, fcmClient *fcm.FCMClient) *Matcher {
	return &Matcher{
		redis: redis,
		hub:   hub,
		fcm:   fcmClient,
	}
}

// Run starts the matching loop
func (m *Matcher) Run(ctx context.Context) {
	log.Println("🔄 Matching worker started")
	ticker := time.NewTicker(matchInterval)
	defer ticker.Stop()

	// Heartbeat ticker - log every 30 seconds to confirm matcher is alive
	heartbeat := time.NewTicker(30 * time.Second)
	defer heartbeat.Stop()

	// Cleanup ticker - remove stale listeners periodically
	cleanup := time.NewTicker(cleanupInterval)
	defer cleanup.Stop()

	// Immediate first check
	log.Println("🔄 Matcher performing initial queue check...")
	m.cleanupStaleListeners(ctx) // Cleanup on startup
	m.processQueue(ctx)
	log.Println("🔄 Matcher entering main loop")

	tickCount := 0
	for {
		select {
		case <-ctx.Done():
			log.Println("🛑 Matching worker stopped")
			return
		case <-heartbeat.C:
			log.Printf("💓 Matcher heartbeat - processed %d ticks", tickCount)
		case <-cleanup.C:
			m.cleanupStaleListeners(ctx)
		case <-ticker.C:
			tickCount++
			m.processQueue(ctx)
		}
	}
}

// cleanupStaleListeners removes disconnected clients from the available listeners set
func (m *Matcher) cleanupStaleListeners(ctx context.Context) {
	listeners, err := m.redis.GetAvailableListeners(ctx)
	if err != nil {
		log.Printf("⚠️ Error getting available listeners for cleanup: %v", err)
		return
	}

	removedCount := 0
	for _, listenerID := range listeners {
		if !m.hub.IsClientConnected(listenerID) {
			m.redis.RemoveAvailableListener(ctx, listenerID)
			removedCount++
			log.Printf("🧹 Removed stale listener %s from available set", listenerID)
		}
	}

	if removedCount > 0 {
		log.Printf("🧹 Cleanup complete: removed %d stale listeners", removedCount)
	}
}

// processQueue checks for pending shares and tries to match them
func (m *Matcher) processQueue(ctx context.Context) {
	// Get oldest share from queue (only if ready - respects retry delays)
	shareID, err := m.redis.PopReadyFromShareQueue(ctx)
	if err != nil {
		log.Printf("⚠️ Error popping from share queue: %v", err)
		return
	}
	if shareID == "" {
		return // No shares ready - this is normal, no log needed
	}

	log.Printf("🔍 Processing share from queue: %s", shareID)

	// Get share details
	shareJSON, err := m.redis.GetShare(ctx, shareID)
	if err != nil {
		log.Printf("❌ Failed to get share %s: %v", shareID, err)
		return
	}

	log.Printf("📋 Share data: %s", shareJSON)

	var share models.ShareRequest
	if err := json.Unmarshal([]byte(shareJSON), &share); err != nil {
		log.Printf("❌ Failed to parse share %s: %v", shareID, err)
		return
	}

	// Check if share expired
	if time.Now().After(share.ExpiresAt) {
		log.Printf("⏰ Share %s expired", shareID)
		m.redis.DeleteShare(ctx, shareID)
		// Re-add sharer to available listeners (they're free to receive calls now)
		if m.hub.IsClientConnected(share.SharerID) {
			m.redis.AddAvailableListener(ctx, share.SharerID)
			log.Printf("📡 Re-added sharer %s to available listeners after expiration", share.SharerID)
		}
		// Notify sharer that share expired
		m.hub.SendToClient(share.SharerID, models.Message{
			Type: models.MsgTypeShareExpired,
			Payload: map[string]interface{}{
				"shareId": shareID,
				"reason":  "expired",
			},
		})
		return
	}

	// Check if max retries exceeded
	if share.RetryCount >= maxRetryAttempts {
		log.Printf("🚫 Share %s exceeded max retry attempts (%d)", shareID, maxRetryAttempts)
		m.redis.DeleteShare(ctx, shareID)
		// Re-add sharer to available listeners (they're free to receive calls now)
		if m.hub.IsClientConnected(share.SharerID) {
			m.redis.AddAvailableListener(ctx, share.SharerID)
			log.Printf("📡 Re-added sharer %s to available listeners after max retries", share.SharerID)
		}
		// Notify sharer that no listeners are available
		m.hub.SendToClient(share.SharerID, models.Message{
			Type: models.MsgTypeShareExpired,
			Payload: map[string]interface{}{
				"shareId": shareID,
				"reason":  "no_listeners",
			},
		})
		return
	}

	// Find available listener
	listenerID := m.findListener(ctx, &share)
	if listenerID == "" {
		// Increment retry count
		share.RetryCount++
		updatedShareData, _ := json.Marshal(share)
		m.redis.SetShare(ctx, shareID, updatedShareData)

		// No listener available, re-queue the share with a delay
		// Use current time + delay as the score so it won't be processed until the delay passes
		retryTime := time.Now().Add(noListenerRetryDelay)
		log.Printf("🔄 Re-queuing share %s (attempt %d/%d, will retry after %v)",
			shareID, share.RetryCount, maxRetryAttempts, noListenerRetryDelay)
		m.redis.AddToShareQueue(ctx, shareID, float64(retryTime.Unix()))
		return
	}

	log.Printf("🎯 Matching share %s with listener %s", shareID, listenerID)
	// Send preview to listener
	m.sendPreviewToListener(listenerID, &share)
}

// findListener finds an available listener for a share
func (m *Matcher) findListener(ctx context.Context, share *models.ShareRequest) string {
	listeners, err := m.redis.GetAvailableListeners(ctx)
	if err != nil {
		log.Printf("❌ Failed to get available listeners: %v", err)
		return ""
	}

	if len(listeners) == 0 {
		log.Printf("😢 No listeners in available set for share %s", share.ID)
		return ""
	}

	log.Printf("👥 Available listeners: %v (looking for listener for sharer: %s)", listeners, share.SharerID)

	disconnectedCount := 0
	for _, listenerID := range listeners {
		// Skip if listener is the sharer
		if listenerID == share.SharerID {
			log.Printf("⏭️ Skipping %s (is the sharer)", listenerID)
			continue
		}

		// Check if listener is connected FIRST (most common reason to skip)
		if !m.hub.IsClientConnected(listenerID) {
			log.Printf("⏭️ Skipping %s (not connected) - removing from available set", listenerID)
			// Remove disconnected listener from available set
			m.redis.RemoveAvailableListener(ctx, listenerID)
			disconnectedCount++
			continue
		}

		// Skip if listener has blocked the sharer
		isBlocked, err := m.redis.IsUserBlocked(ctx, listenerID, share.SharerID)
		if err != nil {
			log.Printf("⚠️ Error checking blocked status: %v", err)
		}
		if isBlocked {
			log.Printf("⏭️ Skipping %s (has blocked the sharer)", listenerID)
			continue
		}

		// Skip if sharer has blocked the listener
		isBlocked, err = m.redis.IsUserBlocked(ctx, share.SharerID, listenerID)
		if err != nil {
			log.Printf("⚠️ Error checking blocked status: %v", err)
		}
		if isBlocked {
			log.Printf("⏭️ Skipping %s (blocked by the sharer)", listenerID)
			continue
		}

		// Skip if listener has already declined this share
		hasDeclined, err := m.redis.HasListenerDeclined(ctx, share.ID, listenerID)
		if err != nil {
			log.Printf("⚠️ Error checking declined status for %s: %v", listenerID, err)
		}
		if hasDeclined {
			log.Printf("⏭️ Skipping %s (already declined this share)", listenerID)
			continue
		}

		log.Printf("✅ Found suitable listener: %s", listenerID)
		return listenerID
	}

	if disconnectedCount > 0 {
		log.Printf("🧹 Removed %d disconnected listeners during search", disconnectedCount)
	}

	log.Printf("😢 No suitable listener found for share %s (checked %d listeners)", share.ID, len(listeners))
	return ""
}

// sendPreviewToListener sends preview to a listener
// Sends via BOTH WebSocket AND FCM for reliability (background app scenarios)
func (m *Matcher) sendPreviewToListener(listenerID string, share *models.ShareRequest) {
	preview := models.IncomingPreviewPayload{
		ShareID:          share.ID,
		Topic:            share.Topic,
		Tone:             share.Tone,
		PreviewText:      share.PreviewText,
		DurationMinutes:  share.DurationMinutes,
		CountdownSeconds: previewCountdownSeconds,
	}

	msg := models.Message{
		Type:    models.MsgTypeIncomingPreview,
		Payload: preview,
	}

	// Try WebSocket first
	wsSent := m.hub.SendToClient(listenerID, msg)
	if wsSent {
		log.Printf("📤 Preview sent to listener %s for share %s (WebSocket)", listenerID, share.ID)
	} else {
		log.Printf("⚠️ WebSocket failed for listener %s", listenerID)
	}

	// ALWAYS try FCM as backup (even if WebSocket succeeds)
	// This ensures the app wakes up if it's in background
	if m.fcm != nil {
		ctx := context.Background()
		fcmToken, err := m.redis.GetFcmToken(ctx, listenerID)
		if err != nil || fcmToken == "" {
			if !wsSent {
				log.Printf("❌ No FCM token for listener %s and WebSocket failed", listenerID)
			}
			return
		}

		err = m.fcm.SendIncomingPreview(ctx, fcmToken, share.ID, share.Topic, share.Tone, share.PreviewText, share.DurationMinutes, previewCountdownSeconds)
		if err != nil {
			log.Printf("❌ Failed to send FCM push to listener %s: %v", listenerID, err)
		} else {
			log.Printf("📤 Preview also sent to listener %s for share %s (FCM push backup)", listenerID, share.ID)
		}
	} else if !wsSent {
		log.Printf("❌ FCM client not available and WebSocket failed for listener %s", listenerID)
	}
}

// CreateMatch creates a match between sharer and listener
func (m *Matcher) CreateMatch(ctx context.Context, share *models.ShareRequest, listenerID string) (*models.ActiveCall, error) {
	callID := uuid.New().String()

	call := &models.ActiveCall{
		ID:         callID,
		ShareID:    share.ID,
		SharerID:   share.SharerID,
		ListenerID: listenerID,
		StartedAt:  time.Now(),
		Status:     "pending",
	}

	// Store call in Redis
	callJSON, err := json.Marshal(call)
	if err != nil {
		return nil, err
	}

	if err := m.redis.SetCall(ctx, callID, callJSON); err != nil {
		return nil, err
	}

	// Map users to call
	m.redis.SetUserCall(ctx, share.SharerID, callID)
	m.redis.SetUserCall(ctx, listenerID, callID)

	// Remove listener from available set
	m.redis.RemoveAvailableListener(ctx, listenerID)

	// Delete share from queue
	m.redis.DeleteShare(ctx, share.ID)

	// Notify both parties
	m.notifyMatch(call)

	log.Printf("✅ Match created: call=%s, sharer=%s, listener=%s", callID, share.SharerID, listenerID)

	return call, nil
}

// notifyMatch sends match notification to both parties
func (m *Matcher) notifyMatch(call *models.ActiveCall) {
	// TODO: Generate TURN credentials
	turnServers := []models.TurnServer{
		{
			URLs: []string{"stun:stun.l.google.com:19302"},
		},
	}

	// Notify sharer
	sharerMsg := models.Message{
		Type: models.MsgTypeMatchMade,
		Payload: models.MatchMadePayload{
			CallID:      call.ID,
			SharerID:    call.SharerID,
			ListenerID:  call.ListenerID,
			Role:        "sharer",
			TurnServers: turnServers,
		},
	}
	m.hub.SendToClient(call.SharerID, sharerMsg)

	// Notify listener
	listenerMsg := models.Message{
		Type: models.MsgTypeMatchMade,
		Payload: models.MatchMadePayload{
			CallID:      call.ID,
			SharerID:    call.SharerID,
			ListenerID:  call.ListenerID,
			Role:        "listener",
			TurnServers: turnServers,
		},
	}
	m.hub.SendToClient(call.ListenerID, listenerMsg)
}

