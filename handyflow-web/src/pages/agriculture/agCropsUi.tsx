// src/pages/agriculture/agCropsUi.tsx
// Small shared pieces for the crop cycle screens.
import type { ReactNode } from "react"
import { lbl } from "./constants"

export function Field({ label, htmlFor, children }: { label: string; htmlFor?: string; children: ReactNode }) {
  return <div><label style={lbl} htmlFor={htmlFor}>{label}</label>{children}</div>
}

export function Th({ children }: { children: ReactNode }) {
  return <th style={{ padding: "8px 10px", fontWeight: 700, textAlign: "left" }}>{children}</th>
}

export function Empty({ children }: { children: ReactNode }) {
  return <p style={{ fontSize: 13, color: "var(--hf-text-muted)", textAlign: "center", padding: "24px 12px", margin: 0 }}>{children}</p>
}

export function Warn({ children, tone = "warning" }: { children: ReactNode; tone?: "warning" | "danger" }) {
  return <p role={tone === "danger" ? "alert" : "status"} style={{ fontSize: 12, margin: "8px 0 0", color: `var(--hf-${tone}-text)` }}>{children}</p>
}
