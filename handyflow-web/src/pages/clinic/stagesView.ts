// Which consultation stages a visit type requires before signing (patch 0194; server rules in VisitStageRules, CLINIC-DEC-010 and 012).
export const STAGES = [
  { id: "SYMPTOMS", label: "Symptoms" },
  { id: "EXAMINATION", label: "Examination" },
  { id: "DIAGNOSIS", label: "Diagnosis" },
  { id: "PLAN", label: "Plan" },
] as const

export type Selection = Record<string, boolean>

/** The visit types the clinic books today. The server accepts any name, so a practice's own types can be added later. */
export const VISIT_TYPES = ["CONSULTATION", "FOLLOW_UP", "PROCEDURE", "EMERGENCY", "CHECKUP", "ANTENATAL", "RESULTS_REVIEW"] as const

export const visitTypeLabel = (v: string): string => v.replace(/_/g, " ").toLowerCase().replace(/^./, c => c.toUpperCase())

/** What the server answered, as one tick per stage. An answer that lists nothing means the default: Symptoms and Diagnosis. */
export function fromApi(data: unknown): Selection {
  const list = (data as { stages?: unknown } | null)?.stages
  const out: Selection = { SYMPTOMS: true, EXAMINATION: false, DIAGNOSIS: true, PLAN: false }
  if (!Array.isArray(list) || list.length === 0) return out
  for (const s of STAGES) out[s.id] = false
  for (const x of list as { stage?: string; required?: boolean }[]) {
    const id = x?.stage?.toUpperCase()
    if (id && id in out) out[id] = x.required === true
  }
  return out
}

/** All four stages, in clinical order, as the server wants them. */
export const toRequest = (sel: Selection): { stages: { stage: string; required: boolean }[] } =>
  ({ stages: STAGES.map(s => ({ stage: s.id, required: sel[s.id] === true })) })

export const sameSelection = (a: Selection, b: Selection): boolean => STAGES.every(s => (a[s.id] === true) === (b[s.id] === true))

export function sourceNote(source: string | undefined): string {
  switch (source) {
    case "TENANT": return "This practice has its own setting for this visit type."
    case "PLATFORM": return "Using the platform's setting. Save to give this practice its own."
    default: return "Nothing is set for this visit type, so Symptoms and Diagnosis are required."
  }
}

/** A plain warning when the choice requires less than the default. It does not block: the practice decides. */
export function lessThanDefault(sel: Selection): string | null {
  const missing = ["SYMPTOMS", "DIAGNOSIS"].filter(id => sel[id] !== true).map(id => STAGES.find(s => s.id === id)!.label)
  return missing.length === 0 ? null
    : `A visit of this type could be signed without ${missing.join(" and ")}. The default requires both; check this with your clinical lead.`
}
