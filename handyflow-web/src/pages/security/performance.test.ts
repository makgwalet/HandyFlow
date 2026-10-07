import { describe, it, expect } from 'vitest'
import { BAND_LABEL, BAND_TONE, percentTone, ratingFormError, riskSettingsError, sourceLabel } from './performance.logic'

const scores = { punctuality: 4, professionalism: 5, appearance: 4, communication: 3, alertness: 5, incidentHandling: 4 }

describe('performance logic', () => {
  it('labels and tones every band, including no score', () => {
    expect(BAND_LABEL.NOT_ENOUGH_DATA).toBe('Not enough data')
    expect(BAND_TONE.AT_RISK).toBe('bad')
    expect(BAND_TONE.NOT_ENOUGH_DATA).toBe('neutral')
    expect(BAND_TONE.EXCELLENT).toBe('ok')
  })
  it('colours a component by how much it earned', () => {
    expect(percentTone(95)).toBe('ok'); expect(percentTone(80)).toBe('ok'); expect(percentTone(79)).toBe('warn')
    expect(percentTone(50)).toBe('warn'); expect(percentTone(49)).toBe('bad')
  })
  it('checks the rating form', () => {
    expect(ratingFormError({ source: '', ratedOn: '2026-10-01', scores }, '2026-10-07')).toMatch(/who/i)
    expect(ratingFormError({ source: 'CLIENT', ratedOn: '', scores }, '2026-10-07')).toMatch(/date/i)
    expect(ratingFormError({ source: 'CLIENT', ratedOn: '2026-10-08', scores }, '2026-10-07')).toMatch(/future/i)
    expect(ratingFormError({ source: 'CLIENT', ratedOn: '2026-10-01', scores: { ...scores, alertness: 0 } }, '2026-10-07')).toMatch(/alertness/i)
    expect(ratingFormError({ source: 'CLIENT', ratedOn: '2026-10-01', scores }, '2026-10-07')).toBeNull()
  })
  it('checks risk thresholds like the server', () => {
    const ok = { reviewAt: 1, warningAt: 3, investigationAt: 5, windowDays: 90, misconductAt: 2, misconductWindowDays: 365 }
    expect(riskSettingsError(ok)).toBeNull()
    expect(riskSettingsError({ ...ok, reviewAt: 4 })).toMatch(/rise/i)
    expect(riskSettingsError({ ...ok, investigationAt: 2 })).toMatch(/rise/i)
    expect(riskSettingsError({ ...ok, reviewAt: 0 })).toMatch(/at least 1/i)
    expect(riskSettingsError({ ...ok, warningAt: 2.5 })).toMatch(/whole/i)
    expect(riskSettingsError({ ...ok, windowDays: 5 })).toMatch(/window/i)
    expect(riskSettingsError({ ...ok, misconductWindowDays: 900 })).toMatch(/window/i)
  })
  it('names rating sources', () => { expect(sourceLabel('CLIENT')).toBe('Client'); expect(sourceLabel('SUPERVISOR')).toBe('Supervisor') })
})
