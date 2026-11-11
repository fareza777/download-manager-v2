# F7 Visual Overhaul — direction, tooling, and performance budget

Status: **Phase 1 and Phase 2 are implemented in the app** (tokens, motion, speed console,
segment ticks, swipe actions, nav animation, AGSL ambient backdrop). A 3D "F7" hero image was
rendered and then removed again: decoration with no job in the empty state.
Phase 3 (live 3D hero) and Phase 4 (adaptive layouts, widget) remain open.
Constraint: native Android (Kotlin + Compose), minSdk 24, target 36, reference device API 33.
Goal: feel genuinely premium and technically sharp, while staying at 60 fps (120 on capable
panels) on a mid-range phone.

---

## 1. Tooling verdict

| Option | Verdict | Why |
| --- | --- | --- |
| **Three.js / WebGL in a WebView** | **Rejected for app UI** | A WebView UI loses native scroll physics, predictive back, haptics and input latency; costs 80–150 MB RSS; breaks the foreground-service/notification surface. It would be slower and less integrated, not more "canggih". Only sensible for a marketing page. |
| **Blender MCP** | **Available on this desktop, not yet exposed to this chat** (only the `maestro` MCP is registered here). Runtime: still not needed. | Blender produces offline art. Use it for the icon set, one hero still, and optional sprite frames — never a live scene in the app, because that costs frames, battery and APK size on mid-range phones. |
| **Compose + AGSL `RuntimeShader`** (API 33+) | **Recommended** | GPU shaders drawn straight into Compose, no extra runtime, no WebView. One compiled shader, a handful of uniforms per frame. Static fallback below API 33. |
| **Compose animation + `graphicsLayer`** | **Recommended, highest value per KB** | The app currently has zero animation. Most of the "expensive" feel is motion design, not 3D. |
| **Filament via SceneView** | **Optional Phase 3** | Real 3D (glTF) is viable and fast on Android, but adds 2–4 MB per ABI and only pays off with a genuine 3D object. A rotating glass "F7" monolith in the empty state is the only scene worth considering. |
| **Lottie** | **Optional, sparingly** | Good for a 1–2 micro-illustration (empty library, success). Not for progress; progress must reflect real numbers. |
| **Video splash** | **No** | Cold-start tax, battery, and it delays a tool that should open instantly. |

### 1.1 What Blender is actually good for here

Three deliverables, in order of value per megabyte:

1. **Launcher icon set (highest value).** Sculpt one "F7" monolith (glass + brushed metal), light it with a
   three-point setup, render at 1024px, export adaptive foreground / background / monochrome layers as
   WebP. Replaces a generic vector mark with something that looks commissioned. Budget: < 120 KB.
2. **One hero still for the empty state.** A single 1200×1200 WebP of the same object, softly lit on the
   paper background. Shows only when there are zero downloads, so it never competes with data. Budget: < 90 KB.
3. **Optional sprite sheet** (slow 360° turn, 48–60 frames, 512px, WebP) for the first-run screen only.
   This is the only "3D in the app" worth considering without a native renderer; it costs 1.5–3 MB and
   must be downloaded lazily or skipped on low-RAM devices. Decide only after Phase 1 data.

Do **not** render UI chrome, progress rings or backgrounds in Blender: those must stay vector or shader so
they scale to every density and animate at frame rate.

---

## 2. Design direction

Three coherent directions; the recommendation is **A with C's instrumentation details and a
small dose of B's glow, only while something is actually running**.

### A. Precision Editorial — recommended
Warm "paper" surfaces, ink typography, oversized numerals, hairline dividers, one saturated
accent. Reads as expensive stationery / Swiss print, not as an AI-generated glass dashboard.
Fast by construction: flat fills, no blur, motion is precise rather than busy.

### B. Obsidian Glass — accent, not base
Dark-first, luminous jade, frosted layers, aurora shader. Striking in a screenshot, but blur on
scrolling content is a frame-time trap and the look is currently generic. Use only as: glow
behind the speed console while downloading, and a translucent bottom bar on API 31+.

### C. Industrial Telemetry — details
Tabular numerals, tick marks, micro-labels, segment counters, thin rules. This is what makes a
download manager look like an instrument instead of a to-do list. Adopt as the data layer of A.

---

## 3. Visual system

### 3.1 Color
Keep the six accents, but formalise tokens instead of hard-coded values:

```
paper.0   #FBF9F4   page top            ink.0    #0E1211   primary text
paper.1   #F3EFE6   page bottom         ink.1    #3A403D   secondary text
surface.0 #FFFFFF   cards               ink.2    #6E6B62   tertiary/muted
surface.1 #EDE7DB   inset wells         line.0   #E5DFD2   hairlines
```

Rules:
- Accent used for **one** thing per screen: the primary action. Everything else is ink/line.
- Status colours are semantic and never decorative: `run #0C6B5A`, `warn #B4502F`,
  `fail #C24432`, `done #1E8E6A`.
- Dark theme must not lighten the accent toward mint (current `lerp(primary, white, 0.62)` turns
  jade into pale cyan and loses identity). Use a dark-theme accent ramp per preset instead, and
  offer a true-black option.
- Gradients: only two, both vertical and subtle (page background, progress fill). No rainbow.

### 3.2 Typography
Keep Plus Jakarta Sans, but make numbers behave like instruments:
- `fontFeatureSettings = "tnum"` on every speed/size/percent text so digits stop jittering.
- Numerals: speed `displayMedium` 36sp/42, size `titleLarge`, percent `labelMedium` in a pill.
- Tabular sizes: 13 / 14 / 16 / 17 / 20 / 26 / 34 / 48. Nothing below 13sp.
- Tighten display tracking (-1.0 to -1.4sp) and keep body at 0.

