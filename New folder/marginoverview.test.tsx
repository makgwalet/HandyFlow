import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import AgMarginOverview from '../pages/agriculture/AgMarginOverview'
import type { FarmMargin, ProfitabilityOverview } from '../pages/agriculture/agProfitability.api'

afterEach(() => cleanup())
const nb = (s: string | null) => (s ?? '').replace(/[\u00a0\u202f]/g, ' ')
const farm = (o: Partial<FarmMargin> = {}): FarmMargin => ({ farmId: 'f1', farmName: 'Home Farm', revenue: 5000, directCost: 1500, grossMargin: 3500, marginPercent: 70, finishedUnits: 1, runningUnits: 0, breedingStockUnits: 0, cautions: 0, ...o })
const sub = (units = 0, revenue = 0, directCost = 0) => ({ units, revenue, directCost, grossMargin: revenue - directCost, marginPercent: revenue > 0 ? Number((((revenue - directCost) / revenue) * 100).toFixed(1)) : null })
const overview = (o: Partial<ProfitabilityOverview> = {}): ProfitabilityOverview => ({
  totals: { revenue: 5200, recordedCost: 1000, labour: 200, equipment: 300, fuel: 400, otherDirect: 100, directCost: 2000, grossMargin: 3200, marginPercent: 61.5 },
  complete: sub(2, 5000, 1500), inProgress: sub(1, 200, 500), breedingStock: sub(0),
  farms: [farm(), farm({ farmId: 'f2', farmName: 'River Farm', revenue: 200, directCost: 500, grossMargin: -300, marginPercent: -150, finishedUnits: 0, runningUnits: 1, cautions: 2 })],
  notes: ['Gross margin only: revenue (ex-VAT, net of credit notes) minus direct production costs. Overheads, finance costs, depreciation and tax are not included.', "River Farm: 2 recorded labour entries aren't costed yet, so labour is understated until they are costed (Insights > Labour)."],
  ...o,
})

let data: ProfitabilityOverview
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><AgMarginOverview /></MemoryRouter></QueryClientProvider>)

beforeEach(() => { vi.clearAllMocks(); perms.clear(); perms.add('AGRICULTURE_FINANCE'); perms.add('INVOICE_READ'); data = overview(); api.get.mockImplementation(async () => ({ data })) })

describe('Dashboard gross-margin card: who sees it', () => {
  it('is absent, and asks for nothing, without AGRICULTURE_FINANCE', async () => {
    perms.delete('AGRICULTURE_FINANCE'); const { container } = wrap(); await new Promise(r => setTimeout(r, 40)); expect(container.innerHTML).toBe(''); expect(api.get).not.toHaveBeenCalled()
  })
  it('is absent, and asks for nothing, without invoice access: it shows revenue', async () => {
    perms.delete('INVOICE_READ'); const { container } = wrap(); await new Promise(r => setTimeout(r, 40)); expect(container.innerHTML).toBe(''); expect(api.get).not.toHaveBeenCalled()
  })
  it('is absent for a user with neither, and for broad Agriculture rights alone', async () => {
    perms.clear(); ['AGRICULTURE_READ', 'AGRICULTURE_MANAGE', 'AGRICULTURE_ADMIN'].forEach(p => perms.add(p)); const { container } = wrap(); await new Promise(r => setTimeout(r, 40)); expect(container.innerHTML).toBe(''); expect(api.get).not.toHaveBeenCalled()
  })
  it('asks for the tenant-wide overview, once', async () => {
    wrap(); await screen.findByRole('group', { name: 'Gross margin' }); expect(api.get).toHaveBeenCalledTimes(1); expect(api.get.mock.calls[0][0]).toBe('/api/v1/agriculture/profitability/overview')
  })
})

