package hub

import (
	"context"
	"encoding/json"
	"log"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/backroom/ws-signaling/internal/fcm"
	"github.com/backroom/ws-signaling/internal/models"
	redisClient "github.com/backroom/ws-signaling/internal/redis"
)

// generateUUID generates a new UUID string
func generateUUID() string {
	return uuid.New().String()
}

const (
	// Time allowed to write a message to the peer
	writeWait = 10 * time.Second

	// Time allowed to read the next pong message from the peer
	pongWait = 60 * time.Second

	// Send pings to peer with this period (must be less than pongWait)
	pingPeriod = (pongWait * 9) / 10

	// Maximum message size allowed from peer
	maxMessageSize = 8192
)

// Client represents a connected WebSocket client
type Client struct {
	hub      *Hub
	conn     *websocket.Conn
	send     chan []byte
	userID   string
	isSharer bool // true if currently in sharer role
}

// Hub manages all WebSocket clients and message routing
type Hub struct {
	// Registered clients by user ID
	clients map[string]*Client

	// Register requests from clients
	register chan *Client

	// Unregister requests from clients
	unregister chan *Client

	// Inbound messages from clients
	broadcast chan *ClientMessage

	// Redis client for persistence
	redis *redisClient.Client

	// FCM client for push notifications
	fcm *fcm.FCMClient

	// TURN server configuration
	turnURL    string
	turnSecret string

	// Mutex for thread-safe access
	mu sync.RWMutex
}

// ClientMessage wraps a message with sender info
type ClientMessage struct {
	Client  *Client
	Message []byte
}

// NewHub creates a new Hub
func NewHub(redis *redisClient.Client, turnURL, turnSecret string) *Hub {
	// Initialize FCM client
	fcmClient, err := fcm.NewFCMClient()
	if err != nil {
		log.Printf("⚠️ Failed to initialize FCM client: %v (push notifications will be disabled)", err)
	} else {
		log.Printf("✅ FCM client initialized")
	}

	return &Hub{
		clients:    make(map[string]*Client),
		register:   make(chan *Client),
		unregister: make(chan *Client),
		broadcast:  make(chan *ClientMessage, 256),
		redis:      redis,
		fcm:        fcmClient,
		turnURL:    turnURL,
		turnSecret: turnSecret,
	}
}

// Run starts the hub's main loop
func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			// Close existing connection for same user (only one connection allowed)
			if existing, ok := h.clients[client.userID]; ok {
				close(existing.send)
				existing.conn.Close()
			}
			h.clients[client.userID] = client
			h.mu.Unlock()
			log.Printf("👤 Client connected: %s (total: %d)", client.userID, len(h.clients))

		case client := <-h.unregister:
			h.mu.Lock()
			if existing, ok := h.clients[client.userID]; ok && existing == client {
				delete(h.clients, client.userID)
				close(client.send)
				// Remove from available listeners when disconnecting
				go func(userID string) {
					ctx := context.Background()
					h.redis.RemoveAvailableListener(ctx, userID)
					log.Printf("🧹 Cleaned up listener availability for %s", userID)
				}(client.userID)
			}
			h.mu.Unlock()
			log.Printf("👋 Client disconnected: %s (total: %d)", client.userID, len(h.clients))

		case msg := <-h.broadcast:
			h.handleMessage(msg)
		}
	}
}

