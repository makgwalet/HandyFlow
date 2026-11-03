// src/pages/projects/tabs/TimeTab.tsx
// Time on a project: log hours against a task, see every entry and its status, and (for approvers) approve or reject submitted
// time — on this project or across all projects. Nobody can approve their own time; the server enforces it too.
import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Check, X } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { useAuthStore } from '../../../store/auth.store'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, SectionTitle, Stat, card, day, fail, inp, list, muted, td, th, today } from './pmKit'
import type { TaskLite } from './ChangeOrdersTab'

interface TimeEntry { id: string; projectId: string; taskId: string | null; userId: string | null; userName: string; entryDate: string; hours: number
  description: string | null; status: 'SUBMITTED' | 'APPROVED' | 'REJECTED'; approvedAt: string | null; createdAt: string }
interface ProjectLite { id: string; projectNumber: string; name: string }

const TONE = { SUBMITTED: 'warning', APPROVED: 'success', REJECTED: 'danger' } as const
const LABEL = { SUBMITTED: 'Awaiting approval', APPROVED: 'Approved', REJECTED: 'Rejected' } as const

export function TimeTab({ projectId, editable }: { projectId: string; editable: boolean }) {
  const qc = useQueryClient()
  const me = useAuthStore(s => s.user?.userId)
  const canLog = usePermission('PM_WRITE')
  const canApprove = usePermission('PM_APPROVE')
  const [scope, setScope] = useState<'project' | 'queue'>('project')
  const [mine, setMine] = useState(false)
  const [logging, setLogging] = useState(false)

  const { data: tasks = [] } = useQuery<TaskLite[]>({ queryKey: ['pm-tasks', projectId], queryFn: async () => list<TaskLite>(await apiClient.get(`/api/v1/projects/${projectId}/tasks`)) })
  const { data: entries = [], isLoading, isError } = useQuery<TimeEntry[]>({
    queryKey: ['pm-time', projectId],
    queryFn: async () => list<TimeEntry>(await apiClient.get(`/api/v1/projects/${projectId}/time-entries`)),
  })
  const { data: queue = [], isLoading: qLoading } = useQuery<TimeEntry[]>({
    queryKey: ['pm-time-approvals'], enabled: canApprove && scope === 'queue',
    queryFn: async () => list<TimeEntry>(await apiClient.get('/api/v1/projects/time-approvals')),
  })
  const { data: projects = [] } = useQuery<ProjectLite[]>({
    queryKey: ['pm-projects-lite'], enabled: canApprove && scope === 'queue', staleTime: 300_000,
    queryFn: async () => list<ProjectLite>(await apiClient.get('/api/v1/projects', { params: { size: 500 } })),
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['pm-time'] }); qc.invalidateQueries({ queryKey: ['pm-time-approvals'] }); qc.invalidateQueries({ queryKey: ['pm-tasks', projectId] }) }

  const taskName = (id: string | null) => { const t = tasks.find(x => x.id === id); return t ? `${t.taskNumber} — ${t.title}` : id ? 'Task on another project' : '—' }
  const projectName = (id: string) => { const p = projects.find(x => x.id === id); return p ? `${p.projectNumber} — ${p.name}` : 'Project' }
  const rows = (scope === 'queue' ? queue : entries).filter(e => !mine || e.userId === me)

  const totals = useMemo(() => {
    const sum = (f: (e: TimeEntry) => boolean) => entries.filter(f).reduce((s, e) => s + Number(e.hours), 0)
    return { all: sum(e => e.status !== 'REJECTED'), approved: sum(e => e.status === 'APPROVED'), pending: sum(e => e.status === 'SUBMITTED'),
      mine: sum(e => e.userId === me && e.status !== 'REJECTED') }
  }, [entries, me])

  const decide = async (e: TimeEntry, approve: boolean) => {
    try {
      await apiClient.post(`/api/v1/projects/time-entries/${e.id}/${approve ? 'approve' : 'reject'}`)
      toast.success(approve ? 'Time approved' : 'Time rejected'); refresh()
    } catch (err) { await fail(err, 'Could not record the decision') }
  }

  return (
    <div>
      <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 14, marginBottom: 14 }}>
        <Stat label="Hours logged" value={totals.all.toFixed(2)} sub="excluding rejected" />
        <Stat label="Approved" value={totals.approved.toFixed(2)} tone="good" />
        <Stat label="Awaiting approval" value={totals.pending.toFixed(2)} tone={totals.pending ? 'warn' : undefined} />
        <Stat label="Your hours" value={totals.mine.toFixed(2)} />
      </div>

      <SectionTitle right={<>
        <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--hf-text-muted)' }}>
          <input type="checkbox" checked={mine} onChange={e => setMine(e.target.checked)} /> Only mine</label>
        {canApprove && <select value={scope} onChange={e => setScope(e.target.value as 'project' | 'queue')} style={{ ...inp, width: 230 }} aria-label="Which entries">
          <option value="project">This project</option><option value="queue">Awaiting my approval — all projects</option></select>}
        {canLog && editable && <Btn kind="primary" disabled={!tasks.length} title={tasks.length ? undefined : 'Add a task first'} onClick={() => setLogging(true)}><Plus size={13} /> Log time</Btn>}
      </>}>{scope === 'queue' ? 'Awaiting approval' : 'Time entries'}</SectionTitle>

      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        {(scope === 'queue' ? qLoading : isLoading) ? <Empty>Loading…</Empty> : isError ? <ErrorLine text="Could not load time entries." /> : rows.length === 0 ? <Empty>{scope === 'queue' ? 'Nothing is waiting for your approval.' : 'No time logged yet.'}</Empty> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Person</th><th style={th}>{scope === 'queue' ? 'Project' : 'Task'}</th><th style={th}>Work</th>
              <th style={{ ...th, textAlign: 'right' }}>Hours</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>{rows.map(e => {
              const own = !!me && e.userId === me
              return (
                <tr key={e.id}>
                  <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(e.entryDate)}</td>
                  <td style={td}>{e.userName}{own && <span style={muted}> (you)</span>}</td>
                  <td style={td}>{scope === 'queue' ? projectName(e.projectId) : taskName(e.taskId)}</td>
                  <td style={td}>{e.description ?? '—'}</td>
                  <td style={{ ...td, textAlign: 'right', fontWeight: 600 }}>{Number(e.hours).toFixed(2)}</td>
                  <td style={td}><Pill tone={TONE[e.status] ?? 'neutral'}>{LABEL[e.status] ?? e.status}</Pill></td>
                  <td style={{ ...td, whiteSpace: 'nowrap', textAlign: 'right' }}>
                    {canApprove && e.status === 'SUBMITTED' && (own ? <span style={muted} title="Someone else must approve your time">not yours to approve</span> : <>
                      <Btn small kind="ghost" onClick={() => decide(e, false)}><X size={12} /> Reject</Btn>
                      <Btn small onClick={() => decide(e, true)}><Check size={12} /> Approve</Btn></>)}
                  </td>
                </tr>
              )
            })}</tbody>
          </table>
        )}
      </div>
      {!editable && <div style={{ ...muted, marginTop: 6 }}>Time can no longer be logged on this project.</div>}
      {logging && <LogTimeDialog projectId={projectId} tasks={tasks} onClose={() => setLogging(false)} onDone={refresh} />}
    </div>
  )
}

