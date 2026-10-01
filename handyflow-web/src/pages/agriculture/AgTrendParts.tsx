// src/pages/agriculture/AgTrendParts.tsx
// Comparison cards ("+12% vs the 30 days before") shared by the Trends page and the dashboard strip.
import type { Comparison } from "./agTrends.api"
import { deltaText, deltaTone, formatComparison, type Tone } from "./agTrends.logic"
import { card, kpiLabel, kpiValue } from "./constants"

const TONE: Record<Tone, [string, string]> = {
  good: ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"],
  bad: ["var(--hf-danger-soft)", "var(--hf-danger-text)"],
  flat: ["var(--hf-surface-sunken)", "var(--hf-text-muted)"],
  none: ["var(--hf-surface-sunken)", "var(--hf-text-faint)"],
}

export function DeltaChip({ c }: { c: Comparison }) {
  const tone = deltaTone(c)
  const [bg, fg] = TONE[tone]
  return <span data-tone={tone} style={{ display: "inline-block", fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: bg, color: fg, whiteSpace: "nowrap" }}>{deltaText(c)}</span>
}

export function ComparisonCard({ c }: { c: Comparison }) {
  return (
    <div style={card} aria-label={c.label}>
      <p style={kpiLabel}>{c.label}</p>
      <p style={{ ...kpiValue, fontSize: 21 }}>{formatComparison(c, c.current)}</p>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 6, flexWrap: "wrap" }}>
        <DeltaChip c={c} />
        <span style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>was {formatComparison(c, c.previous)}</span>
      </div>
    </div>
  )
}