// handleMessage processes incoming messages from clients
func (h *Hub) handleMessage(msg *ClientMessage) {
	var message models.Message
	if err := json.Unmarshal(msg.Message, &message); err != nil {
		log.Printf("❌ Invalid message from %s: %v", msg.Client.userID, err)
		h.sendError(msg.Client, "invalidMessage", "Failed to parse message")
		return
	}

	log.Printf("📨 Message from %s: %s", msg.Client.userID, message.Type)

	switch message.Type {
	case models.MsgTypePing:
		h.handlePing(msg.Client)

	case models.MsgTypeRegister:
		h.handleRegister(msg.Client, message.Payload)

	case models.MsgTypeShareRequest:
		h.handleShareRequest(msg.Client, message.Payload)

	case models.MsgTypeCancelShare:
		h.handleCancelShare(msg.Client, message.Payload)

	case models.MsgTypeAvailability:
		h.handleAvailability(msg.Client, message.Payload)

	case models.MsgTypePreviewAccept:
		h.handlePreviewAccept(msg.Client, message.Payload)

	case models.MsgTypePreviewDecline:
		h.handlePreviewDecline(msg.Client, message.Payload)

	case models.MsgTypeWebRTCOffer:
		h.handleWebRTCOffer(msg.Client, message.Payload)

	case models.MsgTypeWebRTCAnswer:
		h.handleWebRTCAnswer(msg.Client, message.Payload)

	case models.MsgTypeICE:
		h.handleICE(msg.Client, message.Payload)

	case models.MsgTypeEndCall:
		h.handleEndCall(msg.Client, message.Payload)

	case models.MsgTypeMuteState:
		h.handleMuteState(msg.Client, message.Payload)

	case models.MsgTypeReport:
		h.handleReport(msg.Client, message.Payload)

	case models.MsgTypeBlockUser:
		h.handleBlockUser(msg.Client, message.Payload)

	case models.MsgTypeFcmToken:
		h.handleFcmToken(msg.Client, message.Payload)

	default:
		log.Printf("⚠️ Unknown message type from %s: %s", msg.Client.userID, message.Type)
	}
}

// handlePing responds to ping with pong
func (h *Hub) handlePing(client *Client) {
	h.sendToClient(client.userID, models.Message{Type: models.MsgTypePong})
}

// handleRegister handles client registration
func (h *Hub) handleRegister(client *Client, payload interface{}) {
	log.Printf("═══════════════════════════════════════════════════")
	log.Printf("📝 handleRegister CALLED")
	log.Printf("   userID: %s", client.userID)
	log.Printf("   payload: %+v", payload)

	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		log.Printf("   ❌ Invalid payload type: %T", payload)
		h.sendError(client, "invalidPayload", "Invalid register payload")
		return
	}

	role, _ := payloadMap["role"].(string)
	log.Printf("   role: %s", role)
	log.Printf("   ✅ Client %s registered as %s", client.userID, role)
	log.Printf("═══════════════════════════════════════════════════")

	// Send registration confirmation
	h.sendToClient(client.userID, models.Message{
		Type: models.MsgTypeRegistered,
		Payload: map[string]interface{}{
			"userId": client.userID,
			"role":   role,
		},
	})
}

// handleShareRequest handles a sharer submitting a share request
func (h *Hub) handleShareRequest(client *Client, payload interface{}) {
	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		h.sendError(client, "invalidPayload", "Invalid share request payload")
		return
	}

	ctx := context.Background()

	// Parse share request details
	topic, _ := payloadMap["topic"].(string)
	tone, _ := payloadMap["tone"].(string)
	intent, _ := payloadMap["intent"].(string)
	duration := 10
	if d, ok := payloadMap["duration"].(float64); ok {
		duration = int(d)
	}

	// Remove sharer from available listeners (they can't listen while sharing)
	h.redis.RemoveAvailableListener(ctx, client.userID)
	log.Printf("📴 Removed sharer %s from available listeners", client.userID)

	// Create share request
	shareID := generateUUID()
	share := models.ShareRequest{
		ID:              shareID,
		SharerID:        client.userID,
		Topic:           topic,
		Tone:            tone,
		PreviewText:     intent,
		DurationMinutes: duration,
		CreatedAt:       time.Now(),
		ExpiresAt:       time.Now().Add(5 * time.Minute), // Share expires in 5 minutes
		RetryCount:      0,
	}

	// Store share in Redis
	shareData, _ := json.Marshal(share)
	if err := h.redis.SetShare(ctx, shareID, shareData); err != nil {
		log.Printf("❌ Failed to store share %s: %v", shareID, err)
		h.sendError(client, "redisError", "Failed to create share")
		return
	}

	// Add to share queue
	if err := h.redis.AddToShareQueue(ctx, shareID, float64(share.CreatedAt.Unix())); err != nil {
		log.Printf("❌ Failed to queue share %s: %v", shareID, err)
		h.sendError(client, "redisError", "Failed to queue share")
		return
	}

	log.Printf("📤 Share request %s from %s queued (topic: %s)", shareID, client.userID, topic)

	// Confirm share is queued
	h.sendToClient(client.userID, models.Message{
		Type: models.MsgTypeShareQueued,
		Payload: map[string]interface{}{
			"shareId": shareID,
			"status":  "queued",
		},
	})
}

