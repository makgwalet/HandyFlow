import { describe, it, expect } from 'vitest'
import { hoursProblem, slaProblem, itemProblem, duplicateItem, missingForReview, saveProblem, toBody, type ServiceDraft } from './serviceRules'

const draft = (over: Partial<ServiceDraft> = {}): ServiceDraft => ({
  name: 'CIPC annual return', category: 'CIPC', description: '', applicabilityNote: '', exclusions: '', estimatedHours: '3.5', slaDays: '14',
  responsibleRole: 'CONSULTANT', requiresReview: true, pricingModel: 'FIXED', billingTrigger: 'ON_DELIVERY', changeSummary: '',
  items: [{ kind: 'DELIVERABLE', text: 'Annual return filed', mandatory: true }], ...over,
})

describe('serviceRules', () => {
  it('checks hours', () => {
    expect(hoursProblem('')).toBeNull()
    expect(hoursProblem('3.5')).toBeNull()
    expect(hoursProblem('0')).toMatch(/more than 0/)
    expect(hoursProblem('2001')).toMatch(/2000/)
    expect(hoursProblem('abc')).toMatch(/more than 0/)
  })

  it('checks turnaround', () => {
    expect(slaProblem('')).toBeNull()
    expect(slaProblem('30')).toBeNull()
    expect(slaProblem('0')).toMatch(/1 to 365/)
    expect(slaProblem('1.5')).toMatch(/whole number/)
    expect(slaProblem('400')).toMatch(/1 to 365/)
  })

  it('checks an item and duplicates', () => {
    expect(itemProblem({ kind: 'CHECKLIST', text: 'ab', mandatory: true })).toMatch(/3 characters/)
    expect(itemProblem({ kind: 'CHECKLIST', text: 'x'.repeat(301), mandatory: true })).toMatch(/300/)
    expect(itemProblem({ kind: 'CHECKLIST', text: 'Collect ID', mandatory: true })).toBeNull()
    const a = { kind: 'CHECKLIST', text: 'Collect ID', mandatory: true }
    expect(duplicateItem([a, { ...a, text: ' collect id ' }])).toMatch(/twice/)
    expect(duplicateItem([a, { ...a, kind: 'DOCUMENT' }])).toBeNull()
  })

  it('says what review still needs', () => {
    expect(missingForReview(draft(), 1)).toEqual([])
    expect(missingForReview(draft({ name: '', category: '', items: [] }), 1)).toEqual(['name', 'category', 'at least one deliverable'])
    expect(missingForReview(draft(), 2)).toEqual(['what changed'])
    expect(missingForReview(draft({ items: [{ kind: 'CHECKLIST', text: 'Step', mandatory: true }] }), 1)).toEqual(['at least one deliverable'])
  })

  it('finds the first thing that blocks saving', () => {
    expect(saveProblem(draft())).toBeNull()
    expect(saveProblem(draft({ estimatedHours: '0' }))).toMatch(/more than 0/)
    expect(saveProblem(draft({ items: [{ kind: 'DELIVERABLE', text: 'x', mandatory: true }] }))).toMatch(/3 characters/)
  })

  it('builds the request with numbers and empty choices as null', () => {
    const b = toBody(draft({ estimatedHours: '', slaDays: '14', category: '' }))
    expect(b.estimatedHours).toBeNull()
    expect(b.slaDays).toBe(14)
    expect(b.category).toBeNull()
    expect(b.items).toEqual([{ kind: 'DELIVERABLE', text: 'Annual return filed', mandatory: true }])
  })
})
