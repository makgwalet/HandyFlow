import { describe, it, expect } from 'vitest'
import { readStatus, statusLine, statusLabel, statusTone, REMOVE_WARNING } from './faceLogic'

describe('face data wording', () => {
  it('reads only a well-formed status', () => {
    expect(readStatus({ stored: true, consentAt: '2026-11-12T08:00:00Z' })).toEqual({ stored: true, consentAt: '2026-11-12T08:00:00Z' })
    expect(readStatus({ stored: true })).toEqual({ stored: true, consentAt: null })
    expect(readStatus({ stored: false, consentAt: '' })).toEqual({ stored: false, consentAt: null })
    expect(readStatus(null)).toBeNull()
    expect(readStatus({ stored: 'yes' })).toBeNull()
    expect(readStatus('<html>')).toBeNull()
  })
  it('says nothing is held', () => {
    expect(statusLine({ stored: false, consentAt: null })).toBe('No face data is held for this guard.')
    expect(statusLabel({ stored: false, consentAt: null })).toBe('None held')
    expect(statusTone({ stored: false, consentAt: null })).toBe('neutral')
  })
  it('names the consent date in South African time', () => {
    const s = { stored: true, consentAt: '2026-11-12T23:30:00Z' }   // 13 Nov 01:30 in Johannesburg
    expect(statusLine(s)).toContain('13')
    expect(statusLine(s)).toContain('2026')
    expect(statusLabel(s)).toBe('Held, with consent')
    expect(statusTone(s)).toBe('ok')
  })
  it('flags a template held without a consent date', () => {
    const s = { stored: true, consentAt: null }
    expect(statusLine(s)).toMatch(/before consent was recorded/)
    expect(statusLabel(s)).toBe('Held, no consent date')
    expect(statusTone(s)).toBe('warn')
  })
  it('warns that removal is permanent', () => expect(REMOVE_WARNING).toMatch(/for good/))
})
