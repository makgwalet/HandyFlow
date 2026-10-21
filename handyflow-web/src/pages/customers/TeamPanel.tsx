// src/pages/customers/TeamPanel.tsx
// How each salesperson is doing: open deals and forecast, wins, win rate, late deals and late tasks, and this month's target.
// Built from the pipeline, the task queue and the monthly targets.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { localToday, money, type PipelineLead } from "./pipeline.logic"
import type { Task } from "./tasks.logic"
import { buildTeam, monthOfDate, monthTitle, ownerName, shiftMonth, targetAmount, targetProblem, withTargets, type TargetRow } from "./team.logic"

interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const inner = (r: { data: unknown }): unknown => { const o = r.data as { data?: unknown } | null; return o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data }
const list = <T,>(r: { data: unknown }, key?: string): T[] => { const d = inner(r) as Record<string, unknown> | T[] | null; const v = Array.isArray(d) ? d : key ? (d as Record<string, unknown> | null)?.[key] : null; return Array.isArray(v) ? (v as T[]) : [] }
const errorText = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "4px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const small: React.CSSProperties = { ...field, cursor: "pointer", fontSize: 12 }

export default function TeamPanel() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE"), me = useAuthStore(s => s.user?.userId ?? null), today = localToday()
  const [month, setMonth] = useState(monthOfDate(today)), [editing, setEditing] = useState<string | null>(null), [text, setText] = useState(""), [error, setError] = useState<string | null>(null)
  const leads = useQuery<PipelineLead[]>({ queryKey: ["crm", "pipeline"], queryFn: async () => list<PipelineLead>(await apiClient.get("/api/v1/crm/customers/pipeline")) })
  const tasks = useQuery<Task[]>({ queryKey: ["crm", "tasks", "open"], queryFn: async () => list<Task>(await apiClient.get("/api/v1/crm/tasks", { params: { done: false } }), "tasks") })
  const targets = useQuery<TargetRow[]>({ queryKey: ["crm", "targets", month], retry: false, queryFn: async () => list<TargetRow>(await apiClient.get("/api/v1/crm/targets", { params: { period: month } })) })
  const users = useQuery<UserRow[]>({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => { const d = inner(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) as { content?: UserRow[] } | UserRow[] | null; return Array.isArray(d) ? d : d?.content ?? [] } })
  const refresh = () => { setError(null); setEditing(null); qc.invalidateQueries({ queryKey: ["crm", "targets"] }) }
  const save = useMutation({ mutationFn: (v: { ownerId: string; amount: number }) => apiClient.put("/api/v1/crm/targets", { ownerId: v.ownerId, period: month, amount: v.amount }), onSuccess: refresh, onError: e => setError(errorText(e)) })
  const clear = useMutation({ mutationFn: (ownerId: string) => apiClient.delete("/api/v1/crm/targets", { params: { ownerId, period: month } }), onSuccess: refresh, onError: e => setError(errorText(e)) })
  if (leads.isLoading || tasks.isLoading) return <p>Loading…</p>
  if (leads.isError || tasks.isError || !leads.data || !tasks.data) return <div role="alert">Couldn't load the team figures.</div>
  const names = Object.fromEntries((users.data ?? []).map(u => [u.id, [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "Colleague"]))
  const rows = withTargets(buildTeam(leads.data, tasks.data, today), targets.data ?? [])
  if (rows.length === 0) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No deals or tasks yet.</p>
  const late = { color: "var(--hf-danger-text)", fontWeight: 700 }
  const submit = (ownerId: string) => { const p = targetProblem(text); if (p) { setError(p); return } setError(null); save.mutate({ ownerId, amount: targetAmount(text) }) }
  return (
    <div>
      <div role="group" aria-label="Target month" style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
        <button type="button" style={small} aria-label="Previous month" onClick={() => { setMonth(shiftMonth(month, -1)); setEditing(null) }}>‹</button>
        <strong aria-live="polite">{monthTitle(month)}</strong>
        <button type="button" style={small} aria-label="Next month" onClick={() => { setMonth(shiftMonth(month, 1)); setEditing(null) }}>›</button>
      </div>
      {targets.isError && <p role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text)" }}>Couldn't load the targets for this month.</p>}
      {error && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</p>}
      <table aria-label="Team performance" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
        <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Owner</th><th>Open deals</th><th>Open value</th><th>Weighted</th><th>Won (all time)</th><th>Win rate</th><th>Late deals</th><th>Open tasks</th><th>Late tasks</th><th>Target</th><th>Won this month</th><th>Of target</th></tr></thead>
        <tbody>{rows.map(r => {
          const name = ownerName(r.owner, me, names), canTarget = r.owner !== "unassigned"
          return <tr key={r.owner} aria-label={name} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
            <td style={{ padding: "6px 0", fontWeight: 600 }}>{name}</td><td>{r.openDeals}</td><td>{money(r.openValue)}</td><td>{money(r.weighted)}</td><td>{r.won} ({money(r.wonValue)})</td>
            <td>{r.winRate == null ? "—" : `${r.winRate}%`}</td><td style={r.overdueDeals ? late : undefined}>{r.overdueDeals}</td><td>{r.openTasks}</td><td style={r.lateTasks ? late : undefined}>{r.lateTasks}</td>
            <td>{editing === r.owner
              ? <span role="group" aria-label={`Set target for ${name}`}><input aria-label={`Target for ${name}`} inputMode="decimal" style={{ ...field, width: 110 }} value={text} onChange={e => setText(e.target.value)} autoFocus />{" "}
                  <button type="button" style={small} disabled={save.isPending} onClick={() => submit(r.owner)}>Save</button>{" "}<button type="button" style={small} onClick={() => { setEditing(null); setError(null) }}>Cancel</button></span>
              : <>{r.target == null ? "—" : money(r.target)}{canEdit && canTarget && <>{" "}<button type="button" style={small} aria-label={`${r.target == null ? "Set" : "Change"} target for ${name}`} onClick={() => { setEditing(r.owner); setText(r.target == null ? "" : String(r.target)); setError(null) }}>{r.target == null ? "Set" : "Change"}</button>
                  {r.target != null && <>{" "}<button type="button" style={small} aria-label={`Remove target for ${name}`} disabled={clear.isPending} onClick={() => clear.mutate(r.owner)}>Remove</button></>}</>}</>}</td>
            <td>{r.wonDeals} ({money(r.achieved)})</td><td>{r.percent == null ? "—" : `${r.percent}%`}</td></tr>
        })}</tbody>
      </table>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>Deals are grouped by their owner, tasks by who they are assigned to. Weighted uses the same stage chances as the Forecast tab. Win rate counts won and lost deals only. A win counts in the month it was marked won.</p>
    </div>
  )
}
