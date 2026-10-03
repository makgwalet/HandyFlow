// src/pages/agriculture/AgCostEntryForm.tsx
//
// Record an OTHER_DIRECT cost and allocate it across crop cycles, groups, animals or enterprises. The amounts shown are exactly what the
// server will save (same largest-remainder split), and the form only enables Save when the allocation is valid. Labour, equipment and
// fuel are not entered here: they will be costed from HR, Fleet and Fuel.
import { useState } from "react"
import { todayISO } from "./agCrops.logic"
import { Field, Warn } from "./agCropsUi"
import AgAllocationLines from "./AgAllocationLines"
import { useCreateCost, type TargetType } from "./agLedger.api"
import type { TargetOption } from "./agTargets"
import { splitPreview, validateAllocation, type ShareInput } from "./agLedger.logic"
import { btnGhost, btnPrimary, fmtMoney, grid, inp, panel } from "./constants"

export default function AgCostEntryForm({ farmId, options, initialTarget, onDone }: { farmId: string; options: TargetOption[]; initialTarget?: { type: TargetType; id: string } | null; onDone: () => void }) {
  const [f, setF] = useState({ date: todayISO(), description: "", amount: "", quantity: "", unit: "", notes: "" })
  const [shares, setShares] = useState<ShareInput[]>([{ targetType: initialTarget?.type ?? "", targetId: initialTarget?.id ?? "", percentage: "100" }])
  const create = useCreateCost(farmId)
  const today = todayISO()
  const problem = validateAllocation(f.amount, f.description, f.date, shares, today)
  const amount = Number(f.amount.replace(",", "."))
  const preview = !problem ? splitPreview(amount, shares.map(s => Number(s.percentage.replace(",", ".")))) : null

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
      <AgAllocationLines shares={shares} setShares={setShares} options={options} />

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
