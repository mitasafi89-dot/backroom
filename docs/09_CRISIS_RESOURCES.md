# Backroom Crisis Resource Management

## Overview

Backroom provides crisis resources to users who may need professional help. This document defines how we manage, display, and maintain these resources.

---

## Resource Categories

| Category | Description | Priority |
|----------|-------------|----------|
| Suicide Prevention | Immediate risk of self-harm | Highest |
| Mental Health | General mental health support | High |
| Gender-Based Violence | Domestic abuse, sexual violence | High |
| Substance Abuse | Drug and alcohol addiction | Medium |
| Youth Support | Specific to young adults | Medium |
| General Counseling | Non-emergency support | Standard |

---

## Kenya Crisis Resources (MVP)

### Tier 1: Primary Resources (Always Visible)

| Organization | Phone | Hours | Languages | Category |
|--------------|-------|-------|-----------|----------|
| Befrienders Kenya | +254-722-178-177 | 24/7 | EN, SW | Suicide Prevention |
| MHFA Kenya | +254-722-178-177 | Business hours | EN, SW | Mental Health |
| Kenya Red Cross | 1199 | 24/7 | EN, SW | Emergency |

### Tier 2: Specialized Resources

| Organization | Phone | Hours | Languages | Category |
|--------------|-------|-------|-----------|----------|
| FIDA Kenya | +254-20-232-4422 | Business hours | EN, SW | GBV, Legal |
| Childline Kenya | 116 | 24/7 | EN, SW | Youth |
| NACADA | 1192 | Business hours | EN, SW | Substance Abuse |
| Gender Violence Recovery Centre | +254-20-273-1744 | 24/7 | EN, SW | GBV |

### Tier 3: Online Resources

| Resource | URL | Type |
|----------|-----|------|
| Kenya Mental Health Resources | mentalhealth.go.ke | Government |
| MHFA Kenya | mhfakenya.com | NGO |
| Befrienders Worldwide | befrienders.org | International |

---

## Data Model

```javascript
// Firestore: crisisResources collection
{
  id: string,
  name: string,                    // "Befrienders Kenya"
  phone: string,                   // "+254722178177"
  alternatePhone: string | null,   // Backup number
  
  country: string,                 // "KE"
  region: string | null,           // "Nairobi" or null for nationwide
  
  category: string,                // "suicide_prevention" | "mental_health" | etc.
  tier: number,                    // 1 = primary, 2 = specialized, 3 = online
  
  description: {
    en: string,
    sw: string
  },
  
  hours: string,                   // "24/7" | "Mon-Fri 8am-5pm"
  languages: string[],             // ["en", "sw"]
  
  url: string | null,
  email: string | null,
  
  isActive: boolean,
  verifiedAt: timestamp,           // Last verification date
  verifiedBy: string,              // Admin who verified
  
  displayOrder: number,            // For sorting
  
  metadata: {
    createdAt: timestamp,
    updatedAt: timestamp,
    notes: string                  // Internal notes
  }
}
```

---

## Display Logic

### When to Show Resources

| Trigger | Action | Resources Shown |
|---------|--------|-----------------|
| User taps "Help" | Show all Tier 1 + relevant Tier 2 | Based on language |
| Crisis keyword in preview | Show modal before submission | Suicide Prevention |
| Heavy/Very Heavy tone | Show subtle banner | Mental Health |
| User reports feeling unsafe | Show after report | All Tier 1 |
| Post-call (listener suggests) | Optional share | Relevant category |

### Crisis Keyword Detection

```kotlin
// CrisisDetector.kt
object CrisisDetector {
    
    private val suicideKeywords = listOf(
        // English
        "kill myself", "end my life", "want to die", "suicide",
        "no reason to live", "better off dead", "end it all",
        // Swahili
        "kujiua", "kufa", "nimechoka na maisha"
    )
    
    private val selfHarmKeywords = listOf(
        "hurt myself", "cutting", "self harm"
    )
    
    private val severeDistressKeywords = listOf(
        "can't go on", "no hope", "give up", "worthless"
    )
    
    fun analyze(text: String): CrisisAnalysis {
        val lowerText = text.lowercase()
        
        return when {
            suicideKeywords.any { lowerText.contains(it) } -> 
                CrisisAnalysis(
                    level = CrisisLevel.CRITICAL,
                    category = CrisisCategory.SUICIDE,
                    shouldShowResources = true,
                    shouldBlockCall = false, // Allow but show resources
                    suggestedResources = listOf("befrienders_kenya")
                )
            
            selfHarmKeywords.any { lowerText.contains(it) } ->
                CrisisAnalysis(
                    level = CrisisLevel.HIGH,
                    category = CrisisCategory.SELF_HARM,
                    shouldShowResources = true,
                    shouldBlockCall = false,
                    suggestedResources = listOf("befrienders_kenya", "mhfa_kenya")
                )
            
            severeDistressKeywords.any { lowerText.contains(it) } ->
                CrisisAnalysis(
                    level = CrisisLevel.MODERATE,
                    category = CrisisCategory.DISTRESS,
                    shouldShowResources = false, // Just monitor
                    shouldBlockCall = false,
                    suggestedResources = emptyList()
                )
            
            else ->
                CrisisAnalysis(
                    level = CrisisLevel.NONE,
                    category = null,
                    shouldShowResources = false,
                    shouldBlockCall = false,
                    suggestedResources = emptyList()
                )
        }
    }
}

enum class CrisisLevel {
    NONE,
    MODERATE,
    HIGH,
    CRITICAL
}

enum class CrisisCategory {
    SUICIDE,
    SELF_HARM,
    DISTRESS,
    VIOLENCE,
    ABUSE
}

data class CrisisAnalysis(
    val level: CrisisLevel,
    val category: CrisisCategory?,
    val shouldShowResources: Boolean,
    val shouldBlockCall: Boolean,
    val suggestedResources: List<String>
)
```

