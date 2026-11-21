import { describe, it, expect } from 'vitest'
import { canRevokeShared, orderShared, registerBody, sharedLabel, sharedStatusTone, validateRegister } from './sharedDeviceLogic'

describe('validateRegister', () => {
  it('needs a hardware id', () => expect(validateRegister({ hardwareId: '  ', name: '', siteId: '' })).toMatch(/hardware ID/))
  it('rejects overlong values', () => {
    expect(validateRegister({ hardwareId: 'x'.repeat(201), name: '', siteId: '' })).toMatch(/too long/)
    expect(validateRegister({ hardwareId: 'abc', name: 'n'.repeat(101), siteId: '' })).toMatch(/100/)
  })
  it('accepts a good form', () => expect(validateRegister({ hardwareId: 'abc', name: 'Gate 2', siteId: 's1' })).toBeNull())
})
describe('registerBody', () => {
  it('trims and nulls empties', () => {
    expect(registerBody({ hardwareId: ' hw1 ', name: ' ', siteId: '' })).toEqual({ deviceHardwareId: 'hw1', deviceName: null, siteId: null })
    expect(registerBody({ hardwareId: 'hw1', name: ' Gate ', siteId: 's1' })).toEqual({ deviceHardwareId: 'hw1', deviceName: 'Gate', siteId: 's1' })
  })
})
describe('canRevokeShared', () => {
  it('only devices still in play', () => {
    expect(canRevokeShared('ACTIVE')).toBe(true)
    expect(canRevokeShared('PENDING')).toBe(true)
    for (const s of ['REVOKED', 'LOST', 'BLOCKED', 'DECOMMISSIONED', 'REPLACED']) expect(canRevokeShared(s)).toBe(false)
  })
})
describe('labels and order', () => {
  it('names the device or its last 4', () => {
    expect(sharedLabel({ id: '1', deviceHardwareId: 'abc-9f2a', status: 'ACTIVE' })).toBe('Device ending 9F2A')
    expect(sharedLabel({ id: '1', deviceHardwareId: 'abc', deviceName: ' Gate ', status: 'ACTIVE' })).toBe('Gate')
  })
  it('tones', () => { expect(sharedStatusTone('ACTIVE')).toBe('ok'); expect(sharedStatusTone('REVOKED')).toBe('bad'); expect(sharedStatusTone('X')).toBe('neutral') })
  it('orders by site then name, unassigned last', () => {
    const l = orderShared([
      { id: 'c', deviceHardwareId: 'h', deviceName: 'Z', status: 'ACTIVE' },
      { id: 'b', siteId: 's2', deviceHardwareId: 'h', deviceName: 'B', status: 'ACTIVE' },
      { id: 'a', siteId: 's1', deviceHardwareId: 'h', deviceName: 'A', status: 'ACTIVE' },
    ], { s1: 'Alpha', s2: 'Beta' })
    expect(l.map(d => d.id)).toEqual(['a', 'b', 'c'])
  })
})
