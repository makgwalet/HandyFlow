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
