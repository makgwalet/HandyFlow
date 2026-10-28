// My Tasks: the signed-in person's own audit work as a plain list that reads well on a phone.

import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { auditMyTasksApi } from "../../api/auditMyTasks.api"
import { Pill, Stat } from "./auditUi"
import { grid } from "./auditUi.styles"
import { dueLabel, groupTasks, KIND_LABEL } from "./myTasks.logic"

const MUTED = "var(--hf-text-muted)"
const todayJhb = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export function MyTasksSection({ today }: { today?: string }) {
  const navigate = useNavigate()
  const { data, isLoading, isError } = useQuery({ queryKey: ["ia-my-tasks"], queryFn: auditMyTasksApi.mine })

  if (isLoading) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  if (isError || !data) return <div role="alert" style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13 }}>Your tasks could not be loaded. Refresh the page; if it keeps failing, contact support.</div>

  const groups = groupTasks(data.tasks, today ?? todayJhb())
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={grid(150)}>
        <Stat label="My open tasks" value={data.summary.total} />
        <Stat label="Overdue" value={data.summary.overdue} tone={data.summary.overdue ? "danger" : undefined} />
        <Stat label="Due in 7 days" value={data.summary.dueThisWeek} tone={data.summary.dueThisWeek ? "warning" : undefined} />
      </div>
      {data.tasks.length === 0 ? <p style={{ fontSize: 13, color: MUTED }}>You have nothing waiting on you. Actions and findings assigned to you, and review notes you need to answer or clear, show up here.</p> : groups.map(g => (
        <section key={g.key} aria-label={g.title}>
          <h3 style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", margin: "0 0 8px" }}>{g.title} ({g.tasks.length})</h3>
          <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
            {g.tasks.map(t => {
              const k = KIND_LABEL[t.kind]
              return (
                <li key={`${t.kind}-${t.id}`}>
                  <button type="button" disabled={!t.engagementId} onClick={() => t.engagementId && navigate(`/internal-audit/engagements/${t.engagementId}`)}
                    style={{ width: "100%", textAlign: "left", padding: "12px 14px", borderRadius: 10, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", cursor: t.engagementId ? "pointer" : "default", display: "grid", gap: 6 }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                      <Pill label={k.label} tone={k.tone} />
                      {t.severity && <Pill label={t.severity.charAt(0) + t.severity.slice(1).toLowerCase()} tone={t.severity === "CRITICAL" || t.severity === "HIGH" ? "warning" : "muted"} />}
                      <span style={{ fontSize: 12, color: t.overdue ? "var(--hf-danger-text)" : MUTED, fontWeight: t.overdue ? 700 : 400 }}>{dueLabel(t)}</span>
                    </div>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)", overflowWrap: "anywhere" }}>{t.title || "(no text)"}</div>
                    {t.context && <div style={{ fontSize: 12, color: MUTED, overflowWrap: "anywhere" }}>{t.context}</div>}
                  </button>
                </li>
              )
            })}
          </ul>
        </section>
      ))}
    </div>
  )
}
