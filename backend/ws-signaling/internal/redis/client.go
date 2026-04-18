package redis

import (
	"context"
	"encoding/json"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/backroom/ws-signaling/internal/models"
)

// Redis key prefixes
const (
	KeyPrefixShare           = "share:"              // share:{id} -> ShareRequest JSON
	KeyPrefixListener        = "listener:"           // listener:{userId} -> ListenerInfo JSON
	KeyPrefixCall            = "call:"               // call:{id} -> ActiveCall JSON
	KeyPrefixUserCall        = "user_call:"          // user_call:{userId} -> call_id
	KeyPrefixShareDeclined   = "share_declined:"     // share_declined:{shareId} -> Set of listener IDs who declined
	KeyPrefixReport          = "report:"             // report:{id} -> Report JSON
	KeyPrefixBlockedUsers    = "blocked_users:"      // blocked_users:{userId} -> Set of blocked user IDs
	KeyPrefixFcmToken        = "fcm_token:"          // fcm_token:{userId} -> FCM token string
	KeyShareQueue            = "share_queue"         // Sorted set of pending shares
	KeyAvailableListeners    = "available_listeners" // Set of available listener IDs
	KeyPubSubMatch           = "pubsub:match"        // Pub/sub channel for matches
	KeyPubSubSignal          = "pubsub:signal"       // Pub/sub channel for signaling
)

// Client wraps redis client with helper methods
type Client struct {
	*redis.Client
}

// NewClient creates a new Redis client
func NewClient(redisURL string) *Client {
	opts, err := redis.ParseURL(redisURL)
	if err != nil {
		// Fallback to basic options
		opts = &redis.Options{
			Addr: "localhost:6379",
		}
	}

	return &Client{
		Client: redis.NewClient(opts),
	}
}

// AddToShareQueue adds a share to the waiting queue
func (c *Client) AddToShareQueue(ctx context.Context, shareID string, score float64) error {
	return c.ZAdd(ctx, KeyShareQueue, redis.Z{
		Score:  score,
		Member: shareID,
	}).Err()
}

// PopFromShareQueue pops the oldest share from the queue
func (c *Client) PopFromShareQueue(ctx context.Context) (string, error) {
	results, err := c.ZPopMin(ctx, KeyShareQueue, 1).Result()
	if err != nil {
		return "", err
	}
	if len(results) == 0 {
		return "", nil
	}
	return results[0].Member.(string), nil
}

// PopReadyFromShareQueue pops a share only if its scheduled time has passed
// This allows for delayed re-queuing when no listener is available
func (c *Client) PopReadyFromShareQueue(ctx context.Context) (string, error) {
	// Get the item with smallest score (oldest/earliest scheduled)
	results, err := c.ZRangeWithScores(ctx, KeyShareQueue, 0, 0).Result()
	if err != nil {
		return "", err
	}
	if len(results) == 0 {
		return "", nil
	}

	// Check if the item is ready (score is in the past)
	score := results[0].Score
	now := float64(time.Now().Unix())
	if score > now {
		// Not ready yet, don't pop
		return "", nil
	}

	// Ready to process, pop it
	shareID := results[0].Member.(string)
	c.ZRem(ctx, KeyShareQueue, shareID)
	return shareID, nil
}

// AddAvailableListener adds a listener to the available set
func (c *Client) AddAvailableListener(ctx context.Context, userID string) error {
	return c.SAdd(ctx, KeyAvailableListeners, userID).Err()
}

// RemoveAvailableListener removes a listener from the available set
func (c *Client) RemoveAvailableListener(ctx context.Context, userID string) error {
	return c.SRem(ctx, KeyAvailableListeners, userID).Err()
}

// GetAvailableListeners returns all available listener IDs
func (c *Client) GetAvailableListeners(ctx context.Context) ([]string, error) {
	return c.SMembers(ctx, KeyAvailableListeners).Result()
}

// IsListenerAvailable checks if a listener is available
func (c *Client) IsListenerAvailable(ctx context.Context, userID string) (bool, error) {
	return c.SIsMember(ctx, KeyAvailableListeners, userID).Result()
}

// SetShare stores a share request
func (c *Client) SetShare(ctx context.Context, shareID string, data []byte) error {
	return c.Set(ctx, KeyPrefixShare+shareID, data, 0).Err()
}

// GetShare retrieves a share request
func (c *Client) GetShare(ctx context.Context, shareID string) (string, error) {
	return c.Get(ctx, KeyPrefixShare+shareID).Result()
}

