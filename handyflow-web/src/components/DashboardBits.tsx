// Small building blocks shared by the audit dashboards: a figure tile and a titled card.
import type { ReactNode } from "react"

const BORDER = "var(--hf-border)"
const INK = "var(--hf-text)"
const MUTED = "var(--hf-text-muted)"

export function Tile({ label, value, sub, tone }: { label: string; value: ReactNode; sub?: string; tone?: "danger" | "warning" }) {
  const color = tone === "danger" ? "var(--hf-danger-text)" : tone === "warning" ? "var(--hf-warning-text)" : INK
  return (
    <div style={{ background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 8, padding: "12px 14px", minWidth: 130, flex: "1 1 130px" }}>
      <div style={{ fontSize: 11.5, color: MUTED, marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 800, color }}>{value}</div>
      {sub && <div style={{ fontSize: 11, color: MUTED, marginTop: 2 }}>{sub}</div>}
    </div>
  )
}

export function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div style={{ background: "var(--hf-surface)", border: `1px solid ${BORDER}`, borderRadius: 8, padding: 16, marginBottom: 16 }}>
      <div style={{ fontSize: 13, fontWeight: 800, color: INK, marginBottom: 10 }}>{title}</div>
      {children}
    </div>
  )
}

export const TileRow = ({ children }: { children: ReactNode }) => (
  <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>{children}</div>
)
