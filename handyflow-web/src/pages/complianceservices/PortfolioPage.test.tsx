// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import PortfolioPage from './PortfolioPage'

const get = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const sig = (o: object = {}) => ({ code: 'EXPIRED_DOCUMENTS', severity: 'HIGH', label: 'Expired documents', detail: '1 verified document has expired', tab: 'documents', ...o })
const summary = (o: object = {}) => ({
  asOf: '2026-11-25', activeClients: 4, flaggedClients: 2, withHigh: 1,
  clients: [
    { clientId: 'c1', clientName: 'Acme', riskRating: 'HIGH', high: 1, medium: 0, low: 1, signals: [sig(), sig({ code: 'HIGH_RATING', severity: 'LOW', label: 'Rated high risk', detail: 'The client\'s risk rating is high', tab: 'profile' })] },
    { clientId: 'c2', clientName: 'Beta', riskRating: null, high: 0, medium: 1, low: 0, signals: [sig({ code: 'OVERDUE_DEADLINES', severity: 'MEDIUM', label: 'Overdue deadlines', detail: '1 deadline is overdue, the oldest by 4 days', tab: 'deadlines' })] },
  ], ...o,
})
const renderIt = () => render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PortfolioPage /></QueryClientProvider></MemoryRouter>)

describe('PortfolioPage', () => {
  afterEach(cleanup)

  it('lists flagged clients with their signs, each linking to the right tab', async () => {
    get.mockResolvedValue({ data: { data: summary() } })
    renderIt()
    expect(await screen.findByText('2 of 4 active clients show signs of trouble, 1 with urgent ones.')).toBeTruthy()
    expect(screen.getByText('1 urgent, 1 to note')).toBeTruthy()
    const link = screen.getByText('Overdue deadlines').closest('a')!
    expect(link.getAttribute('href')).toBe('/complianceservices/clients/c2?tab=deadlines')
    expect(screen.getByText('Expired documents').closest('a')!.getAttribute('href')).toBe('/complianceservices/clients/c1?tab=documents')
  })

  it('narrows to urgent signs and by name', async () => {
    get.mockResolvedValue({ data: { data: summary() } })
    renderIt()
    await screen.findByText('Beta')
    fireEvent.click(screen.getByText('With urgent signs'))
    expect(screen.queryByText('Beta')).toBeNull()
    expect(screen.getByText('Acme')).toBeTruthy()
    fireEvent.click(screen.getByText('All flagged'))
    fireEvent.change(screen.getByLabelText('Search clients'), { target: { value: 'bet' } })
    expect(screen.queryByText('Acme')).toBeNull()
  })

  it('says nothing flagged is not the same as complying', async () => {
    get.mockResolvedValue({ data: { data: summary({ flaggedClients: 0, withHigh: 0, clients: [] }) } })
    renderIt()
    expect(await screen.findByText(/not a statement that they comply/)).toBeTruthy()
  })

  it('says so when the list cannot be loaded', async () => {
    get.mockImplementation(async () => { throw new Error("boom") })
    renderIt()
    expect(await screen.findByText(/could not be loaded/)).toBeTruthy()
  })
})
