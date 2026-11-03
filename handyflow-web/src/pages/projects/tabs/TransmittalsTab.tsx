// src/pages/projects/tabs/TransmittalsTab.tsx
// Transmittal register: issue documents to external recipients, each with their own expiring secure link;
// see who has opened what; reissue or revoke a link; read the access log.
import React, { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Send, ChevronDown, ChevronRight, RefreshCw, Ban, ScrollText, X, UserPlus, Mail } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { errorText } from '../../../api/projectDocs'

interface Recipient {
  id: string; name: string; organisation: string | null; email: string; canDownload: boolean; canAcknowledge: boolean
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED'; expiresAt: string; revokedAt: string | null; revokedByName: string | null; revokeReason: string | null
  emailSentAt: string | null; verifiedAt: string | null; lastAccessAt: string | null; acknowledgedAt: string | null; documentIds: string[]
}
interface Item { documentId: string; documentNumber: string; title: string; revision: string; statusAtSend: string; fileName: string | null }
interface Transmittal {
  id: string; transmittalNumber: string; subject: string; recipientName: string; recipientOrganisation: string | null; recipientEmail: string | null
  purpose: string; notes: string | null; sentByName: string; sentAt: string; acknowledgedAt: string | null; items: Item[]; recipients: Recipient[]
}
interface LogRow { id: string; recipientId: string | null; documentId: string | null; revision: string | null; action: string; detail: string | null; ipAddress: string | null; actorName: string | null; occurredAt: string }
interface LiveDoc { id: string; documentNumber: string; title: string; revision: string; status: string; fileName: string | null }

const PURPOSES = ['FOR_INFORMATION', 'FOR_REVIEW', 'FOR_APPROVAL', 'FOR_CONSTRUCTION', 'AS_BUILT']
const label = (s: string) => s.replace(/_/g, ' ').toLowerCase().replace(/^\w/, c => c.toUpperCase())
const dt = (iso?: string | null) => iso ? new Date(iso).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 9, fontSize: 14, boxSizing: 'border-box', outline: 'none', background: 'var(--hf-surface)', color: 'var(--hf-text)' }
const btn: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '6px 10px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-text-secondary)' }
const primary: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '8px 14px', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const STATUS: Record<string, { bg: string; color: string }> = {
  ACTIVE: { bg: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)' },
  EXPIRED: { bg: 'var(--hf-surface-sunken)', color: 'var(--hf-text-tertiary)' },
  REVOKED: { bg: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)' },
}

function Modal({ title, onClose, children, width = 560 }: { title: string; onClose: () => void; children: React.ReactNode; width?: number }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }}>
      <div role="dialog" aria-label={title} style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 24, width, maxWidth: '100%', maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,.2)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>{title}</h3>
          <button onClick={onClose} aria-label="Close" style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)' }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
