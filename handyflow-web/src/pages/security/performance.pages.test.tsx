import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
const perms = new Set<string>()
vi.mock('../../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import GuardPerformancePanel from './GuardPerformancePanel'
import RiskRulesTab from './RiskRulesTab'

afterEach(() => cleanup())
beforeEach(() => { vi.clearAllMocks(); perms.clear() })

const settings = (o: any = {}) => ({ reviewAt: 1, warningAt: 3, investigationAt: 5, windowDays: 90, misconductAt: 2, misconductWindowDays: 365, suspensionReviewOnCritical: true, customised: false, updatedByName: null, updatedAt: null, ...o })
const comp = (key: string, label: string, weight: number, hasData: boolean, percent: number, detail: string) => ({ key, label, weight, hasData, percent, points: hasData ? weight * percent / 100 : 0, detail })
const perf = (o: any = {}) => ({
  score: 78, band: 'GOOD', coverage: 85,
  components: [comp('ATTENDANCE', 'Attendance', 20, true, 90, '18 of 20 shifts worked'), comp('PATROL', 'Checkpoint compliance', 15, false, 0, 'Not enough data: fewer than 3 shifts with a scan requirement')],
  recommendations: [{ code: 'WARNING_REVIEW', level: 'WARN', title: 'Review whether a warning is appropriate', reason: '3 complaints in the last 90 days (threshold 3).' }],
  basis: { days: 90, complaintsCounted: 3, substantiatedMisconduct: 0, criticalIncidents: 0, openUrgentComplaints: 0 },
  settings: settings(), ratingAverage: 4.2, ratingCount: 1,
  ratings: [{ id: 'r1', source: 'CLIENT', raterName: 'Mrs Dlamini', siteName: 'ABC Mall', ratedOn: '2026-10-01', punctuality: 4, professionalism: 5, appearance: 4, communication: 4, alertness: 4, incidentHandling: 4, average: 4.2, comment: 'Helpful', createdByName: 'Sam' }],
  ...o })

function renderPanel() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter><GuardPerformancePanel guardId="g1" canManage={perms.has('SECURITY_MANAGE')} /></MemoryRouter></QueryClientProvider>)
}

