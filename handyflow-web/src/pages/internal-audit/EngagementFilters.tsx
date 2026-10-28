// The summary strip and filters above the engagement list.

import type { Engagement } from "./auditShared"
import { FilterBar, Stat } from "./auditUi"
import { filterField, grid } from "./auditUi.styles"
import { engagementProgress, type Group } from "./auditUi.logic"

export function EngagementFilters({ engagements, stage, onStage, q, onQ }: {
  engagements: Engagement[]; stage: Group | "all"; onStage: (g: Group | "all") => void; q: string; onQ: (s: string) => void
}) {
  const p = engagementProgress(engagements)
  return (
    <div style={{ marginBottom: 6 }}>
      <div style={{ ...grid(130, 10), marginBottom: 12 }}>
        <Stat label="Engagements" value={p.total} />
        <Stat label="Planned" value={p.planned} />
        <Stat label="In progress" value={p.inProgress} />
        <Stat label="Completed" value={p.completed} />
      </div>
      <FilterBar>
        <input aria-label="Search engagements" placeholder="Search engagements…" value={q} onChange={e => onQ(e.target.value)} style={{ ...filterField, minWidth: 200 }} />
        <select aria-label="Engagement status" value={stage} onChange={e => onStage(e.target.value as Group | "all")} style={filterField}>
          <option value="all">All statuses</option>
          <option value="planned">Planned</option>
          <option value="inProgress">In progress (fieldwork and reporting)</option>
          <option value="completed">Completed</option>
        </select>
      </FilterBar>
    </div>
  )
}
