// src/pages/fleet/AlertsTab.tsx
//
// One list of what needs attention: vehicles that cannot be driven, papers expiring, services due and fuel flags. Each row opens its screen.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { buildAlerts, periodRange, type AlertInputs, type Severity } from "./fuelAnalytics.logic"
import { fetchAllPages } from "./apiShape"

const list = <T,>(r: { data: unknown }): T[] => { const o = r.data as { data?: unknown }; const p = (o && typeof o === "object" && "data" in o ? o.data : r.data) as T[] | { content?: T[] }; return (Array.isArray(p) ? p : p?.content) ?? [] }
const COLOUR: Record<Severity, string> = { high: "var(--hf-danger-text)", medium: "var(--hf-warning-text)", low: "var(--hf-text-faint)" }
const LABEL: Record<Severity, string> = { high: "Urgent", medium: "Soon", low: "For info" }

export default function AlertsTab({ onNavigate }: { onNavigate: (section: string) => void }) {
  const { from, to } = periodRange("30")
  const fuel = useQuery({ queryKey: ["fleet", "alerts-fuel"], queryFn: async () => list<AlertInputs["fuel"][number]>(await apiClient.get(`/api/v1/fleet/fuel-analytics/alerts?from=${from}&to=${to}`)) })
  const accidents = useQuery({ queryKey: ["fleet", "accidents", ""], queryFn: async () => list<AlertInputs["accidents"][number]>(await apiClient.get("/api/v1/fleet/accidents")) })
  const vehicles = useQuery({ queryKey: ["fleet-vehicles"], queryFn: async () => (await fetchAllPages<AlertInputs["vehicles"][number]>(u => apiClient.get(u), "/api/v1/fleet/vehicles")).items })
  const alerts = buildAlerts({ fuel: fuel.data ?? [], accidents: accidents.data ?? [], vehicles: vehicles.data ?? [] })
  const loading = fuel.isLoading || accidents.isLoading || vehicles.isLoading
  return (
    <div>
      {loading && <p>Loading…</p>}
      {!loading && alerts.length === 0 && <p style={{ color: "var(--hf-success-text-strong)", fontWeight: 700 }}>Nothing needs attention.</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>{alerts.map(a => (
        <li key={a.key} aria-label={`${a.registration} ${a.message}`} style={{ display: "flex", gap: 12, alignItems: "center", padding: "10px 12px", border: "1px solid var(--hf-border)", borderLeft: `4px solid ${COLOUR[a.severity]}`, borderRadius: 8, marginBottom: 8, background: "var(--hf-surface)" }}>
          <span style={{ fontSize: 11, fontWeight: 800, color: COLOUR[a.severity], width: 60 }}>{LABEL[a.severity]}</span>
          <div style={{ flex: 1, fontSize: 13 }}><strong>{a.registration}</strong> · {a.source}<div style={{ color: "var(--hf-text-muted)" }}>{a.message}</div></div>
          <button type="button" onClick={() => onNavigate(a.section)} style={{ padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", cursor: "pointer", fontSize: 12.5 }}>Open</button>
        </li>))}</ul>
    </div>
  )
}
