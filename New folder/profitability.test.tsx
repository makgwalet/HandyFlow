import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import AgProfitabilityTab from '../pages/agriculture/AgProfitabilityTab'
import { costParts, filterUnits, percentText, profitabilityCsvRows, profitabilityFileName, stateLabel, toneOf, typesPresent } from '../pages/agriculture/agProfitability.logic'
import { toCsv } from '../pages/agriculture/agCrops.logic'
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
  breedingStock: { units: 0, revenue: 0, directCost: 0, grossMargin: 0, marginPercent: null },
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
  it('final, to date and breeding stock', () => { expect(stateLabel('COMPLETE')).toBe('Final'); expect(stateLabel('IN_PROGRESS')).toBe('To date'); expect(stateLabel('BREEDING_STOCK')).toBe('Breeding stock') })
})

let data: Profitability
let seasonList: { id: string; name: string }[] = [{ id: 's1', name: '2026/27' }, { id: 's2', name: '2025/26' }]
const reportCalls = () => api.get.mock.calls.filter(c => String(c[0]).endsWith('/profitability'))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><AgProfitabilityTab farmId="f1" /></MemoryRouter></QueryClientProvider>)

beforeEach(() => {
  vi.clearAllMocks(); perms.clear(); perms.add('AGRICULTURE_FINANCE'); perms.add('INVOICE_READ'); data = report()
  api.get.mockImplementation(async (url: string) => (url.endsWith('/seasons') ? { data: { content: seasonList, totalElements: seasonList.length } } : { data }))
})

describe('profitability export rows', () => {
  const ctx = { farm: 'Home Farm', season: null as string | null, generatedOn: '2026-10-04' }
  it('has the farm, what it covers, the not-net-profit warning, a header, one row per unit and a total', () => {
    const rows = profitabilityCsvRows(report(), ctx)
    expect(rows[0]).toEqual(['Gross margin report']); expect(rows[1]).toEqual(['Farm', 'Home Farm']); expect(rows[2]).toEqual(['Covers', 'Whole farm']); expect(rows[3]).toEqual(['Generated', '2026-10-04'])
    expect(String(rows[4][0])).toMatch(/Not net profit/)
    const head = rows.findIndex(r => r[0] === 'Type'); expect(rows[head]).toEqual(['Type', 'Unit', 'Status', 'Margin is', 'Revenue', 'Recorded costs', 'Labour', 'Equipment', 'Fuel', 'Other direct costs', 'Direct costs', 'Gross margin', 'Margin %', 'Read with care'])
    expect(rows[head + 1].slice(0, 5)).toEqual(['Crop cycle', 'Maize – Field 3', 'HARVESTED', 'Final', 5000]); expect(rows[head + 2][3]).toBe('To date')
    expect(rows[head + 3].slice(0, 5)).toEqual(['Total', '', '', '', 5000])
  })
  it('money is plain numbers to two places and a loss is negative, so a spreadsheet can add it up', () => {
    const rows = profitabilityCsvRows(report({ units: [unit({ revenue: 100.456, grossMargin: -250.5, directCost: 350.96 })] }), ctx); const r = rows[rows.findIndex(x => x[0] === 'Type') + 1]
    expect(r[4]).toBe(100.46); expect(r[11]).toBe(-250.5); expect(typeof r[4]).toBe('number'); expect(r[12]).toBe(60)
  })
  it('caveats and notes travel with the numbers', () => {
    const rows = profitabilityCsvRows(report(), ctx); const batch = rows.find(r => r[1] === 'Batch 7')!
    expect(String(batch[13])).toMatch(/purchase price of this batch isn't recorded/); const i = rows.findIndex(r => r[0] === 'Notes'); expect(String(rows[i + 1][0])).toMatch(/Gross margin only/); expect(String(rows[i + 2][0])).toMatch(/labour entries aren't costed/)
  })
  it('a season report says so, and a report with no notes has no Notes block', () => {
    expect(profitabilityCsvRows(report(), { ...ctx, season: '2026/27' })[2]).toEqual(['Covers', 'Season: 2026/27 (its crop cycles only)']); expect(profitabilityCsvRows(report({ notes: [] }), ctx).some(r => r[0] === 'Notes')).toBe(false)
  })
  it('a unit named like a formula is made safe in the file', () => {
    const csv = toCsv(profitabilityCsvRows(report({ units: [unit({ label: '=HYPERLINK("http://evil","x")' })] }), ctx)); expect(csv).toContain(`"'=HYPERLINK(""http://evil"",""x"")"`); expect(csv).not.toMatch(/(^|,)=HYPERLINK/m)
  })
  it('the file name is safe and says farm, season and date', () => {
    expect(profitabilityFileName('Home Farm', null, '2026-10-04')).toBe('gross-margin-home-farm-2026-10-04.csv'); expect(profitabilityFileName('Home Farm', '2026/27', '2026-10-04')).toBe('gross-margin-home-farm-2026-27-2026-10-04.csv')
    expect(profitabilityFileName('../../etc/passwd', null, '2026-10-04')).toBe('gross-margin-etc-passwd-2026-10-04.csv'); expect(profitabilityFileName('###', null, '2026-10-04')).toBe('gross-margin-farm-2026-10-04.csv')
  })
})

