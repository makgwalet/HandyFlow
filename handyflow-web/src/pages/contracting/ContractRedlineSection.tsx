// src/pages/contracting/ContractRedlineSection.tsx
// Version history and redlining of one contract's body. A body is changed by proposing a revised text; a reviewer accepts or
// rejects each changed block. The comparison is block by block (a changed word shows its whole paragraph as removed and added).
import { useState, type CSSProperties } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermissions } from '../../hooks/usePermission'
import { sanitizeHtml } from './sanitizeHtml'

interface Segment { type: 'EQUAL' | 'HUNK'; hunkId: number; blocks: string[]; removed: string[]; added: string[] }
interface VersionInfo { versionNo: number; source: string; note?: string | null; createdAt: string }
interface Proposal {
  id: string; baseVersionNo: number; status: 'OPEN' | 'APPLIED' | 'REJECTED'; note?: string | null; createdAt: string
  decisionNote?: string | null; acceptedHunks?: string | null; appliedVersionNo?: number | null; stale: boolean; hunks: number; segments: Segment[]
}

const SOURCE_LABEL: Record<string, string> = { BASELINE: 'Baseline', CREATED: 'Created', EDITED: 'Edited', PROPOSAL_APPLIED: 'Changes applied' }

const btn: CSSProperties = { padding: '5px 11px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }
const box: CSSProperties = { border: '1px solid var(--hf-border)', borderRadius: 8, padding: '8px 12px', background: 'var(--hf-surface)', fontSize: 13, lineHeight: 1.7, color: 'var(--hf-text-secondary)' }

const errMsg = (e: unknown, fallback: string): string =>
  (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
const data = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T

function Html({ html }: { html: string }) {
  return <div dangerouslySetInnerHTML={{ __html: sanitizeHtml(html) }} />
}

/** Unchanged blocks are muted; removed blocks are struck through in red, added blocks green. Optional per-change checkboxes. */
function DiffView({ segments, accepted, onToggle }: { segments: Segment[]; accepted?: Set<number>; onToggle?: (id: number) => void }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {segments.map((s, i) => s.type === 'EQUAL' ? (
        <div key={i} style={{ ...box, opacity: 0.65 }}>{s.blocks.map((b, j) => <Html key={j} html={b} />)}</div>
      ) : (
        <div key={i} style={{ border: '1.5px solid var(--hf-border-strong, var(--hf-border))', borderRadius: 8 }}>
          {onToggle && accepted && (
            <label style={{ display: 'flex', gap: 6, alignItems: 'center', padding: '4px 10px', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)' }}>
              <input type="checkbox" checked={accepted.has(s.hunkId)} onChange={() => onToggle(s.hunkId)} /> Accept change {s.hunkId + 1}
            </label>
          )}
          {s.removed.length > 0 && (
            <div style={{ ...box, background: 'var(--hf-danger-soft)', textDecoration: 'line-through', color: 'var(--hf-danger-text)', border: 'none' }}>
              {s.removed.map((b, j) => <Html key={j} html={b} />)}
            </div>
          )}
          {s.added.length > 0 && (
            <div style={{ ...box, background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', border: 'none' }}>
              {s.added.map((b, j) => <Html key={j} html={b} />)}
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

function ProposalCard({ p, canWrite, onChanged, onError }: { p: Proposal; canWrite: boolean; onChanged: () => void; onError: (m: string) => void }) {
  const [accepted, setAccepted] = useState<Set<number>>(new Set(Array.from({ length: p.hunks }, (_, i) => i)))
  const [open, setOpen] = useState(p.status === 'OPEN')
  const toggle = (id: number) => setAccepted(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n })

  const apply = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/change-proposals/${p.id}/apply`, { acceptedHunks: Array.from(accepted), note: null }),
    onSuccess: onChanged,
    onError: (e: unknown) => onError(errMsg(e, 'The changes could not be applied')),
  })
  const reject = useMutation({
    mutationFn: (reason: string) => apiClient.post(`/api/v1/contracts/change-proposals/${p.id}/reject`, { reason }),
    onSuccess: onChanged,
    onError: (e: unknown) => onError(errMsg(e, 'The proposal could not be rejected')),
  })
  const askReject = () => { const r = window.prompt('Why is this proposal being rejected?'); if (r && r.trim()) reject.mutate(r.trim()) }

  return (
    <div style={{ border: '1px solid var(--hf-border)', borderRadius: 10, padding: '10px 12px', background: 'var(--hf-surface-muted)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, cursor: 'pointer' }} onClick={() => setOpen(!open)}>
        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--hf-text)' }}>
          {p.status === 'OPEN' ? 'Open proposal' : p.status === 'APPLIED' ? `Applied as version ${p.appliedVersionNo}` : 'Rejected'}
          <span style={{ fontWeight: 400, color: 'var(--hf-text-muted)' }}> · {p.hunks} change{p.hunks === 1 ? '' : 's'} against version {p.baseVersionNo} · {new Date(p.createdAt).toLocaleDateString('en-ZA')}</span>
        </div>
        <span style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{open ? 'Hide' : 'Show'}</span>
      </div>
      {p.note && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 2 }}>{p.note}</div>}
      {p.decisionNote && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>Decision note: {p.decisionNote}</div>}
      {open && (
        <div style={{ marginTop: 8 }}>
          {p.stale && <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginBottom: 6 }}>The contract has changed since this proposal was made, so it cannot be applied. Make a new proposal against the current text.</div>}
          <DiffView segments={p.segments} accepted={p.status === 'OPEN' && canWrite && !p.stale ? accepted : undefined}
            onToggle={p.status === 'OPEN' && canWrite && !p.stale ? toggle : undefined} />
          {p.status === 'OPEN' && canWrite && (
            <div style={{ display: 'flex', gap: 8, marginTop: 10 }}>
              {!p.stale && <button style={{ ...btn, background: accepted.size ? 'var(--hf-primary)' : 'var(--hf-text-faint)', color: 'var(--hf-text-on-solid)', border: 'none' }}
                disabled={accepted.size === 0 || apply.isPending} onClick={() => apply.mutate()}>Apply {accepted.size} of {p.hunks} change{p.hunks === 1 ? '' : 's'}</button>}
              <button style={btn} disabled={reject.isPending} onClick={askReject}>Reject proposal</button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export function ContractRedlineSection({ contractId, status }: { contractId: string; status: string }) {
  const qc = useQueryClient()
  const canWrite = usePermissions(['CONTRACTS_MANAGE', 'CONTRACTS_ADMIN'], 'any')
  const editable = status === 'DRAFT' || status === 'UNDER_REVIEW'
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const [from, setFrom] = useState<number | ''>('')
  const [to, setTo] = useState<number | ''>('')
  const [drafting, setDrafting] = useState(false)
  const [text, setText] = useState('')
  const [note, setNote] = useState('')

  const { data: versions = [] } = useQuery<VersionInfo[]>({
    queryKey: ['contract-versions', contractId], enabled: open,
    queryFn: async () => data<VersionInfo[]>(await apiClient.get(`/api/v1/contracts/${contractId}/versions`)),
  })
  const { data: proposals = [] } = useQuery<Proposal[]>({
    queryKey: ['contract-proposals', contractId], enabled: open,
    queryFn: async () => data<Proposal[]>(await apiClient.get(`/api/v1/contracts/${contractId}/change-proposals`)),
  })
  const { data: cmp } = useQuery<{ segments: Segment[]; hunks: number }>({
    queryKey: ['contract-compare', contractId, from, to], enabled: open && from !== '' && to !== '' && from !== to,
    queryFn: async () => data<{ segments: Segment[]; hunks: number }>(await apiClient.get(`/api/v1/contracts/${contractId}/versions/compare?from=${from}&to=${to}`)),
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['contract-versions', contractId] })
    qc.invalidateQueries({ queryKey: ['contract-proposals', contractId] })
    qc.invalidateQueries({ queryKey: ['contracts'] })
    setError('')
  }

  const startDraft = async () => {
    try {
      const latest = versions[0]?.versionNo
      if (latest == null) return
      const v = data<{ body: string }>(await apiClient.get(`/api/v1/contracts/${contractId}/versions/${latest}`))
      setText(v.body); setNote(''); setDrafting(true); setError('')
    } catch (e: unknown) { setError(errMsg(e, 'The current text could not be loaded')) }
  }
  const propose = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/contracts/${contractId}/change-proposals`, { body: text, note: note || null }),
    onSuccess: () => { setDrafting(false); refresh() },
    onError: (e: unknown) => setError(errMsg(e, 'The proposal could not be saved')),
  })

  return (
    <div style={{ marginBottom: 14 }}>
      <button onClick={() => setOpen(!open)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-primary-text)', fontSize: 12, fontWeight: 600 }}>
        {open ? 'Hide versions and changes' : 'Versions and changes'}
      </button>
      {open && (
        <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {error && <div style={{ fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}

          <div>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', marginBottom: 6 }}>Versions</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 3, fontSize: 12, color: 'var(--hf-text-secondary)' }}>
              {versions.map(v => <div key={v.versionNo}>Version {v.versionNo} · {SOURCE_LABEL[v.source] ?? v.source} · {new Date(v.createdAt).toLocaleString('en-ZA')}{v.note ? ` · ${v.note}` : ''}</div>)}
            </div>
            {versions.length > 1 && (
              <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8, fontSize: 12 }}>
                Compare
                <select value={from} onChange={e => setFrom(e.target.value ? Number(e.target.value) : '')}><option value="">version…</option>{versions.map(v => <option key={v.versionNo} value={v.versionNo}>v{v.versionNo}</option>)}</select>
                with
                <select value={to} onChange={e => setTo(e.target.value ? Number(e.target.value) : '')}><option value="">version…</option>{versions.map(v => <option key={v.versionNo} value={v.versionNo}>v{v.versionNo}</option>)}</select>
              </div>
            )}
            {cmp && <div style={{ marginTop: 8 }}>{cmp.hunks === 0 ? <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>No differences.</div> : <DiffView segments={cmp.segments} />}</div>}
          </div>

          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase' }}>Change proposals</div>
              {canWrite && editable && !drafting && versions.length > 0 && <button style={btn} onClick={startDraft}>Propose a change</button>}
            </div>
            {!editable && <div style={{ fontSize: 12, color: 'var(--hf-text-faint)', marginBottom: 6 }}>The text of a contract that has been sent or signed cannot be changed.</div>}
            {drafting && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 10 }}>
                <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>Edit the text below (HTML: paragraphs, headings, lists and tables). Only the changed blocks are shown for review. Scripts, links and images are removed.</div>
                <textarea rows={12} value={text} onChange={e => setText(e.target.value)} style={{ width: '100%', boxSizing: 'border-box', fontFamily: 'monospace', fontSize: 12, padding: 8, border: '1.5px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)' }} />
                <input placeholder="Why this change (optional)" value={note} onChange={e => setNote(e.target.value)} style={{ padding: '8px 10px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, background: 'var(--hf-surface)' }} />
                <div style={{ display: 'flex', gap: 8 }}>
                  <button style={{ ...btn, background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none' }} disabled={propose.isPending} onClick={() => propose.mutate()}>{propose.isPending ? 'Saving…' : 'Submit proposal'}</button>
                  <button style={btn} onClick={() => setDrafting(false)}>Cancel</button>
                </div>
              </div>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {proposals.length === 0 && !drafting && <div style={{ fontSize: 12, color: 'var(--hf-text-faint)' }}>No proposals yet.</div>}
              {proposals.map(p => <ProposalCard key={p.id} p={p} canWrite={canWrite && editable} onChanged={refresh} onError={setError} />)}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