function LogTimeDialog({ projectId, tasks, onClose, onDone }: { projectId: string; tasks: TaskLite[]; onClose: () => void; onDone: () => void }) {
  const open = tasks.filter(t => t.status !== 'COMPLETED' && t.status !== 'CANCELLED')
  const [f, setF] = useState({ taskId: open[0]?.id ?? tasks[0]?.id ?? '', entryDate: today(), hours: '', description: '' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const save = async (again: boolean) => {
    const hours = Number(f.hours)
    if (!f.taskId) { setErr('Choose a task'); return }
    if (!(hours >= 0.25 && hours <= 24)) { setErr('Hours must be between 0.25 and 24'); return }
    if (f.entryDate > today()) { setErr('You cannot log time for a future date'); return }
    setBusy(true); setErr('')
    try {
      await apiClient.post(`/api/v1/projects/${projectId}/time-entries`, { taskId: f.taskId, entryDate: f.entryDate, hours, description: f.description.trim() || null })
      toast.success('Time logged'); onDone()
      if (again) setF(p => ({ ...p, hours: '', description: '' })); else onClose()
    } catch (e) { setErr(await errorText(e, 'Could not log the time')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Log time" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn disabled={busy} onClick={() => save(true)}>Save and add another</Btn><Btn kind="primary" disabled={busy} onClick={() => save(false)}>Save</Btn></>}>
      <Field label="Task"><select value={f.taskId} onChange={e => set('taskId', e.target.value)} style={inp}>
        {(open.length ? open : tasks).map(t => <option key={t.id} value={t.id}>{t.taskNumber} — {t.title}</option>)}</select></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Date"><input type="date" max={today()} value={f.entryDate} onChange={e => set('entryDate', e.target.value)} style={inp} /></Field>
        <Field label="Hours" hint="Quarter hours, up to 24"><input type="number" min={0.25} max={24} step={0.25} value={f.hours} onChange={e => set('hours', e.target.value)} style={inp} /></Field>
      </div>
      <Field label="What you did"><textarea rows={3} maxLength={1000} value={f.description} onChange={e => set('description', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      <div style={muted}>Each entry adds to the task’s actual hours and goes to an approver. Someone other than you must approve it.</div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
