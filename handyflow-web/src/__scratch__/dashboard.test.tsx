import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: { children: React.ReactNode }) => <div data-testid="map">{children}</div>,
  TileLayer: () => null,
  CircleMarker: ({ children, radius }: { children: React.ReactNode; radius: number }) => <div data-testid="marker" data-radius={radius}>{children}</div>,
  Popup: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  useMap: () => ({ fitBounds: () => {} }),
}))

import AgDashboard from '../pages/agriculture/AgDashboard'
import { barPercent, dueLabel, daysBetween, fmtNum, percent, severityLabel, tidy, typeLabel } from '../pages/agriculture/agDashboard.logic'
import type { AgDashboardData } from '../pages/agriculture/agDashboard.api'

afterEach(() => cleanup())
const nb = (s: string | null) => (s ?? '').replace(/[\u00a0\u202f]/g, ' ')

describe('dashboard helpers', () => {
  it('due labels in whole days, singular and plural', () => {
    expect(dueLabel('2026-09-28', '2026-10-01')).toBe('Overdue by 3 days'); expect(dueLabel('2026-09-30', '2026-10-01')).toBe('Overdue by 1 day')
    expect(dueLabel('2026-10-01', '2026-10-01')).toBe('Due today'); expect(dueLabel('2026-10-02', '2026-10-01')).toBe('In 1 day'); expect(dueLabel('2026-10-08', '2026-10-01')).toBe('In 7 days')
    expect(dueLabel(null, '2026-10-01')).toBe(''); expect(daysBetween('2026-02-28', '2026-03-01')).toBe(1); expect(daysBetween('2027-12-31', '2028-01-01')).toBe(1)
  })
  it('bar widths scale to the largest value, with a sliver for small non-zero values', () => {
    expect(barPercent(50, 100)).toBe(50); expect(barPercent(100, 100)).toBe(100); expect(barPercent(1, 1000)).toBe(3); expect(barPercent(0, 100)).toBe(0); expect(barPercent(5, 0)).toBe(0)
  })
  it('percent and number formatting', () => {
    expect(percent(228.5, 500)).toBe(46); expect(percent(5, 0)).toBeNull(); expect(nb(fmtNum(1234.56))).toBe('1 234,6'); expect(fmtNum(null)).toBe('—')
  })
  it('known and unknown severities and types both have a readable label', () => {
    expect(severityLabel('DUE_TODAY')).toBe('Due today'); expect(severityLabel('MEDIUM')).toBe('Watch'); expect(severityLabel('SOMETHING_NEW')).toBe('Something new')
    expect(typeLabel('HARVEST_DUE')).toBe('Harvest'); expect(typeLabel('SCOUTING_HIGH_SEVERITY')).toBe('Scouting'); expect(typeLabel('PEST_ALERT')).toBe('Pest alert'); expect(tidy('MIXED')).toBe('Mixed')
  })
})

const GREEN = 'f-green', RIVER = 'f-river', BARE = 'f-bare'
const data = (o: Partial<AgDashboardData> = {}): AgDashboardData => ({
  asOf: '2026-10-01',
  totals: { farmCount: 3, totalHectares: 500, farmsWithoutHectares: 1, cropCyclesInProduction: 4, hectaresInProduction: 228.5, plannedCropCycles: 3, animalCount: 50, groupCount: 4, groupHead: 185, totalHead: 235 },
  farmTypes: [{ farmType: 'CROP', farmCount: 2, hectares: 100 }, { farmType: 'MIXED', farmCount: 1, hectares: 400 }],
  farms: [
    { id: GREEN, name: 'Green Valley', farmType: 'MIXED', province: 'Gauteng', region: null, latitude: -26.1, longitude: 28.0, totalHectares: 400, hectaresInProduction: 206.5, cropCyclesInProduction: 3, animalCount: 50, groupHead: 150, attentionCount: 3, urgentCount: 2 },
    { id: BARE, name: 'New Farm', farmType: 'CROP', province: null, region: null, latitude: null, longitude: null, totalHectares: null, hectaresInProduction: 0, cropCyclesInProduction: 0, animalCount: 0, groupHead: 0, attentionCount: 0, urgentCount: 0 },
    { id: RIVER, name: 'Riverside', farmType: 'CROP', province: 'Free State', region: null, latitude: -28.2, longitude: 28.3, totalHectares: 100, hectaresInProduction: 22, cropCyclesInProduction: 1, animalCount: 0, groupHead: 35, attentionCount: 1, urgentCount: 0 },
  ],
  crops: { byStatus: [], inProduction: [{ cropTypeId: 'c1', cropName: 'Maize', cycles: 3, hectares: 148.5 }, { cropTypeId: 'c2', cropName: 'Soybeans', cycles: 1, hectares: 80 }] },
  livestock: [{ speciesId: 's1', name: 'Sheep', category: 'LIVESTOCK', animals: 10, groupHead: 150, totalHead: 160 }, { speciesId: 's2', name: 'Cattle', category: 'LIVESTOCK', animals: 40, groupHead: 0, totalHead: 40 }],
  attention: {
    total: 12, bySeverity: [{ severity: 'CRITICAL', count: 1 }, { severity: 'OVERDUE', count: 1 }, { severity: 'UPCOMING', count: 10 }],
    items: [
      { type: 'LOW_STOCK', severity: 'CRITICAL', title: 'Maize seed is out of stock', description: '0 kg remaining', dueDate: null, referenceId: 'a', farmId: GREEN, farmName: 'Green Valley' },
      { type: 'HARVEST_DUE', severity: 'OVERDUE', title: 'Harvest due', description: 'Field 3 maize', dueDate: '2026-09-28', referenceId: 'b', farmId: GREEN, farmName: 'Green Valley' },
      { type: 'HEALTH_EVENT_DUE', severity: 'UPCOMING', title: 'VACCINATION due', description: 'Annual', dueDate: '2026-10-04', referenceId: 'c', farmId: RIVER, farmName: 'Riverside' },
    ],
  },
  ...o,
})
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><AgDashboard /></MemoryRouter></QueryClientProvider>)
const serve = (d: AgDashboardData) => api.get.mockResolvedValue({ data: d })
beforeEach(() => { vi.clearAllMocks(); serve(data()) })
const kpi = (label: string) => nb(screen.getAllByText(label)[0].parentElement!.textContent)     // the KPI card comes first; "In production" and "Livestock" are also table headings

