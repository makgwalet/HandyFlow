// src/pages/tasks/CreateTaskModal.tsx
import { errorMessage } from '../../store/toast.store'
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, AlertCircle, Loader2, Link2,
} from 'lucide-react'
import type { Column, UserOption } from './tasks.types'
import { PRIORITY, ENTITY_TYPES } from './tasks.constants'
import { inp, lbl, btnSecondary, btnPrimary } from './tasks.styles'

export function CreateTaskModal({ columns, boardId, defaultColumnId, users, onClose, onSaved }: {
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
    onError: e => setError(errorMessage(e, 'Failed to create task')),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20 }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 16, width: '100%', maxWidth: 540, boxShadow: '0 25px 80px rgba(0,0,0,0.25)' }} onClick={e => e.stopPropagation()}>
        <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--hf-border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: 'var(--hf-text)' }}>Create Task</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={lbl}>Title *</label>
            <input value={form.title} onChange={e => f('title', e.target.value)} placeholder="What needs to be done?" style={inp} autoFocus />
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={lbl}>Priority</label>
              <select value={form.priority} onChange={e => f('priority', e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
                {Object.entries(PRIORITY).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Column</label>
              <select value={form.columnId} onChange={e => f('columnId', e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
                {columns.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={lbl}>Assignee</label>
              <select value={form.assigneeId} onChange={e => f('assigneeId', e.target.value)} style={{ ...inp, background: 'var(--hf-surface)' }}>
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
          <div style={{ padding: '12px 14px', background: 'var(--hf-surface-muted)', borderRadius: 9, border: '1px solid var(--hf-border)' }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 10, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Link2 size={11} /> Link to entity (optional)
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <select value={form.linkedEntityType} onChange={e => f('linkedEntityType', e.target.value)} style={{ ...inp, background: 'var(--hf-surface)', fontSize: 13 }}>
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
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, padding: '10px 12px' }}>
              <AlertCircle size={13} style={{ color: 'var(--hf-danger-text)' }} />
              <span style={{ fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</span>
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
