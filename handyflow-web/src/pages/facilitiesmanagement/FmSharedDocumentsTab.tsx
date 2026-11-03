// src/pages/facilitiesmanagement/FmSharedDocumentsTab.tsx
// Completed work orders, and secure sharing of their files (photos, certificates, reports) with the client's people or a third
// party. The files are chosen when the first person is given access; everyone after that gets the same files.
import React, { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, ArrowLeft, FileText, Link2 } from 'lucide-react'
import { apiClient } from '../../api/client'
import { usePermissions } from '../../hooks/usePermission'
import { SecureSharePanel, type ShareRecipient } from '../../components/secure/SecureSharePanel'

const BASE = '/api/v1/facilitiesmanagement/sharing/work-orders'

interface WorkOrderRow { id: string; workOrderNumber: string; description: string | null; context: string | null; completedAt: string | null; files: number; shared: boolean; filesDisposed: boolean }
interface FileRow { id: string; fileName: string; contentType: string; sizeBytes: number; sha256: string; evidenceType: string; uploadedByName: string | null; createdAt: string; shared: boolean }
interface SharingView {
  workOrderId: string; workOrderNumber: string; description: string | null; context: string | null; status: string; completedAt: string | null
  shareable: boolean; notShareableReason: string | null; filesDisposed: boolean; files: FileRow[]; shareId: string | null; recipients: ShareRecipient[]
}

