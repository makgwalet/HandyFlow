import { describe, it, expect } from 'vitest'
import {
  stageLabel, workingHoursPerDay, hoursInWords, minutesInWords, settingsProblem, absenceProblem, daysBetweenInclusive, isoToday,
  type EscalationSettings,
} from './escalationShared'

const base: EscalationSettings = {
  enabled: true, enabledSince: null, reminder1Hours: 24, reminder2Hours: 48, escalateHours: 72, clockMode: 'BUSINESS_HOURS',
  workStart: '08:00', workEnd: '17:00', workDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'], skipPublicHolidays: true, escalationUserIds: [],
}

describe('stageLabel', () => {
  it('names how far a request has been chased', () => {
    expect(stageLabel(0)).toBeNull()
    expect(stageLabel(undefined)).toBeNull()
    expect(stageLabel(1)).toBe('Reminded')
    expect(stageLabel(2)).toBe('Reminded twice')
    expect(stageLabel(3)).toBe('Escalated')
  })
})

describe('working-day equivalent', () => {
  it('counts the hours in a working day', () => expect(workingHoursPerDay('08:00', '17:00')).toBe(9))
  it('says 72 working hours is 8 working days', () => expect(hoursInWords(72, base)).toBe('72 working hours (about 8 working days)'))
  it('keeps a fraction when it is not whole', () => expect(hoursInWords(24, base)).toBe('24 working hours (about 2.7 working days)'))
  it('describes the around-the-clock clock in days', () => expect(hoursInWords(72, { ...base, clockMode: 'CALENDAR' })).toBe('72 hours (3 days, around the clock)'))
})

describe('minutesInWords', () => {
  it('handles gaps and short and long waits', () => {
    expect(minutesInWords(null)).toBe('—')
    expect(minutesInWords(0)).toBe('under a minute')
    expect(minutesInWords(45)).toBe('45 min')
    expect(minutesInWords(150)).toBe('2 h 30 min')
    expect(minutesInWords(120)).toBe('2 h')
    expect(minutesInWords(60 * 50)).toBe('2 days 2 h')
    expect(minutesInWords(60 * 48)).toBe('2 days')
  })
})

describe('settingsProblem', () => {
  it('accepts the defaults', () => expect(settingsProblem(base)).toBeNull())
  it('wants the steps in order', () => expect(settingsProblem({ ...base, reminder2Hours: 24 })).toMatch(/before/))
  it('wants whole hours up to 720', () => {
    expect(settingsProblem({ ...base, escalateHours: 721 })).toMatch(/1 to 720/)
    expect(settingsProblem({ ...base, reminder1Hours: 0 })).toMatch(/1 to 720/)
    expect(settingsProblem({ ...base, reminder1Hours: 1.5 })).toMatch(/1 to 720/)
  })
  it('wants sensible working hours and days only on the working clock', () => {
    expect(settingsProblem({ ...base, workStart: '17:00', workEnd: '08:00' })).toMatch(/start before/)
    expect(settingsProblem({ ...base, workDays: [] })).toMatch(/working day/)
    expect(settingsProblem({ ...base, clockMode: 'CALENDAR', workDays: [], workStart: '17:00', workEnd: '08:00' })).toBeNull()
  })
})

describe('absenceProblem', () => {
  const t = isoToday()
  it('needs both days', () => expect(absenceProblem('', t, '', 'u')).toMatch(/first and last/))
  it('rejects an end before the start', () => expect(absenceProblem('2099-01-10', '2099-01-05', '', 'u')).toMatch(/before/))
  it('rejects time that is already past', () => expect(absenceProblem('2020-01-01', '2020-01-02', '', 'u')).toMatch(/past/))
  it('rejects covering yourself', () => expect(absenceProblem('2099-01-01', '2099-01-02', 'u', 'u')).toMatch(/themselves/))
  it('rejects more than a year', () => expect(absenceProblem('2099-01-01', '2100-06-01', '', 'u')).toMatch(/366/))
  it('accepts a normal request', () => expect(absenceProblem('2099-01-01', '2099-01-05', 'v', 'u')).toBeNull())
})

describe('daysBetweenInclusive', () => {
  it('counts both ends', () => {
    expect(daysBetweenInclusive('2026-12-01', '2026-12-01')).toBe(1)
    expect(daysBetweenInclusive('2026-12-01', '2026-12-05')).toBe(5)
  })
})
