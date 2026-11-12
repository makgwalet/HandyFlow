// Phase 6c helpers: landed cost per litre. Display only; the server does the arithmetic and decides who sees money.
export interface LandedRoute {
  routeId: string; routeDate: string; label: string; tankerRegistration: string | null; deliveredStops: number; litres: number
  revenue: number; productCost: number | null; transportCost: number; landedCost: number | null
  productPerLitre: number | null; transportPerLitre: number | null; landedPerLitre: number | null; revenuePerLitre: number | null
  marginPerLitre: number | null; margin: number | null; complete: boolean; flags: string[]
}
export interface LandedRollup {
  key: string; label: string; routes: number; litres: number; revenue: number; productCost: number; transportCost: number
  landedCost: number; landedPerLitre: number | null; marginPerLitre: number | null; margin: number
}
export interface LandedCostReport {
  from: string; to: string; routesWithDeliveries: number; routesLeftOut: number
  routes: LandedRoute[]; total: LandedRollup; byTanker: LandedRollup[]; byDepot: LandedRollup[]
}

/** Rand per litre to cents ("R18.25/L"); "-" when the figure is not known. Never turns an unknown into zero. */
export const perLitre = (n: number | null | undefined): string => n == null ? "-" : `R${n.toFixed(2)}/L`

const FLAG_TEXT: Record<string, string> = {
  NO_PRODUCT_COST: "A delivery has no cost at sale",
  NO_TRANSPORT_COST: "No vehicle or driver cost recorded",
  PARTIAL_DELIVERY: "Some stops are not delivered yet",
  NO_LITRES: "No litres delivered",
}
export const flagText = (f: string): string => FLAG_TEXT[f] ?? f

export function leftOutNote(r: LandedCostReport): string | null {
  if (r.routesLeftOut <= 0) return null
  const n = r.routesLeftOut
  return `${n} of ${r.routesWithDeliveries} ${r.routesWithDeliveries === 1 ? "route is" : "routes are"} left out of the totals because ${n === 1 ? "its" : "their"} cost is not fully recorded. Fix ${n === 1 ? "it" : "them"} under Cost to serve, then this page fills in.`
}
