import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import ComplaintDetailPage from './ComplaintDetailPage'
import ComplaintsTab from './ComplaintsTab'

afterEach(() => cleanup())
beforeEach(() => { vi.clearAllMocks(); perms.clear() })

const summary = (o: any = {}) => ({ id: 'c1', complaintNumber: 'CMP-0001', guardId: 'g1', guardName: 'Thabo Mokoena', siteId: null, siteName: 'Sandton Mall',
  occurredOn: '2026-10-01', category: 'LATENESS', severity: 'MEDIUM', status: 'RECEIVED', finding: null, action: null, open: true, urgent: false, createdAt: '2026-10-02T08:00:00Z', ...o })
const detail = (o: any = {}, s: any = {}) => ({ summary: summary(s), description: 'Arrived 40 minutes late', complainantType: 'CLIENT', complainantName: 'Mrs Dlamini', complainantContact: null,
  witnesses: null, investigatorName: null, findingNote: null, findingByName: null, findingAt: null, actionNote: null, actionByName: null, actionAt: null,
  resolutionNote: null, closedByName: null, closedAt: null, withdrawnReason: null, createdByName: 'Sam', editable: true, allowedSteps: ['START', 'WITHDRAW'],
  events: [{ id: 'e1', eventType: 'LOGGED', toStatus: 'RECEIVED', note: null, byName: 'Sam', at: '2026-10-02T08:00:00Z' }], evidence: [], ...o })

function renderDetail() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={['/security/complaints/c1']}><Routes>
    <Route path="/security/complaints/:id" element={<ComplaintDetailPage />} /></Routes></MemoryRouter></QueryClientProvider>)
}

describe('ComplaintDetailPage', () => {
  it('shows the complaint, its timeline and only the steps the server allows', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail() } })
    renderDetail()
    expect(await screen.findByText('Arrived 40 minutes late')).toBeTruthy()
    expect(screen.getByText('Complaint logged')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Start investigation' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Withdraw' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Record finding' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Close complaint' })).toBeNull()
  })

  it('shows no step buttons without manage permission', async () => {
    api.get.mockResolvedValue({ data: { data: detail() } })
    renderDetail()
    await screen.findByText('Arrived 40 minutes late')
    expect(screen.queryByRole('button', { name: 'Start investigation' })).toBeNull()
  })

  it('starts the investigation', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail() } })
    api.post.mockResolvedValue({ data: {} })
    renderDetail()
    fireEvent.click(await screen.findByRole('button', { name: 'Start investigation' }))
    fireEvent.change(screen.getByLabelText(/Investigator/), { target: { value: 'Thandi' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/complaints/c1/start', { investigator: 'Thandi' }))
  })

  it('blocks a finding without a note and sends it once complete', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail({ allowedSteps: ['FINDING', 'WITHDRAW'] }, { status: 'UNDER_INVESTIGATION' }) } })
    api.post.mockResolvedValue({ data: {} })
    renderDetail()
    fireEvent.click(await screen.findByRole('button', { name: 'Record finding' }))
    fireEvent.change(screen.getByLabelText('Finding'), { target: { value: 'SUBSTANTIATED' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/found/i)
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('What the investigation found'), { target: { value: 'Gate log confirms' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/complaints/c1/finding', { finding: 'SUBSTANTIATED', note: 'Gate log confirms' }))
  })

  it('offers only "No action" after an unsubstantiated finding', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail({ allowedSteps: ['ACTION', 'CLOSE'], editable: false }, { status: 'FINDING_MADE', finding: 'UNSUBSTANTIATED' }) } })
    renderDetail()
    expect(await screen.findByRole('button', { name: 'Close complaint' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Record action' }))
    const select = screen.getByLabelText('Action taken') as HTMLSelectElement
    expect(Array.from(select.options).map(o => o.textContent)).toEqual(['Choose', 'No action'])
    expect(screen.queryByRole('button', { name: /Edit details/ })).toBeNull()
  })

  it('has no buttons on a closed complaint', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail({ allowedSteps: [], editable: false, resolutionNote: 'Guard warned' }, { status: 'CLOSED', open: false, finding: 'SUBSTANTIATED', action: 'VERBAL_WARNING' }) } })
    renderDetail()
    expect(await screen.findByText(/Guard warned/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Withdraw|Close complaint|Record/ })).toBeNull()
  })
})

describe('ComplaintsTab', () => {
  function renderList(guardId?: string) {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return render(<QueryClientProvider client={qc}><MemoryRouter><ComplaintsTab guardId={guardId} /></MemoryRouter></QueryClientProvider>)
  }
  it('lists open complaints by default and flags urgent ones', async () => {
    api.get.mockResolvedValue({ data: { data: { content: [summary(), summary({ id: 'c2', complaintNumber: 'CMP-0002', category: 'THEFT', urgent: true })] } } })
    renderList()
    expect(await screen.findByText('CMP-0001')).toBeTruthy()
    expect(screen.getByText('urgent')).toBeTruthy()
    expect(api.get.mock.calls[0][0]).toContain('status=OPEN')
  })
  it('limits the list to one guard and hides the guard column', async () => {
    api.get.mockResolvedValue({ data: { data: { content: [summary()] } } })
    renderList('g1')
    await screen.findByText('CMP-0001')
    expect(api.get.mock.calls[0][0]).toContain('guardId=g1')
    expect(screen.queryByText('Guard', { selector: 'th' })).toBeNull()
  })
  it('shows the log button only with manage permission', async () => {
    api.get.mockResolvedValue({ data: { data: { content: [] } } })
    renderList()
    await screen.findByText(/No complaints match/)
    expect(screen.queryByRole('button', { name: /Log complaint/ })).toBeNull()
    cleanup(); perms.add('SECURITY_MANAGE'); renderList()
    expect(await screen.findByRole('button', { name: /Log complaint/ })).toBeTruthy()
  })
})
