package handlers

import (
	"encoding/json"
	"log"
	"net/http"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"github.com/backroom/ws-signaling/internal/auth"
	"github.com/backroom/ws-signaling/internal/hub"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		// In production, validate origin
		return true
	},
}

// WebSocketHandler handles WebSocket connections
type WebSocketHandler struct {
	hub          *hub.Hub
	jwtValidator *auth.JWTValidator
}

// NewWebSocketHandler creates a new WebSocket handler
func NewWebSocketHandler(h *hub.Hub, jwtValidator *auth.JWTValidator) *WebSocketHandler {
	return &WebSocketHandler{
		hub:          h,
		jwtValidator: jwtValidator,
	}
}

// Handle upgrades HTTP to WebSocket and registers the client
func (h *WebSocketHandler) Handle(w http.ResponseWriter, r *http.Request) {
	// Get token from query parameter or header
	token := r.URL.Query().Get("token")
	if token == "" {
		token = r.Header.Get("Authorization")
	}

	var userID string

	// If token is provided, validate it
	if token != "" {
		claims, err := h.jwtValidator.ValidateToken(token)
		if err != nil {
			log.Printf("⚠️ Invalid token (allowing anonymous): %v", err)
			// Generate anonymous user ID instead of rejecting
			userID = "anon_" + uuid.New().String()
		} else {
			userID = claims.GetUserID()
		}
	} else {
		// No token provided - generate anonymous user ID (development mode)
		userID = "anon_" + uuid.New().String()
		log.Printf("🔓 Anonymous connection allowed (no token), userID: %s", userID)
	}

	if userID == "" {
		userID = "anon_" + uuid.New().String()
	}

	// Upgrade to WebSocket
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("❌ WebSocket upgrade failed: %v", err)
		return
	}

	// Register client with hub
	client := h.hub.RegisterClient(conn, userID)

	// Start read/write pumps
	go client.WritePump()
	go client.ReadPump()

	log.Printf("✅ WebSocket connected: %s", userID)
}

// HealthHandler returns server health status
func HealthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	response := map[string]interface{}{
		"status":    "ok",
		"service":   "ws-signaling",
		"timestamp": "",
	}

	json.NewEncoder(w).Encode(response)
}

