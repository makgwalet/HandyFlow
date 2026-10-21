# HR module audit (step 1: Discover, step 2: Map)

Date: 2026-10-21. Branch `fleet-review3`. This is a reading audit of the code. **Nothing was run**: no Maven, no database, no browser. Where I did not read something I say so.

## 1. What exists

**Backend, `platform/.../hr` (48 files, about 3,700 lines):** employees (create, edit, terminate, org chart), leave (balances, request, approve, reject), disciplinary records (add, record outcome), pay runs (create, process), payslips and PDF, EMP201 list and PDF, and an employee self-service portal (register, login, profile, payslips, leave). Tables: `hr_employees`, `hr_leave_balances`, `hr_leave_requests`, `hr_disciplinary`, `hr_pay_runs`, `hr_payslips`, `hr_emp201`, `hr_tax_tables`, `hr_tax_rebates`, `hr_employee_portal_access_grants`.

**Shared:** `shared/SarsPayrollCalculator` is the one PAYE/UIF/SDL formula, used by this module and by `payrollbureau`.

**Front end, `pages/hr` (8 files, about 1,950 lines):** Dashboard, Employees, Leave, Payroll, SARS (EMP201), Compliance, Disciplinary. The mock-ups you attached show a larger product (recruitment, attendance, performance, training, reports, org structure); **none of those screens or backends exist in the HR module.**

**Tests:** `HrControllerTest` (14 tests, controller level) and `PayrollEngineParityTest` (1 test, checks the HR and bureau engines agree with each other, not that either is right). No tests for leave rules, pay run processing, security scope, or the payroll formula against independent examples.

**Not read in depth:** the two PDF generators, the portal services, `payrollbureau`, the `training` and `recruiter` packages (those look like products for training providers and recruitment agencies, not HR for an employer), and the front-end tab code beyond the API calls it makes.

## 2. Findings that affect money or legal exposure (ranked)

**P1. PAYE can be silently zero or use the wrong year's rates.**
- The only tax tables are seeded in V22 with `tax_year = 2026`, labelled "2025/26 (Mar 2025 to Feb 2026)", so the label is the **year the tax year ends**. No later migration touches the tables and no screen or API can add a year.
- `PayrollService.createPayRun` labels a period from March onwards with **that calendar year**, and January and February with the year before. A pay run for April 2025 is labelled 2025 and finds no table. January 2026 is also labelled 2025.
- When no table is found, the calculator logs a warning and returns **zero PAYE, zero UIF and zero SDL**, and the run still completes. A payslip with R0 tax goes out with no error.
- Runs from March to December 2026 are labelled 2026 and get the **2025/26 brackets**, but those months are in the 2026/27 tax year. Today (October 2026) every new run uses last year's rates. I have not looked up the 2026/27 figures; they must come from the official SARS tables, not from me.

**P2. The medical tax credit is given to everyone.** The calculator always subtracts R364 x 12 from tax, whether or not the employee has any medical aid, and ignores dependants. PAYE is understated by up to R364 a month for employees without medical aid. The amount is also hard-coded (2025/26) with no year.

**P3. Each month is calculated alone.** PAYE is this month's salary as an annual figure divided by 12. There is no year-to-date method, so bonuses, overtime, back pay, starters, leavers and unpaid leave are not handled. The payslip has overtime and bonus columns, but nothing ever fills them. Every active employee is paid a full month; a leaver terminated before the run is skipped, so there is no final pay.

**P4. Other formula problems.**
- Age for the 65 and 75 rebates is the current calendar year minus the birth year, using today's date, so re-running an old period can change the result.
- UIF ceiling, SDL rate and threshold, MTC and the default rebates are constants in code, not effective-dated. A missing rebate row silently falls back to the 2025/26 amount.
- The 27.5% pension cap is applied to salary plus taxable travel, not remuneration as defined, and there is no retirement-fund carry-forward.
- No ETI.

**P5. No payroll controls.** One button, "process", moves a run from DRAFT to COMPLETED. There is no preview, no variance check against last month, no exception list, no second-person approval, no lock or reversal. A processed run's status cannot be corrected except by a manual database change. The EMP201 is created at processing with no payment tracking. There is no EMP501, IRP5/IT3(a), bank file or accounting journal.

**P6. Access is wider than the document's own rules.**
- `USER_READ` and `USER_UPDATE` (general user permissions) are accepted on almost every HR endpoint, including creating employees, approving leave and **processing payroll**. Anyone who can edit users can run payroll.
- `HR_READ` (and `USER_READ`) sees every employee's ID number, tax number and gross salary in the employee response. There is no field-level or manager-scope restriction.
- Leave approval is open to anyone with `HR_MANAGE`, with no check that the approver is not the requester (not verified in code; I could not find one).

**P7. Leave is a counter, not a ledger.**
- Working days skip weekends only: no public holidays, no half days.
- Balances are three numbers seeded **once, when the employee is created** (annual 15, sick 30, family responsibility 3). I found no new-year seeding or carry-over job in this module.
- Sick leave is seeded as 30 days for each calendar year, but the rule is 30 days over a 3-year cycle, so the balance is overstated.
- There is no cancel, no evidence (medical certificate), no adjustment with a reason, no team-coverage check, and no history of how a balance got to its value.

## 3. Parity matrix (against your roadmap)

Status words are from your document: COMPLETE, PARTIAL, FRONTEND_MISSING, BACKEND_MISSING, DATA_MISSING, WORKFLOW_MISSING, SECURITY_GAP, UNVERIFIED. Nothing is COMPLETE, because nothing has been run.

