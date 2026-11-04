// src/pages/projects/client-portal/ClientPortalProjectPage.tsx
// One project as the client sees it: progress and milestones, change orders to approve or dispute (value and time effect
// only), questions and change requests with the team's replies, shared documents, and files sent to the team.
import { useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { Check, Download, MessageSquarePlus, Upload, AlertTriangle, ArrowLeft } from 'lucide-react'
import { PM_PORTAL, PM_PORTAL_HOME, pmPortalClient, portalDownload, UPLOAD_STATUS, type ClientChangeOrder, type ProjectView } from '../../../api/projectClientPortal.client'
import { errorText } from '../../../api/projectDocs'
import { toast } from '../../../store/toast.store'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, ReasonDialog, SectionTitle, Stat, card, day, inp, money, muted, td, th } from '../tabs/pmKit'
import { PortalShell } from './PortalShell'
import { RequestDialog } from './RequestDialog'
import { HEALTH_LABEL, ProgressBar, RequestRow, STATUS_LABEL } from './ClientPortalHomePage'

type Tab = 'progress' | 'changes' | 'requests' | 'documents'
const MAX_MB = 10

export function ClientPortalProjectPage() {
  const { projectId = '' } = useParams<{ projectId: string }>()
  const qc = useQueryClient()
  const [tab, setTab] = useState<Tab>('progress')
  const [asking, setAsking] = useState<'QUESTION' | 'CHANGE' | null>(null)
  const { data: v, isLoading, isError, error } = useQuery<ProjectView>({ queryKey: ['pmc-project', projectId],
    queryFn: async () => (await pmPortalClient.get(`${PM_PORTAL}/me/projects/${projectId}`)).data })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['pmc-project', projectId] }); qc.invalidateQueries({ queryKey: ['pmc-projects'] }) }

  if (isLoading) return <PortalShell><Empty>Loading…</Empty></PortalShell>
  if (isError || !v) return <PortalShell>
    <ErrorLine text={(error as any)?.response?.status === 404 ? 'This project is not available to you.' : 'Could not load the project.'} />
    <Link to={PM_PORTAL_HOME} style={{ fontSize: 13 }}>Back to your projects</Link></PortalShell>

  const p = v.project
  const awaiting = v.changeOrders.filter(c => c.clientState === 'AWAITING').length
  const tabs: { key: Tab; label: string; count?: number }[] = [
    { key: 'progress', label: 'Progress' },
    { key: 'changes', label: 'Change orders', count: awaiting },
    { key: 'requests', label: 'Questions & requests', count: v.requests.filter(r => r.status === 'OPEN').length },
    { key: 'documents', label: 'Documents & files' },
  ]
  const h = HEALTH_LABEL[p.health]
  return (
    <PortalShell crumb={`${p.projectNumber} ${p.name}`}>
      <Link to={PM_PORTAL_HOME} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--hf-text-muted)', textDecoration: 'none', marginBottom: 8 }}>
        <ArrowLeft size={13} /> All projects</Link>
      <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap', marginBottom: 14 }}>
        <div style={{ flex: 1, minWidth: 240 }}>
          <div style={muted}>{p.projectNumber}{v.siteAddress ? ` · ${v.siteAddress}` : ''}</div>
          <h1 style={{ fontSize: 22, fontWeight: 800, margin: '2px 0 6px' }}>{p.name}</h1>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}><Pill tone="info">{STATUS_LABEL[p.status] ?? p.status}</Pill>{h && p.status === 'ACTIVE' && <Pill tone={h.tone}>{h.label}</Pill>}</div>
        </div>
        <Btn kind="primary" onClick={() => setAsking('QUESTION')}><MessageSquarePlus size={13} /> Ask or request a change</Btn>
      </div>

      <div style={{ ...card, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 14, marginBottom: 14 }}>
        <div><Stat label="Milestones complete" value={`${Math.round(Number(p.completionPct))}%`} /><div style={{ marginTop: 6 }}><ProgressBar pct={p.completionPct} /></div></div>
        <Stat label="Start" value={day(p.startDate)} />
        <Stat label="Planned finish" value={day(p.endDate)} />
        <Stat label="Change orders to approve" value={awaiting} tone={awaiting ? 'warn' : undefined} />
        <Stat label="Open snags" value={p.openSnags} />
      </div>

      <div role="tablist" style={{ display: 'flex', gap: 2, borderBottom: '1px solid var(--hf-border)', marginBottom: 14, overflowX: 'auto' }}>
        {tabs.map(t => <button key={t.key} role="tab" aria-selected={tab === t.key} onClick={() => setTab(t.key)}
          style={{ padding: '8px 14px', background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, whiteSpace: 'nowrap', marginBottom: -1,
            fontWeight: tab === t.key ? 600 : 400, color: tab === t.key ? 'var(--hf-primary-text)' : 'var(--hf-text-muted)',
            borderBottom: tab === t.key ? '2px solid var(--hf-primary)' : '2px solid transparent' }}>
          {t.label}{!!t.count && <span style={{ marginLeft: 5, background: 'var(--hf-warning-soft-strong)', color: 'var(--hf-warning-text-deep)', fontSize: 10, fontWeight: 700, padding: '1px 6px', borderRadius: 10 }}>{t.count}</span>}
        </button>)}
      </div>

      {tab === 'progress' && <Progress v={v} />}
      {tab === 'changes' && <ChangeOrders projectId={projectId} list={v.changeOrders} onDone={refresh} />}
      {tab === 'requests' && <>
        <SectionTitle right={<><Btn onClick={() => setAsking('QUESTION')}>Ask a question</Btn><Btn onClick={() => setAsking('CHANGE')}>Request a change</Btn></>}>Questions & requests</SectionTitle>
        <div style={{ ...card, padding: 0 }}>{v.requests.length === 0 ? <Empty>Nothing sent yet.</Empty> : v.requests.map(r => <RequestRow key={r.id} r={r} />)}</div>
      </>}
      {tab === 'documents' && <Documents projectId={projectId} v={v} onDone={refresh} />}
      {asking && <RequestDialog kind={asking} projectId={projectId} onClose={() => setAsking(null)} onDone={refresh} />}
    </PortalShell>
  )
}

