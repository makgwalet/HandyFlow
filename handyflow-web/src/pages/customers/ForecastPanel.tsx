// src/pages/customers/ForecastPanel.tsx
// What the open pipeline might bring in, month by month. Each deal counts at the chance of winning at its stage.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { localToday, money, type PipelineLead } from "./pipeline.logic"
import { CHANCE_TEXT, buildForecast, rowLabel } from "./forecast.logic"

const rows = (r: { data: unknown }): PipelineLead[] => { const o = r.data as { data?: unknown } | null; const p = o && !Array.isArray(o) && typeof o === "object" && "data" in o ? o.data : r.data; return Array.isArray(p) ? (p as PipelineLead[]) : [] }

export default function ForecastPanel() {
  const { data, isLoading, isError } = useQuery<PipelineLead[]>({ queryKey: ["crm", "pipeline"], queryFn: async () => rows(await apiClient.get("/api/v1/crm/customers/pipeline")) })
  if (isLoading) return <p>Loading…</p>
  if (isError || !data) return <div role="alert">Couldn't load the pipeline.</div>
  const f = buildForecast(data, localToday())
  if (f.deals === 0) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No open deals, so there is nothing to forecast.</p>
  const tile = (label: string, value: string) => <div aria-label={label} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 14px", minWidth: 120 }}><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{label}</div><div style={{ fontSize: 20, fontWeight: 700 }}>{value}</div></div>
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>{tile("Open deals", String(f.deals))}{tile("Open deal value", money(f.total))}{tile("Weighted forecast", money(f.weighted))}</div>
      <table aria-label="Forecast by month" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
        <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Expected close</th><th>Deals</th><th>Deal value</th><th>Weighted</th></tr></thead>
        <tbody>{f.months.map(m => <tr key={m.month} aria-label={rowLabel(m.month)} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}><td style={{ padding: "6px 0" }}>{rowLabel(m.month)}</td><td>{m.deals}</td><td>{money(m.total)}</td><td>{money(m.weighted)}</td></tr>)}</tbody>
      </table>
      {(f.noValue > 0 || f.undated > 0) && <p role="note" style={{ fontSize: 12.5, margin: "10px 0 0" }}>{f.noValue > 0 && <>{f.noValue} open {f.noValue === 1 ? "deal has" : "deals have"} no value and count as R 0. </>}{f.undated > 0 && <>{f.undated} {f.undated === 1 ? "has" : "have"} no expected close date.</>}</p>}
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>Weighted = deal value × chance of winning at its stage ({CHANCE_TEXT}). These chances are fixed rules of thumb, not a prediction.</p>
    </div>
  )
}
