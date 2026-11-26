# Booking site resources into timetable slots (0174)

A timetable slot can book a quantity of a resource from the site resource register (0172).

## What it is and is not
- It is the provider's own record of what a slot uses. It does not show that a resource meets any standard.
- Nothing is deleted. A booking is released with a reason of at least 10 characters.

## Rules
- The resource must be in service and held at the same site as the cohort. The slot must be scheduled and the cohort open.
- A shareable resource is never a clash. For any other resource, the quantity already booked at overlapping times plus the new booking may not be more than the quantity the site holds.
- A clash is refused unless an administrator approves it with a written reason of at least 10 characters. The reason and the clash description are kept on the booking.
- Overlap is worked out on real moments in time (the slot's start and end with its time zone). Back-to-back slots do not clash.
- A cancelled slot frees its resources automatically, because only bookings on scheduled slots count.
- Two requests for the same resource wait for each other (advisory lock), so both cannot pass the check.
- If a slot is moved later, or a resource goes out of service, the booking row shows a prompt to check. It is never a finding.
- A resource kept in one venue but booked into a slot in another venue shows a note, not a refusal.

## Where it shows
- Cohort > Timetable > each scheduled slot > Resources: list, book (with a check first), release.
- Cohort events record RESOURCE_BOOKED and RESOURCE_RELEASED.

## API
`/api/v1/training-provider/resource-bookings`: GET `/slots/{slotId}`, POST `/slots/{slotId}/check`, POST `/slots/{slotId}`, POST `/{id}/release`, GET `/resources/{resourceId}/schedule`.

## Not done
- A database exclusion constraint (the overlap check is in the service with a lock).
- Changing the quantity of a booking in place (release and book again).
- Linking resources to programmes and offerings (next).
