// src/pages/customers/TeamPanel.tsx
// How each salesperson is doing: open deals and forecast, wins, win rate, late deals and late tasks. Built from the pipeline and task queue.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuthStore } from "../../store/auth.store"
import { localToday, money, type PipelineLead } from "./pipeline.logic"
import type { Task } from "./tasks.logic"
import { buildTeam, ownerName } from "./team.logic"

interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const inner = (r: { data: unknown }): unknown => { const o = r.data as { data?: unknown } | null; return o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data }
const list = <T,>(r: { data: unknown }, key?: string): T[] => { const d = inner(r) as Record<string, unknown> | T[] | null; const v = Array.isArray(d) ? d : key ? (d as Record<string, unknown> | null)?.[key] : null; return Array.isArray(v) ? (v as T[]) : [] }

export default function TeamPanel() {
  const me = useAuthStore(s => s.user?.userId ?? null), today = localToday()
  const leads = useQuery<PipelineLead[]>({ queryKey: ["crm", "pipeline"], queryFn: async () => list<PipelineLead>(await apiClient.get("/api/v1/crm/customers/pipeline")) })
  const tasks = useQuery<Task[]>({ queryKey: ["crm", "tasks", "open"], queryFn: async () => list<Task>(await apiClient.get("/api/v1/crm/tasks", { params: { done: false } }), "tasks") })
  const users = useQuery<UserRow[]>({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => { const d = inner(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) as { content?: UserRow[] } | UserRow[] | null; return Array.isArray(d) ? d : d?.content ?? [] } })
  if (leads.isLoading || tasks.isLoading) return <p>Loading…</p>
  if (leads.isError || tasks.isError || !leads.data || !tasks.data) return <div role="alert">Couldn't load the team figures.</div>
  const names = Object.fromEntries((users.data ?? []).map(u => [u.id, [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "Colleague"]))
  const rows = buildTeam(leads.data, tasks.data, today)
  if (rows.length === 0) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No deals or tasks yet.</p>
  const late = { color: "var(--hf-danger-text)", fontWeight: 700 }
  return (
    <div>
      <table aria-label="Team performance" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
        <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Owner</th><th>Open deals</th><th>Open value</th><th>Weighted</th><th>Won</th><th>Value won</th><th>Win rate</th><th>Late deals</th><th>Open tasks</th><th>Late tasks</th></tr></thead>
        <tbody>{rows.map(r => {
          const name = ownerName(r.owner, me, names)
          return <tr key={r.owner} aria-label={name} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
            <td style={{ padding: "6px 0", fontWeight: 600 }}>{name}</td><td>{r.openDeals}</td><td>{money(r.openValue)}</td><td>{money(r.weighted)}</td><td>{r.won}</td><td>{money(r.wonValue)}</td>
            <td>{r.winRate == null ? "—" : `${r.winRate}%`}</td><td style={r.overdueDeals ? late : undefined}>{r.overdueDeals}</td><td>{r.openTasks}</td><td style={r.lateTasks ? late : undefined}>{r.lateTasks}</td></tr>
        })}</tbody>
      </table>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>Deals are grouped by their owner, tasks by who they are assigned to. Weighted uses the same stage chances as the Forecast tab. Win rate counts won and lost deals only.</p>
    </div>
  )
}
