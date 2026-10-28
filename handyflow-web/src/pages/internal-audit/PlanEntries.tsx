// The entries of one annual plan: a progress summary, filters and a paged table.

import { useState } from "react"
import type { PlanEntry } from "./auditShared"
import { RiskBadge } from "./RiskBadge"
import { FilterBar, Meter, Pager, Stat, StagePill } from "./auditUi"
import { filterField, grid } from "./auditUi.styles"
import { entryStage, filterEntries, paginate, planProgress, quarterCounts, type Group } from "./auditUi.logic"

const PAGE_SIZE = 10
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", borderBottom: "1px solid var(--hf-border)" }
const td: React.CSSProperties = { padding: "10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)", verticalAlign: "top" }

export function PlanEntries({ entries }: { entries: PlanEntry[] }) {
  const [q, setQ] = useState("")
  const [risk, setRisk] = useState("all")
  const [group, setGroup] = useState<Group | "all">("all")
  const [page, setPage] = useState(1)

  const p = planProgress({ entries })
  const quarters = quarterCounts(entries)
  const rows = filterEntries(entries, { risk, group, q })
  const pg = paginate(rows, page, PAGE_SIZE)
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(1) }

  return (
    <div>
      <div style={{ ...grid(120, 10), marginBottom: 12 }}>
        <Stat label="Audits planned" value={p.total} />
        <Stat label="Completed" value={p.completed} />
        <Stat label="In progress" value={p.inProgress} />
        <Stat label="Not started" value={p.planned} sub={p.deferred ? `${p.deferred} deferred` : undefined} />
      </div>
      <div style={{ marginBottom: 14 }}>
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 4 }}>
          <span>Plan completed</span><strong style={{ color: "var(--hf-text)" }}>{p.completionPct}%</strong>
        </div>
        <Meter pct={p.completionPct} color="var(--hf-success-text)" label="Plan completed" />
        <div style={{ display: "flex", gap: 14, flexWrap: "wrap", fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>
          {quarters.map(r => <span key={r.quarter}>Q{r.quarter}: {r.completed} of {r.planned}</span>)}
          {!!quarters.unscheduled && <span>No quarter: {quarters.unscheduled}</span>}
        </div>
      </div>

      <FilterBar>
        <input aria-label="Search plan entries" placeholder="Search areas…" value={q} onChange={e => reset(setQ)(e.target.value)} style={{ ...filterField, minWidth: 180 }} />
        <select aria-label="Risk level" value={risk} onChange={e => reset(setRisk)(e.target.value)} style={filterField}>
          <option value="all">All risk levels</option>
          {["CRITICAL", "HIGH", "MEDIUM", "LOW"].map(r => <option key={r} value={r}>{r.charAt(0) + r.slice(1).toLowerCase()}</option>)}
          <option value="NONE">Not assessed</option>
        </select>
        <select aria-label="Status" value={group} onChange={e => reset(setGroup)(e.target.value as Group | "all")} style={filterField}>
          <option value="all">All statuses</option>
          <option value="planned">Planned</option>
          <option value="inProgress">In progress</option>
          <option value="completed">Completed</option>
          <option value="deferred">Deferred</option>
        </select>
      </FilterBar>

      {entries.length === 0 ? (
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No universe entries selected into this plan yet.</p>
      ) : rows.length === 0 ? (
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No entries match these filters.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 560 }}>
            <thead><tr><th style={th}>Area</th><th style={th}>Quarter</th><th style={th}>Risk</th><th style={th}>Status</th><th style={th}>Rationale</th></tr></thead>
            <tbody>
              {pg.items.map(e => (
                <tr key={e.id}>
                  <td style={{ ...td, fontWeight: 600 }}>{e.universeEntryName}</td>
                  <td style={td}>{e.plannedQuarter ? `Q${e.plannedQuarter}` : "—"}</td>
                  <td style={td}><RiskBadge level={e.riskLevel} /></td>
                  <td style={td}><StagePill label={entryStage(e.status).label} group={entryStage(e.status).group} /></td>
                  <td style={{ ...td, color: "var(--hf-text-muted)" }}>{e.rationale ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager page={pg} onPage={setPage} />
    </div>
  )
}
