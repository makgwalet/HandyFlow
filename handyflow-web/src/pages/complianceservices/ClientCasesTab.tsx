// src/pages/complianceservices/ClientCasesTab.tsx
//
// Problems found for a client and the work to put them right. A case needs an owner before work starts, cannot be
// resolved with steps open, and is closed by a second person unless the same person gives a reason.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import {
  SEVERITIES, statusLabel, severityLabel, isEditable, actionsFor, actionProblem, showsNote, stepProblem, dueText, eventLabel, MIN_REASON, type Action,
} from "./caseRules"

interface Summary {
  id: string; title: string; severity: string; status: string; ownerId: string | null; dueDate: string | null; assessmentId: string | null
  obligationId: string | null; obligationCode: string | null; openSteps: number; totalSteps: number; createdAt: string
}
interface Step { id: string; description: string; status: string; dueDate: string | null; doneBy: string | null; doneAt: string | null; cancelReason: string | null }
interface Event { event: string; fromStatus: string | null; toStatus: string | null; note: string | null; actor: string | null; occurredAt: string }
interface Detail {
  summary: Summary; description: string | null; resolutionNote: string | null; resolvedBy: string | null; resolvedAt: string | null; closedBy: string | null
  closedAt: string | null; closeExceptionReason: string | null; cancelReason: string | null; steps: Step[]; events: Event[]
}
interface Staff { id: string; firstName?: string; lastName?: string; email?: string }

const ACCENT = "var(--hf-success-solid-strong)"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const button: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "5px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const today = () => new Date().toISOString().slice(0, 10)

