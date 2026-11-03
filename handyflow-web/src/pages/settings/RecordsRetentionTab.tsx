// src/pages/settings/RecordsRetentionTab.tsx
// Records retention, tenant-wide: what is due for disposal, legal holds, disposal requests (proposed by one person, approved by a
// different person) and retention policies. RECORDS_READ to look, RECORDS_MANAGE to hold / set policies / propose, RECORDS_APPROVE to
// approve or reject. The server enforces every rule (including "not your own proposal"); the buttons are only hidden to avoid a 403.
import React, { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ShieldAlert, ShieldCheck, Clock, ListChecks, Scale, Trash2, X, Check, Ban, Undo2, Plus, Pencil, AlertTriangle } from 'lucide-react'
import { apiClient } from '../../api/client'
import { errorText } from '../../api/projectDocs'
import { usePermission } from '../../hooks/usePermission'
import { useAuthStore } from '../../store/auth.store'

const BASE = '/api/v1/records/retention'

export interface RetentionItem {
  id: string; moduleCode: string; sourceType: string; sourceId: string; category: string; jurisdiction: string
  reference: string | null; title: string; contextLabel: string | null; clockStart: string | null; retainUntil: string | null
  policyYears: number | null; policySource: 'POLICY' | 'MODULE_POLICY' | 'DEFAULT'; policyConfirmed: boolean; personalInfo: boolean; due: boolean
  legalHold: boolean; holdReason: string | null; holdByName: string | null; holdAt: string | null
  status: 'ACTIVE' | 'PROPOSED' | 'DISPOSED'; batchId: string | null; disposedAt: string | null; disposalDetail: string | null
}
interface Policy {
  id: string | null; moduleCode: string; category: string; jurisdiction: string; retainYears: number; personalInfo: boolean
  confirmed: boolean; notes: string | null; updatedByName: string | null; updatedAt: string | null; recordsKept: number; isDefault: boolean
}
interface BatchItem { itemId: string; outcome: 'PENDING' | 'DISPOSED' | 'SKIPPED' | 'FAILED'; detail: string | null; doneAt: string | null; item: RetentionItem | null }
interface Batch {
  id: string; status: 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED'; reason: string; proposedBy: string; proposedByName: string | null
  proposedAt: string; decidedBy: string | null; decidedByName: string | null; decidedAt: string | null; decisionNote: string | null
  cancelledAt: string | null; completedAt: string | null; itemCount: number; disposedCount: number; skippedCount: number; failedCount: number
  items: BatchItem[] | null
}
interface Summary {
  tracked: number; clockNotStarted: number; due: number; upcoming: number; held: number; proposed: number; disposed: number
  openRequests: number; categoriesWithoutConfirmedPolicy: number; upcomingDays: number
}

type View = 'due' | 'upcoming' | 'held' | 'requests' | 'policies' | 'disposed' | 'all'

const MODULES: Record<string, string> = { PROJECTS: 'Projects', DOCUMENTS: 'Document Service', CONTRACTING: 'Contracts', FM: 'Facilities Management' }
const moduleName = (m: string) => MODULES[m] ?? m
const d = (iso?: string | null) => iso ? new Date(iso.length === 10 ? iso + 'T00:00:00' : iso).toLocaleDateString('en-ZA', { dateStyle: 'medium' }) : '—'
const dt = (iso?: string | null) => iso ? new Date(iso).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 9, fontSize: 14, boxSizing: 'border-box', outline: 'none', background: 'var(--hf-surface)', color: 'var(--hf-text)' }
const btn: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '6px 10px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-text-secondary)' }
const primary: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 5, padding: '8px 14px', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const danger: React.CSSProperties = { ...primary, background: 'var(--hf-danger-text)' }
const th: React.CSSProperties = { padding: '8px 12px', fontWeight: 600 }
const td: React.CSSProperties = { padding: '10px 12px', verticalAlign: 'top' }
const muted: React.CSSProperties = { fontSize: 12, color: 'var(--hf-text-faint)' }