| Area | Status | Evidence |
|---|---|---|
| Employee master | PARTIAL, SECURITY_GAP | One flat record. No person/employment split, no history of changes, no positions, grades or cost centres. Sensitive fields shown to any reader. |
| Org structure | PARTIAL | Org chart from a manager link only. No departments as data, positions, grades, companies or effective dates. |
| Employee 360 | BACKEND_MISSING | Tabs show separate lists; no linked view of documents, training or attendance. |
| Leave | PARTIAL, WORKFLOW_MISSING | See P7. |
| Payroll calculation | PARTIAL, DATA_MISSING | See P1 to P4. |
| Payroll controls | WORKFLOW_MISSING, SECURITY_GAP | See P5 and P6. |
| Statutory (EMP201, EMP501, IRP5) | PARTIAL | EMP201 figure and PDF only. No EMP501, IRP5, payment tracking or exports. |
| Disciplinary / ER | PARTIAL | Records and an outcome only. No case stages, evidence, hearings, appeals or restricted access. |
| Documents and evidence | BACKEND_MISSING | No HR document storage or expiry tracking. |
| Audit | BACKEND_MISSING | Created/updated stamps only. No before/after events. |
| Workflow and delegation | BACKEND_MISSING | Approve/reject only, by permission. |
| Recruitment, onboarding | BACKEND_MISSING, FRONTEND_MISSING | Not in the HR module. |
| Attendance, rosters, overtime | BACKEND_MISSING, FRONTEND_MISSING | Not present. |
| Performance | BACKEND_MISSING, FRONTEND_MISSING | Not present (the mock-up shows it). |
| Training and competencies | BACKEND_MISSING, FRONTEND_MISSING | Not in HR. |
| Reports and analytics | PARTIAL | Dashboard counts only. |
| Employee portal | UNVERIFIED | Endpoints exist; not reviewed. |
| Tests | PARTIAL | See section 1. |

## 4. Proposed order (nothing started)

Your roadmap says to fix foundations before adding screens, and the findings above agree. Payroll goes first because it is the only area that is wrong in a way that produces real payslips today.

1. **Slice 1, payroll correctness (P1 to P4).** Tax-year naming, effective-dated statutory tables with a screen to load them, MTC only with medical aid and dependants, no silent zero (a missing table blocks the run), age at the tax-year end, and tests against independent worked examples that you supply or approve.
2. **Slice 2, payroll controls (P5, P6).** Preview, exceptions, variance, maker/checker, finalise and lock, reversal, proper `PAYROLL_*` permissions and removing `USER_*` from HR.
3. **Slice 3, employee master and security.** Field-level protection, manager scope, effective-dated changes, organisation units and positions, audit events.
4. **Slice 4, leave as a ledger.** Policies, ledger, holidays, evidence, cancellation, carry-over.
5. Then the roadmap's later steps: EMP501 and IRP5, recruitment and onboarding, attendance, ER cases, performance, training, reports.

## 5. Decisions needed before Slice 1

1. **Payroll figures.** I will not type tax tables from memory. Either you give me the official 2025/26 and 2026/27 SARS tables (brackets, rebates, MTC, thresholds), or I fetch them from SARS and you check them before they are used.
2. **Existing payslips.** If real runs exist for 2025 or 2026, some may carry R0 PAYE or the wrong rates. Do you want a report listing the affected runs before anything is changed?
3. **Medical tax credit.** Do we add the dependants count to the employee record now?
4. **Permissions.** Taking `USER_UPDATE` out of payroll processing will stop people who rely on it today. OK to switch to `PAYROLL_*` with a controlled migration like the Catalogue one?
5. **Scope of "HR".** Recruitment and training packages already exist for agency and training-provider products. Do you want the HR module to reuse them or have its own employer-side versions?

## 6. Decisions taken (2026-10-21)

1. SARS figures are fetched from SARS and stored as versioned rules with a source note and an approval state.
2. An impact report comes before any change to issued payslips. Issued payslips and paid runs are never overwritten; corrections go through an approved adjustment or reversal.
3. Medical scheme membership is modelled with dates and dependants. The credit comes from the people covered, not from children or household dependants.
4. `USER_UPDATE` is replaced on payroll by `PAYROLL_*` permissions through a controlled migration (Slice 2, with an inventory and an unmapped-assignments report first).
5. Recruitment and training reuse shared technical services but get HR-owned employer-side workflows, decoupled from the agency and training-provider products.

## 7. Slice 1 status: payroll correctness

**Built (not yet run by me against Maven or a database, see "Not verified")**

- **Tax year from the pay date.** A tax year is named by the year it ends (2027 is 1 March 2026 to 28 February 2027). The old code labelled March to December with the calendar year, so the label was wrong for most runs.
- **Rules in the database, not in code.** `hr_statutory_tax_years` (V485) holds UIF ceiling and rates, SDL, retirement cap, travel percentage and medical credits per year, with status, source note and approval. Brackets and rebates for 2027 were added next to the existing 2026 ones.
- **No silent zero.** A pay run for a year with missing, incomplete or unapproved rules is refused with a plain message (409). Before, a missing table gave R0 PAYE, UIF and SDL.
- **Medical credit only for people covered.** The credit follows `hr_medical_scheme_memberships` (employee plus dependants, with dates). With no membership on record and a medical aid deduction, the run uses an employee-only credit and says so in the run warnings. With neither, there is no credit.
- **Age at the end of the tax year**, not the current calendar year.
- **Impact report** (`GET /payroll/impact-report`, read-only). Recalculates each processed run under the approved rules and classifies every payslip and run as NO_DIFFERENCE, POTENTIAL_DIFFERENCE, CONFIRMED_DIFFERENCE or REVIEW_REQUIRED. It changes nothing.
- **Screens.** HR > Payroll checks (rules by tax year; impact report with payslip detail), Medical scheme button on the employee card, and pay run warnings shown after processing.
- **Permissions.** The new endpoints use `PAYROLL_READ` / `PAYROLL_RUN` / `HR_MANAGE` and do not accept `USER_*`.

**Sources (read 2026-10-21):** SARS rates of tax for individuals; SARS medical tax credit rates; SARS UIF page (ceiling R17 712 a month); SARS employer guide for employees' tax 2026 and 2027; SARS monthly deduction tables.
2025/26 figures match the existing seed. 2026/27: brackets from R245 100, primary rebate R17 820, medical credit R376 / R376 (R752 with one dependant) / R254, retirement cap R430 000.

**Needs your decision or action**

