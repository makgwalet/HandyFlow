// src/pages/contracting/ContractNegotiationSection.tsx
// Staff side of counterparty negotiation for one contract: open it, invite each counterparty with their own link, decide on their
// proposals, apply the accepted ones as a new draft revision, then mark the wording ready for signature. Proposals never change the
// contract by themselves; only "Apply accepted" does, and only for proposals staff accepted.
import { useState, type CSSProperties } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { usePermissions } from '../../hooks/usePermission'
import { apiError, unwrapOne } from './contractingApi'
import { DiffView, type Segment } from './ContractRedlineSection'
import {
  STATUS_LABEL, PROPOSAL_LABEL, KIND_LABEL, EVENT_LABEL, deadlineText, linkState, needsDecision, excerpt,
  type StaffNegotiation, type NegLink, type IssuedLink, type NegProposal,
} from './negotiation'

const btn: CSSProperties = { padding: '5px 11px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }
const solid: CSSProperties = { ...btn, background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none' }
const field: CSSProperties = { padding: '6px 9px', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12, background: 'var(--hf-surface)', color: 'var(--hf-text)' }
const panel: CSSProperties = { border: '1px solid var(--hf-border)', borderRadius: 10, padding: '10px 12px', background: 'var(--hf-surface-muted)' }
const h: CSSProperties = { margin: '0 0 6px', fontSize: 13, fontWeight: 700, color: 'var(--hf-text)' }

interface PartyRef { id: string; fullName: string }

function ProposalRow({ p, canWrite, busy, onAccept, onReject }: { p: NegProposal; canWrite: boolean; busy: boolean; onAccept: () => void; onReject: (reason: string) => void }) {
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const segs: Segment[] = p.kind === 'ADD'
    ? [{ type: 'HUNK', hunkId: 0, blocks: [], removed: [], added: p.proposedHtml ? [p.proposedHtml] : [] }]
    : [{ type: 'HUNK', hunkId: 0, blocks: [], removed: p.originalHtml ? [p.originalHtml] : [], added: p.proposedHtml ? [p.proposedHtml] : [] }]
  const decidable = canWrite && (needsDecision(p) || p.status === 'ACCEPTED')
  return (
    <div style={{ ...panel, background: 'var(--hf-surface)' }} data-testid="proposal">
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, fontSize: 12 }}>
        <strong>{p.partyName ?? 'Counterparty'} · {KIND_LABEL[p.kind]}</strong>
        <span data-testid="proposal-status" style={{ color: p.status === 'CONFLICTED' ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)' }}>{PROPOSAL_LABEL[p.status]}</span>
      </div>
      {p.kind === 'ADD' && p.originalHtml && <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>Added after “{excerpt(p.originalHtml, 70)}”</div>}
      {p.kind === 'ADD' && !p.originalHtml && <div style={{ fontSize: 11, color: 'var(--hf-text-muted)' }}>Added at the start</div>}
      <div style={{ marginTop: 4 }}><DiffView segments={segs} /></div>
      {p.rationale && <div style={{ fontSize: 12, color: 'var(--hf-text-secondary)', marginTop: 4 }}>Reason given: {p.rationale}</div>}
      {p.status === 'CONFLICTED' && <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginTop: 4 }}>Another proposal changes the same wording. Reject one so the other can be decided.</div>}
      {!p.targetStillPresent && p.status !== 'APPLIED' && p.status !== 'REJECTED' && p.status !== 'WITHDRAWN' && p.status !== 'SUPERSEDED' &&
        <div style={{ fontSize: 12, color: 'var(--hf-danger-text)', marginTop: 4 }}>The wording this changes has since been edited, so it cannot be accepted.</div>}
      {p.decisionNote && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 4 }}>Decision note: {p.decisionNote}</div>}
      {p.appliedVersionNo != null && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 4 }}>Applied as version {p.appliedVersionNo}</div>}
      {decidable && !rejecting && (
        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
          {needsDecision(p) && <button style={solid} disabled={busy || p.status === 'CONFLICTED' || !p.targetStillPresent} onClick={onAccept}>Accept</button>}
          <button style={btn} disabled={busy} onClick={() => setRejecting(true)}>Reject</button>
        </div>
      )}
      {decidable && rejecting && (
        <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
          <input aria-label="Reason for rejecting" style={{ ...field, flex: 1 }} placeholder="Why? The counterparty will see this" value={reason} onChange={e => setReason(e.target.value)} />
          <button style={solid} disabled={busy || !reason.trim()} onClick={() => { onReject(reason.trim()); setRejecting(false); setReason('') }}>Reject proposal</button>
          <button style={btn} onClick={() => setRejecting(false)}>Cancel</button>
        </div>
      )}
    </div>
  )
}

