# Backroom Wireframes and Screen Flows

## Overview

This document describes every screen in detail with ASCII wireframes.
Platform: Android (Compose). Design follows 01_DESIGN_SYSTEM.md.

---

## Screen Index

1. Splash Screen
2. Onboarding (Welcome, How It Works, Safety, Permissions)
3. Home Screen
4. Sharer Flow (5 screens)
5. Listener Flow (3 screens)
6. In-Call Screen
7. Post-Call Feedback
8. Settings
9. Help and Resources
10. Subscription (Backroom Plus)

---

## 1. Splash Screen

### Purpose
Brand moment while app initializes.

### Duration
1.5 seconds max, skip if already loaded.

### Wireframe

```
┌─────────────────────────────┐
│                             │
│                             │
│                             │
│       [Backroom Logo]       │
│                             │
│      "Say what you can't."  │
│                             │
│                             │
│          [Loader]           │
│                             │
│                             │
└─────────────────────────────┘
```

### Elements
- Backroom logo: Centered, 80dp
- Tagline: Body Large, On Surface Variant
- Loader: Small circular, Backroom Purple

### Transitions
- If first launch: Go to Onboarding
- If returning user: Go to Home

---

## 2. Onboarding Flow

### 2.1 Welcome Screen

```
┌─────────────────────────────┐
│                             │
│    [Illustration: People    │
│     connected by voice]     │
│                             │
├─────────────────────────────┤
│                             │
│   Welcome to Backroom       │
│                             │
│   Pour your heart out.      │
│   Get it off your chest.    │
│   Anonymously.              │
│                             │
│   • They won't judge you    │
│   • They won't know you     │
│   • They won't remember you │
│                             │
│                             │
│   [  Get Started  ]         │
│                             │
│   ○ ○ ○ ○  (page 1 of 4)    │
└─────────────────────────────┘
```

### 2.2 How It Works

```
┌─────────────────────────────┐
│                             │
│   How It Works              │
│                             │
├─────────────────────────────┤
│                             │
│   ┌─────────────────────┐   │
│   │ 1. Share what you   │   │
│   │    need to say      │   │
│   │    Topic, weight,   │   │
│   │    a few words      │   │
│   └─────────────────────┘   │
│            ↓                │
│   ┌─────────────────────┐   │
│   │ 2. Someone chooses  │   │
│   │    to listen        │   │
│   │    They see your    │   │
│   │    words first      │   │
│   └─────────────────────┘   │
│            ↓                │
│   ┌─────────────────────┐   │
│   │ 3. Talk freely.     │   │
│   │    Stay anonymous.  │   │
│   │    Voice-only,      │   │
│   │    nothing saved    │   │
│   └─────────────────────┘   │
│                             │
│   [  Continue  ]            │
│                             │
│   ● ○ ○ ○  (page 2 of 4)    │
└─────────────────────────────┘
```

### 2.3 Safety and Privacy

```
┌─────────────────────────────┐
│                             │
│   Stay Safe. Stay Anonymous.│
│                             │
├─────────────────────────────┤
│                             │
│   [Shield Icon]             │
│                             │
│   ✓ Voice masking helps     │
│     protect your identity   │
│                             │
│   ✓ No recordings. Ever.    │
│                             │
│   ✓ Exit any call with      │
│     one tap                 │
│                             │
│   ✓ Report bad behavior     │
│     instantly               │
│                             │
│   ✓ You decide who you      │
│     talk to                 │
│                             │
│   [  I Understand  ]        │
│                             │
│   ○ ● ○ ○  (page 3 of 4)    │
└─────────────────────────────┘
```

### 2.4 Permissions

#### 2.4a Microphone Permission

```
┌─────────────────────────────┐
│                             │
│        [Mic Icon]           │
│                             │
│     Allow microphone        │
│                             │
│   This is how you'll talk   │
│   to someone. Your voice    │
│   is changed and never      │
│   saved.                    │
│                             │
│                             │
│   [  Allow Microphone  ]    │
│                             │
│   ○ ○ ○ ●  (page 4 of 4)    │
└─────────────────────────────┘
```

#### 2.4b Notifications Permission

