import { domAnimation, LazyMotion, m } from "motion/react";
import type { ReactNode } from "react";

export { m };

/** Reusable spring presets so every screen shares the same motion feel instead of inventing its own numbers. */
export const springs = {
  /** Carousel/screen transitions, drag release. */
  snappy: { type: "spring", stiffness: 300, damping: 30 } as const,
  /** Entrance emphasis (e.g. the onboarding logo spin). */
  gentle: { type: "spring", stiffness: 170, damping: 16, mass: 0.9 } as const,
  /** Small status/icon swaps. */
  quick: { type: "spring", stiffness: 260, damping: 18 } as const,
};

/**
 * Wraps the app once with `domAnimation` (drag, layout, exit animations) so every
 * screen can import the lightweight `m` component instead of the full `motion` bundle.
 */
export function MotionProvider({ children }: { children: ReactNode }) {
  return (
    <LazyMotion features={domAnimation} strict>
      {children}
    </LazyMotion>
  );
}
