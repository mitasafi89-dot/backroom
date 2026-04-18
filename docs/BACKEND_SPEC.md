# Backroom — Backend Specification

Table of Contents
- Overview
- Goals & Constraints
- High-level architecture
- Services & responsibilities
- Data model (schema)
- API contracts (REST)
- WebSocket / Realtime contract
- Matching algorithm (MVP + improvements)
- Call lifecycle (step-by-step)
- Voice anonymization options
- Security & privacy
- Monitoring & observability
- Testing strategy
- Deployment & infra guidance
- SQL migration snippets
- Minimal OpenAPI summary
- Admin UI & moderation workflows
- Roadmap & prioritization

---

Overview

This document describes the backend architecture and implementation-ready details for Backroom (anonymous, voice-only peer-to-peer support app). It contains data models, REST and realtime contracts, security rules, operational guidance and sample SQL migrations to help engineers implement the server-side systems.

Goals & Constraints
- Anonymous voice-first conversations between a Sharer and a Listener.
- Minimal PII storage; avoid storing raw audio by default.
- Low-latency voice connections (prefer P2P WebRTC with STUN/TURN fallback).
- Safety & moderation: previews, reporting, safe routing for crisis content.
- Platform: Android (Compose) client; backend is cloud-hosted.

High-level architecture

Components (logical):
- Authentication service (anonymous + optional account auth)
- REST API service (profile, boundaries, shares, calls, feedback, reports)
- Signaling / Realtime server (WebSocket) for preview delivery and WebRTC signaling
- Matching workers (queue based, Redis-backed)
- TURN/STUN (coturn recommended) for NAT traversal
- Notifications (FCM) for mobile push
- Database (Postgres) for persistent state
- Cache / queue (Redis) for presence, queues, ephemeral state
- Admin & moderation UI (internal)
- Optional media processing/SFU (only if server-side processing required)
- Monitoring / logging / analytics pipeline (Prometheus, Grafana, Sentry)

Diagram (logical):

Client (Android) ⇄ HTTPS REST API (auth, CRUD, feedback)  
Client ⇄ WebSocket Signaling Server (previews, offer/answer, ICE)  
Client ⇄ Client (WebRTC P2P) [via STUN/TURN]  
Backend: Postgres + Redis + Matching Workers + coturn + Admin UI

Services & responsibilities

1. Auth service
- Issue short-lived JWT tokens (access tokens 15m, refresh tokens optional).
- Support anonymous account creation with device id and optional email linking.
- Endpoints: /auth/anonymous, /auth/refresh, /auth/link

2. REST API service
- User profile and boundaries CRUD
- Create/Cancel share requests
- Feedback and report submission
- Billing & subscription webhook handlers
- Admin endpoints for moderation

3. Signaling / WebSocket server
- Persistent connections for previews, presence, and WebRTC signaling (offer/answer/ICE)
- Channels per user (userId-based) and global listener broadcast channel
- Endpoint authentication via JWT on handshake

4. Matching service
- Maintains waiting queue of Share requests and available Listeners
- Implements matching rules (boundaries, topics, capacity, subscription priority)
- Enqueues a match action and notifies both parties via WebSocket

5. TURN/STUN (coturn)
- Provide STUN servers and authenticated TURN relay for fallback
- Short-lived credentials (e.g., HMAC username/password TTL)

6. Notifications service
- Push notifications via FCM for incoming previews when WebSocket is not connected

7. Moderation & reporting
- Store reports, flag users, escalate to human review
- Automated thresholds to disable availability or pause accounts

8. Analytics & monitoring
- Track events (share_created, matched, call_started, call_ended, feedback_submitted, report_created)
- Metrics and dashboards for SLAs and growth

9. Admin UI
- View reports, suspend users, manage crisis resources, view basic analytics

Data model (relational schema)

Design choices:
- Postgres as primary datastore.
- Redis for ephemeral state (presence, queue, rate-limits).
- Keep schemas normalized, store flexible fields in JSONB where appropriate.

Tables (concise):

users
- id UUID PRIMARY KEY
- created_at timestamptz NOT NULL DEFAULT now()
- anonymous boolean NOT NULL DEFAULT true
- device_id text NULL
- locale text NULL
- push_token text NULL
- subscription_tier text NULL
- last_seen_at timestamptz NULL
- is_suspended boolean NOT NULL DEFAULT false
- constraints: index on device_id, last_seen_at

profiles
- user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE
- display_name text NULL
- anonymization_settings jsonb DEFAULT '{}' -- e.g. {pitch:0.8,formant:-1}
- preferences jsonb DEFAULT '{}'

