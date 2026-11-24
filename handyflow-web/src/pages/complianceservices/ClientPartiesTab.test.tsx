// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientPartiesTab from './ClientPartiesTab'

const get = vi.fn()
const post = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true }))

const ownership = { shareholdingDeclared: 60, fullyDeclared: false, hasDirector: false, hasBeneficialOwner: false, beneficialOwnerGap: true, directors: 0 }
const parties = {
  parties: [
    { id: 'p1', fullName: 'Thandi Nkosi', role: 'SHAREHOLDER', idType: 'PASSPORT', idNumber: 'A1', ownershipPercent: 60, appointedOn: '2024-01-01', resignedOn: null, active: true },
    { id: 'p2', fullName: 'Old Director', role: 'DIRECTOR', idType: null, idNumber: null, ownershipPercent: null, appointedOn: null, resignedOn: '2025-05-01', active: false },
  ],
  ownership,
}
const sites = [{ id: 's1', name: 'Head Office', siteType: 'HEAD_OFFICE', address: '1 Main Rd', employeeCount: 20, active: true }]

function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientPartiesTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientPartiesTab', () => {
  afterEach(cleanup)
  beforeEach(() => {
    get.mockReset(); post.mockReset()
    get.mockImplementation(async (url: string) => ({ data: { data: url.endsWith('/parties') ? parties : sites } }))
    post.mockResolvedValue({ data: { data: parties } })
  })

  it('lists current people, hides former ones, and points out the gaps', async () => {
    renderTab()
    expect(await screen.findByText('Thandi Nkosi')).toBeTruthy()
    expect(screen.getByText('No director on file')).toBeTruthy()
    expect(screen.getByText(/60%, not 100%/)).toBeTruthy()
    expect(screen.getByText(/no beneficial owner is on file/)).toBeTruthy()
    expect(screen.getByText('1 former')).toBeTruthy()
    expect(screen.getByText('Head Office')).toBeTruthy()
  })

  it('refuses an invalid South African ID', async () => {
    renderTab()
    await screen.findByText('Thandi Nkosi')
    fireEvent.change(screen.getByPlaceholderText('Full name'), { target: { value: 'New Person' } })
    fireEvent.change(screen.getByPlaceholderText('ID number'), { target: { value: '1234567890123' } })
    expect(screen.getByText('That South African ID number is not valid')).toBeTruthy()
    expect((screen.getByText('Add person') as HTMLButtonElement).disabled).toBe(true)
  })

  it('adds a person', async () => {
    renderTab()
    await screen.findByText('Thandi Nkosi')
    fireEvent.change(screen.getByPlaceholderText('Full name'), { target: { value: 'New Person' } })
    fireEvent.change(screen.getByPlaceholderText('ID number'), { target: { value: '8001015009087' } })
    fireEvent.click(screen.getByText('Add person'))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe('/api/v1/compliance-services/clients/c1/parties')
    expect(body).toMatchObject({ fullName: 'New Person', role: 'DIRECTOR', idType: 'SA_ID', idNumber: '8001015009087', ownershipPercent: null })
  })

  it('only asks for a percentage on owner roles', async () => {
    renderTab()
    await screen.findByText('Thandi Nkosi')
    expect(screen.queryByPlaceholderText('Share %')).toBeNull()
    fireEvent.change(screen.getAllByRole('combobox')[0], { target: { value: 'SHAREHOLDER' } })
    expect(screen.getByPlaceholderText('Share %')).toBeTruthy()
  })

  it('marks a person as resigned', async () => {
    renderTab()
    fireEvent.click(await screen.findByText('Mark as resigned'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/parties/p1/resign', {}))
  })

  it('deactivates a site', async () => {
    post.mockResolvedValue({ data: { data: sites } })
    renderTab()
    fireEvent.click(await screen.findByText('Deactivate'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/sites/s1/deactivate', {}))
  })
})
