// src/pages/customers/FollowUpPanel.tsx
//
// FIX: "no task/follow-up reminder system" gap, extended: "what if
// follow-ups were unsuccessful, rescheduled and all" — completion is no
// longer a single click. Clicking the circle opens an inline outcome
// prompt (Completed / No response / Reschedule); rescheduling asks for a
// new due date and creates a genuinely new follow-up linked back to this
// one, so a lead that took three attempts shows as three real records,
// not one row silently edited three times.
//
// Lives in its own tab now (CustomersPage.tsx ViewModal) — no longer
// collapsible.

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, Check, X, RotateCcw, Trash2, Plus, AlertCircle, AlertTriangle, PhoneMissed, CalendarPlus } from 'lucide-react'
import { apiClient } from '../../api/client'

type Outcome = 'COMPLETED' | 'NO_RESPONSE' | 'RESCHEDULED'

interface FollowUp {
  id:                 string
  customerId:         string
  dueDate:            string
  note:               string
  assignedTo:         string | null
  completed:          boolean
  completedAt:        string | null
  overdue:            boolean
  createdAt:          string
  outcome:            Outcome | null
  rescheduledFromId:  string | null
}

const OUTCOME_CONFIG: Record<Outcome, { label: string; color: string; bg: string; border: string }> = {
  COMPLETED:    { label: 'Completed',    color: '#16A34A', bg: '#F0FDF4', border: '#BBF7D0' },
  NO_RESPONSE:  { label: 'No response',  color: '#EA580C', bg: '#FFF7ED', border: '#FED7AA' },
  RESCHEDULED:  { label: 'Rescheduled',  color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE' },
}

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })

