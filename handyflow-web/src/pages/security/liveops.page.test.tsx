import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, fireEvent, cleanup, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: any) => <div data-testid="map">{children}</div>,
  TileLayer: () => null,
  Marker: ({ position }: any) => <div data-testid="pin">{position.join(',')}</div>,
  Popup: ({ children }: any) => <div>{children}</div>,
}))
vi.mock('leaflet', () => ({ default: { divIcon: () => ({}) } }))
vi.mock('leaflet/dist/leaflet.css', () => ({}))

import LiveMapTab from './LiveMapTab'

afterEach(() => cleanup())
beforeEach(() => vi.clearAllMocks())

const guard = (o: any = {}) => ({ guardId: 'g' + Math.random(), guardName: 'Thabo Mokoena', grade: 'C', shiftId: 's' + Math.random(), shiftStart: '2026-10-07T04:00:00Z', shiftEnd: '2026-10-07T16:00:00Z',
  overrunning: false, siteId: 'x', siteName: 'ABC Mall', latitude: -26.1, longitude: 28.05, recordedAt: new Date().toISOString(), gpsState: 'LIVE', lastScanAt: null, lastScanCheckpoint: null, ...o })

function mockApi(live: any[]) {
  api.get.mockImplementation(async (url: string) => {
    if (url.startsWith('/api/v1/security/live/guards')) return { data: { data: live } }
    return { data: { data: { content: [{ id: 'x', name: 'ABC Mall' }] } } }
  })
}
function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><LiveMapTab /></MemoryRouter></QueryClientProvider>)
}

describe('Live Operations', () => {
  it('lists guards, plots only those with a position, and counts by GPS state', async () => {
    mockApi([guard({ guardName: 'Thabo Mokoena' }), guard({ guardName: 'Sipho Dube', gpsState: 'NO_GPS', latitude: null, longitude: null, recordedAt: null, overrunning: true })])
    renderTab()
    expect(await screen.findByText('Thabo Mokoena')).toBeTruthy()
    expect(screen.getAllByTestId('pin')).toHaveLength(1)
    expect(screen.getByText(/No GPS ping this shift/)).toBeTruthy()
    expect(screen.getByText('past end')).toBeTruthy()
    expect(screen.getByText('Active guards (2)')).toBeTruthy()
  })
  it('says so plainly when nobody is on shift, and plots nothing', async () => {
    mockApi([])
    renderTab()
    expect(await screen.findByText('No guards are on shift right now')).toBeTruthy()
    expect(screen.queryByTestId('map')).toBeNull()
  })
  it('filters by GPS state and by search', async () => {
    mockApi([guard({ guardName: 'Thabo Mokoena' }), guard({ guardName: 'Neo Khumalo', gpsState: 'STALE' })])
    renderTab()
    await screen.findByText('Thabo Mokoena')
    fireEvent.change(screen.getByLabelText('GPS state'), { target: { value: 'STALE' } })
    expect(screen.queryByText('Thabo Mokoena')).toBeNull()
    expect(screen.getByText('Neo Khumalo')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('GPS state'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Search guards'), { target: { value: 'zzz' } })
    expect(await screen.findByText('No guards match these filters')).toBeTruthy()
  })
  it('asks the server for one site when a site is chosen', async () => {
    mockApi([guard()])
    renderTab()
    await screen.findByText('Thabo Mokoena')
    await screen.findByRole('option', { name: 'ABC Mall' })
    fireEvent.change(screen.getByLabelText('Site'), { target: { value: 'x' } })
    await waitFor(() => expect(api.get.mock.calls.some(c => c[0] === '/api/v1/security/live/guards?siteId=x')).toBe(true))
  })
  it('shows an error instead of crashing when the call fails', async () => {
    api.get.mockImplementation(async (url: string) => { if (url.includes('/live/guards')) throw new Error('500'); return { data: { data: { content: [] } } } })
    renderTab()
    expect((await screen.findByRole('alert')).textContent).toMatch(/could not be loaded/i)
  })
})