describe('Profitability page: season and export', () => {
  it('offers the whole farm and each season, and starts on the whole farm without asking for a season', async () => {
    wrap(); const sel = await screen.findByLabelText('Season') as HTMLSelectElement
    expect(Array.from(sel.options).map(o => o.text)).toEqual(['Whole farm', 'Season: 2026/27', 'Season: 2025/26']); expect(sel.value).toBe(''); expect(reportCalls()[0][1]?.params).toBeUndefined()
  })
  it('choosing a season asks the server for that season, and going back asks for the whole farm', async () => {
    wrap(); const sel = await screen.findByLabelText('Season') as HTMLSelectElement
    fireEvent.change(sel, { target: { value: 's1' } }); await waitFor(() => expect(reportCalls().some(c => c[1]?.params?.seasonId === 's1')).toBe(true))
    fireEvent.change(sel, { target: { value: '' } }); await waitFor(() => expect(reportCalls().length).toBeGreaterThanOrEqual(1)); expect(reportCalls()[0][1]?.params).toBeUndefined()
  })
  it('switching season closes an open unit, so a stale breakdown is never shown against new figures', async () => {
    wrap(); fireEvent.click(await screen.findByRole('button', { name: 'Show details for Maize – Field 3' })); expect(screen.getByLabelText('Details for Maize – Field 3')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Season'), { target: { value: 's1' } }); await waitFor(() => expect(screen.queryByLabelText('Details for Maize – Field 3')).toBeNull())
  })
  it('while a new season loads, the controls stay, the old figures are marked as updating, and export is off so a file never mixes labels and numbers', async () => {
    wrap(); await screen.findByRole('group', { name: 'Gross margin' })
    api.get.mockImplementation((url: string) => (url.endsWith('/seasons') ? Promise.resolve({ data: { content: seasonList, totalElements: 2 } }) : new Promise(() => {})))
    fireEvent.change(screen.getByLabelText('Season'), { target: { value: 's2' } })
    expect(await screen.findByRole('status')).toBeTruthy(); expect(screen.getByText('Updating…')).toBeTruthy()
    expect((screen.getByRole('button', { name: /Export CSV/ }) as HTMLButtonElement).disabled).toBe(true); expect(screen.getByLabelText('Season')).toBeTruthy(); expect(screen.getByRole('group', { name: 'Gross margin' })).toBeTruthy()
    expect((screen.getByLabelText('Season') as HTMLSelectElement).value).toBe('s2')
  })
  it('downloads the report as a file named for the farm, season and date, with the figures and notes in it', async () => {
    let blob: Blob | null = null; let name = ''
    URL.createObjectURL = vi.fn((b: Blob) => { blob = b; return 'blob:test' }); URL.revokeObjectURL = vi.fn()
    const click = HTMLAnchorElement.prototype.click; HTMLAnchorElement.prototype.click = function (this: HTMLAnchorElement) { name = this.download }
    try {
      render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><AgProfitabilityTab farmId="f1" farmName="Home Farm" /></MemoryRouter></QueryClientProvider>)
      await screen.findByRole('group', { name: 'Gross margin' }); fireEvent.click(screen.getByRole('button', { name: /Export CSV/ }))
      const text = await new Promise<string>(res => { const r = new FileReader(); r.onload = () => res(String(r.result)); r.readAsText(blob!) })
      expect(name).toMatch(/^gross-margin-home-farm-\d{4}-\d{2}-\d{2}\.csv$/); expect(text).toContain('Gross margin report'); expect(text).toContain('Home Farm'); expect(text).toContain('Maize – Field 3'); expect(text).toContain('Whole farm'); expect(text).toContain('Notes')
    } finally { HTMLAnchorElement.prototype.click = click }
  })
  it('a season export says which season it covers', async () => {
    let blob: Blob | null = null
    URL.createObjectURL = vi.fn((b: Blob) => { blob = b; return 'blob:test' }); URL.revokeObjectURL = vi.fn()
    const click = HTMLAnchorElement.prototype.click; HTMLAnchorElement.prototype.click = function () {}
    try {
      render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><AgProfitabilityTab farmId="f1" farmName="Home Farm" /></MemoryRouter></QueryClientProvider>)
      api.get.mockImplementation(async (url: string, cfg?: { params?: { seasonId?: string } }) => (url.endsWith('/seasons') ? { data: { content: seasonList, totalElements: 2 } }
        : { data: cfg?.params?.seasonId ? report({ units: [unit({ label: 'Season maize' })] }) : data }))
      fireEvent.change(await screen.findByLabelText('Season'), { target: { value: 's1' } }); await screen.findByLabelText('Crop cycle Season maize')
      fireEvent.click(screen.getByRole('button', { name: /Export CSV/ }))
      const text = await new Promise<string>(res => { const r = new FileReader(); r.onload = () => res(String(r.result)); r.readAsText(blob!) })
      expect(text).toContain('Season: 2026/27 (its crop cycles only)')
    } finally { HTMLAnchorElement.prototype.click = click }
  })
})

