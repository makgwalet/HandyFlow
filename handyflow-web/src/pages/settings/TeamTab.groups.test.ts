import { describe, expect, it } from 'vitest'
import { groupLabel, groupPermissions } from './TeamTab'

const perm = (name: string, group?: string) => ({ id: name, name, description: name, group })

describe('permission grouping', () => {
  it('keeps every permission, including modules that had no hard-coded group', () => {
    const all = [perm('USER_READ', 'USER'), perm('PAYROLL_RUN', 'PAYROLL'), perm('FLEET_VEHICLE_READ', 'FLEET'), perm('HR_EMPLOYEE_READ', 'HR')]
    const grouped = groupPermissions(all)
    expect(grouped.flatMap(([, list]) => list).map(p => p.name).sort()).toEqual(all.map(p => p.name).sort())
    expect(grouped.map(([g]) => g).sort()).toEqual(['FLEET', 'HR', 'PAYROLL', 'USER'])
  })

  it('falls back to the name prefix when the API sends no group', () => {
    expect(groupPermissions([perm('INSURANCE_CLAIM_READ')]).map(([g]) => g)).toEqual(['INSURANCE'])
  })

  it('labels known prefixes and prettifies unknown ones', () => {
    expect(groupLabel('CUSTOMER')).toBe('CRM')
    expect(groupLabel('INSURANCE')).toBe('Insurance')
  })
})