const BADGE: Record<string, { bg: string; color: string }> = {
  ok: { bg: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)' },
  warn: { bg: 'var(--hf-warning-soft, var(--hf-surface-sunken))', color: 'var(--hf-warning-text, var(--hf-text-secondary))' },
  bad: { bg: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)' },
  plain: { bg: 'var(--hf-surface-sunken)', color: 'var(--hf-text-tertiary)' },
}
const Badge = ({ tone, children }: { tone: keyof typeof BADGE; children: React.ReactNode }) =>
  <span style={{ ...BADGE[tone], fontSize: 10, fontWeight: 700, padding: '2px 8px', borderRadius: 12, whiteSpace: 'nowrap' }}>{children}</span>

const BATCH_TONE: Record<Batch['status'], keyof typeof BADGE> = { PROPOSED: 'warn', APPROVED: 'warn', REJECTED: 'plain', CANCELLED: 'plain', COMPLETED: 'ok' }
const OUTCOME_TONE: Record<BatchItem['outcome'], keyof typeof BADGE> = { PENDING: 'warn', DISPOSED: 'ok', SKIPPED: 'plain', FAILED: 'bad' }

function Modal({ title, onClose, children, wide }: { title: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 16 }}>
      <div role="dialog" aria-label={title} style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 24, width: wide ? 820 : 500, maxWidth: '100%', maxHeight: '90vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
          <h3 style={{ margin: 0, fontSize: 16 }}>{title}</h3>
          <button onClick={onClose} aria-label="Close" style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)' }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
