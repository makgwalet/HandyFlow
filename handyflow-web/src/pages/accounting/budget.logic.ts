// Finance F21: small pure helpers for the budget grid and the cash forecast.
export const fmtR = (n: number | null | undefined) =>
  `${(n ?? 0) < 0 ? "-" : ""}R ${Math.abs(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export const monthLabels = (start: string): string[] => {
  const [y, m] = start.split("-").map(Number)
  return Array.from({ length: 12 }, (_, i) => {
    const d = new Date(Date.UTC(y, m - 1 + i, 1))
    return d.toLocaleDateString("en-ZA", { month: "short", year: "2-digit", timeZone: "UTC" })
  })
}

export interface GridRow { accountId: string; code: string; name: string; type: string; months: string[] }

/** Only non-empty, non-zero cells go to the server; blank means zero. */
export function gridToLines(rows: GridRow[]): { accountId: string; month: number; amount: number }[] {
  const out: { accountId: string; month: number; amount: number }[] = []
  for (const r of rows) r.months.forEach((v, month) => {
    const n = Number(String(v).replace(",", ".").trim())
    if (v !== "" && Number.isFinite(n) && n > 0) out.push({ accountId: r.accountId, month, amount: Math.round(n * 100) / 100 })
  })
  return out
}

export function gridProblem(rows: GridRow[]): string | null {
  for (const r of rows) for (const v of r.months) {
    if (v === "") continue
    const n = Number(String(v).replace(",", "."))
    if (!Number.isFinite(n)) return `${r.code} ${r.name}: "${v}" is not a number.`
    if (n < 0) return `${r.code} ${r.name}: amounts cannot be negative.`
  }
  return null
}

export const rowTotal = (r: GridRow) => r.months.reduce((s, v) => s + (Number(String(v).replace(",", ".")) || 0), 0)

export const varianceText = (v: number, percent: number | null, favourable: boolean) =>
  `${favourable ? "Better" : "Worse"} by ${fmtR(Math.abs(v))}${percent == null ? "" : ` (${Math.abs(percent)}%)`}`

export const STATUS_LABEL: Record<string, string> = { DRAFT: "Draft", ACTIVE: "Active", CLOSED: "Closed" }

export interface ForecastQuery { customerDelayDays: number; overdueReceiptInDays: number; overduePayInDays: number; collectPercent: number; lowBalance: string }
export const defaultQuery = (): ForecastQuery => ({ customerDelayDays: 0, overdueReceiptInDays: 14, overduePayInDays: 7, collectPercent: 100, lowBalance: "" })
export const forecastParams = (q: ForecastQuery) => {
  const p = new URLSearchParams({
    customerDelayDays: String(q.customerDelayDays), overdueReceiptInDays: String(q.overdueReceiptInDays),
    overduePayInDays: String(q.overduePayInDays), collectPercent: String(q.collectPercent),
  })
  if (q.lowBalance.trim() !== "" && Number(q.lowBalance) > 0) p.set("lowBalance", String(Number(q.lowBalance)))
  return p.toString()
}
export function itemProblem(date: string, amount: string, label: string): string | null {
  if (!label.trim()) return "Say what the item is."
  if (!date) return "Choose a date."
  const n = Number(amount.replace(",", "."))
  if (!(n > 0)) return "The amount must be more than zero."
  return null
}
