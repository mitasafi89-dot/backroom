# Backroom WebSocket Signaling Server

Real-time WebSocket server for Backroom. Handles:
- Preview delivery to listeners
- WebRTC signaling (SDP/ICE exchange)
- Presence & availability management
- Match notifications

## Architecture

```
┌─────────────┐         ┌─────────────────┐         ┌─────────────┐
│   Android   │◄───────►│  WS-Signaling   │◄───────►│    Redis    │
│    Client   │   WS    │   (Go Server)   │         │  (Queues)   │
└─────────────┘         └─────────────────┘         └─────────────┘
                               │
                               ▼
                        ┌─────────────┐
                        │   coturn    │
                        │ (TURN/STUN) │
                        └─────────────┘
```

## Message Types

### Client → Server

| Type | Description |
|------|-------------|
| `ping` | Keep-alive ping |
| `availability` | Listener toggles availability |
| `preview_accept` | Listener accepts a preview |
| `preview_decline` | Listener declines a preview |
| `webrtc/offer` | WebRTC SDP offer |
| `webrtc/answer` | WebRTC SDP answer |
| `ice` | ICE candidate |
| `end_call` | End current call |

### Server → Client

| Type | Description |
|------|-------------|
| `pong` | Response to ping |
| `incoming_preview` | New share preview for listener |
| `match_made` | Match confirmed, start call |
| `call_ended` | Call has ended |
| `error` | Error message |

## Message Format

All messages are JSON with this structure:

```json
{
  "type": "message_type",
  "payload": { ... }
}
```

### Example: Incoming Preview

```json
{
  "type": "incoming_preview",
  "payload": {
    "share_id": "uuid",
    "topic": "Grief",
    "tone": "heavy",
    "preview_text": "Lost someone close, need to talk",
    "duration_minutes": 10,
    "countdown_seconds": 30
  }
}
```

### Example: Match Made

```json
{
  "type": "match_made",
  "payload": {
    "call_id": "uuid",
    "sharer_id": "uuid",
    "listener_id": "uuid",
    "role": "listener",
    "turn_servers": [
      {
        "urls": ["turn:server.com:3478"],
        "username": "timestamp:userid",
        "credential": "hmac_credential"
      }
    ]
  }
}
```

### Example: WebRTC Offer

```json
{
  "type": "webrtc/offer",
  "payload": {
    "call_id": "uuid",
    "sdp": "v=0\r\no=- ..."
  }
}
```

## Connection Flow

1. **Connect**: Client connects with JWT token
   ```
   ws://localhost:8443/ws?token=<jwt>
   ```

2. **Authenticate**: Server validates JWT and registers client

3. **Availability** (Listener): Toggle availability
   ```json
   { "type": "availability", "payload": { "is_available": true } }
   ```

4. **Receive Preview** (Listener): Server sends preview when share matches

5. **Accept/Decline** (Listener):
   ```json
   { "type": "preview_accept", "payload": { "share_id": "uuid" } }
   ```

6. **Match Made**: Both parties receive match notification

7. **WebRTC Signaling**: Exchange SDP offer/answer and ICE candidates

8. **Call Connected**: Audio flows peer-to-peer

## Running Locally

### Prerequisites
- Go 1.21+
- Redis running on localhost:6379

### Run directly
```bash
cd backend/ws-signaling
go mod download
go run ./cmd/server
```

### Run with Docker
```bash
docker-compose up -d ws-signaling
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `WS_PORT` | `8443` | WebSocket server port |
| `REDIS_URL` | `redis://localhost:6379` | Redis connection URL |
| `JWT_SECRET` | (required) | JWT signing secret (must match REST API) |
| `TURN_URL` | `turn:localhost:3478` | TURN server URL |
| `TURN_SECRET` | (required) | TURN credential secret |

## Testing

### Using websocat (CLI WebSocket client)

```bash
# Install websocat
cargo install websocat

# Connect (replace TOKEN with valid JWT)
websocat "ws://localhost:8443/ws?token=TOKEN"

# Send ping
{"type":"ping"}

# Toggle availability
{"type":"availability","payload":{"is_available":true}}
```

### Using Node.js

```javascript
const WebSocket = require('ws');

const ws = new WebSocket('ws://localhost:8443/ws?token=YOUR_JWT');

ws.on('open', () => {
  console.log('Connected');
  ws.send(JSON.stringify({ type: 'ping' }));
});

ws.on('message', (data) => {
  console.log('Received:', JSON.parse(data));
});
```

## Health Check

```bash
curl http://localhost:8443/health
```

Response:
```json
{
  "status": "ok",
  "service": "ws-signaling"
}
```

## Project Structure

```
ws-signaling/
├── cmd/
│   └── server/
│       └── main.go          # Entry point
├── internal/
│   ├── auth/
│   │   └── jwt.go           # JWT validation
│   ├── handlers/
│   │   └── websocket.go     # HTTP/WS handlers
│   ├── hub/
│   │   └── hub.go           # Connection manager
│   ├── matching/
│   │   └── matcher.go       # Matching worker
│   ├── models/
│   │   └── messages.go      # Message types
│   └── redis/
│       └── client.go        # Redis client
├── Dockerfile
├── go.mod
└── README.md
```

## Next Steps

- [ ] Implement full TURN credential generation
- [ ] Add listener boundary matching
- [ ] Implement call timeout handling
- [ ] Add metrics (Prometheus)
- [ ] Add distributed scaling (Redis pub/sub across instances)

