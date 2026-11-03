// src/pages/projects/tabs/ScheduleSection.tsx
// Schedule control above the Gantt chart: forecast finish against the baseline, critical path and float, broken dependencies,
// linking tasks (FS/SS/FF/SF with lag), moving a task's dates with or without pushing its dependants, and recalculating.
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, Link2, Trash2, AlertTriangle, CalendarClock } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, SectionTitle, Stat, card, day, fail, inp, list, muted, one, td, th } from './pmKit'

interface TaskView { id: string; taskNumber: string; title: string; status: string; plannedStart: string | null; plannedEnd: string | null
  baselineStart: string | null; baselineEnd: string | null; forecastStart: string | null; forecastEnd: string | null
  floatDays: number | null; critical: boolean; overdue: boolean; startVarianceDays: number | null; finishVarianceDays: number | null; outsideProjectDates: boolean }
interface Violation { dependencyId: string; predecessorNumber: string; successorNumber: string; type: string; lagDays: number; shortfallWorkingDays: number; message: string }
interface PhaseRollup { phaseId: string; name: string; taskCount: number; completedCount: number; forecastFinish: string | null; phaseEndDate: string | null; forecastAfterPhaseEnd: boolean }
interface Report { dataDate: string; plannedStart: string | null; plannedFinish: string | null; baselineFinish: string | null; forecastFinish: string | null
  finishVarianceDays: number | null; cycleDetected: boolean; criticalTaskCount: number; tasks: TaskView[]; violations: Violation[]; unscheduledTaskIds: string[]; phases: PhaseRollup[] }
interface Dependency { id: string; predecessorId: string; predecessorNumber: string; successorId: string; successorNumber: string; type: string; lagDays: number }
interface Shift { taskId: string; taskNumber: string; oldStart: string; oldEnd: string; newStart: string; newEnd: string; shiftWorkingDays: number }
interface DatesResult { edited: Shift; shifted: Shift[]; blocked: string[]; remainingViolations: Violation[] }

const TYPE_LABEL: Record<string, string> = { FS: 'Finish → start', SS: 'Start → start', FF: 'Finish → finish', SF: 'Start → finish' }
const vary = (n: number | null) => n == null ? '—' : n === 0 ? 'on time' : n > 0 ? `${n} d late` : `${-n} d early`

