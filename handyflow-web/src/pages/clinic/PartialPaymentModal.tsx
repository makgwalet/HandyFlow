import { useState } from "react"
import ModalShell from "./ModalShell"
import { partialAmountProblem, parseAmount } from "./claimPayment"

const fmtR = (v: number) => `R ${(v || 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

/** Asks how much the scheme actually paid; there is no default. */
export default function PartialPaymentModal({ gross, label = "Claim total", busy, error, onConfirm, onClose }: {
  gross: number; label?: string; busy?: boolean; error?: string; onConfirm: (amount: number) => void; onClose: () => void
}) {
  const [text, setText] = useState("")
  const [touched, setTouched] = useState(false)
  const problem = partialAmountProblem(text, gross)
  return (
    <ModalShell title="Partial payment" onClose={onClose} width={400}
      footer={<>
        <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
        <button type="button" disabled={busy || problem !== null} onClick={() => onConfirm(parseAmount(text)!)}
          style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer", opacity: busy || problem ? 0.6 : 1 }}>
          {busy ? "Saving..." : "Record payment"}
        </button></>}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 14 }}>{label} {fmtR(gross)}</div>
      <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 5 }}>Amount the scheme paid (R) *</label>
      <input aria-label="Amount the scheme paid" inputMode="decimal" value={text}
        onChange={e => { setText(e.target.value); setTouched(true) }}
        style={{ width: "100%", padding: "9px 12px", boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14 }} />
      {touched && problem && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{problem}</div>}
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
    </ModalShell>
  )
}
