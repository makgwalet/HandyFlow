// src/pages/clinic/PrescriptionAllergyCheck.tsx
// Allergy prompt while prescribing. The server compares the medicine NAME with the patient's recorded allergies
// (no drug classes, no brand names, no cross-reactivity). A match asks the prescriber for a reason before the
// prescription can be saved; the reason is stored with the prescription. No match is never a safety clearance.
import { useEffect, useRef, useState } from "react"
import { apiClient } from "../../api/client"

export interface AllergyAlert { allergen: string; severity?: string | null; reaction?: string | null }
export interface AllergyResult { name: string; alerts: AllergyAlert[]; note: string }
interface RxLike { id: string; medicationName: string }

const unwrap = (r: any) => r.data?.data ?? r.data

/** Prescriptions whose medicine matched an allergy and that still have no reason. */
export function missingReasons(rx: (RxLike & { allergyReason?: string })[], results: Record<string, AllergyResult>): string[] {
  return rx.filter(r => r.medicationName.trim()
      && (results[r.id]?.name === r.medicationName.trim())
      && results[r.id].alerts.length > 0
      && !(r.allergyReason ?? "").trim()).map(r => r.medicationName.trim())
}

/** Checks each prescription's medicine name once it settles. Does nothing until the consultation draft exists. */
export function useAllergyChecks(consultationId: string | null, rx: RxLike[]): Record<string, AllergyResult> {
  const [results, setResults] = useState<Record<string, AllergyResult>>({})
  const asked = useRef<Record<string, string>>({})
  useEffect(() => {
    if (!consultationId) return
    const t = setTimeout(() => {
      for (const r of rx) {
        const name = r.medicationName.trim()
        if (!name || asked.current[r.id] === name) continue
        asked.current[r.id] = name
        apiClient.post(`/api/v1/clinic/consultations/${consultationId}/prescriptions/allergy-check`, { medicationName: name })
          .then(res => {
            const d = unwrap(res)
            setResults(p => ({ ...p, [r.id]: { name, alerts: d?.alerts ?? [], note: d?.note ?? "" } }))
          })
          .catch(() => { delete asked.current[r.id] })       // try again on the next change
      }
    }, 500)
    return () => clearTimeout(t)
  }, [consultationId, rx.map(r => r.id + ":" + r.medicationName).join("|")])  // eslint-disable-line react-hooks/exhaustive-deps
  return results
}

export function AllergyWarning({ result, reason, onReason }: {
  result?: AllergyResult; reason: string; onReason: (v: string) => void
}) {
  if (!result || result.alerts.length === 0) return null
  return (
    <div role="alert" style={{ marginTop: 8, padding: "8px 10px", borderRadius: 8, fontSize: 12,
      background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)" }}>
      <div style={{ fontWeight: 700 }}>Recorded allergy matches this medicine</div>
      <ul style={{ margin: "4px 0", paddingLeft: 16 }}>
        {result.alerts.map(a => (
          <li key={a.allergen}>{a.allergen}{a.severity ? ` (${a.severity.toLowerCase()})` : ""}{a.reaction ? `: ${a.reaction}` : ""}</li>
        ))}
      </ul>
      <label style={{ display: "block", fontWeight: 600, marginBottom: 2 }}>Reason to prescribe anyway (required)</label>
      <input value={reason} onChange={e => onReason(e.target.value)} aria-label="Reason to prescribe anyway"
        style={{ width: "100%", padding: "5px 8px", fontSize: 12, borderRadius: 6, border: "1px solid var(--hf-border)",
          background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }} />
      <div style={{ marginTop: 4, color: "var(--hf-text-muted)" }}>{result.note}</div>
    </div>
  )
}
