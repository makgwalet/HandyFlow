# Practitioner site delivery permissions (0173)

The provider's own record of which person may facilitate, assess, moderate or do RPL assessment at which delivery site, for which programme (or any programme), and for what period.

## What it is and is not
- It is a record of placement. It sits beside the appointment checks (qualification, registration, verified evidence) and does not replace them.
- It is not a regulatory approval, and a permission being recorded does not show that anyone is competent or accredited.

## Rules
- Role: FACILITATOR, ASSESSOR, MODERATOR or RPL_ASSESSOR. Optional programme; blank means any programme.
- A start date is required; an end date is optional and may not be before the start.
- A reason for the placement of at least 10 characters is required.
- Only one live permission per person, site, role and programme at a time (a programme-specific one may sit beside an all-programmes one).
- Nothing is deleted. A permission is withdrawn with a reason of at least 10 characters, by whom and when.

## Where it shows
- Practitioners > Site permissions: list, filter by site, include withdrawn, record, withdraw.
- Cohort facilitators: when a facilitator is added and no permission covers the cohort's site and programme on the day, the assignment event carries a note and the facilitator row shows a prompt. It never blocks the assignment; it is a prompt to check.

## API
`/api/v1/training-provider/site-permissions` GET (siteId, practitionerId, status filters), POST, POST `{id}/withdraw`. Module guard `trainingprovider`; READ to view, MANAGE to record or withdraw.

## Not done
- Enforcing rather than prompting (can be added on request), and applying the same check to assessors, moderators and RPL assessors on their own screens.
