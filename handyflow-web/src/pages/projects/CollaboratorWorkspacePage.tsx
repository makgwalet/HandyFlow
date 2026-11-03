// src/pages/projects/CollaboratorWorkspacePage.tsx  —  public: /secure/workspace/:token
// A registered collaborator's standing workspace: every share issued to them that is still active.
import { useParams } from 'react-router-dom'
import { CodeGate, DeadLink, ErrorBox, PublicShell, TransmittalCard, fmtDate, useSecureAccess, type PortalView } from './secureAccess'

interface Entry { recipientId: string; share: PortalView }
interface Workspace { name: string; organisation: string | null; expiresAt: string; shares: Entry[] }

export function CollaboratorWorkspacePage() {
  const { token = '' } = useParams<{ token: string }>()
  const a = useSecureAccess<Workspace>(`/api/public/access/collaborators/${encodeURIComponent(token)}`)

  if (a.phase === 'dead') return <PublicShell><DeadLink error={a.error} /></PublicShell>
  if (a.phase === 'gate') return (
    <PublicShell>
      <CodeGate heading="Your shared documents" notice={a.notice} error={a.error} sendCode={a.sendCode} verify={a.verify}
        intro="This is your standing workspace. We first confirm your email address with a six-digit code." />
    </PublicShell>
  )
  if (a.phase === 'loading' || !a.data) return <PublicShell><div style={{ padding: 60, textAlign: 'center', color: 'var(--hf-text-faint)' }}>Loading…</div></PublicShell>

  const ws = a.data
  return (
    <PublicShell title="Shared documents">
      <div style={{ marginBottom: 16 }}>
        <h1 style={{ fontSize: 22, margin: 0 }}>Hello, {ws.name}</h1>
        <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>
          {ws.organisation ? `${ws.organisation} · ` : ''}Your workspace link is valid until {fmtDate(ws.expiresAt)}
        </div>
      </div>
      {ws.shares.length === 0 && <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-text-faint)' }}>Nothing has been issued to you yet.</div>}
      {ws.shares.map(e => (
        <TransmittalCard key={e.recipientId} view={e.share}
          onDownload={async d => { await a.guard(() => a.api.download(`/shares/${e.recipientId}/documents/${d.documentId}/download`, d.fileName || `${d.documentNumber}-${d.revision}`)) }}
          onAcknowledge={async note => {
            const w = await a.guard(() => a.api.post<Workspace>(`/shares/${e.recipientId}/acknowledge`, { note: note || null }))
            if (w) a.setData(w)
          }} />
      ))}
      {a.error && <ErrorBox>{a.error.message}</ErrorBox>}
    </PublicShell>
  )
}
