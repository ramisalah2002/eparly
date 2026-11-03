---
name: react-native-design-engineering
description: Design, implement, or polish a specific interaction, component, screen, or motion system in an Expo or React Native app. Use for direct work on native motion, gestures, press feedback, sheets, cards, lists, loading transitions, haptics, reduced motion, or interaction composition on iOS and Android.
---

# React Native Design Engineering

## Route First

Before doing any work, evaluate these routes in order and take the first match only:

1. Reverse lookup from a described effect to its canonical motion or gesture term -> `react-native-animation-vocabulary`.
2. Apple-style interaction-principle reasoning -> `apple-design-react-native`.
3. Read-only discovery of places where motion is absent but may be valuable -> `find-react-native-animation-opportunities`.
4. Whole-project or multi-file broad audit, planning, or animation refactor—not opportunity-only discovery and not a bounded review of a named interaction, file, or diff -> `improve-react-native-animations`.
5. Strict review or findings request bounded to a concrete interaction, named file, or specified diff—not a whole-project or multi-file broad audit -> `review-react-native-animations`.

If this skill was explicitly invoked for one of those specialist requests, state the route and do not duplicate the specialist workflow. Continue here only for direct design, implementation, or polishing of a concrete Expo/React Native interaction.

## Core Principle

Make motion explain state, preserve spatial continuity, or confirm an action. Ask what purpose each effect serves and how often people encounter it. Keep frequent interactions immediate and restrained; use intentional play only where it supports the experience.

## Workflow

1. Inspect the actual Expo/React Native project, dependency files, installed versions, platform folders, and existing interaction primitives before selecting APIs. Prefer React Native Reanimated and React Native Gesture Handler when they already exist; do not assume their versions.
2. State the intended behavior, gesture ownership, commit conditions, interruption behavior, accessibility equivalent, and platform verification plan.
3. Read [motion-patterns.md](references/motion-patterns.md) for implementation details. Patch the existing stack instead of introducing a parallel animation system without cause.
4. Implement the smallest coherent behavior, including entrances/exits, loading or list transitions, Safe Area, keyboard, navigation, and scroll composition where relevant.
5. Verify on the platforms actually available. Report simulator/device and iOS/Android results separately, and label every unobserved state as unverified.

## Platform Guardrails

- Target Expo and React Native on iOS and Android. Use native React Native primitives.
- If the request is Web-only, stop: state that this skill cannot implement it and route to Web-specific guidance. Never substitute CSS, DOM, WAAPI, browser media queries, Framer Motion, or Pointer Events for a native implementation. Isolate React Native Web behavior only when it is one explicitly scoped part of an otherwise native request.
- If the request asks for SwiftUI, UIKit, Jetpack Compose, or Android Views implementation, stop: state that this skill cannot produce that platform code, offer appropriate platform-specific guidance, and do not generate code or reinterpret the request as React Native.
- Check iOS and Android behavior instead of asserting parity. Do not claim device, keyboard, haptic, blur/material, navigation, or gesture behavior that was not observed.

## Motion Decisions

- Use timing for bounded state changes with a known duration, spring for settling or retargetable spatial motion, and decay for released momentum. Reserve bounce for momentum or intentional play.
- Require immediate press-down feedback. Keep frequent actions quiet; avoid decorative entrances, repeated bounce, and slow confirmation.
- Prefer transform and opacity for per-frame motion. Use deliberate layout transitions when layout change is the meaning of the interaction and performance is verified.
- Keep exits mounted until completion. Retarget interrupted motion from its current presentation value instead of restarting from a stale endpoint.
- Define stable loading behavior and intentional insert, remove, and reorder transitions without destabilizing list virtualization.

## Gesture Decisions

- Track direct manipulation continuously at 1:1 finger movement before any documented resistance or rubber-banding.
- Use activation thresholds and dominant-axis intent where accidental diagonal capture is possible.
- Combine translation and velocity for release decisions; define projection, snap points, dismiss thresholds, and off-screen destinations explicitly.
- Handle begin, update, end, cancellation, and finalization. Stop or retarget in-flight animation from its current value.
- Decide ownership among the gesture, nested scrolling, sheets, navigation back gestures, and parent recognizers. Verify simultaneous, exclusive, or failure relationships instead of leaving conflicts implicit.
- Account for Safe Area, keyboard state, content bounds, orientation, and reachable snap points.

## Accessibility and Feedback

- Provide reduced-motion equivalents that preserve state communication while removing or shortening nonessential travel, parallax, scale, and bounce. Inspect the installed animation-library version before choosing its reduced-motion API.
- Keep touch targets, accessibility roles, state, focus, announcements, and non-gesture alternatives intact.
- Use haptics causally and sparingly at commit, snap, success, warning, or error moments only. Never emit per-frame haptics or blanket every press with feedback; verify support and suppress unsupported or rapid repeats.

## Implementation Output Order

1. Intended interaction behavior.
2. Stack and dependencies detected.
3. Implementation or patch.
4. Reduced-motion and haptic behavior.
5. Verification performed and unverified platform states.

## Reference

Read [motion-patterns.md](references/motion-patterns.md) before implementing press, entrance/exit, sheet, swipe/drag card, list, loading, gesture interruption, velocity, snap, Safe Area, keyboard, or scroll-conflict behavior.

## Common Mistakes

- Recommending Web-only APIs for native code.
- Selecting animation APIs before inspecting installed versions.
- Animating to completion without cancellation, finalization, or current-value retargeting.
- Ignoring velocity, gesture ownership, nested scrolling, Safe Area, or keyboard effects.
- Using JS-thread React state updates for per-frame gesture or animation values.
- Adding excessive bounce, blanket haptics, or no reduced-motion equivalent.
- Claiming iOS, Android, simulator, or device behavior without observing it.
- Producing platform-native code owned by another skill.
