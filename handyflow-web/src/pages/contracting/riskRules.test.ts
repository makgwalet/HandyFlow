import { describe, it, expect } from 'vitest'
import { LEVEL_LABEL, RULE_TYPES, SEVERITIES } from './riskRules'

describe('risk rule constants', () => {
  it('never labels an unassessed contract as low risk', () => {
    expect(LEVEL_LABEL.NOT_ASSESSED).toBe('Not assessed')
    expect(LEVEL_LABEL.NOT_ASSESSED).not.toMatch(/low/i)
  })
  it('offers every rule type the server understands, once', () => {
    const keys = RULE_TYPES.map(r => r[0])
    expect(new Set(keys).size).toBe(keys.length)
    expect(keys).toEqual(expect.arrayContaining(['REQUIRES_CLAUSE_CATEGORY', 'REQUIRES_PHRASE', 'FORBIDS_PHRASE', 'VALUE_ABOVE', 'TERM_LONGER_THAN_MONTHS', 'AUTO_RENEW_NOTICE_BELOW_DAYS', 'REQUIRES_END_DATE', 'REQUIRES_VALUE']))
  })
  it('lists severities lowest first', () => { expect(SEVERITIES).toEqual(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']) })
})
