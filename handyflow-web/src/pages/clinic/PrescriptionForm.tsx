// src/pages/clinic/PrescriptionForm.tsx
// The one form for adding a prescription to an existing consultation. It replaces the separate copies that lived in
// the consultations list and the patient file, so the allergy prompt, the checks on quantity and repeats, and the
// wording are the same everywhere. The server still checks everything again: the allergy check is by medicine name
// only, and no match is not a safety clearance (see PrescriptionAllergyCheck).
import { useState } from "react"
import { AllergyWarning, missingReasons, useAllergyChecks } from "./PrescriptionAllergyCheck"

export interface RxValues { medicationName: string; dosage: string; frequency: string; duration: string; quantity: string; repeats: string; instructions: string }
export const EMPTY_RX: RxValues = { medicationName: "", dosage: "", frequency: "", duration: "", quantity: "30", repeats: "0", instructions: "" }

const wholeNumber = (s: string) => /^\d+$/.test(s.trim())

/** Problems with the entries, in plain words; empty when the form can be sent. */
export function rxProblems(v: RxValues): string[] {
  const p: string[] = []
  if (!v.medicationName.trim()) p.push("Enter the medicine.")
  if (!wholeNumber(v.quantity) || parseInt(v.quantity, 10) < 1) p.push("Quantity must be a whole number of at least 1.")
  if (!wholeNumber(v.repeats)) p.push("Repeats must be a whole number, 0 or more.")
  return p
}

/** The request body: trimmed text, numbers as numbers, blank optional fields as null. Call only when rxProblems is empty. */
export function rxRequest(v: RxValues, allergyReason: string) {
  const blank = (s: string) => s.trim() || null
  return {
    medicationName: v.medicationName.trim(), dosage: blank(v.dosage), frequency: blank(v.frequency), duration: blank(v.duration),
    quantity: parseInt(v.quantity, 10), repeats: parseInt(v.repeats, 10), instructions: blank(v.instructions),
    allergyOverrideReason: allergyReason.trim() || null,
  }
}

const lbl: React.CSSProperties = { display: "block", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 3 }
const inp: React.CSSProperties = { width: "100%", padding: "7px 10px", fontSize: 13, borderRadius: 8, border: "1px solid var(--hf-border)",
  background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }

export default function PrescriptionForm({ consultationId, onSubmit, busy, error, submitLabel = "Add prescription" }: {
  consultationId: string | null
  onSubmit: (body: ReturnType<typeof rxRequest>) => Promise<unknown> | void
  busy?: boolean
  error?: string
  submitLabel?: string
}) {
  const [v, setV] = useState<RxValues>(EMPTY_RX)
  const [reason, setReason] = useState("")
  const [shown, setShown] = useState(false)
  const set = (k: keyof RxValues) => (e: React.ChangeEvent<HTMLInputElement>) => setV(p => ({ ...p, [k]: e.target.value }))

  const probe = [{ id: "rx-form", medicationName: v.medicationName }]
  const allergy = useAllergyChecks(consultationId, probe)
  const needsReason = missingReasons([{ ...probe[0], allergyReason: reason }], allergy).length > 0
  const problems = rxProblems(v)

  const send = async () => {
    setShown(true)
    if (problems.length > 0 || needsReason) return
    try {
      await onSubmit(rxRequest(v, reason))
      setV(EMPTY_RX); setReason(""); setShown(false)        // cleared only after the server accepted it
    } catch { /* the caller shows the server's message through `error` */ }
  }

  return (
    <div>
      <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 12 }}>Add prescription</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <div style={{ gridColumn: "1 / -1" }}><label style={lbl} htmlFor="rx-med">Medication *</label>
          <input id="rx-med" value={v.medicationName} onChange={set("medicationName")} placeholder="Amoxicillin 500mg" style={inp} /></div>
        <div><label style={lbl} htmlFor="rx-dose">Dosage</label><input id="rx-dose" value={v.dosage} onChange={set("dosage")} placeholder="500mg" style={inp} /></div>
        <div><label style={lbl} htmlFor="rx-freq">Frequency</label><input id="rx-freq" value={v.frequency} onChange={set("frequency")} placeholder="3× daily" style={inp} /></div>
        <div><label style={lbl} htmlFor="rx-dur">Duration</label><input id="rx-dur" value={v.duration} onChange={set("duration")} placeholder="7 days" style={inp} /></div>
        <div><label style={lbl} htmlFor="rx-qty">Quantity</label><input id="rx-qty" type="number" min={1} value={v.quantity} onChange={set("quantity")} style={inp} /></div>
        <div><label style={lbl} htmlFor="rx-rep">Repeats</label><input id="rx-rep" type="number" min={0} value={v.repeats} onChange={set("repeats")} style={inp} /></div>
        <div style={{ gridColumn: "1 / -1" }}><label style={lbl} htmlFor="rx-ins">Instructions</label>
          <input id="rx-ins" value={v.instructions} onChange={set("instructions")} placeholder="Take with food" style={inp} /></div>
      </div>
      <AllergyWarning result={allergy["rx-form"]} reason={reason} onReason={setReason} />
      {shown && problems.length > 0 && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 8 }}>{problems.join(" ")}</div>}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginTop: 8 }}>{error}</div>}
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 14 }}>
        <button type="button" onClick={send} disabled={busy || !v.medicationName.trim() || needsReason}>{busy ? "Adding…" : submitLabel}</button>
      </div>
    </div>
  )
}
