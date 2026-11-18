// Pure helpers for category tolerances, supplier VAT, exchange rates and the contract-savings report (no React, no network).

export const pctProblem = (v: string, what: string): string | null => {
  if (v.trim() === "") return `Enter the ${what}`
  const n = Number(v)
  return Number.isFinite(n) && n >= 0 && n <= 100 ? null : `The ${what} must be between 0 and 100`
}

export interface RateForm { currency: string; rate: string; effectiveFrom: string }
export const emptyRate = (today: string): RateForm => ({ currency: "", rate: "", effectiveFrom: today })

export function rateProblem(f: RateForm, base: string): string | null {
  const cur = f.currency.trim().toUpperCase()
  if (!/^[A-Z]{3}$/.test(cur)) return "Currency must be a 3-letter code such as USD"
  if (cur === base.toUpperCase()) return `${cur} is the base currency and always has a rate of 1`
  const r = Number(f.rate)
  if (f.rate.trim() === "" || !Number.isFinite(r) || r <= 0) return "The rate must be greater than zero"
  const dp = f.rate.includes(".") ? f.rate.split(".")[1].length : 0
  if (dp > 8) return "The rate can have at most 8 decimal places"
  if (!f.effectiveFrom) return "Say the date the rate applies from"
  return null
}

export const rateBody = (f: RateForm) => ({ currency: f.currency.trim().toUpperCase(), rate: Number(f.rate), effectiveFrom: f.effectiveFrom })

/** Words for what a policy change did to existing budget commitments. */
export function restateText(restated: number, skipped: number): string {
  if (!restated && !skipped) return ""
  const base = `${restated} budget commitment${restated === 1 ? "" : "s"} restated`
  return skipped ? `${base}; ${skipped} left alone because their currency has no exchange rate.` : `${base}.`
}

export interface SavingsTotals { saving: number; leakage: number; listValue: number; paidValue: number }
/** Saving as a share of the list value of what was bought, one decimal; null when nothing was bought at a list price. */
export function savingPct(t: SavingsTotals): number | null {
  return t.listValue > 0 ? Math.round((t.saving / t.listValue) * 1000) / 10 : null
}