const Field = ({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) => (
  <div style={{ marginBottom: 12 }}>
    <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>{label}</label>
    {children}
    {hint && <div style={{ ...muted, marginTop: 4 }}>{hint}</div>}
  </div>
)
const Err = ({ msg }: { msg: string }) => msg ? <div role="alert" style={{ marginTop: 8, padding: '8px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, color: 'var(--hf-danger-text)', fontSize: 13 }}>{msg}</div> : null
const Empty = ({ children }: { children: React.ReactNode }) => <div style={{ padding: 44, textAlign: 'center', color: 'var(--hf-text-faint)', fontSize: 14 }}>{children}</div>

/** Where the period comes from, said plainly. */
function PeriodNote({ i }: { i: RetentionItem }) {
  if (i.retainUntil == null) return <span style={muted}>Starts when the record is closed or archived</span>
  const src = i.policySource === 'DEFAULT' ? 'default' : i.policySource === 'MODULE_POLICY' ? 'module policy' : 'policy'
  return (
    <span style={muted}>
      {i.policyYears} years ({src}){!i.policyConfirmed && <> · <span style={{ color: 'var(--hf-danger-text)' }}>period not confirmed</span></>}
      {i.personalInfo && <> · personal information</>}
    </span>
  )
}

type Dlg =
  | { kind: 'hold'; item: RetentionItem }
  | { kind: 'batch'; id: string }
  | { kind: 'policy'; policy: Policy | null }

export default function RecordsRetentionTab() {
  const qc = useQueryClient()
  const canManage = usePermission('RECORDS_MANAGE')
  const canApprove = usePermission('RECORDS_APPROVE')
  const me = useAuthStore(s => s.user?.userId ?? null)
  const [view, setView] = useState<View>('due')
  const [moduleFilter, setModuleFilter] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [reason, setReason] = useState('')
  const [dlg, setDlg] = useState<Dlg | null>(null)
  const [note, setNote] = useState('')
  const [err, setErr] = useState('')
  const [busy, setBusy] = useState(false)

  const summary = useQuery<Summary>({ queryKey: ['records-summary'], queryFn: async () => (await apiClient.get(`${BASE}/summary`)).data, staleTime: 15_000 })
  const listView = view === 'requests' || view === 'policies' ? null : view
  const items = useQuery<RetentionItem[]>({
    queryKey: ['records-items', listView, moduleFilter],
    enabled: listView != null,
    queryFn: async () => (await apiClient.get(`${BASE}/items`, { params: { view: (listView ?? 'due').toUpperCase(), module: moduleFilter || undefined, limit: 500 } })).data ?? [],
  })
  const batches = useQuery<Batch[]>({ queryKey: ['records-batches'], enabled: view === 'requests', queryFn: async () => (await apiClient.get(`${BASE}/disposals`)).data ?? [] })
  const policies = useQuery<Policy[]>({ queryKey: ['records-policies'], enabled: view === 'policies', queryFn: async () => (await apiClient.get(`${BASE}/policies`)).data ?? [] })

  const refresh = () => {
    for (const k of ['records-summary', 'records-items', 'records-batches', 'records-policies', 'records-batch']) qc.invalidateQueries({ queryKey: [k] })
  }
  const run = async (fn: () => Promise<unknown>, ok: string, close = true) => {
    setErr(''); setBusy(true)
    try { await fn(); if (close) setDlg(null); setNote(ok); refresh() }
    catch (e) { setErr(await errorText(e, 'That did not work.')) }
    finally { setBusy(false) }
  }
  const switchView = (v: View) => { setView(v); setSelected(new Set()); setNote(''); setErr('') }

  const s = summary.data
  const tabs: { id: View; label: string; icon: React.ElementType; count?: number }[] = [
    { id: 'due', label: 'Due', icon: Clock, count: s?.due },
    { id: 'upcoming', label: `Coming due (${s?.upcomingDays ?? 90} days)`, icon: Archive, count: s?.upcoming },
    { id: 'held', label: 'Legal holds', icon: ShieldAlert, count: s?.held },
    { id: 'requests', label: 'Disposal requests', icon: ListChecks, count: s?.openRequests },
    { id: 'policies', label: 'Policies', icon: Scale },
    { id: 'disposed', label: 'Disposed', icon: Trash2, count: s?.disposed },
    { id: 'all', label: 'All records', icon: ShieldCheck, count: s ? s.tracked - s.disposed : undefined },
  ]

  const rows = items.data ?? []
  const selectable = view === 'due' && canManage
  const allSelected = selectable && rows.length > 0 && rows.every(r => selected.has(r.id))
  const toggle = (id: string) => setSelected(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n })

  const propose = () => run(async () => {
    await apiClient.post(`${BASE}/disposals`, { itemIds: [...selected], reason })
    setSelected(new Set()); setReason('')
  }, 'Disposal proposed. A different person must now approve it under Disposal requests; nothing has been deleted yet.')

  return (
    <div style={{ fontFamily: "'Inter',sans-serif" }}>
      <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', maxWidth: 760, marginBottom: 14 }}>
        Records are kept for their retention period from the day they are closed or archived. Nothing is deleted automatically: a record that is due is
        proposed for disposal by one person and approved by someone else. Only then are its files deleted. The record itself, its reference and its
        file hash stay as proof of what was kept and when it was disposed of. A legal hold stops a record from being disposed of.
      </div>

      {s && s.categoriesWithoutConfirmedPolicy > 0 && (
        <div role="note" style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginBottom: 14, padding: '10px 14px', border: '1px solid var(--hf-border)', borderRadius: 10, background: 'var(--hf-surface-muted)', fontSize: 13 }}>
          <AlertTriangle size={16} style={{ color: 'var(--hf-danger-text)', flexShrink: 0, marginTop: 1 }} />
          <span>
            {s.categoriesWithoutConfirmedPolicy} {s.categoriesWithoutConfirmedPolicy === 1 ? 'category of records uses' : 'categories of records use'} a retention
            period that has not been confirmed (most use the 7-year default). Ask your compliance owner to set and confirm the periods under Policies
            {canManage ? '' : ' (someone with records management rights can record them)'}.
          </span>
        </div>
      )}

      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--hf-border)', marginBottom: 16, overflowX: 'auto' }}>
        {tabs.map(({ id, label, icon: Icon, count }) => (
          <button key={id} onClick={() => switchView(id)}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '9px 14px', background: 'none', border: 'none', whiteSpace: 'nowrap',
              borderBottom: view === id ? '2px solid var(--hf-accent)' : '2px solid transparent', marginBottom: -1, cursor: 'pointer', fontSize: 13,
              color: view === id ? 'var(--hf-accent-text)' : 'var(--hf-text-muted)', fontWeight: view === id ? 700 : 400 }}>
            <Icon size={14} />{label}{count != null && count > 0 && <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)' }}>{count}</span>}
          </button>
        ))}
      </div>

      {note && <div style={{ marginBottom: 10, fontSize: 13, color: 'var(--hf-success-text-strong)' }}>{note}</div>}
      {!dlg && <Err msg={err} />}

      {listView && (
        <>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 10, flexWrap: 'wrap' }}>
            <select aria-label="Module" value={moduleFilter} onChange={e => { setModuleFilter(e.target.value); setSelected(new Set()) }} style={{ ...inp, width: 'auto', padding: '6px 10px', fontSize: 13 }}>
              <option value="">All modules</option>
              {Object.entries(MODULES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
            {view === 'due' && <span style={muted}>Past their retention date, not on legal hold and not in a request.</span>}
          </div>

          {items.isLoading ? <Empty>Loading…</Empty>
            : items.isError ? <Empty>Could not load the records.</Empty>
            : rows.length === 0 ? <Empty>{view === 'due' ? 'Nothing is due for disposal.' : view === 'held' ? 'No record is under legal hold.' : view === 'disposed' ? 'Nothing has been disposed of yet.' : 'No records here.'}</Empty>
            : (
              <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead><tr style={{ textAlign: 'left', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)', fontSize: 12 }}>
                    {selectable && <th style={{ ...th, width: 32 }}><input type="checkbox" aria-label="Select all" checked={allSelected}
                      onChange={() => setSelected(allSelected ? new Set() : new Set(rows.map(r => r.id)))} /></th>}
                    <th style={th}>Record</th><th style={th}>Category</th>
                    <th style={th}>{view === 'disposed' ? 'Disposed' : 'Kept until'}</th>
                    <th style={th}>{view === 'disposed' ? 'What was removed' : 'Hold'}</th>
                    {view !== 'disposed' && canManage && <th style={th} />}
                  </tr></thead>
                  <tbody>
                    {rows.map(i => (
                      <tr key={i.id} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                        {selectable && <td style={td}><input type="checkbox" aria-label={`Select ${i.reference ?? i.title}`} checked={selected.has(i.id)} onChange={() => toggle(i.id)} /></td>}
                        <td style={td}>
                          <strong>{i.reference || i.title}</strong>{i.reference && <span> · {i.title}</span>}
                          <div style={muted}>{moduleName(i.moduleCode)}{i.contextLabel ? ` · ${i.contextLabel}` : ''}{i.status === 'PROPOSED' ? ' · in a disposal request' : ''}</div>
                        </td>
                        <td style={td}>{i.category}<div style={muted}>{i.jurisdiction}</div></td>
                        <td style={td}>
                          {view === 'disposed' ? dt(i.disposedAt) : <>{d(i.retainUntil)} {i.due && <Badge tone="bad">due</Badge>}<div><PeriodNote i={i} /></div></>}
                        </td>
                        <td style={td}>
                          {view === 'disposed' ? <span style={muted}>{i.disposalDetail}</span>
                            : i.legalHold ? <><Badge tone="bad">Legal hold</Badge><div style={muted}>{i.holdByName ?? '—'}: {i.holdReason}</div></>
                            : <span style={muted}>—</span>}
                        </td>
                        {view !== 'disposed' && canManage && (
                          <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>
                            {i.status !== 'DISPOSED' && <button style={btn} onClick={() => { setErr(''); setReason(''); setDlg({ kind: 'hold', item: i }) }}>
                              {i.legalHold ? <><ShieldCheck size={12} /> Lift hold</> : <><ShieldAlert size={12} /> Hold</>}
                            </button>}
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

          {selectable && selected.size > 0 && (
            <div style={{ position: 'sticky', bottom: 0, marginTop: 12, padding: 14, border: '1px solid var(--hf-border)', borderRadius: 10, background: 'var(--hf-surface)', boxShadow: '0 -4px 16px rgba(15,23,42,.06)' }}>
              <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>Propose {selected.size} record{selected.size === 1 ? '' : 's'} for disposal</div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <input aria-label="Reason for the disposal" placeholder="Why these records can be disposed of (e.g. retention period ended, no claims open)" value={reason}
                  onChange={e => setReason(e.target.value)} style={{ ...inp, flex: '1 1 320px' }} />
                <button style={primary} disabled={busy || reason.trim().length < 3} onClick={propose}><ListChecks size={14} /> Propose disposal</button>
              </div>
              <div style={{ ...muted, marginTop: 6 }}>Nothing is deleted now. Someone other than you must approve the request.</div>
            </div>
          )}
        </>
      )}

      {view === 'requests' && (
        batches.isLoading ? <Empty>Loading…</Empty>
          : batches.isError ? <Empty>Could not load the disposal requests.</Empty>
          : (batches.data ?? []).length === 0 ? <Empty>No disposal has been proposed yet. Records that are due are proposed from the Due list.</Empty>
          : (
            <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                <thead><tr style={{ textAlign: 'left', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)', fontSize: 12 }}>
                  {['Request', 'Status', 'Proposed', 'Decided', 'Records', ''].map(h => <th key={h} style={th}>{h}</th>)}
                </tr></thead>
                <tbody>
                  {(batches.data ?? []).map(b => (
                    <tr key={b.id} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                      <td style={td}>{b.reason}</td>
                      <td style={td}><Badge tone={BATCH_TONE[b.status]}>{b.status === 'PROPOSED' ? 'Waiting for approval' : b.status === 'APPROVED' ? 'Approved, not finished' : b.status.charAt(0) + b.status.slice(1).toLowerCase()}</Badge></td>
                      <td style={td}>{b.proposedByName ?? '—'}<div style={muted}>{dt(b.proposedAt)}</div></td>
                      <td style={td}>{b.decidedByName ?? '—'}<div style={muted}>{dt(b.decidedAt ?? b.cancelledAt)}</div></td>
                      <td style={td}>{b.itemCount}{b.status === 'COMPLETED' && <div style={muted}>{b.disposedCount} disposed · {b.skippedCount} skipped · {b.failedCount} not disposed</div>}</td>
                      <td style={{ ...td, textAlign: 'right' }}><button style={btn} onClick={() => { setErr(''); setReason(''); setDlg({ kind: 'batch', id: b.id }) }}>Open</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
      )}

      {view === 'policies' && (
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginBottom: 10, flexWrap: 'wrap' }}>
            <span style={{ ...muted, maxWidth: 620 }}>
              A category with no policy keeps its records for 7 years. A policy for category * applies to every category of that module that has no
              policy of its own. Changing a period re-dates every record it covers. Mark a policy confirmed once your compliance owner has set it.
            </span>
            {canManage && <button style={primary} onClick={() => { setErr(''); setDlg({ kind: 'policy', policy: null }) }}><Plus size={14} /> Add policy</button>}
          </div>
          {policies.isLoading ? <Empty>Loading…</Empty>
            : policies.isError ? <Empty>Could not load the policies.</Empty>
            : (policies.data ?? []).length === 0 ? <Empty>No records are tracked yet, so there is nothing to set a policy for.</Empty>
            : (
              <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead><tr style={{ textAlign: 'left', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)', fontSize: 12 }}>
                    {['Module', 'Category', 'Jurisdiction', 'Keep for', 'Status', 'Records', ''].map(h => <th key={h} style={th}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {(policies.data ?? []).map(p => (
                      <tr key={`${p.moduleCode}|${p.category}|${p.jurisdiction}`} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                        <td style={td}>{moduleName(p.moduleCode)}</td>
                        <td style={td}>{p.category === '*' ? 'Every other category' : p.category}{p.personalInfo && <div style={muted}>Personal information</div>}</td>
                        <td style={td}>{p.jurisdiction}</td>
                        <td style={td}>{p.retainYears} years{p.notes && <div style={muted}>{p.notes}</div>}</td>
                        <td style={td}>{p.isDefault ? <Badge tone="bad">Default, not set</Badge> : p.confirmed ? <Badge tone="ok">Confirmed</Badge> : <Badge tone="warn">Not confirmed</Badge>}
                          {p.updatedByName && <div style={muted}>{p.updatedByName} · {d(p.updatedAt)}</div>}</td>
                        <td style={td}>{p.recordsKept}</td>
                        <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>
                          {canManage && <button style={{ ...btn, marginRight: 4 }} onClick={() => { setErr(''); setDlg({ kind: 'policy', policy: p }) }}><Pencil size={12} /> {p.isDefault ? 'Set' : 'Edit'}</button>}
                          {canManage && !p.isDefault && p.id && <button style={{ ...btn, color: 'var(--hf-danger-text)' }}
                            onClick={() => { if (window.confirm('Remove this policy? Its records go back to the module policy or the 7-year default, and are re-dated.')) run(() => apiClient.delete(`${BASE}/policies/${p.id}`), 'Policy removed.', false) }}>
                            <Trash2 size={12} /> Remove</button>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
        </>
      )}

      {dlg?.kind === 'hold' && (
        <Modal title={dlg.item.legalHold ? 'Lift the legal hold' : 'Place a legal hold'} onClose={() => setDlg(null)}>
          <div style={{ fontSize: 13, marginBottom: 12 }}>
            <strong>{dlg.item.reference || dlg.item.title}</strong> · {moduleName(dlg.item.moduleCode)}
            <div style={muted}>{dlg.item.legalHold ? 'Once lifted, the record can be disposed of again when it is due.' : 'A record under legal hold is never disposed of, even if it is already in a disposal request.'}</div>
          </div>
          <Field label="Reason (kept in the audit trail)">
            <textarea aria-label="Reason" rows={3} value={reason} onChange={e => setReason(e.target.value)} style={{ ...inp, resize: 'vertical' }}
              placeholder={dlg.item.legalHold ? 'e.g. Claim settled on 3 November' : 'e.g. Letter of demand from the client, 1 November'} />
          </Field>
          <Err msg={err} />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
            <button style={btn} onClick={() => setDlg(null)}>Cancel</button>
            <button style={primary} disabled={busy || reason.trim().length < 3}
              onClick={() => run(() => apiClient.post(`${BASE}/items/${dlg.item.id}/hold`, { hold: !dlg.item.legalHold, reason }),
                dlg.item.legalHold ? 'Legal hold lifted.' : 'Legal hold placed.')}>
              {dlg.item.legalHold ? 'Lift hold' : 'Place hold'}
            </button>
          </div>
        </Modal>
      )}

      {dlg?.kind === 'batch' && (
        <BatchDialog id={dlg.id} me={me} canApprove={canApprove} canManage={canManage} busy={busy} err={err}
          onClose={() => setDlg(null)}
          onApprove={(id, n) => run(() => apiClient.post(`${BASE}/disposals/${id}/approve`, { note: n }), 'Disposal finished. See each record for its outcome.', false)}
          onReject={(id, n) => run(() => apiClient.post(`${BASE}/disposals/${id}/reject`, { note: n }), 'Disposal rejected; the records stay.', false)}
          onCancel={id => run(() => apiClient.post(`${BASE}/disposals/${id}/cancel`), 'Disposal request withdrawn.', false)} />
      )}

      {dlg?.kind === 'policy' && (
        <PolicyDialog policy={dlg.policy} busy={busy} err={err} onClose={() => setDlg(null)}
          onSave={body => run(() => apiClient.put(`${BASE}/policies`, body), 'Policy saved; its records have been re-dated.')} />
      )}
    </div>
  )
}

function BatchDialog({ id, me, canApprove, canManage, busy, err, onClose, onApprove, onReject, onCancel }: {
  id: string; me: string | null; canApprove: boolean; canManage: boolean; busy: boolean; err: string
  onClose: () => void; onApprove: (id: string, note: string) => void; onReject: (id: string, note: string) => void; onCancel: (id: string) => void
}) {
  const [decisionNote, setDecisionNote] = useState('')
  const [confirming, setConfirming] = useState(false)
  const { data: b, isLoading } = useQuery<Batch>({ queryKey: ['records-batch', id], queryFn: async () => (await apiClient.get(`${BASE}/disposals/${id}`)).data })
  const mine = !!b && !!me && b.proposedBy === me
  const open = !!b && (b.status === 'PROPOSED' || b.status === 'APPROVED')
  const pending = useMemo(() => (b?.items ?? []).filter(x => x.outcome === 'PENDING').length, [b])

  return (
    <Modal title="Disposal request" onClose={onClose} wide>
      {isLoading || !b ? <Empty>Loading…</Empty> : (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 10, fontSize: 13, marginBottom: 14 }}>
            <div><div style={muted}>Reason</div>{b.reason}</div>
            <div><div style={muted}>Proposed by</div>{b.proposedByName ?? '—'} · {dt(b.proposedAt)}</div>
            <div><div style={muted}>Status</div><Badge tone={BATCH_TONE[b.status]}>{b.status}</Badge></div>
            {b.decidedAt && <div><div style={muted}>Decided by</div>{b.decidedByName} · {dt(b.decidedAt)}{b.decisionNote && <div style={muted}>{b.decisionNote}</div>}</div>}
          </div>

          <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflowX: 'auto', maxHeight: 340, overflowY: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead><tr style={{ textAlign: 'left', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)', fontSize: 12 }}>
                {['Record', 'Kept until', 'Outcome'].map(h => <th key={h} style={th}>{h}</th>)}
              </tr></thead>
              <tbody>
                {(b.items ?? []).map(x => (
                  <tr key={x.itemId} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                    <td style={td}><strong>{x.item?.reference || x.item?.title || x.itemId}</strong>{x.item?.reference && <span> · {x.item.title}</span>}
                      <div style={muted}>{x.item ? moduleName(x.item.moduleCode) : ''}{x.item?.contextLabel ? ` · ${x.item.contextLabel}` : ''}{x.item?.legalHold ? ' · under legal hold' : ''}</div></td>
                    <td style={td}>{d(x.item?.retainUntil)}{x.item && <div><PeriodNote i={x.item} /></div>}</td>
                    <td style={td}><Badge tone={OUTCOME_TONE[x.outcome]}>{x.outcome === 'PENDING' ? 'Waiting' : x.outcome === 'FAILED' ? 'Not disposed' : x.outcome.charAt(0) + x.outcome.slice(1).toLowerCase()}</Badge>
                      {x.detail && <div style={muted}>{x.detail}</div>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Err msg={err} />

          {open && (
            <div style={{ marginTop: 14 }}>
              {mine ? (
                <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginBottom: 10 }}>You proposed this request, so someone else must approve or reject it. You can withdraw it while it waits.</div>
              ) : canApprove ? (
                <Field label={b.status === 'APPROVED' ? 'This request was approved but did not finish' : 'Your decision'}
                  hint={b.status === 'PROPOSED' ? 'A note is required to reject, optional to approve.' : undefined}>
                  {b.status === 'PROPOSED' && <textarea aria-label="Decision note" rows={2} value={decisionNote} onChange={e => setDecisionNote(e.target.value)} style={{ ...inp, resize: 'vertical' }} />}
                </Field>
              ) : (
                <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', marginBottom: 10 }}>Someone with approval rights for records must decide this request.</div>
              )}

              {confirming && (
                <div role="alert" style={{ marginBottom: 10, padding: '10px 12px', border: '1px solid var(--hf-danger-border)', borderRadius: 8, background: 'var(--hf-danger-soft)', fontSize: 13, color: 'var(--hf-danger-text)' }}>
                  This permanently deletes the files of {b.status === 'APPROVED' ? pending : b.itemCount} record{(b.status === 'APPROVED' ? pending : b.itemCount) === 1 ? '' : 's'}. The records themselves,
                  their references and file hashes are kept. Records placed under legal hold, or no longer due, are skipped.
                </div>
              )}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, flexWrap: 'wrap' }}>
                {mine && b.status === 'PROPOSED' && canManage && <button style={btn} disabled={busy} onClick={() => onCancel(b.id)}><Undo2 size={12} /> Withdraw request</button>}
                {!mine && canApprove && b.status === 'PROPOSED' && <button style={btn} disabled={busy || decisionNote.trim().length < 3} onClick={() => onReject(b.id, decisionNote)}><Ban size={12} /> Reject</button>}
                {!mine && canApprove && (confirming
                  ? <button style={danger} disabled={busy} onClick={() => { setConfirming(false); onApprove(b.id, decisionNote) }}><Check size={14} /> {busy ? 'Disposing…' : 'Yes, delete the files'}</button>
                  : <button style={primary} disabled={busy} onClick={() => setConfirming(true)}><Check size={14} /> {b.status === 'APPROVED' ? 'Finish the disposal' : 'Approve and dispose'}</button>)}
              </div>
            </div>
          )}
        </>
      )}
    </Modal>
  )
}

function PolicyDialog({ policy, busy, err, onClose, onSave }: {
  policy: Policy | null; busy: boolean; err: string; onClose: () => void; onSave: (body: Record<string, unknown>) => void
}) {
  const [f, setF] = useState({
    moduleCode: policy?.moduleCode ?? 'PROJECTS', category: policy?.category ?? '', jurisdiction: policy?.jurisdiction ?? 'ZA',
    retainYears: String(policy?.retainYears ?? 7), personalInfo: policy?.personalInfo ?? false,
    confirmed: policy ? (policy.isDefault ? false : policy.confirmed) : false, notes: policy?.isDefault ? '' : (policy?.notes ?? ''),
  })
  const fixed = !!policy
  const years = Number(f.retainYears)
  const ok = f.category.trim() !== '' && Number.isInteger(years) && years >= 1 && years <= 100 && /^[A-Za-z]{2}(-[A-Za-z0-9]{1,3})?$/.test(f.jurisdiction.trim())
  return (
    <Modal title={policy ? (policy.isDefault ? 'Set a retention policy' : 'Edit retention policy') : 'Add retention policy'} onClose={onClose}>
      <Field label="Module">
        <select aria-label="Module" disabled={fixed} value={f.moduleCode} onChange={e => setF({ ...f, moduleCode: e.target.value })} style={inp}>
          {Object.entries(MODULES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </Field>
      <Field label="Category" hint="As the module records it (e.g. DRAWING, CONTRACT, INSURANCE). Use * for every category of the module that has no policy of its own.">
        <input aria-label="Category" disabled={fixed} value={f.category} onChange={e => setF({ ...f, category: e.target.value.toUpperCase() })} style={inp} />
      </Field>
      <Field label="Jurisdiction" hint="Country code (ZA), or a province such as ZA-GP.">
        <input aria-label="Jurisdiction" disabled={fixed} value={f.jurisdiction} onChange={e => setF({ ...f, jurisdiction: e.target.value.toUpperCase() })} style={inp} />
      </Field>
      <Field label="Keep for (years after the record is closed or archived)">
        <input aria-label="Years" type="number" min={1} max={100} value={f.retainYears} onChange={e => setF({ ...f, retainYears: e.target.value })} style={inp} />
      </Field>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, marginBottom: 8 }}>
        <input type="checkbox" checked={f.personalInfo} onChange={e => setF({ ...f, personalInfo: e.target.checked })} />
        These records hold personal information (POPIA: keep no longer than needed)
      </label>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, marginBottom: 12 }}>
        <input type="checkbox" checked={f.confirmed} onChange={e => setF({ ...f, confirmed: e.target.checked })} />
        The compliance owner has confirmed this period
      </label>
      <Field label="Notes (e.g. the law or rule the period comes from)">
        <textarea aria-label="Notes" rows={2} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} style={{ ...inp, resize: 'vertical' }} />
      </Field>
      <Err msg={err} />
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
        <button style={btn} onClick={onClose}>Cancel</button>
        <button style={primary} disabled={busy || !ok}
          onClick={() => onSave({ moduleCode: f.moduleCode, category: f.category.trim(), jurisdiction: f.jurisdiction.trim(), retainYears: years,
            personalInfo: f.personalInfo, confirmed: f.confirmed, notes: f.notes.trim() || null })}>Save policy</button>
      </div>
    </Modal>
  )
}
