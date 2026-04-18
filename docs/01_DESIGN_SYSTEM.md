# Backroom Design System

## Brand Identity

### App Name
**Backroom**

### Tagline
Primary: "Say what you can't."
Alternatives: "Be heard. Anonymously." | "Speak. Be heard."

### Brand Personality
- Warm but not saccharine
- Safe but not clinical
- Modern but not cold
- Kenyan but universally human

---

## Color Palette

### Primary Colors

| Name | Hex | RGB | Usage |
|------|-----|-----|-------|
| Backroom Purple | #6B4EE6 | 107, 78, 230 | Primary actions, brand identity |
| Backroom Purple Light | #8B7AE8 | 139, 122, 232 | Hover states, secondary elements |
| Backroom Purple Dark | #5039B8 | 80, 57, 184 | Pressed states |

### Semantic Colors

| Name | Hex | Usage |
|------|-----|-------|
| Success Green | #22C55E | Positive feedback, connected state |
| Warning Amber | #F59E0B | Caution, heavy topics |
| Error Red | #EF4444 | Errors, emergency actions |
| Info Blue | #3B82F6 | Informational messages |

### Neutral Colors

| Name | Hex | Usage |
|------|-----|-------|
| Background | #FAFAFA | Main background (light mode) |
| Surface | #FFFFFF | Cards, modals |
| Surface Variant | #F5F5F5 | Secondary surfaces |
| On Surface | #1A1A1A | Primary text |
| On Surface Variant | #6B7280 | Secondary text |
| Outline | #E5E7EB | Borders, dividers |
| Outline Variant | #D1D5DB | Subtle borders |

### Dark Mode Colors

| Name | Hex | Usage |
|------|-----|-------|
| Background Dark | #121212 | Main background |
| Surface Dark | #1E1E1E | Cards, modals |
| Surface Variant Dark | #2A2A2A | Secondary surfaces |
| On Surface Dark | #F5F5F5 | Primary text |
| On Surface Variant Dark | #9CA3AF | Secondary text |

### Topic Colors (Visual Coding)

| Topic | Color | Hex |
|-------|-------|-----|
| Confession | Deep Purple | #7C3AED |
| Venting | Orange | #F97316 |
| Advice | Blue | #3B82F6 |
| Grief | Muted Blue | #64748B |
| Just Talking | Teal | #14B8A6 |
| Something Hard | Rose | #F43F5E |

### Tone Colors (Intensity Indicators)

| Tone | Color | Hex |
|------|-------|-----|
| Light | Green | #22C55E |
| Heavy | Amber | #F59E0B |
| Very Heavy | Red | #EF4444 |

---

## Typography

### Font Family
- **Primary:** Inter (Google Fonts, excellent readability)
- **Fallback:** System default sans-serif

### Type Scale

| Style | Size | Weight | Line Height | Usage |
|-------|------|--------|-------------|-------|
| Display Large | 32sp | 700 | 40sp | Splash, major headers |
| Display Medium | 28sp | 700 | 36sp | Screen titles |
| Headline Large | 24sp | 600 | 32sp | Section headers |
| Headline Medium | 20sp | 600 | 28sp | Card titles |
| Title Large | 18sp | 600 | 24sp | Important labels |
| Title Medium | 16sp | 600 | 22sp | Button text, emphasized |
| Body Large | 16sp | 400 | 24sp | Primary body text |
| Body Medium | 14sp | 400 | 20sp | Secondary body text |
| Body Small | 12sp | 400 | 16sp | Captions, hints |
| Label Large | 14sp | 500 | 20sp | Form labels |
| Label Medium | 12sp | 500 | 16sp | Small labels |
| Label Small | 10sp | 500 | 14sp | Micro labels |

### Typography Guidelines
- Use sentence case for UI elements
- Avoid ALL CAPS except for very short labels
- Maintain minimum 4.5:1 contrast ratio (WCAG AA)
- Support dynamic type scaling for accessibility

---

## Spacing System

### Base Unit
4dp (all spacing is multiples of 4)

