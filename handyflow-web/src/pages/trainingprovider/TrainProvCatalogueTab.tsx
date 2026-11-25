// src/pages/trainingprovider/TrainProvCatalogueTab.tsx
//
// Qualifications and programmes, each with versions. Backed by /api/v1/training-provider/catalogue.
// No qualification, credit or level content is supplied: the provider records what its authority registered.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  ACTIVITIES, DELIVERY_MODES, PROGRAMME_TYPES, QUALIFICATION_TYPES, REGISTRATION_STATUSES, TYPES_BY_ACTIVITY, accreditedClaim, emptyProgramme, emptyQualification,
  emptyQualificationVersion, label, programmeBody, programmeProblem, qualificationBody, qualificationProblem, qualificationVersionBody, qualificationVersionProblem,
  reasonProblem, statusLabel, typeForActivity, type Programme, type ProgrammeForm, type Qualification, type QualificationDetail, type QualificationForm,
  type QualificationVersionForm,
} from "./catalogueRules"
import { Badge, Field, Notice, SubTabs } from "./tpUi"
import { errText, fmtDate, ghost, input, muted, solid, card, unwrap } from "./tpStyle"
import { CAT, useAuthorities } from "./tpApi"
import TrainProvProgrammeDetail from "./TrainProvProgrammeDetail"

function useQualifications() {
  return useQuery<Qualification[]>({ queryKey: ["tp-qualifications"], queryFn: async () => unwrap<Qualification[]>(await apiClient.get(`${CAT}/qualifications`)) })
}