export default function ClientCasesTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const { user } = useAuth()
  const me = user?.userId ?? null
  const base = `/api/v1/compliance-services/clients/${clientId}/cases`
  const [selected, setSelected] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ title: "", description: "", severity: "MEDIUM", dueDate: "" })
  const [note, setNote] = useState("")
  const [stepText, setStepText] = useState("")
  const [stepDue, setStepDue] = useState("")
  const [error, setError] = useState("")

  const { data: list = [] } = useQuery<Summary[]>({ queryKey: ["cs-cases", clientId], queryFn: async () => unwrap<Summary[]>(await apiClient.get(base)) })
  const { data: staff = [] } = useQuery<Staff[]>({
    queryKey: ["cs-case-staff"],
    queryFn: async () => { const d = unwrap<Staff[] | { content?: Staff[] }>(await apiClient.get("/api/v1/identity/users")); return Array.isArray(d) ? d : d?.content ?? [] },
  })
  const nameOf = (id: string | null) => {
    if (!id) return "Nobody"
    if (id === me) return "You"
    const s = staff.find(x => x.id === id)
    return s ? [s.firstName, s.lastName].filter(Boolean).join(" ") || s.email || "Someone" : "Someone"
  }
  const { data: d } = useQuery<Detail>({ queryKey: ["cs-case", clientId, selected], enabled: !!selected, queryFn: async () => unwrap<Detail>(await apiClient.get(`${base}/${selected}`)) })

  const got = (r: Detail) => { setError(""); setNote(""); setStepText(""); setStepDue(""); setSelected(r.summary.id); qc.setQueryData(["cs-case", clientId, r.summary.id], r); qc.invalidateQueries({ queryKey: ["cs-cases", clientId] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] }) }
  const fail = (e: unknown) => setError(errText(e))
  const open = useMutation({
    mutationFn: async () => unwrap<Detail>(await apiClient.post(base, { title: form.title, description: form.description || null, severity: form.severity, dueDate: form.dueDate || null, ownerId: me })),
    onSuccess: r => { setCreating(false); setForm({ title: "", description: "", severity: "MEDIUM", dueDate: "" }); got(r) }, onError: fail,
  })
  const move = useMutation({ mutationFn: async (a: Action) => unwrap<Detail>(await apiClient.post(`${base}/${selected}/transition`, { action: a.action, note: note.trim() || null })), onSuccess: got, onError: fail })
  const owner = useMutation({ mutationFn: async (ownerId: string) => unwrap<Detail>(await apiClient.put(`${base}/${selected}/owner`, { ownerId })), onSuccess: got, onError: fail })
  const due = useMutation({ mutationFn: async (dueDate: string) => unwrap<Detail>(await apiClient.put(`${base}/${selected}/due`, { dueDate: dueDate || null })), onSuccess: got, onError: fail })
  const addStep = useMutation({ mutationFn: async () => unwrap<Detail>(await apiClient.post(`${base}/${selected}/steps`, { description: stepText, dueDate: stepDue || null })), onSuccess: got, onError: fail })
  const doneStep = useMutation({ mutationFn: async (id: string) => unwrap<Detail>(await apiClient.post(`${base}/${selected}/steps/${id}/complete`, {})), onSuccess: got, onError: fail })
  const dropStep = useMutation({
    mutationFn: async (id: string) => {
      const reason = window.prompt(`Why is this step not needed? (at least ${MIN_REASON} characters)`) ?? ""
      if (reason.trim().length < MIN_REASON) throw new Error(`Say why, in at least ${MIN_REASON} characters`)
      return unwrap<Detail>(await apiClient.post(`${base}/${selected}/steps/${id}/cancel`, { reason }))
    },
    onSuccess: got, onError: fail,
  })

  const c = d?.summary
  const caseLike = c && d ? { status: c.status, ownerId: c.ownerId, openSteps: c.openSteps, resolvedBy: d.resolvedBy } : null
  const titleProblem = form.title.trim().length < 3 ? "Give the case a title of at least 3 characters" : null

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <strong style={{ fontSize: 14 }}>Cases</strong>
        <span style={{ flex: 1 }} />
        {canManage && <button style={button} onClick={() => setCreating(!creating)}>{creating ? "Close form" : "Open case"}</button>}
      </div>
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text, #c0392b)" }}>{error}</div>}

      {creating && (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, display: "grid", gap: 8 }}>
          <input style={input} aria-label="Title" placeholder="What needs putting right?" value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} />
          <textarea style={input} rows={2} aria-label="Description" placeholder="Details (optional)" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} />
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
            <select style={input} aria-label="Severity" value={form.severity} onChange={e => setForm({ ...form, severity: e.target.value })}>{SEVERITIES.map(s => <option key={s[0]} value={s[0]}>{s[1]}</option>)}</select>
            <input style={input} type="date" aria-label="Due date" min={today()} value={form.dueDate} onChange={e => setForm({ ...form, dueDate: e.target.value })} />
          </div>
          {titleProblem && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{titleProblem}</div>}
          <div><button style={{ ...button, opacity: titleProblem || open.isPending ? 0.5 : 1 }} disabled={!!titleProblem || open.isPending} onClick={() => open.mutate()}>Open case</button></div>
        </div>
      )}

      {list.length === 0 && !creating && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No cases yet. Open one here, or from a problem found in an assessment.</div>}
      <div style={{ display: "grid", gap: 6 }}>
        {list.map(s => (
          <button key={s.id} onClick={() => { setSelected(selected === s.id ? null : s.id); setNote(""); setError("") }}
            style={{ textAlign: "left", border: `1px solid ${selected === s.id ? ACCENT : "var(--hf-border)"}`, borderRadius: 10, padding: "10px 12px", background: "transparent", color: "var(--hf-text)", cursor: "pointer" }}>
            <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <strong style={{ fontSize: 13 }}>{s.title}</strong>
              <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{severityLabel(s.severity)}</span>
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 12, fontWeight: 600 }}>{statusLabel(s.status)}</span>
            </div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>
              {nameOf(s.ownerId)} · {dueText(s.dueDate, today(), s.status)} · {s.totalSteps === 0 ? "no steps" : `${s.totalSteps - s.openSteps} of ${s.totalSteps} steps done`}
            </div>
          </button>
        ))}
      </div>

      {d && c && (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, display: "grid", gap: 12 }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700 }}>{c.title}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{statusLabel(c.status)} · {severityLabel(c.severity)}{c.obligationCode ? ` · ${c.obligationCode}` : ""}</div>
            {d.description && <div style={{ fontSize: 13, marginTop: 6 }}>{d.description}</div>}
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 8, alignItems: "center" }}>
            {canManage && isEditable(c.status) ? (
              <>
                <select style={input} aria-label="Owner" value={c.ownerId ?? ""} onChange={e => e.target.value && owner.mutate(e.target.value)}>
                  {!c.ownerId && <option value="">No owner</option>}
                  {me && <option value={me}>You</option>}
                  {staff.filter(s => s.id !== me).map(s => <option key={s.id} value={s.id}>{nameOf(s.id)}</option>)}
                </select>
                <input style={input} type="date" aria-label="Case due date" min={today()} value={c.dueDate ?? ""} onChange={e => due.mutate(e.target.value)} />
              </>
            ) : (
              <div style={{ fontSize: 13 }}>Owner: {nameOf(c.ownerId)} · {dueText(c.dueDate, today(), c.status)}</div>
            )}
          </div>

          {d.resolutionNote && <div style={{ fontSize: 13 }}><strong>Resolution:</strong> {d.resolutionNote} <span style={{ color: "var(--hf-text-faint)" }}>({nameOf(d.resolvedBy)})</span></div>}
          {d.closeExceptionReason && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)" }}>Closed by the person who resolved it: {d.closeExceptionReason}</div>}
          {d.cancelReason && <div style={{ fontSize: 13 }}><strong>Cancelled:</strong> {d.cancelReason}</div>}

          <div style={{ display: "grid", gap: 6 }}>
            <strong style={{ fontSize: 13 }}>Steps</strong>
            {d.steps.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>No steps yet</div>}
            {d.steps.map(s => (
              <div key={s.id} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, flexWrap: "wrap" }}>
                <span aria-hidden>{s.status === "DONE" ? "☑" : s.status === "CANCELLED" ? "⊘" : "☐"}</span>
                <span style={{ textDecoration: s.status === "TODO" ? "none" : "line-through", color: s.status === "TODO" ? "var(--hf-text)" : "var(--hf-text-muted)" }}>{s.description}</span>
                {s.dueDate && s.status === "TODO" && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{dueText(s.dueDate, today(), "OPEN")}</span>}
                {s.status === "DONE" && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{nameOf(s.doneBy)}</span>}
                {s.status === "CANCELLED" && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{s.cancelReason}</span>}
                <span style={{ flex: 1 }} />
                {canManage && isEditable(c.status) && s.status === "TODO" && (
                  <>
                    <button style={ghost} onClick={() => doneStep.mutate(s.id)}>Done</button>
                    <button style={ghost} onClick={() => dropStep.mutate(s.id)}>Not needed</button>
                  </>
                )}
              </div>
            ))}
            {canManage && isEditable(c.status) && (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <input style={{ ...input, flex: 1, minWidth: 200 }} aria-label="New step" placeholder="Add a step" value={stepText} onChange={e => setStepText(e.target.value)} />
                <input style={{ ...input, width: 150 }} type="date" aria-label="Step due date" min={today()} value={stepDue} onChange={e => setStepDue(e.target.value)} />
                <button style={{ ...button, opacity: stepProblem(stepText) || addStep.isPending ? 0.5 : 1 }} disabled={!!stepProblem(stepText) || addStep.isPending} onClick={() => addStep.mutate()}>Add step</button>
              </div>
            )}
          </div>

          {canManage && caseLike && actionsFor(c.status).length > 0 && (
            <div style={{ display: "grid", gap: 8 }}>
              {actionsFor(c.status).some(a => showsNote(a, caseLike!, me)) && (
                <textarea style={input} rows={2} aria-label="Note" placeholder="Note or reason (needed to resolve, cancel, reopen, or to close your own resolution)" value={note} onChange={e => setNote(e.target.value)} />
              )}
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                {actionsFor(c.status).map(a => {
                  const problem = actionProblem(a, caseLike!, note, me)
                  return (
                    <button key={a.action} title={problem ?? undefined} style={{ ...(a.action === "CANCEL" ? ghost : button), opacity: problem || move.isPending ? 0.5 : 1 }}
                      disabled={!!problem || move.isPending} onClick={() => move.mutate(a)}>{a.label}</button>
                  )
                })}
              </div>
              {actionsFor(c.status).map(a => { const p = actionProblem(a, caseLike!, note, me); return p && showsNote(a, caseLike!, me) && note.length > 0 ? <div key={a.action} style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{a.label}: {p}</div> : null })}
            </div>
          )}

          <div style={{ display: "grid", gap: 4 }}>
            <strong style={{ fontSize: 13 }}>History</strong>
            {d.events.map((e, i) => (
              <div key={i} style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                {e.occurredAt.slice(0, 10)} · {eventLabel(e.event)} · {nameOf(e.actor)}{e.note && !/^[0-9a-f-]{36}$/.test(e.note) ? ` · ${e.note}` : ""}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
