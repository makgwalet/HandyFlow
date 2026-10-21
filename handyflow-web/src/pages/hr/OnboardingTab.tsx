// src/pages/hr/OnboardingTab.tsx
//
// Onboarding of new employees. Tasks that depend on the employee record tick themselves; the rest are ticked by a manager or
// the person they are assigned to. The server enforces who may do what; this screen only offers what is allowed.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, CheckCircle2, Circle, MinusCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import {
  canReopen, canTick, canWaive, CHECK_LABEL, dueLabel, groupByCategory, onboardingRights, progressText, reasonProblem, STATUS_LABEL,
  taskProblem, type TaskView,
} from "./onboarding.logic"

type Wrapped = { data?: { data?: unknown } }
type ApiErr = { response?: { data?: { message?: string } } }
const unwrap = <T,>(r: Wrapped): T => (r.data?.data ?? r.data) as T
const errorText = (e: unknown, fallback: string) => (e as ApiErr)?.response?.data?.message ?? fallback

interface Summary {
  id: string; employeeId: string; employeeNumber: string; employeeName: string; jobTitle: string | null; department: string | null
  startDate: string; probationEndDate: string | null; status: string; total: number; finished: number; requiredOpen: number; overdue: number; percent: number
}
interface Detail { summary: Summary; cancelReason: string | null; tasks: TaskView[]; finishProblems: string[]; canManage: boolean }

