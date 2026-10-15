import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import type { EvidenceRequestApi, EvidenceRequestView } from "./types"
import { actionsFor, isFinished, MIN_REASON, needsAttention, reasonOk, sortRequests, STATUS_TEXT, type Side } from "./requests.logic"

const BORDER = "var(--hf-border)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"
const INK = "var(--hf-text)"
const NAVY = "var(--hf-primary)"
const field = { width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }
const btn = { padding: "5px 12px", background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
const day = (d: string) => new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

type Filter = "attention" | "all"

/** Requests for evidence between the business and its auditors. The same screen serves both sides; what each may do comes from requests.logic. */
export function EvidenceRequests({ side, api, engagements = [] }: { side: Side; api: EvidenceRequestApi; engagements?: { id: string; name: string }[] }) {
  const [requests, setRequests] = useState<EvidenceRequestView[]>([])
  const [filter, setFilter] = useState<Filter>("attention")
  const [open, setOpen] = useState<EvidenceRequestView | null>(null)
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ title: "", description: "", priority: "MEDIUM", dueDate: "", engagementId: "" })
  const [text, setText] = useState("")
  const [error, setError] = useState("")
  const fileRef = useRef<HTMLInputElement>(null)

  const load = useCallback(() => { api.list().then(setRequests).catch(() => setError("Could not load the requests")) }, [api])
  useEffect(load, [load])

  const attention = useMemo(() => requests.filter(r => needsAttention(r, side)).length, [requests, side])
  const shown = sortRequests(filter === "attention" ? requests.filter(r => needsAttention(r, side)) : requests)

  const run = async (fn: () => Promise<EvidenceRequestView | void>, after?: () => void) => {
    setError("")
    try { const r = await fn(); if (r) setOpen(r); after?.(); load() }
    catch (e: any) { setError(e?.response?.data?.message ?? "That did not work") }
  }
  const show = async (r: EvidenceRequestView) => { if (open?.id === r.id) { setOpen(null); return }; setText(""); await run(async () => { setOpen(await api.detail(r.id)) }) }
  const create = () => run(async () => {
    await api.create({ title: form.title.trim(), description: form.description || undefined, priority: form.priority, dueDate: form.dueDate || undefined, engagementId: form.engagementId || undefined })
  }, () => { setAdding(false); setForm({ title: "", description: "", priority: "MEDIUM", dueDate: "", engagementId: "" }) })

  const ask = (label: string) => window.prompt(label) ?? ""
  const needReason = (label: string, then: (t: string) => Promise<EvidenceRequestView | void>) => {
    const t = ask(label)
    if (!reasonOk(t)) { if (t) setError(`Please give a reason of at least ${MIN_REASON} characters`); return }
    run(() => then(t.trim()))
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <div style={{ display: "flex", gap: 6 }}>
          <button onClick={() => setFilter("attention")} style={{ ...btn, background: filter === "attention" ? NAVY : "var(--hf-surface)", color: filter === "attention" ? "var(--hf-text-on-solid)" : INK }}>Needs {side === "business" ? "you" : "your review"} ({attention})</button>
          <button onClick={() => setFilter("all")} style={{ ...btn, background: filter === "all" ? NAVY : "var(--hf-surface)", color: filter === "all" ? "var(--hf-text-on-solid)" : INK }}>All ({requests.length})</button>
        </div>
        {!adding && <button onClick={() => setAdding(true)} style={{ background: "none", border: "none", color: NAVY, fontWeight: 700, cursor: "pointer", fontSize: 12.5 }}>New request</button>}
      </div>
      {error && <div role="alert" style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 10 }}>{error}</div>}

      {adding && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 12 }}>
          <label style={{ fontSize: 12, color: MUTED, gridColumn: "1 / span 2" }}>What is needed<input aria-label="What is needed" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} placeholder="e.g. Bank statements for March" style={field} /></label>
          <label style={{ fontSize: 12, color: MUTED, gridColumn: "1 / span 2" }}>Details (optional)<input aria-label="Request details" value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} style={field} /></label>
          <label style={{ fontSize: 12, color: MUTED }}>Priority
            <select aria-label="Request priority" value={form.priority} onChange={e => setForm(f => ({ ...f, priority: e.target.value }))} style={field}><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option></select></label>
          <label style={{ fontSize: 12, color: MUTED }}>Due date (optional)<input aria-label="Request due date" type="date" value={form.dueDate} onChange={e => setForm(f => ({ ...f, dueDate: e.target.value }))} style={field} /></label>
          {side === "business" && engagements.length > 0 && (
            <label style={{ fontSize: 12, color: MUTED, gridColumn: "1 / span 2" }}>Engagement
              <select aria-label="Request engagement" value={form.engagementId} onChange={e => setForm(f => ({ ...f, engagementId: e.target.value }))} style={field}>
                <option value="">Not part of an engagement</option>{engagements.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}</select></label>
          )}
          <div style={{ gridColumn: "1 / span 2", display: "flex", gap: 8 }}>
            <button disabled={!form.title.trim()} onClick={create} style={{ ...btn, background: NAVY, color: "var(--hf-text-on-solid)" }}>Send request</button>
            <button onClick={() => setAdding(false)} style={btn}>Cancel</button>
          </div>
        </div>
      )}

      {shown.length === 0 ? <div style={{ fontSize: 12.5, color: FAINT, padding: "12px 0" }}>{filter === "attention" ? "Nothing is waiting for you." : "No requests yet."}</div> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {shown.map(r => (
            <div key={r.id} style={{ border: `1px solid ${BORDER}`, borderRadius: 8 }}>
              <div onClick={() => show(r)} style={{ display: "flex", justifyContent: "space-between", padding: "8px 12px", cursor: "pointer" }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: INK }}>{r.reference} · {r.title}</div>
                  <div style={{ fontSize: 12, color: MUTED }}>
                    {STATUS_TEXT[r.status]}{r.timesRejected ? ` · sent back ${r.timesRejected}×` : ""} · {r.priority.toLowerCase()} priority
                    {r.dueDate ? ` · due ${day(r.dueDate)}` : ""}{r.assignedToName ? ` · ${r.assignedToName}` : ""} · asked by {r.requestedByKind === "AUDITOR" ? (r.requestedByLabel ?? "the auditor") : "the business"}
                  </div>
                </div>
                {r.overdue ? <span style={{ color: "var(--hf-danger-text)", fontSize: 11, fontWeight: 700 }}>OVERDUE</span> : r.dueSoon ? <span style={{ color: "var(--hf-warning-text)", fontSize: 11, fontWeight: 700 }}>DUE SOON</span> : null}
              </div>
              {open?.id === r.id && <Detail r={open} side={side} api={api} text={text} setText={setText} run={run} needReason={needReason} fileRef={fileRef} />}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function Detail({ r, side, api, text, setText, run, needReason, fileRef }: {
  r: EvidenceRequestView; side: Side; api: EvidenceRequestApi; text: string; setText: (t: string) => void
  run: (fn: () => Promise<EvidenceRequestView | void>, after?: () => void) => Promise<void>
  needReason: (label: string, then: (t: string) => Promise<EvidenceRequestView | void>) => void
  fileRef: React.RefObject<HTMLInputElement | null>
}) {
  const can = actionsFor(r, side)
  return (
    <div style={{ borderTop: `1px solid ${BORDER}`, padding: "8px 12px", fontSize: 12.5 }}>
      {r.description && <p style={{ margin: "0 0 8px", color: MUTED }}>{r.description}</p>}
      {r.cancelReason && <p style={{ margin: "0 0 8px", color: MUTED }}>Cancelled: {r.cancelReason}</p>}
      {r.rounds.length > 0 && (
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontWeight: 700, color: INK }}>History</div>
          {r.rounds.map(x => (
            <div key={x.roundNo} style={{ margin: "4px 0", paddingLeft: 8, borderLeft: `2px solid ${BORDER}` }}>
              <div>Round {x.roundNo} · {x.kind === "NOT_AVAILABLE" ? "Marked not available" : "Files sent"} · {day(x.submittedAt)}: {x.note}</div>
              {x.files.map(f => <div key={f.id}><button onClick={() => run(() => api.download(r.id, f.id, f.fileName).then(() => undefined))} style={{ background: "none", border: "none", color: NAVY, cursor: "pointer", padding: 0, fontSize: 12.5 }}>{f.fileName}</button></div>)}
              {x.decision && <div style={{ color: x.decision === "REJECTED" ? "var(--hf-danger-text)" : "var(--hf-success-text-strong)" }}>{x.decision === "REJECTED" ? "Sent back" : "Approved"}{x.decisionNote ? `: ${x.decisionNote}` : ""}</div>}
            </div>
          ))}
        </div>
      )}
      {r.comments.length > 0 && (
        <div style={{ marginBottom: 8 }}>
          <div style={{ fontWeight: 700, color: INK }}>Comments</div>
          {r.comments.map(c => <div key={c.id}><b>{c.authorKind === "AUDITOR" ? (c.authorLabel ?? "Auditor") : (c.authorLabel ?? "Business")}</b> · {day(c.createdAt)}: {c.body}</div>)}
        </div>
      )}
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
        {can.upload && api.upload && (<>
          <input ref={fileRef} type="file" aria-label="Attach a file" style={{ display: "none" }} onChange={e => { const f = e.target.files?.[0]; if (f) run(async () => { await api.upload!(r.id, f) }, () => { if (fileRef.current) fileRef.current.value = "" }) }} />
          <button style={btn} onClick={() => fileRef.current?.click()}>Attach a file</button>
        </>)}
        {can.send && api.submit && <button style={btn} onClick={() => run(() => api.submit!(r.id, window.prompt("Note for the auditor (optional)") ?? ""))}>Send files</button>}
        {can.notAvailable && api.notAvailable && <button style={btn} onClick={() => needReason("Why can this not be provided?", t => api.notAvailable!(r.id, t))}>Not available</button>}
        {can.approve && api.approve && <button style={btn} onClick={() => run(() => api.approve!(r.id, ""))}>{r.status === "NOT_AVAILABLE" ? "Accept as not available" : "Approve"}</button>}
        {can.sendBack && api.reject && <button style={btn} onClick={() => needReason("What is wrong with the answer?", t => api.reject!(r.id, t))}>Send back</button>}
        {can.cancel && <button style={btn} onClick={() => needReason("Why is this being cancelled?", t => api.cancel(r.id, t))}>Cancel request</button>}
      </div>
      {can.comment && (
        <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
          <input aria-label="Add a comment" placeholder="Add a comment" value={text} onChange={e => setText(e.target.value)} style={{ ...field, flex: 1 }} />
          <button disabled={!text.trim()} style={btn} onClick={() => run(() => api.comment(r.id, text.trim()), () => setText(""))}>Comment</button>
        </div>
      )}
    </div>
  )
}
