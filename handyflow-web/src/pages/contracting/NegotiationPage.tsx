// src/pages/contracting/NegotiationPage.tsx
// Public page opened from a negotiation link: /negotiate/{token}. No HandyFlow account. The counterparty confirms a one-time code,
// then reads the wording and proposes additions, deletions and replacements, and comments. Nothing here changes the contract: a
// proposal is a request that the sender accepts or rejects. All calls go to /api/v1/negotiate/{token}/... with the session header.
import { useState, type CSSProperties } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { sanitizeHtml } from './sanitizeHtml'
import { Html, DiffView, type Segment } from './ContractRedlineSection'
import { apiError, unwrapOne } from './contractingApi'
import {
  publicApi, SESSION_HEADER, httpStatus, loadSession, saveSession, buildReplacement, canEditAsText, blockText, excerpt, deadlineText, isLive, byStaff, needsMyAnswer, roundOf,
  COUNTERPARTY_STATUS_LABEL, PROPOSAL_LABEL, KIND_LABEL,
  type CounterpartyNegotiation, type LinkInfo, type NegProposal, type NegComment, type ProposalKind,
} from './negotiation'

const card: CSSProperties = { background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '16px 18px' }
const btn: CSSProperties = { padding: '6px 12px', border: '1px solid var(--hf-border)', borderRadius: 7, background: 'var(--hf-surface)', fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-primary-text)' }
const primary: CSSProperties = { ...btn, background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none' }
const input: CSSProperties = { width: '100%', padding: '8px 10px', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 13, boxSizing: 'border-box', background: 'var(--hf-surface)', color: 'var(--hf-text)' }
const norm = (s: string | null | undefined) => (s ?? '').replace(/\s+/g, ' ').trim()

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', background: 'var(--hf-bg, #f6f7f9)', padding: '28px 16px' }}>
      <div style={{ maxWidth: 860, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 14 }}>{children}</div>
    </div>
  )
}

function Notice({ tone, children }: { tone: 'info' | 'error'; children: React.ReactNode }) {
  return <div role={tone === 'error' ? 'alert' : 'status'} style={{ padding: '9px 12px', borderRadius: 8, fontSize: 13,
    background: tone === 'error' ? 'var(--hf-danger-soft)' : 'var(--hf-surface-muted)', color: tone === 'error' ? 'var(--hf-danger-text)' : 'var(--hf-text-secondary)' }}>{children}</div>
}

// ── step 1 and 2: confirm identity ───────────────────────────────────────────

function CodeStep({ token, info, onVerified }: { token: string; info: LinkInfo; onVerified: (session: string) => void }) {
  const [sent, setSent] = useState<{ channel: string; maskedDestination: string } | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const send = useMutation({
    mutationFn: async () => unwrapOne<{ channel: string; maskedDestination: string }>(await publicApi.post(`/api/v1/negotiate/${token}/code`)),
    onSuccess: r => { setSent(r); setError('') },
    onError: (e: unknown) => setError(httpStatus(e) === 429 ? 'Too many requests. Please wait a few minutes and try again.' : apiError(e, 'The code could not be sent')),
  })
  const verify = useMutation({
    mutationFn: async () => unwrapOne<{ sessionToken: string }>(await publicApi.post(`/api/v1/negotiate/${token}/verify`, { code })),
    onSuccess: r => { saveSession(token, r.sessionToken); onVerified(r.sessionToken) },
    onError: (e: unknown) => setError(httpStatus(e) === 429 ? 'Too many attempts. Please wait a few minutes and try again.'
      : httpStatus(e) === 401 ? 'That code is not correct or has expired.' : apiError(e, 'The code could not be checked')),
  })
  return (
    <Shell>
      <div style={card}>
        <h1 style={{ margin: 0, fontSize: 20, color: 'var(--hf-text)' }}>{info.contractTitle}</h1>
        <p style={{ fontSize: 13, color: 'var(--hf-text-secondary)', lineHeight: 1.6 }}>
          You have been invited to review this contract and propose changes. To protect it, we first confirm it is you.
        </p>
        {!sent ? (
          <button style={primary} disabled={send.isPending} onClick={() => send.mutate()}>
            Send a code to {info.maskedDestination}
          </button>
        ) : (
          <form onSubmit={e => { e.preventDefault(); if (/^\d{6}$/.test(code)) verify.mutate() }} style={{ display: 'flex', flexDirection: 'column', gap: 8, maxWidth: 320 }}>
            <label style={{ fontSize: 13, color: 'var(--hf-text-secondary)' }} htmlFor="neg-code">
              Enter the 6-digit code sent {sent.channel === 'SMS' ? 'by SMS' : 'by email'} to {sent.maskedDestination}
            </label>
            <input id="neg-code" style={{ ...input, letterSpacing: 4, fontSize: 18 }} inputMode="numeric" autoComplete="one-time-code" maxLength={6}
              value={code} onChange={e => setCode(e.target.value.replace(/\D/g, ''))} />
            <div style={{ display: 'flex', gap: 8 }}>
              <button type="submit" style={primary} disabled={code.length !== 6 || verify.isPending}>Continue</button>
              <button type="button" style={btn} disabled={send.isPending} onClick={() => send.mutate()}>Send a new code</button>
            </div>
          </form>
        )}
        {error && <div style={{ marginTop: 10 }}><Notice tone="error">{error}</Notice></div>}
      </div>
    </Shell>
  )
}

