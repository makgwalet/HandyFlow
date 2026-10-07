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

import { chartGeometry, trendSummary, trendText } from './performance.logic'
const pt = (date: string, score: number | null) => ({ date, score, band: null, coverage: 90, recommendations: 0 })
describe('score trend', () => {
  it('summarises the change between the first and last scored day', () => {
    expect(trendSummary([pt('2026-09-01', 60), pt('2026-09-02', null), pt('2026-09-10', 72)])).toMatchObject({ delta: 12, direction: 'up', from: '2026-09-01', to: '2026-09-10' })
    expect(trendSummary([pt('2026-09-01', 80), pt('2026-09-10', 70)])?.direction).toBe('down')
    expect(trendSummary([pt('2026-09-01', 70), pt('2026-09-10', 71)])?.direction).toBe('flat')
    expect(trendSummary([pt('2026-09-01', 70)])).toBeNull()
  })
  it('words the summary', () => {
    const f = (d: string) => d
    expect(trendText(trendSummary([pt('2026-09-01', 60), pt('2026-09-10', 61 + 5)]), f)).toBe('Up 6 points since 2026-09-01.')
    expect(trendText(trendSummary([pt('2026-09-01', 60), pt('2026-09-10', 59)]), f)).toBe('Steady since 2026-09-01 (-1).')
    expect(trendText(null, f)).toMatch(/two days/)
  })
  it('places points by date and score, leaving gaps out', () => {
    const g = chartGeometry([pt('2026-09-01', 100), pt('2026-09-02', null), pt('2026-09-03', 0)], 100, 100, 10)
    expect(g.dots).toHaveLength(2)
    expect(g.dots[0]).toMatchObject({ x: 10, y: 10 })
    expect(g.dots[1]).toMatchObject({ x: 90, y: 90 })
    expect(g.line.startsWith('M10.0 10.0 L90.0 90.0')).toBe(true)
    expect(chartGeometry([], 100, 100).line).toBe('')
    expect(chartGeometry([pt('2026-09-01', 50)], 100, 100).dots[0].x).toBe(50)
  })
})