const Field = ({ label: l, children }: { label: string; children: React.ReactNode }) => (
  <div style={{ marginBottom: 12 }}><label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>{l}</label>{children}</div>
)
const Err = ({ msg }: { msg: string }) => msg ? <div role="alert" style={{ marginTop: 10, padding: '8px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, color: 'var(--hf-danger-text)', fontSize: 13 }}>{msg}</div> : null

// ── Recipient form (used when issuing and when adding) ───────────────────────
interface RForm { name: string; organisation: string; email: string; canDownload: boolean; canAcknowledge: boolean; documentIds: string[] }
const blankR = (): RForm => ({ name: '', organisation: '', email: '', canDownload: true, canAcknowledge: true, documentIds: [] })

function RecipientFields({ r, set, items }: { r: RForm; set: (r: RForm) => void; items: { id: string; text: string }[] }) {
  return (
    <>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Name *"><input value={r.name} onChange={e => set({ ...r, name: e.target.value })} style={inp} /></Field>
        <Field label="Organisation"><input value={r.organisation} onChange={e => set({ ...r, organisation: e.target.value })} style={inp} /></Field>
      </div>
      <Field label="Email * (the link and the access code go here)"><input type="email" value={r.email} onChange={e => set({ ...r, email: e.target.value })} style={inp} /></Field>
      <div style={{ display: 'flex', gap: 18, marginBottom: 10, fontSize: 13 }}>
        <label><input type="checkbox" checked={r.canDownload} onChange={e => set({ ...r, canDownload: e.target.checked })} /> May download</label>
        <label><input type="checkbox" checked={r.canAcknowledge} onChange={e => set({ ...r, canAcknowledge: e.target.checked })} /> May acknowledge</label>
      </div>
      {items.length > 1 && (
        <Field label="Limit to these documents (none ticked = all)">
          <div style={{ maxHeight: 120, overflowY: 'auto', border: '1px solid var(--hf-border)', borderRadius: 8, padding: 8 }}>
            {items.map(i => (
              <label key={i.id} style={{ display: 'block', fontSize: 12, padding: '2px 0' }}>
                <input type="checkbox" checked={r.documentIds.includes(i.id)}
                  onChange={e => set({ ...r, documentIds: e.target.checked ? [...r.documentIds, i.id] : r.documentIds.filter(x => x !== i.id) })} /> {i.text}
              </label>
            ))}
          </div>
        </Field>
      )}
    </>
  )
}
const toBody = (r: RForm) => ({ name: r.name.trim(), organisation: r.organisation.trim() || null, email: r.email.trim(), canDownload: r.canDownload, canAcknowledge: r.canAcknowledge, documentIds: r.documentIds })
const rValid = (r: RForm) => r.name.trim() && /^\S+@\S+\.\S+$/.test(r.email.trim())

// ── Issue a transmittal ──────────────────────────────────────────────────────
function IssueModal({ projectId, onClose, onDone }: { projectId: string; onClose: () => void; onDone: () => void }) {
  const { data: docs = [] } = useQuery<LiveDoc[]>({
    queryKey: ['pm-docs-live', projectId],
    queryFn: async () => (await apiClient.get(`/api/v1/projects/${projectId}/documents?liveOnly=true`)).data ?? [],
  })
  const [f, setF] = useState({ subject: '', purpose: 'FOR_INFORMATION', notes: '', expiryDays: '14', attachFiles: false })
  const [main, setMain] = useState<RForm>(blankR())
  const [extra, setExtra] = useState<RForm[]>([])
  const [picked, setPicked] = useState<string[]>([])
  const [err, setErr] = useState('')
  const mut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/projects/${projectId}/transmittals`, {
      subject: f.subject.trim(), purpose: f.purpose, notes: f.notes.trim() || null, documentIds: picked,
      recipientName: main.name.trim(), recipientOrganisation: main.organisation.trim() || null, recipientEmail: main.email.trim(),
      expiryDays: Number(f.expiryDays) || null, attachFiles: f.attachFiles,
      additionalRecipients: extra.filter(rValid).map(toBody),
    }),
    onSuccess: onDone,
    onError: async (e) => setErr(await errorText(e, 'Could not record the transmittal.')),
  })
  const submit = () => {
    setErr('')
    if (!f.subject.trim()) return setErr('Subject is required')
    if (!rValid(main)) return setErr('The main recipient needs a name and a valid email')
    if (extra.some(r => (r.name || r.email) && !rValid(r))) return setErr('Complete or remove the additional recipient rows')
    if (picked.length === 0) return setErr('Select at least one document')
    const d = Number(f.expiryDays); if (!Number.isInteger(d) || d < 1 || d > 90) return setErr('Link expiry must be 1 to 90 days')
    mut.mutate()
  }
  const itemOpts = docs.filter(d => picked.includes(d.id)).map(d => ({ id: d.id, text: `${d.documentNumber} Rev ${d.revision} — ${d.title}` }))
  return (
    <Modal title="Issue documents" onClose={onClose} width={640}>
      <Field label="Subject *"><input value={f.subject} onChange={e => setF({ ...f, subject: e.target.value })} style={inp} autoFocus /></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <Field label="Purpose"><select value={f.purpose} onChange={e => setF({ ...f, purpose: e.target.value })} style={inp}>{PURPOSES.map(p => <option key={p} value={p}>{label(p)}</option>)}</select></Field>
        <Field label="Links valid for (days, 1–90)"><input type="number" min={1} max={90} value={f.expiryDays} onChange={e => setF({ ...f, expiryDays: e.target.value })} style={inp} /></Field>
      </div>
      <Field label={`Documents * (${picked.length} selected — only current, in-review or approved revisions can be issued)`}>
        <div style={{ maxHeight: 170, overflowY: 'auto', border: '1px solid var(--hf-border)', borderRadius: 8, padding: 8 }}>
          {docs.length === 0 && <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>No live documents in this project.</div>}
          {docs.map(d => (
            <label key={d.id} style={{ display: 'block', fontSize: 13, padding: '3px 0' }}>
              <input type="checkbox" checked={picked.includes(d.id)} onChange={e => setPicked(e.target.checked ? [...picked, d.id] : picked.filter(x => x !== d.id))} />{' '}
              <strong>{d.documentNumber}</strong> Rev {d.revision} — {d.title} <span style={{ color: 'var(--hf-text-faint)' }}>({d.status})</span>
            </label>
          ))}
        </div>
      </Field>
      <div style={{ fontSize: 12, fontWeight: 700, margin: '4px 0 8px' }}>Recipient</div>
      <RecipientFields r={main} set={setMain} items={[]} />
      {extra.map((r, i) => (
        <div key={i} style={{ borderTop: '1px solid var(--hf-border-subtle)', paddingTop: 10, marginTop: 6 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, fontWeight: 700, marginBottom: 6 }}>
            Additional recipient {i + 1}<button onClick={() => setExtra(extra.filter((_, j) => j !== i))} style={{ ...btn, padding: '2px 8px' }}>Remove</button>
          </div>
          <RecipientFields r={r} set={x => setExtra(extra.map((y, j) => j === i ? x : y))} items={itemOpts} />
        </div>
      ))}
      {extra.length < 20 && <button onClick={() => setExtra([...extra, blankR()])} style={{ ...btn, marginBottom: 12 }}><UserPlus size={13} /> Add another recipient</button>}
      <Field label="Notes"><textarea value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} style={{ ...inp, minHeight: 56 }} /></Field>
      <label style={{ fontSize: 12, color: 'var(--hf-text-muted)', display: 'block', marginBottom: 4 }}>
        <input type="checkbox" checked={f.attachFiles} onChange={e => setF({ ...f, attachFiles: e.target.checked })} /> Also attach the files to the email (only if an administrator enabled it; small, non-sensitive files only — the secure link is safer)
      </label>
      <Err msg={err} />
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 16 }}>
        <button onClick={onClose} style={btn}>Cancel</button>
        <button onClick={submit} disabled={mut.isPending} style={{ ...primary, opacity: mut.isPending ? .6 : 1 }}><Send size={13} /> {mut.isPending ? 'Sending…' : 'Issue & email links'}</button>
      </div>
    </Modal>
  )
}

// ── One transmittal, expanded ────────────────────────────────────────────────
function Detail({ projectId, t, onChanged }: { projectId: string; t: Transmittal; onChanged: () => void }) {
  const base = `/api/v1/projects/${projectId}/transmittals/${t.id}`
  const [showLog, setShowLog] = useState(false)
  const [adding, setAdding] = useState(false)
  const [revoking, setRevoking] = useState<Recipient | null>(null)
  const [note, setNote] = useState('')
  const [err, setErr] = useState('')
  const [rf, setRf] = useState<RForm>(blankR())
  const [expiry, setExpiry] = useState('14')
  const [attach, setAttach] = useState(false)
  const [reason, setReason] = useState('')
  const itemOpts = t.items.map(i => ({ id: i.documentId, text: `${i.documentNumber} Rev ${i.revision} — ${i.title}` }))
  // The recipient list stores item ids; the form stores document ids — they differ, so scope is shown by count only.

  const run = async (fn: () => Promise<unknown>, ok?: string) => {
    setErr(''); setNote('')
    try { await fn(); if (ok) setNote(ok); onChanged() } catch (e) { setErr(await errorText(e, 'That did not work.')) }
  }
  const { data: log = [], isFetching } = useQuery<LogRow[]>({
    queryKey: ['pm-tx-log', t.id], enabled: showLog,
    queryFn: async () => (await apiClient.get(`${base}/access-log`)).data ?? [],
  })
  const nameOf = (id: string | null) => id ? (t.recipients.find(r => r.id === id)?.name ?? 'recipient') : '—'

  return (
    <div style={{ padding: '4px 16px 16px', background: 'var(--hf-surface-muted)', borderTop: '1px solid var(--hf-border-subtle)' }}>
      <div style={{ fontSize: 12, fontWeight: 700, margin: '12px 0 6px' }}>Documents issued ({t.items.length})</div>
      {t.items.map(i => <div key={i.documentId} style={{ fontSize: 13 }}>{i.documentNumber} · Rev {i.revision} — {i.title} <span style={{ color: 'var(--hf-text-faint)' }}>({i.statusAtSend})</span></div>)}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '16px 0 6px', flexWrap: 'wrap', gap: 8 }}>
        <div style={{ fontSize: 12, fontWeight: 700 }}>Recipients & links</div>
        <div style={{ display: 'flex', gap: 6 }}>
          {t.recipients.length === 0 && t.recipientEmail && (
            <button style={btn} onClick={() => run(() => apiClient.post(`${base}/legacy-link`), 'Link sent.')} title="This transmittal was recorded before secure links existed"><Mail size={12} /> Send link to {t.recipientName}</button>
          )}
          <button style={btn} onClick={() => { setAdding(true); setRf(blankR()); setErr('') }}><UserPlus size={12} /> Add recipient</button>
          <button style={btn} onClick={() => setShowLog(s => !s)}><ScrollText size={12} /> {showLog ? 'Hide' : 'Show'} access log</button>
        </div>
      </div>
      {t.recipients.length === 0
        ? <div style={{ fontSize: 13, color: 'var(--hf-text-faint)' }}>No secure links yet. {t.recipientEmail ? 'This transmittal predates secure links — use "Send link" above.' : ''}</div>
        : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead><tr style={{ textAlign: 'left', color: 'var(--hf-text-faint)' }}>{['Recipient', 'Status', 'Expires', 'Last opened', 'Acknowledged', 'Scope', ''].map(h => <th key={h} style={{ padding: '4px 8px', fontWeight: 600 }}>{h}</th>)}</tr></thead>
              <tbody>
                {t.recipients.map(r => (
                  <tr key={r.id} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                    <td style={{ padding: '6px 8px' }}><strong>{r.name}</strong>{r.organisation ? ` · ${r.organisation}` : ''}<div style={{ color: 'var(--hf-text-faint)' }}>{r.email}{!r.canDownload && ' · view only'}</div></td>
                    <td style={{ padding: '6px 8px' }}>
                      <span style={{ ...STATUS[r.status], fontSize: 10, fontWeight: 700, padding: '2px 7px', borderRadius: 12 }}>{r.status}</span>
                      {r.revokedAt && <div style={{ color: 'var(--hf-text-faint)' }} title={r.revokeReason ?? ''}>{r.revokedByName}: {r.revokeReason}</div>}
                    </td>
                    <td style={{ padding: '6px 8px' }}>{dt(r.expiresAt)}</td>
                    <td style={{ padding: '6px 8px' }}>{dt(r.lastAccessAt)}{!r.verifiedAt && <div style={{ color: 'var(--hf-text-faint)' }}>never opened</div>}</td>
                    <td style={{ padding: '6px 8px' }}>{dt(r.acknowledgedAt)}</td>
                    <td style={{ padding: '6px 8px' }}>{r.documentIds.length ? `${r.documentIds.length} of ${t.items.length}` : 'All'}</td>
                    <td style={{ padding: '6px 8px', whiteSpace: 'nowrap' }}>
                      {r.status !== 'REVOKED' && <button style={{ ...btn, marginRight: 4 }} title="Send a fresh link; the old one stops working" onClick={() => { if (confirm(`Send ${r.name} a new link? Their current link stops working.`)) run(() => apiClient.post(`${base}/recipients/${r.id}/reissue`, {}), 'New link sent.') }}><RefreshCw size={12} /> Reissue</button>}
                      {r.status === 'REVOKED' && <button style={{ ...btn, marginRight: 4 }} onClick={() => run(() => apiClient.post(`${base}/recipients/${r.id}/reissue`, {}), 'New link sent.')}><RefreshCw size={12} /> Reissue</button>}
                      {r.status === 'ACTIVE' && <button style={{ ...btn, color: 'var(--hf-danger-text)' }} onClick={() => { setRevoking(r); setReason(''); setErr('') }}><Ban size={12} /> Revoke</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      {note && <div style={{ marginTop: 8, fontSize: 13, color: 'var(--hf-success-text-strong)' }}>{note}</div>}
      {!adding && !revoking && <Err msg={err} />}

      {showLog && (
        <div style={{ marginTop: 14 }}>
          <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 6 }}>Access log {isFetching && '…'}</div>
          <div style={{ maxHeight: 240, overflowY: 'auto', border: '1px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)' }}>
            {log.length === 0 && <div style={{ padding: 12, fontSize: 12, color: 'var(--hf-text-faint)' }}>Nothing recorded yet.</div>}
            {log.map(l => (
              <div key={l.id} style={{ display: 'flex', gap: 10, padding: '6px 10px', fontSize: 12, borderTop: '1px solid var(--hf-border-subtle)' }}>
                <span style={{ width: 130, color: 'var(--hf-text-faint)', flexShrink: 0 }}>{dt(l.occurredAt)}</span>
                <span style={{ width: 120, fontWeight: 700, flexShrink: 0 }}>{l.action}</span>
                <span style={{ flex: 1 }}>{nameOf(l.recipientId)}{l.actorName ? ` (by ${l.actorName})` : ''}{l.detail ? ` — ${l.detail}` : ''}{l.revision ? ` · Rev ${l.revision}` : ''}</span>
                <span style={{ color: 'var(--hf-text-faint)' }}>{l.ipAddress}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {adding && (
        <Modal title="Add a recipient" onClose={() => setAdding(false)}>
          <RecipientFields r={rf} set={setRf} items={itemOpts} />
          <Field label="Link valid for (days, 1–90)"><input type="number" min={1} max={90} value={expiry} onChange={e => setExpiry(e.target.value)} style={inp} /></Field>
          <label style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}><input type="checkbox" checked={attach} onChange={e => setAttach(e.target.checked)} /> Also attach the files (only if enabled by an administrator)</label>
          <Err msg={err} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 16 }}>
            <button style={btn} onClick={() => setAdding(false)}>Cancel</button>
            <button style={primary} onClick={() => {
              if (!rValid(rf)) return setErr('Name and a valid email are required')
              const d = Number(expiry); if (!Number.isInteger(d) || d < 1 || d > 90) return setErr('Link expiry must be 1 to 90 days')
              run(async () => { await apiClient.post(`${base}/recipients?expiryDays=${d}&attachFiles=${attach}`, toBody(rf)); setAdding(false) }, 'Recipient added and emailed.')
            }}>Add & email link</button>
          </div>
        </Modal>
      )}

      {revoking && (
        <Modal title={`Revoke access — ${revoking.name}`} onClose={() => setRevoking(null)} width={440}>
          <p style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginTop: 0 }}>Takes effect immediately, including any page they have open. Only the sender or a document controller can do this.</p>
          <Field label="Reason * (kept on the record)"><textarea value={reason} onChange={e => setReason(e.target.value)} maxLength={500} style={{ ...inp, minHeight: 70 }} autoFocus /></Field>
          <Err msg={err} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 14 }}>
            <button style={btn} onClick={() => setRevoking(null)}>Cancel</button>
            <button style={{ ...primary, background: 'var(--hf-danger)' }} onClick={() => {
              if (!reason.trim()) return setErr('A reason is required')
              run(async () => { await apiClient.post(`${base}/recipients/${revoking.id}/revoke`, { reason: reason.trim() }); setRevoking(null) }, 'Access revoked.')
            }}>Revoke access</button>
          </div>
        </Modal>
      )}
    </div>
  )
}

export function TransmittalsTab({ projectId }: { projectId: string }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState<string | null>(null)
  const [issuing, setIssuing] = useState(false)
  const { data = [], isLoading, isError } = useQuery<Transmittal[]>({
    queryKey: ['pm-transmittals', projectId],
    queryFn: async () => (await apiClient.get(`/api/v1/projects/${projectId}/transmittals`)).data ?? [],
    staleTime: 15_000,
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ['pm-transmittals', projectId] })

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
        <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>Each recipient gets their own expiring link and must confirm their email with a code. Nothing is attached unless you choose to.</div>
        <button style={primary} onClick={() => setIssuing(true)}><Plus size={14} /> Issue documents</button>
      </div>
      {isLoading ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-text-faint)' }}>Loading…</div>
        : isError ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-danger-text)' }}>Could not load transmittals.</div>
        : data.length === 0 ? <div style={{ padding: 50, textAlign: 'center', color: 'var(--hf-text-faint)' }}><Send size={32} style={{ opacity: .3 }} /><div style={{ fontWeight: 600, marginTop: 8 }}>Nothing issued yet</div></div>
        : (
          <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflow: 'hidden' }}>
            {data.map((t, i) => {
              const isOpen = open === t.id
              const active = t.recipients.filter(r => r.status === 'ACTIVE').length
              return (
                <div key={t.id} style={{ borderTop: i ? '1px solid var(--hf-border)' : 'none' }}>
                  <button onClick={() => setOpen(isOpen ? null : t.id)} style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 10, padding: '12px 14px', background: 'var(--hf-surface)', border: 'none', cursor: 'pointer', textAlign: 'left', color: 'var(--hf-text)' }}>
                    {isOpen ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 14, fontWeight: 600 }}>{t.transmittalNumber} — {t.subject}</div>
                      <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>{label(t.purpose)} · {t.items.length} document{t.items.length === 1 ? '' : 's'} · {t.sentByName} · {dt(t.sentAt)}</div>
                    </div>
                    <span style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>{t.recipients.length} recipient{t.recipients.length === 1 ? '' : 's'}{t.recipients.length ? ` · ${active} active` : ''}</span>
                  </button>
                  {isOpen && <Detail projectId={projectId} t={t} onChanged={refresh} />}
                </div>
              )
            })}
          </div>
        )}
      {issuing && <IssueModal projectId={projectId} onClose={() => setIssuing(false)} onDone={() => { setIssuing(false); refresh() }} />}
    </div>
  )
}
