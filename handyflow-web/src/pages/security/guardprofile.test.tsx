import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import GuardProfilePage from './GuardProfilePage'

afterEach(() => cleanup())
const overview = (over: any = {}) => ({
  guard: { id: 'g1', firstName: 'Thabo', lastName: 'Mokoena', fullName: 'Thabo Mokoena', psiraNumber: '1234567', idNumber: '9001015009087', phone: '0821234567', photoUrl: null,
    grade: 'C', status: 'ACTIVE', statusNote: null, psiraExpiryDate: '2099-01-01', employeeCode: 'G-00428', emergencyContactName: 'Neo', emergencyContactPhone: '0830000000', cpVettingTier: null, notes: null, createdAt: '2026-01-01T00:00:00Z', bankName: 'FNB' },
  screeningGate: null,
  documents: [{ id: 'd1', category: 'ID_COPY', fileUrl: 'https://x/id.pdf', fileName: 'id.pdf', notes: null, createdAt: '2026-02-01T00:00:00Z' }],
  screening: [{ id: 's1', screeningType: 'CRIMINAL_RECORD_CHECK', reason: 'ONBOARDING', result: 'FAIL', conductedBy: 'ABC', conductedAt: '2026-02-01', nextDueAt: null, reportRef: 'CR-1', createdAt: '2026-02-01T00:00:00Z' }],
  shifts: [{ id: 'sh1', siteId: 's', siteName: 'ABC Centre', startAt: '2026-10-01T06:00:00Z', endAt: '2026-10-01T18:00:00Z', status: 'COMPLETED' }],
  incidents: [{ id: 'i1', siteName: 'ABC Centre', title: 'Gate forced', severity: 'HIGH', status: 'OPEN', reportedAt: '2026-10-02T10:00:00Z' }],
  counts: { shiftsLast90Days: 4, completedLast90Days: 3, incidentsLast180Days: 1, openIncidents: 1 },
  ...over,
})

function page(data: any = overview()) {
  api.get.mockImplementation(async () => ({ data: { data } }))
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={['/security/guards/g1']}><Routes>
    <Route path="/security/guards/:guardId" element={<GuardProfilePage />} /></Routes></MemoryRouter></QueryClientProvider>)
}

describe('Guard 360 page', () => {
  beforeEach(() => { vi.clearAllMocks(); perms.clear(); perms.add('SECURITY_MANAGE') })

  it('loads the overview for the guard in the URL and shows the header', async () => {
    page()
    expect(await screen.findByText('Guard ID: G-00428 · Grade C')).toBeTruthy()
    expect(api.get).toHaveBeenCalledWith('/api/v1/security/guards/g1/overview')
    expect(screen.getByText('75%')).toBeTruthy() // 3 of 4 completed
  })

  it('lists what needs attention, including the failed screening', async () => {
    page()
    await screen.findByText('Needs attention')
    expect(screen.getByText('Criminal record check: failed')).toBeTruthy()
  })

  it('shows the screening matrix with a row for each check', async () => {
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    expect(screen.getByText('Polygraph')).toBeTruthy()
    expect(screen.getAllByText('Not on file').length).toBe(5)
    expect(screen.getByText('CR-1')).toBeTruthy()
  })

  it('shows the open incident count on the tab and lists the incident', async () => {
    page()
    fireEvent.click(await screen.findByRole('tab', { name: /Incidents/ }))
    expect(screen.getByText('Gate forced')).toBeTruthy()
  })

  it('adds a document through the guard documents endpoint', async () => {
    api.post.mockResolvedValue({ data: {} })
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Documents' }))
    fireEvent.click(screen.getByRole('button', { name: /Add document/ }))
    fireEvent.change(screen.getByPlaceholderText('https://...'), { target: { value: ' https://x/psira.pdf ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save document' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/guards/g1/documents',
      { category: 'ID_COPY', fileUrl: 'https://x/psira.pdf', fileName: null, notes: null }))
  })

  it('requires a reason before removing a document', async () => {
    api.delete.mockResolvedValue({ data: {} })
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Documents' }))
    fireEvent.click(screen.getByRole('button', { name: 'Remove ID copy' }))
    const confirm = screen.getByRole('button', { name: 'Remove document' }) as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: 'Wrong guard' } })
    fireEvent.click(confirm)
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/v1/security/guards/g1/documents/d1', { data: { reason: 'Wrong guard' } }))
  })

  it('hides add and remove without manage permission', async () => {
    perms.clear(); perms.add('SECURITY_READ')
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Documents' }))
    expect(screen.queryByRole('button', { name: /Add document/ })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Remove ID copy' })).toBeNull()
  })

  it('says so plainly when the guard cannot be loaded', async () => {
    api.get.mockRejectedValue(new Error('404'))
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={['/security/guards/zzz']}><Routes>
      <Route path="/security/guards/:guardId" element={<GuardProfilePage />} /></Routes></MemoryRouter></QueryClientProvider>)
    expect(await screen.findByText(/could not be loaded/)).toBeTruthy()
  })
})
