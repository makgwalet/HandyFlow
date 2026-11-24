// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import SharedDevicesPanel from './SharedDevicesPanel'

afterEach(() => cleanup())
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><SharedDevicesPanel /></QueryClientProvider>)
}
beforeEach(() => {
  perms.clear(); api.get.mockReset(); api.post.mockReset()
  api.get.mockImplementation((url: string) => Promise.resolve(url.includes('/sites')
    ? { data: { data: { content: [{ id: 's1', name: 'ABC Centre' }] } } }
    : { data: { data: [
        { id: 'd1', siteId: 's1', deviceHardwareId: 'hw-1111', deviceName: 'Gate tablet', status: 'ACTIVE', lastSeenAt: null },
        { id: 'd2', siteId: 's1', deviceHardwareId: 'hw-2222', deviceName: 'Old tablet', status: 'REVOKED', lastSeenAt: null }] } }))
  api.post.mockResolvedValue({ data: { data: { sessionsEnded: 1 } } })
})

describe('SharedDevicesPanel', () => {
  it('lists devices read-only for a user without manage', async () => {
    mount()
    await screen.findByText('Gate tablet')
    expect(screen.queryByText('Register a device')).toBeNull()
    expect(screen.queryByText('Revoke')).toBeNull()
  })

  it('a manager can revoke an active device, after confirming, but not an already revoked one', async () => {
    perms.add('SECURITY_MANAGE')
    mount()
    await screen.findByText('Gate tablet')
    expect(screen.getAllByText('Revoke')).toHaveLength(1)
    fireEvent.click(screen.getByText('Revoke'))
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByText('Yes, revoke'))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/devices/shared/d1/revoke'))
    expect(await screen.findByText('Device revoked. 1 session ended.')).toBeTruthy()
  })

  it('registers a device with a trimmed body', async () => {
    perms.add('SECURITY_MANAGE')
    mount()
    await screen.findByText('Gate tablet')
    fireEvent.click(screen.getByText('Register a device'))
    fireEvent.change(screen.getByLabelText('Hardware ID'), { target: { value: ' hw-9999 ' } })
    fireEvent.change(screen.getByLabelText('Device name'), { target: { value: 'Gate 2' } })
    fireEvent.click(screen.getByText('Register'))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/devices/shared', { deviceHardwareId: 'hw-9999', deviceName: 'Gate 2', siteId: null }))
  })

  it('does not call the server with an empty hardware id', async () => {
    perms.add('SECURITY_MANAGE')
    mount()
    await screen.findByText('Gate tablet')
    fireEvent.click(screen.getByText('Register a device'))
    fireEvent.click(screen.getByText('Register'))
    expect(await screen.findByText(/hardware ID/)).toBeTruthy()
    expect(api.post).not.toHaveBeenCalled()
  })

  it('shows the server message when registering fails', async () => {
    perms.add('SECURITY_MANAGE')
    api.post.mockRejectedValue({ response: { data: { message: 'That device is already registered.' } } })
    mount()
    await screen.findByText('Gate tablet')
    fireEvent.click(screen.getByText('Register a device'))
    fireEvent.change(screen.getByLabelText('Hardware ID'), { target: { value: 'hw-1' } })
    fireEvent.click(screen.getByText('Register'))
    expect(await screen.findByText('That device is already registered.')).toBeTruthy()
  })
})
