// src/pages/facilities/VendorsTab.tsx
//
// Vendors and their onboarding status. A vendor must be approved, with its required documents in force, before it can
// be given work or ordered from; approval needs someone other than the person who added it (the server enforces both).
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Pencil, Plus } from 'lucide-react'
import { facilitiesApi, type Vendor } from '../../api/facilities.api'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, Dialog, Field, StatusChip, Toolbar, type Column } from './ui'
import { VendorFormDialog } from './forms'
import { inputStyle } from './format'

export default function VendorsTab() {
  const [search, setSearch] = useState('')
  const [form, setForm] = useState<Vendor | 'new' | null>(null)
  const [suspending, setSuspending] = useState<Vendor | null>(null)
  const qc = useQueryClient()
  const canManage = usePermissions(['FACILITIES_MANAGE', 'FACILITIES_ADMIN'])
  const q = useQuery({ queryKey: ['facilities', 'vendors'], queryFn: facilitiesApi.vendors })
  const approve = useMutation({
    mutationFn: (id: string) => facilitiesApi.approveVendor(id),
    onSuccess: v => { qc.invalidateQueries({ queryKey: ['facilities', 'vendors'] }); toast.success(`${v.companyName} approved`) },
    onError: e => toast.error(errorMessage(e)),
  })
  const rows = (q.data ?? []).filter(v => !search.trim() || `${v.companyName} ${v.serviceType ?? ''} ${v.contactName ?? ''}`.toLowerCase().includes(search.trim().toLowerCase()))
  const cols: Column<Vendor>[] = [
    { key: 'name', header: 'Vendor', render: v => <strong>{v.companyName}</strong> },
    { key: 'svc', header: 'Service', render: v => v.serviceType ?? '—' },
    { key: 'contact', header: 'Contact', render: v => <div>{v.contactName ?? '—'}<div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{[v.contactPhone, v.contactEmail].filter(Boolean).join(' · ')}</div></div> },
    { key: 'ob', header: 'Onboarding', render: v => <StatusChip status={v.onboardingStatus} /> },
    { key: 'act', header: 'Active', render: v => (v.active ? 'Yes' : 'No') },
    ...(canManage ? [{
      key: 'do', header: '', align: 'right' as const, render: (v: Vendor) => (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          {v.onboardingStatus === 'PENDING' && <Btn kind="primary" busy={approve.isPending && approve.variables === v.id} onClick={() => approve.mutate(v.id)}><Check size={14} />Approve</Btn>}
          {v.onboardingStatus === 'APPROVED' && <Btn onClick={() => setSuspending(v)}>Suspend</Btn>}
          <Btn onClick={() => setForm(v)}><Pencil size={13} />Edit</Btn>
        </span>
      ),
    }] : []),
  ]
  return (
    <div>
      <Toolbar search={search} onSearch={setSearch} placeholder="Search vendors">
        {canManage && <Btn kind="primary" onClick={() => setForm('new')}><Plus size={15} />New vendor</Btn>}
      </Toolbar>
      <DataTable rows={rows} columns={cols} rowKey={v => v.id} loading={q.isLoading} error={q.error} empty="No vendors yet." />
      {form && <VendorFormDialog vendor={form === 'new' ? undefined : form} onClose={() => setForm(null)} />}
      {suspending && <SuspendDialog vendor={suspending} onClose={() => setSuspending(null)} />}
    </div>
  )
}

function SuspendDialog({ vendor, onClose }: { vendor: Vendor; onClose: () => void }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState('')
  const run = useMutation({
    mutationFn: () => facilitiesApi.vendorAction(vendor.id, 'suspend', { reason: reason.trim() }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${vendor.companyName} suspended`); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={`Suspend ${vendor.companyName}`} onClose={onClose} width={460}
      footer={<><Btn onClick={onClose}>Back</Btn><Btn kind="danger" busy={run.isPending} disabled={!reason.trim()} onClick={() => run.mutate()}>Suspend</Btn></>}>
      <p style={{ margin: '0 0 14px', fontSize: 13.5, color: 'var(--hf-text-secondary)' }}>A suspended vendor cannot be given new work or ordered from until they are approved again.</p>
      <Field label="Reason *"><textarea value={reason} onChange={e => setReason(e.target.value)} rows={3} maxLength={500} style={{ ...inputStyle, resize: 'vertical' }} autoFocus /></Field>
    </Dialog>
  )
}
