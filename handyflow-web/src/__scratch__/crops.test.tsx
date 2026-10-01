import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import AgFarmScope from '../pages/agriculture/AgFarmScope'
import AgCropCyclesTab from '../pages/agriculture/AgCropCyclesTab'
import AgCropCycleDetail from '../pages/agriculture/AgCropCycleDetail'
import AgCropInputsTab from '../pages/agriculture/AgCropInputsTab'
import AgCropScoutingTab from '../pages/agriculture/AgCropScoutingTab'
import AgCropHarvestsTab from '../pages/agriculture/AgCropHarvestsTab'
import AgSeasonsTab from '../pages/agriculture/AgSeasonsTab'
import AgCropTypesTab from '../pages/agriculture/AgCropTypesTab'
import AgCostReportsTab from '../pages/agriculture/AgCostReportsTab'
import type { CropCycle, CycleStatus } from '../pages/agriculture/agCrops.types'

const page = <T,>(c: T[]) => ({ content: c, totalElements: c.length })
const BASE = '/api/v1/agriculture'
const FARMS = [{ id: 'f1', name: 'Green Valley', status: 'ACTIVE' }, { id: 'f2', name: 'Riverside', status: 'ACTIVE' }]
const TYPES = [
  { id: 't1', name: 'Maize', category: 'CEREAL', typicalGrowingDays: 150, defaultUnitOfMeasure: 't', status: 'ACTIVE' },
  { id: 't2', name: 'Soybeans', category: 'LEGUME', typicalGrowingDays: 120, defaultUnitOfMeasure: 'kg', status: 'ACTIVE' },
  { id: 't3', name: 'Old crop', category: 'OTHER', typicalGrowingDays: null, defaultUnitOfMeasure: 'kg', status: 'INACTIVE' },
]
const AREAS = [{ id: 'a1', name: 'Field 3', sizeHectares: 126 }, { id: 'a2', name: 'Field 7', sizeHectares: null }]
const SEASONS = [
  { id: 's1', farmId: 'f1', name: '2025/26 summer', startDate: '2025-10-01', endDate: '2026-04-30', status: 'ACTIVE', notes: null },
  { id: 's2', farmId: 'f1', name: '2026/27 summer', startDate: '2026-10-01', endDate: null, status: 'PLANNING', notes: 'next year' },
  { id: 's3', farmId: 'f1', name: '2024/25 summer', startDate: '2024-10-01', endDate: '2025-04-30', status: 'CLOSED', notes: null },
]
const INVENTORY = [
  { id: 'i1', farmId: 'f1', itemName: 'Maize seed', category: 'SEED', unitOfMeasure: 'kg', currentQuantity: 30, unitCost: 85, status: 'ACTIVE' },
  { id: 'i2', farmId: 'f1', itemName: 'LAN 28%', category: 'FERTILISER', unitOfMeasure: 'kg', currentQuantity: 1000, unitCost: 12.5, status: 'ACTIVE' },
  { id: 'i3', farmId: 'f1', itemName: 'Lick', category: 'FEED', unitOfMeasure: 'kg', currentQuantity: 10, unitCost: 5, status: 'ACTIVE' },
]
const cyc = (o: Partial<CropCycle>): CropCycle => ({ id: 'c1', farmId: 'f1', productionAreaId: 'a1', enterpriseId: null, seasonId: 's1', cropTypeId: 't1', variety: null, cycleName: 'Maize – Field 3',
  areaPlantedHectares: 126, plantingDate: '2025-10-15', expectedHarvestDate: '2026-03-14', seedInventoryItemId: null, seedQuantity: null, seedSource: null, status: 'GROWING', notes: null, createdAt: '', updatedAt: '', ...o })
let cycles: CropCycle[] = []
let inputs: object[] = [], scouting: object[] = [], harvests: object[] = []
let cost: object | null = null
let costsCrops: object[] = [], costsAnimals: object[] = [], costsGroups: object[] = []

beforeEach(() => {
  cleanup(); vi.clearAllMocks(); perms.clear(); perms.add('AGRICULTURE_READ'); perms.add('AGRICULTURE_MANAGE')
  cycles = [cyc({})]; inputs = []; scouting = []; harvests = []; cost = { cropCycleId: 'c1', cycleName: 'x', farmId: 'f1', cropTypeId: 't1', areaPlantedHectares: 126, totalSeedCost: 2550, totalInputCost: 8500, totalCost: 11050, costPerHectare: 87.7, totalLaborHours: 12.5, totalYieldHarvested: 6.8, yieldUnitOfMeasure: 't', yieldPerHectare: 0.054 }
  costsCrops = []; costsAnimals = []; costsGroups = []
  api.get.mockImplementation(async (url: string) => {
    const u = url.replace(BASE, '')
    if (u === '/farms') return { data: page(FARMS) }
    if (u === '/crop-types') return { data: page(TYPES) }
    if (u.endsWith('/seasons')) return { data: page(SEASONS) }
    if (u.endsWith('/production-areas')) return { data: page(AREAS) }
    if (u.endsWith('/enterprises')) return { data: page([]) }
    if (u.endsWith('/inventory-items')) return { data: page(INVENTORY) }
    if (u === '/farms/f1/crop-cycles') return { data: page(cycles) }
    if (u === '/farms/f1/crop-cycles/cost-summary') return { data: costsCrops }
    if (u === '/farms/f1/animals/cost-summary') return { data: costsAnimals }
    if (u === '/farms/f1/groups/cost-summary') return { data: costsGroups }
    if (u.endsWith('/cost-summary')) return { data: cost }
    if (u.endsWith('/input-applications')) return { data: page(inputs) }
    if (u.endsWith('/scouting-records')) return { data: page(scouting) }
    if (u.endsWith('/harvest-records')) return { data: page(harvests) }
    if (u.endsWith('/evidence')) return { data: [] }
    const one = /^\/crop-cycles\/([^/]+)$/.exec(u); if (one) return { data: cycles.find(c => c.id === one[1]) ?? cycles[0] }
    return { data: null }
  })
  for (const m of ['post', 'put', 'patch', 'delete'] as const) api[m].mockResolvedValue({ data: null })
})
afterEach(() => cleanup())