```
┌─────────────────────────────┐
│                             │
│   ✓ Microphone allowed      │
│                             │
│        [Bell Icon]          │
│                             │
│   Allow notifications?      │
│                             │
│   We'll let you know when   │
│   someone wants to talk     │
│   to you.                   │
│                             │
│                             │
│   [  Allow Notifications  ] │
│                             │
│   [ Maybe later ]           │
│                             │
│   ○ ○ ○ ○ ●  (page 5 of 5)  │
└─────────────────────────────┘
```

#### 2.4c Location Permission

```
┌─────────────────────────────┐
│                             │
│   ✓ Microphone allowed      │
│   ✓ Notifications allowed   │
│                             │
│        [Location Icon]      │
│                             │
│   Allow location?           │
│                             │
│   This helps us avoid       │
│   matching you with         │
│   people nearby.            │
│                             │
│   (Optional)                │
│                             │
│   [  Allow Location  ]      │
│                             │
│   [ Skip ]                  │
│                             │
│   ○ ○ ○ ○ ●  (page 5 of 5)  │
└─────────────────────────────┘
```


---

## 3. Home Screen

### 3.1 Home as Sharer (Default)

```
┌─────────────────────────────┐
│ Backroom           [⚙️] [?] │
├─────────────────────────────┤
│                             │
│   ┌─────────┬─────────┐     │
│   │ SHARER  │ Listener│     │
│   │ (active)│         │     │
│   └─────────┴─────────┘     │
│                             │
│                             │
│   ┌───────────────────────┐ │
│   │                       │ │
│   │   Need to talk?       │ │
│   │                       │ │
│   │   Someone is ready    │ │
│   │   to listen.          │ │
│   │                       │ │
│   │   [  Start a Call  ]  │ │
│   │                       │ │
│   └───────────────────────┘ │
│                             │
│   ─────────────────────────│
│   Today: 1 call · 8 min     │
│   Last feedback: Helped 💚  │
│                             │
└─────────────────────────────┘
```

### 3.2 Home as Listener (Unavailable)

```
┌─────────────────────────────┐
│ Backroom           [⚙️] [?] │
├─────────────────────────────┤
│                             │
│   ┌─────────┬─────────┐     │
│   │ Sharer  │ LISTENER│     │
│   │         │ (active)│     │
│   └─────────┴─────────┘     │
│                             │
│   Available to Listen       │
│   ┌─────────────────────┐   │
│   │ ○───────────────    │   │
│   │       OFF           │   │
│   └─────────────────────┘   │
│                             │
│   ┌───────────────────────┐ │
│   │                       │ │
│   │   Ready to help?      │ │
│   │                       │ │
│   │   Turn on availability│ │
│   │   to receive calls.   │ │
│   │                       │ │
│   │   [ Set Boundaries ]  │ │
│   │                       │ │
│   └───────────────────────┘ │
│                             │
│   ─────────────────────────│
│   Listened: 5 calls total   │
│   Helped: 4 people          │
│                             │
└─────────────────────────────┘
```

### 3.3 Home as Listener (Available)

```
┌─────────────────────────────┐
│ Backroom           [⚙️] [?] │
├─────────────────────────────┤
│                             │
│   ┌─────────┬─────────┐     │
│   │ Sharer  │ LISTENER│     │
│   │         │ (active)│     │
│   └─────────┴─────────┘     │
│                             │
│   Available to Listen       │
│   ┌─────────────────────┐   │
│   │ ───────────────○    │   │
│   │       ON  🟢        │   │
│   └─────────────────────┘   │
│                             │
│   ┌───────────────────────┐ │
│   │      [Pulse Anim]     │ │
│   │                       │ │
│   │   Waiting for         │ │
│   │   someone who needs   │ │
│   │   to talk...          │ │
│   │                       │ │
│   │   You'll see a        │ │
│   │   preview first.      │ │
│   │                       │ │
│   └───────────────────────┘ │
│                             │
│   [ Edit Boundaries ]       │
│                             │
└─────────────────────────────┘
```

---

## 4. Sharer Flow

### 4.1 Select Topic

