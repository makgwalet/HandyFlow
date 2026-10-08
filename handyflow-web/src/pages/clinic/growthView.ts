// Display rules for growth charts (CLINIC-DEC-013). No clinical thresholds live here: a z-score is shown, never judged.
export interface Measured { observationId: string; takenAt: string; ageMonths: number; value: number | string; zScore: number | null; percentile: number | null }
export interface CurvePoint { ageMonths: number; values: number[] }
export interface Curves { setId: string; source: string; sourceVersion: string; zLines: number[]; points: CurvePoint[] }
export interface MeasureChart { code: string; label: string; unit: string; banner: string | null; measurements: Measured[]; curves: Curves | null }
export interface GrowthChart { patientId: string; sexAtBirth: string | null; currentAgeMonths: number | null; notes: string[]; measures: MeasureChart[] }

export const NOT_APPROVED = "DATA NOT CLINICALLY APPROVED"

export function ageLabel(months: number): string {
  if (months < 0) return "—"
  if (months < 24) return `${Math.floor(months)} m`
  const y = Math.floor(months / 12), m = Math.floor(months - y * 12)
  return m === 0 ? `${y} y` : `${y} y ${m} m`
}

export const zText = (z: number | null | undefined) => z == null ? "—" : `${z >= 0 ? "+" : "−"}${Math.abs(z).toFixed(2)} SD`

export function percentileText(p: number | null | undefined): string {
  if (p == null) return "—"
  if (p < 0.1) return "<0.1"
  if (p > 99.9) return ">99.9"
  return p < 10 || p > 90 ? p.toFixed(1) : p.toFixed(0)
}

export const zKey = (z: number) => `z${z < 0 ? "m" : "p"}${Math.abs(z)}`
export const zName = (z: number) => z === 0 ? "Median" : `${z > 0 ? "+" : "−"}${Math.abs(z)} SD`

/** Rows for one chart: the reference lines (only from an approved set) and the child's own points, on one age axis. */
export function chartRows(m: MeasureChart): Record<string, number>[] {
  const rows: Record<string, number>[] = []
  if (m.curves) for (const p of m.curves.points) {
    const row: Record<string, number> = { age: p.ageMonths }
    m.curves.zLines.forEach((z, i) => { row[zKey(z)] = p.values[i] })
    rows.push(row)
  }
  for (const x of m.measurements) rows.push({ age: x.ageMonths, value: Number(x.value) })
  return rows.sort((a, b) => a.age - b.age)
}

/** The sentence under the chart that says where the lines came from. */
export function sourceLine(m: MeasureChart): string {
  return m.curves ? `Reference: ${m.curves.source}, version ${m.curves.sourceVersion}` : `${NOT_APPROVED}: no approved reference set is active for this measure, so no curves or z-scores are shown.`
}
