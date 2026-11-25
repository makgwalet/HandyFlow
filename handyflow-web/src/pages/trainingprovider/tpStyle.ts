// Styles and small helpers shared by the accreditation and catalogue screens.
import type { CSSProperties } from "react"
import { TRAINPROV_ACCENT } from "./constants"

export const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
export const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
export const todayIso = (): string => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export const input: CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box", fontFamily: "inherit" }
export const solid: CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: TRAINPROV_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
export const ghost: CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
export const card: CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12 }
export const muted: CSSProperties = { fontSize: 12, color: "var(--hf-text-muted)" }


export const TONES: Record<string, CSSProperties> = {
  good: { background: "var(--hf-success-bg)", color: "var(--hf-success-text)" },
  warn: { background: "var(--hf-warning-bg)", color: "var(--hf-warning-text-strong)" },
  bad: { background: "var(--hf-danger-bg)", color: "var(--hf-danger-text)" },
  neutral: { background: "var(--hf-surface-muted)", color: "var(--hf-text-muted)" },
}
export const fmtDate = (d: string | null | undefined) => (d ? d : "—")
