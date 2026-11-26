# Optional programme workplace hours (patch 0171)

Decision 9 of the nine approved on 2026-11-26. Workplace hours on a programme are **optional**. Nothing assumes a programme needs any, no hours are
seeded or defaulted, and a programme with no figure says so plainly ("records no workplace hours" is not "none are needed").

## What is stored

`trainprov_programme_workplace_hours`: one live row per programme version (V937). A figure can only be recorded together with its source note:

| Item | Required |
| --- | --- |
| Hours (above 0, up to 20000, two decimals) | yes |
| Kind of source (qualification document, curriculum, authority letter, accreditation document, funder agreement, provider's own policy, other) | yes |
| Document or reference (3+ characters) | yes |
| Section or page | yes |
| Source's own version or publication date | optional (not every source has one); cannot be after the check |
| Effective date | yes |
| Checked by (name) and date checked (not in the future) | yes |
| What the hours cover | optional |

## Rules

- Changed or withdrawn only while the programme version is a **draft**. An approved version is frozen, so a learner keeps the figure they studied under; a change is a new version.
- Withdrawn with a reason of at least 10 characters; the row stays. Nothing is deleted.
- Logged hours (workplace module) are shown next to the recorded figure on a placement, with its source. They are never compared as a pass, "met" or "complete", and are not a competency decision.
- The database checks the source note too, with NULL-safe CHECKs.

## Where it shows

- Programme screen, each version: "Workplace hours (optional)".
- Placement logbook: the recorded figure, its source and the hours logged on that placement.

## Not done here

- Per-module or per-site hours; hours carried across versions automatically (a new version starts with none recorded).
- Second-person verification of the source (the checker is a recorded name and date).
