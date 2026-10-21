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
