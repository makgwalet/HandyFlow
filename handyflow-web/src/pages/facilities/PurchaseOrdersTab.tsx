// src/pages/facilities/PurchaseOrdersTab.tsx
//
// Purchase orders for stock. Approval needs someone other than the creator and a vendor who is eligible right now; a
// delivery is all-or-nothing and cannot exceed what was ordered. The server enforces all of that; the dialogs
// show its message when it refuses.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, ShieldCheck } from 'lucide-react'
import { facilitiesApi, type PurchaseOrder } from '../../api/facilities.api'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, DefList, Dialog, Field, StatusChip, Toolbar, type Column } from './ui'
import { PurchaseOrderFormDialog } from './forms'
import { GoodsReceipts, InvoiceMatch } from './InvoiceMatch'
import ApprovalLimitsDialog from './ApprovalLimitsDialog'
import { fmtDate, fmtMoney, fmtQty, inputStyle } from './format'

const FILTERS = [{ id: '', label: 'All' }, { id: 'DRAFT', label: 'Draft' }, { id: 'APPROVED', label: 'Approved' }, { id: 'PARTLY_RECEIVED', label: 'Part received' }, { id: 'RECEIVED', label: 'Received' }, { id: 'CANCELLED', label: 'Cancelled' }]

export default function PurchaseOrdersTab() {
  const [status, setStatus] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [limits, setLimits] = useState(false)
  const canManage = usePermissions(['FACILITIES_MANAGE', 'FACILITIES_ADMIN'])
  const isAdmin = usePermissions(['FACILITIES_ADMIN'])
  const q = useQuery({ queryKey: ['facilities', 'purchase-orders', status], queryFn: () => facilitiesApi.purchaseOrders(status || undefined) })
  const cols: Column<PurchaseOrder>[] = [
    { key: 'no', header: 'Number', render: p => <strong>{p.poNumber}</strong> },
    { key: 'v', header: 'Vendor', render: p => p.vendorName ?? '—' },
    { key: 'lines', header: 'Lines', align: 'right', render: p => p.lines.length },
    { key: 'tot', header: 'Total', align: 'right', render: p => fmtMoney(p.totalValue) },
    { key: 'exp', header: 'Expected', render: p => fmtDate(p.expectedDate) },
    { key: 'st', header: 'Status', render: p => <StatusChip status={p.status} /> },
  ]
  return (
    <div>
      <Toolbar>
        <div role="tablist" aria-label="Filter by status" style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {FILTERS.map(f => (
            <button key={f.id} type="button" role="tab" aria-selected={status === f.id} onClick={() => setStatus(f.id)}
              style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12.5, fontWeight: 600, cursor: 'pointer', border: `1px solid ${status === f.id ? 'var(--hf-accent)' : 'var(--hf-border)'}`,
                background: status === f.id ? 'var(--hf-accent-soft)' : 'var(--hf-surface)', color: status === f.id ? 'var(--hf-accent-text-strong)' : 'var(--hf-text-secondary)' }}>{f.label}</button>
          ))}
        </div>
        {isAdmin && <Btn onClick={() => setLimits(true)}><ShieldCheck size={15} />Approval limits</Btn>}
        {canManage && <Btn kind="primary" onClick={() => setCreating(true)}><Plus size={15} />New order</Btn>}
      </Toolbar>
      <DataTable rows={q.data ?? []} columns={cols} rowKey={p => p.id} loading={q.isLoading} error={q.error} onRowClick={p => setOpen(p.id)} empty="No purchase orders." />
      {limits && <ApprovalLimitsDialog onClose={() => setLimits(false)} />}
      {creating && <PurchaseOrderFormDialog onClose={() => setCreating(false)} />}
      {open && q.data?.find(p => p.id === open) && <OrderPanel order={q.data.find(p => p.id === open)!} onClose={() => setOpen(null)} />}
    </div>
  )
}

type Act = 'approve' | 'receive' | 'cancel' | 'close-short'

