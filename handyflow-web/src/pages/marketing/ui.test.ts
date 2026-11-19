import { describe, it, expect } from 'vitest'
import { change, fmtN, pct, fmtDate, fmtDT } from './ui'

describe('marketing ui formatters', () => {
  it('fmtN treats missing values as zero', () => {
    expect(fmtN(undefined)).toBe('0')
    expect(fmtN(null)).toBe('0')
    expect(fmtN(0)).toBe('0')
  })

  it('fmtN groups thousands', () => {
    expect(fmtN(1234567).replace(/\s/g, ' ')).toMatch(/^1 234 567$/)
  })

  it('pct renders a dash when there is no rate, never NaN or 0%', () => {
    expect(pct(null)).toBe('—')
    expect(pct(undefined)).toBe('—')
    expect(pct(0)).toBe('0.0%')
    expect(pct(0.1234)).toBe('12.3%')
    expect(pct(0.1234, 2)).toBe('12.34%')
  })

  it('change is null when there is nothing to compare against', () => {
    expect(change(10, 0)).toBeNull()
    expect(change(10, null)).toBeNull()
    expect(change(null, 5)).toBeNull()
    expect(change(undefined, undefined)).toBeNull()
  })

  it('change is the relative difference', () => {
    expect(change(112, 100)).toBeCloseTo(0.12)
    expect(change(50, 100)).toBeCloseTo(-0.5)
    expect(change(0, 100)).toBe(-1)
  })

  it('dates render a dash when empty', () => {
    expect(fmtDate(null)).toBe('—')
    expect(fmtDT(undefined)).toBe('—')
    expect(fmtDate('2026-11-19T10:00:00Z')).toContain('2026')
  })
})
