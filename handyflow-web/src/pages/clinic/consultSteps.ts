// The five steps of a consultation and how far each one is, worked out from what has been typed.
// This only reports whether the record is filled in; it never judges the clinical content and
// never blocks anything. (Signing is gated separately, by signRules.ts.)
import { signGaps } from "./signRules"

export interface StepNotes {
  chiefComplaint: string; history: string; examination: string; diagnosis: string
  treatmentPlan: string; followUpDays: string
  weightKg: string; heightCm: string; bloodPressure: string
  pulseBpm: string; temperatureC: string; oxygenSatPct: string
  /** Comma-separated; a code alone counts as a diagnosis. */
  icd10Codes?: string
}
export interface StepRx { medicationName: string; dosage: string; frequency: string; duration: string; quantity: number }

export type StepState = "done" | "todo" | "attention"
export interface ConsultStep { id: "symptoms" | "examination" | "diagnose" | "plan" | "sign"; label: string; state: StepState; hint: string }

/** The element on the session screen each step jumps to. */
export const STEP_TARGET: Record<ConsultStep["id"], string> = {
  symptoms: "soap-chiefComplaint",
  examination: "consult-vitals",
  diagnose: "soap-diagnosis",
  plan: "soap-treatmentPlan",
  sign: "consult-complete",
}

const has = (s: string) => s.trim().length > 0

/** What is missing from one prescription draft (empty list = complete). */
export function rxGaps(r: StepRx): string[] {
  const gaps: string[] = []
  if (!has(r.medicationName)) gaps.push("medicine")
  if (!has(r.dosage)) gaps.push("dosage")
  if (!has(r.frequency)) gaps.push("frequency")
  if (!has(r.duration)) gaps.push("duration")
  if (!(r.quantity > 0)) gaps.push("quantity")
  return gaps
}

export function consultSteps(n: StepNotes, rx: StepRx[]): ConsultStep[] {
  const symptoms: ConsultStep = has(n.chiefComplaint) && has(n.history)
    ? { id: "symptoms", label: "Symptoms", state: "done", hint: "Complaint and history recorded" }
    : { id: "symptoms", label: "Symptoms", state: "todo", hint: has(n.chiefComplaint) ? "Add the history" : "Add the chief complaint and history" }

  const vitals = [n.weightKg, n.heightCm, n.bloodPressure, n.pulseBpm, n.temperatureC, n.oxygenSatPct].some(has)
  const examination: ConsultStep = has(n.examination) || vitals
    ? { id: "examination", label: "Examination", state: "done", hint: "Findings or vitals recorded" }
    : { id: "examination", label: "Examination", state: "todo", hint: "Add vitals or examination findings" }

  const incomplete = rx.filter(r => rxGaps(r).length > 0).length
  const hasDx = has(n.diagnosis) || (n.icd10Codes ?? "").split(",").some(has)
  const diagnose: ConsultStep = !hasDx
    ? { id: "diagnose", label: "Assessment & treatment", state: "todo", hint: "Add the diagnosis" }
    : incomplete > 0
      ? { id: "diagnose", label: "Assessment & treatment", state: "attention", hint: `${incomplete} prescription${incomplete === 1 ? "" : "s"} need${incomplete === 1 ? "s" : ""} dosage details` }
      : { id: "diagnose", label: "Assessment & treatment", state: "done", hint: rx.length ? "Diagnosis and prescriptions complete" : "Diagnosis recorded (no prescriptions)" }

  const plan: ConsultStep = has(n.treatmentPlan) || has(n.followUpDays)
    ? { id: "plan", label: "Plan", state: "done", hint: "Plan recorded" }
    : { id: "plan", label: "Plan", state: "todo", hint: "Add the treatment plan or a follow-up" }

  const missing = signGaps({ chiefComplaint: n.chiefComplaint, diagnosis: n.diagnosis, icd10Codes: n.icd10Codes ?? "" }).map(g => g.label)
  const sign: ConsultStep = missing.length === 0
    ? { id: "sign", label: "Sign", state: "done", hint: "Ready to sign" }
    : { id: "sign", label: "Sign", state: "todo", hint: "Required first: " + missing.join(", ") }

  return [symptoms, examination, diagnose, plan, sign]
}
