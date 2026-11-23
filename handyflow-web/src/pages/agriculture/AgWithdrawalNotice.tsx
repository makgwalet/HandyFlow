// src/pages/agriculture/AgWithdrawalNotice.tsx
//
// Shown above a sale, cull, transfer out or harvest when the animal, group or crop is inside a withdrawal period or
// pre-harvest interval. It says what holds it and until when, and asks for the reason that overrides it (kept on the record).
// Renders nothing when nothing is held.
import type React from "react"
import { holdSummary, type WithdrawalStatus } from "./agCompliance.logic"

export default function AgWithdrawalNotice({ status, kind, reason, onReason }: {
  status: WithdrawalStatus | undefined
  /** "withdrawal period" (animals) or "pre-harvest interval" (crops) */
  kind: "withdrawal period" | "pre-harvest interval"
  reason: string
  onReason: (v: string) => void
}) {
  if (!status?.held) return null
  return (
    <div role="alert" style={{ border: "1px solid var(--hf-warning-border, var(--hf-border))", background: "var(--hf-warning-bg, var(--hf-surface-muted))", borderRadius: 8, padding: 10, margin: "0 0 10px" }}>
      <p style={{ fontSize: 12.5, fontWeight: 700, color: "var(--hf-warning-text)", margin: "0 0 4px" }}>Within the {kind}</p>
      <p style={{ fontSize: 12, color: "var(--hf-text)", margin: "0 0 8px" }}>{holdSummary(status)} Going ahead now may put unsafe produce on the market.</p>
      <label style={lbl} htmlFor="ag-override-reason">Reason to go ahead anyway (kept on the record)</label>
      <input id="ag-override-reason" value={reason} onChange={e => onReason(e.target.value)} style={inp} placeholder="e.g. sold for breeding, not slaughter" />
    </div>
  )
}

const lbl: React.CSSProperties = { fontSize: 10.5, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 3, display: "block" }
const inp: React.CSSProperties = { width: "100%", padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, boxSizing: "border-box" }
