// src/pages/projects/tabs/PortalAccessTab.tsx
// Client portal access controls: whether the client's link works now, when it expires, issuing a new link (the old one stops at
// once), switching access off and back on, and what the client can see there.
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, ExternalLink, KeyRound, Power, PowerOff, ShieldCheck } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { usePermission } from '../../../hooks/usePermission'
import { toast } from '../../../store/toast.store'
import { errorText } from '../../../api/projectDocs'
import { Btn, Dialog, Empty, ErrorLine, Field, Pill, SectionTitle, card, inp, muted, one, when } from './pmKit'

interface PortalStatus { enabled: boolean; accessible: boolean; expiresAt: string | null; token: string | null }

const linkFor = (token: string) => `${window.location.origin}/projects/portal/${token}`

export function PortalAccessTab({ projectId, clientName, currentToken }: { projectId: string; clientName: string | null; currentToken: string | null }) {
  const qc = useQueryClient()
  const canAdmin = usePermission('PM_ADMIN')
  const [rotating, setRotating] = useState(false)
  const [issued, setIssued] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const { data: s, isLoading, isError } = useQuery<PortalStatus>({
    queryKey: ['pm-portal-status', projectId],
    queryFn: async () => one<PortalStatus>(await apiClient.get(`/api/v1/projects/${projectId}/portal`)),
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['pm-portal-status', projectId] }); qc.invalidateQueries({ queryKey: ['pm-project', projectId] }) }

  const toggle = async (on: boolean) => {
    if (!on && !window.confirm('Switch the client portal off? The client’s link stops working until you switch it back on.')) return
    setBusy(true)
    try { await apiClient.post(`/api/v1/projects/${projectId}/portal/${on ? 'enable' : 'revoke'}`); toast.success(on ? 'Portal access switched on' : 'Portal access switched off'); refresh() }
    catch (e) { toast.error(await errorText(e, 'Could not change portal access')) } finally { setBusy(false) }
  }

  if (isLoading) return <Empty>Loading…</Empty>
  if (isError || !s) return <ErrorLine text="Could not load the portal status." />
  const expired = s.enabled && !s.accessible
  const days = s.expiresAt ? Math.ceil((new Date(s.expiresAt).getTime() - Date.now()) / 86400000) : null

  return (
    <div style={{ display: 'grid', gap: 14, maxWidth: 820 }}>
      <div style={card}>
        <SectionTitle right={s.accessible ? <Pill tone="success">Link works</Pill> : expired ? <Pill tone="danger">Link expired</Pill> : <Pill tone="neutral">Switched off</Pill>}>
          Read-only link{clientName ? ` for ${clientName}` : ''}</SectionTitle>
        <div style={{ fontSize: 13, lineHeight: 1.6 }}>
          {s.enabled ? <>Access is <b>on</b>. </> : <>Access is <b>off</b> — the link shows nothing. </>}
          {s.expiresAt ? <>The link {expired ? 'expired' : 'expires'} {when(s.expiresAt)}{!expired && days != null && days <= 30 ? <b> ({days} day{days === 1 ? '' : 's'} left)</b> : null}.</> : <>The link never expires.</>}
        </div>
        {expired && <div style={{ ...muted, marginTop: 6 }}>Issue a new link to give the client access again.</div>}
        {canAdmin ? (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 14 }}>
            <Btn kind="primary" onClick={() => setRotating(true)}><KeyRound size={13} /> Issue a new link</Btn>
            {s.accessible && currentToken && <Btn onClick={() => window.open(linkFor(currentToken), '_blank', 'noopener')}><ExternalLink size={13} /> See what the client sees</Btn>}
            {s.enabled ? <Btn kind="danger" disabled={busy} onClick={() => toggle(false)}><PowerOff size={13} /> Switch off</Btn>
              : <Btn disabled={busy} onClick={() => toggle(true)}><Power size={13} /> Switch on</Btn>}
          </div>
        ) : <div style={{ ...muted, marginTop: 10 }}>Only a project administrator can change portal access.</div>}
      </div>

      {issued && (
        <div style={{ ...card, borderColor: 'var(--hf-success-border, var(--hf-border))', background: 'var(--hf-success-soft)' }}>
          <SectionTitle>New link — send it to the client</SectionTitle>
          <div style={{ display: 'flex', gap: 8 }}>
            <input readOnly value={linkFor(issued)} onFocus={e => e.target.select()} style={{ ...inp, fontFamily: 'monospace', fontSize: 12 }} aria-label="Portal link" />
            <Btn onClick={() => navigator.clipboard?.writeText(linkFor(issued)).then(() => toast.success('Link copied'), () => toast.error('Copy failed — select the link and copy it'))}><Copy size={13} /> Copy</Btn>
            <Btn onClick={() => window.open(linkFor(issued), '_blank', 'noopener')}><ExternalLink size={13} /> Open</Btn>
          </div>
          <div style={{ ...muted, marginTop: 6 }}>Anyone with this link can see the portal, so send it only to the client. The previous link no longer works.</div>
        </div>
      )}

      <div style={card}>
        <SectionTitle><span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><ShieldCheck size={15} /> What the link shows</span></SectionTitle>
        <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, lineHeight: 1.7 }}>
          <li>Project name, status, health, start and end dates and overall progress</li>
          <li>Milestones and their dates</li>
          <li>Open snags and red-rated risks</li>
          <li>Documents that are approved and marked visible to the client, with a download where a file is stored</li>
        </ul>
        <div style={{ ...muted, marginTop: 8 }}>Budgets, costs, change order values, internal tasks and team members are never shown on the link.
          Named client logins (Projects › Client logins) also see approved change orders with their value and time effect, so they can approve or dispute them, and can send questions and files. Costs stay hidden there too.</div>
      </div>

      {rotating && <RotateDialog projectId={projectId} onClose={() => setRotating(false)} onIssued={t => { setIssued(t); refresh() }} />}
    </div>
  )
}

