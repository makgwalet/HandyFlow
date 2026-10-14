import type { CSSProperties } from "react"

export const inp: CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, background: "var(--hf-surface)", outline: "none", color: "var(--hf-text)" }
export const lbl: CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }
export const card: CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 18 }
export const pill = (on: boolean): CSSProperties => ({ padding: "6px 14px", borderRadius: 20, fontSize: 13, cursor: "pointer", border: "1px solid " + (on ? "var(--hf-orange-solid-strong)" : "var(--hf-border)"), fontWeight: on ? 700 : 500, background: on ? "var(--hf-orange-soft)" : "var(--hf-surface)", color: on ? "var(--hf-orange-text-strong)" : "var(--hf-text-muted)" })
export const primaryBtn = (disabled = false): CSSProperties => ({ padding: "9px 22px", background: disabled ? "var(--hf-text-faint)" : "var(--hf-orange-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: disabled ? "not-allowed" : "pointer" })
export const ghostBtn: CSSProperties = { padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }
