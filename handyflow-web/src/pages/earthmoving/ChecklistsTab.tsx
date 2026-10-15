// src/pages/earthmoving/ChecklistsTab.tsx
//
// Inspection checklists: for each kind of inspection, the checklist in force, the draft being written and the history. An
// administrator starts a draft (copied from what is in force), changes the questions, and publishes it from a date. A published
// version is never changed; a change is a new version, so an old inspection always shows what was asked at the time. The built-in
// walk-round is the minimum for pre-use, handover and return inspections: questions can be added, never taken away.
import { useState } from "react"
import { ListChecks } from "lucide-react"
import { apiMessage, fmtDate } from "./shared/format"
import { Overlay, ModalHead, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { INSPECTION_TYPE_LABEL, ANSWER_TYPE_LABEL } from "./shared/constants"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useChecklists, useStartChecklistDraft, useSaveChecklistDraft, usePublishChecklist, useDiscardChecklistDraft } from "./shared/hooks"
import type { ChecklistQuestion, ChecklistVersion, ChecklistTypeOverview } from "./shared/types"

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }
const small: React.CSSProperties = { ...inputStyle(), padding: "6px 8px", fontSize: 13 }

const today = () => new Date().toISOString().slice(0, 10)
const blank = (): ChecklistQuestion => ({ id: null, section: null, text: "", answerType: "PASS_FAIL", required: true, builtIn: false, min: null, max: null, unit: null, failOutcome: "DEFECT" })
const num = (v: string): number | null => (v.trim() === "" ? null : Number(v))

