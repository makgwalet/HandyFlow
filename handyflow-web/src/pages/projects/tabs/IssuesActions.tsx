// src/pages/projects/tabs/IssuesActions.tsx
// The issues register (things that have gone wrong: raise, start, resolve with a resolution, close, reopen; optionally from a
// risk) and action items (who does what by when, against a risk or an issue). Owners are project members.
import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, CheckCircle2, ListChecks, AlertTriangle } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { Avatar, Btn, Dialog, Empty, ErrorLine, Field, PRIORITY_TONE, Pill, ReasonDialog, SectionTitle, Stat, card, day, fail, inp, list, muted, one, td, th, when, useMembers } from './pmKit'

export interface Issue {
  id: string; issueNumber: string; title: string; description: string | null; priority: keyof typeof PRIORITY_TONE
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'; riskId: string | null; ownerId: string | null; ownerName: string | null
  dueDate: string | null; overdue: boolean; raisedByName: string | null; resolution: string | null; resolvedAt: string | null
  resolvedByName: string | null; closedAt: string | null; createdAt: string; openActions: number
}
export interface ActionItem {
  id: string; parentType: 'RISK' | 'ISSUE'; parentId: string; title: string; ownerId: string | null; ownerName: string | null
  dueDate: string | null; status: 'OPEN' | 'DONE' | 'CANCELLED'; overdue: boolean; doneAt: string | null; doneByName: string | null; createdByName: string | null; createdAt: string
}
interface RiskLite { id: string; riskNumber: string | null; title: string; status: string; rating: string }

