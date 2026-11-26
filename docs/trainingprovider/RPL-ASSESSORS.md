# RPL assessors (patch 0167)

## The rule
An RPL decision is recorded by a practitioner who holds a checked, in-force **RPL assessor** appointment for the enrolment's programme on the day of the decision. RPL assessor is its own appointment role, with its own credential reference, authority, dates and verification, made and checked the same way as an assessor or moderator appointment. An ordinary assessor appointment does not authorise RPL, and an RPL appointment does not authorise ordinary assessment.

## Checks on a decision
- The person is active and has a live RPL assessor appointment for the programme.
- The appointment is checked (credential reference plus where it was verified) and in force on the decision day.
- If the assessor is linked to a user, only that user can record their decision.
- If the assessor has no login, `recordedForAssessor` (10+ characters: who gave the decision and how) is required and kept on the application.
- The existing rules still apply: reviewed evidence, written basis, second-person confirmation or a written reason.

The application keeps `assessorId`, `assessorAppointmentId` and the typed name, and a snapshot of the practitioner and appointment is stored (`subject_type` RPL, readable at `GET /rpl-applications/{id}/assessor-snapshot`).

## Existing decisions
Decisions made before V933 have only a typed name. They are flagged `legacyAssessorText`, shown as "typed name, not checked", not rewritten, and can still be confirmed.

## Screens
The decision form's free-text assessor box is now a select of people with an RPL assessor appointment for that programme on the decision day, and asks how the decision reached us when the chosen person has no login. "RPL assessor" is a role on the practitioner appointment screen.

## Not done here
Appointment scope is per programme. Scope by qualification or module, and the authority/accreditation framework, come later.
