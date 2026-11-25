// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientAgreementsTab from './ClientAgreementsTab'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let manage = true
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => manage, usePermission: () => manage }))

const base = '/api/v1/compliance-services/clients/c1/agreements'
const summary = (over: object = {}) => ({
  id: 'a1', clientId: 'c1', quoteNumber: 'CQ-000001', agreementNumber: 'CA-000001', title: 'Annual compliance', status: 'DRAFT', startDate: '2027-03-01', endDate: '2028-02-29',
  billingFrequency: 'MONTHLY', billingAmount: 1500, nextBillingDate: '2027-03-01', attention: null, ...over,
})
const agreement = (over: object = {}) => ({
  id: 'a1', clientId: 'c1', quoteNumber: 'CQ-000001', agreementNumber: 'CA-000001', title: 'Annual compliance', status: 'DRAFT', startDate: '2027-03-01', endDate: '2028-02-29',
  billingFrequency: 'MONTHLY', billingAmount: 1500, firstBillingDate: '2027-03-01', autoRenew: false, noticeDays: 30, notes: null, terms: null,
  quotedProfessional: 17000, quotedTotal: 19550, contractValue: 18000, comparisonNote: 'Billed over the term this comes to 1000.00 more than the professional fees on the quote (before VAT). Check that this is what was agreed',
  attention: null, nextBillingDate: '2027-03-01', scheduleTruncated: false,
  schedule: [{ date: '2027-03-01', amount: 1500 }, { date: '2027-04-01', amount: 1500 }],
  lines: [{ id: 'l1', serviceCode: 'CS-CIPC-AR', serviceName: 'CIPC annual return', pricingModel: 'FIXED', description: null, professional: 4500 }],
  actions: ['activate', 'cancel'], canEdit: true, activationNote: null, suspendedReason: null, endReason: null, ...over,
})
const option = { id: 'q1', quoteNumber: 'CQ-000001', title: 'Annual compliance', professional: 17000, total: 19550 }

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientAgreementsTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientAgreementsTab', () => {
  afterEach(cleanup)
  beforeEach(() => {
    manage = true
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/billing-events')) return { data: { data: [] } }
      if (url.endsWith('/available-quotes')) return { data: { data: [option] } }
      if (url.endsWith('/a1')) return { data: { data: agreement() } }
      return { data: { data: [summary()] } }
    })
    post.mockResolvedValue({ data: { data: agreement({ status: 'ACTIVE', actions: ['suspend', 'end'], canEdit: false }) } })
    put.mockResolvedValue({ data: { data: agreement() } })
  })

  it('lists agreements with status, amount and the next billing date', async () => {
    renderIt()
    expect(await screen.findByText('CA-000001')).toBeTruthy()
    expect(screen.getByText(/Draft · .*1.500\.00 every month · next 2027-03-01/)).toBeTruthy()
  })

  it('starts an agreement from an accepted quote with terms taken from the quote', async () => {
    post.mockResolvedValueOnce({ data: { data: agreement() } })
    renderIt()
    await screen.findByText('CA-000001')
    const select = await screen.findByLabelText(/Start an agreement from an accepted quote/)
    await screen.findByRole('option', { name: /CQ-000001/ })
    fireEvent.change(select, { target: { value: 'q1' } })
    fireEvent.click(screen.getByText('Start agreement'))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe(base)
    expect(body.quoteId).toBe('q1')
    expect(body.terms).toMatchObject({ billingFrequency: 'ONCE', billingAmount: 17000, title: 'Annual compliance' })
  })

  it('shows the scope, the billing dates and the note on how the contract differs from the quote', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    expect(await screen.findByText(/CS-CIPC-AR · CIPC annual return/)).toBeTruthy()
    expect(screen.getByText(/2027-04-01 ·/)).toBeTruthy()
    expect(screen.getByText(/1000\.00 more than the professional fees/)).toBeTruthy()
    expect(screen.getByText(/Over the term: .*18.000\.00 before VAT/)).toBeTruthy()
  })

  it('will not activate without a note on how the client confirmed', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Activate' }))
    const confirm = await screen.findByRole('button', { name: /Confirm: activate/ })
    expect((confirm as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('How the client confirmed'), { target: { value: 'Signed copy returned by the director' } })
    expect((confirm as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${base}/a1/activate`, { note: 'Signed copy returned by the director' }))
  })

  it('edits the terms of a draft, blocks a bad amount and saves a good one', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Edit terms' }))
    fireEvent.change(await screen.findByLabelText(/Amount each time/), { target: { value: '0' } })
    expect(screen.getByText(/more than zero/)).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Save draft' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Amount each time/), { target: { value: '1600' } })
    expect(screen.getByText(/12 billing dates · .*19.200\.00 over the term before VAT/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Save draft' }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toBe(`${base}/a1`)
    expect(put.mock.calls[0][1]).toMatchObject({ billingAmount: 1600, billingFrequency: 'MONTHLY' })
  })

  it('offers only what the server allows after activation, and ending needs a date and a reason', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/billing-events')) return { data: { data: [] } }
      if (url.endsWith('/available-quotes')) return { data: { data: [] } }
      if (url.endsWith('/a1')) return { data: { data: agreement({ status: 'ACTIVE', actions: ['suspend', 'end'], canEdit: false, activationNote: 'Signed copy returned' }) } }
      return { data: { data: [summary({ status: 'ACTIVE' })] } }
    })
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    expect(await screen.findByRole('button', { name: 'Suspend' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Activate' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Edit terms' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'End' }))
    fireEvent.change(await screen.findByLabelText('Why it is ending'), { target: { value: 'The client left the practice' } })
    expect(screen.getByText(/Say the date it ends/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Ends on'), { target: { value: '2027-06-30' } })
    fireEvent.click(screen.getByRole('button', { name: /Confirm: end/ }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${base}/a1/end`, { endDate: '2027-06-30', reason: 'The client left the practice' }))
  })

  const bill = (over: object = {}) => ({
    id: 'e1', agreementId: 'a1', agreementNumber: 'CA-000001', clientId: 'c1', clientName: 'Acme', dueDate: '2027-03-01', amount: 1500, description: 'x', status: 'SCHEDULED',
    stage: 'DUE', daysLate: 0, invoiceId: null, invoiceNumber: null, invoice: null, skipReason: null, releaseNote: null, actions: ['raise', 'skip'], blocked: null, ...over,
  })
  const activeWithBills = (bills: object[]) => get.mockImplementation(async (url: string) => {
    if (url.endsWith('/billing-events')) return { data: { data: bills } }
    if (url.endsWith('/available-quotes')) return { data: { data: [] } }
    if (url.endsWith('/a1')) return { data: { data: agreement({ status: 'ACTIVE', actions: ['suspend', 'end'], canEdit: false }) } }
    return { data: { data: [summary({ status: 'ACTIVE' })] } }
  })

  it('makes the next bills for an active agreement and says how many were made', async () => {
    activeWithBills([])
    post.mockResolvedValueOnce({ data: { data: { created: 3, events: [] } } })
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Make the next bills' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${base}/a1/billing-events/generate`, {}))
    expect(await screen.findByText('3 bills made')).toBeTruthy()
  })

  it('raises a draft invoice from a bill that is ready', async () => {
    activeWithBills([bill()])
    post.mockResolvedValueOnce({ data: { data: bill({ status: 'INVOICED', invoiceNumber: 'INV-00042' }) } })
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Raise draft invoice' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/billing-events/e1/raise', {}))
  })

  it('shows where the invoice stands and offers release, which needs a reason', async () => {
    activeWithBills([bill({ status: 'INVOICED', stage: 'INVOICED', invoiceNumber: 'INV-00042', invoice: { status: 'CANCELLED', total: 1725, paid: null, credited: null }, actions: ['release'] })])
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    expect(await screen.findByText(/Invoice INV-00042 · cancelled/)).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Release invoice' }))
    const confirm = await screen.findByRole('button', { name: /Confirm: release invoice/ })
    expect((confirm as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Why the invoice is let go'), { target: { value: 'Wrong amount on the draft' } })
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/billing-events/e1/release', { reason: 'Wrong amount on the draft' }))
  })

  it('explains why a bill cannot be raised yet and hides raise', async () => {
    activeWithBills([bill({ stage: 'UPCOMING', actions: ['skip'], blocked: 'This bill is not due yet. It can be raised from 2027-04-24' })])
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    expect(await screen.findByText(/not due yet/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Raise draft invoice' })).toBeNull()
  })

  it('shows a warning line when the server raises one', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/available-quotes')) return { data: { data: [] } }
      return { data: { data: [summary({ status: 'ACTIVE', attention: 'The term ended on 2027-01-31. End this agreement or start a new one' })] } }
    })
    renderIt()
    expect(await screen.findByText(/The term ended on 2027-01-31/)).toBeTruthy()
  })

  it('is read-only without permission to manage', async () => {
    manage = false
    renderIt()
    fireEvent.click(await screen.findByText('CA-000001'))
    await screen.findByText(/CS-CIPC-AR/)
    expect(screen.queryByText('Start agreement')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Activate' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Edit terms' })).toBeNull()
  })
})
