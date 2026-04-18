# Backroom — Tools, Libraries and Infrastructure Choices

Table of contents
- Purpose
- Recommended MVP stack (short summary)
- Core infrastructure and service tools
  - Authentication & Identity
  - Web framework / REST API
  - Realtime / WebSocket / Signaling
  - WebRTC / Media / TURN/STUN
  - Database
  - Cache & queues
  - Matching worker / background jobs
  - Billing & subscriptions
  - Push notifications
  - Observability: metrics, logs, tracing, error tracking
  - CI/CD & container registry
  - Orchestration & hosting
  - Secrets management & config
  - Database migrations
  - Testing & load tools
  - Developer tooling & local dev
  - Optional: media processing / SFU
- Cost & license notes (free vs paid)
- Suggested configuration / quick start notes
- Action items / next steps

Purpose

This document lists and explains the tools and libraries we should configure for the Backroom backend. For each tool or category I explain: why we need it, recommended options, free/open-source alternatives, pros and cons, and minimal configuration guidance for our architecture (anonymous, voice-first, WebRTC-based mobile app).

Recommended MVP stack (short)
- Language/runtime: Go for signaling & matching; Node.js / NestJS for REST & admin
- Database: Postgres (managed)
- Cache/queue: Redis
- Signaling/Realtime: WebSocket server (Go websocket library or socket.io) + JWT auth
- TURN/STUN: coturn (self-hosted) + ephemeral credentials
- Media: P2P WebRTC with client-side voice masking (Oboe / Superpowered / WebRTC audio processing)
- Observability: Prometheus + Grafana, Sentry, Loki/Fluentd
- CI/CD: GitHub Actions; Docker images to registry
- Hosting: Kubernetes (Helm) or ECS/Fargate if simpler ops
- Billing: M-Pesa (Safaricom Daraja API) for Kenya market

Core infrastructure and service tools

Authentication & Identity

Why: secure API and WebSocket access, map ephemeral anonymous users to records, manage refresh & subscription claims.

Options & recommendations:
- Auth libraries:
  - Node.js: jsonwebtoken / passport-jwt / NestJS built-in guards
  - Go: golang-jwt/jwt, auth middleware libraries
- Identity providers (optional): Firebase Auth (easy, free tier), Auth0 (managed but paid), custom anonymous JWT issuance.

Free alternatives & tradeoffs:
- Firebase Auth (free tier) — easy anonymous sign-in, integrates with FCM. Downside: vendor lock-in and less flexible server-side claims logic.
- Custom JWT issuance (recommended MVP): minimal code, full control. Use short-lived access tokens and optional refresh token flow.

Justification:
- Anonymous flows are simple; custom JWT with device id and minimal profile claims reduces complexity and cost. Use Firebase for faster prototyping if team prefers.

Web framework / REST API

Why: expose profile, shares, feedback, reports, billing endpoints.

Options:
- Node.js (Express / NestJS)
  - NestJS gives structure, DI, decorators, integrates easily with TypeORM/Prisma
- Go (Gin, Echo, Fiber)
  - Lower memory, fast, easy to deploy; good for high-concurrency workloads

Free alternatives:
- All above are open-source. Use Node.js for rapid development; Go for lower-latency and simpler binary distribution.

Justification:
- For REST (admin, billing, profile), choose the language your backend team prefers. A split approach (Node.js REST, Go signaling) is common and pragmatic.

Realtime / WebSocket / Signaling

Why: deliver incoming previews to listeners and exchange WebRTC SDP/ICE between peers.

Options:
- Libraries:
  - Node.js: ws (minimal), socket.io (feature-rich), uWebSockets.js (high-performance, but advanced)
  - Go: gorilla/websocket for simple use; nhooyr/websocket or fasthttp/websocket for production
- Message routing and scaling:
  - Use Redis Pub/Sub to share socket events between multiple signaling instances

Free alternatives:
- All libraries listed are OSS. For scaling, Redis (OSS) is adequate.

Justification:
- Use a lightweight WebSocket server written in Go for low-latency. Persist socket->user mapping in Redis so multiple instances can route messages.

WebRTC / Media / TURN/STUN

Why: real-time voice between peers. TURN required when P2P can't be established.

