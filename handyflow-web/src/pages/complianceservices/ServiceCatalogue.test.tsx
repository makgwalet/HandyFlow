// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ServiceCatalogue from './ServiceCatalogue'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'u1' } }) }))

const summary = [{ id: 's1', code: 'CS-CIPC-AR', name: 'CIPC annual return', category: 'CIPC', inForceVersion: 1, latestVersion: 2, latestStatus: 'IN_REVIEW', pricingModel: 'FIXED', estimatedHours: 3.5 }]
const v = (over: object) => ({
  id: 'v', versionNumber: 1, reviewStatus: 'APPROVED', name: 'CIPC annual return', category: 'CIPC', description: null, applicabilityNote: null, exclusions: 'CIPC fees',
  estimatedHours: 3.5, slaDays: 14, responsibleRole: 'CONSULTANT', requiresReview: true, pricingModel: 'FIXED', billingTrigger: 'ON_DELIVERY', changeSummary: null,
  authoredBy: 'u9', submittedBy: 'u9', reviewedBy: 'u8', reviewNote: null, reviewExceptionReason: null, effectiveFrom: '2026-01-01', effectiveTo: null, inForce: true,
  items: [{ kind: 'DELIVERABLE', text: 'Annual return filed', mandatory: true }, { kind: 'DOCUMENT', text: 'Financial statements', mandatory: false }], ...over,
})
const detail = {
  id: 's1', code: 'CS-CIPC-AR',
  versions: [
    v({ id: 'v1', effectiveTo: '2026-12-31', inForce: false }),
    v({ id: 'v2', versionNumber: 2, reviewStatus: 'IN_REVIEW', effectiveFrom: null, effectiveTo: null, inForce: false, changeSummary: 'Hours revised', authoredBy: 'u1', submittedBy: 'u1' }),
  ],
}

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ServiceCatalogue /></QueryClientProvider>)
}

describe('ServiceCatalogue', () => {
  afterEach(cleanup)
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockImplementation(async (url: string) => ({ data: { data: url.endsWith('/service-catalogue') ? summary : detail } }))
    post.mockResolvedValue({ data: { data: detail } })
  })

  it('lists services with what is on sale and what is waiting', async () => {
    renderIt()
    expect(await screen.findByText('CS-CIPC-AR')).toBeTruthy()
    expect(screen.getByText(/Version 1 on sale · version 2 in review/)).toBeTruthy()
  })

  it('shows a version with its pricing model, hours, items and exclusions but no price', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CS-CIPC-AR'))
    expect(await screen.findByText('Version 2')).toBeTruthy()
    expect(screen.getAllByText(/Fixed fee · billed when it is delivered · 3.5 hours · 14 days turnaround/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Deliverables: Annual return filed/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Documents needed from the client: Financial statements \(optional\)/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/Not included: CIPC fees/).length).toBeGreaterThan(0)
    expect(screen.queryByText(/R\s?\d/)).toBeNull()
  })

  it('asks for a reason when the approver wrote the version and sends it with the date', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CS-CIPC-AR'))
    await screen.findByText('Version 2')
    const approve = screen.getByRole('button', { name: 'Approve' })
    expect((approve as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('On sale from'), { target: { value: '2027-01-01' } })
    expect((approve as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/at least 15 characters/), { target: { value: 'Sole practitioner, nobody else' } })
    expect((approve as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(approve)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/service-catalogue/s1/versions/v2/approve',
      { effectiveFrom: '2027-01-01', exceptionReason: 'Sole practitioner, nobody else', note: null }))
  })

  it('adds a service by code', async () => {
    renderIt()
    fireEvent.change(await screen.findByLabelText('New service code'), { target: { value: 'CS-POPIA-GAP' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/service-catalogue', { code: 'CS-POPIA-GAP', version: null }))
  })

  it('shows the server message when something is refused', async () => {
    post.mockRejectedValue({ response: { data: { message: 'A service with code CS-POPIA-GAP already exists' } } })
    renderIt()
    fireEvent.change(await screen.findByLabelText('New service code'), { target: { value: 'CS-POPIA-GAP' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/already exists/)
  })
})
