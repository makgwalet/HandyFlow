// Pure helpers for showing package usage. The server decides what is refused; this only describes it.

export interface Meter { key: string; label: string; used: number; limit: number | null; over: boolean }
export interface PackageUsage {
  assigned: boolean; enforcement: "OFF" | "MONITOR" | "ENFORCE"
  pkg: { code: string; name: string; monthlyPrice: number | null; proposed: boolean } | null
  meters: Meter[]
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