// ── step 3: the workspace ────────────────────────────────────────────────────

type Mode = 'REPLACE' | 'DELETE' | 'ADD' | 'COMMENT'
interface Draft { blockIndex: number; mode: Mode }

interface NoteActions {
  busy: boolean; canAct: boolean
  onWithdraw: () => void; onAccept: () => void; onReject: (reason: string) => void; onCounter: (html: string, reason: string) => void
}

function ProposalNote({ p, a }: { p: NegProposal; a: NoteActions }) {
  const [mode, setMode] = useState<'REJECT' | 'COUNTER' | null>(null)
  const [reason, setReason] = useState('')
  const [text, setText] = useState('')
  const segs: Segment[] | null = p.kind === 'ADD' ? null : [{ type: 'HUNK', hunkId: 0, blocks: [], removed: p.originalHtml ? [p.originalHtml] : [], added: p.proposedHtml ? [p.proposedHtml] : [] }]
  const theirs = byStaff(p)
  const answerable = a.canAct && needsMyAnswer(p)
  const counterable = answerable && p.kind !== 'DELETE' && (p.kind === 'ADD' || !p.originalHtml || canEditAsText(p.originalHtml))
  return (
    <div style={{ margin: '6px 0 0 14px', padding: '8px 10px', border: theirs ? '1px solid var(--hf-primary)' : '1px dashed var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface-muted)', fontSize: 12 }}>
      <div style={{ fontWeight: 700, color: 'var(--hf-text)' }}>
        {theirs ? 'From the sender' : 'From you'} · {KIND_LABEL[p.kind]}{roundOf(p) > 1 ? ` · round ${roundOf(p)}` : ''} · <span data-testid="proposal-status">{answerable ? 'Waiting for your answer' : PROPOSAL_LABEL[p.status]}</span>
        {p.appliedVersionNo != null && ` (version ${p.appliedVersionNo})`}
      </div>
      {segs ? <DiffView segments={segs} /> : p.proposedHtml && <div style={{ marginTop: 4, background: 'var(--hf-success-soft-strong)', padding: '6px 8px', borderRadius: 6 }}><Html html={p.proposedHtml} /></div>}
      {p.rationale && <div style={{ marginTop: 4, color: 'var(--hf-text-muted)' }}>{theirs ? 'Their reason' : 'Your reason'}: {p.rationale}</div>}
      {p.decisionNote && <div style={{ marginTop: 4, color: 'var(--hf-text-secondary)' }}>{p.decidedByCounterparty ? 'Your answer' : 'Reply from the sender'}: {p.decisionNote}</div>}
      {p.status === 'SUPERSEDED' && p.decisionNote == null && <div style={{ marginTop: 4, color: 'var(--hf-text-muted)' }}>The wording changed after this was proposed.</div>}
      {a.canAct && !theirs && isLive(p) && <button style={{ ...btn, marginTop: 6 }} disabled={a.busy} onClick={a.onWithdraw}>Withdraw</button>}
      {answerable && mode == null && (
        <div style={{ display: 'flex', gap: 6, marginTop: 6, flexWrap: 'wrap' }}>
          <button style={primary} disabled={a.busy} onClick={a.onAccept}>Accept</button>
          {counterable && <button style={btn} disabled={a.busy} onClick={() => { setText(p.proposedHtml ? blockText(p.proposedHtml) : ''); setMode('COUNTER') }}>Suggest different wording</button>}
          <button style={btn} disabled={a.busy} onClick={() => setMode('REJECT')}>Reject</button>
        </div>
      )}
      {answerable && mode === 'REJECT' && (
        <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
          <input aria-label="Reason for rejecting" style={input} placeholder="Why? The sender will see this" value={reason} onChange={e => setReason(e.target.value)} />
          <button style={primary} disabled={a.busy || !reason.trim()} onClick={() => { a.onReject(reason.trim()); setMode(null) }}>Reject</button>
          <button style={btn} onClick={() => setMode(null)}>Cancel</button>
        </div>
      )}
      {answerable && mode === 'COUNTER' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 6 }}>
          <textarea aria-label="Your wording" style={{ ...input, minHeight: 80 }} value={text} onChange={e => setText(e.target.value)} />
          <input aria-label="Why" style={input} placeholder="Why? (optional)" value={reason} onChange={e => setReason(e.target.value)} />
          <div style={{ display: 'flex', gap: 6 }}>
            <button style={primary} disabled={a.busy || !text.trim()} onClick={() => { a.onCounter(buildReplacement(p.originalHtml ?? null, text), reason.trim()); setMode(null) }}>Send</button>
            <button style={btn} onClick={() => setMode(null)}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}

