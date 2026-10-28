// Audit Settings: the rules internal audit runs by. Read only, because none of these are stored as company settings.

import { useQuery } from "@tanstack/react-query"
import { auditSettingsApi } from "../../api/auditSettings.api"
import { Panel, Pill } from "./auditUi"

const MUTED = "var(--hf-text-muted)"
const TONE: Record<string, "success" | "info" | "warning" | "muted"> = { LOW: "success", MEDIUM: "info", HIGH: "warning", CRITICAL: "warning" }
const row: React.CSSProperties = { display: "flex", justifyContent: "space-between", gap: 12, fontSize: 13, padding: "6px 0", borderBottom: "1px solid var(--hf-border-subtle)" }

export function SettingsSection() {
  const { data, isLoading, isError } = useQuery({ queryKey: ["ia-settings"], queryFn: auditSettingsApi.get })
  if (isLoading) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  if (isError || !data) return <div role="alert" style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13 }}>Settings could not be loaded. Refresh the page; if it keeps failing, contact support.</div>

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <p style={{ fontSize: 13, color: MUTED, margin: 0 }}>These are the rules audit follows today. They are fixed in the product and cannot be changed here; this page shows them so everyone works from the same understanding.</p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 14 }}>
        <Panel title="Risk scoring">
          <p style={{ fontSize: 12.5, color: MUTED, margin: "0 0 10px" }}>Each area is scored 1 to 5 on {data.riskInputs.length} things ({data.riskInputs.join(", ").toLowerCase()}), equally weighted. The total ({data.scoreMin} to {data.scoreMax}) sets the level. An auditor can override the level with a reason.</p>
          {data.riskBands.map(b => (
            <div key={b.level} style={row}><Pill label={b.level.charAt(0) + b.level.slice(1).toLowerCase()} tone={TONE[b.level] ?? "muted"} /><span>{b.from} to {b.to}</span></div>
          ))}
        </Panel>
        <Panel title="Reminders">
          <div style={row}><span>Overdue items</span><span>Reminded on day 1, then every {data.repeatEveryDays} days</span></div>
          <div style={row}><span>Escalated to a manager</span><span>From {data.escalateAfterDays} days late</span></div>
          <div style={row}><span>Unanswered review comment</span><span>Day {data.firstReviewReminderDay}, then every {data.repeatEveryDays} days</span></div>
        </Panel>
        <Panel title="Engagement roles">
          <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {data.engagementRoles.map(r => <li key={r.key} style={row}><span>{r.label}</span></li>)}
          </ul>
          <p style={{ fontSize: 12, color: MUTED, margin: "10px 0 0" }}>Roles are given to people on each engagement, from the Engagements list.</p>
        </Panel>
      </div>
    </div>
  )
}
