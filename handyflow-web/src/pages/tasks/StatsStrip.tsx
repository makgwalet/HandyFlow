// src/pages/tasks/StatsStrip.tsx
import type { BoardStats } from './tasks.logic'
import type { Summary } from './tasks.types'

const chip: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 7, fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)',
  background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 99, padding: '4px 12px' }

/** Counts for the open board only (taken from each column's category). */
export function BoardStatsStrip({ stats }: { stats: BoardStats }) {
  const items: { label: string; n: number; dot: string; hide?: boolean }[] = [
    { label: 'In progress', n: stats.inProgress, dot: 'var(--hf-info)' },
    { label: 'In review',   n: stats.inReview,   dot: 'var(--hf-warning)' },
    { label: 'Blocked',     n: stats.blocked,    dot: 'var(--hf-danger)', hide: stats.blocked === 0 },
    { label: 'Done',        n: stats.done,       dot: 'var(--hf-success)' },
    { label: 'Overdue',     n: stats.overdue,    dot: 'var(--hf-danger)', hide: stats.overdue === 0 },
    { label: 'Unassigned',  n: stats.unassigned, dot: 'var(--hf-text-faint)', hide: stats.unassigned === 0 },
  ]
  return (
    <div aria-label="Board summary" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
      {items.filter(i => !i.hide).map(i => (
        <span key={i.label} style={chip}><span aria-hidden style={{ width: 7, height: 7, borderRadius: '50%', background: i.dot }} />{i.label}<b style={{ color: 'var(--hf-text)' }}>{i.n}</b></span>
      ))}
    </div>
  )
}

/** Organisation-wide numbers, shown on the boards list (not on a board). */
export function OrgSummary({ summary }: { summary: Summary }) {
  const cards = [
    { label: 'Assigned to me', n: summary.myTasksCount, tone: 'var(--hf-primary-text)' },
    { label: 'Overdue',        n: summary.overdueCount, tone: summary.overdueCount > 0 ? 'var(--hf-danger-text)' : 'var(--hf-text)' },
    { label: 'In progress',    n: summary.inProgressCount, tone: 'var(--hf-info-text)' },
    { label: 'In review',      n: summary.inReviewCount, tone: 'var(--hf-warning-text)' },
    { label: 'Done',           n: summary.doneCount, tone: 'var(--hf-success-text)' },
  ]
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 12, marginBottom: 24 }}>
      {cards.map(c => (
        <div key={c.label} style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '14px 16px', boxShadow: 'var(--hf-shadow-sm)' }}>
          <div style={{ fontSize: 26, fontWeight: 800, color: c.tone }}>{c.n}</div>
          <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--hf-text-muted)', marginTop: 2 }}>{c.label}</div>
        </div>
      ))}
    </div>
  )
}
