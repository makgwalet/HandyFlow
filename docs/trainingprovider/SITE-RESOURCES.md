# Site resource register (patch 0172)

Part of decision 2 of the nine approved on 2026-11-26 (multi-site data model from day one: site-specific offerings, instructors, facilities,
resources, schedules). Venues, programme offerings per site, cohort facilitators and the timetable overlap guard already existed. This patch adds
the missing piece: a register of equipment and similar resources per delivery site.

## What it is, and is not

A register of what each site holds and the state it is in. It does **not** book resources, and listing a resource says nothing about whether it
satisfies any accreditation or other standard. No resource types are required by any programme here.

## What is stored (V938)

- `trainprov_site_resources`: number (RES-00001), site, optional venue at that site, name, type (equipment, vehicle, practical kit, simulator, IT equipment, consumables, other), optional serial number / asset tag / registration, quantity (1 to 100000), whether it can be shared between groups at the same time, notes, state.
- `trainprov_site_resource_events`: written once per change: added, details changed, moved between sites, state changed. Moves and state changes carry a reason of at least 10 characters.

## Rules

- States: in service, under repair, out of service, retired. Any non-retired state can move to any other; **retired is final**. A state other than "in service" needs a reason and a day (not in the future); the database checks this too.
- An identifier can be on only one non-retired resource in the tenant (case-insensitive), so a tag is not used twice. Retiring frees it.
- New resources go only to active sites; a venue must belong to the resource's site. A move needs an active, different site, a reason, and a day not in the future; retired resources cannot be moved or changed.
- Nothing is deleted.

## Where it shows

Cohorts, then the "Resources" sub-tab: filter by site and state, add, change details, change state, move, and read the history.

## Next, not in this patch

- Attaching resources to timetable slots, and extending the overlap guard to equipment (booking).
- Linking resources to the programmes or offerings that use them.
- Delivery permissions for practitioners per site.