// handleCancelShare handles cancellation of a pending share
func (h *Hub) handleCancelShare(client *Client, payload interface{}) {
	log.Printf("🚫 Share cancellation requested by %s", client.userID)

	ctx := context.Background()

	// Find and remove all shares by this user from the queue
	// First, get all shares in queue to find ones belonging to this user
	shares, err := h.redis.ZRange(ctx, "share_queue", 0, -1).Result()
	if err != nil {
		log.Printf("⚠️ Error getting share queue: %v", err)
		return
	}

	cancelledCount := 0
	for _, shareID := range shares {
		// Get share details to check if it belongs to this user
		shareJSON, err := h.redis.GetShare(ctx, shareID)
		if err != nil {
			continue
		}

		var share models.ShareRequest
		if err := json.Unmarshal([]byte(shareJSON), &share); err != nil {
			continue
		}

		// If this share belongs to the requesting user, remove it
		if share.SharerID == client.userID {
			// Remove from queue
			h.redis.ZRem(ctx, "share_queue", shareID)
			// Delete share data
			h.redis.DeleteShare(ctx, shareID)
			cancelledCount++
			log.Printf("🗑️ Cancelled share %s", shareID)
		}
	}

	// Re-add user to available listeners (they're no longer sharing)
	if h.IsClientConnected(client.userID) {
		h.redis.AddAvailableListener(ctx, client.userID)
		log.Printf("📡 Re-added %s to available listeners after cancel", client.userID)
	}

	log.Printf("✅ Cancelled %d share(s) for %s", cancelledCount, client.userID)

	// Send confirmation to client
	h.sendToClient(client.userID, models.Message{
		Type: "shareCancelled",
		Payload: map[string]interface{}{
			"cancelledCount": cancelledCount,
		},
	})
}

// handleAvailability updates listener availability
func (h *Hub) handleAvailability(client *Client, payload interface{}) {
	// Parse payload
	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		h.sendError(client, "invalidPayload", "Invalid availability payload")
		return
	}

	// Get availability status - check "available" (from Android client) or "isAvailable" (standard camelCase)
	isAvailable := false
	if avail, ok := payloadMap["available"].(bool); ok {
		isAvailable = avail
	} else if avail, ok := payloadMap["isAvailable"].(bool); ok {
		isAvailable = avail
	}

	ctx := context.Background()

	if isAvailable {
		// Add to available listeners set
		if err := h.redis.AddAvailableListener(ctx, client.userID); err != nil {
			log.Printf("❌ Failed to add listener %s to available set: %v", client.userID, err)
			h.sendError(client, "redisError", "Failed to update availability")
			return
		}
		log.Printf("📡 Listener %s is now AVAILABLE", client.userID)
	} else {
		// Remove from available listeners set
		if err := h.redis.RemoveAvailableListener(ctx, client.userID); err != nil {
			log.Printf("❌ Failed to remove listener %s from available set: %v", client.userID, err)
		}
		log.Printf("📡 Listener %s is now UNAVAILABLE", client.userID)
	}

	// Send confirmation back to client
	h.sendToClient(client.userID, models.Message{
		Type: "availabilityConfirmed",
		Payload: map[string]interface{}{
			"available": isAvailable,
		},
	})
}

