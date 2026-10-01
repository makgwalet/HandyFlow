// src/pages/agriculture/AgCostEntryForm.tsx
//
// Record an OTHER_DIRECT cost and allocate it across crop cycles, groups, animals or enterprises. The amounts shown are exactly what the
// server will save (same largest-remainder split), and the form only enables Save when the allocation is valid. Labour, equipment and
// fuel are not entered here: they will be costed from HR, Fleet and Fuel.
import { useState } from "react"
import { Plus, X } from "lucide-react"
import { todayISO } from "./agCrops.logic"
import { Field, Warn } from "./agCropsUi"
import { useCreateCost, type TargetType } from "./agLedger.api"
import { TARGET_LABEL, TARGET_TYPES, evenShares, percentTotal, splitPreview, validateAllocation, type ShareInput } from "./agLedger.logic"
import { btnGhost, btnPrimary, fmtMoney, grid, inp, panel } from "./constants"

export interface TargetOption { type: TargetType; id: string; label: string }

export default function AgCostEntryForm({ farmId, options, initialTarget, onDone }: { farmId: string; options: TargetOption[]; initialTarget?: { type: TargetType; id: string } | null; onDone: () => void }) {
  const [f, setF] = useState({ date: todayISO(), description: "", amount: "", quantity: "", unit: "", notes: "" })
  const [shares, setShares] = useState<ShareInput[]>([{ targetType: initialTarget?.type ?? "", targetId: initialTarget?.id ?? "", percentage: "100" }])
  const create = useCreateCost(farmId)
  const today = todayISO()
  const problem = validateAllocation(f.amount, f.description, f.date, shares, today)
  const amount = Number(f.amount.replace(",", "."))
  const preview = !problem ? splitPreview(amount, shares.map(s => Number(s.percentage.replace(",", ".")))) : null
  const total = percentTotal(shares)

  const setShare = (i: number, patch: Partial<ShareInput>) => setShares(prev => prev.map((s, k) => (k === i ? { ...s, ...patch } : s)))
  const labelOf = (s: ShareInput) => options.find(o => o.type === s.targetType && o.id === s.targetId)?.label ?? "—"

  const submit = () => create.mutate({
    entryDate: f.date, description: f.description.trim(), amount,
    quantity: f.quantity.trim() ? Number(f.quantity) : undefined, unit: f.unit.trim() || undefined, notes: f.notes.trim() || undefined,
    allocations: shares.map(s => ({ targetType: s.targetType as TargetType, targetId: s.targetId, percentage: Number(s.percentage.replace(",", ".")) })),
  }, { onSuccess: onDone })

  return (
    <div style={panel}>
      <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>Record a direct cost</p>
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "0 0 12px" }}>For costs that aren't feed, health, inputs or seed (those are recorded where they happen), for example a hired contractor or packaging. Labour, equipment and fuel will be costed from HR, Fleet and Fuel.</p>
      <div style={grid}>
        <Field label="Date *" htmlFor="cl-date"><input id="cl-date" type="date" max={today} style={inp} value={f.date} onChange={e => setF({ ...f, date: e.target.value })} /></Field>
        <Field label="Description *" htmlFor="cl-desc"><input id="cl-desc" style={inp} value={f.description} onChange={e => setF({ ...f, description: e.target.value })} placeholder="e.g. Hired sprayer, 3 days" /></Field>
        <Field label="Amount (R) *" htmlFor="cl-amount"><input id="cl-amount" type="number" min="0" step="0.01" style={inp} value={f.amount} onChange={e => setF({ ...f, amount: e.target.value })} /></Field>
        <Field label="Quantity" htmlFor="cl-qty"><input id="cl-qty" type="number" min="0" step="any" style={inp} value={f.quantity} onChange={e => setF({ ...f, quantity: e.target.value })} /></Field>
        <Field label="Unit" htmlFor="cl-unit"><input id="cl-unit" style={inp} value={f.unit} onChange={e => setF({ ...f, unit: e.target.value })} placeholder="days, hours…" /></Field>
        <Field label="Notes" htmlFor="cl-notes"><input id="cl-notes" style={inp} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} placeholder="Invoice or reference" /></Field>
      </div>

      <p style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text)", margin: "16px 0 8px" }}>What is this cost for?</p>
      {shares.map((s, i) => (
        <div key={i} style={{ display: "grid", gridTemplateColumns: "150px 1fr 110px auto", gap: 8, alignItems: "end", marginBottom: 8 }}>
          <Field label={i === 0 ? "Type" : ""} htmlFor={`cl-type-${i}`}>
            <select id={`cl-type-${i}`} aria-label={`Target type ${i + 1}`} style={inp} value={s.targetType} onChange={e => setShare(i, { targetType: e.target.value as TargetType | "", targetId: "" })}>
              <option value="">Select…</option>{TARGET_TYPES.map(t => <option key={t} value={t}>{TARGET_LABEL[t]}</option>)}
            </select>
          </Field>
          <Field label={i === 0 ? "Target" : ""} htmlFor={`cl-target-${i}`}>
            <select id={`cl-target-${i}`} aria-label={`Target ${i + 1}`} style={inp} value={s.targetId} disabled={!s.targetType} onChange={e => setShare(i, { targetId: e.target.value })}>
              <option value="">Select…</option>{options.filter(o => o.type === s.targetType).map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
            </select>
          </Field>
          <Field label={i === 0 ? "Share (%)" : ""} htmlFor={`cl-pct-${i}`}>
            <input id={`cl-pct-${i}`} aria-label={`Share ${i + 1} percent`} type="number" min="0" max="100" step="0.01" style={inp} value={s.percentage} onChange={e => setShare(i, { percentage: e.target.value })} />
          </Field>
          {shares.length > 1 ? <button type="button" aria-label={`Remove line ${i + 1}`} style={{ ...btnGhost, padding: "8px" }} onClick={() => setShares(prev => prev.filter((_, k) => k !== i))}><X size={14} /></button> : <span />}
        </div>
      ))}
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 4 }}>
        <button type="button" style={btnGhost} onClick={() => setShares(prev => [...prev, { targetType: "", targetId: "", percentage: "0" }])}><Plus size={13} />Add another target</button>
        {shares.length > 1 && <button type="button" style={btnGhost} onClick={() => setShares(prev => evenShares(prev.length).map((p, k) => ({ ...prev[k], percentage: p })))}>Split evenly</button>}
        <span role="status" aria-label="Allocation total" style={{ fontSize: 12, fontWeight: 700, color: Math.abs(total - 100) <= 0.005 ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>Total {total}%</span>
      </div>

      {preview && (
        <div style={{ marginTop: 12, fontSize: 12.5 }} aria-label="Allocation preview">
          <div style={{ fontWeight: 700, color: "var(--hf-text-secondary)", marginBottom: 4 }}>This will be saved as</div>
          {shares.map((s, i) => <div key={i} style={{ display: "flex", justifyContent: "space-between", maxWidth: 420 }}><span>{labelOf(s)} ({s.percentage}%)</span><strong>{fmtMoney(preview[i])}</strong></div>)}
        </div>
      )}
      {problem && f.description.trim() && f.amount.trim() && <Warn>{problem}</Warn>}

      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button type="button" style={{ ...btnPrimary, opacity: !problem && !create.isPending ? 1 : 0.5 }} disabled={!!problem || create.isPending} onClick={submit}>Save cost</button>
        <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
      </div>
    </div>
  )
}