describe('Agriculture dashboard', () => {
  it('asks the single tenant-wide endpoint, not one call per farm', async () => {
    wrap(); await screen.findByText('Active farms')
    expect(api.get).toHaveBeenCalledTimes(1); expect(api.get).toHaveBeenCalledWith('/api/v1/agriculture/dashboard')
  })
  it('KPIs: farms, land, production share, cycles and livestock, with the caveats', async () => {
    wrap(); await screen.findByText('Active farms')
    expect(kpi('Active farms')).toContain('3')
    expect(kpi('Total land')).toContain('500 ha'); expect(kpi('Total land')).toContain('1 farm with no size recorded')
    expect(kpi('In production')).toMatch(/228,5 ha/); expect(kpi('In production')).toContain('46% of recorded land')
    expect(kpi('Crop cycles in production')).toContain('4'); expect(kpi('Crop cycles in production')).toContain('3 planned')
    expect(kpi('Livestock')).toContain('235'); expect(kpi('Livestock')).toContain('50 animals · 185 in 4 groups')
  })
  it('hides the land caveat when every farm has a size', async () => {
    serve(data({ totals: { ...data().totals, farmsWithoutHectares: 0 } })); wrap(); await screen.findByText('Active farms')
    expect(kpi('Total land')).not.toContain('no size')
  })
  it('attention: severity, title, farm and due wording; severity counts; and a truncation note', async () => {
    wrap(); const panel = within(await screen.findByLabelText('Needs attention'))
    expect(panel.getByText('Maize seed is out of stock')).toBeTruthy(); expect(panel.getByText('Critical')).toBeTruthy()
    expect(panel.getByText('Overdue by 3 days')).toBeTruthy(); expect(panel.getByText('In 3 days')).toBeTruthy()
    expect(panel.getByText(/Stock · Green Valley · 0 kg remaining/)).toBeTruthy(); expect(panel.getByText(/Animal health · Riverside · Annual/)).toBeTruthy()
    expect(panel.getByText('1 critical')).toBeTruthy(); expect(panel.getByText('10 upcoming')).toBeTruthy()
    expect(panel.getByText('Showing the 3 most urgent of 12.')).toBeTruthy()
  })
  it('attention: nothing to do says so, with no truncation note', async () => {
    serve(data({ attention: { total: 0, bySeverity: [], items: [] } })); wrap()
    expect(await screen.findByText('Nothing needs attention across your farms.')).toBeTruthy(); expect(screen.queryByText(/most urgent of/)).toBeNull()
  })
  it('a severity or type from a newer server still renders', async () => {
    serve(data({ attention: { total: 1, bySeverity: [{ severity: 'BRAND_NEW', count: 1 }], items: [{ type: 'WEATHER_ALERT', severity: 'BRAND_NEW', title: 'Frost warning', description: null, dueDate: null, referenceId: 'z', farmId: null, farmName: null }] } })); wrap()
    const panel = within(await screen.findByLabelText('Needs attention')); expect(panel.getByText('Frost warning')).toBeTruthy(); expect(panel.getAllByText(/Brand new/i).length).toBeGreaterThan(0); expect(panel.getByText(/Weather alert/)).toBeTruthy()
  })
  it('farm types legend, livestock bars and crops in production', async () => {
    wrap(); const types = within(await screen.findByLabelText('Farm types'))
    expect(types.getByText(/2 farms/)).toBeTruthy(); expect(types.getByText('Mixed')).toBeTruthy()
    const live = within(screen.getByLabelText('Livestock by species')); expect(live.getByText('Sheep')).toBeTruthy(); expect(live.getByText(/10 animals \+ 150 in groups/)).toBeTruthy(); expect(nb(live.getByText(/^160 head/).textContent)).toBe('160 head · 10 animals + 150 in groups')
    const crops = within(screen.getByLabelText('Crops in production')); expect(crops.getByText('Maize')).toBeTruthy(); expect(nb(crops.getByText(/148,5 ha/).textContent)).toContain('3 cycles'); expect(nb(crops.getByText(/80 ha/).textContent)).toContain('1 cycle')
  })
  it('empty livestock and crops explain themselves', async () => {
    serve(data({ livestock: [], crops: { byStatus: [], inProduction: [] } })); wrap()
    expect(await screen.findByText('No animals or groups registered yet.')).toBeTruthy(); expect(screen.getByText('No crops are planted, growing or being harvested.')).toBeTruthy()
  })
  it('map: shows farms that have a GPS position, marks urgent ones larger, and skips the rest', async () => {
    wrap(); expect(await screen.findByTestId('map')).toBeTruthy()
    const markers = screen.getAllByTestId('marker'); expect(markers).toHaveLength(2)                    // the farm with no GPS is not plotted
    expect(markers.map(m => m.getAttribute('data-radius'))).toEqual(['11', '8'])                         // Green Valley has urgent items
    expect(screen.getAllByText('Open crop cycles')[0].getAttribute('href')).toBe(`/agriculture/crop-cycles?farm=${GREEN}`)
  })
  it('no GPS anywhere: explains how to get a map instead of showing an empty one', async () => {
    serve(data({ farms: data().farms.map(f => ({ ...f, latitude: null, longitude: null })) })); wrap()
    expect(await screen.findByText(/No farm has a GPS position yet/)).toBeTruthy(); expect(screen.queryByTestId('map')).toBeNull()
  })
  it('farm table: figures, attention badge and links that open that farm', async () => {
    wrap(); const table = within(await screen.findByLabelText('Farms'))
    const rows = table.getAllByRole('row').slice(1); expect(rows.map(r => within(r).getAllByRole('cell')[0].textContent)).toEqual(['Green ValleyGauteng', 'New Farm', 'RiversideFree State'])
    const green = within(rows[0]).getAllByRole('cell'); expect(nb(green[2].textContent)).toBe('400 ha'); expect(nb(green[3].textContent)).toBe('206,5 ha · 3 cycles'); expect(green[4].textContent).toBe('200'); expect(green[5].textContent).toBe('3 · 2 urgent')
    const bare = within(rows[1]).getAllByRole('cell'); expect(bare[2].textContent).toBe('—'); expect(bare[5].textContent).toBe('—')
    expect(within(rows[0]).getByRole('link', { name: 'Crops' }).getAttribute('href')).toBe(`/agriculture/crop-cycles?farm=${GREEN}`)
    expect(within(rows[2]).getByRole('link', { name: 'Costs' }).getAttribute('href')).toBe(`/agriculture/costs?farm=${RIVER}`)
  })
  it('is honest about what is not tracked yet', async () => {
    wrap(); expect(await screen.findByText(/Revenue, margin, labour and equipment cost, weather and trends are not tracked yet/)).toBeTruthy()
  })
  it('a tenant with no farms gets a way forward', async () => {
    serve(data({ totals: { ...data().totals, farmCount: 0 }, farms: [] })); wrap()
    expect(await screen.findByText('No active farms yet')).toBeTruthy(); expect(screen.getByRole('link', { name: 'Go to Farms' }).getAttribute('href')).toBe('/agriculture/farms')
  })
  it('a failed load offers a retry that asks again', async () => {
    api.get.mockRejectedValueOnce(new Error('boom')); wrap(); expect((await screen.findByRole('alert')).textContent).toContain("couldn't load the dashboard")
    fireEvent.click(screen.getByRole('button', { name: 'Try again' })); expect(await screen.findByText('Active farms')).toBeTruthy(); expect(api.get).toHaveBeenCalledTimes(2)
  })
  it('shows a loading state first', () => { api.get.mockReturnValue(new Promise(() => {})); wrap(); expect(screen.getByText('Loading dashboard…')).toBeTruthy() })
})
