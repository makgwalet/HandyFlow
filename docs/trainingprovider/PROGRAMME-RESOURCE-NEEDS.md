# Programme resource needs and cohort readiness (0175)

A programme version can list the kinds and quantities of resources the provider says it needs. A cohort can then compare its site's register with that list.

## What it is and is not
- The list is the provider's own, each line with a note of where the need comes from (at least 10 characters). It holds no standard and no regulatory figure, and nothing is seeded.
- A comparison is a prompt to check. It never says a programme meets or fails a standard, and never uses the words compliant or accredited.

## Rules
- A need has a type (the same types as the resource register), a description, optional words to look for in resource names, a quantity of at least 1, and optionally "for every N learners".
- With "for every N learners", the quantity is needed for each started group of N (5 learners with N = 4 is 2 groups).
- Needs are recorded only while the programme version is a draft. An approved version is frozen; a change is a new version.
- A need is withdrawn with a reason of at least 10 characters. Nothing is deleted.

## Comparison
- Counts the quantity of in-service resources at the cohort's site whose type matches (and whose name contains the words, if given).
- Three states: listed in the register enough, fewer than listed, none in the register. Each with a plain sentence ending "Check before the cohort starts" when short.
- Resources booked into timetable slots are not subtracted here; bookings are checked slot by slot (0174).

## Where it shows
- Programme > version: "Resources needed (optional)".
- Cohort > Timetable: "Resources compared with the programme".

## API
`/api/v1/training-provider/catalogue/programme-versions/{versionId}/resource-needs` GET and POST; `/catalogue/resource-needs/{needId}/withdraw`; `/cohorts/{cohortId}/resource-readiness`.

## Not done
- Per-module needs, and flagging a slot that lacks a needed resource.
- Checking readiness again when a cohort's learner count changes (it is worked out each time it is opened).
