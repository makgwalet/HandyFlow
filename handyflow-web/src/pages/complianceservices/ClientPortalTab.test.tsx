// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientPortalTab from './ClientPortalTab'

const get = vi.fn()
const post = vi.fn()
let manage = true
let admin = true
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => manage, usePermission: () => admin }))

const base = '/api/v1/compliance-services/clients/c1/portal-users'
const grant = (over: object = {}) => ({ id: 'g1', email: 'jo@acme.co.za', status: 'ACTIVE', emailSent: true, invitedAt: '2027-03-01T08:00:00Z', acceptedAt: '2027-03-02T09:00:00Z', revokedAt: null, inviteExpiresAt: null, ...over })

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientPortalTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientPortalTab', () => {
  afterEach(cleanup)
  beforeEach(() => {
    manage = true; admin = true
    get.mockReset(); post.mockReset()
    get.mockResolvedValue({ data: { data: [grant(), grant({ id: 'g2', email: 'pat@acme.co.za', status: 'PENDING', acceptedAt: null, inviteExpiresAt: '2027-03-09T08:00:00Z' }), grant({ id: 'g3', email: 'old@acme.co.za', status: 'REVOKED', revokedAt: '2027-03-05T08:00:00Z' })] } })
    post.mockResolvedValue({ data: { data: grant() } })
  })

  it('lists who has access, who is only invited and who was removed', async () => {
    renderIt()
    expect(await screen.findByText('jo@acme.co.za')).toBeTruthy()
    expect(screen.getByText(/Has access since 2027-03-02/)).toBeTruthy()
    expect(screen.getByText(/Invited, not yet signed in · link expires 2027-03-09/)).toBeTruthy()
    expect(screen.getByText(/Access removed on 2027-03-05/)).toBeTruthy()
  })

  it('blocks a bad email and sends a good one', async () => {
    renderIt()
    await screen.findByText('jo@acme.co.za')
    fireEvent.change(screen.getByLabelText('Invite someone by email'), { target: { value: 'nope' } })
    expect(screen.getByText(/does not look like an email/)).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Send invite' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Invite someone by email'), { target: { value: ' new@acme.co.za ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send invite' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(base, { email: 'new@acme.co.za' }))
  })

  it('asks before removing access, then removes it', async () => {
    renderIt()
    await screen.findByText('jo@acme.co.za')
    fireEvent.click(screen.getByRole('button', { name: 'Remove access' }))
    expect(screen.getByText(/lose access on their next request/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Yes, remove' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${base}/g1/revoke`, {}))
  })

  it('says when an invite email did not go out', async () => {
    get.mockResolvedValue({ data: { data: [grant({ status: 'PENDING', emailSent: false, acceptedAt: null })] } })
    renderIt()
    expect(await screen.findByText(/invite email could not be sent/)).toBeTruthy()
  })

  it('shows the server message when the person is already invited', async () => {
    post.mockRejectedValueOnce({ response: { data: { message: 'This person already has access' } } })
    renderIt()
    await screen.findByText('jo@acme.co.za')
    fireEvent.change(screen.getByLabelText('Invite someone by email'), { target: { value: 'jo@acme.co.za' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send invite' }))
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByText('This person already has access')).toBeTruthy()
  })

  it('lets a manager invite but not remove, and a reader do neither', async () => {
    admin = false
    renderIt()
    await screen.findByText('jo@acme.co.za')
    expect(screen.getByRole('button', { name: 'Send invite' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Remove access' })).toBeNull()
    cleanup()
    manage = false
    renderIt()
    await screen.findByText('jo@acme.co.za')
    expect(screen.queryByRole('button', { name: 'Send invite' })).toBeNull()
  })
})
