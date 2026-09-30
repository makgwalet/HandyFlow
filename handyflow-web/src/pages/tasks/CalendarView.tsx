// src/pages/tasks/CalendarView.tsx
import { useState } from 'react'
import {
  ChevronRight, ChevronLeft,
} from 'lucide-react'
import type { Task } from './tasks.types'
import { PRIORITY } from './tasks.constants'
import { btnSecondary } from './tasks.styles'

export function CalendarView({ tasks, onTaskClick }: { tasks: Task[]; onTaskClick: (task: Task) => void }) {
  const [monthDate, setMonthDate] = useState(() => new Date())
  const year  = monthDate.getFullYear()
  const month = monthDate.getMonth()
  const dateKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  const todayKey = dateKey(new Date())

  const firstOfMonth   = new Date(year, month, 1)
  const startDay       = firstOfMonth.getDay()
  const daysInMonth    = new Date(year, month + 1, 0).getDate()

  const cells: { date: Date; inMonth: boolean }[] = []
  for (let i = startDay; i > 0; i--) cells.push({ date: new Date(year, month, 1 - i), inMonth: false })
  for (let d = 1; d <= daysInMonth; d++) cells.push({ date: new Date(year, month, d), inMonth: true })
  while (cells.length % 7 !== 0) {
    const next = new Date(cells[cells.length - 1].date)
    next.setDate(next.getDate() + 1)
    cells.push({ date: next, inMonth: false })
  }

  const tasksByDate = new Map<string, Task[]>()
  tasks.forEach(t => {
    if (!t.dueDate) return
    const key = t.dueDate.slice(0, 10)
    if (!tasksByDate.has(key)) tasksByDate.set(key, [])
    tasksByDate.get(key)!.push(t)
  })

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
        <h3 style={{ margin: 0, fontSize: 15, fontWeight: 800, color: 'var(--hf-text)' }}>
          {monthDate.toLocaleDateString('en-ZA', { month: 'long', year: 'numeric' })}
        </h3>
        <div style={{ display: 'flex', gap: 6 }}>
          <button onClick={() => setMonthDate(new Date())} style={{ ...btnSecondary, padding: '6px 12px', fontSize: 12 }}>Today</button>
          <button onClick={() => setMonthDate(new Date(year, month - 1, 1))}
            style={{ background: 'var(--hf-surface-sunken)', border: 'none', borderRadius: 8, padding: '6px 9px', cursor: 'pointer', display: 'flex' }}>
            <ChevronLeft size={14} />
          </button>
          <button onClick={() => setMonthDate(new Date(year, month + 1, 1))}
            style={{ background: 'var(--hf-surface-sunken)', border: 'none', borderRadius: 8, padding: '6px 9px', cursor: 'pointer', display: 'flex' }}>
            <ChevronRight size={14} />
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 1, background: 'var(--hf-surface-strong)', border: '1px solid var(--hf-border)', borderRadius: 10, overflow: 'hidden' }}>
        {['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].map(d => (
          <div key={d} style={{ background: 'var(--hf-surface-muted)', padding: '7px', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', textAlign: 'center' }}>{d}</div>
        ))}
        {cells.map(({ date, inMonth }, i) => {
          const key      = dateKey(date)
          const dayTasks = tasksByDate.get(key) || []
          const isToday  = key === todayKey
          return (
            <div key={i} style={{ background: 'var(--hf-surface)', minHeight: 96, padding: 6, opacity: inMonth ? 1 : 0.4 }}>
              <div style={{ marginBottom: 4 }}>
                {isToday ? (
                  <span style={{ background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', borderRadius: '50%', width: 19, height: 19, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 10, fontWeight: 800 }}>{date.getDate()}</span>
                ) : (
                  <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--hf-text-faint)' }}>{date.getDate()}</span>
                )}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {dayTasks.slice(0, 3).map(t => (
                  <div key={t.id} onClick={() => onTaskClick(t)} title={t.title}
                    style={{ fontSize: 10, padding: '2px 5px', borderRadius: 4, background: t.overdue ? 'var(--hf-danger-soft)' : (PRIORITY[t.priority]?.bg || 'var(--hf-surface-sunken)'), color: t.overdue ? 'var(--hf-danger-text)' : (PRIORITY[t.priority]?.color || 'var(--hf-text-tertiary)'), cursor: 'pointer', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontWeight: 600 }}>
                    {t.title}
                  </div>
                ))}
                {dayTasks.length > 3 && (
                  <div style={{ fontSize: 10, color: 'var(--hf-text-faint)', fontWeight: 600 }}>+{dayTasks.length - 3} more</div>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
