// src/pages/facilities/InvoiceMatch.tsx
//
// Pieces of the purchase-order screen: goods received notes, the three-way match, and the form for recording a supplier invoice.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { facilitiesApi, type PurchaseOrder } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, Dialog, Field, StatusChip } from './ui'
import { fmtDate, fmtMoney, fmtQty, inputStyle } from './format'

/** Goods received notes for one order. */
export function GoodsReceipts({ orderId }: { orderId: string }) {
  const q = useQuery({ queryKey: ['facilities', 'goods-receipts', orderId], queryFn: () => facilitiesApi.goodsReceipts(orderId) })
  const list = q.data ?? []
  return (
    <div>
      <h3 style={{ margin: '22px 0 8px', fontSize: 13, fontWeight: 700 }}>Goods received{list.length ? ` (${list.length})` : ''}</h3>
      {q.isLoading && <div aria-busy="true" style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>Loading…</div>}
      {q.isError && <div role="alert" style={{ fontSize: 12.5, color: 'var(--hf-danger-text)' }}>The receipts could not be loaded.</div>}
      {!q.isLoading && !q.isError && list.length === 0 && <div style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>Nothing received yet.</div>}
      {list.map(g => (
        <div key={g.id} style={{ padding: '9px 0', borderTop: '1px solid var(--hf-border-subtle)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
            <span><strong>{g.grnNumber}</strong> · {fmtDate(g.receivedOn)}{g.deliveryNote ? ` · ${g.deliveryNote}` : ''}</span>
            <span style={{ fontWeight: 600 }}>{fmtMoney(g.totalValue)}</span>
          </div>
          <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{g.lines.map(l => `${fmtQty(l.quantity)} × ${l.description}`).join(' · ')}</div>
        </div>
      ))}
    </div>
  )
}

const MATCH_TONE: Record<string, 'ok' | 'warn' | 'bad' | 'neutral' | 'info'> = { MATCHED: 'ok', UNDER_INVOICED: 'info', NOT_INVOICED: 'neutral', OVER_INVOICED: 'bad', NOT_RECEIVED: 'bad' }

/** Ordered / received / invoiced for an order, with its invoices and a way to record another. */
export function InvoiceMatch({ order, canManage }: { order: PurchaseOrder; canManage: boolean }) {
  const [recording, setRecording] = useState(false)
  const q = useQuery({ queryKey: ['facilities', 'order-match', order.id], queryFn: () => facilitiesApi.orderMatch(order.id) })
  const m = q.data
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '22px 0 8px' }}>
        <h3 style={{ margin: 0, fontSize: 13, fontWeight: 700 }}>Supplier invoices</h3>
        {canManage && order.status !== 'DRAFT' && order.status !== 'CANCELLED' && <Btn onClick={() => setRecording(true)}>Record invoice</Btn>}
      </div>
      {q.isError && <div role="alert" style={{ fontSize: 12.5, color: 'var(--hf-danger-text)' }}>The match could not be loaded.</div>}
      {m && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 10 }}>
            {([['Ordered', m.orderedValue], ['Received', m.receivedValue], ['Invoiced', m.invoicedValue]] as [string, number][]).map(([k, v]) => (
              <div key={k} style={{ background: 'var(--hf-surface-sunken)', borderRadius: 8, padding: '8px 10px' }}>
                <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>{k}</div><div style={{ fontWeight: 700, fontSize: 13.5 }}>{fmtMoney(v)}</div>
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 12.5, flexWrap: 'wrap' }}>
            <Chip tone={MATCH_TONE[m.status] ?? 'neutral'}>{m.status.replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase())}</Chip>
            <span style={{ color: 'var(--hf-text-muted)' }}>Invoiced minus received: {fmtMoney(m.variance)} (tolerance {fmtMoney(m.tolerance)})</span>
          </div>
          {m.invoices.map(i => (
            <div key={i.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 8, padding: '8px 0', borderTop: '1px solid var(--hf-border-subtle)', marginTop: 8, fontSize: 13 }}>
              <span>{i.invoiceNumber} <span style={{ color: 'var(--hf-text-muted)' }}>· {fmtDate(i.invoiceDate)}</span></span>
              <span style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}><strong>{fmtMoney(i.total)}</strong><StatusChip status={i.status} /></span>
            </div>
          ))}
          {m.invoices.length === 0 && <div style={{ fontSize: 12.5, color: 'var(--hf-text-muted)', marginTop: 8 }}>No invoices recorded.</div>}
        </>
      )}
      {recording && <InvoiceFormDialog orderId={order.id} onClose={() => setRecording(false)} />}
    </div>
  )
}

/** Record a supplier invoice against an order (chosen here when not given). */
export function InvoiceFormDialog({ orderId, onClose }: { orderId?: string; onClose: () => void }) {
  const qc = useQueryClient()
  const orders = useQuery({ queryKey: ['facilities', 'purchase-orders', ''], queryFn: () => facilitiesApi.purchaseOrders(), enabled: !orderId })
  const [po, setPo] = useState(orderId ?? '')
  const [number, setNumber] = useState('')
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const [subtotal, setSubtotal] = useState('')
  const [vat, setVat] = useState('')
  const sub = Number(subtotal), v = vat.trim() === '' ? null : Number(vat)
  const valid = !!po && !!number.trim() && !!date && sub >= 0.01 && (v == null || (Number.isFinite(v) && v >= 0))
  const save = useMutation({
    mutationFn: () => facilitiesApi.recordVendorInvoice({ purchaseOrderId: po, invoiceNumber: number.trim(), invoiceDate: date, subtotal: sub, vatAmount: v }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success('Invoice recorded'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  const usable = (orders.data ?? []).filter(o => ['APPROVED', 'PARTLY_RECEIVED', 'RECEIVED'].includes(o.status))
  return (
    <Dialog title="Record supplier invoice" onClose={onClose} width={480}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!valid} onClick={() => save.mutate()}>Record</Btn></>}>
      {!orderId && (
        <Field label="Purchase order *" hint="Only orders that have been approved can be invoiced.">
          <select value={po} onChange={e => setPo(e.target.value)} style={inputStyle}>
            <option value="">Select an order…</option>{usable.map(o => <option key={o.id} value={o.id}>{o.poNumber} · {o.vendorName} · {fmtMoney(o.totalValue)}</option>)}
          </select>
        </Field>
      )}
      <Field label="Supplier's invoice number *"><input value={number} onChange={e => setNumber(e.target.value)} maxLength={80} style={inputStyle} autoFocus /></Field>
      <Field label="Invoice date *"><input type="date" value={date} onChange={e => setDate(e.target.value)} style={inputStyle} /></Field>
      <Field label="Subtotal excluding VAT (R) *"><input inputMode="decimal" value={subtotal} onChange={e => setSubtotal(e.target.value)} style={inputStyle} /></Field>
      <Field label="VAT (R)"><input inputMode="decimal" value={vat} onChange={e => setVat(e.target.value)} style={inputStyle} /></Field>
    </Dialog>
  )
}
