// Wording and checks for the resources a programme version lists as needed, and how a cohort's site register compares.
// The list is the provider's own, with a note of where each need comes from. A comparison is a prompt to check, never a finding about the programme.
// The server enforces every rule; these save a round trip.
import { TYPES } from "./resourceRules"

export type Need = {
  id: string; programmeVersionId: string; resourceType: string; description: string; matchText: string | null; quantity: number; perLearners: number | null
  sourceNote: string; status: string; withdrawnReason: string | null; createdAt: string
}
export type VersionNeeds = { versionId: string; versionNo: number; versionStatus: string; editable: boolean; needs: Need[] }
export type ReadinessRow = { needId: string; description: string; resourceType: string; required: number; held: number; status: string; note: string }
export type CohortReadiness = { cohortId: string; siteName: string; learners: number; note: string; rows: ReadinessRow[] }

export type SlotNeedRow = { needId: string; description: string; required: number; booked: number; status: string; note: string }
export type SlotNeeds = { slotId: string; notCovered: number; rows: SlotNeedRow[] }
export type CohortSlotNeeds = { cohortId: string; note: string; slots: SlotNeeds[] }

/** One short line for a session. A prompt to check the bookings, never a finding. */
export function slotFlag(n: SlotNeeds | undefined): string | null {
  if (!n || n.notCovered === 0) return null
  return n.notCovered === 1 ? "1 listed need is not fully booked for this session. Check the bookings." : `${n.notCovered} listed needs are not fully booked for this session. Check the bookings.`
}

export const MIN_NOTE = 10
export const NONE_NOTE = "This programme version lists no resource needs. That does not mean none are needed; it means none have been recorded here."
export const NEEDS_NOTE = "List what the provider says this programme needs, and where that comes from. It is the provider's own list and holds no standard. It can be changed only while the version is a draft."
export const READINESS_STATUS: Record<string, string> = { ENOUGH: "Listed in the register", SHORT: "Fewer than listed", NONE_HELD: "None in the register" }
export const readinessTone = (s: string): "good" | "warn" | "bad" => (s === "ENOUGH" ? "good" : s === "SHORT" ? "warn" : "bad")

export type NeedForm = { resourceType: string; description: string; matchText: string; quantity: string; perLearners: string; sourceNote: string }
export const emptyNeed = (): NeedForm => ({ resourceType: "", description: "", matchText: "", quantity: "1", perLearners: "", sourceNote: "" })

export function needProblem(f: NeedForm): string | null {
  if (!TYPES[f.resourceType]) return "Choose the type of resource"
  if (f.description.trim().length < 2) return "Say what is needed"
  if (f.matchText.trim() !== "" && f.matchText.trim().length < 2) return "The words to look for in the resource name need at least two characters"
  const q = Number(f.quantity)
  if (!Number.isInteger(q) || q < 1) return "The quantity must be at least 1"
  if (f.perLearners.trim() !== "") {
    const p = Number(f.perLearners)
    if (!Number.isInteger(p) || p < 1) return "Learners per set must be at least 1"
  }
  if (f.sourceNote.trim().length < MIN_NOTE) return `Say where the need comes from (at least ${MIN_NOTE} characters)`
  return null
}
export const needBody = (f: NeedForm) => ({
  resourceType: f.resourceType, description: f.description.trim(), matchText: f.matchText.trim() || null, quantity: Number(f.quantity),
  perLearners: f.perLearners.trim() === "" ? null : Number(f.perLearners), sourceNote: f.sourceNote.trim(),
})
export const withdrawProblem = (reason: string): string | null => (reason.trim().length >= MIN_NOTE ? null : `Give a reason of at least ${MIN_NOTE} characters`)
export const needLine = (n: Pick<Need, "quantity" | "perLearners" | "description" | "resourceType" | "matchText">): string =>
  `${n.quantity} × ${n.description} (${TYPES[n.resourceType] ?? n.resourceType})${n.perLearners ? ` for every ${n.perLearners} learners` : " for the cohort"}${n.matchText ? `, looking for "${n.matchText}" in resource names` : ""}`
