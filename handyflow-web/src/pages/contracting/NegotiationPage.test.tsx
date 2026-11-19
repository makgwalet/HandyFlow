// @vitest-environment jsdom
// The public negotiation page: identity steps, session handling, and that proposals are sent exactly as quoted from the server's blocks.
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createRoot, type Root } from 'react-dom/client'
import { act } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { wait, click, type, button, hasButton, byLabel } from './testUtils'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('./negotiation', async orig => ({ ...(await orig<typeof import('./negotiation')>()), publicApi: api }))

import NegotiationPage from './NegotiationPage'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

const TOKEN = 'tok-0123456789abcdefghijklmnopqrstuvwxyz'
const P1 = '<p>Payment is due in 30 days.</p>'
const P2 = '<p>Either party may <strong>terminate</strong> on 60 days notice.</p>'

const ok = (data: unknown) => ({ data: { data } })
const fail = (status: number, message = 'nope') => Object.assign(new Error(message), { response: { status, data: { message } } })

const info = { contractTitle: 'Service agreement', channel: 'SMS', maskedDestination: '******4567', expiresAt: '2026-12-01T00:00:00Z' }
const proposal = (o: Record<string, unknown>) => ({ id: 'p1', partyId: 'a', partyName: 'Acme', kind: 'REPLACE', baseVersionNo: 1, blockIndex: 0, originalHtml: P1,
  proposedHtml: '<p>Payment is due in 14 days.</p>', rationale: 'Cash flow', status: 'PENDING_REVIEW', targetStillPresent: true, createdAt: '2026-11-18T10:00:00Z', ...o })
const view = (o: Record<string, unknown> = {}) => ({ status: 'OPEN', responseDeadline: null, canPropose: true, currentVersionNo: 1, body: P1 + P2, blocks: [P1, P2], proposals: [], comments: [], ...o })

let current = view()
let root: Root | null = null
let host: HTMLElement | null = null

beforeEach(() => {
  window.sessionStorage.clear()
  current = view()
  api.get.mockReset(); api.post.mockReset()
  api.get.mockImplementation(async (url: string) => {
    if (url.endsWith('/negotiation')) return ok(current)
    return ok(info)
  })
  api.post.mockImplementation(async (url: string) => {
    if (url.endsWith('/code')) return ok({ channel: 'SMS', maskedDestination: '******4567' })
    if (url.endsWith('/verify')) return ok({ sessionToken: 'session-abc', expiresAt: '2026-11-19T10:00:00Z' })
    return ok({})
  })
})
afterEach(() => { act(() => root?.unmount()); host?.remove(); root = null; host = null })

async function mount(withSession = false) {
  if (withSession) window.sessionStorage.setItem(`hf-neg-session:${TOKEN.slice(0, 12)}`, 'session-abc')
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await act(async () => {
    root!.render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[`/negotiate/${TOKEN}`]}>
      <Routes><Route path="/negotiate/:token" element={<NegotiationPage />} /></Routes></MemoryRouter></QueryClientProvider>)
  })
  await wait(); await wait()
  return host
}