---

## UI Components

### Help Screen

```
┌─────────────────────────────┐
│ ← Back         Help         │
├─────────────────────────────┤
│                             │
│   Need immediate help?      │
│                             │
│   If you're in crisis,      │
│   please reach out to       │
│   trained professionals.    │
│                             │
│   ┌───────────────────────┐ │
│   │ 📞 Befrienders Kenya  │ │
│   │ +254-722-178-177      │ │
│   │ 24/7 · Free · Private │ │
│   │                       │ │
│   │         [Call]        │ │
│   └───────────────────────┘ │
│                             │
│   ┌───────────────────────┐ │
│   │ 📞 Kenya Red Cross    │ │
│   │ 1199                  │ │
│   │ 24/7 Emergency        │ │
│   │                       │ │
│   │         [Call]        │ │
│   └───────────────────────┘ │
│                             │
│   ─────────────────────────│
│                             │
│   More resources            │
│                             │
│   > Mental Health           │
│   > Gender-Based Violence   │
│   > Substance Abuse         │
│   > Youth Support           │
│                             │
│   ─────────────────────────│
│                             │
│   Backroom is not a crisis  │
│   service. These are        │
│   trained professionals     │
│   who can help.             │
│                             │
└─────────────────────────────┘
```

### Crisis Intervention Modal

When crisis keywords are detected:

```
┌─────────────────────────────┐
│                             │
│   💚 We're here for you     │
│                             │
├─────────────────────────────┤
│                             │
│   It sounds like you're     │
│   going through something   │
│   really difficult.         │
│                             │
│   You deserve support from  │
│   someone trained to help.  │
│                             │
│   ┌───────────────────────┐ │
│   │ 📞 Befrienders Kenya  │ │
│   │ +254-722-178-177      │ │
│   │ 24/7 · Free · Private │ │
│   │         [Call Now]    │ │
│   └───────────────────────┘ │
│                             │
│   ─────────────────────────│
│                             │
│   You can still talk on     │
│   Backroom too.             │
│                             │
│   [ Continue to Backroom ]  │
│                             │
│   [ Maybe Later ]           │
│                             │
└─────────────────────────────┘
```

### Subtle Banner (Heavy Calls)

```
┌─────────────────────────────┐
│ 💚 Need more support?       │
│    Befrienders Kenya: 0722..│
└─────────────────────────────┘
```

---

## Verification Process

### Initial Verification

Before adding a resource:
1. Call the number to verify it works
2. Confirm hours of operation
3. Verify languages supported
4. Check organization's legitimacy
5. Get permission if required
6. Document verification

### Ongoing Verification

| Frequency | Action |
|-----------|--------|
| Monthly | Automated call check (number active) |
| Quarterly | Manual verification call |
| Annually | Full review (still operating, info accurate) |
| On report | Immediate investigation |

### Verification Checklist

```markdown
## Resource Verification: [Organization Name]
Date: [Date]
Verified by: [Name]

### Contact Information
- [ ] Phone number works
- [ ] Alternate number works (if applicable)
- [ ] Website accessible (if applicable)
- [ ] Email responsive (if applicable)

### Service Information
- [ ] Hours of operation confirmed
- [ ] Languages confirmed
- [ ] Services match our description
- [ ] Still accepting calls

### Quality Check
- [ ] Call answered promptly
- [ ] Staff was professional
- [ ] Service was appropriate
- [ ] No concerns noted

### Notes
[Any observations or concerns]

### Recommendation
[ ] Keep active
[ ] Update information
[ ] Investigate further
[ ] Remove from list
```

---

## Localization

### English (Default)

```kotlin
object CrisisStrings {
    const val HELP_TITLE = "Need immediate help?"
    const val HELP_SUBTITLE = "If you're in crisis, please reach out to trained professionals."
    const val CALL_BUTTON = "Call"
    const val HOURS_24_7 = "24/7"
    const val FREE = "Free"
    const val CONFIDENTIAL = "Confidential"
}
```

### Swahili

