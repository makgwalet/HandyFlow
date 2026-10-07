// src/pages/businessreadiness/SnapshotReadiness.tsx
//
// The evidence check as it stood when a tender was submitted, read from the frozen snapshot (never recalculated). Snapshots taken before this was stored have none, and say so.
import { basisText, chips, headline, itemNotes, RESULT_LABEL, sortItems } from "./readiness.logic"
import type { ReadinessAssessment } from "./readiness.api"

export function SnapshotReadiness({ readiness }: { readiness?: ReadinessAssessment | null }) {
  if (!readiness) {
    return <div style={{ marginTop: 8, color: "var(--hf-text-faint)" }}>The evidence check was not recorded when this was submitted.</div>
  }
  const attention = sortItems(readiness.items).filter(i => i.result !== "MET" && i.result !== "NOT_APPLICABLE")
  return (
    <div style={{ marginTop: 8 }}>
      <div style={{ fontWeight: 600, color: "var(--hf-text)" }}>Evidence at submission: {headline(readiness.summary)}</div>
      <div style={{ color: "var(--hf-text-muted)" }}>{basisText(readiness)}{chips(readiness.summary).length > 0 ? ` · ${chips(readiness.summary).map(c => c.text).join(", ")}` : ""}</div>
      {attention.map((it, i) => (
        <div key={i} style={{ color: "var(--hf-text-muted)" }}>
          · {RESULT_LABEL[it.result]}: {it.label}{it.detail ? ` — ${it.detail}` : ""}
          {itemNotes(it).map((n, j) => <div key={j} style={{ paddingLeft: 12 }}>{n}</div>)}
        </div>
      ))}
    </div>
  )
}

/** The price as it stood at submission, from the frozen snapshot. Absent when the tender was never priced, or when the viewer may not see pricing (the server leaves it out). */
export function SnapshotPrice({ pricing }: { pricing?: { breakdown: { priceInclVat: number; priceExVat: number }; lines: unknown[] } | null }) {
  if (!pricing) return null
  const zar = (v: number) => `R ${v.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  return (
    <div style={{ marginTop: 8, color: "var(--hf-text-muted)" }}>
      Price at submission: <strong style={{ color: "var(--hf-text)" }}>{zar(pricing.breakdown.priceInclVat)}</strong> including VAT ({zar(pricing.breakdown.priceExVat)} excluding) · {pricing.lines.length} {pricing.lines.length === 1 ? "item" : "items"}
    </div>
  )
}