```
┌─────────────────────────────┐
│ ← Back         Start a Call │
├─────────────────────────────┤
│                             │
│   What do you want to       │
│   talk about?               │
│                             │
│   ┌───────────────────────┐ │
│   │ [Icon] Confession     │ │
│   │ Something I need to   │ │
│   │ get off my chest      │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ [Icon] Letting Out    │ │
│   │ Frustrated and need   │ │
│   │ to let it out         │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ [Icon] Advice         │ │
│   │ I need an opinion     │ │
│   │ on something          │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ [Icon] Grief          │ │
│   │ I lost someone or     │ │
│   │ something             │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ [Icon] Just Talking   │ │
│   │ No reason, just want  │ │
│   │ to talk               │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ [Icon] Something Hard │ │
│   │ Difficult to put      │ │
│   │ into words            │ │
│   └───────────────────────┘ │
│                             │
└─────────────────────────────┘
```

### 4.2 Select Tone

```
┌─────────────────────────────┐
│ ← Back         Start a Call │
├─────────────────────────────┤
│                             │
│   Topic: Grief              │
│                             │
│   How are you feeling       │
│   about this?               │
│                             │
│   ┌───────────────────────┐ │
│   │                       │ │
│   │   ○ Light             │ │
│   │   Just need to chat   │ │
│   │                       │ │
│   │   ○ Heavy             │ │
│   │   This is weighing    │ │
│   │   on me               │ │
│   │                       │ │
│   │   ○ Very Heavy        │ │
│   │   I'm really          │ │
│   │   struggling          │ │
│   │                       │ │
│   └───────────────────────┘ │
│                             │
│   This helps us find the    │
│   right listener for you.   │
│                             │
│                             │
│   [  Continue  ]            │
│                             │
└─────────────────────────────┘
```

### 4.3 Write Intent

```
┌─────────────────────────────┐
│ ← Back         Start a Call │
├─────────────────────────────┤
│                             │
│   Topic: Grief              │
│   Tone: Heavy               │
│                             │
│   In a few words, what's    │
│   on your mind?             │
│                             │
│   ┌───────────────────────┐ │
│   │                       │ │
│   │ Lost someone close,   │ │
│   │ need to talk through  │ │
│   │ the pain              │ │
│   │                       │ │
│   │              72/120   │ │
│   └───────────────────────┘ │
│                             │
│   [!] Don't use real names  │
│   or places.                │
│                             │
│   The listener will read    │
│   this first.               │
│                             │
│                             │
│   [  Continue  ]            │
│                             │
└─────────────────────────────┘
```

### 4.4 Select Duration

```
┌─────────────────────────────┐
│ ← Back         Start a Call │
├─────────────────────────────┤
│                             │
│   How long do you need?     │
│                             │
│   ┌───────────────────────┐ │
│   │                       │ │
│   │   ○  5 minutes        │ │
│   │      Quick chat       │ │
│   │                       │ │
│   │   ●  10 minutes       │ │
│   │      Enough time      │ │
│   │                       │ │
│   │   ○  15 minutes       │ │
│   │      Longer talk      │ │
│   │                       │ │
│   │   ○  30 minutes 🔒    │ │
│   │      Backroom Plus    │ │
│   │                       │ │
│   └───────────────────────┘ │
│                             │
│                             │
│   [  Review & Submit  ]     │
│                             │
└─────────────────────────────┘
```

### 4.5 Review and Submit

```
┌─────────────────────────────┐
│ ← Back         Start a Call │
├─────────────────────────────┤
│                             │
│   Before we find someone... │
│                             │
│   ┌───────────────────────┐ │
│   │                       │ │
│   │ Topic: Grief          │ │
│   │ Tone:  ●●●○○ Heavy    │ │
│   │ Time:  10 minutes     │ │
│   │                       │ │
│   │ Your message:         │ │
│   │ "Lost someone close,  │ │
│   │  need to talk through │ │
│   │  the pain"            │ │
│   │                       │ │
│   │      [ Edit ]         │ │
│   └───────────────────────┘ │
│                             │
│   Someone will read this    │
│   before they pick up.      │
│                             │
│                             │
│   [  Find a Listener  ]     │
│                             │
└─────────────────────────────┘
```

### 4.6 Waiting for Match

