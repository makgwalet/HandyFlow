// src/components/ui/StatTile.tsx
//
// A headline number with an icon, used in the strips at the top of the compliance and tender screens.
import type { ReactNode } from "react"
import type { ChipTone } from "./Chip"
import { toneColor } from "./Chip"

export default function StatTile({ label, value, hint, icon, tone = "neutral" }: { label: string; value: ReactNode; hint?: string; icon?: ReactNode; tone?: ChipTone }) {
  const c = tone === "neutral" ? "var(--hf-text-secondary)" : toneColor(tone)
  return (
    <div style={{ flex: "1 1 140px", minWidth: 130, background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "14px 16px", display: "flex", gap: 12, alignItems: "center" }}>
      {icon && <div style={{ width: 38, height: 38, borderRadius: 10, background: "var(--hf-surface-sunken)", color: c, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>{icon}</div>}
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 22, fontWeight: 800, color: c, lineHeight: 1.1 }}>{value}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 3 }}>{label}</div>
        {hint && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 1 }}>{hint}</div>}
      </div>
    </div>
  )
}
