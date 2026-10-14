// Pure helpers for the disputes panel (finance F16).

export const REASON_LABEL: Record<string, string> = {
  AMOUNT: "Amount is wrong",
  NOT_OWED: "Debt is not owed",
  ALREADY_PAID: "Already paid",
  SERVICE_QUALITY: "Goods or service problem",
  WRONG_DEBTOR: "Wrong debtor",
  OTHER: "Other",
}

export const RESOLUTION_LABEL: Record<string, string> = {
  UPHELD: "Upheld (debtor is right)",
  PARTLY_UPHELD: "Partly upheld",
  REJECTED: "Rejected (debt stands)",
  WITHDRAWN: "Withdrawn by debtor",
}

export const STATUS_LABEL: Record<string, string> = { OPEN: "Open", UNDER_REVIEW: "In review", RESOLVED: "Resolved" }

export const MIN_TEXT = 10

export const needsAmount = (resolution: string) => resolution === "UPHELD" || resolution === "PARTLY_UPHELD"

/** Why the open form cannot be sent yet, or null. */
export function openFormProblem(f: { reasonType: string; description: string; amountDisputed: string }): string | null {
  if (!f.reasonType) return "Choose a reason."
  if (f.description.trim().length < MIN_TEXT) return `Describe the dispute (at least ${MIN_TEXT} characters).`
  if (f.amountDisputed !== "" && !(Number(f.amountDisputed) > 0)) return "The disputed amount must be more than zero."
  return null
}

/** Why the resolve form cannot be sent yet, or null. The server checks again. */
export function resolveFormProblem(f: { resolution: string; note: string; amountConceded: string }, disputed: number | null): string | null {
  if (!f.resolution) return "Choose the outcome."
  if (f.note.trim().length < MIN_TEXT) return `Explain the outcome (at least ${MIN_TEXT} characters).`
  const amt = Number(f.amountConceded)
  if (needsAmount(f.resolution)) {
    if (!(amt > 0)) return "Enter the amount conceded."
    if (disputed != null && amt > disputed) return "More than the amount disputed."
  } else if (f.amountConceded !== "" && amt !== 0) {
    return "No amount is conceded for this outcome."
  }
  return null
}

/** Whole days past the response date; 0 when not late. */
export function daysLate(responseDue: string, today: Date = new Date()): number {
  const due = new Date(responseDue + "T00:00:00")
  const t = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return Math.max(0, Math.round((t.getTime() - due.getTime()) / 86400000))
}

export const isOpenDispute = (status: string) => status !== "RESOLVED"
