// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientQuotesTab from './ClientQuotesTab'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let admin = true
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => admin }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'u1' } }) }))

const base = '/api/v1/compliance-services/clients/c1/quotes'
const summary = (over: object) => ({ id: 'q1', clientId: 'c1', clientName: 'Acme', quoteNumber: 'CQ-000001', title: 'Annual compliance', status: 'PENDING_APPROVAL', validUntil: '2099-01-01', total: 5060, requiredApprover: 'PRACTICE_OWNER', maxDiscountPct: 12, createdAt: '2027-03-01T08:00:00Z', ...over })
const quote = (over: object) => ({
  id: 'q1', clientId: 'c1', quoteNumber: 'CQ-000001', title: 'Annual compliance', status: 'PENDING_APPROVAL', priceBookVersion: 1, validUntil: '2099-01-01', notes: null, terms: null,
  requiredApprover: 'PRACTICE_OWNER', maxDiscountPct: 12, professional: 4400, discountTotal: 600, disbursements: 0, vat: 660, vatRatePct: 15, total: 5060,
  createdBy: 'u9', submittedBy: 'u9', approvedBy: null, approvalNote: null, approvalExceptionReason: null, sentAt: null, decisionNote: null, withdrawnReason: null,
  lines: [{ id: 'l1', serviceCode: 'CS-CIPC-AR', serviceName: 'CIPC annual return', serviceVersion: 1, pricingModel: 'FIXED', description: null, units: null, hours: null, complexityPct: null, urgent: false,
    discountPct: 12, beforeDiscount: 5000, discount: 600, professional: 4400, disbursements: 0, vat: 660, total: 5060,
    parts: [{ label: 'Fee', amount: 5000 }, { label: 'Discount 12%', amount: -600 }], extras: [], disbursementItems: [] }], ...over,
})
const book = [{ inForce: true, versionNumber: 1, lines: [
  { serviceCode: 'CS-CIPC-AR', serviceName: 'CIPC annual return', pricingModel: 'FIXED', hourlyGrade: null },
  { serviceCode: 'CS-OHS', serviceName: 'OHS audit', pricingModel: 'PER_SITE', hourlyGrade: null }] }]

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientQuotesTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientQuotesTab', () => {
  afterEach(cleanup)
  beforeEach(() => {
    admin = true
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/price-book')) return { data: { data: book } }
      if (url.endsWith('/q1')) return { data: { data: quote({}) } }
      return { data: { data: [summary({})] } }
    })
    post.mockResolvedValue({ data: { data: quote({ status: 'APPROVED' }) } })
  })

  it('lists quotes with status and total', async () => {
    renderIt()
    expect(await screen.findByText('CQ-000001')).toBeTruthy()
    expect(screen.getByText(/Waiting for approval · R 5 060.00/)).toBeTruthy()
  })

  it('shows the lines, the totals the server worked out and who must approve the discount', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CQ-000001'))
    expect(await screen.findByText(/CS-CIPC-AR · CIPC annual return/)).toBeTruthy()
    expect(screen.getByText('Discount 12%')).toBeTruthy()
    const totals = screen.getByLabelText('Quote totals')
    expect(totals.textContent).toMatch(/Professional fees.*R 4 400.00/)
    expect(totals.textContent).toMatch(/Includes discount of.*R 600.00/)
    expect(totals.textContent).toMatch(/VAT at 15%.*R 660.00/)
    expect(totals.textContent).toMatch(/Total.*R 5 060.00/)
    expect(screen.getByText(/The practice owner must approve \(proposed limits/)).toBeTruthy()
  })

  it('asks the approver for a reason only when they wrote or submitted the quote', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/price-book')) return { data: { data: book } }
      if (url.endsWith('/q1')) return { data: { data: quote({ createdBy: 'u1', submittedBy: 'u1' }) } }
      return { data: { data: [summary({})] } }
    })
    renderIt()
    fireEvent.click(await screen.findByText('CQ-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    const confirm = screen.getByRole('button', { name: 'Confirm: Approve' }) as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/at least 15 characters/), { target: { value: 'Sole partner, nobody else' } })
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${base}/q1/approve`, { note: null, exceptionReason: 'Sole partner, nobody else' }))
  })

  it('approves without a reason when someone else wrote it', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CQ-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    expect(screen.queryByPlaceholderText(/at least 15 characters/)).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm: Approve' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${base}/q1/approve`, { note: null, exceptionReason: null }))
  })

  it('sends a quote back only with a reason of enough words', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CQ-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Send back' }))
    const confirm = screen.getByRole('button', { name: 'Confirm: Send back' }) as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('What needs to change'), { target: { value: 'Discount too deep for this client' } })
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${base}/q1/reject`, { reason: 'Discount too deep for this client' }))
  })

  it('offers an editor only anyone without admin rights cannot approve', async () => {
    admin = false
    renderIt()
    fireEvent.click(await screen.findByText('CQ-000001'))
    await screen.findByText(/CS-CIPC-AR · CIPC annual return/)
    expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull()
    expect(screen.getByText('Waiting for an administrator to approve the discount.')).toBeTruthy()
  })

  it('edits a draft, asks only for what the service needs, and saves it for the server to price', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/price-book')) return { data: { data: book } }
      if (url.endsWith('/q1')) return { data: { data: quote({ status: 'DRAFT', lines: [], validUntil: new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10) }) } }
      return { data: { data: [summary({ status: 'DRAFT' })] } }
    })
    put.mockResolvedValue({ data: { data: quote({ status: 'DRAFT' }) } })
    renderIt()
    fireEvent.click(await screen.findByText('CQ-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    fireEvent.click(screen.getByRole('button', { name: 'Add a service' }))
    await screen.findByRole('option', { name: /CS-OHS/ })
    fireEvent.change(screen.getByLabelText('Service'), { target: { value: 'CS-OHS' } })
    const save = screen.getByRole('button', { name: 'Save draft' }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    expect(screen.getByText('Say how many units')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Number of sites'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('Discount percent'), { target: { value: '12' } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalledWith(`${base}/q1`, expect.objectContaining({
      lines: [expect.objectContaining({ serviceCode: 'CS-OHS', units: 3, discountPct: 12, hours: null })] })))
  })

  it('records a client acceptance with a note', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/price-book')) return { data: { data: book } }
      if (url.endsWith('/q1')) return { data: { data: quote({ status: 'SENT' }) } }
      return { data: { data: [summary({ status: 'SENT' })] } }
    })
    renderIt()
    fireEvent.click(await screen.findByText('CQ-000001'))
    fireEvent.click(await screen.findByRole('button', { name: 'Record acceptance' }))
    fireEvent.change(screen.getByLabelText('How and when the client accepted'), { target: { value: 'Accepted by email on 3 March' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm: Record acceptance' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${base}/q1/accept`, { note: 'Accepted by email on 3 March' }))
  })

  it('shows the server message when something is refused', async () => {
    post.mockRejectedValue({ response: { data: { message: 'No approved price book is in force' } } })
    renderIt()
    await screen.findByText('CQ-000001')
    fireEvent.click(screen.getByRole('button', { name: 'New quote' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/No approved price book/)
  })
})
