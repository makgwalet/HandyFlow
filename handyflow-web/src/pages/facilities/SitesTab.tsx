// src/pages/facilities/SitesTab.tsx
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { facilitiesApi, type Site } from '../../api/facilities.api'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, StatusChip, Toolbar, type Column } from './ui'
import { SiteFormDialog } from './forms'
import { titleCase } from './format'

/** Address is a free-form map (street, city, …); show it as one line, in a stable order. */
const addressLine = (a: Site['address']) =>
  a ? ['street', 'suburb', 'city', 'province', 'postalCode'].map(k => a[k]).filter(Boolean).join(', ') || Object.values(a).filter(Boolean).join(', ') : ''

export default function SitesTab() {
  const [search, setSearch] = useState('')
  const [form, setForm] = useState<Site | 'new' | null>(null)
  const qc = useQueryClient()
  const canManage = usePermissions(['FACILITIES_MANAGE', 'FACILITIES_ADMIN'])
  const q = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites })
  const assets = useQuery({ queryKey: ['facilities', 'assets'], queryFn: () => facilitiesApi.assets(), staleTime: 5 * 60_000 })
  const toggle = useMutation({
    mutationFn: (s: Site) => facilitiesApi.siteAction(s.id, s.status === 'CLOSED' ? 'reopen' : 'close'),
    onSuccess: s => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${s.name} is now ${titleCase(s.status).toLowerCase()}`) },
    onError: e => toast.error(errorMessage(e)),
  })
  const assetCount = useMemo(() => { const m = new Map<string, number>(); (assets.data ?? []).forEach(a => m.set(a.siteId, (m.get(a.siteId) ?? 0) + 1)); return m }, [assets.data])
  const rows = (q.data ?? []).filter(s => !search.trim() || `${s.name} ${addressLine(s.address)}`.toLowerCase().includes(search.trim().toLowerCase()))
  const cols: Column<Site>[] = [
    { key: 'name', header: 'Site', render: s => <strong>{s.name}</strong> },
    { key: 'type', header: 'Type', render: s => titleCase(s.siteType) },
    { key: 'addr', header: 'Address', render: s => addressLine(s.address) || '—' },
    { key: 'assets', header: 'Assets', align: 'right', render: s => assetCount.get(s.id) ?? 0 },
    { key: 'st', header: 'Status', render: s => <StatusChip status={s.status} /> },
    ...(canManage ? [{
      key: 'do', header: '', align: 'right' as const, render: (s: Site) => (
        <span style={{ display: 'inline-flex', gap: 6 }} onClick={e => e.stopPropagation()}>
          <Btn onClick={() => setForm(s)}><Pencil size={13} />Edit</Btn>
          <Btn busy={toggle.isPending && toggle.variables?.id === s.id} onClick={() => toggle.mutate(s)}>{s.status === 'CLOSED' ? 'Reopen' : 'Close'}</Btn>
        </span>
      ),
    }] : []),
  ]
  return (
    <div>
      <Toolbar search={search} onSearch={setSearch} placeholder="Search sites">
        {canManage && <Btn kind="primary" onClick={() => setForm('new')}><Plus size={15} />New site</Btn>}
      </Toolbar>
      <DataTable rows={rows} columns={cols} rowKey={s => s.id} loading={q.isLoading} error={q.error} empty="No sites yet." />
      {form && <SiteFormDialog site={form === 'new' ? undefined : form} onClose={() => setForm(null)} />}
    </div>
  )
}
