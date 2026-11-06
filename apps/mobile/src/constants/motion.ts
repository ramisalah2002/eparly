/**
 * Standardized Motion Design Tokens for Eparly Mobile.
 * Grounded in Apple HIG and the Eparly Motion Director specification.
 * - Minimal overshoot (damping calibrated for stability)
 * - Immediate touch feedback (<16ms)
 * - Restrained scale targets (0.95 - 0.985)
 * - Calibrated for high-trust fintech experiences
 */
export const MOTION = {
  // Spring configurations (damping, stiffness, mass)
  spring: {
    // Tactile press feedback on touch down/up (cards, buttons, rows)
    press: {
      damping: 18,
      stiffness: 240,
      mass: 0.9,
    },
    // Fluid selection indicators (category accent bar, chart pillar, dock tab emphasis)
    selection: {
      damping: 20,
      stiffness: 200,
      mass: 1.0,
    },
    // Content layout & horizon transitions
    content: {
      damping: 24,
      stiffness: 180,
      mass: 1.0,
    },
  },

  // Timing durations (in milliseconds)
  duration: {
    instant: 100,
    fast: 160,
    standard: 220,
    chart: 260,
  },

  // Tactile press scales
  scale: {
    cardPress: 0.985,
    actionPress: 0.975,
    buttonPress: 0.95,
    iconPress: 0.92,
    unpressed: 1.0,
  },
} as const;
