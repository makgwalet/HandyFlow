// Pure helpers for showing package usage. The server decides what is refused; this only describes it.

export interface Meter { key: string; label: string; used: number; limit: number | null; over: boolean; extra?: number }
export interface UpgradeOption { code: string; name: string; monthlyPrice: number | null; maxWarehouses: number | null; maxClientAccounts: number | null; maxMonthlyTransactions: number | null; maxWebhooks: number | null }
export interface PackageUsage {
  assigned: boolean; enforcement: "OFF" | "MONITOR" | "ENFORCE"
  pkg: { code: string; name: string; monthlyPrice: number | null; proposed: boolean } | null
  meters: Meter[]
  periodStart?: string; periodEnd?: string
  state?: "ACTIVE" | "SCHEDULED" | "EXPIRED" | null; effectiveFrom?: string | null; expiresOn?: string | null; note?: string | null
  upgradeOptions?: UpgradeOption[]
}

export const limitText = (limit: number | null) => (limit == null ? "no limit" : `of ${limit.toLocaleString("en-ZA")}`)

/** How full a meter is, 0 to 100; 0 when there is no limit. */
export function percentUsed(m: Pick<Meter, "used" | "limit">): number {
  if (m.limit == null) return 0
  if (m.limit <= 0) return m.used > 0 ? 100 : 0
  return Math.min(100, Math.round((m.used / m.limit) * 100))
}

export const priceText = (price: number | null) => (price == null ? "Custom quotation" : `R${price.toLocaleString("en-ZA", { minimumFractionDigits: 0, maximumFractionDigits: 2 })} per month, excluding VAT`)

export function enforcementText(e: PackageUsage["enforcement"]): string {
  if (e === "ENFORCE") return "New records beyond a limit are refused."
  if (e === "MONITOR") return "Usage is measured and anything over a limit is flagged, but nothing is refused."
  return "Limits are recorded but not applied."
}

/** The billing month the transaction count belongs to, for display. */
export function periodText(u: Pick<PackageUsage, "periodStart" | "periodEnd">): string | null {
  return u.periodStart && u.periodEnd ? `Billing month ${u.periodStart} to ${u.periodEnd}` : null
}

/** What the dates on a tier mean for the tenant today. Null when there is nothing to say. */
export function windowText(u: Pick<PackageUsage, "state" | "effectiveFrom" | "expiresOn">): string | null {
  if (u.state === "SCHEDULED") return `This package starts on ${u.effectiveFrom}. Until then no limits apply.`
  if (u.state === "EXPIRED") return `This package ended on ${u.expiresOn}, so no limits apply. Ask HandyFlow to renew it.`
  if (u.expiresOn) return `Renews or ends on ${u.expiresOn}.`
  return null
}

/** The upgrade line for one option: its price and what it allows. */
export function upgradeText(o: UpgradeOption): string {
  const cap = (n: number | null, what: string) => (n == null ? `unlimited ${what}` : `${n.toLocaleString("en-ZA")} ${what}`)
  return `${o.name}: ${priceText(o.monthlyPrice)}. ${cap(o.maxWarehouses, "warehouses")}, ${cap(o.maxClientAccounts, "client accounts")}, ${cap(o.maxMonthlyTransactions, "transactions a month")}, ${cap(o.maxWebhooks, "webhooks")}.`
}
