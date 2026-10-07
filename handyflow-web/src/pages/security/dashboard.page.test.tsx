import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, fireEvent, cleanup, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))

import SecurityDashboard from './SecurityDashboard'

afterEach(() => cleanup())
beforeEach(() => vi.clearAllMocks())

const data = (o: any = {}) => ({
  asOf: '2026-10-07T11:30:00Z', activeSites: 6, openAlarms: 3,
  shifts: { onDuty: 8, scheduledToday: 11, notStarted: 1, missedToday: 0, completedToday: 3 },
  workforce: { totalGuards: 14, activeGuards: 11, psiraExpired: 1, psiraExpiring: 2, competenciesExpired: 0, competenciesExpiring: 1 },
  incidents: { open: 7, unacknowledged: 4, criticalOpen: 2, last7Days: 6 },
  complaints: { open: 5, urgent: 1 }, gate: { onSite: 7, overstayed: 1, enteredToday: 13 },
  attention: [
    { code: 'CRITICAL_INCIDENTS', level: 'DANGER', title: '2 critical incidents open', detail: 'Not yet resolved.', count: 2, section: 'incidents' },
    { code: 'OVERSTAYED', level: 'WARNING', title: '1 visitor overstayed on site', detail: 'Past the expected departure time.', count: 1, section: 'gate-dashboard' },
  ],
  activeShifts: [
    { id: 's1', guardId: 'g1', guardName: 'Lerato Dlamini', siteId: 'x', siteName: 'Centurion Mall', startAt: '2026-10-07T04:00:00Z', endAt: '2026-10-07T12:00:00Z', actualStartAt: '2026-10-07T04:22:00Z', minutesLate: 22 },
    { id: 's2', guardId: 'g2', guardName: 'Sipho Ndlovu', siteId: 'y', siteName: 'Sandton Business Park', startAt: '2026-10-07T04:00:00Z', endAt: '2026-10-07T12:00:00Z', actualStartAt: '2026-10-07T04:02:00Z', minutesLate: 2 },
  ],
  openIncidents: [{ id: 'i1', title: 'Smoke alarm in the roof plant room', severity: 'CRITICAL', status: 'OPEN', siteId: 'x', siteName: 'Centurion Mall', createdAt: '2026-10-07T08:00:00Z' }],
  ...o,
})

function renderDash(onNavigate = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><SecurityDashboard onNavigate={onNavigate} /></QueryClientProvider>)
  return onNavigate
}

describe('SecurityDashboard', () => {
  it('makes one request and shows the attention list, tiles and rows', async () => {
    api.get.mockResolvedValue({ data: { data: data() } })
    renderDash()
    expect(await screen.findByText('2 critical incidents open')).toBeTruthy()
    expect(screen.getByText('1 visitor overstayed on site')).toBeTruthy()
    expect(within(screen.getByRole('region', { name: 'Needs attention' })).getByText('1 urgent')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Guards on duty: 8' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'On site now: 7' })).toBeTruthy()
    const duty = screen.getByRole('region', { name: 'On duty now' })
    expect(within(duty).getByText('Late by 22 min')).toBeTruthy()
    expect(within(duty).getByText('On time')).toBeTruthy()
    expect(within(screen.getByRole('region', { name: 'Open incidents' })).getByText('Smoke alarm in the roof plant room')).toBeTruthy()
    expect(api.get).toHaveBeenCalledTimes(1)
    expect(api.get.mock.calls[0][0]).toBe('/api/v1/security/dashboard')
  })

  it('opens the section an attention item points at, and a tile\'s section', async () => {
    api.get.mockResolvedValue({ data: { data: data() } })
    const go = renderDash()
    fireEvent.click(await screen.findByRole('button', { name: 'Open: 1 visitor overstayed on site' }))
    expect(go).toHaveBeenCalledWith('gate-dashboard')
    fireEvent.click(screen.getByRole('button', { name: 'Control room queue: 3' }))
    expect(go).toHaveBeenCalledWith('control-room')
  })

  it('says so when nothing needs attention and nobody is on duty', async () => {
    api.get.mockResolvedValue({ data: { data: data({ attention: [], activeShifts: [], openIncidents: [],
      shifts: { onDuty: 0, scheduledToday: 0, notStarted: 0, missedToday: 0, completedToday: 0 } }) } })
    renderDash()
    expect(await screen.findByText('Nothing needs attention right now.')).toBeTruthy()
    expect(screen.getByText('All clear')).toBeTruthy()
    expect(screen.getByText('No guards are on duty.')).toBeTruthy()
    expect(screen.getByText('No open incidents.')).toBeTruthy()
  })

  it('shows an error with a retry when the dashboard cannot load', async () => {
    api.get.mockRejectedValue(new Error('boom'))
    renderDash()
    expect((await screen.findByRole('alert')).textContent).toMatch(/could not be loaded/i)
    expect(screen.getByRole('button', { name: 'Try again' })).toBeTruthy()
  })
})
