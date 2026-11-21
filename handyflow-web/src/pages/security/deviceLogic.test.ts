import { describe, it, expect } from 'vitest'
import { deviceActions, deviceLabel, minutesLeft, orderDevices, spacedCode, statusTone } from './deviceLogic'

describe('deviceLabel', () => {
  it('prefers the name', () => expect(deviceLabel({ id: '1', status: 'ACTIVE', deviceName: ' Thabo phone ' })).toBe('Thabo phone'))
  it('shows only the end of the hardware id', () => expect(deviceLabel({ id: '1', status: 'ACTIVE', deviceHardwareId: 'abcdef-1234-9f2a' })).toBe('Device ending 9F2A'))
  it('copes with nothing', () => expect(deviceLabel({ id: '1', status: 'ACTIVE' })).toBe('Unnamed device'))
})
describe('orderDevices', () => {
  it('pins the active device then newest first', () => {
    const l = orderDevices([
      { id: 'old', status: 'REPLACED', createdAt: '2026-01-01T00:00:00Z' },
      { id: 'act', status: 'ACTIVE', createdAt: '2026-02-01T00:00:00Z' },
      { id: 'lost', status: 'LOST', createdAt: '2026-03-01T00:00:00Z' }])
    expect(l.map(d => d.id)).toEqual(['act', 'lost', 'old'])
  })
})
describe('statusTone', () => {
  it('maps', () => { expect(statusTone('ACTIVE')).toBe('ok'); expect(statusTone('LOST')).toBe('bad'); expect(statusTone('PENDING')).toBe('warn'); expect(statusTone('??')).toBe('neutral') })
})
describe('spacedCode', () => {
  it('splits six digits', () => expect(spacedCode('123456')).toBe('123 456'))
  it('leaves others', () => expect(spacedCode('12AB')).toBe('12AB'))
})
describe('minutesLeft', () => {
  const t = Date.parse('2026-11-20T10:00:00Z')
  it('rounds up', () => expect(minutesLeft('2026-11-20T10:14:01Z', t)).toBe(15))
  it('never negative', () => expect(minutesLeft('2026-11-20T09:00:00Z', t)).toBe(0))
  it('bad input is zero', () => expect(minutesLeft('x', t)).toBe(0))
})

describe('deviceActions', () => {
  const all = { manage: true, admin: true }
  const acts = (status: string, can = all, deviceType?: string) => deviceActions({ id: '1', status, deviceType }, can)
  it('active and pending phones can be reported lost or revoked', () => {
    expect(acts('ACTIVE')).toEqual(['lost', 'revoke'])
    expect(acts('PENDING')).toEqual(['lost', 'revoke'])
  })
  it('a lost phone can be revoked or reinstated', () => expect(acts('LOST')).toEqual(['revoke', 'reinstate']))
  it('a revoked phone can only be reinstated', () => expect(acts('REVOKED')).toEqual(['reinstate']))
  it('end states offer nothing', () => { for (const s of ['BLOCKED', 'REPLACED', 'DECOMMISSIONED']) expect(acts(s)).toEqual([]) })
  it('reinstate needs the admin permission and lost/revoke need manage', () => {
    expect(acts('LOST', { manage: true, admin: false })).toEqual(['revoke'])
    expect(acts('REVOKED', { manage: true, admin: false })).toEqual([])
    expect(acts('ACTIVE', { manage: false, admin: true })).toEqual([])
    expect(acts('REVOKED', { manage: false, admin: true })).toEqual(['reinstate'])
  })
  it('shared site devices are left to their own screen', () => expect(acts('ACTIVE', all, 'SHARED_SITE_DEVICE')).toEqual([]))
})
