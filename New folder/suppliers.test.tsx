import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import AgInventoryTab, { type InventoryItemResponse } from '../pages/agriculture/AgInventoryTab'
import AgPurchasesTab from '../pages/agriculture/AgPurchasesTab'
import { receiveBody, receiveProblem, spendCsvRows, spendFileName, supplierLabel } from '../pages/agriculture/agSuppliers.logic'
import { toCsv } from '../pages/agriculture/agCrops.logic'
import type { SupplierSpendReport } from '../pages/agriculture/agSuppliers.api'

afterEach(() => cleanup())
const nb = (s: string | null) => (s ?? '').replace(/[\u00a0\u202f]/g, ' ')
const TODAY = new Date().toLocaleDateString('en-CA', { timeZone: 'Africa/Johannesburg' })
const daysAgo = (n: number) => { const d = new Date(`${TODAY}T00:00:00Z`); d.setUTCDate(d.getUTCDate() - n); return d.toISOString().slice(0, 10) }

describe('supplier logic', () => {
  it('shows the linked supplier, else a removed warning, else the old typed name, else nothing', () => {
    expect(supplierLabel({ supplierId: 's1', supplierName: 'AgriSupplies', supplier: 'typed' })).toBe('AgriSupplies')
    expect(supplierLabel({ supplierId: 's1', supplierName: null, supplier: 'typed' })).toBe('Supplier removed from Supply Chain')
    expect(supplierLabel({ supplier: 'AgriFeeds Ltd' })).toBe('AgriFeeds Ltd'); expect(supplierLabel({ supplier: '  ' })).toBeNull(); expect(supplierLabel({})).toBeNull()
  })
  it('a receipt needs a quantity above zero, and a unit cost that is blank or zero or more', () => {
    expect(receiveProblem('', '')).toMatch(/quantity above zero/); expect(receiveProblem('0', '')).toMatch(/quantity above zero/); expect(receiveProblem('-5', '')).toMatch(/quantity above zero/); expect(receiveProblem('abc', '')).toMatch(/quantity above zero/)
    expect(receiveProblem('5', '-1')).toMatch(/unit cost/); expect(receiveProblem('5', 'x')).toMatch(/unit cost/)
    expect(receiveProblem('5', '')).toBeNull(); expect(receiveProblem('5', '0')).toBeNull(); expect(receiveProblem('5,5', '12,5')).toBeNull()
  })
  it('the body sends a blank cost as null, a chosen supplier, and OMITS a blank supplier so the usual one applies', () => {
    expect(receiveBody({ qty: '50', unitCost: '', notes: '', supplierId: '' })).toEqual({ quantity: 50, newUnitCost: null, notes: null })
    expect('supplierId' in receiveBody({ qty: '50', unitCost: '', notes: '', supplierId: '' })).toBe(false)
    expect(receiveBody({ qty: '50', unitCost: '13.5', notes: 'Delivery', supplierId: 's1' })).toEqual({ quantity: 50, newUnitCost: 13.5, notes: 'Delivery', supplierId: 's1' })
    expect(receiveBody({ qty: '2,5', unitCost: '12,5', notes: '', supplierId: '' })).toEqual({ quantity: 2.5, newUnitCost: 12.5, notes: null })
  })
})

const report = (o: Partial<SupplierSpendReport> = {}): SupplierSpendReport => ({
  from: '2025-10-04', to: '2026-10-04',
  suppliers: [{ supplierId: 's1', supplierName: 'AgriSupplies', receipts: 3, spend: 1500.5, sharePercent: 75 }, { supplierId: null, supplierName: 'No supplier recorded', receipts: 1, spend: 500.25, sharePercent: 25 }],
  totalSpend: 2000.75, receipts: 4, receiptsWithoutCost: 0,
  notes: ['Spend is the cost of stock RECEIVED into Agriculture inventory (quantity x unit cost when it was received). It does not include purchase orders or supplier invoices in Supply Chain, or anything bought but never received into stock.', 'Receipts with no supplier recorded are listed together. Choose a supplier when receiving stock, or set an item\'s usual supplier, to see where the money goes.'], ...o,
})

