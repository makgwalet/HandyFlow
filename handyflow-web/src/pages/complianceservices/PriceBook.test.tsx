// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import PriceBook from './PriceBook'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let admin = true
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => admin }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'u1' } }) }))

const services = [{ id: 's1', code: 'CS-CIPC-AR', name: 'CIPC annual return' }, { id: 's2', code: 'CS-OHS', name: 'OHS audit' }]
const line = (over: object) => ({ serviceCode: 'CS-CIPC-AR', serviceName: 'CIPC annual return', pricingModel: 'FIXED', baseFee: 2000, unitRate: null, hourlyGrade: null, minFee: null, urgentSurchargePct: 25, taxTreatment: 'STANDARD', notes: null, gaps: [], ...over })
const version = (over: object) => ({
  id: 'p1', versionNumber: 1, reviewStatus: 'APPROVED', currency: 'ZAR', label: '2027 prices', changeSummary: null, authoredBy: 'u9', submittedBy: 'u9', reviewedBy: 'u8',
  reviewNote: null, reviewExceptionReason: null, effectiveFrom: '2027-01-01', effectiveTo: null, inForce: true,
  lines: [line({}), line({ serviceCode: 'CS-OHS', serviceName: 'OHS audit', pricingModel: 'PER_SITE', baseFee: 3000, unitRate: 800, urgentSurchargePct: null })],
  rates: [{ grade: 'SENIOR', hourlyRate: 900, costRate: 400 }], ...over,
})
const inReview = version({ id: 'p2', versionNumber: 2, reviewStatus: 'IN_REVIEW', effectiveFrom: null, inForce: false, changeSummary: 'Retainers up', authoredBy: 'u1', submittedBy: 'u1' })

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><PriceBook /></QueryClientProvider>)
}

describe('PriceBook', () => {
  afterEach(cleanup)
  beforeEach(() => {
    admin = true
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockImplementation(async (url: string) => ({ data: { data: url.endsWith('/price-book') ? [version({ effectiveTo: '2027-12-31' }), inReview] : services } }))
    post.mockResolvedValue({ data: { data: [version({})] } })
  })

  it('shows each service with its price, the hourly rates, and what is in force', async () => {
    renderIt()
    expect(await screen.findByText(/Price book version 1 · 2027 prices/)).toBeTruthy()
    expect(screen.getByText('Approved · in force today')).toBeTruthy()
    expect(screen.getAllByText(/R 2 000.00 · urgent \+25%/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/R 3 000.00 · R 800.00 per site/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/senior R 900.00 \(costs R 400.00\)/).length).toBeGreaterThan(0)
  })

  it('asks for a reason when the approver wrote the version and sends it with the date', async () => {
    renderIt()
    await screen.findByText(/Price book version 2/)
    const approve = screen.getByRole('button', { name: 'Approve' }) as HTMLButtonElement
    expect(approve.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Prices apply from'), { target: { value: '2028-01-01' } })
    expect(approve.disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/at least 15 characters/), { target: { value: 'Sole practitioner, nobody else' } })
    expect(approve.disabled).toBe(false)
    fireEvent.click(approve)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/price-book/versions/p2/approve',
      { effectiveFrom: '2028-01-01', exceptionReason: 'Sole practitioner, nobody else', note: null }))
  })

  it('works out a per-site price with a disbursement and shows the parts, VAT and total', async () => {
    post.mockResolvedValue({ data: { data: {
      serviceCode: 'CS-OHS', priceBookVersion: 1, currency: 'ZAR', parts: [{ label: 'Base fee', amount: 3000 }, { label: '3 sites at R 800.00', amount: 2400 }],
      professionalBeforeDiscount: 5400, discount: 0, professional: 5400, disbursements: 600, vat: 900, vatRatePct: 15, total: 6900, discountApprover: 'CONSULTANT' } } })
    renderIt()
    await screen.findByText(/Price book version 1/)
    fireEvent.change(screen.getByLabelText('Service to price'), { target: { value: 'CS-OHS' } })
    const run = screen.getByRole('button', { name: 'Work out the price' }) as HTMLButtonElement
    expect(run.disabled).toBe(true)
    expect(screen.getByText('Say how many units')).toBeTruthy()
    fireEvent.change(screen.getByLabelText('Number of sites'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add a disbursement' }))
    fireEvent.change(screen.getByLabelText('Disbursement description'), { target: { value: 'Travel' } })
    fireEvent.change(screen.getByLabelText('Disbursement amount'), { target: { value: '600' } })
    fireEvent.click(screen.getByLabelText('Carries VAT'))
    expect(run.disabled).toBe(false)
    fireEvent.click(run)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/pricing/calculate', expect.objectContaining({
      serviceCode: 'CS-OHS', units: 3, hours: null, urgent: false, discountPct: null, disbursements: [{ description: 'Travel', amount: 600, vatable: true }] })))
    const result = await screen.findByLabelText('Price result')
    expect(result.textContent).toMatch(/Professional fees.*R 5 400.00/)
    expect(result.textContent).toMatch(/Disbursements at cost.*R 600.00/)
    expect(result.textContent).toMatch(/VAT at 15%.*R 900.00/)
    expect(result.textContent).toMatch(/Total.*R 6 900.00/)
  })

  it('says who may agree a discount', async () => {
    post.mockResolvedValue({ data: { data: {
      serviceCode: 'CS-CIPC-AR', priceBookVersion: 1, currency: 'ZAR', parts: [{ label: 'Base fee', amount: 2000 }], professionalBeforeDiscount: 2000, discount: 240,
      professional: 1760, disbursements: 0, vat: 264, vatRatePct: 15, total: 2024, discountApprover: 'PRACTICE_OWNER' } } })
    renderIt()
    await screen.findByText(/Price book version 1/)
    fireEvent.change(screen.getByLabelText('Service to price'), { target: { value: 'CS-CIPC-AR' } })
    fireEvent.change(screen.getByLabelText('Discount %'), { target: { value: '12' } })
    fireEvent.click(screen.getByRole('button', { name: 'Work out the price' }))
    expect((await screen.findByLabelText('Price result')).textContent).toMatch(/Needs the practice owner \(proposed limits/)
  })

  it('shows the margin check to administrators only', async () => {
    renderIt()
    await screen.findByText(/Price book version 1/)
    expect(screen.getByText('Margin check (administrators only)')).toBeTruthy()
    cleanup()
    admin = false
    renderIt()
    await screen.findByText(/Price book version 1/)
    expect(screen.queryByText('Margin check (administrators only)')).toBeNull()
  })

  it('shows the server message when something is refused', async () => {
    post.mockRejectedValue({ response: { data: { message: 'No approved price book is in force on 2026-06-01' } } })
    renderIt()
    await screen.findByText(/Price book version 1/)
    fireEvent.change(screen.getByLabelText('Service to price'), { target: { value: 'CS-CIPC-AR' } })
    fireEvent.click(screen.getByRole('button', { name: 'Work out the price' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/No approved price book/)
  })
})
