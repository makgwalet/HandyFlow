// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import BillingPage from './BillingPage'

const get = vi.fn()
const post = vi.fn()
let manage = true
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => manage, usePermission: () => manage }))

const bill = (over: object = {}) => ({
  id: 'e1', agreementId: 'a1', agreementNumber: 'CA-000001', clientId: 'c1', clientName: 'Acme', dueDate: '2027-03-01', amount: 1500, description: 'x', status: 'SCHEDULED',
  stage: 'DUE', daysLate: 0, invoiceId: null, invoiceNumber: null, invoice: null, skipReason: null, releaseNote: null, actions: ['raise', 'skip'], blocked: null, ...over,
})

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><BillingPage /></QueryClientProvider>)
}

describe('BillingPage', () => {
  afterEach(cleanup)
  beforeEach(() => { manage = true; get.mockReset(); post.mockReset(); get.mockResolvedValue({ data: { data: [bill()] } }) })

  it('starts on the bills to raise, with the client and agreement, and puts the latest first', async () => {
    get.mockResolvedValue({ data: { data: [bill({ id: 'e2', dueDate: '2027-04-01', stage: 'UPCOMING' }), bill({ id: 'e3', dueDate: '2027-02-01', stage: 'OVERDUE_TO_RAISE', daysLate: 28 })] } })
    renderIt()
    expect((await screen.findAllByText(/Acme · CA-000001/)).length).toBe(2)
    expect(get).toHaveBeenCalledWith('/api/v1/compliance-services/billing-events', { params: { filter: 'DUE' } })
    expect(screen.getByText(/Overdue to raise · 28 days late/)).toBeTruthy()
    const dates = screen.getAllByText(/^2027-0\d-01$/).map(n => n.textContent)
    expect(dates).toEqual(['2027-02-01', '2027-04-01'])
    expect(screen.getByText(/2 bills · .*3.000\.00 before VAT/)).toBeTruthy()
  })

  it('changes the filter through the server', async () => {
    renderIt()
    await screen.findByText(/Acme · CA-000001/)
    fireEvent.click(screen.getByRole('button', { name: 'Invoiced' }))
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/v1/compliance-services/billing-events', { params: { filter: 'INVOICED' } }))
    fireEvent.click(screen.getByRole('button', { name: 'All' }))
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/v1/compliance-services/billing-events', { params: {} }))
  })

  it('raises a bill from the worklist, and shows the server error if Invoicing refuses', async () => {
    post.mockRejectedValueOnce({ response: { data: { message: 'Link this client to a customer in Invoicing before raising an invoice' } } })
    renderIt()
    fireEvent.click(await screen.findByRole('button', { name: 'Raise draft invoice' }))
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByText(/Link this client to a customer/)).toBeTruthy()
  })

  it('is read-only without permission to manage, and says so when empty', async () => {
    manage = false
    renderIt()
    await screen.findByText(/Acme · CA-000001/)
    expect(screen.queryByRole('button', { name: 'Raise draft invoice' })).toBeNull()
    cleanup()
    get.mockResolvedValue({ data: { data: [] } })
    renderIt()
    expect(await screen.findByText('Nothing here.')).toBeTruthy()
  })
})
