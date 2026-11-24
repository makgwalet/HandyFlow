import { describe, it, expect } from 'vitest'
import { needsVerifyException, verifyBlocker, MIN_EXCEPTION_REASON } from './documentRules'

describe('needsVerifyException', () => {
  it('is needed only for the uploader', () => {
    expect(needsVerifyException({ uploadedBy: 'u1' }, 'u1')).toBe(true)
    expect(needsVerifyException({ uploadedBy: 'u1' }, 'u2')).toBe(false)
  })
  it('is not needed when either side is unknown', () => {
    expect(needsVerifyException({ uploadedBy: null }, 'u1')).toBe(false)
    expect(needsVerifyException({}, 'u1')).toBe(false)
    expect(needsVerifyException({ uploadedBy: 'u1' }, null)).toBe(false)
    expect(needsVerifyException({ uploadedBy: 'u1' }, undefined)).toBe(false)
  })
})

describe('verifyBlocker', () => {
  it('never blocks someone else verifying', () => expect(verifyBlocker(false, '')).toBeNull())
  it('blocks the uploader without a real reason', () => {
    expect(verifyBlocker(true, '')).toMatch(String(MIN_EXCEPTION_REASON))
    expect(verifyBlocker(true, '   short   ')).not.toBeNull()
  })
  it('lets the uploader proceed with a reason', () => expect(verifyBlocker(true, 'I am the only consultant here')).toBeNull())
})
