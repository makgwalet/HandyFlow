// src/pages/agriculture/agLifecycle.logic.ts
// Mirrors AgLifecycleRules on the server: which statuses an animal can change to, farm choices for a transfer, and the checks
// made before a head-count change is sent. The server repeats every check; these only explain a problem before Save.
export const MIN_REASON = 3

const LEAVING = ["SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT"]
const REINSTATABLE = ["SOLD", "CULLED", "TRANSFERRED_OUT"]

/** The statuses an animal in `from` may be changed to (not including `from`). */
export function allowedAnimalStatuses(from: string): string[] {
  if (from === "ACTIVE") return LEAVING
  if (REINSTATABLE.includes(from)) return ["ACTIVE"]
  return []
}

export function statusLabel(s: string): string { return s.toLowerCase().replace(/_/g, " ") }

export interface FarmLike { id: string; name: string }

/** Active farms other than the one the animal or group is on. */
export function farmChoices(farms: FarmLike[], currentFarmId: string): { id: string; label: string }[] {
  return farms.filter(f => f.id !== currentFarmId).sort((a, b) => a.name.localeCompare(b.name)).map(f => ({ id: f.id, label: f.name }))
}

export function reasonError(reason: string): string | null {
  return reason.trim().length < MIN_REASON ? `Give a reason (at least ${MIN_REASON} characters).` : null
}

/** Why this head-count change cannot be sent, or null. `kind` decrease needs the group to have that many. */
export function countAdjustError(kind: "increase" | "decrease", count: string, reason: string, current: number): string | null {
  const c = count.trim()
  if (!/^\d+$/.test(c) || Number(c) < 1) return "Count must be a whole number of 1 or more."
  if (kind === "decrease" && Number(c) > current) return `The group only has ${current}.`
  return reasonError(reason)
}

export interface CountLogLike { delta: number; countBefore: number; countAfter: number; changeType: string }

export function countLogLabel(l: CountLogLike): string {
  const sign = l.delta > 0 ? "+" : ""
  const what: Record<string, string> = { INCREASE: "Added", DECREASE: "Removed", SALE: "Sold", TRANSFER_OUT: "Transferred out", MORTALITY: "Lost" }
  return `${what[l.changeType] ?? l.changeType} ${sign}${l.delta} (${l.countBefore} → ${l.countAfter})`
}
