// Pure helpers for the Stock tab (Stage 1A): validation that mirrors the server's rules,
// so people get an instant message, while the server stays the authority.
export interface TankLine {
  tankId: string; tankName: string; fuelType: string; depotId: string | null
  physical: number; reserved: number; safetyStock: number; availableToPromise: number
  ledgerBalance: number; ledgerMatches: boolean
}
export interface DepotLine { depotId: string; depotName: string; fuelType: string; physical: number; reserved: number; safetyStock: number; availableToPromise: number }
export interface Availability { tanks: TankLine[]; depots: DepotLine[] }

export const MIN_REASON = 5

export const fmtL = (n: number | null | undefined) =>
  n == null || Number.isNaN(Number(n)) ? "–" : `${Number(n).toLocaleString("en-ZA", { maximumFractionDigits: 2 })} L`

/** Available to promise = physical - reserved - safety stock, never below zero. */
export const availableToPromise = (physical: number, reserved: number, safety: number) =>
  Math.max(0, physical - reserved - safety)

const num = (s: string) => (s.trim() === "" ? NaN : Number(s))

export function validateAdjust(litres: string, reason: string, tank: Pick<TankLine, "physical">, capacity?: number): string | null {
  const n = num(litres)
  if (Number.isNaN(n) || n === 0) return "Enter the litres to add (+) or remove (-)."
  if (tank.physical + n < 0) return `That would take the tank below zero (it holds ${fmtL(tank.physical)}).`
  if (capacity != null && tank.physical + n > capacity) return `That would exceed the tank capacity (${fmtL(capacity)}).`
  if (reason.trim().length < MIN_REASON) return "Give a reason of at least 5 characters."
  return null
}

export function validateTransfer(from: TankLine | undefined, to: TankLine | undefined, litres: string, reason: string): string | null {
  if (!from || !to) return "Choose both tanks."
  if (from.tankId === to.tankId) return "Choose two different tanks."
  if (from.fuelType !== to.fuelType) return `Cannot move ${from.fuelType} into a ${to.fuelType} tank.`
  const n = num(litres)
  if (Number.isNaN(n) || n <= 0) return "Litres must be greater than zero."
  const free = from.physical - from.reserved
  if (n > free) return `Only ${fmtL(Math.max(0, free))} of ${from.tankName} is free (${fmtL(from.reserved)} is reserved).`
  if (reason.trim().length < MIN_REASON) return "Give a reason of at least 5 characters."
  return null
}

export function validateReserve(tank: TankLine | undefined, litres: string): string | null {
  if (!tank) return "Choose a tank."
  const n = num(litres)
  if (Number.isNaN(n) || n <= 0) return "Litres must be greater than zero."
  if (n > tank.availableToPromise) return `Only ${fmtL(tank.availableToPromise)} is available to promise on ${tank.tankName}.`
  return null
}

export const MOVEMENT_LABEL: Record<string, string> = {
  OPENING: "Opening balance", RECEIPT: "Stock in", DISPATCH: "Dispatch", DELIVERY: "Customer delivery",
  ADJUSTMENT: "Adjustment", TRANSFER_OUT: "Transfer out", TRANSFER_IN: "Transfer in", REVERSAL: "Reversal",
}
export const movementLabel = (t: string) => MOVEMENT_LABEL[t] ?? t

/** Tanks whose ledger does not add up to the balance: these need attention. */
export const mismatches = (a: Availability | undefined) => (a?.tanks ?? []).filter(t => !t.ledgerMatches)
