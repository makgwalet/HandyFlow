// Phase 6a helpers: the group report across legal entities. Display only; the server decides what money a reader may see.
export interface GroupRow {
  legalEntityId: string | null; name: string; unassigned: boolean
  deliveries: number; litres: number; revenue: number | null; margin: number | null; deliveriesWithoutCost: number
  previousDeliveries: number; previousLitres: number; previousRevenue: number | null; previousMargin: number | null
  stockLitres: number; stockValue: number | null; tanksWithoutCost: number; openVariances: number
}
export interface GroupReport {
  from: string; to: string; previousFrom: string; previousTo: string; moneyShown: boolean
  entities: GroupRow[]; total: GroupRow; deliveriesLeftOut: number
}

/** "+12.5%" / "-8.0%" against the previous period; "new" when there was nothing before; null when neither period has anything. */
export function changeText(now: number | null | undefined, before: number | null | undefined): string | null {
  if (now == null || before == null) return null
  if (before === 0) return now === 0 ? null : "new"
  const pct = ((now - before) / Math.abs(before)) * 100
  return `${pct >= 0 ? "+" : "-"}${Math.abs(pct).toFixed(1)}%`
}

/** What the figures leave out, in words, so nobody reads a floor as a total. */
export function caveats(r: GroupReport): string[] {
  const out: string[] = []
  if (r.total.deliveriesWithoutCost > 0) out.push(`${r.total.deliveriesWithoutCost} ${r.total.deliveriesWithoutCost === 1 ? "delivery has" : "deliveries have"} no cost at sale, so ${r.total.deliveriesWithoutCost === 1 ? "it is" : "they are"} in revenue and litres but not in margin.`)
  if (r.total.tanksWithoutCost > 0) out.push(`${r.total.tanksWithoutCost} ${r.total.tanksWithoutCost === 1 ? "tank holds" : "tanks hold"} fuel with no known cost, so stock value is a floor.`)
  if (r.deliveriesLeftOut > 0) out.push(`${r.deliveriesLeftOut} reversed ${r.deliveriesLeftOut === 1 ? "delivery is" : "deliveries are"} left out.`)
  if (r.entities.some(e => e.unassigned)) out.push("Some tanks are in a depot with no legal entity. Assign the depot to an entity under Legal entities to move them out of \"Not assigned\".")
  return out
}
