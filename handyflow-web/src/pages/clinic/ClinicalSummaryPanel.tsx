// src/pages/clinic/ClinicalSummaryPanel.tsx
// Patient overview: allergies, conditions and medicines, read from the structured records (S1-2) and editable by
// clinicians. Resolving or stopping an item keeps it on record; nothing is deleted. If the structured lists cannot
// be loaded, the older plain-text lists on the patient are shown read-only instead, so an allergy is never hidden.
import { useDialogs } from "./dialogs"
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useCan } from "./clinicAccess"

interface Allergy { id: string; allergen: string; allergenType?: string; reaction?: string; severity?: string; status: string }
interface Condition { id: string; conditionName: string; icd10Code?: string; status: string }
interface Medication { id: string; medicineName: string; dose?: string; frequency?: string; source?: string; status: string }

export const ALLERGEN_TYPES = ["DRUG", "FOOD", "ENVIRONMENT", "OTHER", "UNKNOWN"]
export const SEVERITIES = ["MILD", "MODERATE", "SEVERE", "LIFE_THREATENING"]

const unwrap = (r: any) => r.data?.data ?? r.data
const label = (v?: string) => (v ?? "").toLowerCase().replace(/_/g, " ")
const base = (patientId: string) => `/api/v1/clinic/patients/${patientId}`

/** Request bodies, trimmed; blanks become null so the server leaves optional fields empty. */
export const bodies = {
  allergy: (f: { allergen: string; allergenType: string; severity: string; reaction: string }) => ({
    allergen: f.allergen.trim(), allergenType: f.allergenType || "UNKNOWN",
    severity: f.severity || null, reaction: f.reaction.trim() || null,
  }),
  condition: (f: { conditionName: string; icd10Code: string }) => ({
    conditionName: f.conditionName.trim(), icd10Code: f.icd10Code.trim() || null,
  }),
  medication: (f: { medicineName: string; dose: string; frequency: string }) => ({
    medicineName: f.medicineName.trim(), dose: f.dose.trim() || null, frequency: f.frequency.trim() || null,
    source: "PATIENT_REPORTED",
  }),
}

const chip = (bg: string, color: string): React.CSSProperties =>
  ({ background: bg, color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 })
const input: React.CSSProperties = { padding: "5px 8px", fontSize: 12, borderRadius: 6, border: "1px solid var(--hf-border)",
  background: "var(--hf-surface)", color: "var(--hf-text)", minWidth: 0 }
const linkBtn: React.CSSProperties = { background: "none", border: "none", cursor: "pointer", fontSize: 11,
  color: "var(--hf-accent-text)", fontWeight: 600, padding: 0 }

function Section({ title, tone, children }: { title: string; tone: "danger" | "warning" | "neutral"; children: React.ReactNode }) {
  const t = { danger: ["var(--hf-danger-soft)", "var(--hf-danger-border)", "var(--hf-danger-text)"],
              warning: ["var(--hf-warning-soft)", "var(--hf-warning-border)", "var(--hf-warning-text)"],
              neutral: ["var(--hf-surface-muted)", "var(--hf-border)", "var(--hf-text-muted)"] }[tone]
  return (
    <div style={{ marginBottom: 12, padding: "14px 16px", background: t[0], border: `1px solid ${t[1]}`, borderRadius: 12 }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: t[2], textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 8 }}>{title}</div>
      {children}
    </div>
  )
}

