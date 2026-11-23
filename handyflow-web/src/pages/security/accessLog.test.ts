import { describe, it, expect } from 'vitest'
import { readEntries, who, what, subject, whenLabel, isSupportSession, meta, emptyLine, KIND_OPTIONS, type AccessLogEntry } from './accessLogLogic'

const e = (o: Partial<AccessLogEntry> = {}): AccessLogEntry => ({
  id: 'e1', occurredAt: '2026-11-22T06:35:00Z', actorId: 'u1', actorType: 'USER', entityType: 'GUARD', entityId: '22222222-2222-4222-8222-222222222222',
  action: 'EVIDENCE_DOWNLOADED', metadata: '{"kind":"SCREENING_EVIDENCE"}', actorName: 'Thandi Admin', guardName: 'Sipho Dlamini', ...o,
})

describe('access log wording', () => {
  it('keeps only well-formed entries', () => {
    expect(readEntries([e(), { id: 1 }, null, { id: 'x' }])).toHaveLength(1)
    expect(readEntries('<html>')).toEqual([])
    expect(readEntries(undefined)).toEqual([])
  })
  it('reads metadata safely', () => {
    expect(meta(e({ metadata: 'not json' }))).toEqual({})
    expect(meta(e({ metadata: '[1]' }))).toEqual({})
    expect(meta(e({ metadata: null }))).toEqual({})
    expect(meta(e())).toEqual({ kind: 'SCREENING_EVIDENCE' })
  })
  it('names who: the person, a support session, the system, or someone since removed', () => {
    expect(who(e())).toBe('Thandi Admin')
    expect(who(e({ actorName: null, actorId: null, metadata: '{"supportSession":true}' }))).toBe('Support staff')
    expect(who(e({ actorName: 'Ops Person', metadata: '{"supportSession":true}' }))).toBe('Ops Person (support session)')
    expect(who(e({ actorName: null, actorId: null, actorType: 'SYSTEM', metadata: '{}' }))).toBe('System')
    expect(who(e({ actorName: null }))).toBe('A user no longer on file')
    expect(isSupportSession(e({ metadata: '{"supportSession":true}' }))).toBe(true)
    expect(isSupportSession(e())).toBe(false)
  })
  it('says what was opened', () => {
    expect(what(e())).toBe('Downloaded screening evidence')
    expect(what(e({ metadata: '{"kind":"INCIDENT_EVIDENCE"}' }))).toBe('Downloaded incident evidence')
    expect(what(e({ metadata: '{}' }))).toBe('Downloaded evidence')
    expect(what(e({ action: 'EXPORTED', metadata: '{"format":"CSV"}' }))).toBe('Exported payroll (CSV)')
    expect(what(e({ action: 'PAY_STATEMENT_VIEWED' }))).toBe('Opened a pay statement')
    expect(what(e({ action: 'VETTING_VIEWED', metadata: '{"kind":"COMPLIANCE_PDF"}' }))).toBe('Opened a vetting compliance PDF')
    expect(what(e({ action: 'VETTING_VIEWED', metadata: '{}' }))).toBe('Opened a vetting record')
    expect(what(e({ action: 'DECLINED_REGISTER_VIEWED' }))).toBe('Opened the declined-engagement register')
    expect(what(e({ action: 'PII_REVEALED', metadata: '{"field":"BANK_ACCOUNT","reason":"Payroll query"}' }))).toBe("Revealed a guard's bank account number. Reason: Payroll query")
    expect(what(e({ action: 'PII_REVEALED', metadata: '{"field":"ID_NUMBER","reason":""}' }))).toBe("Revealed a guard's ID number")
    expect(what(e({ action: 'SOMETHING_NEW', metadata: '{}' }))).toBe('something new')
  })
  it('names the subject: the guard by name, otherwise the kind and the start of the id', () => {
    expect(subject(e())).toBe('Sipho Dlamini')
    expect(subject(e({ guardName: null }))).toBe('Guard 22222222')
    expect(subject(e({ entityType: 'PAYROLL_PERIOD' }))).toBe('Payroll period 22222222')
    expect(subject(e({ entityType: 'DECLINED_REGISTER' }))).toBe('Company register')
  })
  it('shows time in South African time', () => {
    expect(whenLabel('2026-11-22T22:30:00Z')).toContain('23')  // 00:30 on 23 Nov in Johannesburg
    expect(whenLabel('nonsense')).toBe('')
  })
  it('offers a kind filter that matches what the server accepts', () => {
    expect(KIND_OPTIONS.map(o => o.value)).toEqual(['', 'GUARD', 'COMPLAINT', 'INCIDENT', 'PAYROLL_PERIOD', 'PRINCIPAL', 'DECLINED_REGISTER'])
    expect(emptyLine('')).toMatch(/Nothing sensitive/)
    expect(emptyLine('GUARD')).toMatch(/of this kind/)
  })
})
