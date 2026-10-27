// src/pages/facilities/CorrectiveActionsTab.tsx
//
// Corrective actions raised by failed inspections, lapsed certificates or logged by hand. An action can be turned into a
// work order, rescheduled, closed (with a note) or cancelled (with a reason). Overdue ones stand out.
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { facilitiesApi, type CorrectiveAction } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, DataTable, DefList, Dialog, Field, PriorityChip, StatusChip, Toolbar, type Column } from './ui'
import { daysUntil, fmtDate, fmtDateTime, inputStyle, titleCase } from './format'

const CAN_MANAGE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN']
const FILTERS = [{ id: '', label: 'All' }, { id: 'OPEN', label: 'Open' }, { id: 'IN_PROGRESS', label: 'In progress' }, { id: 'CLOSED', label: 'Closed' }, { id: 'CANCELLED', label: 'Cancelled' }]
const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT', 'EMERGENCY']

export default function CorrectiveActionsTab({ onOpenWorkOrder }: { onOpenWorkOrder: (id: string) => void }) {
  const [status, setStatus] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  const [logging, setLogging] = useState(false)
  const canManage = usePermissions(CAN_MANAGE)
  const q = useQuery({ queryKey: ['facilities', 'corrective-actions', status], queryFn: () => facilitiesApi.correctiveActions(status || undefined) })
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const siteName = useMemo(() => new Map((sites.data ?? []).map(s => [s.id, s.name])), [sites.data])
  const dueLabel = (a: CorrectiveAction) => {
    if (a.status === 'CLOSED' || a.status === 'CANCELLED') return fmtDate(a.dueDate)
    const d = daysUntil(a.dueDate)
    return <span style={{ whiteSpace: 'nowrap', color: a.overdue ? 'var(--hf-danger-text)' : undefined, fontWeight: a.overdue ? 700 : undefined }}>{fmtDate(a.dueDate)}{a.overdue && d != null ? ` (${-d}d late)` : ''}</span>
  }
  const cols: Column<CorrectiveAction>[] = [
    { key: 'd', header: 'Finding', render: a => <div style={{ maxWidth: 420 }}><div style={{ fontWeight: 600 }}>{a.description}</div><div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{siteName.get(a.siteId) ?? ''}{a.workOrderId ? ' · work order raised' : ''}</div></div> },
    { key: 'src', header: 'Source', render: a => titleCase(a.sourceType) },
    { key: 'p', header: 'Priority', render: a => <span style={{ display: 'inline-flex', gap: 6 }}><PriorityChip priority={a.priority} />{a.critical && <Chip tone="bad">Critical</Chip>}</span> },
    { key: 'due', header: 'Due', render: dueLabel },
    { key: 'st', header: 'Status', render: a => <StatusChip status={a.status} /> },
  ]
  const selected = q.data?.find(a => a.id === open)
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
        {canManage && <Btn kind="primary" onClick={() => setLogging(true)}><Plus size={15} />Log finding</Btn>}
      </Toolbar>
      <DataTable rows={q.data ?? []} columns={cols} rowKey={a => a.id} loading={q.isLoading} error={q.error} onRowClick={a => setOpen(a.id)}
        rowTone={a => (a.overdue && a.status !== 'CLOSED' && a.status !== 'CANCELLED' ? 'bad' : undefined)} empty="No corrective actions." />
      {logging && <LogDialog onClose={() => setLogging(false)} />}
      {selected && <ActionPanel action={selected} siteName={siteName.get(selected.siteId) ?? '—'} onClose={() => setOpen(null)} onOpenWorkOrder={onOpenWorkOrder} />}
    </div>
  )
}

type Act = 'close' | 'cancel' | 'reschedule'