describe('spend export', () => {
  const ctx = { farm: 'Home Farm', generatedOn: '2026-10-04' }
  it('has the farm and range, a header, a row per supplier as plain numbers, a total and the notes', () => {
    const rows = spendCsvRows(report(), ctx)
    expect(rows[0]).toEqual(['Spend by supplier']); expect(rows[1]).toEqual(['Farm', 'Home Farm']); expect(rows[2]).toEqual(['From', '2025-10-04']); expect(rows[3]).toEqual(['To', '2026-10-04'])
    const head = rows.findIndex(r => r[0] === 'Supplier'); expect(rows[head]).toEqual(['Supplier', 'Receipts', 'Spend', 'Share %'])
    expect(rows[head + 1]).toEqual(['AgriSupplies', 3, 1500.5, 75]); expect(rows[head + 2]).toEqual(['No supplier recorded', 1, 500.25, 25]); expect(rows[head + 3]).toEqual(['Total', 4, 2000.75, null])
    const i = rows.findIndex(r => r[0] === 'Notes'); expect(String(rows[i + 1][0])).toMatch(/RECEIVED into Agriculture inventory/)
  })
  it('a report with no notes has no Notes block, and money is to two places', () => {
    expect(spendCsvRows(report({ notes: [] }), ctx).some(r => r[0] === 'Notes')).toBe(false); expect(spendCsvRows(report({ totalSpend: 10.456 }), ctx).find(r => r[0] === 'Total')![2]).toBe(10.46)
  })
  it('a supplier named like a formula is made safe in the file', () => {
    const csv = toCsv(spendCsvRows(report({ suppliers: [{ supplierId: 's9', supplierName: '=HYPERLINK("http://evil","x")', receipts: 1, spend: 1, sharePercent: 100 }] }), ctx)); expect(csv).toContain(`"'=HYPERLINK(""http://evil"",""x"")"`)
  })
  it('the file name is safe and says farm and range', () => {
    expect(spendFileName('Home Farm', '2025-10-04', '2026-10-04')).toBe('spend-by-supplier-home-farm-2025-10-04-to-2026-10-04.csv'); expect(spendFileName('../../x', 'a', 'b')).toBe('spend-by-supplier-x-a-to-b.csv'); expect(spendFileName('###', 'a', 'b')).toBe('spend-by-supplier-farm-a-to-b.csv')
  })
})

// ---- the inventory tab ------------------------------------------------------------------------------------------------

