// Tax-year helpers for the SARS tab.

/** Tax years run March to February and are named by the year they end in. */
export function currentTaxYear(today = new Date()): number {
  return today.getMonth() >= 2 ? today.getFullYear() + 1 : today.getFullYear()
}

export function monthsOfTaxYear(taxYear: number): string[] {
  const out: string[] = []
  for (let i = 0; i < 12; i++) {
    const m = ((2 + i) % 12) + 1
    const y = m >= 3 ? taxYear - 1 : taxYear
    out.push(`${y}-${String(m).padStart(2, "0")}`)
  }
  return out
}

export const monthLabel = (m: string) => new Date(`${m}-01T00:00:00`).toLocaleDateString("en-ZA", { month: "long", year: "numeric" })
