// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import DashboardPage from './DashboardPage'

const get = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const summary = (over: object = {}) => ({
  generatedOn: '2026-11-25',
  kpis: { activeClients: 4, overdueDeadlines: 2, deadlinesNext14: 3, documentsToReview: 1, documentsExpiring: 2, openCases: 1, regulatoryOpen: 0, quotesOut: 2, quotesOutValue: 3000, activeAgreements: 5, billsToRaise: 1, billsToRaiseValue: 1500 },
  queue: { total: 9, high: 2, medium: 3, low: 4 },
  clientsByStatus: [{ status: 'ACTIVE', count: 4 }, { status: 'INACTIVE', count: 1 }],
  quotePipeline: [{ status: 'DRAFT', count: 1, value: 500 }, { status: 'SENT', count: 2, value: 3000 }],
  tenderPipeline: [{ status: 'IN_PREPARATION', count: 1, value: 0 }],
  deadlineWeeks: [0, 1, 2, 3, 4, 5, 6, 7].map(i => ({ start: `2026-12-0${i + 1}`, count: i === 0 ? 3 : 0 })),
  deadlinesOverdue: 2,
  billingForecast: [{ month: '2026-11', amount: 1500, count: 1 }, { month: '2026-12', amount: 0, count: 0 }, { month: '2027-01', amount: 0, count: 0 }, { month: '2027-02', amount: 0, count: 0 }, { month: '2027-03', amount: 0, count: 0 }, { month: '2027-04', amount: 0, count: 0 }],
  billingBehind: 800, billingBehindCount: 1,
  urgent: [{ type: 'DEADLINE', severity: 'HIGH', clientId: 'c1', clientName: 'Acme', title: 'Annual return', detail: '2 days overdue', dueDate: '2026-11-23', tab: 'deadlines' }],
  upcoming: [{ clientId: 'c1', clientName: 'Acme', title: 'Annual return', dueDate: '2026-11-23' }, { clientId: 'c2', clientName: 'Beta', title: 'VAT return', dueDate: '2026-12-01' }],
  ...over,
})
const renderIt = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><DashboardPage /></MemoryRouter></QueryClientProvider>)

describe('DashboardPage', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: summary() } }) })

  it('shows the headline tiles, each leading to where the work is done', async () => {
    renderIt()
    expect(await screen.findByText('Overdue deadlines')).toBeTruthy()
    expect(screen.getByText('Documents to deal with').closest('a')?.getAttribute('href')).toBe('/complianceservices/queue')
    expect(screen.getByText('Bills to raise').closest('a')?.getAttribute('href')).toBe('/complianceservices/billing')
    expect(screen.getByText('Quotes out').closest('a')?.getAttribute('href')).toBe('/complianceservices/quotes')
    expect(screen.getByText(/R 3 000.00 waiting for an answer/)).toBeTruthy()
  })

  it('lists what needs attention with a link to the client tab, and the upcoming deadlines', async () => {
    renderIt()
    const links = (await screen.findAllByText(/Acme · Annual return/, { selector: 'span' })).map(n => n.closest('a')?.getAttribute('href'))
    expect(links).toEqual(['/complianceservices/clients/c1?tab=deadlines', '/complianceservices/clients/c1?tab=deadlines'])
    expect(screen.getByText(/9 in the work queue · 2 urgent, 3 soon/)).toBeTruthy()
    expect(screen.getByText(/2026-11-23 \(overdue\)/)).toBeTruthy()
    expect(screen.getByText(/Beta · VAT return/)).toBeTruthy()
  })

  it('writes the notes under the charts', async () => {
    renderIt()
    expect(await screen.findByText(/3 deadlines in the next 8 weeks, and 2 already overdue/)).toBeTruthy()
    expect(screen.getByText(/1 bill from earlier months \(R 800.00\) still waiting/)).toBeTruthy()
  })

  it('offers every chart as a table too', async () => {
    renderIt()
    await screen.findByText('Billing forecast')
    fireEvent.click(screen.getAllByRole('button', { name: 'Table' })[1])
    expect(screen.getByText('Scheduled bills')).toBeTruthy()
    expect(screen.getByText('Nov')).toBeTruthy()
  })

  it('says so when there is nothing to show, and when it cannot load', async () => {
    get.mockResolvedValue({ data: { data: summary({ urgent: [], upcoming: [], quotePipeline: [], tenderPipeline: [], queue: { total: 0, high: 0, medium: 0, low: 0 } }) } })
    renderIt()
    expect(await screen.findByText('Nothing needs attention right now.')).toBeTruthy()
    expect(screen.getByText('No quotes yet.')).toBeTruthy()
    expect(screen.getByText('No tenders yet.')).toBeTruthy()
    expect(screen.getByText('No deadlines in the next 30 days.')).toBeTruthy()
    cleanup()
    get.mockRejectedValue(new Error('boom'))
    renderIt()
    expect(await screen.findByRole('alert')).toBeTruthy()
  })
})
