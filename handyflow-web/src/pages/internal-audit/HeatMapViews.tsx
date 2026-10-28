// The residual risk and control effectiveness views of the heat map. Both are read from the two scores in each assessment.

import type { HeatArea } from "../../api/auditHeatMap.api"
import { Panel, Pill, Stat } from "./auditUi"
import { grid } from "./auditUi.styles"
import { BAND_STYLE, byControl, byResidual, controlByProcessArea, effectiveness, EFFECTIVENESS_ORDER, EFFECTIVENESS_STYLE, effectivenessCounts, residualBand, residualCounts, type Band } from "./heatMap.logic"

const MUTED = "var(--hf-text-muted)"
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", borderBottom: "1px solid var(--hf-border)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13, color: "var(--hf-text)", borderBottom: "1px solid var(--hf-border-subtle)" }
const BANDS: Band[] = ["critical", "high", "medium", "low"]

function Chip({ band }: { band: Band }) {
  const b = BAND_STYLE[band]
  return <span style={{ display: "inline-block", padding: "2px 10px", borderRadius: 999, background: b.bg, color: b.color, fontSize: 12, fontWeight: 700 }}>{b.label}</span>
}

const Empty = ({ text }: { text: string }) => <span style={{ fontSize: 13, color: MUTED }}>{text}</span>

export function ResidualView({ areas }: { areas: HeatArea[] }) {
  const counts = residualCounts(areas)
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={grid(130)}>
        {BANDS.map(b => <Stat key={b} label={`${BAND_STYLE[b].label} residual`} value={counts[b]} tone={(b === "critical" || b === "high") && counts[b] ? (b === "critical" ? "danger" : "warning") : undefined} />)}
      </div>
      <Panel title="Areas by residual risk">
        {areas.length === 0 ? <Empty text="No area has been assessed yet. Assess areas in the Audit Universe and they appear here." /> : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 520 }}>
              <thead><tr><th style={th}>Area</th><th style={th}>Process area</th><th style={{ ...th, textAlign: "center" }}>Inherent</th><th style={{ ...th, textAlign: "center" }}>Control risk</th><th style={th}>Residual</th><th style={th}>Final level</th></tr></thead>
              <tbody>
                {byResidual(areas).map(a => (
                  <tr key={a.name + a.processArea}>
                    <td style={{ ...td, fontWeight: 600 }}>{a.name}</td><td style={td}>{a.processArea}</td>
                    <td style={{ ...td, textAlign: "center" }}>{a.inherent}</td><td style={{ ...td, textAlign: "center" }}>{a.control}</td>
                    <td style={td}><Chip band={residualBand(a)} /></td>
                    <td style={{ ...td, color: MUTED }}>{a.finalRisk ? a.finalRisk.charAt(0) + a.finalRisk.slice(1).toLowerCase() : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p style={{ fontSize: 11.5, color: MUTED, margin: "12px 0 0" }}>Residual risk is what is left after controls, read from the two scores as the grid does (inherent × control risk: up to 4 low, up to 9 medium, up to 15 high, above that critical). It is not a separate rating anyone entered, and an area's final level also weighs its past findings, time since last audit and business impact.</p>
      </Panel>
    </div>
  )
}

export function ControlView({ areas }: { areas: HeatArea[] }) {
  const counts = effectivenessCounts(areas)
  const rows = controlByProcessArea(areas)
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={grid(150)}>
        {EFFECTIVENESS_ORDER.map(k => <Stat key={k} label={EFFECTIVENESS_STYLE[k].label} value={counts[k]} sub={EFFECTIVENESS_STYLE[k].hint} tone={k === "weak" && counts.weak ? "warning" : undefined} />)}
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 14, alignItems: "start" }}>
        <Panel title="By process area">
          {rows.length === 0 ? <Empty text="No area has been assessed yet." /> : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", minWidth: 340 }}>
                <thead><tr><th style={th}>Process area</th>{EFFECTIVENESS_ORDER.map(k => <th key={k} style={{ ...th, textAlign: "center" }}>{EFFECTIVENESS_STYLE[k].label}</th>)}</tr></thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.processArea}>
                      <td style={{ ...td, fontWeight: 600 }}>{r.processArea}</td>
                      {EFFECTIVENESS_ORDER.map(k => <td key={k} style={{ ...td, textAlign: "center", fontWeight: r.counts[k] ? 700 : 400, color: r.counts[k] ? (k === "weak" ? "var(--hf-warning-text)" : "var(--hf-text)") : "var(--hf-text-faint)" }}>{r.counts[k]}</td>)}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
        <Panel title="Weakest controls first">
          {areas.length === 0 ? <Empty text="No area has been assessed yet." /> : (
            <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
              {byControl(areas).slice(0, 10).map(a => {
                const e = EFFECTIVENESS_STYLE[effectiveness(a.control)]
                return (
                  <li key={a.name + a.processArea} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, padding: "7px 0", borderBottom: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                    <span><strong>{a.name}</strong> <span style={{ color: MUTED }}>{a.processArea}</span></span>
                    <Pill label={e.label} tone={e.tone} />
                  </li>
                )
              })}
            </ul>
          )}
        </Panel>
      </div>
      <p style={{ fontSize: 11.5, color: MUTED, margin: 0 }}>Control effectiveness is read from the control risk score in each assessment (1 to 5, higher is weaker control): 1 or 2 effective, 3 partly effective, 4 or 5 weak. It is not a separate rating from a control test.</p>
    </div>
  )
}
