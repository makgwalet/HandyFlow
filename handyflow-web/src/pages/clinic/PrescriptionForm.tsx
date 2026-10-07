// src/pages/clinic/PrescriptionForm.tsx
// The one form for adding a prescription to an existing consultation. It replaces the separate copies that lived in
// the consultations list and the patient file, so the allergy prompt, the checks on quantity and repeats, and the
// wording are the same everywhere. The server still checks everything again: the allergy check is by medicine name
// only, and no match is not a safety clearance (see PrescriptionAllergyCheck).
import { useEffect, useState } from "react"
import { apiClient } from "../../api/client"
import { AllergyWarning, missingReasons, useAllergyChecks } from "./PrescriptionAllergyCheck"

export interface RxValues { medicationName: string; dosage: string; frequency: string; duration: string; quantity: string; repeats: string; instructions: string }
export const EMPTY_RX: RxValues = { medicationName: "", dosage: "", frequency: "", duration: "", quantity: "30", repeats: "0", instructions: "" }

export interface CatalogueMed { genericName: string; strength?: string | null; dosageForm?: string | null; brandName?: string | null }
/** How a catalogue medicine is written into the prescription: generic name and strength, as the live session does. */
export const medicineLabel = (m: CatalogueMed) => `${m.genericName} ${m.strength ?? ""}`.trim()

/** Catalogue suggestions for what is typed. A failed search just means no suggestions: typing a medicine always works. */
function useCatalogue(text: string, active: boolean): CatalogueMed[] {
  const [found, setFound] = useState<CatalogueMed[]>([])
  useEffect(() => {
    const q = text.trim()
    if (!active || q.length < 2) { setFound([]); return }
    let stale = false
    const t = setTimeout(async () => {
      try {
        const r = await apiClient.get(`/api/v1/clinic/medications?search=${encodeURIComponent(q)}`)
        const rows = r.data?.data ?? r.data
        if (!stale) setFound(Array.isArray(rows) ? rows.slice(0, 8) : [])
      } catch { if (!stale) setFound([]) }
    }, 300)
    return () => { stale = true; clearTimeout(t) }
  }, [text, active])
  return found
}

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
  const [picking, setPicking] = useState(false)          // suggestions only while the user is typing in the medicine box
  const set = (k: keyof RxValues) => (e: React.ChangeEvent<HTMLInputElement>) => setV(p => ({ ...p, [k]: e.target.value }))

  const suggestions = useCatalogue(v.medicationName, picking)
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
          <input id="rx-med" value={v.medicationName} autoComplete="off" placeholder="Amoxicillin 500mg" style={inp}
            onChange={e => { setPicking(true); set("medicationName")(e) }} />
          {picking && suggestions.length > 0 && (
            <ul role="listbox" aria-label="Medicine suggestions" style={{ listStyle: "none", margin: "2px 0 0", padding: 0, border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)" }}>
              {suggestions.map((m, i) => (
                <li key={i} role="option" aria-selected={false} style={{ padding: "5px 10px", fontSize: 13, cursor: "pointer" }}
                  onClick={() => { setV(p => ({ ...p, medicationName: medicineLabel(m) })); setPicking(false) }}>
                  {medicineLabel(m)}{m.dosageForm ? <span style={{ color: "var(--hf-text-muted)" }}> · {m.dosageForm}</span> : null}
                </li>
              ))}
            </ul>
          )}</div>
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