### Spacing Scale

| Token | Value | Usage |
|-------|-------|-------|
| space-0 | 0dp | No spacing |
| space-1 | 4dp | Tight spacing, icon gaps |
| space-2 | 8dp | Compact elements |
| space-3 | 12dp | Related elements |
| space-4 | 16dp | Standard padding |
| space-5 | 20dp | Medium gaps |
| space-6 | 24dp | Section spacing |
| space-8 | 32dp | Large gaps |
| space-10 | 40dp | Screen margins |
| space-12 | 48dp | Major sections |
| space-16 | 64dp | Hero spacing |

### Screen Margins
- Horizontal: 16dp (phone), 24dp (tablet)
- Vertical: 16dp top, 24dp bottom (for nav bar)

---

## Corner Radius

| Token | Value | Usage |
|-------|-------|-------|
| radius-none | 0dp | Sharp corners |
| radius-sm | 4dp | Subtle rounding |
| radius-md | 8dp | Buttons, inputs |
| radius-lg | 12dp | Cards |
| radius-xl | 16dp | Modals, sheets |
| radius-2xl | 24dp | Large cards |
| radius-full | 9999dp | Pills, avatars |

---

## Elevation (Shadows)

| Level | Usage | Shadow |
|-------|-------|--------|
| elevation-0 | Flat elements | None |
| elevation-1 | Cards at rest | 0 1dp 2dp rgba(0,0,0,0.1) |
| elevation-2 | Cards hover | 0 2dp 4dp rgba(0,0,0,0.1) |
| elevation-3 | Floating buttons | 0 4dp 8dp rgba(0,0,0,0.15) |
| elevation-4 | Modals, sheets | 0 8dp 16dp rgba(0,0,0,0.2) |

---

## Components

### Buttons

#### Primary Button
- Background: Backroom Purple
- Text: White
- Corner radius: radius-md (8dp)
- Padding: 16dp horizontal, 12dp vertical
- Min height: 48dp (touch target)
- States: Default, Pressed (darker), Disabled (50% opacity)

#### Secondary Button
- Background: Transparent
- Border: 1dp Backroom Purple
- Text: Backroom Purple
- Same dimensions as primary

#### Text Button
- Background: Transparent
- Text: Backroom Purple
- Padding: 8dp
- Used for less prominent actions

#### Emergency Button
- Background: Error Red
- Text: White
- Used only for safety actions

### Input Fields

#### Text Input
- Background: Surface Variant
- Border: 1dp Outline (focus: Backroom Purple)
- Corner radius: radius-md
- Padding: 16dp
- Min height: 56dp
- Label: Above input, Label Medium
- Helper text: Below input, Body Small

#### Dropdown/Select
- Same styling as text input
- Chevron icon on right
- Opens bottom sheet on mobile

### Cards

#### Standard Card
- Background: Surface
- Corner radius: radius-lg
- Padding: 16dp
- Elevation: elevation-1
- Border: Optional 1dp Outline

#### Preview Card (Incoming Call)
- Background: Surface
- Corner radius: radius-xl
- Padding: 20dp
- Elevation: elevation-3
- Accent border left: 4dp Topic Color

### Toggle Switch
- Track: 52dp x 32dp
- Thumb: 28dp circle
- Off: Gray track, white thumb
- On: Backroom Purple track, white thumb

### Chips/Pills

#### Topic Chip
- Background: Topic Color (20% opacity)
- Text: Topic Color
- Corner radius: radius-full
- Padding: 8dp horizontal, 4dp vertical

#### Tone Indicator
- 5 dots layout
- Filled dots: Tone Color
- Empty dots: Outline color

### Bottom Sheet
- Background: Surface
- Corner radius: radius-xl (top only)
- Handle: 40dp x 4dp, centered, Outline color
- Padding: 24dp

### Snackbar/Toast
- Background: On Surface (dark)
- Text: Surface (light)
- Corner radius: radius-md
- Positioned 16dp from bottom
- Auto-dismiss: 4 seconds

---

## Iconography

