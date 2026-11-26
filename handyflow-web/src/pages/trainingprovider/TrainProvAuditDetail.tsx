// src/pages/trainingprovider/TrainProvAuditDetail.tsx
//
// One audit: its questions, the auditor's answers, and the corrective actions. Completing the audit and verifying an action each need a second person
// or a written reason. Nothing is deleted.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  OUTCOMES, actionProblem, actionState, answerProblem, completeProblem, label, noteProblem, AUDIT_STATUS, type ActionForm, type AuditAction, type AuditDetail, type AuditItem,
} from "./auditRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

const API = `${LRN}/quality-audits`

function ItemRow({ item, canAnswer, onAddAction, onSaved }: { item: AuditItem; canAnswer: boolean; onAddAction: () => void; onSaved: () => void }) {
  const [editing, setEditing] = useState(false)
  const [outcome, setOutcome] = useState(item.outcome ?? "")
  const [note, setNote] = useState(item.note ?? "")
  const [error, setError] = useState("")
  const save = async () => {
    const p = answerProblem(outcome, note); if (p) { setError(p); return }
    try { await apiClient.post(`${API}/items/${item.id}/answer`, { outcome, note: note.trim() || null }); setEditing(false); setError(""); onSaved() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 4 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>{item.position}. {item.question}</div>
        <Badge text={item.outcome ? label(OUTCOMES, item.outcome) : "Not answered"} tone={item.outcome === "ISSUE" ? "warn" : item.outcome ? "good" : "neutral"} />
      </div>
      {item.note && <div style={muted}>{item.note}</div>}
      {canAnswer && !editing && (
        <div style={{ display: "flex", gap: 8 }}>
          <button style={ghost} onClick={() => setEditing(true)}>{item.outcome ? "Change answer" : "Answer"}</button>
          {item.outcome === "ISSUE" && <button style={ghost} onClick={onAddAction}>Add corrective action</button>}
        </div>
      )}
      {editing && (
        <div style={{ display: "grid", gap: 6 }}>
          <select aria-label={`Outcome for item ${item.position}`} style={input} value={outcome} onChange={e => setOutcome(e.target.value)}><option value="">Choose…</option>{Object.entries(OUTCOMES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          <Field label="What did you see?"><textarea aria-label={`Note for item ${item.position}`} rows={2} style={input} value={note} onChange={e => setNote(e.target.value)} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => void save()}>Save answer</button><button style={ghost} onClick={() => setEditing(false)}>Cancel</button></div>
        </div>
      )}
    </div>
  )
}

function ActionRow({ a, canManage, onSaved }: { a: AuditAction; canManage: boolean; onSaved: () => void }) {
  const [mode, setMode] = useState<"" | "done" | "verify" | "cancel">("")
  const [note, setNote] = useState("")
  const [alone, setAlone] = useState(false)
  const [error, setError] = useState("")
  const send = async (url: string, body: object) => { try { await apiClient.post(`${API}/actions/${a.id}/${url}`, body); setMode(""); setNote(""); setAlone(false); setError(""); onSaved() } catch (e) { setError(errText(e)) } }
  return (
    <div style={{ ...card, display: "grid", gap: 4 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>{a.description}<div style={muted}>Owner {a.ownerName} · due {a.dueOn}</div></div>
        <Badge text={actionState(a)} tone={a.status === "DONE" && a.verified ? "good" : a.overdue ? "warn" : "neutral"} />
      </div>
      {a.doneNote && <div style={muted}>Done {a.doneOn}: {a.doneNote}</div>}
      {a.verifiedAlone && <div style={muted}>Verified by the person who did it: {a.verifiedAloneReason}</div>}
      {a.cancelledReason && <div style={muted}>Cancelled: {a.cancelledReason}</div>}
      {canManage && mode === "" && (
        <div style={{ display: "flex", gap: 8 }}>
          {a.status === "OPEN" && <button style={ghost} onClick={() => setMode("done")}>Mark done</button>}
          {a.status === "OPEN" && <button style={ghost} onClick={() => setMode("cancel")}>Cancel action</button>}
          {a.status === "DONE" && !a.verified && <button style={ghost} onClick={() => setMode("verify")}>Verify</button>}
        </div>
      )}
      {mode !== "" && (
        <div style={{ display: "grid", gap: 6 }}>
          {mode === "verify" ? (
            <>
              <label style={{ fontSize: 13 }}><input type="checkbox" checked={alone} onChange={e => setAlone(e.target.checked)} /> I am verifying work I did myself</label>
              {alone && <Field label="Why are you verifying it yourself?"><input style={input} value={note} onChange={e => setNote(e.target.value)} /></Field>}
            </>
          ) : <Field label={mode === "done" ? "What was done?" : "Why is it cancelled?"}><input style={input} value={note} onChange={e => setNote(e.target.value)} /></Field>}
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} onClick={() => {
              if (mode === "verify") { if (alone) { const p = noteProblem(note); if (p) { setError(p); return } } void send("verify", { confirmAlone: alone, aloneReason: alone ? note.trim() : null }); return }
              const p = noteProblem(note); if (p) { setError(p); return }
              void send(mode === "done" ? "done" : "cancel", mode === "done" ? { note: note.trim() } : { reason: note.trim() })
            }}>{mode === "done" ? "Save as done" : mode === "verify" ? "Verify action" : "Cancel the action"}</button>
            <button style={ghost} onClick={() => setMode("")}>Back</button>
          </div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvAuditDetail({ id, canManage, onBack }: { id: string; canManage: boolean; onBack: () => void }) {
  const qc = useQueryClient()
  const { data } = useQuery<AuditDetail>({ queryKey: ["tp-audit", id], queryFn: async () => unwrap<AuditDetail>(await apiClient.get(`${API}/${id}`)) })
  const [adding, setAdding] = useState<string | null | undefined>(undefined)
  const [af, setAf] = useState<ActionForm>({ description: "", ownerName: "", dueOn: "" })
  const [panel, setPanel] = useState<"" | "complete" | "cancel">("")
  const [summary, setSummary] = useState("")
  const [alone, setAlone] = useState(false)
  const [aloneReason, setAloneReason] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["tp-audit", id] })
  if (!data) return <div style={muted}>Loading…</div>
  const inProgress = data.status === "IN_PROGRESS"
  const call = async (fn: () => Promise<unknown>, done?: () => void) => { setError(""); try { await fn(); done?.(); void refresh() } catch (e) { setError(errText(e)) } }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div><button style={ghost} onClick={onBack}>Back to audits</button></div>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{data.title}</strong> <span style={muted}>{data.auditNo}</span>
          <div style={muted}>{data.templateName} (version {data.templateVersionNo}){data.targetName ? ` · ${data.targetName}` : ""} · planned {data.plannedOn}</div></div>
        <Badge text={label(AUDIT_STATUS, data.status)} tone={data.status === "COMPLETED" ? "good" : inProgress ? "warn" : "neutral"} />
      </div>
      <div style={muted}>{data.note}</div>
      {data.summary && <div style={{ fontSize: 13 }}>Summary: {data.summary}{data.completedAlone ? ` (completed by the lead alone: ${data.completedAloneReason})` : ""}</div>}
      {data.cancelledReason && <div style={muted}>Cancelled: {data.cancelledReason}</div>}
      <Notice text={error} />
      {canManage && data.status === "PLANNED" && <div><button style={solid} onClick={() => void call(() => apiClient.post(`${API}/${id}/start`, {}))}>Start the audit</button></div>}
      {data.items.map(i => <ItemRow key={i.id} item={i} canAnswer={canManage && inProgress} onAddAction={() => { setAdding(i.id); setAf({ description: "", ownerName: "", dueOn: "" }) }} onSaved={() => void refresh()} />)}
      <strong style={{ fontSize: 13 }}>Corrective actions</strong>
      {data.actions.length === 0 ? <div style={muted}>No actions yet.</div> : data.actions.map(a => <ActionRow key={a.id} a={a} canManage={canManage} onSaved={() => void refresh()} />)}
      {canManage && (inProgress || data.status === "COMPLETED") && adding === undefined && <div><button style={ghost} onClick={() => { setAdding(null); setAf({ description: "", ownerName: "", dueOn: "" }) }}>Add an action</button></div>}
      {adding !== undefined && (
        <div style={{ ...card, display: "grid", gap: 6 }}>
          <Field label="What has to be done?"><input aria-label="Action description" style={input} value={af.description} onChange={e => setAf({ ...af, description: e.target.value })} /></Field>
          <Field label="Who owns it?"><input aria-label="Action owner" style={input} value={af.ownerName} onChange={e => setAf({ ...af, ownerName: e.target.value })} /></Field>
          <Field label="Due on"><input aria-label="Action due date" type="date" style={input} value={af.dueOn} onChange={e => setAf({ ...af, dueOn: e.target.value })} /></Field>
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} onClick={() => { const p = actionProblem(af, todayIso()); if (p) { setError(p); return } void call(() => apiClient.post(`${API}/${id}/actions`, { itemId: adding, description: af.description.trim(), ownerName: af.ownerName.trim(), dueOn: af.dueOn }), () => setAdding(undefined)) }}>Save action</button>
            <button style={ghost} onClick={() => setAdding(undefined)}>Cancel</button>
          </div>
        </div>
      )}
      {canManage && inProgress && panel === "" && <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => setPanel("complete")}>Complete the audit</button><button style={ghost} onClick={() => setPanel("cancel")}>Cancel the audit</button></div>}
      {panel === "complete" && (
        <div style={{ ...card, display: "grid", gap: 6 }}>
          <Field label="Summary of the audit"><textarea aria-label="Audit summary" rows={3} style={input} value={summary} onChange={e => setSummary(e.target.value)} /></Field>
          <label style={{ fontSize: 13 }}><input type="checkbox" checked={alone} onChange={e => setAlone(e.target.checked)} /> I led this audit and am completing it myself</label>
          {alone && <Field label="Why are you completing it yourself?"><input aria-label="Reason for completing alone" style={input} value={aloneReason} onChange={e => setAloneReason(e.target.value)} /></Field>}
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} onClick={() => { const p = completeProblem(data, summary) ?? (alone ? noteProblem(aloneReason) : null); if (p) { setError(p); return } void call(() => apiClient.post(`${API}/${id}/complete`, { summary: summary.trim(), confirmAlone: alone, aloneReason: alone ? aloneReason.trim() : null }), () => setPanel("")) }}>Complete audit</button>
            <button style={ghost} onClick={() => setPanel("")}>Back</button>
          </div>
        </div>
      )}
      {panel === "cancel" && (
        <div style={{ ...card, display: "grid", gap: 6 }}>
          <Field label="Why is the audit cancelled?"><input aria-label="Reason for cancelling the audit" style={input} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} onClick={() => { const p = noteProblem(reason); if (p) { setError(p); return } void call(() => apiClient.post(`${API}/${id}/cancel`, { reason: reason.trim() }), () => setPanel("")) }}>Cancel audit</button>
            <button style={ghost} onClick={() => setPanel("")}>Back</button>
          </div>
        </div>
      )}
    </div>
  )
}
