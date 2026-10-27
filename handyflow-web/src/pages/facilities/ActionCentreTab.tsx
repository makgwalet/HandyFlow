// src/pages/facilities/ActionCentreTab.tsx
//
// One ranked list of everything that needs attention (GET /action-centre): lapsed or expiring certificates,
// overdue or critical corrective actions, and vendor documents. Server ranks by severity.
import { useQuery } from '@tanstack/react-query'
import Chip from '../../components/ui/Chip'
import { facilitiesApi, type ActionItem } from '../../api/facilities.api'
import { DataTable, KpiTile, type Column } from './ui'
import { SEVERITY_TONE, fmtDate, kpiGrid, titleCase, daysUntil } from './format'
import { AlertOctagon, AlertTriangle, Info } from 'lucide-react'

const SECTION_FOR: Record<string, string> = { CERTIFICATE: 'compliance', VENDOR: 'vendors' }

export default function ActionCentreTab({ onNavigate }: { onNavigate: (section: string) => void }) {
  const q = useQuery({ queryKey: ['facilities', 'action-centre'], queryFn: facilitiesApi.actionCentre })
  const cols: Column<ActionItem>[] = [
    { key: 'sev', header: 'Severity', width: 110, render: i => <Chip tone={SEVERITY_TONE[i.severity]}>{titleCase(i.severity)}</Chip> },
    { key: 'what', header: 'What', render: i => <div><div style={{ fontWeight: 600 }}>{i.title}</div><div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{i.detail}</div></div> },
    { key: 'type', header: 'Type', render: i => titleCase(i.type) },
    { key: 'due', header: 'Due', render: i => { const d = daysUntil(i.dueDate); return <span style={{ color: d != null && d < 0 ? 'var(--hf-danger-text)' : undefined }}>{fmtDate(i.dueDate)}</span> } },
  ]
  return (
    <div>
      <div style={kpiGrid}>
        <KpiTile label="Critical" value={q.data?.critical ?? '—'} icon={<AlertOctagon size={19} />} tone={q.data?.critical ? 'bad' : 'neutral'} />
        <KpiTile label="High" value={q.data?.high ?? '—'} icon={<AlertTriangle size={19} />} tone={q.data?.high ? 'warn' : 'neutral'} />
        <KpiTile label="Medium" value={q.data?.medium ?? '—'} icon={<Info size={19} />} tone="info" />
      </div>
      <DataTable rows={q.data?.items ?? []} columns={cols} rowKey={i => `${i.entityType}-${i.entityId}-${i.type}`}
        loading={q.isLoading} error={q.error} empty="All clear. Nothing needs attention right now."
        onRowClick={i => { const s = SECTION_FOR[i.entityType]; if (s) onNavigate(s) }}
        rowTone={i => (i.severity === 'CRITICAL' ? 'bad' : undefined)} />
    </div>
  )
}
