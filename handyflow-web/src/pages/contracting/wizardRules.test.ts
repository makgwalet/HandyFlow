import { describe, it, expect } from 'vitest'
import {
  emptyDraft, emptyParty, termsFor, validateStep, firstInvalidStep, buildContractPayload,
  buildPartyPayload, readinessNotes, unfilledVariables,
} from './wizardRules'

describe('wizardRules', () => {
  it('requires a title, and a template when starting from one', () => {
    const d = emptyDraft()
    expect(validateStep('basics', d).title).toBeTruthy()
    expect(validateStep('basics', { ...d, title: 'X', source: 'TEMPLATE' }).templateId).toBeTruthy()
    expect(validateStep('basics', { ...d, title: 'X' })).toEqual({})
  })

  it('validates parties', () => {
    const d = { ...emptyDraft(), parties: [{ ...emptyParty(), email: 'nope', signingOrder: '0' }] }
    const e = validateStep('parties', d)
    expect(e['party0.fullName']).toBeTruthy()
    expect(e['party0.email']).toBeTruthy()
    expect(e['party0.signingOrder']).toBeTruthy()
  })

  it('validates terms', () => {
    const d = { ...emptyDraft(), startDate: '2026-02-01', endDate: '2026-01-01', valueAmount: '-5', autoRenew: true, renewalNoticeDays: 'x' }
    const e = validateStep('terms', d)
    expect(e.endDate).toBeTruthy(); expect(e.valueAmount).toBeTruthy(); expect(e.renewalNoticeDays).toBeTruthy()
  })

  it('hides value and renewal for an NDA and renewal for employment', () => {
    expect(termsFor('NDA')).toEqual({ value: false, renewal: false })
    expect(termsFor('EMPLOYMENT')).toEqual({ value: true, renewal: false })
    expect(termsFor('LEASE')).toEqual({ value: true, renewal: true })
  })

  it('does not validate or send fields that are hidden for the type', () => {
    const d = { ...emptyDraft(), title: 'NDA', contractType: 'NDA', valueAmount: '-9', autoRenew: true }
    expect(validateStep('terms', d)).toEqual({})
    const p = buildContractPayload(d)
    expect(p.valueAmount).toBeNull(); expect(p.autoRenew).toBe(false); expect(p.renewalNoticeDays).toBeNull()
  })

  it('builds the payload and ignores template variables when starting from scratch', () => {
    const d = { ...emptyDraft(), title: ' Deal ', variables: { a: '1' }, valueAmount: '1500.5', autoRenew: true, renewalNoticeDays: '45' }
    const p = buildContractPayload(d)
    expect(p).toMatchObject({ title: 'Deal', templateId: null, variables: null, valueAmount: 1500.5, currency: 'ZAR', autoRenew: true, renewalNoticeDays: 45 })
    const t = buildContractPayload({ ...d, source: 'TEMPLATE', templateId: 't1' })
    expect(t).toMatchObject({ templateId: 't1', variables: { a: '1' } })
  })

  it('builds a party payload and drops the company name for individuals', () => {
    const p = buildPartyPayload({ ...emptyParty(2), fullName: ' Jo ', companyName: 'Acme', email: '' })
    expect(p).toMatchObject({ fullName: 'Jo', companyName: null, email: null, signingOrder: 2 })
    expect(buildPartyPayload({ ...emptyParty(), fullName: 'Jo', partyType: 'COMPANY', companyName: 'Acme' }).companyName).toBe('Acme')
  })

  it('finds the first invalid step', () => {
    expect(firstInvalidStep(emptyDraft())).toBe('basics')
    expect(firstInvalidStep({ ...emptyDraft(), title: 'X' })).toBeNull()
    expect(firstInvalidStep({ ...emptyDraft(), title: 'X', parties: [emptyParty()] })).toBe('parties')
  })

  it('lists readiness notes as guidance', () => {
    const d = { ...emptyDraft(), title: 'X', source: 'TEMPLATE' as const, templateId: 't' }
    const notes = readinessNotes(d, ['client_name'])
    expect(notes.join(' ')).toMatch(/No parties/)
    expect(notes.join(' ')).toMatch(/client name/)
    expect(unfilledVariables(['a', 'b'], { a: ' ', b: 'x' })).toEqual(['a'])
  })
})
