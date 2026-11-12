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
