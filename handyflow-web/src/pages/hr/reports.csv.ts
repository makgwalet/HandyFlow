// What each report looks like as a spreadsheet. Pure functions so they can be checked without a screen.
import { groupText, type Cell } from "./reports.logic"
import type { Compliance, Group, PayrollReport, TimeReport, Workforce } from "./reports.shared"

const section = (title: string, groups: Group[]): Cell[][] => groups.length === 0 ? [] : [[title], ...groups.map(g => [g.label, groupText(g)]), []]

export function workforceRows(w: Workforce): Cell[][] {
  return [
    ["Workforce report", w.from, w.to], [],
    ["Opening headcount", w.opening], ["Closing headcount", w.closing], ["Joiners", w.joiners], ["Leavers", w.leavers],
    ["Turnover for the period (%)", w.turnoverPercent], ["Turnover for a year (%)", w.annualisedTurnoverPercent], [],
    ...section("By department", w.byDepartment), ...section("By employment type", w.byEmploymentType), ...section("By length of service", w.tenure),
    ...section("By age", w.age), ...section("Why people left", w.leaversByReason),
    ...(w.demographicsShown ? [...section("Gender", w.gender), ...section("Race", w.race), ...section("Disability", w.disability)] : []),
    ...w.notes.map(n => [n]),
  ]
}

export function timeRows(t: TimeReport): Cell[][] {
  return [
    ["Time and leave report", t.from, t.to], [],
    ["Leave taken (approved, by first day)"], ["Type", "Requests", "Days"], ...t.leave.map(l => [l.leaveType, l.requests, l.days]), [],
    ["Leave waiting for a decision", t.leavePending], ["Waiting more than 14 days", t.leavePendingStale], ["Sick leave days", t.sickDays], ["Employees with sick leave", t.sickEmployees], [],
    ...(t.attendance ? [
      ["Attendance"], ["Employees", t.attendance.employees], ["Days present", t.attendance.presentDays], ["Days late", t.attendance.lateDays],
      ["Days absent", t.attendance.absentDays], ["Days on leave", t.attendance.leaveDays], ["Hours worked", t.attendance.workedHours], ["Overtime hours", t.attendance.overtimeHours], [],
      ["Most absent"], ["Number", "Name", "Department", "Days absent", "Days late"],
      ...t.attendance.mostAbsent.map(o => [o.employeeNumber, o.employeeName, o.department, o.absentDays, o.lateDays]), [],
    ] : [[t.attendanceNote]]),
    ["Approved overtime by kind"], ["Kind", "Claims", "Hours", "Amount"], ...t.overtimeByKind.map(l => [l.label, l.count, l.hours, l.amount]), [],
    ["Approved overtime by department"], ["Department", "Claims", "Hours", "Amount"], ...t.overtimeByDepartment.map(l => [l.label, l.count, l.hours, l.amount]), [],
    ["Overtime waiting for a decision", t.overtimePending], ...t.notes.map(n => [n]),
  ]
}

export function payrollRows(p: PayrollReport): Cell[][] {
  const tt = p.totals
  return [
    ["Payroll report", p.from, p.to], [],
    ["Totals from finalised and completed runs", tt.runs, "runs"], ["Gross", tt.gross], ["Overtime in gross", tt.overtime], ["PAYE", tt.paye], ["UIF (employee)", tt.uifEmployee],
    ["UIF (employer)", tt.uifEmployer], ["SDL", tt.sdl], ["Net pay", tt.net], ["Employer cost", tt.employerCost], [],
    ["Pay run", "Pay date", "Status", "Counted", "Employees", "Gross", "PAYE", "UIF employee", "UIF employer", "SDL", "Net", "Employer cost"],
    ...p.runs.map(r => [r.number, r.payDate, r.status, r.counted ? "Yes" : "No", r.employees, r.gross, r.paye, r.uifEmployee, r.uifEmployer, r.sdl, r.net, r.employerCost]), [],
    ["Employer cost by department"], ["Department", "Employees", "Cost"], ...p.byDepartment.map(l => [l.label, l.count, l.amount]), [],
    ["EMP201"], ["Period end", "Due", "PAYE", "UIF", "SDL", "Payable", "Status", "Payment reference", "Overdue"],
    ...p.emp201.map(e => [e.periodEnd, e.dueDate, e.paye, e.uif, e.sdl, e.payable, e.status, e.paymentRef, e.overdue ? "Yes" : "No"]), [],
    ...p.notes.map(n => [n]),
  ]
}

export function complianceRows(c: Compliance): Cell[][] {
  const er = c.employeeRelations
  return [
    ["Compliance report", c.from, c.to], [],
    ["Check", "Count", "People"], ...c.checks.map(k => [k.title, k.count, k.people.join("; ")]), [],
    ...(er ? [
      ["Employee relations"], ["Cases opened", er.opened], ["Cases closed", er.closed], ["Average days to close", er.averageDaysToClose], ["Cases open now", er.open],
      ["Open more than 90 days", er.longOpen], ["Hearings overdue", er.hearingsOverdue], ["Warnings expiring in 30 days", er.warningsExpiringSoon],
      ["Confidential cases left out", er.confidentialLeftOut], [],
      ...section("Opened by type", er.byType), ...section("Opened by category", er.byCategory), ...section("Outcomes decided", er.outcomes),
    ] : []),
    ...c.notes.map(n => [n]),
  ]
}