const wrap = (ui: React.ReactNode, url = '/agriculture/crop-cycles') => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[url]}>{ui}</MemoryRouter></QueryClientProvider>)
}
const nb = (s: string | null) => (s ?? '').replace(/[\u00a0\u202f]/g, ' ')    // en-ZA currency uses non-breaking spaces
const body = (m: typeof api.post, n = 0) => m.mock.calls[n][1] as Record<string, unknown>

describe('farm scope', () => {
  it('no active farms: explains and links to Farms', async () => {
    api.get.mockImplementation(async () => ({ data: page([]) }))
    wrap(<AgFarmScope>{f => <div>{f.id}</div>}</AgFarmScope>)
    expect(await screen.findByText('No active farms yet')).toBeTruthy()
    expect(screen.getByRole('link', { name: /Register a farm/ }).getAttribute('href')).toBe('/agriculture/farms')
  })
  it('defaults to the first farm, honours ?farm= and switching changes the farm', async () => {
    wrap(<AgFarmScope>{f => <div data-testid="farm">{f.id}</div>}</AgFarmScope>)
    expect((await screen.findByTestId('farm')).textContent).toBe('f1'); cleanup()
    wrap(<AgFarmScope>{f => <div data-testid="farm">{f.id}</div>}</AgFarmScope>, '/agriculture/costs?farm=f2')
    expect((await screen.findByTestId('farm')).textContent).toBe('f2')
    fireEvent.change(screen.getByLabelText('Farm'), { target: { value: 'f1' } })
    await waitFor(() => expect(screen.getByTestId('farm').textContent).toBe('f1'))
  })
})