Components:
- WebRTC clients: Android's WebRTC library (native), or use a wrapper that supports audio tracks.
- TURN/STUN server: coturn (open-source) — recommended.
- SFU/MCU (optional): mediasoup (Node), Janus, Jitsi. Use only if server-side routing/processing is required.

Free alternatives:
- coturn (OSS) — free, production-ready
- mediasoup (OSS) — free, but operational complexity higher

Justification:
- coturn is the de-facto free TURN server. P2P minimizes server bandwidth costs. Add SFU only if feature requires server-side mix or recording.

Database

Why: persistent storage for users, shares, calls, feedback, reports.

Options:
- Postgres (recommended) — ACID, JSONB, indexes, reliable. Use managed Postgres in cloud (RDS, Cloud SQL) for production.
- Alternatives: MySQL, CockroachDB (for geo), or document stores (MongoDB) if you prefer schema flexibility.

Free alternatives:
- Postgres is OSS; you can self-host.

Justification:
- Postgres provides stable relational semantics and JSONB for flexible metadata. Use connection pooling (pgbouncer) for scaling.

Cache & queues

Why: presence, fast lookups, queues for matching, rate-limiting, ephemeral state.

Options:
- Redis (recommended) — sets, lists, sorted sets, pub/sub. Use it for match queue and presence.
- Alternative: RabbitMQ / Kafka for heavier messaging workloads.

Free alternatives:
- Redis OSS or managed Redis (paid). RabbitMQ OSS.

Justification:
- Redis's data types (sorted set, list) are perfect for queues and presence and fast lookups; it's simple to operate for MVP.

Matching worker / background jobs

Why: pair sharers and listeners, run background tasks (purge old shares, compute metrics).

Options:
- Language: Go workers for speed, Node.js workers for easier shared code.
- Job frameworks: BullMQ (Node.js, Redis-backed), Sidekiq (Ruby), or custom workers using Redis lists.

Free alternatives:
- BullMQ is open-source; you can implement custom lightweight workers with Redis LPUSH/BRPOP.

Justification:
- For deterministic matching logic, a dedicated worker reading from a Redis queue is sufficient and simple.

Billing & subscriptions

Why: accept payments for premium features (longer durations, priority matching).

Options:
- M-Pesa (Safaricom Daraja API) — recommended for Kenya market. STK Push for seamless mobile payments.
- Google Play Billing for in-app purchases (Android) — alternative for international users.

Free alternatives:
- M-Pesa has no monthly fee; Safaricom charges transaction fees (typically 1-2%).

