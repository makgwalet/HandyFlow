// Small building blocks for the cards on the patient overview.
import type { ReactNode } from "react"
import { BORDER, GRAY, flowCard } from "./patientFile.shared"

export function Card({ title, aside, children }: { title: string; aside?: ReactNode; children: ReactNode }) {
  return (
    <div style={{ ...flowCard, background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 12, padding: "14px 16px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8, minHeight: 22, gap: 8 }}>
        <div style={{ fontSize: 12, fontWeight: 800, letterSpacing: "0.05em", textTransform: "uppercase", color: "var(--hf-text-secondary)" }}>{title}</div>
        {aside}
      </div>
      {children}
    </div>
  )
}

export const Fact = ({ k, v }: { k: string; v: string }) => (
  <div style={{ display: "flex", gap: 10, padding: "4px 0", fontSize: 13 }}>
    <div style={{ width: 112, flexShrink: 0, color: GRAY, fontSize: 12 }}>{k}</div>
    <div style={{ color: "var(--hf-text)", fontWeight: 500, minWidth: 0, wordBreak: "break-word" }}>{v}</div>
  </div>
)

export const smallBtn: React.CSSProperties = { padding: "3px 10px", borderRadius: 6, border: `1px solid ${BORDER}`, background: "var(--hf-surface)", fontSize: 12, fontWeight: 600, cursor: "pointer", whiteSpace: "nowrap" }
export const field: React.CSSProperties = { padding: "6px 8px", fontSize: 12, borderRadius: 6, border: `1px solid ${BORDER}`, background: "var(--hf-surface)", color: "var(--hf-text)", minWidth: 0 }
export const primaryBtn: React.CSSProperties = { padding: "6px 12px", borderRadius: 6, border: "none", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 12, fontWeight: 600, cursor: "pointer" }