const item = (o: Partial<InventoryItemResponse> = {}): InventoryItemResponse => ({ id: 'i1', farmId: 'f1', itemName: 'Maize seed', category: 'SEED', unitOfMeasure: 'kg', currentQuantity: 100, reorderLevel: 20, unitCost: 12.5, supplier: null, status: 'ACTIVE', belowReorderLevel: false, notes: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', supplierId: null, supplierName: null, ...o })
let items: InventoryItemResponse[]
const supplierList = [{ id: 's1', name: 'AgriSupplies' }, { id: 's2', name: 'Feed Co' }]
const wrapInv = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><AgInventoryTab farmId="f1" /></MemoryRouter></QueryClientProvider>)
const supplierCalls = () => api.get.mock.calls.filter(c => String(c[0]).endsWith('/agriculture/suppliers'))
const openReceive = async () => { fireEvent.click(await screen.findByTitle('Receive stock')) }

beforeEach(() => {
  vi.clearAllMocks(); perms.clear(); perms.add('AGRICULTURE_MANAGE'); perms.add('SCM_READ'); items = [item()]
  api.get.mockImplementation(async (url: string) => (url.endsWith('/agriculture/suppliers') ? { data: supplierList } : { data: { content: items, totalElements: items.length } }))
  api.post.mockResolvedValue({ data: {} }); api.put.mockResolvedValue({ data: {} }); api.patch.mockResolvedValue({ data: {} })
})

describe('Inventory: receiving stock with a supplier', () => {
  it('offers the active Supply Chain suppliers, with "No supplier recorded" first when the item has no usual supplier', async () => {
    wrapInv(); await openReceive(); const sel = await screen.findByLabelText('Supplier') as HTMLSelectElement
    await waitFor(() => expect(Array.from(sel.options).map(o => o.text)).toEqual(['No supplier recorded', 'AgriSupplies', 'Feed Co']))
  })
  it("with a usual supplier, the first option says so and is the default", async () => {
    items = [item({ supplierId: 's1', supplierName: 'AgriSupplies' })]; wrapInv(); await openReceive(); const sel = await screen.findByLabelText('Supplier') as HTMLSelectElement
    expect(sel.options[0].text).toBe('Usual supplier: AgriSupplies'); expect(sel.value).toBe('')
  })
  it('sends the chosen supplier, the unit cost paid and the quantity', async () => {
    wrapInv(); await openReceive(); await waitFor(() => expect((screen.getByLabelText('Supplier') as HTMLSelectElement).options.length).toBe(3))
    fireEvent.change(screen.getByLabelText('Supplier'), { target: { value: 's2' } }); fireEvent.change(screen.getByLabelText('Unit cost paid (R)'), { target: { value: '13.5' } })
    fireEvent.change(document.querySelector('input[type=number][step="0.01"]') as HTMLInputElement, { target: { value: '50' } }); fireEvent.click(screen.getByRole('button', { name: 'receive' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled()); expect(api.post.mock.calls[0][0]).toBe('/api/v1/agriculture/inventory-items/i1/receive'); expect(api.post.mock.calls[0][1]).toEqual({ quantity: 50, newUnitCost: 13.5, notes: null, supplierId: 's2' })
  })
  it('a blank supplier and blank cost send neither, so the usual supplier and the item cost apply', async () => {
    wrapInv(); await openReceive(); fireEvent.change(document.querySelector('input[type=number][step="0.01"]') as HTMLInputElement, { target: { value: '5' } }); fireEvent.click(screen.getByRole('button', { name: 'receive' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled()); const body = api.post.mock.calls[0][1]; expect(body).toEqual({ quantity: 5, newUnitCost: null, notes: null }); expect('supplierId' in body).toBe(false)
  })
  it('a negative unit cost is explained and the receipt cannot be sent', async () => {
    wrapInv(); await openReceive(); fireEvent.change(document.querySelector('input[type=number][step="0.01"]') as HTMLInputElement, { target: { value: '5' } }); fireEvent.change(screen.getByLabelText('Unit cost paid (R)'), { target: { value: '-3' } })
    expect((await screen.findByRole('alert')).textContent).toMatch(/unit cost must be zero or more/); expect((screen.getByRole('button', { name: 'receive' }) as HTMLButtonElement).disabled).toBe(true); expect(api.post).not.toHaveBeenCalled()
  })
  it("shows the server's own reason when a supplier is refused (for example a blacklisted one)", async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'Supplier Dodgy Ltd is blacklisted and can\'t be used for new purchases; choose another supplier.' } } })
    wrapInv(); await openReceive(); fireEvent.change(document.querySelector('input[type=number][step="0.01"]') as HTMLInputElement, { target: { value: '5' } }); fireEvent.click(screen.getByRole('button', { name: 'receive' }))
    expect((await screen.findByRole('alert')).textContent).toContain('Dodgy Ltd is blacklisted')
  })
  it('without SCM_READ there is no picker, no supplier request, and the usual supplier is named instead; nothing supplier-related is sent', async () => {
    perms.delete('SCM_READ'); items = [item({ supplierId: 's1', supplierName: 'AgriSupplies' })]; wrapInv(); await openReceive()
    expect(screen.queryByLabelText('Supplier')).toBeNull(); expect(screen.getByText('From usual supplier: AgriSupplies')).toBeTruthy(); expect(supplierCalls()).toHaveLength(0)
    fireEvent.change(document.querySelector('input[type=number][step="0.01"]') as HTMLInputElement, { target: { value: '5' } }); fireEvent.click(screen.getByRole('button', { name: 'receive' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled()); expect('supplierId' in api.post.mock.calls[0][1]).toBe(false)
  })
  it('without AGRICULTURE_MANAGE the suppliers are not requested either', async () => {
    perms.delete('AGRICULTURE_MANAGE'); wrapInv(); await openReceive(); expect(screen.queryByLabelText('Supplier')).toBeNull(); expect(supplierCalls()).toHaveLength(0)
  })
  it('issue and adjust are unchanged: no supplier or unit cost', async () => {
    wrapInv(); fireEvent.click(await screen.findByTitle('Issue stock')); expect(screen.queryByLabelText('Supplier')).toBeNull(); expect(screen.queryByLabelText('Unit cost paid (R)')).toBeNull()
  })
})

describe('Inventory: how an item shows its supplier', () => {
  it('shows the linked supplier, a removed-supplier warning, or the old typed name', async () => {
    items = [item({ id: 'a', itemName: 'A', supplierId: 's1', supplierName: 'AgriSupplies' }), item({ id: 'b', itemName: 'B', supplierId: 's9', supplierName: null }), item({ id: 'c', itemName: 'C', supplier: 'AgriFeeds Ltd' })]
    wrapInv(); await screen.findByText('A'); expect(screen.getByText(/AgriSupplies/)).toBeTruthy(); expect(screen.getByText(/Supplier removed from Supply Chain/)).toBeTruthy(); expect(screen.getByText(/AgriFeeds Ltd/)).toBeTruthy()
  })
})

describe('Inventory: the usual supplier on the edit form', () => {
  const openEdit = async () => { fireEvent.click(await screen.findByTitle('Edit')); return await screen.findByLabelText('Usual supplier (from Supply Chain)') as HTMLSelectElement }
  it('changing it saves the item, then sets the supplier', async () => {
    wrapInv(); const sel = await openEdit(); await waitFor(() => expect(sel.options.length).toBe(3)); fireEvent.change(sel, { target: { value: 's1' } }); fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(api.patch).toHaveBeenCalled()); expect(api.put).toHaveBeenCalled(); expect(api.patch.mock.calls[0][0]).toBe('/api/v1/agriculture/inventory-items/i1/supplier'); expect(api.patch.mock.calls[0][1]).toEqual({ supplierId: 's1' })
  })
  it('clearing it sends null', async () => {
    items = [item({ supplierId: 's1', supplierName: 'AgriSupplies' })]; wrapInv(); const sel = await openEdit(); await waitFor(() => expect(sel.value).toBe('s1')); fireEvent.change(sel, { target: { value: '' } }); fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(api.patch).toHaveBeenCalled()); expect(api.patch.mock.calls[0][1]).toEqual({ supplierId: null })
  })
  it('saving without touching it does not call the supplier endpoint', async () => {
    items = [item({ supplierId: 's1', supplierName: 'AgriSupplies' })]; wrapInv(); await openEdit(); fireEvent.click(screen.getByRole('button', { name: 'Save' })); await waitFor(() => expect(api.put).toHaveBeenCalled()); expect(api.patch).not.toHaveBeenCalled()
  })
  it('a usual supplier that is no longer active is still shown, marked, so saving does not silently drop it', async () => {
    items = [item({ supplierId: 's9', supplierName: 'Old Ltd' })]; wrapInv(); const sel = await openEdit(); expect(Array.from(sel.options).map(o => o.text)).toContain('Old Ltd (no longer active)'); expect(sel.value).toBe('s9')
  })
  it('without SCM_READ there is no usual-supplier control and the supplier endpoint is never called', async () => {
    perms.delete('SCM_READ'); items = [item({ supplierId: 's1', supplierName: 'AgriSupplies' })]; wrapInv(); fireEvent.click(await screen.findByTitle('Edit')); await screen.findByText('Supplier (typed name)')
    expect(screen.queryByLabelText('Usual supplier (from Supply Chain)')).toBeNull(); fireEvent.click(screen.getByRole('button', { name: 'Save' })); await waitFor(() => expect(api.put).toHaveBeenCalled()); expect(api.patch).not.toHaveBeenCalled()
  })
})

