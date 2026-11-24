// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientObligationsTab from './ClientObligationsTab'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'u1' } }) }))

const row = (over: object) => ({
  obligationId: 'o1', code: 'CIPC-AR', name: 'Annual return', versionNumber: 1, state: 'APPLICABLE', effectiveState: 'APPLICABLE', suggestedState: 'APPLICABLE',
  suggestionReasons: ['Entity type is PTY_LTD, which is required.'], basedOnUnconfirmed: false, overridden: false, decidedBy: null, decisionReason: null,
  evidenceRef: null, reviewDue: null, approvalStatus: 'NOT_REQUIRED', reviewNeeded: false, ...over,
})
const resp = (rows: object[]) => ({ assignments: rows, counts: { applicable: 1, notApplicable: 0, pending: 1, awaitingApproval: 1, reviewNeeded: 0 } })

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientObligationsTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientObligationsTab', () => {
  afterEach(cleanup)
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockImplementation(async (url: string) => ({ data: { data: url.endsWith('/history') ? [] : resp([
      row({}),
      row({ obligationId: 'o2', code: 'POPIA', name: 'Information officer', state: 'NOT_APPLICABLE', effectiveState: 'APPLICABLE', approvalStatus: 'AWAITING', overridden: true, decidedBy: 'u1', decisionReason: 'No personal data processed', basedOnUnconfirmed: true }),
    ]) } }))
    post.mockResolvedValue({ data: { data: resp([row({})]) } })
    put.mockResolvedValue({ data: { data: resp([row({})]) } })
  })

  it('shows the counts, the reasons and the notes', async () => {
    renderIt()
    expect(await screen.findByText('CIPC-AR')).toBeTruthy()
    expect(screen.getByText(/1 apply, 0 do not, 1 undecided/)).toBeTruthy()
    expect(screen.getAllByText('Entity type is PTY_LTD, which is required.')).toHaveLength(2)
    expect(screen.getByText(/Removal waits for approval and still counts as applicable/)).toBeTruthy()
    expect(screen.getByText(/profile facts nobody has confirmed/)).toBeTruthy()
  })

  it('updates suggestions', async () => {
    renderIt()
    await screen.findByText('CIPC-AR')
    fireEvent.click(screen.getByText('Update suggestions'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/obligations/refresh', {}))
  })

  it('will not save an override without a reason and a review date', async () => {
    renderIt()
    await screen.findByText('CIPC-AR')
    fireEvent.click(screen.getAllByText('Decide')[0])
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'NOT_APPLICABLE' } })
    expect((screen.getByText('Save decision') as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText('Reason'), { target: { value: 'Client has no employees, payroll letter' } })
    expect((screen.getByText('Save decision') as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(document.querySelector('input[type="date"]') as HTMLInputElement, { target: { value: '2027-05-01' } })
    await waitFor(() => expect((screen.getByText('Save decision') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByText('Save decision'))
    await waitFor(() => expect(put).toHaveBeenCalled())
    const [url, body] = put.mock.calls[0]
    expect(url).toBe('/api/v1/compliance-services/clients/c1/obligations/o1/decision')
    expect(body).toMatchObject({ state: 'NOT_APPLICABLE', reason: 'Client has no employees, payroll letter', reviewDue: '2027-05-01' })
  })

  it('needs a reason for the person who decided to approve their own removal', async () => {
    renderIt()
    await screen.findByText('POPIA')
    const approve = screen.getByText('Approve removal') as HTMLButtonElement
    expect(approve.disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText('Reason, at least 15 characters'), { target: { value: 'Sole practitioner, no one else' } })
    await waitFor(() => expect((screen.getByText('Approve removal') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByText('Approve removal'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/obligations/o2/approve', { exceptionReason: 'Sole practitioner, no one else' }))
  })
})
