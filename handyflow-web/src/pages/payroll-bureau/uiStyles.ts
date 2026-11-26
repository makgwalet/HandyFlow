import type React from "react"

export const fmtR = (n: unknown) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
export const fmtD = (d: unknown) => (d ? new Date(d as string).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

export const NAVY = "var(--hf-primary)"
export const NAVY_TEXT = "var(--hf-primary-text)";
export const BORDER = "var(--hf-border)"
export const CANVAS = "var(--hf-surface-muted)"
export const INK = "var(--hf-text)"
export const MUTED = "var(--hf-text-muted)"
export const FAINT = "var(--hf-text-faint)"


// ── Shared small components ──────────────────────────────────────────────────

export const inputStyle: React.CSSProperties = { width: "100%", padding: "8px 10px", border: `1.5px solid ${BORDER}`, borderRadius: 6, fontSize: 13, boxSizing: "border-box" }
export const errorInputStyle: React.CSSProperties = { borderColor: "var(--hf-danger)" }
export const btnPrimary: React.CSSProperties = { padding: "7px 14px", background: NAVY, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 6, fontSize: 12.5, fontWeight: 700, cursor: "pointer" }
export const btnPrimarySmall: React.CSSProperties = { ...btnPrimary, padding: "4px 10px", fontSize: 11.5 }
export const btnSecondary: React.CSSProperties = { padding: "6px 12px", background: "var(--hf-surface)", color: NAVY_TEXT, border: `1px solid ${NAVY}`, borderRadius: 6, fontSize: 12, fontWeight: 700, cursor: "pointer" }
export const rowStyle: React.CSSProperties = { borderTop: `1px solid ${BORDER}` }
export const cellStyle: React.CSSProperties = { padding: "8px 12px", fontSize: 12.5, color: INK }
