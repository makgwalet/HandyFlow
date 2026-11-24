// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import QuotesPage from './QuotesPage'

const get = vi.fn()
const navigate = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
vi.mock('react-router-dom', () => ({ useNavigate: () => navigate }))

const rows = [
  { id: 'q1', clientId: 'c1', clientName: 'Acme', quoteNumber: 'CQ-000001', title: 'Annual', status: 'PENDING_APPROVAL', validUntil: '2099-01-01', total: 5060, requiredApprover: 'PRACTICE_OWNER', maxDiscountPct: 12 },
  { id: 'q2', clientId: 'c2', clientName: 'Beta', quoteNumber: 'CQ-000002', title: null, status: 'SENT', validUntil: '2099-02-01', total: 2760, requiredApprover: 'CONSULTANT', maxDiscountPct: 0 },
]

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><QuotesPage /></QueryClientProvider>)
}

describe('QuotesPage', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); navigate.mockReset(); get.mockResolvedValue({ data: { data: rows } }) })

  it('lists quotes across clients with who must approve a pending one, and the total', async () => {
    renderIt()
    expect(await screen.findByText('CQ-000001')).toBeTruthy()
    expect(screen.getByText('The practice owner must approve')).toBeTruthy()
    expect(screen.getByText(/2 quotes · R 7 820.00 including VAT/)).toBeTruthy()
  })

  it('filters by status through the server', async () => {
    renderIt()
    await screen.findByText('CQ-000001')
    fireEvent.click(screen.getByRole('button', { name: 'Sent' }))
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/v1/compliance-services/quotes', { params: { status: 'SENT' } }))
  })

  it('opens the client\'s Quotes tab', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CQ-000002'))
    expect(navigate).toHaveBeenCalledWith('/complianceservices/clients/c2?tab=quotes')
  })
})
