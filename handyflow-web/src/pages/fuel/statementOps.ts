// Truck stop account statements: what was charged to a customer account in a period. Read only; amounts are before VAT.
export interface StatementLine { date: string; kind: "STAY" | "TAB"; sourceId: string; vehicleRegistration: string; description: string; amount: number; invoiceState: string | null; invoiceNumber: string | null }
export interface Statement { accountId: string; from: string; to: string; lines: StatementLine[]; total: number; invoiced: number; notInvoiced: number }

/** Other tests answer every URL with generic data; accept only a real statement. */
export function asStatement(raw: any): Statement | null {
  if (!raw || !Array.isArray(raw.lines) || typeof raw.total !== "number") return null
  return raw as Statement
}

/** The first and last day of last month's worth of days: the 30 days up to and including today. */
export function defaultRange(today: string): { from: string; to: string } {
  const d = new Date(today + "T12:00:00"); d.setDate(d.getDate() - 29)
  return { from: d.toLocaleDateString("en-CA"), to: today }
}

export function validateRange(from: string, to: string): string | null {
  if (!from || !to) return "Give the first and last day."
  if (to < from) return "The last day cannot be before the first day."
  const days = (new Date(to + "T12:00:00").getTime() - new Date(from + "T12:00:00").getTime()) / 86400000
  if (days >= 366) return "A statement can cover at most 366 days."
  return null
}

export const KIND_LABEL: Record<string, string> = { STAY: "Overnight stay", TAB: "Driver tab" }

export function invoiceLabel(l: { invoiceState: string | null; invoiceNumber: string | null }): string {
  switch (l.invoiceState) {
    case "INVOICED": return `Draft invoice ${l.invoiceNumber ?? ""}`.trim()
    case "CLAIMED": return "Invoice being created"
    case "FAILED": return "Invoice failed"
    case "LINK_FAILED": return `Invoice ${l.invoiceNumber ?? ""} not linked`.replace("  ", " ")
    default: return "Not invoiced yet"
  }
}

/** A text cell that a spreadsheet could read as a formula is prefixed so it is shown as text. */
function cell(v: string): string {
  const safe = /^[=+\-@\t\r]/.test(v) ? "'" + v : v
  return /[",\n\r]/.test(safe) ? '"' + safe.replace(/"/g, '""') + '"' : safe
}

export function toCsv(s: Statement): string {
  const rows = [["Date", "Type", "Vehicle", "Description", "Amount (before VAT)", "Invoice"].join(",")]
  for (const l of s.lines) rows.push([l.date, cell(KIND_LABEL[l.kind] ?? l.kind), cell(l.vehicleRegistration), cell(l.description), l.amount.toFixed(2), cell(invoiceLabel(l))].join(","))
  rows.push(["", "", "", "Total", s.total.toFixed(2), ""].join(","))
  rows.push(["", "", "", "Invoiced", s.invoiced.toFixed(2), ""].join(","))
  rows.push(["", "", "", "Not invoiced yet", s.notInvoiced.toFixed(2), ""].join(","))
  return rows.join("\r\n") + "\r\n"
}