describe('crop cycle list', () => {
  beforeEach(() => {
    cycles = [cyc({ id: 'c1' }), cyc({ id: 'c2', cycleName: null, cropTypeId: 't2', variety: 'DM 5.3', status: 'PLANTED', areaPlantedHectares: 80, productionAreaId: 'a2', expectedHarvestDate: '2099-01-01' }),
      cyc({ id: 'c3', status: 'PLANNED', areaPlantedHectares: 64, plantingDate: null, expectedHarvestDate: null, cycleName: 'Planned block' }),
      cyc({ id: 'c4', status: 'HARVESTED', areaPlantedHectares: 30, cycleName: 'Old harvest' }),
      cyc({ id: 'c5', status: 'GROWING', areaPlantedHectares: 10, cycleName: 'Late block', expectedHarvestDate: '2020-01-01' })]
  })
  it('joins names by id, falls back for unnamed cycles, and flags overdue harvests', async () => {
    wrap(<AgCropCyclesTab farmId="f1" />)
    expect(await screen.findByText('Maize – Field 3')).toBeTruthy()
    expect(screen.getByText('Soybeans, DM 5.3')).toBeTruthy()               // no cycleName: crop + variety
    expect(screen.getAllByText('Field 3').length).toBeGreaterThan(0); expect(screen.getByText('Field 7')).toBeTruthy()
    expect(screen.getByText(/2020-01-01 · overdue/)).toBeTruthy()
  })
  it('KPIs: area in production counts only planted/growing/harvesting cycles', async () => {
    wrap(<AgCropCyclesTab farmId="f1" />); await screen.findByText('Maize – Field 3')
    const kpi = (label: string) => screen.getByText(label).parentElement!.textContent
    expect(kpi('Area in production')).toContain('216 ha')       // 126 + 80 + 10
    expect(kpi('In production')).toContain('3'); expect(kpi('Cycles')).toContain('5')
  })
  it('filters by status and by search', async () => {
    wrap(<AgCropCyclesTab farmId="f1" />); await screen.findByText('Maize – Field 3')
    fireEvent.change(screen.getByLabelText('Filter by status'), { target: { value: 'HARVESTED' } })
    expect(screen.getByText('Old harvest')).toBeTruthy(); expect(screen.queryByText('Planned block')).toBeNull()
    fireEvent.change(screen.getByLabelText('Filter by status'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Search crop cycles'), { target: { value: 'soy' } })
    expect(screen.getByText('Soybeans, DM 5.3')).toBeTruthy(); expect(screen.queryByText('Planned block')).toBeNull()
  })
  it('read-only users cannot create', async () => {
    perms.delete('AGRICULTURE_MANAGE'); wrap(<AgCropCyclesTab farmId="f1" />); await screen.findByText('Maize – Field 3')
    expect(screen.queryByRole('button', { name: /New crop cycle/ })).toBeNull()
  })
  it('opens a cycle in place and goes back', async () => {
    wrap(<AgCropCyclesTab farmId="f1" />)
    fireEvent.click((await screen.findAllByText('Planned block'))[0])
    expect(await screen.findByRole('heading', { name: 'Planned block' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /Back to crop cycles/ }))
    expect(await screen.findByText('Late block')).toBeTruthy()
  })
})

describe('new crop cycle form', () => {
  const open = async () => { wrap(<AgCropCyclesTab farmId="f1" />); fireEvent.click(await screen.findByRole('button', { name: /New crop cycle/ })); await screen.findByLabelText(/Production area/) }
  const fillBasics = async () => {
    await waitFor(() => expect(within(screen.getByLabelText(/Production area/)).getAllByRole('option').length).toBeGreaterThan(1))
    fireEvent.change(screen.getByLabelText(/Production area/), { target: { value: 'a1' } })
    fireEvent.change(screen.getByLabelText(/^Crop/), { target: { value: 't1' } })
  }
  it('area defaults to the field size, only ACTIVE crop types are offered, and without a planting date it starts Planned', async () => {
    await open(); await fillBasics()
    expect((screen.getByLabelText(/Area planted/) as HTMLInputElement).value).toBe('126')
    expect(within(screen.getByLabelText(/^Crop/)).queryByText('Old crop')).toBeNull()
    expect(screen.getByText(/starts as Planned/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Create cycle' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    expect(api.post.mock.calls[0][0]).toBe(`${BASE}/farms/f1/crop-cycles`)
    const b = body(api.post)
    expect(b).toMatchObject({ farmId: 'f1', productionAreaId: 'a1', cropTypeId: 't1', areaPlantedHectares: 126 })
    expect(b.plantingDate).toBeUndefined(); expect(b.seedInventoryItemId).toBeUndefined()
  })
  it('suggests the harvest date from planting date + growing days, until the user sets their own', async () => {
    await open(); await fillBasics()
    fireEvent.change(screen.getByLabelText('Planting date'), { target: { value: '2026-10-15' } })
    expect((screen.getByLabelText('Expected harvest') as HTMLInputElement).value).toBe('2027-03-14')
    fireEvent.change(screen.getByLabelText('Expected harvest'), { target: { value: '2027-04-01' } })
    fireEvent.change(screen.getByLabelText('Planting date'), { target: { value: '2026-10-20' } })
    expect((screen.getByLabelText('Expected harvest') as HTMLInputElement).value).toBe('2027-04-01')
  })
  it('warns when the area exceeds the field, and blocks seed beyond stock', async () => {
    await open(); await fillBasics()
    fireEvent.change(screen.getByLabelText(/Area planted/), { target: { value: '200' } })
    expect(screen.getByText(/200 ha is more than Field 3's recorded size of 126 ha/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Planting date'), { target: { value: '2026-10-15' } })
    fireEvent.change(screen.getByLabelText('Seed from stock'), { target: { value: 'i1' } })
    fireEvent.change(screen.getByLabelText(/Seed quantity/), { target: { value: '50' } })
    expect(screen.getByRole('alert').textContent).toBe('Only 30 kg of Maize seed in stock.')
    expect((screen.getByRole('button', { name: 'Create cycle' }) as HTMLButtonElement).disabled).toBe(true)
  })
  it('only SEED stock is offered, and a planted cycle sends the seed issue', async () => {
    await open(); await fillBasics()
    fireEvent.change(screen.getByLabelText('Planting date'), { target: { value: '2026-10-15' } })
    const seeds = screen.getByLabelText('Seed from stock')
    await waitFor(() => expect(within(seeds).getByText(/Maize seed/)).toBeTruthy())
    expect(within(seeds).queryByText(/LAN/)).toBeNull(); expect(within(seeds).queryByText(/Lick/)).toBeNull()
    fireEvent.change(seeds, { target: { value: 'i1' } }); fireEvent.change(screen.getByLabelText(/Seed quantity/), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create cycle' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    expect(body(api.post)).toMatchObject({ plantingDate: '2026-10-15', seedInventoryItemId: 'i1', seedQuantity: 20, expectedHarvestDate: '2027-03-14' })
  })
})

describe('crop cycle detail: lifecycle', () => {
  const show = async (status: CycleStatus) => { cycles = [cyc({ status })]; wrap(<AgCropCycleDetail farmId="f1" cycleId="c1" onBack={() => {}} />); await screen.findByRole('heading', { name: 'Maize – Field 3' }) }
  const has = (n: RegExp) => !!screen.queryByRole('button', { name: n })
  it.each([
    ['PLANNED', ['Record planting', 'Abandon'], ['Mark growing', 'Start harvest', 'Complete harvest', 'Mark failed']],
    ['PLANTED', ['Mark growing', 'Start harvest', 'Mark failed', 'Abandon'], ['Record planting', 'Complete harvest']],
    ['GROWING', ['Start harvest', 'Mark failed'], ['Mark growing', 'Record planting', 'Complete harvest']],
    ['HARVESTING', ['Complete harvest', 'Mark failed'], ['Start harvest', 'Mark growing']],
    ['HARVESTED', [], ['Mark growing', 'Start harvest', 'Complete harvest', 'Mark failed', 'Abandon', 'Record planting']],
    ['FAILED', [], ['Mark failed', 'Abandon', 'Start harvest']],
  ] as [CycleStatus, string[], string[]][])('%s offers only its valid actions', async (status, yes, no) => {
    await show(status)
    for (const b of yes) expect(has(new RegExp(`^${b}$`)), `${b} should show`).toBe(true)
    for (const b of no) expect(has(new RegExp(`^${b}$`)), `${b} should NOT show`).toBe(false)
  })
  it('a finished-by-failure cycle shows a banner instead of the stepper', async () => {
    await show('FAILED'); expect(screen.getByText(/was marked failed/)).toBeTruthy(); expect(screen.queryByRole('list', { name: 'Crop lifecycle' })).toBeNull()
  })
  it('the stepper marks the current step', async () => {
    await show('GROWING'); const cur = screen.getByRole('list', { name: 'Crop lifecycle' }).querySelector('[aria-current="step"]')!
    expect(cur.textContent).toContain('Growing')
  })
  it('Mark growing and Start harvest call the right endpoints', async () => {
    await show('PLANTED'); fireEvent.click(screen.getByRole('button', { name: 'Mark growing' }))
    await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/crop-cycles/c1/mark-growing`, undefined))
    fireEvent.click(screen.getByRole('button', { name: 'Start harvest' }))
    await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/crop-cycles/c1/start-harvest`, undefined))
  })
  it('Record planting sends the date and the seed issue', async () => {
    await show('PLANNED'); fireEvent.click(screen.getByRole('button', { name: 'Record planting' }))
    fireEvent.change(await screen.findByLabelText('Planting date *'), { target: { value: '2026-10-15' } })
    await waitFor(() => expect(within(screen.getByLabelText('Seed from stock')).getByText(/Maize seed/)).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Seed from stock'), { target: { value: 'i1' } }); fireEvent.change(screen.getByLabelText(/Seed quantity/), { target: { value: '25' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Record planting' }).pop()!)
    await waitFor(() => expect(api.patch).toHaveBeenCalled())
    expect(api.patch.mock.calls[0][0]).toBe(`${BASE}/crop-cycles/c1/record-planting`)
    expect(body(api.patch)).toMatchObject({ plantingDate: '2026-10-15', seedInventoryItemId: 'i1', seedQuantity: 25 })
  })
  it('Record planting blocks seed beyond stock', async () => {
    await show('PLANNED'); fireEvent.click(screen.getByRole('button', { name: 'Record planting' }))
    await waitFor(() => expect(within(screen.getByLabelText('Seed from stock')).getByText(/Maize seed/)).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Seed from stock'), { target: { value: 'i1' } }); fireEvent.change(screen.getByLabelText(/Seed quantity/), { target: { value: '31' } })
    expect(screen.getByRole('alert').textContent).toContain('Only 30 kg')
    expect((screen.getAllByRole('button', { name: 'Record planting' }).pop() as HTMLButtonElement).disabled).toBe(true)
  })
  it('failing a cycle sends the reason', async () => {
    await show('GROWING'); fireEvent.click(screen.getByRole('button', { name: 'Mark failed' }))
    fireEvent.change(screen.getByLabelText(/What went wrong/), { target: { value: 'Hail' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Mark failed' }).pop()!)
    await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/crop-cycles/c1/mark-failed`, { reason: 'Hail' }))
  })
  it('completing a harvest with no harvest records asks first; with records it goes straight through', async () => {
    await show('HARVESTING'); fireEvent.click(screen.getByRole('button', { name: 'Complete harvest' }))
    expect(await screen.findByText(/No harvest has been recorded/)).toBeTruthy(); expect(api.patch).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByRole('button', { name: 'Complete harvest' }).pop()!)
    await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/crop-cycles/c1/complete-harvest`, undefined))
    cleanup(); vi.clearAllMocks(); harvests = [{ id: 'h', quantityHarvested: 5, unitOfMeasure: 't' }]
    await show('HARVESTING'); fireEvent.click(await screen.findByRole('button', { name: 'Complete harvest' }))
    await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/crop-cycles/c1/complete-harvest`, undefined))
  })
  it('Delete is admin-only', async () => {
    await show('GROWING'); expect(has(/^Delete$/)).toBe(false); cleanup(); perms.add('AGRICULTURE_ADMIN')
    await show('GROWING'); fireEvent.click(screen.getByRole('button', { name: 'Delete' })); fireEvent.click(screen.getByRole('button', { name: 'Delete crop cycle' }))
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith(`${BASE}/crop-cycles/c1`))
  })
  it('read-only users see the cycle but no actions', async () => {
    perms.delete('AGRICULTURE_MANAGE'); await show('GROWING'); expect(has(/Start harvest|Edit|Mark failed|Abandon/)).toBe(false)
  })
  it('cost summary: shows costs, a dash for no cost per hectare, and says labour is not costed', async () => {
    cost = { ...(cost as object), costPerHectare: null, yieldPerHectare: null }
    await show('GROWING'); const box = await screen.findByLabelText('Cost summary')
    await waitFor(() => expect(nb(box.textContent)).toContain('11 050'))
    expect(box.textContent).toMatch(/Cost per hectare—/); expect(box.textContent).toContain('Labour hours are shown but not costed')
  })
  it('says yield is understated only when the server reports units it could not convert', async () => {
    harvests = [{ id: 'h1', quantityHarvested: 5, unitOfMeasure: 't' }, { id: 'h2', quantityHarvested: 800, unitOfMeasure: 'kg' }]
    await show('HARVESTING'); await screen.findByLabelText('Cost summary'); await waitFor(() => expect(screen.getByLabelText('Cost summary').textContent).toContain('11'))
    expect(screen.queryByText(/yield is understated/)).toBeNull()                      // kg and t convert: no warning
    cleanup(); cost = { ...(cost as object), unconvertedYieldUnits: 1 }
    await show('HARVESTING'); expect(await screen.findByText(/can't be converted to t, so yield is understated/)).toBeTruthy()
  })
})

const cycleForTabs = cyc({ status: 'GROWING' })
describe('inputs', () => {
  const openForm = async () => { wrap(<AgCropInputsTab cycle={cycleForTabs} />); fireEvent.click(await screen.findByRole('button', { name: /Record input/ })); await screen.findByLabelText('Quantity *') }
  const pickLan = async () => { await waitFor(() => expect(within(screen.getByLabelText('From stock')).getByText(/LAN/)).toBeTruthy()); fireEvent.change(screen.getByLabelText('From stock'), { target: { value: 'i2' } }) }
  it('prefills unit, product and COST from stock (the server stores the cost exactly as sent)', async () => {
    await openForm(); await pickLan(); fireEvent.change(screen.getByLabelText('Quantity *'), { target: { value: '200' } })
    expect((screen.getByLabelText('Unit *') as HTMLInputElement).value).toBe('kg'); expect((screen.getByLabelText('Product used') as HTMLInputElement).value).toBe('LAN 28%')
    expect((screen.getByLabelText('Cost (R)') as HTMLInputElement).value).toBe('2500')
  })
  it('only crop inputs from stock are offered (no feed)', async () => {
    await openForm(); await pickLan(); expect(within(screen.getByLabelText('From stock')).queryByText(/Lick/)).toBeNull()
  })
  it('a typed cost is kept when the quantity changes', async () => {
    await openForm(); await pickLan(); fireEvent.change(screen.getByLabelText('Quantity *'), { target: { value: '200' } })
    fireEvent.change(screen.getByLabelText('Cost (R)'), { target: { value: '3000' } }); fireEvent.change(screen.getByLabelText('Quantity *'), { target: { value: '300' } })
    expect((screen.getByLabelText('Cost (R)') as HTMLInputElement).value).toBe('3000')
  })
  it('blocks more than is in stock, and warns when cost is blank', async () => {
    await openForm(); await pickLan(); fireEvent.change(screen.getByLabelText('Quantity *'), { target: { value: '2000' } })
    expect(screen.getByRole('alert').textContent).toBe('Only 1000 kg of LAN 28% in stock.')
    expect((screen.getByRole('button', { name: 'Record input' }) as HTMLButtonElement).disabled).toBe(true)
    cleanup(); await openForm(); fireEvent.change(screen.getByLabelText('Quantity *'), { target: { value: '5' } }); fireEvent.change(screen.getByLabelText('Unit *'), { target: { value: 'l' } })
    expect(screen.getByText(/won't count towards the cycle's cost per hectare/)).toBeTruthy()
  })
  it('posts the exact payload', async () => {
    await openForm(); await pickLan(); fireEvent.change(screen.getByLabelText('Quantity *'), { target: { value: '200' } })
    fireEvent.click(screen.getByRole('button', { name: 'Record input' })); await waitFor(() => expect(api.post).toHaveBeenCalled())
    expect(api.post.mock.calls[0][0]).toBe(`${BASE}/crop-cycles/c1/input-applications`)
    expect(body(api.post)).toMatchObject({ inputType: 'FERTILISER', inventoryItemId: 'i2', quantityApplied: 200, unitOfMeasure: 'kg', cost: 2500, productUsed: 'LAN 28%' })
  })
  it('lists inputs with a total', async () => {
    inputs = [{ id: 'x', applicationDate: '2025-11-01', inputType: 'FERTILISER', productUsed: 'LAN', quantityApplied: 100, unitOfMeasure: 'kg', cost: 1250, laborHours: 2, appliedByName: 'Sam' },
      { id: 'y', applicationDate: '2025-12-01', inputType: 'PESTICIDE', productUsed: 'Karate', quantityApplied: 5, unitOfMeasure: 'l', cost: 500, laborHours: null, appliedByName: null }]
    wrap(<AgCropInputsTab cycle={cycleForTabs} />); expect(await screen.findByText('Karate')).toBeTruthy()
    expect(nb(screen.getByText('Total recorded cost').parentElement!.textContent)).toContain('1 750')
  })
  it('a failed cycle cannot take new inputs', async () => {
    wrap(<AgCropInputsTab cycle={cyc({ status: 'FAILED' })} />); await waitFor(() => expect(api.get).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: /Record input/ })).toBeNull()
  })
})

describe('scouting', () => {
  const rec = (o: object) => ({ id: 'r1', scoutingDate: '2026-01-10', observationType: 'PEST', severity: 'HIGH', description: 'Armyworm', recommendedAction: null, scoutedByName: null, followUpDate: null, followUpAcknowledged: false, status: 'OPEN', ...o })
  it('a follow-up before the scouting date is refused', async () => {
    wrap(<AgCropScoutingTab cycle={cycleForTabs} />); fireEvent.click(await screen.findByRole('button', { name: /Record scouting/ }))
    fireEvent.change(screen.getByLabelText('Date *'), { target: { value: '2026-02-10' } }); fireEvent.change(screen.getByLabelText('Follow-up date'), { target: { value: '2026-02-01' } })
    fireEvent.change(screen.getByLabelText(/What did you see/), { target: { value: 'Aphids' } })
    expect(screen.getByRole('alert').textContent).toContain("can't be before"); expect((screen.getByRole('button', { name: 'Record scouting' }) as HTMLButtonElement).disabled).toBe(true)
  })
  it('posts the record', async () => {
    wrap(<AgCropScoutingTab cycle={cycleForTabs} />); fireEvent.click(await screen.findByRole('button', { name: /Record scouting/ }))
    fireEvent.change(screen.getByLabelText('Severity'), { target: { value: 'MEDIUM' } }); fireEvent.change(screen.getByLabelText(/What did you see/), { target: { value: 'Aphids' } })
    fireEvent.click(screen.getByRole('button', { name: 'Record scouting' })); await waitFor(() => expect(api.post).toHaveBeenCalled())
    expect(api.post.mock.calls[0][0]).toBe(`${BASE}/crop-cycles/c1/scouting-records`); expect(body(api.post)).toMatchObject({ observationType: 'PEST', severity: 'MEDIUM', description: 'Aphids' })
  })
  it('Resolve, Reopen and Acknowledge hit the right endpoints, and only when they make sense', async () => {
    scouting = [rec({ followUpDate: '2026-02-01' }), rec({ id: 'r2', status: 'RESOLVED', followUpDate: '2026-02-01' }), rec({ id: 'r3', followUpDate: '2026-02-01', followUpAcknowledged: true })]
    wrap(<AgCropScoutingTab cycle={cycleForTabs} />); await screen.findAllByText('Armyworm')
    expect(screen.getAllByRole('button', { name: 'Acknowledge follow-up' })).toHaveLength(1)      // not on the resolved or already-acknowledged ones
    fireEvent.click(screen.getAllByRole('button', { name: 'Resolve' })[0]); await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/scouting-records/r1/resolve`, undefined))
    fireEvent.click(screen.getByRole('button', { name: 'Reopen' })); await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/scouting-records/r2/reopen`, undefined))
    fireEvent.click(screen.getByRole('button', { name: 'Acknowledge follow-up' })); await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/scouting-records/r1/acknowledge-follow-up`, undefined))
  })
})

