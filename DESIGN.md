---
name: Orbital
description: Deep-space glass instrument panel for a South African learnership platform
colors:
  void-black: "#030712"
  glass-surface: "rgba(15, 23, 42, 0.75)"
  glass-surface-deep: "#0f172a"
  glass-border: "rgba(255, 255, 255, 0.08)"
  ion-blue: "#3b82f6"
  ion-blue-deep: "#2563eb"
  ion-blue-pale: "#eff6ff"
  text-main: "#f8fafc"
  text-body: "#cbd5e1"
  text-muted: "#94a3b8"
  auth-bg: "#f8fafc"
  auth-surface: "#ffffff"
  auth-border: "#e2e8f0"
  auth-text: "#0f172a"
  success: "#10b981"
  error: "#ef4444"
  warning: "#f59e0b"
typography:
  display:
    fontFamily: "Outfit, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    fontSize: "2.1rem"
    fontWeight: 800
    lineHeight: 1.15
    letterSpacing: "-0.5px"
  title:
    fontFamily: "Outfit, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    fontSize: "1.4rem"
    fontWeight: 700
    lineHeight: 1.3
  body:
    fontFamily: "Outfit, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    fontSize: "0.95rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Outfit, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 700
    letterSpacing: "0.08em"
rounded:
  sm: "12px"
  md: "16px"
  lg: "28px"
components:
  button-primary:
    backgroundColor: "{colors.ion-blue}"
    textColor: "#ffffff"
    rounded: "{rounded.sm}"
    padding: "0.65rem 1.25rem"
  button-primary-hover:
    backgroundColor: "{colors.ion-blue-deep}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.text-body}"
    rounded: "{rounded.sm}"
    padding: "0.65rem 1.25rem"
  card-glass:
    backgroundColor: "{colors.glass-surface}"
    textColor: "{colors.text-main}"
    rounded: "{rounded.md}"
    padding: "1.5rem"
  card-auth:
    backgroundColor: "{colors.auth-surface}"
    textColor: "{colors.auth-text}"
    rounded: "{rounded.md}"
    padding: "1.5rem"
---

# Design System: Orbital

## Overview

**Creative North Star: "Orbital: Deep Space Glass"**

Orbital renders its core experience — the Landing hub and every role dashboard (Admin, Facilitator, Moderator, Assessor) — as frosted glass instrument panels floating in a near-black void (`#030712`). Cards are translucent, blurred, and edge-lit with a hairline border (`rgba(255,255,255,0.08)`); Ion Blue is the only saturated color allowed to glow against that dark field, used for accents, active states, and calls to action. Outfit carries the entire type system on its own, doing all the work of hierarchy through weight (300–800) and size rather than a second typeface — a deliberately single-voice, technical register.

The six standalone auth forms (Admin/Facilitator/Moderator/Assessor login, Register, and the learner Login) currently render in a separate light blue-and-white mode instead of the dark glass field. This is recorded here as observed fact, not yet resolved as doctrine: a future pass should decide whether to fold auth into the dark glass system or keep it as a deliberate lighter "arrival" register before the operator steps into the dark instrument panel.

This is explicitly not playful or childish edtech: no bright primary colors, no cartoon iconography, no heavy rounding-as-personality. The restraint and the glow are the personality.

**Key Characteristics:**
- Near-black void background with frosted, blurred glass surfaces as the default operating field
- Ion Blue as the single accent color — everything else is void, glass, or text-neutral
- Outfit as the sole typeface, hierarchy carried by weight and size alone
- Soft-and-refined component language: generous rounding, gentle shadows, no hard/instrument-panel sharpness despite the "mission control" backdrop
- A confirmed but unresolved split: dark glass everywhere except the six auth forms, which are light blue-and-white

## Colors

The palette is almost entirely achromatic (void black, glass white-on-black, neutral slate text) with exactly one chromatic accent doing all the signaling work.

### Primary
- **Ion Blue** (`#3b82f6`): the single accent — primary buttons, active tab state, focus rings, links, icon badges, progress/status highlights. Carries essentially all color in the dark-mode system; its rarity against the void is the point.
- **Ion Blue Deep** (`#2563eb`): hover/active state for Ion Blue surfaces and text links.
- **Ion Blue Pale** (`#eff6ff`): light wash used for icon badge backgrounds and info panels (e.g. syllabus/file sections) inside otherwise dark or light cards.

### Neutral
- **Void Black** (`#030712`): the page background across the dark-mode field — deliberately near-pitch, not a dark gray.
- **Glass Surface** (`rgba(15, 23, 42, 0.75)`): the default card/panel fill — translucent, always paired with backdrop blur.
- **Glass Surface Deep** (`#0f172a`): solid, less-translucent panels such as the dashboard sidebar nav.
- **Glass Border** (`rgba(255, 255, 255, 0.08)`): the hairline edge on every glass surface; never a solid neutral gray border in dark mode.
- **Text Main** (`#f8fafc`), **Text Body** (`#cbd5e1`), **Text Muted** (`#94a3b8`): the three-step text neutral ramp, from headline to muted caption.