// DeleteShare removes a share request and its associated declined listeners set
func (c *Client) DeleteShare(ctx context.Context, shareID string) error {
	// Delete the share data, declined listeners tracking set, and remove from queue
	pipe := c.Pipeline()
	pipe.Del(ctx, KeyPrefixShare+shareID)
	pipe.Del(ctx, KeyPrefixShareDeclined+shareID)
	pipe.ZRem(ctx, KeyShareQueue, shareID) // Also remove from the share queue
	_, err := pipe.Exec(ctx)
	return err
}

// SetCall stores an active call
func (c *Client) SetCall(ctx context.Context, callID string, data []byte) error {
	return c.Set(ctx, KeyPrefixCall+callID, data, 0).Err()
}

// GetCall retrieves an active call
func (c *Client) GetCall(ctx context.Context, callID string) (string, error) {
	return c.Get(ctx, KeyPrefixCall+callID).Result()
}

// GetActiveCall retrieves and unmarshals an active call
func (c *Client) GetActiveCall(ctx context.Context, callID string) (*models.ActiveCall, error) {
	data, err := c.Get(ctx, KeyPrefixCall+callID).Result()
	if err != nil {
		return nil, err
	}
	var call models.ActiveCall
	if err := json.Unmarshal([]byte(data), &call); err != nil {
		return nil, err
	}
	return &call, nil
}

// SetUserCall maps a user to their active call
func (c *Client) SetUserCall(ctx context.Context, userID, callID string) error {
	return c.Set(ctx, KeyPrefixUserCall+userID, callID, 0).Err()
}

// GetUserCall gets the active call for a user
func (c *Client) GetUserCall(ctx context.Context, userID string) (string, error) {
	return c.Get(ctx, KeyPrefixUserCall+userID).Result()
}

// DeleteUserCall removes the user-call mapping
func (c *Client) DeleteUserCall(ctx context.Context, userID string) error {
	return c.Del(ctx, KeyPrefixUserCall+userID).Err()
}

// Publish publishes a message to a channel
func (c *Client) PublishMessage(ctx context.Context, channel string, message []byte) error {
	return c.Publish(ctx, channel, message).Err()
}

// AddDeclinedListener records that a listener declined a share
func (c *Client) AddDeclinedListener(ctx context.Context, shareID, listenerID string) error {
	return c.SAdd(ctx, KeyPrefixShareDeclined+shareID, listenerID).Err()
}

// HasListenerDeclined checks if a listener has already declined a share
func (c *Client) HasListenerDeclined(ctx context.Context, shareID, listenerID string) (bool, error) {
	return c.SIsMember(ctx, KeyPrefixShareDeclined+shareID, listenerID).Result()
}

// ClearDeclinedListeners removes the declined listeners set for a share (cleanup)
func (c *Client) ClearDeclinedListeners(ctx context.Context, shareID string) error {
	return c.Del(ctx, KeyPrefixShareDeclined+shareID).Err()
}

// StoreReport stores a user report
func (c *Client) StoreReport(ctx context.Context, report models.Report) error {
	data, err := json.Marshal(report)
	if err != nil {
		return err
	}
	return c.Set(ctx, KeyPrefixReport+report.ID, data, 0).Err()
}

// BlockUser adds a user to the blocker's blocked list
func (c *Client) BlockUser(ctx context.Context, blockerID, blockedID string) error {
	return c.SAdd(ctx, KeyPrefixBlockedUsers+blockerID, blockedID).Err()
}

// UnblockUser removes a user from the blocker's blocked list
func (c *Client) UnblockUser(ctx context.Context, blockerID, blockedID string) error {
	return c.SRem(ctx, KeyPrefixBlockedUsers+blockerID, blockedID).Err()
}

// IsUserBlocked checks if a user has blocked another user
func (c *Client) IsUserBlocked(ctx context.Context, blockerID, blockedID string) (bool, error) {
	return c.SIsMember(ctx, KeyPrefixBlockedUsers+blockerID, blockedID).Result()
}

// GetBlockedUsers returns all users blocked by a specific user
func (c *Client) GetBlockedUsers(ctx context.Context, userID string) ([]string, error) {
	return c.SMembers(ctx, KeyPrefixBlockedUsers+userID).Result()
}

// SetFcmToken stores a user's FCM token for push notifications
func (c *Client) SetFcmToken(ctx context.Context, userID, token string) error {
	// Store token with 30 day expiration (tokens can change)
	return c.Set(ctx, KeyPrefixFcmToken+userID, token, 30*24*time.Hour).Err()
}

// GetFcmToken retrieves a user's FCM token
func (c *Client) GetFcmToken(ctx context.Context, userID string) (string, error) {
	return c.Get(ctx, KeyPrefixFcmToken+userID).Result()
}

// DeleteFcmToken removes a user's FCM token
func (c *Client) DeleteFcmToken(ctx context.Context, userID string) error {
	return c.Del(ctx, KeyPrefixFcmToken+userID).Err()
}
