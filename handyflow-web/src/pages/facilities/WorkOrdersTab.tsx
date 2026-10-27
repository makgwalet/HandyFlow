// src/pages/facilities/WorkOrdersTab.tsx
//
// Work order list, "new work order" and the job card (side panel) with the lifecycle actions. The server enforces
// every rule (scope, status, segregation of duties); the buttons shown here only mirror what the caller may try.
// A work order can be linked to directly with ?wo=<id>.
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FileDown, Plus, ShieldAlert, RotateCcw } from 'lucide-react'
import { facilitiesApi, type WorkOrder, type CreateWorkOrderBody } from '../../api/facilities.api'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import Chip from '../../components/ui/Chip'
import { Btn, DataTable, DefList, Dialog, Field, PriorityChip, StatusChip, Toolbar, type Column } from './ui'
import { EvidencePanel } from './Evidence'
import { daysUntil, fmtDate, fmtDateTime, fmtMoney, inputStyle, saveBlob, titleCase } from './format'

const FILTERS: { id: string; label: string }[] = [
  { id: '', label: 'All' }, { id: 'REQUESTED', label: 'Requested' }, { id: 'OPEN', label: 'Open' }, { id: 'ASSIGNED', label: 'Assigned' },
  { id: 'IN_PROGRESS', label: 'In progress' }, { id: 'ON_HOLD', label: 'On hold' }, { id: 'PENDING_VERIFICATION', label: 'To verify' },
  { id: 'COMPLETED', label: 'Completed' },
]
const CATEGORIES = ['CORRECTIVE', 'EMERGENCY', 'INSPECTION', 'OTHER']
const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT', 'EMERGENCY']
const CAN_CREATE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_WORKORDER_CREATE']
const CAN_APPROVE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_WORKORDER_APPROVE']
const CAN_ASSIGN = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_WORKORDER_ASSIGN']
const CAN_EXECUTE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_TECHNICIAN']
const CAN_VERIFY = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_WORKORDER_VERIFY']
const CAN_ATTACH = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_TECHNICIAN', 'FACILITIES_WORKORDER_REQUEST']
const FINISHED = ['COMPLETED', 'REJECTED', 'CANCELLED']

const isOverdue = (w: WorkOrder) => !FINISHED.includes(w.status) && w.status !== 'PENDING_VERIFICATION' && w.status !== 'REQUESTED'
  && (daysUntil(w.scheduledDate) ?? 0) < 0 && w.scheduledDate != null

