// Pure helpers for the pay run workflow: which steps a person sees, and how the history reads.
// The server decides what is allowed; this only avoids showing a button whose click would be refused.

export type RunStatus = "DRAFT" | "PROCESSING" | "PREPARED" | "APPROVED" | "FINALISED" | "COMPLETED" | "CANCELLED"

export const STATUS_TEXT: Record<string, string> = {
  DRAFT: "Draft",
  PROCESSING: "Calculating",
  PREPARED: "Waiting for approval",
  APPROVED: "Approved",
  FINALISED: "Finalised",
  COMPLETED: "Issued before approval existed",
  CANCELLED: "Cancelled",
}

export const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  DRAFT:      { color: "var(--hf-warning-text)",        bg: "var(--hf-warning-soft)" },
  PROCESSING: { color: "var(--hf-info-text)",           bg: "var(--hf-info-soft)" },
  PREPARED:   { color: "var(--hf-info-text)",           bg: "var(--hf-info-soft)" },
  APPROVED:   { color: "var(--hf-violet-text)",         bg: "var(--hf-violet-soft)" },
  FINALISED:  { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  COMPLETED:  { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  CANCELLED:  { color: "var(--hf-text-faint)",          bg: "var(--hf-surface-muted)" },
}

export const hasTotals = (status: string) => !["DRAFT", "PROCESSING", "CANCELLED"].includes(status)
/** Employees can see the payslips of these runs. */
export const isIssued = (status: string) => status === "FINALISED" || status === "COMPLETED"

export interface Rights { prepare: boolean; approve: boolean; finalise: boolean; configure: boolean; read: boolean }

/** `legacyOpen`: the old permissions (PAYROLL_RUN, USER_UPDATE) still prepare until the date the server reports. */
export function rightsFrom(perms: readonly string[], legacyOpen: boolean): Rights {
  const has = (...p: string[]) => p.some(x => perms.includes(x))
  const prepare = has("PAYROLL_PREPARE") || (legacyOpen && has("PAYROLL_RUN", "USER_UPDATE"))
  const approve = has("PAYROLL_APPROVE")
  const finalise = has("PAYROLL_FINALISE")
  return {
    prepare, approve, finalise,
    configure: has("PAYROLL_CONFIGURATION_ADMIN"),
    read: prepare || approve || finalise || has("PAYROLL_READ") || (legacyOpen && has("PAYROLL_RUN", "USER_UPDATE")),
  }
}

export type StepKey = "calculate" | "approve" | "sendBack" | "finalise"
export interface Step { key: StepKey; label: string; enabled: boolean; reason: string | null; needsNote: boolean }

const OWN_WORK = "You calculated this run, so another person has to do this step."

export function stepsFor(run: { status: string; preparedBy: string | null }, rights: Rights, userId: string | undefined, allowSelf: boolean): Step[] {
  const own = !!userId && run.preparedBy === userId && !allowSelf
  const out: Step[] = []
  if (run.status === "DRAFT" && rights.prepare) out.push({ key: "calculate", label: "Calculate", enabled: true, reason: null, needsNote: false })
  if (run.status === "PREPARED" && rights.prepare) out.push({ key: "calculate", label: "Calculate again", enabled: true, reason: null, needsNote: false })
  if ((run.status === "PREPARED" || run.status === "COMPLETED") && rights.approve)
    out.push({ key: "approve", label: "Approve", enabled: !own, reason: own ? OWN_WORK : null, needsNote: false })
  if ((run.status === "PREPARED" || run.status === "APPROVED") && rights.approve)
    out.push({ key: "sendBack", label: "Send back", enabled: true, reason: null, needsNote: true })
  if (run.status === "APPROVED" && rights.finalise)
    out.push({ key: "finalise", label: "Finalise and lock", enabled: !own, reason: own ? OWN_WORK : null, needsNote: false })
  return out
}

/** One line saying what the run is waiting for, whoever is looking at it. */
export function waitingFor(status: string): string | null {
  switch (status) {
    case "DRAFT": return "Waiting to be calculated."
    case "PREPARED": return "Waiting for an approver. Employees cannot see these payslips yet."
    case "APPROVED": return "Approved. Waiting to be finalised. Employees cannot see these payslips yet."
    case "FINALISED": return "Finalised and locked. Employees can see their payslips."
    case "COMPLETED": return "Issued before approval existed. It can still be approved and finalised, but not calculated again."
    default: return null
  }
}

export interface RunEvent { id: string; action: string; fromStatus: string; toStatus: string; actorId: string | null; note: string | null; at: string }

const EVENT_TEXT: Record<string, string> = {
  CALCULATED: "Calculated",
  APPROVED: "Approved",
  APPROVED_OWN_RUN: "Approved by the person who calculated it",
  SENT_BACK: "Sent back",
  FINALISED: "Finalised and locked",
  FINALISED_OWN_RUN: "Finalised by the person who calculated it",
}

export function eventText(e: Pick<RunEvent, "action" | "actorId">, userId: string | undefined): string {
  const what = EVENT_TEXT[e.action] ?? e.action
  const who = e.actorId == null ? "" : e.actorId === userId ? " by you" : ""
  return what + who
}

/** Sending back needs a reason; every note is at most 500 characters. */
export function noteProblem(step: Pick<Step, "needsNote">, note: string): string {
  if (step.needsNote && !note.trim()) return "Give a reason for sending the run back."
  if (note.length > 500) return "The note can be at most 500 characters."
  return ""
}

export interface Review { blocking: boolean; errors: number; warnings: number; comparedWith: string | null; items: { severity: string; code: string; employee: string | null; message: string }[] }

export function reviewSummary(r: Pick<Review, "errors" | "warnings" | "comparedWith">): string {
  const parts: string[] = []
  parts.push(r.errors === 0 ? "No errors" : `${r.errors} ${r.errors === 1 ? "error" : "errors"} to fix before approval`)
  parts.push(r.warnings === 0 ? "no warnings" : `${r.warnings} ${r.warnings === 1 ? "warning" : "warnings"} to look at`)
  const base = parts.join(", ") + "."
  return r.comparedWith ? `${base} Compared with ${r.comparedWith}.` : `${base} There is no earlier run to compare with.`
}
