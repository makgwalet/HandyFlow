// src/pages/contracting/ObligationsTab.tsx
// What contracts require someone to do, and by when. Entered by staff; nothing is read out of the contract text.
import { useState, type CSSProperties } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X } from 'lucide-react'
import { unwrap } from './contractingApi'
import { usePermissions } from '../../hooks/usePermission'

export interface Obligation {
  id: string; contractId: string; contractNumber: string; contractTitle: string; title: string; description?: string | null
  obligationType: string; responsibleSide: string; ownerEmail?: string | null; dueDate: string; recurrence: string
  status: string; overdue: boolean; daysUntilDue: number; completionNote?: string | null; waivedReason?: string | null
}
interface ContractOption { id: string; contractNumber: string; title: string; status: string }

const TYPES = ['PAYMENT', 'DELIVERY', 'REPORTING', 'COMPLIANCE', 'NOTICE', 'OTHER']
const RECURRENCE = [['NONE', 'Does not repeat'], ['MONTHLY', 'Monthly'], ['QUARTERLY', 'Quarterly'], ['ANNUALLY', 'Annually']]
const FILTERS = [['all', 'All open'], ['overdue', 'Overdue'], ['7', 'Due in 7 days'], ['30', 'Due in 30 days']] as const

const inp: CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: 'var(--hf-surface)' }
const lbl: CSSProperties = { display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 4 }
const btn: CSSProperties = { padding: '5px 11px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }
const MODAL: CSSProperties = { position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }

const errMsg = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

const dueLabel = (o: Obligation) =>
  o.overdue ? `Overdue by ${-o.daysUntilDue} day${o.daysUntilDue === -1 ? '' : 's'}` : o.daysUntilDue === 0 ? 'Due today' : `Due in ${o.daysUntilDue} day${o.daysUntilDue === 1 ? '' : 's'}`

const EMPTY = { contractId: '', title: '', description: '', obligationType: 'PAYMENT', responsibleSide: 'OURS', ownerEmail: '', dueDate: '', recurrence: 'NONE' }

export default function ObligationsTab() {
  const qc = useQueryClient()
  const canWrite = usePermissions(['CONTRACTS_MANAGE', 'CONTRACTS_ADMIN'], 'any')
  const [filter, setFilter] = useState<(typeof FILTERS)[number][0]>('all')
  const [editing, setEditing] = useState<Obligation | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState('')

  const params = filter === 'overdue' ? '?overdueOnly=true' : filter === 'all' ? '' : `?dueWithinDays=${filter}`
  const { data: obligations = [], isLoading } = useQuery<Obligation[]>({
    queryKey: ['contract-obligations', filter],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/contracts/obligations${params}`)) as Obligation[],
  })
  const { data: summary } = useQuery<{ open: number; overdue: number; dueNext7Days: number }>({
    queryKey: ['contract-obligations-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/contracts/obligations/summary'); return r.data?.data ?? r.data },
  })
  const { data: contracts = [] } = useQuery<ContractOption[]>({
    queryKey: ['contracts', 'options'],
    enabled: showForm && !editing,
    queryFn: async () => unwrap(await apiClient.get('/api/v1/contracts?size=200')) as ContractOption[],
  })

  const refresh = () => { qc.invalidateQueries({ queryKey: ['contract-obligations'] }); qc.invalidateQueries({ queryKey: ['contract-obligations-summary'] }) }

  const save = useMutation({
    mutationFn: () => {
      const body = { title: form.title, description: form.description || null, obligationType: form.obligationType,
        responsibleSide: form.responsibleSide, ownerEmail: form.ownerEmail || null, dueDate: form.dueDate, recurrence: form.recurrence }
      return editing ? apiClient.put(`/api/v1/contracts/obligations/${editing.id}`, body)
                     : apiClient.post(`/api/v1/contracts/${form.contractId}/obligations`, body)
    },
    onSuccess: () => { refresh(); setShowForm(false); setEditing(null); setForm(EMPTY); setError('') },
    onError: (e: unknown) => setError(errMsg(e, 'Could not save the obligation')),
  })
  const act = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: Record<string, unknown> }) =>
      apiClient.post(`/api/v1/contracts/obligations/${id}/${action}`, body ?? {}),
    onSuccess: () => { refresh(); setError('') },
    onError: (e: unknown) => setError(errMsg(e, 'That step could not be completed')),
  })

  const openNew = () => { setEditing(null); setForm(EMPTY); setShowForm(true); setError('') }
  const openEdit = (o: Obligation) => {
    setEditing(o)
    setForm({ contractId: o.contractId, title: o.title, description: o.description ?? '', obligationType: o.obligationType,
      responsibleSide: o.responsibleSide, ownerEmail: o.ownerEmail ?? '', dueDate: o.dueDate, recurrence: o.recurrence })
    setShowForm(true); setError('')
  }
  const complete = (o: Obligation) => {
    const note = window.prompt('Note for the record (optional)', '')
    if (note !== null) act.mutate({ id: o.id, action: 'complete', body: { note: note || null } })
  }
  const waive = (o: Obligation) => {
    const reason = window.prompt('Why is this obligation being waived?')
    if (reason && reason.trim()) act.mutate({ id: o.id, action: 'waive', body: { reason: reason.trim() } })
  }
  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) => setForm(p => ({ ...p, [k]: e.target.value }))
  const valid = form.title.trim() && form.dueDate && (editing || form.contractId)

  return (
    <div>
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginBottom: 14, alignItems: 'center' }}>
        {[['Open', summary?.open], ['Overdue', summary?.overdue], ['Due in 7 days', summary?.dueNext7Days]].map(([l, v]) => (
          <div key={l as string} style={{ background: 'var(--hf-surface-sunken)', borderRadius: 10, padding: '8px 14px' }}>
            <div style={{ fontSize: 20, fontWeight: 800, color: l === 'Overdue' && (v as number) > 0 ? 'var(--hf-danger-text)' : 'var(--hf-text)' }}>{v ?? '—'}</div>
            <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>{l}</div>
          </div>
        ))}
        <div style={{ flex: 1 }} />
        {canWrite && <button onClick={openNew} style={{ ...btn, display: 'flex', gap: 6, alignItems: 'center', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', padding: '8px 14px' }}><Plus size={14} /> Add obligation</button>}
      </div>

      <div style={{ display: 'flex', gap: 6, marginBottom: 12 }}>
        {FILTERS.map(([k, l]) => (
          <button key={k} onClick={() => setFilter(k)} style={{ ...btn, background: filter === k ? 'var(--hf-primary)' : 'var(--hf-surface)', color: filter === k ? 'var(--hf-text-on-solid)' : 'var(--hf-primary-text)' }}>{l}</button>
        ))}
      </div>

      {error && !showForm && <div style={{ marginBottom: 12, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
      {isLoading ? <div style={{ color: 'var(--hf-text-muted)' }}>Loading…</div> : obligations.length === 0 ? (
        <div style={{ color: 'var(--hf-text-faint)', fontSize: 14, padding: '24px 0' }}>No obligations match. Obligations are added by staff against a contract.</div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {obligations.map(o => (
            <div key={o.id} style={{ border: '1px solid var(--hf-border)', borderRadius: 10, padding: '10px 14px', background: 'var(--hf-surface)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontWeight: 700, color: 'var(--hf-text)' }}>{o.title}</div>
                  <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>
                    {o.contractNumber} · {o.contractTitle} · {o.obligationType.toLowerCase()} · {o.responsibleSide === 'OURS' ? 'our side' : 'counterparty'}
                    {o.recurrence !== 'NONE' ? ` · repeats ${o.recurrence.toLowerCase()}` : ''}{o.status === 'IN_PROGRESS' ? ' · in progress' : ''}
                  </div>
                  {o.description && <div style={{ fontSize: 12, color: 'var(--hf-text-secondary)', marginTop: 2 }}>{o.description}</div>}
                  <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 2 }}>{o.ownerEmail ? `Reminders go to ${o.ownerEmail}` : 'No reminder address, so no email reminders'}</div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ fontSize: 13, fontWeight: 700, color: o.overdue ? 'var(--hf-danger-text)' : 'var(--hf-text)' }}>{dueLabel(o)}</div>
                  <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>{new Date(o.dueDate).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })}</div>
                </div>
              </div>
              {canWrite && (
                <div style={{ display: 'flex', gap: 6, marginTop: 8, flexWrap: 'wrap' }}>
                  {o.status === 'OPEN' && <button style={btn} disabled={act.isPending} onClick={() => act.mutate({ id: o.id, action: 'start' })}>Start</button>}
                  <button style={btn} disabled={act.isPending} onClick={() => complete(o)}>Complete</button>
                  <button style={btn} disabled={act.isPending} onClick={() => openEdit(o)}>Edit</button>
                  <button style={btn} disabled={act.isPending} onClick={() => waive(o)}>Waive</button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {showForm && (
        <div style={MODAL}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 560, maxHeight: '92vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>{editing ? 'Edit obligation' : 'Add obligation'}</h3>
              <button onClick={() => setShowForm(false)} style={{ background: 'none', border: 'none', cursor: 'pointer' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              {!editing && (
                <div style={{ gridColumn: '1/-1' }}>
                  <label style={lbl}>Contract *</label>
                  <select style={inp} value={form.contractId} onChange={set('contractId')}>
                    <option value="">Choose a contract…</option>
                    {contracts.filter(c => !['TERMINATED', 'EXPIRED', 'DECLINED', 'CANCELLED'].includes(c.status)).map(c => <option key={c.id} value={c.id}>{c.contractNumber} · {c.title}</option>)}
                  </select>
                </div>
              )}
              <div style={{ gridColumn: '1/-1' }}><label style={lbl}>What has to be done *</label><input style={inp} value={form.title} onChange={set('title')} placeholder="e.g. Pay monthly retainer" /></div>
              <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Details</label><textarea rows={2} style={inp} value={form.description} onChange={set('description')} /></div>
              <div><label style={lbl}>Type</label><select style={inp} value={form.obligationType} onChange={set('obligationType')}>{TYPES.map(t => <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>)}</select></div>
              <div><label style={lbl}>Responsible</label><select style={inp} value={form.responsibleSide} onChange={set('responsibleSide')}><option value="OURS">Our side</option><option value="COUNTERPARTY">Counterparty</option></select></div>
              <div><label style={lbl}>Due date *</label><input type="date" style={inp} value={form.dueDate} onChange={set('dueDate')} /></div>
              <div><label style={lbl}>Repeats</label><select style={inp} value={form.recurrence} onChange={set('recurrence')}>{RECURRENCE.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div>
              <div style={{ gridColumn: '1/-1' }}><label style={lbl}>Send reminders to (email)</label><input type="email" style={inp} value={form.ownerEmail} onChange={set('ownerEmail')} placeholder="Reminders at 7 days, 1 day, on the day, then weekly if overdue" /></div>
            </div>
            {error && <div style={{ marginTop: 12, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 18 }}>
              <button onClick={() => setShowForm(false)} style={btn}>Cancel</button>
              <button disabled={!valid || save.isPending} onClick={() => save.mutate()} style={{ ...btn, background: valid ? 'var(--hf-primary)' : 'var(--hf-text-faint)', color: 'var(--hf-text-on-solid)', border: 'none' }}>{save.isPending ? 'Saving…' : 'Save'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
