// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
vi.mock('./alarmLogic', async (orig) => ({ ...(await orig<typeof import('./alarmLogic')>()), playBeep: vi.fn(() => true) }))

import ControlRoomTab from './ControlRoomTab'

afterEach(() => cleanup())
beforeEach(() => { vi.clearAllMocks(); localStorage.clear() })

const ev = (o: Record<string, unknown> = {}) => ({ id: 'e1', siteId: null, source: 'ALARM_PANEL', severity: 'LOW', status: 'NEW', description: 'Door', triggeredByGuardId: null, triagedAt: null, createdAt: '2026-11-20T10:00:00Z', ...o })

function setup(events: Record<string, unknown>[]) {
  api.get.mockImplementation((url: string) => {
    if (url.includes('alarm-events')) return Promise.resolve({ data: { data: { content: events } } })
    return Promise.resolve({ data: { data: [] } })
  })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ControlRoomTab /></QueryClientProvider>)
}

describe('ControlRoomTab alerts', () => {
  it('shows a panic banner for an unacknowledged duress event', async () => {
    setup([ev({ source: 'DURESS', severity: 'CRITICAL' })])
    expect(await screen.findByText(/PANIC pressed — not yet acknowledged/)).toBeTruthy()
  })
  it('shows no banner for a low alarm', async () => {
    setup([ev()])
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    expect(screen.queryByText(/not yet acknowledged/)).toBeNull()
  })
  it('sound toggle is off by default and remembers when switched on', async () => {
    setup([])
    const b = await screen.findByRole('button', { name: /Enable sound/ })
    fireEvent.click(b)
    expect(await screen.findByRole('button', { name: /Sound on/ })).toBeTruthy()
    expect(localStorage.getItem('hf.controlroom.sound')).toBe('1')
  })
})