describe('GuardPerformancePanel', () => {
  it('draws the score trend from the history and says what changed', async () => {
    api.get.mockImplementation(async (url: string) => url.endsWith('/history')
      ? { data: { data: [{ date: '2026-09-20', score: 60, band: 'NEEDS_ATTENTION', coverage: 90, recommendations: 0 }, { date: '2026-10-06', score: 72, band: 'GOOD', coverage: 90, recommendations: 1 }] } }
      : { data: { data: perf() } })
    renderPanel()
    expect(await screen.findByRole('img', { name: /Operational score over the last 30 days. Up 12 points since/ })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '90 days' }))
    await waitFor(() => expect(api.get).toHaveBeenCalledWith('/api/v1/security/guards/g1/performance/history', { params: { days: 90 } }))
  })
  it('says the trend builds up when there is no history', async () => {
    api.get.mockImplementation(async (url: string) => url.endsWith('/history') ? { data: { data: [] } } : { data: { data: perf() } })
    renderPanel()
    expect(await screen.findByText(/No history yet/)).toBeTruthy()
  })
  it('shows the score, explains each component and leaves out the ones without data', async () => {
    api.get.mockResolvedValue({ data: { data: perf() } })
    renderPanel()
    expect(await screen.findByRole('img', { name: /Operational score 78 out of 100, Good/ })).toBeTruthy()
    expect(screen.getByText('18 of 20 shifts worked')).toBeTruthy()
    expect(screen.getByText('not counted')).toBeTruthy()
    expect(screen.getByText(/Based on 85 of 100 points/)).toBeTruthy()
  })
  it('shows recommendations as advice, never as a decision', async () => {
    api.get.mockResolvedValue({ data: { data: perf() } })
    renderPanel()
    expect(await screen.findByText('Review whether a warning is appropriate')).toBeTruthy()
    expect(screen.getByText(/Nothing here suspends, warns or dismisses anyone/)).toBeTruthy()
  })
  it('says so when nothing is flagged', async () => {
    api.get.mockResolvedValue({ data: { data: perf({ recommendations: [], basis: { days: 90, complaintsCounted: 0, substantiatedMisconduct: 0, criticalIncidents: 0, openUrgentComplaints: 0 } }) } })
    renderPanel()
    expect(await screen.findByText(/Nothing to flag/)).toBeTruthy()
  })
  it('shows no score when there is not enough data', async () => {
    api.get.mockResolvedValue({ data: { data: perf({ score: null, band: 'NOT_ENOUGH_DATA', coverage: 15 }) } })
    renderPanel()
    expect(await screen.findByRole('img', { name: 'No operational score yet' })).toBeTruthy()
    expect(screen.getByText(/Not enough records yet/)).toBeTruthy()
  })
  it('adds a rating once every dimension is scored', async () => {
    perms.add('SECURITY_MANAGE')
    api.get.mockResolvedValue({ data: { data: perf() } })
    api.post.mockResolvedValue({ data: {} })
    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /Add rating/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Save rating' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/punctuality/i)
    expect(api.post).not.toHaveBeenCalled()
    for (const d of ['Punctuality', 'Professionalism', 'Appearance', 'Communication', 'Alertness', 'Incident handling'])
      fireEvent.click(screen.getByRole('button', { name: `${d} 4 of 5` }))
    fireEvent.click(screen.getByRole('button', { name: 'Save rating' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    const [url, body] = api.post.mock.calls[0]
    expect(url).toBe('/api/v1/security/guards/g1/ratings')
    expect(body).toMatchObject({ source: 'CLIENT', punctuality: 4, incidentHandling: 4 })
  })
  it('hides Add rating without manage permission', async () => {
    api.get.mockResolvedValue({ data: { data: perf() } })
    renderPanel()
    await screen.findByText('Helpful')
    expect(screen.queryByRole('button', { name: /Add rating/ })).toBeNull()
  })
})

const readiness = (o: any = {}) => ({
  requiredScreening: ['CRIMINAL_RECORD_CHECK', 'DRUG_TEST'], requiredDocuments: ['ID_COPY'], customised: false,
  screeningOptions: [{ value: 'CRIMINAL_RECORD_CHECK', label: 'Criminal record check' }, { value: 'DRUG_TEST', label: 'Drug test' }],
  documentOptions: [{ value: 'ID_COPY', label: 'ID copy' }, { value: 'POPIA_CONSENT', label: 'POPIA consent' }], ...o })

describe('RiskRulesTab', () => {
  function renderRules() {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    return render(<QueryClientProvider client={qc}><RiskRulesTab /></QueryClientProvider>)
  }
  it('is read-only without admin permission', async () => {
    api.get.mockResolvedValue({ data: { data: settings() } })
    renderRules()
    const input = await screen.findByLabelText('Recommend a warning review at') as HTMLInputElement
    expect(input.disabled).toBe(true)
    expect(input.value).toBe('3')
    expect(screen.queryByRole('button', { name: 'Save risk rules' })).toBeNull()
  })
  it('blocks thresholds that do not rise and saves valid ones', async () => {
    perms.add('SECURITY_ADMIN')
    api.get.mockResolvedValue({ data: { data: settings() } })
    api.put.mockResolvedValue({ data: {} })
    renderRules()
    const warn = await screen.findByLabelText('Recommend a warning review at')
    fireEvent.change(warn, { target: { value: '0' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save risk rules' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/at least 1|rise/i)
    expect(api.put).not.toHaveBeenCalled()
    fireEvent.change(warn, { target: { value: '4' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save risk rules' }))
    await waitFor(() => expect(api.put).toHaveBeenCalled())
    expect(api.put.mock.calls[0][1]).toMatchObject({ reviewAt: 1, warningAt: 4, investigationAt: 5 })
  })
  it('shows the readiness requirements read-only without admin permission', async () => {
    api.get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes('readiness-settings') ? readiness() : settings() } }))
    renderRules()
    const box = await screen.findByLabelText('Drug test') as HTMLInputElement
    expect(box.checked).toBe(true)
    expect(box.disabled).toBe(true)
    expect((screen.getByLabelText('POPIA consent') as HTMLInputElement).checked).toBe(false)
    expect(screen.queryByRole('button', { name: 'Save readiness requirements' })).toBeNull()
  })
  it('saves the chosen screenings and documents', async () => {
    perms.add('SECURITY_ADMIN')
    api.get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes('readiness-settings') ? readiness() : settings() } }))
    api.put.mockResolvedValue({ data: {} })
    renderRules()
    fireEvent.click(await screen.findByLabelText('POPIA consent'))
    fireEvent.click(screen.getByLabelText('Drug test'))
    fireEvent.click(screen.getByRole('button', { name: 'Save readiness requirements' }))
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/v1/security/readiness-settings',
      { requiredScreening: ['CRIMINAL_RECORD_CHECK'], requiredDocuments: ['ID_COPY', 'POPIA_CONSENT'] }))
  })
  it('blocks saving when nothing is chosen', async () => {
    perms.add('SECURITY_ADMIN')
    api.get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes('readiness-settings') ? readiness() : settings() } }))
    renderRules()
    fireEvent.click(await screen.findByLabelText('Criminal record check'))
    fireEvent.click(screen.getByLabelText('Drug test'))
    fireEvent.click(screen.getByLabelText('ID copy'))
    fireEvent.click(screen.getByRole('button', { name: 'Save readiness requirements' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/at least one/i)
    expect(api.put).not.toHaveBeenCalled()
  })
})
