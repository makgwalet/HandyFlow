// src/pages/events/wizardOps.ts — pure logic for the create-event wizard: which steps a person sees, what each step needs, and the ordered list of
// calls that turns the draft into an event with its ticket types, vendors and budget lines. The calls run one after another and can be resumed from the one that failed.
import { eventBody, eventFormProblem, type EventForm } from "./eventActions"
import { tierBody, tierProblem } from "./dashboardView"
import { CATEGORIES, categoryText, lineProblem } from "./budgetOps"
import { emailOk, moneyProblem } from "./guestOps"

export type StepId = "details" | "tickets" | "vendors" | "budget" | "review"
export const STEP_LABEL: Record<StepId, string> = { details: "Details", tickets: "Tickets", vendors: "Vendors", budget: "Budget", review: "Review" }
export interface WizardPerms { tickets: boolean; vendors: boolean; budget: boolean }
/** Details and Review always show; the optional steps only to people who may do them, because the server would refuse the calls otherwise. */
export const visibleSteps = (p: WizardPerms): StepId[] => ["details", ...(p.tickets ? ["tickets" as const] : []), ...(p.vendors ? ["vendors" as const] : []), ...(p.budget ? ["budget" as const] : []), "review"]

export const VENDOR_TYPES = ["CATERING", "AV_TECH", "SECURITY", "PHOTOGRAPHY", "TRANSPORT", "DECOR", "ENTERTAINMENT", "OTHER"]
export interface TierDraft { name: string; description: string; price: string; quantity: string; saleStart: string; saleEnd: string }
export interface VendorDraft { vendorType: string; companyName: string; contactName: string; contactPhone: string; contactEmail: string; serviceDescription: string; quotedAmount: string }
export interface LineDraft { category: string; description: string; plannedAmount: string }
export interface Draft { event: EventForm; tiers: TierDraft[]; vendors: VendorDraft[]; lines: LineDraft[] }

export const emptyEvent = (): EventForm => ({ title: "", description: "", eventType: "GENERAL", venueName: "", venueAddress: "", venueCapacity: "", startDatetime: "", endDatetime: "", isFree: true, isPrivate: false, registrationDeadline: "", notes: "" })
export const emptyTier = (): TierDraft => ({ name: "", description: "", price: "", quantity: "", saleStart: "", saleEnd: "" })
export const emptyVendor = (): VendorDraft => ({ vendorType: "CATERING", companyName: "", contactName: "", contactPhone: "", contactEmail: "", serviceDescription: "", quotedAmount: "" })
export const emptyLine = (): LineDraft => ({ category: CATEGORIES[0], description: "", plannedAmount: "" })
export const emptyDraft = (): Draft => ({ event: emptyEvent(), tiers: [], vendors: [], lines: [] })

const nz = (v: string) => (v.trim() === "" ? null : v.trim())
export function vendorProblem(v: VendorDraft): string | null {
  if (!VENDOR_TYPES.includes(v.vendorType)) return "Choose what the vendor supplies."
  if (!v.companyName.trim()) return "Enter the vendor's company name."
  if (!emailOk(v.contactEmail)) return "That vendor email address is not valid."
  return moneyProblem(v.quotedAmount)
}
export const vendorBody = (v: VendorDraft) => ({ vendorType: v.vendorType, companyName: v.companyName.trim(), contactName: nz(v.contactName), contactPhone: nz(v.contactPhone), contactEmail: nz(v.contactEmail), serviceDescription: nz(v.serviceDescription), quotedAmount: v.quotedAmount.trim() === "" ? null : Number(v.quotedAmount), notes: null })
export const lineBody = (l: LineDraft) => ({ category: l.category, description: l.description.trim(), plannedAmount: Number(l.plannedAmount), notes: null })

/** What stops a person leaving this step; an empty optional step is fine. */
export function stepProblem(step: StepId, d: Draft): string | null {
  if (step === "details") return eventFormProblem(d.event)
  const list: [string, string | null][] =
    step === "tickets" ? d.tiers.map((t, i) => [`Ticket type ${i + 1}`, tierProblem(t)])
    : step === "vendors" ? d.vendors.map((v, i) => [`Vendor ${i + 1}`, vendorProblem(v)])
    : step === "budget" ? d.lines.map((l, i) => [`Budget line ${i + 1}`, lineProblem(l)]) : []
  const bad = list.find(([, p]) => p); return bad ? `${bad[0]}: ${bad[1]}` : null
}
/** The first step that is not ready, so Review can send the person back to it. */
export const firstBlockedStep = (steps: StepId[], d: Draft): StepId | null => steps.find(s => s !== "review" && stepProblem(s, d)) ?? null

export const tierPlaces = (d: Draft) => d.tiers.reduce((a, t) => a + (Number(t.quantity) || 0), 0)
export const budgetTotal = (d: Draft) => d.lines.reduce((a, l) => a + (Number(l.plannedAmount) || 0), 0)
/** A heads-up, not a block: the server decides whether over-selling the venue is allowed. */
export function capacityNote(d: Draft): string | null {
  const cap = Number(d.event.venueCapacity); const places = tierPlaces(d)
  return d.event.venueCapacity.trim() !== "" && cap > 0 && places > cap ? `The ticket types add up to ${places} places but the venue holds ${cap}.` : null
}

export interface PlannedCall { label: string; path: (eventId: string) => string; body: unknown }
export function buildPlan(d: Draft, p: WizardPerms): PlannedCall[] {
  return [
    ...(p.tickets ? d.tiers.map(t => ({ label: `Ticket type "${t.name.trim()}"`, path: (id: string) => `/api/v1/events/${id}/tiers`, body: tierBody(t) })) : []),
    ...(p.vendors ? d.vendors.map(v => ({ label: `Vendor "${v.companyName.trim()}"`, path: (id: string) => `/api/v1/events/${id}/vendors`, body: vendorBody(v) })) : []),
    ...(p.budget ? d.lines.map(l => ({ label: `Budget line "${l.description.trim()}" (${categoryText(l.category)})`, path: (id: string) => `/api/v1/events/${id}/budget/lines`, body: lineBody(l) })) : []),
  ]
}
export const createBody = (d: Draft) => eventBody(d.event)

/** Runs the calls in order from `from`. Stops at the first failure and reports how many succeeded so a retry carries on from there instead of duplicating. */
export async function runPlan(plan: PlannedCall[], eventId: string, from: number, post: (path: string, body: unknown) => Promise<unknown>): Promise<{ done: number; error?: unknown }> {
  let done = from
  for (; done < plan.length; done++) {
    try { await post(plan[done].path(eventId), plan[done].body) } catch (error) { return { done, error } }
  }
  return { done }
}
