import { useCallback, useEffect, useMemo, useState } from "react"
import type { AuditQueryApi, AuditQueryView, FindingOption, QueryKind } from "./types"
import { actionsFor, KIND_TEXT, MIN_TEXT, needsAttention, sortQueries, STATUS_TEXT, SUBJECTS, textOk, type Side } from "./queries.logic"

const BORDER = "var(--hf-border)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"
const INK = "var(--hf-text)"
const NAVY = "var(--hf-primary)"
const field = { width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" as const }
const btn = { padding: "5px 12px", background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
const day = (d: string) => new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

/** Audit queries and external review notes. One screen for both sides; what each may do comes from queries.logic. */
export function AuditQueries({ side, api }: { side: Side; api: AuditQueryApi }) {
  const [items, setItems] = useState<AuditQueryView[]>([])
  const [filter, setFilter] = useState<"attention" | "all">("attention")
  const [open, setOpen] = useState<AuditQueryView | null>(null)
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ kind: "QUERY" as QueryKind, title: "", text: "", subjectType: "", subjectLabel: "", dueDate: "" })
  const [reply, setReply] = useState("")
  const [findings, setFindings] = useState<FindingOption[] | null>(null)
  const [error, setError] = useState("")

  const load = useCallback(() => { api.list().then(setItems).catch(() => setError("Could not load the list")) }, [api])
  useEffect(load, [load])

  const attention = useMemo(() => items.filter(q => needsAttention(q, side)).length, [items, side])
  const shown = sortQueries(filter === "attention" ? items.filter(q => needsAttention(q, side)) : items, side)

  const run = async (fn: () => Promise<AuditQueryView | void>, after?: () => void) => {
    setError("")
    try { const r = await fn(); if (r) setOpen(r); after?.(); load() }
    catch (e: any) { setError(e?.response?.data?.message ?? "That did not work") }
  }
  const show = async (q: AuditQueryView) => {
    if (open?.id === q.id) { setOpen(null); return }
    setReply(""); setFindings(null)
    await run(async () => { setOpen(await api.detail(q.id)) })
  }
  const raise = () => run(async () => {
    await api.raise!({ kind: form.kind, title: form.title.trim(), text: form.text.trim(), subjectType: form.subjectType || undefined, subjectLabel: form.subjectLabel || undefined, dueDate: form.dueDate || undefined })
  }, () => { setAdding(false); setForm({ kind: "QUERY", title: "", text: "", subjectType: "", subjectLabel: "", dueDate: "" }) })
  const needText = (label: string, then: (t: string) => Promise<AuditQueryView | void>) => {
    const t = window.prompt(label) ?? ""
    if (!textOk(t)) { if (t) setError(`Please write at least ${MIN_TEXT} characters`); return }
    run(() => then(t.trim()))
  }
  const loadFindings = async () => { if (api.findingOptions) setFindings(await api.findingOptions().catch(() => [])) }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <div style={{ display: "flex", gap: 6 }}>
          <button onClick={() => setFilter("attention")} style={{ ...btn, background: filter === "attention" ? NAVY : "var(--hf-surface)", color: filter === "attention" ? "var(--hf-text-on-solid)" : INK }}>Needs {side === "business" ? "an answer" : "your review"} ({attention})</button>
          <button onClick={() => setFilter("all")} style={{ ...btn, background: filter === "all" ? NAVY : "var(--hf-surface)", color: filter === "all" ? "var(--hf-text-on-solid)" : INK }}>All ({items.length})</button>
        </div>
        {side === "auditor" && !adding && <button onClick={() => setAdding(true)} style={{ background: "none", border: "none", color: NAVY, fontWeight: 700, cursor: "pointer", fontSize: 12.5 }}>New query or note</button>}
      </div>
      {error && <div role="alert" style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 10 }}>{error}</div>}

      {adding && (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 12 }}>
          <label style={{ fontSize: 12, color: MUTED }}>Type
            <select aria-label="Query type" value={form.kind} onChange={e => setForm(f => ({ ...f, kind: e.target.value as QueryKind }))} style={field}>
              <option value="QUERY">Query (needs an answer)</option><option value="REVIEW_NOTE">Review note (for the record)</option></select></label>
          <label style={{ fontSize: 12, color: MUTED }}>About
            <select aria-label="Query subject" value={form.subjectType} onChange={e => setForm(f => ({ ...f, subjectType: e.target.value }))} style={field}>
              {SUBJECTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></label>
          <label style={{ fontSize: 12, color: MUTED, gridColumn: "1 / span 2" }}>Title<input aria-label="Query title" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} style={field} /></label>
          {form.subjectType && <label style={{ fontSize: 12, color: MUTED, gridColumn: "1 / span 2" }}>Which one (number or name)<input aria-label="Query subject label" value={form.subjectLabel} onChange={e => setForm(f => ({ ...f, subjectLabel: e.target.value }))} placeholder="e.g. JE-1042" style={field} /></label>}
          <label style={{ fontSize: 12, color: MUTED, gridColumn: "1 / span 2" }}>What you want to know<textarea aria-label="Query text" value={form.text} onChange={e => setForm(f => ({ ...f, text: e.target.value }))} rows={3} style={field} /></label>
          <label style={{ fontSize: 12, color: MUTED }}>Answer by (optional)<input aria-label="Query due date" type="date" value={form.dueDate} onChange={e => setForm(f => ({ ...f, dueDate: e.target.value }))} style={field} /></label>
          <div style={{ gridColumn: "1 / span 2", display: "flex", gap: 8 }}>
            <button disabled={!form.title.trim() || !textOk(form.text)} onClick={raise} style={{ ...btn, background: NAVY, color: "var(--hf-text-on-solid)" }}>Send</button>
            <button onClick={() => setAdding(false)} style={btn}>Cancel</button>
          </div>
        </div>
      )}

      {shown.length === 0 ? <div style={{ fontSize: 12.5, color: FAINT, padding: "12px 0" }}>{filter === "attention" ? "Nothing is waiting for you." : "No queries or notes yet."}</div> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {shown.map(q => {
            const can = actionsFor(q, side)
            return (
              <div key={q.id} style={{ border: `1px solid ${BORDER}`, borderRadius: 8 }}>
                <div onClick={() => show(q)} style={{ display: "flex", justifyContent: "space-between", padding: "8px 12px", cursor: "pointer" }}>
                  <div>
                    <div style={{ fontSize: 13, fontWeight: 700, color: INK }}>{q.reference} · {q.title}</div>
                    <div style={{ fontSize: 12, color: MUTED }}>
                      {KIND_TEXT[q.kind]} · {STATUS_TEXT[q.status]}{q.timesReopened ? ` · followed up ${q.timesReopened}×` : ""}
                      {q.subjectLabel ? ` · about ${q.subjectLabel}` : ""}{q.dueDate ? ` · answer by ${day(q.dueDate)}` : ""}
                    </div>
                  </div>
                  {q.overdue && <span style={{ color: "var(--hf-danger-text)", fontSize: 11, fontWeight: 700 }}>OVERDUE</span>}
                </div>
                {open?.id === q.id && (
                  <div style={{ borderTop: `1px solid ${BORDER}`, padding: "8px 12px", fontSize: 12.5 }}>
                    {open.messages.map(m => (
                      <div key={m.id} style={{ margin: "4px 0", paddingLeft: 8, borderLeft: `2px solid ${m.authorKind === "AUDITOR" ? NAVY : BORDER}` }}>
                        <b>{m.authorKind === "AUDITOR" ? (m.authorLabel ?? "Auditor") : (m.authorLabel ?? "Business")}</b> · {day(m.createdAt)}<div>{m.body}</div>
                      </div>
                    ))}
                    {side === "business" && open.linkedFindingTitle && <div style={{ color: MUTED, marginTop: 6 }}>Linked to internal finding: {open.linkedFindingTitle}</div>}
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 8, alignItems: "center" }}>
                      {can.answer && api.answer && <><input aria-label="Your answer" placeholder="Your answer" value={reply} onChange={e => setReply(e.target.value)} style={{ ...field, flex: 1, minWidth: 220 }} />
                        <button disabled={!textOk(reply)} style={btn} onClick={() => run(() => api.answer!(q.id, reply.trim()), () => setReply(""))}>Send answer</button></>}
                      {can.resolve && api.resolve && <button style={btn} onClick={() => run(() => api.resolve!(q.id, ""))}>Accept answer</button>}
                      {can.followUp && api.followUp && <button style={btn} onClick={() => needText("What else do you need to know?", t => api.followUp!(q.id, t))}>Ask more</button>}
                      {can.withdraw && api.withdraw && <button style={btn} onClick={() => needText("Why are you withdrawing this?", t => api.withdraw!(q.id, t))}>Withdraw</button>}
                      {can.link && api.link && (findings === null
                        ? <button style={btn} onClick={loadFindings}>{open.linkedFindingId ? "Change linked finding" : "Link to an internal finding"}</button>
                        : <select aria-label="Finding to link" defaultValue="" onChange={e => run(() => api.link!(q.id, e.target.value || null), () => setFindings(null))} style={{ ...field, width: 260 }}>
                            <option value="">{open.linkedFindingId ? "Remove the link" : "Choose a finding"}</option>
                            {findings.map(f => <option key={f.id} value={f.id}>{f.title}</option>)}</select>)}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
