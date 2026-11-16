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

// Forecourt supply reconciliation: where each forecourt's fuel came from, against litres sold. Litres only.
export type FcInbound = { depotId: string; name: string; supplierId: string | null; supplierName: string | null; fromSupplierDepot: number; fromOtherDepots: number; fromSuppliers: number; totalIn: number; supplierDepotSharePct: number | null; litresSold: number }
export type FcRecon = { from: string; to: string; forecourts: FcInbound[]; totalIn: number; totalSold: number }

export function isFcRecon(x: any): x is FcRecon { return !!x && Array.isArray(x.forecourts) && typeof x.totalIn === "number" && typeof x.totalSold === "number" }

/** One plain sentence per forecourt that needs a look; empty when supply follows the supplier on record. */
export function reconNotes(r: FcRecon): string[] {
  const out: string[] = []
  for (const f of r.forecourts) {
    if (f.totalIn === 0) continue
    if (!f.supplierId) out.push(`${f.name} has no supplying depot set, so none of its fuel can be matched to a supplier.`)
    else if (f.fromOtherDepots > 0 || f.fromSuppliers > 0) out.push(`${f.name}: ${Math.round(f.totalIn - f.fromSupplierDepot).toLocaleString("en-ZA")} L of ${Math.round(f.totalIn).toLocaleString("en-ZA")} L did not come from ${f.supplierName}.`)
  }
  return out
}
