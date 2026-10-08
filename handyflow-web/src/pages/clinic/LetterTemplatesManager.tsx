// Manage the clinic's letter templates: list by kind, add, change, archive.
import { useEffect, useState } from "react"
import { apiClient } from "../../api/client"
import ModalShell from "./ModalShell"
import { BORDER, GRAY, RED_TEXT, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { FIELDS, KINDS, KIND_LABEL, MERGE_FIELDS, mergeToken, templateProblem, type Kind, type Template } from "./letterView"

const unwrap = (r: any) => r?.data?.data ?? r?.data
const blank = (kind: Kind): Draft => ({ kind, name: "", title: "", body: "", specialty: "", urgency: "", unfitDays: "" })
interface Draft { id?: string; kind: Kind; name: string; title: string; body: string; specialty: string; urgency: string; unfitDays: string }
const toDraft = (t: Template): Draft => ({ id: t.id, kind: t.kind, name: t.name, title: t.title ?? "", body: t.body ?? "", specialty: t.specialty ?? "", urgency: t.urgency ?? "", unfitDays: t.unfitDays ? String(t.unfitDays) : "" })

export default function LetterTemplatesManager({ onClose }: { onClose: () => void }) {
  const [list, setList] = useState<Template[]>([])
  const [loadError, setLoadError] = useState(false)
  const [edit, setEdit] = useState<Draft | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)

  const load = () => apiClient.get("/api/v1/clinic/letter-templates").then(r => { setList(unwrap(r) ?? []); setLoadError(false) }).catch(() => setLoadError(true))
  useEffect(() => { load() }, [])

  const save = async () => {
    if (!edit) return
    const problem = templateProblem(edit.kind, edit.name, edit.title, edit.body)
    if (problem) { setError(problem); return }
    setBusy(true); setError("")
    const body = { kind: edit.kind, name: edit.name.trim(), title: edit.title.trim() || null, body: edit.body.trim() || null,
      specialty: edit.specialty.trim() || null, urgency: edit.urgency || null, unfitDays: edit.unfitDays ? Number(edit.unfitDays) : null }
    try {
      if (edit.id) await apiClient.put(`/api/v1/clinic/letter-templates/${edit.id}`, body); else await apiClient.post("/api/v1/clinic/letter-templates", body)
      setEdit(null); await load()
    } catch (e: any) { setError(e?.response?.data?.message ?? "The template could not be saved.") }
    finally { setBusy(false) }
  }
  const archive = async (t: Template) => {
    try { await apiClient.delete(`/api/v1/clinic/letter-templates/${t.id}`); await load() } catch { setError("The template could not be archived.") }
  }

  if (edit) {
    const f = FIELDS[edit.kind]
    const set = (k: keyof Draft) => (e: { target: { value: string } }) => setEdit(d => d && { ...d, [k]: e.target.value })
    return (
      <ModalShell title={edit.id ? "Change template" : "New template"} onClose={() => { setEdit(null); setError("") }} width={620}
        footer={<><button type="button" style={smallBtn} onClick={() => { setEdit(null); setError("") }}>Back</button>
          <button type="button" style={{ ...primaryBtn, opacity: busy ? 0.6 : 1 }} disabled={busy} onClick={save}>Save template</button></>}>
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {!edit.id && <div><label style={lbl} htmlFor="lt-kind">Kind of letter</label>
            <select id="lt-kind" style={sinp} value={edit.kind} onChange={e => setEdit(d => d && { ...d, kind: e.target.value as Kind })}>
              {KINDS.map(k => <option key={k} value={k}>{KIND_LABEL[k]}</option>)}</select></div>}
          <div><label style={lbl} htmlFor="lt-name">Template name</label><input id="lt-name" style={sinp} value={edit.name} onChange={set("name")} /></div>
          {f.title && <div><label style={lbl} htmlFor="lt-title">{f.title}</label><input id="lt-title" style={sinp} value={edit.title} onChange={set("title")} /></div>}
          {f.body && <div><label style={lbl} htmlFor="lt-body">{f.body}</label><textarea id="lt-body" rows={7} style={{ ...sinp, resize: "vertical" }} value={edit.body} onChange={set("body")} /></div>}
          {edit.kind === "SICK_NOTE" && <div><label style={lbl} htmlFor="lt-days">Days unfit (optional)</label><input id="lt-days" type="number" min={1} max={365} style={{ ...sinp, width: 120 }} value={edit.unfitDays} onChange={set("unfitDays")} /></div>}
          {edit.kind === "REFERRAL" && <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
            <div><label style={lbl} htmlFor="lt-spec">Specialty (optional)</label><input id="lt-spec" style={sinp} value={edit.specialty} onChange={set("specialty")} /></div>
            <div><label style={lbl} htmlFor="lt-urg">Urgency (optional)</label>
              <select id="lt-urg" style={sinp} value={edit.urgency} onChange={set("urgency")}><option value="">Not set</option><option value="ROUTINE">Routine</option><option value="SEMI_URGENT">Semi-urgent</option><option value="URGENT">Urgent</option></select></div>
          </div>}
          <div style={{ fontSize: 11, color: GRAY }}>Merge fields you can use: {MERGE_FIELDS.map(mergeToken).join("  ")}</div>
          {error && <div role="alert" style={{ fontSize: 13, color: RED_TEXT }}>{error}</div>}
        </div>
      </ModalShell>)
  }

  return (
    <ModalShell title="Letter templates" onClose={onClose} width={620}
      footer={<><button type="button" style={smallBtn} onClick={onClose}>Close</button>
        <button type="button" style={primaryBtn} onClick={() => { setError(""); setEdit(blank("GENERAL_LETTER")) }}>New template</button></>}>
      {loadError && <div role="alert" style={{ fontSize: 13, color: RED_TEXT }}>The templates could not be loaded.</div>}
      {error && <div role="alert" style={{ fontSize: 13, color: RED_TEXT }}>{error}</div>}
      {!loadError && list.length === 0 && <div style={{ fontSize: 13, color: GRAY }}>No templates yet. Add one, or use “Save as template” in a sick note, referral or letter.</div>}
      {KINDS.map(k => {
        const rows = list.filter(t => t.kind === k)
        if (rows.length === 0) return null
        return (
          <div key={k} style={{ marginBottom: 14 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: GRAY, textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 6 }}>{KIND_LABEL[k]}</div>
            {rows.map(t => (
              <div key={t.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, padding: "8px 12px", border: `1px solid ${BORDER}`, borderRadius: 8, marginBottom: 6 }}>
                <span style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{t.name}</span>
                <span style={{ display: "flex", gap: 6 }}>
                  <button type="button" style={smallBtn} onClick={() => { setError(""); setEdit(toDraft(t)) }}>Change</button>
                  <button type="button" style={smallBtn} aria-label={`Archive ${t.name}`} onClick={() => archive(t)}>Archive</button>
                </span>
              </div>))}
          </div>)
      })}
    </ModalShell>
  )
}
