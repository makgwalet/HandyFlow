// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import RemindersPage from './RemindersPage'

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

const settings = (o: object = {}) => ({ enabled: false, minSeverity: 'HIGH', maxItems: 25, recipients: [], updatedAt: null, ...o })
const staff = [{ id: 'u1', firstName: 'Thandi', lastName: 'Nkosi' }, { id: 'u2', firstName: 'Sipho', lastName: 'Dlamini' }]
const reply = (s: object, extra: Record<string, unknown> = {}) => (url: string) => Promise.resolve({ data: { data: url.endsWith('/settings') ? s : url.includes('identity') ? staff : (extra[url.split('/').pop()!] ?? []) } })
const renderIt = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RemindersPage /></QueryClientProvider>)

describe('RemindersPage', () => {
  afterEach(cleanup)
  beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); put.mockResolvedValue({ data: {} }) })

  it('will not save a switched-on digest with nobody chosen, then saves once someone is added', async () => {
    get.mockImplementation(reply(settings()))
    renderIt()
    const box = await screen.findByLabelText('Send the daily digest')
    fireEvent.click(box)
    expect(screen.getByText(/at least one person/)).toBeTruthy()
    expect((screen.getByText('Save') as HTMLButtonElement).disabled).toBe(true)
    await screen.findByText('Thandi Nkosi')
    fireEvent.change(screen.getByLabelText('Add a person'), { target: { value: 'u1' } })
    fireEvent.click(screen.getByText('Add'))
    expect((screen.getByText('Save') as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(screen.getByText('Save'))
    await waitFor(() => expect(put).toHaveBeenCalledWith('/api/v1/compliance-services/reminders/settings', { enabled: true, minSeverity: 'HIGH', maxItems: 25, recipients: [{ userId: 'u1', adminItems: false }] }))
  })

  it('changes the urgency level and shows existing people', async () => {
    get.mockImplementation(reply(settings({ enabled: true, recipients: [{ userId: 'u2', name: 'Sipho Dlamini', email: 's@x.org', adminItems: true }] })))
    renderIt()
    expect(await screen.findByText('Sipho Dlamini')).toBeTruthy()
    fireEvent.click(screen.getByText('Urgent and soon'))
    fireEvent.click(screen.getByText('Save'))
    await waitFor(() => expect(put).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ minSeverity: 'MEDIUM', recipients: [{ userId: 'u2', adminItems: true }] })))
  })

  it('previews what each person would get and sends now', async () => {
    get.mockImplementation(reply(settings(), { preview: [{ userId: 'u1', name: 'Thandi Nkosi', adminItems: false, itemCount: 2, subject: 'Compliance Services: 2 items need attention (1 urgent)', body: '- [Urgent] Acme · VAT return' }] }))
    post.mockResolvedValue({ data: { data: { handedOver: 1, nothingDue: 0, noAddress: 0, failed: 0, alreadyDone: 0 } } })
    renderIt()
    fireEvent.click(await screen.findByText('Preview and send'))
    expect(await screen.findByText(/VAT return/)).toBeTruthy()
    fireEvent.click(screen.getByText('Send now'))
    expect(await screen.findByText(/1 handed to the notification service/)).toBeTruthy()
    expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/reminders/send-now', {})
  })

  it('shows the history without saying anything was delivered', async () => {
    get.mockImplementation(reply(settings(), { log: [{ runDate: '2026-11-25', userId: 'u1', name: 'Thandi Nkosi', itemCount: 3, outcome: 'HANDED_OVER', outcomeText: 'Handed to the notification service', detail: null, at: '2026-11-25T05:20:00Z' }] }))
    renderIt()
    fireEvent.click(await screen.findByText('History'))
    expect(await screen.findByText('Handed to the notification service')).toBeTruthy()
    expect(document.body.textContent?.includes('Delivered')).toBe(false)
  })
})
