# Antigravity UI/UX Skills Ecosystem: Installation & User Guide

This document details the configuration, architecture, and operational workflows for the professional UI/UX skills ecosystem installed for **Google Antigravity**.

The system transforms Antigravity into an elite product design director and frontend engineering specialist, replacing generic AI-generated templates with distinctive, accessible, production-grade user interfaces.

---

## 1. Installed Skills & Ecosystem Overview

| Skill Name | Version / Commit | Official Source Repository | Primary Scope | Primary Responsibility |
|:---|:---:|:---|:---|:---|
| **`product-design-director`** | `1.0.0` | Custom Orchestration Skill | Global & Workspace | Master conductor: enforces 6-step workflow, 3-concept visual exploration, token architecture, and cross-skill routing. |
| **`ui-quality-auditor`** | `1.0.0` | Custom Audit Skill | Global & Workspace | Design QA auditor: evaluates interfaces across 6 dimensions via a 100-point rubric, providing prioritized code fixes. |
| **`ui-ux-pro-max`** | `2.15.0` | [`nextlevelbuilder/ui-ux-pro-max-skill`](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) | Global & Workspace | Design intelligence database: 79 styles, 192 product palettes, 74 font pairings, 119 UX guidelines, and stack presets. |
| **`frontend-design`** | `Latest` | [`anthropics/skills`](https://github.com/anthropics/skills) | Global & Workspace | High-craft aesthetics: distinctive typography, non-generic layouts, and deliberate avoidance of standard AI clichés. |
| **`web-design-guidelines`** | `1.0.0` | [`vercel-labs/agent-skills`](https://github.com/vercel-labs/agent-skills) | Global & Workspace | Vercel Web Interface Guidelines: accessibility (WCAG AA), visible focus rings, keyboard navigation, and HTML semantics. |
| **`vercel-react-best-practices`** | `1.0.0` | [`vercel-labs/agent-skills`](https://github.com/vercel-labs/agent-skills) | Global & Workspace | React & Next.js performance guidelines: 70 rules across 8 categories (composition, bundle size, memoization, RSC). |
| **`react-best-practices`** | `1.0.0` | [`vercel-labs/agent-skills`](https://github.com/vercel-labs/agent-skills) | Global & Workspace | Compatibility alias mirroring `vercel-react-best-practices`. |
| **`expo-native-ui`** | `Latest` | [`expo/skills`](https://github.com/expo/skills) | Global & Workspace | Apple HIG & Material Design styling for React Native / Expo: SF Symbols, semantic system colors, and mobile layout. |
| **`expo-design-system`** | `Latest` | [`expo/skills`](https://github.com/expo/skills) | Global & Workspace | Mobile token architecture: colors, spacing, radius, typography, and reusable component extraction rules. |
| **`expo-animation`** | `Latest` | [`expo/skills`](https://github.com/expo/skills) | Global & Workspace | 60/120fps mobile motion: Reanimated 4, Gesture Handler, spring physics, and haptic feedback. |
| **`expo-router`** | `Latest` | [`expo/skills`](https://github.com/expo/skills) | Global & Workspace | File-based mobile navigation: Native Stack, Tabs, modal form sheets, headers, and deep linking. |
| **`expo-ui`** | `Latest` | [`expo/skills`](https://github.com/expo/skills) | Global & Workspace | Native SwiftUI (iOS) & Jetpack Compose (Android) controls via `@expo/ui` (sheets, pickers, switches, segmented controls). |

---

## 2. Filesystem Installation Locations

To ensure full cross-project availability and compatibility with all Antigravity surfaces, skills are deployed across:

1. **Antigravity App / IDE Global Scope**:
   - `~/.gemini/config/skills/<skill-name>/`
   - Explicitly mapped via `~/.gemini/config/skills.json`
2. **Antigravity CLI Global Scope**:
   - `~/.gemini/antigravity-cli/skills/<skill-name>/`
3. **Universal Agent Scope**:
   - `~/.agents/skills/<skill-name>/`
4. **Current Workspace Scope**:
   - `/Users/salah-eddinerami/Documents/eparly/.agents/skills/<skill-name>/`
   - Explicitly registered via `.agents/skills.json`
   - Pre-existing skills preserved: `banner-design`, `brand`, `design`, `design-system`, `slides`, `ui-styling`.

---

## 3. How Automatic Skill Selection Works

Antigravity employs **Progressive Disclosure** and **Hierarchical Discovery**:

1. **Discovery Hierarchy**: Antigravity scans the workspace `.agents/` folder first, followed by explicit configurations (`skills.json`), and finally the global machine directory `~/.gemini/config/`.
2. **Context Budgeting**: Skills are not loaded into the LLM context upfront. Only their metadata (`name` and `description`) is injected into the agent's catalog. The full instruction text and references are activated strictly when triggered by task context or user intent.
3. **Orchestration Rules**:
   - Global rules (`~/.gemini/config/GEMINI.md` and `~/.gemini/config/AGENTS.md`) and project rules (`.agents/rules/ui-ux-orchestration.md`) route requests automatically:
     - **UI Planning / Creative Work**: Triggers `product-design-director`, pulling in `ui-ux-pro-max` for design system tokens and `frontend-design` for visual personality.
     - **React Native / Expo Tasks**: Activates `expo-native-ui`, `expo-router`, `expo-animation`, `expo-design-system`, and `expo-ui`.
     - **React / Next.js Tasks**: Activates `vercel-react-best-practices` and `web-design-guidelines`.
     - **Code Review / QA Tasks**: Triggers `ui-quality-auditor`.

---

## 4. How to Manually Invoke Skills

While automatic triggering handles most tasks, you can explicitly direct Antigravity to use any skill in your prompt:

### General UI Creation & Overhaul
```text
Act as the product-design-director to plan a new landing page for our analytics product.
Follow the 6-step workflow and propose three distinct visual concepts before writing any code.
```

### Mobile UI Implementation (Expo / React Native)
```text
Using expo-native-ui and expo-animation, build a bottom sheet transaction filter for our mobile app.
Follow Apple HIG styling and use Reanimated for smooth drag gestures.
```

### Web Performance & Next.js Architecture
```text
Review our Next.js dashboard components using vercel-react-best-practices.
Check for unnecessary client component boundaries and unoptimized re-renders.
```

### UI Quality Audit
```text
Run a ui-quality-auditor evaluation on apps/mobile/src/components/dashboard/spending-chart.tsx.
Score it against the 6-dimension rubric and output prioritized actionable fixes.
```

### Searching Design Intelligence (Offline CLI)
The bundled Python script can be run directly in the terminal without network access:
```bash
python3 ~/.gemini/config/skills/ui-ux-pro-max/scripts/search.py "fintech mobile dashboard" --domain style
python3 ~/.gemini/config/skills/ui-ux-pro-max/scripts/search.py "luxury typography" --domain typography
python3 ~/.gemini/config/skills/ui-ux-pro-max/scripts/search.py "analytics" --domain color
```

---

## 5. Technology Stack Integration

### For React Native & Expo (`apps/mobile`)
The project utilizes **Expo SDK 58**, **React Native 0.88.0**, and **React 19.3.0**:
- **Design Tokens**: Standardized via `expo-design-system` using clean TypeScript token definitions.
- **Native Primitives**: `@expo/ui` is prioritized for native bottom sheets, picker menus, and switches via `expo-ui`.
- **Navigation**: Modals, stacks, and tab layouts follow `expo-router` file-based conventions.
- **Micro-interactions**: Gesture handling and springs follow `expo-animation` using Reanimated 4 worklets.

### For Next.js & React (`apps/admin`)
The admin dashboard utilizes **Next.js 16.4.3**, **Tailwind CSS v4**, and **React 19.3.0**:
- **Architecture**: Guidelines from `vercel-react-best-practices` govern Server vs. Client Components, data fetching, and bundle splitting.
- **Accessibility**: Rules from `web-design-guidelines` enforce keyboard navigation, visible `:focus-visible` rings, and semantic HTML elements.
- **Styling**: `frontend-design` prevents the dashboard from adopting generic SaaS cliches.

---

## 6. How to Update Skills

### Updating Vercel & Anthropic & Expo Skills
Run the official maintained `skills` CLI:
```bash
# Update global skills
npx -y skills update -g

# Or update specific skill packages
npx -y skills add vercel-labs/agent-skills --skill web-design-guidelines --skill vercel-react-best-practices -g -y
npx -y skills add anthropics/skills --skill frontend-design -g -y
npx -y skills add expo/skills --skill expo-native-ui --skill expo-design-system --skill expo-animation --skill expo-router --skill expo-ui -g -y
```

### Updating UI/UX Pro Max
```bash
npm install -g ui-ux-pro-max-cli@latest
uipro update -g
```

After updating upstream skills in `~/.agents/skills/`, synchronize the files to Antigravity's global folder:
```bash
cp -R ~/.agents/skills/* ~/.gemini/config/skills/
cp -R ~/.agents/skills/* ~/.gemini/antigravity-cli/skills/
```

---

## 7. Troubleshooting & Verification

### Issue: A skill does not appear in Antigravity's active skills list
1. **Check Directory Existence**: Confirm the skill directory exists in `~/.gemini/config/skills/<skill-name>/` and has a `SKILL.md` file.
2. **Validate YAML Frontmatter**: Ensure the first line of `SKILL.md` is `---` and includes valid `name` and `description` keys.
3. **Check `skills.json`**: Verify that `~/.gemini/config/skills.json` includes `{"entries": [{"path": "~/.gemini/config/skills"}]}`.
4. **Reload Window / New Session**: AI coding assistants (including Antigravity IDE) cache skill manifests at session launch. Reload the window (`Developer: Reload Window` in IDE) or start a new chat turn.

### Issue: Script execution errors in `ui-ux-pro-max`
- Verify Python 3.9+ is available: `python3 --version`.
- The scripts rely strictly on the Python standard library; no external `pip` packages or internet access are required.

---

## 8. Manual Intervention Required
- **Session Refresh**: If Antigravity was running before installation, restart the Antigravity App or run `Developer: Reload Window` in Antigravity IDE so that the internal manifest parser reads the new `~/.gemini/config/skills/` directory.
- No sudo, root, or additional package installations are required.

