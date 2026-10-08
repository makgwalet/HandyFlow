// The two lists a doctor needs beside today's queue: results waiting for review (critical first) and my tasks.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle, Check, FlaskConical, ListChecks, Plus, X } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { clinicDay, dismissProblem, dueLabel, resultFollowUp, sortTasks, titleProblem, type ClinicTask } from "./taskView"

const unwrap = (r: any) => { const p = r?.data?.data ?? r?.data; return Array.isArray(p) ? p : (p?.content ?? []) }
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short", timeZone: "Africa/Johannesburg" })

const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", padding: 14 }
const head: React.CSSProperties = { display: "flex", alignItems: "center", gap: 8, fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 8 }
const small: React.CSSProperties = { padding: "4px 10px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", cursor: "pointer", fontSize: 12, fontWeight: 600, color: "var(--hf-text)" }
const input: React.CSSProperties = { padding: 7, border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 13 }

interface CriticalItem { id: string; patientName?: string | null; patientNameRaw?: string | null; receivedAt: string; criticalMarkers?: string | null }
interface ResultRow { id: string; patientId?: string | null; patientNameRaw?: string | null; receivedAt: string; hasCritical?: boolean; hasAbnormal?: boolean }

async function safe<T>(fn: () => Promise<T[]>): Promise<T[]> { try { return await fn() } catch { return [] } }

