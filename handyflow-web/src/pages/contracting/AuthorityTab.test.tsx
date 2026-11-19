// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AuthorityTab from './AuthorityTab'
import type { Grant } from './authorityShared'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermission: () => true }))

const grant = (o: Partial<Grant> = {}): Grant => ({
  id: 'g1', userId: 'u1', role: null, businessUnit: null, contractTypes: ['SUPPLY'], actions: ['APPROVE'], unlimited: false, maxAmount: 100000,
  effectiveFrom: '2026-01-01', expiresOn: null, conditions: null, delegatedFromId: null, delegatedBy: null, status: 'ACTIVE', inForce: true, mine: true,
  revokedAt: null, revokedReason: null, createdAt: '2026-01-01T00:00:00Z', ...o,
})
const users = [{ id: 'u1', firstName: 'Alice', lastName: 'Ndlovu' }, { id: 'u2', firstName: 'Bob', lastName: 'Smith' }]

function wire(grants: Grant[], enforced = false) {
  get.mockImplementation(async (url: string) => {
    if (url.endsWith('/grants')) return { data: { data: grants } }
    if (url.endsWith('/settings')) return { data: { data: { enforced } } }
    if (url.endsWith('/identity/users')) return { data: users }
    return { data: { data: [] } }
  })
}
function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AuthorityTab /></QueryClientProvider>)
}

describe('AuthorityTab', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } }) })

  it('cannot turn enforcement on while nobody holds authority', async () => {
    wire([])
    renderTab()
    const b = (await screen.findByText('Turn enforcement on')) as HTMLButtonElement
    expect(b.disabled).toBe(true)
    expect(screen.getByText(/Give someone authority first/)).toBeTruthy()
  })

  it('asks before turning enforcement on, then does it', async () => {
    wire([grant()])
    renderTab()
    const on = (await screen.findByText('Turn enforcement on')) as HTMLButtonElement
    await waitFor(() => expect(on.disabled).toBe(false))
    fireEvent.click(on)
    expect(screen.getByRole('alertdialog')).toBeTruthy()
    expect(put).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByText('Turn enforcement on')[0]!)
    await waitFor(() => expect(put).toHaveBeenCalledWith('/api/v1/contracts/authority/settings', { enforced: true }))
  })

  it('shows what the person can do and who holds what', async () => {
    wire([grant()])
    renderTab()
    expect((await screen.findAllByText(/Supply Agreement/)).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Up to R100/).length).toBeGreaterThan(0)
    expect(screen.getByText('In force')).toBeTruthy()
  })

  it('marks an expired grant and a revoked one', async () => {
    wire([grant({ id: 'g2', inForce: false, expiresOn: '2026-02-01' }), grant({ id: 'g3', status: 'REVOKED', inForce: false, revokedReason: 'left' })])
    renderTab()
    expect(await screen.findByText('Expired')).toBeTruthy()
    expect(screen.getByText('Revoked')).toBeTruthy()
  })

  it('will not send a delegation that exceeds the holder\'s limit, and sends one that does not', async () => {
    wire([grant()])
    renderTab()
    fireEvent.click(await screen.findByText('Delegate'))
    fireEvent.change(screen.getByLabelText('Delegate to'), { target: { value: 'u2' } })
    fireEvent.click(screen.getByLabelText('Approve'))
    fireEvent.click(screen.getByLabelText('Supply Agreement'))
    fireEvent.change(screen.getByLabelText('Most per contract (rand)'), { target: { value: '200000' } })
    fireEvent.change(screen.getByLabelText('Until'), { target: { value: '2099-01-01' } })
    expect(screen.getByText(/more than your own limit/)).toBeTruthy()
    expect((screen.getAllByText('Delegate').find(e => e.tagName === 'BUTTON' && (e as HTMLButtonElement).disabled) as HTMLButtonElement | undefined)).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Most per contract (rand)'), { target: { value: '50000' } })
    const send = screen.getAllByText('Delegate').filter(e => e.tagName === 'BUTTON').pop() as HTMLButtonElement
    await waitFor(() => expect(send.disabled).toBe(false))
    fireEvent.click(send)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/contracts/authority/grants/g1/delegate', expect.objectContaining({
      toUserId: 'u2', actions: ['APPROVE'], contractTypes: ['SUPPLY'], maxAmount: 50000, expiresOn: '2099-01-01',
    })))
  })

  it('gives authority to a person with a limit', async () => {
    wire([])
    renderTab()
    fireEvent.click(await screen.findByText('Give authority'))
    fireEvent.change(screen.getByLabelText('Person'), { target: { value: 'u2' } })
    fireEvent.click(screen.getByLabelText('Sign'))
    fireEvent.change(screen.getByLabelText('Most per contract (rand)'), { target: { value: '250000' } })
    const send = screen.getAllByText('Give authority').filter(e => e.tagName === 'BUTTON').pop() as HTMLButtonElement
    await waitFor(() => expect(send.disabled).toBe(false))
    fireEvent.click(send)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/contracts/authority/grants', expect.objectContaining({ userId: 'u2', role: null, actions: ['SIGN'], maxAmount: 250000, unlimited: false })))
  })

  it('revoking needs a reason', async () => {
    wire([grant()])
    renderTab()
    fireEvent.click(await screen.findByText('Revoke'))
    const go = screen.getAllByText('Revoke').filter(e => e.tagName === 'BUTTON').pop() as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Why\?/), { target: { value: 'moved to finance' } })
    expect(go.disabled).toBe(false)
  })
})
