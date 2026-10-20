// Inspection checklist rules the screen applies before it lets someone press Complete (the server checks them again).
export type Outcome = "PASS" | "FAIL" | "NOT_APPLICABLE"
export interface Result { id: string; section: string; label: string; sortOrder: number; required: boolean; photoRequiredOnFail: boolean; severity: string; outcome: string | null; comment: string | null; photoIds: string[] }
export interface Detail { signatureRequired: boolean; signatureRecorded: boolean; results: Result[] }

export const OUTCOME_LABEL: Record<Outcome, string> = { PASS: "Pass", FAIL: "Fail", NOT_APPLICABLE: "N/A" }

/** Checklist items grouped by section, in the order they were set up. */
export function bySection(results: Result[]): { section: string; items: Result[] }[] {
  const out: { section: string; items: Result[] }[] = []
  for (const r of [...results].sort((a, b) => a.sortOrder - b.sortOrder)) {
    const last = out.find(g => g.section === r.section)
    if (last) last.items.push(r); else out.push({ section: r.section, items: [r] })
  }
  return out
}

/** What still stops the inspection being completed; empty when it is ready. */
export function completionProblems(d: Detail): string[] {
  const p: string[] = []
  const unanswered = d.results.filter(r => r.required && !r.outcome)
  if (unanswered.length) p.push(`${unanswered.length} required ${unanswered.length === 1 ? "item is" : "items are"} not answered yet.`)
  const needPhoto = d.results.filter(r => r.outcome === "FAIL" && r.photoRequiredOnFail && r.photoIds.length === 0)
  if (needPhoto.length) p.push(`A photo is needed for: ${needPhoto.map(r => r.label).join(", ")}.`)
  if (d.signatureRequired && !d.signatureRecorded) p.push("The inspector's signature is needed.")
  return p
}

export const failedCount = (results: Result[]) => results.filter(r => r.outcome === "FAIL").length
export const answeredCount = (results: Result[]) => results.filter(r => r.outcome).length

export interface StartForm { vehicleId: string; templateId: string; odometer: string; inspectorName: string }
export function startProblem(f: StartForm): string | null {
  if (!f.vehicleId) return "Choose the vehicle."
  if (!f.templateId) return "Choose the inspection."
  if (f.odometer !== "" && !(Number(f.odometer) >= 0)) return "The odometer must be zero or more."
  return null
}
export const startBody = (f: StartForm) => ({ templateId: f.templateId, odometer: f.odometer === "" ? null : Number(f.odometer), inspectorName: f.inspectorName.trim() || null })