const ISSUE_TONE = { OPEN: 'warning', IN_PROGRESS: 'info', RESOLVED: 'success', CLOSED: 'neutral' } as const
const ISSUE_LABEL = { OPEN: 'Open', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved', CLOSED: 'Closed' } as const

function useIssueData(projectId: string) {
  const issues = useQuery<Issue[]>({ queryKey: ['pm-issues', projectId], queryFn: async () => list<Issue>(await apiClient.get(`/api/v1/projects/${projectId}/issues`)) })
  const actions = useQuery<ActionItem[]>({ queryKey: ['pm-actions', projectId], queryFn: async () => list<ActionItem>(await apiClient.get(`/api/v1/projects/${projectId}/action-items`)) })
  const risks = useQuery<RiskLite[]>({ queryKey: ['pm-risks', projectId], queryFn: async () => list<RiskLite>(await apiClient.get(`/api/v1/projects/${projectId}/risks`)) })
  return { issues: issues.data ?? [], actions: actions.data ?? [], risks: risks.data ?? [], loading: issues.isLoading || actions.isLoading, error: issues.isError }
}
function useRefresh(projectId: string) {
  const qc = useQueryClient()
  return () => ['pm-issues', 'pm-actions', 'pm-closure-readiness'].forEach(k => qc.invalidateQueries({ queryKey: [k, projectId] }))
}
const riskName = (risks: RiskLite[], id: string | null) => { const r = risks.find(x => x.id === id); return r ? `${r.riskNumber ?? 'Risk'} — ${r.title}` : null }

// ── Issues ───────────────────────────────────────────────────────────────────

export function IssuesRegister({ projectId }: { projectId: string }) {
  const canWrite = usePermission('PM_WRITE')
  const { issues, actions, risks, loading, error } = useIssueData(projectId)
  const [filter, setFilter] = useState<'open' | 'all'>('open')
  const [raising, setRaising] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null)
  const open = issues.filter(i => i.status === 'OPEN' || i.status === 'IN_PROGRESS')
  const shown = filter === 'open' ? open : issues
  const current = issues.find(i => i.id === openId) ?? null

  return (
    <div>
      <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 14, marginBottom: 14 }}>
        <Stat label="Open issues" value={open.length} tone={open.length ? 'warn' : undefined} />
        <Stat label="Critical or high" value={open.filter(i => i.priority === 'CRITICAL' || i.priority === 'HIGH').length} tone={open.some(i => i.priority === 'CRITICAL') ? 'bad' : undefined} />
        <Stat label="Overdue" value={open.filter(i => i.overdue).length} tone={open.some(i => i.overdue) ? 'bad' : undefined} />
        <Stat label="Resolved" value={issues.filter(i => i.status === 'RESOLVED' || i.status === 'CLOSED').length} tone="good" />
      </div>
      <SectionTitle right={<>
        <select value={filter} onChange={e => setFilter(e.target.value as 'open' | 'all')} style={{ ...inp, width: 160 }} aria-label="Show"><option value="open">Open issues</option><option value="all">All issues</option></select>
        {canWrite && <Btn kind="primary" onClick={() => setRaising(true)}><Plus size={13} /> Raise issue</Btn>}
      </>}>Issues</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
        {loading ? <Empty>Loading…</Empty> : error ? <ErrorLine text="Could not load issues." /> : shown.length === 0 ? <Empty>{issues.length ? 'No open issues.' : 'No issues raised. Raise one when something goes wrong that needs resolving.'}</Empty> : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr>{['Issue', 'Priority', 'Status', 'Owner', 'Due', 'Actions', 'From risk'].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
            <tbody>{shown.map(i => (
              <tr key={i.id} onClick={() => setOpenId(i.id)} style={{ cursor: 'pointer' }}>
                <td style={td}><b>{i.issueNumber}</b> {i.title}</td>
                <td style={td}><Pill tone={PRIORITY_TONE[i.priority]}>{i.priority}</Pill></td>
                <td style={td}><Pill tone={ISSUE_TONE[i.status]}>{ISSUE_LABEL[i.status]}</Pill></td>
                <td style={td}><span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}><Avatar name={i.ownerName} />{i.ownerName ?? '—'}</span></td>
                <td style={{ ...td, whiteSpace: 'nowrap', color: i.overdue ? 'var(--hf-danger-text)' : undefined, fontWeight: i.overdue ? 700 : undefined }}>{day(i.dueDate)}{i.overdue && ' · overdue'}</td>
                <td style={td}>{i.openActions ? `${i.openActions} open` : '—'}</td>
                <td style={{ ...td, color: 'var(--hf-text-muted)' }}>{riskName(risks, i.riskId) ?? '—'}</td>
              </tr>
            ))}</tbody>
          </table>
        )}
      </div>
      {raising && <IssueEditor projectId={projectId} risks={risks} onClose={() => setRaising(false)} onSaved={i => { setRaising(false); setOpenId(i.id) }} />}
      {current && <IssueDetail projectId={projectId} issue={current} risks={risks} actions={actions.filter(a => a.parentType === 'ISSUE' && a.parentId === current.id)}
        canWrite={canWrite} onClose={() => setOpenId(null)} />}
    </div>
  )
}

