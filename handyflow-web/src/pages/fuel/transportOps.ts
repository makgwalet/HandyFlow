// Stage 3 helpers: tanker/compartment validation, load splitting. The driver's offline queue lives in offlineQueue.ts.
export interface Compartment { id: string; number: number; capacityLitres: number }
export interface Tanker { id: string; registration: string; name: string | null; active: boolean; totalCapacity: number; compartments: Compartment[]; openLoads: number
  fleetVehicleId?: string | null; fleetVehicleLabel?: string | null; fleetSuggestionId?: string | null; fleetSuggestionLabel?: string | null }
export interface FleetVehicleOption { id: string; registration: string; label: string; linkedTankerRegistration: string | null }

/** What a Fleet vehicle reads as in the picker; one already used by a tanker is shown but cannot be chosen. */
export function fleetOptionText(v: FleetVehicleOption): string {
  return v.linkedTankerRegistration ? `${v.label} (already a tanker: ${v.linkedTankerRegistration})` : v.label
}
export interface Driver { id: string; name: string; jobTitle: string | null; phone: string | null }
export interface PlanLoad {
  deliveryId: string; status: string; scheduledAt: string; customerId: string | null; customerName: string | null; fuelType: string
  litres: number; tankerId: string | null; tankerRegistration: string | null; driverEmployeeId: string | null; driverName: string | null; allocated: boolean
  windowStart?: string | null; windowEnd?: string | null; failedAttempts?: number; routeId?: string | null; routeSequence?: number | null
}
export interface TankerDay { tankerId: string; registration: string; capacity: number; loads: number; litres: number }
export interface Plan { date: string; loads: PlanLoad[]; tankers: TankerDay[] }

const num = (s: string) => (s.trim() === "" ? NaN : Number(s))

/** "10000, 6000" -> [10000, 6000]; null with a message when invalid. */
export function parseCapacities(text: string): { values: number[]; error: string | null } {
  const parts = text.split(/[,\s]+/).filter(Boolean)
  if (parts.length === 0) return { values: [], error: "Enter the capacity of each compartment, e.g. 10000, 6000." }
  if (parts.length > 12) return { values: [], error: "A tanker can have at most 12 compartments." }
  const values = parts.map(Number)
  if (values.some(v => Number.isNaN(v) || v <= 0)) return { values: [], error: "Every compartment needs a capacity above zero." }
  return { values, error: null }
}

export function validateTanker(registration: string, capacities: string): string | null {
  if (!registration.trim()) return "Enter the tanker's registration."
  return parseCapacities(capacities).error
}

/** Fills compartments in order until the load is placed; null when it does not fit. */
export function autoSplit(litres: number, compartments: Compartment[]): Record<string, string> | null {
  let left = litres; const out: Record<string, string> = {}
  for (const c of [...compartments].sort((a, b) => a.number - b.number)) {
    if (left <= 0) break
    const take = Math.min(left, c.capacityLitres); out[c.id] = String(take); left -= take
  }
  return left > 0 ? null : out
}

export function validateAllocation(tankerId: string, driverId: string, litres: number, split: Record<string, string>, compartments: Compartment[]): string | null {
  if (!tankerId) return "Choose a tanker."
  if (!driverId) return "Choose a driver."
  let total = 0
  for (const c of compartments) {
    const raw = split[c.id]; if (!raw || raw.trim() === "") continue
    const v = num(raw)
    if (Number.isNaN(v) || v <= 0) return `Compartment ${c.number}: litres must be above zero.`
    if (v > c.capacityLitres) return `Compartment ${c.number} holds ${c.capacityLitres} L; you entered ${v} L.`
    total += v
  }
  if (Math.abs(total - litres) > 0.0001) return `The compartments add up to ${total} L but the load is ${litres} L.`
  return null
}

// ── C04: delivery windows and failed attempts ───────────────────────────────
/** Operational reasons only. They match the server list; the server is the one that enforces it. */
export const FAILURE_REASONS: { code: string; label: string }[] = [
  { code: "SITE_CLOSED", label: "Site closed" }, { code: "NO_ACCESS", label: "No access to the site" },
  { code: "RECEIVER_ABSENT", label: "Nobody to receive the fuel" }, { code: "REFUSED", label: "Customer refused the delivery" },
  { code: "SAFETY", label: "Safety concern" }, { code: "VEHICLE_FAULT", label: "Vehicle fault" }, { code: "OTHER", label: "Other (describe)" },
]
export const reasonLabel = (code: string) => FAILURE_REASONS.find(r => r.code === code)?.label ?? code

