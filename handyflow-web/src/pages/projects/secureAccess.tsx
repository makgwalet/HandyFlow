// src/pages/projects/secureAccess.tsx
//
// Shared pieces of the two public secure-link pages: the "confirm it's you" code gate, the state hook
// behind it, and a few small presentational helpers. Uses the standalone recipient client (never apiClient).
import React, { useCallback, useMemo, useRef, useState } from 'react'
import { Lock, Mail, ShieldAlert, FileText, Download, Loader2 } from 'lucide-react'
import { accessApi, toAccessError, type AccessError } from '../../api/recipientAccess.client'

export const fmtDateTime = (iso?: string | null) => iso ? new Date(iso).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
export const fmtDate = (iso?: string | null) => iso ? new Date(iso).toLocaleDateString('en-ZA', { dateStyle: 'medium' }) : '—'
export const PURPOSE_LABEL: Record<string, string> = {
  FOR_INFORMATION: 'For information', FOR_REVIEW: 'For review', FOR_APPROVAL: 'For approval',
  FOR_CONSTRUCTION: 'For construction', AS_BUILT: 'As built',
}

export type Phase = 'gate' | 'loading' | 'ready' | 'dead'

/**
 * State machine of a public page: gate (needs a code) -> loading -> ready; dead = unknown / expired / withdrawn link.
 * A 401 at any later point (the 30-minute session ended) drops back to the gate.
 */
export function useSecureAccess<T>(base: string, loadPath = '') {
  const session = useRef<string | null>(null)
  const api = useMemo(() => accessApi(base, () => session.current), [base])
  const [phase, setPhase] = useState<Phase>('gate')
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<AccessError | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  /** Every call goes through here so a lapsed session returns the visitor to the code gate. */
  const guard = useCallback(async <R,>(fn: () => Promise<R>): Promise<R | undefined> => {
    try { return await fn() } catch (e) {
      const err = await toAccessError(e)
      if (err.kind === 'needs-code') {
        session.current = null; setData(null); setPhase('gate'); setNotice('Your session ended. Request a new code to continue.')
      } else if (err.kind === 'not-found' || err.kind === 'gone') {
        session.current = null; setData(null); setPhase('dead'); setError(err)
      } else setError(err)
      return undefined
    }
  }, [])

  const load = useCallback(async () => {
    setPhase('loading')
    const v = await guard(() => api.get<T>(loadPath))
    if (v) { setData(v); setPhase('ready'); setError(null) }
  }, [api, guard, loadPath])

  const sendCode = useCallback(async () => {
    setError(null)
    const r = await guard(() => api.requestCode())
    if (r) setNotice(null)
    return r
  }, [api, guard])

  const verify = useCallback(async (code: string) => {
    setError(null)
    const s = await guard(() => api.verify(code))
    if (!s) return false
    session.current = s.session; setNotice(null)
    await load()
    return true
  }, [api, guard, load])

  return { phase, data, setData, error, setError, notice, api, guard, sendCode, verify, load }
}

// ── Presentation ─────────────────────────────────────────────────────────────

export function PublicShell({ children, title }: { children: React.ReactNode; title?: string }) {
  return (
    <div style={{ minHeight: '100vh', background: 'var(--hf-bg, #f5f6f8)', fontFamily: "'Inter',sans-serif", color: 'var(--hf-text)', padding: '32px 16px' }}>
      <div style={{ maxWidth: 760, margin: '0 auto' }}>
        {title && <div style={{ fontSize: 12, fontWeight: 700, letterSpacing: '.06em', textTransform: 'uppercase', color: 'var(--hf-text-faint)', marginBottom: 14 }}>{title}</div>}
        {children}
      </div>
    </div>
  )
}

export const card: React.CSSProperties = { background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 14, padding: 24 }
export const primaryBtn: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 6, padding: '10px 18px', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: 'pointer' }
export const ghostBtn: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '8px 12px', background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer' }

