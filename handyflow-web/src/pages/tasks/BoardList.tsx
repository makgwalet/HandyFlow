// src/pages/tasks/BoardList.tsx
import { Plus, Loader2, AlertCircle } from 'lucide-react'
import type { Board, Summary, Task } from './tasks.types'
import { PRIORITY, fmtDate } from './tasks.constants'
import { isTaskOverdue } from './tasks.logic'
import { btnPrimary, btnSecondary } from './tasks.styles'
import { OrgSummary } from './StatsStrip'

interface Props {
  boards: Board[] | undefined
  loading: boolean
  error: boolean
  onRetry: () => void
  summary: Summary | null | undefined
  myTasks: Task[]
  canAdmin: boolean
  onOpenBoard: (id: string) => void
  onOpenTask: (t: Task) => void
  onNewBoard: () => void
}

export function BoardList({ boards, loading, error, onRetry, summary, myTasks, canAdmin, onOpenBoard, onOpenTask, onNewBoard }: Props) {
  const boardName = (id: string) => boards?.find(b => b.id === id)?.name ?? 'Board'
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20, flexWrap: 'wrap' }}>
        <div style={{ flex: 1 }}>
          <h1 style={{ margin: 0, fontSize: 22, fontWeight: 800, color: 'var(--hf-text)' }}>Tasks</h1>
          <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginTop: 2 }}>Plan, assign and track work across your team.</div>
        </div>
        {canAdmin && <button type="button" onClick={onNewBoard} style={btnPrimary}><Plus size={15} />New board</button>}
      </div>

      {summary && <OrgSummary summary={summary} />}

      {loading && <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--hf-text-muted)', padding: 24 }}><Loader2 size={16} className="hf-spin" />Loading boards…</div>}
      {error && !loading && (
        <div role="alert" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 16, borderRadius: 12, background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', color: 'var(--hf-danger-text)' }}>
          <AlertCircle size={18} /><span style={{ flex: 1, fontSize: 14 }}>We couldn't load your boards.</span>
          <button type="button" onClick={onRetry} style={btnSecondary}>Try again</button>
        </div>
      )}

      {boards && boards.length > 0 && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 14, marginBottom: 28 }}>
          {boards.map(b => (
            <button key={b.id} type="button" onClick={() => onOpenBoard(b.id)}
              style={{ textAlign: 'left', cursor: 'pointer', background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderTop: `4px solid ${b.color ?? 'var(--hf-border-strong)'}`,
                borderRadius: 12, padding: '16px 18px', boxShadow: 'var(--hf-shadow-sm)' }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--hf-text)' }}>{b.name}</div>
              <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginTop: 4, minHeight: 18 }}>{b.description}</div>
              <div style={{ fontSize: 12, color: 'var(--hf-text-faint)', marginTop: 10 }}>{b.columns.length} lists{b.isDefault ? ' · default' : ''}</div>
            </button>
          ))}
        </div>
      )}

      <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--hf-text)', margin: '0 0 10px' }}>Assigned to me</h2>
      {myTasks.length === 0 ? (
        <div style={{ fontSize: 13, color: 'var(--hf-text-faint)', padding: '8px 0' }}>Nothing is assigned to you right now.</div>
      ) : (
        <div style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, overflow: 'hidden' }}>
          {myTasks.slice(0, 8).map((t, i) => {
            const pr = PRIORITY[t.priority] ?? PRIORITY.NORMAL
            const overdue = isTaskOverdue(t)
            return (
              <button key={t.id} type="button" onClick={() => onOpenTask(t)}
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
      )}
    </div>
  )
}
