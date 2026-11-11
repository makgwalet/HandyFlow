// Stage 4D helpers: what needs attention on the manager overview.
export interface Stock { fuelType: string; litresOnHand: number; litresReserved: number; litresAvailable: number; capacityLitres: number }
export interface Overview {
  today: string; stock: Stock[]; lowTanks: number
  quotes: number; confirmedOrders: number; partDeliveredOrders: number
  deliveredNotInvoicedCount: number; deliveredNotInvoicedValue: number; invoiceFailures: number
  customersOnCreditHold: number; complianceExpired: number; complianceExpiring: number
  loadsToday: number; loadsTodayUnallocated: number; purchaseOrdersAwaitingDelivery: number
}
export interface MarginRow { customerId: string; customerName: string; deliveries: number; litres: number; revenue: number; margin: number; marginPerLitre: number | null }
export interface Margin { from: string; to: string; revenue: number; margin: number; deliveriesWithoutCost: number; customers: MarginRow[] }

export interface Attention { key: string; text: string; section: string }

/** The things that need doing, worst first; empty when all is well. */
export function attention(o: Overview): Attention[] {
  const a: Attention[] = []
  if (o.complianceExpired > 0) a.push({ key: "expired", text: `${o.complianceExpired} compliance item${o.complianceExpired === 1 ? " has" : "s have"} expired: affected loads cannot be allocated.`, section: "compliance" })
  if (o.loadsTodayUnallocated > 0) a.push({ key: "unalloc", text: `${o.loadsTodayUnallocated} load${o.loadsTodayUnallocated === 1 ? "" : "s"} today ${o.loadsTodayUnallocated === 1 ? "has" : "have"} no tanker or driver.`, section: "planning" })
  if (o.invoiceFailures > 0) a.push({ key: "invfail", text: `${o.invoiceFailures} invoice${o.invoiceFailures === 1 ? "" : "s"} failed to create.`, section: "invoicing" })
  if (o.lowTanks > 0) a.push({ key: "low", text: `${o.lowTanks} tank${o.lowTanks === 1 ? " is" : "s are"} low.`, section: "tanks" })
  if (o.customersOnCreditHold > 0) a.push({ key: "hold", text: `${o.customersOnCreditHold} customer${o.customersOnCreditHold === 1 ? " is" : "s are"} on credit hold.`, section: "customers" })
  if (o.complianceExpiring > 0) a.push({ key: "expiring", text: `${o.complianceExpiring} compliance item${o.complianceExpiring === 1 ? " expires" : "s expire"} within 30 days.`, section: "compliance" })
  return a
}

export const pct = (part: number, whole: number) => (whole > 0 ? `${((part / whole) * 100).toFixed(1)}%` : "–")
