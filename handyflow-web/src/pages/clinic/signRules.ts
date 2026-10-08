// What the doctor sees before signing, and when signing is allowed (CLINIC-DEC-010, 011, 012).
// Which stages are required depends on the visit type (Symptoms, Examination, Diagnosis, Plan); an unconfigured visit
// type requires Symptoms and Diagnosis. The server enforces the same rule (SignRules.java, VisitStageRules.java);
// this only shows it early so nobody finds out at the last click.
import type { WizardStep } from "./consultWizard"
import { rxGaps, type StepRx } from "./consultSteps"

/** Which stages must be filled in (or overridden with a reason) before this visit type can be signed. */
export interface RequiredStages { symptoms: boolean; examination: boolean; diagnose: boolean; plan: boolean }
export const DEFAULT_REQUIRED: RequiredStages = { symptoms: true, examination: false, diagnose: true, plan: false }

const STAGE_KEY: Record<string, keyof RequiredStages> = { SYMPTOMS: "symptoms", EXAMINATION: "examination", DIAGNOSIS: "diagnose", PLAN: "plan" }

/** The visit-type stages as the server returns them; anything unusable means the default. */
export function requiredFromApi(data: unknown): RequiredStages {
  const list = (data as { stages?: unknown } | null)?.stages
  if (!Array.isArray(list) || list.length === 0) return DEFAULT_REQUIRED
  const out: RequiredStages = { symptoms: false, examination: false, diagnose: false, plan: false }
  for (const s of list as { stage?: string; required?: boolean }[]) {
    const key = s?.stage ? STAGE_KEY[s.stage.toUpperCase()] : undefined
    if (key) out[key] = s.required === true
  }
  return out
}

export interface SignInput {
  chiefComplaint: string; diagnosis: string; icd10Codes: string
  /** Only needed when the visit type requires them. */
  examination?: string; hasVitals?: boolean; hasPlan?: boolean
}
export interface SignGap { step: "symptoms" | "examination" | "diagnose" | "plan"; label: string; message: string; goTo: WizardStep }

const has = (s: string) => s.trim().length > 0
export const OVERRIDE_REASON_MAX = 500

/** Required stages still empty, in clinical order. Mirrors the server rule. */
export function signGaps(n: SignInput, req: RequiredStages = DEFAULT_REQUIRED): SignGap[] {
  const gaps: SignGap[] = []
  if (req.symptoms && !has(n.chiefComplaint)) gaps.push({ step: "symptoms", label: "Symptoms", message: "Add the chief complaint", goTo: "symptoms" })
  if (req.examination && !n.hasVitals && !has(n.examination ?? "")) gaps.push({ step: "examination", label: "Examination", message: "Record vitals or examination findings", goTo: "examination" })
  const coded = n.icd10Codes.split(",").some(c => has(c))
  if (req.diagnose && !has(n.diagnosis) && !coded) gaps.push({ step: "diagnose", label: "Diagnosis", message: "Add the diagnosis or an ICD-10 code", goTo: "diagnose" })
  if (req.plan && !n.hasPlan) gaps.push({ step: "plan", label: "Plan", message: "Add the treatment plan or a follow-up", goTo: "plan" })
  return gaps
}

/** Problem with an override reason, or null when it can be sent. */
export function overrideProblem(reason: string): string | null {
  if (!has(reason)) return "Give a reason to override the requirement."
  if (reason.trim().length > OVERRIDE_REASON_MAX) return `Keep the reason under ${OVERRIDE_REASON_MAX} characters.`
  return null
}

/** ok = fine, warn = worth a look but does not stop signing, required = stops signing unless overridden, blocked = cannot be overridden. */
export type CheckState = "ok" | "warn" | "required" | "blocked"
export interface CheckItem { id: string; label: string; state: CheckState; detail: string; goTo?: WizardStep }

export interface ChecklistInput extends SignInput {
  examination: string; followUpDays: string; treatmentPlan?: string
  weightKg: string; heightCm: string; bloodPressure: string; pulseBpm: string; temperatureC: string; oxygenSatPct: string
}

