// src/pages/projects/client-portal/ClientPortalHomePage.tsx
// A client contact's projects: progress, dates, and what is waiting on them (change orders to approve, replies to read).
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Plus, ChevronRight } from 'lucide-react'
import { PM_PORTAL, PM_PORTAL_HOME, pmPortalClient, KIND_LABEL, REQUEST_STATUS, type ClientRequest, type Me, type MyProject } from '../../../api/projectClientPortal.client'
import { Btn, Empty, ErrorLine, Pill, SectionTitle, card, day, muted } from '../tabs/pmKit'
import { PortalShell } from './PortalShell'
import { RequestDialog } from './RequestDialog'

export const HEALTH_LABEL: Record<string, { label: string; tone: 'success' | 'warning' | 'danger' }> = {
  GREEN: { label: 'On track', tone: 'success' }, AMBER: { label: 'Needs attention', tone: 'warning' }, RED: { label: 'At risk', tone: 'danger' } }
export const STATUS_LABEL: Record<string, string> = { PLANNING: 'Planning', PROPOSED: 'Planning', APPROVED: 'Approved', ACTIVE: 'In progress',
  ON_HOLD: 'On hold', COMPLETED: 'Completed', CLOSED: 'Closed', CANCELLED: 'Cancelled' }

export function ProgressBar({ pct }: { pct: number }) {
  const v = Math.max(0, Math.min(100, Number(pct) || 0))
  return <div style={{ height: 6, background: 'var(--hf-surface-sunken)', borderRadius: 4, overflow: 'hidden' }} role="progressbar" aria-valuenow={Math.round(v)} aria-valuemin={0} aria-valuemax={100}>
    <div style={{ width: `${v}%`, height: '100%', background: 'var(--hf-primary)' }} /></div>
}

export function ClientPortalHomePage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [asking, setAsking] = useState(false)
  const { data: me } = useQuery<Me>({ queryKey: ['pmc-me'], queryFn: async () => (await pmPortalClient.get(`${PM_PORTAL}/me`)).data })
  const { data: projects = [], isLoading, isError } = useQuery<MyProject[]>({ queryKey: ['pmc-projects'],
    queryFn: async () => (await pmPortalClient.get(`${PM_PORTAL}/me/projects`)).data ?? [] })
  const { data: newRequests = [] } = useQuery<ClientRequest[]>({ queryKey: ['pmc-new-requests'],
    queryFn: async () => (await pmPortalClient.get(`${PM_PORTAL}/me/requests`)).data ?? [] })
  const waiting = projects.reduce((s, p) => s + p.changeOrdersAwaiting, 0)

  return (
    <PortalShell>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
        <div style={{ flex: 1, minWidth: 220 }}>
          <h1 style={{ fontSize: 22, fontWeight: 800, margin: 0 }}>Hello{me ? `, ${me.fullName.split(' ')[0]}` : ''}</h1>
          <div style={{ ...muted, marginTop: 4 }}>{me?.accounts.map(a => a.clientName).join(' · ')}</div>
        </div>
        <Btn kind="primary" onClick={() => setAsking(true)} disabled={!me}><Plus size={13} /> Request a new project</Btn>
      </div>

      {waiting > 0 && <div style={{ ...card, borderColor: 'var(--hf-warning-border, var(--hf-border))', background: 'var(--hf-warning-soft)', marginBottom: 14, fontSize: 13 }}>
        <b>{waiting} change order{waiting === 1 ? '' : 's'}</b> need{waiting === 1 ? 's' : ''} your decision. Open the project to approve or dispute.</div>}

      {isLoading ? <Empty>Loading…</Empty> : isError ? <ErrorLine text="Could not load your projects." /> : projects.length === 0 ? <div style={card}><Empty>No projects to show yet.</Empty></div> : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 12 }}>
          {projects.map(p => {
            const h = HEALTH_LABEL[p.health]
            return (
              <button key={p.id} onClick={() => navigate(`${PM_PORTAL_HOME}/projects/${p.id}`)}
                style={{ ...card, textAlign: 'left', cursor: 'pointer', display: 'flex', flexDirection: 'column', gap: 10, color: 'var(--hf-text)', font: 'inherit' }}>
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={muted}>{p.projectNumber}</div>
                    <div style={{ fontSize: 15, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</div>
                  </div>
                  <ChevronRight size={16} style={{ color: 'var(--hf-text-faint)', flexShrink: 0 }} />
                </div>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  <Pill tone="info">{STATUS_LABEL[p.status] ?? p.status}</Pill>
                  {h && p.status === 'ACTIVE' && <Pill tone={h.tone}>{h.label}</Pill>}
                  {p.changeOrdersAwaiting > 0 && <Pill tone="warning">{p.changeOrdersAwaiting} to approve</Pill>}
                  {p.openRequests > 0 && <Pill>{p.openRequests} awaiting reply</Pill>}
                </div>
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 4 }}>
                    <span style={{ color: 'var(--hf-text-muted)' }}>Milestones</span><b>{Math.round(Number(p.completionPct))}%</b></div>
                  <ProgressBar pct={p.completionPct} />
                </div>
                <div style={{ ...muted, display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                  <span>{day(p.startDate)} – {day(p.endDate)}</span>{p.openSnags > 0 && <span>{p.openSnags} open snag{p.openSnags === 1 ? '' : 's'}</span>}</div>
              </button>
            )
          })}
        </div>)}

      {newRequests.length > 0 && <>
        <SectionTitle>Your new-project requests</SectionTitle>
        <div style={{ ...card, padding: 0 }}>
          {newRequests.map(r => <RequestRow key={r.id} r={r} />)}
        </div>
      </>}
      {asking && me && <RequestDialog kind="NEW_PROJECT" accounts={me.accounts} onClose={() => setAsking(false)}
        onDone={() => qc.invalidateQueries({ queryKey: ['pmc-new-requests'] })} />}
    </PortalShell>
  )
}

export function RequestRow({ r }: { r: ClientRequest }) {
  const s = REQUEST_STATUS[r.status]
  return (
    <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--hf-border)' }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <span style={muted}>{r.requestNumber}</span><Pill>{KIND_LABEL[r.kind]}</Pill><b style={{ fontSize: 13 }}>{r.title}</b>
        <span style={{ flex: 1 }} /><Pill tone={s.tone}>{s.label}</Pill>
      </div>
      {r.description && <div style={{ fontSize: 13, marginTop: 6, whiteSpace: 'pre-wrap', color: 'var(--hf-text-secondary, var(--hf-text))' }}>{r.description}</div>}
      <div style={{ ...muted, marginTop: 4 }}>Sent by {r.raisedByName} · {day(r.createdAt)}{r.neededBy ? ` · needed by ${day(r.neededBy)}` : ''}</div>
      {r.response && <div style={{ marginTop: 8, padding: '8px 10px', borderLeft: '3px solid var(--hf-primary)', background: 'var(--hf-surface-muted)', borderRadius: 6, fontSize: 13, whiteSpace: 'pre-wrap' }}>
        {r.response}
        <div style={{ ...muted, marginTop: 4 }}>{r.decidedByName ?? 'Project team'} · {day(r.decidedAt)}
          {r.linkedType === 'CHANGE_ORDER' && ' · the team is pricing it as a change order'}
          {r.linkedType === 'RFI' && ' · logged as a formal RFI'}
          {r.linkedType === 'PROJECT' && ' · set up as a new project'}</div>
      </div>}
    </div>
  )
}