export function ContractNegotiationSection({ contractId, status, parties }: { contractId: string; status: string; parties: PartyRef[] }) {
  const qc = useQueryClient()
  const canWrite = usePermissions(['CONTRACTS_MANAGE', 'CONTRACTS_ADMIN'], 'any')
  const editable = status === 'DRAFT' || status === 'UNDER_REVIEW'
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const [deadline, setDeadline] = useState('')
  const [partyId, setPartyId] = useState('')
  const [issued, setIssued] = useState<IssuedLink | null>(null)
  const [copied, setCopied] = useState(false)
  const [note, setNote] = useState('')
  const [internal, setInternal] = useState(false)
  const [audience, setAudience] = useState('')
  const [compare, setCompare] = useState(false)
  const [closeReason, setCloseReason] = useState('')

  const base = `/api/v1/contracts/${contractId}/negotiation`
  const neg = useQuery<StaffNegotiation>({ queryKey: ['contract-negotiation', contractId], enabled: open, queryFn: async () => unwrapOne<StaffNegotiation>(await apiClient.get(base)) })
  const links = useQuery<NegLink[]>({ queryKey: ['contract-negotiation-links', contractId], enabled: open && !!neg.data?.negotiation,
    queryFn: async () => unwrapOne<NegLink[]>(await apiClient.get(`${base}/links`)) })
  const n = neg.data?.negotiation ?? null
  const cmp = useQuery<{ segments: Segment[] }>({ queryKey: ['contract-negotiation-compare', contractId, n?.baseVersionNo, n?.finalVersionNo ?? neg.data?.currentVersionNo], enabled: open && compare && !!n,
    queryFn: async () => unwrapOne<{ segments: Segment[] }>(await apiClient.get(`/api/v1/contracts/${contractId}/versions/compare?from=${n!.baseVersionNo}&to=${n!.finalVersionNo ?? neg.data!.currentVersionNo}`)) })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['contract-negotiation', contractId] })
    qc.invalidateQueries({ queryKey: ['contract-negotiation-links', contractId] })
    qc.invalidateQueries({ queryKey: ['contract-negotiation-compare', contractId] })
    qc.invalidateQueries({ queryKey: ['contract-versions', contractId] })
    qc.invalidateQueries({ queryKey: ['contracts'] })
    setError('')
  }
  const useRun = <V,>(fn: (v: V) => Promise<unknown>, fallback: string, after?: () => void) => useMutation({
    mutationFn: fn, onSuccess: () => { after?.(); refresh() }, onError: (e: unknown) => setError(apiError(e, fallback)),
  })

  const openIt = useRun<void>(() => apiClient.post(`${base}/open`, { responseDeadline: deadline || null }), 'The negotiation could not be opened')
  const setDl = useRun<void>(() => apiClient.put(`${base}/deadline`, { responseDeadline: deadline || null }), 'The deadline could not be changed')
  const accept = useRun<string>(id => apiClient.post(`${base}/proposals/${id}/accept`, {}), 'The proposal could not be accepted')
  const reject = useRun<{ id: string; reason: string }>(v => apiClient.post(`${base}/proposals/${v.id}/reject`, { reason: v.reason }), 'The proposal could not be rejected')
  const apply = useRun<void>(() => apiClient.post(`${base}/apply`, {}), 'The accepted proposals could not be applied')
  const ready = useRun<void>(() => apiClient.post(`${base}/ready`), 'The negotiation could not be marked ready')
  const close = useRun<void>(() => apiClient.post(`${base}/close`, { reason: closeReason || null }), 'The negotiation could not be closed', () => setCloseReason(''))
  const reopen = useRun<void>(() => apiClient.post(`${base}/reopen`, { responseDeadline: deadline || null }), 'The negotiation could not be reopened')
  const issue = useMutation({
    mutationFn: async () => unwrapOne<IssuedLink>(await apiClient.post(`${base}/links`, { partyId })),
    onSuccess: r => { setIssued(r); setCopied(false); refresh() }, onError: (e: unknown) => setError(apiError(e, 'The link could not be issued')),
  })
  const revoke = useRun<string>(id => apiClient.post(`${base}/links/${id}/revoke`), 'The link could not be revoked')
  const comment = useRun<void>(() => apiClient.post(`${base}/comments`, { body: note, internalOnly: internal, audiencePartyId: internal || !audience ? null : audience }),
    'The comment could not be posted', () => { setNote(''); setInternal(false) })

  const busy = [openIt, setDl, accept, reject, apply, ready, close, reopen, revoke, comment].some(m => m.isPending)
  const d = neg.data
  const props = d?.proposals ?? []
  const waiting = props.filter(needsDecision)
  const accepted = props.filter(p => p.status === 'ACCEPTED')
  const history = props.filter(p => !needsDecision(p) && p.status !== 'ACCEPTED')
  const active = n && ['OPEN', 'STAFF_REVIEW', 'CHANGES_REQUIRED'].includes(n.status)
  const nameOf = (id?: string | null) => parties.find(p => p.id === id)?.fullName

  return (
    <div style={{ marginBottom: 14 }}>
      <button onClick={() => setOpen(!open)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-primary-text)', fontSize: 12, fontWeight: 600 }}>
        {open ? 'Hide negotiation' : 'Negotiation with counterparties'}
      </button>
      {open && (
        <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {error && <div role="alert" style={{ fontSize: 12, color: 'var(--hf-danger-text)' }}>{error}</div>}
          {neg.isLoading && <div style={{ fontSize: 12 }}>Loading…</div>}

          {d && !n && (
            <div style={panel}>
              <div style={h}>Not started</div>
              <div style={{ fontSize: 12, color: 'var(--hf-text-secondary)', marginBottom: 6 }}>
                Counterparties can propose changes to the wording and comment. They can never edit the contract: you decide on every proposal.
              </div>
              {canWrite && editable ? (
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <label style={{ fontSize: 12 }}>Respond by <input type="date" aria-label="Response deadline" style={field} value={deadline} onChange={e => setDeadline(e.target.value)} /></label>
                  <button style={solid} disabled={busy || parties.length === 0} onClick={() => openIt.mutate()}>Open negotiation</button>
                  {parties.length === 0 && <span style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>Add a party first</span>}
                </div>
              ) : <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{editable ? 'You can view but not open negotiations.' : 'Negotiation is only possible while the contract is a draft or under review.'}</div>}
            </div>
          )}

          {d && n && (
            <>
              <div style={panel}>
                <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
                  <div style={h} data-testid="neg-status">{STATUS_LABEL[n.status]}</div>
                  <div style={{ fontSize: 12, color: n.deadlinePassed ? 'var(--hf-danger-text)' : 'var(--hf-text-muted)' }}>{deadlineText(n.responseDeadline)}</div>
                </div>
                <div style={{ fontSize: 12, color: 'var(--hf-text-secondary)' }}>
                  {n.counts.pending + n.counts.conflicted} to decide · {n.counts.accepted} accepted, not yet applied · {n.counts.applied} applied · {n.counts.rejected} rejected
                  · {n.counts.withdrawn + n.counts.superseded} withdrawn or superseded
                </div>
                {n.status === 'READY_FOR_SIGNATURE' && <div style={{ fontSize: 12, marginTop: 4 }}>Final wording pinned at version {n.finalVersionNo}. Editing the contract now blocks sending until it is marked ready again.</div>}
                {n.closeReason && <div style={{ fontSize: 12, marginTop: 4 }}>Closed: {n.closeReason}</div>}
                {canWrite && (
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 8, alignItems: 'center' }}>
                    {active && accepted.length > 0 && <button style={solid} disabled={busy || !editable} onClick={() => apply.mutate()}>Apply {accepted.length} accepted</button>}
                    {active && <button style={btn} disabled={busy || waiting.length > 0 || accepted.length > 0} title={waiting.length || accepted.length ? 'Decide on and apply every proposal first' : undefined} onClick={() => ready.mutate()}>Mark ready for signature</button>}
                    {n.status !== 'CLOSED' && <>
                      <input aria-label="Reason for closing" style={{ ...field, width: 180 }} placeholder="Reason (optional)" value={closeReason} onChange={e => setCloseReason(e.target.value)} />
                      <button style={btn} disabled={busy || waiting.length > 0 || accepted.length > 0} onClick={() => close.mutate()}>Close negotiation</button>
                    </>}
                    {(n.status === 'READY_FOR_SIGNATURE' || n.status === 'CLOSED') && editable && <button style={btn} disabled={busy} onClick={() => reopen.mutate()}>Reopen</button>}
                    {active && <>
                      <input type="date" aria-label="New deadline" style={field} value={deadline} onChange={e => setDeadline(e.target.value)} />
                      <button style={btn} disabled={busy} onClick={() => setDl.mutate()}>Change deadline</button>
                    </>}
                  </div>
                )}
              </div>

              {active && canWrite && (
                <div style={panel}>
                  <div style={h}>Invite a counterparty</div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    <select aria-label="Party" style={field} value={partyId} onChange={e => setPartyId(e.target.value)}>
                      <option value="">Choose a party…</option>
                      {parties.map(p => <option key={p.id} value={p.id}>{p.fullName}</option>)}
                    </select>
                    <button style={solid} disabled={!partyId || issue.isPending} onClick={() => issue.mutate()}>Issue link</button>
                  </div>
                  {issued && (
                    <div style={{ marginTop: 8, fontSize: 12 }}>
                      <div>Link for {nameOf(issued.partyId)} — shown once. It was {issued.deliveryStatus === 'QUEUED' ? 'emailed to them' : 'not emailed (no email address)'}; copy it to send another way.</div>
                      <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
                        <input readOnly aria-label="Negotiation link" style={{ ...field, flex: 1 }} value={issued.url} onFocus={e => e.currentTarget.select()} />
                        <button style={btn} onClick={async () => { try { await navigator.clipboard.writeText(issued.url); setCopied(true) } catch { setCopied(false) } }}>{copied ? 'Copied' : 'Copy'}</button>
                      </div>
                    </div>
                  )}
                </div>
              )}
              {(links.data?.length ?? 0) > 0 && (
                <div style={panel}>
                  <div style={h}>Links</div>
                  {links.data!.map(l => (
                    <div key={l.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, padding: '3px 0' }}>
                      <span>{l.partyName} · {linkState(l)} · expires {new Date(l.expiresAt).toLocaleDateString('en-ZA')}{l.lastAccessedAt ? ` · last opened ${new Date(l.lastAccessedAt).toLocaleDateString('en-ZA')}` : ''}</span>
                      {canWrite && linkState(l) === 'Active' && <button style={btn} disabled={busy} onClick={() => revoke.mutate(l.id)}>Revoke</button>}
                    </div>
                  ))}
                </div>
              )}

              {waiting.length > 0 && <div><div style={h}>Needs your decision ({waiting.length})</div><div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {waiting.map(p => <ProposalRow key={p.id} p={p} canWrite={canWrite && editable} busy={busy} onAccept={() => accept.mutate(p.id)} onReject={r => reject.mutate({ id: p.id, reason: r })} />)}</div></div>}
              {accepted.length > 0 && <div><div style={h}>Accepted, not yet applied ({accepted.length})</div><div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {accepted.map(p => <ProposalRow key={p.id} p={p} canWrite={canWrite && editable} busy={busy} onAccept={() => accept.mutate(p.id)} onReject={r => reject.mutate({ id: p.id, reason: r })} />)}</div></div>}
              {props.length === 0 && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>No proposals yet.</div>}
              {history.length > 0 && <details><summary style={{ ...h, cursor: 'pointer' }}>History ({history.length})</summary><div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {history.map(p => <ProposalRow key={p.id} p={p} canWrite={false} busy={busy} onAccept={() => undefined} onReject={() => undefined} />)}</div></details>}

              <div style={panel}>
                <div style={h}>Comments</div>
                {d.comments.length === 0 && <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>No comments yet.</div>}
                {d.comments.map(c => (
                  <div key={c.id} style={{ fontSize: 12, padding: '4px 0', borderTop: '1px solid var(--hf-border)' }}>
                    <span style={{ color: 'var(--hf-text-muted)' }}>{c.authorType === 'STAFF' ? 'Staff' : nameOf(c.partyId) ?? 'Counterparty'}{c.internalOnly ? ' · internal note (not shown to counterparties)' : ''} · {new Date(c.createdAt).toLocaleString('en-ZA')}{c.blockExcerpt ? ` · on “${excerpt(c.blockExcerpt, 50)}”` : ''}</span>
                    <div style={{ whiteSpace: 'pre-wrap' }}>{c.body}</div>
                  </div>
                ))}
                {canWrite && n.status !== 'CLOSED' && (
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8, alignItems: 'center' }}>
                    <input aria-label="Comment" style={{ ...field, flex: 1, minWidth: 180 }} placeholder="Reply or leave a note" value={note} onChange={e => setNote(e.target.value)} />
                    <label style={{ fontSize: 12 }}><input type="checkbox" checked={internal} onChange={e => setInternal(e.target.checked)} /> Internal note</label>
                    {!internal && <select aria-label="Visible to" style={field} value={audience} onChange={e => setAudience(e.target.value)}>
                      <option value="">All counterparties</option>
                      {parties.map(p => <option key={p.id} value={p.id}>{p.fullName} only</option>)}
                    </select>}
                    <button style={solid} disabled={!note.trim() || busy} onClick={() => comment.mutate()}>Post</button>
                  </div>
                )}
              </div>

              <div style={panel}>
                <button style={btn} onClick={() => setCompare(!compare)}>{compare ? 'Hide comparison' : `Compare version ${n.baseVersionNo} with ${n.finalVersionNo ? `final version ${n.finalVersionNo}` : `current version ${d.currentVersionNo}`}`}</button>
                {compare && (cmp.data ? <div style={{ marginTop: 8 }}><DiffView segments={cmp.data.segments} /></div> : <div style={{ fontSize: 12, marginTop: 6 }}>Loading…</div>)}
              </div>

              <details><summary style={{ ...h, cursor: 'pointer' }}>Audit trail ({d.events.length})</summary>
                {d.events.map(e => (
                  <div key={e.id} style={{ fontSize: 12, padding: '2px 0', color: 'var(--hf-text-secondary)' }}>
                    {new Date(e.createdAt).toLocaleString('en-ZA')} · {EVENT_LABEL[e.eventType] ?? e.eventType}
                    {e.partyId && nameOf(e.partyId) ? ` · ${nameOf(e.partyId)}` : ''}{e.detail ? ` · ${e.detail}` : ''}
                  </div>
                ))}
              </details>
            </>
          )}
        </div>
      )}
    </div>
  )
}
