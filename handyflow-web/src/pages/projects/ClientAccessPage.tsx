// src/pages/projects/ClientAccessPage.tsx
// Projects > External access > Client logins. Who from a client can sign in to the Projects client portal (all of that
// client's projects, or chosen ones), and clients' requests for new projects. Inviting needs PM_ADMIN; answering
// new-project requests needs PM_WRITE (the same right as creating a project).
import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Send, Ban, Pencil } from 'lucide-react'
import { apiClient } from '../../api/client'
import { usePermission } from '../../hooks/usePermission'
import { toast } from '../../store/toast.store'
import { errorText } from '../../api/projectDocs'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, SectionTitle, card, day, fail, inp, list, muted, td, th, when } from './tabs/pmKit'
import { KIND_LABEL, REQUEST_STATUS, type ClientRequest } from '../../api/projectClientPortal.client'

interface ProjectOption { id: string; projectNumber: string; name: string; status: string }
interface ClientOption { clientId: string | null; clientName: string; projects: ProjectOption[] }
interface Grant { id: string; clientId: string | null; clientName: string; allProjects: boolean; projects: ProjectOption[]; email: string; contactName: string | null
  status: 'PENDING' | 'ACTIVE' | 'REVOKED'; invitedByName: string | null; invitedAt: string; inviteExpiresAt: string | null; acceptedAt: string | null
  lastSeenAt: string | null; revokedAt: string | null }

const ADMIN = '/api/v1/project-client/admin'
const GRANT_TONE = { PENDING: 'warning', ACTIVE: 'success', REVOKED: 'neutral' } as const
const GRANT_LABEL = { PENDING: 'Invite sent', ACTIVE: 'Active', REVOKED: 'Removed' } as const

export function ClientAccessPage() {
  const isAdmin = usePermission('PM_ADMIN')
  const canWrite = usePermission('PM_WRITE')
  return (
    <div>
      <div style={{ ...muted, marginBottom: 14, lineHeight: 1.5, maxWidth: 760 }}>
        Client contacts sign in at <b>/projects/client-portal</b> with the same login they use for your other client portals. They see progress,
        milestones, open snags, high risks and shared documents; they approve or dispute change orders (value and time only, never your costs);
        and they send questions, change requests, new-project requests and files, which land in each project's <b>Client portal</b> tab for your team to handle.
      </div>
      {isAdmin && <Logins />}
      {canWrite && <NewProjectRequests />}
      {!isAdmin && !canWrite && <Empty>You need Projects admin rights to manage client logins.</Empty>}
    </div>
  )
}

function Logins() {
  const qc = useQueryClient()
  const [inviting, setInviting] = useState(false)
  const [editing, setEditing] = useState<Grant | null>(null)
  const [showRemoved, setShowRemoved] = useState(false)
  const { data: grants = [], isLoading, isError } = useQuery<Grant[]>({ queryKey: ['pm-client-grants'], queryFn: async () => list<Grant>(await apiClient.get(`${ADMIN}/grants`)) })
  const refresh = () => qc.invalidateQueries({ queryKey: ['pm-client-grants'] })
  const rows = grants.filter(g => showRemoved || g.status !== 'REVOKED')
  const act = async (g: Grant, what: 'resend' | 'revoke') => {
    try { await apiClient.post(`${ADMIN}/grants/${g.id}/${what}`); toast.success(what === 'resend' ? 'Invite sent again' : 'Access removed'); refresh() }
    catch (e) { await fail(e, 'That did not work') }
  }
  return <>
    <SectionTitle right={<>
      <label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--hf-text-muted)' }}>
        <input type="checkbox" checked={showRemoved} onChange={e => setShowRemoved(e.target.checked)} /> Show removed</label>
      <Btn kind="primary" onClick={() => setInviting(true)}><Plus size={13} /> Invite a client contact</Btn></>}>Client logins</SectionTitle>
    <div style={{ ...card, padding: 0, overflowX: 'auto', marginBottom: 22 }}>
      {isLoading ? <Empty>Loading…</Empty> : isError ? <ErrorLine text="Could not load client logins." /> : rows.length === 0 ? <Empty>No client logins yet. Invite a contact to give them their projects online.</Empty> : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>Contact</th><th style={th}>Client</th><th style={th}>Projects</th><th style={th}>Status</th><th style={th}>Last seen</th><th style={th} /></tr></thead>
          <tbody>{rows.map(g => (
            <tr key={g.id} style={{ opacity: g.status === 'REVOKED' ? 0.6 : 1 }}>
              <td style={td}><b>{g.contactName ?? g.email}</b>{g.contactName && <div style={muted}>{g.email}</div>}</td>
              <td style={td}>{g.clientName}</td>
              <td style={td}>{g.status === 'REVOKED' ? '—' : g.allProjects ? <><span>All their projects</span><div style={muted}>{g.projects.length} now</div></>
                : <span title={g.projects.map(p => `${p.projectNumber} ${p.name}`).join('\n')}>{g.projects.length} chosen</span>}</td>
              <td style={td}><Pill tone={GRANT_TONE[g.status]}>{GRANT_LABEL[g.status]}</Pill>
                <div style={muted}>{g.status === 'PENDING' ? `until ${day(g.inviteExpiresAt)}` : g.status === 'ACTIVE' ? `since ${day(g.acceptedAt)}` : day(g.revokedAt)}</div></td>
              <td style={{ ...td, whiteSpace: 'nowrap' }}>{g.lastSeenAt ? when(g.lastSeenAt) : '—'}</td>
              <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>{g.status !== 'REVOKED' && <>
                <Btn small kind="ghost" onClick={() => setEditing(g)}><Pencil size={12} /> Projects</Btn>
                {g.status === 'PENDING' && <Btn small kind="ghost" onClick={() => act(g, 'resend')}><Send size={12} /> Send again</Btn>}
                <Btn small kind="ghost" onClick={() => act(g, 'revoke')}><Ban size={12} /> Remove</Btn></>}</td>
            </tr>))}</tbody>
        </table>)}
    </div>
    {inviting && <InviteDialog onClose={() => setInviting(false)} onDone={refresh} />}
    {editing && <ScopeDialog grant={editing} onClose={() => setEditing(null)} onDone={refresh} />}
  </>
}

