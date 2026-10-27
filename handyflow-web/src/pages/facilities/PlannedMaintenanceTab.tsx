// src/pages/facilities/PlannedMaintenanceTab.tsx
//
// Planned preventive maintenance (PPM). Schedules belong to an asset: pick a site and an asset, then add recurring
// tasks (every N days). When a task falls due the server raises a work order for it.
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus } from 'lucide-react'
import { facilitiesApi, type PpmSchedule } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, Dialog, EmptyState, Field, Toolbar, type Column } from './ui'
import { daysUntil, fmtDate, inputStyle } from './format'

const CAN_MANAGE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_PPM_MANAGE']

const every = (d: number) => (d === 7 ? 'Weekly' : d === 30 ? 'Monthly' : d === 90 ? 'Quarterly' : d === 365 ? 'Yearly' : `Every ${d} days`)

export default function PlannedMaintenanceTab() {
  const [siteId, setSiteId] = useState('')
  const [assetId, setAssetId] = useState('')
  const [form, setForm] = useState<PpmSchedule | 'new' | null>(null)
  const qc = useQueryClient()
  const canManage = usePermissions(CAN_MANAGE)
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const assets = useQuery({ queryKey: ['facilities', 'assets', siteId], queryFn: () => facilitiesApi.assets(siteId || undefined) })
  const assetName = useMemo(() => new Map((assets.data ?? []).map(a => [a.id, `${a.assetTag} · ${a.name}`])), [assets.data])
  const q = useQuery({ queryKey: ['facilities', 'ppm', assetId], queryFn: () => facilitiesApi.ppmSchedules(assetId), enabled: !!assetId })
  const toggle = useMutation({
    mutationFn: (s: PpmSchedule) => facilitiesApi.ppmActive(s.id, s.active ? 'deactivate' : 'reactivate'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['facilities', 'ppm'] }),
    onError: e => toast.error(errorMessage(e)),
  })
  const cols: Column<PpmSchedule>[] = [
    { key: 'n', header: 'Task', render: s => <div><div style={{ fontWeight: 600 }}>{s.taskName}</div>{s.description && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{s.description}</div>}</div> },
    { key: 'f', header: 'Frequency', render: s => every(s.frequencyDays) },
    { key: 'nx', header: 'Next due', render: s => {
      if (!s.active) return '—'
      const d = daysUntil(s.nextDueDate)
      return <span style={{ whiteSpace: 'nowrap', color: d != null && d < 0 ? 'var(--hf-danger-text)' : undefined, fontWeight: d != null && d < 0 ? 700 : undefined }}>{fmtDate(s.nextDueDate)}{d != null && d < 0 ? ` (${-d}d overdue)` : ''}</span>
    } },
    { key: 'l', header: 'Last done', render: s => fmtDate(s.lastCompletedDate) },
    { key: 'a', header: 'Status', render: s => <Chip tone={s.active ? 'ok' : 'neutral'}>{s.active ? 'Active' : 'Paused'}</Chip> },
    ...(canManage ? [{
      key: 'do', header: '', align: 'right' as const, render: (s: PpmSchedule) => (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          <Btn onClick={() => setForm(s)}><Pencil size={13} />Edit</Btn>
          <Btn busy={toggle.isPending && toggle.variables?.id === s.id} onClick={() => toggle.mutate(s)}>{s.active ? 'Pause' : 'Resume'}</Btn>
        </span>
      ),
    }] : []),
  ]
  return (
    <div>
      <Toolbar>
        <select aria-label="Site" value={siteId} onChange={e => { setSiteId(e.target.value); setAssetId('') }} style={{ ...inputStyle, width: 'auto' }}>
          <option value="">All sites</option>
          {(sites.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        <select aria-label="Asset" value={assetId} onChange={e => setAssetId(e.target.value)} style={{ ...inputStyle, width: 'auto', minWidth: 240 }}>
          <option value="">Select an asset…</option>
          {(assets.data ?? []).map(a => <option key={a.id} value={a.id}>{a.assetTag} · {a.name}</option>)}
        </select>
        {canManage && <Btn kind="primary" disabled={!assetId} onClick={() => setForm('new')}><Plus size={15} />New schedule</Btn>}
      </Toolbar>
      {!assetId
        ? <EmptyState>Pick an asset to see its maintenance schedules.</EmptyState>
        : <DataTable rows={q.data ?? []} columns={cols} rowKey={s => s.id} loading={q.isLoading} error={q.error} empty={`No schedules for ${assetName.get(assetId) ?? 'this asset'} yet.`}
            rowTone={s => (s.active && (daysUntil(s.nextDueDate) ?? 1) < 0 ? 'bad' : undefined)} />}
      {form && assetId && <ScheduleForm assetId={assetId} schedule={form === 'new' ? undefined : form} onClose={() => setForm(null)} />}
    </div>
  )
}

const PRESETS = [{ d: 7, l: 'Weekly' }, { d: 30, l: 'Monthly' }, { d: 90, l: 'Quarterly' }, { d: 180, l: 'Every 6 months' }, { d: 365, l: 'Yearly' }]

function ScheduleForm({ assetId, schedule, onClose }: { assetId: string; schedule?: PpmSchedule; onClose: () => void }) {
  const qc = useQueryClient()
  const [taskName, setTaskName] = useState(schedule?.taskName ?? '')
  const [description, setDescription] = useState(schedule?.description ?? '')
  const [days, setDays] = useState(String(schedule?.frequencyDays ?? 30))
  const [start, setStart] = useState('')
  const save = useMutation({
    mutationFn: () => schedule
      ? facilitiesApi.updatePpm(schedule.id, { taskName: taskName.trim(), description: description.trim() || undefined, frequencyDays: Number(days) })
      : facilitiesApi.createPpm({ assetId, taskName: taskName.trim(), description: description.trim() || undefined, frequencyDays: Number(days), startDate: start || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(schedule ? 'Schedule updated' : 'Schedule created'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  const n = Number(days)
  const valid = taskName.trim() && Number.isInteger(n) && n >= 1 && n <= 3650
  return (
    <Dialog title={schedule ? 'Edit schedule' : 'New maintenance schedule'} onClose={onClose} width={480}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!valid} onClick={() => save.mutate()}>Save</Btn></>}>
      <Field label="Task *"><input value={taskName} onChange={e => setTaskName(e.target.value)} maxLength={200} placeholder="e.g. Replace air filters" style={inputStyle} autoFocus /></Field>
      <Field label="Details"><textarea value={description} onChange={e => setDescription(e.target.value)} rows={2} maxLength={1000} style={{ ...inputStyle, resize: 'vertical' }} /></Field>
      <Field label="Repeat every (days) *">
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 8 }}>
          {PRESETS.map(p => (
            <button key={p.d} type="button" onClick={() => setDays(String(p.d))} aria-pressed={days === String(p.d)}
              style={{ padding: '5px 11px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: `1px solid ${days === String(p.d) ? 'var(--hf-accent)' : 'var(--hf-border)'}`,
                background: days === String(p.d) ? 'var(--hf-accent-soft)' : 'var(--hf-surface)', color: days === String(p.d) ? 'var(--hf-accent-text-strong)' : 'var(--hf-text-secondary)' }}>{p.l}</button>
          ))}
        </div>
        <input type="number" min="1" max="3650" step="1" value={days} onChange={e => setDays(e.target.value)} style={inputStyle} />
      </Field>
      {!schedule && <Field label="First due date" hint="Leave blank to start from today."><input type="date" value={start} onChange={e => setStart(e.target.value)} style={inputStyle} /></Field>}
    </Dialog>
  )
}
