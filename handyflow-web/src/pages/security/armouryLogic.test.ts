import { describe, it, expect } from 'vitest'
import { ammoReturnProblem, ammoText, parseRounds, sapsLine } from './armouryLogic'

const NOW = Date.parse('2026-11-20T10:00:00Z')

describe('sapsLine', () => {
  it('ignores firearms that are not lost', () => expect(sapsLine({ status: 'ISSUED' }, NOW)).toBeNull())
  it('shows the case number once recorded', () =>
    expect(sapsLine({ status: 'LOST', sapsCaseNumber: 'CAS 1/11/2026', sapsReportOverdue: true }, NOW)).toEqual({ text: 'SAPS case CAS 1/11/2026', urgent: false }))
  it('flags an overdue report', () => expect(sapsLine({ status: 'LOST', sapsReportOverdue: true }, NOW)?.urgent).toBe(true))
  it('counts down the hours, rounding up', () =>
    expect(sapsLine({ status: 'LOST', sapsReportDueAt: '2026-11-20T20:30:00Z' }, NOW)).toEqual({ text: 'Report to SAPS within 11h', urgent: false }))
  it('turns urgent in the last 6 hours', () =>
    expect(sapsLine({ status: 'LOST', sapsReportDueAt: '2026-11-20T15:00:00Z' }, NOW)).toEqual({ text: 'Report to SAPS within 5h', urgent: true }))
  it('never says 0 hours', () => expect(sapsLine({ status: 'LOST', sapsReportDueAt: '2026-11-20T10:10:00Z' }, NOW)?.text).toBe('Report to SAPS within 1h'))
  it('copes with no data', () => expect(sapsLine({ status: 'LOST' }, NOW)?.urgent).toBe(true))
})

describe('parseRounds', () => {
  it('blank is null', () => expect(parseRounds(' ')).toBeNull())
  it('whole numbers in range', () => { expect(parseRounds('0')).toBe(0); expect(parseRounds('30')).toBe(30); expect(parseRounds('2000')).toBe(2000) })
  it('rejects bad input', () => { for (const s of ['-1', '2.5', 'abc', '2001']) expect(parseRounds(s)).toBeNull() })
})

describe('ammoReturnProblem', () => {
  it('fine when all come back', () => expect(ammoReturnProblem(30, '30', '')).toBeNull())
  it('asks for a count', () => expect(ammoReturnProblem(30, '', '')).toMatch(/Enter how many/))
  it('rejects more than issued', () => expect(ammoReturnProblem(30, '31', '')).toMatch(/More rounds/))
  it('needs a reason when short', () => {
    expect(ammoReturnProblem(30, '28', ' ')).toBe('2 rounds unaccounted for: explain below')
    expect(ammoReturnProblem(30, '29', '')).toBe('1 round unaccounted for: explain below')
    expect(ammoReturnProblem(30, '28', 'Fired at intruder')).toBeNull()
  })
  it('nothing to compare when none were recorded', () => { expect(ammoReturnProblem(null, '', '')).toBeNull(); expect(ammoReturnProblem(undefined, '12', '')).toBeNull() })
  it('rejects an invalid number even without a baseline', () => expect(ammoReturnProblem(null, 'x', '')).toMatch(/whole number/))
})

describe('ammoText', () => {
  it('reads', () => { expect(ammoText(30, ' 9mm ')).toBe('30 x 9mm'); expect(ammoText(30)).toBe('30 rounds'); expect(ammoText(null)).toBeNull() })
})
