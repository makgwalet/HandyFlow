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

// ── Layout helpers for the growth page (patch 0165). Still no judgement: only labels, arithmetic and ordering. ──

const TAB_LABEL: Record<string, string> = { WEIGHT: "Weight-for-age", HEIGHT: "Height-for-age", BMI: "BMI-for-age", HEAD_CIRCUMFERENCE: "Head circumference-for-age" }
export const tabLabel = (m: Pick<MeasureChart, "code" | "label">) => TAB_LABEL[m.code] ?? m.label

const SEX_WORD: Record<string, string> = { MALE: "Boys", FEMALE: "Girls" }

/** "Weight-for-age (Boys 0–5 years)" when a reference set covers an age range, else just the name. */
export function chartTitle(m: MeasureChart, sexAtBirth: string | null | undefined): string {
  const base = tabLabel(m)
  const pts = m.curves?.points ?? []
  const sex = sexAtBirth ? SEX_WORD[sexAtBirth] : undefined
  if (pts.length === 0) return sex ? `${base} (${sex})` : base
  const lo = pts[0].ageMonths, hi = pts[pts.length - 1].ageMonths
  const span = (x: number) => x >= 24 ? `${Math.round(x / 12)}` : `${Math.round(x)}`
  const unit = hi >= 24 ? "years" : "months"
  const range = hi >= 24 ? `${span(Math.max(lo, 0))}–${span(hi)} ${unit}`.replace(/^0–/, "0–") : `${span(lo)}–${span(hi)} ${unit}`
  return `${base} (${sex ? sex + " " : ""}${range})`
}

/** Rows for the shaded bands: a baseline at the lowest line, then the gap to the next line, so bands can be stacked. */
export function bandRows(m: MeasureChart): Record<string, number>[] {
  if (!m.curves || m.curves.zLines.length < 2) return []
  const z = m.curves.zLines
  return m.curves.points.map(p => {
    const row: Record<string, number> = { age: p.ageMonths, base: p.values[0] }
    for (let i = 1; i < z.length; i++) row[`band${i}`] = Math.max(0, p.values[i] - p.values[i - 1])
    return row
  })
}
export const bandCount = (m: MeasureChart) => Math.max(0, (m.curves?.zLines.length ?? 0) - 1)

export interface Latest { value: string; unit: string; takenAt: string; ageMonths: number; z: string; percentile: string; change: string | null; since: string | null }

const fmtNum = (n: number) => String(Math.round(n * 100) / 100)
const dayShort = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: "Africa/Johannesburg" })

/** The newest measurement of a measure, and how much it differs from the one before it (a difference, not a verdict). */
export function latestOf(m: MeasureChart): Latest | null {
  const ms = [...m.measurements].sort((a, b) => a.takenAt.localeCompare(b.takenAt))
  const last = ms[ms.length - 1]
  if (!last) return null
  const prev = ms[ms.length - 2]
  let change: string | null = null
  if (prev) {
    const d = Number(last.value) - Number(prev.value)
    change = `${d >= 0 ? "+" : "−"}${fmtNum(Math.abs(d))} ${m.unit}`
  }
  return { value: fmtNum(Number(last.value)), unit: m.unit, takenAt: last.takenAt, ageMonths: last.ageMonths, z: zText(last.zScore), percentile: percentileText(last.percentile),
    change, since: prev ? dayShort(prev.takenAt) : null }
}

/** "Gained 1.2 kg over 4 m" from first to last measurement; null with fewer than two. */
export function trendLine(m: MeasureChart): string | null {
  const ms = [...m.measurements].sort((a, b) => a.takenAt.localeCompare(b.takenAt))
  if (ms.length < 2) return null
  const first = ms[0], last = ms[ms.length - 1]
  const d = Number(last.value) - Number(first.value)
  const word = d > 0 ? "Up" : d < 0 ? "Down" : "No change"
  const span = ageLabel(Math.max(0, last.ageMonths - first.ageMonths))
  return d === 0 ? `No change over ${span}` : `${word} ${fmtNum(Math.abs(d))} ${m.unit} over ${span}`
}

export interface TableRow { day: string; takenAt: string; ageMonths: number; cells: Record<string, string>; z: string; percentile: string }
const dayKeyZA = (iso: string) => new Date(new Date(iso).getTime() + 2 * 3600_000).toISOString().slice(0, 10)

/** One row per day with a column for each measure, newest first. Z-score and percentile are those of the measure shown on the chart. */
export function mergedTable(g: GrowthChart, activeCode: string): TableRow[] {
  const rows = new Map<string, TableRow>()
  for (const m of g.measures) for (const x of m.measurements) {
    const day = dayKeyZA(x.takenAt)
    const r = rows.get(day) ?? { day, takenAt: x.takenAt, ageMonths: x.ageMonths, cells: {}, z: "—", percentile: "—" }
    r.cells[m.code] = `${fmtNum(Number(x.value))} ${m.unit}`
    if (m.code === activeCode) { r.percentile = percentileText(x.percentile); r.z = zText(x.zScore) }
    rows.set(day, r)
  }
  return [...rows.values()].sort((a, b) => b.day.localeCompare(a.day))
}