```kotlin
object CrisisStringsSw {
    const val HELP_TITLE = "Unahitaji msaada wa haraka?"
    const val HELP_SUBTITLE = "Ukiwa katika hali mbaya, tafadhali wasiliana na wataalamu."
    const val CALL_BUTTON = "Piga simu"
    const val HOURS_24_7 = "Masaa 24"
    const val FREE = "Bure"
    const val CONFIDENTIAL = "Siri"
}
```

---

## Analytics

### Events to Track

| Event | Properties | Purpose |
|-------|------------|---------|
| help_screen_viewed | source | Understand need |
| crisis_modal_shown | trigger, category | Monitor detection |
| resource_call_tapped | resource_id | Measure usage |
| resource_call_completed | resource_id, duration | Verify calls happen |
| crisis_continue_to_sikia | trigger | Understand preference |

### Privacy Considerations

- Do NOT log the content that triggered crisis detection
- Do NOT associate crisis events with user identity
- Aggregate statistics only
- Review logs periodically and delete

---

## Escalation Procedures

### If User Reports Imminent Danger

1. **Immediately show crisis resources**
2. **Do not block the app** - user may need continued support
3. **Log incident** (anonymized)
4. **No direct intervention** - we are not trained responders

### If User Reports Abuse by Another User

1. Collect report
2. Show relevant resources (GBV, legal)
3. Ban reported user pending investigation
4. Escalate to Trust & Safety team

### If Staff Become Aware of Imminent Harm

1. Document observation
2. Consult legal team
3. Consider duty to report (if required by law)
4. Take action as advised

---

## Legal Considerations

### Kenya Law

- Mental Health Act, 2017 - Provides framework for mental health care
- Not mandatory reporters (as platform, not healthcare provider)
- Duty of care - provide resources, don't create liability

### Disclaimers

Include in app:
- "Backroom is not a mental health service"
- "If you are in crisis, contact professional help"
- "We do not provide medical or mental health advice"

### Liability Protection

- Make it easy to access resources
- Don't promise we can prevent harm
- Don't intercept or block crisis situations
- Document that we provide resources

---

## Admin Console Features

### Resource Management

```
┌─────────────────────────────────────────────────────────┐
│ Crisis Resources Management                              │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ [+ Add Resource]  [↻ Bulk Verify]  [📊 Analytics]       │
│                                                          │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ Name              │ Phone     │ Status  │ Verified  │ │
│ ├─────────────────────────────────────────────────────┤ │
│ │ Befrienders Kenya │ +254-722..│ ✅ Active│ 15 Jan   │ │
│ │ MHFA Kenya        │ +254-722..│ ✅ Active│ 10 Jan   │ │
│ │ Kenya Red Cross   │ 1199      │ ✅ Active│ 12 Jan   │ │
│ │ Old Service       │ +254-xxx..│ ⚠️ Verify│ 90d ago  │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Crisis Detection Analytics

```
┌─────────────────────────────────────────────────────────┐
│ Crisis Detection - Last 30 Days                          │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ Total Detections: 47                                     │
│                                                          │
│ By Category:                                             │
│ ████████████████░░░░ Distress (68%)                     │
│ ██████░░░░░░░░░░░░░░ Self-harm (23%)                    │
│ ██░░░░░░░░░░░░░░░░░░ Suicide (9%)                       │
│                                                          │
│ Resource Call Rate: 12%                                  │
│ Continue to Backroom: 88%                                │
│                                                          │
│ Top Trigger Keywords:                                    │
│ 1. "can't go on" - 15                                   │
│ 2. "no hope" - 12                                       │
│ 3. "give up" - 8                                        │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## Implementation Checklist

### Phase 1: MVP
- [ ] Add crisis resources to Firestore
- [ ] Create Help screen
- [ ] Implement basic crisis keyword detection
- [ ] Show crisis modal when triggered
- [ ] Add Call button with tel: intent
- [ ] Track help_screen_viewed event

### Phase 2: Enhanced
- [ ] Add more resources (Tier 2)
- [ ] Implement category filtering
- [ ] Add Swahili translations
- [ ] Create admin resource management
- [ ] Set up monthly verification reminders

### Phase 3: Ongoing
- [ ] Regular resource verification
- [ ] Monitor detection accuracy
- [ ] Update keywords based on usage
- [ ] Expand to other countries

---

## Emergency Contacts by Country (Future)

| Country | Primary Resource | Phone |
|---------|------------------|-------|
| Kenya | Befrienders Kenya | +254-722-178-177 |
| Uganda | Befrienders Uganda | +256-414-339-444 |
| Tanzania | - | - |
| Nigeria | SURPIN | +234-803-211-0009 |
| South Africa | SADAG | 0800-567-567 |
| UK | Samaritans | 116 123 |
| USA | 988 Suicide & Crisis Lifeline | 988 |

---

## Resources for Staff

### If You're Affected by Content

Working on Backroom may expose you to difficult content. If you're affected:

1. Take a break
2. Talk to a colleague
3. Use employee assistance resources
4. You are not responsible for users' actions

### Training Resources

- Mental Health First Aid (MHFA) certification
- Crisis response training
- Vicarious trauma awareness
- Self-care for helpers

