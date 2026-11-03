# Motion Patterns

## Contents

- [Native mapping](#native-mapping)
- [Interaction patterns](#interaction-patterns)
- [Gesture mechanics](#gesture-mechanics)
- [Composition and ownership](#composition-and-ownership)
- [Platform and runtime verification](#platform-and-runtime-verification)
- [Representative interruptible swipe card](#representative-interruptible-swipe-card)

## Native mapping

Inspect `package.json`, the lockfile, Expo SDK, New Architecture settings, and installed versions before copying an API signature.

| Need | Native mapping | Decision check |
| --- | --- | --- |
| Known-duration state change | Reanimated timing | Keep durations short and state-driven; retain exits until completion. |
| Spatial settling | Reanimated spring | Tune damping/stiffness for purpose; do not add bounce by default. |
| Released momentum | Reanimated decay | Clamp bounds and define what interrupts or follows the decay. |
| Direct manipulation | Gesture Handler updates into shared values | Keep updates on the UI thread and track 1:1 before deliberate resistance. |
| Haptic confirmation | Installed native haptic module, often Expo Haptics | Emit once at a causal commit/snap/success/warning/error, never per frame. |
| Reduced motion | Reanimated's installed, version-appropriate reduce-motion API plus OS preference | Preserve state meaning while removing or shortening nonessential travel, scale, parallax, and bounce. |
| Blur/material | Existing native blur/material package | Verify readability, fallback, and performance on both platforms; do not claim identical rendering. |
| Layout transition | Reanimated layout transition or deliberate layout animation | Use only when layout change communicates meaning; verify lists and interruption. |

Prefer transform and opacity for per-frame work. This is a performance default, not a ban on deliberate layout animation. Keep shared-value updates off React's render loop.

## Interaction patterns

### Press

- Give immediate press-down feedback; use subtle opacity/scale/color change appropriate to frequency.
- Return from the current presentation value when released or cancelled.
- Keep the action independent from decorative completion and preserve `Pressable` semantics, focus, role, state, and touch target.

### Modal or sheet

- Define open, intermediate, closed, and dismissed snap points relative to usable bounds.
- Keep content mounted through exit. Coordinate backdrop, focus, navigation ownership, keyboard, Safe Area, and nested scrolling.
- Use velocity plus projected position on release; spring to a snap or commit dismissal off-screen.

### Drag or swipe card

- Activate after a small threshold, lock intent when axes compete, and track continuously.
- Project the endpoint from translation plus velocity. Commit only past a documented threshold; otherwise spring back.
- Provide buttons or accessibility actions for the gesture outcome and distinguish destructive actions.

### List changes

- Animate insert, remove, and reorder only when they clarify causality. Preserve stable keys and virtualization.
- Avoid JS-thread per-frame state, whole-list relayout churn, and simultaneous effects that obscure the changed item.
- Verify rapid consecutive edits, interrupted transitions, scrolling during change, and reduced motion.

### Loading

- Prefer stable geometry and meaningful progress. Avoid perpetual, high-amplitude, or synchronized decoration.
- Reduce or stop nonessential loops when hidden, backgrounded, complete, or Reduce Motion is enabled.
- Make success/error state changes legible without depending on motion or haptics alone.

### Frequent actions

- Optimize for immediate response and low fatigue. Skip decorative entrances and bounce.
- Use haptics only when a successful state transition needs tactile confirmation; suppress rapid repeats.

## Gesture mechanics

### Activation and axis intent

Set a small activation threshold before taking ownership. If vertical scroll and horizontal swipe compete, use directional failure/activation offsets or composed recognizers supported by the installed Gesture Handler version. Lock to the dominant axis only after intent is clear.

### Velocity, projection, and release

Use a documented projection such as `projected = current + velocity * horizon`. Choose the horizon, distance threshold, and velocity threshold from the interaction's scale and verify them with slow drags and quick flicks. Decide commit, dismiss, or nearest snap from the projected endpoint; do not ignore velocity or use it alone.

### Interruption and retargeting

On begin, cancel or supersede the prior animation and capture the current shared/presentation value. Update from that value so a re-grab does not jump. A new destination must begin from wherever the element is currently displayed, not its prior logical endpoint.

### Cancellation and finalization

Handle normal end separately from cancellation/failure. Use finalization to clear pressed/dragging state and restore a valid destination when ownership is lost. Invoke business callbacks only after a committed transition succeeds; make cancelled animation callbacks harmless.

### Rubber-banding

Apply resistance only beyond valid bounds and keep the relationship monotonic. Do not use rubber-banding before activation or obscure whether release will snap, commit, or dismiss.

## Composition and ownership

- **Nested scroll:** Decide whether the child gesture waits for, runs simultaneously with, or excludes the scroll recognizer. Test at content boundaries and mid-scroll.
- **Sheets:** Let content scroll within bounds and transfer downward drag ownership only under explicit conditions, commonly at scroll offset zero.
- **Navigation:** Check platform back-swipe and edge gestures. Reserve edge space or establish recognizer relationships deliberately.
- **Safe Area:** Derive off-screen destinations and snap points from the usable viewport and insets, not a hard-coded screen size alone.
- **Keyboard:** Recompute reachable sheet/card positions for keyboard show/hide and interactive dismissal; verify focused fields remain visible.
- **Orientation and bounds:** Recalculate thresholds and destinations after size changes. Do not leave an interrupted item stranded outside valid bounds.

## Platform and runtime verification

Treat platform differences as checks, not facts inferred from the code:

- On iOS, check back-swipe competition, Reduce Motion, haptic availability, keyboard dismissal, Safe Area, and material/blur rendering.
- On Android, check system back behavior, predictive back where applicable, overscroll/nested scrolling, keyboard resize/pan behavior, navigation insets, haptic support, and elevation/clipping.
- On both, check touch cancellation, rapid re-grab, slow drag, high-velocity flick, multi-touch, rotation/resize, list scrolling, screen-reader alternatives, and app background/foreground transitions.

Use a simulator/emulator for deterministic layout and interaction iteration, then use physical devices for touch feel, haptics, performance, and platform integration. Report exactly what ran: platform, runtime, device or simulator, build mode, and scenario. Say "unverified" for every unavailable device or platform; never convert code inspection into an observed runtime claim.

## Representative interruptible swipe card

This TypeScript pattern demonstrates the control flow, not a drop-in component. Inspect installed Reanimated, Gesture Handler, and Expo Haptics versions before copying imports, callback signatures, reduce-motion configuration, or gesture composition APIs. `ReduceMotion.System` is available in current Reanimated releases; use the documented version-gated equivalent when the installed version differs.

```tsx
import { useMemo } from "react";
import { StyleSheet } from "react-native";
import * as Haptics from "expo-haptics"; // Only when already installed/approved.
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  ReduceMotion,
  cancelAnimation,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";

type Props = {
  children: React.ReactNode;
  width: number;
  hapticsEnabled?: boolean;
  onCommit: (direction: "left" | "right") => void;
};

const SPRING = {
  damping: 20,
  stiffness: 220,
  reduceMotion: ReduceMotion.System,
};

export function SwipeCard({ children, width, hapticsEnabled, onCommit }: Props) {
  const x = useSharedValue(0);
  const startX = useSharedValue(0);
  const committed = useSharedValue(false);

  const finishCommit = (direction: "left" | "right") => {
    if (hapticsEnabled) void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    onCommit(direction);
  };

  const pan = useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX([-8, 8])
        .failOffsetY([-12, 12])
        .onBegin(() => {
          cancelAnimation(x);
          startX.value = x.value;
          committed.value = false;
        })
        .onUpdate((event) => {
          x.value = startX.value + event.translationX;
        })
        .onEnd((event) => {
          const projected = x.value + event.velocityX * 0.18;
          if (Math.abs(projected) < width * 0.3) {
            x.value = withSpring(0, SPRING);
            return;
          }

          committed.value = true;
          const direction = projected < 0 ? "left" : "right";
          const target = (direction === "left" ? -1 : 1) * width * 1.25;
          x.value = withTiming(
            target,
            { duration: 180, reduceMotion: ReduceMotion.System },
            (finished) => {
              if (finished) runOnJS(finishCommit)(direction);
            },
          );
        })
        .onFinalize((_event, success) => {
          // `success === false` covers cancellation/failure after ownership loss.
          if (!success && !committed.value) x.value = withSpring(0, SPRING);
        }),
    [hapticsEnabled, onCommit, width],
  );

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: x.value }],
  }));

  return (
    <GestureDetector gesture={pan}>
      <Animated.View style={[styles.card, animatedStyle]}>{children}</Animated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({ card: { overflow: "hidden" } });
```

The example emits at most one optional haptic, after the off-screen commit finishes; no update callback emits tactile feedback. In a production card, also expose equivalent accessibility actions, clear pending callbacks on unmount, and define parent-stack ownership after `onCommit`.
