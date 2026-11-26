# Timetable overlap guard (patch 0165)

## What is checked
A timetable slot is checked against every other scheduled slot, on real moments in time (start and end with a time zone, default Africa/Johannesburg):

| Thing | Clash when |
|---|---|
| Venue | the same venue is booked by another slot at an overlapping time |
| Facilitator | the same person teaches another slot at an overlapping time |
| Cohort | the cohort has another slot at an overlapping time |
| Learner | any active learner in the cohort is an active member of another cohort that has a slot at an overlapping time (by learner, not by enrolment) |

Back-to-back slots do not clash. Equipment and rooms-as-resources use the same override record (resource types are already allowed) and will be added with the site-resources patch. Assessors are people records, so they are covered the same way once assessments are timetabled.

## Overrides
- A clash is refused. The message lists every clash.
- An administrator (`TRAININGPROVIDER_ADMIN`) can book anyway by sending `overrideReason` (10+ characters). Anyone else gets 403 `TP_TIMETABLE_OVERRIDE_DENIED`; a missing or short reason gets 409 `TP_TIMETABLE_CLASH`.
- Each approved clash is one row in `trainprov_timetable_overrides`: the resource, the other slot, both time ranges as they were, the reason, the approver and the time. Rows are only added. A line is also written to the cohort's history.
- `POST /cohorts/{id}/timetable/check` lists the clashes without booking. `GET /timetable/{slotId}/overrides` returns the audit history.

## Concurrency
Before checking, the service takes a PostgreSQL advisory lock for the cohort, venue, facilitator and each learner (in a fixed order), held until the transaction ends. Two requests for the same thing wait for each other, so both cannot pass the check. There is no database exclusion constraint yet: it would need the `btree_gist` extension and every existing row audited first, and it could not allow an approved override without extra columns.

## Existing data
`slot_date`, `start_time` and `end_time` stay. V931 back-fills `starts_at` and `ends_at` from them in Africa/Johannesburg. Existing slots that already overlap are left alone and are not reported as overrides.
