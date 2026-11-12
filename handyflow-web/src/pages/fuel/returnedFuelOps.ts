// Phase 7a helpers: confirming which tank returned fuel was put into. Display and checks only; the server enforces the limits.
export interface OutstandingReturn {
  deliveryId: string; customerName: string | null; vehicleReg: string | null; driverName: string | null; loadedLitres: number | null
  declaredReturned: number; confirmedBack: number; outstanding: number; lastAttemptAt: string
}

/** Message for the confirm form, or null when it can be sent. Mirrors the server's limit so the user finds out before sending. */
export function validateReturn(tankId: string, litres: string, outstanding: number): string | null {
  if (!tankId) return "Choose the tank the fuel was put into."
  const n = Number(litres)
  if (litres.trim() === "" || !Number.isFinite(n) || n <= 0) return "Enter the litres put into the tank."
  if (n > outstanding) return `Only ${outstanding} L is still waiting to be confirmed for this load.`
  return null
}

export const loadLabel = (r: OutstandingReturn): string => [r.customerName ?? "Load", r.vehicleReg, r.driverName].filter(Boolean).join(" · ")
