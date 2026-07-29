// src/pages/customers/CommunicationPanel.tsx
//
// FIX: "no email/communication log" gap — shown inside ViewModal below
// FollowUpPanel, same collapsible-section convention.

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { MessageSquare, ChevronDown, ChevronUp, Plus, Trash2, AlertCircle, Phone, Mail, Users, MessageCircle, ArrowDownLeft, ArrowUpRight } from 'lucide-react'
import { apiClient } from '../../api/client'

type CommType      = 'CALL' | 'EMAIL' | 'MEETING' | 'WHATSAPP' | 'SMS' | 'OTHER'
type CommDirection = 'INBOUND' | 'OUTBOUND'

interface Communication {
  id:         string
  customerId: string
  type:       CommType
  direction:  CommDirection
  summary:    string
  occurredAt: string
  loggedBy:   string | null
  createdAt:  string
}

const TYPE_CONFIG: Record<CommType, { label: string; Icon: React.ElementType }> = {
  CALL:     { label: 'Call',     Icon: Phone },
  EMAIL:    { label: 'Email',    Icon: Mail },
  MEETING:  { label: 'Meeting',  Icon: Users },
  WHATSAPP: { label: 'WhatsApp', Icon: MessageCircle },
  SMS:      { label: 'SMS',      Icon: MessageSquare },
  OTHER:    { label: 'Other',    Icon: MessageSquare },
}

const fmtDateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })

// datetime-local wants "YYYY-MM-DDTHH:mm" in local time, no timezone suffix
const nowForInput = () => {
  const d = new Date()
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
  return d.toISOString().slice(0, 16)
}

