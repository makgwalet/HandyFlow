// Pure rules for the consultation workspace: opening it, the lifecycle label, the dock and "who is next".
import { consultSteps, type StepNotes, type StepState } from "./consultSteps"
import { DEFAULT_REQUIRED, signGaps, type RequiredStages } from "./signRules"

/** Appointment actions to run before a consultation can open, or null when it cannot be opened (finished, cancelled, no-show). */
export function startSteps(status: string): string[] | null {
  switch (status) {
    case "IN_PROGRESS": return []
    case "SCHEDULED": return ["check_in", "start"]
    case "CONFIRMED": case "CHECKED_IN": case "TRIAGED": return ["start"]
    default: return null
  }
}

export const workspacePath = (appointmentId: string) => `/clinic/consult/${appointmentId}`

/** Consultation states in which the notes cannot be edited: the other clinician has it. */
export const isWithOtherClinician = (status: string) => status === "READY_FOR_DOCTOR" || status === "DOCTOR_COMPLETED"

/** One word for where the record is: Draft, In progress, Ready to sign, or the handoff state. */
export function lifecycleLabel(status: string, n: { chiefComplaint: string; history: string; examination: string; diagnosis: string; icd10Codes: string; treatmentPlan: string; followUpDays?: string; hasVitals?: boolean },
  req: RequiredStages = DEFAULT_REQUIRED): string {
  if (status === "READY_FOR_DOCTOR") return "Waiting for the doctor"
  if (status === "DOCTOR_REVIEWING") return "Doctor reviewing"
  if (status === "DOCTOR_COMPLETED") return "Review done"
  if (status === "RETURNED_TO_NURSE") return "Returned to nurse"
  if (status === "NURSE_IN_PROGRESS") return "Nurse in progress"
  if (signGaps({ ...n, hasPlan: n.treatmentPlan.trim().length > 0 || (n.followUpDays ?? "").trim().length > 0 }, req).length === 0) return "Ready to sign"
  const started = [n.history, n.examination, n.diagnosis, n.icd10Codes, n.treatmentPlan].some(v => v.trim()) || n.chiefComplaint.trim().length > 0
  return started ? "In progress" : "Draft"
}

// ── The dock: where a consultation left off ──────────────────────────────────

/** The fields of a consultation as the server returns them (numbers and nulls). */
export interface ConsultationLike {
  id: string; appointmentId?: string | null; patientId: string; patientName?: string; status: string
  chiefComplaint?: string | null; history?: string | null; examination?: string | null; diagnosis?: string | null
  icd10Codes?: string[] | null; treatmentPlan?: string | null; followUpDays?: number | null
  weightKg?: number | null; heightCm?: number | null; bloodPressure?: string | null; pulseBpm?: number | null
  temperatureC?: number | null; oxygenSatPct?: number | null
}
const s = (v: unknown) => (v == null ? "" : String(v))

export function toNotes(c: ConsultationLike): StepNotes & { icd10Codes: string } {
  return {
    chiefComplaint: s(c.chiefComplaint), history: s(c.history), examination: s(c.examination), diagnosis: s(c.diagnosis),
    icd10Codes: (c.icd10Codes ?? []).join(", "), treatmentPlan: s(c.treatmentPlan), followUpDays: s(c.followUpDays),
    weightKg: s(c.weightKg), heightCm: s(c.heightCm), bloodPressure: s(c.bloodPressure), pulseBpm: s(c.pulseBpm),
    temperatureC: s(c.temperatureC), oxygenSatPct: s(c.oxygenSatPct),
  }
}

export interface DockStage { id: string; label: string; state: StepState }
/** "Symptoms ✓ · Exam ✓ · Assessment — · Plan —" for the dock. Sign is not a stage of work, so it is left out. */
export function dockStages(c: ConsultationLike): DockStage[] {
  const short: Record<string, string> = { symptoms: "Symptoms", examination: "Exam", diagnose: "Assessment", plan: "Plan" }
  return consultSteps(toNotes(c), []).filter(x => x.id !== "sign").map(x => ({ id: x.id, label: short[x.id], state: x.state }))
}

/** The consultation to show in the dock: the most recently touched one that can be reopened (it needs an appointment). */
export function dockCandidate<T extends ConsultationLike & { updatedAt?: string }>(list: T[]): T | null {
  return [...list].filter(c => !!c.appointmentId).sort((a, b) => (b.updatedAt ?? "").localeCompare(a.updatedAt ?? ""))[0] ?? null
}

// ── Next patient ─────────────────────────────────────────────────────────────

export interface QueueAppt {
  id: string; patientId: string; patientName: string; practitionerId?: string | null
  scheduledAt: string; status: string; reason?: string | null; appointmentType?: string | null
}
export interface NextUp { ready: QueueAppt | null; upcoming: QueueAppt | null; waiting: number }

/**
 * Who to see next from today's appointments: someone already in the building first (triaged before merely checked in,
 * then by appointment time), otherwise the next expected one. `mine` keeps one practitioner's patients; unassigned
 * appointments count for everyone. The patient just seen is left out.
 */
export function nextUp(items: QueueAppt[], opts: { mine?: string | null; excludeId?: string } = {}): NextUp {
  const own = items.filter(i => i.id !== opts.excludeId && (!opts.mine || !i.practitionerId || i.practitionerId === opts.mine))
  const byTime = (a: QueueAppt, b: QueueAppt) => a.scheduledAt.localeCompare(b.scheduledAt)
  const triaged = own.filter(i => i.status === "TRIAGED").sort(byTime)
  const checkedIn = own.filter(i => i.status === "CHECKED_IN").sort(byTime)
  const expected = own.filter(i => i.status === "CONFIRMED" || i.status === "SCHEDULED").sort(byTime)
  const inBuilding = [...triaged, ...checkedIn]
  return { ready: inBuilding[0] ?? null, upcoming: expected[0] ?? null, waiting: inBuilding.length }
}