export default function ClinicalSummaryPanel({ patientId, fallbackAllergies = [], fallbackConditions = [] }:
  { patientId: string; fallbackAllergies?: string[]; fallbackConditions?: string[] }) {
  const qc = useQueryClient()
  const { confirm, dialogs } = useDialogs()
  const canWrite = useCan("editClinicalSummary")
  const [error, setError] = useState("")
  const refresh = () => { setError(""); qc.invalidateQueries({ queryKey: ["pf-clinical", patientId] }); qc.invalidateQueries({ queryKey: ["clinic-patients"] }) }
  const fail = (e: any) => setError(e?.response?.data?.message ?? "Could not save that change.")

  const list = <T,>(kind: string) => useQuery<T[]>({
    queryKey: ["pf-clinical", patientId, kind],
    queryFn: async () => unwrap(await apiClient.get(`${base(patientId)}/${kind}`)) ?? [],
    retry: false,
  })
  const allergies = list<Allergy>("allergies"), conditions = list<Condition>("conditions"), medications = list<Medication>("medications")

  const add = useMutation({ mutationFn: ({ kind, body }: { kind: string; body: any }) => apiClient.post(`${base(patientId)}/${kind}`, body), onSuccess: refresh, onError: fail })
  const patch = useMutation({ mutationFn: ({ kind, id, body }: { kind: string; id: string; body: any }) => apiClient.patch(`${base(patientId)}/${kind}/${id}`, body), onSuccess: refresh, onError: fail })

  const [a, setA] = useState({ allergen: "", allergenType: "DRUG", severity: "", reaction: "" })
  const [c, setC] = useState({ conditionName: "", icd10Code: "" })
  const [m, setM] = useState({ medicineName: "", dose: "", frequency: "" })

  return (
    <div>
      {dialogs}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 8 }}>{error}</div>}

      <Section title="⚠ Allergies" tone="danger">
        {allergies.isError
          ? <div style={{ fontSize: 13 }}>{fallbackAllergies.length ? fallbackAllergies.join(", ") : "None recorded"}</div>
          : (allergies.data ?? []).length === 0
            ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No allergies recorded.</div>
            : (allergies.data ?? []).map(x => (
              <div key={x.id} style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", padding: "3px 0", fontSize: 13 }}>
                <strong>{x.allergen}</strong>
                {x.allergenType && x.allergenType !== "UNKNOWN" && <span style={chip("var(--hf-surface)", "var(--hf-text-muted)")}>{label(x.allergenType)}</span>}
                {x.severity && <span style={chip("var(--hf-danger-soft-strong, var(--hf-surface))", "var(--hf-danger-text)")}>{label(x.severity)}</span>}
                {x.reaction && <span style={{ color: "var(--hf-text-muted)" }}>{x.reaction}</span>}
                {canWrite && <>
                  <button style={linkBtn} onClick={() => patch.mutate({ kind: "allergies", id: x.id, body: { status: "RESOLVED" } })}>Resolved</button>
                  <button style={linkBtn} onClick={async () => { if (await confirm({ title: "Mark as entered in error?", body: `"${x.allergen}" will no longer count as an allergy.`, confirmLabel: "Entered in error", danger: true })) patch.mutate({ kind: "allergies", id: x.id, body: { status: "ENTERED_IN_ERROR" } }) }}>Entered in error</button>
                </>}
              </div>
            ))}
        {canWrite && !allergies.isError && (
          <form onSubmit={e => { e.preventDefault(); if (!a.allergen.trim()) return; add.mutate({ kind: "allergies", body: bodies.allergy(a) }, { onSuccess: () => setA({ ...a, allergen: "", reaction: "" }) }) }}
            style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8 }}>
            <input aria-label="Allergen" placeholder="Allergen" value={a.allergen} onChange={e => setA({ ...a, allergen: e.target.value })} style={{ ...input, flex: "1 1 120px" }} />
            <select aria-label="Allergen type" value={a.allergenType} onChange={e => setA({ ...a, allergenType: e.target.value })} style={input}>
              {ALLERGEN_TYPES.map(t => <option key={t} value={t}>{label(t)}</option>)}</select>
            <select aria-label="Severity" value={a.severity} onChange={e => setA({ ...a, severity: e.target.value })} style={input}>
              <option value="">Severity</option>{SEVERITIES.map(t => <option key={t} value={t}>{label(t)}</option>)}</select>
            <input aria-label="Reaction" placeholder="Reaction" value={a.reaction} onChange={e => setA({ ...a, reaction: e.target.value })} style={{ ...input, flex: "1 1 120px" }} />
            <button type="submit" disabled={add.isPending || !a.allergen.trim()} style={{ ...input, cursor: "pointer", fontWeight: 600 }}>Add allergy</button>
          </form>
        )}
      </Section>

      <Section title="Conditions" tone="warning">
        {conditions.isError
          ? <div style={{ fontSize: 13 }}>{fallbackConditions.length ? fallbackConditions.join(", ") : "None recorded"}</div>
          : (conditions.data ?? []).length === 0
            ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No conditions recorded.</div>
            : (conditions.data ?? []).map(x => (
              <div key={x.id} style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", padding: "3px 0", fontSize: 13 }}>
                <strong>{x.conditionName}</strong>
                {x.icd10Code && <span style={chip("var(--hf-surface)", "var(--hf-text-muted)")}>{x.icd10Code}</span>}
                {x.status === "CONTROLLED" && <span style={chip("var(--hf-success-soft)", "var(--hf-success-text-strong)")}>controlled</span>}
                {canWrite && <>
                  {x.status !== "CONTROLLED" && <button style={linkBtn} onClick={() => patch.mutate({ kind: "conditions", id: x.id, body: { status: "CONTROLLED" } })}>Controlled</button>}
                  <button style={linkBtn} onClick={() => patch.mutate({ kind: "conditions", id: x.id, body: { status: "RESOLVED" } })}>Resolved</button>
                </>}
              </div>
            ))}
        {canWrite && !conditions.isError && (
          <form onSubmit={e => { e.preventDefault(); if (!c.conditionName.trim()) return; add.mutate({ kind: "conditions", body: bodies.condition(c) }, { onSuccess: () => setC({ conditionName: "", icd10Code: "" }) }) }}
            style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8 }}>
            <input aria-label="Condition" placeholder="Condition" value={c.conditionName} onChange={e => setC({ ...c, conditionName: e.target.value })} style={{ ...input, flex: "1 1 160px" }} />
            <input aria-label="ICD-10 code" placeholder="ICD-10 (optional)" value={c.icd10Code} onChange={e => setC({ ...c, icd10Code: e.target.value })} style={{ ...input, width: 120 }} />
            <button type="submit" disabled={add.isPending || !c.conditionName.trim()} style={{ ...input, cursor: "pointer", fontWeight: 600 }}>Add condition</button>
          </form>
        )}
      </Section>

      {!medications.isError && (
        <Section title="Current medicines" tone="neutral">
          {(medications.data ?? []).length === 0
            ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No medicines recorded.</div>
            : (medications.data ?? []).map(x => (
              <div key={x.id} style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", padding: "3px 0", fontSize: 13 }}>
                <strong>{x.medicineName}</strong>
                <span style={{ color: "var(--hf-text-muted)" }}>{[x.dose, x.frequency].filter(Boolean).join(" · ")}</span>
                {x.source && x.source !== "PATIENT_REPORTED" && <span style={chip("var(--hf-surface)", "var(--hf-text-muted)")}>{label(x.source)}</span>}
                {canWrite && <button style={linkBtn} onClick={() => patch.mutate({ kind: "medications", id: x.id, body: { status: "STOPPED" } })}>Stopped</button>}
              </div>
            ))}
          {canWrite && (
            <form onSubmit={e => { e.preventDefault(); if (!m.medicineName.trim()) return; add.mutate({ kind: "medications", body: bodies.medication(m) }, { onSuccess: () => setM({ medicineName: "", dose: "", frequency: "" }) }) }}
              style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8 }}>
              <input aria-label="Medicine" placeholder="Medicine" value={m.medicineName} onChange={e => setM({ ...m, medicineName: e.target.value })} style={{ ...input, flex: "1 1 140px" }} />
              <input aria-label="Dose" placeholder="Dose" value={m.dose} onChange={e => setM({ ...m, dose: e.target.value })} style={{ ...input, width: 90 }} />
              <input aria-label="Frequency" placeholder="Frequency" value={m.frequency} onChange={e => setM({ ...m, frequency: e.target.value })} style={{ ...input, width: 110 }} />
              <button type="submit" disabled={add.isPending || !m.medicineName.trim()} style={{ ...input, cursor: "pointer", fontWeight: 600 }}>Add medicine</button>
            </form>
          )}
        </Section>
      )}
    </div>
  )
}
