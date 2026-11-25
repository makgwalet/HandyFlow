export type AgreementSettings = { requireSecondPerson: boolean; minValue: number | null; updatedAt: string | null }

/** What is wrong with the settings as typed, or null. The server checks again. */
export function settingsProblem(on: boolean, min: string): string | null {
  if (!on || !min.trim()) return null
  const n = Number(min.replace(/\s/g, "").replace(",", "."))
  if (!Number.isFinite(n) || n <= 0) return "The amount must be a number more than zero"
  return null
}