// handlePreviewAccept processes listener accepting a preview
func (h *Hub) handlePreviewAccept(client *Client, payload interface{}) {
	log.Printf("✅ Preview accepted by %s", client.userID)

	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		h.sendError(client, "invalidPayload", "Invalid preview accept payload")
		return
	}

	shareID, _ := payloadMap["shareId"].(string)
	log.Printf("═══════════════════════════════════════════════════")
	log.Printf("✅ handlePreviewAccept")
	log.Printf("   listenerID: %s", client.userID)
	log.Printf("   shareID: %s", shareID)

	if shareID == "" {
		log.Printf("   ❌ shareID is empty!")
		h.sendError(client, "missingShareId", "Share ID is required")
		return
	}

	ctx := context.Background()

	// Get share details from Redis
	log.Printf("   📥 Getting share from Redis...")
	shareJSON, err := h.redis.GetShare(ctx, shareID)
	if err != nil {
		log.Printf("   ❌ Failed to get share %s: %v", shareID, err)
		h.sendError(client, "shareNotFound", "Share not found or expired")
		return
	}
	log.Printf("   ✓ Got share data")

	var share models.ShareRequest
	if err := json.Unmarshal([]byte(shareJSON), &share); err != nil {
		log.Printf("   ❌ Failed to parse share %s: %v", shareID, err)
		h.sendError(client, "invalidShare", "Invalid share data")
		return
	}
	log.Printf("   ✓ Parsed share: sharerID=%s, topic=%s", share.SharerID, share.Topic)

	// Create a call ID
	callID := generateUUID()
	log.Printf("   📞 Generated callID: %s", callID)

	// Remove listener from available set (they're now in a call)
	log.Printf("   📴 Removing listener from available set...")
	h.redis.RemoveAvailableListener(ctx, client.userID)

	// Delete the share from Redis (it's been matched)
	log.Printf("   🗑️ Deleting share from Redis...")
	h.redis.DeleteShare(ctx, shareID)

	log.Printf("   📞 Creating call between sharer %s and listener %s", share.SharerID, client.userID)

	// Create TURN server credentials using configuration
	log.Printf("   🔧 TURN URL: %s", h.turnURL)
	turnServers := []models.TurnServer{
		{
			URLs:       []string{h.turnURL},
			Username:   "backroom",
			Credential: h.turnSecret,
		},
		{
			URLs: []string{"stun:stun.l.google.com:19302"},
		},
	}

	// Send match_made to the LISTENER
	log.Printf("   📤 Sending matchMade to LISTENER %s...", client.userID)
	listenerPayload := models.MatchMadePayload{
		CallID:          callID,
		SharerID:        share.SharerID,
		ListenerID:      client.userID,
		Role:            "listener",
		Topic:           share.Topic,
		Intent:          share.PreviewText,
		DurationMinutes: share.DurationMinutes,
		TurnServers:     turnServers,
	}
	h.sendToClient(client.userID, models.Message{
		Type:    models.MsgTypeMatchMade,
		Payload: listenerPayload,
	})
	log.Printf("   ✓ Sent matchMade to listener")

	// Send match_made to the SHARER
	log.Printf("   📤 Sending matchMade to SHARER %s...", share.SharerID)
	sharerPayload := models.MatchMadePayload{
		CallID:          callID,
		SharerID:        share.SharerID,
		ListenerID:      client.userID,
		Role:            "sharer",
		Topic:           share.Topic,
		Intent:          share.PreviewText,
		DurationMinutes: share.DurationMinutes,
		TurnServers:     turnServers,
	}
	h.sendToClient(share.SharerID, models.Message{
		Type:    models.MsgTypeMatchMade,
		Payload: sharerPayload,
	})
	log.Printf("   ✓ Sent matchMade to sharer")

	// Store the active call in Redis for later use (forwarding WebRTC signals)
	log.Printf("   💾 Storing active call in Redis...")
	activeCall := models.ActiveCall{
		ID:         callID,
		ShareID:    shareID,
		SharerID:   share.SharerID,
		ListenerID: client.userID,
		StartedAt:  time.Now(),
		Status:     "active",
	}
	callData, _ := json.Marshal(activeCall)
	h.redis.SetCall(ctx, callID, callData)

	// Also store user->call mapping for easy lookup
	h.redis.Set(ctx, "user_call:"+share.SharerID, callID, 0)
	h.redis.Set(ctx, "user_call:"+client.userID, callID, 0)
	log.Printf("   ✓ Call stored in Redis")

	log.Printf("═══════════════════════════════════════════════════")
	log.Printf("✅ Call %s created successfully!", callID)
}