/** "08:00 to 12:00", "from 08:00", "by 12:00" or null when no window is set. */
export function windowText(start?: string | null, end?: string | null): string | null {
  const t = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
  if (start && end) return `${t(start)} to ${t(end)}`
  if (start) return `from ${t(start)}`
  if (end) return `by ${t(end)}`
  return null
}

/** Null when the failed-attempt details are fine to send, otherwise the message to show. */
export function validateFailure(reasonCode: string, note: string, litresReturned: string, loadedLitres?: number | null): string | null {
  if (!reasonCode) return "Pick a reason."
  if (reasonCode === "OTHER" && !note.trim()) return "Describe what happened."
  if (litresReturned.trim()) {
    const n = Number(litresReturned)
    if (Number.isNaN(n) || n < 0) return "Litres returned must be zero or more."
    if (n > 0 && (loadedLitres == null)) return "No loading was recorded, so litres cannot be returned."
    if (loadedLitres != null && n > loadedLitres) return `Litres returned cannot be more than the ${loadedLitres} L loaded.`
  }
  return null
}

// ── C04 part 2: routes ──────────────────────────────────────────────────────
export interface RouteStop { deliveryId: string; sequence: number; customerName: string | null; fuelType: string; litres: number; status: string; scheduledAt: string; windowStart: string | null; windowEnd: string | null; failedAttempts: number }
export interface RouteView {
  id: string; routeDate: string; label: string | null; status: string; state: "PLANNED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED"
  tankerId: string; tankerRegistration: string | null; driverEmployeeId: string; driverName: string | null
  capacityLitres: number; plannedLitres: number; stopCount: number; deliveredCount: number; cancelReason: string | null; stops: RouteStop[]
}

/** The stop ids in a new order after moving the stop at `index` up (-1) or down (+1). A stop that is no longer scheduled does not move, and nothing moves past one. */
export function moveStop(stops: RouteStop[], index: number, dir: -1 | 1): string[] | null {
  const to = index + dir
  if (index < 0 || index >= stops.length || to < 0 || to >= stops.length) return null
  if (stops[index].status !== "SCHEDULED" || stops[to].status !== "SCHEDULED") return null
  const ids = stops.map(s => s.deliveryId); [ids[index], ids[to]] = [ids[to], ids[index]]
  return ids
}

/** Loads on the day that could still be added to a route: scheduled and not already a stop. */
export const routable = (loads: PlanLoad[]) => loads.filter(l => l.status === "SCHEDULED" && !l.routeId)

export const routeStateLabel = (s: string) => ({ PLANNED: "Planned", IN_PROGRESS: "On the road", COMPLETED: "Completed", CANCELLED: "Cancelled" } as Record<string, string>)[s] ?? s

// ── Eligibility (what a site, driver and compartment allow) ─────────────────
export type Severity = "BLOCK" | "REVIEW" | "INFO"
export interface Finding { code: string; severity: Severity; message: string }
export interface EligibilityResult { findings: Finding[] }

/** The litres entered per compartment, as the server wants them. */
export function splitToLoads(split: Record<string, string>): { compartmentId: string; litres: number }[] {
  return Object.entries(split).filter(([, v]) => v.trim() !== "").map(([compartmentId, v]) => ({ compartmentId, litres: Number(v) }))
}

/**
 * Whether the allocation may be sent, given what the eligibility check found. A Block stops everyone; a Review needs the review permission and
 * a reason of at least 5 characters. Returns the sentence to show, or null when it may go ahead.
 */
export function eligibilityGate(findings: Finding[], canReview: boolean, reason: string): string | null {
  if (findings.some(f => f.severity === "BLOCK")) return "This allocation is blocked. Fix what is listed above; it cannot be overridden."
  if (findings.some(f => f.severity === "REVIEW")) {
    if (!canReview) return "Someone with the eligibility review permission must allocate this load."
    if (reason.trim().length < 5) return "Give a reason of at least 5 characters to allocate with these review points."
  }
  return null
}
