// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientProfileTab from './ClientProfileTab'

const get = vi.fn()
const put = vi.fn()
const post = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'u1' } }) }))

const profile = {
  clientId: 'c1', legalName: 'Acme (Pty) Ltd', tradingName: null, registrationNumber: '2015/123456/07', entityType: 'PTY_LTD',
  taxNumber: null, vatNumber: null, registeredAddress: null, postalAddress: null, industry: null, employeeCount: 12,
  turnoverBand: null, riskRating: null, onboardingStatus: 'NOT_STARTED',
  sources: [
    { fieldName: 'legalName', source: 'STAFF_ENTERED', method: null, reference: null, sourceDate: null, recordedBy: 'u1', verified: false, confirmedByEnterer: false, verifiedBy: null, exceptionReason: null },
    { fieldName: 'registrationNumber', source: 'DOCUMENT', method: null, reference: 'CIPC certificate', sourceDate: null, recordedBy: 'u9', verified: false, confirmedByEnterer: false, verifiedBy: null, exceptionReason: null },
  ],
  unconfirmedFields: ['employeeCount', 'entityType', 'legalName', 'registrationNumber'],
}

function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientProfileTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientProfileTab', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); put.mockReset(); post.mockReset(); get.mockResolvedValue({ data: { data: profile } }); post.mockResolvedValue({ data: { data: profile } }); put.mockResolvedValue({ data: { data: profile } }) })

  it('shows the profile and how much is confirmed', async () => {
    renderTab()
    expect(await screen.findByDisplayValue('Acme (Pty) Ltd')).toBeTruthy()
    expect(screen.getByText(/4 of 9 key facts recorded, 0 confirmed/)).toBeTruthy()
  })

  it('blocks saving a VAT number that does not start with 4', async () => {
    renderTab()
    await screen.findByDisplayValue('Acme (Pty) Ltd')
    const vat = screen.getAllByRole('textbox')[4]
    fireEvent.change(vat, { target: { value: '9012345678' } })
    expect(screen.getByText('VAT numbers start with 4')).toBeTruthy()
    expect((screen.getByText('Save profile') as HTMLButtonElement).disabled).toBe(true)
  })

  it('saves the profile with the employee count as a number', async () => {
    renderTab()
    await screen.findByDisplayValue('Acme (Pty) Ltd')
    fireEvent.click(screen.getByText('Save profile'))
    await waitFor(() => expect(put).toHaveBeenCalled())
    const [url, body] = put.mock.calls[0]
    expect(url).toBe('/api/v1/compliance-services/clients/c1/profile')
    expect(body.employeeCount).toBe(12)
    expect(body.legalName).toBe('Acme (Pty) Ltd')
  })

  it('lets someone else confirm a fact straight away', async () => {
    renderTab()
    await screen.findByDisplayValue('Acme (Pty) Ltd')
    const confirmButtons = screen.getAllByText('Confirm')
    fireEvent.click(confirmButtons[1])   // registrationNumber was entered by u9
    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/api/v1/compliance-services/clients/c1/profile/sources/registrationNumber/confirm', { exceptionReason: null }))
  })

  it('stops the person who entered a fact confirming it without a reason', async () => {
    renderTab()
    await screen.findByDisplayValue('Acme (Pty) Ltd')
    const first = screen.getAllByText('Confirm')[0]   // legalName was entered by u1, the current user
    expect((first as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/You entered this/), { target: { value: 'Sole practitioner, nobody else' } })
    expect((first as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(first)
    await waitFor(() => expect(post).toHaveBeenCalledWith(
      '/api/v1/compliance-services/clients/c1/profile/sources/legalName/confirm', { exceptionReason: 'Sole practitioner, nobody else' }))
  })
})