export function FollowUpPanel({ customerId }: { customerId: string }) {
  const qc = useQueryClient()
  const [showAdd, setShowAdd]     = useState(false)
  const [dueDate, setDueDate]     = useState('')
  const [note, setNote]           = useState('')
  const [formError, setFormError] = useState('')

  // Which pending row currently has the outcome prompt open, and (if
  // Reschedule was chosen within it) the new date being picked.
  const [completingId, setCompletingId]     = useState<string | null>(null)
  const [rescheduleDate, setRescheduleDate] = useState('')

  const invalidate = () => qc.invalidateQueries({ queryKey: ['followups', customerId] })

  const { data, isLoading } = useQuery<{ data: FollowUp[] }>({
    queryKey: ['followups', customerId],
    queryFn: () => apiClient.get(`/api/v1/crm/customers/${customerId}/followups`).then(r => r.data),
  })

  const followUps = data?.data ?? []
  const pending    = followUps.filter(f => !f.completed)
  const completed  = followUps.filter(f => f.completed)
  const overdueCount = pending.filter(f => f.overdue).length

  // For a RESCHEDULED completed row, find the new follow-up it produced —
  // reconstructed client-side from the same dataset rather than a second
  // API call, since rescheduledFromId already links them.
  const findRescheduledTo = (originalId: string) => followUps.find(f => f.rescheduledFromId === originalId)

  const createMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/v1/crm/customers/${customerId}/followups`, { dueDate, note }),
    onSuccess: () => { invalidate(); setShowAdd(false); setDueDate(''); setNote(''); setFormError('') },
    onError: (e: any) => setFormError(e?.response?.data?.message ?? 'Failed to schedule follow-up'),
  })

  const completeMutation = useMutation({
    mutationFn: ({ id, outcome, newDueDate }: { id: string; outcome: Outcome; newDueDate?: string }) =>
      apiClient.post(`/api/v1/crm/customers/${customerId}/followups/${id}/complete`, {
        outcome, rescheduleDate: newDueDate || undefined,
      }),
    onSuccess: () => { invalidate(); setCompletingId(null); setRescheduleDate('') },
  })

  const reopenMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/crm/customers/${customerId}/followups/${id}/reopen`),
    onSuccess: invalidate,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/customers/${customerId}/followups/${id}`),
    onSuccess: invalidate,
  })

  const submitCreate = () => {
    if (!dueDate) { setFormError('Due date is required'); return }
    if (!note.trim()) { setFormError('Note is required'); return }
    setFormError('')
    createMutation.mutate()
  }

  const submitReschedule = (id: string) => {
    if (!rescheduleDate) return
    completeMutation.mutate({ id, outcome: 'RESCHEDULED', newDueDate: rescheduleDate })
  }

  return (
    <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid #F1F5F9' }}>
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: 12,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <CalendarClock size={13} color={overdueCount > 0 ? '#DC2626' : '#94A3B8'} />
          <span style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            Follow-ups
          </span>
          {pending.length > 0 && (
            <span style={{
              fontSize: 10, fontWeight: 600, padding: '1px 7px', borderRadius: 20,
              background: overdueCount > 0 ? '#FEF2F2' : '#EFF6FF',
              color:      overdueCount > 0 ? '#DC2626' : '#1D4ED8',
              border:     `1px solid ${overdueCount > 0 ? '#FECACA' : '#BFDBFE'}`,
            }}>
              {pending.length} pending{overdueCount > 0 ? ` · ${overdueCount} overdue` : ''}
            </span>
          )}
        </div>
      </div>

      <div>
        {isLoading && <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>Loading…</p>}

        {!isLoading && pending.length === 0 && !showAdd && (
          <p style={{ fontSize: 12, color: '#64748B', margin: '0 0 10px', lineHeight: 1.5 }}>
            No pending follow-ups. Schedule one to remind yourself (or a teammate) what to do next.
          </p>
        )}

        {pending.map(f => (
          completingId === f.id ? (
            <OutcomePrompt key={f.id} f={f}
              rescheduleDate={rescheduleDate} setRescheduleDate={setRescheduleDate}
              isPending={completeMutation.isPending}
              onComplete={(outcome) => completeMutation.mutate({ id: f.id, outcome })}
              onSubmitReschedule={() => submitReschedule(f.id)}
              onCancel={() => { setCompletingId(null); setRescheduleDate('') }} />
          ) : (
            <FollowUpRow key={f.id} f={f}
              onStartComplete={() => { setCompletingId(f.id); setRescheduleDate('') }}
              onDelete={() => deleteMutation.mutate(f.id)} />
          )
        ))}

        {completed.length > 0 && (
          <details style={{ marginTop: pending.length > 0 ? 8 : 0 }}>
            <summary style={{ fontSize: 11, color: '#94A3B8', cursor: 'pointer', marginBottom: 6 }}>
              {completed.length} completed
            </summary>
            {completed.map(f => (
              <FollowUpRow key={f.id} f={f}
                rescheduledTo={f.outcome === 'RESCHEDULED' ? findRescheduledTo(f.id) : undefined}
                onReopen={() => reopenMutation.mutate(f.id)}
                onDelete={() => deleteMutation.mutate(f.id)} />
            ))}
          </details>
        )}

        {!showAdd ? (
          <button
            onClick={() => { setShowAdd(true); setFormError('') }}
            style={{
              marginTop: 10, display: 'flex', alignItems: 'center', gap: 5,
              fontSize: 12, color: '#1D4ED8', background: '#EFF6FF',
              border: '1px solid #BFDBFE', borderRadius: 6, padding: '5px 10px',
              cursor: 'pointer',
            }}>
            <Plus size={12} /> Schedule follow-up
          </button>
        ) : (
          <div style={{ marginTop: 10 }}>
            <label style={{ fontSize: 11, fontWeight: 600, color: '#374151', display: 'block', marginBottom: 3 }}>
              Due date
            </label>
            <input type="date" value={dueDate} onChange={e => setDueDate(e.target.value)}
              min={new Date().toISOString().slice(0, 10)}
              style={{ padding: '6px 10px', fontSize: 13, border: '1.5px solid #E2E8F0', borderRadius: 6, fontFamily: 'inherit', marginBottom: 8 }} />
            <label style={{ fontSize: 11, fontWeight: 600, color: '#374151', display: 'block', marginBottom: 3 }}>
              Note
            </label>
            <textarea value={note} onChange={e => setNote(e.target.value)}
              placeholder="e.g. Call back re: renewal quote"
              rows={2}
              style={{
                width: '100%', padding: '8px 10px', fontSize: 13,
                border: '1.5px solid #E2E8F0', borderRadius: 8,
                fontFamily: 'inherit', resize: 'vertical', boxSizing: 'border-box',
              }} />
            {formError && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginTop: 6, fontSize: 12, color: '#DC2626' }}>
                <AlertCircle size={11} />{formError}
              </div>
            )}
            <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
              <button onClick={submitCreate} disabled={createMutation.isPending}
                style={{ fontSize: 12, color: '#1D4ED8', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 6, padding: '5px 12px', cursor: 'pointer', fontFamily: 'inherit' }}>
                {createMutation.isPending ? 'Scheduling…' : 'Schedule'}
              </button>
              <button onClick={() => { setShowAdd(false); setFormError('') }}
                style={{ fontSize: 12, color: '#374151', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 6, padding: '5px 12px', cursor: 'pointer', fontFamily: 'inherit' }}>
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * Inline outcome choice for a pending follow-up — replaces its row while
 * open. Reschedule is a two-step reveal within this same component
 * (choose Reschedule → date picker appears) rather than routing back
 * through the parent's onComplete, since the date is only meaningful
 * paired with the RESCHEDULED outcome and never needs to be submitted
 * without it.
 */
function OutcomePrompt({ f, rescheduleDate, setRescheduleDate, isPending, onComplete, onSubmitReschedule, onCancel }: {
  f: FollowUp
  rescheduleDate: string
  setRescheduleDate: (v: string) => void
  isPending: boolean
  onComplete: (outcome: 'COMPLETED' | 'NO_RESPONSE') => void
  onSubmitReschedule: () => void
  onCancel: () => void
}) {
  const [pickingDate, setPickingDate] = useState(false)

  return (
    <div style={{ padding: '10px 0', borderBottom: '1px solid #F8FAFC' }}>
      <p style={{ fontSize: 12.5, color: '#0F172A', margin: '0 0 8px', fontWeight: 600 }}>{f.note}</p>
      {!pickingDate ? (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <button onClick={() => onComplete('COMPLETED')} disabled={isPending}
            style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, fontWeight: 600, color: '#16A34A', background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: 6, padding: '6px 12px', cursor: 'pointer', fontFamily: 'inherit' }}>
            <Check size={12} /> Completed
          </button>
          <button onClick={() => onComplete('NO_RESPONSE')} disabled={isPending}
            style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, fontWeight: 600, color: '#EA580C', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 6, padding: '6px 12px', cursor: 'pointer', fontFamily: 'inherit' }}>
            <PhoneMissed size={12} /> No response
          </button>
          <button onClick={() => setPickingDate(true)} disabled={isPending}
            style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, fontWeight: 600, color: '#1D4ED8', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 6, padding: '6px 12px', cursor: 'pointer', fontFamily: 'inherit' }}>
            <CalendarPlus size={12} /> Reschedule
          </button>
          <button onClick={onCancel} disabled={isPending}
            style={{ display: 'flex', alignItems: 'center', color: '#94A3B8', background: 'none', border: 'none', cursor: 'pointer', padding: '6px' }}>
            <X size={14} />
          </button>
        </div>
      ) : (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <input type="date" value={rescheduleDate} onChange={e => setRescheduleDate(e.target.value)}
            min={new Date().toISOString().slice(0, 10)}
            style={{ padding: '6px 10px', fontSize: 13, border: '1.5px solid #E2E8F0', borderRadius: 6, fontFamily: 'inherit' }} />
          <button onClick={onSubmitReschedule} disabled={isPending || !rescheduleDate}
            style={{ fontSize: 12, fontWeight: 600, color: '#1D4ED8', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 6, padding: '6px 12px', cursor: 'pointer', fontFamily: 'inherit' }}>
            {isPending ? 'Rescheduling…' : 'Confirm new date'}
          </button>
          <button onClick={() => setPickingDate(false)} disabled={isPending}
            style={{ fontSize: 12, color: '#374151', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 6, padding: '6px 12px', cursor: 'pointer', fontFamily: 'inherit' }}>
            Back
          </button>
        </div>
      )}
    </div>
  )
}

function FollowUpRow({ f, onStartComplete, onReopen, onDelete, rescheduledTo }: {
  f: FollowUp
  onStartComplete?: () => void
  onReopen?: () => void
  onDelete: () => void
  rescheduledTo?: FollowUp
}) {
  const outcomeCfg = f.outcome ? OUTCOME_CONFIG[f.outcome] : null

  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 8, padding: '8px 0',
      borderBottom: '1px solid #F8FAFC', opacity: f.completed ? 0.75 : 1,
    }}>
      {!f.completed ? (
        <button onClick={onStartComplete} title="Record outcome"
          style={{
            flexShrink: 0, marginTop: 1, width: 18, height: 18, borderRadius: '50%',
            border: `1.5px solid ${f.overdue ? '#DC2626' : '#CBD5E1'}`, background: 'white',
            cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0,
          }} />
      ) : (
        <div style={{ flexShrink: 0, marginTop: 1, width: 18, height: 18, borderRadius: '50%', background: outcomeCfg?.color ?? '#16A34A', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Check size={11} color="white" />
        </div>
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <p style={{ fontSize: 12.5, color: '#0F172A', margin: '0 0 2px', lineHeight: 1.4 }}>
          {f.note}
        </p>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, flexWrap: 'wrap' }}>
          {f.overdue && !f.completed && <AlertTriangle size={10} color="#DC2626" />}
          <span style={{ color: f.overdue && !f.completed ? '#DC2626' : '#94A3B8', fontWeight: f.overdue && !f.completed ? 600 : 400 }}>
            {f.completed ? fmtDate(f.completedAt!) : `Due ${fmtDate(f.dueDate)}`}
          </span>
          {outcomeCfg && (
            <span style={{
              fontSize: 10, fontWeight: 600, padding: '1px 7px', borderRadius: 20,
              background: outcomeCfg.bg, color: outcomeCfg.color, border: `1px solid ${outcomeCfg.border}`,
            }}>
              {outcomeCfg.label}
            </span>
          )}
          {rescheduledTo && (
            <span style={{ color: '#1D4ED8' }}>→ new follow-up due {fmtDate(rescheduledTo.dueDate)}</span>
          )}
        </div>
      </div>
      <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
        {f.completed && (
          <button onClick={onReopen} title="Reopen" style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex', padding: 3 }}>
            <RotateCcw size={12} />
          </button>
        )}
        <button onClick={onDelete} title="Delete" style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#CBD5E1', display: 'flex', padding: 3 }}>
          <Trash2 size={12} />
        </button>
      </div>
    </div>
  )
}
