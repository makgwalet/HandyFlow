// src/pages/customers/FollowUpPanel.tsx
//
// FIX: "no task/follow-up reminder system" gap — shown inside ViewModal
// below ConsentPanel, same collapsible-section convention.

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { CalendarClock, ChevronDown, ChevronUp, Check, RotateCcw, Trash2, Plus, AlertCircle, AlertTriangle } from 'lucide-react'
import { apiClient } from '../../api/client'

interface FollowUp {
  id:          string
  customerId:  string
  dueDate:     string
  note:        string
  assignedTo:  string | null
  completed:   boolean
  completedAt: string | null
  overdue:     boolean
  createdAt:   string
}

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })

export function FollowUpPanel({ customerId }: { customerId: string }) {
  const qc = useQueryClient()
  const [expanded, setExpanded]   = useState(false)
  const [showAdd, setShowAdd]     = useState(false)
  const [dueDate, setDueDate]     = useState('')
  const [note, setNote]           = useState('')
  const [formError, setFormError] = useState('')

  const invalidate = () => qc.invalidateQueries({ queryKey: ['followups', customerId] })

  const { data, isLoading } = useQuery<{ data: FollowUp[] }>({
    queryKey: ['followups', customerId],
    queryFn: () => apiClient.get(`/api/v1/crm/customers/${customerId}/followups`).then(r => r.data),
  })

  const followUps = data?.data ?? []
  const pending    = followUps.filter(f => !f.completed)
  const completed  = followUps.filter(f => f.completed)
  const overdueCount = pending.filter(f => f.overdue).length

  const createMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/v1/crm/customers/${customerId}/followups`, { dueDate, note }),
    onSuccess: () => { invalidate(); setShowAdd(false); setDueDate(''); setNote(''); setFormError('') },
    onError: (e: any) => setFormError(e?.response?.data?.message ?? 'Failed to schedule follow-up'),
  })

  const completeMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/crm/customers/${customerId}/followups/${id}/complete`),
    onSuccess: invalidate,
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

  return (
    <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid #F1F5F9' }}>
      <button
        onClick={() => setExpanded(e => !e)}
        style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          width: '100%', background: 'none', border: 'none', cursor: 'pointer',
          padding: 0, marginBottom: expanded ? 12 : 0,
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
        {expanded ? <ChevronUp size={14} color="#94A3B8" /> : <ChevronDown size={14} color="#94A3B8" />}
      </button>

      {expanded && (
        <div>
          {isLoading && <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>Loading…</p>}

          {!isLoading && pending.length === 0 && !showAdd && (
            <p style={{ fontSize: 12, color: '#64748B', margin: '0 0 10px', lineHeight: 1.5 }}>
              No pending follow-ups. Schedule one to remind yourself (or a teammate) what to do next.
            </p>
          )}

          {pending.map(f => (
            <FollowUpRow key={f.id} f={f}
              onComplete={() => completeMutation.mutate(f.id)}
              onDelete={() => deleteMutation.mutate(f.id)} />
          ))}

          {completed.length > 0 && (
            <details style={{ marginTop: pending.length > 0 ? 8 : 0 }}>
              <summary style={{ fontSize: 11, color: '#94A3B8', cursor: 'pointer', marginBottom: 6 }}>
                {completed.length} completed
              </summary>
              {completed.map(f => (
                <FollowUpRow key={f.id} f={f}
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
      )}
    </div>
  )
}

function FollowUpRow({ f, onComplete, onReopen, onDelete }: {
  f: FollowUp; onComplete?: () => void; onReopen?: () => void; onDelete: () => void
}) {
  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 8, padding: '8px 0',
      borderBottom: '1px solid #F8FAFC', opacity: f.completed ? 0.6 : 1,
    }}>
      {!f.completed ? (
        <button onClick={onComplete} title="Mark done"
          style={{
            flexShrink: 0, marginTop: 1, width: 18, height: 18, borderRadius: '50%',
            border: `1.5px solid ${f.overdue ? '#DC2626' : '#CBD5E1'}`, background: 'white',
            cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0,
          }} />
      ) : (
        <div style={{ flexShrink: 0, marginTop: 1, width: 18, height: 18, borderRadius: '50%', background: '#16A34A', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Check size={11} color="white" />
        </div>
      )}
      <div style={{ flex: 1, minWidth: 0 }}>
        <p style={{ fontSize: 12.5, color: '#0F172A', margin: '0 0 2px', lineHeight: 1.4, textDecoration: f.completed ? 'line-through' : 'none' }}>
          {f.note}
        </p>
        <div style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 11 }}>
          {f.overdue && !f.completed && <AlertTriangle size={10} color="#DC2626" />}
          <span style={{ color: f.overdue && !f.completed ? '#DC2626' : '#94A3B8', fontWeight: f.overdue && !f.completed ? 600 : 400 }}>
            {f.completed ? `Completed ${fmtDate(f.completedAt!)}` : `Due ${fmtDate(f.dueDate)}`}
          </span>
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
