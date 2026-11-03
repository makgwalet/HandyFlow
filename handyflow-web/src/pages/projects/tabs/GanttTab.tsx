// src/pages/projects/tabs/GanttTab.tsx
// Schedule tab: schedule control (ScheduleSection) on top, then the plan as a Gantt chart (day / week / month zoom, dependency
// arrows, broken links in red, today line), a list, or a month calendar. The chart exports as PNG and the plan as CSV.
import React, { useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, Download, Image as ImageIcon } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { toast } from '../../../store/toast.store'
import { ScheduleSection } from './ScheduleSection'
import { Btn, Empty, Pill, card, day, list, muted, one, td, th } from './pmKit'

interface Task {
  id: string; taskNumber: string; title: string; status: string
  plannedStart: string | null; plannedEnd: string | null
  progressPct: number; isCritical: boolean; isMilestone: boolean
  assigneeName: string | null; phaseId: string | null; durationDays: number | null
}
interface Phase { id: string; name: string }
interface Dependency { id: string; predecessorId: string; successorId: string; predecessorNumber: string; successorNumber: string; type: string; lagDays: number }
interface Report { violations: { dependencyId: string }[] }

type Zoom = 'day' | 'week' | 'month'
const PX_PER_DAY: Record<Zoom, number> = { day: 30, week: 9, month: 3 }
const DAY_MS = 86_400_000
const STATUS_LABEL: Record<string, string> = { NOT_STARTED: 'Not started', IN_PROGRESS: 'In progress', IN_REVIEW: 'In review', BLOCKED: 'Blocked', COMPLETED: 'Completed', CANCELLED: 'Cancelled' }

const parse = (d: string) => new Date(d + 'T00:00:00')
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n)
const daysBetween = (a: Date, b: Date) => Math.round((b.getTime() - a.getTime()) / DAY_MS)

/** CSS custom properties resolved to real colours, so the exported PNG looks like the screen. */
function palette() {
  const cs = typeof window === 'undefined' ? null : getComputedStyle(document.documentElement)
  const v = (name: string, fb: string) => (cs?.getPropertyValue(name).trim() || fb)
  return {
    surface: v('--hf-surface', '#ffffff'), muted: v('--hf-surface-muted', '#f8fafc'), sunken: v('--hf-surface-sunken', '#eef2f7'),
    border: v('--hf-border', '#e2e8f0'), text: v('--hf-text', '#0f172a'), faint: v('--hf-text-faint', '#94a3b8'), secondary: v('--hf-text-muted', '#64748b'),
    bar: v('--hf-info-soft-strong', '#dbeafe'), barCritical: v('--hf-danger-soft-strong', '#fee2e2'), progress: v('--hf-info', '#2563eb'),
    done: v('--hf-success', '#16a34a'), blocked: v('--hf-danger', '#dc2626'), review: v('--hf-violet-text', '#7c3aed'), danger: v('--hf-danger', '#dc2626'),
    arrow: v('--hf-text-faint', '#94a3b8'), onSolid: v('--hf-text-on-solid', '#ffffff'),
  }
}