// handlePreviewDecline processes listener declining a preview
func (h *Hub) handlePreviewDecline(client *Client, payload interface{}) {
	log.Printf("❌ Preview declined by %s", client.userID)

	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		return
	}

	shareID, _ := payloadMap["shareId"].(string)
	if shareID == "" {
		return
	}

	ctx := context.Background()

	// Get share details
	shareJSON, err := h.redis.GetShare(ctx, shareID)
	if err != nil {
		log.Printf("⚠️ Share %s not found (may have expired)", shareID)
		return
	}

	var share models.ShareRequest
	if err := json.Unmarshal([]byte(shareJSON), &share); err != nil {
		return
	}

	// Record that this listener declined this share (prevents re-matching)
	if err := h.redis.AddDeclinedListener(ctx, shareID, client.userID); err != nil {
		log.Printf("⚠️ Failed to record declined listener: %v", err)
	}

	// Re-queue the share so matcher can find next listener
	h.redis.AddToShareQueue(ctx, shareID, float64(share.CreatedAt.Unix()))
	log.Printf("🔄 Re-queued share %s to find next listener", shareID)
}

// handleWebRTCOffer forwards SDP offer to peer
func (h *Hub) handleWebRTCOffer(client *Client, payload interface{}) {
	h.forwardSignal(client, models.MsgTypeWebRTCOffer, payload)
}

// handleWebRTCAnswer forwards SDP answer to peer
func (h *Hub) handleWebRTCAnswer(client *Client, payload interface{}) {
	h.forwardSignal(client, models.MsgTypeWebRTCAnswer, payload)
}

// handleICE forwards ICE candidate to peer
func (h *Hub) handleICE(client *Client, payload interface{}) {
	h.forwardSignal(client, models.MsgTypeICE, payload)
}

// handleEndCall processes call end request
func (h *Hub) handleEndCall(client *Client, payload interface{}) {
	log.Printf("═══════════════════════════════════════════════════")
	log.Printf("📞 handleEndCall CALLED")
	log.Printf("   userID: %s", client.userID)
	log.Printf("   payload: %+v", payload)

	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		log.Printf("   ❌ Invalid payload type")
		h.sendError(client, "invalidPayload", "Invalid end call payload")
		return
	}

	// Try both camelCase and snake_case for callId
	callID, _ := payloadMap["callId"].(string)
	log.Printf("   callID: %s", callID)

	if callID == "" {
		log.Printf("   ❌ Missing callId!")
		h.sendError(client, "missingCallId", "Call ID is required")
		return
	}

	ctx := context.Background()

	// Get call details from Redis
	log.Printf("   📥 Getting call from Redis...")
	callJSON, err := h.redis.GetCall(ctx, callID)
	if err != nil || callJSON == "" {
		log.Printf("   ⚠️ Call %s not found (may have already ended)", callID)
		log.Printf("═══════════════════════════════════════════════════")
		return
	}
	log.Printf("   ✓ Got call data")

	var call models.ActiveCall
	if err := json.Unmarshal([]byte(callJSON), &call); err != nil {
		log.Printf("   ❌ Failed to parse call %s: %v", callID, err)
		return
	}
	log.Printf("   ✓ Parsed call: sharerID=%s, listenerID=%s", call.SharerID, call.ListenerID)

	// Determine who is ending the call and who the peer is
	var endedBy string
	var peerID string
	if client.userID == call.SharerID {
		endedBy = "sharer"
		peerID = call.ListenerID
		log.Printf("   Ended by: SHARER, peer: %s", peerID)
	} else if client.userID == call.ListenerID {
		endedBy = "listener"
		peerID = call.SharerID
		log.Printf("   Ended by: LISTENER, peer: %s", peerID)
	} else {
		log.Printf("   ❌ User %s is not part of call %s", client.userID, callID)
		h.sendError(client, "notInCall", "You are not part of this call")
		return
	}

	// Get optional reason
	reason, _ := payloadMap["reason"].(string)
	log.Printf("   reason: %s", reason)

	// Calculate call duration
	duration := int(time.Since(call.StartedAt).Seconds())
	log.Printf("   duration: %d seconds", duration)

	// Create call ended payload
	callEndedPayload := models.CallEndedPayload{
		CallID:   callID,
		EndedBy:  endedBy,
		Reason:   reason,
		Duration: duration,
	}

	// Notify the peer that the call has ended
	log.Printf("   📤 Sending callEnded to peer %s...", peerID)
	h.sendToClient(peerID, models.Message{
		Type:    models.MsgTypeCallEnded,
		Payload: callEndedPayload,
	})
	log.Printf("   ✓ Sent callEnded to peer")

	// Also send confirmation to the client who ended the call
	h.sendToClient(client.userID, models.Message{
		Type:    models.MsgTypeCallEnded,
		Payload: callEndedPayload,
	})
	log.Printf("📤 Sent call_ended confirmation to %s", client.userID)

	// Clean up Redis
	// Delete call record
	h.redis.Del(ctx, "call:"+callID)

	// Delete user-call mappings
	h.redis.Del(ctx, "user_call:"+call.SharerID)
	h.redis.Del(ctx, "user_call:"+call.ListenerID)

	// Re-add the LISTENER to available set (they can accept new calls)
	// Note: The listener is the one who was waiting to receive calls
	if h.IsClientConnected(call.ListenerID) {
		h.redis.AddAvailableListener(ctx, call.ListenerID)
		log.Printf("   ✅ Re-added listener %s to available set", call.ListenerID)
	} else {
		log.Printf("   ⚠️ Listener %s not connected, not re-adding to available set", call.ListenerID)
	}

	log.Printf("✅ Call %s ended by %s (duration: %ds)", callID, endedBy, duration)
	log.Printf("═══════════════════════════════════════════════════")
}

