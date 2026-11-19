// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ApprovalsTab from './ApprovalsTab'
import { decisionBlocker, MIN_EXCEPTION_REASON } from './approvalsShared'

const get = vi.fn()
const post = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn() } }))
vi.mock('../../hooks/usePermission', () => ({ usePermission: () => true }))

const base = {
  id: 'a1', action: 'SEND_FOR_SIGNING', subjectId: 's1', subjectLabel: 'CTR-1 Service agreement', status: 'PENDING',
  requestedBy: 'u1', requestedAt: '2026-11-19T08:00:00Z', requestNote: null, decidedBy: null, decidedAt: null,
  decisionNote: null, soleUserException: false, exceptionReason: null, mine: false, canDecide: true,
}
const policy = [{ action: 'SEND_FOR_SIGNING', required: true, allowSoleUserException: true, usingDefault: true }]

function wire(approvals: unknown[]) {
  get.mockImplementation(async (url: string) => ({ data: { data: url.endsWith('/policy') ? policy : approvals } }))
}
function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ApprovalsTab /></QueryClientProvider>)
}

describe('decisionBlocker', () => {
  it('lets someone else approve', () => expect(decisionBlocker({ mine: false }, false, '', false)).toBeNull())
  it('stops approving your own request without the exception', () => expect(decisionBlocker({ mine: true }, false, '', false)).toMatch(/someone else/))
  it('needs a real reason for the exception', () => {
    expect(decisionBlocker({ mine: true }, true, 'short', false)).toMatch(String(MIN_EXCEPTION_REASON))
    expect(decisionBlocker({ mine: true }, true, 'nobody else is on leave cover', false)).toBeNull()
  })
  it('never blocks a rejection', () => expect(decisionBlocker({ mine: true }, false, '', true)).toBeNull())
})

describe('ApprovalsTab', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } }) })

  it('shows what is waiting and approves it', async () => {
    wire([base])
    renderTab()
    fireEvent.click(await screen.findByText('Review'))
    fireEvent.click(screen.getByText('Approve'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/contracts/approvals/a1/approve',
      { note: null, soleUserException: false, exceptionReason: null }))
  })

  it('a rejection needs a note', async () => {
    wire([base])
    renderTab()
    fireEvent.click(await screen.findByText('Review'))
    expect((screen.getByText('Reject') as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/Note/), { target: { value: 'wrong party' } })
    expect((screen.getByText('Reject') as HTMLButtonElement).disabled).toBe(false)
  })

  it('own request: approve is blocked until the exception and reason are given', async () => {
    wire([{ ...base, mine: true }])
    renderTab()
    fireEvent.click(await screen.findByText('Review'))
    expect((screen.getByText('Approve') as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByLabelText(/only person/))
    expect((screen.getByText('Approve as exception') as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/Why is no one else/), { target: { value: 'the only administrator is away' } })
    expect((screen.getByText('Approve as exception') as HTMLButtonElement).disabled).toBe(false)
  })

  it('does not offer review when the person cannot decide, but lets the maker withdraw', async () => {
    wire([{ ...base, mine: true, canDecide: false }])
    renderTab()
    expect(await screen.findByText('Withdraw')).toBeTruthy()
    expect(screen.queryByText('Review')).toBeNull()
  })

  it('says so when nothing is waiting', async () => {
    wire([])
    renderTab()
    expect(await screen.findByText('Nothing is waiting for approval.')).toBeTruthy()
  })
})
