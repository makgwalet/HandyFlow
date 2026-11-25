// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import RegulatoryPage from './RegulatoryPage'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let manage = true
let admin = true
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => manage, usePermission: () => admin }))

const change = (over: object = {}) => ({
  id: 'ch1', sourceId: 's1', sourceName: 'Gazette', title: 'Annual return fee change', changeType: 'AMENDED', status: 'IN_REVIEW', origin: 'MANUAL', publicationDate: '2026-11-01',
  effectiveDate: '2027-01-01', raisedAt: '2026-11-02T08:00:00Z', linkedObligations: 1, impactsOpen: 0, impactsTotal: 0, actions: ['EDIT', 'CONFIRM', 'DISMISS'], ...over,
})
const detail = (over: object = {}, c: object = {}) => ({
  change: change(c), summary: 'Fee goes up', referenceUrl: 'https://example.org/n', obligations: [{ id: 'o1', code: 'CIPC-AR', name: 'Annual return' }], reviewedAt: null, reviewNote: null,
  reviewExceptionReason: null, affected: [{ clientId: 'c1', clientName: 'Acme', obligationCode: 'CIPC-AR', state: 'APPLICABLE' }], impacts: [], ...over,
})
const impact = (over: object = {}) => ({ id: 'i1', changeId: 'ch1', changeTitle: 'Annual return fee change', clientId: 'c1', clientName: 'Acme', obligationCode: 'CIPC-AR', status: 'OPEN', dueDate: '2027-01-01', note: null, handledAt: null, noticeGivenAt: null, actions: ['NOTICE_GIVEN', 'NO_ACTION', 'DONE'], ...over })

function setup(detailValue = detail(), list = [change()]) {
  get.mockImplementation((url: string) => {
    if (url.endsWith('/regulatory/changes')) return Promise.resolve({ data: { data: list } })
    if (url.includes('/regulatory/changes/')) return Promise.resolve({ data: { data: detailValue } })
    if (url.endsWith('/regulatory/sources')) return Promise.resolve({ data: { data: [{ id: 's1', name: 'Gazette', authority: 'Government', url: null, kind: 'NOTICE', active: true }] } })
    if (url.endsWith('/obligations')) return Promise.resolve({ data: { data: [{ id: 'o1', code: 'CIPC-AR', name: 'Annual return' }] } })
    return Promise.resolve({ data: { data: [] } })
  })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><RegulatoryPage /></QueryClientProvider>)
}

describe('RegulatoryPage', () => {
  afterEach(cleanup)
  beforeEach(() => { manage = true; admin = true; get.mockReset(); post.mockReset(); put.mockReset(); post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } }) })

  it('lists changes, and opening one shows what it says and the clients it would affect', async () => {
    setup()
    fireEvent.click(await screen.findByRole('button', { name: /Annual return fee change/ }))
    expect(await screen.findByText('Fee goes up')).toBeTruthy()
    expect(screen.getByText(/Would affect: Acme \(CIPC-AR\)/)).toBeTruthy()
    expect(screen.getByText(/Obligations it touches: CIPC-AR/)).toBeTruthy()
  })

  it('confirming sends the note and the reason, and needs at least 15 characters', async () => {
    setup()
    fireEvent.click(await screen.findByRole('button', { name: /Annual return fee change/ }))
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm…' }))
    const save = screen.getByRole('button', { name: 'Confirm change' }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/what did you check/), { target: { value: 'Checked against the gazette notice' } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/regulatory/changes/ch1/confirm', { note: 'Checked against the gazette notice', exceptionReason: null }))
  })

  it('will not offer to confirm an amendment that has no effective date', async () => {
    setup(detail({}, { effectiveDate: null }), [change({ effectiveDate: null })])
    fireEvent.click(await screen.findByRole('button', { name: /Annual return fee change/ }))
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm…' }))
    expect(screen.getByText(/Add the date the change takes effect/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText(/what did you check/), { target: { value: 'Checked against the gazette notice' } })
    expect((screen.getByRole('button', { name: 'Confirm change' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('shows the server message when confirming is refused', async () => {
    post.mockRejectedValueOnce({ response: { data: { message: 'You raised this change, so someone else should review it.' } } })
    setup()
    fireEvent.click(await screen.findByRole('button', { name: /Annual return fee change/ }))
    fireEvent.click(await screen.findByRole('button', { name: 'Confirm…' }))
    fireEvent.change(screen.getByLabelText(/what did you check/), { target: { value: 'Checked against the gazette notice' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm change' }))
    expect((await screen.findByRole('alert')).textContent).toContain('someone else should review it')
  })

  it('without admin rights a reviewer sees no confirm or dismiss', async () => {
    admin = false
    setup()
    fireEvent.click(await screen.findByRole('button', { name: /Annual return fee change/ }))
    await screen.findByText('Fee goes up')
    expect(screen.queryByRole('button', { name: 'Confirm…' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Dismiss…' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Edit' })).toBeTruthy()
  })

  it('a confirmed change lists its clients, and an impact is moved on with a note', async () => {
    const d = detail({ impacts: [impact()] }, { status: 'CONFIRMED', actions: [], impactsOpen: 1, impactsTotal: 1 })
    setup(d, [d.change])
    fireEvent.click(await screen.findByRole('button', { name: /Annual return fee change/ }))
    expect(await screen.findByText(/Acme · CIPC-AR/)).toBeTruthy()
    expect(screen.getAllByText(/0 of 1 clients dealt with/).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole('button', { name: 'Client told' }))
    fireEvent.change(screen.getByLabelText(/what was done/), { target: { value: 'Phoned the client and sent the notice' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/regulatory/impacts/i1/act', { action: 'NOTICE_GIVEN', note: 'Phoned the client and sent the notice' }))
  })

  it('records a change with the chosen obligations', async () => {
    setup()
    fireEvent.click(await screen.findByRole('button', { name: 'Record a change' }))
    await screen.findByText(/CIPC-AR · Annual return/)
    fireEvent.change(await screen.findByLabelText('Source'), { target: { value: 's1' } })
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'New POPIA notice' } })
    fireEvent.change(screen.getByLabelText('Takes effect'), { target: { value: '2027-02-01' } })
    fireEvent.click(screen.getByLabelText(/CIPC-AR · Annual return/))
    fireEvent.click(screen.getByRole('button', { name: 'Record change' }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe('/api/v1/compliance-services/regulatory/changes')
    expect(body).toMatchObject({ sourceId: 's1', title: 'New POPIA notice', changeType: 'AMENDED', effectiveDate: '2027-02-01', publicationDate: null, obligationIds: ['o1'] })
  })

  it('adds a source, and refuses a bad web address', async () => {
    setup()
    fireEvent.click(await screen.findByRole('button', { name: 'Sources' }))
    fireEvent.change(await screen.findByLabelText('Name'), { target: { value: 'Information Regulator' } })
    fireEvent.change(screen.getByLabelText('Web address'), { target: { value: 'javascript:alert(1)' } })
    expect((screen.getByRole('button', { name: 'Add source' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Web address'), { target: { value: 'https://inforegulator.org.za' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add source' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/regulatory/sources', { name: 'Information Regulator', authority: null, url: 'https://inforegulator.org.za', kind: 'NOTICE' }))
  })

  it('is read only without permission to manage', async () => {
    manage = false; admin = false
    setup()
    await screen.findByRole('button', { name: /Annual return fee change/ })
    expect(screen.queryByRole('button', { name: 'Record a change' })).toBeNull()
  })
})
