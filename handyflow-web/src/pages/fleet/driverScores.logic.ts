// Driver 360: score bands, the settings form (weights must total 100, thresholds must descend) and cost/summary wording.
export const BAND_LABEL: Record<string, string> = { EXCELLENT: "Excellent", GOOD: "Good", FAIR: "Fair", POOR: "Poor" }
export const bandTone = (band: string | null | undefined): "ok" | "warn" | "bad" | "none" =>
  band === "EXCELLENT" || band === "GOOD" ? "ok" : band === "FAIR" ? "warn" : band === "POOR" ? "bad" : "none"

export const scoreText = (overall: number | null | undefined) => (overall == null ? "No data" : `${overall.toFixed(1)}%`)

export interface Settings {
  weightSafety: number; weightInspection: number; weightAccidents: number; weightFuel: number; weightTrips: number; weightCompliance: number
  lookbackDays: number; excellentThreshold: number; goodThreshold: number; fairThreshold: number
}
export type SettingsForm = Record<keyof Settings, string>
export const WEIGHT_FIELDS: { key: keyof Settings; label: string }[] = [
  { key: "weightSafety", label: "Safety" }, { key: "weightInspection", label: "Inspection compliance" }, { key: "weightAccidents", label: "Accidents" },
  { key: "weightFuel", label: "Fuel efficiency" }, { key: "weightTrips", label: "Trip compliance" }, { key: "weightCompliance", label: "Licence and PrDP compliance" },
]
export const toForm = (s: Settings): SettingsForm => Object.fromEntries(Object.entries(s).map(([k, v]) => [k, String(v)])) as SettingsForm
const n = (v: string) => Number(v)
export const weightTotal = (f: SettingsForm) => WEIGHT_FIELDS.reduce((t, w) => t + (n(f[w.key]) || 0), 0)

export function settingsProblem(f: SettingsForm): string | null {
  for (const w of WEIGHT_FIELDS) { const v = n(f[w.key]); if (!Number.isInteger(v) || v < 0 || v > 100) return `${w.label} must be a whole number from 0 to 100.` }
  const total = weightTotal(f)
  if (total !== 100) return `The weights add up to ${total}; they must add up to 100.`
  const days = n(f.lookbackDays)
  if (!Number.isInteger(days) || days < 7 || days > 730) return "Look back between 7 and 730 days."
  const [e, g, fa] = [n(f.excellentThreshold), n(f.goodThreshold), n(f.fairThreshold)]
  if (![e, g, fa].every(v => Number.isInteger(v) && v >= 1 && v <= 100)) return "Band thresholds must be whole numbers from 1 to 100."
  if (!(e > g && g > fa)) return "Thresholds must go down: excellent, then good, then fair."
  return null
}
export const settingsBody = (f: SettingsForm): Settings => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, Number(v)])) as unknown as Settings

/** A low-coverage score is only a hint, and the screen says so. */
export const coverageNote = (reliable: boolean, coverage: number) => (reliable ? null : `Indicative only: only ${Math.round(coverage)}% of the score had data.`)
