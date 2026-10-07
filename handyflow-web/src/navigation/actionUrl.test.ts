import { describe, it, expect } from 'vitest'
import { resolveSafeActionUrl } from './actionUrl'

describe('resolveSafeActionUrl for security links', () => {
  it('keeps the detail pages that exist', () => {
    expect(resolveSafeActionUrl('/security/guards/abc-1')).toBe('/security/guards/abc-1')
    expect(resolveSafeActionUrl('/security/complaints/abc-1')).toBe('/security/complaints/abc-1')
    expect(resolveSafeActionUrl('/security/incidents/abc-1')).toBe('/security/incidents/abc-1')
    expect(resolveSafeActionUrl('/security/sites/abc-1')).toBe('/security/sites/abc-1')
  })
  it('sends anything deeper or unknown to the module page', () => {
    expect(resolveSafeActionUrl('/security/sites/abc-1/on-site')).toBe('/security')
    expect(resolveSafeActionUrl('/security/guards')).toBe('/security')
    expect(resolveSafeActionUrl('/security/other/abc')).toBe('/security')
  })
  it('still drops unknown modules', () => {
    expect(resolveSafeActionUrl('/nowhere/1')).toBeNull()
  })
})
