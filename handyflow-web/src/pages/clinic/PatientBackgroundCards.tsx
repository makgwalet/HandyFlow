// Family history, lifestyle and social history, and medical aid on the patient overview.
// Each card loads its own data and appears only for people who may read it; editing needs the write permission.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { GRAY, RED_TEXT } from "./patientFile.shared"
import { Card, Fact, field, primaryBtn, smallBtn } from "./OverviewCard"
import {
  ALCOHOL, EMPTY_AID, EMPTY_FAMILY, RELATIVES, RELATIVE_LABEL, SMOKING, SUBSTANCE, CHOICE_LABEL,
  aidBody, aidForm, aidProblem, familyBody, familyProblem, socialBody, socialForm, socialLines, type AidForm, type FamilyForm, type SocialForm,
} from "./historyView"

const unwrap = (r: any) => r?.data?.data ?? r?.data
const base = (id: string) => `/api/v1/clinic/patients/${id}`
const msg = (e: any, fallback: string) => e?.response?.data?.message ?? fallback
const Muted = ({ children }: { children: React.ReactNode }) => <div style={{ fontSize: 12, color: GRAY }}>{children}</div>
const Err = ({ children }: { children: React.ReactNode }) => <div role="alert" style={{ marginTop: 6, fontSize: 12, color: RED_TEXT }}>{children}</div>
const Row = ({ children }: { children: React.ReactNode }) => <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8, alignItems: "center" }}>{children}</div>

// ── Family history ───────────────────────────────────────────────────────────

interface FamilyEntry { id: string; relative: string; conditionName: string; ageAtOnset?: number | null; notes?: string | null }

export function FamilyHistoryCard({ patientId }: { patientId: string }) {
  const canRead = usePermission("CLINIC_CLINICAL_HISTORY_READ")
  const canWrite = usePermission("CLINIC_CLINICAL_HISTORY_WRITE")
  const qc = useQueryClient()
  const key = ["pf-family-history", patientId]
  const { data, isError } = useQuery<FamilyEntry[]>({ queryKey: key, enabled: canRead, retry: false,
    queryFn: async () => unwrap(await apiClient.get(`${base(patientId)}/family-history`)) ?? [] })
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState<FamilyForm>(EMPTY_FAMILY)
  const [error, setError] = useState("")
  const done = () => { setError(""); qc.invalidateQueries({ queryKey: key }) }
  const add = useMutation({ mutationFn: (b: object) => apiClient.post(`${base(patientId)}/family-history`, b),
    onSuccess: () => { done(); setForm(EMPTY_FAMILY); setAdding(false) }, onError: (e: any) => setError(msg(e, "Could not save.")) })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.patch(`${base(patientId)}/family-history/${id}`, { status: "ENTERED_IN_ERROR" }),
    onSuccess: done, onError: (e: any) => setError(msg(e, "Could not save.")) })
  if (!canRead) return null

  const submit = () => { const p = familyProblem(form); if (p) { setError(p); return } setError(""); add.mutate(familyBody(form)) }
  return (
    <Card title="Family history" aside={canWrite && !adding ? <button type="button" style={smallBtn} onClick={() => { setError(""); setAdding(true) }}>Add</button> : undefined}>
      {isError ? <Muted>Family history could not be loaded.</Muted>
        : (data ?? []).length === 0 ? <Muted>No family history recorded.</Muted>
        : (data ?? []).map(h => (
          <div key={h.id} style={{ display: "flex", gap: 8, alignItems: "baseline", padding: "3px 0", fontSize: 13 }}>
            <span style={{ width: 96, flexShrink: 0, color: GRAY, fontSize: 12 }}>{RELATIVE_LABEL[h.relative] ?? h.relative}</span>
            <span style={{ flex: 1, minWidth: 0 }}><b>{h.conditionName}</b>{h.ageAtOnset != null && <span style={{ color: GRAY }}> · from age {h.ageAtOnset}</span>}{h.notes && <span style={{ color: GRAY }}> · {h.notes}</span>}</span>
            {canWrite && <button type="button" aria-label={`Remove ${h.conditionName}`} style={{ ...smallBtn, border: "none", color: "var(--hf-accent-text)", padding: 0 }} onClick={() => remove.mutate(h.id)}>Remove</button>}
          </div>
        ))}
      {adding && (
        <Row>
          <select aria-label="Relative" value={form.relative} onChange={e => setForm({ ...form, relative: e.target.value })} style={field}>
            {RELATIVES.map(r => <option key={r} value={r}>{RELATIVE_LABEL[r]}</option>)}</select>
          <input aria-label="Family condition" placeholder="Condition" value={form.conditionName} onChange={e => setForm({ ...form, conditionName: e.target.value })} style={{ ...field, flex: "1 1 140px" }} />
          <input aria-label="Age at onset" placeholder="Age" inputMode="numeric" value={form.ageAtOnset} onChange={e => setForm({ ...form, ageAtOnset: e.target.value })} style={{ ...field, width: 60 }} />
          <input aria-label="Family history notes" placeholder="Notes (optional)" value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} style={{ ...field, flex: "1 1 140px" }} />
          <button type="button" style={primaryBtn} disabled={add.isPending} onClick={submit}>{add.isPending ? "Saving..." : "Save"}</button>
          <button type="button" style={smallBtn} onClick={() => { setAdding(false); setError(""); setForm(EMPTY_FAMILY) }}>Cancel</button>
        </Row>
      )}
      {error && <Err>{error}</Err>}
    </Card>
  )
}

