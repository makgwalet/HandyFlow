// src/pages/agriculture/AgSalesTab.tsx
//
// Sales attributed to production (ADR-001, W2) for one farm. Agriculture does not own sales: an invoice line stays in Invoicing and the
// revenue shown here is computed live from it (ex-VAT, net of credit notes, only while the invoice is issued). Needs AGRICULTURE_FINANCE and
// INVOICE_READ, because it shows invoice data. Costs, margin and the combined profitability view come later (W5).
import { useState } from "react"
import { Plus, Lock } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import type { TargetFilter, TargetType } from "./agLedger.api"
import { TARGET_LABEL, TARGET_TYPES } from "./agLedger.logic"
import { useRemoveAllocation, useSalesAllocations, useSalesTotals } from "./agSales.api"
import { quantityText } from "./agSales.logic"
import { useTargetOptions } from "./agTargets"
import AgSaleAllocator from "./AgSaleAllocator"
import { Empty, Field } from "./agCropsUi"
import { btnDanger, btnGhost, btnPrimary, card, fmtDate, fmtMoney, inp, kpiLabel, kpiValue, panel, statusBadge } from "./constants"

function SalesBody({ farmId }: { farmId: string }) {
  const [type, setType] = useState<TargetType | "">("")
  const [targetId, setTargetId] = useState("")
  const [adding, setAdding] = useState(false)
  const [removing, setRemoving] = useState<string | null>(null)
  const options = useTargetOptions(farmId)
  const filter: TargetFilter | null = type && targetId ? { type, id: targetId } : null
  const allocations = useSalesAllocations(farmId, filter)
  const totals = useSalesTotals(farmId, filter)
  const remove = useRemoveAllocation()
  const rows = allocations.data?.content ?? []
  const labelOf = (t: string, id: string) => options.find(o => o.type === t && o.id === id)?.label ?? TARGET_LABEL[t as TargetType] ?? t
  const toRemove = rows.find(r => r.id === removing)

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 12, marginBottom: 14 }}>
        <div style={card}><p style={kpiLabel}>{filter ? "Revenue, this target" : "Revenue, this farm"}</p><p style={kpiValue}>{totals.data ? fmtMoney(totals.data.revenue) : "—"}</p><p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>ex-VAT, net of credit notes</p></div>
        <div style={card}><p style={kpiLabel}>Allocations</p><p style={kpiValue}>{totals.data?.allocationCount ?? "—"}</p></div>
        {(totals.data?.notCountedCount ?? 0) > 0 && <div style={card}><p style={kpiLabel}>Not counted</p><p style={{ ...kpiValue, color: "var(--hf-warning-text)" }}>{totals.data?.notCountedCount}</p><p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>on invoices that are no longer revenue</p></div>}
      </div>

      {(totals.data?.byTarget.length ?? 0) > 1 && !filter && (
        <div style={{ ...card, marginBottom: 14 }} aria-label="Revenue by target">
          <p style={{ ...kpiLabel, marginBottom: 8 }}>Revenue by target</p>
          {totals.data!.byTarget.map(t => <div key={`${t.targetType}:${t.targetId}`} style={{ display: "flex", justifyContent: "space-between", fontSize: 12.5, maxWidth: 480, padding: "2px 0" }}><span>{TARGET_LABEL[t.targetType]}: {labelOf(t.targetType, t.targetId)}</span><strong>{fmtMoney(t.revenue)}</strong></div>)}
        </div>
      )}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "end", marginBottom: 14 }}>
        <Field label="Show sales for" htmlFor="sl-filter-type">
          <select id="sl-filter-type" style={{ ...inp, width: "auto", minWidth: 150 }} value={type} onChange={e => { setType(e.target.value as TargetType | ""); setTargetId("") }}>
            <option value="">The whole farm</option>{TARGET_TYPES.map(t => <option key={t} value={t}>{TARGET_LABEL[t]}</option>)}
          </select>
        </Field>
        {type && (
          <Field label={TARGET_LABEL[type]} htmlFor="sl-filter-target">
            <select id="sl-filter-target" style={{ ...inp, width: "auto", minWidth: 200 }} value={targetId} onChange={e => setTargetId(e.target.value)}>
              <option value="">Choose…</option>{options.filter(o => o.type === type).map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
            </select>
          </Field>
        )}
        <div style={{ flex: 1 }} />
        {!adding && <button type="button" style={btnPrimary} onClick={() => setAdding(true)}><Plus size={14} />Allocate a sale</button>}
      </div>

      {adding && <AgSaleAllocator farmId={farmId} options={options} initialTarget={filter} onDone={() => setAdding(false)} />}

      {toRemove && (
        <div style={panel}>
          <p style={{ fontSize: 13, margin: "0 0 10px" }}>Remove the allocation of {quantityText(toRemove.quantity, toRemove.unit)} of "{toRemove.description}" ({toRemove.invoiceNumber}) to {labelOf(toRemove.targetType, toRemove.targetId)}? The invoice is not touched; this only takes it out of this {TARGET_LABEL[toRemove.targetType].toLowerCase()}'s revenue.</p>
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" style={btnDanger} disabled={remove.isPending} onClick={() => remove.mutate(toRemove.id, { onSuccess: () => setRemoving(null) })}>Remove allocation</button>
            <button type="button" style={btnGhost} onClick={() => setRemoving(null)}>Cancel</button>
          </div>
        </div>
      )}

      {allocations.isLoading ? <Empty>Loading sales…</Empty> : allocations.isError ? (
        <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load sales. <button type="button" onClick={() => allocations.refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>
      ) : rows.length === 0 ? <Empty>{filter ? "No sales are attributed to this yet." : "No sales attributed to production yet. Use Allocate a sale to link an issued invoice line to a crop cycle, group or animal."}</Empty> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
            <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase", letterSpacing: 0.4 }}>
              {["Sold", "Invoice", "Item", "Belongs to", "Quantity", "Revenue", ""].map((h, i) => <th key={i} style={{ padding: "8px 10px", fontWeight: 700 }}>{h}</th>)}
            </tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }} aria-label={`${r.invoiceNumber} ${r.description}`}>
                  <td style={{ padding: "9px 10px" }}>{fmtDate(r.soldOn)}</td>
                  <td style={{ padding: "9px 10px", fontWeight: 700 }}>{r.invoiceNumber ?? "—"}<div style={{ fontSize: 11, color: "var(--hf-text-faint)", fontWeight: 400 }}>{r.customerName ?? ""}</div></td>
                  <td style={{ padding: "9px 10px" }}>{r.description ?? "—"}</td>
                  <td style={{ padding: "9px 10px" }}>{TARGET_LABEL[r.targetType]}: {labelOf(r.targetType, r.targetId)}</td>
                  <td style={{ padding: "9px 10px" }}>{quantityText(r.quantity, r.unit)}{r.headCount ? <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{r.headCount} head</div> : null}</td>
                  <td style={{ padding: "9px 10px" }}>
                    {r.counted ? <strong>{fmtMoney(r.revenue)}</strong> : <span title={r.notCountedReason ?? undefined} style={statusBadge("OVERDUE")}>Not counted</span>}
                    {!r.counted && r.notCountedReason && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3, maxWidth: 220 }}>{r.notCountedReason}</div>}
                  </td>
                  <td style={{ padding: "9px 10px" }}>{!removing && <button type="button" style={{ ...btnGhost, padding: "4px 10px", fontSize: 11.5 }} onClick={() => setRemoving(r.id)}>Remove</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default function AgSalesTab({ farmId }: { farmId: string }) {
  const canFinance = usePermission("AGRICULTURE_FINANCE")
  const canInvoices = usePermission("INVOICE_READ")
  if (!canFinance || !canInvoices) {
    return (
      <div role="note" style={{ textAlign: "center", padding: "36px 12px" }}>
        <Lock size={26} style={{ color: "var(--hf-text-disabled)", marginBottom: 8 }} />
        <p style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>You don't have access to sales data</p>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>
          Linking sales to production needs the Agriculture finance permission{!canFinance && !canInvoices ? " and permission to view invoices" : !canInvoices ? " and, because it shows invoice data, permission to view invoices" : ""}. Ask an administrator.
        </p>
      </div>
    )
  }
  return <SalesBody farmId={farmId} />
}
