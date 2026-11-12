// D01: tank-to-pump reconciliation helpers.
export interface MovementLine { label: string; litres: number; count: number }
export interface TankReconciliation {
  ready: boolean; message: string | null; tankId: string; tankName: string; fuelType: string
  fromDipAt: string | null; fromDipLitres: number | null; toDipAt: string | null; toDipLitres: number | null
  movements: MovementLine[]; expectedLitres: number | null; varianceLitres: number | null; throughputLitres: number | null; variancePct: number | null
  meterLitres: number | null; ledgerForecourtLitres: number | null; meterVsLedgerLitres: number | null
  tolerancePct: number | null; outsideTolerance: boolean
}

/** Blank means no flag. Mirrors the server: 0 to 100. */
export function validateTolerance(s: string): string | null {
  if (s.trim() === "") return null
  const n = Number(s)
  return !Number.isFinite(n) || n < 0 || n > 100 ? "Enter a tolerance between 0 and 100 percent, or leave it empty." : null
}

/** A window as ISO instants from two date inputs (start of the first day, end of the last), in the browser's zone. */
export function windowParams(fromDay: string, toDay: string): { from?: string; to?: string; error?: string } {
  if (fromDay && toDay && fromDay > toDay) return { error: "The start date is after the end date." }
  return {
    from: fromDay ? new Date(`${fromDay}T00:00:00`).toISOString() : undefined,
    to: toDay ? new Date(`${toDay}T23:59:59`).toISOString() : undefined,
  }
}

export const varianceSentence = (r: Pick<TankReconciliation, "varianceLitres" | "variancePct">): string => {
  if (r.varianceLitres == null) return ""
  const v = Number(r.varianceLitres)
  if (v === 0) return "The closing dip matches what the records say should be in the tank."
  const pct = r.variancePct == null ? "" : ` (${Math.abs(Number(r.variancePct)).toFixed(2)}% of litres out)`
  return v < 0 ? `The tank holds ${Math.abs(v).toLocaleString("en-ZA")} L less than the records say${pct}.` : `The tank holds ${v.toLocaleString("en-ZA")} L more than the records say${pct}.`
}
