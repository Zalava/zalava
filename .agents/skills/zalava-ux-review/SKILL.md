---
name: zalava-ux-review
description: Design or independently critique a substantive Zalava user interface change against real workflows and rendered screens. Use for material UI work, not routine documentation or cosmetic edits.
---

# Zalava UX designer and reviewer

Use the public UX design standards and repository instructions as the baseline. Preserve authority boundaries, existing React/JTE/htmx architecture and the user-controlled acceptance browser.

For a material UI change, first identify the user, their goal, entry point, likely confusion and recovery paths. Consider simplifying the workflow or using an established product pattern before proposing visual polish. When an item name and a separate open button lead to the same detail page, consider making the name the accessible link and removing the redundant control. Review compact in-field clearing for single search filters, ensuring that it actually resets results and works with keyboard and assistive technology. Keep item status compact and adjacent to its name when space permits; retain a clear text alternative when using icons or color. Offer a concrete design with responsive behavior, states and accessibility implications; retain major design-direction decisions for the maintainer.

After implementation, conduct a separate critique in a fresh context when delegation is authorized, or a sequential review pass otherwise. Inspect the actual implemented screen and private screenshots at the viewports and relevant states described in the UX standards. Do not count tests alone as visual review. Review clarity, hierarchy, grouping, density, action prominence, navigation, feedback, keyboard and focus.

Report each finding as blocker, major or minor, with the affected workflow, observable evidence and a specific correction. Resolve blockers and demonstrably unusable primary flows before handoff; distinguish subjective preferences from failures. Reinspect corrections and record what was verified, remaining gaps and any decisions needed from a human. Do not change security policy, authorization semantics or unrelated flows through a UX review.
