# Programme price books (0176)

Fees the provider enters for a programme version, optionally for one funder, for a period, each with a note of where the fee comes from.

## What it is and is not
- Nothing is seeded: no prices, no VAT rate. Every amount is typed by an administrator and the person says whether it includes VAT.
- Funding records still hold no amounts. A price line says what the provider charges, not what any funder approved or paid.
- Invoicing is unchanged. A price line is only ever shown as a suggestion; nothing is billed from here.

## Rules
- Fee type: tuition, registration, assessment, materials, certification, or other (other needs a description).
- Amount more than 0, at most two decimal places. Start date required, end date optional and not before the start.
- A source note of at least 10 characters is required.
- A line is never edited. A changed fee is a new line, and the old one is withdrawn with a reason of at least 10 characters. Nothing is deleted.
- Two live lines for the same fee (same version, same funder or none, same type, and for other the same description) may not cover the same day. The check waits on a lock per programme version, so two requests cannot both pass.
- Only an administrator can add or withdraw a fee; anyone who can read the module can see them.

## Suggestion
- For an enrolment on a given day (today by default): the lines in force for its programme version that are for no particular funder, plus lines for a funder that is actively paying for that enrolment.
- The wording says it is a suggestion, not an invoice, to be confirmed or replaced with another amount and a reason. When nothing is in force it says so and suggests nothing.

## Where it shows
- Programme > version: "Fees (price book)".
- Learner > enrolment > Funding: "Fee suggestion from the price book".

## API
`/api/v1/training-provider/price-lines`: GET (programmeVersionId, status), POST, POST `{id}/withdraw`, GET `/suggest` (enrolmentId, on).

## Not done
- Pre-filling the existing invoice generator. It bills by course delegates, not by programme versions, so it needs its own scoping.
- A database exclusion constraint for overlapping lines (the service enforces it).
- A second-person check on a new fee.

## Database guard (0179, V945)
The database also refuses two live lines for the same fee in overlapping periods (same programme version, same funder or both "any funder", same fee type, and for OTHER the same description ignoring case; dates inclusive, empty end date open-ended). Withdrawn lines are not counted. If two people save at the same moment, the second gets the same "already covers part of that period" message. The migration stops with a count if old data already overlaps; withdraw one line of each pair with a reason and run it again.

Resource bookings have no such database constraint on purpose: they depend on quantity, shareability and an administrator's written override, which an exclusion constraint cannot express. They stay guarded by the locked check in the service.
