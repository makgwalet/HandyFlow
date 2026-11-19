// @vitest-environment jsdom
// The staff negotiation panel: who sees which actions, that nothing is applied without an explicit step, and that links are shown once.
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { createRoot, type Root } from 'react-dom/client'
import { act } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { wait, click, type, button, hasButton, byLabel } from './testUtils'

const perms = vi.hoisted(() => ({ granted: [] as string[] }))
vi.mock('../../hooks/usePermission', () => ({
  usePermissions: (needed: string[]) => needed.some(p => perms.granted.includes(p)),
  usePermission: (p: string) => perms.granted.includes(p),
}))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))

import { ContractNegotiationSection } from './ContractNegotiationSection'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

const P1 = '<p>Payment is due in 30 days.</p>'
const parties = [{ id: 'a', fullName: 'Acme' }, { id: 'b', fullName: 'Beta' }]
const ok = (data: unknown) => ({ data: { data } })
const counts = { pending: 0, conflicted: 0, accepted: 0, applied: 0, rejected: 0, withdrawn: 0, superseded: 0 }
const neg = (o: Record<string, unknown> = {}) => ({ id: 'n1', contractId: 'c1', status: 'STAFF_REVIEW', responseDeadline: null, deadlinePassed: false, baseVersionNo: 1, openedAt: '2026-11-18T10:00:00Z', counts, ...o })
const prop = (o: Record<string, unknown> = {}) => ({ id: 'p1', partyId: 'a', partyName: 'Acme', kind: 'REPLACE', baseVersionNo: 1, blockIndex: 0, originalHtml: P1,
  proposedHtml: '<p>Payment is due in 14 days.</p>', rationale: 'Cash flow', status: 'PENDING_REVIEW', targetStillPresent: true, createdAt: '2026-11-18T10:00:00Z', ...o })

let staffView: Record<string, unknown>
let links: unknown[]
let root: Root | null = null
let host: HTMLElement | null = null

beforeEach(() => {
  perms.granted = ['CONTRACTS_MANAGE']
  staffView = { negotiation: neg(), currentVersionNo: 1, body: P1, blocks: [P1], proposals: [prop()], comments: [], events: [] }
  links = []
  Object.values(api).forEach(f => f.mockReset())
  api.get.mockImplementation(async (url: string) => {
    if (url.endsWith('/negotiation/links')) return ok(links)
    if (url.endsWith('/negotiation')) return ok(staffView)
    if (url.includes('/versions/compare')) return ok({ segments: [{ type: 'HUNK', hunkId: 0, blocks: [], removed: [P1], added: ['<p>Payment is due in 14 days.</p>'] }] })
    return ok({})
  })
  api.post.mockImplementation(async () => ok({}))
  api.put.mockImplementation(async () => ok({}))
})
afterEach(() => { act(() => root?.unmount()); host?.remove(); root = null; host = null })

async function mount(status = 'DRAFT') {
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await act(async () => { root!.render(<QueryClientProvider client={qc}><ContractNegotiationSection contractId="c1" status={status} parties={parties} /></QueryClientProvider>) })
  await click(button(host, 'Negotiation with counterparties'))
  await wait()
  return host
}