function Progress({ v }: { v: ProjectView }) {
  return <>
    <SectionTitle>Milestones</SectionTitle>
    <div style={{ ...card, padding: 0, overflowX: 'auto', marginBottom: 16 }}>
      {v.milestones.length === 0 ? <Empty>No milestones set yet.</Empty> : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>Milestone</th><th style={th}>Planned</th><th style={th}>Actual</th><th style={{ ...th, width: 160 }}>Progress</th></tr></thead>
          <tbody>{v.milestones.map(m => (
            <tr key={m.taskNumber}>
              <td style={td}><b>{m.title}</b><div style={muted}>{m.taskNumber}</div></td>
              <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(m.plannedEnd)}</td>
              <td style={{ ...td, whiteSpace: 'nowrap' }}>{m.actualEnd ? day(m.actualEnd) : m.actualStart ? `started ${day(m.actualStart)}` : '—'}</td>
              <td style={td}><div style={{ display: 'flex', alignItems: 'center', gap: 8 }}><div style={{ flex: 1 }}><ProgressBar pct={m.progressPct} /></div><span style={{ fontSize: 12, width: 34, textAlign: 'right' }}>{Math.round(Number(m.progressPct))}%</span></div></td>
            </tr>))}</tbody>
        </table>)}
    </div>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 14 }}>
      <div>
        <SectionTitle>Open snags</SectionTitle>
        <div style={{ ...card, padding: 0 }}>{v.openSnags.length === 0 ? <Empty>No open snags.</Empty> : v.openSnags.map(s => (
          <div key={s.snagNumber} style={{ padding: '10px 14px', borderBottom: '1px solid var(--hf-border)', display: 'flex', gap: 8, alignItems: 'center' }}>
            <div style={{ flex: 1, minWidth: 0 }}><div style={{ fontSize: 13, fontWeight: 600 }}>{s.title}</div><div style={muted}>{s.snagNumber}{s.location ? ` · ${s.location}` : ''}{s.dueDate ? ` · due ${day(s.dueDate)}` : ''}</div></div>
            <Pill tone={s.severity === 'CRITICAL' || s.severity === 'MAJOR' ? 'danger' : 'neutral'}>{s.severity}</Pill></div>))}</div>
      </div>
      <div>
        <SectionTitle>Main risks</SectionTitle>
        <div style={{ ...card, padding: 0 }}>{v.redRisks.length === 0 ? <Empty>No high risks at the moment.</Empty> : v.redRisks.map((r, i) => (
          <div key={i} style={{ padding: '10px 14px', borderBottom: '1px solid var(--hf-border)', display: 'flex', gap: 8, alignItems: 'center' }}>
            <AlertTriangle size={14} style={{ color: 'var(--hf-danger)', flexShrink: 0 }} /><div style={{ fontSize: 13 }}>{r.title}{r.category && <span style={muted}> · {r.category}</span>}</div></div>))}</div>
      </div>
    </div>
  </>
}

const CO_STATE = { AWAITING: { label: 'Needs your decision', tone: 'warning' }, APPROVED: { label: 'You approved', tone: 'success' }, DISPUTED: { label: 'You disputed', tone: 'danger' } } as const

