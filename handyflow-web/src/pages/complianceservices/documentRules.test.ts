import { describe, it, expect } from 'vitest'
import { needsVerifyException, verifyBlocker, MIN_EXCEPTION_REASON, statusText, isRetired, canReview, canVerify, canReject, canReplace, canArchive, reasonBlocker, replacementMatches } from './documentRules'

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

describe("document life", () => {
  it("names each status", () => {
    expect(statusText("SUPERSEDED")).toBe("Replaced")
    expect(statusText("NEW_THING")).toBe("NEW_THING")
  })
  it("only waiting documents can be verified or rejected", () => {
    for (const s of ["UPLOADED", "UNDER_REVIEW"]) { expect(canVerify(s)).toBe(true); expect(canReject(s)).toBe(true) }
    for (const s of ["VERIFIED", "REJECTED", "SUPERSEDED", "ARCHIVED"]) { expect(canVerify(s)).toBe(false); expect(canReject(s)).toBe(false) }
    expect(canReview("UPLOADED")).toBe(true)
    expect(canReview("UNDER_REVIEW")).toBe(false)
  })
  it("replaced and archived documents are retired and cannot be replaced or archived again", () => {
    expect(isRetired("ARCHIVED")).toBe(true)
    expect(isRetired("REJECTED")).toBe(false)
    expect(canReplace("REJECTED")).toBe(true)
    expect(canReplace("ARCHIVED")).toBe(false)
    expect(canArchive("VERIFIED")).toBe(true)
    expect(canArchive("SUPERSEDED")).toBe(false)
  })
  it("wants a reason of at least 15 characters", () => {
    expect(reasonBlocker("reject", "no")).toMatch(/rejected/)
    expect(reasonBlocker("archive", "   short   ")).toMatch(/archived/)
    expect(reasonBlocker("archive", "Client left in October")).toBeNull()
  })
  it("matches a replacement on type, ignoring case and spaces", () => {
    expect(replacementMatches("Tax Clearance Certificate", " tax clearance certificate ")).toBe(true)
    expect(replacementMatches("Tax Clearance Certificate", "BBBEE Certificate")).toBe(false)
  })
})
