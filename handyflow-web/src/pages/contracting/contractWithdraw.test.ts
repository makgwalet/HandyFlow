import { describe, it, expect } from 'vitest'
import { withdrawAvailability, withdrawStep, canReviseWithdrawn } from './contractWithdraw'

describe('withdrawAvailability', () => {
  it('is only for a contract out for signature', () => {
    expect(withdrawAvailability('SENT', [{ signingStatus: 'PENDING' }]).available).toBe(true)
    for (const s of ['DRAFT', 'UNDER_REVIEW', 'SIGNED', 'TERMINATED', 'WITHDRAWN']) expect(withdrawAvailability(s, []).available).toBe(false)
  })
  it('is not offered once every signer has signed, and says what to do instead', () => {
    const r = withdrawAvailability('SENT', [{ signingStatus: 'SIGNED' }, { signingStatus: 'SIGNED' }])
    expect(r.available).toBe(false)
    expect(r.why).toContain('termination or an amendment')
  })
  it('stays available when only some have signed, and a witness does not count as a signer', () => {
    expect(withdrawAvailability('SENT', [{ signingStatus: 'SIGNED' }, { signingStatus: 'PENDING' }]).available).toBe(true)
    expect(withdrawAvailability('SENT', [{ signingStatus: 'SIGNED' }, { signingStatus: 'PENDING', partyRole: 'WITNESS' }]).available).toBe(false)
  })
})

describe('withdrawStep', () => {
  it('asks for approval first when policy requires it', () => {
    expect(withdrawStep(true, 'NONE', '', false).step).toBe('ASK')
    const w = withdrawStep(true, 'PENDING', 'a good enough reason', false)
    expect(w.step).toBe('WAITING')
    expect(w.disabled).toBe(true)
  })
  it('needs a reason of ten characters before it can withdraw', () => {
    expect(withdrawStep(true, 'APPROVED', 'too short', false).disabled).toBe(true)
    expect(withdrawStep(true, 'APPROVED', 'wrong schedule attached', false)).toMatchObject({ step: 'WITHDRAW', disabled: false })
    expect(withdrawStep(false, 'NONE', 'wrong schedule attached', false).step).toBe('WITHDRAW')
  })
  it('is disabled while busy', () => {
    expect(withdrawStep(false, 'NONE', 'wrong schedule attached', true).disabled).toBe(true)
  })
})

describe('canReviseWithdrawn', () => {
  it('is offered on a withdrawn contract to someone who can manage', () => {
    expect(canReviseWithdrawn('WITHDRAWN', true)).toBe(true)
    expect(canReviseWithdrawn('WITHDRAWN', false)).toBe(false)
    expect(canReviseWithdrawn('SENT', true)).toBe(false)
  })
})