boundaries
- id UUID PRIMARY KEY
- user_id UUID REFERENCES users(id)
- topics text[] -- e.g. ['Grief','Advice']
- max_intensity text -- enum ('light','heavy','very_heavy')
- max_duration_minutes int
- created_at timestamptz DEFAULT now()
- indexes on user_id

shares (share_requests)
- id UUID PRIMARY KEY
- sharer_id UUID REFERENCES users(id)
- topic text
- tone text
- preview_text text -- max 240 chars
- duration_minutes int
- status text -- enum ('waiting','matched','cancelled','expired')
- created_at timestamptz DEFAULT now()
- expires_at timestamptz
- matched_at timestamptz NULL
- matched_listener_id UUID NULL
- estimated_wait_seconds int NULL
- metadata jsonb DEFAULT '{}'
- indexes on status, topic

listener_availability
- user_id UUID PRIMARY KEY REFERENCES users(id)
- is_available boolean
- capacity int -- max simultaneous calls
- current_load int
- last_toggled_at timestamptz
- boundaries_hash text -- quick match fingerprint

calls
- id UUID PRIMARY KEY
- share_id UUID REFERENCES shares(id)
- sharer_id UUID REFERENCES users(id)
- listener_id UUID REFERENCES users(id)
- start_time timestamptz NULL
- end_time timestamptz NULL
- duration_seconds int NULL
- status text -- enum('ongoing','ended','failed')
- ended_by text NULL -- 'sharer'|'listener'|'system'
- reason_code text NULL
- created_at timestamptz DEFAULT now()
- indexes on status, created_at

feedbacks
- id UUID PRIMARY KEY
- call_id UUID REFERENCES calls(id)
- by_user_id UUID REFERENCES users(id)
- rating text -- enum('helped','neutral','uncomfortable')
- text text NULL
- created_at timestamptz DEFAULT now()

reports
- id UUID PRIMARY KEY
- call_id UUID REFERENCES calls(id) -- optional
- reporter_user_id UUID REFERENCES users(id)
- reported_user_id UUID REFERENCES users(id)
- reason text
- details text NULL
- block boolean DEFAULT false
- action_taken text NULL
- created_at timestamptz DEFAULT now()

subscriptions
- user_id UUID PRIMARY KEY REFERENCES users(id)
- provider text -- 'google_play'|'stripe' etc.
- provider_subscription_id text
- tier text
- active_until timestamptz

audit_logs
- id UUID PRIMARY KEY
- event_type text
- user_id UUID NULL
- meta jsonb
- created_at timestamptz DEFAULT now()

API contracts (REST)

Base path: /api/v1
Auth: Bearer <JWT> on secure endpoints

Common response envelope
- success responses use status 200 and JSON body normally
- errors: use standard HTTP codes and body {"error":"...","code":"...","details":{}}

Auth
- POST /api/v1/auth/anonymous
  - Request: { "deviceId": string, "locale"?: string }
  - Response: { "accessToken": string (JWT), "user": { id, anonymous:true, subscription_tier } }
  - Errors: 400 invalid input, 500 server

- POST /api/v1/auth/refresh
  - Request: { "refreshToken": string }
  - Response: { "accessToken": string }

Profiles & Boundaries
- GET /api/v1/profile/me
  - Response: { profile: {...}, boundaries: {...} }

- PATCH /api/v1/profile/me
  - Request: { display_name?, anonymization_settings?, preferences? }
  - Response: 200 updated profile

- PUT /api/v1/boundaries
  - Request: { topics: string[], max_intensity: "light"|"heavy"|"very_heavy", max_duration_minutes: int }
  - Response: 200 boundaries

Sharer flow
- POST /api/v1/shares
  - Request:
```json
{
  "topic":"Grief",
  "tone":"heavy",
  "preview_text":"Lost someone close, need to talk",
  "duration_minutes":10,
  "allow_local_matches":false
}
```
  - Validations:
    - preview_text length <= 240
    - duration_minutes in [5,10,15,30] (30 requires subscription check)
  - Response:
```json
{ "share_id":"uuid", "status":"waiting", "queued_at":"2025-12-01T12:00:00Z", "estimated_wait_seconds":35 }
```
  - Errors: 429 rate limit, 403 subscription required, 400 validation

- POST /api/v1/shares/{id}/cancel
  - Cancels the share request if not matched
  - Response: 200

Listener availability
- POST /api/v1/listener/availability
  - Request: { is_available: boolean, capacity?: int }
  - Response: 200

Call lifecycle
- REST used mainly for logging; signaling for realtime connect
- POST /api/v1/calls/{callId}/end
  - Request: { ended_by: "sharer"|"listener"|"system", reason_code?:string }
  - Response: 200

Feedback & Reports
- POST /api/v1/calls/{callId}/feedback
  - Request: { rating: "helped"|"neutral"|"uncomfortable", text?:string }

