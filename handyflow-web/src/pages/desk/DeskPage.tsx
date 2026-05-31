import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, Headphones, MessageSquare, X, ChevronRight, AlertCircle } from 'lucide-react'

interface Ticket {
  id: string; ticketNumber: string; subject: string; description: string
  channel: string; priority: string; status: string
  requesterName: string | null; requesterEmail: string | null
  assigneeId: string | null; slaBreached: boolean
  createdAt: string; updatedAt: string
}

const PRIORITY_CONFIG: Record<string, { color: string; bg: string }> = {
  URGENT: { color: '#DC2626', bg: '#FEF2F2' },
  HIGH:   { color: '#D97706', bg: '#FFFBEB' },
  NORMAL: { color: '#1D4ED8', bg: '#EFF6FF' },
  LOW:    { color: '#64748B', bg: '#F8FAFC' },
}
const STATUS_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  OPEN:        { color: '#DC2626', bg: '#FEF2F2', label: 'Open'        },
  IN_PROGRESS: { color: '#D97706', bg: '#FFFBEB', label: 'In Progress' },
  WAITING:     { color: '#7C3AED', bg: '#F3E8FF', label: 'Waiting'     },
  RESOLVED:    { color: '#166534', bg: '#DCFCE7', label: 'Resolved'    },
  CLOSED:      { color: '#94A3B8', bg: '#F8FAFC', label: 'Closed'      },
}

