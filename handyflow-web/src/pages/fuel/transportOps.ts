// Stage 3 helpers: tanker/compartment validation, load splitting, and the driver's offline queue.
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

// ── Driver offline queue ────────────────────────────────────────────────────
export interface QueuedCompletion { deliveryId: string; clientRequestId: string; body: Record<string, unknown>; queuedAt: string }
const KEY = "fuel-driver-queue-v1"

export function readQueue(storage: Pick<Storage, "getItem"> = localStorage): QueuedCompletion[] {
  try { const v = JSON.parse(storage.getItem(KEY) ?? "[]"); return Array.isArray(v) ? v : [] } catch { return [] }
}
function write(q: QueuedCompletion[], storage: Pick<Storage, "setItem">) { try { storage.setItem(KEY, JSON.stringify(q)) } catch { /* storage full or blocked: nothing more we can do */ } }

/** The id is made once per completion and kept with the queued item, so every retry carries the same one. */
export function enqueue(deliveryId: string, body: Record<string, unknown>, id: string, storage: Pick<Storage, "getItem" | "setItem"> = localStorage): QueuedCompletion {
  const q = readQueue(storage).filter(x => x.deliveryId !== deliveryId)
  const item = { deliveryId, clientRequestId: id, body: { ...body, clientRequestId: id }, queuedAt: new Date().toISOString() }
  write([...q, item], storage); return item
}
export const dequeue = (deliveryId: string, storage: Pick<Storage, "getItem" | "setItem"> = localStorage) =>
  write(readQueue(storage).filter(x => x.deliveryId !== deliveryId), storage)

/** A failed request with no response from the server (offline, timeout) is worth retrying; one the server answered is not. */
export const isNetworkError = (e: any) => !e?.response

/**
 * Sends what is queued. Network failures stop the run and keep the rest; a server refusal drops that item and
 * is reported, so a bad item can never block the ones behind it.
 */
export async function flushQueue(send: (item: QueuedCompletion) => Promise<unknown>, storage: Pick<Storage, "getItem" | "setItem"> = localStorage) {
  const sent: string[] = []; const refused: { deliveryId: string; message: string }[] = []
  for (const item of readQueue(storage)) {
    try { await send(item); dequeue(item.deliveryId, storage); sent.push(item.deliveryId) }
    catch (e: any) {
      if (isNetworkError(e)) break
      dequeue(item.deliveryId, storage); refused.push({ deliveryId: item.deliveryId, message: e?.response?.data?.message ?? "The server refused this delivery." })
    }
  }
  return { sent, refused }
}
