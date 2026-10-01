// src/pages/tasks/MyTasksList.tsx
import type { Board, Task } from './tasks.types'
import { PRIORITY, fmtDate } from './tasks.constants'
import { isTaskOverdue } from './tasks.logic'

/** The tasks assigned to the current user, across boards. `limit` shows the first N only. */
export function MyTasksList({ tasks, boards, onOpen, limit }: {
  tasks: Task[]; boards: Board[] | undefined; onOpen: (t: Task) => void; limit?: number
}) {
  const boardName = (id: string) => boards?.find(b => b.id === id)?.name ?? 'Board'
  if (tasks.length === 0) {
    return <div style={{ fontSize: 13, color: 'var(--hf-text-faint)', padding: '8px 0' }}>Nothing is assigned to you right now.</div>
  }
  return (
    <div style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, overflow: 'hidden' }}>
      {(limit ? tasks.slice(0, limit) : tasks).map((t, i) => {
        const pr = PRIORITY[t.priority] ?? PRIORITY.NORMAL
        const overdue = isTaskOverdue(t)
        return (
          <button key={t.id} type="button" onClick={() => onOpen(t)}
            style={{ display: 'flex', alignItems: 'center', gap: 12, width: '100%', textAlign: 'left', cursor: 'pointer', background: 'none', border: 'none',
              borderTop: i ? '1px solid var(--hf-border-subtle)' : 'none', padding: '11px 16px' }}>
            <span style={{ flex: 1, minWidth: 0 }}>
              <span style={{ display: 'block', fontSize: 14, fontWeight: 600, color: 'var(--hf-text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.title}</span>
              <span style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>{boardName(t.boardId)}</span>
            </span>
            <span style={{ fontSize: 11, fontWeight: 600, padding: '3px 10px', borderRadius: 99, background: pr.bg, color: pr.color }}>{pr.label}</span>
            {t.dueDate && <span style={{ fontSize: 12, fontWeight: 600, color: overdue ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)' }}>{fmtDate(t.dueDate)}</span>}
          </button>
        )
      })}
    </div>
  )
}
