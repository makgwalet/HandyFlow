// Small shared pieces for the warehousing screens: input styles, an error line, a modal frame and a reason prompt.
import { useState } from "react"
import { X } from "lucide-react"
import { WHSE_ACCENT } from "./constants"

export const inputStyle: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit", background: "var(--hf-surface)", color: "var(--hf-text)" }
export const labelStyle: React.CSSProperties = { fontSize: 11.5, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4, display: "block" }
export const btnPrimary: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "8px 14px", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }
export const btnGhost: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, background: "none", color: "var(--hf-text-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "8px 14px", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }
export const btnDanger: React.CSSProperties = { ...btnGhost, color: "var(--hf-danger-text)", border: "1px solid var(--hf-danger-border)" }
export const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)" }

/** The server's message when there is one (its errors are written for people), otherwise the fallback. */
export function errText(e: unknown, fallback: string): string {
  return (e as any)?.response?.data?.message ?? fallback
}

export function ErrorLine({ error, fallback = "Something went wrong" }: { error: unknown; fallback?: string }) {
  if (!error) return null
  return <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{errText(error, fallback)}</p>
}

export function Modal({ title, onClose, children, width = 440 }: { title: string; onClose: () => void; children: React.ReactNode; width?: number }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50, padding: 16 }}>
      <div role="dialog" aria-label={title} style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 22, width, maxWidth: "100%", maxHeight: "90vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{title}</p>
          <button onClick={onClose} aria-label="Close" style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} style={{ color: "var(--hf-text-faint)" }} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

/** Asks for a reason (at least 5 characters) before something that needs one, such as blocking a lot. */
export function ReasonModal({ title, intro, confirmLabel, pending, error, onConfirm, onClose }: {
  title: string; intro?: string; confirmLabel: string; pending?: boolean; error?: unknown; onConfirm: (reason: string) => void; onClose: () => void
}) {
  const [reason, setReason] = useState("")
  const ok = reason.trim().length >= 5
  return (
    <Modal title={title} onClose={onClose}>
      {intro && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>{intro}</p>}
      <label style={labelStyle} htmlFor="whse-reason">Reason *</label>
      <textarea id="whse-reason" style={{ ...inputStyle, width: "100%", minHeight: 70 }} value={reason} onChange={e => setReason(e.target.value)} maxLength={300} />
      <ErrorLine error={error} />
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button onClick={onClose} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Back</button>
        <button onClick={() => onConfirm(reason.trim())} disabled={!ok || pending} style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: ok ? 1 : 0.5 }}>{pending ? "Working…" : confirmLabel}</button>
      </div>
    </Modal>
  )
}

export function Chip({ children, bg, fg }: { children: React.ReactNode; bg: string; fg: string }) {
  return <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: bg, color: fg, whiteSpace: "nowrap" }}>{children}</span>
}

export const today = () => new Date().toLocaleDateString("en-CA")
export const fmtQty = (n: number | null | undefined) => (n == null ? "–" : n.toLocaleString("en-ZA", { maximumFractionDigits: 3 }))