function ActionPanel({ action: a, siteName, onClose, onOpenWorkOrder }: { action: CorrectiveAction; siteName: string; onClose: () => void; onOpenWorkOrder: (id: string) => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(CAN_MANAGE)
  const [act, setAct] = useState<Act | null>(null)
  const live = a.status === 'OPEN' || a.status === 'IN_PROGRESS'
  const raise = useMutation({
    mutationFn: () => facilitiesApi.correctiveAction(a.id, 'work-order'),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success('Work order raised') },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title="Corrective action" onClose={onClose} side width={500}
      footer={canManage && live ? (
        <>
          <Btn kind="danger" onClick={() => setAct('cancel')}>Cancel</Btn>
          <Btn onClick={() => setAct('reschedule')}>Reschedule</Btn>
          <Btn onClick={() => setAct('close')}>Close</Btn>
          {!a.workOrderId && <Btn kind="primary" busy={raise.isPending} onClick={() => raise.mutate()}>Raise work order</Btn>}
        </>
      ) : undefined}>
      <p style={{ margin: '0 0 16px', fontSize: 14.5, fontWeight: 600 }}>{a.description}</p>
      <DefList items={[
        ['Site', siteName], ['Source', titleCase(a.sourceType)], ['Priority', <PriorityChip key="p" priority={a.priority} />],
        ['Status', <StatusChip key="s" status={a.status} />], ['Due', fmtDate(a.dueDate)], ['Logged', fmtDateTime(a.createdAt)],
        ...(a.workOrderId ? [['Work order', <button key="w" type="button" onClick={() => onOpenWorkOrder(a.workOrderId!)} style={{ background: 'none', border: 'none', padding: 0, color: 'var(--hf-accent-text)', cursor: 'pointer', font: 'inherit', textDecoration: 'underline' }}>Open work order</button>] as [string, React.ReactNode]] : []),
        ...(a.closureNote ? [['Closing note', a.closureNote] as [string, string]] : []),
      ]} />
      {act && <ActDialog act={act} action={a} onClose={() => setAct(null)} onDone={onClose} />}
    </Dialog>
  )
}

function ActDialog({ act, action, onClose, onDone }: { act: Act; action: CorrectiveAction; onClose: () => void; onDone: () => void }) {
  const qc = useQueryClient()
  const [text, setText] = useState('')
  const [date, setDate] = useState(action.dueDate)
  const run = useMutation({
    mutationFn: () => facilitiesApi.correctiveAction(action.id, act, act === 'reschedule' ? { dueDate: date } : { reason: text.trim() }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success('Corrective action updated'); onClose(); onDone() },
    onError: e => toast.error(errorMessage(e)),
  })
  const title = { close: 'Close action', cancel: 'Cancel action', reschedule: 'Reschedule' }[act]
  const ok = act === 'reschedule' ? !!date && date !== action.dueDate : text.trim().length > 0
  return (
    <Dialog title={title} onClose={onClose} width={440}
      footer={<><Btn onClick={onClose}>Back</Btn><Btn kind={act === 'cancel' ? 'danger' : 'primary'} busy={run.isPending} disabled={!ok} onClick={() => run.mutate()}>{title}</Btn></>}>
      {act === 'reschedule'
        ? <Field label="New due date *"><input type="date" value={date} onChange={e => setDate(e.target.value)} style={inputStyle} autoFocus /></Field>
        : <Field label={act === 'close' ? 'What was done? *' : 'Why is it being cancelled? *'}><textarea value={text} onChange={e => setText(e.target.value)} rows={3} maxLength={500} style={{ ...inputStyle, resize: 'vertical' }} autoFocus /></Field>}
    </Dialog>
  )
}

function LogDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const [f, setF] = useState({ siteId: '', assetId: '', description: '', priority: 'NORMAL', dueDate: '' })
  const set = (k: keyof typeof f, v: string) => setF(s => ({ ...s, [k]: v }))
  const assets = useQuery({ queryKey: ['facilities', 'assets', f.siteId], queryFn: () => facilitiesApi.assets(f.siteId), enabled: !!f.siteId })
  const save = useMutation({
    mutationFn: () => facilitiesApi.logFinding({ siteId: f.siteId, assetId: f.assetId || null, description: f.description.trim(), priority: f.priority, dueDate: f.dueDate }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success('Finding logged'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title="Log a finding" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!f.siteId || !f.description.trim() || !f.dueDate} onClick={() => save.mutate()}>Log</Btn></>}>
      <Field label="Site *">
        <select value={f.siteId} onChange={e => { set('siteId', e.target.value); set('assetId', '') }} style={inputStyle}>
          <option value="">Select a site…</option>
          {(sites.data ?? []).filter(s => s.status !== 'CLOSED').map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </Field>
      <Field label="Asset" hint="Optional.">
        <select value={f.assetId} onChange={e => set('assetId', e.target.value)} disabled={!f.siteId} style={inputStyle}>
          <option value="">None</option>
          {(assets.data ?? []).map(a => <option key={a.id} value={a.id}>{a.assetTag} · {a.name}</option>)}
        </select>
      </Field>
      <Field label="What needs fixing? *"><textarea value={f.description} onChange={e => set('description', e.target.value)} rows={3} maxLength={1000} style={{ ...inputStyle, resize: 'vertical' }} autoFocus /></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <Field label="Priority"><select value={f.priority} onChange={e => set('priority', e.target.value)} style={inputStyle}>{PRIORITIES.map(p => <option key={p} value={p}>{titleCase(p)}</option>)}</select></Field>
        <Field label="Due date *"><input type="date" value={f.dueDate} onChange={e => set('dueDate', e.target.value)} style={inputStyle} /></Field>
      </div>
    </Dialog>
  )
}