- **Sign-off.** V485 loads both tax years as APPROVED so payroll keeps working after deploy. `approved_by` is empty and the approval note says the owner sign-off is still to be recorded. Someone accountable should check the figures against SARS and record that. There is no screen for approving a tax year yet.
- **Parity test removed.** `PayrollEngineParityTest` asserted that the HR engine equals the payroll bureau engine. That is no longer true by design, so the test was deleted.
- **Payroll bureau still has the old formula.** `payrollbureau` uses the shared `SarsPayrollCalculator`, which still gives the medical credit to everyone, uses the current calendar year for age, and has hard-coded constants. It needs its own review and impact report. I have not touched it.

**Not covered by Slice 1:** the `PAYROLL_*` permissions migration (Slice 2), overtime, bonus, pro-rata and final pay, a tax-year approval screen, ETI, retirement-fund carry-forward, and whether a run was paid or exported (not tracked, so the impact report cannot say).

**Not verified**

- Maven build, all Mockito tests (`PayrollServiceTest`, `StatutoryRulesServiceTest`, `MedicalSchemeServiceTest`, `PayrollImpactServiceTest`, the new `HrControllerTest` cases), the JPQL queries and migration V485 have not been run.
- Run in a standalone harness and passing: `SarsPayeCalculatorTest` (14, including 18 SARS table rows for 2026 and 2027), `MedicalSchemeRulesTest` (9), `PayrollImpactRulesTest` (8).
- Front end: `tsc`, `eslint` on the new files, 24 tests in `pages/hr` and `navigation`, and `vite build` pass.
- Not looked at in a browser.

**Please run:** `mvn clean test -pl platform`, start the app so Flyway applies V485 and Hibernate validates the new entities, then open HR > Payroll checks and read the impact report before processing any new run.

## 8. Slice 2 status: payroll controls

**What was wrong before**

- A pay run went from DRAFT straight to COMPLETED in one click. Nobody else looked at it, and there was no lock.
- Anyone holding `USER_UPDATE` (a general "edit users" permission) could create and calculate pay runs.
- The employee self-service portal showed payslips the moment a run was calculated, before anyone had checked them.
- Calculating a run twice produced two EMP201 drafts.
- The EMP201 PDF could be downloaded for a run nobody had approved.

**What changed**

- **Steps.** DRAFT, then PREPARED (calculated, waiting), APPROVED, FINALISED (locked). A prepared or approved run can be sent back to draft with a reason. A finalised run cannot be calculated again. Runs made before this slice keep the old status COMPLETED: employees can still see them, they can still be approved and finalised, and they cannot be calculated again.
- **Separation of duties.** The person who calculated a run cannot approve or finalise it. A business with a single payroll person can switch that off in HR > Payroll checks; every approval or finalisation of one's own run is then written in the run's history. It is off by default.
- **Review before approval.** Errors block approval (no payslips, net pay zero or negative). Warnings do not (medical credit assumed, no bank account on record, net pay moved more than 10% from the previous approved run, a person new on the run or missing from it).
- **History.** Every step is stored with who, when and the note, and is never edited (`hr_pay_run_events`).
- **Employees** see payslips only from finalised runs (and legacy COMPLETED ones). **EMP201** export waits for finalisation. A recalculation replaces the EMP201 draft; one that has moved on blocks the recalculation.
- **Permissions (decision 4).** New: `PAYROLL_PREPARE`, `PAYROLL_APPROVE`, `PAYROLL_FINALISE`, `PAYROLL_BANK_FILE_GENERATE`, `PAYROLL_STATUTORY_EXPORT`, `PAYROLL_CONFIGURATION_ADMIN`.
  - Roles holding `PAYROLL_RUN` are given read, prepare and statutory export. They are not given approve or finalise.
  - Roles holding only `USER_UPDATE` are given nothing. They are listed in `hr_payroll_access_review` as needing a decision.
  - The `ADMIN` role is given approve, finalise and configuration so payroll is never left without an approver. This is a choice I made, not one you gave me; take it off the role if you want someone else to approve.
  - `PAYROLL_BANK_FILE_GENERATE` is granted to nobody; there is no bank file yet.
- **Legacy fallback with an end date.** `PAYROLL_RUN` and `USER_UPDATE` still read and calculate until **31 December 2026** (`PayrollAccess.LEGACY_ACCESS_ENDS`), then stop with no further release. They never approve or finalise. The date is a constant I picked; change it if you want a different window.
- **`USER_READ` no longer opens payroll.** It used to show pay runs, payslips and EMP201 to nearly every user. I removed it at once rather than carrying it to the end date. A role that only had `USER_READ` and was used by a bookkeeper to view payroll needs `PAYROLL_READ`.
- **Screens.** Pay run list shows the new statuses, totals once calculated, the steps a person may take, what a run is waiting for, the review and the history. HR > Payroll checks has the approval setting and, for configuration admins, the access review.

**Not verified**

- Maven build, `PayrollControlServiceTest`, the changed `PayrollServiceTest`, `PayrollImpactServiceTest`, `HrControllerTest` (including the 14 new tests and the `@PreAuthorize` expressions that call `PayrollAccess`), the new JPQL (`previousIssued`), and migration V486 have not been run.
- Run and passing in a standalone harness: `PayRunWorkflowRulesTest` (12), `PayrollExceptionRulesTest` (7), `PayrollAccessTest` (7). The harness caught a rounding bug in the variance check, fixed.
- V486 checks worth a look when you run it: the status constraint is dropped by its default name `hr_pay_runs_status_check`; if your database named it differently the drop does nothing and the new statuses will be refused.
- Front end: type-check, 46 tests in `pages/hr` and `navigation`, and the build. Not looked at in a browser.

**Open items**

- `HR_READ` still opens payslip lists and PDFs, as before. Whether HR staff should see salaries is a decision for you.
- There is no bank file, no payment record and no "paid" state yet, so finalised does not mean paid.
- Corrections to a finalised run (an approved adjustment or reversal) are not built. Today a finalised run is simply locked.
- The payroll bureau product has its own pay run, calculator and permissions and was not touched.
- Maker/checker for changing an employee's salary or bank details belongs with Slice 3 (employee master and security).

