// The patient profile page: sections, form state, and what is still missing.
export type SectionId = "identity" | "contact" | "address" | "emergency" | "scheme" | "family" | "consent"

export const SECTIONS: { id: SectionId; label: string; hint: string }[] = [
  { id: "identity", label: "Identity", hint: "Name, ID, date of birth, sex" },
  { id: "contact", label: "Contact", hint: "Phone, email, how to reach them" },
  { id: "address", label: "Address", hint: "Where they live" },
  { id: "emergency", label: "Emergency contacts", hint: "Who to call" },
  { id: "scheme", label: "Medical scheme", hint: "Medical aid or self-pay" },
  { id: "family", label: "Family", hint: "Linked family members" },
  { id: "consent", label: "Consent", hint: "What the patient agreed to" },
]

export interface ChecklistItem { key: string; label: string; section: string; done: boolean }
export interface Completeness { done: number; total: number; percent: number; items: ChecklistItem[] }

export interface ProfileForm {
  title: string; idType: string; nationality: string; preferredLanguage: string; preferredContact: string
  addressLine1: string; addressLine2: string; suburb: string; city: string; province: string; postalCode: string
  emergencyRelationship: string; secondaryContactName: string; secondaryContactPhone: string; secondaryContactRelationship: string
  paymentType: string
}

export const EMPTY_PROFILE: ProfileForm = {
  title: "", idType: "", nationality: "", preferredLanguage: "", preferredContact: "",
  addressLine1: "", addressLine2: "", suburb: "", city: "", province: "", postalCode: "",
  emergencyRelationship: "", secondaryContactName: "", secondaryContactPhone: "", secondaryContactRelationship: "", paymentType: "",
}

/** The server sends null for what is not recorded; a form wants strings. */
export function toForm(p: Partial<Record<keyof ProfileForm, string | null>> | null | undefined): ProfileForm {
  const out = { ...EMPTY_PROFILE }
  for (const k of Object.keys(EMPTY_PROFILE) as (keyof ProfileForm)[]) out[k] = p?.[k] ?? ""
  return out
}

/** The whole profile is sent on every save, so a cleared field is cleared. */
export function toRequest(f: ProfileForm): Record<keyof ProfileForm, string | null> {
  const out = {} as Record<keyof ProfileForm, string | null>
  for (const k of Object.keys(f) as (keyof ProfileForm)[]) out[k] = f[k].trim() === "" ? null : f[k].trim()
  return out
}

export const TITLES = ["Mr", "Mrs", "Ms", "Miss", "Dr", "Prof", "Mx"]
export const PROVINCES = ["Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal", "Limpopo", "Mpumalanga", "Northern Cape", "North West", "Western Cape"]
export const LANGUAGES = ["English", "Afrikaans", "isiZulu", "isiXhosa", "Sesotho", "Setswana", "Sepedi", "Xitsonga", "siSwati", "Tshivenda", "isiNdebele", "Other"]
export const ID_TYPES: [string, string][] = [["SA_ID", "South African ID"], ["PASSPORT", "Passport"], ["OTHER", "Other"]]
export const CONTACTS: [string, string][] = [["PHONE", "Phone call"], ["SMS", "SMS"], ["WHATSAPP", "WhatsApp"], ["EMAIL", "Email"]]
export const SEXES: [string, string][] = [["MALE", "Male"], ["FEMALE", "Female"], ["INTERSEX", "Intersex"], ["UNKNOWN", "Not recorded"]]
export const RELATIONSHIPS = ["Spouse", "Parent", "Child", "Sibling", "Guardian", "Friend", "Other"]

export function sectionProgress(c: Completeness | undefined, section: SectionId): { done: number; total: number } {
  const items = (c?.items ?? []).filter(i => i.section === section)
  return { done: items.filter(i => i.done).length, total: items.length }
}

export const missing = (c: Completeness | undefined) => (c?.items ?? []).filter(i => !i.done)

/** The first section with something missing, so "Complete profile" opens where the work is. */
export function firstIncomplete(c: Completeness | undefined): SectionId {
  const m = missing(c)[0]
  const s = SECTIONS.find(x => x.id === m?.section)
  return s ? s.id : "identity"
}

export function postalProblem(v: string): string | null {
  return v.trim() === "" || /^\d{4}$/.test(v.trim()) ? null : "A postal code is four digits"
}

export function profileSummary(c: Completeness | undefined): string {
  if (!c) return ""
  const m = missing(c)
  if (m.length === 0) return "Profile complete"
  return `${m.length} thing${m.length === 1 ? "" : "s"} missing: ${m.slice(0, 3).map(i => i.label.toLowerCase()).join(", ")}${m.length > 3 ? "…" : ""}`
}