function ChangeOrders({ projectId, list, onDone }: { projectId: string; list: ClientChangeOrder[]; onDone: () => void }) {
  const [approving, setApproving] = useState<ClientChangeOrder | null>(null)
  const [disputing, setDisputing] = useState<ClientChangeOrder | null>(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const total = list.filter(c => c.clientState === 'APPROVED').reduce((s, c) => s + Number(c.costImpact), 0)
  const approve = async () => {
    if (!approving) return
    setBusy(true); setErr('')
    try { await pmPortalClient.post(`${PM_PORTAL}/me/projects/${projectId}/change-orders/${approving.id}/approve`); toast.success(`${approving.changeNumber} approved`); setApproving(null); onDone() }
    catch (e) { setErr(await errorText(e, 'Could not record your approval')) } finally { setBusy(false) }
  }
  return <>
    <SectionTitle right={list.length > 0 ? <span style={muted}>Approved by you so far: <b>{money(total)}</b></span> : undefined}>Change orders</SectionTitle>
    {list.length === 0 ? <div style={card}><Empty>No change orders to review.</Empty></div> : (
      <div style={{ display: 'grid', gap: 10 }}>{list.map(c => {
        const s = CO_STATE[c.clientState]
        return (
          <div key={c.id} style={{ ...card, borderColor: c.clientState === 'AWAITING' ? 'var(--hf-warning-border, var(--hf-border))' : 'var(--hf-border)' }}>
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start', flexWrap: 'wrap' }}>
              <div style={{ flex: 1, minWidth: 220 }}>
                <div style={muted}>{c.changeNumber}{c.approvedAt ? ` · issued ${day(c.approvedAt)}` : ''}</div>
                <div style={{ fontSize: 15, fontWeight: 700 }}>{c.title}</div>
              </div>
              <div style={{ textAlign: 'right', marginLeft: 'auto' }}>
                <div style={{ fontSize: 17, fontWeight: 800, whiteSpace: 'nowrap', color: Number(c.costImpact) < 0 ? 'var(--hf-success-text-strong)' : 'var(--hf-text)' }}>{Number(c.costImpact) > 0 ? '+' : ''}{money(c.costImpact)}</div>
                <div style={muted}>{c.scheduleImpactDays === 0 ? 'No change to the finish date' : c.scheduleImpactDays > 0 ? `Finish ${c.scheduleImpactDays} day${c.scheduleImpactDays === 1 ? '' : 's'} later` : `Finish ${-c.scheduleImpactDays} day${c.scheduleImpactDays === -1 ? '' : 's'} sooner`}</div>
              </div>
            </div>
            {c.description && <div style={{ fontSize: 13, marginTop: 8, whiteSpace: 'pre-wrap' }}>{c.description}</div>}
            {c.reason && <div style={{ ...muted, marginTop: 4 }}>Why: {c.reason}</div>}
            {c.clientState === 'DISPUTED' && <div style={{ ...muted, marginTop: 8, color: 'var(--hf-danger-text)' }}>Your dispute ({day(c.disputedAt)}): {c.disputeReason}. The team will respond.</div>}
            {c.disputeResolvedAt && c.clientState === 'AWAITING' && <div style={{ ...muted, marginTop: 8 }}>The team answered your dispute ({day(c.disputeResolvedAt)}): {c.disputeResolution}</div>}
            <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 10, flexWrap: 'wrap' }}>
              <Pill tone={s.tone}>{s.label}{c.clientState === 'APPROVED' && c.clientApprovedAt ? ` · ${day(c.clientApprovedAt)}` : ''}</Pill>
              <span style={{ flex: 1 }} />
              {c.clientState === 'AWAITING' && <>
                <Btn kind="ghost" onClick={() => setDisputing(c)}>Dispute</Btn>
                <Btn kind="primary" onClick={() => { setErr(''); setApproving(c) }}><Check size={13} /> Approve</Btn></>}
            </div>
          </div>)
      })}</div>)}
    {approving && <Dialog title={`Approve ${approving.changeNumber}?`} onClose={() => setApproving(null)}
      footer={<><Btn onClick={() => setApproving(null)}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={approve}>{busy ? 'Working…' : 'Approve'}</Btn></>}>
      <div style={{ fontSize: 13, lineHeight: 1.6 }}>You are approving <b>{approving.title}</b> for <b>{Number(approving.costImpact) > 0 ? '+' : ''}{money(approving.costImpact)}</b>
        {approving.scheduleImpactDays !== 0 && <> and a finish date {Math.abs(approving.scheduleImpactDays)} day{Math.abs(approving.scheduleImpactDays) === 1 ? '' : 's'} {approving.scheduleImpactDays > 0 ? 'later' : 'sooner'}</>}.
        Your name and the time are recorded.</div>
      {err && <ErrorLine text={err} />}
    </Dialog>}
    {disputing && <ReasonDialog title={`Dispute ${disputing.changeNumber}`} label="What you dispute" confirm="Send dispute" danger max={1000}
      intro="Say what is wrong (the price, the time, or the work itself). The project team is told at once and will reply."
      onClose={() => setDisputing(null)}
      onSubmit={async reason => { await pmPortalClient.post(`${PM_PORTAL}/me/projects/${projectId}/change-orders/${disputing.id}/dispute`, { reason }); toast.success('Dispute sent'); onDone() }} />}
  </>
}

