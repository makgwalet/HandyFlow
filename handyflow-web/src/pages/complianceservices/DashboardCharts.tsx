// src/pages/complianceservices/DashboardCharts.tsx
//
// The small charts on the practice dashboard. One hue (the module's accent) for size, the danger colour only for what is late, direct figures on
// the marks that matter, and a table view of every chart so the numbers never rely on colour or shape alone.
import { useState, type ReactNode } from "react"
import { shares } from "./dashboardRules"

const ACCENT = "var(--hf-success-solid-strong)"
const LATE = "var(--hf-danger-text, #c0392b)"
const MUTED = "var(--hf-text-muted)"
const FAINT = "var(--hf-text-faint)"

export type Row = { label: string; value: number; display: string; late?: boolean; extra?: string }

export function ChartCard({ title, note, rows, columns, children }: { title: string; note?: string; rows: Row[]; columns: [string, string]; children: ReactNode }) {
  const [table, setTable] = useState(false)
  return (
    <section style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "14px 16px", display: "grid", gap: 10, alignContent: "start" }}>
      <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
        <span style={{ flex: 1 }} />
        <button onClick={() => setTable(t => !t)} aria-pressed={table} style={{ border: "none", background: "none", color: MUTED, fontSize: 12, cursor: "pointer", textDecoration: "underline" }}>{table ? "Chart" : "Table"}</button>
      </div>
      {note && <div style={{ fontSize: 12, color: MUTED }}>{note}</div>}
      {table ? (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
          <thead><tr><th style={{ textAlign: "left", color: MUTED, fontWeight: 600, padding: "4px 0" }}>{columns[0]}</th><th style={{ textAlign: "right", color: MUTED, fontWeight: 600 }}>{columns[1]}</th></tr></thead>
          <tbody>{rows.map(r => <tr key={r.label}><td style={{ padding: "3px 0", color: "var(--hf-text)" }}>{r.label}</td><td style={{ textAlign: "right", color: "var(--hf-text)" }}>{r.display}</td></tr>)}</tbody>
        </table>
      ) : children}
    </section>
  )
}

/** Upright bars on one baseline. A late bar is the danger colour and says so in its label. */
export function ColumnChart({ rows, label }: { rows: Row[]; label: string }) {
  const sizes = shares(rows.map(r => r.value))
  return (
    <div role="img" aria-label={label} style={{ display: "flex", gap: 6, alignItems: "flex-end", height: 140, borderBottom: "1px solid var(--hf-border)" }}>
      {rows.map((r, i) => (
        <div key={r.label} title={`${r.label}: ${r.display}${r.extra ? ` (${r.extra})` : ""}`} style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", justifyContent: "flex-end", alignItems: "center", height: "100%" }}>
          {r.value > 0 && <div style={{ fontSize: 11, color: r.late ? LATE : MUTED, marginBottom: 2, whiteSpace: "nowrap" }}>{r.display}</div>}
          <div style={{ width: "70%", maxWidth: 28, height: `${sizes[i] * 100 * 0.8}%`, minHeight: r.value > 0 ? 3 : 0, background: r.late ? LATE : ACCENT, borderRadius: "4px 4px 0 0" }} />
        </div>
      ))}
    </div>
  )
}

export function ColumnAxis({ labels }: { labels: string[] }) {
  return <div style={{ display: "flex", gap: 6 }}>{labels.map((l, i) => <div key={`${l}-${i}`} style={{ flex: 1, textAlign: "center", fontSize: 10.5, color: FAINT, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{l}</div>)}</div>
}

/** One bar per stage, longest first stage order kept; the count and the value are printed beside the bar. */
export function StageBars({ rows, label }: { rows: Row[]; label: string }) {
  const sizes = shares(rows.map(r => r.value))
  return (
    <div role="img" aria-label={label} style={{ display: "grid", gap: 6 }}>
      {rows.map((r, i) => (
        <div key={r.label} title={`${r.label}: ${r.display}`} style={{ display: "grid", gridTemplateColumns: "minmax(90px, 130px) 1fr auto", gap: 8, alignItems: "center", fontSize: 12 }}>
          <span style={{ color: "var(--hf-text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{r.label}</span>
          <span style={{ background: "var(--hf-surface-sunken)", borderRadius: 4, height: 10, display: "block" }}>
            <span style={{ display: "block", height: "100%", width: `${sizes[i] * 100}%`, background: ACCENT, borderRadius: 4 }} />
          </span>
          <span style={{ color: MUTED, whiteSpace: "nowrap" }}>{r.display}</span>
        </div>
      ))}
    </div>
  )
}

/** One whole split into a few named parts. Names and counts are in the legend, so nothing depends on colour alone. */
export function SegmentedBar({ rows, label }: { rows: { label: string; count: number; color: string }[]; label: string }) {
  const total = rows.reduce((n, r) => n + r.count, 0)
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <div role="img" aria-label={label} style={{ display: "flex", gap: 2, height: 14 }}>
        {rows.filter(r => r.count > 0).map(r => <span key={r.label} title={`${r.label}: ${r.count}`} style={{ flex: r.count, background: r.color, borderRadius: 4 }} />)}
        {total === 0 && <span style={{ flex: 1, background: "var(--hf-surface-sunken)", borderRadius: 4 }} />}
      </div>
      <div style={{ display: "flex", gap: 14, flexWrap: "wrap", fontSize: 12, color: "var(--hf-text)" }}>
        {rows.map(r => <span key={r.label} style={{ display: "inline-flex", gap: 6, alignItems: "center" }}><span style={{ width: 10, height: 10, borderRadius: 3, background: r.color, display: "inline-block" }} />{r.label} {r.count}</span>)}
      </div>
    </div>
  )
}
