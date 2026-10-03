// src/pages/agriculture/AgAllocationLines.tsx
//
// The "what is this cost for?" lines shared by every screen that allocates a cost across crop cycles, groups, animals or enterprises:
// a target per line with its share, add and remove, split evenly, and a running total that must reach 100. The caller owns the state and
// validates it (agLedger.logic.validateAllocation); this only edits it.
import { Plus, X } from "lucide-react"
import { Field } from "./agCropsUi"
import { TARGET_LABEL, TARGET_TYPES, evenShares, percentTotal, type ShareInput } from "./agLedger.logic"
import type { TargetType } from "./agLedger.api"
import type { TargetOption } from "./agTargets"
import { btnGhost, inp } from "./constants"

/** `idPrefix` keeps element ids unique, and `labelPrefix` (for example "Fuel: ") keeps the controls' accessible names distinguishable, when a page shows more than one of these. */
export default function AgAllocationLines({ shares, setShares, options, idPrefix = "cl", labelPrefix = "" }: {
  shares: ShareInput[]; setShares: (update: (prev: ShareInput[]) => ShareInput[]) => void; options: TargetOption[]; idPrefix?: string; labelPrefix?: string
}) {
  const total = percentTotal(shares)
  const setShare = (i: number, patch: Partial<ShareInput>) => setShares(prev => prev.map((s, k) => (k === i ? { ...s, ...patch } : s)))
  return (
    <>
      {shares.map((s, i) => (
        <div key={i} style={{ display: "grid", gridTemplateColumns: "150px 1fr 110px auto", gap: 8, alignItems: "end", marginBottom: 8 }}>
          <Field label={i === 0 ? "Type" : ""} htmlFor={`${idPrefix}-type-${i}`}>
            <select id={`${idPrefix}-type-${i}`} aria-label={`${labelPrefix}Target type ${i + 1}`} style={inp} value={s.targetType} onChange={e => setShare(i, { targetType: e.target.value as TargetType | "", targetId: "" })}>
              <option value="">Select…</option>{TARGET_TYPES.map(t => <option key={t} value={t}>{TARGET_LABEL[t]}</option>)}
            </select>
          </Field>
          <Field label={i === 0 ? "Target" : ""} htmlFor={`${idPrefix}-target-${i}`}>
            <select id={`${idPrefix}-target-${i}`} aria-label={`${labelPrefix}Target ${i + 1}`} style={inp} value={s.targetId} disabled={!s.targetType} onChange={e => setShare(i, { targetId: e.target.value })}>
              <option value="">Select…</option>{options.filter(o => o.type === s.targetType).map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
            </select>
          </Field>
          <Field label={i === 0 ? "Share (%)" : ""} htmlFor={`${idPrefix}-pct-${i}`}>
            <input id={`${idPrefix}-pct-${i}`} aria-label={`${labelPrefix}Share ${i + 1} percent`} type="number" min="0" max="100" step="0.01" style={inp} value={s.percentage} onChange={e => setShare(i, { percentage: e.target.value })} />
          </Field>
          {shares.length > 1 ? <button type="button" aria-label={`${labelPrefix}Remove line ${i + 1}`} style={{ ...btnGhost, padding: "8px" }} onClick={() => setShares(prev => prev.filter((_, k) => k !== i))}><X size={14} /></button> : <span />}
        </div>
      ))}
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 4 }}>
        <button type="button" style={btnGhost} onClick={() => setShares(prev => [...prev, { targetType: "", targetId: "", percentage: "0" }])}><Plus size={13} />Add another target</button>
        {shares.length > 1 && <button type="button" style={btnGhost} onClick={() => setShares(prev => evenShares(prev.length).map((p, k) => ({ ...prev[k], percentage: p })))}>Split evenly</button>}
        <span role="status" aria-label={`${labelPrefix}Allocation total`} style={{ fontSize: 12, fontWeight: 700, color: Math.abs(total - 100) <= 0.005 ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>Total {total}%</span>
      </div>

    </>
  )
}
