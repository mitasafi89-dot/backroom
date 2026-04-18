# Backroom Documentation Index

## Overview

This folder contains comprehensive documentation for building Backroom, the anonymous voice support app.

**Last Updated:** January 2026

---

## Document Index

| # | Document | Description | Lines |
|---|----------|-------------|-------|
| 01 | [Design System](01_DESIGN_SYSTEM.md) | Colors, typography, spacing, components | ~450 |
| 02 | [Wireframes](02_WIREFRAMES.md) | ASCII wireframes for all screens | ~700 |
| 03 | [Backend Architecture](03_BACKEND_ARCHITECTURE.md) | Firebase, Firestore schema, Cloud Functions, signaling | ~650 |
| 04 | [Authentication](04_AUTHENTICATION.md) | Anonymous + phone auth flows, security rules | ~500 |
| 05 | [Push Notifications](05_PUSH_NOTIFICATIONS.md) | FCM setup, notification types, handling | ~500 |
| 06 | [Offline Handling](06_OFFLINE_HANDLING.md) | Network states, offline scenarios, sync | ~500 |
| 07 | [Testing Strategy](07_TESTING_STRATEGY.md) | Unit, integration, UI, E2E, beta plan | ~500 |
| 08 | [Legal Documents](08_LEGAL_DOCUMENTS.md) | TOS, Privacy Policy, Community Guidelines | ~400 |
| 09 | [Crisis Resources](09_CRISIS_RESOURCES.md) | Kenya resources, detection, management | ~450 |

**Total:** ~4,650 lines of documentation

---

## Related Documents (Root Level)

| Document | Description |
|----------|-------------|
| [BACKROOM_DEEP_ANALYSIS.md](../BACKROOM_DEEP_ANALYSIS.md) | Original deep analysis, voice anonymization, psychology |
| [BACKROOM_PRODUCT_SPEC.md](../BACKROOM_PRODUCT_SPEC.md) | Product specification, flows, data models |

---

## Quick Reference

### Tech Stack (MVP)

| Layer | Technology |
|-------|------------|
| Frontend | Kotlin + Jetpack Compose |
| Backend | Firebase (Auth, Firestore, Functions) |
| Voice | WebRTC + SoundTouch (voice morphing) |
| Signaling | Firebase Realtime Database |
| Push | Firebase Cloud Messaging (FCM) |
| Media Fallback | TURN server (Twilio) |
| Moderation | Perspective API |
| Analytics | Firebase Analytics |
| Crash Reporting | Firebase Crashlytics |

### Key Decisions Made

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Minimum age | 18+ | Legal safety |
| Default morphing | Basic mandatory | Balance quality + anonymity |
| Geo-filter | Opt-in with nudge | Respect privacy |
| Crisis handling | Warn + resources + allow | Don't reject people in crisis |
| Auth (free) | Anonymous | Zero friction |
| Auth (Plus) | Phone | Account recovery + safety |

### MVP Feature Scope

**Included:**
- Anonymous auth
- Sharer preview flow (topic, tone, intent, duration)
- Listener availability + boundaries
- Matching with filters
- Basic voice morphing
- Two-way voice calls
- Time limits
- Post-call feedback
- Block/report
- Crisis resources
- Settings

**Not Included (MVP):**
- Heavy voice morphing (V1)
- Geo-filter toggle (V1)
- M-Pesa payments (V1)
- Advanced moderation (V1)
- Web version (Later)
- iOS version (Later)

---

## Development Phases

### Phase 1: Foundation (Week 1-2)
- [ ] Project setup (Gradle, dependencies)
- [ ] Firebase project configuration
- [ ] Design system implementation (Color.kt, Type.kt, Theme.kt)
- [ ] Authentication flow (anonymous)
- [ ] Basic navigation

### Phase 2: Core Flows (Week 3-4)
- [ ] Home screen (role switcher)
- [ ] Sharer preview flow (5 screens)
- [ ] Listener setup (boundaries)
- [ ] Firestore schema implementation
- [ ] Matching Cloud Function

### Phase 3: Voice (Week 5-6)
- [ ] WebRTC integration
- [ ] Signaling implementation
- [ ] Voice morphing (SoundTouch)
- [ ] In-call screen
- [ ] Call timer and controls

### Phase 4: Safety & Polish (Week 7-8)
- [ ] Post-call feedback
- [ ] Report flow
- [ ] Crisis resources
- [ ] Push notifications
- [ ] Offline handling
- [ ] Error states

### Phase 5: Testing (Week 9-10)
- [ ] Unit tests
- [ ] Integration tests
- [ ] UI tests
- [ ] Internal testing
- [ ] Bug fixes

### Phase 6: Beta (Week 11-12)
- [ ] Friends & family beta
- [ ] University beta
- [ ] Iterate based on feedback
- [ ] Prepare for launch

---

## Reading Order

For new team members:

1. **Start with:** SIKIA_DEEP_ANALYSIS.md (understand the vision)
2. **Then:** SIKIA_PRODUCT_SPEC.md (understand the product)
3. **Design:** 01_DESIGN_SYSTEM.md → 02_WIREFRAMES.md
4. **Backend:** 03_BACKEND_ARCHITECTURE.md → 04_AUTHENTICATION.md
5. **Features:** 05_PUSH_NOTIFICATIONS.md → 06_OFFLINE_HANDLING.md
6. **Safety:** 08_LEGAL_DOCUMENTS.md → 09_CRISIS_RESOURCES.md
7. **Quality:** 07_TESTING_STRATEGY.md

---

## Contributing to Docs

When updating documentation:

1. Update the relevant document
2. Update the "Last Updated" date
3. If adding new documents, update this index
4. Keep formatting consistent (Markdown)
5. Use code blocks for technical content
6. Use tables for structured information
7. Include practical examples

---

## Questions?

If something is unclear or missing, add it to the documentation. These docs should be the single source of truth for building Backroom.

