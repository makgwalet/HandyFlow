import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
import GuardReviewsCard from './GuardReviewsCard'

afterEach(() => cleanup())
beforeEach(() => vi.clearAllMocks())
const review = { id: 'r1', siteId: null, siteName: null, reviewDate: '2026-09-20', periodFrom: '2026-06-20', periodTo: '2026-09-19', reviewerName: 'Sam', overall: 'BELOW',
  punctuality: 2, professionalism: 3, appearance: 3, communication: 3, alertness: 2, incidentHandling: 3, average: 2.7, strengths: null, improvements: 'Late often',
  trainingNeeds: 'Report writing', actionsAgreed: 'Arrive early', followUpDate: '2026-10-05', createdAt: '2026-09-20T08:00:00Z' }
const list = (o: any = {}) => ({ data: { data: { reviews: [review], lastReviewOn: '2026-09-20', daysSinceLast: 17, dueState: 'FOLLOW_UP_DUE', intervalDays: 90, ...o } } })
const show = (canManage = true) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><GuardReviewsCard guardId="g1" canManage={canManage} /></QueryClientProvider>)

describe('GuardReviewsCard', () => {
  it('lists past reviews with the due notice', async () => {
    api.get.mockResolvedValue(list())
    show()
    expect(await screen.findByText(/follow-up date set at the last review has arrived/i)).toBeTruthy()
    expect(screen.getByText('Below expectations')).toBeTruthy()
    expect(screen.getByText(/Late often/)).toBeTruthy()
  })
  it('will not send an incomplete review, then sends a complete one', async () => {
    api.get.mockResolvedValue(list({ reviews: [], dueState: 'NONE', daysSinceLast: null, lastReviewOn: null }))
    api.post.mockResolvedValue({ data: {} })
    show()
    fireEvent.click(await screen.findByRole('button', { name: 'New review' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save review' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/overall/i)
    expect(api.post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('Overall'), { target: { value: 'MEETS' } })
    for (const d of ['Punctuality', 'Professionalism', 'Appearance', 'Communication', 'Alertness', 'Incident handling'])
      fireEvent.click(screen.getByRole('button', { name: `Review ${d} 4 of 5` }))
    fireEvent.change(screen.getByLabelText('What went well'), { target: { value: 'Reliable' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save review' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    const [url, body] = api.post.mock.calls[0]
    expect(url).toBe('/api/v1/security/guards/g1/reviews')
    expect(body).toMatchObject({ overall: 'MEETS', punctuality: 4, incidentHandling: 4, strengths: 'Reliable', improvements: null })
  })
  it('hides New review without manage rights and hides itself when the reviews cannot be read', async () => {
    api.get.mockResolvedValue(list())
    show(false)
    await screen.findByText('Below expectations')
    expect(screen.queryByRole('button', { name: 'New review' })).toBeNull()
    cleanup()
    api.get.mockRejectedValue(new Error('403'))
    const r = show()
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    expect(r.container.textContent).toBe('')
  })
})
