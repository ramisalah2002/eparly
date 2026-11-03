---
name: improve-react-native-animations
description: Use when a whole Expo or React Native project, or multiple files within one, need a broad motion, gesture, or animation audit, prioritized improvement planning, or execution of an approved motion plan; do not use for single-component implementation, strict diff review, Web-only work, or SwiftUI, UIKit, Jetpack Compose, and Android Views implementation.
---

# Improve React Native Animations

## Core Principle

Treat motion as a native interaction system, not decoration. Build recommendations from repository evidence, preserve the project's existing stack, and separate diagnosis, planning, and implementation. Audit and plan modes may write audit/plan artifacts but never edit application source; only `execute <plan>` may do so.

## Modes

| Mode | Scope | Output |
| --- | --- | --- |
| Default full audit | Whole project, all categories | Recon plus prioritized findings; pause for selection |
| `quick hotspots` | High-frequency or high-risk motion paths | Short findings set; pause for selection |
| `one-category <category>` | One audit category across the project or multiple files | Category findings; pause for selection |
| `plan <finding-id...>` | User-selected findings | Self-contained files under `plans/`; no source edits |
| `execute <plan-path>` | One approved plan | Source changes using the existing stack plus verification |

Route strict single-component or diff review to `review-react-native-animations`. Route direct implementation without an approved plan to `react-native-design-engineering`.
Route requests that only ask where missing motion could help, without reviewing existing animations or requesting a broad fix plan, to `find-react-native-animation-opportunities`.

## Workflow

1. **Recon.** Identify the React Native/Expo stack and versions; inspect Expo config, package manager and lockfile, Reanimated/worklets setup, Babel/Metro/native config, `GestureHandlerRootView`, navigation, Safe Area, keyboard handling, motion tokens, gesture locations, and tests. Produce a frequency map of animation/gesture primitives, helpers, and repeated values with exact paths.
2. **Audit.** Trace representative high-frequency and high-impact flows across the categories below. Read enough call sites to distinguish intentional conventions from isolated mistakes. Do not edit source.
3. **Prioritize.** Return the Findings Contract table with stable IDs and evidence. Then ask which finding IDs the user wants planned. Stop until they select findings; do not silently plan everything.
4. **Confirm and plan.** Re-open each selected finding's evidence in the current tree. Mark stale or disproved findings instead of planning them. For confirmed findings, restate the selected IDs and scope, resolve material ambiguity with the user, then write one self-contained plan per finding under `plans/`. Do not edit source.

For `quick hotspots`, abbreviate recon but still check versions, configuration, ownership, repeated primitives, and reduced-motion handling. For `one-category`, keep recon broad enough to understand that category across files.

## Audit Categories

- **Purpose and frequency:** clarify why motion exists, whether frequent motion earns its cost, and whether feedback is missing or excessive.
- **Timing and easing:** find inconsistent durations, unsuitable curves, sequencing drift, and missing shared tokens.
- **Physicality:** assess springs, momentum, velocity continuity, bounds, overshoot, and spatial origin.
- **Interruptibility and gesture ownership:** check cancellation, rapid retargeting, simultaneous/exclusive gestures, responder conflicts, and JS/UI-thread ownership.
- **Performance:** inspect render-driven animation, bridge traffic, shared-value misuse, allocation in worklets, layout animation risk, list/cardinality cost, and image/layout pressure.
- **Accessibility:** verify reduced-motion behavior, non-motion equivalents, screen-reader implications, haptic purpose and platform support, and keyboard/safe-area interactions.
- **Cohesion:** compare navigation, overlays, lists, feedback, and gestures for a recognizable motion language.
- **Missed opportunities:** identify places where native motion would materially improve causality, continuity, status, or manipulation feedback.

## Findings Contract

Use one compact table:

| ID | Priority | Category | Evidence | Current impact | Recommendation | Confidence |
| --- | --- | --- | --- | --- | --- | --- |

Use stable IDs such as `MOTION-01`. Evidence must name exact paths and relevant symbols or line locations, plus observed configuration/version facts. Separate confirmed defects from design opportunities. Rank by user impact, frequency, implementation risk, and breadth—not novelty. State unverified runtime claims explicitly. After the table, ask: “Which finding IDs should I plan?”

## Plan Contract

Write `plans/<finding-id>-<short-slug>.md`. Each plan must stand alone for a later executor and include:

1. Finding ID, goal, scope, exact files to inspect/change, and dependencies between steps.
2. Current behavior and evidence, followed by concrete desired behavior and non-goals.
3. Version/configuration checks required before implementation, including relevant Expo, React Native, Reanimated/worklets, gesture, and navigation constraints.
4. Ordered implementation steps tied to exact paths and symbols; include representative code or configuration snippets when they remove ambiguity.
5. A verification matrix covering platforms, gestures/interruption, navigation/keyboard/safe area, reduced motion, performance/regression checks, and automated/manual commands.
6. A device “feel check”: what to manipulate, observe, and compare at slow, normal, rapid, interrupted, and repeated input speeds.
7. Risks, rollback boundaries, and every state that remains unverified because a device, build, credential, or runtime was unavailable.

Do not rely on chat history, generic “update as needed” steps, or unstated file discovery. Combine findings only when their implementation is inseparable, and record that relationship.

## Execution Mode

Execute only an explicit `execute <plan-path>` request. Re-read the plan and current files, verify the plan is not stale, and follow the project's installed libraries, patterns, tokens, and architecture. Do not introduce or replace an animation/gesture library unless the plan requires it and the user has approved that scope.

Apply changes incrementally and run the plan's automated checks plus feasible platform/runtime verification. Test reduced motion, cancellation/retargeting, gesture conflicts, rapid repetition, and representative low-end performance. Record deviations from the plan and unresolved runtime checks; never claim device feel or performance was verified when it was not.

## Platform Guardrails

This skill covers native Expo and React Native application code. It may inspect platform files only to understand configuration needed by that stack.

Hard stop when the requested target is Web-only or requires implementing SwiftUI, UIKit, Jetpack Compose, or Android Views. State that the scope is unsupported and route to an appropriate web, iOS, or Android specialist. In mixed projects, continue only for the React Native/Expo surface and label excluded surfaces.

Do not reproduce the output of a strict component/diff review. This skill owns broad multi-file discovery, prioritization, planning, and execution from an approved plan.

## Common Mistakes

- Editing application source during recon, audit, or planning.
- Dumping one long critique without reusable mode boundaries or stable IDs.
- Treating package presence as proof that setup, ownership, or runtime behavior is correct.
- Creating plans before the user selects findings or without re-confirming evidence.
- Writing plans that depend on the current conversation instead of exact paths and checks.
- Recommending fashionable motion where reduced motion, interruptibility, frequency, or device cost argues against it.
- Claiming simulator/device feel, native builds, accessibility, or performance checks that were not run.