describe('harvests', () => {
  it('the unit defaults to the crop type\'s unit; kg is fine for a tonne crop; bags are refused and block saving', async () => {
    wrap(<AgCropHarvestsTab cycle={cycleForTabs} />); fireEvent.click(await screen.findByRole('button', { name: /Record harvest/ }))
    await waitFor(() => expect((screen.getByLabelText('Unit *') as HTMLInputElement).value).toBe('t'))
    fireEvent.change(screen.getByLabelText('Quantity *'), { target: { value: '800' } })
    fireEvent.change(screen.getByLabelText('Unit *'), { target: { value: 'kg' } }); expect(screen.queryByRole('alert')).toBeNull()
    expect((screen.getByRole('button', { name: 'Record harvest' }) as HTMLButtonElement).disabled).toBe(false)
    fireEvent.change(screen.getByLabelText('Unit *'), { target: { value: 'bags' } })
    expect(screen.getByRole('alert').textContent).toMatch(/bags can't be converted to t/); expect((screen.getByRole('button', { name: 'Record harvest' }) as HTMLButtonElement).disabled).toBe(true)
  })
  it('posts the harvest', async () => {
    wrap(<AgCropHarvestsTab cycle={cycleForTabs} />); fireEvent.click(await screen.findByRole('button', { name: /Record harvest/ }))
    await waitFor(() => expect((screen.getByLabelText('Unit *') as HTMLInputElement).value).toBe('t'))
    fireEvent.change(screen.getByLabelText('Quantity *'), { target: { value: '12.5' } }); fireEvent.click(screen.getByRole('button', { name: 'Record harvest' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled()); expect(api.post.mock.calls[0][0]).toBe(`${BASE}/crop-cycles/c1/harvest-records`)
    expect(body(api.post)).toMatchObject({ quantityHarvested: 12.5, unitOfMeasure: 't' })
  })
  it('shows the total in the crop unit, converting kg and t', async () => {
    harvests = [{ id: 'a', harvestDate: '2026-03-01', quantityHarvested: 5, unitOfMeasure: 't', qualityGrade: 'A' }, { id: 'b', harvestDate: '2026-03-02', quantityHarvested: 800, unitOfMeasure: 'kg' }]
    wrap(<AgCropHarvestsTab cycle={cycleForTabs} />); const total = (await screen.findByText('Total')).parentElement!.textContent
    expect(total).toContain('5.8 t'); expect(screen.queryByText(/Not included/)).toBeNull()
  })
  it('legacy records in a unit that cannot convert are left out of the total and called out', async () => {
    harvests = [{ id: 'a', harvestDate: '2026-03-01', quantityHarvested: 5, unitOfMeasure: 't' }, { id: 'b', harvestDate: '2026-03-02', quantityHarvested: 40, unitOfMeasure: 'bags' }]
    wrap(<AgCropHarvestsTab cycle={cycleForTabs} />); expect(await screen.findByText(/Not included in the total: bags/)).toBeTruthy()
    expect(screen.getByText('Total').parentElement!.textContent).toContain('5 t')
  })
  it('cannot record a harvest before planting', async () => {
    wrap(<AgCropHarvestsTab cycle={cyc({ status: 'PLANNED' })} />); expect(await screen.findByText(/once the crop is planted/)).toBeTruthy(); expect(screen.queryByRole('button', { name: /Record harvest/ })).toBeNull()
  })
})

describe('seasons', () => {
  it('lists newest first with cycle counts', async () => {
    cycles = [cyc({ id: 'c1', seasonId: 's1' }), cyc({ id: 'c2', seasonId: 's1' })]
    wrap(<AgSeasonsTab farmId="f1" />); await screen.findByText('2026/27 summer')
    const names = Array.from(document.querySelectorAll('strong')).map(e => e.textContent)
    expect(names).toEqual(['2026/27 summer', '2025/26 summer', '2024/25 summer']); expect(screen.getByText(/2 crop cycles/)).toBeTruthy()
  })
  it('activating a second season warns first; a closed season offers Reopen', async () => {
    wrap(<AgSeasonsTab farmId="f1" />); await screen.findByText('2026/27 summer')
    fireEvent.click(screen.getAllByRole('button', { name: 'Activate' })[0])
    expect(await screen.findByText(/2025\/26 summer is already active/)).toBeTruthy(); expect(api.patch).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByRole('button', { name: 'Activate' })[0]); await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/seasons/s2/activate`, undefined))   // [0] is the confirmation, rendered above the list
    expect(screen.getByRole('button', { name: 'Reopen' })).toBeTruthy()
  })
  it('closing is direct; end before start is refused; create posts the season', async () => {
    wrap(<AgSeasonsTab farmId="f1" />); await screen.findByText('2026/27 summer')
    fireEvent.click(screen.getByRole('button', { name: 'Close season' })); await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/seasons/s1/close`, undefined))
    fireEvent.click(screen.getByRole('button', { name: /New season/ })); fireEvent.change(screen.getByLabelText('Name *'), { target: { value: 'Winter 2027' } })
    fireEvent.change(screen.getByLabelText('Start date *'), { target: { value: '2027-05-01' } }); fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2027-04-01' } })
    expect(screen.getByRole('alert').textContent).toContain("can't be before"); expect((screen.getByRole('button', { name: 'Create season' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2027-09-01' } }); fireEvent.click(screen.getByRole('button', { name: 'Create season' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled()); expect(api.post.mock.calls[0][0]).toBe(`${BASE}/farms/f1/seasons`)
    expect(body(api.post)).toMatchObject({ farmId: 'f1', name: 'Winter 2027', startDate: '2027-05-01', endDate: '2027-09-01' })
  })
  it('delete is admin-only; a season with linked cycles cannot be deleted, an unused one can', async () => {
    cycles = [cyc({ seasonId: 's1' })]; wrap(<AgSeasonsTab farmId="f1" />); await screen.findByText('2026/27 summer'); expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull(); cleanup()
    perms.add('AGRICULTURE_ADMIN'); wrap(<AgSeasonsTab farmId="f1" />); await screen.findByText('2026/27 summer')
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete' })[1])                      // 2025/26, which the cycle uses
    expect((await screen.findByRole('alert')).textContent).toMatch(/can't be deleted while 1 crop cycle\(s\) are linked/); expect(screen.queryByRole('button', { name: 'Delete season' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'OK' })); expect(api.delete).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete' })[0])                      // 2026/27 is unused
    fireEvent.click(await screen.findByRole('button', { name: 'Delete season' })); await waitFor(() => expect(api.delete).toHaveBeenCalledWith(`${BASE}/seasons/s2`))
  })
})

describe('crop types', () => {
  it('creates a crop type with its unit; edit cannot change the category', async () => {
    wrap(<AgCropTypesTab />); fireEvent.click(await screen.findByRole('button', { name: /Add crop type/ }))
    fireEvent.change(screen.getByLabelText('Name *'), { target: { value: 'Sunflower' } }); fireEvent.change(screen.getByLabelText('Category *'), { target: { value: 'OILSEED' } })
    fireEvent.change(screen.getByLabelText('Typical growing days'), { target: { value: '130' } }); fireEvent.click(screen.getByRole('button', { name: 'Add crop type' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    expect(api.post.mock.calls[0][0]).toBe(`${BASE}/crop-types`); expect(body(api.post)).toEqual({ name: 'Sunflower', category: 'OILSEED', typicalGrowingDays: 130, defaultUnitOfMeasure: 'kg' })
    cleanup(); wrap(<AgCropTypesTab />); fireEvent.click((await screen.findAllByRole('button', { name: 'Edit' }))[0]); expect(screen.queryByLabelText('Category *')).toBeNull()
  })
  it('growing days must be a positive whole number', async () => {
    wrap(<AgCropTypesTab />); fireEvent.click(await screen.findByRole('button', { name: /Add crop type/ })); fireEvent.change(screen.getByLabelText('Name *'), { target: { value: 'X' } })
    fireEvent.change(screen.getByLabelText('Typical growing days'), { target: { value: '12.5' } }); expect((screen.getByRole('button', { name: 'Add crop type' }) as HTMLButtonElement).disabled).toBe(true)
  })
  it('deactivate and reactivate use the right endpoints', async () => {
    wrap(<AgCropTypesTab />); await screen.findByText('Old crop')
    fireEvent.click(screen.getAllByRole('button', { name: 'Deactivate' })[0]); await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/crop-types/t1/deactivate`, undefined))
    fireEvent.click(screen.getByRole('button', { name: 'Reactivate' })); await waitFor(() => expect(api.patch).toHaveBeenCalledWith(`${BASE}/crop-types/t3/reactivate`, undefined))
  })
  it('read-only users see the catalogue but cannot change it', async () => {
    perms.delete('AGRICULTURE_MANAGE'); wrap(<AgCropTypesTab />); await screen.findByText('Maize'); expect(screen.queryByRole('button', { name: /Add crop type|Edit|Deactivate/ })).toBeNull()
  })
})

describe('cost reports (costs only)', () => {
  const captureCsv = () => {
    let blob: Blob | null = null
    URL.createObjectURL = vi.fn((b: Blob) => { blob = b; return 'blob:test' }); URL.revokeObjectURL = vi.fn()
    return { text: () => new Promise<string>(res => { const r = new FileReader(); r.onload = () => res(String(r.result)); r.readAsText(blob!) }) }
  }
  it('crops: values, a dash where there is no cost per hectare, totals, and the caveats', async () => {
    costsCrops = [{ cropCycleId: 'c1', cycleName: 'Maize – Field 3', farmId: 'f1', cropTypeId: 't1', areaPlantedHectares: 100, totalSeedCost: 2000, totalInputCost: 8000, totalCost: 10000, costPerHectare: 100, totalLaborHours: 10, totalYieldHarvested: 6.8, yieldUnitOfMeasure: 't', yieldPerHectare: 0.068 },
      { cropCycleId: 'c2', cycleName: null, farmId: 'f1', cropTypeId: 't2', areaPlantedHectares: null, totalSeedCost: 0, totalInputCost: 500, totalCost: 500, costPerHectare: null, totalLaborHours: null, totalYieldHarvested: 0, yieldUnitOfMeasure: 'kg', yieldPerHectare: null }]
    wrap(<AgCostReportsTab farmId="f1" />); expect(await screen.findByText('Maize – Field 3')).toBeTruthy(); expect(screen.getByText('Soybeans')).toBeTruthy()      // unnamed cycle falls back to the crop
    const total = nb(screen.getByText('Total crop cost').parentElement!.textContent); expect(total).toContain('10 500')
    expect(screen.getByText('Average cost / ha').parentElement!.textContent).toContain('100')    // only costed area (100 ha) counts, not the null-area cycle
    expect(screen.getByText(/labour hours are shown but not costed/i)).toBeTruthy(); expect(screen.queryByText(/profit|margin|revenue/i)).toBeNull()
  })
  it('animals: explains that a dash is "no weight yet", not free', async () => {
    costsAnimals = [{ animalId: 'a1', tagNumber: 'AN-001', farmId: 'f1', acquisitionCost: 5000, totalHealthCost: 300, totalFeedCost: 700, totalCost: 6000, currentWeightKg: null, costPerKgLiveweight: null }]
    wrap(<AgCostReportsTab farmId="f1" />); fireEvent.click(await screen.findByRole('tab', { name: 'Animals' }))
    expect(await screen.findByText('AN-001')).toBeTruthy(); expect(screen.getByText(/does not mean the animal is free to keep/)).toBeTruthy()
  })
  it('groups: cost per head and head count', async () => {
    costsGroups = [{ groupId: 'g1', batchNumber: 'B-7', farmId: 'f1', totalHealthCost: 100, totalFeedCost: 900, totalCost: 1000, currentCount: 50, costPerHead: 20, averageWeightKg: 40, costPerKgLiveweight: 0.5 }]
    wrap(<AgCostReportsTab farmId="f1" />); fireEvent.click(await screen.findByRole('tab', { name: 'Groups' }))
    expect(await screen.findByText('B-7')).toBeTruthy(); expect(screen.getByText('Average per head').parentElement!.textContent).toContain('20')
  })
  it('flags crop cycles whose yield is understated because a unit could not convert', async () => {
    costsCrops = [{ cropCycleId: 'c1', cycleName: 'Maize', farmId: 'f1', cropTypeId: 't1', areaPlantedHectares: 10, totalSeedCost: 0, totalInputCost: 0, totalCost: 0, costPerHectare: 0, totalLaborHours: 0, totalYieldHarvested: 5, yieldUnitOfMeasure: 't', yieldPerHectare: 0.5, unconvertedYieldUnits: 1 },
      { cropCycleId: 'c2', cycleName: 'Soy', farmId: 'f1', cropTypeId: 't2', areaPlantedHectares: 10, totalSeedCost: 0, totalInputCost: 0, totalCost: 0, costPerHectare: 0, totalLaborHours: 0, totalYieldHarvested: 3, yieldUnitOfMeasure: 'kg', yieldPerHectare: 0.3 }]
    wrap(<AgCostReportsTab farmId="f1" />); expect(await screen.findByText(/1 crop cycle\(s\) have harvests in a unit that can't be converted/)).toBeTruthy()
  })
  it('empty farm: says so instead of an empty table', async () => {
    wrap(<AgCostReportsTab farmId="f1" />); expect(await screen.findByText(/No crop cycles to report on yet/)).toBeTruthy()
  })
  it('exports exactly what is shown as CSV, quoting names with commas', async () => {
    costsCrops = [{ cropCycleId: 'c1', cycleName: 'Maize, Field 3', farmId: 'f1', cropTypeId: 't1', areaPlantedHectares: 100, totalSeedCost: 2000, totalInputCost: 8000, totalCost: 10000, costPerHectare: 100, totalLaborHours: 10, totalYieldHarvested: 6.8, yieldUnitOfMeasure: 't', yieldPerHectare: 0.068 }]
    const cap = captureCsv(); wrap(<AgCostReportsTab farmId="f1" />); await screen.findByText('Maize, Field 3')
    fireEvent.click(screen.getByRole('button', { name: /Export CSV/ })); const csv = await cap.text()
    expect(csv.replace('\ufeff', '').split('\r\n')).toEqual(['Crop cycle,Area (ha),Seed,Inputs,Total,Cost / ha,Labour h,Yield,Yield unit,Yield / ha', '"Maize, Field 3",100,2000,8000,10000,100,10,6.8,t,0.068'])
  })
  it('a failed report offers a retry', async () => {
    api.get.mockImplementation(async (url: string) => { if (url.includes('crop-cycles/cost-summary')) throw new Error('boom'); return { data: page([]) } })
    wrap(<AgCostReportsTab farmId="f1" />); expect((await screen.findByRole('alert')).textContent).toContain("couldn't load this report")
  })
})

describe('crop type delete needs confirmation', () => {
  it('does not delete on the first click; deletes only after confirming; cancel does nothing', async () => {
    perms.add('AGRICULTURE_ADMIN'); wrap(<AgCropTypesTab />); await screen.findByText('Old crop')
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete' })[0])
    expect(await screen.findByText(/Crop cycles that use it will show "Unknown crop"/)).toBeTruthy(); expect(api.delete).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' })); expect(api.delete).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete' })[0]); fireEvent.click(screen.getByRole('button', { name: 'Delete crop type' }))
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith(`${BASE}/crop-types/t1`))
  })
})

describe('editing a crop cycle replaces fields on the server, so nothing may be lost silently', () => {
  it('Edit is prefilled with the existing notes and sends them back unchanged', async () => {
    cycles = [cyc({ status: 'GROWING', notes: 'Soil test done | Failed: none', variety: 'PAN 6767', cycleName: 'Field 3 maize' })]
    wrap(<AgCropCycleDetail farmId="f1" cycleId="c1" onBack={() => {}} />); fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    expect((screen.getByLabelText('Notes') as HTMLInputElement).value).toBe('Soil test done | Failed: none')
    fireEvent.change(screen.getByLabelText('Variety'), { target: { value: 'DKC 80-40' } }); fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(api.put).toHaveBeenCalled())
    expect(api.put.mock.calls[0][0]).toBe(`${BASE}/crop-cycles/c1`)
    expect(body(api.put as never)).toMatchObject({ variety: 'DKC 80-40', cycleName: 'Field 3 maize', notes: 'Soil test done | Failed: none', areaPlantedHectares: 126, expectedHarvestDate: '2026-03-14' })
  })
  it('clearing a field is deliberate: the help text says so, and the field is omitted', async () => {
    cycles = [cyc({ status: 'GROWING', notes: 'old note' })]
    wrap(<AgCropCycleDetail farmId="f1" cycleId="c1" onBack={() => {}} />); fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    expect(screen.getByText(/clearing one removes it/)).toBeTruthy(); expect(screen.queryByText(/left unchanged/)).toBeNull()
    fireEvent.change(screen.getByLabelText('Notes'), { target: { value: '' } }); fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(api.put).toHaveBeenCalled()); expect((body(api.put as never)).notes).toBeUndefined()
  })
  it('the season form no longer claims an end date cannot be removed', async () => {
    wrap(<AgSeasonsTab farmId="f1" />); await screen.findByText('2026/27 summer')
    fireEvent.click(screen.getAllByRole('button', { name: 'Edit' })[1])      // 2025/26 summer has an end date
    expect((screen.getByLabelText('End date') as HTMLInputElement).value).toBe('2026-04-30'); expect(screen.queryByText(/can't be removed/)).toBeNull()
  })
})