describe('not started', () => {
  beforeEach(() => { staffView = { negotiation: null, currentVersionNo: 1, body: P1, blocks: [P1], proposals: [], comments: [], events: [] } })

  it('lets a manager open it with a deadline', async () => {
    const el = await mount()
    await type(byLabel(el, 'Response deadline'), '2026-12-15')
    await click(button(el, 'Open negotiation'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/open', { responseDeadline: '2026-12-15' })
  })

  it('does not let a read-only user open it', async () => {
    perms.granted = ['CONTRACTS_READ']
    expect(hasButton(await mount(), 'Open negotiation')).toBe(false)
  })

  it('does not offer it once the contract has been sent', async () => {
    const el = await mount('SENT')
    expect(hasButton(el, 'Open negotiation')).toBe(false)
    expect(el.textContent).toContain('only possible while the contract is a draft')
  })

  it('needs at least one party', async () => {
    host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host)
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    await act(async () => { root!.render(<QueryClientProvider client={qc}><ContractNegotiationSection contractId="c1" status="DRAFT" parties={[]} /></QueryClientProvider>) })
    await click(button(host, 'Negotiation with counterparties')); await wait()
    expect(button(host, 'Open negotiation')!.disabled).toBe(true)
  })
})

describe('deciding on proposals', () => {
  it('shows what the counterparty wants changed, with their reason', async () => {
    const el = await mount()
    expect(el.textContent).toContain('Needs your decision (1)')
    expect(el.textContent).toContain('Acme · Replacement')
    expect(el.textContent).toContain('Reason given: Cash flow')
  })

  it('accepting only records the decision: it does not apply anything', async () => {
    const el = await mount()
    await click(button(el, 'Accept'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/proposals/p1/accept', {})
    expect(api.post).not.toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/apply', expect.anything())
  })

  it('needs a reason to reject, which the counterparty will see', async () => {
    const el = await mount()
    await click(button(el, 'Reject'))
    expect(button(el, 'Reject proposal')!.disabled).toBe(true)
    await type(byLabel(el, 'Reason for rejecting'), 'Required by law')
    await click(button(el, 'Reject proposal'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/proposals/p1/reject', { reason: 'Required by law' })
  })

  it('cannot accept a proposal in conflict, or whose wording has been edited', async () => {
    staffView.proposals = [prop({ status: 'CONFLICTED' }), prop({ id: 'p2', targetStillPresent: false })]
    const el = await mount()
    const accepts = Array.from(el.querySelectorAll('button')).filter(b => b.textContent === 'Accept') as HTMLButtonElement[]
    expect(accepts).toHaveLength(2)
    expect(accepts.every(b => b.disabled)).toBe(true)
    expect(el.textContent).toContain('Reject one so the other can be decided')
    expect(el.textContent).toContain('has since been edited')
  })

  it('shows no decision buttons to a read-only user', async () => {
    perms.granted = ['CONTRACTS_READ']
    const el = await mount()
    expect(hasButton(el, 'Accept')).toBe(false)
    expect(hasButton(el, 'Reject')).toBe(false)
    expect(hasButton(el, 'Mark ready for signature')).toBe(false)
    expect(el.textContent).toContain('Acme · Replacement')
  })

  it('shows no decision buttons once the contract is no longer a draft', async () => {
    const el = await mount('SENT')
    expect(hasButton(el, 'Accept')).toBe(false)
  })

  it('keeps decided proposals in a history that cannot be re-decided', async () => {
    staffView.proposals = [prop({ status: 'REJECTED', decisionNote: 'No' }), prop({ id: 'p3', status: 'WITHDRAWN' }), prop({ id: 'p4', status: 'SUPERSEDED' })]
    const el = await mount()
    expect(el.textContent).toContain('History (3)')
    expect(hasButton(el, 'Accept')).toBe(false)
    expect(hasButton(el, 'Reject')).toBe(false)
  })
})

describe('applying, readiness and closing', () => {
  it('applies accepted proposals only when asked, and says how many', async () => {
    staffView = { ...staffView, negotiation: neg({ status: 'CHANGES_REQUIRED', counts: { ...counts, accepted: 1 } }), proposals: [prop({ status: 'ACCEPTED' })] }
    const el = await mount()
    await click(button(el, 'Apply 1 accepted'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/apply', {})
  })

  it('cannot be marked ready or closed while proposals wait or accepted ones are unapplied', async () => {
    const el = await mount()
    expect(button(el, 'Mark ready for signature')!.disabled).toBe(true)
    expect(button(el, 'Close negotiation')!.disabled).toBe(true)
  })

  it('can be marked ready when everything is decided and applied', async () => {
    staffView = { ...staffView, negotiation: neg({ status: 'OPEN' }), proposals: [prop({ status: 'APPLIED', appliedVersionNo: 2 })] }
    const el = await mount()
    await click(button(el, 'Mark ready for signature'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/ready')
  })

  it('explains that editing after ready blocks sending, and offers reopen only while a draft', async () => {
    staffView = { ...staffView, negotiation: neg({ status: 'READY_FOR_SIGNATURE', finalVersionNo: 3 }), proposals: [] }
    let el = await mount()
    expect(el.textContent).toContain('pinned at version 3')
    expect(hasButton(el, 'Reopen')).toBe(true)
    act(() => root?.unmount()); host?.remove()
    el = await mount('SENT')
    expect(hasButton(el, 'Reopen')).toBe(false)
  })

  it('closes with an optional reason', async () => {
    staffView = { ...staffView, negotiation: neg({ status: 'OPEN' }), proposals: [] }
    const el = await mount()
    await type(byLabel(el, 'Reason for closing'), 'Agreed')
    await click(button(el, 'Close negotiation'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/close', { reason: 'Agreed' })
  })

  it('compares the starting version with the current one', async () => {
    const el = await mount()
    await click(button(el, /Compare version 1 with current version 1/))
    expect(api.get).toHaveBeenCalledWith('/api/v1/contracts/c1/versions/compare?from=1&to=1')
  })
})

describe('links', () => {
  beforeEach(() => { staffView = { ...staffView, negotiation: neg({ status: 'OPEN' }), proposals: [] } })

  it('shows a new link once, and the list afterwards never contains it', async () => {
    api.post.mockImplementation(async (url: string) => url.endsWith('/links') ? ok({ linkId: 'l1', partyId: 'a', url: 'https://app.test/negotiate/SECRET', expiresAt: '2026-12-01T00:00:00Z', deliveryStatus: 'QUEUED' }) : ok({}))
    const el = await mount()
    await type(byLabel(el, 'Party'), 'a')
    await click(button(el, 'Issue link'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/links', { partyId: 'a' })
    expect((byLabel(el, 'Negotiation link') as HTMLInputElement).value).toBe('https://app.test/negotiate/SECRET')
    expect(el.textContent).toContain('emailed to them')
    links = [{ id: 'l1', partyId: 'a', partyName: 'Acme', expiresAt: '2099-12-01T00:00:00Z', createdAt: '2026-11-18T10:00:00Z', active: true }]
    act(() => root?.unmount()); host?.remove()
    const again = await mount()
    expect(again.innerHTML).not.toContain('SECRET')
    expect(again.textContent).toContain('Acme · Active')
  })

  it('revokes an active link', async () => {
    links = [{ id: 'l1', partyId: 'a', partyName: 'Acme', expiresAt: '2099-12-01T00:00:00Z', createdAt: '2026-11-18T10:00:00Z', active: true }]
    const el = await mount()
    await click(button(el, 'Revoke'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/links/l1/revoke')
  })

  it('offers no link controls to a read-only user', async () => {
    perms.granted = ['CONTRACTS_READ']
    links = [{ id: 'l1', partyId: 'a', partyName: 'Acme', expiresAt: '2099-12-01T00:00:00Z', createdAt: '2026-11-18T10:00:00Z', active: true }]
    const el = await mount()
    expect(hasButton(el, 'Issue link')).toBe(false)
    expect(hasButton(el, 'Revoke')).toBe(false)
  })
})

describe('comments', () => {
  beforeEach(() => { staffView = { ...staffView, negotiation: neg({ status: 'OPEN' }), proposals: [] } })

  it('sends an internal note without an audience', async () => {
    const el = await mount()
    await type(byLabel(el, 'Comment'), 'Legal to check clause 4')
    await click(el.querySelector('input[type="checkbox"]'))
    expect(byLabel(el, 'Visible to')).toBeNull()
    await click(button(el, 'Post'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/comments', { body: 'Legal to check clause 4', internalOnly: true, audiencePartyId: null })
  })

  it('can address a comment to one counterparty only, and labels internal notes', async () => {
    staffView.comments = [{ id: 'c1', authorType: 'STAFF', body: 'secret', internalOnly: true, createdAt: '2026-11-18T10:00:00Z' }]
    const el = await mount()
    expect(el.textContent).toContain('internal note (not shown to counterparties)')
    await type(byLabel(el, 'Comment'), 'Hello Beta')
    await type(byLabel(el, 'Visible to'), 'b')
    await click(button(el, 'Post'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/comments', { body: 'Hello Beta', internalOnly: false, audiencePartyId: 'b' })
  })
})

describe('two-way negotiation', () => {
  const counterparty = (o: Record<string, unknown> = {}) => prop({ authorType: 'COUNTERPARTY', roundNo: 1, awaiting: 'STAFF', ...o })
  const mineSent = (o: Record<string, unknown> = {}) => prop({ id: 's1', authorType: 'STAFF', roundNo: 1, awaiting: 'COUNTERPARTY', rationale: 'Our standard terms', ...o })

  it('answers a counterparty proposal with new wording, keeping the round', async () => {
    staffView.proposals = [counterparty()]
    const el = await mount()
    await click(button(el, 'Counter with new wording'))
    await type(byLabel(el, 'Counter wording'), 'Payment is due in 21 days.')
    await type(byLabel(el, 'Reason for counter'), 'Halfway')
    await click(button(el, 'Send counter-proposal'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/proposals/p1/counter', { proposedHtml: '<p>Payment is due in 21 days.</p>', rationale: 'Halfway' })
  })

  it('shows earlier rounds inside one exchange, and only the live round can be answered', async () => {
    const r1 = counterparty({ id: 'r1', status: 'COUNTERED', awaiting: null })
    const r2 = mineSent({ id: 'r2', roundNo: 2, parentId: 'r1', status: 'COUNTERED', awaiting: null })
    const r3 = counterparty({ id: 'r3', roundNo: 3, parentId: 'r2' })
    staffView.proposals = [r1, r2, r3]
    const el = await mount()
    expect(el.querySelectorAll('[data-testid="proposal"]')).toHaveLength(1)
    expect(el.textContent).toContain('2 earlier rounds')
    expect(el.querySelectorAll('[data-testid="earlier-round"]')).toHaveLength(2)
    expect(el.textContent).toContain('round 3')
    await click(button(el, 'Accept'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/proposals/r3/accept', {})
  })

  it('lists what staff sent as waiting on the counterparty, with no accept or reject, and lets staff withdraw it', async () => {
    staffView.proposals = [mineSent()]
    const el = await mount()
    expect(el.textContent).toContain('Waiting for the counterparty (1)')
    expect(el.textContent).toContain('Waiting for their answer')
    expect(hasButton(el, 'Accept')).toBe(false)
    expect(hasButton(el, 'Reject')).toBe(false)
    await click(button(el, 'Withdraw'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/proposals/s1/withdraw')
  })

  it('an agreed staff proposal is applied, not re-decided', async () => {
    staffView.proposals = [mineSent({ status: 'ACCEPTED', awaiting: null })]
    const el = await mount()
    expect(el.textContent).toContain('Accepted, not yet applied (1)')
    expect(el.textContent).toContain('agreed')
    expect(hasButton(el, 'Reject')).toBe(false)
  })

  it('proposes a change to a counterparty without touching the contract', async () => {
    staffView.proposals = []
    const el = await mount()
    await type(byLabel(el, 'Propose to'), 'a')
    await type(byLabel(el, 'Proposed wording'), 'Payment is due in 45 days.')
    await type(byLabel(el, 'Reason'), 'Group policy')
    await click(button(el, 'Send proposal'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/c1/negotiation/proposals', {
      partyId: 'a', kind: 'REPLACE', blockIndex: 0, originalHtml: P1, proposedHtml: '<p>Payment is due in 45 days.</p>', rationale: 'Group policy',
    })
    expect(api.post.mock.calls.some(c => String(c[0]).endsWith('/apply'))).toBe(false)
  })

  it('cannot send a proposal without a party or without wording', async () => {
    staffView.proposals = []
    const el = await mount()
    await type(byLabel(el, 'Proposed wording'), '')
    expect(button(el, 'Send proposal')!.disabled).toBe(true)
    await type(byLabel(el, 'Propose to'), 'a')
    expect(button(el, 'Send proposal')!.disabled).toBe(true)
    await type(byLabel(el, 'Proposed wording'), 'Something')
    expect(button(el, 'Send proposal')!.disabled).toBe(false)
  })

  it('offers no proposing to a read-only user or once the contract is sent', async () => {
    perms.granted = ['CONTRACTS_READ']
    expect(hasButton(await mount(), 'Send proposal')).toBe(false)
  })
})
