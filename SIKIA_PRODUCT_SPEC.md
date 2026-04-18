# Backroom Product Specification (MVP → V1)

Purpose
- Designer/engineer-ready breakdown of screens, flows, states, data, events, NFRs, acceptance criteria, and release plan for Backroom, an anonymous, consent-first two-way voice app with soft preview.
- Cross-references BACKROOM_DEEP_ANALYSIS.md for voice anonymization and safety details.
- No code; platform is Android (Kotlin/Compose) for MVP.

Guiding Principles
- Consent first: Listener must opt in; Sharer previews intent.
- Anonymity by default: No personal identifiers; per-call pseudonyms.
- Safety: Strong abuse prevention; quick exits; user control.
- Minimalism: No feeds, follows, chats, or profiles.
- Local-first: Kenya MVP; English + Swahili; M-Pesa later.

---

1) Users & Roles

Personas
- Sharer (caller): Wants to say something difficult, seek listening/advice.
- Listener (receiver): Volunteers to listen within set boundaries and availability.
- Admin (Trust & Safety): Handles abuse, content policy, crisis links, bans.

Role Model
- Any authenticated user can be both Sharer and Listener.
- Role switching is on-demand via Home screen.

Availability Consent Lifecycle (Listener)
States
- NOT_AVAILABLE: Default if never opted in or explicitly toggled off.
- AVAILABLE: User opted in and meets minimum readiness (mic OK, boundaries set).
- INCOMING_PREVIEW: Incoming soft preview presented (30 sec response window).
- IN_CALL: Active call session.
- COOLDOWN: Optional auto-cooldown after heavy calls or user-triggered break.
- SUSPENDED: Shadow-banned or temporary lock due to safety issues.

Transitions
- NOT_AVAILABLE → AVAILABLE: User toggles on; required settings verified.
- AVAILABLE → INCOMING_PREVIEW: Matching selects user; preview presented.
- INCOMING_PREVIEW → AVAILABLE: Skip, timeout (30s), or unavailable.
- INCOMING_PREVIEW → IN_CALL: Accept.
- IN_CALL → COOLDOWN: Post-call logic suggests break; user accepts or system enforces after several heavy calls.
- COOLDOWN → AVAILABLE: Cooldown ends or user overrides if allowed.
- Any → SUSPENDED: Safety action.

Sharer Journey States
- IDLE → PREVIEW_DRAFT → PREVIEW_SUBMITTED → MATCHING → RINGING (listener preview) → CONNECTED → POST_CALL → IDLE
- Rematch loop if skipped/timeout.

---

2) Screens / Pages (Android/Compose)

2.1 Onboarding & Permissions
- Goals: Explain value, safety, and anonymity; capture minimal setup.
- Steps:
  1. Welcome + value prop (consent-first; anonymity; local context).
  2. Safety overview (no recording; report; helplines).
  3. Permissions:
     - Microphone (required to make/take calls).
     - Notifications (recommended for incoming previews).
     - Coarse Location (optional; for geo-filtering; can skip).
  4. Language selection (English, Swahili; system default preselected).
  5. Set initial role preference (Sharer/Listener/Both) — non-binding.
- Empty/Error states: Permission denied → explain consequences + retry; offline path → limited functionality messaging.
- Acceptance Criteria:
  - Permissions requested contextually and only when needed.
  - User can complete onboarding without location.
  - Accessibility labels provided for all elements (WCAG mobile).
- Edge Cases: Denied mic → disable calling flows with clear UI; denied notifications → degrade gracefully; device without SIM.

2.2 Home
- Components:
  - Role switcher: Sharer | Listener
  - Availability toggle: Available to Listen (on/off; only in Listener role)
  - Quick stats: Today’s calls, last sentiment, break suggestion
  - Call to Action: Start a Call (Sharer) / Set Boundaries (Listener)
- States:
  - Listener unavailable: Show encouragement + boundary quick access.
  - Listener available: Show “Incoming previews will appear here.”
- Acceptance Criteria:
  - Toggle reflects actual backend state.
  - Role switch has instant UI feedback; persists across sessions.
- Edge Cases: Attempt to enable availability with missing settings (topics/tone/language) → guided setup.

2.3 Sharer Flow
- Start Call
  - Button from Home (Sharer role).
