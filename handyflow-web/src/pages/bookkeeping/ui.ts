import type { CSSProperties } from "react"
export const card: CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14, overflowX: "auto" }
export const field: CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 6, fontSize: 12, background: "var(--hf-surface)", color: "var(--hf-text)" }
export const btn: CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }
export const primary: CSSProperties = { ...btn, background: "var(--hf-primary, #2563eb)", color: "#fff", border: "1px solid transparent" }
export const th: CSSProperties = { textAlign: "left", padding: "3px 8px", fontSize: 11, color: "var(--hf-text-muted)", textTransform: "uppercase" }
export const td: CSSProperties = { padding: "4px 8px", verticalAlign: "top" }
export const right: CSSProperties = { textAlign: "right", whiteSpace: "nowrap" }
export const muted: CSSProperties = { fontSize: 12, color: "var(--hf-text-secondary)" }
export const danger: CSSProperties = { color: "var(--hf-danger-text, #b91c1c)", fontSize: 12 }
