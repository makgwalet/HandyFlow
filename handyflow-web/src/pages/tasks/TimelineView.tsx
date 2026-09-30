// src/pages/tasks/TimelineView.tsx
import {
  GanttChart,
} from 'lucide-react'
import type { Task } from './tasks.types'
import { PRIORITY, fmtDate } from './tasks.constants'

export function TimelineView({ tasks, onTaskClick }: { tasks: Task[]; onTaskClick: (task: Task) => void }) {
  const dated = tasks.filter(t => !!t.dueDate).sort((a, b) => a.dueDate!.localeCompare(b.dueDate!))

  if (dated.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--hf-text-disabled)' }}>
        <GanttChart size={32} style={{ marginBottom: 10, opacity: 0.5 }} />
        <div style={{ fontSize: 13 }}>No tasks with due dates to show on the timeline</div>
      </div>
    )
  }

  const DAY_MS   = 86400000
  const dayWidth = 34
  const today    = new Date(); today.setHours(0, 0, 0, 0)

  const dueTimes = dated.map(t => new Date(t.dueDate! + 'T00:00:00').getTime())
  const minTime = Math.min(today.getTime(), ...dueTimes) - DAY_MS
  const maxTime = Math.max(today.getTime(), ...dueTimes) + DAY_MS
  const totalDays = Math.max(1, Math.round((maxTime - minTime) / DAY_MS))
  const xFor = (dueDate: string) => Math.round((new Date(dueDate + 'T00:00:00').getTime() - minTime) / DAY_MS) * dayWidth

  const axisDates: Date[] = []
  for (let i = 0; i <= totalDays; i++) axisDates.push(new Date(minTime + i * DAY_MS))

  return (
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflow: 'auto' }}>
      <div style={{ minWidth: 220 + (totalDays + 1) * dayWidth }}>
        {/* Date axis */}
        <div style={{ display: 'flex', borderBottom: '1px solid var(--hf-border)', background: 'var(--hf-surface-muted)', position: 'sticky' as const, top: 0, zIndex: 1 }}>
          <div style={{ width: 220, flexShrink: 0, padding: '8px 12px', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', borderRight: '1px solid var(--hf-border)' }}>Task</div>
          <div style={{ position: 'relative' as const, flex: 1, height: 32 }}>
            {axisDates.map((d, i) => {
              const isWeekend = d.getDay() === 0 || d.getDay() === 6
              return (
                <div key={i} style={{ position: 'absolute' as const, left: i * dayWidth, width: dayWidth, textAlign: 'center' as const, fontSize: 10, fontWeight: 600, color: isWeekend ? 'var(--hf-text-disabled)' : 'var(--hf-text-faint)', paddingTop: 8, borderLeft: '1px solid var(--hf-border-subtle)', height: '100%' }}>
                  {d.getDate()}
                </div>
              )
            })}
          </div>
        </div>

        {/* Rows */}
        {dated.map(t => (
          <div key={t.id} style={{ display: 'flex', borderBottom: '1px solid var(--hf-border-subtle)' }}>
            <div onClick={() => onTaskClick(t)} title={t.title}
              style={{ width: 220, flexShrink: 0, padding: '9px 12px', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', cursor: 'pointer', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', borderRight: '1px solid var(--hf-border-subtle)' }}>
              {t.title}
            </div>
            <div style={{ position: 'relative' as const, flex: 1, height: 38 }}>
              <div onClick={() => onTaskClick(t)} title={`${t.title} — ${fmtDate(t.dueDate)}`}
                style={{ position: 'absolute' as const, left: xFor(t.dueDate!) + 3, top: 8, width: dayWidth - 6, height: 22, borderRadius: 6, cursor: 'pointer',
                  background: t.overdue ? 'var(--hf-danger-soft-strong)' : (PRIORITY[t.priority]?.bg || 'var(--hf-surface-sunken)'),
                  border: `1.5px solid ${t.overdue ? 'var(--hf-danger)' : (PRIORITY[t.priority]?.border || 'var(--hf-border)')}` }} />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
