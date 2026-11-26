# Moderation sampling (patch 0170)

Decision 8 of the nine approved on 2026-11-26. This is the provider's **own** sampling: it decides how many decided results the provider chooses
to moderate, and which. Nothing here says what any regulator requires, no percentage or threshold is seeded, and a closed draw is never described
as showing that a standard is met.

## What is stored

| Record | Table | Notes |
| --- | --- | --- |
| Rule | `trainprov_sampling_rules` | Where it applies: all, one programme, one assessment type, or one cohort. One live rule per scope. Retired with a reason, never deleted. |
| Rule version | `trainprov_sampling_rule_versions` | The settings. Written once and never changed; a change is a new version that starts on a later day. Each has a written reason. |
| Draw | `trainprov_sampling_draws` | The rule and version used, a readable copy of the settings, the window, the seed, the counts. Closed with a decision and a note, or voided with a reason. |
| Draw item | `trainprov_sampling_draw_items` | The **whole** eligible population, one row per attempt, with whether it was picked and why. |
| Corrective action | `trainprov_sampling_actions` | Description, owner, due date; closed (done or cancelled) with a note. |

Historical settings never change: a draw keeps its own copy of the settings in words, and points at the version row, which is never edited.

## Settings a version can hold

- Every eligible result, or a percentage (above 0, up to 100), rounded up.
- A minimum and a maximum count (the maximum does not apply when every result is moderated).
- Selection method: random (seeded) or risk-based.
- Extra sampling after poor outcomes: if at least N of the last M moderations in scope raised a query, add an extra percentage.

## How a draw works

1. Choose a live rule and a window of decision dates ending today or earlier. The version in force today is used.
2. The population is every decided, live attempt in the window, within the rule's scope, on an instrument that requires moderation, that has not been
   moderated and is not already in a live draw.
3. Results chosen on purpose (targeted) are added by the person, each with a written reason of at least 10 characters. They count towards the number required.
4. The rest are filled by the rule's method. Random uses the seed. Risk-based takes the highest scores first, ties broken by the seeded order.
5. Extra sampling is added if the rule asks for it and recent queries trigger it.
6. The same seed and the same population always give the same picks, so a draw can be re-checked.

Risk score (stored per result with its reasons): +2 if the assessor has had a moderation query, +1 if the assessor has never been moderated, +1 for a repeat attempt.
The rule's own wording of its risk factors is kept with the version. These are prompts for where to look, not findings about anyone.

## Closing

A draw closes only when every picked result has a moderation recorded in the moderation module, and, if the decision is "results accepted", every query has been answered.
"Further action required" needs at least one corrective action. A second person closes the draw, or the person who drew it confirms in writing that they are closing it themselves.
A draw can be voided (with a reason) only while no picked result has been moderated.

## Not done here

- Moderation itself is still recorded on each attempt. Sampling only says which to moderate.
- No pass-marks, percentages or thresholds are provided. The provider writes its own.
- Sampling by assessor, by site or by funder is not in this patch.