// handleMuteState forwards mute state to the peer
func (h *Hub) handleMuteState(client *Client, payload interface{}) {
	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		h.sendError(client, "invalidPayload", "Invalid mute state payload")
		return
	}

	callID, _ := payloadMap["callId"].(string)
	if callID == "" {
		return
	}

	muted, _ := payloadMap["muted"].(bool)

	ctx := context.Background()

	// Get call details from Redis to find peer
	callJSON, err := h.redis.GetCall(ctx, callID)
	if err != nil || callJSON == "" {
		return
	}

	var call models.ActiveCall
	if err := json.Unmarshal([]byte(callJSON), &call); err != nil {
		return
	}

	// Determine the peer
	var peerID string
	if client.userID == call.SharerID {
		peerID = call.ListenerID
	} else if client.userID == call.ListenerID {
		peerID = call.SharerID
	} else {
		return
	}

	// Forward mute state to peer
	h.sendToClient(peerID, models.Message{
		Type: models.MsgTypeRemoteMuteState,
		Payload: map[string]interface{}{
			"callId": callID,
			"muted":  muted,
		},
	})
	log.Printf("📤 Forwarded mute state from %s to %s (muted: %v)", client.userID, peerID, muted)
}

// forwardSignal forwards a signaling message to the peer in the same call
func (h *Hub) forwardSignal(client *Client, msgType models.MessageType, payload interface{}) {
	log.Printf("🔄 forwardSignal: type=%s, from=%s", msgType, client.userID)

	// Extract call ID from payload
	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		log.Printf("   ❌ Invalid payload type: %T", payload)
		h.sendError(client, "invalidPayload", "Invalid signal payload")
		return
	}

	// Use camelCase for callId
	callID, _ := payloadMap["callId"].(string)
	log.Printf("   callID: %s", callID)

	if callID == "" {
		log.Printf("   ❌ Missing callId in %s from %s", msgType, client.userID)
		h.sendError(client, "missingCallId", "Call ID is required")
		return
	}

	ctx := context.Background()

	// Get call details from Redis to find peer
	callJSON, err := h.redis.GetCall(ctx, callID)
	if err != nil || callJSON == "" {
		log.Printf("   ❌ Call %s not found for %s signal", callID, msgType)
		h.sendError(client, "callNotFound", "Call not found")
		return
	}

	var call models.ActiveCall
	if err := json.Unmarshal([]byte(callJSON), &call); err != nil {
		log.Printf("   ❌ Failed to parse call %s: %v", callID, err)
		h.sendError(client, "invalidCall", "Invalid call data")
		return
	}

	// Determine the peer (the other participant in the call)
	var peerID string
	if client.userID == call.SharerID {
		peerID = call.ListenerID
	} else if client.userID == call.ListenerID {
		peerID = call.SharerID
	} else {
		log.Printf("   ❌ User %s is not part of call %s", client.userID, callID)
		h.sendError(client, "notInCall", "You are not part of this call")
		return
	}
	log.Printf("   peerID: %s", peerID)

	// Forward the message to the peer
	success := h.sendToClient(peerID, models.Message{
		Type:    msgType,
		Payload: payloadMap,
	})

	if success {
		log.Printf("   ✅ Forwarded %s from %s to %s", msgType, client.userID, peerID)
	} else {
		log.Printf("   ❌ Failed to forward %s to %s (not connected?)", msgType, peerID)
	}
}