- Soft Preview (Mandatory)
  - Topic (single-select): Confession, Venting, Advice, Grief, Just talking, Something hard to say.
  - Emotional Tone: Light, Heavy, Very heavy.
  - One-line Intent (<=120 chars). No PII; inline pre-submit moderation.
  - Language (default device language; can change).
  - Desired duration: 5, 10, 15 (MVP max), 30 (Plus).
  - Review & Submit: Summary + edit.
- Waiting/Rematching
  - State: Matching in progress; show tips; allow cancel.
  - If skipped/timeout by listener: rematch silently; rotate candidate pool.
  - Timeouts: Expose gentle wait-time estimates.
- Acceptance Criteria:
  - Cannot submit without valid preview.
  - Inline PII/risky phrase detection; user can revise.
  - Cancel returns to Home without penalty.
- Edge Cases: Excessive rematches → suggest revising preview; crisis terms → show resources; network drop → resume or cancel.

2.4 Listener Setup & Incoming Preview
- Availability Setup
  - Boundaries: Topics, Emotional intensity, Max duration, Languages.
  - Optional: Avoid local matches (geo-filter), Light topics only toggle.
- Incoming Preview Screen (when Available)
  - Shows Topic, Tone, Duration, Intent line, "Incoming on Backroom" header.
  - Actions: Accept, Skip, Pause Availability.
  - Timer: 30s; auto-skip on timeout.
- Acceptance Criteria:
  - Only compatible previews are surfaced per settings.
  - Skip has no penalty; never rematch same Sharer again for this Listener.
- Edge Cases: Back-to-back previews → rate-limit; user on call → suppress; poor network → fall back smoothly.

2.5 In-Call (Two-way, Anonymous)
- UI Elements:
  - Timer (counts down based on selected duration).
  - Mute toggle.
  - End call.
  - Emergency end + Report (one-tap safety).
  - Optional: “Advice vs Listening” indicator; simple nonverbal reactions.
  - Pseudonymous IDs: “Listener 492” / “Sharer 188” (unique per call).
- Behavioral Rules:
  - No recording; no callback; no chat; no profile sharing.
  - Voice anonymization: Basic morphing mandatory; Heavy mode if enabled (see SIKIA_DEEP_ANALYSIS.md Voice Anonymization).
- Failure Handling:
  - Drop detection; grace period to reconnect; if not, end session.
- Acceptance Criteria:
  - Latency within budget; stable timer; clean teardown.
  - Emergency end immediately terminates both sides and opens report flow.
- Edge Cases: Abuse detection → auto-end; morphing failure → kill-switch ends call; time overrun → 30s wrap-up.

2.6 Post-Call Feedback & Reports
- Feedback prompt (both sides): Helped / Neutral / Uncomfortable.
- Optional: Anonymous thank-you note.
- Safety report: Simple category picker + free-text (optional) + 1-tap block.
- Recognition check: “Did you recognize the other person?” Yes/No/Prefer not.
- Acceptance Criteria:
  - Submissions update reputation invisibly; recognized pair excluded from future matching.
  - Report routes to Trust & Safety; shadow-ban thresholds applied.
- Edge Cases: Duplicate reports; malicious use → reputation safeguards.

2.7 Settings
- Sections:
  - Privacy & Safety: Voice anonymization level (Basic/Heavy), Geo-filter toggle, Blocked users, Data export/delete.
  - Listening Boundaries: Topics, Tone ceiling, Max duration, Languages, Light-only toggle.
  - Notifications: Incoming preview, tips, reminders.
  - Language: English/Swahili.
  - Subscription: Backroom Plus summary and manage.
- Acceptance Criteria:
  - Immediate effect for boundary changes; used by matching.
  - Export/delete process is GDPR/Kenya DPA-compliant (asynchronous if needed).
- Edge Cases: Location permission revoked after enabling geo-filter → auto-disable with notice.

2.8 Subscription Paywall (Backroom Plus)
- Benefits: Longer calls (30 min), priority matching, “experienced listener” preference, “listening only” mode.
- Safe design: No pay-to-skip safety features.
- Acceptance Criteria:
  - Clear disclosure; restore purchases; trial optional.
- Edge Cases: Payment failures; partial entitlement; offline handling.

