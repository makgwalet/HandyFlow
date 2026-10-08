// What the doctor sees before signing, and when signing is allowed (CLINIC-DEC-010, 011).
// Symptoms (a chief complaint) and a Diagnosis (text or an ICD-10 code) are required. The server enforces the same
// rule (SignRules.java); this only shows it early so nobody finds out at the last click.
import type { WizardStep } from "./consultWizard"
import { rxGaps, type StepRx } from "./consultSteps"

export interface SignInput { chiefComplaint: string; diagnosis: string; icd10Codes: string }
export interface SignGap { step: "symptoms" | "diagnose"; label: string; message: string; goTo: WizardStep }

const has = (s: string) => s.trim().length > 0
export const OVERRIDE_REASON_MAX = 500

/** Required steps still empty, in clinical order. Mirrors the server rule. */
export function signGaps(n: SignInput): SignGap[] {
  const gaps: SignGap[] = []
  if (!has(n.chiefComplaint)) gaps.push({ step: "symptoms", label: "Symptoms", message: "Add the chief complaint", goTo: "symptoms" })
  const coded = n.icd10Codes.split(",").some(c => has(c))
  if (!has(n.diagnosis) && !coded) gaps.push({ step: "diagnose", label: "Diagnosis", message: "Add the diagnosis or an ICD-10 code", goTo: "diagnose" })
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
  examination: string; followUpDays: string
  weightKg: string; heightCm: string; bloodPressure: string; pulseBpm: string; temperatureC: string; oxygenSatPct: string
}

export function signChecklist(n: ChecklistInput, rx: StepRx[], allergyBlocks: string[]): CheckItem[] {
  const gaps = signGaps(n)
  const gap = (s: SignGap["step"]) => gaps.find(g => g.step === s)
  const vitals = [n.weightKg, n.heightCm, n.bloodPressure, n.pulseBpm, n.temperatureC, n.oxygenSatPct].some(has)
  const incomplete = rx.filter(r => rxGaps(r).length > 0).length
  const items: CheckItem[] = []

  items.push(gap("symptoms")
    ? { id: "symptoms", label: "Symptoms documented", state: "required", detail: "Required: " + gap("symptoms")!.message.toLowerCase(), goTo: "symptoms" }
    : { id: "symptoms", label: "Symptoms documented", state: "ok", detail: n.chiefComplaint.trim() })
  items.push(has(n.examination) || vitals
    ? { id: "examination", label: "Examination documented", state: "ok", detail: vitals ? "Vitals recorded" : "Findings recorded" }
    : { id: "examination", label: "Examination documented", state: "warn", detail: "Nothing recorded (optional for this visit)", goTo: "examination" })
  items.push(gap("diagnose")
    ? { id: "diagnosis", label: "Diagnosis recorded", state: "required", detail: "Required: " + gap("diagnose")!.message.toLowerCase(), goTo: "diagnose" }
    : { id: "diagnosis", label: "Diagnosis recorded", state: "ok", detail: has(n.diagnosis) ? n.diagnosis.trim() : n.icd10Codes.trim() })

  if (allergyBlocks.length) items.push({ id: "allergy", label: "Prescriptions checked against allergies", state: "blocked",
    detail: "Give a reason to prescribe despite the recorded allergy: " + allergyBlocks.join(", "), goTo: "diagnose" })
  else if (incomplete) items.push({ id: "rx", label: "Prescriptions checked", state: "warn",
    detail: `${incomplete} prescription${incomplete === 1 ? "" : "s"} need${incomplete === 1 ? "s" : ""} dosage details (saved as they are)`, goTo: "diagnose" })
  else items.push({ id: "rx", label: "Prescriptions checked", state: "ok", detail: rx.length ? `${rx.length} complete, allergy check clear` : "No prescriptions" })

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
