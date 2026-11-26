import React from "react"
import { BORDER, CANVAS, INK, MUTED, FAINT, btnPrimary, btnSecondary } from "./uiStyles"

export function StatusBadge({ status }: { status: string }) {
  const tones: Record<string, { c: string; bg: string }> = {
    ACTIVE: { c: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" }, FINALISED: { c: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
    PAID: { c: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" }, FILED: { c: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
    DRAFT: { c: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" }, CALCULATED: { c: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
    APPROVED: { c: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" }, AWAITING_CLIENT: { c: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
    CLIENT_APPROVED: { c: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" }, REVERSED: { c: "var(--hf-danger-text, #b91c1c)", bg: "var(--hf-danger-soft, #fef2f2)" }, PENDING: { c: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
    SENT: { c: "var(--hf-info-text)", bg: "var(--hf-info-soft)" }, PARTIAL: { c: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
    OVERDUE: { c: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" }, OFFBOARDED: { c: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" },
    CANCELLED: { c: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" },
    TERMINATED: { c: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" }, REVOKED: { c: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  }
  const t = tones[status] ?? tones.DRAFT
  return <span style={{ background: t.bg, color: t.c, padding: "2px 8px", borderRadius: 20, fontSize: 10.5, fontWeight: 700 }}>{status}</span>
}

export function Table({ headers, children }: { headers: string[]; children: React.ReactNode }) {
  return (
    <table style={{ width: "100%", borderCollapse: "collapse" as const, background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 8, overflow: "hidden" }}>
      <thead>
        <tr style={{ background: CANVAS }}>
          {headers.map(h => <th key={h} style={{ textAlign: "left" as const, padding: "8px 12px", fontSize: 11, fontWeight: 600, color: MUTED, textTransform: "uppercase" as const, letterSpacing: "0.03em" }}>{h}</th>)}
        </tr>
      </thead>
      <tbody>{children}</tbody>
    </table>
  )
}

export function Empty({ text }: { text: string }) {
  return <div style={{ padding: 32, textAlign: "center" as const, color: FAINT, fontSize: 13 }}>{text}</div>
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}
      onClick={onClose}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 12, padding: 24, width: 460, maxHeight: "85vh", overflowY: "auto" as const, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}
        onClick={e => e.stopPropagation()}>
        <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 800, color: INK }}>{title}</h3>
        {children}
      </div>
    </div>
  )
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 12, flex: 1 }}>
      <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: MUTED, marginBottom: 4 }}>{label}</label>
      {children}
    </div>
  )
}

export function FieldError({ text }: { text?: string }) {
  if (!text) return null
  return <div style={{ fontSize: 11, color: "var(--hf-danger-text)", marginTop: 3 }}>{text}</div>
}

export function ErrorBox({ text }: { text: string }) {
  return <div style={{ padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, fontSize: 12.5, color: "var(--hf-danger-text)", marginBottom: 12 }}>{text}</div>
}

export function ModalActions({ onClose, onSubmit, saving, submitLabel }: { onClose: () => void; onSubmit: () => void; saving: boolean; submitLabel: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 4 }}>
      <button onClick={onClose} style={btnSecondary}>Cancel</button>
      <button onClick={onSubmit} disabled={saving} style={btnPrimary}>{saving ? "Saving…" : submitLabel}</button>
    </div>
  )
}

