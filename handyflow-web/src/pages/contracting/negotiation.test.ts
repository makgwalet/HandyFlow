import { describe, it, expect } from 'vitest'
import {
  buildReplacement, canEditAsText, textBlockTag, excerpt, daysLeft, deadlineText, linkState, needsDecision, isLive, httpStatus, loadSession, saveSession,
  waitingOnCounterparty, threadOf, exchanges, staffProposalBody,
  type NegLink, type NegProposal,
} from './negotiation'

const prop = (status: NegProposal['status']): NegProposal => ({ id: 'p', partyId: 'a', kind: 'REPLACE', baseVersionNo: 1, status, targetStillPresent: true, createdAt: '2026-11-18T10:00:00Z' })

describe('buildReplacement', () => {
  it('keeps the tag of the block being replaced', () => {
    expect(buildReplacement('<h2>Payment</h2>', 'Fees')).toBe('<h2>Fees</h2>')
    expect(buildReplacement('<p class="x">Old</p>', 'New')).toBe('<p>New</p>')
  })
  it('turns blank lines into new paragraphs and single line breaks into <br>', () => {
    expect(buildReplacement('<p>a</p>', 'One\nstill one\n\nTwo')).toBe('<p>One<br>still one</p><p>Two</p>')
  })
  it('uses a paragraph for an addition', () => {
    expect(buildReplacement(null, 'New clause')).toBe('<p>New clause</p>')
  })
  it('escapes markup so typed text can never become tags', () => {
    expect(buildReplacement('<p>a</p>', '<script>alert(1)</script> & more')).toBe('<p>&lt;script&gt;alert(1)&lt;/script&gt; &amp; more</p>')
  })
  it('ignores empty paragraphs', () => {
    expect(buildReplacement('<p>a</p>', '\n\n  \n\nText\n\n\n')).toBe('<p>Text</p>')
  })
})

describe('block helpers', () => {
  it('recognises text blocks', () => {
    expect(textBlockTag('<p>x</p>')).toBe('p')
    expect(textBlockTag('<H3>x</H3>')).toBe('h3')
    expect(textBlockTag('<table><tr><td>x</td></tr></table>')).toBeNull()
    expect(textBlockTag('<ul><li>x</li></ul>')).toBeNull()
  })
  it('only offers text editing where no structure would be lost', () => {
    expect(canEditAsText('<p>Plain text</p>')).toBe(true)
    expect(canEditAsText('<p>Line<br>break</p>')).toBe(true)
    expect(canEditAsText('<p>Some <strong>bold</strong> text</p>')).toBe(false)
    expect(canEditAsText('<ul><li>x</li></ul>')).toBe(false)
  })
  it('shortens an excerpt', () => {
    expect(excerpt('<p>' + 'word '.repeat(40) + '</p>', 20).length).toBeLessThanOrEqual(20)
    expect(excerpt('<p>Short</p>')).toBe('Short')
  })
})

describe('deadlines', () => {
  const now = new Date(2026, 10, 18, 15, 0)
  it('counts whole days', () => {
    expect(daysLeft('2026-11-18', now)).toBe(0)
    expect(daysLeft('2026-11-21', now)).toBe(3)
    expect(daysLeft('2026-11-17', now)).toBe(-1)
    expect(daysLeft(null, now)).toBeNull()
  })
  it('words them for people', () => {
    expect(deadlineText(null, now)).toBe('No deadline')
    expect(deadlineText('2026-11-18', now)).toContain('today')
    expect(deadlineText('2026-11-19', now)).toContain('1 day left')
    expect(deadlineText('2026-11-25', now)).toContain('7 days left')
    expect(deadlineText('2026-11-10', now)).toContain('passed')
  })
})

