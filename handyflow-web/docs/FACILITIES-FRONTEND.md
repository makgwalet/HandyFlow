# Facilities frontend (internal module) — status

Route: `/facilities/:section?` — `src/pages/facilities/`, API client `src/api/facilities.api.ts`.

## Built
Sections: Dashboard, Action centre, Work orders (list, job card, create, full lifecycle actions),
Sites, Assets, Compliance (certificates), Stock (movements; receive/issue/return/adjust), Purchase orders
(approve, receive, cancel, close short), Vendors (approve). Token colours only; light and dark.

## Verified
Lint, type-check, production build; browser render (light/dark) against mocked API only.

## Not done
- Create/edit forms for sites, assets, vendors, stock items, purchase orders
- Inspections, corrective actions, PPM, technicians, reports
- Evidence/photo upload, job-card PDF download, QR scanning, offline technician app, map/calendar
- Not run against the real backend; response shapes assumed `{data:{content}}`
- No mobile-width check; no unit tests (vitest not installed)
- FM provider app and FM client portal not started

## Next
Forms and inspections/PPM, then the FM provider app, then the client portal.
