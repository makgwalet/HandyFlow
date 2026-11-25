// src/pages/trainingprovider/TrainProvAssessmentsTab.tsx
//
// Assessment instruments for programme versions. Backed by /api/v1/training-provider/assessment-instruments.
// A maximum mark sets a scale only. Nothing here decides whether a learner is competent.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  ACTIONS, MARK_NOTE, STATUSES, TYPES, approvalNote, emptyInstrument, instrumentBody, instrumentForm, instrumentProblem, label, reasonProblem, statusTone, updateBody,
  type Instrument, type InstrumentForm,
} from "./assessmentRules"
import type { ProgrammeDetail } from "./catalogueRules"
import { Badge, Notice, SubTabs } from "./tpUi"
import TrainProvModerationQueue from "./TrainProvModerationQueue"
import TrainProvCompletionsList from "./TrainProvCompletionsList"
import { card, errText, fmtDate, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { ASM, CAT, useActiveProgrammes } from "./tpApi"

function InstrumentFormPanel({ initial, onSaved, onCancel }: { initial?: Instrument; onSaved: (i: Instrument) => void; onCancel: () => void }) {
  const { data: programmes = [] } = useActiveProgrammes()
  const [f, setF] = useState<InstrumentForm>(initial ? instrumentForm(initial) : emptyInstrument)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: detail } = useQuery<ProgrammeDetail>({
    queryKey: ["tp-programme-detail", f.programmeId], enabled: !!f.programmeId && !initial,
    queryFn: async () => unwrap<ProgrammeDetail>(await apiClient.get(`${CAT}/programmes/${f.programmeId}`)),
  })
  const { data: ownDetail } = useQuery<ProgrammeDetail>({
    queryKey: ["tp-programme-detail", initial?.programmeId], enabled: !!initial,
    queryFn: async () => unwrap<ProgrammeDetail>(await apiClient.get(`${CAT}/programmes/${initial?.programmeId}`)),
  })
  const versions = (detail?.versions ?? []).filter(v => v.status !== "SUPERSEDED")
  const modules = (initial ? ownDetail?.versions : detail?.versions)?.find(v => v.id === f.programmeVersionId)?.modules ?? []
  const set = (k: keyof InstrumentForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = instrumentProblem(f, !initial); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      onSaved(unwrap<Instrument>(await (initial ? apiClient.put(`${ASM}/${initial.id}`, updateBody(f)) : apiClient.post(ASM, instrumentBody(f)))))
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      {!initial && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <select aria-label="Programme" style={input} value={f.programmeId} onChange={e => setF({ ...f, programmeId: e.target.value, programmeVersionId: "", moduleId: "" })}>
            <option value="">Choose the programme</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}
          </select>
          <select aria-label="Version" style={input} value={f.programmeVersionId} onChange={e => setF({ ...f, programmeVersionId: e.target.value, moduleId: "" })}>
            <option value="">Choose the version</option>{versions.map(v => <option key={v.id} value={v.id}>Version {v.versionNo} · {label(STATUSES, v.status)}</option>)}
          </select>
        </div>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <input aria-label="Title" style={input} placeholder="Title" value={f.title} onChange={set("title")} />
        <select aria-label="Type" style={input} value={f.assessmentType} onChange={set("assessmentType")}>{Object.entries(TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <select aria-label="Module" style={input} value={f.moduleId} onChange={set("moduleId")}>
          <option value="">Whole programme version</option>{modules.map(m => <option key={m.id} value={m.id}>{m.seq}. {m.title}</option>)}
        </select>
        <input aria-label="Maximum mark" style={input} inputMode="decimal" placeholder="Maximum mark (leave empty if not marked)" value={f.maxMark} onChange={set("maxMark")} />
      </div>
      <textarea aria-label="Guidance" style={input} placeholder="Guidance for the assessor (optional)" value={f.guidance} onChange={set("guidance")} />
      <label style={{ fontSize: 13 }}><input type="checkbox" checked={f.requiresModeration} onChange={e => setF({ ...f, requiresModeration: e.target.checked })} /> Results from this instrument are moderated</label>
      <div style={muted}>{MARK_NOTE}</div>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Create instrument"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function InstrumentDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [mode, setMode] = useState<"" | "edit" | "approve" | "retire">("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: i, isLoading } = useQuery<Instrument>({ queryKey: ["tp-instrument", id], queryFn: async () => unwrap<Instrument>(await apiClient.get(`${ASM}/${id}`)) })
  const refresh = () => { void qc.invalidateQueries({ queryKey: ["tp-instrument", id] }); void qc.invalidateQueries({ queryKey: ["tp-instruments"] }) }
  if (isLoading || !i) return <div style={muted}>Loading…</div>
  const close = () => { setMode(""); setReason(""); setError("") }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); close(); refresh() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const approve = () => {
    if (reason.trim() && reasonProblem(reason)) { setError(reasonProblem(reason) ?? ""); return }
    void run(() => apiClient.post(`${ASM}/${id}/approve`, { overrideReason: reason.trim() || null }))
  }
  const retire = () => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    void run(() => apiClient.post(`${ASM}/${id}/retire`, { reason: reason.trim() }))
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div><button style={ghost} onClick={onBack}>Back to instruments</button></div>
      {mode === "edit" ? <InstrumentFormPanel initial={i} onCancel={close} onSaved={() => { close(); refresh() }} /> : (
        <div style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div>
              <strong>{i.title}</strong>
              <div style={muted}>{i.instrumentNo} · {label(TYPES, i.assessmentType)} · {i.programmeTitle ?? "Programme"} version {i.versionNo}{i.moduleTitle ? ` · ${i.moduleTitle}` : ""}</div>
            </div>
            <Badge text={label(STATUSES, i.status)} tone={statusTone(i.status)} />
          </div>
          <div style={muted}>{i.maxMark === null ? "Not marked" : `Maximum mark ${i.maxMark}`} · {i.requiresModeration ? "Results are moderated" : "Results are not moderated"}</div>
          <div style={muted}>{MARK_NOTE}</div>
          {i.guidance && <div style={{ marginTop: 6 }}>{i.guidance}</div>}
          <div style={muted}>{approvalNote(i)}</div>
          {i.retiredReason && <div style={muted}>Retired: {i.retiredReason}</div>}
          {canManage && mode === "" && i.status !== "RETIRED" && (
            <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
              {i.status === "DRAFT" && <button style={ghost} onClick={() => setMode("edit")}>Edit</button>}
              {i.nextStatuses.includes("APPROVED") && <button style={ghost} onClick={() => setMode("approve")}>Approve</button>}
              <button style={ghost} onClick={() => setMode("retire")}>Retire</button>
            </div>
          )}
          {mode === "approve" && (
            <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
              <div style={muted}>A second person approves. Only if you wrote this instrument and nobody else can approve it, say why below. The reason is kept on the record.</div>
              <input aria-label="Why no second person" style={input} placeholder="Why no second person can approve it (only if you wrote it)" value={reason} onChange={e => setReason(e.target.value)} />
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={approve}>Approve instrument</button><button style={ghost} onClick={close}>Cancel</button></div>
            </div>
          )}
          {mode === "retire" && (
            <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
              <div style={muted}>A retired instrument stays on record and cannot be used again. Create a replacement if one is needed.</div>
              <input aria-label="Reason for retiring" style={input} placeholder="Why is this instrument being retired?" value={reason} onChange={e => setReason(e.target.value)} />
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={retire}>Retire instrument</button><button style={ghost} onClick={close}>Cancel</button></div>
            </div>
          )}
        </div>
      )}
      <strong>History</strong>
      {i.history.length === 0 ? <div style={muted}>No history yet.</div> : i.history.map(h => (
        <div key={h.id} style={muted}>{fmtDate(h.actedAt.slice(0, 10))} · {label(ACTIONS, h.action)}{h.note ? ` · ${h.note}` : ""}</div>
      ))}
    </div>
  )
}