const d = (iso?: string | null) => iso ? new Date(iso).toLocaleDateString('en-ZA', { dateStyle: 'medium' }) : '—'
const size = (b: number) => b < 1024 * 1024 ? `${Math.max(1, Math.round(b / 1024))} KB` : `${(b / 1024 / 1024).toFixed(1)} MB`
const muted: React.CSSProperties = { fontSize: 12, color: 'var(--hf-text-faint)' }
const inp: React.CSSProperties = { padding: '8px 11px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, outline: 'none', background: 'var(--hf-surface)', color: 'var(--hf-text)' }

export default function FmSharedDocumentsTab() {
  const [q, setQ] = useState('')
  const [open, setOpen] = useState<string | null>(null)
  const list = useQuery<WorkOrderRow[]>({
    queryKey: ['fm-sharing-list', q], enabled: !open,
    queryFn: async () => (await apiClient.get(BASE, { params: { q: q.trim() || undefined, limit: 100 } })).data ?? [],
  })
  if (open) return <WorkOrderSharing id={open} onBack={() => setOpen(null)} />
  const rows = list.data ?? []
  return (
    <div style={{ fontFamily: "'Inter',sans-serif" }}>
      <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', maxWidth: 720, marginBottom: 14 }}>
        Share the photos, certificates and reports of a completed work order with the client's people or a third party such as an
        insurer. Each person gets their own expiring link and confirms their email with a code; you can revoke access at any time.
      </div>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
        <Search size={14} style={{ color: 'var(--hf-text-faint)' }} />
        <input aria-label="Search work orders" placeholder="Search by number or description" value={q} onChange={e => setQ(e.target.value)} style={{ ...inp, width: 320, maxWidth: '100%' }} />
      </div>
      {list.isLoading ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-text-faint)' }}>Loading…</div>
        : list.isError ? <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-danger-text)' }}>Could not load the work orders.</div>
        : rows.length === 0 ? <div style={{ padding: 50, textAlign: 'center', color: 'var(--hf-text-faint)' }}>No completed work orders{q ? ' match' : ' yet'}.</div>
        : (
          <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead><tr style={{ textAlign: 'left', background: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)', fontSize: 12 }}>
                {['Work order', 'Client and site', 'Completed', 'Files', ''].map(h => <th key={h} style={{ padding: '8px 12px', fontWeight: 600 }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {rows.map(w => (
                  <tr key={w.id} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                    <td style={{ padding: '10px 12px' }}><strong>{w.workOrderNumber}</strong><div style={muted}>{(w.description ?? '').slice(0, 90)}</div></td>
                    <td style={{ padding: '10px 12px' }}>{w.context ?? '—'}</td>
                    <td style={{ padding: '10px 12px' }}>{d(w.completedAt)}</td>
                    <td style={{ padding: '10px 12px' }}>{w.filesDisposed ? <span style={muted}>Disposed</span> : w.files}{w.shared && <div style={{ ...muted, display: 'flex', alignItems: 'center', gap: 4 }}><Link2 size={11} /> Shared</div>}</td>
                    <td style={{ padding: '10px 12px', textAlign: 'right' }}>
                      <button onClick={() => setOpen(w.id)} style={{ padding: '5px 10px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-text-secondary)' }}>Open</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
    </div>
  )
}

function WorkOrderSharing({ id, onBack }: { id: string; onBack: () => void }) {
  const qc = useQueryClient()
  const canChange = usePermissions(['FACILITIESMANAGEMENT_MANAGE', 'FACILITIESMANAGEMENT_ADMIN'], 'any')
  const [chosen, setChosen] = useState<Set<string>>(new Set())
  const { data, isLoading, isError } = useQuery<SharingView>({ queryKey: ['fm-sharing', id], queryFn: async () => (await apiClient.get(`${BASE}/${id}`)).data })
  if (isLoading) return <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-text-faint)' }}>Loading…</div>
  if (isError || !data) return <div style={{ padding: 40, textAlign: 'center', color: 'var(--hf-danger-text)' }}>Could not load this work order.</div>
  const fixed = data.shareId != null
  const toggle = (f: string) => setChosen(prev => { const n = new Set(prev); if (n.has(f)) n.delete(f); else n.add(f); return n })
  return (
    <div style={{ fontFamily: "'Inter',sans-serif" }}>
      <button onClick={onBack} style={{ display: 'inline-flex', alignItems: 'center', gap: 5, background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-primary-text)', fontSize: 13, fontWeight: 600, marginBottom: 10 }}>
        <ArrowLeft size={14} /> All completed work orders
      </button>
      <h3 style={{ margin: '0 0 2px', fontSize: 17 }}>{data.workOrderNumber}</h3>
      <div style={{ ...muted, marginBottom: 14 }}>{data.context ?? ''}{data.completedAt ? ` · completed ${d(data.completedAt)}` : ''} · {data.description}</div>

      <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, background: 'var(--hf-surface)', padding: 14, marginBottom: 14 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8 }}>Files</div>
        {data.filesDisposed ? <div style={muted}>The files of this work order were disposed of under the records retention policy.</div>
          : data.files.length === 0 ? <div style={muted}>No files are attached to this work order.</div>
          : (
            <>
              <div style={{ ...muted, marginBottom: 8 }}>{fixed ? 'These are the files shared with everyone who has access.' : 'Choose the files to share. They are fixed when the first person is given access.'}</div>
              {data.files.map(f => (
                <label key={f.id} style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '6px 0', borderTop: '1px solid var(--hf-border-subtle)', fontSize: 13, cursor: fixed ? 'default' : 'pointer' }}>
                  <input type="checkbox" disabled={fixed || !canChange || !data.shareable} checked={fixed ? f.shared : chosen.has(f.id)} onChange={() => toggle(f.id)} />
                  <FileText size={14} style={{ color: 'var(--hf-text-faint)' }} />
                  <span style={{ flex: 1 }}>{f.fileName}<span style={muted}> · {f.evidenceType} · {size(f.sizeBytes)} · {d(f.createdAt)}{f.uploadedByName ? ` · ${f.uploadedByName}` : ''}</span></span>
                  {fixed && !f.shared && <span style={muted}>not shared</span>}
                </label>
              ))}
            </>
          )}
      </div>

      <SecureSharePanel base={`${BASE}/${id}`} recipients={data.recipients} canChange={canChange} shareable={data.shareable}
        notShareableReason={data.notShareableReason}
        extraBody={() => ({ evidenceIds: [...chosen] })}
        blockAdd={() => (!fixed && chosen.size === 0 ? 'Choose at least one file above first.' : null)}
        onChanged={() => { qc.invalidateQueries({ queryKey: ['fm-sharing', id] }); qc.invalidateQueries({ queryKey: ['fm-sharing-list'] }); setChosen(new Set()) }}
        intro="People you give access to receive their own link to these files and confirm their email with a code each time." />
    </div>
  )
}