export function GanttTab({ projectId }: { projectId: string }) {
  const [view, setView] = useState<'gantt' | 'list' | 'calendar'>('gantt')
  const [zoom, setZoom] = useState<Zoom>('week')
  const svgRef = useRef<SVGSVGElement>(null)

  const { data: tasks = [], isLoading } = useQuery<Task[]>({ queryKey: ['pm-tasks', projectId], queryFn: async () => list<Task>(await apiClient.get(`/api/v1/projects/${projectId}/tasks`)) })
  const { data: phases = [] } = useQuery<Phase[]>({ queryKey: ['pm-phases', projectId], staleTime: 60_000, queryFn: async () => list<Phase>(await apiClient.get(`/api/v1/projects/${projectId}/phases`)) })
  const { data: deps = [] } = useQuery<Dependency[]>({ queryKey: ['pm-dependencies', projectId], queryFn: async () => list<Dependency>(await apiClient.get(`/api/v1/projects/${projectId}/dependencies`)) })
  const { data: report } = useQuery<Report>({ queryKey: ['pm-schedule', projectId], queryFn: async () => one<Report>(await apiClient.get(`/api/v1/projects/${projectId}/schedule`)) })

  // phase header rows, then the tasks without a phase
  const rows = useMemo(() => {
    const out: ({ kind: 'phase'; phase: Phase } | { kind: 'task'; task: Task })[] = []
    const byPhase = new Map<string | null, Task[]>()
    tasks.forEach(t => { const k = phases.some(p => p.id === t.phaseId) ? t.phaseId : null; byPhase.set(k, [...(byPhase.get(k) ?? []), t]) })
    phases.forEach(p => { const ts = byPhase.get(p.id); if (ts?.length) { out.push({ kind: 'phase', phase: p }); ts.forEach(t => out.push({ kind: 'task', task: t })) } })
    ;(byPhase.get(null) ?? []).forEach(t => out.push({ kind: 'task', task: t }))
    return out
  }, [tasks, phases])

  const predecessorsOf = (id: string) => deps.filter(d => d.successorId === id).map(d => `${d.predecessorNumber}${d.type !== 'FS' ? ' ' + d.type : ''}${d.lagDays ? '+' + d.lagDays : ''}`).join(', ')

  const exportCsv = () => {
    const esc = (v: unknown) => { const s = v == null ? '' : String(v); return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s }
    const head = ['Task', 'Title', 'Phase', 'Status', 'Start', 'End', 'Days', 'Progress %', 'Assignee', 'Critical', 'Milestone', 'Predecessors']
    const lines = rows.filter(r => r.kind === 'task').map(r => {
      const t = (r as { task: Task }).task
      return [t.taskNumber, t.title, phases.find(p => p.id === t.phaseId)?.name ?? '', STATUS_LABEL[t.status] ?? t.status, t.plannedStart ?? '', t.plannedEnd ?? '',
        t.durationDays ?? '', Number(t.progressPct ?? 0).toFixed(0), t.assigneeName ?? '', t.isCritical ? 'yes' : '', t.isMilestone ? 'yes' : '', predecessorsOf(t.id)].map(esc).join(',')
    })
    save(new Blob(['﻿' + [head.join(','), ...lines].join('\r\n')], { type: 'text/csv;charset=utf-8' }), `schedule-${iso(new Date())}.csv`)
  }

  const exportPng = () => {
    const svg = svgRef.current
    if (!svg) { toast.info('Switch to the Gantt view to export the chart'); return }
    const w = Number(svg.getAttribute('width')), h = Number(svg.getAttribute('height'))
    const xml = new XMLSerializer().serializeToString(svg)
    const img = new Image()
    img.onload = () => {
      const scale = 2, c = document.createElement('canvas')
      c.width = w * scale; c.height = h * scale
      const ctx = c.getContext('2d')!
      ctx.scale(scale, scale); ctx.drawImage(img, 0, 0)
      c.toBlob(b => b ? save(b, `gantt-${iso(new Date())}.png`) : toast.error('Could not make the image'), 'image/png')
    }
    img.onerror = () => toast.error('Could not make the image')
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(xml)
  }

  const seg = (active: boolean) => ({ padding: '5px 11px', border: 'none', borderRadius: 7, cursor: 'pointer', fontSize: 12, fontWeight: 600,
    background: active ? 'var(--hf-surface)' : 'transparent', color: active ? 'var(--hf-text)' : 'var(--hf-text-muted)', boxShadow: active ? '0 1px 2px rgba(0,0,0,.08)' : 'none' }) as const

  return (
    <>
      <ScheduleSection projectId={projectId} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 10 }}>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <div role="tablist" style={{ display: 'inline-flex', gap: 2, padding: 3, background: 'var(--hf-surface-sunken)', borderRadius: 10 }}>
            {(['gantt', 'list', 'calendar'] as const).map(v => <button key={v} role="tab" aria-selected={view === v} onClick={() => setView(v)} style={seg(view === v)}>{v[0].toUpperCase() + v.slice(1)}</button>)}
          </div>
          {view === 'gantt' && <div style={{ display: 'inline-flex', gap: 2, padding: 3, background: 'var(--hf-surface-sunken)', borderRadius: 10 }} aria-label="Zoom">
            {(['day', 'week', 'month'] as const).map(z => <button key={z} aria-pressed={zoom === z} onClick={() => setZoom(z)} style={seg(zoom === z)}>{z[0].toUpperCase() + z.slice(1)}</button>)}
          </div>}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Btn small disabled={!tasks.length} onClick={exportCsv}><Download size={12} /> CSV</Btn>
          {view === 'gantt' && <Btn small disabled={!tasks.length} onClick={exportPng}><ImageIcon size={12} /> PNG</Btn>}
        </div>
      </div>

      {isLoading ? <Empty>Loading schedule…</Empty> : !tasks.length ? <Empty>No tasks yet. Add tasks in the Tasks tab to build the plan.</Empty>
        : view === 'gantt' ? <GanttChart svgRef={svgRef} rows={rows} deps={deps} broken={new Set(report?.violations?.map(v => v.dependencyId) ?? [])} zoom={zoom} />
        : view === 'list' ? <PlanList rows={rows} predecessorsOf={predecessorsOf} />
        : <PlanCalendar tasks={tasks} />}
    </>
  )
}

