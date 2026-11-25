// Lessons for programme versions. Backed by /api/v1/training-provider/lessons.
// A lesson is approved by a second person, never edited once approved, and retired with a reason.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { KINDS, STATUSES, approvalNote, emptyLesson, label, lessonBody, lessonForm, lessonProblem, reasonProblem, statusTone, updateBody, type Lesson, type LessonForm } from "./lessonRules"
import type { ProgrammeDetail } from "./catalogueRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { CAT, LSN, useActiveProgrammes } from "./tpApi"
import TrainProvLmsLinkSection from "./TrainProvLmsLinkSection"

function LessonFormPanel({ initial, onSaved, onCancel }: { initial?: Lesson; onSaved: () => void; onCancel: () => void }) {
  const { data: programmes = [] } = useActiveProgrammes()
  const [f, setF] = useState<LessonForm>(initial ? lessonForm(initial) : emptyLesson)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: detail } = useQuery<ProgrammeDetail>({
    queryKey: ["tp-programme-detail", f.programmeId], enabled: !!f.programmeId,
    queryFn: async () => unwrap<ProgrammeDetail>(await apiClient.get(`${CAT}/programmes/${f.programmeId}`)),
  })
  const versions = (detail?.versions ?? []).filter(v => v.status !== "SUPERSEDED")
  const modules = detail?.versions.find(v => v.id === f.programmeVersionId)?.modules ?? []
  const set = (k: keyof LessonForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = lessonProblem(f, !initial); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      await (initial ? apiClient.put(`${LSN}/lessons/${initial.id}`, updateBody(f)) : apiClient.post(`${LSN}/lessons`, lessonBody(f)))
      onSaved()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      {!initial && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
          <select aria-label="Programme" style={input} value={f.programmeId} onChange={e => setF({ ...f, programmeId: e.target.value, programmeVersionId: "", moduleId: "" })}>
            <option value="">Choose the programme</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}
          </select>
          <select aria-label="Version" style={input} value={f.programmeVersionId} onChange={e => setF({ ...f, programmeVersionId: e.target.value, moduleId: "" })}>
            <option value="">Choose the version</option>{versions.map(v => <option key={v.id} value={v.id}>Version {v.versionNo} · {label(STATUSES, v.status)}</option>)}
          </select>
          <select aria-label="Module" style={input} value={f.moduleId} onChange={set("moduleId")}>
            <option value="">Whole programme version</option>{modules.map(m => <option key={m.id} value={m.id}>{m.seq}. {m.title}</option>)}
          </select>
        </div>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: 8 }}>
        <input aria-label="Title" style={input} placeholder="Title" value={f.title} onChange={set("title")} />
        <select aria-label="Kind" style={input} value={f.kind} onChange={set("kind")}>{Object.entries(KINDS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <input aria-label="Position" style={input} inputMode="numeric" placeholder="Position" value={f.position} onChange={set("position")} />
      </div>
      {f.kind === "READING"
        ? <textarea aria-label="Reading" style={input} placeholder="The reading" value={f.contentText} onChange={set("contentText")} />
        : <input aria-label="Link" style={input} placeholder="https://…" value={f.url} onChange={set("url")} />}
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Create lesson"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function LessonCard({ l, onChanged }: { l: Lesson; onChanged: () => void }) {
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [mode, setMode] = useState<"" | "edit" | "approve" | "retire">("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const close = () => { setMode(""); setReason(""); setError("") }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); close(); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const approve = () => {
    if (reason.trim() && reasonProblem(reason)) { setError(reasonProblem(reason) ?? ""); return }
    void run(() => apiClient.post(`${LSN}/lessons/${l.id}/approve`, { overrideReason: reason.trim() || null }))
  }
  const retire = () => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    void run(() => apiClient.post(`${LSN}/lessons/${l.id}/retire`, { reason: reason.trim() }))
  }
  if (mode === "edit") return <LessonFormPanel initial={l} onCancel={close} onSaved={() => { close(); onChanged() }} />
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{l.position}. {l.title}</strong><div style={muted}>{l.lessonNo} · {label(KINDS, l.kind)} · version {l.versionNo}{l.moduleTitle ? ` · ${l.moduleTitle}` : ""}</div></div>
        <Badge text={label(STATUSES, l.status)} tone={statusTone(l.status)} />
      </div>
      {l.contentText && <div style={{ marginTop: 6 }}>{l.contentText}</div>}
      {l.url && <div style={{ marginTop: 6 }}><a href={l.url} target="_blank" rel="noreferrer noopener">{l.url}</a></div>}
      <div style={muted}>{approvalNote(l)}</div>
      {l.retiredReason && <div style={muted}>Retired: {l.retiredReason}</div>}
      {canManage && mode === "" && l.status !== "RETIRED" && (
        <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
          {l.status === "DRAFT" && <button style={ghost} onClick={() => setMode("edit")}>Edit</button>}
          {l.nextStatuses.includes("APPROVED") && <button style={ghost} onClick={() => setMode("approve")}>Approve</button>}
          <button style={ghost} onClick={() => setMode("retire")}>Retire</button>
        </div>
      )}
      {mode === "approve" && (
        <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
          <div style={muted}>A second person approves. Only if you wrote this lesson and nobody else can approve it, say why below. The reason is kept on the record.</div>
          <input aria-label="Why no second person" style={input} placeholder="Why no second person can approve it (only if you wrote it)" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={approve}>Approve lesson</button><button style={ghost} onClick={close}>Cancel</button></div>
        </div>
      )}
      {mode === "retire" && (
        <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
          <div style={muted}>A retired lesson stays on record and cannot be used again. Create a replacement if one is needed.</div>
          <input aria-label="Reason for retiring" style={input} placeholder="Why is this lesson being retired?" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={retire}>Retire lesson</button><button style={ghost} onClick={close}>Cancel</button></div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvLessonsTab() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [status, setStatus] = useState("")
  const [adding, setAdding] = useState(false)
  const { data = [], isLoading } = useQuery<Lesson[]>({
    queryKey: ["tp-lessons", status],
    queryFn: async () => unwrap<Lesson[]>(await apiClient.get(`${LSN}/lessons`, { params: { status: status || undefined } })),
  })
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-lessons"] }); void qc.invalidateQueries({ queryKey: ["tp-lesson-progress"] }) }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Lesson status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All statuses</option>{Object.entries(STATUSES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>New lesson</button>}
      </div>
      {adding && <LessonFormPanel onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); changed() }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No lessons found.</div> : data.map(l => <LessonCard key={l.id} l={l} onChanged={changed} />)}
      <TrainProvLmsLinkSection />
    </div>
  )
}