function Documents({ projectId, v, onDone }: { projectId: string; v: ProjectView; onDone: () => void }) {
  const [sending, setSending] = useState(false)
  return <>
    <SectionTitle>Shared with you</SectionTitle>
    <div style={{ ...card, padding: 0, overflowX: 'auto', marginBottom: 16 }}>
      {v.documents.length === 0 ? <Empty>No documents shared yet.</Empty> : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>Document</th><th style={th}>Revision</th><th style={th}>Approved</th><th style={th} /></tr></thead>
          <tbody>{v.documents.map(d => (
            <tr key={d.id}>
              <td style={td}><b>{d.title}</b><div style={muted}>{d.documentNumber} · {d.documentType.toLowerCase()}</div></td>
              <td style={td}>{d.revision ?? '—'}</td>
              <td style={{ ...td, whiteSpace: 'nowrap' }}>{day(d.approvedAt)}</td>
              <td style={{ ...td, textAlign: 'right' }}>{d.downloadable && <Btn small onClick={() => portalDownload(`${PM_PORTAL}/me/projects/${projectId}/documents/${d.id}/download`, d.title)
                .catch(async e => toast.error(await errorText(e, 'Could not download the file')))}><Download size={12} /> Download</Btn>}</td>
            </tr>))}</tbody>
        </table>)}
    </div>
    <SectionTitle right={<Btn kind="primary" onClick={() => setSending(true)}><Upload size={13} /> Send a file</Btn>}>Files you sent</SectionTitle>
    <div style={{ ...card, padding: 0 }}>
      {v.uploads.length === 0 ? <Empty>Send drawings, approvals or photos to the project team. They review each file before adding it to the project.</Empty> : v.uploads.map(u => {
        const s = UPLOAD_STATUS[u.status]
        return (
          <div key={u.id} style={{ padding: '10px 14px', borderBottom: '1px solid var(--hf-border)', display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
            <div style={{ flex: 1, minWidth: 200 }}>
              <div style={{ fontSize: 13, fontWeight: 600, wordBreak: 'break-word' }}>{u.fileName}</div>
              <div style={muted}>{u.uploadedByName} · {day(u.createdAt)} · {(u.sizeBytes / 1024 / 1024).toFixed(1)} MB{u.note ? ` · ${u.note}` : ''}</div>
              {u.status === 'REJECTED' && u.reason && <div style={{ ...muted, color: 'var(--hf-danger-text)' }}>{u.reason}</div>}
            </div>
            <Pill tone={s.tone}>{s.label}</Pill>
          </div>)
      })}
    </div>
    {sending && <SendFileDialog projectId={projectId} onClose={() => setSending(false)} onDone={onDone} />}
  </>
}

function SendFileDialog({ projectId, onClose, onDone }: { projectId: string; onClose: () => void; onDone: () => void }) {
  const ref = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [note, setNote] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)
  const send = async () => {
    if (!file) { setErr('Choose a file'); return }
    if (file.size > MAX_MB * 1024 * 1024) { setErr(`Files can be at most ${MAX_MB} MB`); return }
    setBusy(true); setErr('')
    try {
      const fd = new FormData(); fd.append('file', file); if (note.trim()) fd.append('note', note.trim())
      await pmPortalClient.post(`${PM_PORTAL}/me/projects/${projectId}/uploads`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
      toast.success('File sent to the project team'); onDone(); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not send the file')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Send a file" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy || !file} onClick={send}>{busy ? 'Sending…' : 'Send'}</Btn></>}>
      <Field label="File" hint={`Documents, drawings and photos up to ${MAX_MB} MB. Programs and scripts are not accepted.`}>
        <input ref={ref} type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ ...inp, padding: 6 }} /></Field>
      <Field label="Note for the team (optional)"><textarea rows={3} maxLength={1000} value={note} onChange={e => setNote(e.target.value)} style={{ ...inp, resize: 'vertical' }} /></Field>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
