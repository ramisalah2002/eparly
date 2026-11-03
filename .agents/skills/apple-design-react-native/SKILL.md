---
name: apple-design-react-native
description: Apply fluid Apple-inspired interaction principles to Expo and React Native implementation decisions on iOS and Android. Use when an Expo/React Native interaction needs Apple-style responsiveness, direct manipulation, spring or gesture behavior, velocity and snap logic, materials and depth, multimodal feedback, typography, accessibility, or design-principle reasoning. Do not use for Web-only, SwiftUI, UIKit, Jetpack Compose, or Android Views implementation.
---

# Apple Design for React Native

## Core Idea

Make the interface feel like a responsive physical system whose behavior clarifies state and preserves user agency. Start from the interaction's cause, spatial model, and platform evidence; add polish only when it strengthens hierarchy, continuity, or feedback. Treat delight as the outcome of coherence and craft, not an effect to sprinkle on top.

Stay within Expo and React Native implementation. Translate principles into the project's installed native-capable stack rather than inventing SwiftUI/UIKit equivalents.

## Decision Workflow

Follow this order deterministically:

1. **Gate scope.** Continue only for Expo or React Native targeting iOS or Android. Apply the hard stops in Routing before analysis or code.
2. **Inspect evidence.** Read `package.json`, the lockfile, Expo config, native folders when present, and relevant source. Record the installed Expo, React Native, Reanimated, and Gesture Handler versions before naming APIs.
3. **State the contract.** Define initiation, continuous feedback, gesture ownership, bounds, release prediction, snap/commit rules, cancellation, interruption, completion, and accessible alternatives.
4. **Choose behavior.** Prefer direct tracking during input, a spring for retargetable settling, and projected momentum only when release velocity should matter. Establish spatial origin, hierarchy, and boundary behavior.
5. **Map to the stack.** Reuse existing primitives and version-gate every proposed API. Read [fluid-interactions.md](references/fluid-interactions.md) when implementing gesture math, interruption, reduced motion, materials, haptics, or verification.
6. **Verify.** Test behavior, not just compilation. Separate code-supported expectations from simulator/device observations and from unverified claims.

If the request is underspecified, propose the smallest reversible behavior contract and label assumptions. Ask only when a missing choice changes navigation, data loss, destructive commitment, or platform scope.

## Response and Direct Manipulation

- Respond on press-down, not after press completion. Use restrained scale, highlight, opacity, or material change appropriate to frequency and meaning.
- Couple dragged content continuously to input at 1:1 within its ordinary range. Do not introduce lag, smoothing, or thresholds after ownership is established.
- Keep application state changes independent from ornamental animation completion. Never lock out new input merely because an animation is running.
- Preserve a visible relationship between the control, the manipulated object, and the result. Favor familiar placement and immediate reversibility when possible.
- Define gesture activation before movement ownership: axis intent, touch slop, simultaneous recognition, and parent/child priority must be deliberate.

## Interruptibility and Springs

- Retarget from the current presentation value and current velocity, not from a stale logical endpoint. A re-grab must feel continuous.
- Handle end, cancellation, failure, and finalization. Ensure superseded completions cannot commit stale state or fire duplicate callbacks.
- Treat a spring as behavior: it expresses how displaced content settles and how interrupted motion continues. Prefer a critically damped or near-critically-damped response by default.
- Allow visible bounce only when it follows real momentum, communicates a boundary, or supports an intentionally playful moment. Do not add bounce to routine confirmations.
- Keep the interaction responsive while settling. New gestures and state changes must be able to cancel or retarget motion immediately.

## Velocity and Projection

- Hand release velocity into the settling or decay phase without arbitrary replacement, sign loss, or discontinuity. Convert units explicitly when APIs differ.
- Project momentum before choosing a snap point. Consider current position plus a bounded velocity contribution rather than velocity or distance alone.
- Include release direction and hysteresis in selection so a small reversal does not accidentally cross a state boundary and a settled state does not chatter.
- Clamp unreachable destinations and validate off-screen dismissal against Safe Area, orientation, keyboard, and content size.
- Tune projection and snap rules from recordings on real interaction paths; do not claim device feel from constants alone.

## Spatial Consistency and Boundaries

- Use progressive resistance beyond a meaningful boundary while preserving 1:1 movement inside it. Return from the displayed resisted position without a jump.
- Make entry and exit spatially symmetric unless the navigation model provides a clear reason not to. Preserve direction, scale origin, and layering relationships.
- Derive presentation origins from the source control or manipulated object when that relationship matters. Update geometry after rotation, layout, keyboard, or window changes.
- Keep backgrounds, overlays, and content in a stable depth order. Avoid teleporting objects between unrelated origins.
- Define scroll/gesture ownership at every boundary, including when a nested scroll view reaches its edge.

## Materials and Depth

- Use material changes to communicate hierarchy, grouping, or separation. A surface should become more prominent because its role changed, not because blur looks premium.
- Use blur and translucency cautiously. Maintain readable contrast over variable content and provide an opaque or simplified fallback when needed.
- Verify iOS and Android separately: blur support, rendering cost, color appearance, elevation, clipping, and reduced-transparency behavior can differ.
- Prefer a small, consistent depth vocabulary using surface color, shadow/elevation, scale, and occlusion. Avoid stacking multiple ambiguous glass layers.
- Never state that a material is performant or visually equivalent across devices unless it was observed there.

