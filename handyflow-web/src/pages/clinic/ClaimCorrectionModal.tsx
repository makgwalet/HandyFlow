import { useState } from "react"
import ModalShell from "./ModalShell"
import { correctionBody, correctionProblem, detailsChanged, detailsOf, type CorrectableClaim, type SchemeDetails } from "./claimCorrection"

const inp = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, marginBottom: 12 }
const lab = { display: "block", fontSize: 13, fontWeight: 600, marginBottom: 5 }

/** Correct a claim: the old one is voided and a linked draft is created. Scheme details can change; the lines are copied. */
export default function ClaimCorrectionModal({ claim, busy, error, onConfirm, onClose }: {
  claim: CorrectableClaim; busy?: boolean; error?: string
  onConfirm: (body: ReturnType<typeof correctionBody>) => void; onClose: () => void
}) {
  const [details, setDetails] = useState<SchemeDetails>(detailsOf(claim))
  const [reason, setReason] = useState("")
  const [touched, setTouched] = useState(false)
  const problem = correctionProblem(reason)
  const set = (k: keyof SchemeDetails) => (e: React.ChangeEvent<HTMLInputElement>) => { setDetails(d => ({ ...d, [k]: e.target.value })); setTouched(true) }
  return (
    <ModalShell title="Correct claim" onClose={onClose} width={480}
      footer={<>
        <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", cursor: "pointer" }}>Cancel</button>
        <button type="button" disabled={busy || problem !== null} onClick={() => onConfirm(correctionBody(claim, details, reason))}
          style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontWeight: 600, cursor: "pointer", opacity: busy || problem ? 0.6 : 1 }}>
          {busy ? "Saving..." : "Correct claim"}
        </button></>}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 12 }}>
        This claim is cancelled and a new draft is made for the same consultation, with the same lines. Change the scheme details below if they were wrong,
        then submit the new draft again. Once a payment, write-off or credit note stands against a claim it can no longer be corrected here.
      </div>
      <label style={lab}>Scheme</label>
      <input aria-label="Scheme" value={details.schemeName} onChange={set("schemeName")} style={inp} />
      <label style={lab}>Member number</label>
      <input aria-label="Member number" value={details.memberNumber} onChange={set("memberNumber")} style={inp} />
      <label style={lab}>Dependent code</label>
      <input aria-label="Dependent code" value={details.dependentCode} onChange={set("dependentCode")} style={inp} />
      {detailsChanged(claim, details) && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 10 }}>The new draft will use these scheme details.</div>}
      <label style={lab}>Reason for the correction *</label>
      <textarea aria-label="Reason" rows={3} value={reason} onChange={e => { setReason(e.target.value); setTouched(true) }} style={{ ...inp, marginBottom: 0 }} />
      {touched && problem && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{problem}</div>}
      {error && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
    </ModalShell>
  )
}
