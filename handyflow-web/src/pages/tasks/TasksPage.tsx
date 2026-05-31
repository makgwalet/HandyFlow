import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, CheckSquare, X, Clock, ChevronRight, Trash2, Check,
  MessageSquare, Timer, AlertCircle, User, Calendar, Flag,
  MoreHorizontal, ArrowRight, ChevronLeft, Loader2, Edit3,
  BarChart2, TrendingUp, AlertTriangle
} from 'lucide-react'

// ── Types ─────────────────────────────────────────────────────────────────────

interface Column { id: string; name: string; sortOrder: number; color: string | null; isDoneColumn: boolean }
interface Board  { id: string; name: string; description: string | null; color: string | null; columns: Column[] }
interface TaskComment { id: string; authorId: string; authorName: string; body: string; createdAt: string }
interface TimeLog { id: string; userId: string; userName: string; hours: number; description: string | null; loggedDate: string; createdAt: string }
interface Task {
  id: string; boardId: string; columnId: string; columnName: string | null
  title: string; description: string | null
  priority: string; status: string
  assigneeId: string | null; assigneeName: string | null
  dueDate: string | null; overdue: boolean
  estimatedHours: number | null; loggedHours: number | null
  sortOrder: number
  linkedEntityType: string | null; linkedEntityId: string | null
  commentCount: number; comments: TaskComment[]
  createdAt: string; updatedAt: string; completedAt: string | null
}
interface Summary {
  totalTasks: number; todoCount: number; inProgressCount: number
  inReviewCount: number; doneCount: number; overdueCount: number; myTasksCount: number
}

// ── Constants ─────────────────────────────────────────────────────────────────

const PRIORITY: Record<string, { label: string; color: string; bg: string; border: string; dot: string }> = {
  URGENT: { label: 'Urgent', color: '#B91C1C', bg: '#FEF2F2', border: '#FECACA', dot: '#EF4444' },
  HIGH:   { label: 'High',   color: '#B45309', bg: '#FFFBEB', border: '#FDE68A', dot: '#F59E0B' },
  NORMAL: { label: 'Normal', color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE', dot: '#3B82F6' },
  LOW:    { label: 'Low',    color: '#475569', bg: '#F8FAFC', border: '#E2E8F0', dot: '#94A3B8' },
}

const STATUS_COLOR: Record<string, string> = {
  TODO: '#94A3B8', IN_PROGRESS: '#3B82F6', IN_REVIEW: '#F59E0B', DONE: '#10B981',
}

// ── Small helpers ─────────────────────────────────────────────────────────────

const fmtDate = (d: string | null) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: '2-digit' }) : null

const isOverdue = (dueDate: string | null) =>
  dueDate ? new Date(dueDate) < new Date() : false

const initials = (name: string | null) =>
  name ? name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() : '?'

// ── Reusable UI primitives ────────────────────────────────────────────────────

const Field = ({ label, children, error }: { label: string; children: React.ReactNode; error?: string }) => (
  <div>
    <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6 }}>{label}</label>
    {children}
    {error && <div style={{ fontSize: 12, color: '#EF4444', marginTop: 4 }}>{error}</div>}
  </div>
)

const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px', border: '1px solid #E5E7EB', borderRadius: 8,
  fontSize: 14, boxSizing: 'border-box', background: '#fff', color: '#111827',
  outline: 'none', transition: 'border-color 0.15s',
}

const Badge = ({ priority }: { priority: string }) => {
  const p = PRIORITY[priority] || PRIORITY.NORMAL
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      background: p.bg, color: p.color, border: `1px solid ${p.border}`,
      padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700
    }}>
      <span style={{ width: 5, height: 5, borderRadius: '50%', background: p.dot, flexShrink: 0 }} />
      {p.label}
    </span>
  )
}

const Avatar = ({ name, size = 26 }: { name: string | null; size?: number }) => (
  <div style={{
    width: size, height: size, borderRadius: '50%', background: '#1B3A6B',
    color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
    fontSize: size * 0.36, fontWeight: 700, flexShrink: 0,
  }}>{initials(name)}</div>
)

const ProgressBar = ({ value, max, color = '#1B3A6B' }: { value: number; max: number; color?: string }) => {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0
  return (
    <div style={{ height: 4, background: '#F1F5F9', borderRadius: 99, overflow: 'hidden' }}>
      <div style={{ width: `${pct}%`, height: '100%', background: color, borderRadius: 99, transition: 'width 0.3s' }} />
    </div>
  )
}

