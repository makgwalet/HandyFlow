// Cost-to-serve and fuel settings helpers. Mirrors the server's limits; the server stays the authority.

export interface FuelSettings {
  driverCostPerHour: number; vehicleCostPerKm: number; fixedCostPerDelivery: number
  overheadPctOfRevenue: number; defaultTripHours: number; poOverTolerancePct: number; ratesConfigured: boolean
}
export interface SettingsForm {
  driverCostPerHour: string; vehicleCostPerKm: string; fixedCostPerDelivery: string
  overheadPctOfRevenue: string; defaultTripHours: string; poOverTolerancePct: string
}
export interface CtsRow {
  customerId: string; customerName: string; deliveries: number; litres: number; revenue: number; margin: number
  driverCost: number; vehicleCost: number; fixedCost: number; overheadCost: number; netMargin: number; netPerLitre: number | null
  deliveriesWithoutDistance: number; deliveriesWithEstimatedHours: number
}
export interface CostToServe {
  from: string; to: string; ratesConfigured: boolean; deliveries: number; deliveriesWithoutCost: number
  deliveriesWithoutDistance: number; deliveriesWithEstimatedHours: number
  revenue: number; margin: number; driverCost: number; vehicleCost: number; fixedCost: number; overheadCost: number; netMargin: number
  customers: CtsRow[]
}

export const toForm = (s: FuelSettings): SettingsForm => ({
  driverCostPerHour: String(s.driverCostPerHour), vehicleCostPerKm: String(s.vehicleCostPerKm), fixedCostPerDelivery: String(s.fixedCostPerDelivery),
  overheadPctOfRevenue: String(s.overheadPctOfRevenue), defaultTripHours: String(s.defaultTripHours), poOverTolerancePct: String(s.poOverTolerancePct),
})

const num = (v: string): number => (v.trim() === "" ? NaN : Number(v))

/** Null when fine. A blank rate is read as zero (not charged), so only filled values are range-checked. */
export function validateSettings(f: SettingsForm): string | null {
  for (const [label, v] of [["Driver cost per hour", f.driverCostPerHour], ["Vehicle cost per km", f.vehicleCostPerKm], ["Fixed cost per delivery", f.fixedCostPerDelivery]] as const) {
    if (v.trim() !== "" && (Number.isNaN(num(v)) || num(v) < 0)) return `${label} must be zero or more.`
  }
  if (f.overheadPctOfRevenue.trim() !== "" && (Number.isNaN(num(f.overheadPctOfRevenue)) || num(f.overheadPctOfRevenue) < 0 || num(f.overheadPctOfRevenue) > 100)) return "Overhead must be between 0% and 100%."
  const h = num(f.defaultTripHours); if (Number.isNaN(h) || h <= 0 || h > 24) return "Typical trip hours must be more than 0 and at most 24."
  const t = num(f.poOverTolerancePct); if (Number.isNaN(t) || t < 0 || t > 10) return "Delivery tolerance must be between 0% and 10%."
  return null
}

export function toRequest(f: SettingsForm) {
  const n = (v: string) => (v.trim() === "" ? 0 : Number(v))
  return { driverCostPerHour: n(f.driverCostPerHour), vehicleCostPerKm: n(f.vehicleCostPerKm), fixedCostPerDelivery: n(f.fixedCostPerDelivery),
    overheadPctOfRevenue: n(f.overheadPctOfRevenue), defaultTripHours: Number(f.defaultTripHours), poOverTolerancePct: Number(f.poOverTolerancePct) }
}

/** Short caveats for a customer row, so an estimate is never mistaken for a measurement. */
export function rowCaveats(r: Pick<CtsRow, "deliveriesWithoutDistance" | "deliveriesWithEstimatedHours" | "deliveries">): string[] {
  const out: string[] = []
  if (r.deliveriesWithoutDistance > 0) out.push(`${r.deliveriesWithoutDistance} of ${r.deliveries} with no site distance (no vehicle cost counted)`)
  if (r.deliveriesWithEstimatedHours > 0) out.push(`${r.deliveriesWithEstimatedHours} of ${r.deliveries} with estimated driver hours`)
  return out
}

/** A customer that looks profitable on margin but is not once service costs are counted. */
export const losesMoneyToServe = (r: Pick<CtsRow, "margin" | "netMargin">) => r.margin > 0 && r.netMargin < 0
