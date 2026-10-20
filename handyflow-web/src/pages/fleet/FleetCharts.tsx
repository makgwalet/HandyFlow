// src/pages/fleet/FleetCharts.tsx
//
// Dashboard cards: fleet status donut and the costliest vehicles. Drawn with plain SVG/CSS so they need no chart library.
import { costBars, donutSlices, statusCounts, type CostRow } from "./fleet.logic"

const LABEL: Record<string, string> = { AVAILABLE: "Available", ON_TRIP: "On trip", MAINTENANCE: "Maintenance", BREAKDOWN: "Breakdown", RETIRED: "Retired" }
const COLOUR: Record<string, string> = {
  AVAILABLE: "var(--hf-success-text-strong)", ON_TRIP: "var(--hf-info-text)", MAINTENANCE: "var(--hf-warning-text)", BREAKDOWN: "var(--hf-danger-text)", RETIRED: "var(--hf-text-faint)",
}
const card: React.CSSProperties = { background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const R = (n: number) => `R ${Math.round(n).toLocaleString("en-ZA")}`

export function StatusDonut({ vehicles }: { vehicles: { status: string }[] }) {
  const counts = statusCounts(vehicles)
  const slices = donutSlices(counts)
  const r = 40, c = 2 * Math.PI * r
  return (
    <div style={card}>
      <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 10 }}>Fleet status</div>
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <svg width="110" height="110" viewBox="0 0 110 110" role="img" aria-label={`Fleet status: ${vehicles.length} vehicles`}>
          <circle cx="55" cy="55" r={r} fill="none" stroke="var(--hf-surface-sunken)" strokeWidth="16" />
          {slices.map(s => (
            <circle key={s.status} data-testid={`slice-${s.status}`} cx="55" cy="55" r={r} fill="none" stroke={COLOUR[s.status]} strokeWidth="16"
              strokeDasharray={`${s.length * c} ${c}`} strokeDashoffset={-s.start * c} transform="rotate(-90 55 55)" />
          ))}
          <text x="55" y="60" textAnchor="middle" fontSize="20" fontWeight="800" fill="var(--hf-text)">{vehicles.length}</text>
        </svg>
        <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 12.5 }}>
          {counts.map(s => (
            <li key={s.status} style={{ display: "flex", gap: 6, alignItems: "center", padding: "2px 0" }}>
              <span style={{ width: 9, height: 9, borderRadius: 99, background: COLOUR[s.status] }} />{LABEL[s.status]}: <strong>{s.count}</strong>
            </li>))}
        </ul>
      </div>
    </div>
  )
}

export function CostliestVehicles({ rows }: { rows: CostRow[] }) {
  const bars = costBars(rows)
  return (
    <div style={card}>
      <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 10 }}>Costliest vehicles</div>
      {bars.length === 0 && <div style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>No service or fuel costs recorded yet.</div>}
      {bars.map(b => (
        <div key={b.registration} style={{ marginBottom: 8 }}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12 }}><span>{b.registration}</span><strong>{R(b.totalCost)}</strong></div>
          <div style={{ height: 6, background: "var(--hf-surface-sunken)", borderRadius: 99 }}>
            <div data-testid={`bar-${b.registration}`} style={{ height: "100%", width: `${Math.round(b.share * 100)}%`, background: "var(--hf-accent)", borderRadius: 99 }} />
          </div>
        </div>))}
    </div>
  )
}