export function signChecklist(n: ChecklistInput, rx: StepRx[], allergyBlocks: string[], req: RequiredStages = DEFAULT_REQUIRED): CheckItem[] {
  const vitals = [n.weightKg, n.heightCm, n.bloodPressure, n.pulseBpm, n.temperatureC, n.oxygenSatPct].some(has)
  const planned = has(n.treatmentPlan ?? "") || has(n.followUpDays)
  const gaps = signGaps({ ...n, hasVitals: vitals, hasPlan: planned }, req)
  const gap = (s: SignGap["step"]) => gaps.find(g => g.step === s)
  const incomplete = rx.filter(r => rxGaps(r).length > 0).length
  const items: CheckItem[] = []

  items.push(gap("symptoms")
    ? { id: "symptoms", label: "Symptoms documented", state: "required", detail: "Required: " + gap("symptoms")!.message.toLowerCase(), goTo: "symptoms" }
    : { id: "symptoms", label: "Symptoms documented", state: "ok", detail: has(n.chiefComplaint) ? n.chiefComplaint.trim() : "Not required for this visit" })
  items.push(gap("examination")
    ? { id: "examination", label: "Examination documented", state: "required", detail: "Required: " + gap("examination")!.message.toLowerCase(), goTo: "examination" }
    : has(n.examination) || vitals
      ? { id: "examination", label: "Examination documented", state: "ok", detail: vitals ? "Vitals recorded" : "Findings recorded" }
      : { id: "examination", label: "Examination documented", state: "warn", detail: "Nothing recorded (optional for this visit)", goTo: "examination" })
  items.push(gap("diagnose")
    ? { id: "diagnosis", label: "Diagnosis recorded", state: "required", detail: "Required: " + gap("diagnose")!.message.toLowerCase(), goTo: "diagnose" }
    : { id: "diagnosis", label: "Diagnosis recorded", state: "ok", detail: has(n.diagnosis) ? n.diagnosis.trim() : has(n.icd10Codes) ? n.icd10Codes.trim() : "Not required for this visit" })

  if (allergyBlocks.length) items.push({ id: "allergy", label: "Prescriptions checked against allergies", state: "blocked",
    detail: "Give a reason to prescribe despite the recorded allergy: " + allergyBlocks.join(", "), goTo: "diagnose" })
  else if (incomplete) items.push({ id: "rx", label: "Prescriptions checked", state: "warn",
    detail: `${incomplete} prescription${incomplete === 1 ? "" : "s"} need${incomplete === 1 ? "s" : ""} dosage details (saved as they are)`, goTo: "diagnose" })
  else items.push({ id: "rx", label: "Prescriptions checked", state: "ok", detail: rx.length ? `${rx.length} complete, allergy check clear` : "No prescriptions" })

  if (gap("plan")) items.push({ id: "plan", label: "Plan documented", state: "required", detail: "Required: " + gap("plan")!.message.toLowerCase(), goTo: "plan" })
  else if (req.plan) items.push({ id: "plan", label: "Plan documented", state: "ok", detail: has(n.treatmentPlan ?? "") ? n.treatmentPlan!.trim() : `Follow-up in ${n.followUpDays.trim()} days` })
  items.push(has(n.followUpDays)
    ? { id: "follow-up", label: "Follow-up set", state: "ok", detail: `In ${n.followUpDays.trim()} days` }
    : { id: "follow-up", label: "Follow-up set", state: "warn", detail: "Follow-up not specified", goTo: "plan" })
  return items
}

export interface SignVerdict { canSign: boolean; canOverride: boolean }
/** Sign straight away when nothing is required or blocked; override only when the sole problems are required steps. */
export function signVerdict(items: CheckItem[]): SignVerdict {
  const blocked = items.some(i => i.state === "blocked")
  const required = items.some(i => i.state === "required")
  return { canSign: !blocked && !required, canOverride: !blocked && required }
}