### Light auth mode (observed, unresolved — see Overview)
- **Auth Background** (`#f8fafc`) and **Auth Surface** (`#ffffff`) with **Auth Border** (`#e2e8f0`) and **Auth Text** (`#0f172a`): the alternate light register used only by the six standalone auth forms, layered over the same faint blue/purple/indigo ambient blob decoration used elsewhere.

### System
- **Success** (`#10b981`), **Error** (`#ef4444`), **Warning** (`#f59e0b`): status colors for banners and submission-state badges, each paired with a matching pale background tint (e.g. success banner `#ecfdf5` on `#10b981` border).

### Named Rules
**The One Accent Rule.** Ion Blue is the only saturated color in the dark-mode system. Every other surface is void, glass, or neutral text — new UI should reach for glass/neutral first and spend the accent deliberately, not decoratively.

## Typography

**Display Font:** Outfit (with `-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif` fallback)
**Body Font:** Outfit — same family as display; no secondary typeface anywhere in the system.

**Character:** One typeface carrying the full range from 300 to 800 weight. Hierarchy is built entirely from weight and size, not from a font pairing — a technical, single-voice register that fits the instrument-panel read.

### Hierarchy
- **Display** (800, 2.1rem, letter-spacing -0.5px): the Landing/auth hero heading (e.g. "Student Portal Entry").
- **Headline** (800, 1.85rem): dashboard page titles (`.portal-header h1`).
- **Title** (700, 1.4rem): section/card headers within a dashboard panel.
- **Body** (400–600, 0.85–0.95rem, line-height ~1.5): form labels, descriptions, list content.
- **Label** (700, 11px, letter-spacing 0.08em, uppercase): form field labels and status badges — the one place the system goes small-caps/tracked.

### Named Rules
**The Weight-Not-Voice Rule.** New hierarchy needs are solved by moving up/down the Outfit weight and size scale, never by introducing a second typeface.

## Layout

The dark-mode field is a centered content column: dashboards use a `max-width: 1100–1280px` wrapper (`.portal-wrapper`, or Tailwind `max-w-7xl` on the admin dashboard) with generous outer padding (`2rem 1.5rem` / `px-4 sm:px-6 lg:px-8`). The Landing/auth hero sits in a single centered card, `max-width: 448–540px`, vertically and horizontally centered in the viewport with `min-height: 100vh`.

Dashboards that need navigation use a sidebar + content split (`lg:grid-cols-12`, sidebar `lg:col-span-3`, content `lg:col-span-9`), collapsing to a stacked single column below `lg`. Tables get an explicit responsive wrapper rule: `display:block; overflow-x:auto` with `white-space:nowrap` cells, rather than a bespoke mobile table redesign — the honest fallback for dense admin data on small screens, consistent with the confirmed low-bandwidth/mobile learner constraint in PRODUCT.md.

Spacing is Tailwind's default scale (4px steps) throughout; no bespoke spacing scale exists outside of it.

## Elevation & Depth

Structural layering: elevation is communicated by a deliberate combination of blur depth and shadow strength, not by a single flat shadow token. Three layers are observable:

1. **Base / inline items** (list rows, submission items): a whisper-thin shadow (`0 1px 3px rgba(0,0,0,0.02)`), no blur — barely lifted off the page.
2. **Card / resting** (the default `bg-white`-overridden glass panel): `backdrop-filter: blur(16px)` with `box-shadow: 0 8px 32px rgba(0,0,0,0.45)` in dark mode, or a softer colored shadow (`0 10px 25px -5px rgba(37,99,235,0.05)`) in light auth mode.
3. **Hero / floating** (the Landing/auth entry card): the deepest blur (`24px`) and the heaviest, most layered shadow stack (triple `box-shadow`), marking it as the topmost surface in the system.

Hover states step surfaces up one layer at a time: a resting card gains `translateY(-1px)` plus a deeper, Ion-Blue-tinted shadow rather than jumping straight to the hero layer's treatment.

### Shadow Vocabulary
- **Whisper** (`0 1px 3px rgba(0,0,0,0.02)`): inline list rows at rest.
- **Card** (`0 8px 32px rgba(0,0,0,0.45)` dark / `0 10px 25px -5px rgba(37,99,235,0.05)` light): the standard glass panel.
- **Hover-lift** (`0 8px 24px rgba(37,99,235,0.08)`): card/list-item hover state, always paired with a small upward translate.
- **Hero** (`0 25px 50px -12px rgba(15,23,42,0.12), 0 10px 20px -10px rgba(15,23,42,0.06), 0 0 0 1px rgba(15,23,42,0.05)`): the Landing/auth entry card only.

