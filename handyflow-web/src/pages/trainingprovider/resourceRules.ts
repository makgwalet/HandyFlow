// Wording and checks for the site resource register. It records what equipment a delivery site holds and the state it is in. It is not a
// booking system, and listing a resource does not show that it meets any standard. The server enforces every rule; these save a round trip.

export type Resource = {
  id: string; resourceNo: string; siteId: string; siteName: string | null; venueId: string | null; venueName: string | null; name: string; resourceType: string
  identifier: string | null; quantity: number; shareable: boolean; notes: string | null; status: string; statusReason: string | null; statusChangedOn: string | null; nextStatuses: string[]
}
export type ResourceEvent = { id: string; eventType: string; fromStatus: string | null; toStatus: string | null; fromSiteName: string | null; toSiteName: string | null; note: string | null; occurredOn: string; createdAt: string }
export type ResourceDetail = { resource: Resource; history: ResourceEvent[] }

export const MIN_REASON = 10
export const REGISTER_NOTE = "This is a register of what each site holds and the state it is in. It does not book resources, and listing one does not show that it meets any standard."
export const TYPES: Record<string, string> = {
  EQUIPMENT: "Equipment", VEHICLE: "Vehicle", PRACTICAL_KIT: "Practical kit", SIMULATOR: "Simulator", IT_EQUIPMENT: "IT equipment", CONSUMABLES: "Consumables", OTHER: "Other",
}
export const STATUSES: Record<string, string> = { IN_SERVICE: "In service", UNDER_REPAIR: "Under repair", OUT_OF_SERVICE: "Out of service", RETIRED: "Retired" }
export const EVENTS: Record<string, string> = { ADDED: "Added", CHANGED: "Details changed", MOVED: "Moved", STATUS: "State changed" }
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const statusTone = (s: string): "good" | "warn" | "bad" | "neutral" => (s === "IN_SERVICE" ? "good" : s === "RETIRED" ? "neutral" : "warn")

export type ResourceForm = { siteId: string; venueId: string; name: string; resourceType: string; identifier: string; quantity: string; shareable: boolean; notes: string }
export const emptyResource = (): ResourceForm => ({ siteId: "", venueId: "", name: "", resourceType: "", identifier: "", quantity: "1", shareable: false, notes: "" })
export const resourceForm = (r: Resource): ResourceForm => ({
  siteId: r.siteId, venueId: r.venueId ?? "", name: r.name, resourceType: r.resourceType, identifier: r.identifier ?? "", quantity: String(r.quantity), shareable: r.shareable, notes: r.notes ?? "",
})
export function resourceProblem(f: ResourceForm): string | null {
  if (!f.siteId) return "Choose the site"
  if (f.name.trim().length < 2) return "Give the resource a name"
  if (!TYPES[f.resourceType]) return "Choose the type of resource"
  const q = Number(f.quantity)
  if (f.quantity.trim() === "" || !Number.isInteger(q) || q < 1 || q > 100000) return "Quantity must be a whole number from 1 to 100000"
  return null
}
export const resourceBody = (f: ResourceForm) => ({
  siteId: f.siteId, venueId: f.venueId || null, name: f.name.trim(), resourceType: f.resourceType, identifier: f.identifier.trim() || null, quantity: Number(f.quantity),
  shareable: f.shareable, notes: f.notes.trim() || null,
})

export type StatusForm = { toStatus: string; reason: string; changedOn: string }
export const emptyStatus = (toStatus: string, today: string): StatusForm => ({ toStatus, reason: "", changedOn: today })
export function statusProblem(f: StatusForm, today: string): string | null {
  if (!STATUSES[f.toStatus]) return "Choose a state"
  if (f.reason.trim().length < MIN_REASON) return `Give a reason of at least ${MIN_REASON} characters`
  if (!f.changedOn) return "Give the day it changed"
  if (f.changedOn > today) return "The change cannot be dated in the future"
  return null
}

export type MoveForm = { siteId: string; venueId: string; reason: string; movedOn: string }
export const emptyMove = (today: string): MoveForm => ({ siteId: "", venueId: "", reason: "", movedOn: today })
export function moveProblem(f: MoveForm, current: Pick<Resource, "siteId">, today: string): string | null {
  if (!f.siteId) return "Choose the site it is moving to"
  if (f.siteId === current.siteId) return "It is already at that site"
  if (f.reason.trim().length < MIN_REASON) return `Give a reason of at least ${MIN_REASON} characters`
  if (!f.movedOn) return "Give the day it moved"
  if (f.movedOn > today) return "The move cannot be dated in the future"
  return null
}

export function historyLine(e: ResourceEvent): string {
  if (e.eventType === "STATUS") return `${label(STATUSES, e.fromStatus)} to ${label(STATUSES, e.toStatus)}: ${e.note ?? ""}`
  if (e.eventType === "MOVED") return `${e.fromSiteName ?? "—"} to ${e.toSiteName ?? "—"}: ${e.note ?? ""}`
  return label(EVENTS, e.eventType)
}
