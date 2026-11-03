# Fluid Interaction Patterns

Use these patterns after inspecting the installed Expo, React Native, Reanimated, and Gesture Handler versions. Names and callback semantics vary by release; confirm every API against project source, types, and official documentation before adapting a fragment. The snippets are representative, not a full component.

## Contents

1. [Version Gate](#version-gate)
2. [Rubber-Band Resistance](#rubber-band-resistance)
3. [Velocity Projection](#velocity-projection)
4. [Snap Selection](#snap-selection)
5. [Current-Value Interruption](#current-value-interruption)
6. [Gesture and Scroll Ownership](#gesture-and-scroll-ownership)
7. [Reduced-Motion Substitutions](#reduced-motion-substitutions)
8. [Materials Checklist](#materials-checklist)
9. [Haptic Causality](#haptic-causality)
10. [Device Verification](#device-verification)

## Version Gate

Before choosing syntax, record:

```text
Expo SDK:
React Native:
Reanimated:
Gesture Handler:
Haptics / blur / audio libraries:
New Architecture or legacy runtime:
Targets actually available for verification:
```

Inspect `package.json`, the resolved lockfile, Expo config, Babel/Metro config, native folders, types in `node_modules`, and the project's existing primitives. Version-gate gesture callbacks, animation cancellation, reduced-motion configuration, blur/material support, and haptic methods. Do not infer physical-device behavior from package presence.

## Rubber-Band Resistance

Track 1:1 inside the valid interval. Outside it, map overshoot `d` to a concave response that grows progressively slower. One compact mapping is:

\[
r(d) = \operatorname{sign}(d) \cdot \frac{L\,|d|}{L + c|d|}
\]

`L` is a visual resistance length and `c > 0` controls stiffness. The derivative decreases with distance, so resistance grows without a hard visual stop. Choose constants relative to available geometry, not by copying fixed pixels across surfaces.

```ts
const resist = (distance: number, length: number, stiffness = 0.8) => {
  'worklet';
  const magnitude = Math.abs(distance);
  return Math.sign(distance) * (length * magnitude) / (length + stiffness * magnitude);
};

const displayed =
  raw < min ? min + resist(raw - min, resistanceLength) :
  raw > max ? max + resist(raw - max, resistanceLength) :
  raw;
```

Keep `raw` and `displayed` distinct. On release, settle from `displayed`; on re-grab, reconstruct the gesture offset from the current displayed value so there is no jump. Clamp the final state to a valid destination.

## Velocity Projection

A useful release estimate is current position plus a bounded velocity horizon:

\[
p_{projected} = p + \operatorname{clamp}(v \cdot T, -D_{max}, D_{max})
\]

`T` is a short prediction horizon and `D_max` prevents a noisy velocity sample from skipping implausibly far. Position and velocity must share units and direction.

```ts
const projected = position + clamp(
  velocity * projectionSeconds,
  -maxProjection,
  maxProjection,
);
```

Use the recognizer's exact release velocity when supported. If a later spring accepts initial velocity, pass the same signed value after documented unit conversion. Do not normalize it to a generic “fast” constant.

## Snap Selection

Choose among sorted reachable snap points using projected position, direction, and hysteresis. A deterministic pattern:

```ts
type Release = {
  position: number;
  velocity: number;
  currentIndex: number;
};

function selectSnap({ position, velocity, currentIndex }: Release, snaps: number[]) {
  'worklet';
  const projected = position + clamp(velocity * horizon, -maxProjection, maxProjection);
  const direction = Math.sign(velocity);
  const candidates = snaps.map((point, index) => ({ point, index }));
  const nearest = minBy(candidates, item => Math.abs(item.point - projected));

  const stayedNearCurrent =
    Math.abs(projected - snaps[currentIndex]) < holdRadius &&
    Math.abs(velocity) < escapeVelocity;

  if (stayedNearCurrent) return currentIndex;

  // Require directional intent before skipping past the adjacent state.
  return constrainByDirection(nearest.index, currentIndex, direction);
}
```

Hysteresis gives the current state a hold region; use a separate escape threshold for leaving it. For destructive dismissal, require a stronger and explicit commit rule. Recompute reachable snaps when Safe Area, keyboard, orientation, font scale, or content size changes.

## Current-Value Interruption

Maintain presentation state on the animation/gesture runtime and authoritative semantic state at stable boundaries. On new input:

1. Read the current presentation value and velocity if the installed API exposes them.
2. Cancel or supersede the in-flight animation.
3. Increment an operation token so an old completion cannot commit.
4. Set the gesture origin to the presentation value.
5. Track from that origin immediately and preserve velocity continuity on the next release.

```ts
// Create this inside the component with the installed Reanimated API.
const generation = useSharedValue(0);

// This function owns authoritative application state on the JS runtime.
const commitStableState = (target: number) => setStableState(target);

function retarget(target: number, initialVelocity: number) {
  'worklet';
  cancelSupportedAnimation(offset); // Replace with the installed API.
  generation.value += 1;
  const mine = generation.value;
  offset.value = springTo(target, { velocity: initialVelocity }, finished => {
    if (!finished || generation.value !== mine) return;
    runOnJS(commitStableState)(target); // Replace with the installed UI-to-JS bridge.
  });
}

function beginGesture() {
  'worklet';
  cancelSupportedAnimation(offset);
  generation.value += 1;
  gestureOrigin.value = offset.value;
}
```

The token must live in UI-runtime state shared by every participating worklet; do not mutate a module-local JavaScript `let` as a cross-worklet token. Capture the incremented numeric token in each completion callback, compare it with the current shared value, and bridge only the current finished animation's semantic commit to JavaScript. `runOnJS` is representative: confirm the installed Reanimated/Worklets version and use its supported UI-to-JS scheduling API and signature. If the completion has no semantic JS work, keep it on the UI runtime instead.

Treat cancellation, failure, and finalization as distinct if the installed recognizer does. Make cleanup idempotent. Never use an “isAnimating” flag to reject all input.

## Gesture and Scroll Ownership

Write an ownership table before composing recognizers:

| Condition | Owner | Transfer rule |
| --- | --- | --- |
| Content can scroll in drag direction | Scroll | Manipulator waits or runs simultaneously without moving |
| Scroll is at boundary and drag continues outward | Parent/manipulator | Transfer only after direction and boundary are stable |
| Horizontal navigation-back region | Navigation | Child fails or defers according to explicit edge rule |
| Drag begins on dedicated handle | Manipulator | Activate after touch slop; scroll remains available elsewhere |
| Recognizer cancels or view unmounts | Neither | Finalize idempotently; restore a reachable stable state |

Use the installed Gesture Handler relationship APIs for simultaneous, exclusive, or require-to-fail behavior. Avoid toggling recognizers from JS on every frame. Include nested lists, refresh controls, system navigation gestures, keyboard dismissal, and modal/sheet parents in the model.

## Reduced-Motion Substitutions

Keep cause and state visible while reducing spatial travel:

| Standard behavior | Reduced-motion substitute |
| --- | --- |
| Gesture tracking | Keep 1:1 tracking; remove added parallax or overshoot |
| Long slide across screen | Short crossfade or immediate placement with highlight |
| Zoom from source | Fade/scale only minimally, or dissolve while preserving focus |
| Spring with overshoot | Critically damped settle or short non-spatial transition |
| Background parallax | Static background |
| Repeated decorative loop | Static affordance or one semantic state change |

Read the preference through an API supported by the installed stack. Preserve haptic/visual confirmation when it is causal and allowed; Reduced Motion is not a blanket “disable feedback” switch. Provide buttons or actions for gesture-only flows and verify focus and announcements.

## Materials Checklist

- What hierarchy or separation does the material communicate?
- Is text readable over the worst underlying content and at increased contrast?
- Is an opaque/simplified fallback available for reduced transparency or unsupported blur?
- Are clipping, corners, shadow/elevation, and overlay order coherent during motion?
- Does the material remain stable during keyboard, rotation, and scrolling?
- Was rendering cost observed on relevant iOS and Android hardware, not inferred?
- Were Android blur/elevation/color differences checked independently?
- Are nested translucent layers limited enough to preserve hierarchy?

## Haptic Causality

Bind feedback to semantic edges, not animation frames:

```ts
const crossed = previousSide !== nextSide;
const eligible = crossed && !alreadyFiredForGesture && platformMethodIsSupported;

if (eligible) {
  alreadyFiredForGesture = true;
  scheduleAtVisibleCommit(() => triggerSupportedFeedback());
}
```

Reset the per-gesture guard only when a new gesture begins or when deliberate reverse crossing should produce a second meaningful event. Suppress feedback for cancellation, stale completions, and passive programmatic motion unless it confirms a user-caused state change. Verify library version, OS support, user settings, simulator limitations, and physical-device behavior. Provide a visible or semantic equivalent.

## Device Verification

Use a small matrix and retain evidence:

| Area | iOS simulator | iOS device | Android emulator | Android device |
| --- | --- | --- | --- | --- |
| Press-down latency and visual response |  |  |  |  |
| Slow/fast drag and exact re-grab |  |  |  |  |
| Projection, direction, hysteresis |  |  |  |  |
| Scroll/navigation ownership |  |  |  |  |
| Blur/material/contrast/clipping |  |  |  |  |
| Haptics/sound synchronization | N/A or limited |  | N/A or limited |  |
| Reduced Motion and large type |  |  |  |  |
| Keyboard, Safe Area, rotation |  |  |  |  |

For every cell, record **observed**, **code-supported**, **not available**, or **unverified**. Save a short recording for threshold, interruption, and material issues when possible. A compile, typecheck, unit test, or emulator run supports only the claim it actually exercises; never upgrade it into a physical-device claim.
