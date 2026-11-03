// src/pages/projects/tabs/TasksTab.tsx
// Tasks as a list or a board (Not started · In progress · In review · Blocked · Completed). Cards can be dragged between columns;
// a move the workflow does not allow is refused with the reason. Click a task to change its title, priority, assignee (a project
// member), estimate or notes.
import React, { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Flag, GripVertical } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { Avatar, Btn, Dialog, Empty, ErrorLine, Field, PRIORITY_TONE, Pill, card, day, inp, list, muted, td, th, useMembers } from './pmKit'

interface Task {
  id: string; taskNumber: string; title: string; status: string; priority: keyof typeof PRIORITY_TONE
  taskType: string; assigneeId: string | null; assigneeName: string | null; plannedStart: string | null; plannedEnd: string | null
  progressPct: number; estimatedHours: number | null; actualHours: number; isCritical: boolean; isMilestone: boolean
  phaseId: string | null; notes: string | null
}
interface Phase { id: string; name: string; status: string }

const COLUMNS = ['NOT_STARTED', 'IN_PROGRESS', 'IN_REVIEW', 'BLOCKED', 'COMPLETED'] as const
type Col = typeof COLUMNS[number]
const LABEL: Record<string, string> = { NOT_STARTED: 'Not started', IN_PROGRESS: 'In progress', IN_REVIEW: 'In review', BLOCKED: 'Blocked', COMPLETED: 'Completed', CANCELLED: 'Cancelled' }
const TONE = { NOT_STARTED: 'neutral', IN_PROGRESS: 'info', IN_REVIEW: 'violet', BLOCKED: 'danger', COMPLETED: 'success', CANCELLED: 'neutral' } as const

/** The server action that moves a task from one column to another, or why it cannot. */
export function moveAction(from: string, to: Col): { action: string } | { refuse: string } {
  if (from === to) return { refuse: '' }
  if (from === 'COMPLETED' || from === 'CANCELLED') return { refuse: `A ${LABEL[from].toLowerCase()} task cannot be moved` }
  switch (to) {
    case 'NOT_STARTED': return { refuse: 'A task that has started cannot go back to Not started' }
    case 'IN_PROGRESS': return from === 'IN_REVIEW' ? { action: 'RETURN' } : { action: 'START' }
    case 'IN_REVIEW':   return { action: 'REVIEW' }
    case 'BLOCKED':     return { action: 'BLOCK' }
    case 'COMPLETED':   return { action: 'COMPLETE' }
  }
}