function RotateDialog({ projectId, onClose, onIssued }: { projectId: string; onClose: () => void; onIssued: (token: string) => void }) {
  const [ttl, setTtl] = useState('default')
  const [custom, setCustom] = useState('90')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const go = async () => {
    const ttlDays = ttl === 'default' ? null : ttl === 'custom' ? Number(custom) : Number(ttl)
    if (ttl === 'custom' && !(ttlDays! >= 1 && ttlDays! <= 730)) { setErr('Choose between 1 and 730 days'); return }
    setBusy(true); setErr('')
    try {
      const r = one<PortalStatus>(await apiClient.post(`/api/v1/projects/${projectId}/portal/rotate-token`, ttl === 'default' ? {} : { ttlDays }))
      if (!r.token) throw new Error('no token')
      toast.success('New portal link issued'); onIssued(r.token); onClose()
    } catch (e) { setErr(await errorText(e, 'Could not issue a new link')) } finally { setBusy(false) }
  }
  return (
    <Dialog title="Issue a new portal link" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={busy} onClick={go}>{busy ? 'Issuing…' : 'Issue link'}</Btn></>}>
      <div style={{ fontSize: 13, marginBottom: 12, lineHeight: 1.5 }}>The client’s current link <b>stops working immediately</b>. The new link is shown once, here — copy it before you close it.</div>
      <Field label="The new link works for">
        <select value={ttl} onChange={e => setTtl(e.target.value)} style={inp}>
          <option value="default">The standard period</option>
          <option value="30">30 days</option><option value="90">90 days</option><option value="180">6 months</option><option value="365">1 year</option>
          <option value="custom">Another number of days…</option><option value="0">Never expires</option>
        </select>
      </Field>
      {ttl === 'custom' && <Field label="Days (1–730)"><input type="number" min={1} max={730} value={custom} onChange={e => setCustom(e.target.value)} style={{ ...inp, maxWidth: 140 }} /></Field>}
      {ttl === '0' && <div style={{ ...muted, color: 'var(--hf-warning-text-deep)' }}>A link that never expires keeps working until you issue a new one or switch access off.</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
