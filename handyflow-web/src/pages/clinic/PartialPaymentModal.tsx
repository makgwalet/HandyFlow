import { useState } from "react"
import { partialAmountProblem, parseAmount } from "./claimPayment"

const fmtR = (v: number) => `R ${(v || 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

/** Asks how much the scheme actually paid; there is no default. */
export default function PartialPaymentModal({ gross, busy, error, onConfirm, onClose }: {
  gross: number; busy?: boolean; error?: string; onConfirm: (amount: number) => void; onClose: () => void
}) {
  const [text, setText] = useState("")
  const [touched, setTouched] = useState(false)
  const problem = partialAmountProblem(text, gross)
  return (
    <div role="dialog" aria-label="Partial payment" style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 400, maxWidth: "92vw" }}>
        <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Partial payment</h3>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 14 }}>Claim total {fmtR(gross)}</div>
        <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 5 }}>Amount the scheme paid (R) *</label>
        <input aria-label="Amount the scheme paid" inputMode="decimal" autoFocus value={text}
          onChange={e => { setText(e.target.value); setTouched(true) }}
          style={{ width: "100%", padding: "9px 12px", boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14 }} />
        {touched && problem && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{problem}</div>}
        {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
        <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 18 }}>
          <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
          <button type="button" disabled={busy || problem !== null} onClick={() => onConfirm(parseAmount(text)!)}
            style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer", opacity: busy || problem ? 0.6 : 1 }}>
            {busy ? "Saving..." : "Record payment"}
          </button>
        </div>
      </div>
    </div>
  )
}
