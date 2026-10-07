import { describe, it, expect } from 'vitest'
import { defaultReviewForm, dueLine, overallLabel, reviewFormError } from './reviews.logic'

const ok = () => ({ ...defaultReviewForm('2026-10-07'), overall: 'MEETS', scores: { punctuality: 4, professionalism: 4, appearance: 4, communication: 4, alertness: 4, incidentHandling: 4 }, strengths: 'Reliable' })

describe('supervisor review form', () => {
  it('starts with the last 90 days, reviewed today', () => {
    const f = defaultReviewForm('2026-10-07')
    expect(f.periodFrom).toBe('2026-07-09')
    expect(f.periodTo).toBe('2026-10-07')
    expect(f.reviewDate).toBe('2026-10-07')
  })
  it('accepts a complete review', () => { expect(reviewFormError(ok(), '2026-10-07')).toBeNull() })
  it('names what is missing, in the order a person fills it in', () => {
    expect(reviewFormError({ ...ok(), periodFrom: '' }, '2026-10-07')).toMatch(/period/i)
    expect(reviewFormError({ ...ok(), periodTo: '2026-07-01' }, '2026-10-07')).toMatch(/end before/i)
    expect(reviewFormError({ ...ok(), reviewDate: '2026-10-09', periodTo: '2026-10-06' }, '2026-10-07')).toMatch(/future/i)
    expect(reviewFormError({ ...ok(), periodTo: '2026-10-07', reviewDate: '2026-10-06' }, '2026-10-07')).toMatch(/before the end/i)
    expect(reviewFormError({ ...ok(), overall: '' }, '2026-10-07')).toMatch(/overall/i)
    expect(reviewFormError({ ...ok(), scores: { punctuality: 4 } }, '2026-10-07')).toMatch(/every area/i)
    expect(reviewFormError({ ...ok(), strengths: ' ' }, '2026-10-07')).toMatch(/went well/i)
    expect(reviewFormError({ ...ok(), followUpDate: '2026-10-01' }, '2026-10-07')).toMatch(/follow-up/i)
  })
  it('words when a review is due', () => {
    const l = (o: any) => ({ reviews: [], lastReviewOn: null, daysSinceLast: null, dueState: 'OK', intervalDays: 90, ...o })
    expect(dueLine(l({ dueState: 'NONE' })).text).toMatch(/not been reviewed/)
    expect(dueLine(l({ dueState: 'OVERDUE', daysSinceLast: 100 })).text).toMatch(/100 days ago.*every 90 days/)
    expect(dueLine(l({ dueState: 'FOLLOW_UP_DUE' })).tone).toBe('warn')
    expect(dueLine(l({ dueState: 'OK', daysSinceLast: 1 }))).toEqual({ tone: 'ok', text: 'Last reviewed 1 day ago.' })
    expect(overallLabel('BELOW')).toBe('Below expectations')
  })
})
