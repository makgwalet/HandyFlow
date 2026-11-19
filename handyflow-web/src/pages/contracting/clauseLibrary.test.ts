import { describe, it, expect } from 'vitest'
import { clauseAttention, filterClauses, viewCounts, type LibraryClause } from './clauseLibrary'

const today = new Date('2026-11-19')
const c = (o: Partial<LibraryClause> = {}): LibraryClause => ({ title: 'Force majeure', category: 'OTHER', body: '<p>Events beyond control</p>', status: 'PUBLISHED', legalStatus: 'REVIEWED', ...o })

describe('clauseAttention', () => {
  it('flags expired reviews and missing required evidence on live clauses', () => {
    expect(clauseAttention(c({ legalStatus: 'REVIEW_EXPIRED' }), today)).toBe('Review expired')
    expect(clauseAttention(c({ evidenceRequired: true }), today)).toBe('Evidence missing')
    expect(clauseAttention(c({ evidenceRequired: true, evidenceDocumentId: 'd' }), today)).toBeNull()
  })
  it('flags a review that is due soon or past, but not one far away', () => {
    expect(clauseAttention(c({ nextReviewDate: '2026-11-29' }), today)).toBe('Review due in 10 days')
    expect(clauseAttention(c({ nextReviewDate: '2026-11-19' }), today)).toBe('Review due today')
    expect(clauseAttention(c({ nextReviewDate: '2026-10-01' }), today)).toBe('Review expired')
    expect(clauseAttention(c({ nextReviewDate: '2027-06-01' }), today)).toBeNull()
  })
  it('does not flag drafts or archived clauses', () => {
    expect(clauseAttention(c({ status: 'DRAFT', evidenceRequired: true }), today)).toBeNull()
    expect(clauseAttention(c({ status: 'WITHDRAWN', legalStatus: 'WITHDRAWN' }), today)).toBeNull()
  })
})

describe('filterClauses and viewCounts', () => {
  const list = [
    c({ title: 'A published' }),
    c({ title: 'B draft', status: 'DRAFT', legalStatus: 'NOT_REVIEWED' }),
    c({ title: 'C old', status: 'SUPERSEDED', legalStatus: 'SUPERSEDED' }),
    c({ title: 'D expired', legalStatus: 'REVIEW_EXPIRED' }),
  ]
  it('hides archived clauses from the default view', () => {
    expect(filterClauses(list, 'ACTIVE', '', today).map(x => x.title)).toEqual(['A published', 'B draft', 'D expired'])
    expect(filterClauses(list, 'ARCHIVED', '', today).map(x => x.title)).toEqual(['C old'])
    expect(filterClauses(list, 'ATTENTION', '', today).map(x => x.title)).toEqual(['D expired'])
  })
  it('searches title, area, source and wording ignoring markup and case', () => {
    expect(filterClauses(list, 'ALL', 'BEYOND', today)).toHaveLength(4)
    expect(filterClauses(list, 'ALL', 'draft', today).map(x => x.title)).toEqual(['B draft'])
    expect(filterClauses([c({ legalArea: 'Employment' })], 'ALL', 'employ', today)).toHaveLength(1)
    expect(filterClauses(list, 'ALL', '<p>', today)).toHaveLength(0)
  })
  it('counts every view', () => {
    expect(viewCounts(list, today)).toEqual({ ACTIVE: 3, ATTENTION: 1, PUBLISHED: 2, DRAFT: 1, ARCHIVED: 1, ALL: 4 })
  })
})