describe('Profitability page', () => {
  it('without the finance permission it explains and asks for nothing', async () => {
    perms.delete('AGRICULTURE_FINANCE'); wrap(); expect(await screen.findByText("You don't have access to profitability")).toBeTruthy(); await new Promise(r => setTimeout(r, 40)); expect(api.get).not.toHaveBeenCalled()
  })
  it('without invoice access it also explains and asks for nothing: margins show revenue', async () => {
    perms.delete('INVOICE_READ'); wrap(); expect(await screen.findByText("You don't have access to profitability")).toBeTruthy(); expect(screen.getByText(/because they show revenue/)).toBeTruthy(); await new Promise(r => setTimeout(r, 40)); expect(api.get).not.toHaveBeenCalled()
  })
  it('asks for the farm\'s report', async () => {
    wrap(); await screen.findByRole('group', { name: 'Gross margin' }); expect(reportCalls()[0][0]).toBe('/api/v1/agriculture/farms/f1/profitability'); expect(reportCalls()[0][1]?.params).toBeUndefined()
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
  it('breeding stock gets its own card, row label and filter', async () => {
    data = report({ breedingStock: { units: 1, revenue: 300, directCost: 850, grossMargin: -550, marginPercent: null }, units: [...report().units, unit({ targetType: 'ANIMAL', targetId: 'a1', label: 'TAG 1 (Samson)', status: 'ACTIVE', state: 'BREEDING_STOCK', revenue: 300, recordedCost: 800, labour: 0, equipment: 0, fuel: 50, otherDirect: 0, directCost: 850, grossMargin: -550, marginPercent: null, caveats: ["Breeding stock: its purchase price is capital, so it isn't counted here. Only its running costs and any sales are shown, and this isn't a production margin."] })] })
    wrap(); const card = within(await screen.findByLabelText('Breeding stock')); expect(card.getByText(/1 unit\b/)).toBeTruthy(); expect(card.getByText(/Purchase prices are capital/)).toBeTruthy()
    expect(nb(card.getByText(/Revenue/).textContent)).toContain('300,00'); expect(nb(card.getByText(/Revenue/).textContent)).toContain('850,00'); expect(nb(card.getByText(/Loss/).textContent)).toContain('550,00')
    const row = within(screen.getByLabelText('Animal TAG 1 (Samson)')); expect(row.getByText('Breeding stock')).toBeTruthy(); expect(row.getByRole('img', { name: 'Read with care' })).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Filter by state'), { target: { value: 'BREEDING_STOCK' } }); expect(screen.queryByLabelText('Crop cycle Maize – Field 3')).toBeNull(); expect(screen.getByLabelText('Animal TAG 1 (Samson)')).toBeTruthy()
  })
  it('without breeding stock there is no card and the state filter does not offer it', async () => {
    wrap(); const sel = await screen.findByLabelText('Filter by state') as HTMLSelectElement; expect(Array.from(sel.options).map(o => o.value)).toEqual(['ALL', 'COMPLETE', 'IN_PROGRESS']); expect(screen.queryByLabelText('Breeding stock')).toBeNull()
  })
  it('an empty farm explains how margins appear', async () => {
    data = report({ units: [], totals: { revenue: 0, recordedCost: 0, labour: 0, equipment: 0, fuel: 0, otherDirect: 0, directCost: 0, grossMargin: 0, marginPercent: null }, complete: { units: 0, revenue: 0, directCost: 0, grossMargin: 0, marginPercent: null }, inProgress: { units: 0, revenue: 0, directCost: 0, grossMargin: 0, marginPercent: null }, breedingStock: { units: 0, revenue: 0, directCost: 0, grossMargin: 0, marginPercent: null }, notes: [] })
    wrap(); expect(await screen.findByText(/Margins appear once costs are recorded or sales are allocated/)).toBeTruthy(); expect(screen.getAllByText('None yet.')).toHaveLength(2)
  })
  it('a failed load can be retried', async () => {
    api.get.mockImplementation(async (url: string) => { if (url.endsWith('/seasons')) return { data: { content: [], totalElements: 0 } }; throw new Error('boom') }); wrap(); expect((await screen.findByRole('alert')).textContent).toContain("couldn't load profitability")
    api.get.mockImplementation(async (url: string) => (url.endsWith('/seasons') ? { data: { content: [], totalElements: 0 } } : { data })); fireEvent.click(screen.getByRole('button', { name: 'Try again' })); expect(await screen.findByRole('group', { name: 'Gross margin' })).toBeTruthy()
  })
})
