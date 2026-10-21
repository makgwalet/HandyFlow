// Small components shared by the attendance screens.
import type React from "react"
import { STATUS_LABEL, statusColors } from "./attendance.logic"

export function Chip({ text, color, bg }: { text: string; color: string; bg: string }) {
  return <span style={{ background: bg, color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, whiteSpace: "nowrap" }}>{text}</span>
}

export function StatusChip({ status }: { status: string }) {
  const [color, bg] = statusColors(status)
  return <Chip text={STATUS_LABEL[status] ?? status} color={color} bg={bg} />
}

export function Notice({ kind, children }: { kind: "ok" | "error"; children: React.ReactNode }) {
  const ok = kind === "ok"
  return (
    <div role={ok ? "status" : "alert"} style={{ marginBottom: 14, padding: "10px 14px", borderRadius: 8, fontSize: 13,
      background: ok ? "var(--hf-success-soft)" : "var(--hf-danger-soft)", color: ok ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>
      {children}
    </div>
  )
}
