// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientDocumentsTab from './ClientDocumentsTab'

const get = vi.fn()
const post = vi.fn()
const del = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'me' } }) }))

const doc = (over: object) => ({
  id: 'd1', registrationId: null, documentType: 'Tax Clearance Certificate', evidenceId: 'e1', issueDate: null, expiryDate: null, verified: false, verifiedAt: null,
  createdAt: '2026-11-01T00:00:00Z', uploadedBy: 'someone', status: 'UPLOADED', canDelete: true, ...over,
})

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientDocumentsTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientDocumentsTab life of a document', () => {
  afterEach(cleanup)
  beforeEach(() => {
    get.mockReset(); post.mockReset(); del.mockReset()
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (url: string) => {
      if (url.endsWith('/registrations')) return { data: { data: [] } }
      if (url.endsWith('/events')) return { data: { data: [{ event: 'UPLOADED', fromStatus: null, toStatus: 'UPLOADED', reason: null, occurredAt: '2026-11-01T08:00:00Z' }] } }
      return { data: { data: [
        doc({}),
        doc({ id: 'd2', documentType: 'BBBEE Certificate', status: 'VERIFIED', verified: true, verifiedAt: '2026-11-02T00:00:00Z', canDelete: false }),
        doc({ id: 'd3', documentType: 'Old Certificate', status: 'SUPERSEDED', canDelete: false }),
        doc({ id: 'd4', documentType: 'Bad Scan', status: 'REJECTED', rejectionReason: 'Wrong company on the certificate', canDelete: false }),
      ] } }
    })
  })

  it('hides replaced documents until asked, and shows why one was rejected', async () => {
    renderIt()
    expect(await screen.findByText('Tax Clearance Certificate')).toBeTruthy()
    expect(screen.queryByText('Old Certificate')).toBeNull()
    expect(screen.getByText(/Rejected: Wrong company on the certificate/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText(/Show replaced and archived documents/))
    expect(screen.getByText('Old Certificate')).toBeTruthy()
  })

  it('offers delete only for an untouched upload', async () => {
    renderIt()
    await screen.findByText('Tax Clearance Certificate')
    expect(screen.getAllByTitle('Delete')).toHaveLength(1)
  })

  it('will not reject without a reason, then sends it', async () => {
    renderIt()
    await screen.findByText('Tax Clearance Certificate')
    fireEvent.click(screen.getAllByText('Reject')[0])
    const button = screen.getByText('Reject document') as HTMLButtonElement
    expect(button.disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText('Why is this rejected?'), { target: { value: 'The certificate is for another company' } })
    fireEvent.click(button)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/documents/d1/reject', { reason: 'The certificate is for another company' }))
  })

  it('archives a verified document with a reason, not by deleting it', async () => {
    renderIt()
    await screen.findByText('BBBEE Certificate')
    fireEvent.click(screen.getAllByText('Archive')[1])
    fireEvent.change(screen.getByPlaceholderText('Why is this archived?'), { target: { value: 'Client left the practice in October' } })
    fireEvent.click(screen.getByText('Archive document'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/documents/d2/archive', { reason: 'Client left the practice in October' }))
    expect(del).not.toHaveBeenCalled()
  })

  it('starts a replacement for the same kind of document', async () => {
    renderIt()
    await screen.findByText('BBBEE Certificate')
    fireEvent.click(screen.getAllByText('Replace')[1])
    expect(screen.getByText(/Upload a replacement for BBBEE Certificate/)).toBeTruthy()
    expect(screen.getByText(/stays on record but stops counting/)).toBeTruthy()
  })

  it('shows the history of a document', async () => {
    renderIt()
    await screen.findByText('Tax Clearance Certificate')
    fireEvent.click(screen.getAllByText('History')[0])
    expect(await screen.findByText(/2026-11-01 · uploaded/)).toBeTruthy()
  })
})