// SendToClient sends a message to a specific client
func (h *Hub) sendToClient(userID string, msg models.Message) bool {
	h.mu.RLock()
	client, ok := h.clients[userID]
	h.mu.RUnlock()

	if !ok {
		return false
	}

	data, err := json.Marshal(msg)
	if err != nil {
		return false
	}

	select {
	case client.send <- data:
		return true
	default:
		return false
	}
}

// SendToClient is the exported version
func (h *Hub) SendToClient(userID string, msg models.Message) bool {
	return h.sendToClient(userID, msg)
}

// sendError sends an error message to a client
func (h *Hub) sendError(client *Client, code, message string) {
	h.sendToClient(client.userID, models.Message{
		Type: models.MsgTypeError,
		Payload: models.ErrorPayload{
			Code:    code,
			Message: message,
		},
	})
}

// RegisterClient registers a new client connection
func (h *Hub) RegisterClient(conn *websocket.Conn, userID string) *Client {
	client := &Client{
		hub:    h,
		conn:   conn,
		send:   make(chan []byte, 256),
		userID: userID,
	}

	h.register <- client

	// Send connected confirmation to the client
	go func() {
		// Small delay to ensure client is fully registered
		time.Sleep(50 * time.Millisecond)
		h.sendToClient(userID, models.Message{
			Type: "connected",
			Payload: map[string]interface{}{
				"clientId": userID,
				"message":  "Connected to Backroom signaling server",
			},
		})
	}()

	return client
}

// IsClientConnected checks if a client is connected
func (h *Hub) IsClientConnected(userID string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	_, ok := h.clients[userID]
	return ok
}

// GetConnectedCount returns the number of connected clients
func (h *Hub) GetConnectedCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}

// ReadPump pumps messages from the WebSocket connection to the hub
func (c *Client) ReadPump() {
	defer func() {
		c.hub.unregister <- c
		c.conn.Close()
	}()

	c.conn.SetReadLimit(maxMessageSize)
	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, message, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("❌ WebSocket error for %s: %v", c.userID, err)
			}
			break
		}

		c.hub.broadcast <- &ClientMessage{
			Client:  c,
			Message: message,
		}
	}
}

// WritePump pumps messages from the hub to the WebSocket connection
func (c *Client) WritePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				// Hub closed the channel
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			// Send this message
			if err := c.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				return
			}

			// Send any additional queued messages as separate frames
			n := len(c.send)
			for i := 0; i < n; i++ {
				msg := <-c.send
				if err := c.conn.WriteMessage(websocket.TextMessage, msg); err != nil {
					return
				}
			}

		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// handleReport handles user reports
