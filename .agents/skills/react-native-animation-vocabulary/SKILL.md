---
name: react-native-animation-vocabulary
description: Identify canonical vocabulary for mobile motion, gesture, and animation effects from plain-language descriptions. Use when a user describes a mobile or React Native interaction without knowing its term, asks what an interaction is called, or wants precise platform-neutral vocabulary for a prompt or design discussion. Focus on reverse lookup and naming, not design, implementation, or code review.
---

# React Native Animation Vocabulary

## Core Rule

Name the underlying interaction or motion concept, not merely a visual resemblance or a framework API. Prefer the established canonical term that will retrieve useful design and engineering material—for example, **velocity handoff**, **momentum projection**, or **origin-aware animation**—over an invented descriptive label.

Do not turn a naming request into design advice or implementation. Stop after identifying and disambiguating the concept unless the user explicitly asks a follow-up implementation question.

## Response Contract

Return, in this order:

1. **Canonical term** first.
2. A one-sentence definition.
3. A brief explanation of why it matches the described behavior.
4. One or two nearest alternatives only when they clarify a real ambiguity.
5. Confidence or ambiguity when the description supports multiple terms.

Keep a straightforward lookup short. Preserve meaningful qualifiers such as *gesture-driven*, *interruptible*, *scroll-linked*, or *origin-aware* when they distinguish the requested behavior.

## Disambiguation

Distinguish effects by the observable behavior that makes the term useful:

- Separate input coupling from motion after release: **1:1 tracking** describes movement during the gesture; **decay/inertia** describes continued movement after release.
- Separate release prediction from destination selection: **momentum projection** predicts the endpoint from velocity; a **snap point** is the destination itself.
- Separate transfer from continuation: **velocity handoff** carries velocity between phases or components; **decay/inertia** continues motion under diminishing velocity.
- Separate geometry correspondence from property tweening: **shared element transition / matched geometry** preserves identity across layouts; **morph** changes shape or form.
- Separate elastic boundary response from spring settling: **rubber-banding** is resistance beyond a boundary; **spring** describes the return or target motion.
- Separate transform pivot from spatial correspondence: **origin-aware animation** animates around a meaningful origin; matched geometry connects corresponding elements across layouts.

State uncertainty explicitly when details overlap. Ask for a video, screen recording, or relevant code only when release behavior, gesture coupling, transition identity, or another unseen detail would materially change the term.

## Platform and API Names

Give the platform-neutral concept first, then an API or framework name only as a secondary note when useful. Do not mistake APIs such as SwiftUI `matchedGeometryEffect` or React Native Reanimated gesture/animation primitives for the general concept they implement. Treat “matched geometry” as a common conceptual alias for a shared element transition, while noting platform-specific naming only when it helps the user search.

Keep the scope native-mobile while allowing platform-neutral motion terminology. Stop before writing SwiftUI, UIKit, Jetpack Compose, Android Views, or React Native implementation code.

## Reference Use

Read [references/glossary.md](references/glossary.md) when resolving a term, comparing close alternatives, or producing a search/prompt phrase. Use its definitions and confusions as guidance rather than dumping the glossary into the response.

## Routing

If the user explicitly continues beyond naming:

- Route React Native interaction implementation to **react-native-design-engineering**.
- Route a broad project-wide animation audit or overhaul to **improve-react-native-animations**.
- Route a strict animation, gesture, haptics, or reduced-motion review to **review-react-native-animations**.
- Apple-principle explanations are outside naming scope. If an Apple-specific React Native design skill is available, route to it; otherwise state the boundary and keep the answer to terminology.

For SwiftUI, UIKit, Compose, or Views implementation, identify the concept and stop; use an appropriate platform-specific skill rather than extending this vocabulary skill.

## Common Mistakes

- Do not coin plausible-sounding labels when a canonical term exists, such as “velocity continuity” for **velocity handoff**, “projected-endpoint snapping” for **momentum projection**, or “anchored scale transition” for **origin-aware animation**.
- Do not answer with a library method, component, modifier, or hook when the user wants the effect's name.
- Do not collapse related concepts into one label: snapping is not projection, tracking is not inertia, and overshoot is not rubber-banding.
- Do not list many alternatives to avoid choosing. Lead with the best match and explain the discriminator.
- Do not ask for artifacts when the description already distinguishes the term.
- Do not provide unsolicited code, architecture, or tuning values.