function save(blob: Blob, name: string) {
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = name; a.click()
  setTimeout(() => URL.revokeObjectURL(a.href), 1000)
}

// ── Gantt ────────────────────────────────────────────────────────────────────

type Row = { kind: 'phase'; phase: Phase } | { kind: 'task'; task: Task }

function GanttChart({ rows, deps, broken, zoom, svgRef }: { rows: Row[]; deps: Dependency[]; broken: Set<string>; zoom: Zoom; svgRef: React.RefObject<SVGSVGElement | null> }) {
  const c = useMemo(palette, [])
  const ppd = PX_PER_DAY[zoom]
  const labelW = 280, headH = 44, rowH = 30

  const { min, days } = useMemo(() => {
    const ds = rows.flatMap(r => r.kind === 'task' ? [r.task.plannedStart, r.task.plannedEnd].filter(Boolean) as string[] : [])
    const today = new Date(); const t0 = new Date(today.getFullYear(), today.getMonth(), today.getDate())
    const lo = ds.length ? new Date(Math.min(...ds.map(d => parse(d).getTime()))) : t0
    const hi = ds.length ? new Date(Math.max(...ds.map(d => parse(d).getTime()))) : addDays(t0, 30)
    const pad = zoom === 'day' ? 3 : zoom === 'week' ? 7 : 20
    let start = addDays(lo, -pad)
    if (zoom === 'week') start = addDays(start, -((start.getDay() + 6) % 7))   // Monday
    if (zoom === 'month') start = new Date(start.getFullYear(), start.getMonth(), 1)
    return { min: start, days: Math.max(daysBetween(start, addDays(hi, pad)) + 1, 14) }
  }, [rows, zoom])

  const x = (d: string | Date) => labelW + daysBetween(min, typeof d === 'string' ? parse(d) : d) * ppd
  const W = labelW + days * ppd, H = headH + rows.length * rowH + 4
  const rowOf = new Map<string, number>(); rows.forEach((r, i) => { if (r.kind === 'task') rowOf.set(r.task.id, i) })
  const yMid = (i: number) => headH + i * rowH + rowH / 2

  // header ticks
  const ticks: { x: number; label: string; major?: string }[] = []
  for (let i = 0; i < days; i++) {
    const d = addDays(min, i)
    const first = d.getDate() === 1
    if (zoom === 'day') ticks.push({ x: x(d), label: String(d.getDate()), major: first || i === 0 ? d.toLocaleString('en-ZA', { month: 'short', year: 'numeric' }) : undefined })
    else if (zoom === 'week' && d.getDay() === 1) ticks.push({ x: x(d), label: `${d.getDate()} ${d.toLocaleString('en-ZA', { month: 'short' })}`, major: first || ticks.length === 0 ? d.toLocaleString('en-ZA', { month: 'long', year: 'numeric' }) : undefined })
    else if (zoom === 'month' && first) ticks.push({ x: x(d), label: d.toLocaleString('en-ZA', { month: 'short' }), major: d.getMonth() === 0 || ticks.length === 0 ? String(d.getFullYear()) : undefined })
  }
  const today = new Date(); const todayX = x(new Date(today.getFullYear(), today.getMonth(), today.getDate()))

  const barColour = (t: Task) => t.status === 'COMPLETED' ? c.done : t.status === 'BLOCKED' ? c.blocked : t.status === 'IN_REVIEW' ? c.review : c.progress
  const edge = (t: Task, side: 'start' | 'end') => side === 'start' ? x(t.plannedStart!) : x(t.plannedEnd!) + ppd

  return (
    <div style={{ ...card, padding: 0, overflow: 'auto', maxHeight: 640 }}>
      <svg ref={svgRef} xmlns="http://www.w3.org/2000/svg" width={W} height={H} style={{ display: 'block', fontFamily: 'Inter, system-ui, sans-serif' }}>
        <defs>
          <marker id="gantt-arrow" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L8,4 L0,8 z" fill={c.arrow} /></marker>
          <marker id="gantt-arrow-bad" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L8,4 L0,8 z" fill={c.danger} /></marker>
        </defs>
        <rect x={0} y={0} width={W} height={H} fill={c.surface} />
        {/* header */}
        <rect x={0} y={0} width={W} height={headH} fill={c.muted} />
        <text x={12} y={28} fontSize={11} fontWeight={700} fill={c.faint}>TASK</text>
        {ticks.map((t, i) => (
          <g key={i}>
            <line x1={t.x} y1={t.major ? 0 : 22} x2={t.x} y2={H} stroke={c.border} strokeWidth={t.major ? 1 : 0.5} />
            {t.major && <text x={t.x + 4} y={14} fontSize={10} fontWeight={700} fill={c.secondary}>{t.major}</text>}
            {(zoom !== 'day' || ppd >= 18) && <text x={t.x + 3} y={36} fontSize={9.5} fill={c.faint}>{t.label}</text>}
          </g>
        ))}
        <line x1={0} y1={headH} x2={W} y2={headH} stroke={c.border} />
        <line x1={labelW} y1={0} x2={labelW} y2={H} stroke={c.border} />

        {/* rows */}
        {rows.map((r, i) => {
          const y = headH + i * rowH
          if (r.kind === 'phase') return (
            <g key={'p' + r.phase.id}><rect x={0} y={y} width={W} height={rowH} fill={c.sunken} opacity={0.6} />
              <text x={12} y={y + 19} fontSize={11} fontWeight={700} fill={c.secondary}>{r.phase.name.toUpperCase()}</text></g>
          )
          const t = r.task
          const label = `${t.taskNumber}  ${t.title}`
          return (
            <g key={t.id}>
              <line x1={0} y1={y + rowH} x2={W} y2={y + rowH} stroke={c.border} strokeWidth={0.4} />
              {t.isCritical && <circle cx={14} cy={y + rowH / 2} r={3} fill={c.danger} />}
              <text x={24} y={y + 19} fontSize={12} fill={c.text}>{label.length > 40 ? label.slice(0, 39) + '…' : label}<title>{label}</title></text>
              {t.plannedStart && t.plannedEnd && (t.isMilestone ? (() => {
                const cx = edge(t, 'end') - ppd / 2, cy = y + rowH / 2, s = 7
                return <polygon points={`${cx},${cy - s} ${cx + s},${cy} ${cx},${cy + s} ${cx - s},${cy}`} fill={t.status === 'COMPLETED' ? c.done : c.progress}><title>{label} — {day(t.plannedEnd)}</title></polygon>
              })() : (() => {
                const bx = edge(t, 'start'), bw = Math.max(edge(t, 'end') - bx, 4), by = y + 7, bh = rowH - 14
                return <g>
                  <rect x={bx} y={by} width={bw} height={bh} rx={3} fill={t.isCritical ? c.barCritical : c.bar} stroke={t.isCritical ? c.danger : 'none'} strokeWidth={t.isCritical ? 1 : 0} />
                  <rect x={bx} y={by} width={bw * Math.min(100, Number(t.progressPct ?? 0)) / 100} height={bh} rx={3} fill={barColour(t)} />
                  {bw > 34 && <text x={bx + 5} y={by + bh - 4} fontSize={9} fontWeight={700} fill={Number(t.progressPct) > 25 ? c.onSolid : c.text}>{Number(t.progressPct ?? 0).toFixed(0)}%</text>}
                  <title>{label}: {day(t.plannedStart)} – {day(t.plannedEnd)} · {STATUS_LABEL[t.status] ?? t.status}{t.assigneeName ? ' · ' + t.assigneeName : ''}</title>
                </g>
              })())}
            </g>
          )
        })}

        {/* dependency arrows: from the predecessor's finish (FS, FF) or start (SS, SF) to the successor's start (FS, SS) or finish (FF, SF) */}
        {deps.map(d => {
          const pi = rowOf.get(d.predecessorId), si = rowOf.get(d.successorId)
          if (pi == null || si == null) return null
          const p = (rows[pi] as { task: Task }).task, s = (rows[si] as { task: Task }).task
          if (!p.plannedStart || !p.plannedEnd || !s.plannedStart || !s.plannedEnd) return null
          const fromEnd = d.type === 'FS' || d.type === 'FF', toStart = d.type === 'FS' || d.type === 'SS'
          const x1 = edge(p, fromEnd ? 'end' : 'start'), y1 = yMid(pi)
          const x2 = edge(s, toStart ? 'start' : 'end'), y2 = yMid(si)
          const out = fromEnd ? 8 : -8, into = toStart ? -8 : 8
          const midY = y1 + (y2 > y1 ? rowH / 2 : -rowH / 2)
          const path = `M${x1},${y1} h${out} V${midY} H${x2 + into} V${y2} H${x2}`
          const bad = broken.has(d.id)
          return <path key={d.id} d={path} fill="none" stroke={bad ? c.danger : c.arrow} strokeWidth={bad ? 1.6 : 1.1} strokeDasharray={bad ? '4 2' : undefined}
            markerEnd={`url(#${bad ? 'gantt-arrow-bad' : 'gantt-arrow'})`}><title>{d.predecessorNumber} → {d.successorNumber} ({d.type}{d.lagDays ? ` + ${d.lagDays} d` : ''}){bad ? ' — broken by the planned dates' : ''}</title></path>
        })}

        {todayX > labelW && todayX < W && <g><line x1={todayX} y1={headH} x2={todayX} y2={H} stroke={c.danger} strokeWidth={1.4} strokeDasharray="4 3" />
          <text x={todayX + 3} y={headH + 11} fontSize={9} fontWeight={700} fill={c.danger}>Today</text></g>}
      </svg>
      <div style={{ ...muted, display: 'flex', gap: 16, flexWrap: 'wrap', padding: '8px 12px', borderTop: '1px solid var(--hf-border)' }}>
        <span>● critical path</span><span>◆ milestone</span><span>→ task link</span><span style={{ color: 'var(--hf-danger-text)' }}>⇢ link the dates break</span>
        <span>Bars: blue in progress · purple in review · green done · red blocked</span>
      </div>
    </div>
  )
}

