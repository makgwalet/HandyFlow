// Finance patch F13: small helpers for the month-end close and cash flow screens.

/** The last day of the month before {@code today}, as yyyy-mm-dd. */
export function lastMonthEnd(today = new Date()): string {
  const d = new Date(today.getFullYear(), today.getMonth(), 0)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
}

/** The first day of the month that {@code periodEnd} (yyyy-mm-dd) falls in. */
export function monthStart(periodEnd: string): string { return `${periodEnd.slice(0, 7)}-01` }

/** The twelve month ends before {@code today}, latest first, as { value: yyyy-mm-dd, label: "September 2026" }. */
export function recentMonthEnds(today = new Date(), count = 12): { value: string; label: string }[] {
  const out: { value: string; label: string }[] = []
  for (let i = 0; i < count; i++) {
    const d = new Date(today.getFullYear(), today.getMonth() - i, 0)
    out.push({
      value: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`,
      label: d.toLocaleDateString("en-ZA", { month: "long", year: "numeric" }),
    })
  }
  return out
}

export type ItemStatus = "DONE" | "BLOCKED" | "WARNING" | "WAITING"
export const STATUS_TEXT: Record<ItemStatus, string> = { DONE: "Done", BLOCKED: "Fix first", WARNING: "Check", WAITING: "To do" }
export const STATUS_COLOR: Record<ItemStatus, string> = { DONE: "#16a34a", BLOCKED: "#dc2626", WARNING: "#b45309", WAITING: "#6b7280" }

/** How many blocking items are still not done, for the summary line. */
export function remaining(items: { blocking: boolean; status: string }[]): number {
  return items.filter(i => i.blocking && i.status !== "DONE").length
}
