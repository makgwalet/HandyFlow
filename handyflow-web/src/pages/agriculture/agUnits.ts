// src/pages/agriculture/agUnits.ts
//
// Mirror of the backend's AgUnits (platform/.../agriculture/domain/rules/AgUnits.java). The server converts harvest
// quantities into the crop type's own unit before summing yield, and REJECTS a unit it cannot convert, so the screens apply
// the same rules to explain a problem before the request is sent. Keep the two tables identical.
const ALIASES: Record<string, string> = {
  kg: "kg", kgs: "kg", kilogram: "kg", kilograms: "kg", kilo: "kg", kilos: "kg",
  g: "g", gram: "g", grams: "g",
  t: "t", ton: "t", tons: "t", tonne: "t", tonnes: "t",
  lb: "lb", lbs: "lb", pound: "lb", pounds: "lb",
}
/** Kilograms in one of each mass unit. "t" is the metric tonne. */
const KG_PER_UNIT: Record<string, number> = { kg: 1, g: 0.001, t: 1000, lb: 0.45359237 }

/** Trimmed, lower-cased, aliases folded ("Tonnes" and " T " both become "t"). Unknown units are only normalised for case and spacing. */
export function canonicalUnit(unit: string | null | undefined): string {
  const key = (unit ?? "").trim().toLowerCase()
  return ALIASES[key] ?? key
}

export const isMassUnit = (unit: string | null | undefined) => canonicalUnit(unit) in KG_PER_UNIT

export const sameUnit = (a: string | null | undefined, b: string | null | undefined) => {
  const ca = canonicalUnit(a)
  return ca !== "" && ca === canonicalUnit(b)
}

/** The same unit, or two mass units. Anything else (bags, bales...) must match exactly. */
export const canConvertUnit = (from: string | null | undefined, to: string | null | undefined) =>
  sameUnit(from, to) || (isMassUnit(from) && isMassUnit(to))

/** The quantity in the target unit, rounded to six decimals like the server, or null when it cannot be converted. */
export function convertUnit(quantity: number, from: string | null | undefined, to: string | null | undefined): number | null {
  const f = canonicalUnit(from), t = canonicalUnit(to)
  if (!f || !t || !Number.isFinite(quantity)) return null
  if (f === t) return quantity
  if (!(f in KG_PER_UNIT) || !(t in KG_PER_UNIT)) return null
  return Math.round(((quantity * KG_PER_UNIT[f]) / KG_PER_UNIT[t]) * 1e6) / 1e6
}

export interface YieldSum { total: number; unconvertedUnits: string[] }

/**
 * Sum harvest quantities in the target unit. Units that cannot be converted are left out and listed, never added in. With no
 * target unit nothing converts, so quantities are summed as recorded and a mix of units is reported.
 */
export function sumHarvests(target: string | null | undefined, rows: { unitOfMeasure: string; quantityHarvested: number }[]): YieldSum {
  let total = 0
  const left = new Set<string>()
  const seen = new Set<string>()
  for (const r of rows) {
    seen.add(canonicalUnit(r.unitOfMeasure))
    if (!target?.trim()) { total += r.quantityHarvested; continue }
    const v = convertUnit(r.quantityHarvested, r.unitOfMeasure, target)
    if (v == null) left.add(r.unitOfMeasure.trim()); else total += v
  }
  const unconverted = target?.trim() ? [...left] : seen.size > 1 ? [...seen].slice(1) : []
  return { total: Math.round(total * 1e6) / 1e6, unconvertedUnits: unconverted }
}
