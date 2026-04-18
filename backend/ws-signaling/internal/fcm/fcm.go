package fcm

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"time"

	"golang.org/x/oauth2/google"
)

// FCMClient handles sending push notifications via Firebase Cloud Messaging
type FCMClient struct {
	projectID  string
	httpClient *http.Client
	accessToken string
	tokenExpiry time.Time
}

// FCMMessage represents a push notification message
type FCMMessage struct {
	Message struct {
		Token string            `json:"token"`
		Data  map[string]string `json:"data,omitempty"`
		Notification *Notification `json:"notification,omitempty"`
		Android *AndroidConfig `json:"android,omitempty"`
	} `json:"message"`
}

// Notification for display when app is in background
type Notification struct {
	Title string `json:"title,omitempty"`
	Body  string `json:"body,omitempty"`
}

// AndroidConfig for Android-specific settings
type AndroidConfig struct {
	Priority string `json:"priority,omitempty"` // "high" or "normal"
	TTL      string `json:"ttl,omitempty"`      // e.g., "60s"
}

// NewFCMClient creates a new FCM client
func NewFCMClient() (*FCMClient, error) {
	projectID := os.Getenv("FIREBASE_PROJECT_ID")
	if projectID == "" {
		projectID = "backroom-b51b1" // Default from google-services.json
	}

	client := &FCMClient{
		projectID:  projectID,
		httpClient: &http.Client{Timeout: 10 * time.Second},
	}

	return client, nil
}

// getAccessToken gets a valid OAuth2 access token for FCM
func (c *FCMClient) getAccessToken(ctx context.Context) (string, error) {
	// Check if we have a valid cached token
	if c.accessToken != "" && time.Now().Before(c.tokenExpiry) {
		return c.accessToken, nil
	}

	// Get credentials from environment or service account file
	credentials, err := google.FindDefaultCredentials(ctx, "https://www.googleapis.com/auth/firebase.messaging")
	if err != nil {
		// Try to read from service account file
		serviceAccountPath := os.Getenv("GOOGLE_APPLICATION_CREDENTIALS")
		if serviceAccountPath == "" {
			serviceAccountPath = "/root/firebase-service-account.json"
		}

		credBytes, err := os.ReadFile(serviceAccountPath)
		if err != nil {
			return "", fmt.Errorf("failed to read service account: %v", err)
		}

		conf, err := google.JWTConfigFromJSON(credBytes, "https://www.googleapis.com/auth/firebase.messaging")
		if err != nil {
			return "", fmt.Errorf("failed to parse service account: %v", err)
		}

		token, err := conf.TokenSource(ctx).Token()
		if err != nil {
			return "", fmt.Errorf("failed to get token: %v", err)
		}

		c.accessToken = token.AccessToken
		c.tokenExpiry = token.Expiry.Add(-5 * time.Minute) // Refresh 5 min early
		return c.accessToken, nil
	}

	token, err := credentials.TokenSource.Token()
	if err != nil {
		return "", fmt.Errorf("failed to get token: %v", err)
	}

	c.accessToken = token.AccessToken
	c.tokenExpiry = token.Expiry.Add(-5 * time.Minute)
	return c.accessToken, nil
}

// SendToDevice sends a push notification to a specific device
func (c *FCMClient) SendToDevice(ctx context.Context, fcmToken string, data map[string]string, notification *Notification) error {
	log.Printf("📱 Sending FCM push to token: %s...%s", fcmToken[:20], fcmToken[len(fcmToken)-10:])

	accessToken, err := c.getAccessToken(ctx)
	if err != nil {
		return fmt.Errorf("failed to get access token: %v", err)
	}

	// Build the message
	msg := FCMMessage{}
	msg.Message.Token = fcmToken
	msg.Message.Data = data
	msg.Message.Notification = notification
	msg.Message.Android = &AndroidConfig{
		Priority: "high",
		TTL:      "60s", // Message expires after 60 seconds
	}

	body, err := json.Marshal(msg)
	if err != nil {
		return fmt.Errorf("failed to marshal message: %v", err)
	}

	// Send the request
	url := fmt.Sprintf("https://fcm.googleapis.com/v1/projects/%s/messages:send", c.projectID)
	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("failed to create request: %v", err)
	}

	req.Header.Set("Authorization", "Bearer "+accessToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("failed to send request: %v", err)
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("FCM returned status %d: %s", resp.StatusCode, string(respBody))
	}

	log.Printf("   ✅ FCM push sent successfully")
	return nil
}

// SendIncomingPreview sends an incoming call preview notification
func (c *FCMClient) SendIncomingPreview(ctx context.Context, fcmToken string, shareID, topic, tone, previewText string, durationMinutes, countdownSeconds int) error {
	log.Printf("📞 Sending incoming preview push for share: %s", shareID)

	data := map[string]string{
		"type":             "incomingPreview",
		"shareId":          shareID,
		"topic":            topic,
		"tone":             tone,
		"previewText":      previewText,
		"durationMinutes":  fmt.Sprintf("%d", durationMinutes),
		"countdownSeconds": fmt.Sprintf("%d", countdownSeconds),
	}

	notification := &Notification{
		Title: "Someone wants to talk",
		Body:  fmt.Sprintf("%s • %s • %d min", topic, tone, durationMinutes),
	}

	return c.SendToDevice(ctx, fcmToken, data, notification)
}

// SendMatchMade sends a match made notification
func (c *FCMClient) SendMatchMade(ctx context.Context, fcmToken, callID, role string) error {
	log.Printf("🎯 Sending match made push for call: %s", callID)

	data := map[string]string{
		"type":   "matchMade",
		"callId": callID,
		"role":   role,
	}

	notification := &Notification{
		Title: "Call Connected",
		Body:  "Your call is ready to begin",
	}

	return c.SendToDevice(ctx, fcmToken, data, notification)
}

// SendCallEnded sends a call ended notification
func (c *FCMClient) SendCallEnded(ctx context.Context, fcmToken, callID, reason string) error {
	log.Printf("📴 Sending call ended push for call: %s", callID)

	data := map[string]string{
		"type":   "callEnded",
		"callId": callID,
		"reason": reason,
	}

	// No notification for call ended - just data message
	return c.SendToDevice(ctx, fcmToken, data, nil)
}

// SendWakeUp sends a wake up notification to reconnect the app
func (c *FCMClient) SendWakeUp(ctx context.Context, fcmToken string) error {
	log.Printf("⏰ Sending wake up push")

	data := map[string]string{
		"type": "wakeUp",
	}

	// No notification - silent push
	return c.SendToDevice(ctx, fcmToken, data, nil)
}

