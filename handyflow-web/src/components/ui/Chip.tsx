// src/components/ui/Chip.tsx
//
// A small rounded label for states and countdowns ("Closes in 5 days", "Verified"). Tone picks the colours from the theme tokens.
import type { ReactNode } from "react"

export type ChipTone = "ok" | "warn" | "bad" | "info" | "accent" | "neutral"

const TONES: Record<ChipTone, { color: string; bg: string }> = {
  ok:      { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  warn:    { color: "var(--hf-warning-text)",        bg: "var(--hf-warning-soft)" },
  bad:     { color: "var(--hf-danger-text)",         bg: "var(--hf-danger-soft)" },
  info:    { color: "var(--hf-sky-text-strong)",     bg: "var(--hf-info-soft)" },
  accent:  { color: "var(--hf-accent-text)",         bg: "var(--hf-accent-soft)" },
  neutral: { color: "var(--hf-text-muted)",          bg: "var(--hf-surface-sunken)" },
}

export const toneColor = (t: ChipTone) => TONES[t].color

export default function Chip({ tone = "neutral", icon, children, title }: { tone?: ChipTone; icon?: ReactNode; children: ReactNode; title?: string }) {
  const c = TONES[tone]
  return (
    <span title={title} style={{ display: "inline-flex", alignItems: "center", gap: 5, background: c.bg, color: c.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, whiteSpace: "nowrap" }}>
      {icon}{children}
    </span>
  )
}