function Workspace({ token, session, onSessionLost, onInvalid }: { token: string; session: string; onSessionLost: () => void; onInvalid: () => void }) {
  const qc = useQueryClient()
  const headers = { [SESSION_HEADER]: session }
  const [draft, setDraft] = useState<Draft | null>(null)
  const [text, setText] = useState('')
  const [why, setWhy] = useState('')
  const [reply, setReply] = useState<string | null>(null)
  const [commentText, setCommentText] = useState('')
  const [error, setError] = useState('')

  const view = useQuery<CounterpartyNegotiation>({
    queryKey: ['neg-view', token], retry: false,
    queryFn: async () => {
      try { return unwrapOne<CounterpartyNegotiation>(await publicApi.get(`/api/v1/negotiate/${token}/negotiation`, { headers })) }
      catch (e) { const s = httpStatus(e); if (s === 401) onSessionLost(); else if (s === 404) onInvalid(); throw e }
    },
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['neg-view', token] }); setError('') }
  const fail = (e: unknown, fallback: string) => {
    const s = httpStatus(e)
    if (s === 401) onSessionLost(); else if (s === 404) onInvalid()
    else setError(s === 429 ? 'Too many requests. Please wait a moment and try again.' : apiError(e, fallback))
  }
  const close = () => { setDraft(null); setText(''); setWhy(''); setReply(null); setCommentText('') }

  const propose = useMutation({
    mutationFn: async (body: { kind: ProposalKind; blockIndex: number; originalHtml: string | null; proposedHtml: string | null; rationale: string | null }) =>
      publicApi.post(`/api/v1/negotiate/${token}/proposals`, body, { headers }),
    onSuccess: () => { close(); refresh() },
    onError: (e: unknown) => fail(e, 'Your proposal could not be sent'),
  })
  const withdraw = useMutation({
    mutationFn: async (id: string) => publicApi.post(`/api/v1/negotiate/${token}/proposals/${id}/withdraw`, null, { headers }),
    onSuccess: refresh, onError: (e: unknown) => fail(e, 'The proposal could not be withdrawn'),
  })
  const answer = useMutation({
    mutationFn: async (v: { id: string; action: 'accept' | 'reject' | 'counter'; body: Record<string, unknown> | null }) =>
      publicApi.post(`/api/v1/negotiate/${token}/proposals/${v.id}/${v.action}`, v.body, { headers }),
    onSuccess: refresh, onError: (e: unknown) => fail(e, 'Your answer could not be sent'),
  })
  const comment = useMutation({
    mutationFn: async (body: { body: string; blockIndex: number | null; blockExcerpt: string | null; proposalId: string | null; parentId: string | null }) =>
      publicApi.post(`/api/v1/negotiate/${token}/comments`, body, { headers }),
    onSuccess: () => { close(); refresh() }, onError: (e: unknown) => fail(e, 'Your comment could not be posted'),
  })

  if (view.isLoading) return <Shell><Notice tone="info">Loading…</Notice></Shell>
  if (!view.data) return <Shell><Notice tone="error">{error || 'The negotiation could not be loaded.'}</Notice></Shell>
  const v = view.data
  const can = v.canPropose
  const mine = (i: number) => v.proposals.filter(p => p.kind !== 'ADD' && norm(p.originalHtml) === norm(v.blocks[i]) && p.status !== 'WITHDRAWN')
  const added = (i: number) => v.proposals.filter(p => p.kind === 'ADD' && (i < 0 ? !p.originalHtml : norm(p.originalHtml) === norm(v.blocks[i])) && p.status !== 'WITHDRAWN')

  const submit = () => {
    if (!draft) return
    const block = draft.blockIndex >= 0 ? v.blocks[draft.blockIndex] : null
    if (draft.mode === 'COMMENT') {
      if (!text.trim()) return
      comment.mutate({ body: text.trim(), blockIndex: draft.blockIndex >= 0 ? draft.blockIndex : null, blockExcerpt: block ? excerpt(block, 200) : null, proposalId: null, parentId: null })
      return
    }
    if (draft.mode !== 'DELETE' && !text.trim()) return
    propose.mutate({
      kind: draft.mode, blockIndex: draft.blockIndex, originalHtml: block,
      proposedHtml: draft.mode === 'DELETE' ? null : buildReplacement(draft.mode === 'REPLACE' ? block : null, text), rationale: why.trim() || null,
    })
  }
  const startDraft = (blockIndex: number, mode: Mode) => {
    setDraft({ blockIndex, mode }); setWhy(''); setReply(null)
    setText(mode === 'REPLACE' && v.blocks[blockIndex] ? blockText(v.blocks[blockIndex]) : '')
  }

  // A plain function, not a component: a component defined here would be remounted on every keystroke and lose focus.
  const renderForm = (blockIndex: number) => draft && draft.blockIndex === blockIndex ? (
    <div style={{ margin: '8px 0', padding: 10, border: '1px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface-muted)', display: 'flex', flexDirection: 'column', gap: 6 }}>
      <strong style={{ fontSize: 12 }}>{draft.mode === 'REPLACE' ? 'Suggest new wording' : draft.mode === 'DELETE' ? 'Suggest removing this paragraph' : draft.mode === 'ADD' ? 'Suggest a new paragraph here' : 'Comment on this paragraph'}</strong>
      {draft.mode !== 'DELETE' && <textarea aria-label={draft.mode === 'COMMENT' ? 'Your comment' : 'Proposed wording'} style={{ ...input, minHeight: 90 }} value={text} onChange={e => setText(e.target.value)} />}
      {draft.mode !== 'COMMENT' && <input aria-label="Why" style={input} placeholder="Why? (optional, helps the sender decide)" value={why} onChange={e => setWhy(e.target.value)} />}
      <div style={{ display: 'flex', gap: 8 }}>
        <button style={primary} disabled={propose.isPending || comment.isPending} onClick={submit}>{draft.mode === 'COMMENT' ? 'Post comment' : 'Send proposal'}</button>
        <button style={btn} onClick={close}>Cancel</button>
      </div>
    </div>
  ) : null

  const noteActions = (p: NegProposal): NoteActions => ({
    busy: withdraw.isPending || answer.isPending, canAct: can,
    onWithdraw: () => withdraw.mutate(p.id),
    onAccept: () => answer.mutate({ id: p.id, action: 'accept', body: {} }),
    onReject: r => answer.mutate({ id: p.id, action: 'reject', body: { reason: r } }),
    onCounter: (html, r) => answer.mutate({ id: p.id, action: 'counter', body: { proposedHtml: html, rationale: r || null } }),
  })
  const threads = v.comments
  const label = (c: NegComment) => c.authorType === 'STAFF' ? 'Sender' : 'You'

  return (
    <Shell>
      <div style={card}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
          <h1 style={{ margin: 0, fontSize: 20, color: 'var(--hf-text)' }}>Review and propose changes</h1>
          <span data-testid="neg-status" style={{ fontSize: 12, fontWeight: 700, color: 'var(--hf-primary-text)' }}>{COUNTERPARTY_STATUS_LABEL[v.status]}</span>
        </div>
        <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 4 }}>{deadlineText(v.responseDeadline)} · Version {v.currentVersionNo}</div>
        <p style={{ fontSize: 13, color: 'var(--hf-text-secondary)', lineHeight: 1.6, marginBottom: 0 }}>
          Your proposals are requests. The contract changes only if the sender accepts them. You will see their decision here.
        </p>
        {!can && <div style={{ marginTop: 8 }}><Notice tone="info">{v.status === 'OPEN' || v.status === 'STAFF_REVIEW' || v.status === 'CHANGES_REQUIRED'
          ? 'The deadline for responding has passed. Ask the sender to extend it.' : 'This negotiation is no longer taking changes.'}</Notice></div>}
        {error && <div style={{ marginTop: 8 }}><Notice tone="error">{error}</Notice></div>}
      </div>

      <div style={card}>
        {can && <div style={{ marginBottom: 8 }}><button style={btn} onClick={() => startDraft(-1, 'ADD')}>Add a paragraph at the start</button>{renderForm(-1)}</div>}
        {added(-1).map(p => <ProposalNote key={p.id} p={p} a={noteActions(p)} />)}
        {v.blocks.map((b, i) => (
          <div key={i} data-testid={`block-${i}`} style={{ padding: '8px 0', borderTop: i ? '1px solid var(--hf-border)' : undefined }}>
            <div style={{ fontSize: 14, lineHeight: 1.7, color: 'var(--hf-text)' }} dangerouslySetInnerHTML={{ __html: sanitizeHtml(b) }} />
            {can && (
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 4 }}>
                {canEditAsText(b) && <button style={btn} onClick={() => startDraft(i, 'REPLACE')}>Suggest edit</button>}
                <button style={btn} onClick={() => startDraft(i, 'DELETE')}>Suggest deletion</button>
                <button style={btn} onClick={() => startDraft(i, 'ADD')}>Add paragraph after</button>
                <button style={btn} onClick={() => startDraft(i, 'COMMENT')}>Comment</button>
              </div>
            )}
            {renderForm(i)}
            {mine(i).map(p => <ProposalNote key={p.id} p={p} a={noteActions(p)} />)}
            {added(i).map(p => <ProposalNote key={p.id} p={p} a={noteActions(p)} />)}
          </div>
        ))}
      </div>

      <div style={card}>
        <h2 style={{ margin: '0 0 8px', fontSize: 15, color: 'var(--hf-text)' }}>Comments</h2>
        {threads.length === 0 && <div style={{ fontSize: 13, color: 'var(--hf-text-muted)' }}>No comments yet.</div>}
        {threads.map(c => (
          <div key={c.id} style={{ padding: '6px 0', borderTop: '1px solid var(--hf-border)', marginLeft: c.parentId ? 16 : 0 }}>
            <div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{label(c)} · {new Date(c.createdAt).toLocaleString('en-ZA')}{c.blockExcerpt ? ` · on “${excerpt(c.blockExcerpt, 60)}”` : ''}</div>
            <div style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{c.body}</div>
            {can && c.authorType === 'STAFF' && <button style={{ ...btn, marginTop: 4 }} onClick={() => { setReply(c.id); setCommentText('') }}>Reply</button>}
            {reply === c.id && (
              <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                <input aria-label="Your reply" style={input} value={commentText} onChange={e => setCommentText(e.target.value)} />
                <button style={primary} disabled={!commentText.trim() || comment.isPending}
                  onClick={() => comment.mutate({ body: commentText.trim(), blockIndex: c.blockIndex ?? null, blockExcerpt: c.blockExcerpt ?? null, proposalId: c.proposalId ?? null, parentId: c.id })}>Send</button>
              </div>
            )}
          </div>
        ))}
        {can && reply == null && (
          <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
            <input aria-label="New comment" style={input} placeholder="Ask a question or add a general comment" value={commentText} onChange={e => setCommentText(e.target.value)} />
            <button style={primary} disabled={!commentText.trim() || comment.isPending}
              onClick={() => comment.mutate({ body: commentText.trim(), blockIndex: null, blockExcerpt: null, proposalId: null, parentId: null })}>Post</button>
          </div>
        )}
      </div>
    </Shell>
  )
}

