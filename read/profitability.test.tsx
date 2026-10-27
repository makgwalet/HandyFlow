import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import AgProfitabilityTab from '../pages/agriculture/AgProfitabilityTab'
import { costParts, filterUnits, percentText, stateLabel, toneOf, typesPresent } from '../pages/agriculture/agProfitability.logic'
import type { Profitability, UnitProfit } from '../pages/agriculture/agProfitability.api'

afterEach(() => cleanup())
const nb = (s: string | null) => (s ?? '').replace(/[\u00a0\u202f]/g, ' ')

const unit = (o: Partial<UnitProfit> = {}): UnitProfit => ({
  targetType: 'CROP_CYCLE', targetId: 'c1', label: 'Maize – Field 3', status: 'HARVESTED', state: 'COMPLETE', revenue: 5000, recordedCost: 1000, labour: 200, equipment: 300, fuel: 400, otherDirect: 100,
  directCost: 2000, grossMargin: 3000, marginPercent: 60, caveats: [], ...o,
})
const report = (o: Partial<Profitability> = {}): Profitability => ({
  farmId: 'f1',
  totals: { revenue: 5000, recordedCost: 1500, labour: 200, equipment: 300, fuel: 400, otherDirect: 100, directCost: 2500, grossMargin: 2500, marginPercent: 50 },
  complete: { units: 1, revenue: 5000, directCost: 2000, grossMargin: 3000, marginPercent: 60 },
  inProgress: { units: 1, revenue: 0, directCost: 500, grossMargin: -500, marginPercent: null },
  units: [unit(), unit({ targetType: 'GROUP', targetId: 'g1', label: 'Batch 7', status: 'ACTIVE', state: 'IN_PROGRESS', revenue: 0, recordedCost: 500, labour: 0, equipment: 0, fuel: 0, otherDirect: 0, directCost: 500, grossMargin: -500, marginPercent: null,
    caveats: ["The purchase price of this batch isn't recorded, so its costs are understated and its margin overstated."] })],
  notes: ['Gross margin only: revenue (ex-VAT, net of credit notes) minus direct production costs. Overheads, finance costs, depreciation and tax are not included.', '2 recorded labour entries aren\'t costed yet, so labour is understated until they are costed (Insights > Labour).'],
  ...o,
})

describe('profitability presentation logic', () => {
  it('filters by type and by state, and either may be left open', () => {
    const u = report().units
    expect(filterUnits(u, 'ALL', 'ALL')).toHaveLength(2); expect(filterUnits(u, 'GROUP', 'ALL').map(x => x.label)).toEqual(['Batch 7'])
    expect(filterUnits(u, 'ALL', 'COMPLETE').map(x => x.label)).toEqual(['Maize – Field 3']); expect(filterUnits(u, 'GROUP', 'COMPLETE')).toHaveLength(0)
  })
  it('only offers the types that appear, in a stable order', () => {
    expect(typesPresent(report().units)).toEqual(['CROP_CYCLE', 'GROUP']); expect(typesPresent([])).toEqual([])
    expect(typesPresent([unit({ targetType: 'UNLISTED' }), unit({ targetType: 'ANIMAL' })])).toEqual(['ANIMAL', 'UNLISTED'])
  })
  it('tone follows the sign', () => { expect(toneOf(1)).toBe('gain'); expect(toneOf(-0.01)).toBe('loss'); expect(toneOf(0)).toBe('even') })
  it('percent has one decimal, and a dash when there is no revenue', () => { expect(nb(percentText(60))).toContain('60,0%'); expect(nb(percentText(-250.5))).toContain('250,5%'); expect(percentText(null)).toBe('—') })
  it('lists only the cost parts that are not zero, and says what each is', () => {
    const parts = costParts(unit({ equipment: 0, fuel: 0 })); expect(parts.map(p => p.amount)).toEqual([1000, 200, 100]); expect(parts[0].label).toMatch(/Recorded costs/); expect(costParts(unit({ recordedCost: 0, labour: 0, equipment: 0, fuel: 0, otherDirect: 0 }))).toEqual([])
  })
  it('final versus to date', () => { expect(stateLabel('COMPLETE')).toBe('Final'); expect(stateLabel('IN_PROGRESS')).toBe('To date') })
})