export function TasksTab({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const canWrite = usePermission('PM_WRITE')
  const [view, setView] = useState<'board' | 'list'>('board')
  const [filter, setFilter] = useState('')
  const [who, setWho] = useState('')
  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<Task | null>(null)
  const [dragId, setDragId] = useState<string | null>(null)
  const [over, setOver] = useState<Col | null>(null)

  const { data: tasks = [], isLoading } = useQuery<Task[]>({ queryKey: ['pm-tasks', projectId], queryFn: async () => list<Task>(await apiClient.get(`/api/v1/projects/${projectId}/tasks`)) })
  const { data: phases = [] } = useQuery<Phase[]>({ queryKey: ['pm-phases', projectId], staleTime: 60_000, queryFn: async () => list<Phase>(await apiClient.get(`/api/v1/projects/${projectId}/phases`)) })
  const { data: members = [] } = useMembers(projectId)
  const refresh = () => ['pm-tasks', 'pm-schedule', 'pm-project'].forEach(k => qc.invalidateQueries({ queryKey: [k, projectId] }))

  const act = async (t: Task, action: string) => {
    try { await apiClient.post(`/api/v1/projects/tasks/${t.id}/status`, { action }); refresh() }
    catch (e) { toast.error(await errorText(e, 'Could not change the task')) }
  }
  const drop = async (col: Col) => {
    const t = tasks.find(x => x.id === dragId); setDragId(null); setOver(null)
    if (!t) return
    const m = moveAction(t.status, col)
    if ('refuse' in m) { if (m.refuse) toast.info(m.refuse); return }
    // optimistic: show it in the new column straight away, put it back if the server refuses
    qc.setQueryData<Task[]>(['pm-tasks', projectId], old => old?.map(x => x.id === t.id ? { ...x, status: col } : x))
    try { await apiClient.post(`/api/v1/projects/tasks/${t.id}/status`, { action: m.action }); toast.success(`${t.taskNumber} → ${LABEL[col]}`) }
    catch (e) { toast.error(await errorText(e, 'Could not move the task')) }
    finally { refresh() }
  }

  const shown = useMemo(() => tasks.filter(t => (!filter || t.status === filter) && (!who || (who === 'none' ? !t.assigneeId && !t.assigneeName : t.assigneeId === who))), [tasks, filter, who])
  const chip = (active: boolean): React.CSSProperties => ({ padding: '5px 11px', borderRadius: 20, fontSize: 12, cursor: 'pointer', fontWeight: active ? 700 : 500,
    border: active ? '1.5px solid var(--hf-primary)' : '1px solid var(--hf-border)', background: active ? 'var(--hf-info-soft)' : 'var(--hf-surface)', color: active ? 'var(--hf-primary-text)' : 'var(--hf-text-muted)' })

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14, gap: 10, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {view === 'list' && ['', ...COLUMNS, 'CANCELLED'].map(s => <button key={s} onClick={() => setFilter(s)} style={chip(filter === s)}>{s ? LABEL[s] : 'All'}</button>)}
          <select value={who} onChange={e => setWho(e.target.value)} style={{ ...inp, width: 200 }} aria-label="Assignee">
            <option value="">Everyone</option><option value="none">Unassigned</option>
            {members.map(m => <option key={m.userId} value={m.userId}>{m.userName}</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {(['board', 'list'] as const).map(v => <button key={v} onClick={() => setView(v)} style={chip(view === v)}>{v === 'board' ? 'Board' : 'List'}</button>)}
          {canWrite && <Btn kind="primary" onClick={() => setCreating(true)}><Plus size={14} /> Add task</Btn>}
        </div>
      </div>

      {isLoading ? <Empty>Loading…</Empty> : view === 'board' ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, minmax(190px, 1fr))', gap: 10, overflowX: 'auto' }}>
          {COLUMNS.map(col => {
            const items = shown.filter(t => t.status === col)
            return (
              <div key={col} onDragOver={e => { if (dragId && canWrite) { e.preventDefault(); setOver(col) } }} onDragLeave={() => setOver(o => o === col ? null : o)}
                onDrop={e => { e.preventDefault(); drop(col) }}
                style={{ background: over === col ? 'var(--hf-info-soft)' : 'var(--hf-surface-muted)', border: `1px dashed ${over === col ? 'var(--hf-primary)' : 'transparent'}`,
                  borderRadius: 10, padding: 10, minHeight: 220 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                  <Pill tone={TONE[col]}>{LABEL[col]}</Pill><span style={{ ...muted, fontWeight: 700 }}>{items.length}</span>
                </div>
                <div style={{ display: 'grid', gap: 8 }}>
                  {items.map(t => (
                    <div key={t.id} draggable={canWrite} onDragStart={e => { setDragId(t.id); e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', t.id) }}
                      onDragEnd={() => { setDragId(null); setOver(null) }} onClick={() => setEditing(t)}
                      style={{ ...card, padding: '9px 10px', cursor: canWrite ? 'grab' : 'pointer', opacity: dragId === t.id ? 0.5 : 1 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 5 }}>
                        <span style={{ ...muted, display: 'inline-flex', alignItems: 'center', gap: 3 }}>{canWrite && <GripVertical size={11} />}{t.taskNumber}
                          {t.isCritical && <span title="On the critical path" style={{ color: 'var(--hf-danger-text)' }}> ●</span>}{t.isMilestone && <Flag size={10} />}</span>
                        <Pill tone={PRIORITY_TONE[t.priority] ?? 'info'}>{t.priority}</Pill>
                      </div>
                      <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6, lineHeight: 1.35 }}>{t.title}</div>
                      <div style={{ height: 4, background: 'var(--hf-surface-sunken)', borderRadius: 2, marginBottom: 7 }}>
                        <div style={{ height: '100%', width: `${t.progressPct ?? 0}%`, background: 'var(--hf-info)', borderRadius: 2 }} /></div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--hf-text-muted)' }}>
                          <Avatar name={t.assigneeName} />{t.assigneeName ?? 'Unassigned'}</span>
                        <span style={muted}>{t.plannedEnd ? day(t.plannedEnd) : ''}</span>
                      </div>
                    </div>
                  ))}
                  {items.length === 0 && <div style={{ ...muted, textAlign: 'center', padding: '18px 0' }}>{dragId ? 'Drop here' : 'Nothing here'}</div>}
                </div>
              </div>
            )
          })}
        </div>
      ) : shown.length === 0 ? <Empty>{tasks.length ? 'No tasks match.' : 'No tasks yet. Add the first one.'}</Empty> : (
        <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr>{['#', 'Title', 'Assignee', 'Priority', 'Status', 'Dates', 'Progress', ''].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
            <tbody>{shown.map(t => (
              <tr key={t.id}>
                <td style={{ ...td, whiteSpace: 'nowrap', color: 'var(--hf-text-muted)' }}>{t.taskNumber}{t.isCritical && <span style={{ color: 'var(--hf-danger-text)' }}> ●</span>}</td>
                <td style={{ ...td, fontWeight: 600, cursor: 'pointer' }} onClick={() => setEditing(t)}>{t.title}</td>
                <td style={td}><span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}><Avatar name={t.assigneeName} />{t.assigneeName ?? '—'}</span></td>
                <td style={td}><Pill tone={PRIORITY_TONE[t.priority] ?? 'info'}>{t.priority}</Pill></td>
                <td style={td}><Pill tone={TONE[t.status as Col] ?? 'neutral'}>{LABEL[t.status] ?? t.status}</Pill></td>
                <td style={{ ...td, whiteSpace: 'nowrap', fontSize: 12 }}>{day(t.plannedStart)} → {day(t.plannedEnd)}</td>
                <td style={{ ...td, minWidth: 80 }}><div style={{ height: 6, background: 'var(--hf-surface-sunken)', borderRadius: 3 }}>
                  <div style={{ height: '100%', width: `${t.progressPct ?? 0}%`, background: 'var(--hf-info)', borderRadius: 3 }} /></div>
                  <div style={muted}>{Number(t.progressPct ?? 0).toFixed(0)}%</div></td>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{canWrite && <StatusButtons t={t} act={act} />}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {view === 'board' && canWrite && <div style={{ ...muted, marginTop: 8 }}>Drag a card to another column to move it. Moving to Completed sets progress to 100%.</div>}

      {creating && <CreateTask projectId={projectId} phases={phases} onClose={() => setCreating(false)} onDone={refresh} />}
      {editing && <EditTask projectId={projectId} task={editing} canWrite={canWrite} act={act} onClose={() => setEditing(null)} onDone={refresh} />}
    </div>
  )
}

function StatusButtons({ t, act }: { t: Task; act: (t: Task, a: string) => void }) {
  return <span style={{ display: 'inline-flex', gap: 4 }}>
    {(t.status === 'NOT_STARTED' || t.status === 'BLOCKED') && <Btn small onClick={() => act(t, 'START')}>{t.status === 'BLOCKED' ? 'Resume' : 'Start'}</Btn>}
    {(t.status === 'IN_PROGRESS') && <Btn small onClick={() => act(t, 'REVIEW')}>Send for review</Btn>}
    {t.status === 'IN_REVIEW' && <><Btn small kind="ghost" onClick={() => act(t, 'RETURN')}>Send back</Btn><Btn small onClick={() => act(t, 'COMPLETE')}>Sign off</Btn></>}
    {t.status === 'IN_PROGRESS' && <Btn small kind="ghost" onClick={() => act(t, 'BLOCK')}>Block</Btn>}
  </span>
}

function CreateTask({ projectId, phases, onClose, onDone }: { projectId: string; phases: Phase[]; onClose: () => void; onDone: () => void }) {
  const { data: members = [] } = useMembers(projectId)
  const [f, setF] = useState({ title: '', taskType: 'TASK', priority: 'MEDIUM', phaseId: '', assigneeId: '', plannedStart: '', plannedEnd: '', estimatedHours: '', notes: '' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const save = async () => {
    if (!f.title.trim()) { setErr('Title is required'); return }
    if (f.plannedStart && f.plannedEnd && f.plannedEnd < f.plannedStart) { setErr('The end date is before the start date'); return }
    const m = members.find(x => x.userId === f.assigneeId)
    setBusy(true); setErr('')
    try {
      await apiClient.post(`/api/v1/projects/${projectId}/tasks`, { title: f.title.trim(), taskType: f.taskType, priority: f.priority, phaseId: f.phaseId || null,
        assigneeId: m?.userId ?? null, assigneeName: m?.userName ?? null, plannedStart: f.plannedStart || null, plannedEnd: f.plannedEnd || null,
        estimatedHours: f.estimatedHours ? Number(f.estimatedHours) : null, notes: f.notes.trim() || null, requiresInspection: false })
      toast.success('Task added'); onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not add the task')) } finally { setBusy(false) }
  }
  const g2: React.CSSProperties = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }
  return (
    <Dialog width={580} title="Add task" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Add task'}</Btn></>}>
      <Field label="Title"><input autoFocus maxLength={500} value={f.title} onChange={e => set('title', e.target.value)} style={inp} /></Field>
      <div style={g2}>
        <Field label="Type"><select value={f.taskType} onChange={e => set('taskType', e.target.value)} style={inp}><option value="TASK">Task</option><option value="MILESTONE">Milestone</option><option value="SUMMARY">Summary</option></select></Field>
        <Field label="Priority"><select value={f.priority} onChange={e => set('priority', e.target.value)} style={inp}>{Object.keys(PRIORITY_TONE).map(p => <option key={p}>{p}</option>)}</select></Field>
        <Field label="Assignee" hint={members.length ? undefined : 'Add people under Members to assign tasks'}>
          <select value={f.assigneeId} onChange={e => set('assigneeId', e.target.value)} style={inp}><option value="">Unassigned</option>{members.map(m => <option key={m.userId} value={m.userId}>{m.userName}</option>)}</select></Field>
        {phases.length > 0 ? <Field label="Phase"><select value={f.phaseId} onChange={e => set('phaseId', e.target.value)} style={inp}><option value="">No phase</option>{phases.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></Field> : <span />}
        <Field label="Planned start"><input type="date" value={f.plannedStart} onChange={e => set('plannedStart', e.target.value)} style={inp} /></Field>
        <Field label="Planned end"><input type="date" value={f.plannedEnd} onChange={e => set('plannedEnd', e.target.value)} style={inp} /></Field>
        <Field label="Estimated hours"><input type="number" min={0.25} step={0.25} value={f.estimatedHours} onChange={e => set('estimatedHours', e.target.value)} style={inp} /></Field>
      </div>
      <Field label="Notes"><textarea rows={2} maxLength={2000} value={f.notes} onChange={e => set('notes', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function EditTask({ projectId, task, canWrite, act, onClose, onDone }:
  { projectId: string; task: Task; canWrite: boolean; act: (t: Task, a: string) => Promise<void> | void; onClose: () => void; onDone: () => void }) {
  const { data: members = [] } = useMembers(projectId)
  const [f, setF] = useState({ title: task.title, priority: task.priority as string, assigneeId: task.assigneeId ?? '', estimatedHours: task.estimatedHours == null ? '' : String(task.estimatedHours), notes: task.notes ?? '' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const closed = task.status === 'COMPLETED' || task.status === 'CANCELLED'
  const save = async () => {
    if (!f.title.trim()) { setErr('Title is required'); return }
    setBusy(true); setErr('')
    try {
      await apiClient.put(`/api/v1/projects/tasks/${task.id}`, {
        title: f.title.trim() !== task.title ? f.title.trim() : null, priority: f.priority !== task.priority ? f.priority : null,
        assigneeId: f.assigneeId && f.assigneeId !== task.assigneeId ? f.assigneeId : null, clearAssignee: !f.assigneeId && !!(task.assigneeId || task.assigneeName),
        estimatedHours: f.estimatedHours !== '' ? Number(f.estimatedHours) : null, notes: f.notes !== (task.notes ?? '') ? f.notes : null })
      toast.success('Task updated'); onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not update the task')) } finally { setBusy(false) }
  }
  // a name typed in before members existed: keep showing it until someone is chosen
  const legacy = task.assigneeName && !task.assigneeId
  return (
    <Dialog width={560} title={<span>{task.taskNumber} <span style={{ marginLeft: 6 }}><Pill tone={TONE[task.status as Col] ?? 'neutral'}>{LABEL[task.status] ?? task.status}</Pill></span></span>} onClose={onClose}
      footer={canWrite ? <><span style={{ marginRight: 'auto' }}>{!closed && <StatusButtons t={task} act={async (t, a) => { await act(t, a); onClose() }} />}</span>
        <Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save'}</Btn></> : <Btn onClick={onClose}>Close</Btn>}>
      <Field label="Title"><input disabled={!canWrite} maxLength={500} value={f.title} onChange={e => set('title', e.target.value)} style={inp} /></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Assignee" hint={legacy ? `Currently “${task.assigneeName}” (typed in, not a member)` : undefined}>
          <select disabled={!canWrite} value={f.assigneeId} onChange={e => set('assigneeId', e.target.value)} style={inp}>
            <option value="">Unassigned</option>{members.map(m => <option key={m.userId} value={m.userId}>{m.userName}</option>)}</select></Field>
        <Field label="Priority"><select disabled={!canWrite} value={f.priority} onChange={e => set('priority', e.target.value)} style={inp}>{Object.keys(PRIORITY_TONE).map(p => <option key={p}>{p}</option>)}</select></Field>
        <Field label="Estimated hours" hint={`${Number(task.actualHours ?? 0).toFixed(2)} h logged`}><input disabled={!canWrite} type="number" min={0} step={0.25} value={f.estimatedHours} onChange={e => set('estimatedHours', e.target.value)} style={inp} /></Field>
        <Field label="Dates" hint="Change dates on the Schedule tab, where links are checked"><div style={{ fontSize: 13, paddingTop: 8 }}>{day(task.plannedStart)} → {day(task.plannedEnd)}</div></Field>
      </div>
      <Field label="Notes"><textarea disabled={!canWrite} rows={3} maxLength={4000} value={f.notes} onChange={e => set('notes', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
