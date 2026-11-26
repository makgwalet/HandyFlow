# Assessor identity and appointment snapshots (patch 0166)

## Who an assessor is
An assessor is a practitioner record, not a login. An internal assessor is linked to a user; an external assessor has a practitioner record and no login. What authorises them is an ASSESSOR appointment for the programme that is checked (a credential reference and where it was verified) and in force on the day. A user id never authorises anyone.

## What is checked
- Recording an attempt: the appointment must be checked and in force on the assessment day (unchanged).
- Recording a decision: the appointment must be checked and in force on the day the decision is recorded. If the assessor was re-appointed since the assessment, the live appointment counts. A withdrawn or expired appointment blocks the decision until it is put right.
- An assessor linked to a user can only be decided for by that user (unchanged). An assessor with no login has the decision recorded for them: `recordedForAssessor` (10+ characters, how the decision reached the person recording it) is then required and is kept on the decision.

## Snapshots
`trainprov_appointment_snapshots` keeps, once and never changed, the practitioner (number, name, employment type, whether they had a login) and the appointment (programme, issuing authority, credential reference, valid dates, verification source, date and person, standing and the day it was worked out for) at two moments: when the attempt was recorded and when it was decided. `GET /assessment-attempts/{id}/assessor-snapshots` returns them. `subject_type` also allows MODERATION and RPL for later patches.

Attempts that existed before V932 got a snapshot copied from the current records, flagged `backfilled`, with no standing: it shows what is on record now, not what was on record on the day.

## History
`GET /assessment-attempts/by-assessor/{practitionerId}` lists every attempt a person assessed.

## Not done here
Appointment scope is per programme, as it was. Scope by qualification or module, issuing-authority framework and RPL assessor appointments come in later patches.
