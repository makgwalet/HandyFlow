# Facilities frontend (internal module) — status

Route: `/facilities/:section?` — `src/pages/facilities/`, API client `src/api/facilities.api.ts`.

## Built
Sections: Dashboard, Action centre, Work orders (list, job card, create, full lifecycle actions),
Inspections (run a checklist with pass/fail/N-A, complete, cancel; manage checklist templates),
Corrective actions (log, raise work order, reschedule, close, cancel), Planned maintenance (per-asset schedules),
Sites, Assets, Compliance (certificates), Stock (movements; receive/issue/return/adjust), Purchase orders
(create, approve, receive, cancel, close short, fill from reorder suggestions), Vendors (approve, suspend).
Create/edit forms: sites (close/reopen), assets (incl. status), vendors, stock items (deactivate/reactivate), purchase orders.
Token colours only; light, dark and phone width.

## Verified
Lint, type-check, production build; browser render (light/dark/390px) against mocked API only.

## Not done
- Evidence/photo upload (assets, inspections, work orders), job-card PDF download, QR scanning, offline technician app, map/calendar
- Compliance certificate create/renew/revoke forms; vendor document upload and compliance view; technician management
- Reports and exports; audit-log viewer; site access admin
- Not run against the real backend; response shapes assumed `{data:{content}}`
- No unit tests (vitest not installed)
- FM provider app and FM client portal not started

## Next
Compliance forms and vendor documents, then the FM provider app, then the client portal.
