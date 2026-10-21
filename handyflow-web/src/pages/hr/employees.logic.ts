// Pure helpers for the employee screens: what a person is shown, and checks done before a request is sent.
// The server decides and redacts; this only avoids showing a button whose click would be refused, and catches
// typing mistakes early. The messages match the server's.

export interface EmployeeRights {
  /** Add, edit, terminate, set manager and login. */
  manage: boolean
  /** ID number, tax number, date of birth, gender, race, emergency contact. */
  sensitive: boolean
  /** Salary, allowances and bank details. */
  compensation: boolean
  askPay: boolean
  decidePay: boolean
  /** May open the history of a record. */
  audit: boolean
}

export function employeeRights(permissions: string[]): EmployeeRights {
  const has = (...p: string[]) => p.some(x => permissions.includes(x))
  return {
    manage: has("HR_MANAGE", "USER_UPDATE"),
    sensitive: has("HR_SENSITIVE_READ"),
    compensation: has("HR_COMPENSATION_READ", "HR_COMPENSATION_MANAGE", "HR_COMPENSATION_APPROVE"),
    askPay: has("HR_COMPENSATION_MANAGE"),
    decidePay: has("HR_COMPENSATION_APPROVE"),
    audit: has("HR_MANAGE", "HR_SENSITIVE_READ", "HR_COMPENSATION_APPROVE"),
  }
}

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
export const today = () => iso(new Date())

/** Same rules as the server: a last day that has come, not before the start date, and a reason. */
export function terminationProblem(lastDay: string, reason: string, startDate: string | null, now: string = today()): string | null {
  if (!lastDay) return "Enter the last day worked."
  if (startDate && lastDay < startDate) return "The last day worked is before the start date."
  if (lastDay > now) return "The last day worked is in the future. Record the termination on or after that day, so final pay is not missed."
  if (!reason.trim()) return "Give a reason for the termination."
  if (reason.trim().length > 200) return "The reason can be at most 200 characters."
  return null
}

export function reasonProblem(reason: string): string | null {
  if (!reason.trim()) return "Give the reason for the change."
  if (reason.trim().length > 500) return "The reason can be at most 500 characters."
  return null
}

/** Effective date: up to 31 days back and 366 ahead, and not before the employee started. */
export function effectiveProblem(effective: string, startDate: string | null, now: string = today()): string | null {
  if (!effective) return "Choose the date the change takes effect."
  if (startDate && effective < startDate) return "The change cannot take effect before the employee started."
  const n = new Date(now + "T00:00:00")
  const back = new Date(n); back.setDate(back.getDate() - 31)
  const ahead = new Date(n); ahead.setDate(ahead.getDate() + 366)
  if (effective < iso(back)) return "The change cannot take effect more than a month ago."
  if (effective > iso(ahead)) return "The change cannot take effect more than a year from now."
  return null
}

export interface PayDraft { grossSalary: string; travelAllowance: string; medicalAidContribution: string; pensionContribution: string }

export function compensationProblem(d: PayDraft): string | null {
  const gross = Number(d.grossSalary)
  if (!d.grossSalary.trim() || !(gross > 0)) return "Gross salary must be more than zero."
  if (gross > 5_000_000) return "Gross salary looks too high. Check the amount."
  const n = (v: string) => (v.trim() === "" ? 0 : Number(v))
  const travel = n(d.travelAllowance), med = n(d.medicalAidContribution), pen = n(d.pensionContribution)
  if ([travel, med, pen].some(v => Number.isNaN(v))) return "Allowances and contributions must be numbers."
  if (travel < 0 || med < 0 || pen < 0) return "Allowances and contributions cannot be negative."
  if (med + pen > gross) return "Medical aid and pension together are more than the gross salary."
  if (travel > gross) return "The travel allowance is more than the gross salary."
  return null
}

export function bankProblem(bankName: string, account: string, branch: string): string | null {
  if (!bankName.trim()) return "Enter the bank name."
  if (!account.trim()) return "Enter the account number."
  if (!/^\d{4,17}$/.test(account.replace(/\s/g, ""))) return "The account number must be 4 to 17 digits."
  if (branch.trim() && !/^\d{6}$/.test(branch.replace(/\s/g, ""))) return "The branch code must be 6 digits."
  return null
}

export interface ChangeField { key: string; label: string; oldValue: string | null; newValue: string | null }
export interface EmployeeChange {
  id: string; employeeId: string; employeeName: string; changeType: "COMPENSATION" | "BANK_DETAILS"; fields: ChangeField[]
  effectiveFrom: string; reason: string; status: string
  requestedBy: string | null; requestedAt: string; decidedBy: string | null; decidedAt: string | null; decisionNote: string | null
  selfApproved: boolean; appliedAt: string | null
}

export const CHANGE_STATUS: Record<string, string> = {
  PENDING: "Waiting for a decision",
  APPROVED: "Approved, waiting for its date",
  APPLIED: "Applied",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
}

export const TYPE_TEXT: Record<string, string> = { COMPENSATION: "Pay change", BANK_DETAILS: "Bank details change" }

/** Why a person cannot decide this change themselves, or null when they can (the server has the final say). */
export function cannotDecide(change: EmployeeChange, me: string | undefined, allowSelf: boolean): string | null {
  if (change.status !== "PENDING") return "This change has already been decided."
  if (me && change.requestedBy === me && !allowSelf) return "You asked for this change, so someone else has to decide it."
  return null
}

export const AUDIT_TEXT: Record<string, string> = {
  CREATED: "Registered",
  UPDATED: "Details edited",
  TERMINATED: "Terminated",
  MANAGER_CHANGED: "Manager changed",
  LOGIN_LINKED: "Login linked",
  LOGIN_UNLINKED: "Login unlinked",
  CHANGE_REQUESTED: "Pay or bank change requested",
  CHANGE_APPROVED: "Change approved",
  CHANGE_APPROVED_OWN: "Change approved by the person who asked",
  CHANGE_REJECTED: "Change rejected",
  CHANGE_CANCELLED: "Change cancelled",
  CHANGE_APPLIED: "Change took effect",
  VIEWED_PERSONAL_DATA: "Personal data viewed",
  VIEWED_PAY: "Pay viewed",
}
export const auditText = (action: string) => AUDIT_TEXT[action] ?? action