export default function WorkOrdersTab() {
  const [params, setParams] = useSearchParams()
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')
  const [creating, setCreating] = useState(false)
  const openId = params.get('wo')
  const canCreate = usePermissions(CAN_CREATE)

  const orders = useQuery({ queryKey: ['facilities', 'work-orders', status], queryFn: () => facilitiesApi.workOrders(status || undefined) })
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const assets = useQuery({ queryKey: ['facilities', 'assets'], queryFn: () => facilitiesApi.assets(), staleTime: 5 * 60_000 })
  const siteName = useMemo(() => new Map((sites.data ?? []).map(s => [s.id, s.name])), [sites.data])
  const assetName = useMemo(() => new Map((assets.data ?? []).map(a => [a.id, a.name])), [assets.data])

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase()
    return (orders.data ?? []).filter(w => !q || [w.workOrderNumber, w.description, siteName.get(w.siteId), assetName.get(w.assetId ?? ''), w.technicianName, w.vendorName]
      .some(v => v?.toLowerCase().includes(q)))
  }, [orders.data, search, siteName, assetName])

  const open = (id: string | null) => setParams(p => { const n = new URLSearchParams(p); if (id) n.set('wo', id); else n.delete('wo'); return n }, { replace: true })

  const cols: Column<WorkOrder>[] = [
    { key: 'no', header: 'Number', render: w => <strong style={{ whiteSpace: 'nowrap' }}>{w.workOrderNumber}</strong> },
    { key: 'desc', header: 'Description', render: w => (
      <div style={{ maxWidth: 360 }}>
        <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{w.description}</div>
        <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{titleCase(w.category)}{w.assetId ? ` · ${assetName.get(w.assetId) ?? 'Asset'}` : ''}</div>
      </div>) },
    { key: 'site', header: 'Site', render: w => <span style={{ whiteSpace: 'nowrap' }}>{siteName.get(w.siteId) ?? '—'}</span> },
    { key: 'pri', header: 'Priority', render: w => <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}><PriorityChip priority={w.priority} />{w.safetyImpact && <span title="Safety impact"><ShieldAlert size={14} style={{ color: 'var(--hf-danger-text)' }} aria-label="Safety impact" /></span>}</span> },
    { key: 'st', header: 'Status', render: w => <StatusChip status={w.status} /> },
    { key: 'who', header: 'Assigned to', render: w => <span style={{ whiteSpace: 'nowrap' }}>{w.technicianName ?? w.vendorName ?? <span style={{ color: 'var(--hf-text-faint)' }}>Unassigned</span>}</span> },
    { key: 'due', header: 'Scheduled', render: w => <span style={{ whiteSpace: 'nowrap', color: isOverdue(w) ? 'var(--hf-danger-text)' : undefined, fontWeight: isOverdue(w) ? 700 : undefined }}>{fmtDate(w.scheduledDate)}{isOverdue(w) ? ' · overdue' : ''}</span> },
  ]

  return (
    <div>
      <Toolbar search={search} onSearch={setSearch} placeholder="Search number, description, site or technician">
        {canCreate && <Btn kind="primary" onClick={() => setCreating(true)}><Plus size={15} />New work order</Btn>}
      </Toolbar>
      <div role="tablist" aria-label="Filter by status" style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 14 }}>
        {FILTERS.map(f => (
          <button key={f.id} type="button" role="tab" aria-selected={status === f.id} onClick={() => setStatus(f.id)}
            style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12.5, fontWeight: 600, cursor: 'pointer',
              border: `1px solid ${status === f.id ? 'var(--hf-accent)' : 'var(--hf-border)'}`,
              background: status === f.id ? 'var(--hf-accent-soft)' : 'var(--hf-surface)', color: status === f.id ? 'var(--hf-accent-text-strong)' : 'var(--hf-text-secondary)' }}>
            {f.label}
          </button>
        ))}
      </div>
      <DataTable rows={rows} columns={cols} rowKey={w => w.id} loading={orders.isLoading} error={orders.error} onRowClick={w => open(w.id)}
        empty={search || status ? 'No work orders match.' : 'No work orders yet.'} rowTone={w => (isOverdue(w) ? 'warn' : undefined)} />
      {creating && <NewWorkOrderDialog onClose={() => setCreating(false)} onCreated={id => { setCreating(false); open(id) }} />}
      {openId && <JobCard id={openId} siteName={siteName} assetName={assetName} onClose={() => open(null)} />}
    </div>
  )
}

