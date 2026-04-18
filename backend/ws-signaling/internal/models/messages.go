package models

import "time"

// MessageType defines the type of WebSocket message
type MessageType string

const (
	// Client -> Server (keeping these as they are - client sends them)
	MsgTypeRegister       MessageType = "register"
	MsgTypeShareRequest   MessageType = "shareRequest"
	MsgTypeCancelShare    MessageType = "cancelShare"
	MsgTypePreviewAccept  MessageType = "previewAccept"
	MsgTypePreviewDecline MessageType = "previewDecline"
	MsgTypeAvailability   MessageType = "availability"
	MsgTypeWebRTCOffer    MessageType = "webrtc/offer"
	MsgTypeWebRTCAnswer   MessageType = "webrtc/answer"
	MsgTypeICE            MessageType = "ice"
	MsgTypeEndCall        MessageType = "endCall"
	MsgTypeMuteState      MessageType = "muteState"
	MsgTypePing           MessageType = "ping"
	MsgTypeReport         MessageType = "report"
	MsgTypeBlockUser      MessageType = "blockUser"
	MsgTypeFcmToken       MessageType = "fcmToken"

	// Server -> Client
	MsgTypeRegistered      MessageType = "registered"
	MsgTypeShareQueued     MessageType = "shareQueued"
	MsgTypeShareSubmitted  MessageType = "shareSubmitted"
	MsgTypeShareExpired    MessageType = "shareExpired"
	MsgTypeIncomingPreview MessageType = "incomingPreview"
	MsgTypeMatchMade       MessageType = "matchMade"
	MsgTypeCallEnded       MessageType = "callEnded"
	MsgTypeRemoteMuteState MessageType = "remoteMuteState"
	MsgTypeError           MessageType = "error"
	MsgTypePong            MessageType = "pong"
	MsgTypeReportReceived  MessageType = "reportReceived"
	MsgTypeUserBlocked     MessageType = "userBlocked"
)

// Message is the base WebSocket message structure
type Message struct {
	Type    MessageType     `json:"type"`
	Payload interface{}     `json:"payload,omitempty"`
}

// IncomingPreviewPayload is sent to listeners when a share request matches
type IncomingPreviewPayload struct {
	ShareID          string `json:"shareId"`
	Topic            string `json:"topic"`
	Tone             string `json:"tone"`
	PreviewText      string `json:"previewText"`
	DurationMinutes  int    `json:"durationMinutes"`
	CountdownSeconds int    `json:"countdownSeconds"`
}

// PreviewAcceptPayload is sent by listener to accept a preview
type PreviewAcceptPayload struct {
	ShareID string `json:"shareId"`
}

// PreviewDeclinePayload is sent by listener to decline a preview
type PreviewDeclinePayload struct {
	ShareID string `json:"shareId"`
}

// AvailabilityPayload is sent by listener to toggle availability
type AvailabilityPayload struct {
	IsAvailable bool `json:"isAvailable"`
	Capacity    int  `json:"capacity,omitempty"`
}

// MatchMadePayload is sent to both parties when a match is confirmed
type MatchMadePayload struct {
	CallID          string       `json:"callId"`
	SharerID        string       `json:"sharerId"`
	ListenerID      string       `json:"listenerId"`
	Role            string       `json:"role"` // "sharer" or "listener"
	Topic           string       `json:"topic,omitempty"`
	Intent          string       `json:"intent,omitempty"`
	DurationMinutes int          `json:"durationMinutes,omitempty"`
	TurnServers     []TurnServer `json:"turnServers"`
}

// TurnServer contains TURN/STUN server credentials
type TurnServer struct {
	URLs       []string `json:"urls"`
	Username   string   `json:"username,omitempty"`
	Credential string   `json:"credential,omitempty"`
}

// WebRTCOfferPayload contains SDP offer
type WebRTCOfferPayload struct {
	CallID string `json:"callId"`
	SDP    string `json:"sdp"`
}

// WebRTCAnswerPayload contains SDP answer
type WebRTCAnswerPayload struct {
	CallID string `json:"callId"`
	SDP    string `json:"sdp"`
}

// ICEPayload contains ICE candidate
type ICEPayload struct {
	CallID    string      `json:"callId"`
	Candidate interface{} `json:"candidate"`
}

// EndCallPayload is sent when a call ends
type EndCallPayload struct {
	CallID     string `json:"callId"`
	Reason     string `json:"reason,omitempty"`
}

// CallEndedPayload is sent to notify call has ended
type CallEndedPayload struct {
	CallID   string `json:"callId"`
	EndedBy  string `json:"endedBy"` // "sharer", "listener", "system"
	Reason   string `json:"reason,omitempty"`
	Duration int    `json:"durationSeconds,omitempty"`
}

// ErrorPayload contains error details
type ErrorPayload struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

// ShareRequest represents a pending share in the queue
type ShareRequest struct {
	ID              string    `json:"id"`
	SharerID        string    `json:"sharerId"`
	Topic           string    `json:"topic"`
	Tone            string    `json:"tone"`
	PreviewText     string    `json:"previewText"`
	DurationMinutes int       `json:"durationMinutes"`
	CreatedAt       time.Time `json:"createdAt"`
	ExpiresAt       time.Time `json:"expiresAt"`
	RetryCount      int       `json:"retryCount,omitempty"` // Track retry attempts
}

// ListenerInfo represents a listener's availability info
type ListenerInfo struct {
	UserID       string   `json:"userId"`
	IsAvailable  bool     `json:"isAvailable"`
	Capacity     int      `json:"capacity"`
	CurrentLoad  int      `json:"currentLoad"`
	Topics       []string `json:"topics"`
	MaxIntensity string   `json:"maxIntensity"`
}

// ActiveCall represents an ongoing call
type ActiveCall struct {
	ID         string    `json:"id"`
	ShareID    string    `json:"shareId"`
	SharerID   string    `json:"sharerId"`
	ListenerID string    `json:"listenerId"`
	StartedAt  time.Time `json:"startedAt"`
	Status     string    `json:"status"`
}

// ReportPayload is sent when a user reports an issue
type ReportPayload struct {
	CallID    string `json:"callId"`
	Reason    string `json:"reason"`
	Details   string `json:"details"`
	BlockUser bool   `json:"blockUser"`
}

// BlockUserPayload is sent when a user wants to block another user
type BlockUserPayload struct {
	UserID string `json:"userId"`
}

// Report represents a stored report
type Report struct {
	ID           string    `json:"id"`
	ReporterID   string    `json:"reporterId"`
	ReportedID   string    `json:"reportedId"`
	CallID       string    `json:"callId"`
	Reason       string    `json:"reason"`
	Details      string    `json:"details"`
	BlockedUser  bool      `json:"blockedUser"`
	CreatedAt    time.Time `json:"createdAt"`
}

// FcmTokenPayload is sent by client to register FCM token for push notifications
type FcmTokenPayload struct {
	Token string `json:"token"`
}

// FcmTokenInfo stores FCM token for a user
type FcmTokenInfo struct {
	UserID    string    `json:"userId"`
	Token     string    `json:"token"`
	UpdatedAt time.Time `json:"updatedAt"`
}

