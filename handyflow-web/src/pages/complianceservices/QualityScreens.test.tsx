// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import QualityReviewPanel from './QualityReviewPanel'
import ClientQualityTab from './ClientQualityTab'
import QualityPage from './QualityPage'

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'me' } }) }))

const summary = (o: object = {}) => ({ id: 'r1', clientId: 'c1', clientName: 'Acme', subjectType: 'CASE', subjectLabel: 'Late return', templateName: 'Case check', round: 1, status: 'IN_PROGRESS', sample: false, startedAt: '2026-11-20T00:00:00Z', completedAt: null, checksDone: 0, checksTotal: 2, failed: 0, actions: ['RECORD', 'COMPLETE'], ...o })
const detail = (o: object = {}, items?: object[], s: object = {}) => ({
  review: summary(s), subjectId: 's1', preparedBy: 'someone', reviewedBy: null, outcomeNote: null, exceptionReason: null, previousReviewId: null,
  items: items ?? [{ position: 1, text: 'Figures agree', critical: true, result: 'PENDING', note: null }, { position: 2, text: 'Wording is clear', critical: false, result: 'PENDING', note: null }], ...o,
})
const wrap = (ui: React.ReactNode) => render(<MemoryRouter><QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider></MemoryRouter>)

describe('quality screens', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); put.mockResolvedValue({ data: {} }); post.mockResolvedValue({ data: {} }) })

  it('saves a pass straight away, and a fail only once it says why', async () => {
    get.mockResolvedValue({ data: { data: detail() } })
    wrap(<QualityReviewPanel reviewId="r1" canManage onChanged={() => {}} />)
    const pass = await screen.findAllByText('Pass')
    fireEvent.click(pass[0])
    await waitFor(() => expect(put).toHaveBeenCalledWith('/api/v1/compliance-services/quality/reviews/r1/checks/1', { result: 'PASS', note: null }))
    const fail = screen.getAllByText('Fail')
    fireEvent.click(fail[1])
    expect(put).toHaveBeenCalledTimes(1)
    const save = screen.getByText('Save') as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Why, for Wording is clear/), { target: { value: 'The wording is ambiguous' } })
    expect((screen.getByText('Save') as HTMLButtonElement).disabled).toBe(false)
  })

  it('will not offer approval while a check is unmarked', async () => {
    get.mockResolvedValue({ data: { data: detail() } })
    wrap(<QualityReviewPanel reviewId="r1" canManage onChanged={() => {}} />)
    fireEvent.change(await screen.findByLabelText('Summary of the review'), { target: { value: 'Checked every figure against the evidence' } })
    expect(screen.getByText(/Mark every check first/)).toBeTruthy()
    const approve = screen.getAllByText('Approve')
    expect((approve[approve.length - 1] as HTMLButtonElement).disabled).toBe(true)
  })

  it('asks for a reason when the reviewer prepared the work', async () => {
    const done = [{ position: 1, text: 'Figures agree', critical: true, result: 'PASS', note: null }]
    get.mockResolvedValue({ data: { data: detail({ preparedBy: 'me' }, done) } })
    wrap(<QualityReviewPanel reviewId="r1" canManage onChanged={() => {}} />)
    fireEvent.change(await screen.findByLabelText('Summary of the review'), { target: { value: 'Checked every figure against the evidence' } })
    expect(screen.getByText(/You prepared this work/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText(/Why you are reviewing your own work/), { target: { value: 'Nobody else is in today' } })
    expect(screen.queryByText(/You prepared this work\. Give/)).toBeNull()
  })

  it('offers the next round on work that was sent back', async () => {
    get.mockResolvedValue({ data: { data: detail({}, [{ position: 1, text: 'Figures agree', critical: false, result: 'FAIL', note: 'The total is wrong' }], { status: 'REWORK_REQUIRED', actions: ['NEXT_ROUND'] }) } })
    wrap(<QualityReviewPanel reviewId="r1" canManage onChanged={() => {}} />)
    fireEvent.click(await screen.findByText('Start the next round'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/quality/reviews/r1/next-round', {}))
  })

  it('lists a client\'s reviews and starts one for other work', async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith('/quality-reviews') ? [summary()] : url.endsWith('/templates') ? [{ id: 't1', name: 'Other check', subjectType: 'OTHER', description: null, active: true, items: [] }] : [] } }))
    wrap(<ClientQualityTab clientId="c1" />)
    expect(await screen.findByText('Late return')).toBeTruthy()
    fireEvent.click(screen.getByText('Start a review'))
    fireEvent.change(screen.getByLabelText('Kind of work'), { target: { value: 'OTHER' } })
    fireEvent.change(screen.getByLabelText('Describe the work'), { target: { value: 'Year-end letter' } })
    await screen.findByText('Other check')
    fireEvent.change(screen.getByLabelText('Checklist'), { target: { value: 't1' } })
    fireEvent.click(screen.getByText('Start review'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/quality-reviews', { subjectType: 'OTHER', subjectId: null, subjectLabel: 'Year-end letter', templateId: 't1' }))
  })

  it('creates a checklist and runs a sample from the page', async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith('/templates') ? [{ id: 't2', name: 'Assessment check', subjectType: 'ASSESSMENT', description: null, active: true, items: [{ position: 1, text: 'x', critical: false }] }] : [] } }))
    post.mockResolvedValue({ data: { data: { pool: 10, started: 1 } } })
    wrap(<QualityPage />)
    fireEvent.click(await screen.findByText('Checklists'))
    fireEvent.click(await screen.findByText('New checklist'))
    expect((screen.getByText('Save checklist') as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Case check' } })
    fireEvent.change(screen.getByLabelText('Check 1'), { target: { value: 'Figures agree' } })
    fireEvent.click(screen.getByText('Save checklist'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/quality/templates', expect.objectContaining({ name: 'Case check', items: [{ text: 'Figures agree', critical: false }] })))
    fireEvent.click(screen.getByText('Random sample'))
    await screen.findByText('Pick a sample')
    fireEvent.change(screen.getByLabelText('Checklist'), { target: { value: 't2' } })
    fireEvent.click(screen.getByText('Pick a sample'))
    expect(await screen.findByText(/Picked 1 of 10/)).toBeTruthy()
  })
})