// ── Lifestyle and social history ─────────────────────────────────────────────

export function SocialHistoryCard({ patientId }: { patientId: string }) {
  const canRead = usePermission("CLINIC_CLINICAL_HISTORY_READ")
  const canWrite = usePermission("CLINIC_CLINICAL_HISTORY_WRITE")
  const qc = useQueryClient()
  const key = ["pf-social-history", patientId]
  const { data, isError } = useQuery<any>({ queryKey: key, enabled: canRead, retry: false,
    queryFn: async () => unwrap(await apiClient.get(`${base(patientId)}/social-history`)) })
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<SocialForm>(socialForm(null))
  const [error, setError] = useState("")
  const save = useMutation({ mutationFn: (b: object) => apiClient.put(`${base(patientId)}/social-history`, b),
    onSuccess: () => { setError(""); setEditing(false); qc.invalidateQueries({ queryKey: key }) }, onError: (e: any) => setError(msg(e, "Could not save.")) })
  if (!canRead) return null

  const lines = socialLines(data)
  const choice = (label: string, value: string, set: (v: string) => void, options: readonly string[]) => (
    <label style={{ fontSize: 11, color: GRAY, display: "flex", flexDirection: "column", gap: 2 }}>{label}
      <select aria-label={label} value={value} onChange={e => set(e.target.value)} style={field}>{options.map(o => <option key={o} value={o}>{CHOICE_LABEL[o]}</option>)}</select>
    </label>)
  return (
    <Card title="Lifestyle and social history" aside={canWrite && !editing ? <button type="button" style={smallBtn} onClick={() => { setForm(socialForm(data)); setError(""); setEditing(true) }}>{lines.length ? "Edit" : "Add"}</button> : undefined}>
      {isError ? <Muted>Lifestyle and social history could not be loaded.</Muted>
        : !editing && (lines.length === 0 ? <Muted>Nothing recorded yet.</Muted> : lines.map(([k, v]) => <Fact key={k} k={k} v={v} />))}
      {editing && (
        <div>
          <Row>
            {choice("Smoking", form.smokingStatus, v => setForm({ ...form, smokingStatus: v }), SMOKING)}
            {choice("Alcohol", form.alcoholUse, v => setForm({ ...form, alcoholUse: v }), ALCOHOL)}
            {choice("Other substances", form.substanceUse, v => setForm({ ...form, substanceUse: v }), SUBSTANCE)}
          </Row>
          <Row>
            <input aria-label="Occupation" placeholder="Occupation" value={form.occupation} onChange={e => setForm({ ...form, occupation: e.target.value })} style={{ ...field, flex: "1 1 140px" }} />
            <input aria-label="Living situation" placeholder="Living situation" value={form.livingSituation} onChange={e => setForm({ ...form, livingSituation: e.target.value })} style={{ ...field, flex: "1 1 140px" }} />
            <input aria-label="Physical activity" placeholder="Physical activity" value={form.physicalActivity} onChange={e => setForm({ ...form, physicalActivity: e.target.value })} style={{ ...field, flex: "1 1 140px" }} />
          </Row>
          <Row>
            <input aria-label="Social history notes" placeholder="Notes (optional)" value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} style={{ ...field, flex: "1 1 200px" }} />
            <button type="button" style={primaryBtn} disabled={save.isPending} onClick={() => save.mutate(socialBody(form))}>{save.isPending ? "Saving..." : "Save"}</button>
            <button type="button" style={smallBtn} onClick={() => { setEditing(false); setError("") }}>Cancel</button>
          </Row>
        </div>
      )}
      {error && <Err>{error}</Err>}
    </Card>
  )
}