export function ResultsToReviewPanel({ onNavigate }: { onNavigate: (section: string) => void }) {
  const qc = useQueryClient()
  const canRead = usePermission("CLINIC_RESULT_READ")
  const canReview = usePermission("CLINIC_RESULT_REVIEW")
  const canTask = usePermission("CLINIC_TASK_CREATE")
  const [error, setError] = useState("")
  const critical = useQuery<CriticalItem[]>({ queryKey: ["lab-critical"], enabled: canRead, retry: false, refetchInterval: 60000,
    queryFn: () => safe(async () => unwrap(await apiClient.get("/api/v1/clinic/lab/critical"))) })
  const unreviewed = useQuery<ResultRow[]>({ queryKey: ["lab-inbox", "UNREVIEWED"], enabled: canRead, retry: false,
    queryFn: () => safe(async () => unwrap(await apiClient.get("/api/v1/clinic/lab/results", { params: { status: "UNREVIEWED" } }))) })
  const review = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/clinic/lab/results/${id}/review`),
    onSuccess: () => { setError(""); qc.invalidateQueries({ queryKey: ["lab-inbox"] }); qc.invalidateQueries({ queryKey: ["lab-critical"] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not mark the result reviewed"),
  })
  const follow = useMutation({
    mutationFn: (body: object) => apiClient.post("/api/v1/clinic/tasks", body),
    onSuccess: () => { setError(""); qc.invalidateQueries({ queryKey: ["clinic-tasks"] }) },
    onError: (e: any) => setError(e?.response?.data?.message ?? "Could not create the task"),
  })
  if (!canRead) return null

  const crit = critical.data ?? []
  const critIds = new Set(crit.map(c => c.id))
  const others = (unreviewed.data ?? []).filter(r => !critIds.has(r.id))
  const shown = [...crit.map(c => ({ id: c.id, name: c.patientName ?? c.patientNameRaw ?? "Unmatched result", at: c.receivedAt, critical: true, note: c.criticalMarkers ?? null, patientId: null as string | null })),
    ...others.map(r => ({ id: r.id, name: r.patientNameRaw ?? "Unmatched result", at: r.receivedAt, critical: !!r.hasCritical, note: r.hasAbnormal ? "Abnormal" : null, patientId: r.patientId ?? null }))].slice(0, 6)
  const count = crit.length + others.length

  return (
    <section aria-label="Results to review" style={card}>
      <div style={head}><FlaskConical size={15}/> Results to review <span style={{ color: "var(--hf-text-muted)", fontWeight: 600 }}>· {critical.isLoading || unreviewed.isLoading ? "…" : count}</span>
        <button type="button" style={{ ...small, marginLeft: "auto" }} onClick={() => onNavigate("lab-inbox")}>Open lab inbox</button></div>
      {crit.length > 0 && <div role="alert" style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 12, fontWeight: 700, color: "var(--hf-danger-text)", marginBottom: 6 }}><AlertTriangle size={13}/> {crit.length} critical result{crit.length === 1 ? "" : "s"} not yet reviewed</div>}
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginBottom: 6 }}>{error}</div>}
      {shown.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing waiting for review.</div>}
      {shown.map(r => (
        <div key={r.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontWeight: 600, color: r.critical ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{r.critical ? "CRITICAL · " : ""}{r.name}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{when(r.at)}{r.note ? ` · ${r.note}` : ""}</div>
          </div>
          {canTask && <button type="button" style={small} disabled={follow.isPending} title="Make sure someone follows this up"
            onClick={() => follow.mutate(resultFollowUp({ id: r.id, patientId: r.patientId, patientName: r.name === "Unmatched result" ? null : r.name }))}>Follow-up task</button>}
          {canReview && <button type="button" style={small} disabled={review.isPending} onClick={() => review.mutate(r.id)}>Mark reviewed</button>}
        </div>
      ))}
      {count > shown.length && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>and {count - shown.length} more in the lab inbox</div>}
    </section>
  )
}

export function TasksPanel() {
  const qc = useQueryClient()
  const canRead = usePermission("CLINIC_TASK_READ")
  const canCreate = usePermission("CLINIC_TASK_CREATE")
  const canClose = usePermission("CLINIC_TASK_COMPLETE")
  const [title, setTitle] = useState("")
  const [due, setDue] = useState("")
  const [dismissing, setDismissing] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const today = clinicDay()

  const tasks = useQuery<ClinicTask[]>({ queryKey: ["clinic-tasks", "mine"], enabled: canRead, retry: false, refetchInterval: 60000,
    queryFn: () => safe(async () => unwrap(await apiClient.get("/api/v1/clinic/tasks", { params: { mine: true } }))) })
  const done = () => { setError(""); qc.invalidateQueries({ queryKey: ["clinic-tasks"] }) }
  const fail = (fallback: string) => (e: any) => setError(e?.response?.data?.message ?? fallback)
  const add = useMutation({
    mutationFn: () => apiClient.post("/api/v1/clinic/tasks", { title: title.trim(), dueDate: due || undefined }),
    onSuccess: () => { setTitle(""); setDue(""); done() }, onError: fail("Could not add the task"),
  })
  const complete = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/clinic/tasks/${id}/complete`, {}), onSuccess: done, onError: fail("Could not complete the task") })
  const dismiss = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/clinic/tasks/${id}/dismiss`, { note: reason.trim() }),
    onSuccess: () => { setDismissing(null); setReason(""); done() }, onError: fail("Could not dismiss the task"),
  })
  if (!canRead) return null

  const list = sortTasks(tasks.data ?? [])
  const problem = titleProblem(title)
  return (
    <section aria-label="My tasks" style={card}>
      <div style={head}><ListChecks size={15}/> My tasks <span style={{ color: "var(--hf-text-muted)", fontWeight: 600 }}>· {tasks.isLoading ? "…" : list.length}</span></div>
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginBottom: 6 }}>{error}</div>}
      {list.length === 0 && !tasks.isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No open tasks.</div>}
      {list.map(t => {
        const d = dueLabel(t.dueDate, t.overdue, today)
        return (
          <div key={t.id} style={{ padding: "6px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600 }}>{t.title}</div>
                <div style={{ fontSize: 11, color: d.tone === "overdue" ? "var(--hf-danger-text)" : "var(--hf-text-muted)", fontWeight: d.tone === "overdue" ? 700 : 400 }}>
                  {t.patientName ? `${t.patientName} · ` : ""}{d.text}</div>
              </div>
              {canClose && <>
                <button type="button" style={small} aria-label={`Done: ${t.title}`} disabled={complete.isPending} onClick={() => complete.mutate(t.id)}><Check size={12}/> Done</button>
                <button type="button" style={small} aria-label={`Dismiss: ${t.title}`} onClick={() => { setDismissing(dismissing === t.id ? null : t.id); setReason(""); setError("") }}><X size={12}/></button>
              </>}
            </div>
            {dismissing === t.id && (
              <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
                <input aria-label="Why dismiss" value={reason} onChange={e => setReason(e.target.value)} placeholder="Why is this not needed?" style={{ ...input, flex: 1 }}/>
                <button type="button" style={small} disabled={!!dismissProblem(reason) || dismiss.isPending} title={dismissProblem(reason) ?? undefined} onClick={() => dismiss.mutate(t.id)}>Dismiss</button>
              </div>
            )}
          </div>
        )
      })}
      {canCreate && (
        <form onSubmit={e => { e.preventDefault(); if (!problem) add.mutate() }} style={{ display: "flex", gap: 6, marginTop: 10 }}>
          <input aria-label="New task" value={title} onChange={e => setTitle(e.target.value)} placeholder="Add a task…" style={{ ...input, flex: 1 }}/>
          <input aria-label="Due date" type="date" min={today} value={due} onChange={e => setDue(e.target.value)} style={input}/>
          <button type="submit" style={small} disabled={!!problem || add.isPending} title={problem ?? undefined}><Plus size={12}/> Add</button>
        </form>
      )}
    </section>
  )
}