- POST /api/v1/reports
  - Request: { call_id?:uuid, reporter_user_id, reported_user_id, reason, details?:string, block?:boolean }
  - Response: { report_id }

Billing
- POST /api/v1/subscriptions/checkout-session
- POST /api/v1/webhooks/stripe (secured)

WebSocket / Realtime contract

Endpoint: wss://api.backroom.example/ws
Handshake: client connects with query ?token=<JWT>

Messages are JSON and contain: { "type": "...", "payload": { ... } }

Channel model:
- Per-user channel: server routes messages by user id over the socket
- Global listener broadcast (filtered server-side) for incoming previews

Important message types (examples):

1) incoming_preview (server → listener)
{ "type":"incoming_preview", "payload": { "share_id":"uuid", "topic":"Grief", "tone":"heavy", "preview_text":"Lost someone...", "duration_minutes":10, "countdown_seconds":28 } }

2) preview_accept (listener → server)
{ "type":"preview_accept", "payload": { "share_id":"uuid" } }

3) preview_decline (listener → server)
{ "type":"preview_decline", "payload": { "share_id":"uuid" } }

4) match_made (server → both)
{ "type":"match_made", "payload": { "call_id":"uuid", "sharer_id":"uuid", "listener_id":"uuid", "turn_servers":[{url,username,credential}], "role":"listener|sharer" } }

5) webrtc/offer, webrtc/answer
{ "type":"webrtc/offer", "payload": { "call_id":"uuid", "sdp":"..." } }

6) ice
{ "type":"ice", "payload": { "call_id":"uuid", "candidate":{...} } }

7) presence_update
{ "type":"presence_update", "payload": { "user_id":"uuid", "is_available":true } }

Validation & security:
- All messages must be validated server-side (max sizes, expected fields)
- Rate-limit per socket (e.g., 50 msgs/s) and per-user actions

Matching algorithm

MVP: FIFO queue with constraint checks
- Maintain a Redis list of waiting shares ordered by created_at.
- Maintain a Redis set of available listeners (with boundaries metadata cached).
- On new share, matching worker tries to pop an available listener satisfying constraints:
  - listener.is_available == true
  - topic ∈ listener.topics
  - sharer.tone intensity <= listener.max_intensity
  - listener.current_load < listener.capacity
  - subscription priority: prefer paid listeners if sharer opted for priority match

Pseudocode (simplified):

```
function match_share(share):
  for listener in available_listeners_sorted_by_priority:
    if matches(share, listener):
      reserve(listener)
      notify_listener_preview(listener, share)
      start_preview_countdown(share, listener)
      return
  enqueue(share) // no match
```

Preview acceptance flow
- Server sends incoming_preview to listener with countdown X seconds (e.g., 30s)
- If listener accepts within countdown, server marks match and sends match_made to both
- If listener declines or countdown expires, server tries next listener

Improvements (phase 2+)
- Weighted matching score (topic match, tone tolerance, listener rating, response latency)
- Locality or timezone awareness (if opted-in)
- Machine-learning assisted routing (learned helpfulness)
- Warm pool of ‘trusted listeners’ (higher priority)

Call lifecycle (step-by-step)

1. Sharer POST /shares
2. Share stored with status=waiting
3. Matching worker finds candidate listener(s) and sends incoming_preview via WebSocket
4. Listener accepts → server creates call row, sets status=ongoing, sends match_made with TURN servers
5. Sharer & Listener exchange SDP/ICE over WebSocket
6. WebRTC P2P connection established; clients notify server (call_started)
7. Call in progress; clients send heartbeat/ping as needed
8. Call ends (either side presses End) → clients call POST /calls/{callId}/end or send ws message
9. Server marks call ended, stores duration, triggers post-call feedback flow
10. Feedback/report endpoints available to either party

Voice anonymization options

1) On-device anonymization (recommended)
- Pros: no clear audio leaves device; privacy preserved
- Cons: requires native audio DSP; CPU & battery considerations
- Android options: implement a native processing pipeline using Oboe (C++), Superpowered SDK (commercial), WebRTC audio processing + pitch-shift, or open-source DSP libraries (e.g., Rubber Band for time-stretch/pitch, SoundTouch)
- Implementation pattern: capture microphone, process buffer in real-time, feed processed audio to WebRTC track

2) Server-side anonymization (fallback)
- Route audio through SFU or media processing service and apply transformations server-side
- Pros: easier to update effect chain
- Cons: clear audio traverses server; higher cost; compliance concerns

Recommendation: build on-device proof-of-concept using WebRTC audio processing or a native audio pipeline. Only enable server-side processing with explicit policy and short retention if absolutely necessary.

Security & privacy