## 9. Slice 3 status: employee master and security

**What was wrong**

- Editing an employee overwrote salary, allowances and bank details in place, by anyone with `HR_MANAGE` or `USER_UPDATE`, with no trace.
- Anyone who could read the staff list saw ID numbers, salaries and tax numbers.
- The termination reason was accepted and thrown away. A future "last day" took the person off payroll before final pay.
- Managers could never be set, so the org chart was always flat. No staff login was linked to an employee.
- No server-side check of ID numbers (the screen had one), duplicates, bank accounts, email or phone.

**What changed (V487 and the HR code)**

- **Three levels of view.** Working details (name, job, contact, manager) for anyone who can read employees. Personal data (ID, tax number, date of birth, gender, race, emergency contact) needs `HR_SENSITIVE_READ`. Pay (salary, allowances, masked bank account) needs `HR_COMPENSATION_READ`. Redaction happens in a new view service; `HrService.getEmployee` and `EmployeeResponse`, used by 17 other modules, are unchanged.
- **Pay and bank changes are requests.** `HR_COMPENSATION_MANAGE` asks (new values, effective date, reason). Someone else with `HR_COMPENSATION_APPROVE` decides, unless the business turned on self-approval in the payroll settings (then it is recorded as self-approved). One open request per employee and kind. The employee row changes only when the change is approved and its date has come; payroll applies due changes just before it calculates. A calculated run that predates an applied change gets a warning in its review.
- **Editing no longer touches pay or bank.** The old full update path (also used by `HrFacade`) now changes working details only.
- **Manager scope.** `HR_TEAM_READ` shows a manager their own team (the people below them in the reporting line), working details only. It needs the employee linked to their login. It is granted to nobody by the migration.
- **Checks on the way in.** SA ID (13 digits, valid date, Luhn), date of birth against the ID, minimum age 15, tax number (10 digits), email, phone, bank account (4 to 17 digits) and branch (6 digits), duplicate ID number.
- **Termination** needs a last day that has come (not before the start) and a reason of at most 200 characters, and records who did it and when.
- **Reporting lines.** Set or remove a manager; a person cannot report to themselves or to their own team. Link or unlink a staff login; one login to one employee.
- **Audit.** Per employee: created, edited (field names only), terminated, manager and login changes, every pay or bank request and decision, and views of personal data or pay (at most one per person per hour). Values are never stored in the trail.
- **Screens.** Employees tab hides what the person may not see, says "Restricted" instead of showing a blank, and offers only the buttons they may use. New: request pay change, request bank change, pay and bank changes waiting (with approve, reject, cancel), history, manager and login, termination with reason.

**Permissions migration (V487)**

- `HR_MANAGE` holders get `HR_SENSITIVE_READ`, `HR_COMPENSATION_READ` and `HR_COMPENSATION_MANAGE`. They are not given approve.
- Payroll roles (`PAYROLL_RUN`, `PAYROLL_PREPARE`, `PAYROLL_APPROVE`) get `HR_COMPENSATION_READ` so the pay run screens keep working.
- The `ADMIN` role gets `HR_COMPENSATION_APPROVE` so there is always an approver. My choice, as with payroll approval; remove it if someone else should decide.
- Roles with only `HR_READ` (or `USER_READ`) lose sight of ID numbers and salaries. They are listed in `hr_employee_access_review`, and in `GET /hr/employees/access-review`, with `needs_review` set.

**Decisions I took that you may want to change**

- A termination cannot be recorded for a future last day. The cost is that notice periods cannot be diarised here; the benefit is that nobody drops off payroll before their final pay.
- Portal access of a terminated employee is left as it is, so they can still reach payslips and IRP5s. If you want it revoked at termination, say so.
- The audit trail records views of personal data and pay. That is a privacy choice (POPIA accountability); the trail is readable only by `HR_MANAGE`, `HR_SENSITIVE_READ` and `HR_COMPENSATION_APPROVE`.
- Creating an employee still takes pay and bank details directly (the starting figures), validated. Only changes after creation need approval.

**Not verified**

- Maven build and every Mockito test: `EmployeeChangeServiceTest`, `EmployeeViewServiceTest`, `HrServiceEmployeeTest`, the changed `HrControllerTest`, `PayrollServiceTest` and `PayrollControlServiceTest`. The JPQL (`countByIdNumber`, `findByUserId`, `findNotTerminated`, `due`, `appliedSince`, `countSince`), the `jsonb` mapping on `HrEmployeeChange`, and migration V487 have not been run.
- Run and passing in a standalone harness: `EmployeeRulesTest` (11), `EmployeeChangeRulesTest` (12), `OrgRulesTest` (7), `HrAccessTest` (6).
- Front end: 22 new tests in `Employees.test.tsx` pass; ESLint reports nothing new in the changed files (the 18 it reports in `EmployeesTab.tsx` were there before). Not looked at in a browser.
- **Correction to section 8.** The type-check I reported for Slice 2 ran `tsc -p .`, which checks nothing in this repository (the root `tsconfig.json` has `"files": []`). The real check is `tsc -p tsconfig.app.json`. Run properly now, it shows no errors in the Slice 1, 2 or 3 files, and many existing errors elsewhere in the repository.
- Also found and fixed in this slice: `HrControllerTest` from Slice 2 was missing the import of `PayrollControlService`, so it would not have compiled.

**Open items**

- Other modules that return `EmployeeResponse` through their own endpoints (agriculture, training, expenses, legal practice, recruiter, security guard link and others) still expose ID number, tax number and salary to whoever can call those endpoints. Each needs its own look.
- Organisation units, positions and cost centres are not modelled; "department" is still free text from a fixed list.
- Final pay on termination (leave payout, notice pay, pro-rata) is not built.
- Pay history is the change record from now on. Salaries before this slice cannot be reconstructed.
- Suspended and on-leave employees are still left out of payroll (only `ACTIVE` is paid) and there is no screen to reach those statuses.
- `HR_READ` still opens the payslip lists. Whether HR staff should see salaries through payslips is your decision.

