// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ObligationCatalogue from './ObligationCatalogue'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'u1' } }) }))

const summary = [{ id: 'o1', code: 'CIPC-AR', name: 'Annual return', inForceVersion: 1, latestVersion: 2, latestStatus: 'IN_REVIEW', riskLevel: 'HIGH', frequency: 'ANNUAL', regulator: 'CIPC' }]
const v = (over: object) => ({
  id: 'v', versionNumber: 1, reviewStatus: 'APPROVED', name: 'Annual return', description: null, legalSource: 'Companies Act s33', regulator: 'CIPC', jurisdiction: 'ZA',
  frequency: 'ANNUAL', dueRuleType: 'ANNUAL_FIXED', dueMonth: 6, dueDay: 30, dueOffsetDays: null, dueOffsetMonths: null, validityMonths: null, riskLevel: 'HIGH',
  responsibleRole: 'CLIENT', guidance: null, changeSummary: null, authoredBy: 'u9', submittedBy: 'u9', reviewedBy: 'u8', reviewNote: null, reviewExceptionReason: null,
  effectiveFrom: '2025-01-01', effectiveTo: '2026-06-30', inForce: false, conditions: [], evidence: [], ...over,
})
const detail = {
  id: 'o1', code: 'CIPC-AR',
  versions: [
    v({ id: 'v1', conditions: [{ field: 'ENTITY_TYPE', operator: 'IN', value: 'PTY_LTD' }], evidence: [{ documentType: 'CoR14.3', mandatory: true }] }),
    v({ id: 'v2', versionNumber: 2, reviewStatus: 'IN_REVIEW', effectiveFrom: null, effectiveTo: null, changeSummary: 'Due date moved', authoredBy: 'u1', submittedBy: 'u1' }),
  ],
}

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ObligationCatalogue /></QueryClientProvider>)
}

describe('ObligationCatalogue', () => {
  afterEach(cleanup)
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockImplementation(async (url: string) => ({ data: { data: url.endsWith('/obligations') ? summary : detail } }))
    post.mockResolvedValue({ data: { data: detail } })
  })

  it('lists obligations with what is in force and what is waiting', async () => {
    renderIt()
    expect(await screen.findByText('CIPC-AR')).toBeTruthy()
    expect(screen.getByText(/Version 1 in force · version 2 in review/)).toBeTruthy()
  })

  it('shows the versions with their conditions and evidence', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CIPC-AR'))
    expect(await screen.findByText('Version 2')).toBeTruthy()
    expect(screen.getByText(/Applies when: Entity type is Private company/)).toBeTruthy()
    expect(screen.getByText(/Evidence: CoR14.3/)).toBeTruthy()
    expect(screen.getByText('In force 2025-01-01 to 2026-06-30')).toBeTruthy()
  })

  it('asks for a reason when the approver wrote the version', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CIPC-AR'))
    await screen.findByText('Version 2')
    fireEvent.change(document.querySelector('input[type="date"]') as HTMLInputElement, { target: { value: '2026-07-01' } })
    expect((screen.getByText('Approve') as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText('Reason, at least 15 characters'), { target: { value: 'Sole practitioner, no one else' } })
    await waitFor(() => expect((screen.getByText('Approve') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByText('Approve'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/obligations/o1/versions/v2/approve',
      { effectiveFrom: '2026-07-01', exceptionReason: 'Sole practitioner, no one else', note: null }))
  })

  it('sends a version back only with a reason', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('CIPC-AR'))
    await screen.findByText('Version 2')
    const back = screen.getByText('Send back') as HTMLButtonElement
    expect(back.disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText('What needs to change'), { target: { value: 'Cite the section' } })
    await waitFor(() => expect((screen.getByText('Send back') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByText('Send back'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/obligations/o1/versions/v2/reject', { reason: 'Cite the section' }))
  })

  it('adds an obligation by code', async () => {
    renderIt()
    await screen.findByText('CIPC-AR')
    fireEvent.change(screen.getByPlaceholderText(/New obligation code/), { target: { value: 'POPIA-OFFICER' } })
    fireEvent.click(screen.getByText('Add'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/obligations', { code: 'POPIA-OFFICER', version: null }))
  })
})