```
┌─────────────────────────────┐
│           Finding...        │
├─────────────────────────────┤
│                             │
│                             │
│                             │
│       [Animated Pulse]      │
│                             │
│   Finding someone for you...│
│                             │
│   This usually takes        │
│   less than a minute.       │
│                             │
│                             │
│   ─────────────────────────│
│                             │
│   Take a breath.            │
│   You're about to be heard. │
│                             │
│                             │
│                             │
│       [ Cancel ]            │
│                             │
└─────────────────────────────┘
```

---

## 5. Listener Flow

### 5.1 Set Boundaries

```
┌─────────────────────────────┐
│ ← Back        My Boundaries │
├─────────────────────────────┤
│                             │
│   Topics I'll Accept        │
│   ┌───────────────────────┐ │
│   │ ☑ Confession          │ │
│   │ ☑ Letting Out         │ │
│   │ ☑ Advice              │ │
│   │ ☑ Grief               │ │
│   │ ☑ Just Talking        │ │
│   │ ☐ Something Hard      │ │
│   └───────────────────────┘ │
│                             │
│   Emotional Intensity       │
│   ┌───────────────────────┐ │
│   │ Max I can handle:     │ │
│   │ ○ Light only          │ │
│   │ ● Up to Heavy         │ │
│   │ ○ Any (incl. Very)    │ │
│   └───────────────────────┘ │
│                             │
│   Max Call Duration         │
│   ┌───────────────────────┐ │
│   │ [Slider: 5-30 min]    │ │
│   │ Currently: 15 min     │ │
│   └───────────────────────┘ │
│                             │
│   [  Save Boundaries  ]     │
│                             │
└─────────────────────────────┘
```

### 5.2 Incoming Preview

```
┌─────────────────────────────┐
│                             │
│    Someone wants to talk    │
│                             │
├─────────────────────────────┤
│                             │
│   ┌───────────────────────┐ │
│   │ ▌                     │ │
│   │ ▌  Topic: Grief       │ │
│   │ ▌                     │ │
│   │ ▌  Tone: ●●●○○ Heavy  │ │
│   │ ▌                     │ │
│   │ ▌  Time: ~10 minutes  │ │
│   │ ▌                     │ │
│   │ ▌  Their words:       │ │
│   │ ▌  "Lost someone      │ │
│   │ ▌   close, need to    │ │
│   │ ▌   talk through      │ │
│   │ ▌   the pain"         │ │
│   │ ▌                     │ │
│   └───────────────────────┘ │
│                             │
│        ◯◯◯◯◯◯◯◯◯◯          │
│        28 seconds           │
│                             │
│   ┌─────────┐ ┌───────────┐ │
│   │  Skip   │ │  Accept   │ │
│   └─────────┘ └───────────┘ │
│                             │
│   [ Pause My Availability ] │
│                             │
└─────────────────────────────┘
```

---

## 6. In-Call Screen

```
┌─────────────────────────────┐
│                             │
│                             │
│       Listener #4829        │
│                             │
│       [Voice Wave Anim]     │
│                             │
│          08:42              │
│       remaining             │
│                             │
│                             │
│   ┌───────────────────────┐ │
│   │ Topic: Grief          │ │
│   │ "Lost someone close"  │ │
│   └───────────────────────┘ │
│                             │
│                             │
│   ┌─────┐  ┌─────┐  ┌─────┐ │
│   │ 🔇  │  │ End │  │ ⚠️  │ │
│   │Mute │  │Call │  │Exit │ │
│   └─────┘  └─────┘  └─────┘ │
│                             │
│                             │
│   You're anonymous.         │
│   Your voice is modified.   │
│                             │
└─────────────────────────────┘
```

### In-Call States

**Muted State:**
```
│   │ 🔇  │ ← Icon changes, label: "Unmute"
│   │Muted│
```

**30 Second Warning:**
```
│          00:30              │
│       ⚠️ Wrapping up        │
```

**Emergency Exit Confirmation:**
```
┌───────────────────────────┐
│                           │
│   End call immediately?   │
│                           │
│   This will also let you  │
│   report if needed.       │
│                           │
│   [ Cancel ]  [ End Now ] │
│                           │
└───────────────────────────┘
```

---

## 7. Post-Call Feedback

### 7.1 Main Feedback