const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", marginBottom: 20, overflow: "hidden" }
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }
const primary: React.CSSProperties = { padding: "9px 18px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 700, cursor: "pointer" }
const plain: React.CSSProperties = { padding: "6px 12px", background: "var(--hf-surface)", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer" }

const STATUSES = ["IN_PROGRESS", "COMPLETE", "CANCELLED"]

export default function OnboardingTab() {
  const qc = useQueryClient()
  const { user } = useAuth() as { user?: { userId?: string; permissions?: string[] } }
  const rights = onboardingRights(user?.permissions ?? [])
  const [status, setStatus] = useState("IN_PROGRESS")
  const [openId, setOpenId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const list = useQuery({
    queryKey: ["hr-onboardings", status],
    queryFn: async () => unwrap<Summary[]>(await apiClient.get("/api/v1/hr/onboardings", { params: { status } })),
    enabled: rights.view,
  })

  if (!rights.view) return <p style={{ color: "var(--hf-text-muted)", fontSize: 14 }}>You do not have access to onboarding.</p>

  return (
    <div>
      <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
        {STATUSES.map(s => (
          <button key={s} onClick={() => { setStatus(s); setOpenId(null) }} aria-pressed={status === s}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: status === s ? 600 : 400,
              background: status === s ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: status === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            {STATUS_LABEL[s]}
          </button>
        ))}
      </div>

      {error && <div role="alert" style={{ display: "flex", gap: 8, color: "var(--hf-danger)", fontSize: 13, marginBottom: 12 }}><AlertCircle size={16} />{error}</div>}
      {list.isLoading && <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>}
      {list.isError && <p role="alert" style={{ color: "var(--hf-danger)" }}>Could not load onboarding.</p>}
      {list.data && list.data.length === 0 && (
        <p style={{ color: "var(--hf-text-muted)", fontSize: 14 }}>No onboarding {STATUS_LABEL[status].toLowerCase()}. New employees get one automatically when they are added.</p>
      )}

      {(list.data ?? []).map(s => (
        <div key={s.id} style={card}>
          <button onClick={() => setOpenId(openId === s.id ? null : s.id)} aria-expanded={openId === s.id}
            style={{ display: "block", width: "100%", textAlign: "left", padding: "14px 18px", background: "none", border: "none", cursor: "pointer" }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
              <strong style={{ fontSize: 14, color: "var(--hf-text)" }}>{s.employeeName} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>{s.employeeNumber}</span></strong>
              <span style={{ fontSize: 12, color: s.overdue > 0 ? "var(--hf-danger)" : "var(--hf-text-muted)" }}>
                {s.overdue > 0 ? `${s.overdue} overdue · ` : ""}{s.percent}%
              </span>
            </div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>
              {[s.jobTitle, s.department].filter(Boolean).join(" · ")} · started {s.startDate} · {progressText(s)}
            </div>
            <div style={{ height: 6, background: "var(--hf-surface-sunken)", borderRadius: 3, marginTop: 8 }}>
              <div style={{ width: `${s.percent}%`, height: 6, background: "var(--hf-primary)", borderRadius: 3 }} />
            </div>
          </button>
          {openId === s.id && <DetailPanel id={s.id} userId={user?.userId} onError={setError} onChanged={() => qc.invalidateQueries({ queryKey: ["hr-onboardings"] })} />}
        </div>
      ))}
    </div>
  )
}

function DetailPanel({ id, userId, onError, onChanged }: { id: string; userId?: string; onError: (m: string | null) => void; onChanged: () => void }) {
  const qc = useQueryClient()
  const q = useQuery({
    queryKey: ["hr-onboarding", id],
    queryFn: async () => unwrap<Detail>(await apiClient.get(`/api/v1/hr/onboardings/${id}`)),
  })
  const [reasonFor, setReasonFor] = useState<{ kind: "waive" | "cancel"; taskId?: string } | null>(null)
  const [reason, setReason] = useState("")
  const [newTitle, setNewTitle] = useState("")
  const [problem, setProblem] = useState<string | null>(null)

  const act = useMutation({
    mutationFn: async (call: () => Promise<unknown>) => call(),
    onSuccess: () => { onError(null); setReasonFor(null); setReason(""); setNewTitle(""); qc.invalidateQueries({ queryKey: ["hr-onboarding", id] }); onChanged() },
    onError: (e: unknown) => onError(errorText(e, "That did not work.")),
  })

  if (q.isLoading) return <p style={{ padding: 18, color: "var(--hf-text-muted)" }}>Loading…</p>
  if (!q.data) return <p role="alert" style={{ padding: 18, color: "var(--hf-danger)" }}>Could not load this onboarding.</p>

  const d = q.data
  const manage = d.canManage
  const open = d.summary.status === "IN_PROGRESS"
  const today = new Date()

  const submitReason = () => {
    const p = reasonProblem(reason, reasonFor?.kind === "cancel" ? "for cancelling" : "this task does not apply")
    setProblem(p)
    if (p || !reasonFor) return
    if (reasonFor.kind === "waive") act.mutate(() => apiClient.post(`/api/v1/hr/onboarding-tasks/${reasonFor.taskId}/waive`, { note: reason.trim() }))
    else act.mutate(() => apiClient.post(`/api/v1/hr/onboardings/${id}/cancel`, { note: reason.trim() }))
  }

  return (
    <div style={{ padding: "0 18px 18px", borderTop: "1px solid var(--hf-border-subtle)" }}>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        Probation ends {d.summary.probationEndDate ?? "—"}.{d.cancelReason ? ` Cancelled: ${d.cancelReason}` : ""}
      </p>

      {groupByCategory(d.tasks).map(g => (
        <div key={g.category} style={{ marginBottom: 14 }}>
          <h4 style={{ fontSize: 12, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--hf-text-faint)", margin: "10px 0 6px" }}>{g.label}</h4>
          {g.tasks.map(t => (
            <div key={t.id} style={{ display: "flex", gap: 10, padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", alignItems: "flex-start" }}>
              {t.status === "DONE" ? <CheckCircle2 size={18} aria-label="Done" /> : t.status === "WAIVED" ? <MinusCircle size={18} aria-label="Waived" /> : <Circle size={18} aria-label="Open" />}
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 13, color: "var(--hf-text)", textDecoration: t.status === "WAIVED" ? "line-through" : "none" }}>
                  {t.title}{!t.required && <span style={{ color: "var(--hf-text-muted)" }}> (optional)</span>}
                </div>
                <div style={{ fontSize: 12, color: t.overdue ? "var(--hf-danger)" : "var(--hf-text-muted)" }}>
                  {[t.automatic && t.check ? `Checked: ${CHECK_LABEL[t.check] ?? t.check}` : null, dueLabel(t, today), t.status === "WAIVED" && t.note ? `Waived: ${t.note}` : null]
                    .filter(Boolean).join(" · ")}
                </div>
              </div>
              {open && (
                <div style={{ display: "flex", gap: 6 }}>
                  {canTick(t, manage, userId) && <button style={plain} onClick={() => act.mutate(() => apiClient.post(`/api/v1/hr/onboarding-tasks/${t.id}/complete`, {}))}>Done</button>}
                  {canWaive(t, manage) && <button style={plain} onClick={() => { setReasonFor({ kind: "waive", taskId: t.id }); setReason(""); setProblem(null) }}>Waive</button>}
                  {canReopen(t, manage) && <button style={plain} onClick={() => act.mutate(() => apiClient.post(`/api/v1/hr/onboarding-tasks/${t.id}/reopen`))}>Reopen</button>}
                </div>
              )}
            </div>
          ))}
        </div>
      ))}

      {reasonFor && (
        <div style={{ margin: "12px 0" }}>
          <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }} htmlFor="onb-reason">
            {reasonFor.kind === "cancel" ? "Reason for cancelling" : "Reason this task does not apply"}
          </label>
          <input id="onb-reason" style={input} value={reason} onChange={e => setReason(e.target.value)} />
          {problem && <div role="alert" style={{ color: "var(--hf-danger)", fontSize: 12, marginTop: 4 }}>{problem}</div>}
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button style={primary} onClick={submitReason}>{reasonFor.kind === "cancel" ? "Cancel onboarding" : "Waive task"}</button>
            <button style={plain} onClick={() => setReasonFor(null)}>Never mind</button>
          </div>
        </div>
      )}

      {open && manage && (
        <div style={{ borderTop: "1px solid var(--hf-border-subtle)", paddingTop: 12, marginTop: 8 }}>
          <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
            <input aria-label="New task" placeholder="Add a task for this person" style={input} value={newTitle} onChange={e => setNewTitle(e.target.value)} />
            <button style={plain} onClick={() => {
              const p = taskProblem(newTitle); setProblem(p)
              if (!p) act.mutate(() => apiClient.post(`/api/v1/hr/onboardings/${id}/tasks`, { title: newTitle.trim(), category: "INDUCTION", required: true }))
            }}>Add</button>
          </div>
          {d.finishProblems.length > 0 && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>To finish: {d.finishProblems[0]}{d.finishProblems.length > 1 ? ` (and ${d.finishProblems.length - 1} more)` : ""}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button style={primary} disabled={d.finishProblems.length > 0} onClick={() => act.mutate(() => apiClient.post(`/api/v1/hr/onboardings/${id}/finish`))}>Finish onboarding</button>
            <button style={plain} onClick={() => { setReasonFor({ kind: "cancel" }); setReason(""); setProblem(null) }}>Cancel onboarding</button>
          </div>
        </div>
      )}
    </div>
  )
}
