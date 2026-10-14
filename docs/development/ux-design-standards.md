# Zalava UX/UI design standards

## Scope and principles
These standards are mandatory for changes to Zalava's user-facing interfaces, including React and server-rendered JTE/htmx screens. Favor clarity, consistency, accessibility, responsive behavior, and predictable feedback over novelty. Apply Nielsen's ten usability heuristics as a review checklist: system status visibility; real-world language; user control; consistency; error prevention; recognition over recall; efficiency; minimalist design; actionable error recovery; and accessible help.

Use WCAG 2.2 AA as the accessibility target. Consult Material Design 3 for adaptive layout, interaction patterns and states, and IBM Carbon for disciplined component systems, content hierarchy and enterprise workflows. These are references, not dependencies: preserve existing Zalava UI architecture and visual identity; do not introduce a new framework or copy another design system wholesale.

## Interaction and feedback
- Every actionable control must visibly respond to hover (where relevant), focus, press, disabled, pending, success and failure states. A click that starts asynchronous work must promptly show progress, prevent accidental duplicate submissions where appropriate, and provide a clear outcome.
- Provide explicit loading, empty, error and success states for every data-driven surface. Loading should avoid disruptive layout shifts; empty states explain what is missing and suggest a relevant next action; errors use plain language, preserve user input and offer recovery/retry where possible.
- Confirm destructive or irreversible operations; make cancellation and undo available where feasible. Distinguish navigation from actions; use meaningful labels rather than icon-only ambiguity.
- Ensure forms have persistent labels, validation near the field, understandable errors and clear required/optional indicators. Never rely on color alone to communicate status.
- Keep feedback consistent across chat, tools, permissions, settings, modules, and management screens. For chat, distinguish queued, running, completed and failed work and surface permissions requiring action.

## Visual hierarchy and design tokens
- Define and reuse semantic tokens for colors, typography, spacing, radii, elevation, borders, focus indicators and breakpoints instead of scattered one-off values. Prefer existing tokens and components; introduce new ones only when needed and document their purpose.
- Establish clear heading hierarchy, readable line lengths, consistent alignment and spacing, restrained accent use, and sufficient contrast. Prefer a coherent set of reusable controls, dialogs, alerts, navigation patterns and feedback components over page-specific variants.
- Meet WCAG 2.2 AA contrast requirements (typically 4.5:1 for normal text, 3:1 for large text and relevant non-text UI); support 200% text resizing and zoom/reflow without loss of functionality.

## Responsive and adaptive layouts
- Design mobile-first, then verify phone, tablet and desktop. Navigation must remain discoverable and operable when adapting between sidebar, drawer and compact menu patterns; never hide critical functions without an accessible alternative.
- Avoid horizontal page scrolling at narrow viewports (except intrinsically two-dimensional content), clipped dialogs, overlapping controls and tiny touch targets. Target at least 24×24 CSS px for pointer targets or sufficient spacing/exceptions per WCAG 2.2 AA; prefer larger comfortable targets for touch.
- Use responsive content priorities: stack panels, collapse secondary details progressively and keep primary tasks accessible. Verify both portrait and landscape where meaningful.

## Accessibility
- All interactive functions must work with keyboard alone, with logical focus order, visible focus indicators and no keyboard traps. Manage focus correctly on dialog open/close and route/content transitions.
- Use semantic HTML, accessible names, correct roles and states, associated form labels, meaningful headings, and live announcements for important asynchronous updates without excessive noise.
- Support reduced-motion preferences; do not convey meaning through color, motion or position alone. Test zoom, contrast, screen-reader-relevant semantics, and authentication/permission interactions where affected.

## Implementation boundaries
- Preserve the existing React, JTE/htmx and Playwright architecture. Reuse established components, styling mechanisms, and interaction conventions; no new UI framework or design-system dependency without explicit approval.
- Keep browser tests aligned with actual rendered behavior, including asynchronous transitions and permission states. Prefer shared components and token updates when addressing recurring UX defects.

## Required verification for UI changes
1. Inspect the implemented screens in an isolated browser at representative phone (~390px), tablet (~768px) and desktop (~1440px) widths, adjusting for actual supported layouts. Capture and visually review screenshots, including overflow, hierarchy, contrast, navigation and interactive controls.
2. Exercise keyboard-only navigation, focus visibility/order, dialogs, forms, and affected assistive-technology semantics. Check WCAG 2.2 AA requirements relevant to the change.
3. Exercise normal, hover/focus/pressed, disabled, loading/pending, empty, error, success and recovery states as applicable; verify click feedback and prevent unintended duplicate actions.
4. Run relevant Playwright/browser acceptance checks and focused regressions. Automated tests supplement, but do not replace, visual inspection.
5. Record screens, viewports, interactions, results and any blockers in the PR and the **private** execution/acceptance plan in `cordin/zalava-dev`. Do not commit screenshots, traces, local data or private plans to the public repository.

For documentation-only changes, review the standards and links for consistency; browser verification is required when UI behavior or appearance is actually modified.
