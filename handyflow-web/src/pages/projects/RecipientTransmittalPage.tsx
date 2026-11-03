// src/pages/projects/RecipientTransmittalPage.tsx  —  public: /secure/share/:token (Secure Document Access Engine)
import { useParams } from 'react-router-dom'
import { CodeGate, DeadLink, ErrorBox, PublicShell, TransmittalCard, useSecureAccess, type PortalView } from './secureAccess'

export function RecipientTransmittalPage() {
  const { token = '' } = useParams<{ token: string }>()
  const a = useSecureAccess<PortalView>(`/api/public/access/shares/${encodeURIComponent(token)}`)

  if (a.phase === 'dead') return <PublicShell><DeadLink error={a.error} /></PublicShell>
  if (a.phase === 'gate') return (
    <PublicShell>
      <CodeGate heading="Documents sent to you" notice={a.notice} error={a.error} sendCode={a.sendCode} verify={a.verify}
        intro="To protect these documents, we first confirm your email address. We will email you a six-digit code." />
    </PublicShell>
  )
  if (a.phase === 'loading' || !a.data) return <PublicShell><div style={{ padding: 60, textAlign: 'center', color: 'var(--hf-text-faint)' }}>Loading…</div></PublicShell>

  return (
    <PublicShell title="Shared documents">
      <TransmittalCard view={a.data}
        onDownload={async d => { await a.guard(() => a.api.download(`/documents/${d.documentId}/download`, d.fileName || `${d.documentNumber}-${d.revision}`)) }}
        onAcknowledge={async note => { const v = await a.guard(() => a.api.post<PortalView>('/acknowledge', { note: note || null })); if (v) a.setData(v) }} />
      {a.error && <ErrorBox>{a.error.message}</ErrorBox>}
    </PublicShell>
  )
}
