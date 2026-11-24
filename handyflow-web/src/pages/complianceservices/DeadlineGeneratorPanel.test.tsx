// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import DeadlineGeneratorPanel from './DeadlineGeneratorPanel'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><DeadlineGeneratorPanel clientId="c1" /></QueryClientProvider>)
}

describe('DeadlineGeneratorPanel', () => {
  afterEach(cleanup)
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockResolvedValue({ data: { data: { financialYearEndMonth: 2, registeredOn: '2019-03-15' } } })
    put.mockResolvedValue({ data: { data: { financialYearEndMonth: 12, registeredOn: '2019-03-15' } } })
    post.mockResolvedValue({ data: { data: { from: '2026-11-24', to: '2027-11-24', created: 3, cancelled: 0, unchanged: 1, skipped: [{ code: 'POPIA-1', reason: 'Depends on an event date, which has to be entered by hand' }] } } })
  })

  it('shows the saved key dates', async () => {
    renderIt()
    await waitFor(() => expect((screen.getByLabelText('Financial year end month') as HTMLSelectElement).value).toBe('2'))
    expect((screen.getByLabelText('Registered on') as HTMLInputElement).value).toBe('2019-03-15')
  })

  it('saves a changed month', async () => {
    renderIt()
    await waitFor(() => expect((screen.getByLabelText('Financial year end month') as HTMLSelectElement).value).toBe('2'))
    fireEvent.change(screen.getByLabelText('Financial year end month'), { target: { value: '12' } })
    fireEvent.click(screen.getByText('Save key dates'))
    await waitFor(() => expect(put).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/key-dates', { financialYearEndMonth: 12, registeredOn: '2019-03-15' }))
  })

  it('will not save a registration date in the future', async () => {
    renderIt()
    await waitFor(() => expect((screen.getByLabelText('Registered on') as HTMLInputElement).value).toBe('2019-03-15'))
    fireEvent.change(screen.getByLabelText('Registered on'), { target: { value: '2999-01-01' } })
    expect((screen.getByText('Save key dates') as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/cannot be in the future/)).toBeTruthy()
  })

  it('says what working out the deadlines did, and what could not be worked out', async () => {
    renderIt()
    fireEvent.click(screen.getByText('Work out deadlines'))
    expect(await screen.findByText(/3 new dates, 1 already there, 1 could not be worked out/)).toBeTruthy()
    expect(screen.getByText(/POPIA-1: Depends on an event date/)).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/deadlines/generate', {})
  })
})