export function ErrorBox({ children }: { children: React.ReactNode }) {
  return <div role="alert" style={{ marginTop: 12, padding: '9px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, color: 'var(--hf-danger-text)', fontSize: 13 }}>{children}</div>
}

export function DeadLink({ error }: { error: AccessError | null }) {
  return (
    <div style={{ ...card, textAlign: 'center', padding: '44px 24px' }}>
      <ShieldAlert size={34} style={{ color: 'var(--hf-text-faint)', marginBottom: 10 }} />
      <div style={{ fontSize: 16, fontWeight: 700, marginBottom: 6 }}>{error?.kind === 'gone' ? 'This link is no longer active' : 'Link not found'}</div>
      <div style={{ fontSize: 14, color: 'var(--hf-text-muted)', maxWidth: 420, margin: '0 auto' }}>{error?.message}</div>
    </div>
  )
}

/** Step 1: ask for a code. Step 2: enter it. The code goes to the address the link was issued to. */
export function CodeGate({ heading, intro, notice, error, sendCode, verify }: {
  heading: string; intro: string; notice: string | null; error: AccessError | null
  sendCode: () => Promise<{ sentTo: string; validMinutes: number } | undefined>
  verify: (code: string) => Promise<boolean>
}) {
  const [sent, setSent] = useState<{ sentTo: string; validMinutes: number } | null>(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)

  const onSend = async () => { setBusy(true); const r = await sendCode(); setBusy(false); if (r) { setSent(r); setCode('') } }
  const onVerify = async (e: React.FormEvent) => {
    e.preventDefault(); if (!/^[0-9]{6}$/.test(code)) return
    setBusy(true); const ok = await verify(code); setBusy(false); if (!ok) setCode('')
  }

  return (
    <div style={{ ...card, maxWidth: 460, margin: '40px auto 0', textAlign: 'center' }}>
      <div style={{ width: 52, height: 52, borderRadius: '50%', background: 'var(--hf-info-soft-strong)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 14px' }}>
        {sent ? <Mail size={22} style={{ color: 'var(--hf-info-text)' }} /> : <Lock size={22} style={{ color: 'var(--hf-info-text)' }} />}
      </div>
      <h1 style={{ fontSize: 19, margin: '0 0 6px' }}>{heading}</h1>
      {!sent ? (
        <>
          <p style={{ fontSize: 14, color: 'var(--hf-text-muted)', margin: '0 0 18px', lineHeight: 1.5 }}>{intro}</p>
          {notice && <div style={{ fontSize: 13, color: 'var(--hf-warning-text-deep)', marginBottom: 12 }}>{notice}</div>}
          <button onClick={onSend} disabled={busy} style={{ ...primaryBtn, opacity: busy ? .6 : 1 }}>
            {busy ? <Loader2 size={15} className="animate-spin" /> : <Mail size={15} />} Email me a code
          </button>
        </>
      ) : (
        <form onSubmit={onVerify}>
          <p style={{ fontSize: 14, color: 'var(--hf-text-muted)', margin: '0 0 16px', lineHeight: 1.5 }}>
            We sent a six-digit code to <strong>{sent.sentTo}</strong>. It is valid for {sent.validMinutes} minutes.
          </p>
          <input value={code} onChange={e => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            inputMode="numeric" autoComplete="one-time-code" autoFocus aria-label="Six-digit code" placeholder="••••••"
            style={{ width: 200, textAlign: 'center', fontSize: 26, letterSpacing: '.35em', padding: '10px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 10, background: 'var(--hf-surface)', color: 'var(--hf-text)', outline: 'none' }} />
          <div style={{ marginTop: 16, display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}>
            <button type="submit" disabled={busy || code.length !== 6} style={{ ...primaryBtn, opacity: busy || code.length !== 6 ? .55 : 1 }}>
              {busy && <Loader2 size={15} className="animate-spin" />} Continue
            </button>
            <button type="button" onClick={onSend} disabled={busy} style={ghostBtn}>Send a new code</button>
          </div>
        </form>
      )}
      {error && <ErrorBox>{error.message}</ErrorBox>}
      <div style={{ marginTop: 18, fontSize: 11, color: 'var(--hf-text-faint)' }}>This confirms you are the person the documents were sent to. Your access is recorded.</div>
    </div>
  )
}

export interface PortalDoc { documentId: string; documentNumber: string; title: string; revision: string; statusAtIssue: string; fileName: string | null; fileSha256: string | null; downloadable: boolean }
export interface PortalView {
  transmittalNumber: string; subject: string; projectName: string; purpose: string; notes: string | null
  sentBy: string; sentAt: string; recipientName: string; expiresAt: string
  canDownload: boolean; canAcknowledge: boolean; acknowledgedAt: string | null; documents: PortalDoc[]
}

/** One transmittal: details, document list with downloads, acknowledge. Shared by both pages. */
export function TransmittalCard({ view, onDownload, onAcknowledge }: {
  view: PortalView
  onDownload: (d: PortalDoc) => Promise<void>
  onAcknowledge: (note: string) => Promise<void>
}) {
  const [busyDoc, setBusyDoc] = useState<string | null>(null)
  const [note, setNote] = useState('')
  const [acking, setAcking] = useState(false)

  const dl = async (d: PortalDoc) => { setBusyDoc(d.documentId); try { await onDownload(d) } finally { setBusyDoc(null) } }
  const ack = async () => { setAcking(true); try { await onAcknowledge(note.trim()) } finally { setAcking(false) } }

  return (
    <div style={{ ...card, marginBottom: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <div style={{ fontSize: 12, color: 'var(--hf-text-faint)', fontWeight: 600 }}>{view.transmittalNumber} · {view.projectName}</div>
          <h2 style={{ fontSize: 18, margin: '2px 0 4px' }}>{view.subject}</h2>
        </div>
        <span style={{ alignSelf: 'flex-start', fontSize: 11, fontWeight: 700, padding: '3px 10px', borderRadius: 20, background: 'var(--hf-info-soft-strong)', color: 'var(--hf-info-text)' }}>
          {PURPOSE_LABEL[view.purpose] ?? view.purpose}
        </span>
      </div>
      <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginBottom: 10 }}>
        From {view.sentBy} · {fmtDateTime(view.sentAt)} · your access ends {fmtDate(view.expiresAt)}
      </div>
      {view.notes && <div style={{ fontSize: 14, padding: '10px 12px', background: 'var(--hf-surface-muted)', borderRadius: 8, marginBottom: 12, whiteSpace: 'pre-wrap' }}>{view.notes}</div>}

      <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflow: 'hidden' }}>
        {view.documents.length === 0 && <div style={{ padding: 16, fontSize: 13, color: 'var(--hf-text-faint)' }}>No documents are available to you on this transmittal.</div>}
        {view.documents.map((d, i) => (
          <div key={d.documentId} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 14px', borderTop: i ? '1px solid var(--hf-border-subtle)' : 'none' }}>
            <FileText size={18} style={{ color: 'var(--hf-text-faint)', flexShrink: 0 }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 14, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis' }}>{d.title}</div>
              <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', wordBreak: 'break-all' }}>
                {d.documentNumber} · Rev {d.revision}{d.fileName ? ` · ${d.fileName}` : ''}
                {d.fileSha256 && <span title="SHA-256 of the issued file"> · SHA-256 {d.fileSha256.slice(0, 12)}…</span>}
              </div>
            </div>
            {view.canDownload && d.downloadable
              ? <button onClick={() => dl(d)} disabled={busyDoc === d.documentId} style={{ ...ghostBtn, opacity: busyDoc === d.documentId ? .6 : 1 }}>
                  {busyDoc === d.documentId ? <Loader2 size={14} className="animate-spin" /> : <Download size={14} />} Download
                </button>
              : <span style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{view.canDownload ? 'No file' : 'View only'}</span>}
          </div>
        ))}
      </div>

      {view.canAcknowledge && (
        <div style={{ marginTop: 14 }}>
          {view.acknowledgedAt
            ? <div style={{ fontSize: 13, color: 'var(--hf-success-text-strong)', fontWeight: 600 }}>✓ Receipt acknowledged {fmtDateTime(view.acknowledgedAt)}</div>
            : <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <input value={note} onChange={e => setNote(e.target.value)} maxLength={500} placeholder="Optional note to the sender"
                  style={{ flex: 1, minWidth: 180, padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 9, fontSize: 14, background: 'var(--hf-surface)', color: 'var(--hf-text)' }} />
                <button onClick={ack} disabled={acking} style={{ ...primaryBtn, opacity: acking ? .6 : 1 }}>
                  {acking && <Loader2 size={14} className="animate-spin" />} Acknowledge receipt
                </button>
              </div>}
        </div>
      )}
    </div>
  )
}