// ── Task Detail Modal ─────────────────────────────────────────────────────────

function TaskDetailModal({
  task, columns, onClose, onUpdate, onDelete, onComplete, onMove,
}: {
  task: Task; columns: Column[]
  onClose: () => void
  onUpdate: (data: Partial<Task>) => void
  onDelete: () => void
  onComplete: () => void
  onMove: (columnId: string) => void
}) {
  const qc = useQueryClient()
  const [tab, setTab] = useState<'details' | 'comments' | 'time'>('details')
  const [editTitle, setEditTitle] = useState(false)
  const [title, setTitle] = useState(task.title)
  const [comment, setComment] = useState('')
  const [hours, setHours] = useState('')
  const [hoursDesc, setHoursDesc] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(false)

  const addComment = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/tasks/${task.id}/comments`, { body: comment }),
    onSuccess: () => { setComment(''); qc.invalidateQueries({ queryKey: ['task', task.id] }) },
  })

  const logTime = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/tasks/${task.id}/time`, {
      hours: parseFloat(hours), description: hoursDesc || null,
      loggedDate: new Date().toISOString().split('T')[0],
    }),
    onSuccess: () => { setHours(''); setHoursDesc(''); qc.invalidateQueries({ queryKey: ['task', task.id] }) },
  })

  const logged = task.loggedHours ?? 0
  const estimated = task.estimatedHours ?? 0
  const timeColor = estimated > 0 && logged > estimated ? '#EF4444' : '#10B981'

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'flex-start', justifyContent: 'center', zIndex: 1000, padding: '40px 20px', overflowY: 'auto' }}>
      <div style={{ background: '#fff', borderRadius: 16, width: '100%', maxWidth: 680, boxShadow: '0 25px 80px rgba(0,0,0,0.25)', position: 'relative' }}
        onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div style={{ padding: '20px 24px 0', borderBottom: '1px solid #F1F5F9' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
            <div style={{ flex: 1, marginRight: 12 }}>
              {editTitle ? (
                <input value={title} onChange={e => setTitle(e.target.value)}
                  onBlur={() => { onUpdate({ title }); setEditTitle(false) }}
                  onKeyDown={e => e.key === 'Enter' && (onUpdate({ title }), setEditTitle(false))}
                  style={{ ...inp, fontSize: 18, fontWeight: 700, border: '2px solid #1B3A6B', padding: '4px 8px' }}
                  autoFocus />
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}
                  onClick={() => setEditTitle(true)}>
                  <h2 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: '#111827', lineHeight: 1.3 }}>{task.title}</h2>
                  <Edit3 size={14} color="#9CA3AF" />
                </div>
              )}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8, flexWrap: 'wrap' as const }}>
                <Badge priority={task.priority} />
                <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: STATUS_COLOR[task.status] || '#94A3B8', fontWeight: 600 }}>
                  <span style={{ width: 6, height: 6, borderRadius: '50%', background: STATUS_COLOR[task.status] || '#94A3B8' }} />
                  {task.status?.replace('_', ' ')}
                </span>
                {task.overdue && (
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#EF4444', fontWeight: 600 }}>
                    <AlertTriangle size={12} /> Overdue
                  </span>
                )}
              </div>
            </div>
            <div style={{ display: 'flex', gap: 6 }}>
              {task.completedAt === null && (
                <button onClick={onComplete} title="Mark complete"
                  style={{ background: '#F0FDF4', border: '1px solid #BBF7D0', color: '#166534', borderRadius: 8, padding: '7px 12px', fontSize: 12, fontWeight: 600, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 5 }}>
                  <Check size={13} /> Done
                </button>
              )}
              {confirmDelete ? (
                <div style={{ display: 'flex', gap: 4 }}>
                  <button onClick={() => setConfirmDelete(false)} style={{ background: '#F1F5F9', border: 'none', borderRadius: 7, padding: '7px 10px', fontSize: 12, cursor: 'pointer', color: '#374151' }}>Cancel</button>
                  <button onClick={onDelete} style={{ background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 7, padding: '7px 10px', fontSize: 12, cursor: 'pointer', color: '#DC2626', fontWeight: 600 }}>Delete</button>
                </div>
              ) : (
                <button onClick={() => setConfirmDelete(true)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 7, borderRadius: 7 }}>
                  <Trash2 size={15} />
                </button>
              )}
              <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 7, borderRadius: 7 }}>
                <X size={18} />
              </button>
            </div>
          </div>

          {/* Move column */}
          <div style={{ display: 'flex', gap: 6, paddingBottom: 14, flexWrap: 'wrap' as const }}>
            {columns.filter(c => c.id !== task.columnId).map(c => (
              <button key={c.id} onClick={() => onMove(c.id)}
                style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, padding: '4px 10px', borderRadius: 6, border: '1px solid #E5E7EB', background: '#F9FAFB', cursor: 'pointer', color: '#374151' }}>
                <ArrowRight size={11} />
                {c.name}
              </button>
            ))}
          </div>

          {/* Tabs */}
          <div style={{ display: 'flex', gap: 0, borderBottom: 'none', marginTop: 4 }}>
            {(['details', 'comments', 'time'] as const).map(t => (
              <button key={t} onClick={() => setTab(t)}
                style={{
                  padding: '8px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
                  border: 'none', background: 'none', color: tab === t ? '#1B3A6B' : '#9CA3AF',
                  borderBottom: `2px solid ${tab === t ? '#1B3A6B' : 'transparent'}`,
                  textTransform: 'capitalize', transition: 'all 0.15s',
                }}>
                {t === 'comments' ? `Comments (${task.commentCount})` : t === 'time' ? 'Time' : 'Details'}
              </button>
            ))}
          </div>
        </div>

        {/* Body */}
        <div style={{ padding: '20px 24px 24px' }}>
          {tab === 'details' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 220px', gap: 24 }}>
              <div>
                <div style={{ fontSize: 13, color: '#6B7280', fontWeight: 500, marginBottom: 6 }}>Description</div>
                <div style={{ fontSize: 14, color: '#374151', lineHeight: 1.6, minHeight: 60, background: '#F9FAFB', borderRadius: 8, padding: '10px 12px' }}>
                  {task.description || <span style={{ color: '#D1D5DB' }}>No description</span>}
                </div>

                {estimated > 0 && (
                  <div style={{ marginTop: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#6B7280', marginBottom: 6 }}>
                      <span style={{ fontWeight: 600 }}>Time Progress</span>
                      <span style={{ color: timeColor, fontWeight: 700 }}>{logged}h / {estimated}h</span>
                    </div>
                    <ProgressBar value={logged} max={estimated} color={timeColor} />
                  </div>
                )}
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                {[
                  { icon: <User size={13} />, label: 'Assignee', value: task.assigneeName || 'Unassigned' },
                  { icon: <Calendar size={13} />, label: 'Due Date', value: fmtDate(task.dueDate) || 'No date' },
                  { icon: <Flag size={13} />, label: 'Priority', value: task.priority },
                  { icon: <BarChart2 size={13} />, label: 'Column', value: task.columnName || '—' },
                  { icon: <Timer size={13} />, label: 'Estimated', value: task.estimatedHours ? `${task.estimatedHours}h` : '—' },
                  { icon: <Clock size={13} />, label: 'Logged', value: task.loggedHours ? `${task.loggedHours}h` : '0h' },
                ].map(({ icon, label, value }) => (
                  <div key={label} style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                    <div style={{ color: '#9CA3AF', marginTop: 1, flexShrink: 0 }}>{icon}</div>
                    <div>
                      <div style={{ fontSize: 11, color: '#9CA3AF', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>{label}</div>
                      <div style={{ fontSize: 13, color: '#374151', fontWeight: 500, marginTop: 1 }}>{value}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {tab === 'comments' && (
            <div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginBottom: 16 }}>
                {task.comments.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '24px 0', color: '#D1D5DB', fontSize: 13 }}>No comments yet</div>
                ) : task.comments.map(c => (
                  <div key={c.id} style={{ display: 'flex', gap: 10 }}>
                    <Avatar name={c.authorName} size={30} />
                    <div style={{ flex: 1, background: '#F9FAFB', borderRadius: 10, padding: '10px 14px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                        <span style={{ fontSize: 13, fontWeight: 700, color: '#111827' }}>{c.authorName}</span>
                        <span style={{ fontSize: 11, color: '#9CA3AF' }}>{fmtDate(c.createdAt)}</span>
                      </div>
                      <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.5 }}>{c.body}</div>
                    </div>
                  </div>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <textarea value={comment} onChange={e => setComment(e.target.value)}
                  rows={2} placeholder="Add a comment..."
                  style={{ ...inp, flex: 1, resize: 'none' as const, fontSize: 13 }} />
                <button onClick={() => addComment.mutate()} disabled={!comment.trim() || addComment.isPending}
                  style={{ ...btnPrimary, alignSelf: 'flex-end', padding: '9px 16px', fontSize: 13 }}>
                  {addComment.isPending ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : 'Send'}
                </button>
              </div>
            </div>
          )}

          {tab === 'time' && (
            <div>
              <div style={{ background: '#F9FAFB', borderRadius: 10, padding: '14px 16px', marginBottom: 16, display: 'flex', gap: 20 }}>
                <div>
                  <div style={{ fontSize: 11, color: '#9CA3AF', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Logged</div>
                  <div style={{ fontSize: 22, fontWeight: 700, color: '#111827', marginTop: 2 }}>{logged}h</div>
                </div>
                {estimated > 0 && (
                  <>
                    <div style={{ width: 1, background: '#E5E7EB' }} />
                    <div>
                      <div style={{ fontSize: 11, color: '#9CA3AF', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Estimated</div>
                      <div style={{ fontSize: 22, fontWeight: 700, color: '#111827', marginTop: 2 }}>{estimated}h</div>
                    </div>
                    <div style={{ width: 1, background: '#E5E7EB' }} />
                    <div>
                      <div style={{ fontSize: 11, color: '#9CA3AF', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Remaining</div>
                      <div style={{ fontSize: 22, fontWeight: 700, color: Math.max(0, estimated - logged) === 0 ? '#EF4444' : '#10B981', marginTop: 2 }}>
                        {Math.max(0, estimated - logged)}h
                      </div>
                    </div>
                  </>
                )}
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
                <Field label="Hours *">
                  <input type="number" min="0.1" step="0.5" value={hours} onChange={e => setHours(e.target.value)} placeholder="e.g. 2.5" style={inp} />
                </Field>
                <Field label="Description">
                  <input value={hoursDesc} onChange={e => setHoursDesc(e.target.value)} placeholder="What did you work on?" style={inp} />
                </Field>
              </div>
              <button onClick={() => logTime.mutate()} disabled={!hours || logTime.isPending}
                style={{ ...btnPrimary, width: '100%', justifyContent: 'center' }}>
                {logTime.isPending ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <><Timer size={14} /> Log Time</>}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

// ── Create/Edit Task Modal ────────────────────────────────────────────────────

function TaskModal({
  columns, boardId, defaultColumnId, onClose, onSaved,
}: {
  columns: Column[]; boardId: string; defaultColumnId: string | null
  onClose: () => void; onSaved: () => void
}) {
  const [form, setForm] = useState({
    title: '', description: '', priority: 'NORMAL',
    assigneeName: '', dueDate: '', estimatedHours: '', columnId: defaultColumnId || columns[0]?.id || '',
  })
  const [error, setError] = useState('')
  const f = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))

  const create = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/tasks/boards/${boardId}/tasks`, {
      title: form.title,
      description: form.description || null,
      priority: form.priority,
      assigneeName: form.assigneeName || null,
      dueDate: form.dueDate || null,
      estimatedHours: form.estimatedHours ? parseFloat(form.estimatedHours) : null,
      columnId: form.columnId,
    }),
    onSuccess: () => { onSaved(); onClose() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to create task. Please try again.'),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20 }}>
      <div style={{ background: '#fff', borderRadius: 16, width: '100%', maxWidth: 520, boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }}
        onClick={e => e.stopPropagation()}>
        <div style={{ padding: '20px 24px', borderBottom: '1px solid #F1F5F9', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#111827' }}>Create Task</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 4 }}><X size={18} /></button>
        </div>
        <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Field label="Title *">
            <input value={form.title} onChange={e => f('title', e.target.value)} placeholder="Task title" style={inp} autoFocus />
          </Field>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Priority">
              <select value={form.priority} onChange={e => f('priority', e.target.value)} style={inp}>
                {Object.entries(PRIORITY).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
              </select>
            </Field>
            <Field label="Column">
              <select value={form.columnId} onChange={e => f('columnId', e.target.value)} style={inp}>
                {columns.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Field label="Assignee">
              <input value={form.assigneeName} onChange={e => f('assigneeName', e.target.value)} placeholder="Name" style={inp} />
            </Field>
            <Field label="Due Date">
              <input type="date" value={form.dueDate} onChange={e => f('dueDate', e.target.value)} style={inp} />
            </Field>
          </div>
          <Field label="Estimated Hours">
            <input type="number" min="0" step="0.5" value={form.estimatedHours} onChange={e => f('estimatedHours', e.target.value)} placeholder="e.g. 4" style={inp} />
          </Field>
          <Field label="Description">
            <textarea value={form.description} onChange={e => f('description', e.target.value)} rows={3}
              placeholder="What needs to be done?" style={{ ...inp, resize: 'none' as const }} />
          </Field>
          {error && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, padding: '10px 12px' }}>
              <AlertCircle size={14} color="#EF4444" />
              <span style={{ fontSize: 13, color: '#DC2626' }}>{error}</span>
            </div>
          )}
        </div>
        <div style={{ padding: '0 24px 20px', display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button onClick={onClose} style={btnSecondary}>Cancel</button>
          <button onClick={() => create.mutate()} disabled={!form.title.trim() || create.isPending} style={btnPrimary}>
            {create.isPending ? <><Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> Creating...</> : <><Plus size={14} /> Create Task</>}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Task Card ─────────────────────────────────────────────────────────────────

function TaskCard({ task, columns, onMoveTask, onClick }: {
  task: Task; columns: Column[]
  onMoveTask: (taskId: string, columnId: string) => void
  onClick: () => void
}) {
  const [showMenu, setShowMenu] = useState(false)
  const due = task.dueDate ? new Date(task.dueDate) : null
  const overdue = due && due < new Date() && !task.completedAt
  const otherCols = columns.filter(c => c.id !== task.columnId)

  return (
    <div onClick={onClick} style={{
      background: '#fff', border: `1px solid ${overdue ? '#FECACA' : '#E5E7EB'}`,
      borderRadius: 10, padding: '13px 14px', cursor: 'pointer',
      transition: 'all 0.15s', position: 'relative',
      boxShadow: overdue ? '0 0 0 1px #FECACA inset' : 'none',
    }}
      onMouseEnter={e => { (e.currentTarget as HTMLElement).style.boxShadow = overdue ? '0 0 0 1px #FECACA inset, 0 4px 12px rgba(0,0,0,0.08)' : '0 4px 12px rgba(0,0,0,0.08)' }}
      onMouseLeave={e => { (e.currentTarget as HTMLElement).style.boxShadow = overdue ? '0 0 0 1px #FECACA inset' : 'none' }}>

      {task.completedAt && (
        <div style={{ position: 'absolute', top: 10, right: 10, background: '#D1FAE5', borderRadius: '50%', padding: 3 }}>
          <Check size={9} color="#059669" />
        </div>
      )}

      <div style={{ fontSize: 13, fontWeight: 600, color: task.completedAt ? '#9CA3AF' : '#111827', marginBottom: 8, lineHeight: 1.4, textDecoration: task.completedAt ? 'line-through' : 'none', paddingRight: task.completedAt ? 20 : 0 }}>
        {task.title}
      </div>

      {task.description && (
        <div style={{ fontSize: 12, color: '#9CA3AF', marginBottom: 8, lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
          {task.description}
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: otherCols.length > 0 ? 8 : 0 }}>
        <Badge priority={task.priority} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
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
        </div>
      </div>

      {task.estimatedHours && task.estimatedHours > 0 && (
        <div style={{ marginBottom: 8 }}>
          <ProgressBar value={task.loggedHours ?? 0} max={task.estimatedHours} color={task.loggedHours && task.loggedHours > task.estimatedHours ? '#EF4444' : '#1B3A6B'} />
        </div>
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        {task.assigneeName ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <Avatar name={task.assigneeName} size={20} />
            <span style={{ fontSize: 11, color: '#6B7280' }}>{task.assigneeName}</span>
          </div>
        ) : <div />}

        {otherCols.length > 0 && (
          <div style={{ display: 'flex', gap: 3 }} onClick={e => e.stopPropagation()}>
            {otherCols.slice(0, 2).map(c => (
              <button key={c.id} onClick={() => onMoveTask(task.id, c.id)}
                style={{ fontSize: 10, padding: '2px 7px', borderRadius: 5, border: '1px solid #E5E7EB', background: '#F9FAFB', cursor: 'pointer', color: '#6B7280', display: 'flex', alignItems: 'center', gap: 3 }}>
                <ArrowRight size={9} />{c.name}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// ── Main TasksPage ────────────────────────────────────────────────────────────

export function TasksPage() {
  const qc = useQueryClient()
  const [selectedBoard, setSelectedBoard] = useState<Board | null>(null)
  const [tasks, setTasks] = useState<Task[]>([])
  const [loading, setLoading] = useState(false)
  const [showCreate, setShowCreate] = useState(false)
  const [createCol, setCreateCol] = useState<string | null>(null)
  const [selectedTask, setSelectedTask] = useState<Task | null>(null)
  const [filter, setFilter] = useState<'all' | 'mine' | 'overdue'>('all')

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
      return r.data
    },
    refetchInterval: 30_000,
  })

  const loadBoard = async (board: Board) => {
    setSelectedBoard(board)
    setLoading(true)
    try {
      const r = await apiClient.get(`/api/v1/tasks/boards/${board.id}/tasks?size=500`)
      setTasks(r.data?.content || [])
    } catch { setTasks([]) }
    finally { setLoading(false) }
  }

  const refreshTasks = async () => {
    if (!selectedBoard) return
    const r = await apiClient.get(`/api/v1/tasks/boards/${selectedBoard.id}/tasks?size=500`)
    setTasks(r.data?.content || [])
  }

  const openTask = async (task: Task) => {
    // Fetch full task detail (with comments)
    try {
      const r = await apiClient.get(`/api/v1/tasks/${task.id}`)
      setSelectedTask(r.data)
    } catch { setSelectedTask(task) }
  }

  const moveTask = useMutation({
    mutationFn: ({ taskId, columnId }: { taskId: string; columnId: string }) =>
      apiClient.post(`/api/v1/tasks/${taskId}/move`, { columnId, sortOrder: 0 }),
    onMutate: ({ taskId, columnId }) => {
      // Optimistic update
      setTasks(prev => prev.map(t => t.id === taskId ? { ...t, columnId } : t))
      if (selectedTask?.id === taskId) setSelectedTask(prev => prev ? { ...prev, columnId } : null)
    },
    onSuccess: refreshTasks,
  })

  const updateTask = useMutation({
    mutationFn: ({ id, data }: { id: string; data: any }) =>
      apiClient.put(`/api/v1/tasks/${id}`, data),
    onSuccess: async () => { await refreshTasks() },
  })

  const deleteTask = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/tasks/${id}`),
    onSuccess: () => {
      setSelectedTask(null)
      refreshTasks()
      qc.invalidateQueries({ queryKey: ['tasks-summary'] })
    },
  })

  const completeTask = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/tasks/${id}/complete`),
    onSuccess: async () => {
      await refreshTasks()
      qc.invalidateQueries({ queryKey: ['tasks-summary'] })
      // Re-fetch selected task
      if (selectedTask) {
        const r = await apiClient.get(`/api/v1/tasks/${selectedTask.id}`)
        setSelectedTask(r.data)
      }
    },
  })

  const columns = selectedBoard?.columns ?? []

  const filteredTasks = tasks.filter(t => {
    if (filter === 'overdue') return t.overdue
    return true
  })

  const totalTasks = summary?.totalTasks ?? (summary as any)?.todoCount + (summary as any)?.inProgressCount + (summary as any)?.inReviewCount + (summary as any)?.doneCount ?? 0

  const statCards = [
    { label: 'Total Tasks',  value: (summary as any)?.totalTasks ?? tasks.length,                color: '#1B3A6B', icon: <CheckSquare size={16} /> },
    { label: 'In Progress',  value: (summary as any)?.inProgressCount ?? 0,                      color: '#2563EB', icon: <TrendingUp size={16} /> },
    { label: 'Done',         value: (summary as any)?.doneCount ?? 0,                            color: '#059669', icon: <Check size={16} /> },
    { label: 'Overdue',      value: (summary as any)?.overdueCount ?? tasks.filter(t => t.overdue).length, color: '#DC2626', icon: <AlertTriangle size={16} /> },
  ]

  return (
    <div style={{ fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }}>
      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>

      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: '#111827', margin: '0 0 4px', letterSpacing: '-0.02em' }}>Tasks</h1>
        <p style={{ fontSize: 14, color: '#6B7280', margin: 0 }}>Kanban boards for team task management</p>
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12, marginBottom: 24 }}>
        {statCards.map(s => (
          <div key={s.label} style={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 12, padding: '16px 18px', display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 38, height: 38, borderRadius: 10, background: `${s.color}15`, display: 'flex', alignItems: 'center', justifyContent: 'center', color: s.color, flexShrink: 0 }}>
              {s.icon}
            </div>
            <div>
              <div style={{ fontSize: 24, fontWeight: 800, color: s.color, letterSpacing: '-0.02em' }}>{s.value}</div>
              <div style={{ fontSize: 12, color: '#9CA3AF', marginTop: 1 }}>{s.label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Board List */}
      {!selectedBoard ? (
        <div style={{ background: '#fff', border: '1px solid #E5E7EB', borderRadius: 14, padding: 24 }}>
          <div style={{ fontSize: 14, fontWeight: 700, color: '#374151', marginBottom: 16, textTransform: 'uppercase', letterSpacing: '0.06em', fontSize: 11, color: '#9CA3AF' }}>Select a Board</div>
          {boardsLoading ? (
            <div style={{ textAlign: 'center', padding: 40, color: '#D1D5DB', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
              <Loader2 size={18} style={{ animation: 'spin 1s linear infinite' }} /> Loading boards...
            </div>
          ) : boards.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '48px 20px' }}>
              <div style={{ width: 56, height: 56, borderRadius: 14, background: '#F1F5F9', margin: '0 auto 16px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <CheckSquare size={24} color="#D1D5DB" />
              </div>
              <div style={{ fontWeight: 700, color: '#374151', fontSize: 15 }}>No boards yet</div>
              <div style={{ color: '#9CA3AF', fontSize: 13, marginTop: 4 }}>Create a board to start managing tasks</div>
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 12 }}>
              {boards.map(board => (
                <div key={board.id} onClick={() => loadBoard(board)}
                  style={{ border: '1px solid #E5E7EB', borderRadius: 12, padding: '18px 20px', cursor: 'pointer', transition: 'all 0.15s', background: '#FAFAFA' }}
                  onMouseEnter={e => { Object.assign((e.currentTarget as HTMLElement).style, { background: '#fff', boxShadow: '0 4px 16px rgba(0,0,0,0.08)', borderColor: '#D1D5DB' }) }}
                  onMouseLeave={e => { Object.assign((e.currentTarget as HTMLElement).style, { background: '#FAFAFA', boxShadow: 'none', borderColor: '#E5E7EB' }) }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                    <div style={{ width: 36, height: 36, borderRadius: 9, background: board.color ? `${board.color}20` : '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <CheckSquare size={16} color={board.color || '#1D4ED8'} />
                    </div>
                    <ChevronRight size={15} color="#D1D5DB" />
                  </div>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#111827' }}>{board.name}</div>
                  {board.description && <div style={{ fontSize: 12, color: '#9CA3AF', marginTop: 3, lineHeight: 1.4 }}>{board.description}</div>}
                  <div style={{ display: 'flex', gap: 5, marginTop: 12 }}>
                    {board.columns?.slice(0, 6).map(col => (
                      <div key={col.id} title={col.name} style={{ width: 7, height: 7, borderRadius: '50%', background: col.color || '#94A3B8' }} />
                    ))}
                    <span style={{ fontSize: 11, color: '#9CA3AF', marginLeft: 2 }}>{board.columns?.length ?? 0} columns</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      ) : (
        <div>
          {/* Board header */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 18, flexWrap: 'wrap' as const }}>
            <button onClick={() => { setSelectedBoard(null); setTasks([]) }}
              style={{ display: 'flex', alignItems: 'center', gap: 5, background: '#F1F5F9', border: 'none', borderRadius: 8, padding: '7px 12px', fontSize: 13, cursor: 'pointer', color: '#374151', fontWeight: 500 }}>
              <ChevronLeft size={14} /> All Boards
            </button>
            <h2 style={{ margin: 0, fontSize: 17, fontWeight: 800, color: '#111827', letterSpacing: '-0.01em' }}>{selectedBoard.name}</h2>

            {/* Filters */}
            <div style={{ display: 'flex', gap: 4, marginLeft: 8 }}>
              {(['all', 'overdue'] as const).map(f => (
                <button key={f} onClick={() => setFilter(f)}
                  style={{ fontSize: 12, padding: '5px 11px', borderRadius: 6, border: '1px solid #E5E7EB', cursor: 'pointer', fontWeight: 500,
                    background: filter === f ? '#1B3A6B' : '#fff', color: filter === f ? '#fff' : '#6B7280' }}>
                  {f === 'all' ? 'All' : 'Overdue'}
                </button>
              ))}
            </div>

            <button onClick={() => { setCreateCol(columns[0]?.id ?? null); setShowCreate(true) }}
              style={{ ...btnPrimary, marginLeft: 'auto' }}>
              <Plus size={14} /> New Task
            </button>
          </div>

          {/* Kanban board */}
          {loading ? (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 60, color: '#9CA3AF', gap: 10 }}>
              <Loader2 size={20} style={{ animation: 'spin 1s linear infinite' }} /> Loading tasks...
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 14, overflowX: 'auto', paddingBottom: 16, alignItems: 'flex-start' }}>
              {[...columns].sort((a, b) => a.sortOrder - b.sortOrder).map(col => {
                const colTasks = filteredTasks
                  .filter(t => t.columnId === col.id)
                  .sort((a, b) => a.sortOrder - b.sortOrder)
                return (
                  <div key={col.id} style={{ minWidth: 280, maxWidth: 300, flexShrink: 0 }}>
                    {/* Column header */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, padding: '0 2px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                        <div style={{ width: 8, height: 8, borderRadius: '50%', background: col.color || '#94A3B8' }} />
                        <span style={{ fontSize: 13, fontWeight: 700, color: '#374151' }}>{col.name}</span>
                        <span style={{ background: '#F1F5F9', color: '#6B7280', borderRadius: 20, padding: '1px 8px', fontSize: 11, fontWeight: 700 }}>{colTasks.length}</span>
                      </div>
                      <button onClick={() => { setCreateCol(col.id); setShowCreate(true) }}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#9CA3AF', padding: 4, borderRadius: 6, transition: 'all 0.1s' }}>
                        <Plus size={15} />
                      </button>
                    </div>

                    {/* Cards */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minHeight: 60 }}>
                      {colTasks.map(task => (
                        <TaskCard key={task.id} task={task} columns={columns}
                          onMoveTask={(taskId, columnId) => moveTask.mutate({ taskId, columnId })}
                          onClick={() => openTask(task)} />
                      ))}
                      {colTasks.length === 0 && (
                        <div onClick={() => { setCreateCol(col.id); setShowCreate(true) }}
                          style={{ padding: '20px 0', textAlign: 'center', fontSize: 12, color: '#D1D5DB', border: '1px dashed #E5E7EB', borderRadius: 10, cursor: 'pointer', transition: 'all 0.15s' }}
                          onMouseEnter={e => { Object.assign((e.currentTarget as HTMLElement).style, { color: '#9CA3AF', borderColor: '#D1D5DB', background: '#FAFAFA' }) }}
                          onMouseLeave={e => { Object.assign((e.currentTarget as HTMLElement).style, { color: '#D1D5DB', borderColor: '#E5E7EB', background: 'transparent' }) }}>
                          + Add task
                        </div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* Create Task Modal */}
      {showCreate && selectedBoard && (
        <TaskModal
          columns={columns}
          boardId={selectedBoard.id}
          defaultColumnId={createCol}
          onClose={() => { setShowCreate(false); setCreateCol(null) }}
          onSaved={() => { refreshTasks(); qc.invalidateQueries({ queryKey: ['tasks-summary'] }) }}
        />
      )}

      {/* Task Detail Modal */}
      {selectedTask && (
        <TaskDetailModal
          task={selectedTask}
          columns={columns}
          onClose={() => setSelectedTask(null)}
          onUpdate={data => updateTask.mutate({ id: selectedTask.id, data })}
          onDelete={() => deleteTask.mutate(selectedTask.id)}
          onComplete={() => completeTask.mutate(selectedTask.id)}
          onMove={columnId => moveTask.mutate({ taskId: selectedTask.id, columnId })}
        />
      )}
    </div>
  )
}

// ── Shared styles ─────────────────────────────────────────────────────────────

const btnPrimary: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6,
  background: '#1B3A6B', color: '#fff', border: 'none',
  borderRadius: 8, padding: '9px 16px', fontSize: 13, fontWeight: 600,
  cursor: 'pointer', transition: 'opacity 0.15s',
}

const btnSecondary: React.CSSProperties = {
  padding: '9px 16px', border: '1px solid #E5E7EB', borderRadius: 8,
  background: '#fff', fontSize: 13, cursor: 'pointer', color: '#374151', fontWeight: 500,
}
