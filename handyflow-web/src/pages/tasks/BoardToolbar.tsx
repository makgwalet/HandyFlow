// src/pages/tasks/BoardToolbar.tsx
import { ChevronLeft, Plus, Search, Settings, Download, LayoutGrid, CalendarDays, GanttChart, X, AlertTriangle } from 'lucide-react'
import type { Board, TasksView, UserOption } from './tasks.types'
import { NO_FILTERS, UNASSIGNED, countActiveFilters, type TaskFilters } from './tasks.logic'
import { inp, btnPrimary, btnSecondary } from './tasks.styles'
import { MenuButton } from './MenuButton'

interface Props {
  board: Board
  taskCount: number
  view: TasksView
  onView: (v: TasksView) => void
  filters: TaskFilters
  onFilters: (f: TaskFilters) => void
  users: UserOption[]
  canManage: boolean
  canAdmin: boolean
  onBack: () => void
  onNewTask: () => void
  onAddList: () => void
  onSettings: () => void
  onExport: (kind: 'pdf' | 'timesheet') => void
}

const VIEWS: { id: TasksView; label: string; icon: React.ReactNode }[] = [
  { id: 'board',    label: 'Board',    icon: <LayoutGrid size={14} /> },
  { id: 'calendar', label: 'Calendar', icon: <CalendarDays size={14} /> },
  { id: 'timeline', label: 'Schedule', icon: <GanttChart size={14} /> },   // plots due dates only, so not "Timeline"
]
const field: React.CSSProperties = { ...inp, width: 'auto', padding: '7px 10px', fontSize: 13 }

export function BoardToolbar(p: Props) {
  const active = countActiveFilters(p.filters)
  const set = (patch: Partial<TaskFilters>) => p.onFilters({ ...p.filters, ...patch })
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <button type="button" onClick={p.onBack} aria-label="Back to all boards"
          style={{ ...btnSecondary, display: 'inline-flex', alignItems: 'center', gap: 4, padding: '7px 10px' }}><ChevronLeft size={15} />Boards</button>
        <span aria-hidden style={{ width: 11, height: 11, borderRadius: '50%', background: p.board.color ?? 'var(--hf-text-faint)' }} />
        <span style={{ fontSize: 12, fontWeight: 700, color: 'var(--hf-text-muted)', background: 'var(--hf-surface-sunken)', borderRadius: 99, padding: '2px 10px' }}>
          {p.taskCount} {p.taskCount === 1 ? 'task' : 'tasks'}
        </span>
        <div style={{ flex: 1 }} />
        <MenuButton label="Export board" icon={<Download size={15} />} text="Export"
          triggerStyle={{ ...btnSecondary, padding: '8px 12px' }}
          items={[{ label: 'Status report (PDF)', onSelect: () => p.onExport('pdf') }, { label: 'Timesheet (CSV)', onSelect: () => p.onExport('timesheet') }]} />
        {p.canAdmin && <button type="button" onClick={p.onSettings} aria-label="Board settings" style={{ ...btnSecondary, display: 'inline-flex', padding: '8px 10px' }}><Settings size={15} /></button>}
        {p.canAdmin && <button type="button" onClick={p.onAddList} style={btnSecondary}>Add list</button>}
        {p.canManage && <button type="button" onClick={p.onNewTask} style={btnPrimary}><Plus size={15} />New task</button>}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <div role="tablist" aria-label="Board view" style={{ display: 'inline-flex', background: 'var(--hf-surface-sunken)', borderRadius: 9, padding: 3 }}>
          {VIEWS.map(v => (
            <button key={v.id} role="tab" aria-selected={p.view === v.id} onClick={() => p.onView(v.id)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: 6, border: 'none', borderRadius: 7, padding: '6px 12px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
                background: p.view === v.id ? 'var(--hf-surface)' : 'transparent', color: p.view === v.id ? 'var(--hf-text)' : 'var(--hf-text-muted)',
                boxShadow: p.view === v.id ? 'var(--hf-shadow-sm)' : 'none' }}>{v.icon}{v.label}</button>
          ))}
        </div>
        <div style={{ position: 'relative', flex: '1 1 200px', maxWidth: 320 }}>
          <Search size={14} aria-hidden style={{ position: 'absolute', left: 10, top: 10, color: 'var(--hf-text-faint)' }} />
          <input value={p.filters.q} onChange={e => set({ q: e.target.value })} placeholder="Search tasks" aria-label="Search tasks"
            style={{ ...field, width: '100%', paddingLeft: 30 }} />
        </div>
        <select value={p.filters.priority} onChange={e => set({ priority: e.target.value })} aria-label="Filter by priority" style={field}>
          <option value="ALL">All priorities</option>
          {['URGENT', 'HIGH', 'NORMAL', 'LOW'].map(x => <option key={x} value={x}>{x.charAt(0) + x.slice(1).toLowerCase()}</option>)}
        </select>
        <select value={p.filters.assignee} onChange={e => set({ assignee: e.target.value })} aria-label="Filter by assignee" style={field}>
          <option value="ALL">Everyone</option>
          <option value={UNASSIGNED}>Unassigned</option>
          {p.users.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
        </select>
        <button type="button" aria-pressed={p.filters.overdue} onClick={() => set({ overdue: !p.filters.overdue })}
          style={{ ...btnSecondary, display: 'inline-flex', alignItems: 'center', gap: 6, padding: '7px 12px',
            ...(p.filters.overdue ? { background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', borderColor: 'var(--hf-danger-border)' } : {}) }}>
          <AlertTriangle size={13} />Overdue
        </button>
        {active > 0 && (
          <button type="button" onClick={() => p.onFilters(NO_FILTERS)}
            style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-muted)', fontSize: 13, fontWeight: 600 }}>
            <X size={13} />Clear {active} {active === 1 ? 'filter' : 'filters'}
          </button>
        )}
      </div>
    </div>
  )
}