export function IssueEditor({ projectId, risks, issue, fromRisk, onClose, onSaved }:
  { projectId: string; risks: RiskLite[]; issue?: Issue; fromRisk?: string; onClose: () => void; onSaved: (i: Issue) => void }) {
  const refresh = useRefresh(projectId)
  const { data: members = [] } = useMembers(projectId)
  const [f, setF] = useState({ title: issue?.title ?? '', description: issue?.description ?? '', priority: (issue?.priority ?? 'MEDIUM') as string,
    riskId: issue?.riskId ?? fromRisk ?? '', ownerId: issue?.ownerId ?? '', dueDate: issue?.dueDate ?? '' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const save = async () => {
    if (!f.title.trim()) { setErr('Title is required'); return }
    setBusy(true); setErr('')
    try {
      const r = issue
        ? await apiClient.put(`/api/v1/projects/issues/${issue.id}`, { title: f.title.trim(), description: f.description, priority: f.priority,
            ownerId: f.ownerId || null, clearOwner: !f.ownerId && !!issue.ownerId, dueDate: f.dueDate || null })
        : await apiClient.post(`/api/v1/projects/${projectId}/issues`, { title: f.title.trim(), description: f.description.trim() || null, priority: f.priority,
            riskId: f.riskId || null, ownerId: f.ownerId || null, dueDate: f.dueDate || null })
      toast.success(issue ? 'Issue updated' : 'Issue raised'); refresh(); onSaved(one<Issue>(r))
    } catch (e) { setErr(await errorText(e, 'Could not save the issue')) } finally { setBusy(false) }
  }
  return (
    <Dialog width={580} title={issue ? `Edit ${issue.issueNumber}` : 'Raise an issue'} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : issue ? 'Save' : 'Raise issue'}</Btn></>}>
      <Field label="What has happened"><input autoFocus maxLength={300} value={f.title} onChange={e => set('title', e.target.value)} style={inp} /></Field>
      <Field label="Details (optional)"><textarea rows={3} maxLength={4000} value={f.description} onChange={e => set('description', e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
        <Field label="Priority"><select value={f.priority} onChange={e => set('priority', e.target.value)} style={inp}>{Object.keys(PRIORITY_TONE).map(p => <option key={p}>{p}</option>)}</select></Field>
        <Field label="Owner"><select value={f.ownerId} onChange={e => set('ownerId', e.target.value)} style={inp}><option value="">No owner yet</option>{members.map(m => <option key={m.userId} value={m.userId}>{m.userName}</option>)}</select></Field>
        <Field label="Resolve by"><input type="date" value={f.dueDate} onChange={e => set('dueDate', e.target.value)} style={inp} /></Field>
      </div>
      {!issue && <Field label="Came from a risk (optional)"><select value={f.riskId} onChange={e => set('riskId', e.target.value)} style={inp}>
        <option value="">Not from a logged risk</option>{risks.map(r => <option key={r.id} value={r.id}>{r.riskNumber ?? 'Risk'} — {r.title}</option>)}</select></Field>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function IssueDetail({ projectId, issue: i, risks, actions, canWrite, onClose }:
  { projectId: string; issue: Issue; risks: RiskLite[]; actions: ActionItem[]; canWrite: boolean; onClose: () => void }) {
  const refresh = useRefresh(projectId)
  const [editing, setEditing] = useState(false)
  const [resolving, setResolving] = useState(false)
  const [adding, setAdding] = useState(false)
  const act = async (action: string, resolution?: string) => {
    await apiClient.post(`/api/v1/projects/issues/${i.id}/action`, { action, resolution: resolution ?? null })
    toast.success({ START: 'Work started', RESOLVE: 'Issue resolved', CLOSE: 'Issue closed', REOPEN: 'Issue reopened' }[action] ?? 'Done'); refresh()
  }
  const safely = (fn: () => Promise<unknown>) => fn().catch(e => fail(e, 'Could not update the issue'))
  const isOpen = i.status === 'OPEN' || i.status === 'IN_PROGRESS'
  return (
    <Dialog width={760} onClose={onClose}
      title={<span>{i.issueNumber} · {i.title} <span style={{ marginLeft: 6 }}><Pill tone={ISSUE_TONE[i.status]}>{ISSUE_LABEL[i.status]}</Pill></span></span>}
      footer={canWrite && <>
        {isOpen && <Btn kind="ghost" onClick={() => setEditing(true)}>Edit</Btn>}
        <span style={{ flex: 1 }} />
        {i.status === 'OPEN' && <Btn onClick={() => safely(() => act('START'))}>Start work</Btn>}
        {isOpen && <Btn kind="primary" onClick={() => setResolving(true)}><CheckCircle2 size={13} /> Resolve</Btn>}
        {i.status === 'RESOLVED' && <Btn kind="primary" onClick={() => safely(() => act('CLOSE'))}>Close</Btn>}
        {!isOpen && <Btn kind="ghost" onClick={() => safely(() => act('REOPEN'))}>Reopen</Btn>}
      </>}>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 14, marginBottom: 14 }}>
        <Stat label="Priority" value={<Pill tone={PRIORITY_TONE[i.priority]}>{i.priority}</Pill>} />
        <Stat label="Owner" value={<span style={{ display: 'inline-flex', gap: 6, alignItems: 'center', fontSize: 14 }}><Avatar name={i.ownerName} />{i.ownerName ?? 'None'}</span>} />
        <Stat label="Resolve by" value={day(i.dueDate)} tone={i.overdue ? 'bad' : undefined} sub={i.overdue ? 'overdue' : undefined} />
        <Stat label="Raised" value={day(i.createdAt)} sub={i.raisedByName ?? undefined} />
      </div>
      {i.description && <p style={{ fontSize: 13, whiteSpace: 'pre-wrap', marginTop: 0 }}>{i.description}</p>}
      {i.riskId && <div style={{ ...muted, marginBottom: 10 }}>From risk {riskName(risks, i.riskId)}</div>}
      {i.resolution && <div style={{ ...card, background: 'var(--hf-success-soft)', marginBottom: 14 }}>
        <div style={{ ...muted, fontWeight: 700, marginBottom: 4 }}>Resolution — {when(i.resolvedAt)}{i.resolvedByName ? `, ${i.resolvedByName}` : ''}</div>
        <div style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{i.resolution}</div></div>}
      <SectionTitle right={canWrite && isOpen && <Btn small onClick={() => setAdding(true)}><Plus size={12} /> Action</Btn>}>Action items</SectionTitle>
      <ActionList projectId={projectId} items={actions} canWrite={canWrite} emptyText="No actions yet." />
      {editing && <IssueEditor projectId={projectId} risks={risks} issue={i} onClose={() => setEditing(false)} onSaved={() => setEditing(false)} />}
      {resolving && <ReasonDialog title={`Resolve ${i.issueNumber}`} label="How it was resolved" confirm="Resolve" max={4000} onClose={() => setResolving(false)}
        intro={actions.some(a => a.status === 'OPEN') ? <span style={{ color: 'var(--hf-warning-text-deep)' }}><AlertTriangle size={12} /> {actions.filter(a => a.status === 'OPEN').length} action(s) are still open.</span> : undefined}
        onSubmit={text => act('RESOLVE', text)} />}
      {adding && <ActionEditor projectId={projectId} parentType="ISSUE" parentId={i.id} parentLabel={`${i.issueNumber} — ${i.title}`} onClose={() => setAdding(false)} />}
    </Dialog>
  )
}

// ── Action items ─────────────────────────────────────────────────────────────

export function ActionItems({ projectId }: { projectId: string }) {
  const canWrite = usePermission('PM_WRITE')
  const { issues, actions, risks, loading } = useIssueData(projectId)
  const { data: members = [] } = useMembers(projectId)
  const [show, setShow] = useState<'open' | 'overdue' | 'all'>('open')
  const [who, setWho] = useState('')
  const [adding, setAdding] = useState(false)
  const shown = actions.filter(a => (show === 'all' || (show === 'open' ? a.status === 'OPEN' : a.overdue)) && (!who || a.ownerId === who))
  const parentOf = (a: ActionItem) => a.parentType === 'RISK' ? riskName(risks, a.parentId) ?? 'Risk' : (() => { const i = issues.find(x => x.id === a.parentId); return i ? `${i.issueNumber} — ${i.title}` : 'Issue' })()
  const open = actions.filter(a => a.status === 'OPEN')
  return (
    <div>
      <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: 14, marginBottom: 14 }}>
        <Stat label="Open" value={open.length} />
        <Stat label="Overdue" value={open.filter(a => a.overdue).length} tone={open.some(a => a.overdue) ? 'bad' : undefined} />
        <Stat label="Due in 7 days" value={open.filter(a => a.dueDate && !a.overdue && (new Date(a.dueDate).getTime() - Date.now()) / 86400000 <= 7).length} />
        <Stat label="Done" value={actions.filter(a => a.status === 'DONE').length} tone="good" />
      </div>
      <SectionTitle right={<>
        <select value={show} onChange={e => setShow(e.target.value as 'open' | 'overdue' | 'all')} style={{ ...inp, width: 140 }} aria-label="Show">
          <option value="open">Open</option><option value="overdue">Overdue</option><option value="all">All</option></select>
        <select value={who} onChange={e => setWho(e.target.value)} style={{ ...inp, width: 180 }} aria-label="Owner"><option value="">Everyone</option>{members.map(m => <option key={m.userId} value={m.userId}>{m.userName}</option>)}</select>
        {canWrite && <Btn kind="primary" disabled={!risks.length && !issues.length} title={!risks.length && !issues.length ? 'Log a risk or raise an issue first' : undefined} onClick={() => setAdding(true)}><Plus size={13} /> Add action</Btn>}
      </>}><span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}><ListChecks size={15} /> Action items</span></SectionTitle>
      {loading ? <Empty>Loading…</Empty> : <ActionList projectId={projectId} items={shown} canWrite={canWrite} parentOf={parentOf}
        emptyText={actions.length ? 'Nothing matches.' : 'No action items yet. Add them from a risk or an issue.'} />}
      {adding && <ActionEditor projectId={projectId} risks={risks} issues={issues} onClose={() => setAdding(false)} />}
    </div>
  )
}

