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

describe('resolveSafeActionUrl for agriculture links', () => {
  const farm = '11111111-1111-1111-1111-111111111111'
  const animal = '22222222-2222-2222-2222-222222222222'
  it('keeps a farm, animal, group, tab and crop-cycle link', () => {
    for (const url of [
      `/agriculture/farms?farm=${farm}`,
      `/agriculture/farms?farm=${farm}&animal=${animal}`,
      `/agriculture/farms?farm=${farm}&group=${animal}`,
      `/agriculture/farms?farm=${farm}&tab=inventory`,
      `/agriculture/crop-cycles?farm=${farm}`,
    ]) expect(resolveSafeActionUrl(url)).toBe(url)
  })
  it('sends anything else on the module to its home', () => {
    expect(resolveSafeActionUrl('/agriculture/health-events/abc')).toBe('/agriculture')
    expect(resolveSafeActionUrl('/agriculture/farms')).toBe('/agriculture')
    expect(resolveSafeActionUrl(`/agriculture/ledger?farm=${farm}`)).toBe('/agriculture')
    expect(resolveSafeActionUrl(`/agriculture/farms?farm=${farm}&next=https://evil.example`)).toBe('/agriculture')
    expect(resolveSafeActionUrl(`/agriculture/farms?farm=${farm}#x`)).toBe('/agriculture')
  })
})
