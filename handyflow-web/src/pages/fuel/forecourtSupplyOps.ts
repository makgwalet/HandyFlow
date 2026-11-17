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
    else if (f.fromOtherDepots > 0 || f.fromSuppliers > 0) out.push(`${f.name}: ${Math.round(f.totalIn - f.fromSupplierDepot).toLocaleString("en-ZA")} L of ${Math.round(f.totalIn).toLocaleString("en-ZA")} L did not come from its supplying depot (${f.supplierName}).`)
  }
  return out
}

// Splitting a forecourt's supply across several supplying depots.
export type SplitLine = { supplyingDepotId: string; percentage: string }

export function splitTotal(lines: SplitLine[]): number {
  return Math.round(lines.reduce((s, l) => s + (Number(l.percentage) || 0), 0) * 100) / 100
}

/** Null when the lines can be saved. No lines is fine: it removes the split. */
export function splitProblem(lines: SplitLine[]): string | null {
  if (lines.length === 0) return null
  if (lines.some(l => !l.supplyingDepotId)) return "Choose a depot on every line."
  if (new Set(lines.map(l => l.supplyingDepotId)).size !== lines.length) return "Each depot can appear only once."
  if (lines.some(l => !(Number(l.percentage) > 0) || Number(l.percentage) > 100)) return "Each percentage must be above 0 and at most 100."
  const t = splitTotal(lines)
  return t === 100 ? null : `The percentages must total 100. They total ${t}.`
}

// Matching a forecourt's receipts to the dispatches that supplied it.
export type FcLinkedReceipt = { matchId: string; receiptId: string; receivedAt: string; litres: number; deliveryNote: string | null }
export type FcMatched = { dispatchId: string; dispatchedAt: string; fromTank: string; fuelType: string; litresDispatched: number; litresReceived: number; difference: number; receipts: FcLinkedReceipt[] }
export type FcOpenDispatch = { dispatchId: string; dispatchedAt: string; fromTank: string; fuelType: string; litres: number }
export type FcOpenReceipt = { receiptId: string; receivedAt: string; tank: string; fuelType: string; litres: number; deliveryNote: string | null }
export type FcMatching = { depotId: string; name: string; matched: FcMatched[]; openDispatches: FcOpenDispatch[]; openReceipts: FcOpenReceipt[]; dispatchedLitres: number; receivedLitres: number }
export type FcMatchingReport = { from: string; to: string; forecourts: FcMatching[] }

export function isFcMatching(x: any): x is FcMatchingReport { return !!x && Array.isArray(x.forecourts) && x.forecourts.every((f: any) => Array.isArray(f.matched) && Array.isArray(f.openDispatches) && Array.isArray(f.openReceipts)) }

/** Open receipts that could be linked to this dispatch: same fuel type. The server checks the forecourt and the rest. */
export function receiptChoices(f: FcMatching, d: FcOpenDispatch): FcOpenReceipt[] {
  return f.openReceipts.filter(r => r.fuelType === d.fuelType)
}

/** Plain wording for a linked dispatch: nothing is judged, the gap is just stated. */
export function differenceText(m: FcMatched): string {
  const d = Math.round(m.difference * 100) / 100
  if (d === 0) return "Received in full"
  return d > 0 ? `${d.toLocaleString("en-ZA")} L less received than dispatched` : `${(-d).toLocaleString("en-ZA")} L more received than dispatched`
}
