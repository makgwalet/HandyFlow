// Phase 8d: forecourt sales grouped by the depot that supplies each forecourt.
export type FcRow = { depotId: string | null; name: string; shifts: number; litresSold: number; salesValue: number | null }
export type FcSupplier = { supplierId: string | null; name: string; unassigned: boolean; forecourts: FcRow[]; litresSold: number; salesValue: number | null }
export type FcReport = { from: string; to: string; moneyShown: boolean; suppliers: FcSupplier[]; totalLitres: number; totalSales: number | null; readingsLeftOut: number }

/** The page also shows reports from other endpoints in tests and old servers; only render what has the expected shape. */
export function isFcReport(x: any): x is FcReport { return !!x && Array.isArray(x.suppliers) && typeof x.totalLitres === "number" }

export function supplyCaveat(r: FcReport): string | null {
  const parts: string[] = []
  if (r.suppliers.some(s => s.unassigned)) parts.push("Some forecourt sales have no supplying depot set. Set it under Stock > Depots.")
  if (r.readingsLeftOut > 0) parts.push(`${r.readingsLeftOut} meter reading(s) had no litres and were left out.`)
  return parts.length ? parts.join(" ") : null
}
