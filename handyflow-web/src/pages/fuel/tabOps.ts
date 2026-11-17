// Truck stop driver tabs: a bill of the truck stop's services at their Bookings price. Display and checks only; the server prices lines and enforces every rule.
import type { InvoiceState } from "./stayOps"

export type TabStatus = "OPEN" | "SETTLED" | "VOID"
export interface TabLine { id: string; offeringId: string; description: string; quantity: number; unitPrice: number; amount: number }
export interface Tab {
  id: string; vehicleRegistration: string; driverName: string | null; accountId: string | null; status: TabStatus; paymentMethod: string | null
  openedAt: string; settledAt: string | null; voidReason: string | null; invoiceState: InvoiceState | null; invoiceNumber: string | null; invoiceError: string | null
  lines: TabLine[]; total: number
}
export interface TabOffering { id: string; serviceName: string; active: boolean; price: number | null }

export const TAB_STATUS_LABEL: Record<TabStatus, string> = { OPEN: "Open", SETTLED: "Settled", VOID: "Void" }
export const TILL_METHODS: { id: string; label: string }[] = [{ id: "CASH", label: "Cash" }, { id: "CARD", label: "Card" }, { id: "OTHER", label: "Other" }]

/** Other tests answer every URL with generic data; keep only rows that look like tabs. */
export function onlyTabs(raw: unknown): Tab[] {
  if (!Array.isArray(raw)) return []
  return raw.filter((t: any) => t && typeof t.vehicleRegistration === "string" && Array.isArray(t.lines)) as Tab[]
}

/** Only services that can be sold: switched on and with a Bookings price. */
export function sellable(raw: unknown): TabOffering[] {
  if (!Array.isArray(raw)) return []
  return (raw as any[]).filter(o => o && typeof o.serviceName === "string" && o.active && typeof o.price === "number" && o.price > 0)
    .map(o => ({ id: o.id, serviceName: o.serviceName, active: true, price: o.price }))
}

export function validateQuantity(q: string): string | null {
  const n = Number(q)
  if (q.trim() === "" || !Number.isFinite(n) || n <= 0) return "Enter a quantity above 0."
  if (n > 999) return "That quantity is too large."
  return null
}

export function validateNewTab(registration: string): string | null {
  return registration.trim() === "" ? "Give the vehicle registration." : null
}

/** Which buttons an open tab offers. A tab on an account is charged to it; one without is settled at the till. Nothing can be done with no lines. */
export function tabActions(t: Tab): ("settle" | "charge" | "void" | "addLines")[] {
  if (t.status !== "OPEN") return []
  const out: ("settle" | "charge" | "void" | "addLines")[] = ["addLines", "void"]
  if (t.lines.length > 0) out.unshift(t.accountId ? "charge" : "settle")
  return out
}

export function tabInvoiceText(t: Tab): string | null {
  if (t.status !== "SETTLED" || !t.accountId) return null
  switch (t.invoiceState) {
    case "INVOICED": return `Draft invoice ${t.invoiceNumber ?? ""}`.trim()
    case "CLAIMED": return "Invoice being created"
    case "PENDING": return "Invoice not raised yet"
    case "FAILED": return `Invoice failed: ${t.invoiceError ?? "unknown error"}`
    case "LINK_FAILED": return `Invoice ${t.invoiceNumber ?? ""} was created but not linked here. Do not raise it again.`.replace("  ", " ")
    default: return "Invoice not raised yet"
  }
}

export const canRaiseTabInvoice = (t: Tab): boolean => t.status === "SETTLED" && !!t.accountId && (t.invoiceState === "PENDING" || t.invoiceState === "FAILED")

export function paidText(t: Tab, accountName?: string): string {
  if (t.status === "VOID") return `Void: ${t.voidReason ?? ""}`.trim()
  if (t.status === "OPEN") return t.accountId ? `Will be charged to ${accountName ?? "the account"}` : "Not paid yet"
  if (t.paymentMethod === "ACCOUNT") return `Charged to ${accountName ?? "the account"}`
  return `Paid by ${(TILL_METHODS.find(m => m.id === t.paymentMethod)?.label ?? t.paymentMethod ?? "").toLowerCase()}`
}
