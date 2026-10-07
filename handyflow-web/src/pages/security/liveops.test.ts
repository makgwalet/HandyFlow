import { describe, it, expect } from 'vitest'
import { DEFAULT_CENTER, ago, filterGuards, mapCenter, summarise, withPosition, type LiveGuard } from './liveops.logic'

const g = (o: Partial<LiveGuard> = {}): LiveGuard => ({ guardId: 'g' + Math.random(), guardName: 'Thabo Mokoena', grade: 'C', shiftId: 's', shiftStart: '2026-10-07T04:00:00Z', shiftEnd: '2026-10-07T16:00:00Z',
  overrunning: false, siteId: 'x', siteName: 'ABC Mall', latitude: -26, longitude: 28, recordedAt: '2026-10-07T09:58:00Z', gpsState: 'LIVE', lastScanAt: null, lastScanCheckpoint: null, ...o })

describe('live operations logic', () => {
  const list = [g(), g({ guardName: 'Neo Khumalo', gpsState: 'STALE', siteName: 'Parkview' }), g({ guardName: 'Sipho Dube', gpsState: 'NO_GPS', latitude: null, longitude: null, recordedAt: null, overrunning: true })]
  it('summarises by GPS state and counts overrunning shifts', () => {
    expect(summarise(list)).toEqual({ onDuty: 3, live: 1, stale: 1, noGps: 1, overrunning: 1 })
    expect(summarise([])).toEqual({ onDuty: 0, live: 0, stale: 0, noGps: 0, overrunning: 0 })
  })
  it('filters by GPS state and by guard or site name', () => {
    expect(filterGuards(list, { gps: 'STALE', search: '' }).map(x => x.guardName)).toEqual(['Neo Khumalo'])
    expect(filterGuards(list, { gps: '', search: 'park' }).map(x => x.guardName)).toEqual(['Neo Khumalo'])
    expect(filterGuards(list, { gps: '', search: ' sipho ' }).length).toBe(1)
    expect(filterGuards(list, { gps: '', search: '' }).length).toBe(3)
  })
  it('plots only guards that have a position and centres on them', () => {
    expect(withPosition(list).length).toBe(2)
    expect(mapCenter([g({ latitude: -26, longitude: 28 }), g({ latitude: -28, longitude: 30 })])).toEqual([-27, 29])
  })
  it('falls back to a plain national view, with nothing plotted, when nobody has a position', () => {
    expect(mapCenter([list[2]])).toEqual(DEFAULT_CENTER)
    expect(mapCenter([])).toEqual(DEFAULT_CENTER)
  })
  it('describes how long ago', () => {
    const now = new Date('2026-10-07T10:00:00Z').getTime()
    expect(ago(null, now)).toBe('never')
    expect(ago('2026-10-07T10:00:00Z', now)).toBe('just now')
    expect(ago('2026-10-07T09:55:00Z', now)).toBe('5 min ago')
    expect(ago('2026-10-07T07:00:00Z', now)).toBe('3 h ago')
    expect(ago('2026-10-04T10:00:00Z', now)).toBe('3 d ago')
  })
})
