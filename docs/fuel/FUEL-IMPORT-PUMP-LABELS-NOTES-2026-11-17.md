# Fuel: pump labels for the pump/POS import (2026-11-17)

Written, not compiled or run (Java). Web: ImportsTab tests (12) and tsc pass.

## What changed
A sender may call a pump by its own id (for example "P-01"). A **pump label** maps that label, for one source, to a Fuel pump. Until now the import matched by Fuel pump name only.
- V701 `fuel_import_pump_aliases` (unique per company, source and label). New table only; senders with no labels behave exactly as before.
- Matching: the Fuel pump name first, overridden by a label set for that source. Source and label are trimmed and compared ignoring case. A label pointing at a pump since deactivated is ignored (the shift then lists "not set up").
- A shift where two labels resolve to the same pump lists a problem and cannot be accepted silently.
- Endpoints under `/api/v1/fuel/forecourt/imports/aliases`: GET (reviewers), POST and DELETE (FUEL_MANAGE or FUEL_FORECOURT_MANAGE, as for keys). Retail division rule applies.
- Web: "Pump labels" panel under the keys on Imported shifts; sources seen on staged shifts are offered as suggestions.

## Not changed
Staged shifts keep the sender's original labels; the mapping is applied when checking and on accept. Adding a label does not re-check shifts already staged: reopen the shift (the accept check uses the current labels).

## Still not built for import
Auto-accept (D1), live transactions, refunds and voids, tank-gauge feeds, provider times on accepted shifts.