// ── List ─────────────────────────────────────────────────────────────────────

function PlanList({ rows, predecessorsOf }: { rows: Row[]; predecessorsOf: (id: string) => string }) {
  return (
    <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead><tr>{['Task', 'Start', 'End', 'Days', 'Progress', 'Status', 'Assignee', 'After'].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
        <tbody>{rows.map(r => r.kind === 'phase'
          ? <tr key={'p' + r.phase.id}><td colSpan={8} style={{ ...td, background: 'var(--hf-surface-muted)', fontWeight: 700, fontSize: 12, textTransform: 'uppercase', color: 'var(--hf-text-muted)' }}>{r.phase.name}</td></tr>
          : <tr key={r.task.id}>
            <td style={td}><b>{r.task.taskNumber}</b> {r.task.title} {r.task.isCritical && <Pill tone="danger">Critical</Pill>} {r.task.isMilestone && <Pill tone="violet">Milestone</Pill>}</td>
            <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(r.task.plannedStart)}</td><td style={{ ...td, whiteSpace: 'nowrap' }}>{day(r.task.plannedEnd)}</td>
            <td style={td}>{r.task.durationDays ?? '—'}</td><td style={td}>{Number(r.task.progressPct ?? 0).toFixed(0)}%</td>
            <td style={td}>{STATUS_LABEL[r.task.status] ?? r.task.status}</td><td style={td}>{r.task.assigneeName ?? '—'}</td>
            <td style={{ ...td, color: 'var(--hf-text-muted)' }}>{predecessorsOf(r.task.id) || '—'}</td>
          </tr>)}</tbody>
      </table>
    </div>
  )
}

