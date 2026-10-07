import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import IncidentDetailPage from './IncidentDetailPage'

afterEach(() => cleanup())
beforeEach(() => { vi.clearAllMocks(); perms.clear() })

const incident = (o: any = {}) => ({ id: 'i1', siteId: 's1', siteName: 'ABC Mall', shiftId: null, guardId: 'g1', guardName: 'Thabo Mokoena', title: 'Gate forced after hours',
  description: 'Rear gate found open at 02:10', severity: 'HIGH', status: 'OPEN', type: 'TRESPASS', latitude: null, longitude: null,
  acknowledgedAt: null, resolvedAt: null, reportedAt: '2026-10-02T21:10:00Z', updatedAt: '2026-10-02T21:10:00Z', ...o })
const detail = (o: any = {}, inc: any = {}) => ({ incident: incident(inc), assigneeName: null, assignedAt: null,
  allowedActions: ['ACKNOWLEDGE', 'RESOLVE', 'ASSIGN', 'NOTE', 'EVIDENCE', 'ESCALATE'],
  events: [{ id: 'e1', eventType: 'REPORTED', toStatus: 'OPEN', note: null, byName: 'Thabo', at: '2026-10-02T21:10:00Z' }], evidence: [], ...o })

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={['/security/incidents/i1']}><Routes>
    <Route path="/security/incidents/:id" element={<IncidentDetailPage />} /></Routes></MemoryRouter></QueryClientProvider>)
}

describe('IncidentDetailPage', () => {
  it('shows the incident, timeline and only the actions the server allows', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail() } })
    renderPage()
    expect(await screen.findByText('Rear gate found open at 02:10')).toBeTruthy()
    expect(screen.getByText('Incident reported')).toBeTruthy()
    for (const n of ['Acknowledge', 'Assign', 'Escalate', 'Add note', 'Resolve']) expect(screen.getByRole('button', { name: n })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Reopen' })).toBeNull()
    expect(screen.getByRole('button', { name: /Report \(PDF\)/ })).toBeTruthy()
  })
  it('shows no action buttons without manage permission, but still the report', async () => {
    api.get.mockResolvedValue({ data: { data: detail() } })
    renderPage()
    await screen.findByText('Rear gate found open at 02:10')
    expect(screen.queryByRole('button', { name: 'Acknowledge' })).toBeNull()
    expect(screen.getByRole('button', { name: /Report \(PDF\)/ })).toBeTruthy()
  })
  it('acknowledges in one click', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail() } })
    api.post.mockResolvedValue({ data: {} })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Acknowledge' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/incidents/i1/acknowledge'))
  })
  it('assigns, refusing a blank name', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail() } })
    api.post.mockResolvedValue({ data: {} })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Assign' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/who/i)
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('Who is dealing with this'), { target: { value: 'Thandi Zulu' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/incidents/i1/assign', { assigneeName: 'Thandi Zulu' }))
  })
  it('escalates one level by default and only offers higher severities', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail() } })
    api.post.mockResolvedValue({ data: {} })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Escalate' }))
    const sel = screen.getByLabelText('New severity') as HTMLSelectElement
    expect(Array.from(sel.options).map(o => o.textContent)).toEqual(['One level up (Critical)', 'Critical'])
    fireEvent.change(screen.getByLabelText('Why is it being escalated'), { target: { value: 'Armed suspects' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/incidents/i1/escalate', { severity: null, reason: 'Armed suspects' }))
  })
  it('resolves with an optional note', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail({ allowedActions: ['RESOLVE', 'NOTE'] }, { status: 'ACKNOWLEDGED' }) } })
    api.post.mockResolvedValue({ data: {} })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Resolve' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/incidents/i1/resolve', { note: null }))
  })
  it('offers only reopen and note on a resolved incident, and shows time to resolve', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: detail({ allowedActions: ['REOPEN', 'NOTE'] }, { status: 'RESOLVED', resolvedAt: '2026-10-02T23:15:00Z' }) } })
    renderPage()
    expect(await screen.findByRole('button', { name: 'Reopen' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Resolve' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Escalate' })).toBeNull()
    expect(screen.getByText('2 h 5 min')).toBeTruthy()
  })
})
