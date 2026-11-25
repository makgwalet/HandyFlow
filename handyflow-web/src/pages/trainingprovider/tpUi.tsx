// Small shared components for the accreditation and catalogue screens.
import type { ReactNode } from "react"
import { TRAINPROV_ACCENT, TRAINPROV_ACCENT_TEXT } from "./constants"
import { TONES, muted } from "./tpStyle"
export function Badge({ text, tone = "neutral" }: { text: string; tone?: "good" | "warn" | "bad" | "neutral" }) {
  return <span style={{ ...TONES[tone], fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 999, whiteSpace: "nowrap" }}>{text}</span>
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label style={{ display: "grid", gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)" }}>{label}</span>
      {children}
      {hint && <span style={muted}>{hint}</span>}
    </label>
  )
}

export function Notice({ text }: { text: string }) {
  return text ? <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{text}</div> : null
}

export function SubTabs({ tabs, value, onChange }: { tabs: [string, string][]; value: string; onChange: (id: string) => void }) {
  return (
    <div role="tablist" style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
      {tabs.map(([id, text]) => (
        <button key={id} role="tab" aria-selected={value === id} onClick={() => onChange(id)}
          style={{ padding: "7px 14px", borderRadius: 999, border: "1px solid var(--hf-border)", fontSize: 13, fontWeight: 600, cursor: "pointer",
            background: value === id ? TRAINPROV_ACCENT : "transparent", color: value === id ? "var(--hf-text-on-solid)" : TRAINPROV_ACCENT_TEXT }}>
          {text}
        </button>
      ))}
    </div>
  )
}