export function ScheduleSection({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const canWrite = usePermission('PM_WRITE')
  const [busy, setBusy] = useState(false)
  const [linking, setLinking] = useState(false)
  const [moving, setMoving] = useState<TaskView | null>(null)
  const [showTasks, setShowTasks] = useState(false)

  const { data: r, isLoading, isError } = useQuery<Report>({
    queryKey: ['pm-schedule', projectId],
    queryFn: async () => one<Report>(await apiClient.get(`/api/v1/projects/${projectId}/schedule`)),
  })
  const { data: deps = [] } = useQuery<Dependency[]>({
    queryKey: ['pm-dependencies', projectId],
    queryFn: async () => list<Dependency>(await apiClient.get(`/api/v1/projects/${projectId}/dependencies`)),
  })
  const refresh = () => ['pm-schedule', 'pm-dependencies', 'pm-tasks'].forEach(k => qc.invalidateQueries({ queryKey: [k, projectId] }))

  const recalc = async () => {
    setBusy(true)
    try { await apiClient.post(`/api/v1/projects/${projectId}/schedule/recalculate`); toast.success('Critical path recalculated'); refresh() }
    catch (e) { await fail(e, 'Could not recalculate') } finally { setBusy(false) }
  }
  const unlink = async (d: Dependency) => {
    if (!window.confirm(`Remove the link ${d.predecessorNumber} → ${d.successorNumber}?`)) return
    try { await apiClient.delete(`/api/v1/projects/${projectId}/dependencies/${d.id}`); toast.success('Link removed'); refresh() }
    catch (e) { await fail(e, 'Could not remove the link') }
  }

  if (isLoading) return <Empty>Loading schedule…</Empty>
  if (isError || !r) return <ErrorLine text="Could not load the schedule analysis." />
  const late = r.finishVarianceDays != null && r.finishVarianceDays > 0

  return (
    <div style={{ display: 'grid', gap: 14, marginBottom: 18 }}>
      <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 14, alignItems: 'center' }}>
        <Stat label="Forecast finish" value={r.cycleDetected ? 'Unavailable' : day(r.forecastFinish)} tone={late ? 'bad' : 'good'} sub={`as at ${day(r.dataDate)}`} />
        <Stat label="Baseline finish" value={day(r.baselineFinish)} sub={r.baselineFinish ? undefined : 'set when the project is approved'} />
        <Stat label="Against baseline" value={vary(r.finishVarianceDays)} tone={late ? 'bad' : r.finishVarianceDays != null ? 'good' : undefined} />
        <Stat label="Critical tasks" value={r.criticalTaskCount} />
        <Stat label="Broken links" value={r.violations.length} tone={r.violations.length ? 'bad' : undefined} />
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
          {canWrite && <Btn onClick={() => setLinking(true)}><Link2 size={13} /> Link tasks</Btn>}
          {canWrite && <Btn kind="primary" disabled={busy} onClick={recalc}><RefreshCw size={13} /> {busy ? 'Working…' : 'Recalculate'}</Btn>}
        </div>
      </div>

      {r.cycleDetected && <ErrorLine text="The task links form a loop, so no forecast can be worked out. Remove one of the links below." />}
      {r.unscheduledTaskIds.length > 0 && <div style={muted}>{r.unscheduledTaskIds.length} task(s) have no planned dates and are left out of the forecast.</div>}

      {r.violations.length > 0 && (
        <div style={{ ...card, borderColor: 'var(--hf-danger-border, var(--hf-border))' }}>
          <SectionTitle>Links the planned dates break</SectionTitle>
          {r.violations.map(v => (
            <div key={v.dependencyId} style={{ display: 'flex', gap: 8, alignItems: 'flex-start', fontSize: 13, marginBottom: 6 }}>
              <AlertTriangle size={14} style={{ color: 'var(--hf-danger-text)', marginTop: 2, flexShrink: 0 }} />
              <span><b>{v.predecessorNumber} → {v.successorNumber}</b> ({v.type}{v.lagDays ? ` + ${v.lagDays} d` : ''}): {v.message}</span>
            </div>
          ))}
          <div style={muted}>Move the later task’s dates (with “push dependants”) to fix these.</div>
        </div>
      )}

      {r.phases.some(p => p.forecastAfterPhaseEnd) && (
        <div style={{ ...card, background: 'var(--hf-warning-soft)' }}>
          {r.phases.filter(p => p.forecastAfterPhaseEnd).map(p => (
            <div key={p.phaseId} style={{ fontSize: 13 }}><b>{p.name}</b> is forecast to finish {day(p.forecastFinish)}, after its end date {day(p.phaseEndDate)}.</div>
          ))}
        </div>
      )}

      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        <div style={{ padding: '12px 14px 0' }}><SectionTitle>Task links ({deps.length})</SectionTitle></div>
        {deps.length === 0 ? <Empty>No tasks are linked yet. Link tasks so a delay to one moves the ones after it.</Empty> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Before</th><th style={th}>After</th><th style={th}>Type</th><th style={th}>Lag</th><th style={th} /></tr></thead>
            <tbody>{deps.map(d => (
              <tr key={d.id}><td style={td}>{d.predecessorNumber}</td><td style={td}>{d.successorNumber}</td><td style={td}>{TYPE_LABEL[d.type] ?? d.type}</td>
                <td style={td}>{d.lagDays ? `${d.lagDays} working days` : '—'}</td>
                <td style={{ ...td, textAlign: 'right' }}>{canWrite && <Btn small kind="ghost" onClick={() => unlink(d)}><Trash2 size={12} /> Remove</Btn>}</td></tr>
            ))}</tbody>
          </table>
        )}
      </div>

      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        <div style={{ padding: '12px 14px 0' }}>
          <SectionTitle right={<Btn small onClick={() => setShowTasks(s => !s)}>{showTasks ? 'Hide' : 'Show'} forecast by task</Btn>}>Forecast by task</SectionTitle>
        </div>
        {showTasks && (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr>{['Task', 'Planned', 'Forecast', 'Baseline end', 'Finish', 'Float', ''].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
            <tbody>{r.tasks.map(t => (
              <tr key={t.id} style={{ background: t.critical ? 'var(--hf-danger-soft)' : undefined }}>
                <td style={td}><b>{t.taskNumber}</b> {t.title}
                  <div style={{ display: 'flex', gap: 4, marginTop: 2 }}>{t.critical && <Pill tone="danger">Critical</Pill>}{t.overdue && <Pill tone="warning">Overdue</Pill>}
                    {t.outsideProjectDates && <Pill tone="violet">Outside project dates</Pill>}</div></td>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(t.plannedStart)} – {day(t.plannedEnd)}</td>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(t.forecastStart)} – {day(t.forecastEnd)}</td>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(t.baselineEnd)}</td>
                <td style={{ ...td, whiteSpace: 'nowrap', color: (t.finishVarianceDays ?? 0) > 0 ? 'var(--hf-danger-text)' : undefined }}>{vary(t.finishVarianceDays)}</td>
                <td style={td}>{t.floatDays == null ? '—' : `${t.floatDays} d`}</td>
                <td style={{ ...td, textAlign: 'right' }}>{canWrite && t.status !== 'COMPLETED' && t.status !== 'CANCELLED' &&
                  <Btn small kind="ghost" onClick={() => setMoving(t)}><CalendarClock size={12} /> Move</Btn>}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
      </div>

      {linking && <LinkDialog projectId={projectId} tasks={r.tasks} onClose={() => setLinking(false)} onDone={refresh} />}
      {moving && <MoveDialog task={moving} onClose={() => setMoving(null)} onDone={refresh} />}
    </div>
  )
}

function LinkDialog({ projectId, tasks, onClose, onDone }: { projectId: string; tasks: TaskView[]; onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState({ predecessorId: '', successorId: '', type: 'FS', lagDays: '0' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const save = async () => {
    if (!f.predecessorId || !f.successorId) { setErr('Choose both tasks'); return }
    if (f.predecessorId === f.successorId) { setErr('A task cannot depend on itself'); return }
    setBusy(true); setErr('')
    try {
      const r = one<{ warnings: string[] }>(await apiClient.post(`/api/v1/projects/${projectId}/dependencies`, { ...f, lagDays: Number(f.lagDays) || 0 }))
      if (r?.warnings?.length) toast.info(`Linked — ${r.warnings.join(' ')}`)
      else toast.success('Tasks linked')
      onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not link the tasks')) } finally { setBusy(false) }
  }
  const opts = tasks.map(t => <option key={t.id} value={t.id}>{t.taskNumber} — {t.title}</option>)
  return (
    <Dialog title="Link two tasks" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>Link</Btn></>}>
      <Field label="This task…"><select value={f.predecessorId} onChange={e => set('predecessorId', e.target.value)} style={inp}><option value="">Choose…</option>{opts}</select></Field>
      <Field label="…comes before"><select value={f.successorId} onChange={e => set('successorId', e.target.value)} style={inp}><option value="">Choose…</option>{opts}</select></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 10 }}>
        <Field label="How" hint="Finish → start: the second starts after the first finishes">
          <select value={f.type} onChange={e => set('type', e.target.value)} style={inp}>{Object.entries(TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v} ({k})</option>)}</select></Field>
        <Field label="Lag (working days)"><input type="number" min={0} max={365} value={f.lagDays} onChange={e => set('lagDays', e.target.value)} style={inp} /></Field>
      </div>
      <div style={muted}>A link that would create a loop is refused. If the current dates break it you are told, and can move the dates next.</div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function MoveDialog({ task, onClose, onDone }: { task: TaskView; onClose: () => void; onDone: () => void }) {
  const [start, setStart] = useState(task.plannedStart ?? '')
  const [end, setEnd] = useState(task.plannedEnd ?? '')
  const [push, setPush] = useState(true)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const [result, setResult] = useState<DatesResult | null>(null)
  const save = async () => {
    if (!start || !end) { setErr('Both dates are required'); return }
    if (end < start) { setErr('The end date is before the start date'); return }
    setBusy(true); setErr('')
    try {
      const r = one<DatesResult>(await apiClient.put(`/api/v1/projects/tasks/${task.id}/dates`, { plannedStart: start, plannedEnd: end, shiftDependents: push }))
      setResult(r); onDone()
      toast.success(r.remainingViolations.length ? `Dates updated — ${r.remainingViolations.length} link(s) still broken` : 'Dates updated')
    } catch (e) { setErr(await errorText(e, 'Could not change the dates')) } finally { setBusy(false) }
  }
  return (
    <Dialog title={`Move ${task.taskNumber}`} onClose={onClose}
      footer={result ? <Btn kind="primary" onClick={onClose}>Done</Btn> : <><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save dates'}</Btn></>}>
      {!result ? <>
        <div style={{ ...muted, marginBottom: 12 }}>{task.title}</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <Field label="Planned start"><input type="date" value={start} onChange={e => setStart(e.target.value)} style={inp} /></Field>
          <Field label="Planned end"><input type="date" value={end} onChange={e => setEnd(e.target.value)} style={inp} /></Field>
        </div>
        <label style={{ display: 'flex', gap: 8, alignItems: 'flex-start', fontSize: 13, cursor: 'pointer' }}>
          <input type="checkbox" checked={push} onChange={e => setPush(e.target.checked)} style={{ marginTop: 2 }} />
          <span>Push dependants out so their links still hold<div style={muted}>Tasks that have started or finished are never moved.</div></span>
        </label>
        {err && <ErrorLine text={err} />}
      </> : <>
        <div style={{ fontSize: 13, marginBottom: 10 }}><b>{result.edited.taskNumber}</b>: {day(result.edited.newStart)} – {day(result.edited.newEnd)}</div>
        {result.shifted.length > 0 && <><div style={{ ...muted, fontWeight: 700 }}>Moved with it</div>
          {result.shifted.map(s => <div key={s.taskId} style={{ fontSize: 13 }}>{s.taskNumber}: {day(s.oldStart)} → {day(s.newStart)} ({s.shiftWorkingDays} working days)</div>)}</>}
        {result.blocked.length > 0 && <div style={{ fontSize: 13, color: 'var(--hf-warning-text-deep)', marginTop: 8 }}>Not moved because they have started or finished: {result.blocked.join(', ')}</div>}
        {result.remainingViolations.length > 0 && <div style={{ marginTop: 8 }}>{result.remainingViolations.map(v =>
          <div key={v.dependencyId} style={{ fontSize: 13, color: 'var(--hf-danger-text)' }}>{v.predecessorNumber} → {v.successorNumber}: {v.message}</div>)}</div>}
        {!result.shifted.length && !result.blocked.length && !result.remainingViolations.length && <div style={muted}>No other task needed to move.</div>}
      </>}
    </Dialog>
  )
}
