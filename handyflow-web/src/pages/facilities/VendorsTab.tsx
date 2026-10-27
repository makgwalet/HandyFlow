// src/pages/facilities/VendorsTab.tsx
//
// Vendors and their onboarding status. A vendor must be approved, with its required documents in force, before it can
// be given work or ordered from; approval needs someone other than the person who added it (the server enforces both).
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check } from 'lucide-react'
import { facilitiesApi, type Vendor } from '../../api/facilities.api'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, StatusChip, Toolbar, type Column } from './ui'

export default function VendorsTab() {
  const [search, setSearch] = useState('')
  const qc = useQueryClient()
  const canApprove = usePermissions(['FACILITIES_MANAGE', 'FACILITIES_ADMIN'])
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
    { key: 'do', header: '', align: 'right', render: v => v.onboardingStatus === 'PENDING' && canApprove
      ? <Btn kind="primary" busy={approve.isPending && approve.variables === v.id} onClick={() => approve.mutate(v.id)}><Check size={14} />Approve</Btn> : null },
  ]
  return (
    <div>
      <Toolbar search={search} onSearch={setSearch} placeholder="Search vendors" />
      <DataTable rows={rows} columns={cols} rowKey={v => v.id} loading={q.isLoading} error={q.error} empty="No vendors yet." />
    </div>
  )
}