## 10. Slice 4 status: leave as a ledger

**What was wrong**

- Leave balances were a single editable row per type and year, seeded once. Nothing recorded why a balance changed, approval could be done by anyone with a read right, rejection needed no reason, requests could overlap, and requests larger than the balance were accepted.
- Public holidays and half days were not handled, and sick leave had no cycle.

**What changed**

- Balances are now computed from an immutable ledger (`hr_leave_ledger`, blocked from update and delete by a trigger): opening, accrual, carry forward, adjustment, held for a request, hold released, taken, reversed.
- A request holds its days when made, releases them on rejection or cancellation, and turns them into days taken on approval.
- BCEA rules live in `LeaveRules`: annual leave earned at one day per 17 days worked (15 days a year on a five-day week), sick leave 30 days per three-year cycle, family responsibility 3 days a year. Weekends and public holidays are not leave days. A half day is one day only.
- New rights `LEAVE_APPROVE` and `LEAVE_ADJUST`. Approving and rejecting need `LEAVE_APPROVE` or `HR_MANAGE`; a team manager decides only for their own team; nobody decides their own request unless the payroll self-approval setting allows it. Rejection needs a reason.
- Cancel: a waiting request or approved leave that has not started can be cancelled by the employee's approver; leave that has started needs `LEAVE_ADJUST` and a reason. HR can adjust a balance with a dated, signed, explained entry.
- Statement endpoint and screen show every movement behind a balance.
- Payroll checks warn (`UNPAID_LEAVE_NOT_DEDUCTED`) when approved unpaid leave falls in a run.
- Front end: half day, medical certificate flag (expected for sick leave over two days), cancel, statement, adjustment, a CANCELLED status, and buttons shown only to people who hold the right.

**Decisions I took that you may want to change**

- Unused annual leave carries forward in full. Nothing expires.
- The ledger starts at the beginning of the year in which V488 runs; older balances are migrated as opening entries with a reconciling adjustment.
- Self-approval reuses the payroll setting `allow_self_approval`.
- Study leave is no longer created for everyone; existing entitlements were migrated as opening balances.
- Backdating: a request cannot start in a closed year or more than 90 days ago; HR uses an adjustment.

**Not verified**

- Maven build and every Mockito test: `LeaveServiceTest`, the changed `HrControllerTest`, `HrServiceEmployeeTest` and `PayrollControlServiceTest`. The JPQL in the new repositories and migration V488 have not been run. Expected values in `LeaveServiceTest` for accrual and carry forward are my reading of the rules, not a run.
- Run and passing in a standalone harness: `LeaveRulesTest` (17).
- Front end: 14 tests in `Leave.test.tsx` pass. ESLint reports only `no-explicit-any`, as the file did before (20 before, 29 now, the new ones in the same style). Not looked at in a browser.

**Open items**

- Leave expiry or forfeiture, and a carry-over cap, are not built.
- Leave payout at termination (final pay) is not built.
- A five-day week is assumed; part-time pro-rata is not modelled.
- Payroll warns about unpaid leave but does not deduct it.
- Employees cannot cancel their own request from the portal yet.
- Sick leave that crosses a cycle boundary counts against the cycle it starts in.
- Reading a balance can write an accrual entry; two requests made at the same moment can collide on the unique key and need a retry.

## 11. Slice 5 status: tax certificates and the EMP501 workpaper

**What was missing**

- No IRP5 or IT3(a), no EMP501 figures and no way to compare the EMP201 returns with what employees were paid. Section 4 already recorded this as PARTIAL.

**What changed**

- Certificates are built per employee for the interim (March to August) or annual (March to February) reconciliation, from payslips of finalised pay runs only. Tax years are named by the year they end in (2027 is 1 March 2026 to 28 February 2027).
- A certificate is an IRP5 when tax was deducted and an IT3(a) when none was. Each gets a 30-digit number: the employer's 10-digit PAYE reference, the 4-digit year, the period digits (08 or 02) and 14 digits from a sequence. Numbers are never reused, including after cancelling.
- The reconciliation compares PAYE, UIF and SDL three ways (payslips, certificates, EMP201 returns). Issuing is refused while a run is not finalised, an EMP201 is missing, the three do not agree, the employer's PAYE reference is missing, or an employee has no valid ID number or has tax deducted above the income paid.
- Issued certificates are locked by database triggers (no update except cancel and release, no delete). A correction cancels the old one and issues the next version with a new number and a reason.
- Interim certificates cannot be released to employees (SARS: interim is for SARS only). Annual ones are released by an explicit action, then appear in the employee portal.
- New right `PAYROLL_CERTIFICATE_ISSUE` (administrators only). Viewing and the spreadsheet use `PAYROLL_STATUTORY_EXPORT`. The employer's PAYE, SDL and UIF reference numbers and the retirement fund type are set by `PAYROLL_CONFIGURATION_ADMIN`.
- Payslips now keep the medical tax credit actually applied (code 4116). Earlier payslips have none recorded and the certificate says so.

**What the certificate does not contain, and why it matters**

- Only these codes are produced: 3601, 3605, 3607, 3701, 3713 (income), 4001/4003/4006 and 4005 (employee contributions), 4102, 4116, 4141, 4142 (tax).
- Not produced: the totals SARS also requires (3695 to 3699, 3696, 4149), 4582, employer contributions (3810, 3817, 3825, 3828, 4472 to 4475) and all other fringe benefits. SARS's own documents I could read confirm 3696 as non-taxable income and 3699 as taxable gross employment income, but not how they are built, and payroll does not record employer contributions at all. **Where an employer pays towards medical aid or a retirement fund, a certificate from here understates taxable income.**
- The spreadsheet is a workpaper to complete the submission from in e@syFile. It is not a SARS import file.

**Decisions I took that you may want to change**

- One retirement fund type for the whole company (pension, provident or retirement annuity), because payroll does not record it per employee.
- Bonuses and overtime would use 3605 and 3607 if payroll ever paid them; today it pays neither, so they are always zero.
- The travel allowance is reported in full under 3701; how SARS wants the taxable portion shown was not confirmed.
- Payments to SARS are not recorded in HandyFlow, so the reconciliation cannot compare with what was paid. It says so on screen.