let data: Profitability
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><AgProfitabilityTab farmId="f1" /></MemoryRouter></QueryClientProvider>)

beforeEach(() => {
  vi.clearAllMocks(); perms.clear(); perms.add('AGRICULTURE_FINANCE'); perms.add('INVOICE_READ'); data = report()
  api.get.mockImplementation(async () => ({ data }))
})

describe('Profitability page', () => {
  it('without the finance permission it explains and asks for nothing', async () => {
    perms.delete('AGRICULTURE_FINANCE'); wrap(); expect(await screen.findByText("You don't have access to profitability")).toBeTruthy(); await new Promise(r => setTimeout(r, 40)); expect(api.get).not.toHaveBeenCalled()
  })
  it('without invoice access it also explains and asks for nothing: margins show revenue', async () => {
    perms.delete('INVOICE_READ'); wrap(); expect(await screen.findByText("You don't have access to profitability")).toBeTruthy(); expect(screen.getByText(/because they show revenue/)).toBeTruthy(); await new Promise(r => setTimeout(r, 40)); expect(api.get).not.toHaveBeenCalled()
  })
  it('asks for the farm\'s report', async () => {
    wrap(); await screen.findByRole('group', { name: 'Gross margin' }); expect(api.get.mock.calls[0][0]).toBe('/api/v1/agriculture/farms/f1/profitability')
  })
  it('shows the totals, and says gross margin is not net profit', async () => {
    wrap(); const rev = await screen.findByRole('group', { name: 'Revenue' }); expect(nb(rev.textContent)).toContain('5 000,00')
    const margin = screen.getByRole('group', { name: 'Gross margin' }); expect(nb(margin.textContent)).toContain('Gain'); expect(nb(margin.textContent)).toContain('2 500,00'); expect(nb(margin.textContent)).toContain('50,0%'); expect(margin.textContent).toContain('Not net profit')
  })
  it('separates finished units (final) from units still running (to date, unsold stock not valued)', async () => {
    wrap(); const fin = within(await screen.findByLabelText('Finished')); expect(fin.getByText(/1 unit\b/)).toBeTruthy(); expect(fin.getByText(/these margins are final/)).toBeTruthy(); expect(nb(fin.getByText(/Revenue/).textContent)).toContain('5 000,00')
    const run = within(screen.getByLabelText('Still running')); expect(run.getByText(/Unsold stock isn't valued/)).toBeTruthy(); expect(nb(run.getByText(/Loss/).textContent)).toContain('500,00')
  })
  it('says where the direct costs come from', async () => {
    wrap(); const t = nb((await screen.findByLabelText('Where the direct costs come from')).textContent); for (const x of ['recorded R 1 500,00', 'labour R 200,00', 'equipment R 300,00', 'fuel R 400,00', 'other direct R 100,00']) expect(t).toContain(x)
  })
  it('lists the notes, including the uncosted labour warning', async () => {
    wrap(); const notes = within(await screen.findByLabelText('Notes about this report')); expect(notes.getByText(/Gross margin only/)).toBeTruthy(); expect(notes.getByText(/2 recorded labour entries aren't costed yet/)).toBeTruthy()
  })
  it('each unit shows its state, revenue, cost and margin, with a loss written as "Loss", never by colour alone', async () => {
    wrap(); const maize = within(await screen.findByLabelText('Crop cycle Maize – Field 3')); expect(maize.getByText('Final')).toBeTruthy(); expect(nb(maize.getByText(/Gain/).textContent)).toContain('3 000,00')
    const batch = within(screen.getByLabelText('Group Batch 7')); expect(batch.getByText('To date')).toBeTruthy(); expect(nb(batch.getByText(/Loss/).textContent)).toContain('500,00'); expect(nb(batch.getByText(/Loss/).textContent)).not.toMatch(/[-\u2212]\s*R/); expect(batch.getByText('—')).toBeTruthy()      // no revenue: no percentage
  })
  it('a unit with a caveat is marked "Read with care", and one without is not', async () => {
    wrap(); expect(within(await screen.findByLabelText('Group Batch 7')).getByRole('img', { name: 'Read with care' })).toBeTruthy(); expect(within(screen.getByLabelText('Crop cycle Maize – Field 3')).queryByRole('img', { name: 'Read with care' })).toBeNull()
  })
  it('opening a unit shows its cost breakdown, and the caveat; closing hides them', async () => {
    wrap(); fireEvent.click(await screen.findByRole('button', { name: 'Show details for Maize – Field 3' })); const d = within(screen.getByLabelText('Details for Maize – Field 3'))
    expect(nb(d.getByText(/Recorded costs/).parentElement!.textContent)).toContain('1 000,00'); expect(nb(d.getByText('Labour').parentElement!.textContent)).toContain('200,00'); expect(d.getByText('Equipment')).toBeTruthy(); expect(d.getByText('Fuel')).toBeTruthy(); expect(d.getByText('Other direct costs')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Hide details for Maize – Field 3' })); expect(screen.queryByLabelText('Details for Maize – Field 3')).toBeNull()
  })
  it('a running unit explains that its margin is not final; the caveat is shown with it', async () => {
    wrap(); fireEvent.click(await screen.findByRole('button', { name: 'Show details for Batch 7' })); const d = within(screen.getByLabelText('Details for Batch 7'))
    expect(d.getByText(/this is costs and sales to date/)).toBeTruthy(); expect(d.getByRole('note').textContent).toMatch(/purchase price of this batch isn't recorded/)
  })
  it('only one unit is open at a time', async () => {
    wrap(); fireEvent.click(await screen.findByRole('button', { name: 'Show details for Maize – Field 3' })); fireEvent.click(screen.getByRole('button', { name: 'Show details for Batch 7' }))
    expect(screen.queryByLabelText('Details for Maize – Field 3')).toBeNull(); expect(screen.getByLabelText('Details for Batch 7')).toBeTruthy()
  })
  it('the type and state filters narrow the table, and only types that appear are offered', async () => {
    wrap(); const typeSel = await screen.findByLabelText('Filter by type') as HTMLSelectElement; expect(Array.from(typeSel.options).map(o => o.text)).toEqual(['All types', 'Crop cycle', 'Group'])
    fireEvent.change(typeSel, { target: { value: 'GROUP' } }); expect(screen.queryByLabelText('Crop cycle Maize – Field 3')).toBeNull(); expect(screen.getByLabelText('Group Batch 7')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Filter by state'), { target: { value: 'COMPLETE' } }); expect(await screen.findByText('No units match these filters.')).toBeTruthy()
  })
  it('an empty farm explains how margins appear', async () => {
    data = report({ units: [], totals: { revenue: 0, recordedCost: 0, labour: 0, equipment: 0, fuel: 0, otherDirect: 0, directCost: 0, grossMargin: 0, marginPercent: null }, complete: { units: 0, revenue: 0, directCost: 0, grossMargin: 0, marginPercent: null }, inProgress: { units: 0, revenue: 0, directCost: 0, grossMargin: 0, marginPercent: null }, notes: [] })
    wrap(); expect(await screen.findByText(/Margins appear once costs are recorded or sales are allocated/)).toBeTruthy(); expect(screen.getAllByText('None yet.')).toHaveLength(2)
  })
  it('a failed load can be retried', async () => {
    api.get.mockRejectedValueOnce(new Error('boom')); wrap(); expect((await screen.findByRole('alert')).textContent).toContain("couldn't load profitability")
    fireEvent.click(screen.getByRole('button', { name: 'Try again' })); expect(await screen.findByRole('group', { name: 'Gross margin' })).toBeTruthy()
  })
})
