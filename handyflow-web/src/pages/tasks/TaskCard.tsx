// src/pages/tasks/TaskCard.tsx
import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { CheckSquare, MessageSquare, Link2, Calendar, MoreHorizontal, User } from 'lucide-react'
import type { Column, Task } from './tasks.types'
import { PRIORITY, categoryOf, fmtDate } from './tasks.constants'
import { isTaskOverdue } from './tasks.logic'
import { Avatar } from './tasks.ui'
import { MenuButton, type MenuItem } from './MenuButton'

export interface TaskCardProps {
  task: Task
  columns: Column[]
  canManage: boolean
  onOpen: (task: Task) => void
  onMoveTo: (task: Task, columnId: string) => void
  onComplete: (task: Task) => void
}

const meta: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--hf-text-muted)' }

/** The card itself: used inside a column and, as a copy, in the drag overlay. */
export function TaskCardView({ task, columns, canManage, onOpen, onMoveTo, onComplete, lifted = false, menu = true }:
  TaskCardProps & { lifted?: boolean; menu?: boolean }) {
  const column = columns.find(c => c.id === task.columnId)
  const done = categoryOf(column) === 'DONE'
  const overdue = !done && isTaskOverdue(task)
  const priority = PRIORITY[task.priority] ?? PRIORITY.NORMAL

  // Dragging is optional; this menu is the touch- and keyboard-friendly way to move a card.
  const items: MenuItem[] = [
    { label: 'Open task', onSelect: () => onOpen(task) },
    ...(canManage && !done ? [{ label: 'Mark done', onSelect: () => onComplete(task) }] : []),
    ...(canManage ? [
      { label: 'Move to', heading: true },
      ...columns.map(c => ({ label: c.name, checked: c.id === task.columnId, disabled: c.id === task.columnId, onSelect: () => onMoveTo(task, c.id) })),
    ] : []),
  ]

  return (
    <div style={{
      background: 'var(--hf-surface)', border: '1px solid var(--hf-border-subtle)', borderRadius: 12, padding: '14px 14px 10px',
      boxShadow: lifted ? 'var(--hf-shadow-md)' : 'var(--hf-shadow-sm)', cursor: canManage ? 'grab' : 'pointer',
      transform: lifted ? 'rotate(1.5deg)' : undefined,
    }}>
      <div style={{
        fontSize: 14, fontWeight: 600, lineHeight: 1.4, overflowWrap: 'anywhere', color: done ? 'var(--hf-text-faint)' : 'var(--hf-text)',
        textDecoration: done ? 'line-through' : 'none', display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden',
      }}>{task.title}</div>

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, marginTop: 12 }}>
        {task.assigneeName ? (
          <span title={task.assigneeName} style={{ display: 'inline-flex' }}><Avatar name={task.assigneeName} size={26} /></span>
        ) : (
          <span title="Unassigned" aria-label="Unassigned" style={{ width: 26, height: 26, borderRadius: '50%', display: 'inline-flex', alignItems: 'center',
            justifyContent: 'center', border: '1.5px dashed var(--hf-border-strong)', color: 'var(--hf-text-faint)' }}><User size={13} /></span>
        )}
        <span style={{ fontSize: 11, fontWeight: 600, padding: '3px 10px', borderRadius: 99, background: priority.bg, color: priority.color, whiteSpace: 'nowrap' }}>
          {priority.label}
        </span>
      </div>

      <div style={{ height: 1, background: 'var(--hf-border-subtle)', margin: '12px 0 8px' }} />

      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, minHeight: 28 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          {task.checklistTotal > 0 && (
            <span style={meta} title="Checklist"><CheckSquare size={14} />{task.checklistCompleted}/{task.checklistTotal}</span>
          )}
          {task.commentCount > 0 && <span style={meta} title="Comments"><MessageSquare size={14} />{task.commentCount}</span>}
          {task.linkedEntityType && (
            <span style={meta} title={`Linked to ${task.linkedEntityType.replace('_', ' ').toLowerCase()}`}><Link2 size={14} /></span>
          )}
          {task.dueDate && (
            <span style={{ ...meta, padding: '2px 8px', borderRadius: 99, fontWeight: 600,
              background: overdue ? 'var(--hf-danger-soft)' : 'transparent', color: overdue ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)' }}
              title={overdue ? 'Overdue' : 'Due date'}>
              <Calendar size={13} />{fmtDate(task.dueDate)}
            </span>
          )}
        </div>
        {menu && <MenuButton label={`Actions for ${task.title}`} icon={<MoreHorizontal size={16} />} items={items} />}
      </div>
    </div>
  )
}

/** A card that can be dragged. Drag is off when the user cannot manage tasks. */
export function SortableTaskCard(props: TaskCardProps) {
  const { task, canManage, onOpen } = props
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: task.id, disabled: !canManage, data: { type: 'task', columnId: task.columnId },
  })
  return (
    <div ref={setNodeRef} {...attributes} {...listeners}
      style={{ transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.35 : 1, outlineOffset: 2, borderRadius: 12 }}
      onClick={() => onOpen(task)}
      onKeyDown={e => {
        listeners?.onKeyDown?.(e)                                   // Space lifts the card (see KanbanBoard's sensors)
        if (e.key === 'Enter' && e.target === e.currentTarget) onOpen(task)
      }}>
      <TaskCardView {...props} />
    </div>
  )
}