2.9 Help & Resources
- Crisis resources (Kenya MVP): Befrienders Kenya, MHFA Kenya, GBV hotlines; localized and kept current.
- FAQs: Anonymity, voice morphing limits, reporting, safety.
- Acceptance Criteria: Resources accessible without account; phone taps deep-link.

2.10 Admin Console (Outline)
- Queues: Reports, crisis keyword escalations, mismatch patterns.
- Actions: Warn, suspend, shadow-ban; view anonymized user history.
- Audit log; resource management.

---

3) System Behaviors & State Machines

3.1 Matching Constraints
- Listener must be AVAILABLE; Sharer in MATCHING.
- Filters: Topic, Tone ceiling, Language, Max duration, Geo (if enabled), Reputation thresholds, Shadow-bans.
- Scoring (tiebreakers): Listener freshness (not overloaded), compatibility score, network quality.
- Rematching rules: On skip/timeout, exclude listener from this sharer’s pool for N days; after K skips, prompt sharer to revise preview.

3.2 In-Call Rules
- IDs are per-call, random, not searchable.
- No recording (deterrence + detection); watermarking optional.
- Drop/timeout: If one side disconnects, allow 15s rejoin; otherwise end.
- Time limit enforcement with gentle wrap-up.

3.3 Reputation & Safety
- Reputation increments: Positive feedback, low report rate, stable behavior.
- Decrements: Mismatch reports (preview vs call), abuse reports, auto-detected violations.
- Actions: Warnings → cooldown → shadow-ban (silent) → device-level ban (repeat).
- Recognition handling: If either side reports recognition, permanently exclude that pair.

3.4 Voice Anonymization UX Touchpoints
- Default Basic morphing (mandatory).
- Settings allow Heavy morphing.
- Onboarding disclosure: “We modify your voice for privacy but cannot guarantee perfect anonymity.”
- Post-call: Ask if recognized; adjust future matching.
- See SIKIA_DEEP_ANALYSIS.md (Voice Anonymization) for technical detail.

---

4) Data Models (MVP-level, backend-agnostic)

PII Boundary & Retention
- No call recordings; no real names; minimal metadata.
- Retain call metadata (topic, tone, duration, timestamps) for analytics and safety; purge detailed logs after N days (e.g., 90) except safety cases.

Entities (field examples; types indicative)
- User
  - id (UUID), createdAt, locale, roles, status (active/suspended), deviceRefs[]
- Preferences
  - userId, topicsAllowed[], toneMax, maxDurationMin, languages[], geoFilterEnabled(bool), anonymizationLevel(enum), lightOnly(bool)
- Availability
  - userId, status(enum NOT_AVAILABLE/AVAILABLE/COOLDOWN), updatedAt, cooldownUntil
- CallRequest
  - id, sharerId, topic, tone, intentLine (<=120), language, desiredDurationMin, createdAt, status(enum QUEUED/MATCHED/CANCELLED/EXPIRED)
- CallSession
  - id, requestId, sharerId, listenerId, startedAt, endedAt, durationSec, endReason(enum NORMAL/TIMEOUT/DROP/EMERGENCY/ABUSE), pseudonymSharer, pseudonymListener
- Feedback
  - sessionId, userId, sentiment(enum HELPED/NEUTRAL/UNCOMFORTABLE), thanked(bool), recognized(bool/nullable), createdAt
- Report
  - id, sessionId, reporterId, accusedId, category(enum ABUSE/MISMATCH/OTHER), notes(optional), createdAt, status(enum OPEN/RESOLVED)
- Reputation
  - userId, score(int), lastUpdated, flags[]
- Device
  - deviceId(hash), userId, createdAt, lastSeen, riskFlags[]
- Subscription
  - userId, tier(enum FREE/PLUS), startAt, endAt, status, providerInfo
- CrisisResource
  - id, country, name, phone, hours, verifiedAt

---

5) Events & Analytics

Event Naming (examples)
- Onboarding: app_opened, onboarding_completed, permission_mic_granted/denied, permission_notifications_granted/denied, permission_location_skipped
- Listener: availability_on/off, boundaries_saved
- Sharer: preview_opened, preview_submitted, preview_rejected_moderation, matching_started, matching_cancelled
- Preview: incoming_preview_shown, incoming_preview_accepted/skipped/timeout
- Call: call_connected, call_dropped, call_ended_reason, emergency_end_triggered
- Feedback: feedback_submitted, report_filed, recognized_reported
- Settings: anonymization_level_changed, geo_filter_toggled
- Subscription: paywall_viewed, purchase_started/succeeded/failed