export function DeskPage() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [selected, setSelected]     = useState<Ticket | null>(null)
  const [comments, setComments]     = useState<any[]>([])
  const [comment, setComment]       = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [error, setError] = useState('')
  const [form, setForm] = useState({ subject: '', description: '', priority: 'NORMAL', channel: 'HELPDESK', requesterName: '', requesterEmail: '' })
  const f = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: page, isLoading } = useQuery({
    queryKey: ['desk-tickets', statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: '50' })
      if (statusFilter) params.set('status', statusFilter)
      const r = await apiClient.get(`/api/v1/desk/tickets?${params}`)
      return r.data
    },
  })

  const { data: summary } = useQuery({
    queryKey: ['desk-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/desk/summary'); return r.data },
  })

  const createTicket = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/desk/tickets', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['desk-tickets'] }); qc.invalidateQueries({ queryKey: ['desk-summary'] }); setShowCreate(false); setForm({ subject: '', description: '', priority: 'NORMAL', channel: 'HELPDESK', requesterName: '', requesterEmail: '' }) },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create ticket'),
  })

  const updateStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      apiClient.patch(`/api/v1/desk/tickets/${id}/status`, { status }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['desk-tickets'] })
      if (selected?.id === vars.id) setSelected(s => s ? { ...s, status: vars.status } : null)
    },
  })

  const addComment = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/desk/tickets/${id}/comments`, body),
    onSuccess: async () => {
      if (selected) {
        const r = await apiClient.get(`/api/v1/desk/tickets/${selected.id}/comments`)
        setComments(r.data || [])
      }
      setComment('')
    },
  })

  const loadTicket = async (ticket: Ticket) => {
    setSelected(ticket)
    const r = await apiClient.get(`/api/v1/desk/tickets/${ticket.id}/comments`)
    setComments(r.data || [])
  }

  const tickets: Ticket[] = page?.content || []
  const fmtDate = (d: string) => new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

  const stats = [
    { label: 'Open',        value: summary?.openTickets       ?? 0, color: '#DC2626' },
    { label: 'In Progress', value: summary?.inProgressTickets ?? 0, color: '#D97706' },
    { label: 'Resolved',    value: summary?.resolvedToday     ?? 0, color: '#166534' },
    { label: 'SLA Breached',value: summary?.slaBreached       ?? 0, color: '#7C3AED' },
  ]

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0F172A', margin: '0 0 4px' }}>Desk Support</h1>
        <p style={{ fontSize: 14, color: '#64748B', margin: 0 }}>Helpdesk tickets with SLA tracking</p>
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, minWidth: 120, background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: 26, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 10 }}>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {['', 'OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED'].map(s => (
              <button key={s} onClick={() => setStatusFilter(s)} style={filterBtn(statusFilter === s)}>
                {s ? (STATUS_CONFIG[s]?.label || s) : 'All'}
              </button>
            ))}
          </div>
          <button onClick={() => { setShowCreate(true); setError('') }} style={btnPrimary}>
            <Plus size={15} /> New Ticket
          </button>
        </div>

        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading tickets...</div>
        ) : tickets.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
            <Headphones size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
            <div style={{ fontWeight: 600, color: '#475569' }}>No tickets found</div>
          </div>
        ) : (
          <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: '#F8FAFC' }}>
                  {['Ticket', 'Requester', 'Priority', 'Status', 'Updated', ''].map(h => (
                    <th key={h} style={th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {tickets.map((t, i) => {
                  const pCfg = PRIORITY_CONFIG[t.priority] || PRIORITY_CONFIG.NORMAL
                  const sCfg = STATUS_CONFIG[t.status] || STATUS_CONFIG.OPEN
                  return (
                    <tr key={t.id} onClick={() => loadTicket(t)}
                      style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA', cursor: 'pointer' }}>
                      <td style={td}>
                        <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A', display: 'flex', alignItems: 'center', gap: 6 }}>
                          {t.slaBreached && <AlertCircle size={13} color="#DC2626" />}
                          {t.subject}
                        </div>
                        <div style={{ fontSize: 11, color: '#94A3B8' }}>#{t.ticketNumber} · {t.channel}</div>
                      </td>
                      <td style={td}><span style={{ fontSize: 13, color: '#475569' }}>{t.requesterName || t.requesterEmail || '—'}</span></td>
                      <td style={td}><span style={{ background: pCfg.bg, color: pCfg.color, padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{t.priority}</span></td>
                      <td style={td}><span style={{ background: sCfg.bg, color: sCfg.color, padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{sCfg.label}</span></td>
                      <td style={td}><span style={{ fontSize: 12, color: '#94A3B8' }}>{fmtDate(t.updatedAt)}</span></td>
                      <td style={td}><ChevronRight size={16} color="#94A3B8" /></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Ticket Detail */}
      {selected && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 620, maxHeight: '85vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <div>
                <h3 style={{ margin: '0 0 4px', fontSize: 17, fontWeight: 700, color: '#0F172A' }}>{selected.subject}</h3>
                <div style={{ fontSize: 12, color: '#94A3B8' }}>#{selected.ticketNumber} · {selected.requesterName || selected.requesterEmail || 'Unknown'}</div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>

            <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
              {['OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED'].map(s => {
                const cfg = STATUS_CONFIG[s]
                return (
                  <button key={s} onClick={() => updateStatus.mutate({ id: selected.id, status: s })}
                    style={{ padding: '5px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: '1.5px solid',
                      borderColor: selected.status === s ? cfg.color : '#E2E8F0',
                      background: selected.status === s ? cfg.bg : '#fff',
                      color: selected.status === s ? cfg.color : '#64748B',
                    }}>
                    {cfg.label}
                  </button>
                )
              })}
            </div>

            <div style={{ padding: '12px 16px', background: '#F8FAFC', borderRadius: 10, marginBottom: 16, fontSize: 14, color: '#374151' }}>
              {selected.description}
            </div>

            <div style={{ marginBottom: 12 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 10 }}>
                Comments ({comments.length})
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 12 }}>
                {comments.map((c: any) => (
                  <div key={c.id} style={{ padding: '10px 14px', background: c.isInternal ? '#FFFBEB' : '#F0F9FF', borderRadius: 8, border: `1px solid ${c.isInternal ? '#FDE68A' : '#BAE6FD'}` }}>
                    <div style={{ fontSize: 11, color: '#94A3B8', marginBottom: 4 }}>
                      {c.authorName} · {fmtDate(c.createdAt)} {c.isInternal && <span style={{ color: '#D97706', fontWeight: 600 }}> · Internal</span>}
                    </div>
                    <div style={{ fontSize: 13, color: '#374151' }}>{c.body}</div>
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <textarea value={comment} onChange={e => setComment(e.target.value)} rows={2} placeholder="Add a comment..."
                  style={{ flex: 1, padding: '9px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 14, resize: 'none' as const }} />
                <button onClick={() => addComment.mutate({ id: selected.id, body: { body: comment, isInternal: false } })}
                  disabled={!comment || addComment.isPending} style={{ ...btnPrimary, alignSelf: 'flex-end' }}>
                  <MessageSquare size={14} /> Send
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Create Ticket */}
      {showCreate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 520, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>New Support Ticket</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <F label="Subject *"><input value={form.subject} onChange={e => f('subject', e.target.value)} placeholder="Describe the issue" style={inp} /></F>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <F label="Priority">
                  <select value={form.priority} onChange={e => f('priority', e.target.value)} style={inp}>
                    {['URGENT','HIGH','NORMAL','LOW'].map(p => <option key={p}>{p}</option>)}
                  </select>
                </F>
                <F label="Channel">
                  <select value={form.channel} onChange={e => f('channel', e.target.value)} style={inp}>
                    <option value="HELPDESK">Helpdesk</option>
                    <option value="INTERNAL">Internal</option>
                  </select>
                </F>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <F label="Requester Name"><input value={form.requesterName} onChange={e => f('requesterName', e.target.value)} placeholder="John Smith" style={inp} /></F>
                <F label="Requester Email"><input value={form.requesterEmail} onChange={e => f('requesterEmail', e.target.value)} placeholder="john@example.com" style={inp} /></F>
              </div>
              <F label="Description *">
                <textarea value={form.description} onChange={e => f('description', e.target.value)} rows={3} placeholder="Detailed description of the issue..." style={{ ...inp, resize: 'vertical' as const }} />
              </F>
            </div>
            {error && <div style={{ marginTop: 10, color: '#DC2626', fontSize: 13 }}>{error}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createTicket.mutate({ subject: form.subject, description: form.description, priority: form.priority, channel: form.channel, requesterName: form.requesterName || null, requesterEmail: form.requesterEmail || null })}
                disabled={!form.subject || !form.description || createTicket.isPending} style={btnPrimary}>
                {createTicket.isPending ? 'Creating...' : 'Create Ticket'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function F({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 5 }}>{label}</label>{children}</div>
}

const filterBtn = (active: boolean): React.CSSProperties => ({ padding: '6px 12px', borderRadius: 6, fontSize: 12, cursor: 'pointer', border: active ? '1px solid #0D9488' : '1px solid #E2E8F0', background: active ? '#F0FDF4' : '#fff', color: active ? '#0D9488' : '#64748B', fontWeight: active ? 600 : 400 })
const btnPrimary: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 500, cursor: 'pointer' }
const btnCancel: React.CSSProperties  = { padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer', color: '#374151' }
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff' }
const th: React.CSSProperties = { padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 600, color: '#64748B', letterSpacing: '0.05em', borderBottom: '1px solid #E2E8F0' }
const td: React.CSSProperties = { padding: '12px 16px', fontSize: 13, borderBottom: '1px solid #F1F5F9' }
