// Sales by division: types and the few calculations the screen needs. The server decides which divisions and money a reader may see.
export interface DivisionRow {
  division: string; label: string; count: number; litres: number; revenue: number | null; dailyRevenue: number[] | null
  previousCount: number; previousLitres: number; previousRevenue: number | null
}
export interface DivisionSales {
  from: string; to: string; previousFrom: string; previousTo: string; moneyShown: boolean; divisions: DivisionRow[]; total: DivisionRow
}

/** What one count means in each division, singular and plural. */
const UNIT: Record<string, [string, string]> = {
  DISTRIBUTION: ["load", "loads"], RETAIL_FORECOURT: ["shift", "shifts"], WHOLESALE_SUPPLY: ["dispatch", "dispatches"], TRUCK_STOP: ["stay", "stays"], TOTAL: ["sale", "sales"],
}
export const unitFor = (division: string, n: number) => (UNIT[division] ?? ["sale", "sales"])[n === 1 ? 0 : 1]

/** Each division's share of revenue, 0-100, summing to about 100. Null without money or with nothing sold. */
export function shares(rows: DivisionRow[]): Record<string, number> | null {
  if (rows.some(r => r.revenue == null)) return null
  const total = rows.reduce((a, r) => a + (r.revenue ?? 0), 0)
  if (total <= 0) return null
  return Object.fromEntries(rows.map(r => [r.division, ((r.revenue ?? 0) / total) * 100]))
}

/** Plain-words definition under each division so nobody reads the figures as something else. */
export const DEFINITIONS: Record<string, string> = {
  DISTRIBUTION: "Delivered loads, at the amount invoiced for them. Reversed loads are left out.",
  RETAIL_FORECOURT: "Closed shifts, at the sales value on the pump readings, dated as the forecourt reports are.",
  WHOLESALE_SUPPLY: "Dispatches billed to a customer. Internal issues and transfers are not sales.",
  TRUCK_STOP: "Stays checked out in the period, at the nightly rate for the nights stayed, before VAT. No litres.",
}
