// src/pages/tasks/KanbanColumn.tsx
import { useState } from 'react'
import { useDroppable } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import { Plus, MoreVertical, Loader2 } from 'lucide-react'
import type { Column, Task } from './tasks.types'
import { categoryOf } from './tasks.constants'
import { inp } from './tasks.styles'
import { SortableTaskCard, type TaskCardProps } from './TaskCard'
import { MenuButton } from './MenuButton'

interface Props extends Omit<TaskCardProps, 'task'> {
  column: Column
  tasks: Task[]
  canAdmin: boolean
  /** Creates a task with just a title. Rejects on failure (the caller has already toasted). */
  onQuickAdd: (columnId: string, title: string) => Promise<unknown>
  onEditLists: () => void
}

export function KanbanColumn({ column, columns, tasks, canManage, canAdmin, onOpen, onMoveTo, onComplete, onQuickAdd, onEditLists }: Props) {
  const { setNodeRef, isOver } = useDroppable({ id: column.id, data: { type: 'column' } })
  const [adding, setAdding] = useState(false)
  const [title, setTitle] = useState('')
  const [busy, setBusy] = useState(false)
  const done = categoryOf(column) === 'DONE'

  const submit = async () => {
    const t = title.trim()
    if (!t || busy) return
    setBusy(true)
    try { await onQuickAdd(column.id, t); setTitle('') } catch { /* already toasted; keep the text so nothing is lost */ }
    finally { setBusy(false) }
  }

  return (
    <section aria-label={`${column.name}, ${tasks.length} tasks`}
      style={{
        width: 300, flexShrink: 0, display: 'flex', flexDirection: 'column', maxHeight: 'calc(100vh - 290px)', minHeight: 140, borderRadius: 14,
        // a column's own colour is saved data, used for the dot only; the tint comes from the theme
        background: done ? 'color-mix(in srgb, var(--hf-success) 9%, var(--hf-surface-sunken))' : 'var(--hf-surface-sunken)',
        outline: isOver ? '2px solid var(--hf-primary)' : 'none', outlineOffset: -2,
      }}>
      <header style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '14px 10px 10px 16px' }}>
        <span aria-hidden style={{ width: 9, height: 9, borderRadius: '50%', background: column.color ?? 'var(--hf-text-faint)', flexShrink: 0 }} />
        <h3 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: 'var(--hf-text)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {column.name}
        </h3>
        <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--hf-text-muted)', background: 'var(--hf-surface)', borderRadius: 99, padding: '1px 9px' }}>
          {tasks.length}
        </span>
        {canManage && (
          <button type="button" onClick={() => setAdding(a => !a)} aria-label={`Add a task to ${column.name}`} aria-expanded={adding}
            style={{ display: 'flex', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-muted)', padding: 4, borderRadius: 6 }}>
            <Plus size={17} />
          </button>
        )}
        {canAdmin && (
          <MenuButton label={`${column.name} list options`} icon={<MoreVertical size={17} />}
            items={[{ label: 'Edit lists\u2026', onSelect: onEditLists }]} />
        )}
      </header>

      <div ref={setNodeRef} style={{ flex: 1, overflowY: 'auto', padding: '2px 10px 12px', display: 'flex', flexDirection: 'column', gap: 10, minHeight: 60 }}>
        {adding && canManage && (
          <div style={{ display: 'flex', gap: 6 }}>
            <input autoFocus value={title} maxLength={500} placeholder="Task title, then Enter" aria-label={`New task in ${column.name}`}
              onChange={e => setTitle(e.target.value)} disabled={busy}
              onKeyDown={e => { if (e.key === 'Enter') submit(); if (e.key === 'Escape') { setAdding(false); setTitle('') } }}
              style={{ ...inp, flex: 1, padding: '8px 10px', fontSize: 13 }} />
            {busy && <Loader2 size={16} style={{ alignSelf: 'center', color: 'var(--hf-text-muted)' }} className="hf-spin" />}
          </div>
        )}

        <SortableContext items={tasks.map(t => t.id)} strategy={verticalListSortingStrategy}>
          {tasks.map(t => (
            <SortableTaskCard key={t.id} task={t} columns={columns} canManage={canManage} onOpen={onOpen} onMoveTo={onMoveTo} onComplete={onComplete} />
          ))}
        </SortableContext>

        {tasks.length === 0 && !adding && (
          <div style={{ border: '1.5px dashed var(--hf-border-strong)', borderRadius: 10, padding: '18px 10px', textAlign: 'center',
            fontSize: 12, color: 'var(--hf-text-faint)' }}>
            {canManage ? 'Drop a task here' : 'No tasks'}
          </div>
        )}
      </div>
    </section>
  )
}