function Editor({ draft, onClose }: { draft: ChecklistVersion; onClose: () => void }) {
  const [qs, setQs] = useState<ChecklistQuestion[]>(draft.questions)
  const [signoff, setSignoff] = useState(draft.requiresSignoff)
  const [note, setNote] = useState(draft.note ?? "")
  const [from, setFrom] = useState(today())
  const [err, setErr] = useState("")
  const save = useSaveChecklistDraft()
  const publish = usePublishChecklist()
  const busy = save.isPending || publish.isPending

  const set = (i: number, patch: Partial<ChecklistQuestion>) => setQs(l => l.map((q, n) => (n === i ? { ...q, ...patch } : q)))
  const move = (i: number, d: number) => setQs(l => { const n = i + d; if (n < 0 || n >= l.length) return l; const c = [...l]; [c[i], c[n]] = [c[n], c[i]]; return c })
  const complete = qs.length > 0 && qs.every(q => q.text.trim() !== "" && (q.answerType !== "NUMERIC" || q.min != null || q.max != null))

  const body = { id: draft.id, requiresSignoff: signoff, note: note.trim() || null, questions: qs }
  const saveOnly = () => { setErr(""); save.mutate(body, { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not save the draft")) }) }
  const saveAndPublish = async () => {
    setErr("")
    try {
      await save.mutateAsync(body)
      await publish.mutateAsync({ id: draft.id, effectiveFrom: from })
      onClose()
    } catch (e) { setErr(apiMessage(e, "Could not publish the checklist")) }
  }

  return (
    <Overlay onClose={onClose} width={760}>
      <ModalHead title={`${INSPECTION_TYPE_LABEL[draft.inspectionType]} checklist · version ${draft.versionNo} (draft)`} onClose={onClose} />
      <div style={{ maxHeight: 400, overflowY: "auto", paddingRight: 4 }}>
        {qs.map((q, i) => (
          <div key={i} style={{ borderTop: i === 0 ? "none" : "1px solid var(--hf-border)", padding: "8px 0" }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr 1fr", gap: 8 }}>
              <input aria-label={`Section ${i + 1}`} placeholder="Section" value={q.section ?? ""} onChange={e => set(i, { section: e.target.value || null })} style={small} />
              <input aria-label={`Question ${i + 1}`} placeholder="Question" maxLength={80} value={q.text} disabled={q.builtIn}
                onChange={e => set(i, { text: e.target.value })} style={small} />
              <select aria-label={`Answer type ${i + 1}`} value={q.answerType} disabled={q.builtIn} onChange={e => set(i, { answerType: e.target.value })} style={small}>
                {Object.entries(ANSWER_TYPE_LABEL).map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
            </div>
            <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 6, flexWrap: "wrap", fontSize: 12 }}>
              <label><input type="checkbox" checked={q.required} disabled={q.builtIn} onChange={e => set(i, { required: e.target.checked })} /> Required</label>
              {q.builtIn && <span style={{ color: "var(--hf-text-muted)" }}>Built-in: cannot be removed or weakened</span>}
              {q.answerType === "NUMERIC" && (<>
                <input aria-label={`Lowest ${i + 1}`} type="number" placeholder="Lowest" value={q.min ?? ""} onChange={e => set(i, { min: num(e.target.value) })} style={{ ...small, width: 90 }} />
                <input aria-label={`Highest ${i + 1}`} type="number" placeholder="Highest" value={q.max ?? ""} onChange={e => set(i, { max: num(e.target.value) })} style={{ ...small, width: 90 }} />
                <input aria-label={`Unit ${i + 1}`} placeholder="Unit" maxLength={12} value={q.unit ?? ""} onChange={e => set(i, { unit: e.target.value || null })} style={{ ...small, width: 70 }} />
                <select aria-label={`Outside range ${i + 1}`} value={q.failOutcome} onChange={e => set(i, { failOutcome: e.target.value })} style={{ ...small, width: 150 }}>
                  <option value="DEFECT">Outside range: defect</option><option value="UNSAFE">Outside range: unsafe</option></select>
              </>)}
              <span style={{ marginLeft: "auto", display: "flex", gap: 4 }}>
                <button type="button" aria-label={`Move up ${i + 1}`} style={btn()} onClick={() => move(i, -1)}>Up</button>
                <button type="button" aria-label={`Move down ${i + 1}`} style={btn()} onClick={() => move(i, 1)}>Down</button>
                <button type="button" aria-label={`Remove ${i + 1}`} style={btn()} disabled={q.builtIn} onClick={() => setQs(l => l.filter((_, n) => n !== i))}>Remove</button>
              </span>
            </div>
          </div>
        ))}
      </div>
      <button type="button" style={{ ...btn(), marginTop: 8 }} onClick={() => setQs(l => [...l, blank()])}>Add a question</button>
      <div style={{ marginTop: 12 }}>
        <label><input type="checkbox" checked={signoff} onChange={e => setSignoff(e.target.checked)} /> The inspector signs off with their name</label>
      </div>
      <label htmlFor="cl-note" style={{ ...lbl, marginTop: 10 }}>What changed in this version</label>
      <input id="cl-note" maxLength={300} value={note} onChange={e => setNote(e.target.value)} style={inputStyle()} />
      {err && <ErrBanner msg={err} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "space-between", alignItems: "flex-end", marginTop: 18, flexWrap: "wrap" }}>
        <div><label htmlFor="cl-from" style={lbl}>In use from</label>
          <input id="cl-from" type="date" min={today()} value={from} onChange={e => setFrom(e.target.value)} style={{ ...inputStyle(), width: 170 }} /></div>
        <div style={{ display: "flex", gap: 8 }}>
          <button style={btn()} onClick={onClose}>Close</button>
          <button style={btn()} disabled={busy || !complete} onClick={saveOnly}>Save draft</button>
          <button style={btn(true)} disabled={busy || !complete || from < today()} onClick={saveAndPublish}>Publish</button>
        </div>
      </div>
    </Overlay>
  )
}

function TypeCard({ t, canAdmin, onEdit }: { t: ChecklistTypeOverview; canAdmin: boolean; onEdit: (d: ChecklistVersion) => void }) {
  const [err, setErr] = useState("")
  const start = useStartChecklistDraft(onEdit)
  const discard = useDiscardChecklistDraft()
  const label = INSPECTION_TYPE_LABEL[t.inspectionType] ?? t.inspectionType
  const a = t.active
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
        <div>
          <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{label}</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>
            {a == null ? "No checklist yet: inspections of this kind cannot be recorded until one is published."
              : a.source === "BUILT_IN" ? `Built-in walk-round (${a.questions.length} questions)`
              : `Version ${a.versionNo} from ${fmtDate(a.effectiveFrom)} (${a.questions.length} questions)${a.requiresSignoff ? ", signed off by the inspector" : ""}`}
          </div>
        </div>
        {canAdmin && (
          <div style={{ display: "flex", gap: 8 }}>
            {t.draft ? (<>
              <button style={btn(true)} onClick={() => onEdit(t.draft!)}>Continue draft v{t.draft.versionNo}</button>
              <button style={btn()} disabled={discard.isPending}
                onClick={() => { if (window.confirm("Discard this draft?")) discard.mutate(t.draft!.id, { onError: (e: unknown) => setErr(apiMessage(e, "Could not discard the draft")) }) }}>Discard</button>
            </>) : (
              <button style={btn(true)} disabled={start.isPending}
                onClick={() => { setErr(""); start.mutate(t.inspectionType, { onError: (e: unknown) => setErr(apiMessage(e, "Could not start a draft")) }) }}>
                {a == null ? "Write a checklist" : "Start a new version"}</button>
            )}
          </div>
        )}
      </div>
      {a && (
        <ul style={{ margin: "8px 0 0", paddingLeft: 18, fontSize: 12, color: "var(--hf-text-secondary)", columns: 2 }}>
          {a.questions.map(q => <li key={q.text}>{q.text}{q.answerType !== "PASS_FAIL" ? ` (${ANSWER_TYPE_LABEL[q.answerType]?.toLowerCase()})` : ""}</li>)}
        </ul>
      )}
      {t.history.length > 0 && (
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>
          History: {t.history.map(h => `v${h.versionNo} from ${fmtDate(h.effectiveFrom)}`).join(" · ")}
        </div>
      )}
      {err && <ErrBanner msg={err} />}
    </div>
  )
}

export default function ChecklistsTab() {
  const perms = useEarthPerms()
  const { data = [], isLoading } = useChecklists()
  const [editing, setEditing] = useState<ChecklistVersion | null>(null)
  return (
    <div>
      <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginBottom: 12 }}>
        <ListChecks size={14} style={{ verticalAlign: "-2px", marginRight: 6 }} />
        Each kind of inspection has its own checklist. A published version is never changed: a change is a new version, and every inspection keeps the version it was answered against.
      </div>
      {isLoading ? <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>
        : <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {data.map(t => <TypeCard key={t.inspectionType} t={t} canAdmin={perms.canAdmin} onEdit={setEditing} />)}
        </div>}
      {editing && <Editor key={editing.id} draft={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
