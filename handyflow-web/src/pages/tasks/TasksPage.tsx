// src/pages/tasks/TasksPage.tsx
import { useState, useCallback, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, CheckSquare, X, Clock, ChevronRight, Trash2, Check,
  MessageSquare, Timer, AlertCircle, User, Calendar, Flag,
  ArrowRight, ChevronLeft, Loader2, Edit3, BarChart2,
  TrendingUp, AlertTriangle, Search, Filter, Tag,
  Link2, MoreHorizontal, Archive, Settings, Hash,
  Circle, CheckCircle2, GitBranch, RefreshCw,
  Paperclip, Download, FileText, LayoutGrid, CalendarDays, GanttChart,
} from 'lucide-react'

// ── Types ──────────────────────────────────────────────────────────────────
interface Column { id: string; name: string; sortOrder: number; color: string | null; isDoneColumn: boolean }
interface Board  { id: string; name: string; description: string | null; color: string | null; isDefault: boolean; columns: Column[] }
interface TaskComment { id: string; authorId: string; authorName: string; body: string; createdAt: string }
interface TimeLog { id: string; userId: string; userName: string; hours: number; description: string | null; loggedDate: string }
interface Attachment { id: string; fileName: string; contentType: string | null; sizeBytes: number; uploadedBy: string | null; uploadedByName: string; createdAt: string }
interface ChecklistItem { id: string; text: string; completed: boolean; sortOrder: number; createdAt: string; completedAt: string | null }
interface Task {
  id: string; boardId: string; columnId: string; columnName: string | null
  title: string; description: string | null
  priority: string; status: string
  assigneeId: string | null; assigneeName: string | null
  dueDate: string | null; overdue: boolean
  estimatedHours: number | null; loggedHours: number | null
  sortOrder: number
  linkedEntityType: string | null; linkedEntityId: string | null
  commentCount: number; checklistTotal: number; checklistCompleted: number; comments: TaskComment[]
  createdAt: string; updatedAt: string; completedAt: string | null
}
interface Summary {
  totalTasks: number; todoCount: number; inProgressCount: number
  inReviewCount: number; doneCount: number; overdueCount: number; myTasksCount: number
}
interface UserOption { id: string; name: string }

// ── Constants ──────────────────────────────────────────────────────────────
const PRIORITY: Record<string, { label: string; color: string; bg: string; border: string; dot: string }> = {
  URGENT: { label: 'Urgent', color: '#B91C1C', bg: '#FEF2F2', border: '#FECACA', dot: '#EF4444' },
  HIGH:   { label: 'High',   color: '#B45309', bg: '#FFFBEB', border: '#FDE68A', dot: '#F59E0B' },
  NORMAL: { label: 'Normal', color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE', dot: '#3B82F6' },
  LOW:    { label: 'Low',    color: '#475569', bg: '#F8FAFC', border: '#E2E8F0', dot: '#94A3B8' },
}
const STATUS_COLOR: Record<string, string> = {
  TODO: '#94A3B8', IN_PROGRESS: '#3B82F6', IN_REVIEW: '#F59E0B',
  DONE: '#10B981', CANCELLED: '#6B7280',
}
const ENTITY_TYPES = ['QUOTE','INVOICE','CUSTOMER','LEASE','EMPLOYEE','CREATIVE_JOB','AP_BILL','PROPERTY','TICKET']

// ── Helpers ────────────────────────────────────────────────────────────────
const fmtDate = (d: string | null) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: '2-digit' }) : null
const fmtDateFull = (d: string | null) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' }) : '—'
const initials = (name: string | null) =>
  name ? name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() : '?'
const isOverdueDate = (dueDate: string | null, completed: string | null) =>
  dueDate && !completed ? new Date(dueDate) < new Date() : false
const fmtFileSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// ── UI Primitives ──────────────────────────────────────────────────────────
const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px', border: '1.5px solid #E5E7EB', borderRadius: 8,
  fontSize: 14, boxSizing: 'border-box', background: '#fff', color: '#111827', outline: 'none',
}
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280',
  textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6,
}

