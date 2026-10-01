import { describe, it, expect } from 'vitest'
import { resolveSafeActionUrl } from '../navigation/actionUrl'

// The hand-kept list this replaced (copied from the old NotificationDrawer).
const OLD = ['dashboard', 'customers', 'quotes', 'invoices', 'catalogue', 'billing', 'security', 'fuel', 'earthmoving', 'property', 'fleet',
  'bookings', 'accounting', 'settings', 'hr', 'clinic', 'events', 'contracts', 'expenses', 'invite', 'creative', 'desk', 'tasks', 'marketing',
  'recruiter', 'pos', 'accountant', 'ap', 'profile', 'recurring', 'supply-chain', 'projects']

describe('resolveSafeActionUrl', () => {
  it.each(OLD)('still accepts "%s" (nothing the old list allowed is now rejected)', base => {
    expect(resolveSafeActionUrl('/' + base)).not.toBeNull()
  })
  it('a Tasks deep link keeps its query (it used to fall back to the dashboard)', () => {
    expect(resolveSafeActionUrl('/tasks?board=b1&task=t9')).toBe('/tasks?board=b1&task=t9')
  })
  it.each(['/booking-agency', '/payroll-bureau', '/recruitment-agency', '/control-exceptions', '/complianceservices',
    '/legalcompliance', '/warehousing', '/training', '/agriculture'])('newly recognised: %s', u => {
    expect(resolveSafeActionUrl(u)).toBe(u)
  })
  it('other modules are still truncated to their base page', () => {
    expect(resolveSafeActionUrl('/desk/anything/deeper')).toBe('/desk')
    expect(resolveSafeActionUrl('/complianceservices/clients/123')).toBe('/complianceservices')
    expect(resolveSafeActionUrl('/recurring/new?x=1')).toBe('/recurring')
  })
  it('quotes and projects keep their deeper path', () => {
    expect(resolveSafeActionUrl('/quotes/42')).toBe('/quotes/42')
    expect(resolveSafeActionUrl('/projects/3f2b1c9a-8d4e-4f6a-9b7c-1a2b3c4d5e6f')).toBe('/projects/3f2b1c9a-8d4e-4f6a-9b7c-1a2b3c4d5e6f')
  })
  it('unknown, empty and root links are rejected', () => {
    for (const u of ['/nope', '/nope?x=1', '', '/', '?board=1']) expect(resolveSafeActionUrl(u)).toBeNull()
  })
  it('the base comes from the path only: a query on an unknown base does not make it known', () => {
    expect(resolveSafeActionUrl('/unknown?next=/tasks')).toBeNull()
  })
})
