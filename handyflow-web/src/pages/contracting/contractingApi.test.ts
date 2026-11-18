import { describe, it, expect } from 'vitest'
import { unwrap, unwrapOne, fmtR, apiError } from './contractingApi'

describe('unwrap (lists)', () => {
  it('reads a list or a page from the ApiResponse envelope', () => {
    expect(unwrap({ data: { data: [1, 2] } })).toEqual([1, 2])
    expect(unwrap({ data: { data: { content: [3] } } })).toEqual([3])
    expect(unwrap({ data: [4] })).toEqual([4])
  })
  it('gives an empty list for a single object, which is why dashboards must use unwrapOne', () => {
    expect(unwrap({ data: { data: { statusCounts: { SIGNED: 2 } } } })).toEqual([])
  })
})

describe('unwrapOne (single objects)', () => {
  it('reads the object from the envelope', () => {
    expect(unwrapOne<{ totalContracts: number }>({ data: { data: { totalContracts: 7 } } }).totalContracts).toBe(7)
    expect(unwrapOne<{ a: number }>({ data: { a: 1 } }).a).toBe(1)
  })
})

describe('fmtR and apiError', () => {
  it('formats rand and shows a dash for no value', () => {
    expect(fmtR(null)).toBe('—')
    expect(fmtR(undefined)).toBe('—')
    expect(fmtR(1500)).toMatch(/^R\s?1.?500,00$/)
  })
  it('prefers the server message', () => {
    expect(apiError({ response: { data: { message: 'Nope' } } }, 'fallback')).toBe('Nope')
    expect(apiError(new Error('x'), 'fallback')).toBe('fallback')
  })
})