export default function TrainProvAssessmentsTab() {
  const [section, setSection] = useState("instruments")
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <SubTabs tabs={[["instruments", "Instruments"], ["moderation", "Moderation queue"], ["completions", "Completions"]]} value={section} onChange={setSection} />
      {section === "instruments" ? <Instruments /> : section === "moderation" ? <TrainProvModerationQueue /> : <TrainProvCompletionsList />}
    </div>
  )
}

function Instruments() {
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [status, setStatus] = useState("")
  const [programmeId, setProgrammeId] = useState("")
  const [adding, setAdding] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null)
  const { data: programmes = [] } = useActiveProgrammes()
  const { data = [], isLoading } = useQuery<Instrument[]>({
    queryKey: ["tp-instruments", status],
    queryFn: async () => unwrap<Instrument[]>(await apiClient.get(ASM, { params: { status: status || undefined } })),
  })
  if (openId) return <InstrumentDetail id={openId} onBack={() => setOpenId(null)} />
  const shown = programmeId ? data.filter(i => i.programmeId === programmeId) : data
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Programme filter" style={{ ...input, width: "auto" }} value={programmeId} onChange={e => setProgrammeId(e.target.value)}>
          <option value="">All programmes</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}
        </select>
        <select aria-label="Instrument status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All statuses</option>{Object.entries(STATUSES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Add an instrument</button>}
      </div>
      {adding && <InstrumentFormPanel onCancel={() => setAdding(false)} onSaved={i => { setAdding(false); setOpenId(i.id) }} />}
      <div style={muted}>{MARK_NOTE}</div>
      {isLoading ? <div style={muted}>Loading…</div> : shown.length === 0 ? <div style={muted}>No instruments found.</div> : shown.map(i => (
        <button key={i.id} onClick={() => setOpenId(i.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{i.title}</strong><div style={muted}>{i.instrumentNo} · {label(TYPES, i.assessmentType)} · {i.programmeTitle ?? "Programme"} version {i.versionNo}{i.moduleTitle ? ` · ${i.moduleTitle}` : ""}</div></div>
            <Badge text={label(STATUSES, i.status)} tone={statusTone(i.status)} />
          </div>
        </button>
      ))}
    </div>
  )
}
