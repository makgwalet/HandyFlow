// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientRegulatoryTab from './ClientRegulatoryTab'

const get = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn() } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))

const impact = (over: object = {}) => ({ id: 'i1', changeId: 'ch1', changeTitle: 'Fee change', clientId: 'c1', clientName: 'Acme', obligationCode: 'CIPC-AR', status: 'OPEN', dueDate: '2027-01-01', note: null, handledAt: null, noticeGivenAt: null, actions: ['DONE'], ...over })
const renderIt = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ClientRegulatoryTab clientId="c1" /></QueryClientProvider>)

describe('ClientRegulatoryTab', () => {
  afterEach(cleanup)
  beforeEach(() => get.mockReset())

  it('shows open impacts before finished ones, for this client only', async () => {
    get.mockResolvedValue({ data: { data: [impact({ id: 'd', changeTitle: 'Old change', status: 'DONE', note: 'Handled it fully', actions: [] }), impact()] } })
    renderIt()
    const rows = await screen.findAllByText(/· CIPC-AR/)
    expect(rows.map(r => r.textContent)).toEqual(['Fee change · CIPC-AR', 'Old change · CIPC-AR'])
    expect(get).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/regulatory-impacts')
  })

  it('says so when nothing touches the client', async () => {
    get.mockResolvedValue({ data: { data: [] } })
    renderIt()
    expect(await screen.findByText(/No regulatory changes touch this client/)).toBeTruthy()
  })
})
