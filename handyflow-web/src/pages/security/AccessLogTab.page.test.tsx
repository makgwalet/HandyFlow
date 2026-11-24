// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
vi.mock("./companyZone", async orig => ({ ...(await orig<typeof import("./companyZone")>()), useCompanyZone: () => ({ zone: "Africa/Johannesburg", ready: true }) }))
import AccessLogTab from './AccessLogTab'

afterEach(() => cleanup())
beforeEach(() => vi.clearAllMocks())
const row = (o: object = {}) => ({ id: 'e1', occurredAt: '2026-11-22T06:35:00Z', actorId: 'u1', actorType: 'USER', entityType: 'GUARD', entityId: '22222222-2222-4222-8222-222222222222', action: 'EVIDENCE_DOWNLOADED', metadata: '{"kind":"SCREENING_EVIDENCE"}', actorName: 'Thandi Admin', guardName: 'Sipho Dlamini', ...o })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AccessLogTab /></QueryClientProvider>)

describe('AccessLogTab', () => {
  it('lists who opened what, about whom', async () => {
    api.get.mockResolvedValue({ data: { data: [row(), row({ id: 'e2', actorName: null, actorId: null, action: 'EXPORTED', entityType: 'PAYROLL_PERIOD', metadata: '{"format":"CSV","supportSession":true}', guardName: null })] } })
    show()
    expect(await screen.findByText('Thandi Admin')).toBeTruthy()
    expect(screen.getByText('Downloaded screening evidence')).toBeTruthy()
    expect(screen.getByText('Sipho Dlamini')).toBeTruthy()
    expect(screen.getByText('Support staff')).toBeTruthy()
    expect(screen.getByText('Exported payroll (CSV)')).toBeTruthy()
    expect(api.get).toHaveBeenCalledWith('/api/v1/security/access-log', { params: { limit: 200 } })
  })
  it('asks the server for one kind when filtered', async () => {
    api.get.mockResolvedValue({ data: { data: [] } })
    show()
    await screen.findByText(/Nothing sensitive/)
    fireEvent.change(screen.getByLabelText('Kind of record'), { target: { value: 'PAYROLL_PERIOD' } })
    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/api/v1/security/access-log', { params: { limit: 200, entityType: 'PAYROLL_PERIOD' } }))
    expect(await screen.findByText(/of this kind/)).toBeTruthy()
  })
  it('says so plainly when the log cannot be read', async () => {
    api.get.mockRejectedValue({ response: { status: 403 } })
    show()
    expect((await screen.findByRole('alert')).textContent).toMatch(/Security administrator/)
  })
  it('reloads on Refresh', async () => {
    api.get.mockResolvedValue({ data: { data: [row()] } })
    show()
    await screen.findByText('Thandi Admin')
    fireEvent.click(screen.getByRole('button', { name: /Refresh/ }))
    await waitFor(() => expect(api.get).toHaveBeenCalledTimes(2))
  })
})
