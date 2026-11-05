# Phase A status: module entitlement coverage (5 Nov, patch 0612)

`ModuleEntitlementPaths` now also covers creative, desk, recruiter, pos, tasks, marketing and supply chain (key `supply_chain`, the catalogue spelling). These have catalogue rows and can be enforced. Projects, auditor, accountant and ap are mapped but **report-only**, because they have no catalogue row and enforcing them would lock out every tenant. `/api/v1/accountant/portal/` is excluded. Approvals, capture, notifications, audit-trail, documents, secure-access and catalogue are deliberately unmapped pending D-05 (shared platform services).

Still open: Agriculture guards `supplychain`, which no tenant holds (G-02); catalogue rows for projects, auditor, accountant and ap; Clinic keeps its own guard. Enforcement stays off (`ENTITLEMENT_ENFORCE=false`) until the `ENTITLEMENT-REPORT` log has been reviewed.

Verification: only the pure path-map class was compiled and its assertions run. The Spring interceptor change and the JUnit test were not compiled or run in the authoring environment.
