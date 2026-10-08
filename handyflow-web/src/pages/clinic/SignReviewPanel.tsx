// "Ready to sign?": a safety check, not a summary. Lists what is in order, what is worth a look, and what stops the
// signature. A required step can be overridden only with a typed reason, which the server records (CLINIC-DEC-011).
import { useState, type ReactNode } from "react"
import { AlertCircle, AlertTriangle, CheckCircle, Loader } from "lucide-react"
import ModalShell from "./ModalShell"
import { overrideProblem, OVERRIDE_REASON_MAX, type CheckItem, type SignVerdict } from "./signRules"
import type { WizardStep } from "./consultWizard"
import { TEAL, cancelBtn, primaryBtn } from "./consultationSession.shared"

const ICON = { ok: CheckCircle, warn: AlertTriangle, required: AlertCircle, blocked: AlertCircle } as const
const COLOR = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", required: "var(--hf-danger-text)", blocked: "var(--hf-danger-text)" } as const

export default function SignReviewPanel({ mode, items, verdict, busy, error, onGoTo, onSign, onHandoff, children }: {
  mode: "sign" | "handoff"; items: CheckItem[]; verdict: SignVerdict; busy: boolean; error: string
  onGoTo: (s: WizardStep) => void; onSign: (overrideReason?: string) => void; onHandoff?: () => void; children?: ReactNode
}) {
  const [overriding, setOverriding] = useState(false)
  const [reason, setReason] = useState("")
  const [touched, setTouched] = useState(false)
  const problem = overrideProblem(reason)
  const required = items.filter(i => i.state === "required")
  const blocked = items.filter(i => i.state === "blocked")

  return (
    <section aria-label={mode === "sign" ? "Ready to sign?" : "Ready to hand over?"} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <div style={{ padding: "14px 16px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
        <h3 style={{ margin: "0 0 8px", fontSize: 15, fontWeight: 800, color: "var(--hf-text)" }}>{mode === "sign" ? "Ready to sign?" : "Ready to hand over?"}</h3>
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 6 }}>
          {items.map(i => {
            const Icon = ICON[i.state]
            return (
              <li key={i.id} data-state={i.state} style={{ display: "flex", gap: 8, alignItems: "flex-start", fontSize: 13 }}>
                <Icon size={15} aria-hidden="true" style={{ color: COLOR[i.state], marginTop: 2, flexShrink: 0 }} />
                <div style={{ flex: 1 }}>
                  <span style={{ fontWeight: 700, color: "var(--hf-text)" }}>{i.label}</span>
                  <span style={{ color: i.state === "ok" ? "var(--hf-text-muted)" : COLOR[i.state] }}> · {i.detail}</span>
                </div>
                {i.state !== "ok" && i.goTo && (
                  <button type="button" onClick={() => onGoTo(i.goTo!)} style={{ background: "none", border: "none", color: "var(--hf-accent-text)", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>Go there</button>
                )}
              </li>
            )
          })}
        </ul>
      </div>

      {children}

      {error && <div role="alert" style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      {mode === "handoff" ? (
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <button onClick={onHandoff} disabled={busy} style={{ ...primaryBtn, background: TEAL }}>{busy ? "Sending…" : "Send to doctor"}</button>
          <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>The doctor reviews and signs. Needs a chief complaint and at least one vital sign.</span>
        </div>
      ) : verdict.canSign ? (
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <button onClick={() => onSign()} disabled={busy} style={{ ...primaryBtn, background: TEAL, display: "flex", alignItems: "center", gap: 8 }}>
            {busy ? <><Loader size={14} aria-hidden="true" /> Signing…</> : <><CheckCircle size={15} aria-hidden="true" /> Sign &amp; complete</>}
          </button>
          <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Signing locks the record. Later changes are addenda.</span>
        </div>
      ) : (
        <div role="alert" style={{ padding: "12px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 12 }}>
          <div style={{ fontWeight: 800, color: "var(--hf-danger-text)", marginBottom: 4 }}>Cannot sign yet</div>
          <div style={{ fontSize: 13, color: "var(--hf-danger-text)", marginBottom: 8 }}>
            {blocked.length ? blocked.map(b => b.detail).join(" ") : required.map(r => r.label.replace(" documented", "").replace(" recorded", "")).join(" and ") + " required for this visit."}
          </div>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {required.map(r => <button key={r.id} type="button" onClick={() => onGoTo(r.goTo!)} style={cancelBtn}>Go to {r.id === "symptoms" ? "symptoms" : "diagnosis"}</button>)}
            {verdict.canOverride && <button type="button" onClick={() => { setOverriding(true); setTouched(false) }} style={{ ...cancelBtn, color: "var(--hf-danger-text)", borderColor: "var(--hf-danger-border)" }}>Override requirement…</button>}
          </div>
        </div>
      )}

      {overriding && (
        <ModalShell title="Override requirement" onClose={() => setOverriding(false)} width={480} footer={<>
          <button type="button" onClick={() => setOverriding(false)} style={cancelBtn}>Cancel</button>
          <button type="button" disabled={busy} onClick={() => { setTouched(true); if (!overrideProblem(reason)) { setOverriding(false); onSign(reason.trim()) } }}
            style={{ ...primaryBtn, background: "var(--hf-danger)" }}>Confirm override and sign</button>
        </>}>
          <p style={{ margin: "0 0 10px", fontSize: 13, color: "var(--hf-text-secondary)" }}>
            {required.map(r => r.label.replace(" documented", "").replace(" recorded", "")).join(" and ")} {required.length === 1 ? "is" : "are"} required for this visit.
            Signing without {required.length === 1 ? "it" : "them"} is recorded in the audit trail with your reason.
          </p>
          <label htmlFor="override-reason" style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text)" }}>Reason</label>
          <textarea id="override-reason" value={reason} onChange={e => setReason(e.target.value)} rows={3} maxLength={OVERRIDE_REASON_MAX + 50}
            style={{ width: "100%", boxSizing: "border-box", marginTop: 4, padding: 8, borderRadius: 8, border: "1px solid var(--hf-border)", fontSize: 13 }} />
          {touched && problem && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{problem}</div>}
        </ModalShell>
      )}
    </section>
  )
}
