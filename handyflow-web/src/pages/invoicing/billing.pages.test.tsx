import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import QuotesListPage from './QuotesListPage'
import InvoicesListPage from './InvoicesListPage'
import InvoiceDetailPage from './InvoiceDetailPage'
import RecurringListPage from './RecurringListPage'
import RecurringDetailPage from './RecurringDetailPage'
import RetainersListPage from './RetainersListPage'
import CreditNotesListPage from './CreditNotesListPage'
import { QuoteDetailPage } from '../quotes/QuoteDetailPage'

afterEach(() => cleanup())
beforeEach(() => { vi.clearAllMocks(); perms.clear() })

const day = (offset: number) => { const d = new Date(); d.setDate(d.getDate() + offset); d.setHours(10, 0, 0, 0); return d.toISOString() }
const page = (rows: any[]) => ({ data: { data: { content: rows, totalElements: rows.length } } })
const one = (row: any) => ({ data: { data: row } })

const quote = (o: any = {}) => ({ id: 'q1', quoteNumber: 'QT-0001', title: 'Guarding contract', status: 'DRAFT', total: 1150, subtotal: 1000, vatTotal: 150,
  expiresAt: null, createdAt: day(-5), customerId: 'c1', lineItems: [{ id: 'l1', description: 'Guard hours', unit: 'hr', quantity: 10, unitPrice: 100, vatRate: 15, lineTotal: 1000 }], ...o })
const invoice = (o: any = {}) => ({ id: 'i1', invoiceNumber: 'INV-0001', customerId: 'c1', status: 'ISSUED', issuedAt: day(-10), dueDate: day(5), subtotal: 100, vatTotal: 15,
  total: 115, amountPaid: 0, lineItems: [{ id: 'l1', description: 'Site fee', quantity: 1, unitPrice: 100, vatRate: 15, lineTotal: 100 }], createdAt: day(-10),
  invoiceType: 'STANDARD', recurringScheduleId: null, committedHours: null, ratePerHour: null, hoursConsumed: null, walkinClientName: null, ...o })
const schedule = (o: any = {}) => ({ id: 's1', title: 'Monthly site security', status: 'ACTIVE', frequency: 'MONTHLY', customIntervalDays: null, nextRunAt: day(3), lastRunAt: null,
  total: 5000, subtotal: 4348, vatTotal: 652, customerId: 'c1', lineItems: [{ id: 'l1', description: 'Security', quantity: 1, unitPrice: 4348, vatRate: 15, lineTotal: 4348 }],
  walkinClientName: null, createdAt: day(-60), variableHours: false, ratePerHour: null, minimumHoursPerCycle: null, hoursVatRate: null, contractStartDate: null,
  contractEndDate: null, contractedTotalHours: null, totalHoursBilled: 0, remainingCycles: 0, ...o })

function route(map: Record<string, any>) {
  api.get.mockImplementation((url: string) => {
    for (const [k, v] of Object.entries(map)) if (url.includes(k)) return v instanceof Error ? Promise.reject(v) : Promise.resolve(v)
    return Promise.resolve(page([]))
  })
}
const customers = page([{ id: 'c1', name: 'Acme Mining' }])

function renderAt(path: string, pattern: string, element: JSX.Element) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  function Where() { return <div data-testid="where">{useLocation().pathname}</div> }
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[path]}><Where />
    <Routes><Route path={pattern} element={element} /><Route path="*" element={<div>elsewhere</div>} /></Routes></MemoryRouter></QueryClientProvider>)
}

