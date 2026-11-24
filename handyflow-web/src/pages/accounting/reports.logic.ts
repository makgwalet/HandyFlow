export type ReportKind = "profit-and-loss" | "balance-sheet" | "trial-balance"

/** The Balance Sheet and Trial Balance are positions as at a date; only the Profit and Loss covers a period. */
export function isAsAt(kind: ReportKind): boolean {
  return kind !== "profit-and-loss"
}

export function fromLabel(kind: ReportKind): string {
  return isAsAt(kind) ? "Financial year starts" : "From"
}

export function toLabel(kind: ReportKind): string {
  return isAsAt(kind) ? "As at" : "To"
}

export function reportHint(kind: ReportKind): string | null {
  return isAsAt(kind)
    ? "Shows everything posted up to the 'as at' date. Profit from earlier years is shown in Retained Earnings; profit from the financial year start is Current Year Earnings."
    : null
}
