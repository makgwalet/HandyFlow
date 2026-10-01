// src/pages/agriculture/AgSaleAllocator.tsx
//
// Find an issued invoice line and attribute it to crop cycles, groups, animals or enterprises. Agriculture records the attribution only: the
// invoice stays in Invoicing, revenue is computed live from it, and nothing here marks animals sold or changes a head count.
import { useState } from "react"
import { Search, X } from "lucide-react"
import { todayISO } from "./agCrops.logic"
import { Field, Warn } from "./agCropsUi"
import { TARGET_LABEL, TARGET_TYPES } from "./agLedger.logic"
import type { TargetType } from "./agLedger.api"
import { useAllocateSale, useSaleLines, type SaleLine } from "./agSales.api"
import { defaultRange, previewShare, quantityText, validateSale, type SaleShareInput } from "./agSales.logic"
import type { TargetOption } from "./agTargets"
import { btnGhost, btnPrimary, fmtDate, fmtMoney, grid, inp, panel } from "./constants"

export default function AgSaleAllocator({ farmId, options, initialTarget, onDone }: { farmId: string; options: TargetOption[]; initialTarget?: { type: TargetType; id: string } | null; onDone: () => void }) {
  const today = todayISO()
  const [range, setRange] = useState(defaultRange(today))
  const [q, setQ] = useState("")
  const [committed, setCommitted] = useState({ ...defaultRange(today), q: "" })
  const [line, setLine] = useState<SaleLine | null>(null)
  const [shares, setShares] = useState<SaleShareInput[]>([])
  const [soldOn, setSoldOn] = useState("")
  const [notes, setNotes] = useState("")
  const lines = useSaleLines(farmId, committed, true)
  const allocate = useAllocateSale(farmId)

  const choose = (l: SaleLine) => {
    setLine(l)
    setShares([{ targetType: initialTarget?.type ?? "", targetId: initialTarget?.id ?? "", quantity: String(l.remainingQuantity), headCount: "" }])
    setSoldOn(""); setNotes("")
  }
  const setShare = (i: number, patch: Partial<SaleShareInput>) => setShares(prev => prev.map((s, k) => (k === i ? { ...s, ...patch } : s)))
  const problem = line ? validateSale(line, shares, soldOn, today) : null

  const submit = () => line && allocate.mutate({
    invoiceLineId: line.lineItemId, soldOn: soldOn || undefined, notes: notes.trim() || undefined,
    allocations: shares.map(s => ({ targetType: s.targetType as TargetType, targetId: s.targetId, quantity: Number(s.quantity.replace(",", ".")), headCount: s.headCount.trim() ? Number(s.headCount) : undefined })),
  }, { onSuccess: onDone })

  return (
    <div style={panel}>
      <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 4px" }}>Allocate a sale to production</p>
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "0 0 12px" }}>Pick an issued invoice line and say which crop cycle, group, animal or enterprise it belongs to. The invoice stays in Invoicing and revenue follows it (ex-VAT, net of credit notes). This does not mark animals sold or change a head count.</p>

      {!line ? (
        <>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "end", marginBottom: 12 }}>
            <Field label="From" htmlFor="sa-from"><input id="sa-from" type="date" style={{ ...inp, width: "auto" }} value={range.from} max={range.to} onChange={e => setRange({ ...range, from: e.target.value })} /></Field>
            <Field label="To" htmlFor="sa-to"><input id="sa-to" type="date" style={{ ...inp, width: "auto" }} value={range.to} max={today} onChange={e => setRange({ ...range, to: e.target.value })} /></Field>
            <Field label="Invoice, customer or item" htmlFor="sa-q"><input id="sa-q" style={{ ...inp, minWidth: 220 }} value={q} onChange={e => setQ(e.target.value)} placeholder="e.g. INV-0042 or ABC Foods" /></Field>
            <button type="button" style={btnPrimary} onClick={() => setCommitted({ from: range.from, to: range.to, q })}><Search size={14} />Find</button>
            <button type="button" style={btnGhost} onClick={onDone}>Cancel</button>
          </div>
          {lines.isLoading ? <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)" }}>Looking for invoice lines…</p> : lines.isError ? (
            <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load invoice lines. Check the dates (at most a year) and try again.</p>
          ) : (lines.data ?? []).length === 0 ? <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>No issued invoice lines found for these dates.</p> : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
                <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase", letterSpacing: 0.4 }}>
                  {["Invoice", "Customer", "Item", "Quantity", "Revenue (ex-VAT)", "Left to allocate", ""].map((h, i) => <th key={i} style={{ padding: "8px 10px", fontWeight: 700 }}>{h}</th>)}
                </tr></thead>
                <tbody>
                  {(lines.data ?? []).map(l => (
                    <tr key={l.lineItemId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                      <td style={{ padding: "9px 10px", fontWeight: 700 }}>{l.invoiceNumber}<div style={{ fontSize: 11, color: "var(--hf-text-faint)", fontWeight: 400 }}>{fmtDate(l.issuedOn)}</div></td>
                      <td style={{ padding: "9px 10px" }}>{l.customerName ?? "—"}</td>
                      <td style={{ padding: "9px 10px" }}>{l.description}</td>
                      <td style={{ padding: "9px 10px" }}>{quantityText(l.quantity, l.unit)}</td>
                      <td style={{ padding: "9px 10px" }}>{fmtMoney(l.netRevenue)}</td>
                      <td style={{ padding: "9px 10px" }}>{l.remainingQuantity > 0 ? quantityText(l.remainingQuantity, l.unit) : "Fully allocated"}</td>
                      <td style={{ padding: "9px 10px" }}><button type="button" style={{ ...btnGhost, padding: "4px 10px", fontSize: 11.5, opacity: l.remainingQuantity > 0 ? 1 : 0.5 }} disabled={l.remainingQuantity <= 0} onClick={() => choose(l)}>Allocate</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      ) : (
        <>
          <div style={{ fontSize: 12.5, color: "var(--hf-text-secondary)", marginBottom: 12 }}>
            <strong>{line.invoiceNumber}</strong> · {line.customerName ?? "—"} · {line.description} · {quantityText(line.quantity, line.unit)} · {fmtMoney(line.netRevenue)} ex-VAT · <strong>{quantityText(line.remainingQuantity, line.unit)} left</strong>
          </div>
          {shares.map((s, i) => {
            const qty = Number(s.quantity.replace(",", "."))
            const about = previewShare(line.netRevenue, line.quantity, qty)
            return (
              <div key={i} style={{ display: "grid", gridTemplateColumns: "140px 1fr 120px 110px auto", gap: 8, alignItems: "end", marginBottom: 8 }}>
                <Field label={i === 0 ? "Type" : ""} htmlFor={`sa-type-${i}`}>
                  <select id={`sa-type-${i}`} aria-label={`Target type ${i + 1}`} style={inp} value={s.targetType} onChange={e => setShare(i, { targetType: e.target.value as TargetType | "", targetId: "" })}>
                    <option value="">Select…</option>{TARGET_TYPES.map(t => <option key={t} value={t}>{TARGET_LABEL[t]}</option>)}
                  </select>
                </Field>
                <Field label={i === 0 ? "Target" : ""} htmlFor={`sa-target-${i}`}>
                  <select id={`sa-target-${i}`} aria-label={`Target ${i + 1}`} style={inp} value={s.targetId} disabled={!s.targetType} onChange={e => setShare(i, { targetId: e.target.value })}>
                    <option value="">Select…</option>{options.filter(o => o.type === s.targetType).map(o => <option key={o.id} value={o.id}>{o.label}</option>)}
                  </select>
                </Field>
                <Field label={i === 0 ? `Quantity${line.unit ? ` (${line.unit})` : ""}` : ""} htmlFor={`sa-qty-${i}`}>
                  <input id={`sa-qty-${i}`} aria-label={`Quantity ${i + 1}`} type="number" min="0" step="any" style={inp} value={s.quantity} onChange={e => setShare(i, { quantity: e.target.value })} />
                </Field>
                <Field label={i === 0 ? "Head sold" : ""} htmlFor={`sa-head-${i}`}>
                  <input id={`sa-head-${i}`} aria-label={`Head sold ${i + 1}`} type="number" min="1" step="1" style={inp} value={s.headCount} onChange={e => setShare(i, { headCount: e.target.value })} placeholder="optional" />
                </Field>
                {shares.length > 1 ? <button type="button" aria-label={`Remove line ${i + 1}`} style={{ ...btnGhost, padding: "8px" }} onClick={() => setShares(prev => prev.filter((_, k) => k !== i))}><X size={14} /></button> : <span />}
                {about != null && <div style={{ gridColumn: "1 / -1", fontSize: 11.5, color: "var(--hf-text-faint)", marginTop: -4 }}>about {fmtMoney(about)} of revenue</div>}
              </div>
            )
          })}
          <button type="button" style={btnGhost} onClick={() => setShares(prev => [...prev, { targetType: "", targetId: "", quantity: "", headCount: "" }])}>Add another target</button>
          <div style={{ ...grid, marginTop: 12 }}>
            <Field label="Sold on (defaults to the invoice date)" htmlFor="sa-sold"><input id="sa-sold" type="date" max={today} style={inp} value={soldOn} onChange={e => setSoldOn(e.target.value)} /></Field>
            <Field label="Notes" htmlFor="sa-notes"><input id="sa-notes" style={inp} value={notes} onChange={e => setNotes(e.target.value)} /></Field>
          </div>
          {problem && <Warn tone="danger">{problem}</Warn>}
          <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
            <button type="button" style={{ ...btnPrimary, opacity: !problem && !allocate.isPending ? 1 : 0.5 }} disabled={!!problem || allocate.isPending} onClick={submit}>Save allocation</button>
            <button type="button" style={btnGhost} onClick={() => setLine(null)}>Back to invoice lines</button>
          </div>
        </>
      )}
    </div>
  )
}