// ---- the purchases page -----------------------------------------------------------------------------------------------

let spend: SupplierSpendReport
const spendCalls = () => api.get.mock.calls.filter(c => String(c[0]).endsWith('/purchases/by-supplier'))
const wrapPur = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><AgPurchasesTab farmId="f1" farmName="Home Farm" /></MemoryRouter></QueryClientProvider>)

describe('Purchases page', () => {
  beforeEach(() => { perms.clear(); perms.add('AGRICULTURE_FINANCE'); perms.add('SCM_READ'); spend = report(); api.get.mockImplementation(async () => ({ data: spend })) })

  it('without AGRICULTURE_FINANCE it explains and asks for nothing', async () => {
    perms.delete('AGRICULTURE_FINANCE'); wrapPur(); expect(await screen.findByText("You don't have access to purchases")).toBeTruthy(); await new Promise(r => setTimeout(r, 40)); expect(api.get).not.toHaveBeenCalled()
  })
  it('without SCM_READ it explains and asks for nothing: naming suppliers is Supply Chain\'s right', async () => {
    perms.delete('SCM_READ'); wrapPur(); expect(await screen.findByText("You don't have access to purchases")).toBeTruthy(); expect(screen.getByText(/access to Supply Chain suppliers/)).toBeTruthy(); await new Promise(r => setTimeout(r, 40)); expect(api.get).not.toHaveBeenCalled()
  })
  it('asks for the last 365 days of the farm by default', async () => {
    wrapPur(); await screen.findByRole('table', { name: 'Spend by supplier' }); const c = spendCalls()[0]
    expect(c[0]).toBe('/api/v1/agriculture/farms/f1/purchases/by-supplier'); expect(c[1].params).toEqual({ from: daysAgo(365), to: TODAY })
  })
  it('shows each supplier with receipts, spend and share, and the totals', async () => {
    wrapPur(); const row = within(await screen.findByLabelText('Supplier AgriSupplies')); expect(row.getByText('3')).toBeTruthy(); expect(nb(row.getByText(/1 500,50/).textContent)).toContain('1 500,50'); expect(nb(row.getByText(/75,0%/).textContent)).toContain('75,0%')
    expect(nb(screen.getByRole('group', { name: 'Total spend' }).textContent)).toContain('2 000,75'); expect(screen.getByRole('group', { name: 'Receipts' }).textContent).toContain('4')
  })
  it('receipts with no supplier are shown as their own row', async () => {
    wrapPur(); expect(within(await screen.findByLabelText('Supplier No supplier recorded')).getByText('No supplier recorded')).toBeTruthy()
  })
  it('says what the figures are and are not, and the no-supplier advice', async () => {
    wrapPur(); const notes = within(await screen.findByLabelText('Notes about this report')); expect(notes.getByText(/RECEIVED into Agriculture inventory/)).toBeTruthy(); expect(notes.getByText(/purchase orders or supplier invoices in Supply Chain/)).toBeTruthy(); expect(notes.getByText(/Choose a supplier when receiving stock/)).toBeTruthy()
  })
  it('a zero share is a dash, never 0 percent', async () => {
    spend = report({ suppliers: [{ supplierId: 's1', supplierName: 'AgriSupplies', receipts: 2, spend: 0, sharePercent: null }], totalSpend: 0, receipts: 2, receiptsWithoutCost: 2 }); wrapPur(); expect(within(await screen.findByLabelText('Supplier AgriSupplies')).getByText('—')).toBeTruthy()
  })
  it('an empty range says nothing was received', async () => {
    spend = report({ suppliers: [], totalSpend: 0, receipts: 0, notes: [] }); wrapPur(); expect(await screen.findByText('No stock was received in this range.')).toBeTruthy(); expect(screen.queryByRole('table')).toBeNull()
  })
  it('changing a date asks again with the new range', async () => {
    wrapPur(); await screen.findByRole('table', { name: 'Spend by supplier' }); fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-01-01' } })
    await waitFor(() => expect(spendCalls().some(c => c[1].params.from === '2026-01-01' && c[1].params.to === TODAY)).toBe(true))
  })
  it('a start date after the end date is explained and no request is made for it', async () => {
    wrapPur(); await screen.findByRole('table', { name: 'Spend by supplier' }); const before = spendCalls().length; fireEvent.change(screen.getByLabelText('From'), { target: { value: daysAgo(-5) } })
    expect((await screen.findByRole('alert')).textContent).toMatch(/start date can't be after the end date/); await new Promise(r => setTimeout(r, 40)); expect(spendCalls().length).toBe(before)
  })
  it('a range longer than three years is explained and not requested', async () => {
    wrapPur(); await screen.findByRole('table', { name: 'Spend by supplier' }); const before = spendCalls().length; fireEvent.change(screen.getByLabelText('From'), { target: { value: daysAgo(1200) } })
    expect((await screen.findByRole('alert')).textContent).toMatch(/at most 1095 days/); await new Promise(r => setTimeout(r, 40)); expect(spendCalls().length).toBe(before)
  })
  it('a failed load can be retried', async () => {
    api.get.mockRejectedValueOnce(new Error('boom')); wrapPur(); expect((await screen.findByRole('alert')).textContent).toContain("couldn't load purchases"); fireEvent.click(screen.getByRole('button', { name: 'Try again' })); expect(await screen.findByRole('table', { name: 'Spend by supplier' })).toBeTruthy()
  })
  it('while a new range loads there is nothing to export, and the date controls stay', async () => {
    wrapPur(); await screen.findByRole('table', { name: 'Spend by supplier' })
    api.get.mockImplementation(() => new Promise(() => {})); fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-01-01' } })
    expect(await screen.findByText('Loading purchases…')).toBeTruthy(); expect((screen.getByRole('button', { name: /Export CSV/ }) as HTMLButtonElement).disabled).toBe(true); expect((screen.getByLabelText('From') as HTMLInputElement).value).toBe('2026-01-01')
  })
  it('exports the report as a file named for the farm and range, with the figures and notes', async () => {
    let blob: Blob | null = null; let name = ''
    URL.createObjectURL = vi.fn((b: Blob) => { blob = b; return 'blob:test' }); URL.revokeObjectURL = vi.fn()
    const click = HTMLAnchorElement.prototype.click; HTMLAnchorElement.prototype.click = function (this: HTMLAnchorElement) { name = this.download }
    try {
      wrapPur(); await screen.findByRole('table', { name: 'Spend by supplier' }); fireEvent.click(screen.getByRole('button', { name: /Export CSV/ }))
      const text = await new Promise<string>(res => { const r = new FileReader(); r.onload = () => res(String(r.result)); r.readAsText(blob!) })
      expect(name).toBe('spend-by-supplier-home-farm-2025-10-04-to-2026-10-04.csv'); expect(text).toContain('Spend by supplier'); expect(text).toContain('AgriSupplies,3,1500.5,75'); expect(text).toContain('Notes')
    } finally { HTMLAnchorElement.prototype.click = click }
  })
})