function OrderPanel({ order, onClose }: { order: PurchaseOrder; onClose: () => void }) {
  const [act, setAct] = useState<Act | null>(null)
  const canManage = usePermissions(['FACILITIES_MANAGE', 'FACILITIES_ADMIN'])
  const isOpen = order.status === 'APPROVED' || order.status === 'PARTLY_RECEIVED'
  return (
    <Dialog title={order.poNumber} onClose={onClose} side width={560}
      footer={canManage ? (
        <>
          {order.status === 'DRAFT' && <><Btn kind="danger" onClick={() => setAct('cancel')}>Cancel order</Btn><Btn kind="primary" onClick={() => setAct('approve')}>Approve</Btn></>}
          {order.status === 'APPROVED' && <Btn kind="danger" onClick={() => setAct('cancel')}>Cancel order</Btn>}
          {order.status === 'PARTLY_RECEIVED' && <Btn onClick={() => setAct('close-short')}>Close short</Btn>}
          {isOpen && <Btn kind="primary" onClick={() => setAct('receive')}>Receive goods</Btn>}
        </>
      ) : undefined}>
      <DefList items={[
        ['Vendor', order.vendorName ?? '—'], ['Status', <StatusChip key="s" status={order.status} />], ['Expected', fmtDate(order.expectedDate)],
        ['Total', fmtMoney(order.totalValue)], ['Approved', order.approvedAt ? fmtDate(order.approvedAt) : '—'],
        ...(order.closedReason ? [['Closed because', order.closedReason] as [string, string]] : []), ['Notes', order.notes ?? '—'],
      ]} />
      <h3 style={{ margin: '22px 0 8px', fontSize: 13, fontWeight: 700 }}>Lines</h3>
      <table style={{ width: '100%', fontSize: 12.5, borderCollapse: 'collapse' }}>
        <thead><tr style={{ color: 'var(--hf-text-muted)', textAlign: 'right' }}><th scope="col" style={{ textAlign: 'left', fontWeight: 600, paddingBottom: 6 }}>Item</th><th scope="col" style={{ fontWeight: 600 }}>Ordered</th><th scope="col" style={{ fontWeight: 600 }}>Received</th><th scope="col" style={{ fontWeight: 600 }}>Unit</th></tr></thead>
        <tbody>
          {order.lines.map(l => (
            <tr key={l.id} style={{ borderTop: '1px solid var(--hf-border-subtle)', textAlign: 'right' }}>
              <td style={{ padding: '7px 0', textAlign: 'left' }}>{l.description}</td><td>{fmtQty(l.orderedQty)}</td><td>{fmtQty(l.receivedQty)}</td><td>{fmtMoney(l.unitCost)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {order.status !== 'DRAFT' && order.status !== 'CANCELLED' && <GoodsReceipts orderId={order.id} />}
      {order.status !== 'DRAFT' && order.status !== 'CANCELLED' && <InvoiceMatch order={order} canManage={canManage} />}
      {act && <OrderAction act={act} order={order} onClose={() => setAct(null)} onDone={onClose} />}
    </Dialog>
  )
}

function OrderAction({ act, order, onClose, onDone }: { act: Act; order: PurchaseOrder; onClose: () => void; onDone: () => void }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState('')
  const [reference, setReference] = useState('')
  const [qty, setQty] = useState<Record<string, string>>({})
  const [storeId, setStoreId] = useState('')
  const stores = useQuery({ queryKey: ['facilities', 'stores'], queryFn: facilitiesApi.stores, enabled: act === 'receive' })
  const activeStores = (stores.data ?? []).filter(s => s.active)
  const needsReason = act === 'cancel' || act === 'close-short'
  const lines = Object.entries(qty).filter(([, v]) => Number(v) > 0).map(([lineId, v]) => ({ lineId, quantity: Number(v) }))
  const run = useMutation({
    mutationFn: () => act === 'receive' ? facilitiesApi.receiveOrderAt(order.id, { reference: reference || undefined, lines }, storeId)
      : facilitiesApi.purchaseOrderAction(order.id, act, needsReason ? { reason } : {}),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${order.poNumber} updated`); onClose(); onDone() },
    onError: e => toast.error(errorMessage(e)),
  })
  const title = { approve: 'Approve purchase order', receive: 'Receive goods', cancel: 'Cancel purchase order', 'close-short': 'Close short' }[act]
  const ok = act === 'receive' ? lines.length > 0 : needsReason ? reason.trim().length > 0 : true
  return (
    <Dialog title={title} onClose={onClose} width={480}
      footer={<><Btn onClick={onClose}>Back</Btn><Btn kind={act === 'cancel' ? 'danger' : 'primary'} busy={run.isPending} disabled={!ok} onClick={() => run.mutate()}>{title}</Btn></>}>
      {act === 'approve' && <p style={{ margin: 0, fontSize: 14, color: 'var(--hf-text-secondary)' }}>Approve {order.poNumber} for {fmtMoney(order.totalValue)} to {order.vendorName}? Someone other than the person who raised it must approve, and the vendor's documents must be in force.</p>}
      {needsReason && <Field label={act === 'cancel' ? 'Why is the order being cancelled? *' : 'Why is the rest not coming? *'}><textarea value={reason} onChange={e => setReason(e.target.value)} rows={3} maxLength={500} style={{ ...inputStyle, resize: 'vertical' }} autoFocus /></Field>}
      {act === 'receive' && (
        <>
          {activeStores.length > 1 && (
            <Field label="Receive into" hint="Leave blank for the default store.">
              <select value={storeId} onChange={e => setStoreId(e.target.value)} style={inputStyle}>
                <option value="">Default store</option>{activeStores.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
          )}
          <Field label="Delivery note / invoice reference"><input value={reference} onChange={e => setReference(e.target.value)} maxLength={200} style={inputStyle} /></Field>
          {order.lines.filter(l => l.outstandingQty > 0).map(l => (
            <Field key={l.id} label={l.description} hint={`${fmtQty(l.outstandingQty)} outstanding`}>
              <input type="number" min="0" max={l.outstandingQty} step="0.001" value={qty[l.id] ?? ''} onChange={e => setQty(s => ({ ...s, [l.id]: e.target.value }))} style={inputStyle} placeholder="Quantity delivered" />
            </Field>
          ))}
        </>
      )}
    </Dialog>
  )
}
