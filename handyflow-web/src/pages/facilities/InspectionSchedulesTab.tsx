// src/pages/facilities/InspectionSchedulesTab.tsx
//
// Recurring inspections: a checklist done at a site (or on one asset) every N days. The next due date moves on when an
// inspection from that checklist is completed there; reminders go out ahead of the date and when it is overdue.
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { facilitiesApi, type InspectionSchedule } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, Dialog, Field, Toolbar, type Column } from './ui'
import { fmtDate, inputStyle } from './format'

const CAN_MANAGE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_COMPLIANCE_MANAGE']
const every = (d: number) => (d === 1 ? 'Daily' : d === 7 ? 'Weekly' : d === 14 ? 'Fortnightly' : d === 30 ? 'Monthly' : d === 90 ? 'Quarterly' : d === 365 ? 'Yearly' : `Every ${d} days`)

export default function InspectionSchedulesTab() {
  const [siteId, setSiteId] = useState('')
  const [form, setForm] = useState<InspectionSchedule | 'new' | null>(null)
  const [removing, setRemoving] = useState<InspectionSchedule | null>(null)
  const qc = useQueryClient()
  const canManage = usePermissions(CAN_MANAGE)
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const q = useQuery({ queryKey: ['facilities', 'inspection-schedules', siteId], queryFn: () => facilitiesApi.inspectionSchedules(siteId || undefined) })
  const toggle = useMutation({
    mutationFn: (s: InspectionSchedule) => facilitiesApi.inspectionScheduleActive(s.id, s.active ? 'deactivate' : 'reactivate'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['facilities'] }),
    onError: e => toast.error(errorMessage(e)),
  })
  const del = useMutation({
    mutationFn: (s: InspectionSchedule) => facilitiesApi.deleteInspectionSchedule(s.id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success('Schedule removed'); setRemoving(null) },
    onError: e => toast.error(errorMessage(e)),
  })
  const cols: Column<InspectionSchedule>[] = [
    { key: 't', header: 'Checklist', render: s => <strong>{s.templateName}</strong> },
    { key: 'w', header: 'Where', render: s => <div>{s.siteName}<div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{s.assetName ?? 'Whole site'}</div></div> },
    { key: 'f', header: 'Frequency', render: s => every(s.frequencyDays) },
    { key: 'n', header: 'Next due', render: s => {
      if (!s.active) return '—'
      return <span style={{ whiteSpace: 'nowrap', color: s.overdue ? 'var(--hf-danger-text)' : undefined, fontWeight: s.overdue ? 700 : undefined }}>
        {fmtDate(s.nextDueDate)}{s.overdue ? ` (${-s.daysUntilDue} d overdue)` : s.daysUntilDue <= 7 ? ` (in ${s.daysUntilDue} d)` : ''}</span>
    } },
    { key: 'l', header: 'Last done', render: s => fmtDate(s.lastCompletedDate) },
    { key: 'a', header: 'Status', render: s => <Chip tone={s.active ? 'ok' : 'neutral'}>{s.active ? 'Active' : 'Paused'}</Chip> },
    ...(canManage ? [{
      key: 'do', header: '', align: 'right' as const, render: (s: InspectionSchedule) => (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          <Btn onClick={() => setForm(s)}><Pencil size={13} />Edit</Btn>
          <Btn busy={toggle.isPending && toggle.variables?.id === s.id} onClick={() => toggle.mutate(s)}>{s.active ? 'Pause' : 'Resume'}</Btn>
          <Btn aria-label="Remove schedule" onClick={() => setRemoving(s)}><Trash2 size={13} /></Btn>
        </span>
      ),
    }] : []),
  ]
  return (
    <div>
      <Toolbar>
        <select aria-label="Site" value={siteId} onChange={e => setSiteId(e.target.value)} style={{ ...inputStyle, width: 'auto' }}>
          <option value="">All sites</option>{(sites.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        {canManage && <Btn kind="primary" onClick={() => setForm('new')}><Plus size={15} />New schedule</Btn>}
      </Toolbar>
      <DataTable rows={q.data ?? []} columns={cols} rowKey={s => s.id} loading={q.isLoading} error={q.error}
        empty="No recurring inspections yet. Add one to be reminded when a checklist is due." rowTone={s => (s.active && s.overdue ? 'bad' : undefined)} />
      {form && <ScheduleForm schedule={form === 'new' ? undefined : form} defaultSiteId={siteId} onClose={() => setForm(null)} />}
      {removing && (
        <Dialog title="Remove schedule" onClose={() => setRemoving(null)} width={420}
          footer={<><Btn onClick={() => setRemoving(null)}>Keep it</Btn><Btn kind="danger" busy={del.isPending} onClick={() => del.mutate(removing)}>Remove</Btn></>}>
          <p style={{ margin: 0, fontSize: 14, color: 'var(--hf-text-secondary)' }}>Stop scheduling {removing.templateName} at {removing.siteName}? Inspections already done are kept. To pause it instead, use Pause.</p>
        </Dialog>
      )}
    </div>
  )
}

function ScheduleForm({ schedule, defaultSiteId, onClose }: { schedule?: InspectionSchedule; defaultSiteId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const templates = useQuery({ queryKey: ['facilities', 'inspection-templates', false], queryFn: () => facilitiesApi.inspectionTemplates(false), enabled: !schedule })
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000, enabled: !schedule })
  const [templateId, setTemplateId] = useState('')
  const [siteId, setSiteId] = useState(defaultSiteId)
  const [assetId, setAssetId] = useState('')
  const assets = useQuery({ queryKey: ['facilities', 'assets', siteId], queryFn: () => facilitiesApi.assets(siteId), enabled: !schedule && !!siteId })
  const [days, setDays] = useState(String(schedule?.frequencyDays ?? 30))
  const [due, setDue] = useState(schedule?.nextDueDate ?? '')
  const n = Number(days)
  const validDays = Number.isInteger(n) && n >= 1 && n <= 3650
  const valid = validDays && (!!schedule || (!!templateId && !!siteId))
  const tplName = useMemo(() => new Map((templates.data ?? []).map(t => [t.id, t.name])), [templates.data])
  const save = useMutation({
    mutationFn: () => schedule
      ? facilitiesApi.updateInspectionSchedule(schedule.id, { frequencyDays: n, nextDueDate: due || null })
      : facilitiesApi.createInspectionSchedule({ templateId, siteId, assetId: assetId || null, frequencyDays: n, firstDueDate: due || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(schedule ? 'Schedule updated' : 'Schedule created'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={schedule ? `Edit ${schedule.templateName}` : 'New inspection schedule'} onClose={onClose} width={500}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!valid} onClick={() => save.mutate()}>Save</Btn></>}>
      {!schedule && (
        <>
          <Field label="Checklist *">
            <select value={templateId} onChange={e => setTemplateId(e.target.value)} style={inputStyle}>
              <option value="">Select a checklist…</option>{(templates.data ?? []).map(t => <option key={t.id} value={t.id}>{tplName.get(t.id)}</option>)}
            </select>
          </Field>
          <Field label="Site *">
            <select value={siteId} onChange={e => { setSiteId(e.target.value); setAssetId('') }} style={inputStyle}>
              <option value="">Select a site…</option>{(sites.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Asset" hint="Leave as 'Whole site' to schedule it for the site itself.">
            <select value={assetId} onChange={e => setAssetId(e.target.value)} disabled={!siteId} style={inputStyle}>
              <option value="">Whole site</option>{(assets.data ?? []).map(a => <option key={a.id} value={a.id}>{a.assetTag} — {a.name}</option>)}
            </select>
          </Field>
        </>
      )}
      <Field label="Repeat every (days) *" hint={validDays ? every(n) : 'Whole days, 1 to 3650'}>
        <input inputMode="numeric" value={days} onChange={e => setDays(e.target.value.replace(/\D/g, ''))} style={inputStyle} />
      </Field>
      <Field label={schedule ? 'Next due' : 'First due'} hint={schedule ? undefined : 'Defaults to one interval from today.'}>
        <input type="date" value={due} onChange={e => setDue(e.target.value)} style={inputStyle} />
      </Field>
    </Dialog>
  )
}