**Not verified**

- Maven build and every Mockito test, including `TaxCertificateServiceTest`. The JPQL (`paidBetween`, `findByPayRuns`, `forPayRuns`, `forReconciliation`, `releasedFor`) and migration V489 have not been run.
- Run and passing in a standalone harness: `TaxCertificateRulesTest` (18).
- Front end: 15 tests in `TaxCertificates.test.tsx` pass; ESLint reports nothing in the new files. Not looked at in a browser.
- Not checked against SARS: the validation rules for the file (field lengths, nature of person, tolerances between EMP201 and certificates). I could not read the Business Requirement Specification (V25.3.0).

**Open items**

- Employer medical aid and retirement fund contributions and the other fringe benefits, so the certificate is complete.
- The SARS import file for e@syFile, and a certificate PDF in SARS's layout for employees.
- Recording payments made to SARS against each EMP201.
- Bonus, overtime and back pay in payroll (the engine pays a regular monthly salary only).
- ETI is not modelled.
- Certificates for employees who left mid-year show the months they worked but not their reasons for leaving or directive numbers.

## 12. Slice 6 status: onboarding

**What was wrong**

- A new employee had nothing to track what must be in place before payroll or first day: ID, tax number, bank details, written particulars, UIF declaration, login, induction. Recruiter's "convert to employee" creates the employee with no ID, tax or bank details ("completed later in HR") and nothing tracked that later.
- No place to record probation or when it ends.

**What changed**

- Every new employee gets an onboarding. It is started by a Modulith listener on `EmployeeCreatedEvent`, so it covers employees added by hand and those converted from Recruiter, and a failure there cannot undo or block creating the employee. HR can also start one for any existing employee.
- Each company has its own task list, made the first time from 19 standard tasks (written particulars under BCEA s29, UIF declaration, POPIA privacy notice, OHSA induction, probation review and so on). Editing the list affects only onboardings started afterwards; tasks already created are copies.
- Seven tasks are checked against the employee record (ID number, tax number, bank details, salary, manager, login, emergency contact). They tick themselves when the record is valid and cannot be ticked by hand, so a task cannot say done while the data is missing. Other tasks are ticked by a manager or the person assigned, or waived with a reason.
- Finishing is refused while a required task is open. A finished or cancelled onboarding can no longer be changed. Cancelling needs a reason.
- Managers with team access only see onboarding for people in their own team.
- New permission `HR_ONBOARDING_MANAGE` (V490), granted to roles that hold `HR_MANAGE`.
- Screen: People > Onboarding, with progress per person, tasks grouped by category, due dates and overdue marking, and the actions above.

**Decisions I took that you may want to change**

- Recruiter stays the hiring system; onboarding starts from the employee record. Nothing was added to Recruiter.
- Probation is 3 months from the start date by default (editable, at most 12).
- Medical scheme, retirement fund and contract signing are manual ticks because HR does not record them on the employee.
- Existing employees were not given an onboarding.

**Not verified**

- Maven build and every Mockito test, including `OnboardingServiceTest`. The JPQL in the three new repositories and migration V490 have not been run.
- Run and passing in a standalone harness: `OnboardingRulesTest` (14).
- Front end: 11 tests in `Onboarding.test.tsx` pass; ESLint reports nothing in the new files. Not looked at in a browser.
- If the listener fails, Modulith keeps the event publication for resubmission; I have not seen that happen here.

**Open items (Recruiter, deliberately not built)**

- Requisition approval and offer approval.
- Candidate data retention and deletion under POPIA.
- Overdue notifications or a digest to managers. Overdue is only shown on screen.
- Employees converted from Recruiter start with ID, tax and bank tasks open until HR completes the record.
- Document uploads (signed contract, ID copy) are ticked, not stored against the task.

## 13. Slice 7 status: attendance and overtime

**What was wrong**

- HR had no attendance at all (the Security module has guard shifts, nothing for general staff), so lateness, absence and hours worked were not recorded anywhere.
- Payroll paid a regular monthly salary only. Overtime, which the Basic Conditions of Employment Act (BCEA) requires to be paid at 1.5 times (2 times for a Sunday not ordinarily worked), had no source and no place on the payslip.

**What changed**

- Employees clock in and out (their login is linked to their employee record). HR, or the manager of a person, adds, corrects or voids an entry with a reason. Nothing is deleted; every change is kept in a log that the database refuses to edit. Nobody corrects their own time unless they hold HR attendance management. A person cannot be clocked in twice.
- Work schedules (days, start, end, unpaid break) per company, with a default; an employee works the default unless assigned another. A company with none gets a Monday to Friday 08:00 to 17:00 week with an hour's break the first time one is needed.
- A day is read against the schedule, public holidays and approved leave: present, late (more than 5 minutes), absent, on leave, public holiday, rest day, no clock-out, or still to come. Hours beyond the schedule on a working day, and all hours on an unscheduled day, are overtime.
- BCEA limits are flagged, not enforced: more than 45 ordinary hours or 10 overtime hours in a week, more than 12 hours in a day, less than 12 hours' rest.
- Overtime claims are made from the record (one per employee per day, safe to ask for again). Rate = monthly salary x 12 / 52 / ordinary weekly hours (s 35), times 1.5, or 2 for a Sunday not ordinarily worked. The rate and amount are fixed when the claim is made. Someone other than the employee approves or rejects; approving locks that day's entries until the claim is rejected.
- A pay run takes approved overtime worked up to the end of its period that no other run has taken, adds it to the month's remuneration before PAYE, UIF (up to the ceiling) and SDL, shows it on the payslip (code 3607 on the tax certificate), and marks the claims as taken so they cannot change or be paid twice. Recalculating releases and re-takes them. The run lists how many claims are still waiting.
- Permissions `HR_ATTENDANCE_MANAGE` and `HR_OVERTIME_APPROVE` (V491, V492), given to roles that hold `HR_MANAGE`. Screen: People > Attendance (My time, Register, Overtime, Schedules).

