// Wording and checks for funders and the enrolments they pay for. The server enforces every rule.
// A funding link records who is paying. It holds no amounts, and it does not mean any funding was approved or paid.

export type Funder = {
  id: string; funderNo: string; name: string; funderType: string; clientId: string | null; clientName: string | null; contactName: string | null; contactEmail: string | null
  contactPhone: string | null; note: string | null; status: string; retireReason: string | null; activeLinks: number
}
export type FundingLink = {
  id: string; enrolmentId: string; enrolmentNo: string; learnerName: string | null; funderId: string; funderNo: string; funderName: string; funderType: string
  fundedFrom: string; agreementRef: string | null; funderReference: string | null; note: string | null; status: string; endedOn: string | null; endReason: string | null
}
export type EnrolmentFunding = { links: FundingLink[]; summary: string }

export const TYPES: Record<string, string> = { SELF: "Self-funded", EMPLOYER: "Employer", SETA: "SETA", GRANT: "Grant", GOVERNMENT: "Government", OTHER: "Other" }
export const FUNDING_NOTE = "A funder link records who is paying. It holds no amounts, and it does not mean any funding was approved or paid."
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")
export const MIN_REASON = 10

const long = (s: string, n: number) => s.trim().length >= n
export const reasonProblem = (r: string): string | null => (long(r, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`)

export type FunderForm = { name: string; funderType: string; clientId: string; contactName: string; contactEmail: string; contactPhone: string; note: string }
export const emptyFunder: FunderForm = { name: "", funderType: "", clientId: "", contactName: "", contactEmail: "", contactPhone: "", note: "" }
export const funderForm = (f: Funder): FunderForm => ({
  name: f.name, funderType: f.funderType, clientId: f.clientId ?? "", contactName: f.contactName ?? "", contactEmail: f.contactEmail ?? "", contactPhone: f.contactPhone ?? "", note: f.note ?? "",
})
export function funderProblem(f: FunderForm): string | null {
  if (!f.funderType) return "Choose the kind of funder"
  if (!long(f.name, 2)) return "Give the name of the funder"
  const e = f.contactEmail.trim()
  if (e && (!e.includes("@") || e.startsWith("@") || e.endsWith("@"))) return "That email address does not look right"
  return null
}
const blank = (s: string) => (s.trim() ? s.trim() : null)
export const funderBody = (f: FunderForm) => ({
  name: f.name.trim(), funderType: f.funderType, clientId: f.clientId || null, contactName: blank(f.contactName), contactEmail: blank(f.contactEmail), contactPhone: blank(f.contactPhone), note: blank(f.note),
})

export type LinkForm = { funderId: string; fundedFrom: string; agreementRef: string; funderReference: string; note: string }
export const emptyLink = (today: string): LinkForm => ({ funderId: "", fundedFrom: today, agreementRef: "", funderReference: "", note: "" })
export function linkProblem(f: LinkForm, enrolmentStart: string | null, liveFunderIds: string[], today: string): string | null {
  if (!f.funderId) return "Choose the funder"
  if (!f.fundedFrom) return "Enter the day the funding starts"
  if (f.fundedFrom > addYears(today, 1)) return "The day is more than a year ahead"
  if (enrolmentStart && f.fundedFrom < addYears(enrolmentStart, -1)) return "The day is more than a year before the enrolment started"
  if (liveFunderIds.includes(f.funderId)) return "This enrolment is already linked to that funder"
  return null
}
export const linkBody = (enrolmentId: string, f: LinkForm) => ({
  enrolmentId, funderId: f.funderId, fundedFrom: f.fundedFrom, agreementRef: blank(f.agreementRef), funderReference: blank(f.funderReference), note: blank(f.note),
})
function addYears(iso: string, n: number): string {
  const [y, m, d] = iso.split("-")
  return `${String(Number(y) + n).padStart(4, "0")}-${m}-${d}`
}

export function endProblem(on: string, from: string, reason: string, today: string): string | null {
  if (!on) return "Enter the day the funding ended"
  if (on > today) return "The day cannot be in the future"
  if (on < from) return "It cannot end before it started"
  return reasonProblem(reason)
}

export function linkLine(l: Pick<FundingLink, "fundedFrom" | "agreementRef" | "funderReference" | "status" | "endedOn" | "endReason">): string {
  const parts = [`From ${l.fundedFrom}`]
  if (l.agreementRef) parts.push(`agreement ${l.agreementRef}`)
  if (l.funderReference) parts.push(`funder reference ${l.funderReference}`)
  if (l.status === "ENDED") parts.push(`ended ${l.endedOn}: ${l.endReason ?? "—"}`)
  return parts.join(" · ")
}
