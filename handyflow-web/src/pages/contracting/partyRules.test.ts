import { describe, it, expect } from 'vitest'
import { nextSigningOrder, partyBlocker, orderNote } from './partyRules'

const ps = [{ fullName: 'Ann', signingOrder: 1 }, { fullName: 'Ben', signingOrder: 2 }]

describe('partyRules', () => {
  it('defaults to the next free order', () => {
    expect(nextSigningOrder([])).toBe(1)
    expect(nextSigningOrder(ps)).toBe(3)
    expect(nextSigningOrder([{ fullName: 'X', signingOrder: 5 }])).toBe(6)
  })
  it('refuses a malformed email and a bad order', () => {
    expect(partyBlocker({ fullName: 'Eve', email: 'rtrtrt', signingOrder: '1' })).toMatch(/rtrtrt/)
    expect(partyBlocker({ fullName: 'Eve', email: 'eve@example.com', signingOrder: '0' })).toMatch(/whole number/)
    expect(partyBlocker({ fullName: ' ', email: '', signingOrder: '1' })).toMatch(/name/)
  })
  it('accepts a blank email and a good form', () => {
    expect(partyBlocker({ fullName: 'Eve', email: '', signingOrder: '2' })).toBeNull()
    expect(partyBlocker({ fullName: 'Eve', email: ' eve@example.com ', signingOrder: '3' })).toBeNull()
  })
  it('explains the chosen order', () => {
    expect(orderNote(1, ps)).toMatch(/together with Ann/)
    expect(orderNote(3, ps)).toMatch(/after 2 earlier parties/)
    expect(orderNote(1, [])).toMatch(/Signs first/)
    expect(orderNote(2, [{ fullName: 'Ann', signingOrder: 1 }])).toMatch(/after 1 earlier party/)
  })
  it('ignores declined parties', () => {
    expect(orderNote(1, [{ fullName: 'Gone', signingOrder: 1, signingStatus: 'DECLINED' }])).toMatch(/Signs first/)
  })
})
