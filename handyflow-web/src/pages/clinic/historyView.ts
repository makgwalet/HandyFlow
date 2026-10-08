// Pure helpers for the patient background cards (family history, lifestyle and social history, medical aid)
// and the quick sick note / referral actions on the overview.

export const RELATIVES = ["MOTHER", "FATHER", "SIBLING", "GRANDPARENT", "CHILD", "AUNT_UNCLE", "COUSIN", "OTHER"] as const
export const RELATIVE_LABEL: Record<string, string> = { MOTHER: "Mother", FATHER: "Father", SIBLING: "Sibling", GRANDPARENT: "Grandparent", CHILD: "Child", AUNT_UNCLE: "Aunt or uncle", COUSIN: "Cousin", OTHER: "Other relative" }
export const SMOKING = ["UNKNOWN", "NEVER", "FORMER", "CURRENT"] as const
export const ALCOHOL = ["UNKNOWN", "NONE", "OCCASIONAL", "REGULAR", "HEAVY"] as const
export const SUBSTANCE = ["UNKNOWN", "NONE", "PAST", "CURRENT"] as const
export const CHOICE_LABEL: Record<string, string> = { UNKNOWN: "Not recorded", NEVER: "Never", FORMER: "Former", CURRENT: "Current", NONE: "None", OCCASIONAL: "Occasional", REGULAR: "Regular", HEAVY: "Heavy", PAST: "Past" }

export interface FamilyForm { relative: string; conditionName: string; ageAtOnset: string; notes: string }
export const EMPTY_FAMILY: FamilyForm = { relative: "MOTHER", conditionName: "", ageAtOnset: "", notes: "" }

/** The reason a family entry cannot be saved yet, or null. */
export function familyProblem(f: FamilyForm): string | null {
  if (!f.relative) return "Choose the relative."
  if (!f.conditionName.trim()) return "Enter the condition."
  if (f.ageAtOnset.trim() !== "") {
    const n = Number(f.ageAtOnset)
    if (!Number.isInteger(n) || n < 0 || n > 120) return "Age at onset must be a whole number from 0 to 120."
  }
  return null
}

export const familyBody = (f: FamilyForm) => ({
  relative: f.relative, conditionName: f.conditionName.trim(),
  ageAtOnset: f.ageAtOnset.trim() === "" ? null : Number(f.ageAtOnset), notes: f.notes.trim() || null,
})

export interface SocialForm { smokingStatus: string; alcoholUse: string; substanceUse: string; occupation: string; livingSituation: string; physicalActivity: string; notes: string }
export const EMPTY_SOCIAL: SocialForm = { smokingStatus: "UNKNOWN", alcoholUse: "UNKNOWN", substanceUse: "UNKNOWN", occupation: "", livingSituation: "", physicalActivity: "", notes: "" }

export function socialForm(s: Partial<SocialForm> | null | undefined): SocialForm {
  return { ...EMPTY_SOCIAL, ...Object.fromEntries(Object.entries(s ?? {}).filter(([k, v]) => k in EMPTY_SOCIAL && v != null)) } as SocialForm
}

/** The lines worth showing: only what has been recorded, so "unknown" never reads as an answer. */
export function socialLines(s: Partial<SocialForm> | null | undefined): [string, string][] {
  const f = socialForm(s)
  const out: [string, string][] = []
  if (f.smokingStatus !== "UNKNOWN") out.push(["Smoking", CHOICE_LABEL[f.smokingStatus] ?? f.smokingStatus])
  if (f.alcoholUse !== "UNKNOWN") out.push(["Alcohol", CHOICE_LABEL[f.alcoholUse] ?? f.alcoholUse])
  if (f.substanceUse !== "UNKNOWN") out.push(["Other substances", CHOICE_LABEL[f.substanceUse] ?? f.substanceUse])
  if (f.occupation.trim()) out.push(["Occupation", f.occupation.trim()])
  if (f.livingSituation.trim()) out.push(["Living situation", f.livingSituation.trim()])
  if (f.physicalActivity.trim()) out.push(["Physical activity", f.physicalActivity.trim()])
  if (f.notes.trim()) out.push(["Notes", f.notes.trim()])
  return out
}

export const socialBody = (f: SocialForm) => ({
  smokingStatus: f.smokingStatus, alcoholUse: f.alcoholUse, substanceUse: f.substanceUse,
  occupation: f.occupation.trim() || null, livingSituation: f.livingSituation.trim() || null,
  physicalActivity: f.physicalActivity.trim() || null, notes: f.notes.trim() || null,
})

export interface AidForm { schemeName: string; planName: string; memberNumber: string; dependentCode: string; principalMember: string; schemeContactPhone: string }
export const EMPTY_AID: AidForm = { schemeName: "", planName: "", memberNumber: "", dependentCode: "", principalMember: "", schemeContactPhone: "" }

export function aidProblem(f: AidForm): string | null {
  if (!f.schemeName.trim()) return "Enter the medical scheme."
  if (!f.memberNumber.trim()) return "Enter the member number."
  if (f.dependentCode.trim().length > 10) return "The dependant code is too long (10 characters at most)."
  return null
}

export const aidBody = (f: AidForm) => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, (v as string).trim() || null]))

export function aidForm(a: Partial<AidForm> | null | undefined): AidForm {
  return { ...EMPTY_AID, ...Object.fromEntries(Object.entries(a ?? {}).filter(([k, v]) => k in EMPTY_AID && v != null)) } as AidForm
}

// ── Quick sick note and referral ─────────────────────────────────────────────

export interface ConsultationRef { id: string; consultedAt: string }

/** Newest first. The two documents are written for a consultation, so the newest is the one offered. */
export const newestFirst = <T extends ConsultationRef>(list: T[]): T[] =>
  [...list].sort((a, b) => String(b.consultedAt).localeCompare(String(a.consultedAt)))

export interface CertForm { consultationId: string; unfitFrom: string; unfitTo: string; notes: string }

export function certProblem(f: CertForm): string | null {
  if (!f.consultationId) return "Choose the consultation this certificate belongs to."
  if (f.unfitFrom && f.unfitTo && f.unfitTo < f.unfitFrom) return "The last day cannot be before the first day."
  return null
}

export function certParams(f: CertForm): URLSearchParams {
  const p = new URLSearchParams()
  if (f.unfitFrom) p.set("unfitFrom", f.unfitFrom)
  if (f.unfitTo) p.set("unfitTo", f.unfitTo)
  if (f.notes.trim()) p.set("notes", f.notes.trim())
  return p
}
