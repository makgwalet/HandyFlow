// src/pages/facilities/VendorInvoicesTab.tsx
//
// Supplier invoices checked against purchase orders (three-way match: ordered, received, invoiced). An invoice that
// goes beyond what was received needs a note explaining why before it can be approved; otherwise dispute it.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { facilitiesApi, type VendorInvoice } from '../../api/facilities.api'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, Dialog, Field, StatusChip, Toolbar, type Column } from './ui'
import { ReasonDialog } from './ComplianceForms'
import { InvoiceFormDialog } from './InvoiceMatch'
import { fmtDate, fmtMoney, inputStyle } from './format'

const FILTERS = [{ id: '', label: 'All' }, { id: 'RECORDED', label: 'To review' }, { id: 'APPROVED', label: 'Approved' }, { id: 'DISPUTED', label: 'Disputed' }]
const CAN_MANAGE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN']

export default function VendorInvoicesTab() {
  const [status, setStatus] = useState('')
  const [recording, setRecording] = useState(false)
  const [approving, setApproving] = useState<VendorInvoice | null>(null)
  const [disputing, setDisputing] = useState<VendorInvoice | null>(null)
  const canManage = usePermissions(CAN_MANAGE)
  const q = useQuery({ queryKey: ['facilities', 'vendor-invoices', status], queryFn: () => facilitiesApi.vendorInvoices(status || undefined) })
  const cols: Column<VendorInvoice>[] = [
    { key: 'no', header: 'Invoice', render: i => <div><strong>{i.invoiceNumber}</strong><div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{fmtDate(i.invoiceDate)}</div></div> },
    { key: 'v', header: 'Vendor', render: i => i.vendorName ?? '—' },
    { key: 'po', header: 'Order', render: i => i.poNumber },
    { key: 'sub', header: 'Subtotal', align: 'right', render: i => fmtMoney(i.subtotal) },
    { key: 'vat', header: 'VAT', align: 'right', render: i => fmtMoney(i.vatAmount) },
    { key: 'tot', header: 'Total', align: 'right', render: i => <strong>{fmtMoney(i.total)}</strong> },
    { key: 'st', header: 'Status', render: i => <div><StatusChip status={i.status} />{i.decisionNote && <div style={{ fontSize: 11.5, color: 'var(--hf-text-muted)', marginTop: 3 }}>{i.decisionNote}</div>}</div> },
    ...(canManage ? [{
      key: 'do', header: '', align: 'right' as const, render: (i: VendorInvoice) => i.status === 'RECORDED' ? (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          <Btn kind="primary" onClick={() => setApproving(i)}>Approve</Btn>
          <Btn onClick={() => setDisputing(i)}>Dispute</Btn>
        </span>
      ) : null,
    }] : []),
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
        {canManage && <Btn kind="primary" onClick={() => setRecording(true)}><Plus size={15} />Record invoice</Btn>}
      </Toolbar>
      <DataTable rows={q.data ?? []} columns={cols} rowKey={i => i.id} loading={q.isLoading} error={q.error} empty="No vendor invoices." />
      {recording && <InvoiceFormDialog onClose={() => setRecording(false)} />}
      {approving && <ApproveDialog invoice={approving} onClose={() => setApproving(null)} />}
      {disputing && (
        <ReasonDialog title={`Dispute ${disputing.invoiceNumber}`} confirm="Dispute" onClose={() => setDisputing(null)}
          intro="Say what is wrong, e.g. the price differs from the order or goods were not delivered. A disputed invoice is not paid."
          onConfirm={reason => facilitiesApi.disputeVendorInvoice(disputing.id, reason)} />
      )}
    </div>
  )
}

function ApproveDialog({ invoice, onClose }: { invoice: VendorInvoice; onClose: () => void }) {
  const qc = useQueryClient()
  const [note, setNote] = useState('')
  const run = useMutation({
    mutationFn: () => facilitiesApi.approveVendorInvoice(invoice.id, note.trim() || undefined),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${invoice.invoiceNumber} approved`); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={`Approve ${invoice.invoiceNumber}`} onClose={onClose} width={460}
      footer={<><Btn onClick={onClose}>Back</Btn><Btn kind="primary" busy={run.isPending} onClick={() => run.mutate()}>Approve</Btn></>}>
      <p style={{ margin: '0 0 14px', fontSize: 13.5, color: 'var(--hf-text-secondary)' }}>
        Approve {fmtMoney(invoice.total)} from {invoice.vendorName} against {invoice.poNumber}? If the order has been invoiced for more than was received (or nothing was received), you must say why in the note.
      </p>
      <Field label="Note" hint="Required when the invoiced total is more than what was received."><input value={note} onChange={e => setNote(e.target.value)} maxLength={500} style={inputStyle} /></Field>
    </Dialog>
  )
}
