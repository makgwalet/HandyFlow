// Links to an outside learning system, one active link per programme version. Ended links stay listed.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { LMS_NOTE, STATUSES, emptyLink, endProblem, label, linkBody, linkForm, linkProblem, statusTone, updateBody, type LinkForm, type LmsLink } from "./lmsRules"
import { STATUSES as VERSION_STATUSES } from "./lessonRules"
import type { ProgrammeDetail } from "./catalogueRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { CAT, LMS, useActiveProgrammes } from "./tpApi"

function LinkFormPanel({ initial, onSaved, onCancel }: { initial?: LmsLink; onSaved: () => void; onCancel: () => void }) {
  const { data: programmes = [] } = useActiveProgrammes()
  const [f, setF] = useState<LinkForm>(initial ? linkForm(initial) : emptyLink)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: detail } = useQuery<ProgrammeDetail>({
    queryKey: ["tp-programme-detail", f.programmeId], enabled: !!f.programmeId,
    queryFn: async () => unwrap<ProgrammeDetail>(await apiClient.get(`${CAT}/programmes/${f.programmeId}`)),
  })
  const versions = (detail?.versions ?? []).filter(v => v.status !== "SUPERSEDED")
  const set = (k: keyof LinkForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = linkProblem(f, !initial); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await (initial ? apiClient.put(`${LMS}/${initial.id}`, updateBody(f)) : apiClient.post(LMS, linkBody(f))); onSaved() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      {!initial && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <select aria-label="Link programme" style={input} value={f.programmeId} onChange={e => setF({ ...f, programmeId: e.target.value, programmeVersionId: "" })}>
            <option value="">Choose the programme</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}
          </select>
          <select aria-label="Link version" style={input} value={f.programmeVersionId} onChange={set("programmeVersionId")}>
            <option value="">Choose the version</option>{versions.map(v => <option key={v.id} value={v.id}>Version {v.versionNo} · {label(VERSION_STATUSES, v.status)}</option>)}
          </select>
        </div>
      )}
      <input aria-label="Learning system name" style={input} placeholder="Name of the learning system" value={f.systemName} onChange={set("systemName")} />
      <input aria-label="Launch link" style={input} placeholder="https://…" value={f.launchUrl} onChange={set("launchUrl")} />
      <textarea aria-label="Link notes" style={input} placeholder="Notes (optional)" value={f.notes} onChange={set("notes")} />
      <div style={muted}>{LMS_NOTE}</div>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Add link"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function LinkCard({ l, onChanged }: { l: LmsLink; onChanged: () => void }) {
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [mode, setMode] = useState<"" | "edit" | "end">("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const close = () => { setMode(""); setReason(""); setError("") }
  const end = async () => {
    const p = endProblem(reason); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${LMS}/${l.id}/end`, { reason: reason.trim() }); close(); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (mode === "edit") return <LinkFormPanel initial={l} onCancel={close} onSaved={() => { close(); onChanged() }} />
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{l.systemName}</strong><div style={muted}>{l.programmeTitle ?? "Programme"} version {l.versionNo}</div></div>
        <Badge text={label(STATUSES, l.status)} tone={statusTone(l.status)} />
      </div>
      <div style={{ marginTop: 6 }}><a href={l.launchUrl} target="_blank" rel="noreferrer noopener">{l.launchUrl}</a></div>
      {l.notes && <div style={muted}>{l.notes}</div>}
      {l.endedReason && <div style={muted}>Ended: {l.endedReason}</div>}
      {canManage && l.status === "ACTIVE" && mode === "" && (
        <div style={{ display: "flex", gap: 8, marginTop: 8 }}><button style={ghost} onClick={() => setMode("edit")}>Edit link</button><button style={ghost} onClick={() => setMode("end")}>End link</button></div>
      )}
      {mode === "end" && (
        <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
          <div style={muted}>An ended link stays on record. Add a new one if learners should use a different address.</div>
          <input aria-label="Reason for ending the link" style={input} placeholder="Why is this link being ended?" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={end}>End the link</button><button style={ghost} onClick={close}>Cancel</button></div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvLmsLinkSection() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [adding, setAdding] = useState(false)
  const [showEnded, setShowEnded] = useState(false)
  const { data = [], isLoading } = useQuery<LmsLink[]>({
    queryKey: ["tp-lms-links", showEnded],
    queryFn: async () => unwrap<LmsLink[]>(await apiClient.get(LMS, { params: { status: showEnded ? undefined : "ACTIVE" } })),
  })
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-lms-links"] }); void qc.invalidateQueries({ queryKey: ["tp-lms-link"] }) }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <strong>Learning system links</strong>
      <div style={muted}>{LMS_NOTE}</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Add a link</button>}
        <label style={{ fontSize: 13 }}><input type="checkbox" checked={showEnded} onChange={e => setShowEnded(e.target.checked)} /> Show ended links</label>
      </div>
      {adding && <LinkFormPanel onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); changed() }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No links found.</div> : data.map(l => <LinkCard key={l.id} l={l} onChanged={changed} />)}
    </div>
  )
}
