# Research: Best motion framework for clean animations in this app

## Decision this informs
Whether to keep building animations (like the onboarding logo spin) on the `motion` package
already installed, or migrate/standardize on a different library (GSAP, React Spring,
plain CSS) going forward.

## Current system
- React 19 app (`granite-field/apps/frontend`), already depends on `motion@^13.2.0` — the
  package formerly known as Framer Motion, renamed Motion in Feb 2025. Import path in use:
  `motion/react`.
- Already used in two files: [OnboardingScreens.tsx](../apps/frontend/src/OnboardingScreens.tsx)
  (slide carousel with drag gestures, `AnimatePresence`, spring transitions, and the new
  logo-spin entrance) and `EscrowPadlockCard.tsx`.
- Animation needs observed in this codebase so far: enter/exit transitions between screens,
  drag-to-swipe gesture handling, spring-based micro-interactions (badge spin), simple
  fade/slide on form screens (`LoginScreen`). No scroll-driven timelines, no SVG path
  morphing, no canvas/WebGL work.

## How it's solved

### Studies
No rigorous academic literature exists specifically comparing JS animation libraries —
this is a tooling/engineering decision, not a research question with peer-reviewed
grounding. The nearest applicable research is on animation *quality*, not library choice:
UI motion best practice (easing curves, duration, physics-based vs keyframe motion feels
more "natural" to users) — documented in Material Design's motion guidelines and Apple HIG,
which both favor spring/physics-based easing for interactive UI over fixed-duration
keyframes. Treated as abstract-only design guidance, not a controlled study.

### Companies
- **The Software House (TSH.io)** — adopted Framer Motion/Motion across client React
  projects specifically for products with a "crucial audiovisual layer," citing rich
  animation capability plus development speed as the reason it beat rolling custom CSS
  transitions. [tsh.io/blog/framer-motion](https://tsh.io/blog/framer-motion)
- **Framer (the vendor)** — built and dogfoods Motion inside their own design tool's React
  runtime; the library's gesture + layout-animation primitives (drag, `AnimatePresence`,
  `layout` prop) were purpose-built for exactly this app's carousel/drag pattern.
- **General 2026 ecosystem guidance (LogRocket, Annnimate, Good Fella Lab comparisons)** —
  converges on: Motion for UI enter/exit + layout animation, GSAP for scroll-driven/timeline
  choreography, React Spring for pure physics feel, plain CSS for trivial hovers/fades.
  [blog.logrocket.com/best-react-animation-libraries](https://blog.logrocket.com/best-react-animation-libraries/)

## Where they agree / diverge
No real divergence for this app's shape. The "it depends" advice in every 2026 comparison
splits by animation *type*, not by rigor vs practicality — and this app's actual usage
(component enter/exit, gesture-driven carousels, spring micro-interactions) sits squarely
in Motion's strongest use case, not GSAP's (scroll/timeline) or React Spring's (raw physics
without layout/gesture helpers). Bundle cost is the only real tradeoff: Motion's full API is
~32-46KB gzipped vs React Spring's ~18KB or GSAP core's ~23KB, but Motion's `LazyMotion` +
`m` component pattern brings the initial cost down to ~4.6KB with features loaded on demand.

## Recommendation (the seed)
Keep `motion` as the app's one animation library — it's already installed, already used
in two files, and its primitives (`AnimatePresence`, `variants`, `drag`, spring transitions,
`useReducedMotion`) map directly onto this app's actual needs (screen transitions, swipeable
carousels, micro-interactions like the logo spin). Don't introduce a second library for
niche cases; if a future screen needs true scroll-driven or timeline-heavy sequences GSAP
becomes worth adding *then*, not preemptively.

To make "adding animations that are clean" actually easier, the concrete follow-up isn't a
new library — it's process on top of the existing one:
1. Adopt `LazyMotion` + `m` components at the app root instead of importing `motion` directly
   everywhere, to keep the bundle-size tradeoff in check as animation usage grows.
2. Extract the spring/easing constants already duplicated across `OnboardingScreens.tsx`
   (`{ type: "spring", stiffness: 300, damping: 30 }` vs `stiffness: 170, damping: 16`) into
   a shared `motion-tokens.ts` (e.g. `springs.snappy`, `springs.gentle`) so new animations
   reuse a consistent feel instead of each screen inventing its own numbers.
3. Keep the `useReducedMotion()` guard pattern used in `MascotBadge` as the house rule for
   any new non-trivial animation.

## Open questions
- Not benchmarked in this pass: actual runtime perf of Motion vs GSAP on the target mobile
  devices this app runs on (assumed WebView/PWA context based on `env(safe-area-inset-bottom)`
  usage) — worth profiling if animations start feeling janky on low-end devices.
- Whether `LazyMotion` migration is worth the refactor now vs deferring until bundle size
  is actually measured as a problem.