const Badge = ({ priority }: { priority: string }) => {
  const p = PRIORITY[priority] || PRIORITY.NORMAL
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: p.bg, color: p.color, border: `1px solid ${p.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
      <span style={{ width: 5, height: 5, borderRadius: '50%', background: p.dot, flexShrink: 0 }} />{p.label}
    </span>
  )
}

const Avatar = ({ name, size = 26 }: { name: string | null; size?: number }) => (
  <div style={{ width: size, height: size, borderRadius: '50%', background: '#1B3A6B', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: size * 0.38, fontWeight: 700, flexShrink: 0 }}>
    {initials(name)}
  </div>
)

const ProgressBar = ({ value, max, color = '#1B3A6B' }: { value: number; max: number; color?: string }) => {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0
  return (
    <div style={{ height: 4, background: '#F1F5F9', borderRadius: 99, overflow: 'hidden' }}>
      <div style={{ width: `${pct}%`, height: '100%', background: color, borderRadius: 99, transition: 'width 0.3s' }} />
    </div>
  )
}

const btnPrimary: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6, background: '#1B3A6B', color: '#fff',
  border: 'none', borderRadius: 8, padding: '9px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
}
const btnSecondary: React.CSSProperties = {
  padding: '9px 16px', border: '1.5px solid #E5E7EB', borderRadius: 8, background: '#fff',
  fontSize: 13, cursor: 'pointer', color: '#374151', fontWeight: 500,
}

// ── Confirm Modal ──────────────────────────────────────────────────────────
function ConfirmModal({ title, message, confirmLabel = 'Confirm', danger = false, onConfirm, onCancel }: {
  title: string; message: string; confirmLabel?: string; danger?: boolean
  onConfirm: () => void; onCancel: () => void
}) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 400, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 22 }}>
          <div style={{ width: 40, height: 40, borderRadius: '50%', background: danger ? '#FEF2F2' : '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <AlertTriangle size={18} color={danger ? '#DC2626' : '#1D4ED8'} />
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>{message}</div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <button onClick={onCancel} style={btnSecondary}>Cancel</button>
          <button onClick={onConfirm} style={{ ...btnPrimary, background: danger ? '#DC2626' : '#1B3A6B' }}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  )
}

// ── Task Detail Modal ──────────────────────────────────────────────────────
function TaskDetailModal({ task, columns, users, onClose, onUpdate, onDelete, onComplete, onMove, onRefresh }: {
  task: Task; columns: Column[]; users: UserOption[]
  onClose: () => void
  onUpdate: (data: any) => void
  onDelete: () => void
  onComplete: () => void
  onMove: (columnId: string) => void
  onRefresh: () => void
}) {
  const qc = useQueryClient()
  const [tab, setTab] = useState<'details' | 'comments' | 'time' | 'files'>('details')
  const [editTitle, setEditTitle] = useState(false)
  const [title, setTitle] = useState(task.title)
  const [editDesc, setEditDesc] = useState(false)
  const [desc, setDesc] = useState(task.description || '')
  const [comment, setComment] = useState('')
  const [hours, setHours] = useState('')
  const [hoursDesc, setHoursDesc] = useState('')
  const [hoursDate, setHoursDate] = useState(new Date().toISOString().split('T')[0])
  const [showDelete, setShowDelete] = useState(false)

  const { data: timeLogs = [] } = useQuery<TimeLog[]>({
    queryKey: ['task-timelogs', task.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/tasks/${task.id}/time`)
      return r.data || []
    },
    enabled: tab === 'time',
  })

  const addComment = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/tasks/${task.id}/comments`, { body: comment }),
    onSuccess: () => { setComment(''); onRefresh() },
  })

  const logTime = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/tasks/${task.id}/time`, {
      hours: parseFloat(hours), description: hoursDesc || null, loggedDate: hoursDate,
    }),
    onSuccess: () => {
      setHours(''); setHoursDesc('')
      qc.invalidateQueries({ queryKey: ['task-timelogs', task.id] })
      onRefresh()
    },
  })

  const { data: attachments = [] } = useQuery<Attachment[]>({
    queryKey: ['task-attachments', task.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/tasks/${task.id}/attachments`)
      return r.data?.data ?? r.data ?? []
    },
    enabled: tab === 'files',
  })

  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploadError, setUploadError] = useState('')

  const uploadAttachment = useMutation({
    mutationFn: (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      // FIX: apiClient defaults every request to Content-Type: application/json,
      // which silently overrides FormData's own multipart boundary and makes the
      // server reject the request entirely ("Content-Type 'application/json' is
      // not supported"). Explicitly unsetting it here lets the browser generate
      // the correct "multipart/form-data; boundary=..." header itself — axios
      // only does this when the Content-Type header is absent, not when it's
      // been set to something else by a default.
      return apiClient.post(`/api/v1/tasks/${task.id}/attachments`, formData, {
        headers: { 'Content-Type': undefined },
      })
    },
    onSuccess: () => { setUploadError(''); qc.invalidateQueries({ queryKey: ['task-attachments', task.id] }) },
    onError: (e: any) => setUploadError(e.response?.data?.message || 'Failed to upload file'),
  })

  const deleteAttachment = useMutation({
    mutationFn: (attachmentId: string) => apiClient.delete(`/api/v1/tasks/${task.id}/attachments/${attachmentId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['task-attachments', task.id] }),
  })

  const downloadAttachment = async (a: Attachment) => {
    const r = await apiClient.get(`/api/v1/tasks/${task.id}/attachments/${a.id}/download`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([r.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = a.fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  }

  const [newChecklistText, setNewChecklistText] = useState('')

  const { data: checklistItems = [] } = useQuery<ChecklistItem[]>({
    queryKey: ['task-checklist', task.id],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/tasks/${task.id}/checklist-items`)
      return r.data?.data ?? r.data ?? []
    },
  })

  const addChecklistItem = useMutation({
    mutationFn: (text: string) => apiClient.post(`/api/v1/tasks/${task.id}/checklist-items`, { text }),
    onSuccess: () => {
      setNewChecklistText('')
      qc.invalidateQueries({ queryKey: ['task-checklist', task.id] })
      onRefresh()
    },
  })

  const toggleChecklistItem = useMutation({
    mutationFn: ({ itemId, completed }: { itemId: string; completed: boolean }) =>
      apiClient.patch(`/api/v1/tasks/${task.id}/checklist-items/${itemId}/toggle`, { completed }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['task-checklist', task.id] })
      onRefresh()
    },
  })

  const deleteChecklistItem = useMutation({
    mutationFn: (itemId: string) => apiClient.delete(`/api/v1/tasks/${task.id}/checklist-items/${itemId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['task-checklist', task.id] })
      onRefresh()
    },
  })

  const logged    = Number(task.loggedHours ?? 0)
  const estimated = Number(task.estimatedHours ?? 0)
  const overBudget = estimated > 0 && logged > estimated
  const timeColor = overBudget ? '#EF4444' : '#10B981'
  const overdueFlag = isOverdueDate(task.dueDate, task.completedAt)

  return (
    <>
      <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'flex-start', justifyContent: 'center', zIndex: 1000, padding: '32px 20px', overflowY: 'auto' }}>
        <div style={{ background: '#fff', borderRadius: 16, width: '100%', maxWidth: 720, boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }} onClick={e => e.stopPropagation()}>

          {/* Header */}
          <div style={{ padding: '20px 24px 0', borderBottom: '1px solid #F1F5F9' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
              <div style={{ flex: 1, marginRight: 12 }}>
                {editTitle ? (
                  <input value={title} onChange={e => setTitle(e.target.value)}
                    onBlur={() => { onUpdate({ title }); setEditTitle(false) }}
                    onKeyDown={e => { if (e.key === 'Enter') { onUpdate({ title }); setEditTitle(false) } if (e.key === 'Escape') setEditTitle(false) }}
                    style={{ ...inp, fontSize: 18, fontWeight: 700, border: '2px solid #1B3A6B', padding: '4px 8px', width: '100%' }} autoFocus />
                ) : (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', group: 'true' } as any} onClick={() => setEditTitle(true)}>
                    <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: '#111827', lineHeight: 1.3, textDecoration: task.completedAt ? 'line-through' : 'none', color: task.completedAt ? '#94A3B8' : '#111827' } as any}>{task.title}</h2>
                    <Edit3 size={13} color="#CBD5E1" />
                  </div>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 10, flexWrap: 'wrap' as const }}>
                  <Badge priority={task.priority} />
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: STATUS_COLOR[task.status] || '#94A3B8', fontWeight: 700 }}>
                    <span style={{ width: 6, height: 6, borderRadius: '50%', background: STATUS_COLOR[task.status] || '#94A3B8' }} />
                    {task.status?.replace('_', ' ')}
                  </span>
                  {overdueFlag && (
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#EF4444', fontWeight: 700, background: '#FEF2F2', padding: '2px 8px', borderRadius: 20 }}>
                      <AlertTriangle size={11} /> Overdue
                    </span>
                  )}
                  {task.completedAt && (
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#10B981', fontWeight: 700, background: '#F0FDF4', padding: '2px 8px', borderRadius: 20 }}>
                      <CheckCircle2 size={11} /> Completed
                    </span>
                  )}
                </div>
              </div>

              <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
                {!task.completedAt && (
                  <button onClick={onComplete} style={{ display: 'flex', alignItems: 'center', gap: 5, background: '#F0FDF4', border: '1px solid #BBF7D0', color: '#166534', borderRadius: 8, padding: '7px 12px', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                    <Check size={13} /> Mark done
                  </button>
                )}
                <button onClick={() => setShowDelete(true)} style={{ background: 'none', border: '1.5px solid #E5E7EB', cursor: 'pointer', color: '#94A3B8', padding: '6px 8px', borderRadius: 7, display: 'flex' }}>
                  <Trash2 size={14} />
                </button>
                <button onClick={onClose} style={{ background: '#F1F5F9', border: 'none', cursor: 'pointer', color: '#64748B', padding: '6px 8px', borderRadius: 7, display: 'flex' }}>
                  <X size={16} />
                </button>
              </div>
            </div>

            {/* Move to column buttons */}
            {columns.filter(c => c.id !== task.columnId).length > 0 && (
              <div style={{ display: 'flex', gap: 5, paddingBottom: 6, flexWrap: 'wrap' as const }}>
                <span style={{ fontSize: 11, color: '#94A3B8', alignSelf: 'center', marginRight: 4 }}>Move to:</span>
                {columns.filter(c => c.id !== task.columnId).map(c => (
                  <button key={c.id} onClick={() => onMove(c.id)}
                    style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, padding: '4px 10px', borderRadius: 20, border: '1px solid #E5E7EB', background: '#F9FAFB', cursor: 'pointer', color: '#374151', fontWeight: 600, transition: 'all 0.1s' }}>
                    <div style={{ width: 6, height: 6, borderRadius: '50%', background: c.color || '#94A3B8' }} />
                    {c.name}
                  </button>
                ))}
              </div>
            )}

            {/* Tabs */}
            <div style={{ display: 'flex', gap: 0, marginTop: 6 }}>
              {(['details', 'comments', 'time', 'files'] as const).map(t => (
                <button key={t} onClick={() => setTab(t)} style={{ padding: '10px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: tab === t ? '#1B3A6B' : '#9CA3AF', borderBottom: `2px solid ${tab === t ? '#1B3A6B' : 'transparent'}`, marginBottom: -1, transition: 'all 0.15s' }}>
                  {t === 'comments' ? `Comments (${task.commentCount})` : t === 'time' ? `Time (${logged}h)` : t === 'files' ? 'Files' : 'Details'}
                </button>
              ))}
            </div>
          </div>

          {/* Body */}
          <div style={{ padding: '22px 24px 26px' }}>
            {tab === 'details' && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 220px', gap: 28 }}>
                {/* Left — description + linked entity */}
                <div>
                  <label style={lbl}>Description</label>
                  {editDesc ? (
                    <div>
                      <textarea value={desc} onChange={e => setDesc(e.target.value)} rows={5}
                        style={{ ...inp, resize: 'vertical' as const, fontFamily: 'inherit', lineHeight: 1.6 }} autoFocus />
                      <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                        <button onClick={() => { onUpdate({ description: desc }); setEditDesc(false) }} style={{ ...btnPrimary, padding: '7px 14px', fontSize: 12 }}>Save</button>
                        <button onClick={() => { setDesc(task.description || ''); setEditDesc(false) }} style={{ ...btnSecondary, padding: '7px 12px', fontSize: 12 }}>Cancel</button>
                      </div>
                    </div>
                  ) : (
                    <div onClick={() => setEditDesc(true)} style={{ fontSize: 14, color: task.description ? '#374151' : '#CBD5E1', lineHeight: 1.7, background: '#F9FAFB', borderRadius: 9, padding: '12px 14px', minHeight: 80, cursor: 'pointer', border: '1.5px solid transparent', transition: 'border-color 0.15s' }}
                      onMouseEnter={e => (e.currentTarget as HTMLElement).style.borderColor = '#CBD5E1'}
                      onMouseLeave={e => (e.currentTarget as HTMLElement).style.borderColor = 'transparent'}>
                      {task.description || 'Click to add a description...'}
                    </div>
                  )}

                  {/* Checklist */}
                  <div style={{ marginTop: 20 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                      <label style={lbl}>Checklist</label>
                      {checklistItems.length > 0 && (
                        <span style={{ fontSize: 11, color: '#9CA3AF', fontWeight: 700 }}>
                          {checklistItems.filter(i => i.completed).length}/{checklistItems.length}
                        </span>
                      )}
                    </div>
                    {checklistItems.length > 0 && (
                      <div style={{ marginBottom: 10 }}>
                        <ProgressBar
                          value={checklistItems.filter(i => i.completed).length}
                          max={checklistItems.length}
                          color={checklistItems.every(i => i.completed) ? '#10B981' : '#1B3A6B'} />
                      </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 8 }}>
                      {checklistItems.map(item => (
                        <div key={item.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '5px 6px', borderRadius: 7 }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#F9FAFB'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                          <input type="checkbox" checked={item.completed}
                            onChange={e => toggleChecklistItem.mutate({ itemId: item.id, completed: e.target.checked })}
                            style={{ width: 15, height: 15, cursor: 'pointer', accentColor: '#1B3A6B', flexShrink: 0 }} />
                          <span style={{ flex: 1, fontSize: 13, color: item.completed ? '#9CA3AF' : '#374151', textDecoration: item.completed ? 'line-through' : 'none' }}>
                            {item.text}
                          </span>
                          <button onClick={() => deleteChecklistItem.mutate(item.id)}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#CBD5E1', padding: 2, display: 'flex', flexShrink: 0 }}>
                            <X size={13} />
                          </button>
                        </div>
                      ))}
                    </div>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <input value={newChecklistText} onChange={e => setNewChecklistText(e.target.value)}
                        onKeyDown={e => { if (e.key === 'Enter' && newChecklistText.trim()) addChecklistItem.mutate(newChecklistText.trim()) }}
                        placeholder="Add checklist item..." style={{ ...inp, flex: 1, fontSize: 13 }} />
                      <button onClick={() => newChecklistText.trim() && addChecklistItem.mutate(newChecklistText.trim())}
                        disabled={!newChecklistText.trim() || addChecklistItem.isPending}
                        style={{ ...btnSecondary, padding: '7px 12px', fontSize: 12, opacity: !newChecklistText.trim() ? 0.5 : 1 }}>
                        <Plus size={13} />
                      </button>
                    </div>
                  </div>

                  {/* Time progress */}
                  {estimated > 0 && (
                    <div style={{ marginTop: 20, padding: '14px 16px', background: '#F8FAFC', borderRadius: 10, border: '1px solid #E2E8F0' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 8 }}>
                        <span style={{ fontWeight: 700, color: '#374151' }}>Time progress</span>
                        <span style={{ color: timeColor, fontWeight: 800 }}>{logged}h / {estimated}h {overBudget ? '— over budget' : ''}</span>
                      </div>
                      <ProgressBar value={logged} max={estimated} color={timeColor} />
                      <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 6 }}>
                        {overBudget ? `${(logged - estimated).toFixed(1)}h over estimate` : `${Math.max(0, estimated - logged).toFixed(1)}h remaining`}
                      </div>
                    </div>
                  )}

                  {/* Linked entity */}
                  {task.linkedEntityType && (
                    <div style={{ marginTop: 16, padding: '10px 14px', background: '#EFF6FF', borderRadius: 9, border: '1px solid #BFDBFE', display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Link2 size={13} color="#1D4ED8" />
                      <span style={{ fontSize: 12, color: '#1D4ED8', fontWeight: 600 }}>Linked to {task.linkedEntityType.replace('_', ' ')}</span>
                      <span style={{ fontSize: 11, color: '#64748B', fontFamily: 'monospace' }}>{task.linkedEntityId?.slice(0, 8)}...</span>
                    </div>
                  )}
                </div>

                {/* Right — metadata sidebar */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <div style={{ color: '#9CA3AF', marginTop: 4, flexShrink: 0 }}><User size={13} /></div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 10, color: '#9CA3AF', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 3 }}>Assignee</div>
                      <select
                        value={task.assigneeId || ''}
                        onChange={e => onUpdate({ assigneeId: e.target.value || null })}
                        style={{ ...inp, fontSize: 13, fontWeight: 600, color: '#374151', padding: '5px 6px', background: '#fff', border: '1.5px solid #E5E7EB' }}>
                        <option value="">Unassigned</option>
                        {users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
                      </select>
                    </div>
                  </div>
                  {[
                    { icon: <Calendar size={13} />, label: 'Due date',  value: fmtDateFull(task.dueDate), warn: !!overdueFlag },
                    { icon: <Flag size={13} />,     label: 'Priority',  value: task.priority },
                    { icon: <Hash size={13} />,     label: 'Column',    value: task.columnName || '—' },
                    { icon: <Timer size={13} />,    label: 'Estimated', value: estimated > 0 ? `${estimated}h` : '—' },
                    { icon: <Clock size={13} />,    label: 'Logged',    value: logged > 0 ? `${logged}h` : '—' },
                  ].map(({ icon, label, value, warn }) => (
                    <div key={label} style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                      <div style={{ color: '#9CA3AF', marginTop: 1, flexShrink: 0 }}>{icon}</div>
                      <div>
                        <div style={{ fontSize: 10, color: '#9CA3AF', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
                        <div style={{ fontSize: 13, color: warn ? '#EF4444' : '#374151', fontWeight: 600, marginTop: 2 }}>{value}</div>
                      </div>
                    </div>
                  ))}
                  <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 14 }}>
                    <div style={{ fontSize: 10, color: '#9CA3AF', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Created</div>
                    <div style={{ fontSize: 12, color: '#64748B' }}>{fmtDateFull(task.createdAt)}</div>
                  </div>
                  {task.completedAt && (
                    <div>
                      <div style={{ fontSize: 10, color: '#9CA3AF', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Completed</div>
                      <div style={{ fontSize: 12, color: '#10B981', fontWeight: 600 }}>{fmtDateFull(task.completedAt)}</div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {tab === 'comments' && (
              <div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 20, maxHeight: 340, overflowY: 'auto' }}>
                  {task.comments.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '32px 0', color: '#CBD5E1' }}>
                      <MessageSquare size={28} style={{ marginBottom: 8, opacity: 0.5 }} />
                      <div style={{ fontSize: 13 }}>No comments yet — be the first</div>
                    </div>
                  ) : task.comments.map(c => (
                    <div key={c.id} style={{ display: 'flex', gap: 10 }}>
                      <Avatar name={c.authorName} size={32} />
                      <div style={{ flex: 1, background: '#F8FAFC', borderRadius: 10, padding: '11px 14px', border: '1px solid #E2E8F0' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                          <span style={{ fontSize: 13, fontWeight: 700, color: '#111827' }}>{c.authorName}</span>
                          <span style={{ fontSize: 11, color: '#9CA3AF' }}>{fmtDate(c.createdAt)}</span>
                        </div>
                        <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.6 }}>{c.body}</div>
                      </div>
                    </div>
                  ))}
                </div>
                <div style={{ display: 'flex', gap: 10 }}>
                  <textarea value={comment} onChange={e => setComment(e.target.value)}
                    onKeyDown={e => { if (e.key === 'Enter' && (e.ctrlKey || e.metaKey) && comment.trim()) addComment.mutate() }}
                    rows={3} placeholder="Add a comment... (Ctrl+Enter to submit)"
                    style={{ ...inp, flex: 1, resize: 'none' as const, fontSize: 13 }} />
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
                  <button onClick={() => addComment.mutate()} disabled={!comment.trim() || addComment.isPending}
                    style={{ ...btnPrimary, opacity: !comment.trim() ? 0.5 : 1 }}>
                    {addComment.isPending ? <Loader2 size={13} /> : <><MessageSquare size={13} /> Comment</>}
                  </button>
                </div>
              </div>
            )}

            {tab === 'time' && (
              <div>
                {/* Summary */}
                <div style={{ display: 'flex', gap: 1, marginBottom: 20, background: '#F8FAFC', borderRadius: 12, overflow: 'hidden', border: '1px solid #E2E8F0' }}>
                  {[
                    { label: 'Logged',    value: `${logged}h`,                                           color: '#1B3A6B' },
                    { label: 'Estimated', value: estimated > 0 ? `${estimated}h` : '—',                  color: '#374151' },
                    { label: 'Remaining', value: estimated > 0 ? `${Math.max(0, estimated - logged).toFixed(1)}h` : '—', color: overBudget ? '#EF4444' : '#10B981' },
                  ].map((s, i) => (
                    <div key={s.label} style={{ flex: 1, padding: '16px 18px', borderLeft: i > 0 ? '1px solid #E2E8F0' : 'none' }}>
                      <div style={{ fontSize: 10, color: '#9CA3AF', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{s.label}</div>
                      <div style={{ fontSize: 22, fontWeight: 800, color: s.color, marginTop: 4 }}>{s.value}</div>
                    </div>
                  ))}
                </div>

                {/* Log time form */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 12, marginBottom: 12 }}>
                  <div>
                    <label style={lbl}>Hours *</label>
                    <input type="number" min="0.1" step="0.25" value={hours} onChange={e => setHours(e.target.value)} placeholder="1.5" style={inp} />
                  </div>
                  <div>
                    <label style={lbl}>Date</label>
                    <input type="date" value={hoursDate} onChange={e => setHoursDate(e.target.value)} style={inp} />
                  </div>
                  <div>
                    <label style={lbl}>Description</label>
                    <input value={hoursDesc} onChange={e => setHoursDesc(e.target.value)} placeholder="What you worked on" style={inp} />
                  </div>
                </div>
                <button onClick={() => logTime.mutate()} disabled={!hours || parseFloat(hours) <= 0 || logTime.isPending}
                  style={{ ...btnPrimary, width: '100%', justifyContent: 'center', marginBottom: 20, opacity: !hours ? 0.5 : 1 }}>
                  {logTime.isPending ? <><Loader2 size={13} /> Logging...</> : <><Timer size={13} /> Log Time</>}
                </button>

                {/* Time log history */}
                {(timeLogs as TimeLog[]).length > 0 && (
                  <div>
                    <div style={{ fontSize: 11, fontWeight: 700, color: '#9CA3AF', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Log history</div>
                    {(timeLogs as TimeLog[]).map(l => (
                      <div key={l.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '9px 12px', borderBottom: '1px solid #F1F5F9', fontSize: 13 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                          <Avatar name={l.userName} size={24} />
                          <div>
                            <div style={{ fontWeight: 600, color: '#111827' }}>{l.userName}</div>
                            {l.description && <div style={{ fontSize: 12, color: '#94A3B8' }}>{l.description}</div>}
                          </div>
                        </div>
                        <div style={{ textAlign: 'right' as const }}>
                          <div style={{ fontWeight: 700, color: '#1B3A6B' }}>{Number(l.hours).toFixed(1)}h</div>
                          <div style={{ fontSize: 11, color: '#9CA3AF' }}>{fmtDate(l.loggedDate)}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {tab === 'files' && (
              <div>
                <input ref={fileInputRef} type="file" style={{ display: 'none' }}
                  onChange={e => { const f = e.target.files?.[0]; if (f) uploadAttachment.mutate(f); e.target.value = '' }} />
                <button onClick={() => fileInputRef.current?.click()} disabled={uploadAttachment.isPending}
                  style={{ ...btnPrimary, marginBottom: 16, opacity: uploadAttachment.isPending ? 0.6 : 1 }}>
                  {uploadAttachment.isPending ? <><Loader2 size={13} /> Uploading...</> : <><Paperclip size={13} /> Upload file</>}
                </button>
                {uploadError && (
                  <div style={{ fontSize: 12, color: '#EF4444', marginBottom: 12 }}>{uploadError}</div>
                )}

                {attachments.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '32px 0', color: '#CBD5E1' }}>
                    <Paperclip size={28} style={{ marginBottom: 8, opacity: 0.5 }} />
                    <div style={{ fontSize: 13 }}>No files attached yet</div>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {attachments.map(a => (
                      <div key={a.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 12px', background: '#F8FAFC', borderRadius: 9, border: '1px solid #E2E8F0' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                          <FileText size={16} color="#64748B" style={{ flexShrink: 0 }} />
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontSize: 13, fontWeight: 600, color: '#111827', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{a.fileName}</div>
                            <div style={{ fontSize: 11, color: '#94A3B8' }}>{fmtFileSize(a.sizeBytes)} · {a.uploadedByName} · {fmtDate(a.createdAt)}</div>
                          </div>
                        </div>
                        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                          <button onClick={() => downloadAttachment(a)} title="Download"
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748B', padding: 6, borderRadius: 6, display: 'flex' }}>
                            <Download size={14} />
                          </button>
                          <button onClick={() => deleteAttachment.mutate(a.id)} title="Delete"
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', padding: 6, borderRadius: 6, display: 'flex' }}>
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {showDelete && (
        <ConfirmModal
          title="Delete task?"
          message={`"${task.title}" will be permanently deleted. Time logs and comments will also be removed.`}
          confirmLabel="Delete task"
          danger
          onConfirm={() => { onDelete(); setShowDelete(false) }}
          onCancel={() => setShowDelete(false)}
        />
      )}
    </>
  )
}

// ── Create Task Modal ──────────────────────────────────────────────────────
function CreateTaskModal({ columns, boardId, defaultColumnId, users, onClose, onSaved }: {
  columns: Column[]; boardId: string; defaultColumnId: string | null; users: UserOption[]
  onClose: () => void; onSaved: () => void
}) {
  const [form, setForm] = useState({
    title: '', description: '', priority: 'NORMAL', assigneeId: '',
    dueDate: '', estimatedHours: '', columnId: defaultColumnId || columns[0]?.id || '',
    linkedEntityType: '', linkedEntityId: '',
  })
  const [error, setError] = useState('')
  const f = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))

  const create = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/tasks/boards/${boardId}/tasks`, {
      title: form.title, description: form.description || null, priority: form.priority,
      assigneeId: form.assigneeId || null, dueDate: form.dueDate || null,
      estimatedHours: form.estimatedHours ? parseFloat(form.estimatedHours) : null,
      columnId: form.columnId || null,
      linkedEntityType: form.linkedEntityType || null,
      linkedEntityId: form.linkedEntityId || null,
    }),
    onSuccess: () => { onSaved(); onClose() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create task'),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20 }}>
      <div style={{ background: '#fff', borderRadius: 16, width: '100%', maxWidth: 540, boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }} onClick={e => e.stopPropagation()}>
        <div style={{ padding: '20px 24px', borderBottom: '1px solid #F1F5F9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#111827' }}>Create Task</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={lbl}>Title *</label>
            <input value={form.title} onChange={e => f('title', e.target.value)} placeholder="What needs to be done?" style={inp} autoFocus />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={lbl}>Priority</label>
              <select value={form.priority} onChange={e => f('priority', e.target.value)} style={{ ...inp, background: '#fff' }}>
                {Object.entries(PRIORITY).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Column</label>
              <select value={form.columnId} onChange={e => f('columnId', e.target.value)} style={{ ...inp, background: '#fff' }}>
                {columns.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={lbl}>Assignee</label>
              <select value={form.assigneeId} onChange={e => f('assigneeId', e.target.value)} style={{ ...inp, background: '#fff' }}>
                <option value="">Unassigned</option>
                {users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Due date</label>
              <input type="date" value={form.dueDate} onChange={e => f('dueDate', e.target.value)} style={inp} />
            </div>
          </div>
          <div>
            <label style={lbl}>Estimated hours</label>
            <input type="number" min="0" step="0.5" value={form.estimatedHours} onChange={e => f('estimatedHours', e.target.value)} placeholder="e.g. 4" style={inp} />
          </div>
          <div>
            <label style={lbl}>Description</label>
            <textarea value={form.description} onChange={e => f('description', e.target.value)} rows={3} placeholder="Additional details..." style={{ ...inp, resize: 'none' as const, fontFamily: 'inherit' }} />
          </div>

          {/* Cross-module link */}
          <div style={{ padding: '12px 14px', background: '#F8FAFC', borderRadius: 9, border: '1px solid #E2E8F0' }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Link2 size={11} /> Link to entity (optional)
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <select value={form.linkedEntityType} onChange={e => f('linkedEntityType', e.target.value)} style={{ ...inp, background: '#fff', fontSize: 13 }}>
                  <option value="">No link</option>
                  {ENTITY_TYPES.map(t => <option key={t} value={t}>{t.replace('_', ' ')}</option>)}
                </select>
              </div>
              <div>
                <input value={form.linkedEntityId} onChange={e => f('linkedEntityId', e.target.value)} placeholder="Entity UUID" style={{ ...inp, fontSize: 13 }} disabled={!form.linkedEntityType} />
              </div>
            </div>
          </div>

          {error && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, padding: '10px 12px' }}>
              <AlertCircle size={13} color="#EF4444" />
              <span style={{ fontSize: 13, color: '#DC2626' }}>{error}</span>
            </div>
          )}
        </div>
        <div style={{ padding: '0 24px 22px', display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button onClick={onClose} style={btnSecondary}>Cancel</button>
          <button onClick={() => create.mutate()} disabled={!form.title.trim() || create.isPending}
            style={{ ...btnPrimary, opacity: !form.title.trim() ? 0.5 : 1 }}>
            {create.isPending ? <><Loader2 size={13} /> Creating...</> : <><Plus size={13} /> Create Task</>}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Create Board Modal ─────────────────────────────────────────────────────
function CreateBoardModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ name: '', description: '', color: '#1B3A6B' })
  const [error, setError] = useState('')
  const create = useMutation({
    mutationFn: () => apiClient.post('/api/v1/tasks/boards', form),
    onSuccess: () => { onSaved(); onClose() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create board'),
  })
  const BOARD_COLORS = ['#1B3A6B','#0D9488','#D97706','#7C3AED','#DC2626','#0284C7','#166534','#374151']
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20 }}>
      <div style={{ background: '#fff', borderRadius: 16, width: 420, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ padding: '20px 24px', borderBottom: '1px solid #F1F5F9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>New Board</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={lbl}>Board name *</label>
            <input value={form.name} onChange={e => setForm(p => ({ ...p, name: e.target.value }))} placeholder="e.g. Product Roadmap" style={inp} autoFocus />
          </div>
          <div>
            <label style={lbl}>Description</label>
            <input value={form.description} onChange={e => setForm(p => ({ ...p, description: e.target.value }))} placeholder="What is this board for?" style={inp} />
          </div>
          <div>
            <label style={lbl}>Color</label>
            <div style={{ display: 'flex', gap: 8 }}>
              {BOARD_COLORS.map(c => (
                <div key={c} onClick={() => setForm(p => ({ ...p, color: c }))}
                  style={{ width: 28, height: 28, borderRadius: '50%', background: c, cursor: 'pointer', border: form.color === c ? '3px solid #1B3A6B' : '2px solid transparent', boxSizing: 'border-box', transition: 'transform 0.1s' }} />
              ))}
            </div>
          </div>
          {error && <div style={{ background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, padding: '9px 12px', fontSize: 13, color: '#DC2626' }}>{error}</div>}
        </div>
        <div style={{ padding: '0 24px 22px', display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button onClick={onClose} style={btnSecondary}>Cancel</button>
          <button onClick={() => create.mutate()} disabled={!form.name.trim() || create.isPending}
            style={{ ...btnPrimary, opacity: !form.name.trim() ? 0.5 : 1 }}>
            {create.isPending ? <Loader2 size={13} /> : <><Plus size={13} /> Create Board</>}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Task Card ──────────────────────────────────────────────────────────────
function TaskCard({ task, columns, onMoveTask, onClick, isDragging, onDragStart, onDragEnd }: {
  task: Task; columns: Column[]
  onMoveTask: (taskId: string, columnId: string) => void
  onClick: () => void
  isDragging: boolean
  onDragStart: () => void
  onDragEnd: () => void
}) {
  const due     = task.dueDate ? new Date(task.dueDate) : null
  const overdue = due && due < new Date() && !task.completedAt
  const otherCols = columns.filter(c => c.id !== task.columnId)

  return (
    <div onClick={onClick}
      draggable
      onDragStart={e => { e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', task.id); onDragStart() }}
      onDragEnd={onDragEnd}
      style={{ background: '#fff', border: `1px solid ${overdue ? '#FCA5A5' : '#E5E7EB'}`, borderRadius: 10, padding: '13px 14px', cursor: isDragging ? 'grabbing' : 'grab', opacity: isDragging ? 0.4 : 1, transition: 'box-shadow 0.15s, opacity 0.15s', borderLeft: `3px solid ${overdue ? '#EF4444' : (columns.find(c => c.id === task.columnId)?.color || '#E5E7EB')}` }}
      onMouseEnter={e => { (e.currentTarget as HTMLElement).style.boxShadow = '0 4px 16px rgba(0,0,0,0.1)' }}
      onMouseLeave={e => { (e.currentTarget as HTMLElement).style.boxShadow = 'none' }}>

      {/* Title */}
      <div style={{ fontSize: 13, fontWeight: 700, color: task.completedAt ? '#9CA3AF' : '#111827', marginBottom: task.description ? 6 : 10, lineHeight: 1.4, textDecoration: task.completedAt ? 'line-through' : 'none', display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 6 }}>
        <span>{task.title}</span>
        {task.completedAt && <CheckCircle2 size={13} color="#10B981" style={{ flexShrink: 0, marginTop: 1 }} />}
      </div>

      {task.description && (
        <div style={{ fontSize: 12, color: '#94A3B8', marginBottom: 10, lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' } as any}>
          {task.description}
        </div>
      )}

      {/* Progress bar */}
      {task.estimatedHours && task.estimatedHours > 0 && (
        <div style={{ marginBottom: 10 }}>
          <ProgressBar value={Number(task.loggedHours ?? 0)} max={Number(task.estimatedHours)} color={Number(task.loggedHours ?? 0) > Number(task.estimatedHours) ? '#EF4444' : '#1B3A6B'} />
        </div>
      )}

      {/* Footer row */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Badge priority={task.priority} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {task.checklistTotal > 0 && (
            <span style={{ display: 'flex', alignItems: 'center', gap: 3, fontSize: 11, color: task.checklistCompleted === task.checklistTotal ? '#10B981' : '#9CA3AF', fontWeight: task.checklistCompleted === task.checklistTotal ? 700 : 400 }}>
              <CheckSquare size={10} />{task.checklistCompleted}/{task.checklistTotal}
            </span>
          )}
          {task.commentCount > 0 && (
            <span style={{ display: 'flex', alignItems: 'center', gap: 3, fontSize: 11, color: '#9CA3AF' }}>
              <MessageSquare size={10} />{task.commentCount}
            </span>
          )}
          {due && (
            <span style={{ display: 'flex', alignItems: 'center', gap: 3, fontSize: 11, color: overdue ? '#EF4444' : '#9CA3AF', fontWeight: overdue ? 700 : 400 }}>
              <Clock size={10} />{fmtDate(task.dueDate)}
            </span>
          )}
          {task.linkedEntityType && <Link2 size={10} color="#94A3B8" title={`Linked to ${task.linkedEntityType}`} />}
        </div>
      </div>

      {/* Assignee + move buttons */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 9 }}>
        {task.assigneeName ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <Avatar name={task.assigneeName} size={20} />
            <span style={{ fontSize: 11, color: '#6B7280' }}>{task.assigneeName}</span>
          </div>
        ) : <div />}
        {otherCols.length > 0 && (
          <div style={{ display: 'flex', gap: 4 }} onClick={e => e.stopPropagation()}>
            {otherCols.slice(0, 3).map(c => (
              <button key={c.id} onClick={() => onMoveTask(task.id, c.id)}
                style={{ fontSize: 10, padding: '3px 8px', borderRadius: 20, border: '1px solid #E5E7EB', background: '#F9FAFB', cursor: 'pointer', color: '#6B7280', display: 'flex', alignItems: 'center', gap: 3, transition: 'all 0.1s' }}
                onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = `${c.color || '#94A3B8'}20`; (e.currentTarget as HTMLElement).style.color = c.color || '#374151' }}
                onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = '#F9FAFB'; (e.currentTarget as HTMLElement).style.color = '#6B7280' }}>
                <ArrowRight size={9} />{c.name}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// ── Main TasksPage ─────────────────────────────────────────────────────────
// ── Calendar View ─────────────────────────────────────────────────────────
function CalendarView({ tasks, onTaskClick }: { tasks: Task[]; onTaskClick: (task: Task) => void }) {
  const [monthDate, setMonthDate] = useState(() => new Date())
  const year  = monthDate.getFullYear()
  const month = monthDate.getMonth()
  const dateKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  const todayKey = dateKey(new Date())

  const firstOfMonth   = new Date(year, month, 1)
  const startDay       = firstOfMonth.getDay()
  const daysInMonth    = new Date(year, month + 1, 0).getDate()

  const cells: { date: Date; inMonth: boolean }[] = []
  for (let i = startDay; i > 0; i--) cells.push({ date: new Date(year, month, 1 - i), inMonth: false })
  for (let d = 1; d <= daysInMonth; d++) cells.push({ date: new Date(year, month, d), inMonth: true })
  while (cells.length % 7 !== 0) {
    const next = new Date(cells[cells.length - 1].date)
    next.setDate(next.getDate() + 1)
    cells.push({ date: next, inMonth: false })
  }

  const tasksByDate = new Map<string, Task[]>()
  tasks.forEach(t => {
    if (!t.dueDate) return
    const key = t.dueDate.slice(0, 10)
    if (!tasksByDate.has(key)) tasksByDate.set(key, [])
    tasksByDate.get(key)!.push(t)
  })

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
        <h3 style={{ margin: 0, fontSize: 15, fontWeight: 800, color: '#111827' }}>
          {monthDate.toLocaleDateString('en-ZA', { month: 'long', year: 'numeric' })}
        </h3>
        <div style={{ display: 'flex', gap: 6 }}>
          <button onClick={() => setMonthDate(new Date())} style={{ ...btnSecondary, padding: '6px 12px', fontSize: 12 }}>Today</button>
          <button onClick={() => setMonthDate(new Date(year, month - 1, 1))}
            style={{ background: '#F1F5F9', border: 'none', borderRadius: 8, padding: '6px 9px', cursor: 'pointer', display: 'flex' }}>
            <ChevronLeft size={14} />
          </button>
          <button onClick={() => setMonthDate(new Date(year, month + 1, 1))}
            style={{ background: '#F1F5F9', border: 'none', borderRadius: 8, padding: '6px 9px', cursor: 'pointer', display: 'flex' }}>
            <ChevronRight size={14} />
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 1, background: '#E5E7EB', border: '1px solid #E5E7EB', borderRadius: 10, overflow: 'hidden' }}>
        {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(d => (
          <div key={d} style={{ background: '#F8FAFC', padding: '7px', fontSize: 11, fontWeight: 700, color: '#64748B', textAlign: 'center' }}>{d}</div>
        ))}
        {cells.map(({ date, inMonth }, i) => {
          const key      = dateKey(date)
          const dayTasks = tasksByDate.get(key) || []
          const isToday  = key === todayKey
          return (
            <div key={i} style={{ background: '#fff', minHeight: 96, padding: 6, opacity: inMonth ? 1 : 0.4 }}>
              <div style={{ marginBottom: 4 }}>
                {isToday ? (
                  <span style={{ background: '#1B3A6B', color: '#fff', borderRadius: '50%', width: 19, height: 19, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 800 }}>{date.getDate()}</span>
                ) : (
                  <span style={{ fontSize: 11, fontWeight: 600, color: '#9CA3AF' }}>{date.getDate()}</span>
                )}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {dayTasks.slice(0, 3).map(t => (
                  <div key={t.id} onClick={() => onTaskClick(t)} title={t.title}
                    style={{ fontSize: 10, padding: '2px 5px', borderRadius: 4, background: t.overdue ? '#FEF2F2' : (PRIORITY[t.priority]?.bg || '#F1F5F9'), color: t.overdue ? '#DC2626' : (PRIORITY[t.priority]?.color || '#475569'), cursor: 'pointer', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 600 }}>
                    {t.title}
                  </div>
                ))}
                {dayTasks.length > 3 && (
                  <div style={{ fontSize: 10, color: '#9CA3AF', fontWeight: 600 }}>+{dayTasks.length - 3} more</div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ── Timeline View ─────────────────────────────────────────────────────────
// NOTE: tasks only carry a single dueDate, not a start date, so this renders
// each task as a marker on its due date rather than a true start→end Gantt
// bar. Adding a startDate field to Task would let this show real duration
// bars instead — a reasonable follow-up if that level of detail is wanted.
function TimelineView({ tasks, onTaskClick }: { tasks: Task[]; onTaskClick: (task: Task) => void }) {
  const dated = tasks.filter(t => !!t.dueDate).sort((a, b) => a.dueDate!.localeCompare(b.dueDate!))

  if (dated.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0', color: '#CBD5E1' }}>
        <GanttChart size={32} style={{ marginBottom: 10, opacity: 0.5 }} />
        <div style={{ fontSize: 13 }}>No tasks with due dates to show on the timeline</div>
      </div>
    )
  }

  const DAY_MS   = 86400000
  const dayWidth = 34
  const today    = new Date(); today.setHours(0, 0, 0, 0)

  const dueTimes = dated.map(t => new Date(t.dueDate! + 'T00:00:00').getTime())
  const minTime = Math.min(today.getTime(), ...dueTimes) - DAY_MS
  const maxTime = Math.max(today.getTime(), ...dueTimes) + DAY_MS
  const totalDays = Math.max(1, Math.round((maxTime - minTime) / DAY_MS))
  const xFor = (dueDate: string) => Math.round((new Date(dueDate + 'T00:00:00').getTime() - minTime) / DAY_MS) * dayWidth

  const axisDates: Date[] = []
  for (let i = 0; i <= totalDays; i++) axisDates.push(new Date(minTime + i * DAY_MS))

  return (
    <div style={{ border: '1px solid #E5E7EB', borderRadius: 10, overflow: 'auto' }}>
      <div style={{ minWidth: 220 + (totalDays + 1) * dayWidth }}>
        {/* Date axis */}
        <div style={{ display: 'flex', borderBottom: '1px solid #E5E7EB', background: '#F8FAFC', position: 'sticky' as const, top: 0, zIndex: 1 }}>
          <div style={{ width: 220, flexShrink: 0, padding: '8px 12px', fontSize: 11, fontWeight: 700, color: '#64748B', borderRight: '1px solid #E5E7EB' }}>Task</div>
          <div style={{ position: 'relative' as const, flex: 1, height: 32 }}>
            {axisDates.map((d, i) => {
              const isWeekend = d.getDay() === 0 || d.getDay() === 6
              return (
                <div key={i} style={{ position: 'absolute' as const, left: i * dayWidth, width: dayWidth, textAlign: 'center' as const, fontSize: 10, fontWeight: 600, color: isWeekend ? '#CBD5E1' : '#94A3B8', paddingTop: 8, borderLeft: '1px solid #F1F5F9', height: '100%' }}>
                  {d.getDate()}
                </div>
              )
            })}
          </div>
        </div>

        {/* Rows */}
        {dated.map(t => (
          <div key={t.id} style={{ display: 'flex', borderBottom: '1px solid #F1F5F9' }}>
            <div onClick={() => onTaskClick(t)} title={t.title}
              style={{ width: 220, flexShrink: 0, padding: '9px 12px', fontSize: 12, fontWeight: 600, color: '#374151', cursor: 'pointer', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', borderRight: '1px solid #F1F5F9' }}>
              {t.title}
            </div>
            <div style={{ position: 'relative' as const, flex: 1, height: 38 }}>
              <div onClick={() => onTaskClick(t)} title={`${t.title} — ${fmtDate(t.dueDate)}`}
                style={{ position: 'absolute' as const, left: xFor(t.dueDate!) + 3, top: 8, width: dayWidth - 6, height: 22, borderRadius: 6, cursor: 'pointer',
                  background: t.overdue ? '#FEE2E2' : (PRIORITY[t.priority]?.bg || '#F1F5F9'),
                  border: `1.5px solid ${t.overdue ? '#EF4444' : (PRIORITY[t.priority]?.border || '#E2E8F0')}` }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export function TasksPage() {
  const qc = useQueryClient()
  const [selectedBoard, setSelectedBoard] = useState<Board | null>(null)
  const [tasks, setTasks] = useState<Task[]>([])
  const [loadingTasks, setLoadingTasks] = useState(false)
  const [showCreateTask, setShowCreateTask] = useState(false)
  const [showCreateBoard, setShowCreateBoard] = useState(false)
  const [createCol, setCreateCol] = useState<string | null>(null)
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [search, setSearch] = useState('')
  const [filterPriority, setFilterPriority] = useState('ALL')
  const [filterAssignee, setFilterAssignee] = useState('ALL')
  const [filterOverdue, setFilterOverdue] = useState(false)
  const [draggedTaskId, setDraggedTaskId] = useState<string | null>(null)
  const [dragOverColId, setDragOverColId] = useState<string | null>(null)
  const [view, setView] = useState<'kanban' | 'calendar' | 'timeline'>('kanban')

  const { data: boards = [], isLoading: boardsLoading } = useQuery<Board[]>({
    queryKey: ['task-boards'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/tasks/boards')
      return r.data || []
    },
  })

  const { data: summary } = useQuery<Summary>({
    queryKey: ['tasks-summary'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/tasks/summary')
      return r.data?.data ?? r.data
    },
    refetchInterval: 30_000,
  })

  // FIX: real users to assign tasks to — previously the create form captured a free-text
  // name that never resolved to a real assigneeId, so "My Tasks" silently missed those tasks.
  const { data: userOptions = [] } = useQuery<UserOption[]>({
    queryKey: ['tasks-assignable-users'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/tasks/assignable-users')
      return r.data?.data ?? r.data ?? []
    },
    staleTime: 5 * 60_000,
  })

  const loadBoard = async (board: Board) => {
    setSelectedBoard(board)
    setLoadingTasks(true)
    try {
      const r = await apiClient.get(`/api/v1/tasks/boards/${board.id}/tasks?size=500`)
      const p = r.data?.data ?? r.data
      setTasks(p?.content ?? p ?? [])
    } catch { setTasks([]) }
    finally { setLoadingTasks(false) }
  }

  const refreshTasks = useCallback(async () => {
    if (!selectedBoard) return
    try {
      const r = await apiClient.get(`/api/v1/tasks/boards/${selectedBoard.id}/tasks?size=500`)
      const p = r.data?.data ?? r.data
      setTasks(p?.content ?? p ?? [])
    } catch {}
  }, [selectedBoard])

  const openTask = async (task: Task) => {
    try {
      const r = await apiClient.get(`/api/v1/tasks/${task.id}`)
      setSelectedTask(r.data?.data ?? r.data)
    } catch { setSelectedTask(task) }
  }

  const moveTask = useMutation({
    mutationFn: ({ taskId, columnId }: { taskId: string; columnId: string }) =>
      apiClient.post(`/api/v1/tasks/${taskId}/move`, { columnId, sortOrder: 0 }),
    onMutate: ({ taskId, columnId }) => {
      setTasks(prev => prev.map(t => t.id === taskId ? { ...t, columnId } : t))
      if (selectedTask?.id === taskId) setSelectedTask(p => p ? { ...p, columnId } : null)
    },
    onSettled: refreshTasks,
  })

  const updateTask = useMutation({
    mutationFn: ({ id, data }: { id: string; data: any }) => apiClient.put(`/api/v1/tasks/${id}`, data),
    onSuccess: async () => {
      await refreshTasks()
      if (selectedTask) {
        const r = await apiClient.get(`/api/v1/tasks/${selectedTask.id}`)
        setSelectedTask(r.data?.data ?? r.data)
      }
    },
  })

  const deleteTask = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/tasks/${id}`),
    onSuccess: () => { setSelectedTask(null); refreshTasks(); qc.invalidateQueries({ queryKey: ['tasks-summary'] }) },
  })

  const completeTask = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/tasks/${id}/complete`),
    onSuccess: async () => {
      await refreshTasks()
      qc.invalidateQueries({ queryKey: ['tasks-summary'] })
      if (selectedTask) {
        const r = await apiClient.get(`/api/v1/tasks/${selectedTask.id}`)
        setSelectedTask(r.data?.data ?? r.data)
      }
    },
  })

  const columns = selectedBoard?.columns ?? []

  // Compute unique assignees for filter dropdown
  const assignees = Array.from(new Set(tasks.map(t => t.assigneeName).filter(Boolean)))

  // Apply filters
  const filteredTasks = tasks.filter(t => {
    if (search && !t.title.toLowerCase().includes(search.toLowerCase()) &&
        !t.description?.toLowerCase().includes(search.toLowerCase()) &&
        !t.assigneeName?.toLowerCase().includes(search.toLowerCase())) return false
    if (filterPriority !== 'ALL' && t.priority !== filterPriority) return false
    if (filterAssignee !== 'ALL' && t.assigneeName !== filterAssignee) return false
    if (filterOverdue && !t.overdue) return false
    return true
  })

  const activeFilters = [filterPriority !== 'ALL', filterAssignee !== 'ALL', filterOverdue, !!search].filter(Boolean).length

  // When a board is loaded, derive column-accurate counts from task data
  // to avoid relying on stale status fields in the summary endpoint.
  const boardStats = selectedBoard ? {
    inProgress: tasks.filter(t => {
      const col = columns.find(c => c.id === t.columnId)
      const n   = col?.name?.toUpperCase() ?? ''
      return !col?.isDoneColumn && (n.includes('PROGRESS') || n.includes('DOING'))
    }).length,
    done: tasks.filter(t => columns.find(c => c.id === t.columnId)?.isDoneColumn).length,
    overdue: tasks.filter(t => t.overdue).length,
  } : null

  const statCards = [
    { label: 'Total',       value: summary?.totalTasks ?? tasks.length,                                    color: '#1B3A6B', bg: '#EEF2FF', icon: <CheckSquare size={16} /> },
    { label: 'In Progress', value: boardStats?.inProgress ?? summary?.inProgressCount ?? 0,                color: '#2563EB', bg: '#EFF6FF', icon: <RefreshCw size={16} /> },
    { label: 'Completed',   value: boardStats?.done ?? summary?.doneCount ?? 0,                            color: '#059669', bg: '#F0FDF4', icon: <CheckCircle2 size={16} /> },
    { label: 'Overdue',     value: boardStats?.overdue ?? summary?.overdueCount ?? 0,                      color: (boardStats?.overdue ?? summary?.overdueCount ?? 0) > 0 ? '#DC2626' : '#64748B', bg: (boardStats?.overdue ?? summary?.overdueCount ?? 0) > 0 ? '#FEF2F2' : '#F8FAFC', icon: <AlertTriangle size={16} /> },
    { label: 'My tasks',    value: summary?.myTasksCount ?? 0,                                             color: '#D97706', bg: '#FFFBEB', icon: <User size={16} /> },
  ]

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Page header */}
      <div style={{ marginBottom: 22, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: '#1B3A6B', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <CheckSquare size={18} color="#fff" />
            </div>
            <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: 0 }}>Tasks</h1>
          </div>
          <p style={{ fontSize: 13, color: '#94A3B8', margin: 0, paddingLeft: 46 }}>Kanban boards · Assignment · Time tracking · Cross-module links</p>
        </div>
        <button onClick={() => setShowCreateBoard(true)} style={{ ...btnSecondary, display: 'flex', alignItems: 'center', gap: 6 }}>
          <Plus size={14} /> New Board
        </button>
      </div>

      {/* Stats strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 12, marginBottom: 24 }}>
        {statCards.map(s => (
          <div key={s.label} style={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 12, padding: '14px 18px', display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 36, height: 36, borderRadius: 9, background: s.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color: s.color, flexShrink: 0 }}>{s.icon}</div>
            <div>
              <div style={{ fontSize: 22, fontWeight: 800, color: s.color, letterSpacing: '-0.02em' }}>{s.value}</div>
              <div style={{ fontSize: 11, color: '#9CA3AF', marginTop: 1 }}>{s.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Board selection / Kanban view */}
      {!selectedBoard ? (
        <div style={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 14, padding: 24 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#9CA3AF', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Select a board</div>
          </div>
          {boardsLoading ? (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 48, color: '#9CA3AF', gap: 10 }}>
              <Loader2 size={18} style={{ animation: 'spin 1s linear infinite' }} /> Loading boards...
            </div>
          ) : boards.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '56px 20px' }}>
              <div style={{ width: 60, height: 60, borderRadius: 16, background: '#F1F5F9', margin: '0 auto 16px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <CheckSquare size={26} color="#CBD5E1" />
              </div>
              <div style={{ fontWeight: 700, color: '#374151', fontSize: 16, marginBottom: 6 }}>No boards yet</div>
              <div style={{ color: '#9CA3AF', fontSize: 14, marginBottom: 20 }}>Create a board to start managing tasks as a team</div>
              <button onClick={() => setShowCreateBoard(true)} style={btnPrimary}><Plus size={14} /> Create first board</button>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 14 }}>
              {(boards as Board[]).map(board => (
                <div key={board.id} onClick={() => loadBoard(board)}
                  style={{ border: '1px solid #E5E7EB', borderLeft: `4px solid ${board.color || '#1B3A6B'}`, borderRadius: 12, padding: '18px 20px', cursor: 'pointer', transition: 'all 0.15s', background: '#FAFAFA' }}
                  onMouseEnter={e => Object.assign((e.currentTarget as HTMLElement).style, { background: '#fff', boxShadow: '0 4px 16px rgba(0,0,0,0.08)' })}
                  onMouseLeave={e => Object.assign((e.currentTarget as HTMLElement).style, { background: '#FAFAFA', boxShadow: 'none' })}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                    <div style={{ fontWeight: 800, fontSize: 15, color: '#111827' }}>
                      {board.name}
                      {board.isDefault && <span style={{ marginLeft: 8, fontSize: 10, background: '#EEF2FF', color: '#1B3A6B', padding: '1px 6px', borderRadius: 10, fontWeight: 700 }}>Default</span>}
                    </div>
                    <ChevronRight size={15} color="#CBD5E1" />
                  </div>
                  {board.description && <div style={{ fontSize: 13, color: '#9CA3AF', marginBottom: 12, lineHeight: 1.5 }}>{board.description}</div>}
                  <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
                    {board.columns?.slice(0, 8).map(col => (
                      <div key={col.id} title={col.name} style={{ width: 8, height: 8, borderRadius: '50%', background: col.color || '#94A3B8' }} />
                    ))}
                    <span style={{ fontSize: 11, color: '#9CA3AF', marginLeft: 4 }}>{board.columns?.length ?? 0} columns</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <div>
          {/* Board header */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <button onClick={() => { setSelectedBoard(null); setTasks([]) }}
                style={{ display: 'flex', alignItems: 'center', gap: 5, background: '#F1F5F9', border: 'none', borderRadius: 8, padding: '7px 12px', fontSize: 13, cursor: 'pointer', color: '#374151', fontWeight: 600 }}>
                <ChevronLeft size={14} /> Boards
              </button>
              <div style={{ width: 10, height: 10, borderRadius: '50%', background: selectedBoard.color || '#1B3A6B' }} />
              <h2 style={{ margin: 0, fontSize: 17, fontWeight: 800, color: '#111827' }}>{selectedBoard.name}</h2>
              <span style={{ fontSize: 13, color: '#94A3B8' }}>{filteredTasks.length} task{filteredTasks.length !== 1 ? 's' : ''}</span>
              <div style={{ display: 'flex', gap: 2, background: '#F1F5F9', borderRadius: 8, padding: 3, marginLeft: 6 }}>
                {([
                  { key: 'kanban', label: 'Board', icon: <LayoutGrid size={13} /> },
                  { key: 'calendar', label: 'Calendar', icon: <CalendarDays size={13} /> },
                  { key: 'timeline', label: 'Timeline', icon: <GanttChart size={13} /> },
                ] as const).map(v => (
                  <button key={v.key} onClick={() => setView(v.key)}
                    style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 10px', borderRadius: 6, border: 'none', cursor: 'pointer', fontSize: 12, fontWeight: 700, background: view === v.key ? '#fff' : 'transparent', color: view === v.key ? '#1B3A6B' : '#64748B', boxShadow: view === v.key ? '0 1px 2px rgba(0,0,0,0.08)' : 'none', transition: 'all 0.1s' }}>
                    {v.icon} {v.label}
                  </button>
                ))}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
              {/* Search */}
              <div style={{ position: 'relative' as const }}>
                <Search size={13} style={{ position: 'absolute' as const, left: 9, top: '50%', transform: 'translateY(-50%)', color: '#9CA3AF' }} />
                <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search tasks..."
                  style={{ paddingLeft: 28, padding: '7px 10px 7px 28px', border: '1.5px solid #E5E7EB', borderRadius: 8, fontSize: 13, outline: 'none', width: 190 }} />
              </div>
              {/* Priority filter */}
              <select value={filterPriority} onChange={e => setFilterPriority(e.target.value)}
                style={{ padding: '7px 10px', border: '1.5px solid #E5E7EB', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
                <option value="ALL">All priorities</option>
                {Object.entries(PRIORITY).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
              </select>
              {/* Assignee filter */}
              {assignees.length > 0 && (
                <select value={filterAssignee} onChange={e => setFilterAssignee(e.target.value)}
                  style={{ padding: '7px 10px', border: '1.5px solid #E5E7EB', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
                  <option value="ALL">All assignees</option>
                  {assignees.map(a => <option key={a!} value={a!}>{a}</option>)}
                </select>
              )}
              {/* Overdue toggle */}
              <button onClick={() => setFilterOverdue(p => !p)}
                style={{ padding: '7px 12px', border: `1.5px solid ${filterOverdue ? '#FECACA' : '#E5E7EB'}`, borderRadius: 8, fontSize: 12, fontWeight: 600, background: filterOverdue ? '#FEF2F2' : '#fff', color: filterOverdue ? '#DC2626' : '#64748B', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 5 }}>
                <AlertTriangle size={12} /> Overdue
              </button>
              {activeFilters > 0 && (
                <button onClick={() => { setSearch(''); setFilterPriority('ALL'); setFilterAssignee('ALL'); setFilterOverdue(false) }}
                  style={{ padding: '7px 10px', border: '1px solid #E5E7EB', borderRadius: 8, fontSize: 12, background: '#F8FAFC', color: '#64748B', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}>
                  <X size={11} /> Clear ({activeFilters})
                </button>
              )}
              <button onClick={() => { setCreateCol(columns[0]?.id ?? null); setShowCreateTask(true) }} style={btnPrimary}>
                <Plus size={14} /> New Task
              </button>
            </div>
          </div>

          {/* Kanban / Calendar / Timeline */}
          {loadingTasks ? (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 60, color: '#9CA3AF', gap: 10 }}>
              <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> Loading tasks...
            </div>
          ) : view === 'calendar' ? (
            <CalendarView tasks={filteredTasks} onTaskClick={openTask} />
          ) : view === 'timeline' ? (
            <TimelineView tasks={filteredTasks} onTaskClick={openTask} />
          ) : (
            <div style={{ display: 'flex', gap: 14, overflowX: 'auto', paddingBottom: 20, alignItems: 'flex-start' }}>
              {[...columns].sort((a, b) => a.sortOrder - b.sortOrder).map(col => {
                const colTasks = filteredTasks.filter(t => t.columnId === col.id).sort((a, b) => a.sortOrder - b.sortOrder)
                const doneCol  = col.isDoneColumn
                const isDragOver = dragOverColId === col.id
                return (
                  <div key={col.id} style={{ minWidth: 292, maxWidth: 292, flexShrink: 0 }}
                    onDragOver={e => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; if (dragOverColId !== col.id) setDragOverColId(col.id) }}
                    onDragLeave={() => setDragOverColId(prev => (prev === col.id ? null : prev))}
                    onDrop={e => {
                      e.preventDefault()
                      const taskId = e.dataTransfer.getData('text/plain') || draggedTaskId
                      if (taskId) moveTask.mutate({ taskId, columnId: col.id })
                      setDragOverColId(null)
                      setDraggedTaskId(null)
                    }}>
                    {/* Column header */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, padding: '0 2px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                        <div style={{ width: 9, height: 9, borderRadius: '50%', background: col.color || '#94A3B8' }} />
                        <span style={{ fontSize: 13, fontWeight: 700, color: '#374151' }}>{col.name}</span>
                        <span style={{ background: '#F1F5F9', color: '#64748B', borderRadius: 20, padding: '1px 8px', fontSize: 11, fontWeight: 700 }}>{colTasks.length}</span>
                        {doneCol && <CheckCircle2 size={12} color="#10B981" />}
                      </div>
                      <button onClick={() => { setCreateCol(col.id); setShowCreateTask(true) }}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 4, borderRadius: 6, display: 'flex' }}>
                        <Plus size={15} />
                      </button>
                    </div>

                    {/* Cards */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 9, minHeight: 80, padding: 6, margin: -6, borderRadius: 12, border: `1.5px dashed ${isDragOver ? '#1B3A6B' : 'transparent'}`, background: isDragOver ? '#EFF6FF' : 'transparent', transition: 'background 0.1s, border-color 0.1s' }}>
                      {colTasks.map(task => (
                        <TaskCard key={task.id} task={task} columns={columns}
                          onMoveTask={(taskId, columnId) => moveTask.mutate({ taskId, columnId })}
                          onClick={() => openTask(task)}
                          isDragging={draggedTaskId === task.id}
                          onDragStart={() => setDraggedTaskId(task.id)}
                          onDragEnd={() => { setDraggedTaskId(null); setDragOverColId(null) }} />
                      ))}
                      <div onClick={() => { setCreateCol(col.id); setShowCreateTask(true) }}
                        style={{ padding: '14px 0', textAlign: 'center', fontSize: 12, color: '#D1D5DB', border: '1.5px dashed #E5E7EB', borderRadius: 10, cursor: 'pointer', transition: 'all 0.15s' }}
                        onMouseEnter={e => Object.assign((e.currentTarget as HTMLElement).style, { color: '#9CA3AF', borderColor: '#D1D5DB', background: '#FAFAFA' })}
                        onMouseLeave={e => Object.assign((e.currentTarget as HTMLElement).style, { color: '#D1D5DB', borderColor: '#E5E7EB', background: 'transparent' })}>
                        + Add task
                      </div>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* Modals */}
      {showCreateTask && selectedBoard && (
        <CreateTaskModal columns={columns} boardId={selectedBoard.id} defaultColumnId={createCol} users={userOptions}
          onClose={() => { setShowCreateTask(false); setCreateCol(null) }}
          onSaved={() => { refreshTasks(); qc.invalidateQueries({ queryKey: ['tasks-summary'] }) }} />
      )}
      {showCreateBoard && (
        <CreateBoardModal onClose={() => setShowCreateBoard(false)}
          onSaved={() => qc.invalidateQueries({ queryKey: ['task-boards'] })} />
      )}
      {selectedTask && (
        <TaskDetailModal task={selectedTask} columns={columns} users={userOptions} onClose={() => setSelectedTask(null)}
          onUpdate={data => updateTask.mutate({ id: selectedTask.id, data })}
          onDelete={() => deleteTask.mutate(selectedTask.id)}
          onComplete={() => completeTask.mutate(selectedTask.id)}
          onMove={columnId => moveTask.mutate({ taskId: selectedTask.id, columnId })}
          onRefresh={async () => {
            await refreshTasks()
            const r = await apiClient.get(`/api/v1/tasks/${selectedTask.id}`)
            setSelectedTask(r.data?.data ?? r.data)
          }} />
      )}

      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
    </div>
  )
}