describe('statuses', () => {
  it('separates what needs a decision from what is still live', () => {
    expect(needsDecision(prop('PENDING_REVIEW'))).toBe(true)
    expect(needsDecision(prop('CONFLICTED'))).toBe(true)
    expect(needsDecision(prop('ACCEPTED'))).toBe(false)
    expect(isLive(prop('ACCEPTED'))).toBe(true)
    for (const s of ['REJECTED', 'WITHDRAWN', 'SUPERSEDED', 'APPLIED'] as const) expect(isLive(prop(s))).toBe(false)
  })
  it('reads link state', () => {
    const now = new Date('2026-11-18T10:00:00Z')
    const l: NegLink = { id: 'l', partyId: 'a', expiresAt: '2026-11-20T00:00:00Z', createdAt: '2026-11-18T00:00:00Z', active: true }
    expect(linkState(l, now)).toBe('Active')
    expect(linkState({ ...l, expiresAt: '2026-11-17T00:00:00Z' }, now)).toBe('Expired')
    expect(linkState({ ...l, revokedAt: '2026-11-18T09:00:00Z' }, now)).toBe('Revoked')
  })
})

describe('session storage', () => {
  it('survives a missing or blocked storage without throwing', () => {
    expect(httpStatus({ response: { status: 401 } })).toBe(401)
    expect(httpStatus(new Error('x'))).toBeUndefined()
    expect(() => saveSession('abcdefghijklmnop', 's')).not.toThrow()
    expect(() => loadSession('abcdefghijklmnop')).not.toThrow()
  })
})

describe('two-way negotiation helpers', () => {
  const base = { partyId: 'a', kind: 'REPLACE', baseVersionNo: 1, status: 'PENDING_REVIEW', targetStillPresent: true, createdAt: '2026-11-18T10:00:00Z' } as const
  const r1 = { ...base, id: 'r1', authorType: 'COUNTERPARTY', roundNo: 1, status: 'COUNTERED', awaiting: null } as NegProposal
  const r2 = { ...base, id: 'r2', authorType: 'STAFF', roundNo: 2, parentId: 'r1', status: 'COUNTERED', awaiting: null } as NegProposal
  const r3 = { ...base, id: 'r3', authorType: 'COUNTERPARTY', roundNo: 3, parentId: 'r2', awaiting: 'STAFF' } as NegProposal

  it('staff decide only what is waiting on staff', () => {
    expect(needsDecision(r3)).toBe(true)
    expect(needsDecision({ ...r3, authorType: 'STAFF', awaiting: 'COUNTERPARTY' })).toBe(false)
    expect(waitingOnCounterparty({ ...r3, authorType: 'STAFF', awaiting: 'COUNTERPARTY' })).toBe(true)
    expect(needsDecision({ ...base, id: 'old' } as NegProposal)).toBe(true)
  })

  it('threads every round of an exchange, oldest first, and shows only the live end', () => {
    expect(threadOf([r1, r2, r3], r3).map(x => x.id)).toEqual(['r1', 'r2', 'r3'])
    const lone = { ...base, id: 'x' } as NegProposal
    const ex = exchanges([r1, r2, r3, lone])
    expect(ex.map(c => c.map(x => x.id))).toEqual([['r1', 'r2', 'r3'], ['x']])
  })

  it('survives a parent that is missing or a loop', () => {
    expect(threadOf([r3], r3).map(x => x.id)).toEqual(['r3'])
    const a = { ...base, id: 'a', parentId: 'b' } as NegProposal
    const b = { ...base, id: 'b', parentId: 'a' } as NegProposal
    expect(threadOf([a, b], a).length).toBeLessThanOrEqual(2)
  })

  it('builds the body for a change staff make', () => {
    const blocks = ['<p>One</p>', '<h2>Two</h2>']
    expect(staffProposalBody('REPLACE', blocks, 1, 'New two', ' because ')).toEqual({ kind: 'REPLACE', blockIndex: 1, originalHtml: '<h2>Two</h2>', proposedHtml: '<h2>New two</h2>', rationale: 'because' })
    expect(staffProposalBody('DELETE', blocks, 0, '', '')).toEqual({ kind: 'DELETE', blockIndex: 0, originalHtml: '<p>One</p>', proposedHtml: null, rationale: null })
    expect(staffProposalBody('ADD', blocks, -1, 'First', '')).toMatchObject({ blockIndex: -1, originalHtml: null, proposedHtml: '<p>First</p>' })
  })
})