describe('QuotesListPage', () => {
  it('shows the headline figures, customer names and a not-opened note on sent quotes', async () => {
    route({ '/quotes': page([quote(), quote({ id: 'q2', quoteNumber: 'QT-0002', status: 'SENT', total: 2000, firstViewedAt: null }),
      quote({ id: 'q3', quoteNumber: 'QT-0003', status: 'ACCEPTED', total: 3000 })]), '/crm/customers': customers })
    renderAt('/quotes', '/quotes', <QuotesListPage />)
    expect(await screen.findByText('QT-0002')).toBeTruthy()
    expect(screen.getAllByText('Acme Mining').length).toBe(3)
    expect(screen.getByText('Not opened yet')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Accepted, not invoiced: 1' })).toBeTruthy()
  })
  it('filters by status pill and by search, and says when nothing matches', async () => {
    route({ '/quotes': page([quote(), quote({ id: 'q2', quoteNumber: 'QT-0002', status: 'SENT', title: 'Alarm install' })]), '/crm/customers': customers })
    renderAt('/quotes', '/quotes', <QuotesListPage />)
    await screen.findByText('QT-0002')
    fireEvent.click(within(screen.getByRole('group', { name: 'Quote status' })).getByRole('button', { name: /^Sent/ }))
    expect(screen.queryByText('QT-0001')).toBeNull()
    expect(screen.getByText('QT-0002')).toBeTruthy()
    fireEvent.click(within(screen.getByRole('group', { name: 'Quote status' })).getByRole('button', { name: /^All/ }))
    fireEvent.change(screen.getByLabelText(/Search quote/), { target: { value: 'alarm' } })
    expect(screen.queryByText('QT-0001')).toBeNull()
    fireEvent.change(screen.getByLabelText(/Search quote/), { target: { value: 'zzz' } })
    expect(screen.getByText('No quotes match')).toBeTruthy()
  })
  it('offers New quote only with create permission, and opens a quote on click', async () => {
    route({ '/quotes': page([quote()]), '/crm/customers': customers })
    const first = renderAt('/quotes', '/quotes', <QuotesListPage />)
    await screen.findByText('QT-0001')
    expect(screen.queryByRole('button', { name: /New quote/ })).toBeNull()
    first.unmount(); perms.add('INVOICE_CREATE')
    renderAt('/quotes', '/quotes', <QuotesListPage />)
    expect(await screen.findByRole('button', { name: /New quote/ })).toBeTruthy()
    fireEvent.click(await screen.findByText('QT-0001'))
    expect(screen.getByTestId('where').textContent).toBe('/quotes/q1')
  })
  it('shows an error with a retry', async () => {
    route({ '/quotes': new Error('boom'), '/crm/customers': customers })
    renderAt('/quotes', '/quotes', <QuotesListPage />)
    expect((await screen.findByRole('alert')).textContent).toMatch(/could not be loaded/)
    expect(screen.getByRole('button', { name: 'Try again' })).toBeTruthy()
  })
  it('says when a long list was cut short', async () => {
    api.get.mockImplementation((url: string) => Promise.resolve(url.includes('/quotes') ? { data: { data: { content: [quote()], totalElements: 450 } } } : customers))
    renderAt('/quotes', '/quotes', <QuotesListPage />)
    expect(await screen.findByText(/newest 200 of 450 quotes/)).toBeTruthy()
  })
})

describe('InvoicesListPage', () => {
  const rows = () => page([
    invoice(), invoice({ id: 'i2', invoiceNumber: 'INV-0002', status: 'OVERDUE', dueDate: day(-3), total: 230, amountPaid: 30 }),
    invoice({ id: 'i3', invoiceNumber: 'INV-0003', status: 'PAID', total: 100, amountPaid: 100 }), invoice({ id: 'i4', invoiceNumber: 'INV-0004', status: 'DRAFT' }),
  ])
  it('counts what is owing, words the due dates and filters to open invoices', async () => {
    route({ '/invoices': rows(), '/crm/customers': customers })
    renderAt('/invoices', '/invoices', <InvoicesListPage />)
    await screen.findByText('INV-0002')
    expect(screen.getByRole('button', { name: /^Outstanding: R 315$/ })).toBeTruthy()
    expect(screen.getByText('3 days overdue')).toBeTruthy()
    expect(screen.getByText('Due in 5 days')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /^Open/ }))
    expect(screen.queryByText('INV-0003')).toBeNull()
    expect(screen.queryByText('INV-0004')).toBeNull()
    expect(screen.getByText('INV-0001')).toBeTruthy()
  })
  it('records a payment: refuses more than is owing, then posts the amount', async () => {
    perms.add('INVOICE_CREATE')
    route({ '/invoices': page([invoice({ total: 115, amountPaid: 15 })]), '/crm/customers': customers })
    api.post.mockResolvedValue({ data: {} })
    renderAt('/invoices', '/invoices', <InvoicesListPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Record payment' }))
    const dialog = screen.getByRole('dialog')
    const amount = within(dialog).getByLabelText('Amount received (R)') as HTMLInputElement
    expect(amount.value).toBe('100')
    fireEvent.change(amount, { target: { value: '250' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }))
    expect((await within(dialog).findByRole('alert')).textContent).toMatch(/more than/)
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.change(amount, { target: { value: '60' } })
    fireEvent.change(within(dialog).getByLabelText('Reference (optional)'), { target: { value: 'EFT-1' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Record payment' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/invoicing/invoices/i1/payments', { amountPaid: 60, paymentMethod: 'EFT', reference: 'EFT-1' }))
  })
  it('issues a draft, and hides actions without permission', async () => {
    route({ '/invoices': page([invoice({ status: 'DRAFT' })]), '/crm/customers': customers })
    api.post.mockResolvedValue({ data: {} })
    const first = renderAt('/invoices', '/invoices', <InvoicesListPage />)
    await screen.findByText('INV-0001')
    expect(screen.queryByRole('button', { name: 'Issue' })).toBeNull()
    first.unmount(); perms.add('INVOICE_CREATE')
    renderAt('/invoices', '/invoices', <InvoicesListPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Issue' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/invoicing/invoices/i1/issue'))
  })
  it('opens an invoice on click', async () => {
    route({ '/invoices': page([invoice()]), '/crm/customers': customers })
    renderAt('/invoices', '/invoices', <InvoicesListPage />)
    fireEvent.click(await screen.findByText('INV-0001'))
    expect(screen.getByTestId('where').textContent).toBe('/invoices/i1')
  })
})

