# Payroll Bureau: repository audit, 2026-11-26

Read-only pass. No code was changed. Status words: EXISTS, PARTIAL, MISSING, DEFECTIVE, NEEDS VERIFICATION.
Nothing here was run: Maven, the database and the browser are not available in the sandbox.

## What exists today
- Backend: 12 domain entities, 11 repositories, 5 services (`PayrollBureauService`, `PayrollBureauEngine`, `PayDeadlineEngine`, `PayBureauPayslipPdfGenerator`, `PayrollBureauAutoSendScheduler`), portal auth and data services, 3 controllers. Migrations V213-V218, V220, V233-V237.
- Permissions: only PAYROLLBUREAU_READ, _MANAGE, _ADMIN. 20 write endpoints use MANAGE/ADMIN, 14 read endpoints use READ/MANAGE/ADMIN, delete-client is ADMIN.
- Frontend: one 1,267-line `PayrollBureauPage.tsx` (tabs: employees, pay runs, deadlines, fee notes, portal access), two small validation files, and four portal pages.
- Tests: none for the module. The only related test is the shared `SarsPayrollCalculatorTest`.
- Flow: client -> employees -> pay run (DRAFT) -> `process` (calculate + create payslips, status PROCESSED) -> payslip PDF/email -> EMP201/EMP501 deadlines -> fee notes.

## Defects found (severity order)
| # | Severity | Defect |
|---|---|---|
| D1 | High | `getPayslips` reads `findByPayRun(payRunId)`, a query with no tenant filter, and the service comment says the ownership check was "deliberately simplified". Any bureau user who knows or guesses a pay-run id can read another tenant's payslips. |
| D2 | High | Processing a run has no `@Version` on `PayRun`, no lock and no unique (pay run, employee) constraint on payslips. Two clicks, or two requests, can create duplicate payslips and double totals. |
| D3 | High | ID number, tax number and bank account details are stored as plain text. |
| D4 | High | Salary and bank-detail changes (`updateEmployee`) take effect immediately with no approval and no history. Direct payment-fraud exposure. |
| D5 | Medium | Only two run states (DRAFT, PROCESSED). No review, approval, lock, payment, or reversal. A processed run cannot be reopened or corrected. |
| D6 | Medium | Calculation reads the live employee record. No frozen snapshot, so re-printing a payslip after a salary change can disagree with what was paid. Payslip stores only `tax_year`, not the rule version. |
| D7 | Medium | UIF ceiling, UIF/SDL rates and SDL threshold are constants in code. Year-specific rebates, medical credit and retirement cap are in a code table (2025-2027). An unknown year silently reuses an earlier year (logged only). |
| D8 | Medium | PAYE is the monthly figure from an annual table, with `annualPayroll` taken as current monthly gross x 12. No year-to-date or annualised-period logic, so bonuses, mid-year starters and weekly/fortnightly pay are wrong or unsupported. |
| D9 | Medium | No tax tables for the year gives zero PAYE with only a log warning. A run still completes. |
| D10 | Medium | Payslip email is sent inline in a loop; one flag (`payslipsAutoSentAt`) covers the whole run. A partial failure either re-sends to everyone or marks all as sent. No per-employee delivery record. |
| D11 | Low | Repository queries `PayRunRepository.findByClient`, `PayslipRepository.findByPayRun`, portal `findByClient` calls are not tenant-scoped. The portal ones are guarded by the grant check, so only the payslip one is exploitable today. |
| D12 | Low | `PayDeadlineEngine` creates EMP201 (7th) and EMP501 (31 May) with a weekend/holiday roll-back. SARS's current rules for the due dates and the holiday table need checking; there is no IRP5 or other deadline type. |

Portal check: the portal data service takes the tenant from the grant and calls `requireAccess` first, as the plan wants. It does NOT repeat the Training Provider mistake. Scope is fee notes and deadlines only.

