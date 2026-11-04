// src/pages/projects/ProjectInfo.tsx
// Editing a project's details, its recent activity, and the contracts linked to it (shown under the Overview tab).
import { useState } from 'react'
import { useQuery, useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { Activity, FileSignature, Link2, Plus, Unlink, ExternalLink, Flag, GitPullRequest, MessageSquare, FileText, Users, Wallet, AlertTriangle, Pencil } from 'lucide-react'
import { apiClient } from '../../api/client'
import { usePermission, usePermissions } from '../../hooks/usePermission'
import { toast } from '../../store/toast.store'
import { errorText } from '../../api/projectDocs'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, SectionTitle, card, day, fail, inp, list, money, muted, one, when, useMembers } from './tabs/pmKit'
import type { Project } from './ProjectDetailTab'

// ── Edit project ─────────────────────────────────────────────────────────────

const CONTRACT_TYPES = ['JBCC_PBA', 'JBCC_MWA', 'NEC3', 'NEC4', 'FIDIC', 'GCC', 'LUMP_SUM', 'REMEASUREMENT', 'COST_PLUS', 'OTHER']

export function EditProjectDialog({ project: p, onClose }: { project: Project; onClose: () => void }) {
  const qc = useQueryClient()
  const { data: members = [] } = useMembers(p.id)
  const locked = !['PLANNING', 'PROPOSED'].includes(p.status)
  const [f, setF] = useState({
    name: p.name ?? '', description: p.description ?? '', siteAddress: p.siteAddress ?? '',
    startDate: p.startDate ?? '', endDate: p.endDate ?? '',
    contractValue: p.contractValue == null ? '' : String(p.contractValue), contractRef: p.contractRef ?? '', contractType: p.contractType ?? '',
    retentionPct: p.retentionPct == null ? '' : String(p.retentionPct), cidbGrade: p.cidbGrade ?? '', nhbrcNumber: p.nhbrcNumber ?? '',
    projectManagerId: p.projectManagerId ?? '', sponsorId: p.sponsorId ?? '',
    objectives: p.objectives ?? '', scopeStatement: p.scopeStatement ?? '', businessCase: p.businessCase ?? '', notes: p.notes ?? '',
  })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(x => ({ ...x, [k]: v }))
  const g2 = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 10 } as const

  const save = async () => {
    if (!f.name.trim()) { setErr('The project needs a name'); return }
    if (f.startDate && f.endDate && f.endDate < f.startDate) { setErr('The end date is before the start date'); return }
    const pm = members.find(m => m.userId === f.projectManagerId), sp = members.find(m => m.userId === f.sponsorId)
    // send only what changed, so the server's activity log names exactly what was edited
    const body: Record<string, unknown> = {}
    const put = (k: string, v: unknown, before: unknown) => { if ((v ?? '') !== (before ?? '')) body[k] = v }
    put('name', f.name.trim(), p.name); put('description', f.description, p.description ?? ''); put('siteAddress', f.siteAddress, p.siteAddress ?? '')
    if (!locked) put('startDate', f.startDate || null, p.startDate); put('endDate', f.endDate || null, p.endDate)
    if (f.contractValue !== (p.contractValue == null ? '' : String(p.contractValue)) && f.contractValue !== '') body.contractValue = Number(f.contractValue)
    put('contractRef', f.contractRef, p.contractRef ?? ''); put('contractType', f.contractType, p.contractType ?? '')
    if (f.retentionPct !== (p.retentionPct == null ? '' : String(p.retentionPct)) && f.retentionPct !== '') body.retentionPct = Number(f.retentionPct)
    put('cidbGrade', f.cidbGrade, p.cidbGrade ?? ''); put('nhbrcNumber', f.nhbrcNumber, p.nhbrcNumber ?? '')
    if (pm && pm.userId !== p.projectManagerId) { body.projectManagerId = pm.userId; body.projectManagerName = pm.userName }
    if (sp && sp.userId !== p.sponsorId) { body.sponsorId = sp.userId; body.sponsorName = sp.userName }
    put('objectives', f.objectives, p.objectives ?? ''); put('scopeStatement', f.scopeStatement, p.scopeStatement ?? '')
    put('businessCase', f.businessCase, p.businessCase ?? ''); put('notes', f.notes, p.notes ?? '')
    if (!Object.keys(body).length) { onClose(); return }
    setBusy(true); setErr('')
    try {
      await apiClient.put(`/api/v1/projects/${p.id}`, body)
      toast.success('Project updated')
      for (const k of ['pm-project', 'pm-activity', 'pm-members', 'pm-schedule']) qc.invalidateQueries({ queryKey: [k, p.id] })
      qc.invalidateQueries({ queryKey: ['pm-projects-active'] }); qc.invalidateQueries({ queryKey: ['pm-portfolio'] })
      onClose()
    } catch (e) { setErr(await errorText(e, 'Could not save the project')) } finally { setBusy(false) }
  }

  const ta = (k: keyof typeof f, rows = 2, max = 4000) => <textarea rows={rows} maxLength={max} value={f[k]} onChange={e => set(k, e.target.value)} style={{ ...inp, resize: 'vertical' }} />
  return (
    <Dialog width={780} title={`Edit ${p.projectNumber}`} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save'}</Btn></>}>
      <Field label="Name"><input autoFocus maxLength={255} value={f.name} onChange={e => set('name', e.target.value)} style={inp} /></Field>
      <Field label="Description">{ta('description', 2, 2000)}</Field>
      <div style={g2}>
        <Field label="Site address"><input maxLength={500} value={f.siteAddress} onChange={e => set('siteAddress', e.target.value)} style={inp} /></Field>
        <Field label="Start" hint={locked ? 'Fixed once the project is approved (it is part of the baseline)' : undefined}>
          <input type="date" disabled={locked} value={f.startDate} onChange={e => set('startDate', e.target.value)} style={inp} /></Field>
        <Field label="Planned end" hint={locked && p.baselineEnd ? `Baseline end ${day(p.baselineEnd)}; formal moves go through change orders` : undefined}>
          <input type="date" value={f.endDate} onChange={e => set('endDate', e.target.value)} style={inp} /></Field>
      </div>
      <div style={g2}>
        <Field label="Project manager" hint={members.length ? undefined : 'Only project members can be chosen'}>
          <select value={f.projectManagerId} onChange={e => set('projectManagerId', e.target.value)} style={inp}>
            <option value="">{p.projectManagerName && !p.projectManagerId ? p.projectManagerName + ' (typed in)' : 'Not set'}</option>
            {members.map(m => <option key={m.userId} value={m.userId}>{m.userName}</option>)}</select></Field>
        <Field label="Sponsor">
          <select value={f.sponsorId} onChange={e => set('sponsorId', e.target.value)} style={inp}><option value="">Not set</option>
            {members.map(m => <option key={m.userId} value={m.userId}>{m.userName}</option>)}</select></Field>
      </div>
      <SectionTitle>Contract</SectionTitle>
      <div style={g2}>
        <Field label="Contract value (R)"><input type="number" min={0} step="0.01" value={f.contractValue} onChange={e => set('contractValue', e.target.value)} style={inp} /></Field>
        <Field label="Contract reference"><input maxLength={100} value={f.contractRef} onChange={e => set('contractRef', e.target.value)} style={inp} /></Field>
        <Field label="Form of contract"><select value={f.contractType} onChange={e => set('contractType', e.target.value)} style={inp}><option value="">—</option>
          {[...new Set([...CONTRACT_TYPES, ...(p.contractType ? [p.contractType] : [])])].map(t => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}</select></Field>
        <Field label="Retention %"><input type="number" min={0} max={100} step="0.5" value={f.retentionPct} onChange={e => set('retentionPct', e.target.value)} style={inp} /></Field>
        <Field label="CIDB grade"><input maxLength={20} value={f.cidbGrade} onChange={e => set('cidbGrade', e.target.value)} style={inp} /></Field>
        <Field label="NHBRC number"><input maxLength={50} value={f.nhbrcNumber} onChange={e => set('nhbrcNumber', e.target.value)} style={inp} /></Field>
      </div>
      <SectionTitle>Governance</SectionTitle>
      <div style={g2}>
        <Field label="Objectives">{ta('objectives')}</Field>
        <Field label="Scope">{ta('scopeStatement')}</Field>
        <Field label="Business case">{ta('businessCase')}</Field>
        <Field label="Notes">{ta('notes', 2, 2000)}</Field>
      </div>
      <div style={muted}>The budget is the sum of the budget lines, so it changes on the Budget & cost tab.</div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function EditProjectButton({ project }: { project: Project }) {
  const canWrite = usePermission('PM_WRITE')
  const [open, setOpen] = useState(false)
  if (!canWrite || ['COMPLETED', 'CLOSED', 'CANCELLED'].includes(project.status)) return null
  return <><Btn onClick={() => setOpen(true)}><Pencil size={13} /> Edit</Btn>{open && <EditProjectDialog project={project} onClose={() => setOpen(false)} />}</>
}

// ── Activity ─────────────────────────────────────────────────────────────────

interface ActivityItem { kind: string; summary: string; detail: string | null; actorName: string | null; at: string; refId: string | null }
const KIND_ICON: Record<string, typeof Activity> = { STATUS: Flag, CHANGE_ORDER: GitPullRequest, RFI: MessageSquare, DOCUMENT: FileText, MEMBER: Users,
  BUDGET: Wallet, ISSUE: AlertTriangle, DETAILS: Pencil, CONTRACT: FileSignature }
const KIND_LABEL: Record<string, string> = { STATUS: 'Status', CHANGE_ORDER: 'Change orders', RFI: 'RFIs', DOCUMENT: 'Documents', MEMBER: 'Members',
  BUDGET: 'Budget', ISSUE: 'Issues', DETAILS: 'Details', CONTRACT: 'Contracts' }

export function ActivityFeed({ projectId }: { projectId: string }) {
  const [kind, setKind] = useState('')
  const q = useInfiniteQuery<ActivityItem[]>({
    queryKey: ['pm-activity', projectId], initialPageParam: null as string | null,
    queryFn: async ({ pageParam }) => list<ActivityItem>(await apiClient.get(`/api/v1/projects/${projectId}/activity`, { params: { limit: 40, before: pageParam ?? undefined } })),
    getNextPageParam: last => last.length < 40 ? undefined : last[last.length - 1].at,
  })
  const items = (q.data?.pages.flat() ?? []).filter(i => !kind || i.kind === kind)
  const kinds = [...new Set((q.data?.pages.flat() ?? []).map(i => i.kind))]
  return (
    <div style={card}>
      <SectionTitle right={kinds.length > 1 && <select value={kind} onChange={e => setKind(e.target.value)} style={{ ...inp, width: 160 }} aria-label="Show">
        <option value="">Everything</option>{kinds.map(k => <option key={k} value={k}>{KIND_LABEL[k] ?? k}</option>)}</select>}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><Activity size={15} /> Recent activity</span></SectionTitle>
      {q.isLoading ? <Empty>Loading…</Empty> : q.isError ? <ErrorLine text="Could not load the activity." /> : items.length === 0 ? <Empty>Nothing has happened on this project yet.</Empty> : (
        <ol style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {items.map((i, n) => {
            const Icon = KIND_ICON[i.kind] ?? Activity
            const today = new Date().toDateString() === new Date(i.at).toDateString()
            return (
              <li key={n} style={{ display: 'flex', gap: 10, padding: '8px 0', borderBottom: '1px solid var(--hf-border)' }}>
                <span style={{ width: 26, height: 26, borderRadius: '50%', background: 'var(--hf-surface-sunken)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Icon size={13} style={{ color: 'var(--hf-text-muted)' }} /></span>
                <span style={{ flex: 1, minWidth: 0 }}>
                  <span style={{ fontSize: 13 }}>{i.summary}</span>
                  {i.detail && <span style={{ display: 'block', fontSize: 12, color: 'var(--hf-text-muted)', whiteSpace: 'pre-wrap' }}>{i.detail}</span>}
                  <span style={{ display: 'block', fontSize: 11, color: 'var(--hf-text-faint)' }}>{i.actorName ? `${i.actorName} · ` : ''}{today ? 'today ' + new Date(i.at).toLocaleTimeString('en-ZA', { hour: '2-digit', minute: '2-digit' }) : when(i.at)}</span>
                </span>
              </li>
            )
          })}
        </ol>
      )}
      {q.hasNextPage && <div style={{ textAlign: 'center', marginTop: 8 }}><Btn small disabled={q.isFetchingNextPage} onClick={() => q.fetchNextPage()}>{q.isFetchingNextPage ? 'Loading…' : 'Show older'}</Btn></div>}
    </div>
  )
}

// ── Contracts ────────────────────────────────────────────────────────────────

interface Contract { id: string; contractNumber: string; title: string; contractType: string; status: string; startDate: string | null; endDate: string | null; valueAmount: number | null }
const CONTRACT_TONE: Record<string, 'neutral' | 'info' | 'success' | 'warning' | 'danger'> = { DRAFT: 'neutral', UNDER_REVIEW: 'info', SENT: 'warning', SIGNED: 'success', TERMINATED: 'danger', EXPIRED: 'neutral' }
const DRAFT_TYPES = ['SUBCONTRACTOR', 'SUPPLY', 'CONSULTING', 'EQUIPMENT_HIRE', 'SERVICE_LEVEL', 'JOINT_VENTURE', 'NDA']

export function ProjectContracts({ project }: { project: Project }) {
  const qc = useQueryClient()
  const canWrite = usePermission('PM_WRITE')
  const canPick = usePermissions(['USER_READ', 'USER_UPDATE', 'CONTRACTS_MANAGE', 'CONTRACTS_ADMIN'], 'any')
  const canDraft = usePermissions(['USER_UPDATE', 'CONTRACTS_MANAGE'], 'any')
  const editable = !['COMPLETED', 'CLOSED', 'CANCELLED'].includes(project.status)
  const [dialog, setDialog] = useState<null | 'link' | 'draft'>(null)
  const { data = [], isLoading, isError, error } = useQuery<Contract[]>({ queryKey: ['pm-contracts', project.id], queryFn: async () => list<Contract>(await apiClient.get(`/api/v1/projects/${project.id}/contracts`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['pm-contracts', project.id] }); qc.invalidateQueries({ queryKey: ['pm-activity', project.id] }) }
  if (isError && (error as any)?.response?.status === 403) return null   // no cost access on this project: contracts carry values
  const unlink = async (c: Contract) => {
    if (!window.confirm(`Unlink ${c.contractNumber} from this project? The contract itself is not changed.`)) return
    try { await apiClient.delete(`/api/v1/projects/${project.id}/contracts/${c.id}`); toast.success('Contract unlinked'); refresh() } catch (e) { await fail(e, 'Could not unlink the contract') }
  }
  return (
    <div style={card}>
      <SectionTitle right={canWrite && editable && <>
        {canPick && <Btn small onClick={() => setDialog('link')}><Link2 size={12} /> Link existing</Btn>}
        {canDraft && <Btn small onClick={() => setDialog('draft')}><Plus size={12} /> New contract</Btn>}</>}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><FileSignature size={15} /> Contracts</span></SectionTitle>
      {isLoading ? <Empty>Loading…</Empty> : isError ? <ErrorLine text="Could not load the contracts." /> : data.length === 0 ? <div style={muted}>No contracts linked. Link the client contract, subcontracts and supply or hire agreements here.</div> : (
        <div style={{ display: 'grid', gap: 6 }}>
          {data.map(c => (
            <div key={c.id} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 10px', border: '1px solid var(--hf-border)', borderRadius: 8 }}>
              <span style={{ flex: 1, minWidth: 0 }}>
                <span style={{ fontSize: 13, fontWeight: 600 }}>{c.contractNumber} · {c.title}</span>
                <span style={{ display: 'block', fontSize: 11, color: 'var(--hf-text-muted)' }}>{c.contractType.replace(/_/g, ' ').toLowerCase()}{c.valueAmount != null ? ` · ${money(c.valueAmount)}` : ''}{c.endDate ? ` · to ${day(c.endDate)}` : ''}</span>
              </span>
              <Pill tone={CONTRACT_TONE[c.status] ?? 'neutral'}>{c.status.replace('_', ' ')}</Pill>
              <a href="/contracts/contracts" style={{ color: 'var(--hf-text-muted)', display: 'inline-flex' }} title="Open Contracting"><ExternalLink size={14} /></a>
              {canWrite && editable && <button onClick={() => unlink(c)} title="Unlink" aria-label={`Unlink ${c.contractNumber}`} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'inline-flex' }}><Unlink size={14} /></button>}
            </div>
          ))}
        </div>
      )}
      {dialog === 'link' && <LinkContractDialog projectId={project.id} onClose={() => setDialog(null)} onDone={refresh} />}
      {dialog === 'draft' && <DraftContractDialog project={project} onClose={() => setDialog(null)} onDone={refresh} />}
    </div>
  )
}

function LinkContractDialog({ projectId, onClose, onDone }: { projectId: string; onClose: () => void; onDone: () => void }) {
  const { data = [], isLoading } = useQuery<Contract[]>({ queryKey: ['pm-contracts-linkable'], queryFn: async () => list<Contract>(await apiClient.get(`/api/v1/projects/${projectId}/contracts/linkable`)) })
  const [pick, setPick] = useState('')
  const [q, setQ] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const shown = data.filter(c => !q || `${c.contractNumber} ${c.title}`.toLowerCase().includes(q.toLowerCase()))
  const save = async () => {
    setBusy(true); setErr('')
    try { await apiClient.post(`/api/v1/projects/${projectId}/contracts/link`, { contractId: pick }); toast.success('Contract linked'); onDone(); onClose() }
    catch (e) { setErr(await errorText(e, 'Could not link the contract')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Link a contract" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!pick || busy} onClick={save}>Link</Btn></>}>
      <input autoFocus placeholder="Search by number or title" value={q} onChange={e => setQ(e.target.value)} style={{ ...inp, marginBottom: 10 }} />
      {isLoading ? <Empty>Loading…</Empty> : shown.length === 0 ? <Empty>{data.length ? 'No contract matches.' : 'Every contract is already linked to something.'}</Empty> : (
        <div style={{ maxHeight: 320, overflowY: 'auto', display: 'grid', gap: 4 }}>
          {shown.map(c => (
            <label key={c.id} style={{ display: 'flex', gap: 8, alignItems: 'center', padding: '7px 9px', border: `1px solid ${pick === c.id ? 'var(--hf-primary)' : 'var(--hf-border)'}`, borderRadius: 8, cursor: 'pointer' }}>
              <input type="radio" name="contract" checked={pick === c.id} onChange={() => setPick(c.id)} />
              <span style={{ flex: 1 }}><b>{c.contractNumber}</b> {c.title}<span style={{ display: 'block', ...muted }}>{c.contractType.replace(/_/g, ' ').toLowerCase()} · {c.status.toLowerCase()}</span></span>
            </label>
          ))}
        </div>
      )}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function DraftContractDialog({ project: p, onClose, onDone }: { project: Project; onClose: () => void; onDone: () => void }) {
  const [f, setF] = useState({ contractType: 'SUBCONTRACTOR', title: '', valueAmount: '', startDate: p.startDate ?? '', endDate: p.endDate ?? '' })
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const set = (k: keyof typeof f, v: string) => setF(x => ({ ...x, [k]: v }))
  const save = async () => {
    setBusy(true); setErr('')
    try {
      const c = one<Contract>(await apiClient.post(`/api/v1/projects/${p.id}/contracts`, { contractType: f.contractType, title: f.title.trim() || null,
        valueAmount: f.valueAmount ? Number(f.valueAmount) : null, startDate: f.startDate || null, endDate: f.endDate || null }))
      toast.success(`Draft ${c.contractNumber} started — finish and send it in Contracting`); onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not start the contract')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Start a contract for this project" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>{busy ? 'Starting…' : 'Start draft'}</Btn></>}>
      <div style={{ ...muted, marginBottom: 12 }}>A draft is made from Contracting's template for that type, with the project's name and site filled in. Add the parties and send it for signing in Contracting.</div>
      <Field label="Type"><select value={f.contractType} onChange={e => set('contractType', e.target.value)} style={inp}>{DRAFT_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, ' ').toLowerCase()}</option>)}</select></Field>
      <Field label="Title" hint={`Leave blank for “${p.name} — ${f.contractType.replace(/_/g, ' ').toLowerCase()}”`}><input maxLength={300} value={f.title} onChange={e => set('title', e.target.value)} style={inp} /></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 10 }}>
        <Field label="Value (R)"><input type="number" min={0} step="0.01" value={f.valueAmount} onChange={e => set('valueAmount', e.target.value)} style={inp} /></Field>
        <Field label="Start"><input type="date" value={f.startDate} onChange={e => set('startDate', e.target.value)} style={inp} /></Field>
        <Field label="End"><input type="date" value={f.endDate} onChange={e => set('endDate', e.target.value)} style={inp} /></Field>
      </div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

/** Shown under the Overview tab: contracts and recent activity. */
export function OverviewExtras({ project }: { project: Project }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(360px,1fr))', gap: 14, marginTop: 16, alignItems: 'start' }}>
      <ActivityFeed projectId={project.id} />
      <ProjectContracts project={project} />
    </div>
  )
}