describe('link and identity', () => {
  it('shows one neutral message for any invalid link', async () => {
    api.get.mockRejectedValue(fail(404))
    const el = await mount()
    expect(el.textContent).toContain('This link is not valid')
    expect(hasButton(el, /Send a code/)).toBe(false)
  })

  it('asks for a code first and shows only a masked destination', async () => {
    const el = await mount()
    expect(el.textContent).toContain('Service agreement')
    expect(hasButton(el, 'Send a code to ******4567')).toBe(true)
    expect(el.textContent).not.toContain('Payment is due')
    expect(api.get).not.toHaveBeenCalledWith(expect.stringContaining('/negotiation'), expect.anything())
  })

  it('sends the code, verifies it, then loads the wording with the session header', async () => {
    const el = await mount()
    await click(button(el, /Send a code/))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/code`)
    await type(el.querySelector('#neg-code'), '123456')
    await click(button(el, 'Continue'))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/verify`, { code: '123456' })
    await wait()
    expect(el.textContent).toContain('Payment is due in 30 days.')
    expect(api.get).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/negotiation`, { headers: { 'X-Negotiation-Session': 'session-abc' } })
  })

  it('stays on the code step after a wrong code', async () => {
    api.post.mockImplementation(async (url: string) => {
      if (url.endsWith('/code')) return ok({ channel: 'SMS', maskedDestination: '******4567' })
      throw fail(401)
    })
    const el = await mount()
    await click(button(el, /Send a code/))
    await type(el.querySelector('#neg-code'), '000000')
    await click(button(el, 'Continue'))
    expect(el.textContent).toContain('not correct or has expired')
    expect(el.textContent).not.toContain('Payment is due')
  })

  it('does not let a code shorter than six digits be submitted', async () => {
    const el = await mount()
    await click(button(el, /Send a code/))
    await type(el.querySelector('#neg-code'), '123')
    expect(button(el, 'Continue')!.disabled).toBe(true)
  })

  it('returns to the code step when the session has expired', async () => {
    api.get.mockImplementation(async (url: string) => { if (url.endsWith('/negotiation')) throw fail(401); return ok(info) })
    const el = await mount(true)
    expect(hasButton(el, /Send a code/)).toBe(true)
    expect(window.sessionStorage.getItem(`hf-neg-session:${TOKEN.slice(0, 12)}`)).toBeNull()
  })

  it('reuses a saved session so a refresh does not ask for another code', async () => {
    const el = await mount(true)
    expect(el.textContent).toContain('Payment is due in 30 days.')
    expect(hasButton(el, /Send a code/)).toBe(false)
  })

  it('shows the invalid-link message if the link is revoked while the page is open', async () => {
    api.get.mockImplementation(async (url: string) => { if (url.endsWith('/negotiation')) throw fail(404); return ok(info) })
    const el = await mount(true)
    expect(el.textContent).toContain('This link is not valid')
  })
})

describe('proposing changes', () => {
  it('sends a replacement quoting the server block exactly, and never changes the page text itself', async () => {
    const el = await mount(true)
    await click(button(el, 'Suggest edit'))
    const ta = el.querySelector('textarea[aria-label="Proposed wording"]') as HTMLTextAreaElement
    expect(ta.value).toBe('Payment is due in 30 days.')
    await type(ta, 'Payment is due in 14 days.')
    await type(byLabel(el, 'Why'), 'Cash flow')
    await click(button(el, 'Send proposal'))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/proposals`,
      { kind: 'REPLACE', blockIndex: 0, originalHtml: P1, proposedHtml: '<p>Payment is due in 14 days.</p>', rationale: 'Cash flow' },
      { headers: { 'X-Negotiation-Session': 'session-abc' } })
    expect(el.querySelector('[data-testid="block-0"]')!.textContent).toContain('30 days')
  })

  it('sends a deletion with no new wording', async () => {
    const el = await mount(true)
    await click(button(el, 'Suggest deletion'))
    await click(button(el, 'Send proposal'))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/proposals`, expect.objectContaining({ kind: 'DELETE', originalHtml: P1, proposedHtml: null }), expect.anything())
  })

  it('anchors an addition to the paragraph it follows, or to the start', async () => {
    const el = await mount(true)
    await click(button(el, 'Add paragraph after'))
    await type(el.querySelector('textarea'), 'New clause')
    await click(button(el, 'Send proposal'))
    expect(api.post).toHaveBeenLastCalledWith(`/api/v1/negotiate/${TOKEN}/proposals`, expect.objectContaining({ kind: 'ADD', blockIndex: 0, originalHtml: P1, proposedHtml: '<p>New clause</p>' }), expect.anything())
    await click(button(el, 'Add a paragraph at the start'))
    await type(el.querySelector('textarea'), 'Definitions')
    await click(button(el, 'Send proposal'))
    expect(api.post).toHaveBeenLastCalledWith(`/api/v1/negotiate/${TOKEN}/proposals`, expect.objectContaining({ kind: 'ADD', blockIndex: -1, originalHtml: null }), expect.anything())
  })

  it('does not send an empty replacement', async () => {
    const el = await mount(true)
    await click(button(el, 'Suggest edit'))
    await type(el.querySelector('textarea'), '   ')
    await click(button(el, 'Send proposal'))
    expect(api.post).not.toHaveBeenCalledWith(expect.stringContaining('/proposals'), expect.anything(), expect.anything())
  })

  it('offers no text edit for a paragraph with formatting, only deletion and additions', async () => {
    const el = await mount(true)
    const second = el.querySelector('[data-testid="block-1"]')!
    expect(Array.from(second.querySelectorAll('button')).map(b => b.textContent)).toEqual(['Suggest deletion', 'Add paragraph after', 'Comment'])
    expect(Array.from(el.querySelector('[data-testid="block-0"]')!.querySelectorAll('button')).map(b => b.textContent)).toContain('Suggest edit')
  })

  it('shows the server message when a proposal is refused and keeps the draft', async () => {
    api.post.mockImplementation(async (url: string) => { if (url.endsWith('/proposals')) throw fail(400, 'That wording is not in the current contract.'); return ok({}) })
    const el = await mount(true)
    await click(button(el, 'Suggest deletion'))
    await click(button(el, 'Send proposal'))
    expect(el.textContent).toContain('That wording is not in the current contract.')
    expect(hasButton(el, 'Send proposal')).toBe(true)
  })
})

describe('outcomes and withdrawing', () => {
  it('shows each proposal with its status, and the sender’s reason when rejected', async () => {
    current = view({ proposals: [proposal({ status: 'REJECTED', decisionNote: 'Required by law' })] })
    const el = await mount(true)
    expect(el.querySelector('[data-testid="proposal-status"]')!.textContent).toBe('Rejected')
    expect(el.textContent).toContain('Required by law')
    expect(hasButton(el, 'Withdraw')).toBe(false)
  })

  it('lets the author withdraw a live proposal', async () => {
    current = view({ proposals: [proposal({})] })
    const el = await mount(true)
    await click(button(el, 'Withdraw'))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/proposals/p1/withdraw`, null, expect.anything())
  })

  it('hides withdrawn proposals from the paragraph', async () => {
    current = view({ proposals: [proposal({ status: 'WITHDRAWN' })] })
    const el = await mount(true)
    expect(el.querySelector('[data-testid="proposal-status"]')).toBeNull()
  })

  it('offers no editing at all when the negotiation is not taking changes', async () => {
    current = view({ canPropose: false, status: 'READY_FOR_SIGNATURE', proposals: [proposal({ status: 'APPLIED', appliedVersionNo: 2 })] })
    const el = await mount(true)
    expect(hasButton(el, 'Suggest edit')).toBe(false)
    expect(hasButton(el, 'Suggest deletion')).toBe(false)
    expect(hasButton(el, 'Withdraw')).toBe(false)
    expect(el.textContent).toContain('no longer taking changes')
    expect(el.textContent).toContain('Applied to the contract')
  })
})