describe('Dashboard gross-margin card: what it shows', () => {
  it('shows revenue, direct costs and the margin for all farms, saying it is not net profit', async () => {
    wrap(); expect(nb((await screen.findByRole('group', { name: 'Revenue' })).textContent)).toContain('5 200,00'); expect(nb(screen.getByRole('group', { name: 'Direct costs' }).textContent)).toContain('2 000,00')
    const m = nb(screen.getByRole('group', { name: 'Gross margin' }).textContent); expect(m).toContain('Gain'); expect(m).toContain('3 200,00'); expect(m).toContain('61,5%'); expect(screen.getByText(/not net profit/)).toBeTruthy()
  })
  it('counts finished and running units, and mentions breeding stock only when there is some', async () => {
    wrap(); expect(await screen.findByText(/2 finished, 1 still running\./)).toBeTruthy(); expect(screen.queryByText(/breeding stock\./)).toBeNull(); cleanup()
    data = overview({ breedingStock: sub(3, 0, 100) }); wrap(); expect(await screen.findByText(/2 finished, 1 still running, 3 breeding stock\./)).toBeTruthy()
  })
  it('lists each farm with its own figures; a loss is written as a word, not only by colour', async () => {
    wrap(); const home = within(await screen.findByLabelText('Farm Home Farm')); expect(nb(home.getByText(/Gain/).textContent)).toContain('3 500,00'); expect(nb(home.getByText('R 5 000,00').textContent)).toBeTruthy()
    const river = within(screen.getByLabelText('Farm River Farm')); const loss = nb(river.getByText(/Loss/).textContent); expect(loss).toContain('300,00'); expect(loss).not.toMatch(/[-\u2212]\s*R/)
  })
  it('a farm with nothing recorded says so instead of showing zeroes', async () => {
    data = overview({ farms: [farm({ farmId: 'f3', farmName: 'Idle Farm', revenue: 0, directCost: 0, grossMargin: 0, marginPercent: null, finishedUnits: 0 })] }); wrap()
    expect(within(await screen.findByLabelText('Farm Idle Farm')).getByText('No activity yet')).toBeTruthy()
  })
  it('a farm with costs but no sales yet shows its loss, not "No activity yet"; one with sales but no costs shows its gain', async () => {
    data = overview({ farms: [farm({ farmId: 'f4', farmName: 'Costs Only', revenue: 0, directCost: 800, grossMargin: -800, marginPercent: null }), farm({ farmId: 'f5', farmName: 'Sales Only', revenue: 600, directCost: 0, grossMargin: 600, marginPercent: 100 })] }); wrap()
    const costs = within(await screen.findByLabelText('Farm Costs Only')); expect(costs.queryByText('No activity yet')).toBeNull(); expect(nb(costs.getByText(/Loss/).textContent)).toContain('800,00')
    const sales = within(screen.getByLabelText('Farm Sales Only')); expect(sales.queryByText('No activity yet')).toBeNull(); expect(nb(sales.getByText(/Gain/).textContent)).toContain('600,00')
  })
  it('a farm with things to read with care is marked and says how many; one without is not', async () => {
    wrap(); expect(within(await screen.findByLabelText('Farm River Farm')).getByRole('img', { name: '2 things to read with care' })).toBeTruthy(); expect(within(screen.getByLabelText('Farm Home Farm')).queryByRole('img')).toBeNull(); cleanup()
    data = overview({ farms: [farm({ cautions: 1 })] }); wrap(); expect(await screen.findByRole('img', { name: '1 thing to read with care' })).toBeTruthy()
  })
  it('links to the Profitability screen', async () => {
    wrap(); expect((await screen.findByRole('link', { name: 'Open Profitability' })).getAttribute('href')).toBe('/agriculture/profitability')
  })
  it('keeps the notes behind "About these figures", with their count, and shows each farm warning with the farm\'s name', async () => {
    wrap(); const summary = await screen.findByText(/About these figures \(2\)/); expect(summary).toBeTruthy(); fireEvent.click(summary)
    const notes = within(screen.getByLabelText('Notes about these figures')); expect(notes.getByText(/Gross margin only/)).toBeTruthy(); expect(notes.getByText(/River Farm: 2 recorded labour entries/)).toBeTruthy()
  })
  it('shows nothing at all when there are no farms, and no notes block when there are no notes', async () => {
    data = overview({ farms: [] }); const { container } = wrap(); await new Promise(r => setTimeout(r, 60)); expect(container.innerHTML).toBe(''); cleanup()
    data = overview({ notes: [] }); wrap(); await screen.findByRole('group', { name: 'Gross margin' }); expect(screen.queryByText(/About these figures/)).toBeNull()
  })
})

describe('Dashboard gross-margin card: loading and errors', () => {
  it('says it is loading', async () => {
    api.get.mockImplementation(() => new Promise(() => {})); wrap(); expect(await screen.findByText('Loading gross margin…')).toBeTruthy()
  })
  it('a failed load is explained inside the card and can be retried', async () => {
    api.get.mockRejectedValueOnce(new Error('boom')); wrap(); expect((await screen.findByRole('alert')).textContent).toContain("couldn't load gross margin")
    fireEvent.click(screen.getByRole('button', { name: 'Try again' })); expect(await screen.findByRole('group', { name: 'Gross margin' })).toBeTruthy()
  })
})
