// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import WorkQueue from './WorkQueue'

const get = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const queue = (items: object[]) => ({ data: { data: { counts: { total: items.length, high: 1, medium: 1, low: 0 }, items } } })
const ITEMS = [
  { type: 'DEADLINE', severity: 'HIGH', clientId: 'c1', clientName: 'Acme', title: 'Annual return', detail: '2 days overdue', dueDate: '2026-11-22', tab: 'deadlines' },
  { type: 'DOCUMENT_EXPIRY', severity: 'MEDIUM', clientId: 'c2', clientName: 'Beta', title: 'BBBEE Certificate', detail: 'Expires 2026-12-10 (16 days)', dueDate: '2026-12-10', tab: 'documents' },
  { type: 'CATALOGUE_REVIEW', severity: 'LOW', clientId: null, clientName: null, title: 'VAT-201 v2', detail: 'Waiting for review for 9 days', dueDate: null, tab: 'catalogue' },
]

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><WorkQueue /></MemoryRouter></QueryClientProvider>)
}

describe('WorkQueue', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); get.mockResolvedValue(queue(ITEMS)) })

  it('lists items most urgent first with counts, and links each to where the work is done', async () => {
    renderIt()
    expect(await screen.findByText('Annual return')).toBeTruthy()
    expect(screen.getByText(/3 to look at · 1 urgent, 1 soon, 0 when you can/)).toBeTruthy()
    expect(screen.getByText('Annual return').closest('a')?.getAttribute('href')).toBe('/complianceservices/clients/c1?tab=deadlines')
    expect(screen.getByText('VAT-201 v2').closest('a')?.getAttribute('href')).toBe('/complianceservices/catalogue')
    expect(screen.getByText('Catalogue')).toBeTruthy()
  })

  it('filters by type and by search', async () => {
    renderIt()
    await screen.findByText('Annual return')
    fireEvent.click(screen.getByText('Documents expiring (1)'))
    expect(screen.queryByText('Annual return')).toBeNull()
    expect(screen.getByText('BBBEE Certificate')).toBeTruthy()
    fireEvent.click(screen.getByText('All (3)'))
    fireEvent.change(screen.getByLabelText('Search the queue'), { target: { value: 'zzz' } })
    expect(screen.getByText('Nothing matches.')).toBeTruthy()
  })

  it('says so when nothing needs attention', async () => {
    get.mockResolvedValue(queue([]))
    renderIt()
    expect(await screen.findByText('Nothing needs attention right now.')).toBeTruthy()
  })

  it('says when the queue could not be loaded', async () => {
    get.mockRejectedValue(new Error('boom'))
    renderIt()
    expect(await screen.findByRole('alert')).toBeTruthy()
  })
})
