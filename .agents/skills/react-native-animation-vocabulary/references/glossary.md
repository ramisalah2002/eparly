# React Native Animation Vocabulary Glossary

Use this glossary for reverse lookup from a described native-mobile behavior to a canonical, searchable term. Prefer the bold term; aliases follow after a slash where both are useful.

## Contents

- [Gesture coupling and boundaries](#gesture-coupling-and-boundaries)
- [Release, snapping, and gesture decisions](#release-snapping-and-gesture-decisions)
- [Motion models and continuity](#motion-models-and-continuity)
- [Transitions and spatial effects](#transitions-and-spatial-effects)
- [Feedback, accessibility, and performance](#feedback-accessibility-and-performance)

## Gesture coupling and boundaries

### Rubber-banding

- **Definition:** Nonlinear resistance that lets content stretch or move beyond a boundary while making further displacement progressively harder.
- **Common confusion:** Not the spring return itself and not generic overshoot; rubber-banding describes the boundary response during manipulation.
- **Useful prompt:** “Add rubber-banding past the scroll boundary, followed by a spring return.”

### Direct manipulation

- **Definition:** Interaction in which a gesture acts on the visible object itself and the object responds continuously as the user moves.
- **Common confusion:** Broader than 1:1 tracking; direct manipulation can include constraints, resistance, or transformed mappings.
- **Useful prompt:** “Make the card feel directly manipulated rather than indirectly controlled.”

### 1:1 tracking

- **Definition:** Gesture mapping where one unit of pointer or finger movement produces an equal unit of on-screen movement.
- **Common confusion:** Not a synonym for direct manipulation, which may deliberately use nonlinear resistance or scaling.
- **Useful prompt:** “Use 1:1 tracking while the sheet is under the finger.”

### Dominant-axis lock

- **Definition:** Gesture behavior that commits movement to the initially dominant horizontal or vertical axis and suppresses drift on the other axis.
- **Common confusion:** Not gesture arbitration, which decides which recognizer or component owns the gesture.
- **Useful prompt:** “Apply dominant-axis lock after a small directional threshold.”

### Gesture arbitration

- **Definition:** The policy by which competing gesture recognizers or nested components decide simultaneous, exclusive, or transferred gesture ownership.
- **Common confusion:** Not dominant-axis lock and not merely setting a movement threshold.
- **Useful prompt:** “Define gesture arbitration between the horizontal carousel and vertical feed.”

## Release, snapping, and gesture decisions

### Velocity handoff

- **Definition:** Transfer of the gesture or animation's current velocity into a following motion phase or another component so movement remains continuous.
- **Common confusion:** “Velocity continuity” describes the quality but is less useful as the canonical mechanism; decay continues velocity rather than transferring it.
- **Useful prompt:** “Preserve velocity handoff from the drag into the dismiss animation.”

### Momentum projection

- **Definition:** Prediction of a gesture's likely resting endpoint from its release position and velocity before choosing an outcome or target.
- **Common confusion:** Not “projected-endpoint snapping”; projection estimates the endpoint, while snapping selects or animates to a snap point.
- **Useful prompt:** “Use momentum projection to choose the destination from release velocity.”

### Snap point

- **Definition:** A discrete stable position or value to which a dragged or scrolling element settles.
- **Common confusion:** A snap point is the target, not the momentum projection or spring used to choose and reach it.
- **Useful prompt:** “Settle the sheet at the nearest eligible snap point.”

### Detent

- **Definition:** A named or meaningful resting level in a stepped control or sheet, often representing a partial, medium, or full presentation state.
- **Common confusion:** A detent is a semantic kind of snap point; use “snap point” for generic positions and “detent” for distinct presentation levels.
- **Useful prompt:** “Give the sheet medium and expanded detents.”

### Hysteresis

- **Definition:** Different thresholds for entering and leaving a state, preventing rapid toggling near a boundary.
- **Common confusion:** Not generic debounce and not momentum projection; hysteresis depends on prior state and direction.
- **Useful prompt:** “Add hysteresis so the selected snap state does not flicker near the midpoint.”

## Motion models and continuity

### Decay / inertia

- **Definition:** Post-release motion that continues with the current velocity and gradually slows under friction or drag.
- **Common confusion:** Not momentum projection, which predicts an endpoint, or velocity handoff, which transfers velocity into another phase.
- **Useful prompt:** “Continue with decay/inertia after release.”

### Spring

- **Definition:** Target-seeking motion modeled with stiffness, damping, mass, and initial velocity rather than a fixed-duration easing curve.
- **Common confusion:** “Spring” does not necessarily imply bounce; damping determines whether it overshoots.
- **Useful prompt:** “Use a spring that preserves the release velocity.”

### Critically damped

- **Definition:** Spring response at the boundary between oscillating and overdamped motion, returning to equilibrium as quickly as possible without overshoot.
- **Common confusion:** Not simply “high damping”; an overdamped spring is slower, and a near-critical implementation may be described perceptually rather than mathematically exact.
- **Useful prompt:** “Use a critically damped response with no visible overshoot.”

### Underdamped

- **Definition:** Spring response with insufficient damping to prevent overshoot and oscillation before settling.
- **Common confusion:** Not every easing curve that overshoots is an underdamped physical spring.
- **Useful prompt:** “Use a lightly underdamped spring for one restrained overshoot.”

### Overshoot

- **Definition:** Motion that passes beyond its target before returning toward it.
- **Common confusion:** Overshoot is an observed behavior, not necessarily a spring model, and differs from boundary rubber-banding.

### Settling

- **Definition:** The final phase in which motion converges on a stable target and velocity approaches zero.
- **Common confusion:** Not synonymous with snapping; free decay and springs can settle without discrete snap points.

### Interruption / retargeting

- **Definition:** Taking control of an in-flight animation or changing its destination while preserving current visual state and, when appropriate, velocity.
- **Common confusion:** Interruption stops or seizes motion; retargeting continues toward a new target. Neither inherently means cancellation back to the start.
- **Useful prompt:** “Make the transition interruptible and retarget from its current state.”

### Cancellation

- **Definition:** Termination of an active gesture or animation, followed by a defined cleanup, rollback, or settle behavior.
- **Common confusion:** Not every interruption is cancellation; an interrupted animation may retarget and continue.

## Transitions and spatial effects

### Origin-aware animation

- **Definition:** Scale, reveal, rotation, or transition motion whose transform origin corresponds to the source, touch point, anchor, or other spatially meaningful origin.
- **Common confusion:** Prefer this concept over the invented “anchored scale transition”; it is not matched geometry unless an element's identity and geometry persist across layouts.
- **Useful prompt:** “Use an origin-aware animation that expands from the tapped thumbnail.”

### Staggered entrance

- **Definition:** Coordinated entrance in which related items begin at small successive delays rather than simultaneously.
- **Common confusion:** Not a cascade driven continuously by scroll position unless timing is explicitly scroll-linked.
- **Useful prompt:** “Use a subtle staggered entrance in reading order.”

### Shared element transition / matched geometry

- **Definition:** Transition that visually preserves an element's identity while interpolating its bounds, position, shape, or other geometry between two layouts or screens.
- **Common confusion:** SwiftUI `matchedGeometryEffect` is one API for this concept; a simple crossfade or unrelated morph does not preserve shared identity.
- **Useful prompt:** “Use a shared element transition (matched geometry) from the grid thumbnail to the detail hero.”

### Crossfade

- **Definition:** Transition that decreases one visual's opacity while increasing another's, usually with temporal overlap.
- **Common confusion:** A dissolve is commonly treated as the same family; a crossfade does not by itself interpolate geometry or shape.

### Morph

- **Definition:** Continuous transformation of one shape, icon, path, or visual form into another.
- **Common confusion:** Not a shared element transition unless the same perceived object also persists across layouts.
- **Useful prompt:** “Morph the play icon into pause without a crossfade.”

### Clip / mask reveal

- **Definition:** Reveal or conceal effect produced by animating the clipping region or mask through which content is visible.
- **Common confusion:** Content need not move or scale; a wipe is one directional form of a clip/mask reveal.
- **Useful prompt:** “Reveal the image with an expanding circular mask.”

### Parallax

- **Definition:** Apparent depth created when visual layers move at different rates in response to scrolling, panning, or device motion.
- **Common confusion:** Not every scroll-linked animation is parallax; parallax specifically uses differential motion to imply depth.
- **Useful prompt:** “Add restrained parallax between the hero image and foreground content.”

### Scroll-linked animation

- **Definition:** Animation whose progress or values are continuously derived from scroll position rather than elapsed time alone.
- **Common confusion:** A one-time animation triggered after crossing a scroll threshold is scroll-triggered, not necessarily scroll-linked.
- **Useful prompt:** “Make header collapse progress scroll-linked.”

### Layout animation

- **Definition:** Animation of visual changes caused by layout updates, such as insertion, removal, resizing, or reflow.
- **Common confusion:** Not necessarily shared element or matched geometry; layout animation may animate unrelated elements into their new frames.
- **Useful prompt:** “Animate the list reflow with a layout animation.”

## Feedback, accessibility, and performance

### Press feedback

- **Definition:** Immediate visual or tactile response while a control is pressed, such as highlight, opacity, scale, or compression.
- **Common confusion:** Not confirmation of the completed action; it communicates contact and responsiveness.
- **Useful prompt:** “Add immediate press feedback on touch-down.”

### Haptic confirmation

- **Definition:** Tactile feedback signaling that a meaningful action, threshold, state change, or selection has completed.
- **Common confusion:** Not generic vibration and not press feedback at touch-down unless the press itself is the confirmed event.
- **Useful prompt:** “Give one haptic confirmation when the card locks into the detent.”

### Reduced motion

- **Definition:** An accessibility preference and adaptation that reduces or replaces motion likely to cause discomfort while preserving state and spatial meaning.
- **Common confusion:** Not always “disable all animation”; use fades, shorter travel, or immediate state changes as appropriate.
- **Useful prompt:** “Provide a reduced-motion alternative that preserves transition clarity.”

### Jank

- **Definition:** Perceptible unevenness, stutter, latency, or missed responsiveness in an interaction or animation.
- **Common confusion:** Broader than a dropped frame; blocking input response, inconsistent pacing, or repeated frame misses can all feel janky.

### Dropped frame

- **Definition:** A frame not produced before its display deadline, causing the previous or next frame to remain visible longer than intended.
- **Common confusion:** A diagnostic event rather than a complete description of jank, and not synonymous with low average frame rate.

### UI-thread animation

- **Definition:** Animation whose per-frame updates execute on the native UI/rendering side without waiting for the JavaScript thread.
- **Common confusion:** This is an execution placement, not a visual style; specific React Native libraries use different names and pipelines for it.
- **Useful prompt:** “Keep gesture-driven updates on the UI thread.”