## Haptics and Sound

- Trigger haptics or sound only for a meaningful causal event such as crossing a commit threshold, reaching a discrete snap, completing a success, or signaling a warning/error.
- Synchronize feedback with the visible state event. Guard against repeated threshold crossings, cancelled gestures, duplicate completion callbacks, and unsupported platforms.
- Avoid feedback on every frame, every drag update, routine navigation, or purely decorative motion.
- Respect system/user settings and offer a visual or semantic equivalent. Verify the installed library and device support before promising a haptic or audio result.

## Accessibility

- Preserve immediate feedback, direct 1:1 tracking, state clarity, and meaningful confirmation under Reduced Motion. Remove or shorten vestibular travel, parallax, zoom, sweeping transitions, and overshoot.
- Substitute crossfade, highlight, instant placement, or a short non-spatial transition where continuity still needs explanation. Do not disable all feedback.
- Provide a non-gesture route for every essential action. Ensure roles, labels, state, focus order, announcements, and target size match the interaction's meaning.
- Respect font scaling and larger content without hiding snap points, clipping actions, or breaking gesture bounds. Recompute geometry when content size changes.
- Treat reduced transparency, contrast, screen reader, switch/keyboard input, and platform accessibility settings as verification cases when relevant.

## Typography and Design Foundations

- Prefer system typography unless the product has an established accessible type system. Support Dynamic Type/font scaling and test truncation, wrapping, and maximum sizes rather than disabling scaling.
- Establish hierarchy through role, size, weight, spacing, and placement before relying on motion or translucency.
- Preserve wayfinding: users should understand where they are, what changed, and how to return. Preserve agency with cancel, undo, re-grab, or explicit commitment where appropriate.
- Favor familiarity and simplicity in frequent flows. Spend complexity only where it improves comprehension or emotional character.
- Judge craft through alignment, response, transition continuity, platform fit, accessibility, and edge-state recovery. Let delight emerge from those qualities.

## React Native Mapping

- Inspect installed versions before proposing imports or API signatures. Never assume the latest Expo, React Native, Reanimated, Gesture Handler, haptics, blur, or audio API.
- Prefer UI-thread-capable shared values/worklets for continuous gesture-driven transforms when supported by the installed stack. Keep authoritative application state synchronized at stable boundaries.
- Use Gesture Handler relationships deliberately for simultaneous, exclusive, or failure behavior. Include scroll, navigation-back gestures, parents, and nested children in the ownership model.
- Prefer transform and opacity for high-frequency per-frame updates. Use layout animation when layout change is the meaning and verify its platform and list behavior.
- Map accessibility preferences through supported Expo/RN APIs or project abstractions, with version-gated fallbacks. Do not paste SwiftUI/UIKit concepts as if they were React Native APIs.
- Patch the existing architecture and tokens. Introduce a dependency only when the current stack cannot express the required native behavior and the tradeoff is explicit.

## Verification

Use this completion gate:

1. Confirm the dependency versions and configuration that support each chosen API.
2. Exercise press-down, slow/fast drag, reversal, release near every threshold, re-grab during motion, rapid repeated input, cancellation, and navigation away.
3. Verify scroll ownership, nested gestures, Safe Area, keyboard, orientation, font scale, and reachable bounds where applicable.
4. Verify Reduced Motion and the non-gesture alternative; check screen-reader meaning and focus behavior for essential actions.
5. Observe iOS and Android separately for materials, clipping, performance, haptics, and platform-specific fallbacks.
6. Report each result as **observed**, **code-supported**, or **unverified**. Never turn a successful build, simulator check, or static reading into an unverified device claim.

## Routing

Apply the first matching rule:

1. **Hard stop:** For Web-only, SwiftUI, UIKit, Jetpack Compose, or Android Views implementation, state that this skill cannot produce that implementation and stop. Do not translate the request into React Native silently.
2. **Terminology lookup:** Route requests primarily asking what an effect is called to `react-native-animation-vocabulary`.
3. **Strict review:** Route a diff, component, or evidence-based animation/gesture review to `review-react-native-animations`.
4. **Whole-project work:** Route a multi-file or whole-project audit and improvement program to `improve-react-native-animations`.
5. **Direct non-Apple implementation:** Route ordinary React Native interaction implementation without Apple-principle reasoning to `react-native-design-engineering`.
6. **Apple-principle implementation:** Continue here when the task needs Apple-style interaction reasoning translated into Expo/React Native decisions. If implementation grows into a broad project audit, hand off using rule 4.

## Common Mistakes

- Delaying all response until `onPress`, or animating a drag behind the finger.
- Restarting from a model endpoint, discarding release velocity, or blocking input during settling.
- Calling any soft spring “Apple-like” without defining damping, interruption, or purpose.
- Selecting snaps from distance alone, ignoring direction, or omitting hysteresis near thresholds.
- Applying a linear clamp outside bounds instead of progressive resistance, then jumping on release.
- Using the screen center as every transition origin or making exits unrelated to entrances.
- Treating blur, translucency, heavy shadow, bounce, haptics, or sound as a style preset.
- Removing all feedback for Reduced Motion or leaving essential actions gesture-only.
- Freezing font scale, truncating large type, or relying on animation to establish hierarchy.
- Naming APIs from memory, assuming platform parity, or reporting simulator behavior as device verification.
