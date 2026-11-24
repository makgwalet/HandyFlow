// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))

import ArmouryTab from './ArmouryTab'
import { useAuthStore } from '../../store/auth.store'

const firearm = (over: Record<string, unknown>) => ({
  id: 'f1', firearmSerial: 'SN-1', firearmType: 'Handgun', makeModel: null, sapsLicenseNumber: 'L1',
  licenseExpiry: '2030-01-01', licenseExpired: false, assignedGuardId: null, assignedGuardName: null,
  status: 'IN_ARMOURY', lastServiceAt: null, nextServiceDueAt: null, notes: null, createdAt: '2026-01-01T00:00:00Z', ...over,
})

function setup(list: Record<string, unknown>[]) {
  api.get.mockImplementation((url: string) => {
    if (url.includes('/armoury')) return Promise.resolve({ data: { data: { content: list } } })
    return Promise.resolve({ data: { data: { content: [{ id: 'g1', fullName: 'Thabo' }, { id: 'g2', fullName: 'Sipho' }], last: true } } })
  })
  api.post.mockResolvedValue({ data: {} })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><ArmouryTab /></QueryClientProvider>)
}

afterEach(() => cleanup())
beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.setState({ user: { permissions: ['SECURITY_MANAGE', 'SECURITY_ARMOURY_ISSUE'] } } as never)
})

describe('ArmouryTab', () => {
  it('reports a firearm lost with the SAPS case number', async () => {
    setup([firearm({})])
    fireEvent.click(await screen.findByRole('button', { name: /report lost/i }))
    const submit = await screen.findByRole('button', { name: 'Report lost' })
    expect((submit as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('What happened'), { target: { value: 'Stolen from vehicle' } })
    fireEvent.change(screen.getByLabelText('SAPS case number'), { target: { value: ' CAS 5/11/2026 ' } })
    fireEvent.click(submit)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/armoury/f1/report-lost',
      { notes: 'Stolen from vehicle', sapsCaseNumber: 'CAS 5/11/2026' }))
  })

  it('shows an overdue SAPS report and lets the case number be recorded', async () => {
    setup([firearm({ status: 'LOST', sapsReportOverdue: true })])
    expect(await screen.findByText(/SAPS report overdue/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'SAPS case' }))
    fireEvent.change(await screen.findByLabelText('Case number'), { target: { value: 'CAS 9' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/armoury/f1/saps-case', { caseNumber: 'CAS 9' }))
  })

  it('will not return a firearm with rounds missing until they are explained', async () => {
    setup([firearm({ status: 'ISSUED', assignedGuardId: 'g1', assignedGuardName: 'Thabo', ammoRoundsIssued: 30, ammoCalibre: '9mm' })])
    expect(await screen.findByText(/30 x 9mm out/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /^return$/i }))
    const ret = await screen.findByRole('button', { name: 'Return to Armoury' })
    expect((ret as HTMLButtonElement).disabled).toBe(true)                                   // count not entered yet
    fireEvent.change(screen.getByLabelText('Rounds handed back'), { target: { value: '28' } })
    expect((ret as HTMLButtonElement).disabled).toBe(true)                                   // short, no explanation
    fireEvent.change(screen.getByLabelText('Explain the missing rounds'), { target: { value: 'Fired at intruder' } })
    expect((ret as HTMLButtonElement).disabled).toBe(false)
  })

  it('sends rounds and calibre when issuing', async () => {
    setup([firearm({})])
    fireEvent.click(await screen.findByRole('button', { name: /^issue$/i }))
    const selects = await screen.findAllByRole('combobox')
    fireEvent.change(selects[0], { target: { value: 'g1' } })
    fireEvent.change(selects[1], { target: { value: 'g2' } })
    fireEvent.change(screen.getByLabelText('Rounds issued'), { target: { value: '30' } })
    fireEvent.change(screen.getByLabelText('Calibre'), { target: { value: '9mm' } })
    fireEvent.click(screen.getByRole('button', { name: 'Issue Firearm' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/armoury/f1/issue',
      expect.objectContaining({ guardId: 'g1', witnessedByGuardId: 'g2', ammoRounds: 30, ammoCalibre: '9mm' })))
  })
})