Key Metrics
- Connection rate (% previews that lead to calls)
- Average wait time (Sharer)
- Call completion rate; average call duration
- Post-call sentiment distribution
- Report rate per 100 calls; action time to resolution
- Listener burnout: heavy calls per day, cooldown usage
- DAU/MAU; L7 retention; conversion to Plus

Privacy
- No content logging; aggregate metrics; anonymized IDs; sampling where possible.

---

6) Non-Functional Requirements (NFRs)

Performance & Reliability
- End-to-end audio latency: target <200ms; morphing budget <50ms.
- Call success rate (connected within 30s): >95% on 4G/WiFi.
- App cold start <2s on mid-tier Android devices.

Security & Privacy
- DTLS-SRTP for media; no recordings; minimal PII.
- Kenya Data Protection Act compliance; clear consent; data export/delete.
- Anti-recording deterrence; watermarking optional.

Localization & Accessibility
- English + Swahili MVP; support right-to-left readiness.
- WCAG mobile: contrast, touch targets, TalkBack labels.

Battery & Network
- Opus ~24–32 kbps; 10-min call ≈ 4–5 MB per user.
- Background tasks minimized; TURN usage monitored.

---

7) Acceptance Criteria & Edge Cases (per major feature)

Onboarding & Permissions
- AC: Users can proceed without location; mic prompt only when needed.
- Edge: Deny mic → disable call features with clear UI.

Home & Availability
- AC: Toggle faithfully mirrors backend; cannot enable without bounds.
- Edge: Rapid toggle spam → debounce; network loss → optimistic UI with rollback.

Sharer Preview & Matching
- AC: Must select topic, tone, valid intent; auto-flag risky/PII; allow edit.
- Edge: 3+ rematches → suggest revision; long waits → notify and resume later.

Incoming Preview
- AC: 30s timer; Accept connects; Skip rematches; Pause turns off availability.
- Edge: Multiple previews collision → queue; already in-call → defer.

In-Call
- AC: Stable audio, timer, mute, end, emergency end; pseudonyms unique per call.
- Edge: Drop → 15s rejoin; morphing failure → kill-switch terminate; TURN fallback.

Post-Call Feedback & Reports
- AC: Submitting updates reputation; recognized → exclude pair permanently.
- Edge: Duplicate/malicious reports → rate-limit; offline queueing.

Settings
- AC: Boundary changes immediately used in matching; geo-filter requires coarse location or is auto-disabled.
- Edge: Locale change applies on next app start if required.

Subscription
- AC: Accurate entitlement gating; no safety features behind paywall.
- Edge: Purchase rollback; restore across devices.

Help/Resources
- AC: Numbers tap-to-call; content localized; updated quarterly.
- Edge: Offline → show cached essentials; country mismatch → Kenya default.

---

8) Release Plan

MVP (0–1)
- Listener availability & boundaries
- Sharer soft preview (topic, tone, intent, duration)
- Matching with filters; rematch; per-call pseudonyms
- Two-way voice with mandatory basic morphing
- Time limits; block/report; post-call feedback; recognition check
- English/Swahili; no M-Pesa

V1 (1–2)
- Heavy morphing option; geo-filter toggle
- Priority matching (Plus); longer calls
- Crisis resources module; improved moderation; cooldown automation
- Notifications polish; basic admin console; analytics dashboards

Later (2+)
- M-Pesa integration; enterprise partnerships
- Advanced AI moderation; media server upgrade if needed
- Accessibility enhancements; regional language packs

---

9) Explicitly Out of Scope (MVP/V1)
- Social feeds, follows, profiles, DMs
- Reconnect with past callers
- Text chat during calls
- Video calls

---

10) Open Policy/Design Questions
- Minimum age: 18+ MVP vs. 16+ with consent?
- Default morphing level: Basic only vs. allow user choice at start?
- Geo-filter: Default off with nudge vs. default on in small communities?
- Crisis escalation: Block heavy terms or warn + resources + allow?

References
- See SIKIA_DEEP_ANALYSIS.md — Voice anonymization, Anti-abuse, Technical architecture.