export function CommunicationPanel({ customerId }: { customerId: string }) {
  const qc = useQueryClient()
  const [expanded, setExpanded] = useState(false)
  const [showAdd, setShowAdd]   = useState(false)
  const [type, setType]         = useState<CommType>('CALL')
  const [direction, setDirection] = useState<CommDirection>('OUTBOUND')
  const [summary, setSummary]   = useState('')
  const [occurredAt, setOccurredAt] = useState(nowForInput())
  const [formError, setFormError] = useState('')

  const invalidate = () => qc.invalidateQueries({ queryKey: ['communications', customerId] })

  const { data, isLoading } = useQuery<{ data: Communication[] }>({
    queryKey: ['communications', customerId],
    queryFn: () => apiClient.get(`/api/v1/crm/customers/${customerId}/communications`).then(r => r.data),
  })

  const communications = data?.data ?? []

  const logMutation = useMutation({
    mutationFn: () =>
      apiClient.post(`/api/v1/crm/customers/${customerId}/communications`, {
        type, direction, summary, occurredAt: new Date(occurredAt).toISOString(),
      }),
    onSuccess: () => {
      invalidate(); setShowAdd(false); setSummary(''); setOccurredAt(nowForInput()); setFormError('')
    },
    onError: (e: any) => setFormError(e?.response?.data?.message ?? 'Failed to log communication'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/customers/${customerId}/communications/${id}`),
    onSuccess: invalidate,
  })

  const submitLog = () => {
    if (!summary.trim()) { setFormError('A summary is required'); return }
    setFormError('')
    logMutation.mutate()
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
          <MessageSquare size={13} color="#94A3B8" />
          <span style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            Communications
          </span>
          {communications.length > 0 && (
            <span style={{
              fontSize: 10, fontWeight: 600, padding: '1px 7px', borderRadius: 20,
              background: '#F8FAFC', color: '#64748B', border: '1px solid #E2E8F0',
            }}>
              {communications.length}
            </span>
          )}
        </div>
        {expanded ? <ChevronUp size={14} color="#94A3B8" /> : <ChevronDown size={14} color="#94A3B8" />}
      </button>

      {expanded && (
        <div>
          {isLoading && <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>Loading…</p>}

          {!isLoading && communications.length === 0 && !showAdd && (
            <p style={{ fontSize: 12, color: '#64748B', margin: '0 0 10px', lineHeight: 1.5 }}>
              No communications logged yet. Log calls, emails, and meetings here to keep
              a record separate from the system activity timeline.
            </p>
          )}

          {communications.map(c => (
            <CommRow key={c.id} c={c} onDelete={() => deleteMutation.mutate(c.id)} />
          ))}

          {!showAdd ? (
            <button
              onClick={() => { setShowAdd(true); setFormError('') }}
              style={{
                marginTop: 10, display: 'flex', alignItems: 'center', gap: 5,
                fontSize: 12, color: '#1D4ED8', background: '#EFF6FF',
                border: '1px solid #BFDBFE', borderRadius: 6, padding: '5px 10px',
                cursor: 'pointer',
              }}>
              <Plus size={12} /> Log communication
            </button>
          ) : (
            <div style={{ marginTop: 10 }}>
              <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: 11, fontWeight: 600, color: '#374151', display: 'block', marginBottom: 3 }}>Type</label>
                  <select value={type} onChange={e => setType(e.target.value as CommType)}
                    style={{ width: '100%', padding: '6px 10px', fontSize: 13, border: '1.5px solid #E2E8F0', borderRadius: 6, fontFamily: 'inherit', background: 'white' }}>
                    {Object.entries(TYPE_CONFIG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                  </select>
                </div>
                <div style={{ flex: 1 }}>
                  <label style={{ fontSize: 11, fontWeight: 600, color: '#374151', display: 'block', marginBottom: 3 }}>Direction</label>
                  <div style={{ display: 'flex', gap: 4 }}>
                    {(['OUTBOUND', 'INBOUND'] as const).map(d => (
                      <button key={d} onClick={() => setDirection(d)}
                        style={{
                          flex: 1, padding: '6px 8px', fontSize: 12, borderRadius: 6, cursor: 'pointer', fontFamily: 'inherit',
                          border: direction === d ? '1.5px solid #1D4ED8' : '1.5px solid #E2E8F0',
                          background: direction === d ? '#EFF6FF' : 'white',
                          color: direction === d ? '#1D4ED8' : '#64748B',
                        }}>
                        {d === 'OUTBOUND' ? 'Out' : 'In'}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              <label style={{ fontSize: 11, fontWeight: 600, color: '#374151', display: 'block', marginBottom: 3 }}>When</label>
              <input type="datetime-local" value={occurredAt} onChange={e => setOccurredAt(e.target.value)}
                max={nowForInput()}
                style={{ padding: '6px 10px', fontSize: 13, border: '1.5px solid #E2E8F0', borderRadius: 6, fontFamily: 'inherit', marginBottom: 8, width: '100%', boxSizing: 'border-box' }} />
              <label style={{ fontSize: 11, fontWeight: 600, color: '#374151', display: 'block', marginBottom: 3 }}>Summary</label>
              <textarea value={summary} onChange={e => setSummary(e.target.value)}
                placeholder="e.g. Discussed renewal pricing, sending updated quote"
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
                <button onClick={submitLog} disabled={logMutation.isPending}
                  style={{ fontSize: 12, color: '#1D4ED8', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 6, padding: '5px 12px', cursor: 'pointer', fontFamily: 'inherit' }}>
                  {logMutation.isPending ? 'Logging…' : 'Log it'}
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

function CommRow({ c, onDelete }: { c: Communication; onDelete: () => void }) {
  const { label, Icon } = TYPE_CONFIG[c.type]
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, padding: '8px 0', borderBottom: '1px solid #F8FAFC' }}>
      <div style={{ flexShrink: 0, marginTop: 1, width: 22, height: 22, borderRadius: 6, background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <Icon size={12} color="#64748B" />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 2 }}>
          <span style={{ fontSize: 11.5, fontWeight: 700, color: '#374151' }}>{label}</span>
          {c.direction === 'OUTBOUND'
            ? <ArrowUpRight size={11} color="#94A3B8" titleAccess="Outbound" />
            : <ArrowDownLeft size={11} color="#94A3B8" titleAccess="Inbound" />}
          <span style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDateTime(c.occurredAt)}</span>
        </div>
        <p style={{ fontSize: 12.5, color: '#0F172A', margin: 0, lineHeight: 1.4 }}>{c.summary}</p>
      </div>
      <button onClick={onDelete} title="Delete" style={{ flexShrink: 0, background: 'none', border: 'none', cursor: 'pointer', color: '#CBD5E1', display: 'flex', padding: 3 }}>
        <Trash2 size={12} />
      </button>
    </div>
  )
}
