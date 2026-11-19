import { describe, it, expect } from 'vitest'
import { grantState, limitText, typesText, grantProblem, delegationProblem, enforcementBlocker, type Grant, type GrantForm, type DelegationForm } from './authorityShared'

const TODAY = '2026-11-19'
const src = (o: Partial<Grant> = {}): Grant => ({
  id: 'g1', userId: 'u1', role: null, businessUnit: null, contractTypes: ['SUPPLY'], actions: ['APPROVE'], unlimited: false, maxAmount: 100000,
  effectiveFrom: '2026-11-01', expiresOn: '2027-03-01', conditions: null, delegatedFromId: null, delegatedBy: null, status: 'ACTIVE', inForce: true, mine: true,
  revokedAt: null, revokedReason: null, createdAt: '', ...o,
})
const dform = (o: Partial<DelegationForm> = {}): DelegationForm => ({ toUserId: 'u2', actions: ['APPROVE'], types: ['SUPPLY'], unlimited: false, maxAmount: '50000', from: TODAY, to: '2026-12-01', ...o })
const gform = (o: Partial<GrantForm> = {}): GrantForm => ({ kind: 'USER', userId: 'u1', role: '', actions: ['SIGN'], types: [], unlimited: true, maxAmount: '', from: TODAY, to: '', ...o })

describe('grantState', () => {
  it('tells in force, scheduled, expired, revoked and a delegation whose source ended', () => {
    expect(grantState(src(), TODAY)).toBe('IN_FORCE')
    expect(grantState(src({ inForce: false, effectiveFrom: '2026-12-01' }), TODAY)).toBe('SCHEDULED')
    expect(grantState(src({ inForce: false, expiresOn: '2026-11-18' }), TODAY)).toBe('EXPIRED')
    expect(grantState(src({ status: 'REVOKED', inForce: false }), TODAY)).toBe('REVOKED')
    expect(grantState(src({ inForce: false, delegatedFromId: 'g0' }), TODAY)).toBe('SOURCE_ENDED')
  })
})

describe('plain words', () => {
  it('describes limits and types', () => {
    expect(limitText({ unlimited: true, maxAmount: null })).toBe('No financial limit')
    expect(limitText({ unlimited: false, maxAmount: 100000 })).toContain('Up to R')
    expect(typesText([], t => t)).toBe('All contract types')
    expect(typesText(['NDA', 'SUPPLY'], t => t.toLowerCase())).toBe('nda, supply')
  })
})

describe('grantProblem', () => {
  it('needs a grantee, an action and a limit or an explicit no-limit', () => {
    expect(grantProblem(gform())).toBeNull()
    expect(grantProblem(gform({ userId: '' }))).toContain('person')
    expect(grantProblem(gform({ kind: 'ROLE', role: '' }))).toContain('role')
    expect(grantProblem(gform({ actions: [] }))).toContain('action')
    expect(grantProblem(gform({ unlimited: false, maxAmount: '' }))).toContain('financial limit')
    expect(grantProblem(gform({ unlimited: false, maxAmount: '5000' }))).toBeNull()
    expect(grantProblem(gform({ from: '2026-12-01', to: '2026-11-01' }))).toContain('end date')
  })
})

describe('delegationProblem', () => {
  it('allows a smaller, shorter delegation', () => expect(delegationProblem(src(), dform(), TODAY)).toBeNull())
  it('refuses more than the source holds', () => {
    expect(delegationProblem(src(), dform({ actions: ['APPROVE', 'SIGN'] }), TODAY)).toContain('actions you hold')
    expect(delegationProblem(src(), dform({ maxAmount: '100001' }), TODAY)).toContain('own limit')
    expect(delegationProblem(src(), dform({ unlimited: true }), TODAY)).toContain('own financial limit')
    expect(delegationProblem(src(), dform({ types: ['NDA'] }), TODAY)).toContain('contract types you hold')
    expect(delegationProblem(src(), dform({ types: [] }), TODAY)).toContain('choose the types')
    expect(delegationProblem(src(), dform({ to: '2027-03-02' }), TODAY)).toContain('outlast')
    expect(delegationProblem(src(), dform({ from: '2026-10-31' }), TODAY)).toContain('before your own')
  })
  it('needs a recipient, dates that make sense and an end date', () => {
    expect(delegationProblem(src(), dform({ toUserId: '' }), TODAY)).toContain('who')
    expect(delegationProblem(src(), dform({ to: '' }), TODAY)).toContain('end date')
    expect(delegationProblem(src(), dform({ from: '2026-12-05', to: '2026-12-01' }), TODAY)).toContain('end date')
  })
})

describe('enforcementBlocker', () => {
  it('stops enforcement being switched on while nobody has authority', () => {
    expect(enforcementBlocker([])).toContain('Give someone authority first')
    expect(enforcementBlocker([{ inForce: false }])).not.toBeNull()
    expect(enforcementBlocker([{ inForce: true }])).toBeNull()
  })
})
