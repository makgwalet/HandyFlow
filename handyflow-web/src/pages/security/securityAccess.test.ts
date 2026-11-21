import { describe, it, expect } from 'vitest'
import { securityAccess } from './securityAccess'

describe('securityAccess', () => {
  it('grants nothing without permissions', () => {
    for (const p of [undefined, null, []]) expect(Object.values(securityAccess(p)).every(v => v === false)).toBe(true)
  })
  it('maps each permission to exactly one flag', () => {
    const map: Record<string, keyof ReturnType<typeof securityAccess>> = {
      SECURITY_PAYROLL_READ: 'payrollRead', SECURITY_PAYROLL_MANAGE: 'payrollManage',
      SECURITY_PAYROLL_APPROVE: 'payrollApprove', SECURITY_PAYROLL_PAY: 'payrollPay',
      SECURITY_ARMOURY_ISSUE: 'armouryIssue', SECURITY_SCREENING_DECIDE: 'screeningDecide',
      SECURITY_GUARD_BANKING_MANAGE: 'bankingManage', SECURITY_INTEGRATIONS_MANAGE: 'integrationsManage',
      SECURITY_ADMIN: 'securityAdmin',
    }
    for (const [perm, flag] of Object.entries(map)) {
      const a = securityAccess([perm])
      expect(a[flag]).toBe(true)
      expect(Object.entries(a).filter(([, v]) => v).map(([k]) => k)).toEqual([flag])
    }
  })
  it('SECURITY_MANAGE alone no longer opens the split-out actions', () => {
    expect(Object.values(securityAccess(['SECURITY_MANAGE', 'SECURITY_READ'])).some(Boolean)).toBe(false)
  })
})
