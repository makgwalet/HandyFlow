# Fuel Phase 6b: automation (patch 0796)

## Implemented
- **Reorder rules** (Logistics > Reorder rules). A rule, set by the company per tank, says: when this tank is below N litres, draft a requisition for M litres (optional supplier and estimated price). Every number is the company's; nothing is assumed. One rule per tank.
- **Daily run** at 06:30 SAST (`fuel.automation.enabled`, default on). For each tenant that still has Fuel it checks each switched-on rule. Below the level (strictly) it creates a DRAFT requisition whose reason names the tank, its litres and the level. **It never submits, approves, converts or orders.** A tank that already has a requisition moving (draft, waiting, approved) or a converted one whose order is not fully received is left alone, so drafts cannot pile up. Each rule runs on its own: one failure is logged and the others still run.
- **Notice.** When drafts were made, the administrators get one in-app and email notice naming the requisitions and saying nothing has been submitted or ordered.
- **Check now.** A button runs the rules immediately (after a confirmation that it makes real drafts) and reports what happened in words.
- **Log.** Every draft and every failure is recorded (append-only) and shown under the rules.
- **Overdue purchase orders.** Opt-in switch (off by default). When on, the administrators are told once about orders past their expected date and not fully received. The order is marked alerted only after the notice is sent, so a failed send is retried the next day. Orders with no expected date are never flagged.
- **Licence expiry** is not repeated: the compliance reminder job already sends 30-day, 7-day and expired notices.

## Preserved
Requisitions, purchase orders, their approvals, and the tank low-stock alert are unchanged. A requisition made by a rule is an ordinary draft: edit it, add a price, submit it, or cancel it like any other.

## Files
- DB: V678 (`fuel_automation_rules`, `fuel_automation_log`, `fuel_purchase_requisitions.automation_rule_id`, `fuel_settings.alert_overdue_purchase_orders`, `fuel_purchase_orders.overdue_alerted_at`). No new permission: FUEL_READ to look, FUEL_MANAGE or FUEL_PURCHASING_MANAGE to change.
- Java: FuelAutomationRule, FuelAutomationLog, two repositories, AutomationDtos, FuelAutomationService, FuelAutomationScheduler, FuelAutomationController; additions to FuelPurchaseRequisition(+Repository, +Service.createFromRule), FuelPurchaseOrder(+Repository), FuelSettings(+Service, +Repository), FuelDivisionRules ("automation" with purchasing), NotificationType (FUEL_REORDER_DRAFTED, FUEL_PO_OVERDUE; no DB constraint on type).
- Web: AutomationTab, automationOps, menu entry "Reorder rules", FuelPage case.

## API
/api/v1/fuel/automation: GET /rules, GET /log, GET /settings (FUEL_READ); POST /rules, PUT /rules/{id}, DELETE /rules/{id}, POST /run, PUT /settings (FUEL_MANAGE or FUEL_PURCHASING_MANAGE). All behind the fuel module guard.

## Tests
- Java: FuelAutomationServiceTest (8), FuelAutomationSchedulerTest (5); the tenant-scoping test allow-list gained the two tenant-sweep queries. Parse-checked only; not run (you run Maven).
- Web: AutomationTab (7), automationOps (4). Fuel and navigation suite passes; tsc clean.
- V678 applied to a scratch PostgreSQL 16 with stubbed parent tables: a second rule for the same tank, a zero level and a bad log outcome are all refused.

## Gaps and risks
- An order raised without a requisition is not linked to a tank, so a rule cannot see it. If you order by hand while a tank is below its level, the next run can still draft a requisition. Raise orders from requisitions to avoid that.
- Rules are per tank only; no rule by fuel type, depot or a days-of-cover forecast.
- The notice goes to administrators only; no per-user choice.
- The scheduler's sweep is not clustered: if more than one app instance runs, each would run it. Drafting is protected by the open-requisition check, overdue alerts by the alerted flag, but two instances racing at the same second could both draft. The existing schedulers have the same property.
- Run Maven including FuelRepositoryTenantScopingTest, FuelEndpointSecurityTest, ArchitectureVerificationTest (notifications is already an allowed dependency) and PermissionConsistencyTest.

## Next
Phase 6c: landed cost per litre (0797).
