// src/pages/facilities/AssetsTab.tsx
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import Chip from '../../components/ui/Chip'
import { facilitiesApi, type Asset } from '../../api/facilities.api'
import { usePermissions } from '../../hooks/usePermission'
import { Btn, DataTable, DefList, Dialog, StatusChip, Toolbar, type Column } from './ui'
import { AssetFormDialog } from './forms'
import { daysUntil, fmtDate, inputStyle, titleCase } from './format'

const CRIT_TONE = { LOW: 'neutral', MEDIUM: 'info', HIGH: 'warn', CRITICAL: 'bad' } as const

export default function AssetsTab() {
  const [search, setSearch] = useState('')
  const [siteId, setSiteId] = useState('')
  const [open, setOpen] = useState<Asset | null>(null)
  const [form, setForm] = useState<Asset | 'new' | null>(null)
  const canManage = usePermissions(['FACILITIES_MANAGE', 'FACILITIES_ADMIN'])
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const q = useQuery({ queryKey: ['facilities', 'assets', siteId], queryFn: () => facilitiesApi.assets(siteId || undefined) })
  const siteName = useMemo(() => new Map((sites.data ?? []).map(s => [s.id, s.name])), [sites.data])
  const rows = (q.data ?? []).filter(a => !search.trim() || `${a.assetTag} ${a.name} ${a.assetType} ${a.location ?? ''}`.toLowerCase().includes(search.trim().toLowerCase()))
  const warranty = (a: Asset) => { const d = daysUntil(a.warrantyExpiryDate); return d == null ? '—' : d < 0 ? <Chip tone="neutral">Expired {fmtDate(a.warrantyExpiryDate)}</Chip> : <span>{fmtDate(a.warrantyExpiryDate)}</span> }
  const cols: Column<Asset>[] = [
    { key: 'tag', header: 'Tag', render: a => <strong style={{ whiteSpace: 'nowrap' }}>{a.assetTag}</strong> },
    { key: 'name', header: 'Asset', render: a => <div><div>{a.name}</div><div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{[a.manufacturer, a.model].filter(Boolean).join(' ')}</div></div> },
    { key: 'type', header: 'Type', render: a => titleCase(a.assetType) },
    { key: 'site', header: 'Site', render: a => siteName.get(a.siteId) ?? '—' },
    { key: 'crit', header: 'Criticality', render: a => a.criticality ? <Chip tone={CRIT_TONE[a.criticality as keyof typeof CRIT_TONE] ?? 'neutral'}>{titleCase(a.criticality)}</Chip> : '—' },
    { key: 'war', header: 'Warranty until', render: warranty },
    { key: 'st', header: 'Status', render: a => <StatusChip status={a.status} /> },
  ]
  return (
    <div>
      <Toolbar search={search} onSearch={setSearch} placeholder="Search tag, name, type or location">
        <select aria-label="Filter by site" value={siteId} onChange={e => setSiteId(e.target.value)} style={{ ...inputStyle, width: 'auto' }}>
          <option value="">All sites</option>
          {(sites.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        {canManage && <Btn kind="primary" onClick={() => setForm('new')}><Plus size={15} />New asset</Btn>}
      </Toolbar>
      <DataTable rows={rows} columns={cols} rowKey={a => a.id} loading={q.isLoading} error={q.error} onRowClick={setOpen} empty="No assets found." />
      {open && (
        <Dialog title={`${open.assetTag} · ${open.name}`} onClose={() => setOpen(null)} side width={460}
          footer={canManage ? <Btn onClick={() => { setForm(open); setOpen(null) }}><Pencil size={13} />Edit</Btn> : undefined}>
          <DefList items={[
            ['Site', siteName.get(open.siteId) ?? '—'], ['Type', titleCase(open.assetType)], ['Location', open.location ?? '—'],
            ['Manufacturer', open.manufacturer ?? '—'], ['Model', open.model ?? '—'], ['Serial number', open.serialNumber ?? '—'],
            ['Installed', fmtDate(open.installDate)], ['Warranty until', fmtDate(open.warrantyExpiryDate)], ['Criticality', titleCase(open.criticality)],
            ['Status', <StatusChip key="s" status={open.status} />], ['Notes', open.notes ?? '—'],
          ]} />
        </Dialog>
      )}
      {form && <AssetFormDialog asset={form === 'new' ? undefined : form} defaultSiteId={siteId || undefined} onClose={() => setForm(null)} />}
    </div>
  )
}
