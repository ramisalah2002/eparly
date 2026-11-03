---
name: review-react-native-animations
description: Use when reviewing Expo or React Native animation, gestures, motion, haptics, reduced motion, accessibility, or interaction polish and the assessment must distinguish verified native behavior from assumptions.
---

# Review React Native Animations

## Core Principle

Review only what evidence supports. Separate observed behavior, code-supported risk, and unverified state. Missing evidence is not a finding.

This is a review task. Do not implement source changes unless the user explicitly requests implementation.

## Review Workflow

1. **Gate scope.** Confirm Expo or React Native on iOS/Android. Hard-stop Web-only, SwiftUI/UIKit, Android Views/Compose, and other non-React-Native targets; state the boundary and produce no platform code. Treat Web-only APIs found in purported native code as feasibility issues.
2. **Inventory evidence.** Inspect source, installed Expo/RN and dependency versions, configuration, target platforms, and supplied reproductions, recordings, traces, or tests. Record omissions.
3. **Trace the interaction.** Follow initiation, tracking, commit, cancellation, interruption/re-grab, completion, recovery, gesture ownership, callbacks, exit mounting, and alternate input.
4. **Assess.** Check purpose/frequency, response, timing, physicality, manipulation, conflicts, performance, accessibility, reduced motion, haptics, and platform consistency.
5. **Report.** Prioritize demonstrated impact, cite code or observations, recommend the smallest useful change, give a reproducible check, and mark unsupported claims unverified.
6. **Verdict.** State overall quality, top validated risk or evidence gap, and one highest-value next action.

## Non-Negotiable Standards

- Require native-capable primitives. CSS transitions, `@starting-style`, DOM APIs and browser-only Pointer Event APIs such as `setPointerCapture`, WAAPI, browser media queries, and Framer Motion are Web-only unless isolated to React Native Web. Do not confuse them with React Native's supported `onPointer*` events; verify those against the installed version.
- Require immediate response, bounded timing, mounted exits, and state changes that do not wait for decoration.
- Require 1:1 tracking before deliberate resistance, stable activation/dominant-axis intent, and distance-plus-velocity release decisions where relevant.
- Require interruption from the presentation value, cancellation/finalization, superseded-callback protection, and valid recovery.
- Require gesture ownership with scroll, sheets, navigation, parents, Safe Area, and keyboard changes.
- Prefer UI-thread shared values and transform/opacity per frame. Flag JS-thread state updates only when code proves them; verify layout, virtualization, and dropped-frame claims.
- Require Reduce Motion that preserves state communication, non-gesture alternatives, roles/state, focus/announcements as needed, and adequate targets.
- Allow causal, supported, restrained haptics at commit/snap/success/warning/error only; reject blanket or per-frame haptics.

## Priorities

| Priority | Meaning |
| --- | --- |
| Blocker | The interaction cannot work natively, or presents severe accessibility or data-loss risk. |
| High | Frequent failure, broken gesture ownership, inaccessible completion, or materially misleading feedback. |
| Medium | Meaningful but recoverable degradation in clarity, interruption, physicality, performance, or platform consistency. |
| Low | Limited-impact polish, consistency, or maintainability issue. |

Prioritize harm and failure before polish. Incomplete evidence stays unverified; it does not increase severity.

## Required Output

Start with scope and evidence reviewed. Then use exactly this findings table:

| Priority | Current behavior | Recommended change | Why | Verification |
| --- | --- | --- | --- | --- |

Link **Current behavior** to an observation or code location. Give a minimal change, impact, and reproducible check with platform status. With no validated findings, retain the table, say so, and list evidence gaps outside it as unverified.

After the table, provide this verdict:

- **Overall quality:** Concise assessment grounded in the findings and verified coverage.
- **Top risk:** Highest-impact validated issue; if none is validated, the most consequential evidence gap, labeled unverified.
- **Highest-value next action:** The single action that most improves quality or confidence.

## Evidence and Verification

- Label evidence `Observed`, `Code-supported`, or `Unverified`.
- Record inspected versions, platform/OS, device/simulator, build mode, scenario, and unverified states. Never call an unrun case observed.
- Request missing source, configuration, reproduction, recordings, logs, traces, or device results when conclusions depend on them.
- Use static evidence for compatibility/control flow; require runtime evidence for feel, frames, conflicts, accessibility behavior, haptics, and device claims.
- Specify initial state, action, expected result, platform/device, and whether verification actually ran.

## Platform Guardrails

- Review React Native Web only when explicit and separate from iOS/Android; Web support never proves native support.
- Check iOS and Android separately for back, keyboard, scroll/recognizer arbitration, haptics, materials/elevation, accessibility, and reduced motion.
- Verify APIs against installed versions, not current documentation alone.
- Hard-stop implementation or translation in SwiftUI/UIKit, Compose/Views, or browser-only stacks. Ask for an Expo/React Native target or another applicable skill.

## Common Review Mistakes

- Returning free-form bullets instead of the required findings table and verdict.
- Rewriting before establishing behavior and impact.
- Treating absent runtime evidence as proof, or claiming parity from one simulator/static inspection.
- Missing stale callbacks, cancellation, re-grab, exit mounting, or velocity while focusing on easing.
- Over-prescribing bounce, haptics, or decoration; removing motion without preserving state communication.
- Converting Web APIs, producing out-of-scope platform code, or editing source without an explicit request.
