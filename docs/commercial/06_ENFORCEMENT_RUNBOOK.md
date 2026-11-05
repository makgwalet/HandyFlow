# Turning entitlement enforcement on: runbook

Status: written 5 Nov 2026 with patch 0627. Enforcement is OFF by default (`handyflow.entitlement.enforce`, env `ENTITLEMENT_ENFORCE`). In report mode a request a tenant is not entitled to is allowed and logged once per tenant and module (and again every 100th time) as `ENTITLEMENT-REPORT`.

## 1. Collect (run the app normally for at least one full business week)

Keep the application log. The line looks like:

    ENTITLEMENT-REPORT tenant=<uuid> module=<key> would be refused when enforcement is on (path=<uri>, occurrences=<n>)

## 2. Summarise (PowerShell, from the folder holding the log)

    Select-String -Path .\*.log -Pattern 'ENTITLEMENT-REPORT' |
      ForEach-Object {
        if ($_.Line -match 'tenant=(\S+) module=(\S+) .*occurrences=(\d+)') {
          [pscustomobject]@{ Tenant=$Matches[1]; Module=$Matches[2]; Occurrences=[int]$Matches[3] }
        }
      } |
      Group-Object Tenant, Module |
      ForEach-Object { [pscustomobject]@{ Tenant=($_.Group[0].Tenant); Module=($_.Group[0].Module); MaxOccurrences=($_.Group | Measure-Object Occurrences -Maximum).Maximum } } |
      Sort-Object MaxOccurrences -Descending | Format-Table -AutoSize

Each row is a tenant that used a module it holds no entitlement for.

## 3. Decide per row

| What the row is | Action before enforcing |
|---|---|
| A paying tenant using a module they should have | Fix their subscription or add-on (billing data), not the code |
| A trial, internal or demo tenant | Give it a trial or internal subscription row so it is not locked out |
| A module key that no tenant holds (e.g. `projects`, `auditor`, `accountant`, `ap`) | These have no `module_catalogue` row; they stay report-only until D-01 to D-05 are decided and rows are added |
| A key that looks misspelled | Compare with `module_catalogue.module_key`; one such case (`supplychain` vs `supply_chain`) was fixed in 0627 |

## 4. Enable

1. Staging first: set `ENTITLEMENT_ENFORCE=true`, restart, repeat step 1 for a few days. Expect HTTP 402/403 only for tenants you chose to leave without a module.
2. Production: same switch. Rollback is setting it back to `false` and restarting; no data changes.

## 5. Still not enforced even when the switch is on

- Modules in `ModuleEntitlementPaths.REPORT_ONLY` (no catalogue row).
- Platform services shared by all modules (approvals, capture, notifications, audit trail, documents, secure access) and external portals: pending decision D-05.
- Clinic keeps its own guard (`ClinicModuleGuardConfig`).

## 6. Open product answers this runbook cannot settle

Which platform services are free; soft or hard invoice cap; seat limits; read-only versus no access after a downgrade.

## 7. Token permissions (patch 0632), a second switch

Besides the per-request module check above, login, refresh and invitation-accept can leave out of the token (and out of the permission list sent to the browser) every permission that belongs to a module the tenant does not hold. The owner of each permission is `permissions.module_key` (V590, reviewed in `docs/iam/PERMISSION_METADATA_REVIEW.md`).

- Switch: `ENTITLEMENT_TOKEN_ENFORCE` (property `handyflow.entitlement.token-enforce`), default `false` = report only.
- Report line, once per tenant and module per process: `TOKEN-ENTITLEMENT-REPORT tenant=<uuid> module=<key> N permission(s) would be removed from the token (enforcement is off)`.
- Never dropped: `core` permissions, `unassigned` ones, and modules with no `module_catalogue` row (they stay report-only).
- Login never fails because of it: any error in the check returns the permissions unchanged.
- Read the report lines the same way as step 2 (change the pattern to `TOKEN-ENTITLEMENT-REPORT`). Turn it on after the per-request switch has been on without surprises, because a user keeps the old permissions until their token is refreshed (default access-token lifetime) or they log in again.
- Not done yet: the `tcv` claim that forces a re-login when a tenant's modules change, so a module added mid-session shows up only at the next refresh.
