// Pure helpers for Fuel setup (operating profile, divisions, legal entities).
export type Division = "WHOLESALE_SUPPLY" | "DISTRIBUTION" | "RETAIL_FORECOURT" | "TRUCK_STOP"

export const DIVISIONS: { id: Division; label: string; blurb: string }[] = [
  { id: "WHOLESALE_SUPPLY", label: "Wholesale supply", blurb: "Buy fuel from suppliers and sell to account customers: purchase orders, orders, pricing, invoicing." },
  { id: "DISTRIBUTION", label: "Distribution", blurb: "Deliver fuel to customers' sites with your own tankers and drivers: planning, deliveries, driver app." },
  { id: "RETAIL_FORECOURT", label: "Retail forecourt", blurb: "Run service stations: pumps, shifts, cash-up and forecourt sales." },
  { id: "TRUCK_STOP", label: "Truck stop", blurb: "Heavy-vehicle stops with fleet accounts and services. Set-up only for now; operations come in a later release." },
]

export const SIZES = [{ id: "SMALL", label: "Small" }, { id: "MEDIUM", label: "Medium" }, { id: "LARGE", label: "Large" }, { id: "ENTERPRISE", label: "Enterprise" }]
export const ORGS = [{ id: "ONE_ENTITY", label: "One legal entity" }, { id: "MULTIPLE_ENTITIES", label: "Multiple legal entities" }, { id: "NOT_CONFIGURED", label: "Not decided yet" }]

export interface DivisionView { division: Division; label: string; active: boolean; deactivationReason?: string | null }
export interface ChecklistItem { key: string; label: string; done: boolean }
export interface Profile {
  status: "NOT_STARTED" | "IN_PROGRESS" | "ACTIVE"; legacyMode: boolean; businessSize?: string | null; orgStructure?: string | null
  wizardStep: number; wizardAnswers?: string | null; activeDivisions: Division[]; divisions: DivisionView[]; checklist: ChecklistItem[]
}
export interface WizardState { step: number; divisions: Division[]; businessSize: string; orgStructure: string }

export const EMPTY_WIZARD: WizardState = { step: 0, divisions: [], businessSize: "", orgStructure: "" }
export const LAST_STEP = 2

/** Rebuilds the wizard from a saved profile; corrupt or missing answers fall back to an empty selection. */
export function wizardFromProfile(p: Profile): WizardState {
  let divisions: Division[] = []
  try {
    const parsed = JSON.parse(p.wizardAnswers ?? "{}")
    if (Array.isArray(parsed.divisions)) divisions = parsed.divisions.filter((d: unknown) => DIVISIONS.some(x => x.id === d))
  } catch { /* ignore */ }
  return { step: Math.min(Math.max(p.wizardStep ?? 0, 0), LAST_STEP), divisions, businessSize: p.businessSize ?? "", orgStructure: p.orgStructure ?? "" }
}

export const toWizardRequest = (w: WizardState) => ({
  businessSize: w.businessSize || null, orgStructure: w.orgStructure || null, wizardStep: w.step, wizardAnswers: JSON.stringify({ divisions: w.divisions }),
})

export function toggleDivision(list: Division[], d: Division): Division[] { return list.includes(d) ? list.filter(x => x !== d) : [...list, d] }

/** Returns an error for the step the user is on, or null when they may continue. */
export function validateStep(w: WizardState): string | null {
  if (w.step === 0 && w.divisions.length === 0) return "Choose at least one division."
  if (w.step === 1 && !w.businessSize) return "Choose your business size."
  if (w.step === 1 && !w.orgStructure) return "Say how your business is structured."
  return null
}

export interface LegalEntity { id: string; name: string; registrationNo?: string | null; taxNo?: string | null; vatNo?: string | null; address?: string | null; status: string; depotCount: number }
export interface EntityForm { name: string; registrationNo: string; taxNo: string; vatNo: string; address: string }
export const emptyEntity = (): EntityForm => ({ name: "", registrationNo: "", taxNo: "", vatNo: "", address: "" })
export const entityToForm = (e: LegalEntity): EntityForm => ({ name: e.name, registrationNo: e.registrationNo ?? "", taxNo: e.taxNo ?? "", vatNo: e.vatNo ?? "", address: e.address ?? "" })
export function validateEntity(f: EntityForm): string | null { return f.name.trim() ? null : "Enter the legal entity's name." }
export const toEntityRequest = (f: EntityForm) => ({
  name: f.name.trim(), registrationNo: f.registrationNo.trim() || null, taxNo: f.taxNo.trim() || null, vatNo: f.vatNo.trim() || null, address: f.address.trim() || null,
})