**Rules I took from outside the code, and how sure I am**

- BCEA limits and rates (ss 9, 10, 14, 15, 16, 35) and the earnings threshold (R261,748.45 from 1 April 2025; R269,600.90 from 1 May 2026) come from law-firm summaries (Cliffe Dekker Hofmeyr, 20 April 2026; Bowmans; DLA Piper; Timesheet.io and PayTools for the section detail). I did not read the Act or the Gazette notice. The threshold values are versioned in `WorkingTimeRules` with their source; a date before 1 April 2025 is refused rather than guessed.
- Above the threshold the Act's hours and overtime provisions do not apply, so no claim is made for those employees; their overtime is whatever their contract says. The test uses basic monthly salary x 12; whether the Act's "earnings" include allowances was not checked.
- PAYE on overtime: I add the month's overtime to the month and annualise it, which is the periodic method the rest of the engine uses. This taxes the overtime as if it recurred every month, so PAYE can be higher than the year needs until the year is assessed. SARS also allows an averaging method for fluctuating pay; I could not confirm from a SARS source which one it expects and did not implement averaging. The run says this on screen.
- UIF and SDL on overtime: I treated overtime as remuneration for both. Not confirmed against the UIF and SDL Acts.

**Decisions I took that you may want to change**

- A schedule is the employee's ordinary hours; no shifts across midnight.
- One punch pair in a day has the schedule's break taken off when it is longer than 5 hours; several pairs are taken to have the breaks between them.
- Work on an unscheduled Saturday is overtime at 1.5 from the first minute, even for someone whose contract is under 45 hours.
- Public holiday work is recorded and flagged, but the pay due under s 18 is not calculated or claimed. I could not confirm the exact wording of s 18, so HR decides it.
- Sunday as an ordinary working day: the Sunday rate for the ordinary hours (s 16) is flagged, not paid; only hours beyond the schedule are claimed, at 1.5.
- Night-work allowances and transport (s 17), the 36-hour weekly rest, and the one-hour meal interval are not checked.
- Overtime is not capped or refused when it breaks the weekly limit; it is flagged.
- Approval is by `HR_MANAGE` or `HR_OVERTIME_APPROVE` only; team managers can claim and correct for their team but not approve.
- Half-day leave is shown as such and the shortfall is not counted against the employee.

**Not verified**

- Maven build and every Mockito test, including `AttendanceServiceTest`, `OvertimeServiceTest` and the two new tests in `PayrollServiceTest`. The JPQL in the new repositories and migrations V491 and V492 have not been run.
- Run and passing in a standalone harness: `AttendanceRulesTest` (22) and `OvertimeRulesTest` (9). The PAYE figure used in the payroll test (R4,941.78 on R31,003) was reproduced by running `SarsPayeCalculator` here.
- Front end: 14 tests in `Attendance.test.tsx` pass; ESLint reports nothing in the new files. Not looked at in a browser.
- Time zone: entries are read in South African time (no daylight saving). A tenant outside South Africa would be wrong.

**Open items**

- No device or file import (biometric clocks, CSV); no geofence or IP check on clock-in; no kiosk mode.
- No notifications for missed clock-outs, absence, or claims waiting for approval.
- Unpaid leave and unauthorised absence still do not reduce pay (already listed for payroll).
- Time off in lieu instead of overtime pay, and agreed overtime (s 10(1)) as a recorded agreement, are not modelled.
- Bonus, back pay, final pay and the SARS averaging method are still outstanding in payroll.
- If a way to cancel a calculated pay run is added, it must release that run's overtime claims (`OvertimeService.takeFor` already does so on recalculation).
- Employees whose login is not linked to their record cannot clock in; the screen says so.

## 14. Slice 8 status: employee relations (discipline, grievances, incapacity)

**What was wrong**

- The only record was one table of disciplinary entries with a single sanction. There was no process behind it: no notice, charges, hearing, chair, finding, appeal or timeline, and nothing for grievances or incapacity.
- The screen sent incident types (such as `NOTICE_TO_ATTEND`) that the database check on `incident_type` refused for some values, and the Security complaint referral sent values that check did not allow either. Saving could fail on a valid choice.
- `HrController` has not compiled since Slice 4. My Slice 4 patch (e7395e93) deleted `HrService.recordDisciplinaryOutcome` while the controller still called it. Patch 0420 removes that endpoint.
- Nothing stopped a person working a case about themselves, and nothing kept sensitive cases (harassment, ill health) from everyone with general HR access.

**What changed**

- One case table, `hr_er_cases` (the old `hr_disciplinary` table renamed, ids kept), now holds discipline, grievances and incapacity, each with its own route:
  - Discipline: reported, investigating, notice issued, hearing held, decided, appealed, closed (or withdrawn). Charges are stated in the notice and each gets a finding. A counselling, verbal warning or written warning may be given without a hearing after the employee has responded (the "informal" route). A final written warning or a dismissal needs notice and a hearing.
  - Grievance: lodged by the employee (or by HR for them), decided by someone who is neither the complainant nor the person complained about, with a "next level" to a different decider.
  - Incapacity: poor performance or ill health, with the steps the process expects recorded (standard set, support given, time to improve; or nature investigated, medical evidence, alternatives considered), a review period, a meeting, and an outcome.
