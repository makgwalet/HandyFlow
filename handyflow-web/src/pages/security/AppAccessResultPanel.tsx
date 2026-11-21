// src/pages/security/AppAccessResultPanel.tsx
//
// Shown after bulk app access: who got access, who was skipped, and the temporary PINs. The PINs exist only in this
// response (the server keeps a hash), so the panel says so and offers a printable sheet.
import { Download } from "lucide-react"
import { outcomeLabel, pinSheetCsv, SMS_NOTE, summaryText, type AccessBatch } from "./appAccessLogic"

const th: React.CSSProperties = { textAlign: "left", padding: "6px 8px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase" }
const td: React.CSSProperties = { padding: "7px 8px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)" }

export default function AppAccessResultPanel({ result, onClose }: { result: AccessBatch; onClose: () => void }) {
  const hasPins = result.results.some(r => r.outcome === "PROVISIONED" && r.temporaryPin)
  const download = () => {
    const blob = new Blob([pinSheetCsv(result.results)], { type: "text/csv;charset=utf-8" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a"); a.href = url; a.download = "guard-app-access.csv"; a.click(); URL.revokeObjectURL(url)
  }
  return (
    <div role="dialog" aria-label="App access result" style={{ marginBottom: 14, padding: 16, border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 8 }}>
        <strong style={{ fontSize: 14 }}>{summaryText(result)}</strong>
        <div style={{ display: "flex", gap: 8 }}>
          {hasPins && <button onClick={download} style={{ padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 13, fontWeight: 600, cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 6 }}><Download size={13} /> Download PIN sheet</button>}
          <button onClick={onClose} style={{ padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", fontSize: 13, cursor: "pointer" }}>Close</button>
        </div>
      </div>
      {hasPins && <div role="note" style={{ marginBottom: 10, padding: "8px 12px", borderRadius: 8, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text-deep)", fontSize: 12 }}>
        These PINs are shown once and cannot be looked up later. Download the sheet before closing this. {SMS_NOTE}
      </div>}
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Guard</th><th style={th}>Code</th><th style={th}>Result</th><th style={th}>Temporary PIN</th></tr></thead>
          <tbody>{result.results.map(r => (
            <tr key={r.guardId}>
              <td style={td}>{r.fullName ?? "Unknown guard"}</td>
              <td style={td}>{r.employeeCode ?? "-"}</td>
              <td style={td}>{outcomeLabel(r.outcome)}{r.message ? ` (${r.message})` : ""}</td>
              <td style={{ ...td, fontFamily: "monospace", letterSpacing: 2 }}>{r.outcome === "PROVISIONED" ? r.temporaryPin ?? "" : ""}</td>
            </tr>
          ))}</tbody>
        </table>
      </div>
    </div>
  )
}
