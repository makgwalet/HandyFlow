import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { render, screen, waitFor, cleanup } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ApprovalPanel from './RecApproval'
import RecPrivacy from './RecPrivacy'
import { decisionProblem, mayResubmit, retentionProblem, showApproval, fmtDay, type ApprovalView } from './recApproval.logic'

vi.mock('../../api/client', () => ({ apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))
import { apiClient } from '../../api/client'
import { useAuth } from '../../hooks/useAuth'

const view = (o: Partial<ApprovalView> = {}): ApprovalView => ({
  status: 'SUBMITTED', required: true, submittedBy: 'u1', submittedByName: 'Sam', submittedAt: '2026-10-20T08:00:00Z',
  decidedBy: null, decidedByName: null, decidedAt: null, note: null, canDecide: true, ...o,
})
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const as = (...permissions: string[]) => mk(useAuth).mockReturnValue({ user: { permissions } })

beforeEach(() => { vi.clearAllMocks() })
afterEach(() => cleanup())
const mk = (f: unknown) => f as Mock

describe('logic', () => {
  it('a refusal needs a note, an approval does not', () => {
    expect(decisionProblem(false, '  ')).toMatch(/why/)
    expect(decisionProblem(true, '')).toBeNull()
    expect(decisionProblem(true, 'x'.repeat(501))).toMatch(/too long/)
  })
  it('retention months 3 to 60', () => {
    expect(retentionProblem('2')).not.toBeNull(); expect(retentionProblem('61')).not.toBeNull()
    expect(retentionProblem('1.5')).not.toBeNull(); expect(retentionProblem('12')).toBeNull()
  })
  it('shows the panel only when required or started', () => {
    expect(showApproval(view({ required: false, status: 'NOT_SUBMITTED' }))).toBe(false)
    expect(showApproval(view({ required: false, status: 'APPROVED' }))).toBe(true)
    expect(mayResubmit(view({ status: 'SUBMITTED' }))).toBe(false)
    expect(mayResubmit(view({ status: 'REJECTED' }))).toBe(true)
  })
  it('formats days in South African time', () => { expect(fmtDay('2026-10-21T23:30:00Z')).toBe('22 Oct 2026') })
})

describe('ApprovalPanel', () => {
  it('lets an approver reject only with a note, and sends it', async () => {
    as('RECRUITER_ADMIN')
    ;mk(apiClient.get).mockResolvedValue({ data: view() })
    ;mk(apiClient.post).mockResolvedValue({ data: view({ status: 'REJECTED', note: 'Pay range missing' }) })
    wrap(<ApprovalPanel kind="job" id="j1" />)
    await userEvent.click(await screen.findByText('Do not approve'))
    expect((await screen.findByRole('alert')).textContent).toMatch(/why/)
    expect(apiClient.post).not.toHaveBeenCalled()
    await userEvent.type(screen.getByLabelText('Approval note'), 'Pay range missing')
    await userEvent.click(screen.getByText('Do not approve'))
    await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/api/v1/recruiter/jobs/j1/approval/decision', { approve: false, note: 'Pay range missing' }))
    expect(await screen.findByText('Not approved')).toBeTruthy()
  })
  it('does not offer to decide what you sent yourself', async () => {
    as('RECRUITER_ADMIN')
    ;mk(apiClient.get).mockResolvedValue({ data: view({ canDecide: false }) })
    wrap(<ApprovalPanel kind="offer" id="a1" />)
    expect(await screen.findByText(/you sent this yourself/)).toBeTruthy()
    expect(screen.queryByText('Approve')).toBeNull()
  })
  it('renders nothing when approval is off and nothing started', async () => {
    as('RECRUITER_ADMIN')
    ;mk(apiClient.get).mockResolvedValue({ data: view({ required: false, status: 'NOT_SUBMITTED' }) })
    const { container } = wrap(<ApprovalPanel kind="job" id="j1" />)
    await waitFor(() => expect(apiClient.get).toHaveBeenCalled())
    expect(container.textContent).toBe('')
  })
  it('shows the server message when sending fails', async () => {
    as('RECRUITER_MANAGE')
    ;mk(apiClient.get).mockResolvedValue({ data: view({ status: 'NOT_SUBMITTED' }) })
    ;mk(apiClient.post).mockRejectedValue({ response: { data: { message: 'Add a description first' } } })
    wrap(<ApprovalPanel kind="offer" id="a1" />)
    await userEvent.click(await screen.findByText('Send for approval'))
    expect((await screen.findByRole('alert')).textContent).toMatch(new RegExp('Add a description first'))
  })
})

describe('RecPrivacy', () => {
  const cand = { applicantId: 'p1', name: 'Thandi M', email: 't@x.co', lastActivity: '2025-01-10T08:00:00Z', dueSince: '2026-01-10', applications: 2, hasCv: true }
  it('asks to confirm, then removes the chosen applicants and reports refusals', async () => {
    as('RECRUITER_ADMIN')
    ;mk(apiClient.get).mockImplementation(async (u: string): Promise<unknown> => ({ data: u.endsWith('/settings') ? { requireJobApproval: false, requireOfferApproval: false, retentionMonths: 12 } : [cand] }))
    ;mk(apiClient.post).mockResolvedValue({ data: { removed: 0, refused: [{ applicantId: 'p1', reason: 'Thandi M has an open application' }] } })
    wrap(<RecPrivacy />)
    await userEvent.click(await screen.findByLabelText('Choose Thandi M'))
    await userEvent.click(screen.getByText(/Remove details of 1/))
    expect(apiClient.post).not.toHaveBeenCalled()
    await userEvent.click(screen.getByText('Remove permanently'))
    await waitFor(() => expect(apiClient.post).toHaveBeenCalledWith('/api/v1/recruiter/retention/remove', { applicantIds: ['p1'] }))
    expect(await screen.findByText(/has an open application/)).toBeTruthy()
  })
  it('blocks a bad retention period before sending', async () => {
    as('RECRUITER_ADMIN')
    ;mk(apiClient.get).mockImplementation(async (u: string): Promise<unknown> => ({ data: u.endsWith('/settings') ? { requireJobApproval: false, requireOfferApproval: false, retentionMonths: 12 } : [] }))
    wrap(<RecPrivacy />)
    const months = await screen.findByLabelText(/Keep unsuccessful/)
    await userEvent.clear(months); await userEvent.type(months, '2')
    await userEvent.click(screen.getByText('Save settings'))
    expect((await screen.findByRole('alert')).textContent).toMatch(/3 and 60/)
    expect(apiClient.put).not.toHaveBeenCalled()
  })
  it('hides the removal list from a reader', async () => {
    as('RECRUITER_READ')
    ;mk(apiClient.get).mockResolvedValue({ data: { requireJobApproval: false, requireOfferApproval: false, retentionMonths: 12 } })
    wrap(<RecPrivacy />)
    await screen.findByText('Recruiting settings')
    expect(screen.queryByText(/retention period has ended/)).toBeNull()
  })
})
