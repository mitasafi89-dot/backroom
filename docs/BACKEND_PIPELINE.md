# Backroom — Backend Pipeline & Runbook

Table of Contents
- Overview
- Runtime components & processes
- Orchestration & deployment patterns
- Docker Compose (dev) example
- Kubernetes / Helm notes (production)
- Startup sequence & healthchecks
- Environment variables per service
- Logging & log retention
- Backups & restore procedures
- Database migrations
- CI/CD pipeline (GitHub Actions example)
- Runbooks for common incidents
- Scaling & autoscaling guidance
- Security operations & key rotation
- Local dev quickstart
- Maintenance & cron jobs
- Healthcheck endpoints and smoke tests

---

Overview

This document lists the run-time processes and operational guidance needed to run Backroom backend components in development and production. It includes a recommended `docker-compose` developer setup, healthchecks, backups, CI/CD outline, incident runbooks and scaling guidance.

Runtime components & processes

Core services (production):
- rest-api (Node.js / NestJS or Go) — handles profile, shares, feedback, subscriptions
- ws-signaling (Go recommended for low-latency) — WebSocket server for previews and WebRTC signaling
- matching-worker (Go/Python/Node) — dequeue shares and find listeners, uses Redis
- coturn (TURN/STUN) — relays audio when P2P fails
- postgres (managed)
- redis (managed)
- admin-ui (React or similar) — internal moderation
- metrics-exporter (Prometheus node_exporter)
- logging agent (Loki/Fluentd)
- optionally: media-sfu (mediasoup/Janus) — only if server-side media processing is required
- cron/worker (cleanup jobs, retention tasks)

Development-only services:
- mock-signaling (lightweight Node server to simulate matches)
- local FCM emulator (optional)

Orchestration & deployment patterns

Development: Docker Compose for quick iteration.
Production: Kubernetes with Helm charts (recommended) or ECS/Fargate for simpler ops teams.

Docker Compose (dev) example

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_USER: backroom
      POSTGRES_PASSWORD: backroom
      POSTGRES_DB: backroom
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  redis:
    image: redis:7
    ports:
      - "6379:6379"

  coturn:
    image: instrumentisto/coturn
    environment:
      - TURN_PUBLIC_IP=127.0.0.1
    ports:
      - "3478:3478"
      - "3478:3478/udp"

  rest-api:
    build: ./services/rest-api
    depends_on:
      - postgres
      - redis
    environment:
      - DATABASE_URL=postgres://backroom:backroom@postgres:5432/backroom
      - REDIS_URL=redis://redis:6379
    ports:
      - "8080:8080"

  ws-signaling:
    build: ./services/ws-signaling
    depends_on:
      - redis
      - coturn
    environment:
      - REDIS_URL=redis://redis:6379
      - TURN_URL=turn:coturn:3478
    ports:
      - "8443:8443"

  matching-worker:
    build: ./services/matching-worker
    depends_on:
      - redis
    environment:
      - REDIS_URL=redis://redis:6379

volumes:
  pgdata:
```

Kubernetes / Helm notes (production)

- Create separate namespaces: production, staging, dev
- Use HorizontalPodAutoscaler for ws-signaling & matching-worker
- Use StatefulSet for coturn (or run as Deployment with persistent storage)
- Use managed Postgres & Redis when possible for reliability
- Use NetworkPolicy to restrict access between services

Startup sequence & healthchecks

Recommended startup order:
1. Postgres (ready)
2. Redis (ready)
3. REST API (depends on DB/Redis)
4. coturn
5. ws-signaling
6. matching-worker
7. admin-ui

Healthchecks (HTTP endpoints):
- REST API: GET /health -> {status:"ok", db:true, redis:true}
- WS Signaling: GET /health -> {status:"ok", redis:true, turn:true}
- Matching Worker: GET /health -> {status:"ok", queue_len: <int>}
- coturn: use STUN probing or check uptime/logs

Environment variables per service

Common variables
- DATABASE_URL: postgres connection
- REDIS_URL: redis connection
- JWT_SECRET: secret for signing tokens (use vault)
- ENV: production|staging|development

rest-api specific
- PORT
- ADMIN_API_KEY
- STRIPE_API_KEY
- STRIPE_WEBHOOK_SECRET
- FCM_SERVER_KEY

ws-signaling specific
- PORT
- REDIS_URL
- TURN_URLS (comma separated)
- TURN_USERNAME_SECRET (HMAC secret)

matching-worker
- REDIS_URL
- WORKER_CONCURRENCY

coturn
- REALM
- TURN_USER (if static; prefer TURN REST credentials)
- LONG_TERM_SECRET (for HMAC)

Secrets & recommendations
- Store secrets in a secrets manager (AWS Secrets Manager, GCP Secret Manager, Vault)
- Do not hardcode in repo; use environment variables or secrets injection by orchestration

Logging & log retention

- Structured JSON logs with fields: timestamp, service, level, msg, user_id (masked), call_id, request_id
- Retain logs for 90 days for operational troubleshooting; longer for reports & legal holds
- Use log levels: DEBUG (dev), INFO (prod), WARN, ERROR, CRITICAL

Backups & restore procedures

Postgres
- Daily full logical backups (pg_dump) and hourly WAL backups with point-in-time recovery (if supported)
- Example backup command (pg_dump):

```bash
PGPASSWORD=$PG_PASS pg_dump -h $PG_HOST -U $PG_USER -Fc $PG_DB -f backroom-$(date +%F).dump
```

Restore

```bash
pg_restore -h $HOST -U $USER -d $DB backroom-2025-12-01.dump
```

Redis
- Snapshot RDB backups (configured in redis.conf) and/or AOF for persistence
- Export snapshots regularly and store in S3-compatible storage

Database migrations

- Use a migration tool (Flyway, Liquibase, or node-pg-migrate) depending on server language
- Migration workflow: migrations checked into repo, executed during deployment as pre-deploy hook

CI/CD pipeline (GitHub Actions example)

1. On push to main: run linters and unit tests
2. Build docker images (rest-api, ws-signaling) and push to registry
3. Run integration tests against a preview environment
4. Deploy to staging (helm upgrade)
5. On manual approval, deploy to production

Sample workflow outline (conceptual):

- jobs:
  - test
  - build
  - push
  - deploy

Runbooks for common incidents

1) High WebSocket latency / failed signals
Symptoms: high RT T between client and signaling, or many failed connections
Steps:
- Check ws-signaling pods' CPU/memory; increase replicas if CPU high
- Check network latencies between client and pods (dig, ping from region)
- Verify Redis latency (slow commands) and connectivity
- Check logs for auth failures (JWT expiry)
- Restart ws-signaling pods if needed

2) TURN exhaustion / high relay ratio
Symptoms: TURN relays % high and bandwidth costs spike
Steps:
- Query coturn stats (if enabled) for concurrent relays
- Add more coturn nodes, scale up bandwidth
- Analyze why P2P failed (common NAT types); consider optimizing STUN/TURN selection
- Consider client-side ICE optimization

3) Matching backlog
Symptoms: Redis queue length increases, shares not getting matched
Steps:
- Check matching-worker process health and logs
- Increase worker concurrency or replicas
- Inspect listener availability set size
- Evaluate matching filters that cause low matchability (too strict boundaries)

4) High report rate
Symptoms: spike in reports
Steps:
- Pause auto-matching temporarily
- Pull top reported user ids and disable availability
- Notify moderation team; create incident ticket

Scaling & autoscaling guidance

- ws-signaling scale: HPA on CPU and websocket_connection_count per pod
- matching-worker: scale on Redis queue length
- coturn: scale horizontally; monitor connection count
- Postgres: vertical scale and read replicas for analytics

Security operations & key rotation

- Rotate JWT secret and TURN HMAC secret every 90 days
- Revoke old refresh tokens on major key rotation
- Maintain audit log of secret access
- Responding to suspected data breach: rotate secrets, disable affected services, preserve logs, notify legal per policy

Local dev quickstart

1. Clone repo
2. Copy `.env.example` to `.env` and fill in dev values
3. Start dev stack

```bash
docker-compose up --build
```

4. Run migrations

```bash
# example using node-pg-migrate
npm run migrate:up
```

5. Start Android emulator and point app to http://10.0.2.2:8080 (or host IP)

Maintenance & cron jobs

- cleanup: delete stale shares older than 24 hours
- retention: purge old audit_logs older than retention window
- rotate-turn-credentials: cron job to refresh TURN long-term secret
- daily-report: run daily metrics aggregation job

Healthcheck endpoints and smoke tests

- REST API: GET /health
- WS: GET /health
- DB: SELECT 1

Smoke test (curl):

```bash
curl -sS https://api.staging.backroom.example/api/v1/health | jq
```

---

End of BACKEND_PIPELINE.md

