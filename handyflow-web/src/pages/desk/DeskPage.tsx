// src/pages/desk/DeskPage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, MessageSquare, AlertTriangle, CheckCircle,
  Clock, Search, ChevronRight, User, Tag, Calendar,
  Shield, Link2, Send, Lock, Inbox, BarChart2,
  RefreshCw, UserCheck, Filter, Download, Edit3,
} from 'lucide-react'

// ── Types ──────────────────────────────────────────────────────────────────
interface Ticket {
  id: string; ticketNumber: string; channel: string
  requesterName: string | null; requesterEmail: string | null
  requesterPhone: string | null; customerId: string | null
  subject: string; description: string
  categoryId: string | null; categoryName: string | null
  priority: string; status: string
  assignedTo: string | null; assignedToName: string | null
  slaBreached: boolean; dueAt: string | null
  firstResponseAt: string | null; resolvedAt: string | null
  closedAt: string | null; publicToken: string | null
  notes: string | null; comments: Comment[]
  createdAt: string; updatedAt: string
}
interface Comment {
  id: string; authorName: string; authorType: string
  internal: boolean; body: string; createdAt: string
}
interface Category { id: string; name: string; color: string | null }
interface SlaPolicy { id: string; priority: string; firstResponseHours: number; resolutionHours: number }
interface Summary {
  openCount: number; inProgressCount: number; waitingCount: number
  resolvedCount: number; urgentOpen: number; slaBreachedCount: number
  helpdeskCount: number; internalCount: number
}