describe('answering the sender’s proposals', () => {
  const theirs = (o: Record<string, unknown> = {}) => proposal({ id: 's1', authorType: 'STAFF', roundNo: 1, awaiting: 'COUNTERPARTY', rationale: 'Our standard terms',
    proposedHtml: '<p>Payment is due in 45 days.</p>', ...o })

  it('shows what the sender proposed, marked as waiting for an answer', async () => {
    current = view({ proposals: [theirs()] })
    const el = await mount(true)
    expect(el.textContent).toContain('From the sender')
    expect(el.querySelector('[data-testid="proposal-status"]')!.textContent).toBe('Waiting for your answer')
    expect(el.textContent).toContain('Their reason: Our standard terms')
    expect(hasButton(el, 'Withdraw')).toBe(false)
  })

  it('accepts: only records the answer', async () => {
    current = view({ proposals: [theirs()] })
    const el = await mount(true)
    await click(button(el, 'Accept'))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/proposals/s1/accept`, {}, expect.anything())
  })

  it('rejecting needs a reason', async () => {
    current = view({ proposals: [theirs()] })
    const el = await mount(true)
    await click(button(el, 'Reject'))
    expect(button(el, 'Reject')!.disabled).toBe(true)
    await type(byLabel(el, 'Reason for rejecting'), 'Cannot agree to 45 days')
    await click(button(el, 'Reject'))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/proposals/s1/reject`, { reason: 'Cannot agree to 45 days' }, expect.anything())
  })

  it('answers with different wording', async () => {
    current = view({ proposals: [theirs()] })
    const el = await mount(true)
    await click(button(el, 'Suggest different wording'))
    expect((byLabel(el, 'Your wording') as HTMLTextAreaElement).value).toBe('Payment is due in 45 days.')
    await type(byLabel(el, 'Your wording'), 'Payment is due in 30 days.')
    await type(byLabel(el, 'Why'), 'Our cycle')
    await click(button(el, 'Send'))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/proposals/s1/counter`, { proposedHtml: '<p>Payment is due in 30 days.</p>', rationale: 'Our cycle' }, expect.anything())
  })

  it('offers no way to answer a deletion with wording, but can accept or reject it', async () => {
    current = view({ proposals: [theirs({ kind: 'DELETE', proposedHtml: null })] })
    const el = await mount(true)
    expect(hasButton(el, 'Accept')).toBe(true)
    expect(hasButton(el, 'Suggest different wording')).toBe(false)
  })

  it('cannot answer once the negotiation no longer takes changes, nor a proposal that is already answered', async () => {
    current = view({ canPropose: false, proposals: [theirs()] })
    expect(hasButton(await mount(true), 'Accept')).toBe(false)
  })

  it('shows an answered proposal as history and does not offer buttons', async () => {
    current = view({ proposals: [theirs({ status: 'COUNTERED', awaiting: null })] })
    const el = await mount(true)
    expect(el.querySelector('[data-testid="proposal-status"]')!.textContent).toBe('Answered with new wording')
    expect(hasButton(el, 'Accept')).toBe(false)
  })
})

describe('comments and safety', () => {
  it('lets the counterparty reply to a staff comment, quoting the parent', async () => {
    current = view({ comments: [{ id: 'c1', authorType: 'STAFF', body: 'Can you clarify?', internalOnly: false, createdAt: '2026-11-18T10:00:00Z', proposalId: null, blockIndex: 0, blockExcerpt: 'Payment' }] })
    const el = await mount(true)
    await click(button(el, 'Reply'))
    await type(byLabel(el, 'Your reply'), 'Yes, see my proposal')
    await click(button(el, 'Send'))
    expect(api.post).toHaveBeenCalledWith(`/api/v1/negotiate/${TOKEN}/comments`, expect.objectContaining({ body: 'Yes, see my proposal', parentId: 'c1' }), expect.anything())
  })

  it('removes event handlers and scripts from the contract text before showing it', async () => {
    current = view({ blocks: ['<p onclick="window.__hit=1">Hello</p><script>window.__hit=2</script>'] })
    const el = await mount(true)
    expect(el.querySelector('[onclick]')).toBeNull()
    expect(el.querySelector('script')).toBeNull()
  })

  it('never calls the staff API', async () => {
    await mount(true)
    const urls = [...api.get.mock.calls, ...api.post.mock.calls].map(c => String(c[0]))
    expect(urls.every(u => u.startsWith('/api/v1/negotiate/'))).toBe(true)
  })
})
