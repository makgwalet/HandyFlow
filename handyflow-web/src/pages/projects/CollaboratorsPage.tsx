// src/pages/projects/CollaboratorsPage.tsx
// Registered external collaborators: standing workspace links for people who work with us repeatedly.
// Reading needs PM_READ; registering, reissuing, disabling and enabling need PM_APPROVE or PM_ADMIN
// (the server enforces it; the buttons are only hidden to avoid a 403).
import React, { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { UserPlus, RefreshCw, Ban, RotateCcw, ScrollText, X } from 'lucide-react'
import { apiClient } from '../../api/client'
import { errorText } from '../../api/projectDocs'
import { usePermissions } from '../../hooks/usePermission'

interface Collaborator {
  id: string; name: string; organisation: string | null; email: string; status: 'ACTIVE' | 'EXPIRED' | 'DISABLED'
  expiresAt: string; disabledAt: string | null; disabledByName: string | null; disabledReason: string | null
  emailSentAt: string | null; lastLoginAt: string | null; createdAt: string
}
interface Trail { id: string; action: string; detail: string | null; ipAddress: string | null; actorName: string | null; occurredAt: string }

const dt = (iso?: string | null) => iso ? new Date(iso).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 9, fontSize: 14, boxSizing: 'border-box', outline: 'none', background: 'var(--hf-surface)', color: 'var(--hf-text)' }
const btn: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '6px 10px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-text-secondary)' }
const primary: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '8px 14px', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const STATUS: Record<string, { bg: string; color: string }> = {
  ACTIVE: { bg: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)' },
  EXPIRED: { bg: 'var(--hf-surface-sunken)', color: 'var(--hf-text-tertiary)' },
  DISABLED: { bg: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)' },
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }}>
      <div role="dialog" aria-label={title} style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 24, width: 500, maxWidth: '100%', maxHeight: '90vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>{title}</h3>
          <button onClick={onClose} aria-label="Close" style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)' }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
