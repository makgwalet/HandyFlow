import { describe, it, expect } from 'vitest'
import { actionFormError, availableActions, elapsed, severitiesAbove, titleCase } from './incident.logic'

describe('incident logic', () => {
  it('shows only the allowed actions, in order, and leaves evidence to its own panel', () => {
    expect(availableActions(['RESOLVE', 'EVIDENCE', 'ASSIGN', 'ACKNOWLEDGE']).map(a => a.action)).toEqual(['ACKNOWLEDGE', 'ASSIGN', 'RESOLVE'])
    expect(availableActions(['REOPEN', 'NOTE']).map(a => a.label)).toEqual(['Add note', 'Reopen'])
    expect(availableActions([])).toEqual([])
  })
  it('offers only higher severities when escalating', () => {
    expect(severitiesAbove('LOW')).toEqual(['MEDIUM', 'HIGH', 'CRITICAL'])
    expect(severitiesAbove('high')).toEqual(['CRITICAL'])
    expect(severitiesAbove('CRITICAL')).toEqual([])
    expect(severitiesAbove('???')).toEqual([])
  })
  it('requires text for assign, escalate, note and reopen', () => {
    for (const a of ['ASSIGN', 'ESCALATE', 'NOTE', 'REOPEN']) {
      expect(actionFormError(a, { text: '  ' })).toBeTruthy()
      expect(actionFormError(a, { text: 'ok' })).toBeNull()
    }
    expect(actionFormError('RESOLVE', {})).toBeNull()
  })
  it('measures time to acknowledge or resolve', () => {
    expect(elapsed('2026-10-01T08:00:00Z', null)).toBeNull()
    expect(elapsed('2026-10-01T08:00:00Z', '2026-10-01T08:12:00Z')).toBe('12 min')
    expect(elapsed('2026-10-01T08:00:00Z', '2026-10-01T11:05:00Z')).toBe('3 h 5 min')
    expect(elapsed('2026-10-01T08:00:00Z', '2026-10-03T12:00:00Z')).toBe('2 d 4 h')
  })
  it('formats names', () => { expect(titleCase('SUSPICIOUS')).toBe('Suspicious'); expect(titleCase(null)).toBe('-') })
})
