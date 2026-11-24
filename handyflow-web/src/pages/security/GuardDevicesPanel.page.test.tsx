// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import GuardDevicesPanel from './GuardDevicesPanel'

afterEach(() => cleanup())
const dev = (id: string, status: string, name: string) => ({ id, status, deviceName: name, deviceType: 'PERSONAL_GUARD_DEVICE', createdAt: '2026-10-01T00:00:00Z' })

function mount(canManage: boolean) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><GuardDevicesPanel guardId="g1" canManage={canManage} /></QueryClientProvider>)
}

beforeEach(() => {
  perms.clear(); api.get.mockReset(); api.post.mockReset()
  api.get.mockResolvedValue({ data: { data: [dev('d1', 'ACTIVE', 'Thabo phone'), dev('d2', 'REVOKED', 'Old phone')] } })
  api.post.mockResolvedValue({ data: { data: { sessionsEnded: 2 } } })
})

describe('GuardDevicesPanel actions', () => {
  it('offers nothing to a user who cannot manage', async () => {
    mount(false)
    await screen.findByText('Thabo phone')
    expect(screen.queryByText('Report lost')).toBeNull()
    expect(screen.queryByText('Reinstate')).toBeNull()
  })

  it('a manager can report lost but not reinstate; confirms before calling the server', async () => {
    mount(true)
    await screen.findByText('Thabo phone')
    expect(screen.queryByText('Reinstate')).toBeNull()
    fireEvent.click(screen.getByText('Report lost'))
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByText('Yes, report lost'))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/devices/personal/d1/lost'))
    expect(await screen.findByText('Done. 2 sessions ended.')).toBeTruthy()
  })

  it('cancel does not call the server', async () => {
    mount(true)
    await screen.findByText('Thabo phone')
    fireEvent.click(screen.getByText('Revoke'))
    fireEvent.click(screen.getByText('Cancel'))
    expect(api.post).not.toHaveBeenCalled()
  })

  it('an admin sees Reinstate on the revoked phone and uses the reinstate endpoint', async () => {
    perms.add('SECURITY_ADMIN')
    mount(true)
    await screen.findByText('Old phone')
    fireEvent.click(screen.getByText('Reinstate'))
    fireEvent.click(await screen.findByText('Yes, reinstate'))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/devices/personal/d2/reinstate'))
  })

  it('shows the server message when the action is refused', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'This is no longer the guard\'s current device.' } } })
    perms.add('SECURITY_ADMIN')
    mount(true)
    await screen.findByText('Old phone')
    fireEvent.click(screen.getByText('Reinstate'))
    fireEvent.click(await screen.findByText('Yes, reinstate'))
    expect(await screen.findByText(/no longer the guard/)).toBeTruthy()
  })
})
