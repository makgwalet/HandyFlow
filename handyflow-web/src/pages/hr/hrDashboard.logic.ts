// What the HR dashboard may call payroll. A draft or waiting-for-approval run is a projection, not an actual.

export interface DashRun {
  payRunNumber: string; status: string; totalNet?: number | string | null; payDate?: string | null
  totalGross?: number | string | null; totalPaye?: number | string | null; totalUif?: number | string | null; employeeCount?: number | null
}

/** Runs whose figures have been approved or issued: the only ones that count as actual payroll. */
export const ACTUAL_STATUSES = ["APPROVED", "FINALISED", "COMPLETED"]
/** Runs still being worked on. */
export const PROJECTED_STATUSES = ["DRAFT", "PROCESSING", "PREPARED"]

const byPayDateDesc = (a: DashRun, b: DashRun) => String(b.payDate ?? "").localeCompare(String(a.payDate ?? ""))

/** The newest run with approved figures, or undefined. */
export function lastActualRun(runs: DashRun[]): DashRun | undefined {
  return runs.filter(r => ACTUAL_STATUSES.includes(r.status)).sort(byPayDateDesc)[0]
}

/** The newest run still being worked on, or undefined. */
export function projectedRun(runs: DashRun[]): DashRun | undefined {
  return runs.filter(r => PROJECTED_STATUSES.includes(r.status)).sort(byPayDateDesc)[0]
}

/** Text for the payroll card, so a draft is never shown as money paid out. */
export function payrollCard(runs: DashRun[]): { label: string; run?: DashRun; note: string | null } {
  const actual = lastActualRun(runs)
  const projected = projectedRun(runs)
  if (actual) {
    const newer = projected && String(projected.payDate ?? "") > String(actual.payDate ?? "")
    return { label: "Last approved payroll", run: actual, note: newer ? `${projected!.payRunNumber} is not approved yet` : null }
  }
  if (projected) return { label: "Payroll (not approved yet)", run: projected, note: "Projected: this run is still " + (projected.status === "PREPARED" ? "waiting for approval" : "a draft") }
  return { label: "Payroll", run: undefined, note: null }
}
