// Moving between the steps of a consultation. Steps are never locked: the clinician can go to any step,
// the order only decides what "Next" and "Back" do.
import type { ConsultStep } from "./consultSteps"

export type WizardStep = ConsultStep["id"]
export const WIZARD_ORDER: WizardStep[] = ["symptoms", "examination", "diagnose", "plan", "sign"]

export const nextStep = (s: WizardStep): WizardStep | null => WIZARD_ORDER[WIZARD_ORDER.indexOf(s) + 1] ?? null
export const prevStep = (s: WizardStep): WizardStep | null => WIZARD_ORDER[WIZARD_ORDER.indexOf(s) - 1] ?? null
export const stepNumber = (s: WizardStep): number => WIZARD_ORDER.indexOf(s) + 1

/** Quick follow-up choices for the Plan step. */
export const FOLLOW_UP_CHOICES = [3, 7, 14, 30, 90]

/** One line for the sign-off summary: vitals that were filled in, e.g. "BP 120/80 · Pulse 72 · Temp 36.6°C". */
export function vitalsLine(n: { weightKg: string; heightCm: string; bloodPressure: string; pulseBpm: string; temperatureC: string; oxygenSatPct: string }): string {
  const parts: [string, string, string][] = [
    ["Weight", n.weightKg, " kg"], ["Height", n.heightCm, " cm"], ["BP", n.bloodPressure, ""],
    ["Pulse", n.pulseBpm, " bpm"], ["Temp", n.temperatureC, "°C"], ["SpO₂", n.oxygenSatPct, "%"],
  ]
  return parts.filter(([, v]) => v.trim()).map(([l, v, u]) => `${l} ${v.trim()}${u}`).join(" · ")
}
