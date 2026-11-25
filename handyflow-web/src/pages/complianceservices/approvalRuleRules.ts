// Wording and checks for quote approval rules. The server enforces them; these save a round trip.

export type ApprovalRule = {
  id: string; name: string; minFees: number | null; serviceCode: string | null; riskRating: string | null; clientId: string | null; clientName: string | null
  requiredApprover: string; reason: string; status: string; proposedBy: string; activatedBy: string | null; retireNote: string | null
}

export const APPROVERS: Record<string, string> = {
  COMPLIANCE_MANAGER: "Compliance manager", PRACTICE_OWNER: "Practice owner", PRACTICE_OWNER_WITH_EXCEPTION: "Practice owner, with a recorded exception",
}
export const approverLabel = (a: string) => APPROVERS[a] ?? a

export const RISKS: Record<string, string> = { LOW: "Low", MEDIUM: "Medium", HIGH: "High" }

export const STATUS: Record<string, string> = { PROPOSED: "Waiting for a second administrator", ACTIVE: "In force", RETIRED: "Retired" }
export const statusLabel = (s: string) => STATUS[s] ?? s

export const money = (n: number) => `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** The conditions of a rule in plain words. */
export function when(r: Pick<ApprovalRule, "minFees" | "serviceCode" | "riskRating" | "clientName" | "clientId">): string {
  const parts: string[] = []
  if (r.minFees != null) parts.push(`professional fees of ${money(r.minFees)} or more`)
  if (r.serviceCode) parts.push(`the quote includes ${r.serviceCode}`)
  if (r.riskRating) parts.push(`the client's risk rating is ${RISKS[r.riskRating]?.toLowerCase() ?? r.riskRating}`)
  if (r.clientId) parts.push(`the client is ${r.clientName ?? "one particular client"}`)
  return parts.join(" and ")
}

export type RuleForm = { name: string; minFees: string; serviceCode: string; riskRating: string; clientId: string; requiredApprover: string; reason: string }
export const emptyForm: RuleForm = { name: "", minFees: "", serviceCode: "", riskRating: "", clientId: "", requiredApprover: "COMPLIANCE_MANAGER", reason: "" }

export function formProblem(f: RuleForm): string | null {
  if (f.name.trim().length < 3) return "Give the rule a name of at least 3 characters"
  const fees = f.minFees.trim() === "" ? null : Number(f.minFees)
  if (fees !== null && (!Number.isFinite(fees) || fees <= 0)) return "The fee amount must be more than zero"
  if (fees === null && !f.serviceCode.trim() && !f.riskRating && !f.clientId) return "Say when the rule applies: a fee amount, a service, a risk rating or a client"
  if (f.reason.trim().length < 10) return "Give the reason for the rule, at least 10 characters"
  return null
}

export const toBody = (f: RuleForm) => ({
  name: f.name.trim(), minFees: f.minFees.trim() === "" ? null : Number(f.minFees), serviceCode: f.serviceCode.trim() || null, riskRating: f.riskRating || null,
  clientId: f.clientId || null, requiredApprover: f.requiredApprover, reason: f.reason.trim(),
})

export const noteProblem = (n: string): string | null => (n.trim().length >= 10 ? null : "Give a reason of at least 10 characters")