// ── Medical aid ──────────────────────────────────────────────────────────────

export function MedicalAidCard({ patientId }: { patientId: string }) {
  const canRead = usePermission("CLINIC_PATIENT_READ")
  const canWrite = usePermission("CLINIC_PATIENT_UPDATE")
  const qc = useQueryClient()
  const key = ["pf-medical-aid", patientId]
  const { data, isError } = useQuery<any | null>({ queryKey: key, enabled: canRead, retry: false,
    queryFn: async () => unwrap(await apiClient.get(`${base(patientId)}/medical-aid`)) ?? null })
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<AidForm>(EMPTY_AID)
  const [error, setError] = useState("")
  const refresh = () => { setError(""); setEditing(false); qc.invalidateQueries({ queryKey: key }) }
  const save = useMutation({ mutationFn: (b: object) => apiClient.put(`${base(patientId)}/medical-aid`, b), onSuccess: refresh, onError: (e: any) => setError(msg(e, "Could not save.")) })
  const remove = useMutation({ mutationFn: () => apiClient.delete(`${base(patientId)}/medical-aid`), onSuccess: refresh, onError: (e: any) => setError(msg(e, "Could not remove.")) })
  if (!canRead) return null

  const own = data && !data.inherited
  const submit = () => { const p = aidProblem(form); if (p) { setError(p); return } setError(""); save.mutate(aidBody(form)) }
  const text = (label: string, k: keyof AidForm, ph = label) =>
    <input aria-label={label} placeholder={ph} value={form[k]} onChange={e => setForm({ ...form, [k]: e.target.value })} style={{ ...field, flex: "1 1 130px" }} />
  return (
    <Card title="Medical aid" aside={canWrite && !editing ? <button type="button" style={smallBtn}
      onClick={() => { setForm(own ? aidForm(data) : EMPTY_AID); setError(""); setEditing(true) }}>{own ? "Edit" : data ? "Add own" : "Add medical aid"}</button> : undefined}>
      {isError ? <Muted>Medical aid could not be loaded.</Muted>
        : !data ? (!editing && <Muted>No medical aid recorded. Treated as a private patient.</Muted>)
        : !editing && (<>
          {data.inherited && <div style={{ fontSize: 11, color: GRAY, marginBottom: 4 }}>From {data.inheritedFrom ?? "the principal member"}'s account.</div>}
          <Fact k="Scheme" v={data.schemeName} />
          {data.planName && <Fact k="Plan" v={data.planName} />}
          <Fact k="Member number" v={data.memberNumber} />
          {data.dependentCode && <Fact k="Dependant code" v={data.dependentCode} />}
          {data.principalMember && <Fact k="Principal member" v={data.principalMember} />}
          {data.schemeContactPhone && <Fact k="Scheme phone" v={data.schemeContactPhone} />}
          {own && canWrite && <Row><button type="button" style={{ ...smallBtn, border: "none", color: RED_TEXT, padding: 0 }} disabled={remove.isPending} onClick={() => remove.mutate()}>Remove medical aid</button></Row>}
        </>)}
      {editing && (
        <div>
          <Row>{text("Medical scheme", "schemeName", "Scheme (e.g. Discovery)")}{text("Plan", "planName", "Plan / option")}</Row>
          <Row>{text("Member number", "memberNumber")}{text("Dependant code", "dependentCode")}</Row>
          <Row>{text("Principal member", "principalMember", "Principal member (if a dependant)")}{text("Scheme phone", "schemeContactPhone")}</Row>
          <Row>
            <button type="button" style={primaryBtn} disabled={save.isPending} onClick={submit}>{save.isPending ? "Saving..." : "Save"}</button>
            <button type="button" style={smallBtn} onClick={() => { setEditing(false); setError("") }}>Cancel</button>
          </Row>
        </div>
      )}
      {error && <Err>{error}</Err>}
    </Card>
  )
}
