// Fuel fleet map: join each tanker to its tracked Fleet vehicle and to today's loads. Display only.
import type { PlanLoad, Tanker } from "./transportOps"
import { hasFix, type LivePosition } from "../fleet/liveMap.logic"

export type TankerState = "TRACKED" | "NO_FIX" | "UNLINKED" | "NOT_TRACKED"
export interface TankerOnMap {
  tanker: Tanker; state: TankerState; position: LivePosition | null
  loads: PlanLoad[]; current: PlanLoad | null; litresToday: number
}

/** The load that matters now: the one on the road, else the first still waiting to leave (by scheduled time). */
export function currentLoad(loads: PlanLoad[]): PlanLoad | null {
  const open = loads.filter(l => l.status === "IN_TRANSIT" || l.status === "SCHEDULED")
  return open.find(l => l.status === "IN_TRANSIT") ?? [...open].sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt))[0] ?? null
}

/**
 * One row per active tanker. UNLINKED: the tanker has no Fleet vehicle. NOT_TRACKED: it has one but Fleet has no position for it.
 * NO_FIX: a position exists but it has no usable coordinates. Only TRACKED tankers are drawn.
 */
export function joinTankers(tankers: Tanker[], positions: LivePosition[], loads: PlanLoad[]): TankerOnMap[] {
  const byVehicle = new Map(positions.map(p => [p.vehicleId, p]))
  return tankers.filter(t => t.active).map(t => {
    const mine = loads.filter(l => l.tankerId === t.id)
    const pos = t.fleetVehicleId ? byVehicle.get(t.fleetVehicleId) ?? null : null
    const state: TankerState = !t.fleetVehicleId ? "UNLINKED" : !pos ? "NOT_TRACKED" : hasFix(pos) ? "TRACKED" : "NO_FIX"
    return { tanker: t, state, position: pos, loads: mine, current: currentLoad(mine), litresToday: mine.filter(l => l.status !== "CANCELLED" && l.status !== "REVERSED").reduce((a, l) => a + (l.litres ?? 0), 0) }
  }).sort((a, b) => rank(a) - rank(b) || a.tanker.registration.localeCompare(b.tanker.registration))
}
const rank = (r: TankerOnMap) => (r.current?.status === "IN_TRANSIT" ? 0 : r.state === "TRACKED" ? 1 : 2)

export const STATE_TEXT: Record<TankerState, string> = {
  TRACKED: "Tracked", NO_FIX: "No position yet", NOT_TRACKED: "Linked, but not tracked", UNLINKED: "Not linked to a Fleet vehicle",
}

/** The positions to draw, labelled with the tanker's own registration. */
export function drawable(rows: TankerOnMap[]): LivePosition[] {
  return rows.filter(r => r.state === "TRACKED" && r.position).map(r => ({ ...r.position!, vehicleId: r.tanker.id, registration: r.tanker.registration }))
}
