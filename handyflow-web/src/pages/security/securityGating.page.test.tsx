import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))

import { useAuthStore } from '../../store/auth.store'
import PayrollTab from './PayrollTab'
import ArmouryTab from './ArmouryTab'

function as(permissions: string[]) {
  useAuthStore.setState({ user: { permissions } } as never)
}
function mount(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}
const period = (status: string) => ({
  id: 'p1', name: 'Nov', periodType: 'MONTHLY', periodStart: '2026-11-01', periodEnd: '2026-11-30', status,
  totalHours: null, totalAmountCents: null, totalAmountZar: null, lineItemCount: 0, approvedBy: null, approvedAt: null,
  exportedAt: null, exportFormat: null, createdAt: '2026-11-01T00:00:00Z',
})
const firearm = (status: string) => ({
  id: 'f1', firearmSerial: 'SN-1', firearmType: 'Handgun', makeModel: null, sapsLicenseNumber: 'L1',
  licenseExpiry: '2030-01-01', licenseExpired: false, assignedGuardId: null, assignedGuardName: null,
  status, lastServiceAt: null, nextServiceDueAt: null, notes: null, createdAt: '2026-01-01T00:00:00Z',
})

afterEach(() => cleanup())
beforeEach(() => { vi.clearAllMocks(); api.post.mockResolvedValue({ data: {} }) })

describe('Payroll gating', () => {
  function payroll(status: string) {
    api.get.mockImplementation(() => Promise.resolve({ data: { data: { content: [period(status)] } } }))
  }
  it('shows a no-access message without the payroll read permission', async () => {
    payroll('DRAFT'); as(['SECURITY_MANAGE'])
    mount(<PayrollTab />)
    expect(await screen.findByText(/do not have access to guard payroll/i)).toBeTruthy()
  })
  it('read-only sees periods but no create, approve or pay', async () => {
    payroll('DRAFT'); as(['SECURITY_PAYROLL_READ'])
    mount(<PayrollTab />)
    await screen.findByText('Nov')
    expect(screen.queryByRole('button', { name: /new period/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /approve/i })).toBeNull()
  })
  it('approver sees Approve, preparer sees New Period', async () => {
    payroll('DRAFT'); as(['SECURITY_PAYROLL_READ', 'SECURITY_PAYROLL_APPROVE', 'SECURITY_PAYROLL_MANAGE'])
    mount(<PayrollTab />)
    expect(await screen.findByRole('button', { name: /approve/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /new period/i })).toBeTruthy()
  })
  it('Mark Paid needs the pay permission', async () => {
    payroll('EXPORTED'); as(['SECURITY_PAYROLL_READ'])
    mount(<PayrollTab />)
    await screen.findByText('Nov')
    expect(screen.queryByRole('button', { name: /mark paid/i })).toBeNull()
    cleanup(); as(['SECURITY_PAYROLL_READ', 'SECURITY_PAYROLL_PAY'])
    mount(<PayrollTab />)
    expect(await screen.findByRole('button', { name: /mark paid/i })).toBeTruthy()
  })
})

describe('Armoury gating', () => {
  function armoury(status: string) {
    api.get.mockImplementation((url: string) => url.includes('/armoury')
      ? Promise.resolve({ data: { data: { content: [firearm(status)] } } })
      : Promise.resolve({ data: { data: { content: [], last: true } } }))
  }
  it('Issue needs the armoury issue permission', async () => {
    armoury('IN_ARMOURY'); as(['SECURITY_MANAGE'])
    mount(<ArmouryTab />)
    await screen.findByText('SN-1')
    expect(screen.queryByRole('button', { name: /^issue$/i })).toBeNull()
    cleanup(); as(['SECURITY_ARMOURY_ISSUE'])
    mount(<ArmouryTab />)
    expect(await screen.findByRole('button', { name: /issue/i })).toBeTruthy()
  })
  it('Return needs the armoury issue permission', async () => {
    armoury('ISSUED'); as(['SECURITY_MANAGE'])
    mount(<ArmouryTab />)
    await screen.findByText('SN-1')
    expect(screen.queryByRole('button', { name: /^return$/i })).toBeNull()
    cleanup(); as(['SECURITY_ARMOURY_ISSUE'])
    mount(<ArmouryTab />)
    expect(await screen.findByRole('button', { name: /return/i })).toBeTruthy()
  })
})