func (h *Hub) handleReport(client *Client, payload interface{}) {
	log.Printf("═══════════════════════════════════════════════════")
	log.Printf("🚨 handleReport CALLED")
	log.Printf("   reporterID: %s", client.userID)
	log.Printf("   payload: %+v", payload)

	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		log.Printf("   ❌ Invalid payload type: %T", payload)
		h.sendError(client, "invalidPayload", "Invalid report payload")
		return
	}

	callID, _ := payloadMap["callId"].(string)
	reason, _ := payloadMap["reason"].(string)
	details, _ := payloadMap["details"].(string)
	blockUser, _ := payloadMap["blockUser"].(bool)

	log.Printf("   callID: %s", callID)
	log.Printf("   reason: %s", reason)
	log.Printf("   details: %s", details)
	log.Printf("   blockUser: %v", blockUser)

	// Get the call info to find the reported user
	ctx := context.Background()
	callData, err := h.redis.GetActiveCall(ctx, callID)
	if err != nil {
		log.Printf("   ⚠️ Could not find call data for callId: %s (may have already ended)", callID)
	}

	var reportedUserID string
	if callData != nil {
		// Determine who was reported (the other party)
		if client.userID == callData.SharerID {
			reportedUserID = callData.ListenerID
		} else {
			reportedUserID = callData.SharerID
		}
		log.Printf("   reportedUserID: %s", reportedUserID)
	}

	// Store the report in Redis
	report := models.Report{
		ID:          generateUUID(),
		ReporterID:  client.userID,
		ReportedID:  reportedUserID,
		CallID:      callID,
		Reason:      reason,
		Details:     details,
		BlockedUser: blockUser,
		CreatedAt:   time.Now(),
	}

	err = h.redis.StoreReport(ctx, report)
	if err != nil {
		log.Printf("   ❌ Failed to store report: %v", err)
	} else {
		log.Printf("   ✅ Report stored with ID: %s", report.ID)
	}

	// If user wants to block, add to blocked list
	if blockUser && reportedUserID != "" {
		err = h.redis.BlockUser(ctx, client.userID, reportedUserID)
		if err != nil {
			log.Printf("   ❌ Failed to block user: %v", err)
		} else {
			log.Printf("   ✅ User %s blocked by %s", reportedUserID, client.userID)
		}
	}

	// Send confirmation
	h.sendToClient(client.userID, models.Message{
		Type: models.MsgTypeReportReceived,
		Payload: map[string]interface{}{
			"reportId":    report.ID,
			"message":     "Report received. Thank you for helping keep Backroom safe.",
			"userBlocked": blockUser,
		},
	})

	log.Printf("   ✅ Report handled successfully")
	log.Printf("═══════════════════════════════════════════════════")
}

// handleBlockUser handles user blocking
func (h *Hub) handleBlockUser(client *Client, payload interface{}) {
	log.Printf("═══════════════════════════════════════════════════")
	log.Printf("🚫 handleBlockUser CALLED")
	log.Printf("   blockerID: %s", client.userID)
	log.Printf("   payload: %+v", payload)

	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		log.Printf("   ❌ Invalid payload type: %T", payload)
		h.sendError(client, "invalidPayload", "Invalid blockUser payload")
		return
	}

	userIDToBlock, _ := payloadMap["userId"].(string)
	if userIDToBlock == "" {
		log.Printf("   ❌ No userId provided")
		h.sendError(client, "invalidPayload", "userId is required")
		return
	}

	log.Printf("   userIDToBlock: %s", userIDToBlock)

	ctx := context.Background()
	err := h.redis.BlockUser(ctx, client.userID, userIDToBlock)
	if err != nil {
		log.Printf("   ❌ Failed to block user: %v", err)
		h.sendError(client, "blockFailed", "Failed to block user")
		return
	}

	h.sendToClient(client.userID, models.Message{
		Type: models.MsgTypeUserBlocked,
		Payload: map[string]interface{}{
			"userId":  userIDToBlock,
			"message": "User has been blocked",
		},
	})

	log.Printf("   ✅ User %s blocked by %s", userIDToBlock, client.userID)
	log.Printf("═══════════════════════════════════════════════════")
}

// handleFcmToken handles FCM token registration for push notifications
func (h *Hub) handleFcmToken(client *Client, payload interface{}) {
	log.Printf("═══════════════════════════════════════════════════")
	log.Printf("🔑 handleFcmToken CALLED")
	log.Printf("   userID: %s", client.userID)

	payloadMap, ok := payload.(map[string]interface{})
	if !ok {
		log.Printf("   ❌ Invalid payload type: %T", payload)
		h.sendError(client, "invalidPayload", "Invalid fcmToken payload")
		return
	}

	token, _ := payloadMap["token"].(string)
	if token == "" {
		log.Printf("   ❌ No token provided")
		h.sendError(client, "invalidPayload", "token is required")
		return
	}

	log.Printf("   token: %s...%s", token[:20], token[len(token)-10:])

	ctx := context.Background()
	err := h.redis.SetFcmToken(ctx, client.userID, token)
	if err != nil {
		log.Printf("   ❌ Failed to store FCM token: %v", err)
		h.sendError(client, "tokenFailed", "Failed to store FCM token")
		return
	}

	log.Printf("   ✅ FCM token stored for user %s", client.userID)
	log.Printf("═══════════════════════════════════════════════════")
}