const Field = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <div style={{ marginBottom: 12 }}><label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>{label}</label>{children}</div>
)
const Err = ({ msg }: { msg: string }) => msg ? <div role="alert" style={{ marginTop: 8, padding: '8px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, color: 'var(--hf-danger-text)', fontSize: 13 }}>{msg}</div> : null

type Dlg = { kind: 'register' } | { kind: 'disable' | 'trail' | 'reissue' | 'enable'; c: Collaborator }

export function CollaboratorsPage() {
  const qc = useQueryClient()
  const canChange = usePermissions(['PM_APPROVE', 'PM_ADMIN'], 'any')
  const [dlg, setDlg] = useState<Dlg | null>(null)
  const [err, setErr] = useState('')
  const [form, setForm] = useState({ name: '', organisation: '', email: '', days: '365', reason: '', revokeLinks: true })
  const [note, setNote] = useState('')

  const { data = [], isLoading, isError } = useQuery<Collaborator[]>({
    queryKey: ['pm-collaborators'],
    queryFn: async () => (await apiClient.get('/api/v1/secure-access/collaborators')).data ?? [],
    staleTime: 15_000,
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ['pm-collaborators'] })
  const open = (d: Dlg) => { setDlg(d); setErr(''); setForm({ name: '', organisation: '', email: '', days: '365', reason: '', revokeLinks: true }) }
  const days = () => { const n = Number(form.days); return Number.isInteger(n) && n >= 1 && n <= 730 ? n : null }

  const run = async (fn: () => Promise<unknown>, ok: string) => {
    setErr('')
    try { await fn(); setDlg(null); setNote(ok); refresh() } catch (e) { setErr(await errorText(e, 'That did not work.')) }
  }

  const { data: trail = [], isFetching } = useQuery<Trail[]>({
    queryKey: ['pm-collaborator-trail', dlg && dlg.kind === 'trail' ? dlg.c.id : null],
    enabled: !!dlg && dlg.kind === 'trail',
    queryFn: async () => (await apiClient.get(`/api/v1/secure-access/collaborators/${(dlg as { c: Collaborator }).c.id}/trail`)).data ?? [],
  })

  return (
    <div style={{ fontFamily: "'Inter',sans-serif" }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginBottom: 14, flexWrap: 'wrap' }}>
        <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', maxWidth: 560 }}>
          Collaborators get one standing link to a workspace listing every transmittal issued to them. They still confirm their email with a code each visit. Disabling an account cuts access at once.
        </div>
        {canChange && <button style={primary} onClick={() => open({ kind: 'register' })}><UserPlus size={14} /> Register collaborator</button>}
      </div>
      {note && <div style={{ marginBottom: 10, fontSize: 13, color: 'var(--hf-success-text-strong)' }}>{note}</div>}

      {isLoading ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-text-faint)' }}>Loading…</div>
        : isError ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-danger-text)' }}>Could not load collaborators.</div>
        : data.length === 0 ? <div style={{ padding: 50, textAlign: 'center', color: 'var(--hf-text-faint)' }}>No collaborators registered yet.</div>
        : (
          <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead><tr style={{ textAlign: 'left', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)', fontSize: 12 }}>
                {['Collaborator', 'Status', 'Link expires', 'Last sign-in', ''].map(h => <th key={h} style={{ padding: '8px 12px', fontWeight: 600 }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {data.map(c => (
                  <tr key={c.id} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                    <td style={{ padding: '10px 12px' }}><strong>{c.name}</strong>{c.organisation ? ` · ${c.organisation}` : ''}<div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>{c.email}</div></td>
                    <td style={{ padding: '10px 12px' }}>
                      <span style={{ ...STATUS[c.status], fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 12 }}>{c.status}</span>
                      {c.disabledAt && <div style={{ fontSize: 11, color: 'var(--hf-text-faint)' }}>{c.disabledByName}: {c.disabledReason}</div>}
                    </td>
                    <td style={{ padding: '10px 12px' }}>{dt(c.expiresAt)}</td>
                    <td style={{ padding: '10px 12px' }}>{dt(c.lastLoginAt)}</td>
                    <td style={{ padding: '10px 12px', whiteSpace: 'nowrap', textAlign: 'right' }}>
                      <button style={{ ...btn, marginRight: 4 }} onClick={() => open({ kind: 'trail', c })}><ScrollText size={12} /> Trail</button>
                      {canChange && c.status !== 'DISABLED' && <button style={{ ...btn, marginRight: 4 }} onClick={() => open({ kind: 'reissue', c })}><RefreshCw size={12} /> New link</button>}
                      {canChange && c.status !== 'DISABLED' && <button style={{ ...btn, color: 'var(--hf-danger-text)' }} onClick={() => open({ kind: 'disable', c })}><Ban size={12} /> Disable</button>}
                      {canChange && c.status === 'DISABLED' && <button style={btn} onClick={() => open({ kind: 'enable', c })}><RotateCcw size={12} /> Enable</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

      {dlg?.kind === 'register' && (
        <Modal title="Register a collaborator" onClose={() => setDlg(null)}>
          <Field label="Name *"><input style={inp} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} autoFocus /></Field>
          <Field label="Organisation"><input style={inp} value={form.organisation} onChange={e => setForm({ ...form, organisation: e.target.value })} /></Field>
          <Field label="Email * (the workspace link and codes go here)"><input type="email" style={inp} value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></Field>
          <Field label="Link valid for (days, 1–730)"><input type="number" min={1} max={730} style={inp} value={form.days} onChange={e => setForm({ ...form, days: e.target.value })} /></Field>
          <Err msg={err} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 14 }}>
            <button style={btn} onClick={() => setDlg(null)}>Cancel</button>
            <button style={primary} onClick={() => {
              if (!form.name.trim() || !/^\S+@\S+\.\S+$/.test(form.email.trim())) return setErr('Name and a valid email are required')
              const d = days(); if (!d) return setErr('Link expiry must be 1 to 730 days')
              run(() => apiClient.post('/api/v1/secure-access/collaborators', { name: form.name.trim(), organisation: form.organisation.trim() || null, email: form.email.trim(), expiryDays: d }), 'Collaborator registered and emailed.')
            }}>Register & email link</button>
          </div>
        </Modal>
      )}

      {(dlg?.kind === 'reissue' || dlg?.kind === 'enable') && (() => { const d0 = dlg; const verb = d0.kind === 'enable' ? 'Enable' : 'Send new link to'; return (
        <Modal title={`${verb} ${d0.c.name}`} onClose={() => setDlg(null)}>
          <p style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginTop: 0 }}>
            {d0.kind === 'enable' ? 'The account is turned back on with a NEW link. Transmittal links that were revoked stay revoked; reissue them from the transmittal if needed.' : 'The current link and any open session stop working immediately.'}
          </p>
          <Field label="Link valid for (days, 1–730)"><input type="number" min={1} max={730} style={inp} value={form.days} onChange={e => setForm({ ...form, days: e.target.value })} /></Field>
          <Err msg={err} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 14 }}>
            <button style={btn} onClick={() => setDlg(null)}>Cancel</button>
            <button style={primary} onClick={() => {
              const d = days(); if (!d) return setErr('Link expiry must be 1 to 730 days')
              run(() => apiClient.post(`/api/v1/secure-access/collaborators/${d0.c.id}/${d0.kind}`, { expiryDays: d }), 'New link sent.')
            }}>{d0.kind === 'enable' ? 'Enable & email link' : 'Send new link'}</button>
          </div>
        </Modal>
      ) })()}

      {dlg?.kind === 'disable' && (() => { const c = dlg.c; return (
        <Modal title={`Disable ${c.name}`} onClose={() => setDlg(null)}>
          <p style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginTop: 0 }}>Access stops immediately, including any page they have open.</p>
          <Field label="Reason * (kept on the record)"><textarea style={{ ...inp, minHeight: 70 }} maxLength={500} value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} autoFocus /></Field>
          <label style={{ fontSize: 13 }}><input type="checkbox" checked={form.revokeLinks} onChange={e => setForm({ ...form, revokeLinks: e.target.checked })} /> Also revoke every transmittal link already issued to them</label>
          <Err msg={err} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 14 }}>
            <button style={btn} onClick={() => setDlg(null)}>Cancel</button>
            <button style={{ ...primary, background: 'var(--hf-danger)' }} onClick={() => {
              if (!form.reason.trim()) return setErr('A reason is required')
              run(() => apiClient.post(`/api/v1/secure-access/collaborators/${c.id}/disable`, { reason: form.reason.trim(), revokeLinks: form.revokeLinks }), 'Account disabled.')
            }}>Disable account</button>
          </div>
        </Modal>
      ) })()}

      {dlg?.kind === 'trail' && (
        <Modal title={`Trail — ${dlg.c.name}`} onClose={() => setDlg(null)}>
          {isFetching && <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>Loading…</div>}
          {!isFetching && trail.length === 0 && <div style={{ fontSize: 13, color: 'var(--hf-text-faint)' }}>Nothing recorded yet.</div>}
          {trail.map(l => (
            <div key={l.id} style={{ display: 'flex', gap: 10, padding: '6px 0', fontSize: 12, borderTop: '1px solid var(--hf-border-subtle)' }}>
              <span style={{ width: 125, color: 'var(--hf-text-faint)', flexShrink: 0 }}>{dt(l.occurredAt)}</span>
              <span style={{ width: 100, fontWeight: 700, flexShrink: 0 }}>{l.action}</span>
              <span style={{ flex: 1 }}>{l.actorName ? `${l.actorName}. ` : ''}{l.detail ?? ''}</span>
              <span style={{ color: 'var(--hf-text-faint)' }}>{l.ipAddress}</span>
            </div>
          ))}
        </Modal>
      )}
    </div>
  )
}