function Programmes({ canManage, isAdmin }: { canManage: boolean; isAdmin: boolean }) {
  const qc = useQueryClient()
  const { data: quals = [] } = useQualifications()
  const [activity, setActivity] = useState("")
  const [openId, setOpenId] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState<ProgrammeForm>(emptyProgramme)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data = [], isLoading } = useQuery<Programme[]>({ queryKey: ["tp-programmes", activity], queryFn: async () => unwrap<Programme[]>(await apiClient.get(`${CAT}/programmes`, { params: { activity: activity || undefined } })) })
  if (openId) return <TrainProvProgrammeDetail id={openId} canManage={canManage} isAdmin={isAdmin} onBack={() => setOpenId(null)} />
  const create = async () => {
    const p = programmeProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      const v = unwrap<Programme>(await apiClient.post(`${CAT}/programmes`, programmeBody(f)))
      setAdding(false); setF(emptyProgramme); qc.invalidateQueries({ queryKey: ["tp-programmes"] }); setOpenId(v.id)
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Filter by activity" style={{ ...input, width: "auto" }} value={activity} onChange={e => setActivity(e.target.value)}>
          <option value="">All kinds of training</option>{Object.entries(ACTIVITIES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        {canManage && !adding && <button style={solid} onClick={() => { setAdding(true); setError("") }}>New programme</button>}
      </div>
      {adding && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Programme code" style={input} placeholder="Code" value={f.code} onChange={e => setF({ ...f, code: e.target.value })} />
          <input aria-label="Programme title" style={input} placeholder="Title" value={f.title} onChange={e => setF({ ...f, title: e.target.value })} />
          <Field label="Kind of training"><select aria-label="Training activity" style={input} value={f.trainingActivity} onChange={e => setF({ ...f, trainingActivity: e.target.value, programmeType: typeForActivity(e.target.value, f.programmeType) })}>{Object.entries(ACTIVITIES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></Field>
          <select aria-label="Programme type" style={input} value={f.programmeType} onChange={e => setF({ ...f, programmeType: e.target.value })}>{(TYPES_BY_ACTIVITY[f.trainingActivity] ?? []).map(k => <option key={k} value={k}>{PROGRAMME_TYPES[k] ?? k}</option>)}</select>
          <select aria-label="Delivery mode" style={input} value={f.deliveryMode} onChange={e => setF({ ...f, deliveryMode: e.target.value })}>{Object.entries(DELIVERY_MODES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          <select aria-label="Qualification" style={input} value={f.qualificationId} onChange={e => setF({ ...f, qualificationId: e.target.value })}>
            <option value="">{f.trainingActivity === "ACCREDITED" ? "Choose the qualification (required)" : "No qualification"}</option>{quals.filter(q => q.status === "ACTIVE").map(q => <option key={q.id} value={q.id}>{q.title}</option>)}
          </select>
          <textarea aria-label="Description" style={{ ...input, minHeight: 50 }} placeholder="Description (optional)" value={f.description} onChange={e => setF({ ...f, description: e.target.value })} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={create}>Create</button><button style={ghost} onClick={() => setAdding(false)}>Cancel</button></div>
        </div>
      )}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No programmes yet.</div> : data.map(p => (
        <button key={p.id} onClick={() => setOpenId(p.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{p.title}</strong><div style={muted}>{p.code} · {label(PROGRAMME_TYPES, p.programmeType)} · {p.liveOfferings} offering{p.liveOfferings === 1 ? "" : "s"}</div></div>
            <div style={{ display: "grid", justifyItems: "end", gap: 4 }}><Badge text={statusLabel(p.status)} tone={p.status === "ACTIVE" ? "good" : "neutral"} /><span style={muted}>{accreditedClaim(p)}</span></div>
          </div>
        </button>
      ))}
    </div>
  )
}

function QualificationView({ id, canManage, isAdmin, onBack }: { id: string; canManage: boolean; isAdmin: boolean; onBack: () => void }) {
  const qc = useQueryClient()
  const { data, isLoading } = useQuery<QualificationDetail>({ queryKey: ["tp-qualification", id], queryFn: async () => unwrap<QualificationDetail>(await apiClient.get(`${CAT}/qualifications/${id}`)) })
  const [panel, setPanel] = useState("")
  const [vf, setVf] = useState<QualificationVersionForm>(emptyQualificationVersion)
  const [editing, setEditing] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setPanel(""); setEditing(null); setReason(""); qc.invalidateQueries({ queryKey: ["tp-qualification", id] }); qc.invalidateQueries({ queryKey: ["tp-qualifications"] }) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  const q = data.qualification
  const hasDraft = data.versions.some(v => v.status === "DRAFT")
  const save = () => {
    const m = qualificationVersionProblem(vf); if (m) { setError(m); return }
    run(() => (editing ? apiClient.put(`${CAT}/qualification-versions/${editing}`, qualificationVersionBody(vf)) : apiClient.post(`${CAT}/qualifications/${id}/versions`, qualificationVersionBody(vf))))
  }
  const set = (k: keyof QualificationVersionForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => setVf({ ...vf, [k]: e.target.value })
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div><button style={ghost} onClick={onBack}>← All qualifications</button></div>
      <div style={card}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
          <div><strong style={{ fontSize: 16 }}>{q.title}</strong><div style={muted}>{label(QUALIFICATION_TYPES, q.qualificationType)}{q.officialRef ? ` · ${q.officialRef}` : ""}{q.saqaId ? ` · SAQA ${q.saqaId}` : ""}{q.nqfLevel != null ? ` · NQF ${q.nqfLevel}` : ""}{q.credits != null ? ` · ${q.credits} credits` : ""}</div></div>
          <Badge text={statusLabel(q.status)} tone={q.status === "ACTIVE" ? "good" : "neutral"} />
        </div>
        <div style={muted}>{q.currentVersionNo ? `Version ${q.currentVersionNo} is current` : "No current version yet"}</div>
        {q.retiredReason && <div style={muted}>Retired: {q.retiredReason}</div>}
        {canManage && q.status === "ACTIVE" && (
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            {!hasDraft && <button style={solid} onClick={() => { setPanel("version"); setEditing(null); setVf(emptyQualificationVersion); setError("") }}>New version</button>}
            {isAdmin && <button style={ghost} onClick={() => { setPanel("retire"); setError("") }}>Retire</button>}
          </div>
        )}
      </div>
      {panel === "retire" && (
        <div style={{ ...card, display: "grid", gap: 6, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Retire qualification reason" style={input} placeholder="Why is this being retired? Nothing is deleted" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => { const m = reasonProblem(reason); if (m) setError(m); else run(() => apiClient.post(`${CAT}/qualifications/${id}/retire`, { reason: reason.trim() })) }}>Retire</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}
      {panel === "version" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <Field label="Registration status, as the authority shows it"><select aria-label="Registration status" style={input} value={vf.registrationStatus} onChange={set("registrationStatus")}>{Object.entries(REGISTRATION_STATUSES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></Field>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <input aria-label="Registration start" type="date" style={input} value={vf.registrationStart} onChange={set("registrationStart")} />
            <input aria-label="Registration end" type="date" style={input} value={vf.registrationEnd} onChange={set("registrationEnd")} />
          </div>
          <textarea aria-label="Entry requirements" style={{ ...input, minHeight: 44 }} placeholder="Entry requirements" value={vf.entryRequirements} onChange={set("entryRequirements")} />
          <textarea aria-label="Exit outcomes" style={{ ...input, minHeight: 44 }} placeholder="Exit outcomes" value={vf.exitOutcomes} onChange={set("exitOutcomes")} />
          <textarea aria-label="Rules of combination" style={{ ...input, minHeight: 44 }} placeholder="Rules of combination" value={vf.rulesOfCombination} onChange={set("rulesOfCombination")} />
          <input aria-label="Source note" style={input} placeholder="Where this came from, for example the register entry or letter" value={vf.sourceNote} onChange={set("sourceNote")} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{editing ? "Save version" : "Add version"}</button><button style={ghost} onClick={() => setPanel("")}>Cancel</button></div>
        </div>
      )}
      <strong>Versions</strong>
      {data.versions.length === 0 ? <div style={muted}>No versions yet.</div> : data.versions.map(v => (
        <div key={v.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center" }}>
            <div><strong>Version {v.versionNo}</strong> <span style={muted}>{label(REGISTRATION_STATUSES, v.registrationStatus)} · {fmtDate(v.registrationStart)} to {fmtDate(v.registrationEnd)}</span></div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <Badge text={statusLabel(v.status)} tone={v.status === "CURRENT" ? "good" : "neutral"} />
              {canManage && v.status === "DRAFT" && <button style={ghost} onClick={() => { setPanel("version"); setEditing(v.id); setError(""); setVf({ registrationStatus: v.registrationStatus, registrationStart: v.registrationStart ?? "", registrationEnd: v.registrationEnd ?? "", entryRequirements: v.entryRequirements ?? "", exitOutcomes: v.exitOutcomes ?? "", rulesOfCombination: v.rulesOfCombination ?? "", sourceNote: v.sourceNote ?? "", effectiveFrom: v.effectiveFrom ?? "" }) }}>Edit</button>}
              {isAdmin && v.status === "DRAFT" && <button style={solid} disabled={busy} onClick={() => run(() => apiClient.post(`${CAT}/qualification-versions/${v.id}/make-current`))}>Make current</button>}
            </div>
          </div>
          {v.sourceNote && <div style={muted}>Source: {v.sourceNote}</div>}
          {v.entryRequirements && <div style={{ fontSize: 13 }}><strong>Entry:</strong> {v.entryRequirements}</div>}
          {v.exitOutcomes && <div style={{ fontSize: 13 }}><strong>Exit outcomes:</strong> {v.exitOutcomes}</div>}
          {v.rulesOfCombination && <div style={{ fontSize: 13 }}><strong>Rules of combination:</strong> {v.rulesOfCombination}</div>}
        </div>
      ))}
    </div>
  )
}

function Qualifications({ canManage, isAdmin }: { canManage: boolean; isAdmin: boolean }) {
  const qc = useQueryClient()
  const { data = [], isLoading } = useQualifications()
  const { data: authorities = [] } = useAuthorities()
  const [openId, setOpenId] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState<QualificationForm>(emptyQualification)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  if (openId) return <QualificationView id={openId} canManage={canManage} isAdmin={isAdmin} onBack={() => setOpenId(null)} />
  const create = async () => {
    const p = qualificationProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      const v = unwrap<Qualification>(await apiClient.post(`${CAT}/qualifications`, qualificationBody(f)))
      setAdding(false); setF(emptyQualification); qc.invalidateQueries({ queryKey: ["tp-qualifications"] }); setOpenId(v.id)
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const set = (k: keyof QualificationForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>Record each qualification or unit standard exactly as its authority registered it. Nothing is supplied for you.</div>
      {canManage && !adding && <div><button style={solid} onClick={() => { setAdding(true); setError("") }}>New qualification</button></div>}
      {adding && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Qualification title" style={input} placeholder="Title" value={f.title} onChange={set("title")} />
          <select aria-label="Qualification type" style={input} value={f.qualificationType} onChange={set("qualificationType")}>{Object.entries(QUALIFICATION_TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
          <input aria-label="Official reference" style={input} placeholder="Official reference" value={f.officialRef} onChange={set("officialRef")} />
          <input aria-label="SAQA ID" style={input} placeholder="SAQA ID" value={f.saqaId} onChange={set("saqaId")} />
          <input aria-label="Sub-framework" style={input} placeholder="Sub-framework" value={f.subFramework} onChange={set("subFramework")} />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
            <input aria-label="Qualification NQF level" style={input} placeholder="NQF level" value={f.nqfLevel} onChange={set("nqfLevel")} />
            <input aria-label="Qualification credits" style={input} placeholder="Credits" value={f.credits} onChange={set("credits")} />
          </div>
          <select aria-label="Qualification authority" style={input} value={f.authorityId} onChange={set("authorityId")}><option value="">Authority (optional)</option>{authorities.filter(a => a.status === "ACTIVE").map(a => <option key={a.id} value={a.id}>{a.name}</option>)}</select>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={create}>Create</button><button style={ghost} onClick={() => setAdding(false)}>Cancel</button></div>
        </div>
      )}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No qualifications recorded yet.</div> : data.map(q => (
        <button key={q.id} onClick={() => setOpenId(q.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
            <div><strong>{q.title}</strong><div style={muted}>{label(QUALIFICATION_TYPES, q.qualificationType)}{q.officialRef ? ` · ${q.officialRef}` : ""}{q.nqfLevel != null ? ` · NQF ${q.nqfLevel}` : ""} · {q.versions} version{q.versions === 1 ? "" : "s"}</div></div>
            <Badge text={statusLabel(q.status)} tone={q.status === "ACTIVE" ? "good" : "neutral"} />
          </div>
        </button>
      ))}
    </div>
  )
}

export default function TrainProvCatalogueTab() {
  const isAdmin = usePermissions(["TRAININGPROVIDER_ADMIN"])
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [tab, setTab] = useState("programmes")
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>Programme catalogue</h2>
        <div style={muted}>Qualifications and programmes with approved versions, modules and where each is offered. An accredited programme is only offered where an approval covers it.</div></div>
      <SubTabs value={tab} onChange={setTab} tabs={[["programmes", "Programmes"], ["qualifications", "Qualifications"]]} />
      {tab === "programmes" ? <Programmes canManage={canManage} isAdmin={isAdmin} /> : <Qualifications canManage={canManage} isAdmin={isAdmin} />}
    </div>
  )
}
