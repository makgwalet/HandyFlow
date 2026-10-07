// src/pages/security/hrLink.logic.ts
//
// Pure rules for the HR link on a guard and the "Refer to HR" step on a complaint. HR owns the employee record and the
// disciplinary case; Security stores only the ids and shows what HR says.
export interface HrLink { linked: boolean; employeeMissing: boolean; employeeId: string | null; employeeNumber: string | null; fullName: string | null; jobTitle: string | null; department: string | null; status: string | null }
export interface EmployeeOption { id: string; employeeNumber: string | null; fullName: string; jobTitle: string | null; status: string | null }
export interface HrReferral { disciplinaryId: string; employeeId: string | null; employeeName: string | null; referredAt: string; referredBy: string | null; outcome: string | null; hearingDate: string | null }

/** The server needs at least two characters before it searches. */
export const searchReady = (q: string) => q.trim().length >= 2

const OUTCOME_LABEL: Record<string, string> = { VERBAL_WARNING: "Verbal warning", WRITTEN_WARNING: "Written warning", FINAL_WRITTEN_WARNING: "Final written warning", DISMISSAL: "Dismissal" }
export const hrOutcomeLabel = (o: string | null) => (o ? OUTCOME_LABEL[o] ?? o.toLowerCase().replace(/_/g, " ") : "No outcome recorded yet")

export function linkLine(l: HrLink): string {
  if (!l.linked) return "Not linked to an HR employee record."
  if (l.employeeMissing) return "Linked, but the HR record can no longer be found. Unlink it or link the right record."
  return [l.fullName, l.employeeNumber, l.jobTitle].filter(Boolean).join(" · ")
}

/** Why the Refer to HR button is not offered on a substantiated complaint, or null when it is (or does not apply). */
export function referHint(s: { finding: string | null; status: string }, canRefer: boolean, referred: boolean): string | null {
  if (referred || canRefer || s.finding !== "SUBSTANTIATED" || s.status === "WITHDRAWN") return null
  return "To refer this to HR, link the guard to their HR employee record on the guard's profile first."
}
