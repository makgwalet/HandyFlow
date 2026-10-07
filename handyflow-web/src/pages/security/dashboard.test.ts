import { describe, it, expect } from 'vitest'
import { greeting, headline, isLate, punctualityLabel, shiftProgress, toneForCount } from './dashboard.logic'

const item = (level: 'DANGER' | 'WARNING' | 'INFO') => ({ code: level, level, title: 't', detail: 'd', count: 1, section: 'guards' })

describe('dashboard rules', () => {
  it('greets by the time of day', () => {
    expect(greeting(new Date(2026, 9, 7, 9))).toBe('Good morning')
    expect(greeting(new Date(2026, 9, 7, 13))).toBe('Good afternoon')
    expect(greeting(new Date(2026, 9, 7, 19))).toBe('Good evening')
  })
  it('tones a count that should be zero', () => {
    expect(toneForCount(0, 'bad')).toBe('ok')
    expect(toneForCount(2, 'bad')).toBe('bad')
  })
  it('words punctuality, with the 15 minute grace', () => {
    expect(punctualityLabel(null, null)).toBe('Not clocked in')
    expect(punctualityLabel(0, '2026-10-07T04:00:00Z')).toBe('On time')
    expect(punctualityLabel(15, '2026-10-07T04:15:00Z')).toBe('On time')
    expect(punctualityLabel(22, '2026-10-07T04:22:00Z')).toBe('Late by 22 min')
    expect(isLate(15)).toBe(false)
    expect(isLate(16)).toBe(true)
    expect(isLate(null)).toBe(false)
  })
  it('summarises shift progress without dividing by zero', () => {
    expect(shiftProgress({ onDuty: 0, scheduledToday: 0, notStarted: 0, missedToday: 0, completedToday: 0 })).toBe('No shifts scheduled today')
    expect(shiftProgress({ onDuty: 8, scheduledToday: 11, notStarted: 0, missedToday: 0, completedToday: 3 })).toBe('8 on duty, 3 of 11 done today')
  })
  it('headlines the most serious level', () => {
    expect(headline([])).toEqual({ tone: 'ok', text: 'All clear' })
    expect(headline([item('INFO')])).toEqual({ tone: 'info', text: '1 coming up' })
    expect(headline([item('WARNING'), item('INFO')])).toEqual({ tone: 'warn', text: '1 to check' })
    expect(headline([item('DANGER'), item('DANGER'), item('WARNING')])).toEqual({ tone: 'bad', text: '2 urgent' })
  })
})
