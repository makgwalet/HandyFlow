// src/pages/agriculture/AgCostLedgerTab.tsx
//
// The Agriculture cost ledger (ADR-001, W1) for one farm: net cost by category, every cost with how it was split, and reversal.
// Needs AGRICULTURE_FINANCE (ledger rows carry rates derived from salaries). Costs are never edited: a mistake is reversed, which writes
// a negating row dated like the original, so history stays complete. The existing direct costs (feed, health, inputs, seed, animal
// purchases) are NOT here; they stay where they are recorded, so nothing is counted twice. Cost reports will combine both in a later step.
import { useState } from "react"
import { Plus, Lock } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { useCostEntries, useCostTotals, useReverseCost, type TargetFilter, type TargetType } from "./agLedger.api"
import { useTargetOptions } from "./agTargets"
import { CATEGORY_LABEL, TARGET_LABEL, TARGET_TYPES, groupEntries, type CostGroup } from "./agLedger.logic"
import AgCostEntryForm from "./AgCostEntryForm"
import { Empty, Field } from "./agCropsUi"
import { btnDanger, btnGhost, btnPrimary, card, fmtDate, fmtMoney, inp, kpiLabel, kpiValue, panel, statusBadge } from "./constants"

const MAX_ROWS = 200

function LedgerBody({ farmId }: { farmId: string }) {
  const [type, setType] = useState<TargetType | "">("")
  const [targetId, setTargetId] = useState("")
  const [adding, setAdding] = useState(false)
  const [reversing, setReversing] = useState<CostGroup | null>(null)
  const [reason, setReason] = useState("")
  const options = useTargetOptions(farmId)
  const filter: TargetFilter | null = type && targetId ? { type, id: targetId } : null
  const entries = useCostEntries(farmId, filter)
  const totals = useCostTotals(farmId, filter)
  const reverse = useReverseCost()
  const costs = groupEntries(entries.data?.content ?? [])
  const labelOf = (t: string, id: string) => options.find(o => o.type === t && o.id === id)?.label ?? `${TARGET_LABEL[t as TargetType] ?? t}`

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 12, marginBottom: 14 }}>
        <div style={card}><p style={kpiLabel}>{filter ? "Net cost, this target" : "Net cost, this farm"}</p><p style={kpiValue}>{totals.data ? fmtMoney(totals.data.total) : "—"}</p></div>
        {(totals.data?.byCategory ?? []).map(c => <div key={c.category} style={card}><p style={kpiLabel}>{CATEGORY_LABEL[c.category] ?? c.category}</p><p style={{ ...kpiValue, fontSize: 20 }}>{fmtMoney(c.amount)}</p></div>)}
      </div>

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "end", marginBottom: 14 }}>
        <Field label="Show costs for" htmlFor="cl-filter-type">
          <select id="cl-filter-type" style={{ ...inp, width: "auto", minWidth: 150 }} value={type} onChange={e => { setType(e.target.value as TargetType | ""); setTargetId("") }}>
            <option value="">The whole farm</option>{TARGET_TYPES.map(t => <option key={t} value={t}>{TARGET_LABEL[t]}</option>)}
          </select>
        </Field>
        {type && (
          <Field label={TARGET_LABEL[type]} htmlFor="cl-filter-target">
            <select id="cl-filter-target" style={{ ...inp, width: "auto", minWidth: 200 }} value={targetId} onChange={e => setTargetId(e.target.value)}>
              <option value="">Choose…</option>{options.filter(o => o.type === type).map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
            </select>
          </Field>
        )}
        <div style={{ flex: 1 }} />
        {!adding && <button type="button" style={btnPrimary} onClick={() => setAdding(true)}><Plus size={14} />Record a cost</button>}
      </div>

      {adding && <AgCostEntryForm farmId={farmId} options={options} initialTarget={filter} onDone={() => setAdding(false)} />}

      {reversing && (
        <div style={panel}>
          <p style={{ fontSize: 13, margin: "0 0 10px" }}>Reverse "{reversing.description}" ({fmtMoney(reversing.total)})? It stays in the ledger, marked reversed, with a negating entry, so the history is kept. To correct it, reverse it and record it again.</p>
          <Field label="Reason (optional)" htmlFor="cl-reason"><input id="cl-reason" style={inp} value={reason} onChange={e => setReason(e.target.value)} /></Field>
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <button type="button" style={btnDanger} disabled={reverse.isPending} onClick={() => reverse.mutate({ groupId: reversing.id, reason }, { onSuccess: () => { setReversing(null); setReason("") } })}>Reverse cost</button>
            <button type="button" style={btnGhost} onClick={() => { setReversing(null); setReason("") }}>Cancel</button>
          </div>
        </div>
      )}

      {entries.isLoading ? <Empty>Loading the cost ledger…</Empty> : entries.isError ? (
        <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load the cost ledger. <button type="button" onClick={() => entries.refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>
      ) : costs.length === 0 ? <Empty>{filter ? "No costs recorded for this yet." : "No direct costs recorded yet. Use Record a cost for things like a hired contractor."}</Empty> : (
        <div style={{ display: "grid", gap: 10 }}>
          {costs.map(c => (
            <div key={c.id} style={{ ...card, opacity: c.status === "REVERSED" ? 0.7 : 1 }} aria-label={c.description}>
              <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                <strong style={{ fontSize: 14, color: "var(--hf-text)", textDecoration: c.status === "REVERSED" ? "line-through" : undefined }}>{c.description}</strong>
                <span style={statusBadge(c.status === "REVERSED" ? "ABANDONED" : "ACTIVE")}>{c.status === "REVERSED" ? "Reversed" : CATEGORY_LABEL[c.category] ?? c.category}</span>
                <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{fmtDate(c.date)}</span>
                <div style={{ flex: 1 }} />
                <strong style={{ textDecoration: c.status === "REVERSED" ? "line-through" : undefined }}>{fmtMoney(c.total)}</strong>
                {c.status === "ACTIVE" && !reversing && <button type="button" style={{ ...btnGhost, padding: "4px 10px", fontSize: 11.5 }} onClick={() => setReversing(c)}>Reverse</button>}
              </div>
              <ul style={{ listStyle: "none", margin: "8px 0 0", padding: 0, fontSize: 12.5, color: "var(--hf-text-muted)" }}>
                {c.rows.map(r => <li key={r.id} style={{ display: "flex", justifyContent: "space-between", maxWidth: 460 }}><span>{TARGET_LABEL[r.targetType]}: {labelOf(r.targetType, r.targetId)}{c.rows.length > 1 ? ` (${r.percentage}%)` : ""}</span><span>{fmtMoney(r.amount)}</span></li>)}
              </ul>
              {c.status === "REVERSED" && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Reversed{c.reversedNote ? `: ${c.reversedNote}` : ""}</p>}
            </div>
          ))}
          {(entries.data?.totalElements ?? 0) > MAX_ROWS && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>Showing the newest {MAX_ROWS} ledger rows.</p>}
        </div>
      )}
    </div>
  )
}

export default function AgCostLedgerTab({ farmId }: { farmId: string }) {
  const canFinance = usePermission("AGRICULTURE_FINANCE")
  if (!canFinance) {
    return (
      <div role="note" style={{ textAlign: "center", padding: "36px 12px" }}>
        <Lock size={26} style={{ color: "var(--hf-text-disabled)", marginBottom: 8 }} />
        <p style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>You don't have access to cost data</p>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>The cost ledger needs the Agriculture finance permission. Ask an administrator to grant it.</p>
      </div>
    )
  }
  return <LedgerBody farmId={farmId} />
}
