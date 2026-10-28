// Risk Heat Map: where the audit universe sits by inherent risk and control risk, and how each process area is rated.

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { auditHeatMapApi, type HeatCell } from "../../api/auditHeatMap.api"
import { Panel, Stat } from "./auditUi"
import { grid } from "./auditUi.styles"
import { ControlView, ResidualView } from "./HeatMapViews"
import { BAND_STYLE, cellBand, cellLabel, gridRows, highShare, rowCounts, scoreWord } from "./heatMap.logic"

const MUTED = "var(--hf-text-muted)"
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", borderBottom: "1px solid var(--hf-border)" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)" }

export function HeatMapSection({ onGo }: { onGo?: (section: string) => void }) {
  const { data, isLoading, isError } = useQuery({ queryKey: ["ia-heat-map"], queryFn: auditHeatMapApi.get })
  const [view, setView] = useState<"grid" | "residual" | "control" | "areas">("grid")
  const [picked, setPicked] = useState<HeatCell | null>(null)

  if (isLoading) return <div style={{ color: MUTED, fontSize: 13 }}>Loading…</div>
  if (isError || !data) return <div role="alert" style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13 }}>The heat map could not be loaded. Refresh the page; if it keeps failing, contact support.</div>

  const rows = gridRows(data.cells)
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={grid(160)}>
        <Stat label="Areas in the universe" value={data.total} />
        <Stat label="Assessed" value={data.assessed} sub="have a risk assessment" />
        <Stat label="Not assessed" value={data.notAssessed} tone={data.notAssessed ? "warning" : undefined} sub={data.notAssessed ? "not on the map yet" : undefined}
          onClick={onGo && data.notAssessed ? () => onGo("universe") : undefined} />
        <Stat label="Rated high or critical" value={`${highShare(data.byProcessArea)}%`} sub="of the areas assessed" />
      </div>

      <div role="tablist" aria-label="Heat map view" style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {([["grid", "Inherent and control risk"], ["residual", "Residual risk"], ["control", "Control effectiveness"], ["areas", "By process area"]] as const).map(([id, label]) => (
          <button key={id} role="tab" aria-selected={view === id} onClick={() => setView(id)}
            style={{ padding: "7px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", background: view === id ? "var(--hf-info-soft)" : "var(--hf-surface)", color: view === id ? "var(--hf-primary-text)" : "var(--hf-text-secondary)", fontWeight: 600, fontSize: 13, cursor: "pointer" }}>{label}</button>
        ))}
      </div>

      {view === "residual" ? <ResidualView areas={data.areas ?? []} />
      : view === "control" ? <ControlView areas={data.areas ?? []} />
      : data.assessed === 0 && view === "grid" ? (
        <Panel title="Inherent and control risk"><span style={{ fontSize: 13, color: MUTED }}>No area has been assessed yet. Assess areas in the Audit Universe and they appear here.</span></Panel>
      ) : view === "grid" ? (
        <Panel title="Inherent risk against control risk">
          <div style={{ display: "grid", gridTemplateColumns: "auto 1fr", gap: 8, alignItems: "stretch" }}>
            <div style={{ writingMode: "vertical-rl", transform: "rotate(180deg)", textAlign: "center", fontSize: 12, color: MUTED }}>Inherent risk (higher is worse) →</div>
            <div>
              <table role="grid" aria-label="Heat map" style={{ borderCollapse: "separate", borderSpacing: 4, width: "100%", maxWidth: 560 }}>
                <tbody>
                  {rows.map(r => (
                    <tr key={r[0].inherent}>
                      <th scope="row" style={{ fontSize: 11, color: MUTED, width: 28, textAlign: "right", paddingRight: 6 }}>{r[0].inherent}</th>
                      {r.map(c => {
                        const b = BAND_STYLE[cellBand(c.inherent, c.control)]
                        const on = picked?.inherent === c.inherent && picked?.control === c.control
                        return (
                          <td key={c.control} style={{ padding: 0 }}>
                            <button type="button" aria-label={cellLabel(c)} aria-pressed={on} onClick={() => setPicked(on ? null : c)} disabled={c.count === 0}
                              style={{ width: "100%", aspectRatio: "1.3", minHeight: 44, borderRadius: 8, border: on ? "2px solid var(--hf-primary)" : "1px solid transparent", background: b.bg, color: b.color, fontWeight: 800, fontSize: 16, opacity: c.count === 0 ? 0.45 : 1, cursor: c.count === 0 ? "default" : "pointer" }}>
                              {c.count || ""}
                            </button>
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                  <tr>
                    <td />
                    {[1, 2, 3, 4, 5].map(n => <th key={n} scope="col" style={{ fontSize: 11, color: MUTED, paddingTop: 4 }}>{n}</th>)}
                  </tr>
                </tbody>
              </table>
              <div style={{ textAlign: "center", maxWidth: 560, fontSize: 12, color: MUTED }}>Control risk (higher is weaker control) →</div>
              <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginTop: 10, fontSize: 12 }}>
                {(["low", "medium", "high", "critical"] as const).map(k => (
                  <span key={k} style={{ display: "inline-flex", alignItems: "center", gap: 5 }}>
                    <span aria-hidden style={{ width: 12, height: 12, borderRadius: 3, background: BAND_STYLE[k].bg, border: "1px solid var(--hf-border)" }} />{BAND_STYLE[k].label}
                  </span>
                ))}
              </div>
            </div>
          </div>
          {picked && (
            <div style={{ marginTop: 14, borderTop: "1px solid var(--hf-border-subtle)", paddingTop: 10 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", marginBottom: 6 }}>
                Inherent {scoreWord(picked.inherent)}, control {scoreWord(picked.control)}
              </div>
              <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>{picked.areas.map(a => <li key={a}>{a}</li>)}</ul>
            </div>
          )}
          <p style={{ fontSize: 11.5, color: MUTED, margin: "12px 0 0" }}>Colours follow the product of the two scores (up to 4 low, up to 9 medium, up to 15 high, above that critical). An area's final risk level also weighs its past findings, time since last audit and business impact, so it can differ from its square.</p>
        </Panel>
      ) : (
        <Panel title="Areas by final risk level">
          {data.byProcessArea.length === 0 ? <span style={{ fontSize: 13, color: MUTED }}>The audit universe is empty.</span> : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 480 }}>
                <thead><tr><th style={th}>Process area</th>{(["low", "medium", "high", "critical"] as const).map(k => <th key={k} style={{ ...th, textAlign: "center" }}>{BAND_STYLE[k].label}</th>)}<th style={{ ...th, textAlign: "center" }}>Not assessed</th></tr></thead>
                <tbody>
                  {data.byProcessArea.map(r => (
                    <tr key={r.processArea}>
                      <td style={{ ...td, fontWeight: 600 }}>{r.processArea}</td>
                      {rowCounts(r).map(({ band, n }) => (
                        <td key={band} style={{ ...td, textAlign: "center", padding: 4 }}>
                          <div style={{ borderRadius: 6, padding: "6px 0", background: n ? BAND_STYLE[band].bg : "transparent", color: n ? BAND_STYLE[band].color : "var(--hf-text-faint)", fontWeight: 700 }}>{n}</div>
                        </td>
                      ))}
                      <td style={{ ...td, textAlign: "center", color: MUTED }}>{r.notAssessed}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      )}
    </div>
  )
}
