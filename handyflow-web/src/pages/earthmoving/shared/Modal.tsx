// src/pages/earthmoving/shared/Modal.tsx
import { useEffect } from "react"
import { X, AlertCircle } from "lucide-react"

/**
 * WHY this file exists: AssetsTab already had Overlay/ModalHead/ModalFoot/Sect
 * sub-components, but DeploymentsTab, IncidentsTab, MaintenanceTab and
 * OperatorLogsTab each hand-rolled their own copy of the same backdrop +
 * card markup instead of reusing them — four slightly-different modal
 * implementations doing the same job. Centralizing them here means:
 *   1. One visual language for every modal in the module (already true
 *      visually, but now also true in code).
 *   2. Two real bugs fixed once instead of missed in four places:
 *      - clicking the dark backdrop now actually closes the modal (the
 *        original Overlay accepted an onClose prop but never wired it to
 *        anything — dead code that looked like it worked).
 *      - Escape now closes the topmost modal, which is a baseline a11y
 *        expectation for any dialog.
 */

export function Overlay({ onClose, width = 620, children }: { onClose: () => void; width?: number; children: React.ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    window.addEventListener("keydown", onKey)
    return () => window.removeEventListener("keydown", onKey)
  }, [onClose])

  return (
    <div
      role="presentation"
      onClick={onClose}
      style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}
    >
      <div
        role="dialog"
        aria-modal="true"
        onClick={e => e.stopPropagation()} // don't let clicks inside the card bubble up and close it
        style={{ background: "#fff", borderRadius: 16, padding: 28, width, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}
      >
        {children}
      </div>
    </div>
  )
}

export function ModalHead({ title, subtitle, onClose }: { title: string; subtitle?: string; onClose: () => void }) {
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
      <div>
        <h3 style={{ margin: subtitle ? "0 0 3px" : 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
        {subtitle && <div style={{ fontSize: 12, color: "#94A3B8" }}>{subtitle}</div>}
      </div>
      <button onClick={onClose} aria-label="Close dialog" style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}>
        <X size={20} />
      </button>
    </div>
  )
}

export function ModalFoot({ onCancel, onSubmit, loading, label, disabled = false }: {
  onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean
}) {
  return (
    <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>
        Cancel
      </button>
      <button
        onClick={onSubmit}
        disabled={loading || disabled}
        style={{ padding: "9px 22px", background: loading || disabled ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}
      >
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}

export function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>
        {title}
      </div>
      {children}
    </div>
  )
}

export function ErrBanner({ msg }: { msg: string }) {
  return (
    <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
      <AlertCircle size={14} />{msg}
    </div>
  )
}

export const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

export const inputStyle = (hasError = false): React.CSSProperties => ({
  width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
  border: `1.5px solid ${hasError ? "#DC2626" : "#E2E8F0"}`,
  borderRadius: 8, fontSize: 14,
  background: hasError ? "#FFF5F5" : "#fff", outline: "none",
})
