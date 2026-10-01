// src/pages/tasks/BoardList.tsx
import { Plus, Loader2, AlertCircle } from 'lucide-react'
import type { Board, Summary, Task } from './tasks.types'
import { MyTasksList } from './MyTasksList'
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
  onViewAllMine: () => void
}

const MY_TASKS_PREVIEW = 8

export function BoardList({ boards, loading, error, onRetry, summary, myTasks, canAdmin, onOpenBoard, onOpenTask, onNewBoard, onViewAllMine }: Props) {
  return (
    <div>
      {canAdmin && (
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
          <button type="button" onClick={onNewBoard} style={btnPrimary}><Plus size={15} />New board</button>
        </div>
      )}

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

      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', margin: '0 0 10px' }}>
        <h2 style={{ fontSize: 15, fontWeight: 700, color: 'var(--hf-text)', margin: 0 }}>Assigned to me</h2>
        {myTasks.length > MY_TASKS_PREVIEW && (
          <button type="button" onClick={onViewAllMine}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-primary-text)', fontSize: 13, fontWeight: 600 }}>
            View all {myTasks.length}
          </button>
        )}
      </div>
      <MyTasksList tasks={myTasks} boards={boards} onOpen={onOpenTask} limit={MY_TASKS_PREVIEW} />
    </div>
  )
}
