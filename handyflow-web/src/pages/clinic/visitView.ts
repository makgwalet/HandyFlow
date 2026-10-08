// Pure helpers for the Visits tab (the patient's consultation history).
import { bmi } from "./briefing"

export interface VisitTeam { role: string; name: string; at?: string | null }
export interface VisitRx { id: string; medicationName: string; dosage?: string | null; frequency?: string | null; duration?: string | null
  quantity?: number | null; repeats: number; instructions?: string | null; prescribedAt?: string | null; dispensed: boolean }
export interface VisitAddendum { id: string; text: string; authorName?: string | null; createdAt: string }
export interface Visit {
  id: string; appointmentId?: string | null; status: string; consultedAt: string; signedAt?: string | null
  doctorName?: string | null; team: VisitTeam[]
  chiefComplaint?: string | null; history?: string | null; examination?: string | null; diagnosis?: string | null; icd10Codes?: string[] | null
  treatmentPlan?: string | null; followUpDays?: number | null
  weightKg?: number | null; heightCm?: number | null; bloodPressure?: string | null; pulseBpm?: number | null; temperatureC?: number | null; oxygenSatPct?: number | null
  billed: boolean; billingAmount?: number | null; prescriptions: VisitRx[]; addenda: VisitAddendum[]
}

type Tone = "ok" | "warn" | "info" | "muted"
export const STATUS: Record<string, { label: string; tone: Tone }> = {
  DRAFT: { label: "In progress", tone: "warn" }, NURSE_IN_PROGRESS: { label: "With the nurse", tone: "warn" },
  READY_FOR_DOCTOR: { label: "Waiting for the doctor", tone: "warn" }, DOCTOR_REVIEWING: { label: "Doctor reviewing", tone: "warn" },
  DOCTOR_COMPLETED: { label: "Ready to sign", tone: "warn" }, RETURNED_TO_NURSE: { label: "Returned to the nurse", tone: "warn" },
  SIGNED: { label: "Signed", tone: "ok" }, LOCKED: { label: "Signed and locked", tone: "info" },
}
export const statusOf = (s: string) => STATUS[s] ?? { label: s.toLowerCase().replace(/_/g, " "), tone: "muted" as Tone }

/** An unsigned visit that can still be worked on in the consultation page. */
export const isOpenVisit = (s: string) => ["DRAFT", "NURSE_IN_PROGRESS", "READY_FOR_DOCTOR", "DOCTOR_REVIEWING", "DOCTOR_COMPLETED", "RETURNED_TO_NURSE"].includes(s)
/** A signed visit: read-only, corrected and added to only through addenda. */
export const isAmendable = (s: string) => s === "SIGNED" || s === "LOCKED"

export const headline = (v: Visit) => v.diagnosis?.trim() || v.chiefComplaint?.trim() || "No reason recorded"

export function vitalLines(v: Visit): [string, string][] {
  const b = bmi(v.weightKg ?? undefined, v.heightCm ?? undefined)
  const rows: [string, string | null][] = [
    ["BP", v.bloodPressure ?? null], ["Pulse", v.pulseBpm != null ? `${v.pulseBpm} bpm` : null], ["Temp", v.temperatureC != null ? `${v.temperatureC} °C` : null],
    ["SpO₂", v.oxygenSatPct != null ? `${v.oxygenSatPct} %` : null], ["Weight", v.weightKg != null ? `${v.weightKg} kg` : null],
    ["Height", v.heightCm != null ? `${v.heightCm} cm` : null], ["BMI", b != null ? String(b) : null],
  ]
  return rows.filter((r): r is [string, string] => r[1] != null && r[1] !== "")
}

export const rxLine = (r: VisitRx) => [r.dosage, r.frequency, r.duration].filter(Boolean).join(" · ")
  + (r.quantity ? ` · Qty ${r.quantity}` : "") + (r.repeats > 0 ? ` · Repeats ${r.repeats}` : "")

/** The one-line reason the "Resume" button is missing, or null when it can be offered. */
export const resumeProblem = (v: Visit): string | null => isOpenVisit(v.status) && !v.appointmentId ? "This visit has no appointment to resume from." : null