Justification:
- M-Pesa is the dominant mobile payment platform in Kenya with 90%+ market penetration. STK Push provides excellent UX (payment prompt directly on user's phone). Server handles callbacks and activates subscriptions.

Push notifications

Why: notify users of incoming previews when WebSocket isn't connected.

Options:
- FCM (Firebase Cloud Messaging) — recommended for Android.
- Alternatives: OneSignal (free tier), Amazon SNS.

Free alternatives:
- FCM is free and integrates with Firebase Auth if used.

Justification:
- FCM is standard for Android push; use it in combination with WebSocket fallback.

Observability: metrics, logs, tracing, error tracking

Why: monitor health, errors, performance, and enable troubleshooting.

Options & recommendations:
- Metrics: Prometheus (OSS) + Grafana (dashboard)
- Logs: Loki / Elasticsearch + Fluentd / Vector
- Tracing: OpenTelemetry + Jaeger
- Error tracking: Sentry (free tier available)

Free alternatives:
- Prometheus/Grafana/Loki/OpenTelemetry are open-source and free to self-host. Sentry has a free tier.

Justification:
- Use OSS stack for fine-grained control. Managed versions are convenient but costlier.

CI/CD & container registry

Why: build/test/deploy pipeline for reliability.

Options:
- GitHub Actions (recommended) — first-class integration with GitHub, free for OSS and generous for private repositories.
- Alternatives: GitLab CI (free CE), Jenkins (self-hosted), CircleCI.
- Container registry: GitHub Container Registry (ghcr), Docker Hub, or ECR (AWS)

Free alternatives:
- GitHub Actions free tier; GitLab CI CE.

Justification:
- GitHub Actions provides easy pipeline and integration. Push built images to ghcr or cloud provider registry.

Orchestration & hosting

Why: run services reliably, scale, and manage network.

Options:
- Kubernetes (recommended for production) with Helm charts
- Alternatives: AWS ECS/Fargate (simpler), Docker Compose (dev only), Heroku (small apps)

Free alternatives:
- k3s for lightweight Kubernetes in small clusters; DigitalOcean offers cheap managed k8s; you can self-host.

Justification:
- Kubernetes gives fine-grained control for scaling WebSocket & TURN services. ECS is a reasonable managed alternative.

Secrets management & config

Why: store keys & secrets securely.

Options:
- HashiCorp Vault (self-host), AWS Secrets Manager, GCP Secret Manager

Free alternatives:
- Vault OSS available; or store secrets in Kubernetes Secrets with caution.

Justification:
- Use a managed secrets manager when in the cloud; Vault for multi-cloud or self-hosted.

Database migrations

Why: manage schema changes consistently.

Options:
- Flyway, Liquibase, node-pg-migrate, Goose (Go), sqitch

Free alternatives:
- All listed are OSS. Choose one consistent with language; Flyway works cross-platform.

Justification:
- A migration tool is essential to avoid schema drift and to support rolling deployments.

Testing & load tools

Why: validate correctness and capacity.

Options:
- Unit tests: Jest (Node), Go testing package
- Integration: Postman/Newman, Supertest (Node)
- Load testing: k6 (OSS), JMeter (OSS), Gatling
- WebRTC testing: use Pion (Go) based test harness or headless browsers to emulate SDP flows

Free alternatives:
- All listed are Open Source.

Justification:
- Use k6 for load testing and Go test/Jest for unit tests; simulate WebRTC signaling in integration tests.

Developer tooling & local dev

Why: make setup reproducible for developers.

Tools & recommendations:
- Docker & docker-compose for local stacks
- make or npm scripts for common tasks
- env files (.env.example) + sample data

Optional media processing / SFU

Why: needed only if server-side routing/mixing/recording is required.

Options:
- mediasoup (Node.js), Janus, Jitsi, Kurento

Free alternatives:
- mediasoup and Janus are OSS but require operational expertise.

Justification:
- Defer SFU to later phases. P2P for MVP reduces infrastructure and cost.

Cost & license notes (free vs paid)

- coturn: MIT/BSD-style license (free)
- Postgres & Redis: OSS (free to self-host); managed versions are paid
- M-Pesa: no monthly fee, per-transaction charges (typically 1-2%)
- Sentry/Grafana Cloud: free tiers available; paid for higher volume
- Superpowered SDK: commercial license (not free) — evaluate for audio processing needs

Suggested configuration / quick start notes

1) MVP choices
- REST API: Node.js + NestJS
- Realtime: Go ws-signaling server using nhooyr or gorilla/websocket
- DB: Postgres (managed for prod)
- Queue/Cache: Redis
- TURN: coturn
- CI: GitHub Actions
- Metrics: Prometheus + Grafana; errors: Sentry

2) Minimal secrets to provision
- JWT_SECRET
- DATABASE_URL
- REDIS_URL
- MPESA_CONSUMER_KEY
- MPESA_CONSUMER_SECRET
- MPESA_PASSKEY
- FCM_SERVER_KEY
- TURN_LONG_TERM_SECRET or coturn credentials

3) Example dev commands (Docker Compose)

```bash
# start dev stack
docker-compose up --build -d

# run migrations (example using node-pg-migrate)
npm run migrate:up
```

Action items / next steps

1. Choose primary languages for REST vs signaling (if you prefer a single language I'll adapt recommendations).
2. Decide cloud provider (AWS / GCP / Azure) for managed services and provider-specific tooling.
3. I can create a `docs/TOOLS.md` file in the repo (this was created). Next I can scaffold the minimal dev Docker Compose and a simple `services/ws-signaling/` skeleton with healthcheck and a JWT-authenticated WebSocket handshake — which would let front-end folks test signaling locally.

If you'd like, I will scaffold the signaling server next (Go) with: JWT handshake, health endpoint, basic user->socket routing using Redis, and unit tests. Or I can produce a short `terraform` / `helm` starter manifest for infra.

---

End of TOOLS.md

