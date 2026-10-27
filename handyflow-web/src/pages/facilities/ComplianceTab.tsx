// src/pages/facilities/ComplianceTab.tsx
//
// Compliance certificates, soonest expiry first. The status shown is worked out here from the expiry date for
// valid certificates, so a certificate that lapsed overnight shows as expired even before the server's daily job runs.
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { facilitiesApi, type Certificate } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { DataTable, StatusChip, Toolbar, type Column } from './ui'
import { daysUntil, fmtDate, titleCase } from './format'

const FILTERS = [{ id: 'all', label: 'All' }, { id: 'attention', label: 'Needs attention' }, { id: 'valid', label: 'Valid' }]

function effective(c: Certificate): { status: string; days: number | null } {
  const days = daysUntil(c.expiryDate)
  if (c.status === 'VALID' && days != null && days < 0) return { status: 'EXPIRED', days }
  return { status: c.status, days }
}

export default function ComplianceTab() {
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const q = useQuery({ queryKey: ['facilities', 'certificates'], queryFn: facilitiesApi.certificates })
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const siteName = useMemo(() => new Map((sites.data ?? []).map(s => [s.id, s.name])), [sites.data])

  const rows = (q.data ?? []).filter(c => {
    const e = effective(c)
    const text = `${c.certificateType} ${c.certificateNumber ?? ''} ${c.issuedBy ?? ''} ${siteName.get(c.siteId ?? '') ?? ''}`.toLowerCase()
    if (search.trim() && !text.includes(search.trim().toLowerCase())) return false
    if (filter === 'attention') return e.status === 'EXPIRED' || (e.status === 'VALID' && e.days != null && e.days <= 30)
    if (filter === 'valid') return e.status === 'VALID'
    return true
  })
  const cols: Column<Certificate>[] = [
    { key: 'type', header: 'Certificate', render: c => <div><strong>{titleCase(c.certificateType)}</strong><div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{c.certificateNumber ?? ''}</div></div> },
    { key: 'site', header: 'Site', render: c => siteName.get(c.siteId ?? '') ?? '—' },
    { key: 'by', header: 'Issued by', render: c => c.issuedBy ?? '—' },
    { key: 'exp', header: 'Expires', render: c => fmtDate(c.expiryDate) },
    { key: 'left', header: 'Time left', render: c => {
      const e = effective(c)
      if (c.status !== 'VALID' || e.days == null) return '—'
      return e.days < 0 ? <Chip tone="bad">Expired {-e.days} d ago</Chip> : e.days <= 30 ? <Chip tone="warn">{e.days} d left</Chip> : <span>{e.days} d</span>
    } },
    { key: 'st', header: 'Status', render: c => <StatusChip status={effective(c).status} /> },
  ]
  return (
    <div>
      <Toolbar search={search} onSearch={setSearch} placeholder="Search certificates">
        <div role="group" aria-label="Filter" style={{ display: 'flex', gap: 6 }}>
          {FILTERS.map(f => (
            <button key={f.id} type="button" aria-pressed={filter === f.id} onClick={() => setFilter(f.id)}
              style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12.5, fontWeight: 600, cursor: 'pointer', border: `1px solid ${filter === f.id ? 'var(--hf-accent)' : 'var(--hf-border)'}`,
                background: filter === f.id ? 'var(--hf-accent-soft)' : 'var(--hf-surface)', color: filter === f.id ? 'var(--hf-accent-text-strong)' : 'var(--hf-text-secondary)' }}>{f.label}</button>
          ))}
        </div>
      </Toolbar>
      <DataTable rows={rows} columns={cols} rowKey={c => c.id} loading={q.isLoading} error={q.error} empty="No certificates match."
        rowTone={c => (effective(c).status === 'EXPIRED' ? 'bad' : undefined)} />
    </div>
  )
}