function useClients() {
  return useQuery<ClientOption[]>({ queryKey: ['pm-client-options'], staleTime: 60_000, queryFn: async () => list<ClientOption>(await apiClient.get(`${ADMIN}/clients`)) })
}

function ProjectPicker({ projects, all, setAll, chosen, setChosen }:
  { projects: ProjectOption[]; all: boolean; setAll: (v: boolean) => void; chosen: Set<string>; setChosen: (s: Set<string>) => void }) {
  const toggle = (id: string) => { const n = new Set(chosen); n.has(id) ? n.delete(id) : n.add(id); setChosen(n) }
  return <>
    <div role="radiogroup" style={{ display: 'grid', gap: 6, marginBottom: 10 }}>
      <label style={{ display: 'flex', gap: 8, fontSize: 13 }}><input type="radio" checked={all} onChange={() => setAll(true)} />
        <span><b>All of this client's projects</b><span style={{ ...muted, display: 'block' }}>Including projects you add for them later.</span></span></label>
      <label style={{ display: 'flex', gap: 8, fontSize: 13 }}><input type="radio" checked={!all} onChange={() => setAll(false)} /><b>Only the projects I choose</b></label>
    </div>
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 8, maxHeight: 220, overflowY: 'auto' }}>
      {projects.map(p => (
        <label key={p.id} style={{ display: 'flex', gap: 8, alignItems: 'center', padding: '7px 10px', borderBottom: '1px solid var(--hf-border)', fontSize: 13, opacity: all ? 0.65 : 1 }}>
          <input type="checkbox" disabled={all} checked={all || chosen.has(p.id)} onChange={() => toggle(p.id)} />
          <span style={{ flex: 1 }}><b>{p.projectNumber}</b> {p.name}</span><span style={muted}>{p.status.toLowerCase().replace('_', ' ')}</span>
        </label>))}
    </div>
  </>
}

function InviteDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { data: clients = [], isLoading } = useClients()
  const [key, setKey] = useState('')
  const [all, setAll] = useState(true)
  const [chosen, setChosen] = useState<Set<string>>(new Set())
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const keyOf = (c: ClientOption) => c.clientId ?? `name:${c.clientName.toLowerCase()}`
  const client = useMemo(() => clients.find(c => keyOf(c) === key) ?? null, [clients, key])
  const send = async () => {
    if (!client) { setErr('Choose the client'); return }
    if (!/^\S+@\S+\.\S+$/.test(email.trim())) { setErr('Enter a valid email address'); return }
    if (!all && chosen.size === 0) { setErr('Choose at least one project'); return }
    setBusy(true); setErr('')
    try {
      await apiClient.post(`${ADMIN}/grants`, { clientId: client.clientId, clientName: client.clientName, allProjects: all,
        projectIds: all ? [] : [...chosen], email: email.trim(), contactName: name.trim() || null })
      toast.success(`Invite emailed to ${email.trim()}`); onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not send the invite')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Invite a client contact" width={560} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={send}>{busy ? 'Sending…' : 'Send invite'}</Btn></>}>
      <Field label="Client" hint="Clients are taken from the client named on your projects.">
        <select value={key} onChange={e => { setKey(e.target.value); setChosen(new Set()) }} style={inp} disabled={isLoading}>
          <option value="">{isLoading ? 'Loading…' : clients.length ? 'Choose…' : 'No project names a client yet'}</option>
          {clients.map(c => <option key={keyOf(c)} value={keyOf(c)}>{c.clientName} ({c.projects.length} project{c.projects.length === 1 ? '' : 's'})</option>)}
        </select></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Contact name"><input value={name} onChange={e => setName(e.target.value)} maxLength={255} style={inp} /></Field>
        <Field label="Email"><input type="email" value={email} onChange={e => setEmail(e.target.value)} maxLength={255} style={inp} /></Field>
      </div>
      {client && <ProjectPicker projects={client.projects} all={all} setAll={setAll} chosen={chosen} setChosen={setChosen} />}
      <div style={{ ...muted, marginTop: 10 }}>The invite link is emailed, works once and lasts 14 days. You never see it, so it cannot be forwarded by mistake from here.</div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function ScopeDialog({ grant, onClose, onDone }: { grant: Grant; onClose: () => void; onDone: () => void }) {
  const { data: clients = [] } = useClients()
  const client = clients.find(c => grant.clientId ? c.clientId === grant.clientId : c.clientName.toLowerCase() === grant.clientName.toLowerCase())
  const [all, setAll] = useState(grant.allProjects)
  const [chosen, setChosen] = useState<Set<string>>(new Set(grant.allProjects ? [] : grant.projects.map(p => p.id)))
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const save = async () => {
    if (!all && chosen.size === 0) { setErr('Choose at least one project'); return }
    setBusy(true); setErr('')
    try { await apiClient.put(`${ADMIN}/grants/${grant.id}/projects`, { allProjects: all, projectIds: all ? [] : [...chosen] }); toast.success('Access updated'); onDone(); onClose() }
    catch (e) { setErr(await errorText(e, 'Could not change the projects')) } finally { setBusy(false) }
  }
  return (
    <Dialog title={`Projects for ${grant.contactName ?? grant.email}`} width={560} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={save}>Save</Btn></>}>
      <div style={{ ...muted, marginBottom: 10 }}>{grant.clientName} · takes effect at once</div>
      <ProjectPicker projects={client?.projects ?? grant.projects} all={all} setAll={setAll} chosen={chosen} setChosen={setChosen} />
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

const PROJECT_TYPES = ['CONSTRUCTION', 'EARTHMOVING', 'SECURITY', 'EVENT', 'IT', 'GENERAL']

function NewProjectRequests() {
  const qc = useQueryClient()
  const [deciding, setDeciding] = useState<ClientRequest | null>(null)
  const [showClosed, setShowClosed] = useState(false)
  const { data = [], isLoading } = useQuery<ClientRequest[]>({ queryKey: ['pm-client-new-projects'], queryFn: async () => list<ClientRequest>(await apiClient.get(`${ADMIN}/new-project-requests`)) })
  const rows = data.filter(r => showClosed || r.status === 'OPEN')
  return <>
    <SectionTitle right={<label style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12, color: 'var(--hf-text-muted)' }}>
      <input type="checkbox" checked={showClosed} onChange={e => setShowClosed(e.target.checked)} /> Show answered</label>}>New-project requests from clients</SectionTitle>
    <div style={{ ...card, padding: 0 }}>
      {isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No open requests.</Empty> : rows.map(r => (
        <div key={r.id} style={{ padding: '12px 14px', borderBottom: '1px solid var(--hf-border)', display: 'flex', gap: 10, alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 240 }}>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}><span style={muted}>{r.requestNumber}</span><b style={{ fontSize: 13 }}>{r.title}</b><Pill tone={REQUEST_STATUS[r.status].tone}>{REQUEST_STATUS[r.status].label}</Pill></div>
            {r.description && <div style={{ fontSize: 13, marginTop: 4, whiteSpace: 'pre-wrap' }}>{r.description}</div>}
            <div style={{ ...muted, marginTop: 4 }}>{r.raisedByName} ({r.raisedByEmail}) · {day(r.createdAt)}{r.neededBy ? ` · needed by ${day(r.neededBy)}` : ''}</div>
            {r.response && <div style={{ ...muted, marginTop: 4 }}>Reply ({r.decidedByName}): {r.response}</div>}
          </div>
          {r.status === 'OPEN' && <Btn onClick={() => setDeciding(r)}>Respond</Btn>}
        </div>))}
    </div>
    {deciding && <DecideDialog request={deciding} path={`${ADMIN}/new-project-requests/${deciding.id}/decide`} onClose={() => setDeciding(null)}
      onDone={() => qc.invalidateQueries({ queryKey: ['pm-client-new-projects'] })} />}
  </>
}

