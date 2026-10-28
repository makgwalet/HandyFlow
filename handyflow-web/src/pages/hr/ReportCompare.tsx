// src/pages/hr/ReportCompare.tsx
//
// The headline figures of a report beside the same figures for an earlier period, and how each moved. Good and bad are only shown for figures
// where one direction is plainly better; headcount, pay and the like are shown without a verdict.
import { Block } from "./ReportParts"
import { td, th } from "./reports.shared"
import { change, formatChange, formatValue, type Metric } from "./reports.compare"
import { fmtDate } from "./er.logic"
import type { Period } from "./reports.logic"

const TONE = { good: "var(--hf-success, #1a7f37)", bad: "var(--hf-danger, #b42318)", neutral: "inherit" } as const
const ARROW = { up: "▲", down: "▼", same: "●", none: "" } as const

export default function ReportCompare({ metrics, now, before, label }: { metrics: Metric[]; now: Period; before: Period; label: string }) {
  return (
    <Block title="Comparison">
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>
        {label}: {fmtDate(now.from)} to {fmtDate(now.to)} against {fmtDate(before.from)} to {fmtDate(before.to)}.
      </p>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead>
          <tr><th style={th}>Figure</th><th style={{ ...th, textAlign: "right" }}>This period</th><th style={{ ...th, textAlign: "right" }}>Compared with</th><th style={{ ...th, textAlign: "right" }}>Change</th></tr>
        </thead>
        <tbody>
          {metrics.map(x => {
            const c = change(x)
            return (
              <tr key={x.label}>
                <td style={td}>{x.label}</td>
                <td style={{ ...td, textAlign: "right" }}>{formatValue(x.current, x.unit)}</td>
                <td style={{ ...td, textAlign: "right" }}>{formatValue(x.before, x.unit)}</td>
                <td style={{ ...td, textAlign: "right", color: TONE[c.tone] }}>
                  <span aria-hidden>{ARROW[c.direction]} </span>{formatChange(x)}
                  {c.tone !== "neutral" && <span style={{ position: "absolute", left: -9999 }}>{c.tone === "good" ? " (better)" : " (worse)"}</span>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </Block>
  )
}