### Named Rules
**The Blur-Marks-Layer Rule.** Blur radius scales with elevation (no blur → 16px → 24px); don't apply the hero card's 24px blur to a routine panel, and don't leave a floating/hero-level surface unblurred.

## Shapes

Rounding scales with a surface's importance rather than following a single fixed radius. Base UI (buttons, inputs, standard cards) uses a **12px** radius; larger content cards and dashboard panels step up to **16px** (Tailwind `rounded-2xl`); the Landing/auth hero card is the most rounded surface in the system at **28px**, with its nested login/register sub-panels at **18px**. Borders are hairline throughout (`1px`) and always paired with a translucent, not solid, border color. The one deliberate exception is the file-upload dropzone, which uses a **2px dashed** border to signal "drop target" rather than "surface edge."

## Components

### Buttons
- **Shape:** 12px radius (`rounded-xl`) as the default; nothing sharper, nothing pill-shaped.
- **Primary:** Ion Blue fill, white text, weight 600–700, paired with a soft Ion-Blue-tinted shadow (`shadow-blue-500/20` / `0 4px 12px rgba(37,99,235,0.15)`); hover moves to Ion Blue Deep with a deeper shadow; active state scales down slightly (`scale(0.98–0.99)`) rather than changing color — a tactile press, not a color flash.
- **Ghost/Secondary:** transparent or pale neutral (`bg-slate-100` / `bg-white/10` depending on mode) fill, neutral text, hairline border; hover just deepens the neutral fill — never introduces Ion Blue on a secondary action.
- **Tab (segmented):** flex-equal buttons inside a pale pill container; inactive tabs are transparent with muted text, the active tab fills solid Ion Blue with its own drop shadow — the clearest single "you are here" signal in the system.

### Cards / Containers
- **Corner Style:** 16px (`rounded-2xl`) for standard panels; 28px only for the Landing/auth hero.
- **Background:** translucent glass (`rgba(15,23,42,0.75)`) in dark mode via the `bg-white` + blur override, solid white in light auth mode.
- **Shadow Strategy:** see Elevation & Depth — card-level shadow at rest, hover-lift on interaction.
- **Border:** hairline, translucent (`rgba(255,255,255,0.08)` dark / `#e2e8f0` light).
- **Internal Padding:** 1.25–2.25rem depending on card size; hero card goes up to 3–3.5rem.

### Inputs / Fields
- **Style:** translucent dark fill in dashboard forms, solid white in auth forms; hairline border, 12px radius, label set as an uppercase 11px tracked caption above the field (not inline/floating).
- **Focus:** border shifts to Ion Blue plus a soft 4px Ion-Blue ring (`focus:ring-4 focus:ring-blue-500/10`) — a glow, not a hard outline.
- **Error/Disabled:** error state uses the system Error red for inline banners above the form rather than per-field red borders; disabled fields simply mute opacity/interaction, no separate visual language yet observed.

### Status Badges
- **Style:** pill-shaped, uppercase, letter-spaced, small (0.75rem); each status (submitted / graded / late) pairs a pale tint background with a matching saturated border and dark-tinted text of the same hue family — success-green, info-blue, error-red.

### Navigation (dashboard sidebar)
- **Style:** a solid deep-glass panel (`#0f172a`-family, not translucent), sticky on scroll, stacked vertical items over a hairline divider; distinct from the translucent blur treatment of content cards — navigation is structurally "behind the glass," content cards float in front of it.

## Do's and Don'ts

### Do:
- **Do** keep Ion Blue as the only saturated accent in dark-mode surfaces; reach for glass/neutral tones first.
- **Do** scale blur and shadow together when introducing a new surface — never a deep shadow with no blur, or heavy blur with a whisper shadow, outside the defined three-layer scale.
- **Do** carry all typographic hierarchy through Outfit's weight and size range rather than introducing a second typeface.
- **Do** use the pill/segmented pattern (transparent inactive, solid Ion Blue active with its own shadow) for any new tab or toggle group.

### Don't:
- **Don't** introduce a second accent color alongside Ion Blue for routine UI — reserve any additional hue for status semantics (success/error/warning) only.
- **Don't** use hard, sharp corners or flat drop shadows; every surface in this system is soft-and-refined, not instrument-sharp, even though the North Star reads as "deep space glass."
- **Don't** silently resolve the dark-glass-vs-light-auth split while doing routine work on either side — it's recorded here as an open question (see Overview), not a decision either mode should make unilaterally.
- **Don't** drift toward bright, rounded, cartoonish edtech styling; the confirmed anti-reference is playful/childish edtech, not just "generic corporate LMS."
