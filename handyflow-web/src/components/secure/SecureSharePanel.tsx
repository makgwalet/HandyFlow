// src/components/secure/SecureSharePanel.tsx
// Staff side of the Secure Document Access Engine for any module: the people who have a link to a record's documents, giving one
// more person access, a new link, revoking, and the access log. The module's API is under `base` and follows the same shape:
//   POST {base}/recipients, POST {base}/recipients/{id}/reissue, POST {base}/recipients/{id}/revoke, GET {base}/log.
// Each recipient gets their own expiring link and confirms their email with a code before anything opens.
import React, { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { UserPlus, RefreshCw, Ban, ScrollText, X, Link2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { errorText } from '../../api/projectDocs'

export interface ShareRecipient {
  id: string; name: string; organisation: string | null; email: string; canDownload: boolean; canAcknowledge: boolean
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED'; expiresAt: string; revokedAt: string | null; revokedByName: string | null; revokeReason: string | null
  emailSentAt: string | null; verifiedAt: string | null; lastAccessAt: string | null; acknowledgedAt: string | null; itemIds: string[]
}
interface LogEntry { id: string; recipientId: string | null; documentId: string | null; revision: string | null; action: string; detail: string | null; ipAddress: string | null; actorName: string | null; occurredAt: string }

const dt = (iso?: string | null) => iso ? new Date(iso).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
const inp: React.CSSProperties = { width: '100%', padding: '8px 11px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, boxSizing: 'border-box', outline: 'none', background: 'var(--hf-surface)', color: 'var(--hf-text)' }
const btn: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '5px 9px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-text-secondary)' }
const primary: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '7px 13px', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const muted: React.CSSProperties = { fontSize: 12, color: 'var(--hf-text-faint)' }
const STATUS: Record<ShareRecipient['status'], { bg: string; color: string; label: string }> = {
  ACTIVE: { bg: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', label: 'Active' },
  EXPIRED: { bg: 'var(--hf-surface-sunken)', color: 'var(--hf-text-tertiary)', label: 'Expired' },
  REVOKED: { bg: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', label: 'Revoked' },
}
const ACTION: Record<string, string> = {
  LINK_ISSUED: 'Link sent', LINK_REISSUED: 'New link sent', LINK_UNAVAILABLE: 'Link used after it stopped working',
  OTP_REQUESTED: 'Code requested', OTP_FAILED: 'Wrong code', OTP_LOCKED: 'Too many wrong codes', CODE_EXPIRED: 'Code expired',
  OTP_VERIFIED: 'Email confirmed', VERIFICATION_REQUIRED: 'Asked to confirm email', VIEWED: 'Opened', DOWNLOADED: 'Downloaded',
  DOWNLOAD_FAILED: 'Download failed', DOWNLOAD_NOT_PERMITTED: 'Download refused (view only)', ACKNOWLEDGED: 'Acknowledged',
  ACK_NOT_PERMITTED: 'Acknowledgement refused', ACCESS_REFUSED: 'Refused', REVOKED: 'Access revoked',
}

/**
 * {@code extraBody}: added to the first "give access" request (FM sends the chosen files there). Return a string from
 * {@code blockAdd} to explain why access cannot be given yet (for example, no file chosen).
 */
export function SecureSharePanel({ base, recipients, canChange, shareable, notShareableReason, extraBody, blockAdd, onChanged, intro }: {
  base: string; recipients: ShareRecipient[]; canChange: boolean; shareable: boolean; notShareableReason?: string | null
  extraBody?: () => Record<string, unknown>; blockAdd?: () => string | null; onChanged: () => void; intro?: React.ReactNode
}) {
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ name: '', organisation: '', email: '', days: '14', canDownload: true, canAcknowledge: true })
  const [err, setErr] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [showLog, setShowLog] = useState(false)

  const log = useQuery<LogEntry[]>({ queryKey: ['secure-share-log', base], enabled: showLog, queryFn: async () => (await apiClient.get(`${base}/log`)).data ?? [] })
  const names = Object.fromEntries(recipients.map(r => [r.id, r.name]))

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setErr(''); setBusy(true)
    try { await fn(); setNote(ok); onChanged(); if (showLog) log.refetch() }
    catch (e) { setErr(await errorText(e, 'That did not work.')) }
    finally { setBusy(false) }
  }
  const days = () => { const n = Number(form.days); return Number.isInteger(n) && n >= 1 && n <= 90 ? n : null }
  const blocked = blockAdd?.() ?? null
  const validEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())

  const add = () => run(async () => {
    await apiClient.post(`${base}/recipients`, {
      ...(recipients.length === 0 && extraBody ? extraBody() : {}),
      name: form.name.trim(), organisation: form.organisation.trim() || null, email: form.email.trim(),
      canDownload: form.canDownload, canAcknowledge: form.canAcknowledge, expiryDays: days(),
    })
    setAdding(false); setForm({ name: '', organisation: '', email: '', days: '14', canDownload: true, canAcknowledge: true })
  }, 'Link sent. They will confirm their email with a code before anything opens.')

  return (
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, background: 'var(--hf-surface)', padding: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
          <Link2 size={13} /> Secure sharing
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          <button style={btn} onClick={() => setShowLog(v => !v)}><ScrollText size={12} /> {showLog ? 'Hide access log' : 'Access log'}</button>
          {canChange && shareable && !adding && <button style={primary} onClick={() => { setErr(''); setNote(''); setAdding(true) }}><UserPlus size={13} /> Give access</button>}
        </div>
      </div>
      {intro && <div style={{ ...muted, marginBottom: 10 }}>{intro}</div>}
      {!shareable && notShareableReason && <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginBottom: 10 }}>{notShareableReason}</div>}
      {note && <div style={{ fontSize: 13, color: 'var(--hf-success-text-strong)', marginBottom: 8 }}>{note}</div>}
      {err && <div role="alert" style={{ marginBottom: 8, padding: '8px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, color: 'var(--hf-danger-text)', fontSize: 13 }}>{err}</div>}

      {adding && (
        <div style={{ border: '1px dashed var(--hf-border)', borderRadius: 9, padding: 12, marginBottom: 12 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 8 }}>
            <input aria-label="Name" placeholder="Name" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} style={inp} />
            <input aria-label="Organisation" placeholder="Organisation (optional)" value={form.organisation} onChange={e => setForm({ ...form, organisation: e.target.value })} style={inp} />
            <input aria-label="Email" type="email" placeholder="Email address" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} style={inp} />
            <input aria-label="Link valid for (days)" type="number" min={1} max={90} value={form.days} onChange={e => setForm({ ...form, days: e.target.value })} style={inp} title="Days the link stays valid (1 to 90)" />
          </div>
          <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', marginTop: 8, fontSize: 13 }}>
            <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}><input type="checkbox" checked={form.canDownload} onChange={e => setForm({ ...form, canDownload: e.target.checked })} /> May download</label>
            <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}><input type="checkbox" checked={form.canAcknowledge} onChange={e => setForm({ ...form, canAcknowledge: e.target.checked })} /> May acknowledge receipt</label>
          </div>
          {blocked && <div style={{ ...muted, marginTop: 8, color: 'var(--hf-danger-text)' }}>{blocked}</div>}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 10 }}>
            <button style={btn} onClick={() => setAdding(false)}><X size={12} /> Cancel</button>
            <button style={primary} disabled={busy || !!blocked || form.name.trim() === '' || !validEmail || days() == null} onClick={add}><UserPlus size={13} /> Send link</button>
          </div>
        </div>
      )}

      {recipients.length === 0 ? (
        <div style={{ ...muted, padding: '8px 0' }}>Nobody outside the company has access.</div>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead><tr style={{ textAlign: 'left', color: 'var(--hf-text-faint)', fontSize: 11 }}>
              {['Person', 'Access', 'Link expires', 'Activity', ''].map(h => <th key={h} style={{ padding: '6px 8px', fontWeight: 600 }}>{h}</th>)}
            </tr></thead>
            <tbody>
              {recipients.map(r => (
                <tr key={r.id} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                  <td style={{ padding: '8px' }}><strong>{r.name}</strong>{r.organisation ? ` · ${r.organisation}` : ''}<div style={muted}>{r.email}</div></td>
                  <td style={{ padding: '8px' }}>
                    <span style={{ background: STATUS[r.status].bg, color: STATUS[r.status].color, fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 12 }}>{STATUS[r.status].label}</span>
                    <div style={muted}>{[r.canDownload ? 'download' : 'view only', r.canAcknowledge ? 'acknowledge' : null].filter(Boolean).join(' · ')}</div>
                    {r.revokedAt && <div style={muted}>{r.revokedByName}: {r.revokeReason}</div>}
                  </td>
                  <td style={{ padding: '8px' }}>{dt(r.expiresAt)}</td>
                  <td style={{ padding: '8px', ...muted }}>
                    {r.verifiedAt ? `Confirmed ${dt(r.verifiedAt)}` : 'Not opened yet'}
                    {r.lastAccessAt && <div>Last visit {dt(r.lastAccessAt)}</div>}
                    {r.acknowledgedAt && <div>Acknowledged {dt(r.acknowledgedAt)}</div>}
                  </td>
                  <td style={{ padding: '8px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                    {canChange && r.status !== 'REVOKED' && shareable && <button style={{ ...btn, marginRight: 4 }} disabled={busy}
                      onClick={() => { const v = window.prompt('Days the new link stays valid (1 to 90):', '14'); if (v == null) return; const n = Number(v)
                        if (!Number.isInteger(n) || n < 1 || n > 90) { setErr('Use a whole number of days from 1 to 90.'); return }
                        run(() => apiClient.post(`${base}/recipients/${r.id}/reissue`, { expiryDays: n }), 'New link sent; the previous link no longer works.') }}>
                      <RefreshCw size={12} /> New link</button>}
                    {canChange && r.status !== 'REVOKED' && <button style={{ ...btn, color: 'var(--hf-danger-text)' }} disabled={busy}
                      onClick={() => { const v = window.prompt('Reason for revoking access (kept in the access log):'); if (!v || v.trim().length < 3) return
                        run(() => apiClient.post(`${base}/recipients/${r.id}/revoke`, { reason: v.trim() }), 'Access revoked. The link stopped working at once.') }}>
                      <Ban size={12} /> Revoke</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showLog && (
        <div style={{ marginTop: 12, borderTop: '1px solid var(--hf-border-subtle)', paddingTop: 10 }}>
          {log.isLoading ? <div style={muted}>Loading…</div>
            : (log.data ?? []).length === 0 ? <div style={muted}>Nothing has happened yet.</div>
            : (
              <div style={{ maxHeight: 260, overflowY: 'auto' }}>
                {(log.data ?? []).map(l => (
                  <div key={l.id} style={{ display: 'flex', gap: 10, fontSize: 12, padding: '4px 0', borderBottom: '1px solid var(--hf-border-subtle)' }}>
                    <span style={{ color: 'var(--hf-text-faint)', whiteSpace: 'nowrap' }}>{dt(l.occurredAt)}</span>
                    <span style={{ fontWeight: 600 }}>{ACTION[l.action] ?? l.action}</span>
                    <span style={{ color: 'var(--hf-text-muted)' }}>{l.recipientId ? names[l.recipientId] ?? '' : l.actorName ?? ''}{l.detail ? ` · ${l.detail}` : ''}{l.ipAddress ? ` · ${l.ipAddress}` : ''}</span>
                  </div>
                ))}
              </div>
            )}
        </div>
      )}
    </div>
  )
}