// ── Constants ──────────────────────────────────────────────────────────────
const PRIORITY: Record<string, { color: string; bg: string; border: string; dot: string }> = {
  URGENT: { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', dot: '#EF4444' },
  HIGH:   { color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', dot: '#F59E0B' },
  NORMAL: { color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE', dot: '#60A5FA' },
  LOW:    { color: '#64748B', bg: '#F8FAFC', border: '#E2E8F0', dot: '#CBD5E1' },
}
const STATUS: Record<string, { color: string; bg: string; border: string; dot: string; label: string; action: string }> = {
  OPEN:                   { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', dot: '#EF4444', label: 'Open',             action: 'REOPEN' },
  IN_PROGRESS:            { color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', dot: '#F59E0B', label: 'In Progress',      action: 'START' },
  WAITING_ON_CUSTOMER:    { color: '#7C3AED', bg: '#F5F3FF', border: '#DDD6FE', dot: '#A78BFA', label: 'Waiting – Client',  action: 'WAIT_CUSTOMER' },
  WAITING_ON_THIRD_PARTY: { color: '#0369A1', bg: '#E0F2FE', border: '#BAE6FD', dot: '#38BDF8', label: 'Waiting – 3rd Party',action: 'WAIT_THIRD_PARTY' },
  RESOLVED:               { color: '#166534', bg: '#DCFCE7', border: '#86EFAC', dot: '#22C55E', label: 'Resolved',         action: 'RESOLVE' },
  CLOSED:                 { color: '#94A3B8', bg: '#F8FAFC', border: '#E2E8F0', dot: '#CBD5E1', label: 'Closed',           action: 'CLOSE' },
}
const STATUS_ACTIONS: { action: string; label: string; from: string[] }[] = [
  { action: 'START',            label: 'Start working',    from: ['OPEN','WAITING_ON_CUSTOMER','WAITING_ON_THIRD_PARTY'] },
  { action: 'WAIT_CUSTOMER',    label: 'Waiting on client',from: ['OPEN','IN_PROGRESS'] },
  { action: 'WAIT_THIRD_PARTY', label: 'Waiting on 3rd party',from: ['OPEN','IN_PROGRESS'] },
  { action: 'RESOLVE',          label: 'Mark resolved',    from: ['OPEN','IN_PROGRESS','WAITING_ON_CUSTOMER','WAITING_ON_THIRD_PARTY'] },
  { action: 'CLOSE',            label: 'Close ticket',     from: ['RESOLVED'] },
  { action: 'REOPEN',           label: 'Re-open',          from: ['RESOLVED','CLOSED'] },
]

// ── Helpers ────────────────────────────────────────────────────────────────
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }
const btnP: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const btnS: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, padding: '9px 14px', border: '1.5px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 13, cursor: 'pointer', color: '#374151', fontWeight: 500 }

const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
const slaColor = (ticket: Ticket) => {
  if (ticket.slaBreached) return '#DC2626'
  if (!ticket.dueAt) return '#64748B'
  const hrs = (new Date(ticket.dueAt).getTime() - Date.now()) / 3_600_000
  return hrs < 4 ? '#D97706' : '#64748B'
}
const slaLabel = (ticket: Ticket) => {
  if (ticket.slaBreached) return 'SLA breached'
  if (!ticket.dueAt) return '—'
  const hrs = Math.round((new Date(ticket.dueAt).getTime() - Date.now()) / 3_600_000)
  if (hrs < 0) return `${Math.abs(hrs)}h overdue`
  if (hrs < 24) return `${hrs}h left`
  return `${Math.round(hrs / 24)}d left`
}

// ── Ticket Detail Slide-over ───────────────────────────────────────────────
function TicketDetail({ ticket: initial, onClose, onUpdated }: {
  ticket: Ticket; onClose: () => void; onUpdated: (t: Ticket) => void
}) {
  const qc = useQueryClient()
  const [ticket, setTicket] = useState<Ticket>(initial)
  const [tab, setTab]       = useState<'thread'|'details'|'notes'>('thread')
  const [commentText, setCommentText] = useState('')
  const [isInternal, setIsInternal]   = useState(false)
  const [showCopyToast, setShowCopyToast] = useState(false)
  const [error, setError] = useState('')

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['desk-tickets'] })
    qc.invalidateQueries({ queryKey: ['desk-summary'] })
  }

  // Load full ticket with comments on mount
  const { data: full } = useQuery<Ticket>({
    queryKey: ['desk-ticket', ticket.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/desk/tickets/${ticket.id}`)
      const t = r.data?.data ?? r.data
      setTicket(t)
      return t
    },
  })

  const doAction = useMutation({
    mutationFn: (action: string) => apiClient.post(`/api/v1/desk/tickets/${ticket.id}/action/${action}`),
    onSuccess: (r: any) => {
      const t = r.data?.data ?? r.data; setTicket(t); onUpdated(t); invalidate()
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Action failed'),
  })

  const addComment = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/desk/tickets/${ticket.id}/comments`, {
      body: commentText, internal: isInternal,
    }),
    onSuccess: (r: any) => {
      const t = r.data?.data ?? r.data; setTicket(t); onUpdated(t)
      setCommentText(''); invalidate()
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to add comment'),
  })

  const sc  = STATUS[ticket.status] ?? STATUS.OPEN
  const pc  = PRIORITY[ticket.priority] ?? PRIORITY.NORMAL
  const availableActions = STATUS_ACTIONS.filter(a => a.from.includes(ticket.status))
  const comments = (full?.comments ?? ticket.comments ?? [])

  const copyPublicLink = () => {
    if (ticket.publicToken) {
      navigator.clipboard.writeText(`${window.location.origin}/support/${ticket.publicToken}`)
      setShowCopyToast(true)
      setTimeout(() => setShowCopyToast(false), 3000)
    }
  }

  const exportThread = () => {
    const lines = comments.map(c =>
      `[${fmtDT(c.createdAt)}] ${c.authorName} (${c.authorType}${c.internal ? ', internal' : ''}):\n${c.body}`
    ).join('\n\n---\n\n')
    const text = `Ticket: ${ticket.ticketNumber}\nSubject: ${ticket.subject}\nStatus: ${ticket.status}\nRequester: ${ticket.requesterName}\n\n${lines}`
    const a = document.createElement('a')
    a.href = 'data:text/plain;charset=utf-8,' + encodeURIComponent(text)
    a.download = `${ticket.ticketNumber}.txt`; a.click()
  }

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'stretch', justifyContent: 'flex-end', zIndex: 1000 }}>
      <div style={{ background: '#fff', width: 640, height: '100%', overflowY: 'auto', boxShadow: '-8px 0 40px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column' }}>

        {/* Header */}
        <div style={{ padding: '20px 24px 16px', borderBottom: '1px solid #F1F5F9', flexShrink: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
            <div style={{ flex: 1, marginRight: 12 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
                <span style={{ fontSize: 12, color: '#94A3B8', fontWeight: 600 }}>#{ticket.ticketNumber}</span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                  <span style={{ width: 5, height: 5, borderRadius: '50%', background: sc.dot }} />{sc.label}
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: pc.bg, color: pc.color, border: `1px solid ${pc.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                  {ticket.priority}
                </span>
                {ticket.slaBreached && (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#FEF2F2', color: '#DC2626', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                    <AlertTriangle size={10} /> SLA
                  </span>
                )}
                {ticket.channel === 'INTERNAL' && (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#F5F3FF', color: '#7C3AED', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                    <Shield size={10} /> Internal
                  </span>
                )}
              </div>
              <h2 style={{ margin: 0, fontSize: 17, fontWeight: 800, color: '#0F172A', lineHeight: 1.3 }}>{ticket.subject}</h2>
            </div>
            <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
              {ticket.publicToken && (
                <button onClick={copyPublicLink} title="Copy public tracking link"
                  style={{ ...btnS, padding: '6px 10px', fontSize: 12, position: 'relative' as const }}>
                  <Link2 size={13} />
                  {showCopyToast && <span style={{ position: 'absolute' as const, bottom: '110%', left: '50%', transform: 'translateX(-50%)', background: '#0F172A', color: '#fff', padding: '4px 8px', borderRadius: 6, fontSize: 11, whiteSpace: 'nowrap' as const }}>Copied!</span>}
                </button>
              )}
              <button onClick={exportThread} style={{ ...btnS, padding: '6px 10px' }}><Download size={13} /></button>
              <button onClick={onClose} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#64748B', flexShrink: 0 }}>
                <X size={14} />
              </button>
            </div>
          </div>

          {/* Requester + assignee strip */}
          <div style={{ display: 'flex', gap: 20, fontSize: 12, color: '#64748B', flexWrap: 'wrap' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <User size={12} />{ticket.requesterName ?? ticket.requesterEmail ?? 'Unknown'}
              {ticket.requesterEmail && <span style={{ color: '#94A3B8' }}>({ticket.requesterEmail})</span>}
            </span>
            {ticket.assignedToName && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                <UserCheck size={12} />{ticket.assignedToName}
              </span>
            )}
            {ticket.dueAt && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 5, color: slaColor(ticket), fontWeight: 600 }}>
                <Clock size={12} />{slaLabel(ticket)}
              </span>
            )}
            {ticket.categoryName && (
              <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                <Tag size={12} />{ticket.categoryName}
              </span>
            )}
          </div>

          {/* Action buttons */}
          {availableActions.length > 0 && (
            <div style={{ display: 'flex', gap: 6, marginTop: 12, flexWrap: 'wrap' }}>
              {availableActions.map(a => {
                const targetStatus = Object.entries(STATUS).find(([, v]) => v.action === a.action)
                const cfg = targetStatus ? STATUS[targetStatus[0]] : null
                return (
                  <button key={a.action} onClick={() => doAction.mutate(a.action)} disabled={doAction.isPending}
                    style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: cfg?.bg ?? '#F8FAFC', color: cfg?.color ?? '#64748B', border: `1px solid ${cfg?.border ?? '#E2E8F0'}`, borderRadius: 7, fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                    <ChevronRight size={11} />{a.label}
                  </button>
                )
              })}
            </div>
          )}

          {/* Tabs */}
          <div style={{ display: 'flex', gap: 0, marginTop: 14 }}>
            {(['thread','details','notes'] as const).map(t => (
              <button key={t} onClick={() => setTab(t)}
                style={{ padding: '8px 16px', fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: tab === t ? '#1B3A6B' : '#9CA3AF', borderBottom: `2px solid ${tab === t ? '#1B3A6B' : 'transparent'}`, marginBottom: -1, textTransform: 'capitalize' }}>
                {t === 'thread' ? `Thread (${comments.filter(c => !c.internal).length})` : t === 'notes' ? `Notes (${comments.filter(c => c.internal).length})` : 'Details'}
              </button>
            ))}
          </div>
        </div>

        {/* Body */}
        <div style={{ flex: 1, overflowY: 'auto', padding: '20px 24px' }}>
          {error && <div style={{ marginBottom: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

          {tab === 'thread' && (
            <div>
              {/* Original message */}
              <div style={{ marginBottom: 16, padding: '14px 16px', background: '#F0F9FF', borderRadius: 10, border: '1px solid #BAE6FD' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#1B3A6B', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <span style={{ fontSize: 11, color: '#fff', fontWeight: 700 }}>{(ticket.requesterName ?? 'C').charAt(0).toUpperCase()}</span>
                  </div>
                  <div>
                    <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>{ticket.requesterName ?? 'Customer'}</span>
                    <span style={{ fontSize: 11, color: '#94A3B8', marginLeft: 8 }}>{fmtDT(ticket.createdAt)}</span>
                  </div>
                </div>
                <div style={{ fontSize: 14, color: '#374151', lineHeight: 1.7, whiteSpace: 'pre-wrap' as const }}>{ticket.description}</div>
              </div>

              {/* Comment thread — non-internal only */}
              {comments.filter(c => !c.internal && c.authorType !== 'SYSTEM').map(c => (
                <div key={c.id} style={{ marginBottom: 12, display: 'flex', gap: 10 }}>
                  <div style={{ width: 28, height: 28, borderRadius: '50%', background: c.authorType === 'CUSTOMER' ? '#0D9488' : '#1B3A6B', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <span style={{ fontSize: 11, color: '#fff', fontWeight: 700 }}>{c.authorName.charAt(0).toUpperCase()}</span>
                  </div>
                  <div style={{ flex: 1, background: c.authorType === 'CUSTOMER' ? '#F0FDF9' : '#F8FAFC', borderRadius: 10, padding: '10px 14px', border: '1px solid #E2E8F0' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>{c.authorName}</span>
                      <span style={{ fontSize: 11, color: '#94A3B8' }}>{c.authorType === 'CUSTOMER' ? 'Customer' : 'Support'} · {fmtDT(c.createdAt)}</span>
                    </div>
                    <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.6, whiteSpace: 'pre-wrap' as const }}>{c.body}</div>
                  </div>
                </div>
              ))}

              {/* System events */}
              {comments.filter(c => c.authorType === 'SYSTEM').map(c => (
                <div key={c.id} style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div style={{ height: 1, flex: 1, background: '#F1F5F9' }} />
                  <span style={{ fontSize: 11, color: '#94A3B8', whiteSpace: 'nowrap' as const }}>{c.body} · {fmtDT(c.createdAt)}</span>
                  <div style={{ height: 1, flex: 1, background: '#F1F5F9' }} />
                </div>
              ))}

              {/* Reply box */}
              {!['CLOSED'].includes(ticket.status) && (
                <div style={{ marginTop: 16, border: '1.5px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                  <textarea
                    value={commentText} onChange={e => setCommentText(e.target.value)}
                    rows={4} placeholder="Reply to requester..."
                    style={{ width: '100%', padding: '12px 14px', border: 'none', fontSize: 14, resize: 'none' as const, fontFamily: 'inherit', outline: 'none', boxSizing: 'border-box' as const }}
                  />
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 12px', background: '#F8FAFC', borderTop: '1px solid #E2E8F0' }}>
                    <span style={{ fontSize: 12, color: '#94A3B8' }}>Sends email notification to requester</span>
                    <button onClick={() => addComment.mutate()} disabled={!commentText.trim() || addComment.isPending}
                      style={{ ...btnP, padding: '7px 14px', fontSize: 13, opacity: !commentText.trim() ? 0.5 : 1 }}>
                      {addComment.isPending ? 'Sending...' : <><Send size={12} /> Send reply</>}
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}

          {tab === 'notes' && (
            <div>
              <div style={{ marginBottom: 14, padding: '10px 14px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 9, fontSize: 12, color: '#92400E', display: 'flex', alignItems: 'center', gap: 7 }}>
                <Lock size={12} /> Internal notes are only visible to your team — never shown to the customer.
              </div>

              {comments.filter(c => c.internal).map(c => (
                <div key={c.id} style={{ marginBottom: 12, padding: '12px 14px', background: '#FFFBEB', borderRadius: 10, border: '1px solid #FDE68A' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>{c.authorName}</span>
                    <span style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDT(c.createdAt)}</span>
                  </div>
                  <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.6 }}>{c.body}</div>
                </div>
              ))}

              {comments.filter(c => c.internal).length === 0 && (
                <div style={{ textAlign: 'center', padding: '30px', color: '#94A3B8', fontSize: 13 }}>No internal notes yet.</div>
              )}

              {/* Internal note input */}
              <div style={{ marginTop: 14, border: '1.5px solid #FDE68A', borderRadius: 10, overflow: 'hidden', background: '#FFFBEB' }}>
                <textarea
                  value={isInternal ? commentText : ''}
                  onChange={e => { setCommentText(e.target.value); setIsInternal(true) }}
                  rows={3} placeholder="Add internal note (staff only)..."
                  style={{ width: '100%', padding: '12px 14px', border: 'none', fontSize: 14, resize: 'none' as const, fontFamily: 'inherit', outline: 'none', background: 'transparent', boxSizing: 'border-box' as const }}
                />
                <div style={{ display: 'flex', justifyContent: 'flex-end', padding: '8px 12px', borderTop: '1px solid #FDE68A' }}>
                  <button onClick={() => { setIsInternal(true); addComment.mutate() }}
                    disabled={!commentText.trim() || addComment.isPending}
                    style={{ ...btnP, padding: '7px 14px', fontSize: 13, background: '#D97706', opacity: !commentText.trim() ? 0.5 : 1 }}>
                    <Lock size={12} /> Save note
                  </button>
                </div>
              </div>
            </div>
          )}

          {tab === 'details' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
              {[
                ['Requester',     ticket.requesterName ?? '—'],
                ['Email',         ticket.requesterEmail ?? '—'],
                ['Phone',         ticket.requesterPhone ?? '—'],
                ['Channel',       ticket.channel],
                ['Category',      ticket.categoryName ?? '—'],
                ['Assigned to',   ticket.assignedToName ?? 'Unassigned'],
                ['SLA due',       ticket.dueAt ? fmtDT(ticket.dueAt) : '—'],
                ['First response',ticket.firstResponseAt ? fmtDT(ticket.firstResponseAt) : 'Not yet'],
                ['Resolved',      ticket.resolvedAt ? fmtDT(ticket.resolvedAt) : '—'],
                ['Closed',        ticket.closedAt ? fmtDT(ticket.closedAt) : '—'],
                ['Created',       fmtDT(ticket.createdAt)],
                ['Updated',       fmtDT(ticket.updatedAt)],
              ].map(([k, v]) => (
                <div key={k} style={{ display: 'flex', justifyContent: 'space-between', padding: '9px 0', borderBottom: '1px solid #F1F5F9', fontSize: 13 }}>
                  <span style={{ color: '#94A3B8', fontWeight: 600, minWidth: 130 }}>{k}</span>
                  <span style={{ color: '#374151', fontWeight: 500, textAlign: 'right' as const }}>{v}</span>
                </div>
              ))}
              {ticket.notes && (
                <div style={{ marginTop: 16, padding: '12px 14px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 9, fontSize: 13, color: '#374151' }}>
                  <div style={{ fontSize: 10, fontWeight: 700, color: '#D97706', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.06em' }}>Notes</div>
                  {ticket.notes}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ── Create Ticket Modal ────────────────────────────────────────────────────
function CreateTicketModal({ categories, onClose, onSaved }: {
  categories: Category[]; onClose: () => void; onSaved: () => void
}) {
  const [form, setForm] = useState({
    subject: '', description: '', priority: 'NORMAL', channel: 'HELPDESK',
    requesterName: '', requesterEmail: '', requesterPhone: '',
    categoryId: '', notes: '',
  })
  const [error, setError] = useState('')
  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const create = useMutation({
    mutationFn: () => apiClient.post('/api/v1/desk/tickets', {
      subject: form.subject, description: form.description,
      priority: form.priority, channel: form.channel,
      requesterName: form.requesterName, requesterEmail: form.requesterEmail || null,
      requesterPhone: form.requesterPhone || null,
      categoryId: form.categoryId || null, notes: form.notes || null,
    }),
    onSuccess: () => { onSaved(); onClose() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create ticket'),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 640, maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 22 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 800 }}>New Support Ticket</h3>
            <p style={{ margin: '3px 0 0', fontSize: 13, color: '#64748B' }}>Create on behalf of a customer or log an internal issue</p>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Subject *</label>
            <input autoFocus value={form.subject} onChange={e => f('subject', e.target.value)} placeholder="Brief description of the issue" style={inp} />
          </div>
          <div>
            <label style={lbl}>Requester name *</label>
            <input value={form.requesterName} onChange={e => f('requesterName', e.target.value)} placeholder="John Smith" style={inp} />
          </div>
          <div>
            <label style={lbl}>Requester email</label>
            <input type="email" value={form.requesterEmail} onChange={e => f('requesterEmail', e.target.value)} placeholder="john@company.co.za" style={inp} />
          </div>
          <div>
            <label style={lbl}>Phone</label>
            <input value={form.requesterPhone} onChange={e => f('requesterPhone', e.target.value)} placeholder="+27 11 000 0000" style={inp} />
          </div>
          <div>
            <label style={lbl}>Channel</label>
            <select value={form.channel} onChange={e => f('channel', e.target.value)} style={{ ...inp, background: '#fff' }}>
              <option value="HELPDESK">Helpdesk (customer issue)</option>
              <option value="INTERNAL">Internal (HandyFlow issue)</option>
            </select>
          </div>
          <div>
            <label style={lbl}>Priority</label>
            <select value={form.priority} onChange={e => f('priority', e.target.value)} style={{ ...inp, background: '#fff' }}>
              {['URGENT','HIGH','NORMAL','LOW'].map(p => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>
          <div>
            <label style={lbl}>Category</label>
            <select value={form.categoryId} onChange={e => f('categoryId', e.target.value)} style={{ ...inp, background: '#fff' }}>
              <option value="">Select category...</option>
              {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Description *</label>
            <textarea value={form.description} onChange={e => f('description', e.target.value)} rows={5}
              placeholder="Full description of the issue, steps to reproduce, screenshots..." style={{ ...inp, resize: 'vertical' as const, fontFamily: 'inherit' }} />
          </div>
          <div style={{ gridColumn: '1/-1' }}>
            <label style={lbl}>Internal notes</label>
            <input value={form.notes} onChange={e => f('notes', e.target.value)} placeholder="Assign priority reason, internal context..." style={inp} />
          </div>
        </div>

        {error && <div style={{ marginTop: 12, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}

        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 22 }}>
          <button onClick={onClose} style={btnS}>Cancel</button>
          <button disabled={!form.subject || !form.requesterName || !form.description || create.isPending}
            onClick={() => create.mutate()}
            style={{ ...btnP, opacity: (!form.subject || !form.requesterName || !form.description) ? 0.5 : 1 }}>
            {create.isPending ? 'Creating...' : <><Plus size={13} /> Create ticket</>}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Main Page ──────────────────────────────────────────────────────────────
export function DeskPage() {
  const qc = useQueryClient()
  const [statusFilter,   setStatusFilter]   = useState('')
  const [channelFilter,  setChannelFilter]  = useState('')
  const [priorityFilter, setPriorityFilter] = useState('')
  const [search,         setSearch]         = useState('')
  const [showCreate,     setShowCreate]     = useState(false)
  const [selectedTicket, setSelectedTicket] = useState<Ticket | null>(null)

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['desk-tickets'] })
    qc.invalidateQueries({ queryKey: ['desk-summary'] })
  }

  const { data: summary }    = useQuery<Summary>({ queryKey: ['desk-summary'], queryFn: async () => { const r = await apiClient.get('/api/v1/desk/summary'); return r.data?.data ?? r.data } })
  const { data: categories = [] } = useQuery<Category[]>({ queryKey: ['desk-categories'], queryFn: async () => { const r = await apiClient.get('/api/v1/desk/categories'); return r.data?.data ?? r.data ?? [] } })

  const { data: page, isLoading } = useQuery({
    queryKey: ['desk-tickets', statusFilter, channelFilter, priorityFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: '200' })
      if (statusFilter)   params.set('status',   statusFilter)
      if (channelFilter)  params.set('channel',  channelFilter)
      if (priorityFilter) params.set('priority', priorityFilter)
      const r = await apiClient.get(`/api/v1/desk/tickets?${params}`)
      return r.data?.data ?? r.data
    },
    refetchInterval: 30_000,
  })

  const allTickets: Ticket[] = page?.content ?? page ?? []
  const filtered = allTickets.filter(t =>
    !search || t.subject.toLowerCase().includes(search.toLowerCase()) ||
    t.ticketNumber.toLowerCase().includes(search.toLowerCase()) ||
    (t.requesterName ?? '').toLowerCase().includes(search.toLowerCase()) ||
    (t.requesterEmail ?? '').toLowerCase().includes(search.toLowerCase())
  )

  const exportCSV = () => {
    const headers = ['Ticket #','Subject','Requester','Email','Channel','Priority','Status','Category','Assigned To','SLA Breached','Created','Updated']
    const rows = filtered.map(t => [
      t.ticketNumber, `"${t.subject}"`, t.requesterName ?? '', t.requesterEmail ?? '',
      t.channel, t.priority, t.status, t.categoryName ?? '',
      t.assignedToName ?? '', t.slaBreached ? 'Yes' : 'No',
      fmtDate(t.createdAt), fmtDate(t.updatedAt),
    ])
    const csv = [headers, ...rows].map(r => r.join(',')).join('\n')
    const a = document.createElement('a'); a.href = 'data:text/csv;charset=utf-8,' + encodeURIComponent(csv); a.download = 'desk-tickets.csv'; a.click()
  }

  const kpis = [
    { label: 'Open',          value: summary?.openCount ?? 0,        color: '#DC2626', bg: '#FEF2F2', icon: <Inbox size={16} /> },
    { label: 'In Progress',   value: summary?.inProgressCount ?? 0,  color: '#D97706', bg: '#FFFBEB', icon: <RefreshCw size={16} /> },
    { label: 'Waiting',       value: summary?.waitingCount ?? 0,     color: '#7C3AED', bg: '#F5F3FF', icon: <Clock size={16} /> },
    { label: 'Resolved',      value: summary?.resolvedCount ?? 0,    color: '#166534', bg: '#DCFCE7', icon: <CheckCircle size={16} /> },
    { label: 'Urgent open',   value: summary?.urgentOpen ?? 0,       color: summary?.urgentOpen ? '#DC2626' : '#94A3B8', bg: summary?.urgentOpen ? '#FEF2F2' : '#F8FAFC', icon: <AlertTriangle size={16} /> },
    { label: 'SLA breaches',  value: summary?.slaBreachedCount ?? 0, color: summary?.slaBreachedCount ? '#DC2626' : '#94A3B8', bg: summary?.slaBreachedCount ? '#FEF2F2' : '#F8FAFC', icon: <Shield size={16} /> },
    { label: 'Helpdesk',      value: summary?.helpdeskCount ?? 0,    color: '#0D9488', bg: '#F0FDF9', icon: <MessageSquare size={16} /> },
    { label: 'Internal',      value: summary?.internalCount ?? 0,    color: '#1B3A6B', bg: '#EEF2FF', icon: <BarChart2 size={16} /> },
  ]

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Header */}
      <div style={{ marginBottom: 22, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: '#0D9488', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <MessageSquare size={18} color="#fff" />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: 0 }}>Desk Support</h1>
          </div>
          <p style={{ fontSize: 13, color: '#94A3B8', margin: 0, paddingLeft: 46 }}>
            Support tickets · SLA tracking · Customer portal · Internal issues
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={exportCSV} style={btnS}><Download size={13} /> Export</button>
          <button onClick={() => setShowCreate(true)} style={btnP}><Plus size={14} /> New Ticket</button>
        </div>
      </div>

      {/* KPI strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 10 }}>
        {kpis.slice(0, 4).map(k => (
          <div key={k.label} style={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 12, padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer' }}
            onClick={() => setStatusFilter(statusFilter === k.label.toUpperCase().replace(/ /g, '_') ? '' : k.label.toUpperCase().replace(/ /g, '_'))}>
            <div style={{ width: 36, height: 36, borderRadius: 9, background: k.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color: k.color, flexShrink: 0 }}>{k.icon}</div>
            <div>
              <div style={{ fontSize: 22, fontWeight: 800, color: k.color }}>{k.value}</div>
              <div style={{ fontSize: 10, color: '#9CA3AF' }}>{k.label}</div>
            </div>
          </div>
        ))}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10, marginBottom: 22 }}>
        {kpis.slice(4).map(k => (
          <div key={k.label} style={{ background: k.bg, borderRadius: 10, padding: '10px 14px', display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ color: k.color }}>{k.icon}</div>
            <div>
              <div style={{ fontSize: 18, fontWeight: 800, color: k.color }}>{k.value}</div>
              <div style={{ fontSize: 10, color: k.color, opacity: 0.7 }}>{k.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Main card */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: 24 }}>
        {/* Toolbar */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18, flexWrap: 'wrap', gap: 10 }}>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            {/* Status filters */}
            {['', 'OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER', 'RESOLVED', 'CLOSED'].map(s => {
              const cfg = STATUS[s]; const active = statusFilter === s
              return (
                <button key={s} onClick={() => setStatusFilter(s)}
                  style={{ padding: '6px 12px', borderRadius: 20, fontSize: 12, cursor: 'pointer', fontWeight: active ? 700 : 500, border: `1.5px solid ${active && cfg ? cfg.border : '#E2E8F0'}`, background: active && cfg ? cfg.bg : '#fff', color: active && cfg ? cfg.color : '#64748B', display: 'flex', alignItems: 'center', gap: 4 }}>
                  {s && cfg && <span style={{ width: 6, height: 6, borderRadius: '50%', background: cfg.dot }} />}
                  {s ? cfg.label : 'All tickets'}
                </button>
              )
            })}
            <div style={{ position: 'relative' as const }}>
              <Search size={13} style={{ position: 'absolute' as const, left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }} />
              <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search tickets..."
                style={{ paddingLeft: 28, padding: '7px 10px 7px 28px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', width: 180 }} />
            </div>
            <select value={priorityFilter} onChange={e => setPriorityFilter(e.target.value)}
              style={{ padding: '7px 10px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
              <option value="">All priorities</option>
              {['URGENT','HIGH','NORMAL','LOW'].map(p => <option key={p} value={p}>{p}</option>)}
            </select>
            <select value={channelFilter} onChange={e => setChannelFilter(e.target.value)}
              style={{ padding: '7px 10px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
              <option value="">All channels</option>
              <option value="HELPDESK">Helpdesk</option>
              <option value="INTERNAL">Internal</option>
            </select>
            {(search || statusFilter || priorityFilter || channelFilter) && (
              <button onClick={() => { setSearch(''); setStatusFilter(''); setPriorityFilter(''); setChannelFilter('') }}
                style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '6px 10px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, background: '#F8FAFC', color: '#64748B', cursor: 'pointer' }}>
                <X size={11} /> Clear
              </button>
            )}
          </div>
          <div style={{ fontSize: 12, color: '#94A3B8' }}>{filtered.length} ticket{filtered.length !== 1 ? 's' : ''}</div>
        </div>

        {/* Tickets table */}
        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 48, color: '#94A3B8' }}>Loading tickets...</div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 20px' }}>
            <MessageSquare size={40} style={{ marginBottom: 12, color: '#CBD5E1' }} />
            <div style={{ fontWeight: 700, color: '#475569', fontSize: 15, marginBottom: 6 }}>No tickets found</div>
            <div style={{ fontSize: 13, color: '#94A3B8', marginBottom: 18 }}>Create your first ticket or adjust the filters.</div>
            <button onClick={() => setShowCreate(true)} style={btnP}><Plus size={14} /> New ticket</button>
          </div>
        ) : (
          <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
              <thead>
                <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                  {['Subject', 'Requester', 'Category', 'Priority', 'Status', 'SLA', 'Updated', ''].map(h => (
                    <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#64748B', letterSpacing: '0.05em' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.map((t, i) => {
                  const sc = STATUS[t.status] ?? STATUS.OPEN
                  const pc = PRIORITY[t.priority] ?? PRIORITY.NORMAL
                  const rowBg = t.slaBreached ? '#FFF5F5' : t.priority === 'URGENT' && !['RESOLVED','CLOSED'].includes(t.status) ? '#FFFAF0' : i % 2 === 0 ? '#fff' : '#FAFAFA'
                  return (
                    <tr key={t.id} onClick={() => setSelectedTicket(t)} style={{ background: rowBg, cursor: 'pointer', transition: 'background 0.1s' }}
                      onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#F0F9FF'}
                      onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = rowBg}>
                      <td style={{ padding: '12px 16px', maxWidth: 260 }}>
                        <div style={{ fontWeight: 700, color: '#0F172A', display: 'flex', alignItems: 'flex-start', gap: 6 }}>
                          {t.slaBreached && <AlertTriangle size={12} color="#DC2626" style={{ flexShrink: 0, marginTop: 2 }} />}
                          <span style={{ display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' } as any}>{t.subject}</span>
                        </div>
                        <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 2, display: 'flex', gap: 6 }}>
                          <span>#{t.ticketNumber}</span>
                          {t.channel === 'INTERNAL' && <span style={{ color: '#7C3AED' }}>Internal</span>}
                        </div>
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <div style={{ fontWeight: 500, color: '#374151', fontSize: 13 }}>{t.requesterName ?? '—'}</div>
                        <div style={{ fontSize: 11, color: '#94A3B8' }}>{t.requesterEmail ?? ''}</div>
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: 12, color: '#64748B' }}>
                        {t.categoryName ? <span style={{ background: '#F1F5F9', padding: '2px 8px', borderRadius: 20, fontSize: 11 }}>{t.categoryName}</span> : '—'}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: pc.bg, color: pc.color, border: `1px solid ${pc.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <span style={{ width: 4, height: 4, borderRadius: '50%', background: pc.dot }} />{t.priority}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <span style={{ width: 4, height: 4, borderRadius: '50%', background: sc.dot }} />{sc.label}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <div style={{ fontSize: 12, color: slaColor(t), fontWeight: t.slaBreached ? 700 : 400 }}>{slaLabel(t)}</div>
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: 12, color: '#94A3B8' }}>{fmtDate(t.updatedAt)}</td>
                      <td style={{ padding: '12px 16px' }}><ChevronRight size={14} color="#94A3B8" /></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showCreate && (
        <CreateTicketModal categories={categories as Category[]} onClose={() => setShowCreate(false)}
          onSaved={invalidate} />
      )}

      {selectedTicket && (
        <TicketDetail ticket={selectedTicket} onClose={() => setSelectedTicket(null)}
          onUpdated={updated => { setSelectedTicket(updated); invalidate() }} />
      )}
    </div>
  )
}
