# Backroom Backend

Local development setup for Backroom backend services.

## Prerequisites

- Docker & Docker Compose
- Redis (running locally or via Docker)
- Node.js 18+ (for REST API)
- Go 1.21+ (for signaling server - later)

## Quick Start

### 1. Environment Setup

```bash
# Copy environment template
cp .env.example .env

# Edit .env with your values (especially secrets!)
# Generate a JWT secret:
openssl rand -hex 32
```

### 2. Start Postgres

```bash
# Start the database
docker-compose up -d postgres

# Check it's running
docker-compose ps

# View logs
docker-compose logs -f postgres
```

### 3. Verify Database

```bash
# Connect to Postgres
docker exec -it backroom-postgres psql -U backroom -d backroom

# List tables
\dt

# Exit
\q
```

### 4. Redis (if using Docker)

Uncomment the Redis service in `docker-compose.yml` if you want Docker-managed Redis.

Otherwise, ensure your local Redis is running on port 6379.

```bash
# Test Redis connection
redis-cli ping
# Should return: PONG
```

## Database Schema

The initial schema is in `backend/migrations/init.sql` and includes:

| Table | Description |
|-------|-------------|
| `users` | User accounts (anonymous or linked) |
| `profiles` | Extended user settings, voice anonymization prefs |
| `boundaries` | Listener topic/intensity/duration limits |
| `shares` | Share requests from sharers |
| `listener_availability` | Real-time listener availability |
| `calls` | Call records (no audio stored!) |
| `feedbacks` | Post-call feedback |
| `reports` | User reports for moderation |
| `blocks` | User-to-user blocks |
| `subscriptions` | Subscription records |
| `audit_logs` | Event audit trail |
| `crisis_resources` | Admin-editable crisis hotlines |

## Services (to be added)

| Service | Port | Status |
|---------|------|--------|
| Postgres | 5432 | ✅ Ready |
| Redis | 6379 | ✅ Ready (local) |
| coturn (TURN/STUN) | 3478, 5349 | ✅ Ready |
| REST API (NestJS) | 8080 | ✅ Ready |
| WebSocket Signaling (Go) | 8443 | ✅ Ready |

## M-Pesa Integration

Backroom uses M-Pesa (Safaricom Daraja API) for subscription payments.

See: `backend/rest-api/src/modules/mpesa/README.md` for setup guide.

Quick setup:
1. Create account at https://developer.safaricom.co.ke/
2. Get Consumer Key and Secret
3. Update `.env` with M-Pesa credentials
4. Use ngrok for local callback testing

## Commands

```bash
# Start all services
docker-compose up -d

# Start specific service
docker-compose up -d postgres
docker-compose up -d coturn
docker-compose up -d rest-api

# Stop all services
docker-compose down

# Reset database (destroys data!)
docker-compose down -v
docker-compose up -d postgres

# View logs
docker-compose logs -f [service_name]

# Test coturn is running
docker exec backroom-coturn turnadmin -l

# Access API docs
open http://localhost:8080/docs
```

## Testing TURN/STUN

1. Go to https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/
2. Add TURN server:
   - URL: `turn:localhost:3478`
   - Username: `backroom`
   - Credential: `backroom_turn_secret`
3. Click "Gather candidates"
4. Look for `relay` candidates (indicates TURN works)

## Next Steps

1. ✅ Postgres configured
2. ✅ coturn (TURN/STUN) configured
3. ✅ REST API scaffolded (NestJS + M-Pesa)
4. ✅ WebSocket signaling server (Go)
5. ✅ FCM push notifications configured
6. ⬜ Complete shares/calls/feedback modules
7. ⬜ Add observability (Prometheus/Grafana)

