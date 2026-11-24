import { describe, it, expect } from 'vitest'
import { formatMoney, normaliseGrade, moneyProblem, percentProblem, gradeProblem, lineGaps, lineProblem, rateProblem, saveProblem, missingForReview, toBody, inputsNeeded, calcProblem, emptyLine, type BookDraft, type LineDraft } from './pricingRules'

const line = (over: Partial<LineDraft> = {}): LineDraft => ({ ...emptyLine(), serviceCode: 'CS-CIPC-AR', baseFee: '1500', ...over })
const book = (over: Partial<BookDraft> = {}): BookDraft => ({ label: '2027', changeSummary: '', lines: [line()], rates: [], ...over })

describe('pricingRules', () => {
  it('formats money with a space between thousands', () => {
    expect(formatMoney(2400)).toBe('R 2 400.00')
    expect(formatMoney(1234567.5)).toBe('R 1 234 567.50')
    expect(formatMoney(0.5)).toBe('R 0.50')
    expect(formatMoney(null)).toBe('—')
  })

  it('cleans grades and checks amounts and percentages', () => {
    expect(normaliseGrade(' senior consultant ')).toBe('SENIOR_CONSULTANT')
    expect(gradeProblem('a')).toMatch(/2 to 30/)
    expect(gradeProblem('senior')).toBeNull()
    expect(moneyProblem('-1', 'Fee')).toMatch(/Fee must be/)
    expect(moneyProblem('', 'Fee')).toBeNull()
    expect(percentProblem('201', 'Surcharge')).toMatch(/0 and 200/)
    expect(percentProblem('101', 'Discount', 100)).toMatch(/0 and 100/)
  })

  it('says what each pricing model still needs', () => {
    expect(lineGaps(line({ baseFee: '' }))).toEqual(['a base fee'])
    expect(lineGaps(line({ pricingModel: 'HOURLY', baseFee: '' }))).toEqual(['an hourly grade'])
    expect(lineGaps(line({ pricingModel: 'HOURLY', baseFee: '', hourlyGrade: 'senior' }))).toEqual([])
    expect(lineGaps(line({ pricingModel: 'PER_SITE', baseFee: '3000' }))).toEqual(['a rate per unit'])
    expect(lineGaps(line({ pricingModel: 'PER_SITE', baseFee: '3000', unitRate: '800' }))).toEqual([])
    expect(lineGaps(line({ pricingModel: 'HYBRID', baseFee: '' }))).toHaveLength(1)
  })

  it('checks a line against the grades that have rates', () => {
    expect(lineProblem(line({ serviceCode: '' }), [])).toMatch(/Choose the service/)
    expect(lineProblem(line({ baseFee: '-5' }), [])).toMatch(/Base fee/)
    expect(lineProblem(line({ hourlyGrade: 'senior' }), ['JUNIOR'])).toMatch(/SENIOR has no hourly rate/)
    expect(lineProblem(line({ hourlyGrade: 'senior' }), ['SENIOR'])).toBeNull()
  })

  it('checks rates', () => {
    expect(rateProblem({ grade: '', hourlyRate: '900', costRate: '' })).toMatch(/Name the grade/)
    expect(rateProblem({ grade: 'senior', hourlyRate: '', costRate: '' })).toMatch(/hourly rate is required/)
    expect(rateProblem({ grade: 'senior', hourlyRate: '900', costRate: '-1' })).toMatch(/Cost rate/)
    expect(rateProblem({ grade: 'senior', hourlyRate: '900', costRate: '400' })).toBeNull()
  })

  it('refuses a draft with a service priced twice or a grade listed twice', () => {
    expect(saveProblem(book({ lines: [line(), line()] }))).toMatch(/priced twice/)
    expect(saveProblem(book({ rates: [{ grade: 'senior', hourlyRate: '1', costRate: '' }, { grade: 'SENIOR', hourlyRate: '2', costRate: '' }] }))).toMatch(/listed twice/)
    expect(saveProblem(book())).toBeNull()
  })

  it('says what a version needs before review', () => {
    expect(missingForReview(book({ lines: [] }), 1)).toEqual(['at least one priced service'])
    expect(missingForReview(book({ lines: [line({ baseFee: '' })] }), 1)[0]).toMatch(/1 line/)
    expect(missingForReview(book(), 2)).toEqual(['what changed'])
    expect(missingForReview(book({ changeSummary: 'Retainers up 6%' }), 2)).toEqual([])
  })

  it('sends cost rates only for an administrator', () => {
    const d = book({ rates: [{ grade: 'senior', hourlyRate: '900', costRate: '400' }] })
    expect(toBody(d, true).rates[0].costRate).toBe(400)
    expect(toBody(d, false).rates[0].costRate).toBeNull()
    expect(toBody(d, true).rates[0].grade).toBe('SENIOR')
    expect(toBody(d, true).lines[0].baseFee).toBe(1500)
    expect(toBody(d, true).lines[0].unitRate).toBeNull()
  })

  it('asks the calculator only for what the model uses', () => {
    expect(inputsNeeded('FIXED', false)).toEqual({ units: false, hours: false })
    expect(inputsNeeded('PER_SITE', false)).toEqual({ units: true, hours: false })
    expect(inputsNeeded('HOURLY', true)).toEqual({ units: false, hours: true })
    expect(inputsNeeded('HYBRID', true)).toEqual({ units: true, hours: true })
  })

  it('checks calculator inputs', () => {
    const p = { units: '', hours: '', complexityPct: '', discountPct: '', extras: [], disbursements: [] }
    expect(calcProblem(p, { units: true, hours: false }, 'PER_SITE')).toMatch(/how many units/)
    expect(calcProblem(p, { units: false, hours: true }, 'HOURLY')).toMatch(/how many hours/)
    expect(calcProblem({ ...p, discountPct: '101' }, { units: false, hours: false }, 'FIXED')).toMatch(/Discount/)
    expect(calcProblem({ ...p, extras: [{ description: 'verification', quantity: '0', unitPrice: '150' }] }, { units: false, hours: false }, 'RETAINER')).toMatch(/quantity and a price/)
    expect(calcProblem({ ...p, disbursements: [{ description: 'CIPC fee', amount: '', vatable: false }] }, { units: false, hours: false }, 'FIXED')).toMatch(/needs an amount/)
    expect(calcProblem({ ...p, units: '3', extras: [{ description: 'verification', quantity: '2', unitPrice: '150' }] }, { units: true, hours: false }, 'PER_SITE')).toBeNull()
  })
})
