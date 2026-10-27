// src/pages/facilities/SitesTab.tsx
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { facilitiesApi, type Site } from '../../api/facilities.api'
import { DataTable, StatusChip, Toolbar, type Column } from './ui'
import { titleCase } from './format'

/** Address is a free-form map (street, city, …); show it as one line, in a stable order. */
const addressLine = (a: Site['address']) =>
  a ? ['street', 'suburb', 'city', 'province', 'postalCode'].map(k => a[k]).filter(Boolean).join(', ') || Object.values(a).filter(Boolean).join(', ') : ''

export default function SitesTab() {
  const [search, setSearch] = useState('')
  const q = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites })
  const assets = useQuery({ queryKey: ['facilities', 'assets'], queryFn: () => facilitiesApi.assets(), staleTime: 5 * 60_000 })
  const assetCount = useMemo(() => { const m = new Map<string, number>(); (assets.data ?? []).forEach(a => m.set(a.siteId, (m.get(a.siteId) ?? 0) + 1)); return m }, [assets.data])
  const rows = (q.data ?? []).filter(s => !search.trim() || `${s.name} ${addressLine(s.address)}`.toLowerCase().includes(search.trim().toLowerCase()))
  const cols: Column<Site>[] = [
    { key: 'name', header: 'Site', render: s => <strong>{s.name}</strong> },
    { key: 'type', header: 'Type', render: s => titleCase(s.siteType) },
    { key: 'addr', header: 'Address', render: s => addressLine(s.address) || '—' },
    { key: 'assets', header: 'Assets', align: 'right', render: s => assetCount.get(s.id) ?? 0 },
    { key: 'st', header: 'Status', render: s => <StatusChip status={s.status} /> },
  ]
  return (
    <div>
      <Toolbar search={search} onSearch={setSearch} placeholder="Search sites" />
      <DataTable rows={rows} columns={cols} rowKey={s => s.id} loading={q.isLoading} error={q.error} empty="No sites yet." />
    </div>
  )
}
