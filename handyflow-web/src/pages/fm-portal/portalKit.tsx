// Small building blocks shared by the client portal's tabs.
import type { ReactNode } from "react"
import { color, radius, shadow, space } from "../accountant-portal/portal-theme"
import type { Tone } from "../facilitiesmanagement/fm.logic"

export const money = (n: number | null | undefined) => (n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)
export const day = (d: string | null | undefined) => (d ? new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "2-digit", month: "short", year: "numeric" }) : "—")
const TONES: Record<Tone, [string, string]> = { success: [color.greenBg, color.greenText], warning: [color.amberBg, color.amberText], danger: [color.redBg, color.redText], info: ["var(--hf-info-soft-strong)", "var(--hf-info-text)"], neutral: ["var(--hf-surface-sunken)", color.muted] }
export function Chip({ tone, children }: { tone: Tone; children: ReactNode }) {
  const [bg, fg] = TONES[tone]
  return <span style={{ background: bg, color: fg, fontSize: 11.5, fontWeight: 700, padding: "2px 9px", borderRadius: 20, whiteSpace: "nowrap" }}>{children}</span>
}
export const panel: React.CSSProperties = { background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, boxShadow: shadow.card, overflowX: "auto", overflowY: "hidden" }
export const empty: React.CSSProperties = { color: color.muted, fontSize: 13.5, padding: `${space(6)} ${space(4)}`, textAlign: "center" }
export const th: React.CSSProperties = { textAlign: "left", fontSize: 11, textTransform: "uppercase", letterSpacing: "0.04em", color: color.muted, fontWeight: 700, padding: "11px 16px", background: "var(--hf-surface-muted)", borderBottom: `1px solid ${color.border}`, whiteSpace: "nowrap" }
export const td: React.CSSProperties = { padding: "14px 16px", fontSize: 13.5, borderBottom: `1px solid ${color.borderLight}`, verticalAlign: "top", color: color.ink }
export const sub: React.CSSProperties = { fontSize: 12, color: color.muted }
export const inputStyle: React.CSSProperties = { width: "100%", padding: "10px 12px", border: `1.5px solid ${color.border}`, borderRadius: 8, fontSize: 14, boxSizing: "border-box", color: color.ink, background: color.surface, fontFamily: "inherit" }
export const btn = (primary = false, danger = false): React.CSSProperties => ({ padding: "8px 14px", border: primary ? "none" : `1px solid ${danger ? color.redText : color.border}`, borderRadius: 8, background: primary ? color.navy : color.surface, color: primary ? "var(--hf-text-on-solid)" : danger ? color.redText : color.slate, fontSize: 13, fontWeight: 700, cursor: "pointer" })
export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return <label style={{ display: "block", marginBottom: space(3) }}><span style={{ display: "block", fontSize: 12.5, fontWeight: 600, color: color.slate, marginBottom: 4 }}>{label}</span>{children}{hint && <span style={{ display: "block", fontSize: 11.5, color: color.faint, marginTop: 3 }}>{hint}</span>}</label>
}
export function Modal({ title, onClose, children, footer }: { title: string; onClose: () => void; children: ReactNode; footer: ReactNode }) {
  return (
    <div role="dialog" aria-modal="true" aria-label={title} onMouseDown={e => { if (e.target === e.currentTarget) onClose() }} style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", padding: space(4), zIndex: 50 }}>
      <div style={{ background: color.surface, borderRadius: radius.lg, width: 560, maxWidth: "100%", maxHeight: "92vh", display: "flex", flexDirection: "column", boxShadow: shadow.modal }}>
        <div style={{ padding: `${space(4)} ${space(5)}`, borderBottom: `1px solid ${color.border}`, fontWeight: 800, color: color.ink, fontSize: 16 }}>{title}</div>
        <div style={{ padding: space(5), overflowY: "auto" }}>{children}</div>
        <div style={{ padding: `${space(3)} ${space(5)}`, borderTop: `1px solid ${color.border}`, display: "flex", gap: space(2), justifyContent: "flex-end", flexWrap: "wrap" }}>{footer}</div>
      </div>
    </div>
  )
}
export const errText = (e: any, fallback: string): string => e?.response?.data?.message ?? fallback

/** A first name to greet someone with. A login that only has an email gets the part that reads like a name ("a+naledi@x.com" gives "Naledi"). */
export function greetingName(fullName: string | null | undefined): string {
  const raw = (fullName ?? "").trim()
  if (!raw) return ""
  const name = raw.includes("@") ? (raw.split("@")[0].split("+").pop() ?? "") : raw.split(/\s+/)[0]
  const clean = name.replace(/[^\p{L}\p{N}'-]+/gu, " ").trim().split(" ")[0]
  return clean ? clean.charAt(0).toUpperCase() + clean.slice(1) : ""
}
export const initialOf = (name: string | null | undefined) => (greetingName(name) || "?").charAt(0).toUpperCase()
