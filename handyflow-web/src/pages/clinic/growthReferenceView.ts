// Review of growth reference sets (CLINIC-DEC-019, 020; server rules in the growth reference service). A set reaches clinicians only when
// ACTIVE; until then charts say the data is not clinically approved. The server checks who may do each step; this only hides buttons
// whose click would be refused.
export interface RefSet {
  id: string; measure: string; sex: string; title: string; source: string; sourceVersion: string
  minAgeMonths: number; maxAgeMonths: number; status: string; pointCount: number
  createdBy?: string | null; reviewedBy?: string | null; reviewedAt?: string | null; approvedBy?: string | null; approvedAt?: string | null
}

export type RefAction = "submit" | "approve" | "send-back" | "activate" | "retire"

const STATUS: Record<string, string> = {
  DRAFT: "Draft", CLINICAL_REVIEW: "In clinical review", APPROVED: "Approved, not yet active", ACTIVE: "Active", RETIRED: "Retired",
}
export const statusLabel = (s: string) => STATUS[s] ?? s

const MEASURE: Record<string, string> = { WEIGHT_FOR_AGE: "Weight for age", LENGTH_FOR_AGE: "Length/height for age", HEAD_CIRC_FOR_AGE: "Head circumference for age", WEIGHT_FOR_LENGTH: "Weight for length/height", BMI_FOR_AGE: "BMI for age" }
export const measureLabel = (m: string) => MEASURE[m] ?? m.replace(/_/g, " ").toLowerCase().replace(/^./, c => c.toUpperCase())
export const sexLabel = (s: string) => (s === "M" || s === "MALE" ? "Boys" : s === "F" || s === "FEMALE" ? "Girls" : s)

export function ageRange(s: Pick<RefSet, "minAgeMonths" | "maxAgeMonths">): string {
  const f = (m: number) => (m % 12 === 0 && m >= 24 ? `${m / 12} years` : `${m} months`)
  return `${f(s.minAgeMonths)} to ${f(s.maxAgeMonths)}`
}

export interface Can { admin: boolean; approve: boolean }

/** The steps this person can take on this set now. */
export function actionsFor(s: Pick<RefSet, "status">, can: Can): RefAction[] {
  const out: RefAction[] = []
  if (s.status === "DRAFT" && can.admin) out.push("submit")
  if (s.status === "CLINICAL_REVIEW" && can.approve) out.push("approve")
  if (["CLINICAL_REVIEW", "APPROVED"].includes(s.status) && can.approve) out.push("send-back")
  if (s.status === "APPROVED" && can.approve) out.push("activate")
  if (s.status === "ACTIVE" && can.approve) out.push("retire")
  return out
}

export const ACTION_LABEL: Record<RefAction, string> = {
  submit: "Send for review", approve: "Confirm it matches its source", "send-back": "Send back to draft", activate: "Activate", retire: "Retire",
}

/** What the person must understand before the step is taken. */
export const ACTION_WARNING: Partial<Record<RefAction, string>> = {
  approve: "You are confirming that these numbers match the source cited. The person who activates it must be someone else.",
  activate: "From now on clinicians see z-scores and percentiles from this set. It replaces the active set for the same measure and sex. A different person from the reviewer must do this.",
  retire: "Charts for this measure go back to \"data not clinically approved\" until another set is activated.",
  "send-back": "The set goes back to draft and any review or approval is lost.",
}

export const order = (list: RefSet[]): RefSet[] => {
  const rank: Record<string, number> = { CLINICAL_REVIEW: 0, APPROVED: 1, DRAFT: 2, ACTIVE: 3, RETIRED: 4 }
  return [...list].sort((a, b) => (rank[a.status] ?? 9) - (rank[b.status] ?? 9) || a.measure.localeCompare(b.measure) || a.sex.localeCompare(b.sex) || a.title.localeCompare(b.title))
}