// ── new work order ──────────────────────────────────────────────────────────────────────────
function NewWorkOrderDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const qc = useQueryClient()
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites })
  const [form, setForm] = useState<CreateWorkOrderBody & { requiresVerification: boolean }>({
    siteId: '', assetId: null, category: 'CORRECTIVE', priority: 'NORMAL', description: '', scheduledDate: null, requiresVerification: false,
  })
  const assets = useQuery({ queryKey: ['facilities', 'assets', form.siteId], queryFn: () => facilitiesApi.assets(form.siteId), enabled: !!form.siteId })
  const create = useMutation({
    mutationFn: () => facilitiesApi.createWorkOrder({ ...form, assetId: form.assetId || null, scheduledDate: form.scheduledDate || null }),
    onSuccess: wo => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${wo.workOrderNumber} created`); onCreated(wo.id) },
    onError: e => toast.error(errorMessage(e)),
  })
  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) => setForm(f => ({ ...f, [k]: v }))
  const valid = form.siteId && form.description.trim().length > 0
  return (
    <Dialog title="New work order" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={create.isPending} disabled={!valid} onClick={() => create.mutate()}>Create</Btn></>}>
      <Field label="Site *">
        <select value={form.siteId} onChange={e => { set('siteId', e.target.value); set('assetId', null) }} style={inputStyle}>
          <option value="">Select a site…</option>
          {(sites.data ?? []).filter(s => s.status !== 'CLOSED').map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </Field>
      <Field label="Asset" hint="Optional. Pick the item that needs work.">
        <select value={form.assetId ?? ''} onChange={e => set('assetId', e.target.value || null)} style={inputStyle} disabled={!form.siteId}>
          <option value="">None</option>
          {(assets.data ?? []).map(a => <option key={a.id} value={a.id}>{a.assetTag} · {a.name}</option>)}
        </select>
      </Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <Field label="Category"><select value={form.category} onChange={e => set('category', e.target.value)} style={inputStyle}>{CATEGORIES.map(c => <option key={c} value={c}>{titleCase(c)}</option>)}</select></Field>
        <Field label="Priority"><select value={form.priority} onChange={e => set('priority', e.target.value)} style={inputStyle}>{PRIORITIES.map(c => <option key={c} value={c}>{titleCase(c)}</option>)}</select></Field>
      </div>
      <Field label="Description *"><textarea value={form.description} onChange={e => set('description', e.target.value)} rows={4} maxLength={2000} style={{ ...inputStyle, resize: 'vertical' }} placeholder="What needs doing, and where exactly?" /></Field>
      <Field label="Scheduled date"><input type="date" value={form.scheduledDate ?? ''} onChange={e => set('scheduledDate', e.target.value || null)} style={inputStyle} /></Field>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, color: 'var(--hf-text-secondary)' }}>
        <input type="checkbox" checked={form.requiresVerification} onChange={e => set('requiresVerification', e.target.checked)} />
        Needs independent verification when finished
      </label>
    </Dialog>
  )
}

// ── job card ────────────────────────────────────────────────────────────────────────────────
type ActionKind = 'approve' | 'reject' | 'assign' | 'start' | 'hold' | 'complete' | 'verify' | 'reject-verification' | 'cancel'

function JobCard({ id, siteName, assetName, onClose }: { id: string; siteName: Map<string, string>; assetName: Map<string, string>; onClose: () => void }) {
  const [action, setAction] = useState<ActionKind | null>(null)
  const canApprove = usePermissions(CAN_APPROVE), canAssign = usePermissions(CAN_ASSIGN)
  const canExecute = usePermissions(CAN_EXECUTE), canVerify = usePermissions(CAN_VERIFY), canAttach = usePermissions(CAN_ATTACH)
  const pdf = useMutation({
    mutationFn: async () => saveBlob(await facilitiesApi.jobCardPdf(id), `job-card-${wo.data?.workOrderNumber ?? id}.pdf`),
    onError: e => toast.error(errorMessage(e)),
  })
  const wo = useQuery({ queryKey: ['facilities', 'work-order', id], queryFn: () => facilitiesApi.workOrder(id) })
  const costs = useQuery({ queryKey: ['facilities', 'work-order', id, 'costs'], queryFn: () => facilitiesApi.costs(id) })
  const w = wo.data

  const buttons: { kind: ActionKind; label: string; primary?: boolean; danger?: boolean }[] = []
  if (w) {
    if (w.status === 'REQUESTED' && canApprove) buttons.push({ kind: 'approve', label: 'Approve', primary: true }, { kind: 'reject', label: 'Decline', danger: true })
    if (['OPEN', 'ASSIGNED', 'ON_HOLD', 'IN_PROGRESS'].includes(w.status) && canAssign) buttons.push({ kind: 'assign', label: w.technicianId || w.vendorId ? 'Reassign' : 'Assign', primary: w.status === 'OPEN' })
    if (['ASSIGNED', 'ON_HOLD'].includes(w.status) && canExecute) buttons.push({ kind: 'start', label: w.status === 'ON_HOLD' ? 'Resume' : 'Start work', primary: true })
    if (w.status === 'IN_PROGRESS' && canExecute) buttons.push({ kind: 'hold', label: 'Put on hold' })
    if (['ASSIGNED', 'IN_PROGRESS', 'ON_HOLD'].includes(w.status) && canExecute) buttons.push({ kind: 'complete', label: 'Complete', primary: w.status === 'IN_PROGRESS' })
    if (w.status === 'PENDING_VERIFICATION' && canVerify) buttons.push({ kind: 'verify', label: 'Verify', primary: true }, { kind: 'reject-verification', label: 'Send back', danger: true })
    if (['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD'].includes(w.status) && canAssign) buttons.push({ kind: 'cancel', label: 'Cancel', danger: true })
  }

  return (
    <Dialog title={w ? `${w.workOrderNumber}` : 'Work order'} onClose={onClose} side width={520}
      footer={buttons.length || w ? <>{w && <Btn busy={pdf.isPending} onClick={() => pdf.mutate()} style={{ marginRight: 'auto' }}><FileDown size={14} />Job card PDF</Btn>}{buttons.map(b => <Btn key={b.kind} kind={b.primary ? 'primary' : b.danger ? 'danger' : 'secondary'} onClick={() => setAction(b.kind)}>{b.label}</Btn>)}</> : undefined}>
      {wo.isError && <div role="alert" style={{ color: 'var(--hf-danger-text)', fontSize: 13 }}>This work order could not be loaded. It may be outside your sites.</div>}
      {!w && !wo.isError && <div aria-busy="true" style={{ color: 'var(--hf-text-muted)', fontSize: 13 }}>Loading…</div>}
      {w && (
        <>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
            <StatusChip status={w.status} /><PriorityChip priority={w.priority} />
            {w.safetyImpact && <Chip tone="bad" icon={<ShieldAlert size={12} />}>Safety impact</Chip>}
            {w.reworkCount > 0 && <Chip tone="warn" icon={<RotateCcw size={12} />}>Reworked {w.reworkCount}×</Chip>}
            {w.requiresVerification && <Chip tone="info">Needs verification</Chip>}
          </div>
          <p style={{ margin: '0 0 16px', fontSize: 14, lineHeight: 1.5, color: 'var(--hf-text)' }}>{w.description}</p>
          <DefList items={[
            ['Site', siteName.get(w.siteId) ?? '—'], ['Asset', w.assetId ? assetName.get(w.assetId) ?? '—' : '—'], ['Category', titleCase(w.category)],
            ['Assigned to', w.technicianName ?? w.vendorName ?? 'Unassigned'], ['Reported by', w.reportedBy ?? '—'], ['Scheduled', fmtDate(w.scheduledDate)],
            ['Created', fmtDateTime(w.createdAt)], ['Completed', fmtDateTime(w.completedAt)],
            ...(w.holdReason && w.status === 'ON_HOLD' ? [['On hold because', w.holdReason] as [string, string]] : []),
            ...(w.rejectionReason ? [['Declined because', w.rejectionReason] as [string, string]] : []),
            ...(w.cancellationReason ? [['Cancelled because', w.cancellationReason] as [string, string]] : []),
            ...(w.lastReworkReason ? [['Sent back because', w.lastReworkReason] as [string, string]] : []),
            ...(w.completionNotes ? [['Completion notes', w.completionNotes] as [string, string]] : []),
            ...(w.downtimeMinutes != null ? [['Downtime', `${w.downtimeMinutes} min`] as [string, string]] : []),
            ...(w.failureCause ? [['Failure cause', w.failureCause] as [string, string]] : []),
            ['Cost', fmtMoney(w.cost)],
          ]} />
          <h3 style={{ margin: '22px 0 8px', fontSize: 13, fontWeight: 700 }}>Cost lines</h3>
          {costs.data && costs.data.length > 0 ? (
            <table style={{ width: '100%', fontSize: 12.5, borderCollapse: 'collapse' }}>
              <tbody>
                {costs.data.map(c => (
                  <tr key={c.id} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                    <td style={{ padding: '7px 0' }}>{c.description}<div style={{ fontSize: 11.5, color: 'var(--hf-text-muted)' }}>{titleCase(c.costType)} · {c.quantity} × {fmtMoney(c.unitCost)}</div></td>
                    <td style={{ textAlign: 'right', fontWeight: 600 }}>{fmtMoney(c.totalCost)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : <div style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>No cost recorded yet. Parts issued from stock appear here.</div>}
          <EvidencePanel queryKey={['work-order', id]} load={() => facilitiesApi.evidence('work-orders', id)}
            attach={f => facilitiesApi.attachEvidence('work-orders', id, f)} canAttach={canAttach && !FINISHED.includes(w.status)}
            note={w.requiresVerification ? 'Photos of the finished work are needed before this job can be completed.' : undefined} />
        </>
      )}
      {w && action && <ActionDialog kind={action} wo={w} onClose={() => setAction(null)} />}
    </Dialog>
  )
}

const ACTION_COPY: Record<ActionKind, { title: string; confirm: string; reason?: string; danger?: boolean }> = {
  approve: { title: 'Approve request', confirm: 'Approve' },
  reject: { title: 'Decline request', confirm: 'Decline', reason: 'Reason (shown to the requester)', danger: true },
  assign: { title: 'Assign work order', confirm: 'Assign' },
  start: { title: 'Start work', confirm: 'Start' },
  hold: { title: 'Put on hold', confirm: 'Put on hold', reason: 'Why is the work on hold?' },
  complete: { title: 'Complete work', confirm: 'Complete' },
  verify: { title: 'Verify completed work', confirm: 'Verify' },
  'reject-verification': { title: 'Send back for rework', confirm: 'Send back', reason: 'What needs to be redone?', danger: true },
  cancel: { title: 'Cancel work order', confirm: 'Cancel work order', reason: 'Why is it being cancelled?', danger: true },
}

function ActionDialog({ kind, wo, onClose }: { kind: ActionKind; wo: WorkOrder; onClose: () => void }) {
  const qc = useQueryClient()
  const copy = ACTION_COPY[kind]
  const [reason, setReason] = useState('')
  const [target, setTarget] = useState<{ type: 'tech' | 'vendor'; id: string }>({ type: 'tech', id: '' })
  const [priority, setPriority] = useState('')
  const [scheduledDate, setScheduledDate] = useState('')
  const [notes, setNotes] = useState('')
  const [cost, setCost] = useState('')
  const [downtime, setDowntime] = useState('')
  const [cause, setCause] = useState('')
  const techs = useQuery({ queryKey: ['facilities', 'technicians'], queryFn: facilitiesApi.technicians, enabled: kind === 'assign', staleTime: 60_000 })
  const vendors = useQuery({ queryKey: ['facilities', 'vendors'], queryFn: facilitiesApi.vendors, enabled: kind === 'assign', staleTime: 60_000 })

  const body = (): object => {
    switch (kind) {
      case 'approve': return { priority: priority || undefined, scheduledDate: scheduledDate || undefined }
      case 'assign': {
        if (target.type === 'tech') { const t = techs.data?.find(x => x.id === target.id); return { technicianId: target.id, technicianName: t?.name } }
        const v = vendors.data?.find(x => x.id === target.id); return { vendorId: target.id, vendorName: v?.companyName }
      }
      case 'complete': return { completionNotes: notes || undefined, cost: cost ? Number(cost) : undefined, downtimeMinutes: downtime ? Number(downtime) : undefined, failureCause: cause || undefined }
      default: return copy.reason ? { reason } : {}
    }
  }
  const run = useMutation({
    mutationFn: () => facilitiesApi.workOrderAction(wo.id, kind, body()),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${wo.workOrderNumber}: ${copy.title.toLowerCase()} done`); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  const ok = (!copy.reason || reason.trim()) && (kind !== 'assign' || target.id)
  return (
    <Dialog title={copy.title} onClose={onClose} width={460}
      footer={<><Btn onClick={onClose}>Back</Btn><Btn kind={copy.danger ? 'danger' : 'primary'} busy={run.isPending} disabled={!ok} onClick={() => run.mutate()}>{copy.confirm}</Btn></>}>
      {copy.reason && <Field label={copy.reason}><textarea value={reason} onChange={e => setReason(e.target.value)} rows={3} maxLength={500} style={{ ...inputStyle, resize: 'vertical' }} autoFocus /></Field>}
      {kind === 'approve' && (
        <>
          <Field label="Priority" hint="Leave as is to keep the priority worked out from the request. It cannot go below the safety floor.">
            <select value={priority} onChange={e => setPriority(e.target.value)} style={inputStyle}><option value="">Keep ({titleCase(wo.priority)})</option>{PRIORITIES.map(p => <option key={p} value={p}>{titleCase(p)}</option>)}</select>
          </Field>
          <Field label="Scheduled date"><input type="date" value={scheduledDate} onChange={e => setScheduledDate(e.target.value)} style={inputStyle} /></Field>
        </>
      )}
      {kind === 'assign' && (
        <>
          <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
            {(['tech', 'vendor'] as const).map(t => (
              <button key={t} type="button" onClick={() => setTarget({ type: t, id: '' })} aria-pressed={target.type === t}
                style={{ flex: 1, padding: '8px', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer', border: `1px solid ${target.type === t ? 'var(--hf-accent)' : 'var(--hf-border-strong)'}`,
                  background: target.type === t ? 'var(--hf-accent-soft)' : 'var(--hf-surface)', color: target.type === t ? 'var(--hf-accent-text-strong)' : 'var(--hf-text-secondary)' }}>
                {t === 'tech' ? 'Technician' : 'Vendor'}
              </button>
            ))}
          </div>
          <Field label={target.type === 'tech' ? 'Technician' : 'Vendor'} hint={target.type === 'vendor' ? 'Only approved vendors with valid documents can be assigned; the server checks this.' : undefined}>
            <select value={target.id} onChange={e => setTarget(t => ({ ...t, id: e.target.value }))} style={inputStyle}>
              <option value="">Select…</option>
              {target.type === 'tech'
                ? (techs.data ?? []).filter(t => t.active).map(t => <option key={t.id} value={t.id}>{t.name}{t.specialization ? ` · ${t.specialization}` : ''}</option>)
                : (vendors.data ?? []).filter(v => v.active && v.onboardingStatus === 'APPROVED').map(v => <option key={v.id} value={v.id}>{v.companyName}</option>)}
            </select>
          </Field>
        </>
      )}
      {kind === 'complete' && (
        <>
          <Field label="Completion notes"><textarea value={notes} onChange={e => setNotes(e.target.value)} rows={3} maxLength={2000} style={{ ...inputStyle, resize: 'vertical' }} autoFocus /></Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Cost (R)" hint="Used only if no cost lines were captured."><input type="number" min="0" step="0.01" value={cost} onChange={e => setCost(e.target.value)} style={inputStyle} /></Field>
            <Field label="Downtime (minutes)"><input type="number" min="0" step="1" value={downtime} onChange={e => setDowntime(e.target.value)} style={inputStyle} /></Field>
          </div>
          <Field label="Failure cause"><input value={cause} onChange={e => setCause(e.target.value)} maxLength={200} style={inputStyle} /></Field>
          {wo.requiresVerification && <div style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>This job needs verification. Attach evidence first; it then waits for a verifier.</div>}
        </>
      )}
      {['start', 'verify'].includes(kind) && <p style={{ margin: 0, fontSize: 14, color: 'var(--hf-text-secondary)' }}>{kind === 'start' ? `Start work on ${wo.workOrderNumber}?` : `Confirm that ${wo.workOrderNumber} has been done properly. You cannot verify work you completed yourself.`}</p>}
    </Dialog>
  )
}
