package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/backroom/ws-signaling/internal/auth"
	"github.com/backroom/ws-signaling/internal/fcm"
	"github.com/backroom/ws-signaling/internal/handlers"
	"github.com/backroom/ws-signaling/internal/hub"
	"github.com/backroom/ws-signaling/internal/matching"
	redisClient "github.com/backroom/ws-signaling/internal/redis"
)

func main() {
	log.Println("🚀 Starting Backroom WebSocket Signaling Server...")

	// Load configuration
	config := loadConfig()

	// Initialize Redis
	rdb := redisClient.NewClient(config.RedisURL)
	defer rdb.Close()

	// Test Redis connection
	ctx := context.Background()
	if err := rdb.Ping(ctx).Err(); err != nil {
		log.Fatalf("❌ Failed to connect to Redis: %v", err)
	}
	log.Println("✅ Connected to Redis")

	// Initialize JWT validator
	jwtValidator := auth.NewJWTValidator(config.JWTSecret)

	// Initialize Hub (manages all WebSocket connections)
	h := hub.NewHub(rdb, config.TurnURL, config.TurnSecret)
	go h.Run()

	// Initialize FCM client for push notifications
	fcmClient, err := fcm.NewFCMClient()
	if err != nil {
		log.Printf("⚠️ Failed to initialize FCM client: %v (push notifications will be disabled)", err)
	} else {
		log.Println("✅ FCM client initialized for push notifications")
	}

	// Initialize Matching Worker
	matcher := matching.NewMatcher(rdb, h, fcmClient)
	go matcher.Run(ctx)

	// Setup HTTP routes
	mux := http.NewServeMux()

	// Health check
	mux.HandleFunc("/health", handlers.HealthHandler)

	// WebSocket endpoint
	wsHandler := handlers.NewWebSocketHandler(h, jwtValidator)
	mux.HandleFunc("/ws", wsHandler.Handle)

	// Create server
	server := &http.Server{
		Addr:         ":" + config.Port,
		Handler:      mux,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Start server in goroutine
	go func() {
		log.Printf("🌐 WebSocket server listening on port %s", config.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("❌ Server error: %v", err)
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("🛑 Shutting down server...")

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Fatalf("❌ Server forced to shutdown: %v", err)
	}

	log.Println("👋 Server stopped")
}

// Config holds server configuration
type Config struct {
	Port       string
	RedisURL   string
	JWTSecret  string
	TurnURL    string
	TurnSecret string
}

func loadConfig() *Config {
	return &Config{
		Port:       getEnv("WS_PORT", "8443"),
		RedisURL:   getEnv("REDIS_URL", "redis://localhost:6379"),
		JWTSecret:  getEnv("JWT_SECRET", "dev_jwt_secret_backroom_2026_change_in_production"),
		TurnURL:    getEnv("TURN_URL", "turn:192.168.1.160:3478"),
		TurnSecret: getEnv("TURN_SECRET", "backroom_turn_secret"),
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