- The case timeline (`hr_er_events`) is kept by a database trigger that refuses edits and deletes. Each entry says whether the employee may see it.
- Warnings carry a validity in months from company policy and are listed as in force or expired. The chair sees the employee's warnings in force when deciding. A warning that came from the old screen, with no recorded validity, is listed separately and not counted as in force.
- Appeal: the deadline runs from the day the employee was told. An appeal can confirm, replace with a lighter outcome, or overturn. It can never make the outcome harsher. A dismissal records the date the employee was told and the 30-day CCMA referral deadline.
- Precautionary suspension is recorded on full pay, with reason and dates. It does not change the employee's status, because payroll pays people whose status is ACTIVE.
- Company policy (`hr_er_policy`, screen: Policy and warnings): hearing notice 48 hours, warnings valid 3, 6 and 12 months, 7 days to appeal, 5 working days to answer a grievance, 28 days minimum review. These are defaults for you to change, not law.
- Permissions `HR_ER_READ`, `HR_ER_MANAGE`, `HR_ER_DECIDE` (V493), given to roles that hold `HR_MANAGE`; `HR_ER_READ` also to roles that hold `HR_READ`. Cases that are confidential (ill health, harassment, discrimination, victimisation, or flagged by HR) also need `HR_SENSITIVE_READ` unless you own the case; one you may not see answers "not found". Nobody may work a case about themselves.
- Case numbers `ER-YYYY-NNNN`, from an atomic counter.
- Employees see their own matters under People > Employee relations > My cases: a grievance they raised always; a matter against them only once they have been given notice or an outcome. Investigation notes and the case officer's working are never sent to them. They can lodge a grievance, confirm they received an outcome (this is not agreement) and appeal.
- Old records: kept, marked "Imported record"; closed, with the old sanction as the outcome.

**Rules I took from outside the code, and how sure I am**

- The figures that come from law: 30 days to refer a dismissal dispute to the CCMA. Everything else numerical is company policy.
- The 2025 Code of Good Practice: Dismissal asks for a fair reason and a fair procedure, reasonable notice of a hearing, a chance to respond, and, for poor performance and ill health, the steps built into the incapacity route. I took the content of those expectations from law-firm summaries, not the Code. The sources disagreed on the date it took effect; I did not settle it.
- The informal route (a written warning without a hearing) is my own decision about what a small business reasonably does. Many policies require a hearing for a written warning; if yours does, say so and I will move it.

**Decisions I took that you may want to change**

- The table was renamed, not just extended, so any query that names `hr_disciplinary` would break. I found none in the Java code; Security keeps a column called `hr_disciplinary_id`, which still holds the same case ids and needed no change.
- People who hold only `USER_READ` no longer see disciplinary cases.
- Working days for the grievance response skip weekends, not public holidays (no tenant holiday calendar yet).
- A dismissal outcome does not terminate the employee. Termination and final pay stay separate steps.
- Suspension without pay is not offered.
- An appeal decider may not be the chair of the original hearing.

**Not verified**

- The Maven build, every Mockito test for Slices 1 to 8, and migrations V488 to V493 have not been run. V493 and the JPQL have not run against PostgreSQL; the Hibernate mappings have not been validated by Spring.
- In a standalone harness: 49 rules tests and 73 service tests pass; a deliberate break of the logic made 3 of 39 sampled tests fail. The harness uses my own small stand-ins for Mockito, Spring and Lombok, so it shows the logic is consistent, not that the wiring works.
- `HrService` and `HrController` edits were read through but not compiled in that harness. Package-private injection of `ErCaseSupport` and `ErCaseViews` is untested.
- Front end: 19 tests in `EmployeeRelations.test.tsx` pass (a deliberate break of the notice and decision checks made 2 fail); ESLint reports nothing in the new files; `tsc` reports the same 138 errors as before and none in these files. Not looked at in a browser.

**Open items**

- Letters: the notice of hearing, outcome letters and warning letters should come from the Letter & Document Generation Centre; for now the screen records the facts and the employee sees them on screen.
- Evidence and attachments are not built.
- No notifications or reminders (grievance late, hearing tomorrow, appeal window closing).
- Recording a dismissal does not start termination or final pay.
- No public holiday calendar for working-day counts.
- Old warnings with no recorded validity need a person to set it.
- The Security complaint referral still opens a plain disciplinary case; it does not carry the complaint's evidence across.

## 15. Slice 9 status: Letter & Document Generation Centre

**What was wrong**

- HR wrote letters outside the system. Nothing tied a letter to the employee file or to a disciplinary, grievance or incapacity case, and nobody could show later exactly what an employee was sent.

**What changed**

- Per-tenant letter templates with `{{merge.fields}}`. Eleven standard templates (confirmation, certificate of service, notice of hearing, notice of meeting, suspension, warning, dismissal, grievance outcome, incapacity outcome, appeal outcome, general) are created on first use. HR can edit them, switch them off, write new ones and restore the standard wording.
- A letter is prepared as a draft that HR can edit, then issued. Issuing generates the PDF, stores it on the employee file and records its SHA-256. A database trigger then refuses any change to the wording, file or employee. A letter can only be voided, with a reason, and the PDF is kept.
- Gaps are marked `[[Label]]` and block issuing. Fields in the "type in" group are never filled automatically.
- Salary, ID number, tax number and bank details are never available as merge fields.
- A letter prepared from a case uses the case's details, and the case timeline records LETTER_ISSUED, LETTER_VOIDED and LETTER_ACKNOWLEDGED. Who sees a case letter follows who may see the case, and nobody can prepare, edit or issue a letter about themselves.
- The employee sees only issued letters ("My letters"), downloads the PDF, and can confirm receipt.
- New permissions HR_LETTER_READ, MANAGE and ISSUE (migration V494).

**What is not done, or not what it may seem**

- Confirming receipt is a recorded receipt with the file fingerprint. It is not an advanced electronic signature, and receipt is not agreement.
- The standard wording is a starting point. A labour adviser must read it before it is relied on.
- Appeal and CCMA dates in a letter run from the day the employee was told, or from today if that is not recorded yet.
- The certificate of service has typed-in gaps (address, sectoral standard, remuneration, optional reason).
- A draft is discarded by voiding it.
- No email to the employee when a letter is issued, no bulk or mail-merge, no letterhead or logo, and the case screens do not suggest a template.
- Two people opening the templates screen at the very first moment can race the seeding of the standard templates.

**Not verified**

- The Maven build, real Mockito runs, V494 and the JPQL against PostgreSQL, Hibernate mappings and Spring wiring have not been run.
- The real iText PDF has never been rendered here. Characters outside the default font's range may not render.
- Backend logic was checked with a stub harness (22 rules tests, 28 service tests). Front end: 16 tests, ESLint clean.