```
┌─────────────────────────────┐
│                             │
│       Call Ended            │
│       10:00                 │
│                             │
├─────────────────────────────┤
│                             │
│   How did that feel?        │
│                             │
│   ┌─────┐ ┌─────┐ ┌───────┐ │
│   │ 💚  │ │ 😐  │ │  😔   │ │
│   │Help │ │Neut │ │Uncomf │ │
│   │ -ed │ │-ral │ │-table │ │
│   └─────┘ └─────┘ └───────┘ │
│                             │
│                             │
│   ─────────────────────────│
│                             │
│   [ Send Anonymous Thanks ] │
│   (optional)                │
│                             │
│   [ Report a Problem ]      │
│                             │
│   [ Done ]                  │
│                             │
└─────────────────────────────┘
```

### 7.2 Recognition Check

```
┌─────────────────────────────┐
│                             │
│   Quick Privacy Check       │
│                             │
├─────────────────────────────┤
│                             │
│   Did you recognize the     │
│   other person's voice?     │
│                             │
│   This is confidential and  │
│   helps us improve.         │
│                             │
│                             │
│   ┌───────────────────────┐ │
│   │ Yes, I recognized     │ │
│   │ them                  │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ No, they were         │ │
│   │ anonymous             │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ Prefer not to say     │ │
│   └───────────────────────┘ │
│                             │
└─────────────────────────────┘
```

### 7.3 Report Flow

```
┌─────────────────────────────┐
│ ← Back           Report     │
├─────────────────────────────┤
│                             │
│   What happened?            │
│                             │
│   ┌───────────────────────┐ │
│   │ ○ Harassment or abuse │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ ○ Preview didn't      │ │
│   │   match the call      │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ ○ Inappropriate       │ │
│   │   content             │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ ○ Made me feel unsafe │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ ○ Other               │ │
│   └───────────────────────┘ │
│                             │
│   Details (optional):       │
│   ┌───────────────────────┐ │
│   │                       │ │
│   └───────────────────────┘ │
│                             │
│   ☐ Block this person       │
│                             │
│   [  Submit Report  ]       │
│                             │
└─────────────────────────────┘
```

---

## 8. Settings

```
┌─────────────────────────────┐
│ ← Back           Settings   │
├─────────────────────────────┤
│                             │
│   PRIVACY & SAFETY          │
│   ┌───────────────────────┐ │
│   │ Voice Anonymization   │ │
│   │ Basic (mandatory)   > │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ Avoid Local Matches   │ │
│   │                  OFF  │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ Blocked Users       > │ │
│   └───────────────────────┘ │
│                             │
│   LISTENING PREFERENCES     │
│   ┌───────────────────────┐ │
│   │ My Boundaries       > │ │
│   └───────────────────────┘ │
│                             │
│   NOTIFICATIONS             │
│   ┌───────────────────────┐ │
│   │ Incoming Previews  ON │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ Tips & Reminders   ON │ │
│   └───────────────────────┘ │
│                             │
│   APP                       │
│   ┌───────────────────────┐ │
│   │ Backroom Plus       > │ │
│   └───────────────────────┘ │
│                             │
│   ACCOUNT                   │
│   ┌───────────────────────┐ │
│   │ Export My Data      > │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ Delete Account      > │ │
│   └───────────────────────┘ │
│                             │
│   Version 1.0.0             │
│                             │
└─────────────────────────────┘
```

---

## 9. Help and Resources

```
┌─────────────────────────────┐
│ ← Back              Help    │
├─────────────────────────────┤
│                             │
│   CRISIS RESOURCES          │
│                             │
│   If you're in crisis,      │
│   please reach out:         │
│                             │
│   ┌───────────────────────┐ │
│   │ 📞 Befrienders Kenya  │ │
│   │ +254-722-178-177      │ │
│   │ 24/7                  │ │
│   │              [Call]   │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ 📞 MHFA Kenya         │ │
│   │ Mental health support │ │
│   │              [Call]   │ │
│   └───────────────────────┘ │
│                             │
│   ─────────────────────────│
│                             │
│   FREQUENTLY ASKED          │
│                             │
│   ┌───────────────────────┐ │
│   │ How anonymous am I? > │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ How does voice        │ │
│   │ modification work?  > │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ What if I feel        │ │
│   │ unsafe?             > │ │
│   └───────────────────────┘ │
│   ┌───────────────────────┐ │
│   │ How do I report       │ │
│   │ someone?            > │ │
│   └───────────────────────┘ │
│                             │
└─────────────────────────────┘
```