// ── page ─────────────────────────────────────────────────────────────────────

export default function NegotiationPage() {
  const { token = '' } = useParams<{ token: string }>()
  const [session, setSession] = useState<string | null>(() => loadSession(token))
  const [invalid, setInvalid] = useState(false)
  const info = useQuery<LinkInfo>({
    queryKey: ['neg-info', token], retry: false, enabled: !!token,
    queryFn: async () => unwrapOne<LinkInfo>(await publicApi.get(`/api/v1/negotiate/${token}`)),
  })

  const lost = () => { saveSession(token, null); setSession(null) }
  const bad = invalid || (info.isError && httpStatus(info.error) === 404)
  if (bad) return <Shell><div style={card}><h1 style={{ margin: 0, fontSize: 18 }}>This link is not valid</h1><p style={{ fontSize: 13, color: 'var(--hf-text-secondary)' }}>It may have expired, been replaced by a newer link, or the negotiation may have ended. Ask the sender for a new link.</p></div></Shell>
  if (info.isError) return <Shell><Notice tone="error">{httpStatus(info.error) === 429 ? 'Too many requests. Please wait a moment and refresh.' : 'The page could not be loaded. Please try again.'}</Notice></Shell>
  if (!info.data) return <Shell><Notice tone="info">Loading…</Notice></Shell>
  if (!session) return <CodeStep token={token} info={info.data} onVerified={setSession} />
  return <Workspace token={token} session={session} onSessionLost={lost} onInvalid={() => setInvalid(true)} />
}