/** Accept (turns it into work), answer, or decline, always with a reply the client sees. Shared with the project inbox. */
export function DecideDialog({ request, path, onClose, onDone }:
  { request: ClientRequest; path: string; onClose: () => void; onDone: () => void }) {
  const isNew = request.kind === 'NEW_PROJECT'
  const acceptLabel = request.kind === 'QUESTION' ? 'Log it as an RFI' : request.kind === 'CHANGE' ? 'Start a change order' : 'Create the project'
  const [outcome, setOutcome] = useState<'ACCEPT' | 'ANSWER' | 'DECLINE'>(request.kind === 'QUESTION' ? 'ANSWER' : 'ACCEPT')
  const [response, setResponse] = useState('')
  const [projectType, setProjectType] = useState('CONSTRUCTION')
  const [budget, setBudget] = useState('')
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const go = async () => {
    if (!response.trim()) { setErr('Write a reply for the client'); return }
    if (outcome === 'ACCEPT' && isNew && !(Number(budget) > 0)) { setErr('Enter the budget'); return }
    if (start && end && end < start) { setErr('The end date is before the start date'); return }
    setBusy(true); setErr('')
    try {
      await apiClient.post(path, { outcome, response: response.trim(), ...(outcome === 'ACCEPT' && isNew ? { projectType, budgetTotal: Number(budget), startDate: start || null, endDate: end || null } : {}) })
      toast.success(outcome === 'ACCEPT' ? (isNew ? 'Project created in planning' : request.kind === 'CHANGE' ? 'Draft change order created' : 'Draft RFI created') : 'Reply sent')
      onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not save')) } finally { setBusy(false) }
  }
  const opts: { v: typeof outcome; label: string; hint: string }[] = [
    { v: 'ACCEPT', label: acceptLabel, hint: isNew ? 'A project in planning for this client, ready for you to fill in.' : request.kind === 'CHANGE' ? 'A draft change order for you to price (needs cost access on the project). The client approves it once it is approved internally.' : 'A draft RFI with their question, for the design team.' },
    { v: 'ANSWER', label: 'Just reply', hint: 'Your reply closes the request.' },
    { v: 'DECLINE', label: 'Decline', hint: 'Say why in the reply.' },
  ]
  return (
    <Dialog title={`${KIND_LABEL[request.kind]} ${request.requestNumber}`} width={560} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind={outcome === 'DECLINE' ? 'danger' : 'primary'} disabled={busy} onClick={go}>{busy ? 'Saving…' : 'Save and reply'}</Btn></>}>
      <div style={{ fontSize: 13, fontWeight: 700 }}>{request.title}</div>
      {request.description && <div style={{ fontSize: 13, whiteSpace: 'pre-wrap', margin: '4px 0 6px' }}>{request.description}</div>}
      <div style={{ ...muted, marginBottom: 12 }}>{request.raisedByName} · {day(request.createdAt)}{request.neededBy ? ` · needed by ${day(request.neededBy)}` : ''}</div>
      <div role="radiogroup" style={{ display: 'grid', gap: 6, marginBottom: 12 }}>
        {opts.map(o => <label key={o.v} style={{ display: 'flex', gap: 8, fontSize: 13 }}><input type="radio" checked={outcome === o.v} onChange={() => setOutcome(o.v)} />
          <span><b>{o.label}</b><span style={{ ...muted, display: 'block' }}>{o.hint}</span></span></label>)}
      </div>
      {outcome === 'ACCEPT' && isNew && <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Project type"><select value={projectType} onChange={e => setProjectType(e.target.value)} style={inp}>{PROJECT_TYPES.map(t => <option key={t} value={t}>{t.charAt(0) + t.slice(1).toLowerCase()}</option>)}</select></Field>
        <Field label="Budget (R)"><input type="number" min={0.01} step="0.01" value={budget} onChange={e => setBudget(e.target.value)} style={inp} /></Field>
        <Field label="Start (optional)"><input type="date" value={start} onChange={e => setStart(e.target.value)} style={inp} /></Field>
        <Field label="End (optional)"><input type="date" value={end} onChange={e => setEnd(e.target.value)} style={inp} /></Field>
      </div>}
      <Field label="Reply to the client"><textarea rows={4} maxLength={5000} value={response} onChange={e => setResponse(e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
