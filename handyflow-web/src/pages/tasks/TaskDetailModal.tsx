// src/pages/tasks/TaskDetailModal.tsx
import { errorMessage } from '../../store/toast.store'
import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, Clock, Trash2, Check, MessageSquare, Timer, User, Calendar, Flag, Loader2, Edit3, AlertTriangle, Link2, Hash, CheckCircle2, Paperclip, Download, FileText,
} from 'lucide-react'
import type { Column, TimeLog, Attachment, ChecklistItem, Task, UserOption } from './tasks.types'
import { STATUS_COLOR, fmtDate, fmtDateFull, isOverdueDate, fmtFileSize, todayISO } from './tasks.constants'
import type { TaskUpdate } from './tasks.api'
import { toast } from '../../store/toast.store'
import { Badge, Avatar, ProgressBar, ConfirmModal } from './tasks.ui'
import { inp, lbl, btnPrimary, btnSecondary } from './tasks.styles'

const metaLabel: React.CSSProperties = {
  fontSize: 10, color: 'var(--hf-text-faint)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 3,
}
const metaInput: React.CSSProperties = {
  ...inp, fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', padding: '5px 6px',
}

export function TaskDetailModal({ task, columns, users, onClose, onUpdate, onDelete, onComplete, onMove, onRefresh, readOnly = false }: {
  task: Task; columns: Column[]; users: UserOption[]
  onClose: () => void
  /** Edit a field. To remove a value send its clear flag (clearAssignee, clearDueDate, ...): null means "no change". */
  onUpdate: (data: TaskUpdate) => void
  onDelete: () => void
  onComplete: () => void
  onMove: (columnId: string) => void
  onRefresh: () => void
  /** Users with TASKS_READ only: every control is disabled or hidden. */
  readOnly?: boolean
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
  const [hoursDate, setHoursDate] = useState(todayISO())
  const [showDelete, setShowDelete] = useState(false)
  const [est, setEst] = useState(task.estimatedHours != null ? String(task.estimatedHours) : '')

  const commitEstimate = () => {
    const typed = est.trim()
    if (typed === '') {
      if (task.estimatedHours != null) onUpdate({ clearEstimatedHours: true })
      return
    }
    const n = Number(typed)
    if (!Number.isFinite(n) || n < 0 || n > 9999.99) {
      setEst(task.estimatedHours != null ? String(task.estimatedHours) : '')
      toast.error('Enter an estimate between 0 and 9999.99 hours.')
      return
    }
    if (n !== task.estimatedHours) onUpdate({ estimatedHours: n })
  }

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
    onError: e => setUploadError(errorMessage(e, 'Failed to upload file')),
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
  const timeColor = overBudget ? 'var(--hf-danger-text)' : 'var(--hf-success-text)'
  const overdueFlag = isOverdueDate(task.dueDate, task.completedAt)

  return (
    <>
      <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'flex-start', justifyContent: 'center', zIndex: 1000, padding: '32px 20px', overflowY: 'auto' }}>
        <div style={{ background: 'var(--hf-surface)', borderRadius: 16, width: '100%', maxWidth: 720, boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }} onClick={e => e.stopPropagation()}>

          {/* Header */}
          <div style={{ padding: '20px 24px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 14 }}>
              <div style={{ flex: 1, marginRight: 12 }}>
                {editTitle ? (
                  <input value={title} onChange={e => setTitle(e.target.value)}
                    onBlur={() => { onUpdate({ title }); setEditTitle(false) }}
                    onKeyDown={e => { if (e.key === 'Enter') { onUpdate({ title }); setEditTitle(false) } if (e.key === 'Escape') setEditTitle(false) }}
                    style={{ ...inp, fontSize: 18, fontWeight: 700, border: '2px solid var(--hf-primary)', padding: '4px 8px', width: '100%' }} autoFocus />
                ) : (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' } as React.CSSProperties} onClick={() => { if (!readOnly) setEditTitle(true) }}>
                    <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, lineHeight: 1.3, textDecoration: task.completedAt ? 'line-through' : 'none', color: task.completedAt ? 'var(--hf-text-faint)' : 'var(--hf-text)' } as React.CSSProperties}>{task.title}</h2>
                    <Edit3 size={13} style={{ color: 'var(--hf-text-disabled)' }} />
                  </div>
                )}
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 10, flexWrap: 'wrap' as const }}>
                  <Badge priority={task.priority} />
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: STATUS_COLOR[task.status] || 'var(--hf-text-faint)', fontWeight: 700 }}>
                    <span style={{ width: 6, height: 6, borderRadius: '50%', background: STATUS_COLOR[task.status] || 'var(--hf-text-faint)' }} />
                    {task.status?.replace('_', ' ')}
                  </span>
                  {overdueFlag && (
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--hf-danger-text)', fontWeight: 700, background: 'var(--hf-danger-soft)', padding: '2px 8px', borderRadius: 20 }}>
                      <AlertTriangle size={11} /> Overdue
                    </span>
                  )}
                  {task.completedAt && (
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--hf-success-text)', fontWeight: 700, background: 'var(--hf-success-soft)', padding: '2px 8px', borderRadius: 20 }}>
                      <CheckCircle2 size={11} /> Completed
                    </span>
                  )}
                </div>
              </div>

              <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
                {!readOnly && !task.completedAt && (
                  <button onClick={onComplete} style={{ display: 'flex', alignItems: 'center', gap: 5, background: 'var(--hf-success-soft)', border: '1px solid var(--hf-success-border-subtle)', color: 'var(--hf-success-text-strong)', borderRadius: 8, padding: '7px 12px', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}>
                    <Check size={13} /> Mark done
                  </button>
                )}
                {!readOnly && (
                <button onClick={() => setShowDelete(true)} style={{ background: 'none', border: '1.5px solid var(--hf-border)', cursor: 'pointer', color: 'var(--hf-text-faint)', padding: '6px 8px', borderRadius: 7, display: 'flex' }}>
                  <Trash2 size={14} />
                </button>
                )}
                <button onClick={onClose} style={{ background: 'var(--hf-surface-sunken)', border: 'none', cursor: 'pointer', color: 'var(--hf-text-muted)', padding: '6px 8px', borderRadius: 7, display: 'flex' }}>
                  <X size={16} />
                </button>
              </div>
            </div>

            {/* Move to column buttons */}
            {columns.filter(c => c.id !== task.columnId).length > 0 && (
              <div style={{ display: 'flex', gap: 5, paddingBottom: 6, flexWrap: 'wrap' as const }}>
                <span style={{ fontSize: 11, color: 'var(--hf-text-faint)', alignSelf: 'center', marginRight: 4 }}>Move to:</span>
                {columns.filter(c => c.id !== task.columnId).map(c => (
                  <button key={c.id} onClick={() => onMove(c.id)}
                    style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, padding: '4px 10px', borderRadius: 20, border: '1px solid var(--hf-border)', background: 'var(--hf-surface-muted)', cursor: 'pointer', color: 'var(--hf-text-secondary)', fontWeight: 600, transition: 'all 0.1s' }}>
                    <div style={{ width: 6, height: 6, borderRadius: '50%', background: c.color || 'var(--hf-text-faint)' }} />
                    {c.name}
                  </button>
                ))}
              </div>
            )}

            {/* Tabs */}
            <div style={{ display: 'flex', gap: 0, marginTop: 6 }}>
              {(['details', 'comments', 'time', 'files'] as const).map(t => (
                <button key={t} onClick={() => setTab(t)} style={{ padding: '10px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer', border: 'none', background: 'none', color: tab === t ? 'var(--hf-primary-text)' : 'var(--hf-text-faint)', borderBottom: `2px solid ${tab === t ? 'var(--hf-primary)' : 'transparent'}`, marginBottom: -1, transition: 'all 0.15s' }}>
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
                        <button onClick={() => { onUpdate(desc.trim() ? { description: desc } : { clearDescription: true }); setEditDesc(false) }} style={{ ...btnPrimary, padding: '7px 14px', fontSize: 12 }}>Save</button>
                        <button onClick={() => { setDesc(task.description || ''); setEditDesc(false) }} style={{ ...btnSecondary, padding: '7px 12px', fontSize: 12 }}>Cancel</button>
                      </div>
                    </div>
                  ) : (
                    <div onClick={() => { if (!readOnly) setEditDesc(true) }} style={{ fontSize: 14, color: task.description ? 'var(--hf-text-secondary)' : 'var(--hf-text-disabled)', lineHeight: 1.7, background: 'var(--hf-surface-muted)', borderRadius: 9, padding: '12px 14px', minHeight: 80, cursor: 'pointer', border: '1.5px solid transparent', transition: 'border-color 0.15s' }}
                      onMouseEnter={e => (e.currentTarget as HTMLElement).style.borderColor = 'var(--hf-border-strong)'}
                      onMouseLeave={e => (e.currentTarget as HTMLElement).style.borderColor = 'transparent'}>
                      {task.description || 'Click to add a description...'}
                    </div>
                  )}

                  {/* Checklist */}
                  <div style={{ marginTop: 20 }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                      <label style={lbl}>Checklist</label>
                      {checklistItems.length > 0 && (
                        <span style={{ fontSize: 11, color: 'var(--hf-text-faint)', fontWeight: 700 }}>
                          {checklistItems.filter(i => i.completed).length}/{checklistItems.length}
                        </span>
                      )}
                    </div>
                    {checklistItems.length > 0 && (
                      <div style={{ marginBottom: 10 }}>
                        <ProgressBar
                          value={checklistItems.filter(i => i.completed).length}
                          max={checklistItems.length}
                          color={checklistItems.every(i => i.completed) ? 'var(--hf-success-text)' : 'var(--hf-primary-text)'} />
                      </div>
                    )}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 8 }}>
                      {checklistItems.map(item => (
                        <div key={item.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '5px 6px', borderRadius: 7 }}
                          onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = 'var(--hf-surface-muted)'}
                          onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                          <input type="checkbox" checked={item.completed}
                            onChange={e => toggleChecklistItem.mutate({ itemId: item.id, completed: e.target.checked })}
                            style={{ width: 15, height: 15, cursor: 'pointer', accentColor: 'var(--hf-primary)', flexShrink: 0 }} />
                          <span style={{ flex: 1, fontSize: 13, color: item.completed ? 'var(--hf-text-faint)' : 'var(--hf-text-secondary)', textDecoration: item.completed ? 'line-through' : 'none' }}>
                            {item.text}
                          </span>
                          <button onClick={() => deleteChecklistItem.mutate(item.id)}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-disabled)', padding: 2, display: 'flex', flexShrink: 0 }}>
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
                    <div style={{ marginTop: 20, padding: '14px 16px', background: 'var(--hf-surface-muted)', borderRadius: 10, border: '1px solid var(--hf-border)' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 8 }}>
                        <span style={{ fontWeight: 700, color: 'var(--hf-text-secondary)' }}>Time progress</span>
                        <span style={{ color: timeColor, fontWeight: 800 }}>{logged}h / {estimated}h {overBudget ? '— over budget' : ''}</span>
                      </div>
                      <ProgressBar value={logged} max={estimated} color={timeColor} />
                      <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 6 }}>
                        {overBudget ? `${(logged - estimated).toFixed(1)}h over estimate` : `${Math.max(0, estimated - logged).toFixed(1)}h remaining`}
                      </div>
                    </div>
                  )}

                  {/* Linked entity */}
                  {task.linkedEntityType && (
                    <div style={{ marginTop: 16, padding: '10px 14px', background: 'var(--hf-info-soft)', borderRadius: 9, border: '1px solid var(--hf-info-border)', display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Link2 size={13} style={{ color: 'var(--hf-info-text)' }} />
                      <span style={{ fontSize: 12, color: 'var(--hf-info-text)', fontWeight: 600 }}>Linked to {task.linkedEntityType.replace('_', ' ')}</span>
                      <span style={{ fontSize: 11, color: 'var(--hf-text-muted)', fontFamily: 'monospace' }}>{task.linkedEntityId?.slice(0, 8)}...</span>
                      {!readOnly && (
                        <button onClick={() => onUpdate({ clearLink: true })} aria-label="Remove link"
                          style={{ marginLeft: 'auto', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-muted)', fontSize: 11, fontWeight: 600, textDecoration: 'underline' }}>
                          Remove link
                        </button>
                      )}
                    </div>
                  )}
                </div>

                {/* Right — metadata sidebar */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <div style={{ color: 'var(--hf-text-faint)', marginTop: 4, flexShrink: 0 }}><User size={13} /></div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 10, color: 'var(--hf-text-faint)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 3 }}>Assignee</div>
                      <select
                        value={task.assigneeId || ''}
                        disabled={readOnly}
                        onChange={e => onUpdate(e.target.value ? { assigneeId: e.target.value } : { clearAssignee: true })}
                        style={{ ...inp, fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', padding: '5px 6px', background: 'var(--hf-surface)', border: '1.5px solid var(--hf-border)' }}>
                        <option value="">Unassigned</option>
                        {users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
                      </select>
                    </div>
                  </div>
                  {/* Editable fields. Clearing a value sends an explicit clear flag, because null would mean "no change". */}
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <div style={{ color: 'var(--hf-text-faint)', marginTop: 4, flexShrink: 0 }}><Calendar size={13} /></div>
                    <div style={{ flex: 1 }}>
                      <div style={metaLabel}>Due date{overdueFlag ? ' \u00b7 overdue' : ''}</div>
                      <input type="date" value={task.dueDate ?? ''} disabled={readOnly}
                        onChange={e => onUpdate(e.target.value ? { dueDate: e.target.value } : { clearDueDate: true })}
                        style={{ ...metaInput, color: overdueFlag ? 'var(--hf-danger-text)' : 'var(--hf-text-secondary)' }} />
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <div style={{ color: 'var(--hf-text-faint)', marginTop: 4, flexShrink: 0 }}><Flag size={13} /></div>
                    <div style={{ flex: 1 }}>
                      <div style={metaLabel}>Priority</div>
                      <select value={task.priority} disabled={readOnly} onChange={e => onUpdate({ priority: e.target.value })} style={metaInput}>
                        {(['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const).map(p => <option key={p} value={p}>{p.charAt(0) + p.slice(1).toLowerCase()}</option>)}
                      </select>
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <div style={{ color: 'var(--hf-text-faint)', marginTop: 4, flexShrink: 0 }}><Hash size={13} /></div>
                    <div style={{ flex: 1 }}>
                      <div style={metaLabel}>Column</div>
                      <select value={task.columnId} disabled={readOnly} onChange={e => onMove(e.target.value)} style={metaInput}>
                        {columns.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                      </select>
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <div style={{ color: 'var(--hf-text-faint)', marginTop: 4, flexShrink: 0 }}><Timer size={13} /></div>
                    <div style={{ flex: 1 }}>
                      <div style={metaLabel}>Estimated (hours)</div>
                      <input type="number" min={0} max={9999.99} step={0.25} inputMode="decimal" value={est} disabled={readOnly}
                        placeholder="—" onChange={e => setEst(e.target.value)} onBlur={commitEstimate}
                        onKeyDown={e => { if (e.key === 'Enter') (e.target as HTMLInputElement).blur() }} style={metaInput} />
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                    <div style={{ color: 'var(--hf-text-faint)', marginTop: 1, flexShrink: 0 }}><Clock size={13} /></div>
                    <div>
                      <div style={{ ...metaLabel, marginBottom: 0 }}>Logged</div>
                      <div style={{ fontSize: 13, color: 'var(--hf-text-secondary)', fontWeight: 600, marginTop: 2 }}>{logged > 0 ? `${logged}h` : '\u2014'}</div>
                    </div>
                  </div>
                  <div style={{ borderTop: '1px solid var(--hf-border-subtle)', paddingTop: 14 }}>
                    <div style={{ fontSize: 10, color: 'var(--hf-text-faint)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Created</div>
                    <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{fmtDateFull(task.createdAt)}</div>
                  </div>
                  {task.completedAt && (
                    <div>
                      <div style={{ fontSize: 10, color: 'var(--hf-text-faint)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Completed</div>
                      <div style={{ fontSize: 12, color: 'var(--hf-success-text)', fontWeight: 600 }}>{fmtDateFull(task.completedAt)}</div>
                    </div>
                  )}
                </div>
              </div>
            )}

            {tab === 'comments' && (
              <div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 20, maxHeight: 340, overflowY: 'auto' }}>
                  {task.comments.length === 0 ? (
                    <div style={{ textAlign: 'center', padding: '32px 0', color: 'var(--hf-text-disabled)' }}>
                      <MessageSquare size={28} style={{ marginBottom: 8, opacity: 0.5 }} />
                      <div style={{ fontSize: 13 }}>No comments yet — be the first</div>
                    </div>
                  ) : task.comments.map(c => (
                    <div key={c.id} style={{ display: 'flex', gap: 10 }}>
                      <Avatar name={c.authorName} size={32} />
                      <div style={{ flex: 1, background: 'var(--hf-surface-muted)', borderRadius: 10, padding: '11px 14px', border: '1px solid var(--hf-border)' }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
                          <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--hf-text)' }}>{c.authorName}</span>
                          <span style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{fmtDate(c.createdAt)}</span>
                        </div>
                        <div style={{ fontSize: 13, color: 'var(--hf-text-secondary)', lineHeight: 1.6 }}>{c.body}</div>
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
                <div style={{ display: 'flex', gap: 1, marginBottom: 20, background: 'var(--hf-surface-muted)', borderRadius: 12, overflow: 'hidden', border: '1px solid var(--hf-border)' }}>
                  {[
                    { label: 'Logged',    value: `${logged}h`,                                           color: 'var(--hf-primary-text)' },
                    { label: 'Estimated', value: estimated > 0 ? `${estimated}h` : '—',                  color: 'var(--hf-text-secondary)' },
                    { label: 'Remaining', value: estimated > 0 ? `${Math.max(0, estimated - logged).toFixed(1)}h` : '—', color: overBudget ? 'var(--hf-danger-text)' : 'var(--hf-success-text)' },
                  ].map((s, i) => (
                    <div key={s.label} style={{ flex: 1, padding: '16px 18px', borderLeft: i > 0 ? '1px solid var(--hf-border)' : 'none' }}>
                      <div style={{ fontSize: 10, color: 'var(--hf-text-faint)', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em' }}>{s.label}</div>
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
                    <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10 }}>Log history</div>
                    {(timeLogs as TimeLog[]).map(l => (
                      <div key={l.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '9px 12px', borderBottom: '1px solid var(--hf-border-subtle)', fontSize: 13 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                          <Avatar name={l.userName} size={24} />
                          <div>
                            <div style={{ fontWeight: 600, color: 'var(--hf-text)' }}>{l.userName}</div>
                            {l.description && <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>{l.description}</div>}
                          </div>
                        </div>
                        <div style={{ textAlign: 'right' as const }}>
                          <div style={{ fontWeight: 700, color: 'var(--hf-primary-text)' }}>{Number(l.hours).toFixed(1)}h</div>
                          <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{fmtDate(l.loggedDate)}</div>
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
                  <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginBottom: 12 }}>{uploadError}</div>
                )}

                {attachments.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '32px 0', color: 'var(--hf-text-disabled)' }}>
                    <Paperclip size={28} style={{ marginBottom: 8, opacity: 0.5 }} />
                    <div style={{ fontSize: 13 }}>No files attached yet</div>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {attachments.map(a => (
                      <div key={a.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 12px', background: 'var(--hf-surface-muted)', borderRadius: 9, border: '1px solid var(--hf-border)' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                          <FileText size={16} style={{ color: 'var(--hf-text-muted)', flexShrink: 0 }} />
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--hf-text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{a.fileName}</div>
                            <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{fmtFileSize(a.sizeBytes)} · {a.uploadedByName} · {fmtDate(a.createdAt)}</div>
                          </div>
                        </div>
                        <div style={{ display: 'flex', gap: 4, flexShrink: 0 }}>
                          <button onClick={() => downloadAttachment(a)} title="Download"
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-muted)', padding: 6, borderRadius: 6, display: 'flex' }}>
                            <Download size={14} />
                          </button>
                          <button onClick={() => deleteAttachment.mutate(a.id)} title="Delete"
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', padding: 6, borderRadius: 6, display: 'flex' }}>
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
