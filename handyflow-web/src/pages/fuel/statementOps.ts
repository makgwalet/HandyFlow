// Account statements: what Fuel charged to a customer account in a period (deliveries, truck stop stays and tabs), and where the invoices stand. Read only; line amounts are before VAT.
export interface StatementLine { date: string; kind: "DELIVERY" | "STAY" | "TAB"; sourceId: string; vehicleRegistration: string; description: string; amount: number; invoiceState: string | null; invoiceNumber: string | null; invoiceId?: string | null }
export interface InvoiceStanding { invoiceId: string; invoiceNumber: string | null; status: string; issuedAt: string | null; total: number; paid: number; credited: number; outstanding: number }
export interface Statement {
  accountId: string; from: string; to: string; lines: StatementLine[]; total: number; invoiced: number; notInvoiced: number
  division?: string; invoices?: InvoiceStanding[]; invoicedWithVat?: number; paid?: number; credited?: number; outstanding?: number; settlementKnown?: boolean
}

export const DIVISION_CHOICES: { id: string; label: string }[] = [
  { id: "ALL", label: "All" }, { id: "DISTRIBUTION", label: "Fuel deliveries" }, { id: "TRUCK_STOP", label: "Truck stop" },
]

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

export const KIND_LABEL: Record<string, string> = { DELIVERY: "Fuel delivery", STAY: "Overnight stay", TAB: "Driver tab" }

export const STATUS_LABEL: Record<string, string> = { DRAFT: "Draft", ISSUED: "Issued", PARTIALLY_PAID: "Part paid", PAID: "Paid", OVERPAID: "Overpaid", OVERDUE: "Overdue", CANCELLED: "Cancelled" }

/** True when the statement carries what Invoicing says about the invoices. */
export function hasSettlement(s: Statement): boolean { return s.settlementKnown === true && (s.invoices?.length ?? 0) > 0 }

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
  if (hasSettlement(s)) {
    rows.push("")
    rows.push(["Invoice", "Status", "Total (with VAT)", "Paid", "Credited", "Still owed"].join(","))
    for (const i of s.invoices ?? []) rows.push([cell(i.invoiceNumber ?? ""), cell(STATUS_LABEL[i.status] ?? i.status), i.total.toFixed(2), i.paid.toFixed(2), i.credited.toFixed(2), i.outstanding.toFixed(2)].join(","))
    rows.push(["Total", "", (s.invoicedWithVat ?? 0).toFixed(2), (s.paid ?? 0).toFixed(2), (s.credited ?? 0).toFixed(2), (s.outstanding ?? 0).toFixed(2)].join(","))
  }
  return rows.join("\r\n") + "\r\n"
}