---

## 10. Subscription (Backroom Plus)

```
┌─────────────────────────────┐
│ ← Back       Backroom Plus  │
├─────────────────────────────┤
│                             │
│   ┌───────────────────────┐ │
│   │                       │ │
│   │     Backroom Plus     │ │
│   │                       │ │
│   │   Support the         │ │
│   │   community           │ │
│   │                       │ │
│   └───────────────────────┘ │
│                             │
│   What you get:             │
│                             │
│   ✓ Longer calls (30 min)   │
│   ✓ Priority matching       │
│   ✓ Experienced listeners   │
│   ✓ Support our mission     │
│                             │
│                             │
│   ┌───────────────────────┐ │
│   │                       │ │
│   │   KES 299/month       │ │
│   │   (~$2.99 USD)        │ │
│   │                       │ │
│   │   [ Subscribe Now ]   │ │
│   │                       │ │
│   └───────────────────────┘ │
│                             │
│   Cancel anytime.           │
│   No safety features are    │
│   behind this paywall.      │
│                             │
│   [ Restore Purchase ]      │
│                             │
└─────────────────────────────┘
```

---

## Navigation Map

```
                    ┌─────────┐
                    │ Splash  │
                    └────┬────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              │
    ┌──────────┐   ┌──────────┐         │
    │Onboarding│   │   Home   │◄────────┘
    │ (4 steps)│──►│          │
    └──────────┘   └────┬─────┘
                        │
         ┌──────────────┼──────────────┐
         │              │              │
         ▼              ▼              ▼
   ┌──────────┐   ┌──────────┐   ┌──────────┐
   │  Sharer  │   │ Listener │   │ Settings │
   │   Flow   │   │   Flow   │   │          │
   └────┬─────┘   └────┬─────┘   └──────────┘
        │              │
        ▼              ▼
   ┌──────────┐   ┌──────────┐
   │ Waiting  │   │ Incoming │
   │          │   │ Preview  │
   └────┬─────┘   └────┬─────┘
        │              │
        └──────┬───────┘
               │
               ▼
         ┌──────────┐
         │ In-Call  │
         └────┬─────┘
              │
              ▼
         ┌──────────┐
         │Post-Call │
         │ Feedback │
         └────┬─────┘
              │
              ▼
         ┌──────────┐
         │   Home   │
         └──────────┘
```

---

## Transition Animations

| From | To | Animation |
|------|----|-----------|
| Splash | Onboarding | Fade |
| Splash | Home | Fade |
| Onboarding steps | Next step | Slide left |
| Home | Sharer flow | Slide up (bottom sheet) |
| Home | Settings | Slide right |
| Waiting | In-Call | Expand from center |
| In-Call | Post-Call | Fade with scale down |
| Any | Home | Slide down or fade |

---

## Error States

### No Network

```
┌───────────────────────────┐
│                           │
│   [Wifi-off icon]         │
│                           │
│   No connection           │
│                           │
│   Check your internet     │
│   and try again.          │
│                           │
│   [ Retry ]               │
│                           │
└───────────────────────────┘
```

### No Listeners Available

```
┌───────────────────────────┐
│                           │
│   No listeners right now  │
│                           │
│   Everyone's busy. We'll  │
│   notify you when someone │
│   is available.           │
│                           │
│   [ Notify Me ]  [ Edit ] │
│                           │
└───────────────────────────┘
```

### Permission Denied

```
┌───────────────────────────┐
│                           │
│   Microphone Required     │
│                           │
│   Backroom needs microphone │
│   access to make calls.   │
│                           │
│   [ Open Settings ]       │
│                           │
└───────────────────────────┘
```

---

## Empty States

### No Call History

```
│                           │
│   No calls yet            │
│                           │
│   Start your first call   │
│   or become a listener.   │
│                           │
```

### No Blocked Users

```
│                           │
│   No blocked users        │
│                           │
│   Anyone you block will   │
│   appear here.            │
│                           │
```

---

## Next Steps

1. Convert to Figma with actual visuals
2. Add real icons and illustrations
3. Create interactive prototype
4. User test with 5-10 people
5. Iterate based on feedback

