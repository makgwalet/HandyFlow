// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import AgreementsPage from './AgreementsPage'

const get = vi.fn()
const navigate = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
vi.mock('react-router-dom', async () => ({ ...(await vi.importActual<object>('react-router-dom')), useNavigate: () => navigate }))

const row = (over: object = {}) => ({
  id: 'a1', clientId: 'c1', clientName: 'Acme', quoteNumber: 'CQ-000001', agreementNumber: 'CA-000001', title: 'Annual compliance', status: 'ACTIVE',
  startDate: '2027-03-01', endDate: null, billingFrequency: 'MONTHLY', billingAmount: 1500, nextBillingDate: '2027-04-01', attention: null, ...over,
})

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><AgreementsPage /></MemoryRouter></QueryClientProvider>)
}

describe('AgreementsPage', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); navigate.mockReset(); get.mockResolvedValue({ data: { data: [row()] } }) })

  it('lists agreements across clients and opens the clients Agreements tab', async () => {
    renderIt()
    expect(await screen.findByText('CA-000001')).toBeTruthy()
    expect(screen.getByText(/Acme · Annual compliance/)).toBeTruthy()
    fireEvent.click(screen.getByText('CA-000001'))
    expect(navigate).toHaveBeenCalledWith('/complianceservices/clients/c1?tab=agreements')
  })

  it('filters by status through the server', async () => {
    renderIt()
    await screen.findByText('CA-000001')
    fireEvent.click(screen.getByRole('button', { name: 'Ended' }))
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/v1/compliance-services/agreements', { params: { status: 'ENDED' } }))
  })

  it('says so when there are none, and shows warnings', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    renderIt()
    expect(await screen.findByText('No agreements.')).toBeTruthy()
    cleanup()
    get.mockResolvedValue({ data: { data: [row({ attention: 'The term ended on 2027-01-31. End this agreement or start a new one' })] } })
    renderIt()
    expect(await screen.findByText(/The term ended on 2027-01-31/)).toBeTruthy()
  })
})