- All traffic must use TLS (HTTPS, WSS).
- Authenticate HTTP & WS requests using short-lived JWTs.
- Use per-session TURN credentials that expire (coturn with REST API or long-term SDP username HMAC).
- Do not store audio blobs unless explicitly opted-in and legally approved. If stored, encrypt at rest, limit retention, and audit accesses.
- Logs: no raw audio content. Mask PII in logs. Store only metadata: call ids, durations, user ids.
- Data retention: define policy (e.g., metadata 365 days, reports 3 years, audit logs 90 days). Include endpoints:
  - GET /api/v1/account/export
  - POST /api/v1/account/delete

Monitoring & observability

Events to instrument:
- share_created, share_matched, match_failed, call_started, call_ended, feedback_submitted, report_created

Metrics:
- match_rate, median_wait_seconds, avg_call_duration, drop_rate, reports_per_1000_calls, websocket_connection_count, turn_relay_ratio

Tools:
- Prometheus + Grafana for metrics
- Sentry for errors
- ELK / Loki for logs (structured JSON logs)

Alerting (examples):
- match queue length > 50 for > 5m
- 5xx errors > 1% of requests in 5m
- TURN relay ratio > 40% (cost spike)

Testing strategy

Unit tests
- Matching logic, boundary checks, helper functions

Integration tests
- WebSocket signaling handshake and message validation (mock SDP)
- REST endpoints (CRUD) with Postgres test DB

E2E / Smoke
- Simulate sharer & listener lifecycle (create share, match, exchange mock SDP, end, feedback)

Performance
- Load test matching & WebSocket (k6/jMeter)
- Latency targets: signaling RTT < 200ms, median match wait < 45s in MVP

Security Tests
- Penetration tests for WebSocket auth, rate-limit bypass

Deployment & infra guidance

Choices (practical):
- Services: Go (signaling & matching), Node.js/NestJS (REST + Admin) — or pick a single language if the team prefers.
- Data: Managed Postgres (Cloud SQL / RDS), Redis (managed), coturn on small fleet, Kubernetes (Helm) or ECS/Fargate as hosting.
- CI/CD: GitHub Actions; deploy via Helm to k8s; use blue/green or canary for signaling services to reduce disruption.

Cost considerations
- TURN relay bandwidth is the largest variable cost — track relay ratio and use autoscaling.
- Use on-demand scaling for matching workers.

SQL migration snippets

Example: users table

```sql
CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  anonymous boolean NOT NULL DEFAULT true,
  device_id text,
  locale text,
  push_token text,
  subscription_tier text,
  last_seen_at timestamptz,
  is_suspended boolean NOT NULL DEFAULT false
);
CREATE INDEX idx_users_device_id ON users(device_id);
```

Example: shares table

```sql
CREATE TABLE shares (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  sharer_id uuid REFERENCES users(id),
  topic text,
  tone text,
  preview_text text,
  duration_minutes int,
  status text,
  created_at timestamptz DEFAULT now(),
  expires_at timestamptz,
  matched_at timestamptz,
  matched_listener_id uuid,
  metadata jsonb DEFAULT '{}'
);
CREATE INDEX idx_shares_status ON shares(status);
```

Minimal OpenAPI summary

paths:
  /api/v1/auth/anonymous:
    post:
      summary: Create or return an anonymous user
      requestBody: application/json
      responses:
        '200': { description: OK }

Admin UI & moderation workflows

- Admin features:
  - View reports queue with filters (reason, severity, created_at)
  - Suspend/un-suspend users
  - View call logs (metadata only)
  - Edit crisis resource list

Moderation flow (example):
- A report is submitted -> stored with severity scoring.
- If reports_for_user > threshold within window => auto-pause availability & escalate to review.
- Human reviewer sees report, inspects context (metadata), then takes action (warn, suspend, ban).
- If immediate safety concern (explicit threats to others), escalate to legal team and preserve minimal evidence per policy.

Roadmap & prioritization

MVP (Phase 1):
1. Auth (anonymous JWT)
2. REST API for profiles, boundaries, shares
3. WebSocket signaling server for preview & SDP/ICE
4. Redis-backed matching worker (FIFO)
5. coturn for STUN/TURN
6. On-device voice-masking POC (client)
7. Call logging, feedback, reports

Phase 2:
- Weighted matching, subscription priority, admin UI, analytics dashboards
- CI/CD hardened, autoscaling rules, improved on-device transforms

Phase 3:
- ML-assisted routing & safety triage, advanced moderation tools, optional server-side processing with strict retention

Action items
1. Review this spec and confirm preferred implementation language & cloud provider.
2. Create OpenAPI spec & wire up server skeletons for Auth, REST, WebSocket.
3. Implement Redis + matching worker test harness.

---

End of BACKEND_SPEC.md

