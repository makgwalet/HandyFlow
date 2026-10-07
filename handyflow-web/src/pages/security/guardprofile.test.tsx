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
const item = (key: string, label: string, state: string, o: any = {}) => ({ key, label, required: true, state, detail: 'x', validUntil: null, evidenceCount: 0, met: state === 'MET', screeningId: null, competencyId: null, ...o })
const overview = (over: any = {}) => ({
  guard: { id: 'g1', firstName: 'Thabo', lastName: 'Mokoena', fullName: 'Thabo Mokoena', psiraNumber: '1234567', idNumber: '9001015009087', phone: '0821234567', photoUrl: null,
    grade: 'C', status: 'ACTIVE', statusNote: null, psiraExpiryDate: '2099-01-01', employeeCode: 'G-00428', emergencyContactName: 'Neo', emergencyContactPhone: '0830000000', cpVettingTier: null, notes: null, createdAt: '2026-01-01T00:00:00Z', bankName: 'FNB' },
  screeningGate: null,
  documents: [{ id: 'd1', category: 'ID_COPY', fileUrl: 'https://x/id.pdf', fileName: 'id.pdf', notes: null, createdAt: '2026-02-01T00:00:00Z' }],
  screening: [
    { id: 's1', screeningType: 'CRIMINAL_RECORD_CHECK', reason: 'ONBOARDING', result: 'PASS', conductedBy: 'ABC', conductedAt: '2026-02-01', nextDueAt: '2027-02-01', reportRef: 'CR-1', createdAt: '2026-02-01T00:00:00Z',
      provider: 'ABC Screening', requestedAt: '2026-01-30', decision: null, decisionNote: null, decidedByName: null, decidedAt: null,
      evidence: [{ id: 'e1', fileName: 'clearance.pdf', label: 'Certificate', sizeBytes: 2048, uploadedByName: 'Sam', createdAt: '2026-02-02T00:00:00Z' }] },
    { id: 's2', screeningType: 'DRUG_TEST', reason: 'ONBOARDING', result: 'PENDING', conductedBy: null, conductedAt: null, nextDueAt: null, reportRef: null, createdAt: '2026-10-01T00:00:00Z',
      provider: null, requestedAt: '2026-10-01', decision: null, decisionNote: null, decidedByName: null, decidedAt: null, evidence: [] }],
  readiness: { percent: 60, ready: false, reasons: ['Drug test: result still pending', 'Reference check: not on file'], items: [
    item('PSIRA', 'PSiRA registration', 'MET'), item('ID_COPY', 'ID copy on file', 'MET'),
    item('CRIMINAL_RECORD_CHECK', 'Criminal record check', 'MET', { screeningId: 's1', evidenceCount: 1, validUntil: '2027-02-01' }),
    item('REFERENCE_CHECK', 'Reference check', 'MISSING'),
    item('DRUG_TEST', 'Drug test', 'PENDING', { screeningId: 's2' }),
    item('POLYGRAPH', 'Polygraph', 'MISSING', { required: false })] },
  competencies: [
    { id: 'c1', guardId: 'g1', competencyType: 'FIRST_AID', label: 'First aid', title: null, issuedBy: 'St John', issueDate: '2026-01-10', expiryDate: '2027-01-10', certificateRef: 'FA-9', required: true, notes: null,
      state: 'UNVERIFIED', detail: 'awaiting verification', verifiedByName: null, verifiedAt: null, verificationNote: null,
      evidence: [{ id: 'ce1', fileName: 'firstaid.pdf', label: 'Certificate', sizeBytes: 4096, uploadedByName: 'Sam', createdAt: '2026-01-11T00:00:00Z' }], createdAt: '2026-01-11T00:00:00Z' },
    { id: 'c2', guardId: 'g1', competencyType: 'DRIVER', label: 'Driver', title: null, issuedBy: null, issueDate: null, expiryDate: null, certificateRef: null, required: false, notes: null,
      state: 'INCOMPLETE', detail: 'no certificate attached', verifiedByName: null, verifiedAt: null, verificationNote: null, evidence: [], createdAt: '2026-01-12T00:00:00Z' }],
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

  it('shows the readiness percentage, the verdict and the reasons it is not ready', async () => {
    page()
    expect(await screen.findByRole('img', { name: 'Deployment readiness 60 percent, not ready' })).toBeTruthy()
    expect(screen.getByText('Not ready, 60%')).toBeTruthy()
    expect(screen.getByText('Drug test: result still pending')).toBeTruthy()
  })

  it('says ready when the server says so', async () => {
    page(overview({ readiness: { percent: 100, ready: true, reasons: [], items: [] } }))
    expect(await screen.findByText('Ready to deploy, 100%')).toBeTruthy()
  })

  it('shows the compliance matrix with evidence counts, and marks optional checks', async () => {
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    expect(screen.getByText('Criminal record check')).toBeTruthy()
    expect(screen.getByText('1 file')).toBeTruthy()
    expect(screen.getByText('(not required)')).toBeTruthy()
  })

  it('opens a screening from the matrix and shows its evidence and trail', async () => {
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0])
    expect(await screen.findByText('clearance.pdf')).toBeTruthy()
    expect(screen.getByText(/from ABC Screening/)).toBeTruthy()
    expect(screen.getByText('Awaiting sign-off.')).toBeTruthy()
  })

  it('uploads an evidence file with its label', async () => {
    api.post.mockResolvedValue({ data: {} })
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0])
    fireEvent.change(await screen.findByLabelText('Evidence label'), { target: { value: 'Verification response' } })
    const file = new File(['x'], 'response.pdf', { type: 'application/pdf' })
    fireEvent.change(screen.getByLabelText('Evidence file'), { target: { files: [file] } })
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    const [url, body] = api.post.mock.calls[0]
    expect(url).toBe('/api/v1/security/guards/g1/screening/s1/evidence')
    expect((body as FormData).get('label')).toBe('Verification response')
    expect(((body as FormData).get('file') as File).name).toBe('response.pdf')
  })

  it('signs off a screening, sending the note', async () => {
    api.post.mockResolvedValue({ data: {} })
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0])
    fireEvent.change(await screen.findByLabelText('Decision note'), { target: { value: ' Verified ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Clear' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/guards/g1/screening/s1/decision', { decision: 'CLEARED', note: 'Verified' }))
  })

  it('offers no sign-off while the result is pending', async () => {
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[1]) // drug test (pending)
    expect(await screen.findByText(/Record the result first/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Clear' })).toBeNull()
  })

  it('records a result for a pending screening', async () => {
    api.post.mockResolvedValue({ data: {} })
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[1])
    fireEvent.click(await screen.findByRole('button', { name: 'Record result' }))
    fireEvent.change(screen.getByLabelText('Valid until'), { target: { value: '2027-10-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save result' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/guards/g1/screening/s2/result',
      expect.objectContaining({ result: 'PASS', nextDueAt: '2027-10-01', reportRef: null })))
  })

  it('requests a new screening with provider and date', async () => {
    api.post.mockResolvedValue({ data: {} })
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    fireEvent.click(screen.getByRole('button', { name: /Request screening/ }))
    fireEvent.change(screen.getByPlaceholderText('Agency doing the check'), { target: { value: 'LabCo' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Request screening' }).pop()!)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/guards/g1/screening',
      expect.objectContaining({ screeningType: 'CRIMINAL_RECORD_CHECK', reason: 'ONBOARDING', provider: 'LabCo' })))
  })

  it('renew pre-fills the same type as a periodic screening', async () => {
    api.post.mockResolvedValue({ data: {} })
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0])
    fireEvent.click(await screen.findByRole('button', { name: /Renew/ }))
    fireEvent.click(screen.getAllByRole('button', { name: 'Request screening' }).pop()!)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/guards/g1/screening',
      expect.objectContaining({ screeningType: 'CRIMINAL_RECORD_CHECK', reason: 'PERIODIC', provider: 'ABC Screening' })))
  })

  it('hides every screening action without manage permission', async () => {
    perms.clear(); perms.add('SECURITY_READ')
    page()
    fireEvent.click(await screen.findByRole('tab', { name: 'Compliance' }))
    expect(screen.queryByRole('button', { name: /Request screening/ })).toBeNull()
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0])
    await screen.findByText('clearance.pdf')
    expect(screen.queryByLabelText('Evidence file')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Renew' })).toBeNull()
    expect(screen.queryByLabelText('Decision note')).toBeNull()
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

describe('Skills tab', () => {
  beforeEach(() => { vi.clearAllMocks(); perms.clear(); perms.add('SECURITY_MANAGE') })
  const openSkills = async () => { page(); fireEvent.click(await screen.findByRole('tab', { name: 'Skills' })) }

  it('lists competencies with status, certificate count and the required flag', async () => {
    await openSkills()
    expect(screen.getByText('First aid')).toBeTruthy()
    expect(screen.getByText('Required')).toBeTruthy()
    expect(screen.getByText('1 file')).toBeTruthy()
    expect(screen.getByText('Unverified')).toBeTruthy()
    expect(screen.getByText('no certificate attached')).toBeTruthy()
  })

  it('verifies a competency that has a certificate, sending the note', async () => {
    api.post.mockResolvedValue({ data: {} })
    await openSkills()
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0])
    fireEvent.change(await screen.findByLabelText('Verification note'), { target: { value: ' Original sighted ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Verify' }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/guards/g1/competencies/c1/verify', { note: 'Original sighted' }))
  })

  it('cannot verify while no certificate is attached', async () => {
    await openSkills()
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[1])
    expect((await screen.findByRole('button', { name: 'Verify' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/cannot be verified until one is/)).toBeTruthy()
  })

  it('uploads a certificate to the competency', async () => {
    api.post.mockResolvedValue({ data: {} })
    await openSkills()
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[1])
    fireEvent.change(await screen.findByLabelText('Evidence file'), { target: { files: [new File(['x'], 'licence.pdf', { type: 'application/pdf' })] } })
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    expect(api.post.mock.calls[0][0]).toBe('/api/v1/security/guards/g1/competencies/c2/evidence')
  })

  it('adds a competency with the required flag', async () => {
    api.post.mockResolvedValue({ data: {} })
    await openSkills()
    fireEvent.click(screen.getByRole('button', { name: /Add competency/ }))
    fireEvent.change(screen.getByLabelText('Competency'), { target: { value: 'FIREARM_COMPETENCY' } })
    fireEvent.change(screen.getByLabelText('Expiry date'), { target: { value: '2028-03-01' } })
    fireEvent.click(screen.getByLabelText(/Required for deployment/))
    fireEvent.click(screen.getAllByRole('button', { name: 'Add competency' }).pop()!)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith('/api/v1/security/guards/g1/competencies',
      expect.objectContaining({ competencyType: 'FIREARM_COMPETENCY', expiryDate: '2028-03-01', issueDate: null, required: true })))
  })

  it('asks for a name when the type is Other and does not call the server', async () => {
    await openSkills()
    fireEvent.click(screen.getByRole('button', { name: /Add competency/ }))
    fireEvent.change(screen.getByLabelText('Competency'), { target: { value: 'OTHER' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Add competency' }).pop()!)
    expect((await screen.findByRole('alert')).textContent).toBe('Give the competency a name')
    expect(api.post).not.toHaveBeenCalled()
  })

  it('refuses an expiry date before the issue date on the form', async () => {
    await openSkills()
    fireEvent.click(screen.getByRole('button', { name: /Add competency/ }))
    fireEvent.change(screen.getByLabelText('Issue date'), { target: { value: '2026-06-01' } })
    fireEvent.change(screen.getByLabelText('Expiry date'), { target: { value: '2026-05-01' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'Add competency' }).pop()!)
    expect((await screen.findByRole('alert')).textContent).toBe('The expiry date cannot be before the issue date')
  })

  it('warns that editing removes a verification, and removes only after confirmation', async () => {
    api.delete.mockResolvedValue({ data: {} })
    await openSkills()
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0])
    fireEvent.click(await screen.findByRole('button', { name: 'Remove' }))
    expect(api.delete).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Yes, remove' }))
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/v1/security/guards/g1/competencies/c1'))
  })

  it('shows read-only users the list without any change controls', async () => {
    perms.clear(); perms.add('SECURITY_READ')
    await openSkills()
    expect(screen.queryByRole('button', { name: /Add competency/ })).toBeNull()
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0])
    await screen.findByText('firstaid.pdf')
    expect(screen.queryByRole('button', { name: 'Verify' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Edit' })).toBeNull()
  })
})