## The 22 engines
| # | Engine | Status | What exists / what is missing |
|---|---|---|---|
| 1 | Payroll Calculation | DEFECTIVE | `PayrollBureauEngine` wraps `SarsPayrollCalculator`. Monthly only, no snapshots, no overtime, deductions, loans, bonuses, proration (D6, D8). |
| 2 | SA Statutory Tax | PARTIAL | Shared calculator has tax tables, rebates by age, medical credit, UIF, SDL. Hard-coded constants and year table (D7, D9). No ETI, COIDA, pension-fund rules beyond a cap. |
| 3 | Pay Component and Remuneration | MISSING | Employee has salary, travel allowance, pension, medical aid fields only. No component catalogue, taxable/non-taxable flags, SARS source codes. |
| 4 | Pay Period and Pay Group | MISSING | A run has start/end dates; no pay groups, frequencies, cut-off dates or period calendar. Weekly/fortnightly impossible. |
| 5 | Input and Import | MISSING | No variable-input capture, no CSV/Excel import, no client-submitted inputs. |
| 6 | Adjustment and Retroactive | MISSING | No back-pay, corrections or reversals. |
| 7 | Workflow and Lifecycle | DEFECTIVE | Two statuses (D5), no state machine, no per-client workflow. |
| 8 | Validation and Exception | MISSING | Only form validation in the UI (`payrollValidation.ts`). No pre-run exceptions (missing bank/tax number, zero pay, duplicates). |
| 9 | Variance and QA | MISSING | No comparison to prior run. |
| 10 | Maker-Checker and Approval | MISSING | None; shared approvals engine not yet checked for fit (NEEDS VERIFICATION). |
| 11 | Locking and Reversal | MISSING | None (D2, D5). |
| 12 | Audit and Evidence | PARTIAL | Logo via evidence facade; no audit trail for salary, bank, run or payslip actions. |
| 13 | SARS Filing | PARTIAL | Deadlines generated and marked filed (D12). No EMP201 figures, EMP501 reconciliation, IRP5/IT3(a) data, or export files. |
| 14 | Statutory Reconciliation | MISSING | None. |
| 15 | Banking and Payment | MISSING | Bank details stored (D3, D4); no bank file formats, no payment batches or status. |
| 16 | Accounting and Cost Allocation | PARTIAL | Fee notes post journals through the accounting facade. No payroll journals (salaries, PAYE, UIF, SDL liabilities), no cost centres. |
| 17 | Leave and Absence | MISSING | None; the bureau cannot depend on `hr`. |
| 18 | Reporting and Analytics | MISSING | Payslips and run totals only. |
| 19 | Bureau Client Service | PARTIAL | Client CRUD, offboard, reactivate, logos, address, pay day. No onboarding checklist, service tiers, SLAs, client-approval step. |
| 20 | Client Portal | PARTIAL | Invite, register, login, grants, fee notes and deadlines read-only. No payroll approval, payslip, input submission or documents. |
| 21 | Employee Self-Service | MISSING | Employee documents exist (staff-side). No employee login or payslip access. |
| 22 | Billing and Profitability | PARTIAL | Fee notes with lines, payments, send, journals. No price lists per client or per payslip, no time/cost capture, no profitability. |

Totals: EXISTS 0 (nothing fully meets the plan), DEFECTIVE 3, PARTIAL 7, MISSING 12.

## Needs verification before any build
1. Whether the shared approvals engine supports segregation of duties, limits and immutable history (engine 10).
2. Current SARS figures (UIF ceiling, rebates, medical credit, EMP201/EMP501 dates) against the code constants.
3. `SarsTaxTable` seeding for the 2027 year in a real database.
4. Whether `PayrollBureauAutoSendScheduler` has `@EnableScheduling` backing it (its own comment says this was never checked).
5. Modulith: the bureau may not depend on `hr`, so the shared calculator and its year figures need a single home (decision 6).

## Proposed build sequence (not started)
0. Safety fixes, small and independent: D1 tenant check, D2 version plus unique (run, employee) plus processing guard, D11 tenant-scoped queries, D9 fail-closed when tables are missing. No product decisions needed.
1. Calculation foundation: effective-dated statutory rules table, frozen snapshots and rule version on payslip, pay components, pay groups and periods, annualised PAYE.
2. Control and workflow: lifecycle states, validation and variance, maker-checker for bank/salary changes and run approval, lock and reversal, audit trail, encryption of sensitive fields (D3, D4, D5).
3. Payments and accounting: bank files, payroll journals, cost allocation.
4. SARS: EMP201 figures, EMP501 reconciliation, IRP5 data and exports.
5. Portals: client approval and payslip views, then employee self-service.
6. Commercial: service tiers, pricing, profitability.

## Six decisions still open
1. Primary launch customer (plan recommends independent bureaus and accounting practices).
2. Payroll coverage (plan recommends SA monthly, weekly, fortnightly; multi-country later).
3. Client approval (plan recommends configurable per client).
4. Employee self-service (plan recommends client portal first, employee portal later).
5. SARS filing (plan recommends validated exports and guided filing first).
6. Shared calculation engine with HR & Payroll (plan recommends shared statutory capability, separate module workflows).

## Update: safety fixes applied (patch 0180)
- D1 fixed: `getPayslips` now loads the run through the tenant-scoped lookup first, and `PayslipRepository.findByPayRun` takes the tenant id.
- D2 fixed: `PayRun` has `@Version` and V946 adds `pay_runs.version` plus a unique index on (pay_run_id, pay_employee_id). V946 stops with a clear message if duplicate payslips already exist.
- D9 fixed for the bureau: processing a run is refused (422 `NO_TAX_TABLES`) when the tax year has no PAYE tables. The shared calculator itself is unchanged because HR uses it too.
- D11 fixed: `PayRunRepository.findByClient` takes the tenant id.
- Test: `PayrollBureauSafetyTest` (Mockito, not run in the sandbox).