describe('InvoiceDetailPage', () => {
  const open = (inv: any, extra: Record<string, any> = {}) => {
    route({ '/credit-notes': { data: { data: [] } }, '/invoicing/invoices/i1': one(inv), '/crm/customers/c1': one({ name: 'Acme Mining', email: 'ap@acme.test' }), ...extra })
    return renderAt('/invoices/i1', '/invoices/:id', <InvoiceDetailPage />)
  }
  it('shows what is owing, the customer and the lines', async () => {
    open(invoice({ total: 115, amountPaid: 15, status: 'PARTIALLY_PAID' }))
    expect(await screen.findByText('Acme Mining')).toBeTruthy()
    expect(screen.getByText('Site fee')).toBeTruthy()
    const pay = screen.getByRole('region', { name: 'Payment' })
    expect(within(pay).getByText('R 100,00', { exact: false })).toBeTruthy()
    expect(screen.getByText('Part paid')).toBeTruthy()
  })
  it('shows retainer hours with an overage warning and offers Log hours', async () => {
    perms.add('INVOICE_CREATE')
    open(invoice({ invoiceType: 'RETAINER', committedHours: 100, hoursConsumed: 112.5, ratePerHour: 500 }))
    expect(await screen.findByText(/12.5h over the commitment/)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Log hours' })).toBeTruthy()
  })
  it('issues a credit note after checking the form', async () => {
    perms.add('INVOICE_CREATE')
    api.post.mockResolvedValue({ data: {} })
    open(invoice())
    fireEvent.click(await screen.findByRole('button', { name: 'Credit note' }))
    const d = screen.getByRole('dialog')
    fireEvent.click(within(d).getByRole('button', { name: 'Issue credit note' }))
    expect((await within(d).findByRole('alert')).textContent).toMatch(/reason/i)
    fireEvent.change(within(d).getByLabelText('Reason'), { target: { value: 'Overcharged' } })
    fireEvent.change(within(d).getByLabelText('Amount (R, excluding VAT)'), { target: { value: '500' } })
    expect(within(d).getByText(/R 575,00 in total/)).toBeTruthy()
    fireEvent.click(within(d).getByRole('button', { name: 'Issue credit note' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/invoicing/invoices/i1/credit-notes', { reason: 'Overcharged', description: undefined, amount: 500, vatRate: 15 }))
  })
  it('has no actions for a cancelled invoice, and a clear message when it is not found', async () => {
    perms.add('INVOICE_CREATE')
    open(invoice({ status: 'CANCELLED' }))
    await screen.findByText('Cancelled')
    expect(screen.queryByRole('button', { name: 'Record payment' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Credit note' })).toBeNull()
    cleanup()
    open(invoice(), { '/invoicing/invoices/i1': new Error('404') })
    expect(await screen.findByText('Invoice not found')).toBeTruthy()
  })
})

describe('RecurringListPage and detail', () => {
  it('totals recurring revenue and pauses a schedule', async () => {
    perms.add('INVOICE_CREATE')
    route({ '/recurring-schedules': page([schedule(), schedule({ id: 's2', title: 'Paused thing', status: 'PAUSED', total: 9999 })]), '/crm/customers': customers })
    api.post.mockResolvedValue({ data: {} })
    renderAt('/recurring', '/recurring', <RecurringListPage />)
    await screen.findByText('Monthly site security')
    expect(screen.getByText('Monthly recurring revenue').previousSibling?.textContent).toMatch(/5\D?000/)
    expect(screen.getByText('Runs in 3 days')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Pause' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/invoicing/recurring-schedules/s1/pause'))
    fireEvent.click(screen.getByRole('button', { name: 'Resume' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/invoicing/recurring-schedules/s2/resume'))
  })
  it('logs cycle hours, billing the minimum when fewer were worked', async () => {
    perms.add('INVOICE_CREATE')
    route({ '/recurring-schedules': page([schedule({ id: 'v1', title: 'Excavator hire', variableHours: true, ratePerHour: 900, minimumHoursPerCycle: 160 })]), '/crm/customers': customers })
    api.post.mockResolvedValue({ data: {} })
    renderAt('/recurring', '/recurring', <RecurringListPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Log hours' }))
    const d = screen.getByRole('dialog')
    fireEvent.click(within(d).getByRole('button', { name: 'Log hours and invoice' }))
    expect((await within(d).findByRole('alert')).textContent).toMatch(/hours/i)
    fireEvent.change(within(d).getByLabelText('Actual hours worked'), { target: { value: '120' } })
    fireEvent.change(within(d).getByLabelText('Period'), { target: { value: 'June 2026' } })
    expect(within(d).getByText(/bills 160h, not 120h/)).toBeTruthy()
    fireEvent.click(within(d).getByRole('button', { name: 'Log hours and invoice' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/invoicing/recurring-schedules/v1/log-cycle-hours', { actualHours: 120, periodLabel: 'June 2026', operatorNotes: undefined }))
  })
  it('shows a schedule with the invoices it created', async () => {
    route({ '/recurring-schedules/s1': one(schedule()), '/invoicing/invoices': page([invoice({ recurringScheduleId: 's1', invoiceType: 'RECURRING_INSTANCE' }), invoice({ id: 'x', invoiceNumber: 'INV-9', recurringScheduleId: null })]), '/crm/customers': customers })
    renderAt('/recurring/s1', '/recurring/:id', <RecurringDetailPage />)
    expect(await screen.findByText('Invoices created · 1')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'INV-0001' }).getAttribute('href')).toBe('/invoices/i1')
    expect(screen.queryByText('INV-9')).toBeNull()
  })
})

describe('RetainersListPage', () => {
  it('lists only retainers, with hours used and an overage flag', async () => {
    route({ '/invoicing/invoices': page([
      invoice({ id: 'r1', invoiceNumber: 'INV-R1', invoiceType: 'RETAINER', committedHours: 100, hoursConsumed: 40, total: 5000 }),
      invoice({ id: 'r2', invoiceNumber: 'INV-R2', invoiceType: 'RETAINER', committedHours: 50, hoursConsumed: 60, total: 3000 }),
      invoice({ id: 'n1', invoiceNumber: 'INV-STD' })]), '/crm/customers': customers })
    renderAt('/retainers', '/retainers', <RetainersListPage />)
    await screen.findByText('INV-R1')
    expect(screen.queryByText('INV-STD')).toBeNull()
    expect(screen.getByText('Over by 10h')).toBeTruthy()
    expect(screen.getAllByRole('progressbar').length).toBe(2)
    fireEvent.click(within(screen.getByRole('group', { name: 'Retainer view' })).getByRole('button', { name: /^In overage/ }))
    expect(screen.queryByText('INV-R1')).toBeNull()
    expect(screen.getByText('INV-R2')).toBeTruthy()
  })
})

describe('CreditNotesListPage', () => {
  it('lists credit notes and links back to the invoice', async () => {
    route({ '/credit-notes': page([{ id: 'n1', creditNoteNumber: 'CN-0001', invoiceId: 'i1', invoiceNumber: 'INV-0001', reason: 'Overcharged', description: null,
      subtotal: 100, vatTotal: 15, total: 115, currency: 'ZAR', issuedAt: day(-1), createdAt: day(-1) }]) })
    renderAt('/credit-notes', '/credit-notes', <CreditNotesListPage />)
    fireEvent.click(await screen.findByText('CN-0001'))
    expect(screen.getByTestId('where').textContent).toBe('/invoices/i1')
  })
})

describe('QuoteDetailPage', () => {
  const open = (q: any, extra: Record<string, any> = {}) => {
    route({ '/quotes/q1': one(q), '/crm/customers/c1': one({ name: 'Acme Mining' }), '/invoicing/invoices': page([]), ...extra })
    return renderAt('/quotes/q1', '/quotes/:id', <QuoteDetailPage />)
  }
  it('rejects a sent quote through the confirmation dialog', async () => {
    perms.add('INVOICE_CREATE')
    api.post.mockResolvedValue({ data: {} })
    open(quote({ status: 'SENT', sentAt: day(-2) }))
    fireEvent.click(await screen.findByRole('button', { name: 'Mark rejected' }))
    const d = screen.getByRole('dialog')
    fireEvent.click(within(d).getByRole('button', { name: 'Mark rejected' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/invoicing/quotes/q1/reject'))
  })
  it('converts an accepted quote and goes to the new invoice', async () => {
    perms.add('INVOICE_CREATE')
    api.post.mockResolvedValue({ data: { data: 'new-invoice-id' } })
    open(quote({ status: 'ACCEPTED', acceptedAt: day(-1) }))
    fireEvent.click(await screen.findByRole('button', { name: 'Convert to invoice' }))
    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/invoices/new-invoice-id'))
  })
  it('only lets people with send rights send, and not an empty quote', async () => {
    perms.add('INVOICE_SEND')
    open(quote({ lineItems: [] }))
    const send = await screen.findByRole('button', { name: 'Send quote' }) as HTMLButtonElement
    expect(send.disabled).toBe(true)
    expect(screen.getByText(/Add at least one line item/)).toBeTruthy()
  })
  it('links an invoiced quote to its invoice', async () => {
    open(quote({ status: 'INVOICED' }), { '/invoicing/invoices': page([invoice({ id: 'inv-9', quoteId: 'q1' })]) })
    await screen.findByRole('heading', { name: 'QT-0001' })
    fireEvent.click(await screen.findByRole('button', { name: 'View invoice' }))
    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/invoices/inv-9'))
  })
  it('says when the quote is not found', async () => {
    open(quote(), { '/quotes/q1': new Error('404') })
    expect(await screen.findByText('Quote not found')).toBeTruthy()
  })
})