function ActionList({ projectId, items, canWrite, parentOf, emptyText }:
  { projectId: string; items: ActionItem[]; canWrite: boolean; parentOf?: (a: ActionItem) => string; emptyText: string }) {
  const refresh = useRefresh(projectId)
  const act = async (a: ActionItem, action: 'DONE' | 'CANCEL' | 'REOPEN') => {
    try { await apiClient.post(`/api/v1/projects/action-items/${a.id}/action`, { action }); toast.success(action === 'DONE' ? 'Marked done' : action === 'CANCEL' ? 'Cancelled' : 'Reopened'); refresh() }
    catch (e) { await fail(e, 'Could not update the action') }
  }
  if (!items.length) return <div style={{ ...card }}><Empty>{emptyText}</Empty></div>
  return (
    <div style={{ ...card, padding: 0, overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead><tr>{['Action', ...(parentOf ? ['For'] : []), 'Owner', 'Due', 'Status', ''].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
        <tbody>{items.map(a => (
          <tr key={a.id} style={{ opacity: a.status === 'OPEN' ? 1 : 0.65 }}>
            <td style={{ ...td, textDecoration: a.status === 'CANCELLED' ? 'line-through' : undefined }}>{a.title}</td>
            {parentOf && <td style={{ ...td, color: 'var(--hf-text-muted)' }}><Pill tone={a.parentType === 'RISK' ? 'warning' : 'violet'}>{a.parentType === 'RISK' ? 'Risk' : 'Issue'}</Pill> {parentOf(a)}</td>}
            <td style={td}><span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}><Avatar name={a.ownerName} />{a.ownerName ?? '—'}</span></td>
            <td style={{ ...td, whiteSpace: 'nowrap', color: a.overdue ? 'var(--hf-danger-text)' : undefined, fontWeight: a.overdue ? 700 : undefined }}>{day(a.dueDate)}</td>
            <td style={td}>{a.status === 'DONE' ? <><Pill tone="success">Done</Pill><div style={muted}>{day(a.doneAt)}{a.doneByName ? ` · ${a.doneByName}` : ''}</div></>
              : a.status === 'CANCELLED' ? <Pill tone="neutral">Cancelled</Pill> : a.overdue ? <Pill tone="danger">Overdue</Pill> : <Pill tone="info">Open</Pill>}</td>
            <td style={{ ...td, whiteSpace: 'nowrap', textAlign: 'right' }}>{canWrite && (a.status === 'OPEN'
              ? <><Btn small kind="ghost" onClick={() => act(a, 'CANCEL')}>Cancel</Btn><Btn small onClick={() => act(a, 'DONE')}><CheckCircle2 size={12} /> Done</Btn></>
              : <Btn small kind="ghost" onClick={() => act(a, 'REOPEN')}>Reopen</Btn>)}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  )
}

export function ActionEditor({ projectId, parentType, parentId, parentLabel, risks = [], issues = [], onClose }:
  { projectId: string; parentType?: 'RISK' | 'ISSUE'; parentId?: string; parentLabel?: string; risks?: RiskLite[]; issues?: Issue[]; onClose: () => void }) {
  const refresh = useRefresh(projectId)
  const { data: members = [] } = useMembers(projectId)
  const options = useMemo(() => [
    ...issues.filter(i => i.status === 'OPEN' || i.status === 'IN_PROGRESS').map(i => ({ key: `ISSUE:${i.id}`, label: `Issue ${i.issueNumber} — ${i.title}` })),
    ...risks.filter(r => r.status === 'OPEN').map(r => ({ key: `RISK:${r.id}`, label: `Risk ${r.riskNumber ?? ''} — ${r.title}` })),
  ], [issues, risks])
  const [parent, setParent] = useState(parentType && parentId ? `${parentType}:${parentId}` : options[0]?.key ?? '')
  const [f, setF] = useState({ title: '', ownerId: '', dueDate: '' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(p => ({ ...p, [k]: v }))
  const save = async () => {
    if (!f.title.trim()) { setErr('Say what has to be done'); return }
    if (!parent) { setErr('Choose the risk or issue it is for'); return }
    const [pt, pid] = parent.split(':')
    setBusy(true); setErr('')
    try {
      await apiClient.post(`/api/v1/projects/${projectId}/action-items`, { parentType: pt, parentId: pid, title: f.title.trim(), ownerId: f.ownerId || null, dueDate: f.dueDate || null })
      toast.success('Action added'); refresh(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not add the action')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Add an action" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Add action'}</Btn></>}>
      {parentLabel ? <div style={{ ...muted, marginBottom: 12 }}>For {parentLabel}</div> : (
        <Field label="For"><select value={parent} onChange={e => setParent(e.target.value)} style={inp}>{options.map(o => <option key={o.key} value={o.key}>{o.label}</option>)}</select></Field>)}
      <Field label="What has to be done"><input autoFocus maxLength={300} value={f.title} onChange={e => set('title', e.target.value)} style={inp} /></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Who"><select value={f.ownerId} onChange={e => set('ownerId', e.target.value)} style={inp}><option value="">Not assigned</option>{members.map(m => <option key={m.userId} value={m.userId}>{m.userName}</option>)}</select></Field>
        <Field label="By when"><input type="date" value={f.dueDate} onChange={e => set('dueDate', e.target.value)} style={inp} /></Field>
      </div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

/** Small strip under a risk: its open actions and buttons to add one or raise an issue from it. */
export function RiskFollowUp({ projectId, risk }: { projectId: string; risk: RiskLite }) {
  const canWrite = usePermission('PM_WRITE')
  const { actions, risks } = useIssueData(projectId)
  const [adding, setAdding] = useState(false)
  const [raising, setRaising] = useState(false)
  const mine = actions.filter(a => a.parentType === 'RISK' && a.parentId === risk.id)
  return (
    <div style={{ marginTop: 14 }}>
      <SectionTitle right={canWrite && <><Btn small onClick={() => setRaising(true)}>Raise issue from this risk</Btn><Btn small onClick={() => setAdding(true)}><Plus size={12} /> Action</Btn></>}>Action items</SectionTitle>
      <ActionList projectId={projectId} items={mine} canWrite={canWrite} emptyText="No actions on this risk yet." />
      {adding && <ActionEditor projectId={projectId} parentType="RISK" parentId={risk.id} parentLabel={`${risk.riskNumber ?? 'risk'} — ${risk.title}`} onClose={() => setAdding(false)} />}
      {raising && <IssueEditor projectId={projectId} risks={risks} fromRisk={risk.id} onClose={() => setRaising(false)} onSaved={() => setRaising(false)} />}
    </div>
  )
}
