// Allowed values and checks for the practice's service catalogue. The server enforces every rule; this keeps
// obviously wrong input from being sent and says what a version still needs before review.

export const CATEGORIES: [string, string][] = [
  ["ONBOARDING", "Onboarding"], ["CIPC", "CIPC"], ["SARS", "SARS"], ["POPIA", "POPIA"], ["PAIA", "PAIA"], ["BBBEE", "B-BBEE advisory"], ["OHS", "OHS"],
  ["EMPLOYMENT", "Employment"], ["LICENCE", "Licence management"], ["TENDER_READINESS", "Tender readiness"], ["MONITORING", "Ongoing monitoring"],
  ["AUDIT_PREPARATION", "Audit preparation"], ["OTHER", "Other"],
]
export const PRICING_MODELS: [string, string][] = [
  ["FIXED", "Fixed fee"], ["HOURLY", "Hourly"], ["PER_DOCUMENT", "Per document"], ["PER_REGISTRATION", "Per registration"], ["PER_SITE", "Per site"],
  ["PER_EMPLOYEE", "Per employee"], ["RETAINER", "Monthly retainer"], ["PROJECT", "Project or milestones"], ["HYBRID", "Hybrid"],
]
export const BILLING_TRIGGERS: [string, string][] = [
  ["ON_ACCEPTANCE", "When the client accepts"], ["ON_DELIVERY", "When it is delivered"], ["MONTHLY", "Every month"], ["ON_MILESTONE", "At each milestone"],
]
export const ITEM_KINDS: [string, string][] = [["DELIVERABLE", "Deliverable"], ["CHECKLIST", "Checklist step"], ["DOCUMENT", "Document needed from the client"]]

/** How each kind is headed when its items are listed. */
export const ITEM_HEADINGS: Record<string, string> = { DELIVERABLE: "Deliverables", CHECKLIST: "Checklist", DOCUMENT: "Documents needed from the client" }

export type ServiceItem = { kind: string; text: string; mandatory: boolean }
export type ServiceDraft = {
  name: string; category: string; description: string; applicabilityNote: string; exclusions: string; estimatedHours: string; slaDays: string
  responsibleRole: string; requiresReview: boolean; pricingModel: string; billingTrigger: string; changeSummary: string; items: ServiceItem[]
}

export function hoursProblem(raw: string): string | null {
  const t = raw.trim()
  if (!t) return null
  const n = Number(t)
  if (!Number.isFinite(n) || n <= 0 || n > 2000) return "Hours must be more than 0 and at most 2000"
  return null
}

export function slaProblem(raw: string): string | null {
  const t = raw.trim()
  if (!t) return null
  const n = Number(t)
  if (!Number.isInteger(n) || n < 1 || n > 365) return "Turnaround must be a whole number of days from 1 to 365"
  return null
}

export function itemProblem(i: ServiceItem): string | null {
  const t = i.text.trim()
  if (t.length < 3) return "Describe it in at least 3 characters"
  if (t.length > 300) return "At most 300 characters"
  return null
}

export function duplicateItem(items: ServiceItem[]): string | null {
  const seen = new Set<string>()
  for (const i of items) {
    const k = `${i.kind}|${i.text.trim().toLowerCase()}`
    if (seen.has(k)) return `"${i.text.trim()}" is listed twice`
    seen.add(k)
  }
  return null
}

/** What a version still needs before it can go for review. Mirrors the server. */
export function missingForReview(d: ServiceDraft, versionNumber: number): string[] {
  const m: string[] = []
  if (!d.name.trim()) m.push("name")
  if (!d.category) m.push("category")
  if (!d.pricingModel) m.push("pricing model")
  if (!d.billingTrigger) m.push("billing trigger")
  if (!d.responsibleRole) m.push("responsible role")
  if (!d.estimatedHours.trim()) m.push("estimated hours")
  if (!d.items.some(i => i.kind === "DELIVERABLE" && i.text.trim())) m.push("at least one deliverable")
  if (versionNumber > 1 && !d.changeSummary.trim()) m.push("what changed")
  return m
}

/** Anything that stops the draft being saved at all. */
export function saveProblem(d: ServiceDraft): string | null {
  return hoursProblem(d.estimatedHours) ?? slaProblem(d.slaDays) ?? d.items.map(itemProblem).find(Boolean) ?? duplicateItem(d.items) ?? null
}

export const num = (s: string) => (s.trim() === "" ? null : Number(s))

export function toBody(d: ServiceDraft) {
  return {
    name: d.name, category: d.category || null, description: d.description, applicabilityNote: d.applicabilityNote, exclusions: d.exclusions,
    estimatedHours: num(d.estimatedHours), slaDays: num(d.slaDays), responsibleRole: d.responsibleRole || null, requiresReview: d.requiresReview,
    pricingModel: d.pricingModel || null, billingTrigger: d.billingTrigger || null, changeSummary: d.changeSummary,
    items: d.items.map(i => ({ kind: i.kind, text: i.text.trim(), mandatory: i.mandatory })),
  }
}
