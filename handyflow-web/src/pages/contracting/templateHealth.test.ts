import { describe, it, expect } from 'vitest'
import { placeholdersIn, templateHealth, templateUnusableReason } from './templateHealth'

const ok = { required: false, state: 'NONE' as const }
const good = { bodyTemplate: '<p>Hi {{client_name}} {{ client_name }}</p>', variables: { client_name: 'text' }, legalStatus: 'APPROVED' }

describe('placeholdersIn', () => {
  it('lists distinct names and tolerates spaces', () => {
    expect(placeholdersIn('{{a}} {{ b }} {{a}}')).toEqual(['a', 'b'])
    expect(placeholdersIn(null)).toEqual([])
  })
})

describe('templateHealth', () => {
  it('has no blocker for a good template that needs no approval', () => {
    expect(templateHealth(good, ok).publishBlocker).toBeNull()
  })
  it('blocks an empty body and ignores markup', () => {
    expect(templateHealth({ ...good, bodyTemplate: '<p> </p>' }, ok).publishBlocker).toMatch(/Document text/)
  })
  it('blocks without a current legal review, naming the reason', () => {
    for (const s of ['NOT_REVIEWED', 'REJECTED', 'EXPIRED', 'WITHDRAWN']) {
      expect(templateHealth({ ...good, legalStatus: s }, ok).publishBlocker).toMatch(/Legal review evidence/)
    }
  })
  it('warns but does not block for a conditional review or placeholder mismatches', () => {
    const r = templateHealth({ bodyTemplate: '{{x}}', variables: { y: 'text' }, legalStatus: 'CONDITIONAL' }, ok)
    expect(r.publishBlocker).toBeNull()
    expect(r.items.find(i => i.id === 'placeholders')?.detail).toMatch(/not declared: x.*never used: y/)
    expect(r.items.find(i => i.id === 'legal')?.state).toBe('warn')
  })
  it('adds the approval step only when the policy requires it', () => {
    expect(templateHealth(good, ok).items.some(i => i.id === 'approval')).toBe(false)
    expect(templateHealth(good, { required: true, state: 'NONE' }).publishBlocker).toMatch(/Approval to publish/)
    expect(templateHealth(good, { required: true, state: 'PENDING' }).publishBlocker).toMatch(/Waiting/)
    expect(templateHealth(good, { required: true, state: 'APPROVED' }).publishBlocker).toBeNull()
  })
})

describe('templateUnusableReason', () => {
  it('matches the server rules', () => {
    expect(templateUnusableReason({ status: 'DRAFT' })).toBe('Not published')
    expect(templateUnusableReason({ status: 'PUBLISHED', legalStatus: 'EXPIRED' })).toBe('Legal review expired')
    expect(templateUnusableReason({ status: 'PUBLISHED', legalStatus: 'NOT_REVIEWED' })).toBeNull()
    expect(templateUnusableReason({})).toBeNull()
  })
})
