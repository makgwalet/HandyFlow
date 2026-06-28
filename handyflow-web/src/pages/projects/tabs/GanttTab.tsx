// src/pages/projects/tabs/GanttTab.tsx
import React, { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../../api/client'

interface Task {
  id: string; taskNumber: string; title: string; status: string
  plannedStart: string | null; plannedEnd: string | null
  actualStart: string | null; actualEnd: string | null
  progressPct: number; isCritical: boolean; isMilestone: boolean
  assigneeName: string | null; phaseId: string | null; durationDays: number | null
}
interface Phase { id: string; name: string; status: string; startDate: string | null; endDate: string | null }

function unwrap<T>(res: any): T[] {
  const d = res?.data?.data ?? res?.data ?? []
  return Array.isArray(d) ? d as T[] : d?.content ?? []
}

const STATUS_COLOR: Record<string, string> = {
  NOT_STARTED: '#CBD5E1', IN_PROGRESS: '#3B82F6',
  COMPLETED: '#22C55E', BLOCKED: '#EF4444', CANCELLED: '#9CA3AF',
}

export function GanttTab({ projectId }: { projectId: string }) {
  const { data: tasks = [], isLoading } = useQuery<Task[]>({
    queryKey: ['pm-tasks', projectId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/projects/${projectId}/tasks`); return unwrap<Task>(r) },
    staleTime: 30_000,
  })
  const { data: phases = [] } = useQuery<Phase[]>({
    queryKey: ['pm-phases', projectId],
    queryFn: async () => { const r = await apiClient.get(`/api/v1/projects/${projectId}/phases`); return unwrap<Phase>(r) },
    staleTime: 60_000,
  })

  // Compute Gantt date range from tasks
  const { minDate, totalDays } = useMemo(() => {
    const dates = tasks.flatMap(t => [t.plannedStart, t.plannedEnd].filter(Boolean) as string[])
    if (!dates.length) return { minDate: new Date(), totalDays: 90 }
    const min = new Date(Math.min(...dates.map(d => new Date(d).getTime())))
    const max = new Date(Math.max(...dates.map(d => new Date(d).getTime())))
    min.setDate(min.getDate() - 3)
    max.setDate(max.getDate() + 7)
    return { minDate: min, totalDays: Math.ceil((max.getTime() - min.getTime()) / 86400000) }
  }, [tasks])

  const dayW = Math.max(16, Math.min(32, 1200 / Math.max(totalDays, 1)))
  const rowH = 32
  const labelW = 260

  const tasksByPhase = useMemo(() => {
    const map = new Map<string | null, Task[]>()
    tasks.forEach(t => {
      const key = t.phaseId ?? null
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(t)
    })
    return map
  }, [tasks])

  const toX = (d: string | null) => {
    if (!d) return 0
    return Math.max(0, (new Date(d).getTime() - minDate.getTime()) / 86400000) * dayW
  }
  const toW = (start: string | null, end: string | null) => {
    if (!start || !end) return dayW
    return Math.max(dayW, (new Date(end).getTime() - new Date(start).getTime()) / 86400000 * dayW)
  }

  // Month headers
  const months = useMemo(() => {
    const result: { label: string; x: number; w: number }[] = []
    let cur = new Date(minDate)
    while (cur.getTime() < minDate.getTime() + totalDays * 86400000) {
      const x = (cur.getTime() - minDate.getTime()) / 86400000 * dayW
      const next = new Date(cur); next.setMonth(next.getMonth() + 1)
      const endX = Math.min((next.getTime() - minDate.getTime()) / 86400000 * dayW, totalDays * dayW)
      result.push({ label: cur.toLocaleString('en-ZA', { month: 'short', year: '2-digit' }), x, w: endX - x })
      cur = next
    }
    return result
  }, [minDate, totalDays, dayW])

  if (isLoading) return <div style={{ padding: 40, textAlign: 'center', color: '#94A3B8' }}>Loading schedule…</div>
  if (!tasks.length) return (
    <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
      <div style={{ fontSize: 40, marginBottom: 12, opacity: .3 }}>📅</div>
      <div style={{ fontWeight: 600, color: '#475569', marginBottom: 4 }}>No tasks yet</div>
      <div style={{ fontSize: 13 }}>Add tasks in the Tasks tab to build your Gantt chart</div>
    </div>
  )

  // Flatten rows: phase header + its tasks
  const rows: Array<{ type: 'phase'; phase: Phase } | { type: 'task'; task: Task }> = []
  phases.forEach(ph => {
    rows.push({ type: 'phase', phase: ph })
    ;(tasksByPhase.get(ph.id) ?? []).forEach(t => rows.push({ type: 'task', task: t }))
  })
  ;(tasksByPhase.get(null) ?? []).forEach(t => rows.push({ type: 'task', task: t }))

  const chartW = totalDays * dayW
  const chartH = rows.length * rowH + 36

  return (
    <div>
      <div style={{ fontSize: 12, color: '#94A3B8', marginBottom: 8 }}>
        🔴 Critical path &nbsp;·&nbsp; ◆ Milestone &nbsp;·&nbsp; Drag to scroll horizontally
      </div>
      <div style={{ overflowX: 'auto', border: '1px solid #E2E8F0', borderRadius: 12 }}>
        <div style={{ display: 'flex', minWidth: labelW + chartW }}>
          {/* Left labels */}
          <div style={{ width: labelW, flexShrink: 0, borderRight: '1px solid #E2E8F0' }}>
            <div style={{ height: 36, background: '#F8FAFC', borderBottom: '1px solid #E2E8F0', display: 'flex', alignItems: 'center', padding: '0 12px', fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Task
            </div>
            {rows.map((row, i) => row.type === 'phase' ? (
              <div key={row.phase.id} style={{ height: rowH, display: 'flex', alignItems: 'center', padding: '0 12px', background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{row.phase.name}</span>
              </div>
            ) : (
              <div key={row.task.id} style={{ height: rowH, display: 'flex', alignItems: 'center', padding: '0 12px 0 24px', borderBottom: '1px solid #F1F5F9', gap: 6 }}>
                {row.task.isCritical && <span style={{ width: 6, height: 6, borderRadius: '50%', background: '#EF4444', flexShrink: 0 }} />}
                {row.task.isMilestone && <span style={{ fontSize: 10 }}>◆</span>}
                <span style={{ fontSize: 12, color: '#374151', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.task.taskNumber} {row.task.title}</span>
              </div>
            ))}
          </div>

          {/* Gantt chart area */}
          <div style={{ flex: 1, overflowX: 'visible', position: 'relative' }}>
            <svg width={chartW} height={chartH} style={{ display: 'block' }}>
              {/* Month headers */}
              {months.map((m, i) => (
                <g key={i}>
                  <rect x={m.x} y={0} width={m.w} height={36} fill={i % 2 === 0 ? '#F8FAFC' : '#F1F5F9'} />
                  <text x={m.x + 6} y={22} fontSize={10} fill="#94A3B8" fontWeight={600}>{m.label}</text>
                  <line x1={m.x} y1={0} x2={m.x} y2={chartH} stroke="#E2E8F0" strokeWidth={1} />
                </g>
              ))}

              {/* Today line */}
              {(() => {
                const todayX = toX(new Date().toISOString().split('T')[0])
                return <line x1={todayX} y1={36} x2={todayX} y2={chartH} stroke="#EF4444" strokeWidth={1.5} strokeDasharray="4,3" />
              })()}

              {/* Task bars */}
              {rows.map((row, i) => {
                if (row.type === 'phase') return null
                const t = row.task
                const y = i * rowH + 36
                const barY = y + rowH * 0.25
                const barH = rowH * 0.5
                if (!t.plannedStart) return null
                const x = toX(t.plannedStart)
                const w = toW(t.plannedStart, t.plannedEnd)
                const progW = w * (t.progressPct / 100)
                const color = STATUS_COLOR[t.status] ?? '#CBD5E1'

                if (t.isMilestone) {
                  const cx = x + w / 2
                  const cy = y + rowH / 2
                  const sz = 7
                  return (
                    <g key={t.id}>
                      <polygon points={`${cx},${cy-sz} ${cx+sz},${cy} ${cx},${cy+sz} ${cx-sz},${cy}`}
                        fill={t.status === 'COMPLETED' ? '#22C55E' : '#1D4ED8'} />
                    </g>
                  )
                }

                return (
                  <g key={t.id}>
                    <rect x={x} y={barY} width={w} height={barH} rx={3} fill={t.isCritical ? '#FECACA' : '#DBEAFE'} />
                    <rect x={x} y={barY} width={progW} height={barH} rx={3} fill={color} />
                    {w > 40 && (
                      <text x={x + 5} y={barY + barH * 0.72} fontSize={9} fill="#fff" fontWeight={600}>{t.progressPct?.toFixed(0)}%</text>
                    )}
                  </g>
                )
              })}
            </svg>
          </div>
        </div>
      </div>

      {/* Legend */}
      <div style={{ display: 'flex', gap: 20, marginTop: 10, fontSize: 11, color: '#64748B' }}>
        {Object.entries(STATUS_COLOR).map(([status, color]) => (
          <span key={status} style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
            <span style={{ width: 14, height: 8, background: color, borderRadius: 2, display: 'inline-block' }} />
            {status.replace('_', ' ')}
          </span>
        ))}
      </div>
    </div>
  )
}
