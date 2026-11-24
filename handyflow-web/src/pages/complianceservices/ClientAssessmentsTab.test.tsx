// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientAssessmentsTab from './ClientAssessmentsTab'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))

const item = (over: object) => ({
  obligationId: 'o1', code: 'CIPC-AR', name: 'Annual return', versionNumber: 1, riskLevel: 'HIGH', applicability: 'APPLICABLE', evidenceStatus: 'MET',
  control: 'UNKNOWN', workflow: 'NONE', status: 'NOT_ASSESSED', displayStatus: 'NOT_ASSESSED', note: null,
  evidence: [{ documentType: 'Annual return receipt', mandatory: true, result: 'MET', detail: 'Annual return receipt document is verified', expiresOn: null }], ...over,
})
const assessment = (over: object = {}) => ({
  id: 'a1', status: 'DRAFT', asOf: '2026-11-24', weights: { low: 1, medium: 2, high: 4, critical: 8, isDefault: true },
  score: { percent: null, scored: 0, unscored: 1, provisional: true }, summaryNote: null, items: [item({})], completionBlockers: ['CIPC-AR'], comparedWith: null, changes: [], ...over,
})
const summaries = [{ id: 'a1', status: 'DRAFT', asOf: '2026-11-24', scorePercent: null, scoredCount: null, unscoredCount: null }]

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientAssessmentsTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientAssessmentsTab', () => {
  afterEach(cleanup)
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/assessment-weights')) return { data: { data: { low: 1, medium: 2, high: 4, critical: 8, isDefault: true } } }
      if (url.endsWith('/assessments')) return { data: { data: summaries } }
      return { data: { data: assessment() } }
    })
    put.mockResolvedValue({ data: { data: assessment({ items: [item({ control: 'YES', status: 'COMPLIANT', displayStatus: 'COMPLIANT' })], completionBlockers: [], score: { percent: 100, scored: 1, unscored: 0, provisional: true } }) } })
    post.mockResolvedValue({ data: { data: assessment({ status: 'COMPLETED' }) } })
  })

  it('shows a provisional score, the weights and the evidence result', async () => {
    renderIt()
    expect(await screen.findByText('CIPC-AR')).toBeTruthy()
    expect(screen.getByText(/Nothing scored yet · 0 scored · 1 not assessed · provisional/)).toBeTruthy()
    expect(screen.getByText(/weights low 1, medium 2, high 4, critical 8/)).toBeTruthy()
    expect(screen.getByText(/Annual return receipt: Annual return receipt document is verified/)).toBeTruthy()
    expect(screen.getByText('Not assessed')).toBeTruthy()
  })

  it('does not let the assessment be completed while something is unjudged', async () => {
    renderIt()
    await screen.findByText('CIPC-AR')
    expect(screen.getByText(/Still to judge, or explain: CIPC-AR/)).toBeTruthy()
    expect((screen.getByText('Complete assessment') as HTMLButtonElement).disabled).toBe(true)
  })

  it('wants a note before saving a no, then sends the judgement', async () => {
    renderIt()
    await screen.findByText('CIPC-AR')
    fireEvent.click(screen.getByText('Judge'))
    fireEvent.change(screen.getByLabelText('Control'), { target: { value: 'NO' } })
    expect((screen.getByText('Save judgement') as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/Say what is missing/)).toBeTruthy()
    fireEvent.change(screen.getByPlaceholderText(/what is missing, or what is being done/), { target: { value: 'No annual return has been filed' } })
    fireEvent.click(screen.getByText('Save judgement'))
    await waitFor(() => expect(put).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/assessments/a1/items/o1', { control: 'NO', workflow: 'NONE', note: 'No annual return has been filed' }))
  })

  it('refuses weights that fall as risk rises', async () => {
    renderIt()
    await screen.findByText('CIPC-AR')
    fireEvent.click(screen.getByText('Change weights'))
    fireEvent.change(screen.getAllByRole('spinbutton')[0], { target: { value: '9' } })
    expect((screen.getByText('Save weights') as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/cannot weigh less/)).toBeTruthy()
  })

  it('checks the documents again on request', async () => {
    renderIt()
    await screen.findByText('CIPC-AR')
    fireEvent.click(screen.getByText('Check documents again'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/assessments/a1/recheck', {}))
  })

  it('lists what changed since the last completed assessment', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/assessment-weights')) return { data: { data: { low: 1, medium: 2, high: 4, critical: 8, isDefault: true } } }
      if (url.endsWith('/assessments')) return { data: { data: summaries } }
      return { data: { data: assessment({ changes: [{ code: 'CIPC-AR', from: 'COMPLIANT', to: 'EXPIRED', reason: 'Certificate expired' }] }) } }
    })
    renderIt()
    expect(await screen.findByText('CIPC-AR: Compliant → Evidence expired (Certificate expired)')).toBeTruthy()
  })
})