### Icon Style
- Outlined style (not filled) for most icons
- 24dp default size
- 2dp stroke weight
- Rounded caps and joins

### Required Icons

| Icon | Usage |
|------|-------|
| mic | Microphone, recording |
| mic-off | Muted |
| phone | Call active |
| phone-off | End call |
| alert-triangle | Warning, emergency |
| shield | Safety, privacy |
| clock | Timer, duration |
| user | Anonymous user |
| settings | Settings |
| help-circle | Help, resources |
| x | Close, cancel |
| check | Confirm, success |
| chevron-right | Navigation |
| chevron-down | Dropdown |
| volume-2 | Audio on |
| volume-x | Audio off |
| heart | Helped feedback |
| meh | Neutral feedback |
| frown | Uncomfortable feedback |

### Icon Library
Recommend: Lucide Icons (open source, consistent)

---

## Motion/Animation

### Duration Scale

| Token | Value | Usage |
|-------|-------|-------|
| duration-fast | 150ms | Micro-interactions |
| duration-normal | 250ms | Standard transitions |
| duration-slow | 350ms | Complex animations |
| duration-slower | 500ms | Page transitions |

### Easing

| Token | Value | Usage |
|-------|-------|-------|
| ease-out | cubic-bezier(0, 0, 0.2, 1) | Elements entering |
| ease-in | cubic-bezier(0.4, 0, 1, 1) | Elements exiting |
| ease-in-out | cubic-bezier(0.4, 0, 0.2, 1) | Elements moving |

### Animation Guidelines
- Prefer subtle over flashy
- Avoid animations during calls (distraction)
- Respect reduced motion preferences
- Use haptic feedback for confirmations

---

## Accessibility

### Touch Targets
- Minimum: 48dp x 48dp
- Recommended: 56dp for primary actions

### Contrast
- Text: Minimum 4.5:1 (WCAG AA)
- Large text: Minimum 3:1
- Icons: Minimum 3:1

### Focus States
- Visible focus ring: 2dp Backroom Purple
- Focus order: Logical, top-to-bottom, left-to-right

### Screen Reader
- All interactive elements have content descriptions
- Images have alt text
- State changes announced

### Color Independence
- Never use color alone to convey information
- Always pair with text, icons, or patterns

---

## Dark Mode Considerations

### Automatic Switching
- Follow system preference by default
- Allow manual override in settings

### Color Adjustments
- Reduce saturation slightly in dark mode
- Increase contrast for readability
- Use elevation instead of shadows

---

## Responsive Breakpoints

| Breakpoint | Width | Target |
|------------|-------|--------|
| Compact | 0-599dp | Phone portrait |
| Medium | 600-839dp | Phone landscape, small tablet |
| Expanded | 840dp+ | Tablet, foldable |

### Layout Adjustments
- Compact: Single column, full-width cards
- Medium: Two columns where appropriate
- Expanded: Side panel navigation possible

---

## File Organization (Compose)

```
ui/
├── theme/
│   ├── Color.kt
│   ├── Type.kt
│   ├── Shape.kt
│   ├── Spacing.kt
│   └── Theme.kt
├── components/
│   ├── buttons/
│   ├── cards/
│   ├── inputs/
│   └── feedback/
└── screens/
    ├── onboarding/
    ├── home/
    ├── call/
    └── settings/
```

---

## Design Tokens (Compose Implementation)

```kotlin
// Example structure - actual implementation in code phase
object BackroomColors {
    val primary = Color(0xFF6B4EE6)
    val primaryLight = Color(0xFF8B7AE8)
    val primaryDark = Color(0xFF5039B8)
    // ... etc
}

object BackroomTypography {
    val displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    )
    // ... etc
}

object BackroomSpacing {
    val space1 = 4.dp
    val space2 = 8.dp
    val space4 = 16.dp
    // ... etc
}
```

---

## Next Steps

1. Create Figma file with these tokens
2. Build component library in Compose
3. Design screen mockups
4. Prototype key flows
5. User test with 5-10 people

