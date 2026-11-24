import { describe, it, expect } from 'vitest'
import { statusLabel, validUntilProblem, lineProblem, draftProblem, missingToSubmit, toBody, actionsFor, approveNeedsReason, actionProblem, expiryText, emptyQuoteLine, daysBetween, type QuoteDraft, type ServiceBasis } from './quoteRules'

const TODAY = '2027-03-01'
const bases: ServiceBasis[] = [
  { serviceCode: 'CS-CIPC-AR', pricingModel: 'FIXED', hourlyGrade: null },
  { serviceCode: 'CS-OHS', pricingModel: 'PER_SITE', hourlyGrade: null },
  { serviceCode: 'CS-ADV', pricingModel: 'HOURLY', hourlyGrade: 'SENIOR' },
]
const draft = (over: Partial<QuoteDraft> = {}): QuoteDraft => ({ title: '', validUntil: '2027-03-31', notes: '', terms: '', lines: [{ ...emptyQuoteLine(), serviceCode: 'CS-CIPC-AR' }], ...over })

describe('quoteRules', () => {
  it('words the statuses, expired included', () => {
    expect(statusLabel('PENDING_APPROVAL')).toBe('Waiting for approval')
    expect(statusLabel('EXPIRED')).toBe('Expired')
    expect(statusLabel('ODD')).toBe('ODD')
  })

  it('counts days between dates', () => {
    expect(daysBetween('2027-03-01', '2027-03-31')).toBe(30)
    expect(daysBetween('2027-03-01', '2027-02-28')).toBe(-1)
  })

  it('checks how long a quote stands', () => {
    expect(validUntilProblem('', TODAY)).toMatch(/how long/)
    expect(validUntilProblem('2027-02-28', TODAY)).toMatch(/passed/)
    expect(validUntilProblem('2027-03-01', TODAY)).toBeNull()
    expect(validUntilProblem('2028-02-29', TODAY)).toBeNull()
    expect(validUntilProblem('2028-03-01', TODAY)).toMatch(/365/)
  })

  it('asks for what each line needs and nothing else', () => {
    const l = { ...emptyQuoteLine(), serviceCode: 'CS-OHS' }
    expect(lineProblem({ ...l, serviceCode: '' }, undefined)).toMatch(/Choose the service/)
    expect(lineProblem(l, undefined)).toMatch(/no price in the price book/)
    expect(lineProblem(l, bases[1])).toMatch(/how many units/)
    expect(lineProblem({ ...l, units: '3' }, bases[1])).toBeNull()
    expect(lineProblem({ ...emptyQuoteLine(), serviceCode: 'CS-ADV' }, bases[2])).toMatch(/how many hours/)
    expect(lineProblem({ ...emptyQuoteLine(), serviceCode: 'CS-CIPC-AR', discountPct: '101' }, bases[0])).toMatch(/Discount/)
  })

  it('refuses a draft with a service twice or a date that has passed', () => {
    const l = { ...emptyQuoteLine(), serviceCode: 'CS-CIPC-AR' }
    expect(draftProblem(draft({ lines: [l, l] }), bases, TODAY)).toMatch(/on this quote twice/)
    expect(draftProblem(draft({ validUntil: '2027-01-01' }), bases, TODAY)).toMatch(/passed/)
    expect(draftProblem(draft(), bases, TODAY)).toBeNull()
  })

  it('says what is missing before submitting', () => {
    expect(missingToSubmit(draft({ lines: [], validUntil: '' }), TODAY)).toEqual(['at least one service', 'a date it stands until'])
    expect(missingToSubmit(draft(), TODAY)).toEqual([])
  })

  it('sends numbers as numbers and blanks as null', () => {
    const body = toBody(draft({ title: ' Annual ', lines: [{ ...emptyQuoteLine(), serviceCode: 'CS-OHS', units: '3', discountPct: '12', extras: [{ description: ' verify ', quantity: '2', unitPrice: '150' }],
      disbursements: [{ description: 'Travel', amount: '600', vatable: true }] }] }))
    expect(body.title).toBe('Annual')
    expect(body.lines[0].units).toBe(3)
    expect(body.lines[0].hours).toBeNull()
    expect(body.lines[0].discountPct).toBe(12)
    expect(body.lines[0].extras).toEqual([{ description: 'verify', quantity: 2, unitPrice: 150 }])
    expect(body.lines[0].disbursements).toEqual([{ description: 'Travel', amount: 600, vatable: true }])
  })

  it('offers each person only what their status and permissions allow', () => {
    const ids = (s: string, m: boolean, a: boolean) => actionsFor(s, m, a).map(x => x.id)
    expect(ids('DRAFT', true, false)).toEqual(['submit', 'withdraw'])
    expect(ids('PENDING_APPROVAL', true, false)).toEqual(['withdraw'])
    expect(ids('PENDING_APPROVAL', true, true)).toEqual(['approve', 'reject', 'withdraw'])
    expect(ids('APPROVED', true, false)).toEqual(['sent', 'withdraw'])
    expect(ids('SENT', true, false)).toEqual(['accept', 'decline', 'withdraw'])
    expect(ids('ACCEPTED', true, true)).toEqual([])
    expect(ids('EXPIRED', true, true)).toEqual([])
    expect(ids('DRAFT', false, false)).toEqual([])
  })

  it('asks for a reason when the approver wrote or submitted the quote', () => {
    expect(approveNeedsReason({ createdBy: 'u1', submittedBy: 'u2' }, 'u1')).toBe(true)
    expect(approveNeedsReason({ createdBy: 'u2', submittedBy: 'u1' }, 'u1')).toBe(true)
    expect(approveNeedsReason({ createdBy: 'u2', submittedBy: 'u3' }, 'u1')).toBe(false)
    const approve = actionsFor('PENDING_APPROVAL', true, true)[0]
    const accept = actionsFor('SENT', true, false)[0]
    expect(actionProblem(approve, '', true, 'short')).toMatch(/at least 15/)
    expect(actionProblem(approve, '', true, 'Only partner in the practice')).toBeNull()
    expect(actionProblem(approve, '', false, '')).toBeNull()
    expect(actionProblem(accept, 'ok', false, '')).toMatch(/at least 10/)
    expect(actionProblem(accept, 'Accepted by email 3 March', false, '')).toBeNull()
  })

  it('says how long a quote has left', () => {
    expect(expiryText('2027-03-31', TODAY, 'SENT')).toBe('Stands until 2027-03-31 (30 days)')
    expect(expiryText('2027-03-02', TODAY, 'SENT')).toBe('Stands until 2027-03-02 (1 day)')
    expect(expiryText('2027-03-01', TODAY, 'SENT')).toBe('Stands until today')
    expect(expiryText('2027-02-20', TODAY, 'EXPIRED')).toBe('Expired 2027-02-20')
    expect(expiryText('2027-02-20', TODAY, 'ACCEPTED')).toBe('Stood until 2027-02-20')
    expect(expiryText(null, TODAY, 'DRAFT')).toBe('')
  })
})
