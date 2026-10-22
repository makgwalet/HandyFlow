// Types shared by the HR reports screens. They mirror the server's ReportDtos.
export { card, errorText, input, plain, primary, td, th, unwrap } from "./attendance.shared"
import type { Group } from "./reports.logic"
export type { Group } from "./reports.logic"

export const BASE = "/api/v1/hr/reports"

export interface Line { label: string; count: number; amount: number | null; hours: number | null }

export interface Workforce {
  from: string; to: string; opening: number; closing: number; joiners: number; leavers: number
  turnoverPercent: number | null; annualisedTurnoverPercent: number | null
  byDepartment: Group[]; byEmploymentType: Group[]; tenure: Group[]; age: Group[]; leaversByReason: Group[]
  demographicsShown: boolean; gender: Group[]; race: Group[]; disability: Group[]; notes: string[]
}

export interface LeaveLine { leaveType: string; requests: number; days: number }
export interface AttendanceOutlier { employeeNumber: string; employeeName: string; department: string | null; absentDays: number; lateDays: number }
export interface AttendanceSummary { employees: number; presentDays: number; lateDays: number; absentDays: number; leaveDays: number; workedHours: number; overtimeHours: number; mostAbsent: AttendanceOutlier[] }
export interface TimeReport {
  from: string; to: string; leave: LeaveLine[]; leavePending: number; leavePendingStale: number; sickDays: number; sickEmployees: number
  attendance: AttendanceSummary | null; attendanceNote: string | null; overtimeByKind: Line[]; overtimeByDepartment: Line[]; overtimePending: number; notes: string[]
}

export interface RunLine {
  number: string; periodStart: string; periodEnd: string; payDate: string; status: string; employees: number; gross: number; paye: number
  uifEmployee: number; uifEmployer: number; sdl: number; net: number; employerCost: number; counted: boolean
}
export interface PayrollTotals { gross: number; overtime: number; paye: number; uifEmployee: number; uifEmployer: number; sdl: number; net: number; employerCost: number; runs: number }
export interface Emp201Line { periodStart: string; periodEnd: string; dueDate: string; paye: number; uif: number; sdl: number; payable: number; status: string; paymentRef: string | null; overdue: boolean }
export interface PayrollReport { from: string; to: string; runs: RunLine[]; totals: PayrollTotals; byDepartment: Line[]; emp201: Emp201Line[]; notes: string[] }

export interface Check { code: string; title: string; count: number; people: string[]; why: string }
export interface ErSummary {
  opened: number; closed: number; open: number; longOpen: number; hearingsOverdue: number; warningsExpiringSoon: number; averageDaysToClose: number | null
  byType: Group[]; byCategory: Group[]; outcomes: Group[]; confidentialLeftOut: number
}
export interface Compliance { from: string; to: string; checks: Check[]; employeeRelations: ErSummary | null; notes: string[] }