// ── Calendar ─────────────────────────────────────────────────────────────────

function PlanCalendar({ tasks }: { tasks: Task[] }) {
  const first = useMemo(() => {
    const now = new Date(); const ds = tasks.map(t => t.plannedStart).filter(Boolean) as string[]
    const inPlan = ds.some(d => parse(d) <= now) && tasks.some(t => t.plannedEnd && parse(t.plannedEnd) >= now)
    const d = inPlan || !ds.length ? now : parse(ds.sort()[0])
    return new Date(d.getFullYear(), d.getMonth(), 1)
  }, [tasks])
  const [month, setMonth] = useState(first)
  const gridStart = addDays(month, -((month.getDay() + 6) % 7))
  const cells = Array.from({ length: 42 }, (_, i) => addDays(gridStart, i))
  const todayIso = iso(new Date())
  const on = (d: Date) => {
    const k = iso(d)
    return tasks.filter(t => t.plannedStart && t.plannedEnd && t.plannedStart <= k && k <= t.plannedEnd && (!t.isMilestone || t.plannedEnd === k))
      .sort((a, b) => Number(b.isMilestone) - Number(a.isMilestone) || Number(b.isCritical) - Number(a.isCritical))
  }
  return (
    <div style={{ ...card, padding: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
        <Btn small kind="ghost" onClick={() => setMonth(m => new Date(m.getFullYear(), m.getMonth() - 1, 1))}><ChevronLeft size={14} /> Previous</Btn>
        <div style={{ fontWeight: 700 }}>{month.toLocaleString('en-ZA', { month: 'long', year: 'numeric' })}</div>
        <Btn small kind="ghost" onClick={() => setMonth(m => new Date(m.getFullYear(), m.getMonth() + 1, 1))}>Next <ChevronRight size={14} /></Btn>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, minmax(0,1fr))', gap: 4 }}>
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map(d => <div key={d} style={{ ...muted, fontWeight: 700, textAlign: 'center' }}>{d}</div>)}
        {cells.map(d => {
          const items = on(d), inMonth = d.getMonth() === month.getMonth(), isToday = iso(d) === todayIso
          return (
            <div key={iso(d)} style={{ minHeight: 86, border: `1px solid ${isToday ? 'var(--hf-primary)' : 'var(--hf-border)'}`, borderRadius: 8, padding: 4,
              background: inMonth ? 'var(--hf-surface)' : 'var(--hf-surface-muted)', opacity: inMonth ? 1 : 0.6 }}>
              <div style={{ fontSize: 11, fontWeight: isToday ? 800 : 600, color: isToday ? 'var(--hf-primary-text)' : 'var(--hf-text-muted)', marginBottom: 3 }}>{d.getDate()}</div>
              {items.slice(0, 3).map(t => (
                <div key={t.id} title={`${t.taskNumber} ${t.title}: ${day(t.plannedStart)} – ${day(t.plannedEnd)}`}
                  style={{ fontSize: 10.5, lineHeight: 1.3, padding: '1px 4px', borderRadius: 4, marginBottom: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                    background: t.isMilestone ? 'var(--hf-violet-soft-strong)' : t.isCritical ? 'var(--hf-danger-soft)' : 'var(--hf-info-soft)',
                    color: t.isMilestone ? 'var(--hf-violet-text)' : t.isCritical ? 'var(--hf-danger-text)' : 'var(--hf-info-text)' }}>
                  {t.isMilestone ? '◆ ' : ''}{t.taskNumber} {t.title}</div>
              ))}
              {items.length > 3 && <div style={{ fontSize: 10, color: 'var(--hf-text-faint)' }}>+{items.length - 3} more</div>}
            </div>
          )
        })}
      </div>
    </div>
  )
}
