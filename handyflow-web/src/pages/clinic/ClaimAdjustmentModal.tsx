import { useState } from "react"
import ModalShell from "./ModalShell"
import { adjustmentProblem, rand, reasonProblem } from "./claimMoney"
import { parseAmount } from "./claimPayment"

export type AdjustmentKind = "WRITE_OFF" | "CREDIT_NOTE" | "VOID"

const TEXT: Record<AdjustmentKind, { title: string; button: string; help: string }> = {
  WRITE_OFF: { title: "Write off", button: "Write off", help: "Accept that this part of the balance will not be paid. It is recorded with your name and reason and cannot be edited." },
  CREDIT_NOTE: { title: "Issue credit note", button: "Issue credit note", help: "Reduce what the scheme owes (for example after a rejection). The patient does not become liable. A numbered credit note is recorded." },
  VOID: { title: "Void claim", button: "Void claim", help: "Cancel a claim on which no money has moved. The consultation goes back to unbilled so a new claim can be made." },
}

/** Write-off, credit note or void: an amount (not for void) and a typed reason; nothing is sent without both. */
export default function ClaimAdjustmentModal({ kind, outstanding, busy, error, onConfirm, onClose }: {
  kind: AdjustmentKind; outstanding: number; busy?: boolean; error?: string
  onConfirm: (v: { amount?: number; reason: string }) => void; onClose: () => void
}) {
  const [amount, setAmount] = useState("")
  const [reason, setReason] = useState("")
  const [touched, setTouched] = useState(false)
  const t = TEXT[kind]
  const problem = kind === "VOID" ? reasonProblem(reason) : adjustmentProblem(amount, outstanding, reason)
  return (
    <ModalShell title={t.title} onClose={onClose} width={440}
      footer={<>
        <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
        <button type="button" disabled={busy || problem !== null}
          onClick={() => onConfirm({ amount: kind === "VOID" ? undefined : parseAmount(amount)!, reason: reason.trim() })}
          style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: kind === "VOID" ? "var(--hf-danger)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer", opacity: busy || problem ? 0.6 : 1 }}>
          {busy ? "Saving..." : t.button}
        </button></>}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 12 }}>{t.help}</div>
      {kind !== "VOID" && (
        <>
          <div style={{ fontSize: 13, marginBottom: 8 }}>The scheme still owes {rand(outstanding)}</div>
          <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 5 }}>Amount (R) *</label>
          <input aria-label="Amount" inputMode="decimal" value={amount} onChange={e => { setAmount(e.target.value); setTouched(true) }}
            style={{ width: "100%", padding: "9px 12px", boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, marginBottom: 12 }} />
        </>
      )}
      <label style={{ display: "block", fontSize: 13, fontWeight: 600, marginBottom: 5 }}>Reason *</label>
      <textarea aria-label="Reason" rows={3} value={reason} onChange={e => { setReason(e.target.value); setTouched(true) }}
        style={{ width: "100%", padding: "9px 12px", boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14 }} />
      {touched && problem && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{problem}</div>}
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
    </ModalShell>
  )
}