### 3.3 Shape, spacing, depth
- 8dp grid; page margin 20dp; card padding 16dp; radius scale 12 / 18 / 22 / 28 (sheets).
- Elevation via one hairline plus one soft shadow, never stacked shadows.
- Cards are content containers only. No card inside a card inside a card.

### 3.4 Iconography
- 1.75dp stroke, rounded caps, 24dp grid, optical alignment at 20/21dp.
- Distinct per-category icon and tint (today image and document share the same glyph and every
  tile uses the same accent — cheap-looking).
- Animate icon *weight*, not size, on state change (pause ⇄ play morph at 180ms).

---

## 4. Signature components (the "wow", all real-data driven)

1. **Speed console** — replaces the current card. Large tabular speed, unit baseline-aligned,
   a 60-second sparkline with a soft area fill, and a thin ring of live connection ticks around
   the number. Speed text is interpolated between engine ticks so it never jumps once per second.
2. **Segment ring / tick bar** — the 8–16 real ranges rendered as ticks that fill independently.
   This is the feature that differentiates the app; make it visible, not hidden in a details sheet.
3. **Magnetic FAB** — collapses to a circle on scroll, expands to "New download" at rest,
   long-press opens Paste / Batch / Magnet as a small radial cluster. Spring-driven, no bounce
   overshoot beyond 4%.
4. **Row interactions** — swipe left to pause/resume, swipe right to delete with a confirm
   threshold; progress hairline pinned to the card's bottom edge; shared-element icon into the
   details sheet (Compose 1.7 `SharedTransitionLayout`).
5. **Details sheet** — hero icon, spring-animated progress, per-segment timeline, friendly
   destination ("Downloads/F7") instead of a raw `content://` URI, copy actions.
6. **Ambient shader** — slow ink-flow / grain behind the empty state and the page top, AGSL,
   ~30 fps, paused when the screen is not resumed, disabled in battery saver and when
   "remove animations" is on.
7. **Bottom bar** — frosted only on API 31+, hairline otherwise; sliding indicator with an
   icon-weight morph; 48dp touch targets (current 36–42dp buttons are below the guideline).

---

## 5. Motion spec

| Token | Value | Used for |
| --- | --- | --- |
| `fast` | 120ms, `FastOutLinearIn` | icon morph, ripple-adjacent feedback |
| `base` | 200ms, `FastOutSlowIn` | chips, toggles, small reveals |
| `slow` | 320ms, `FastOutSlowIn` | sheet content, list enter |
| `spring` | damping 0.85, stiffness 380 | FAB, progress, counters |
| `softSpring` | damping 0.90, stiffness 220 | shared element, sheet drag settle |
| `stagger` | 28ms per item, max 6 | list entrance only |

Rules:
- Nothing exceeds 400ms. No infinite decorative loops except the ambient shader (throttled).
- Numbers animate with `animateFloatAsState` toward the engine value over 900ms linear, so the
  display is continuous between 1s ticks.
- Respect `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and Compose's reduced-motion signal;
  then swap springs for `snap()` and freeze the shader.
- Haptics: `TextHandleMove`-class tick on pause/resume and on swipe threshold; never on scroll.

---

## 6. Performance budget (non-negotiable)

- 8ms CPU per frame at 60Hz; no frame over 16ms during scroll (`FrameMetrics`/JankStats).
- Shader compiled once at composition, uniforms updated by reference; never allocate `Paint`,
  `Path`, `Brush` or lambdas inside `onDraw`. Cache paths in `drawWithCache`.
- Blur only on static surfaces (bottom bar, scrim). Never blur a `LazyColumn`.
- Every animation runs on `graphicsLayer` (transform/alpha) so it stays off the main-thread
  layout pass.
- Shader and sparkline pause when `Lifecycle.State != RESUMED` and when the screen is off.
- `LazyColumn` keys already exist — add `Modifier.animateItem()` and stable content types.
- No bitmap assets for chrome: everything vector or shader. Target APK growth < 300 KB for
  Phase 1–2; 3D phase adds ~2–4 MB and is opt-in.
- Measure before/after with `adb shell dumpsys gfxinfo` and Macrobenchmark `FrameTimingMetric`;
  a visual change is not "smooth" until the numbers say so.

---

## 7. Phased plan

**Phase 1 — Motion + typography + component overhaul (no new dependencies)**
Tokens and theme cleanup, tabular numerals, speed console, segment ticks, swipe actions, magnetic
FAB, shared-element details, icon redesign, 48dp targets, dark accent ramp, empty states.
Risk: low. Largest perceived jump per hour of work.

**Phase 2 — GPU shader layer (AGSL, API 33+, static fallback)**
Ambient grain/ink-flow, glow behind the console while active, frosted bottom bar on API 31+.
Risk: medium; must be gated by lifecycle, battery saver, and reduced motion.

**Phase 3 — Optional 3D hero (Filament/SceneView) or offline Blender art**
Only if a real 3D concept is wanted: a glass "F7" monolith in the empty state, or Blender-rendered
icon/illustration set. Adds APK size and a native dependency; decide after Phase 1–2 data.

**Phase 4 — Adaptive + platform polish**
Tablet/foldable two-pane layout, predictive back preview, splash screen API, themed monochrome
icon, quick-settings tile, home-screen widget with live speed.

---

## 8. What not to do

- No WebView-based UI, no Three.js shell, no 3D navigation.
- No constantly running particle field, no blur behind scrolling lists, no video splash.
- No fake data visualisations: every tick, percentage and sparkline must come from the engine.
- No "glass" everywhere; translucency is a spice, and it is the first thing that janks.
