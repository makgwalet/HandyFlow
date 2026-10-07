import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
import GuardHrLinkCard from './GuardHrLinkCard'

afterEach(() => cleanup())
beforeEach(() => vi.clearAllMocks())
const none = { linked: false, employeeMissing: false, employeeId: null, employeeNumber: null, fullName: null, jobTitle: null, department: null, status: null }
const show = (canManage = true) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><GuardHrLinkCard guardId="g1" canManage={canManage} /></QueryClientProvider>)

describe('GuardHrLinkCard', () => {
  it('searches HR employees and links the chosen one', async () => {
    api.get.mockImplementation(async (url: string) => url.endsWith('/hr-employees')
      ? { data: { data: [{ id: 'e7', employeeNumber: 'EMP-0007', fullName: 'Gerhard Botha', jobTitle: 'Officer', status: 'ACTIVE' }] } }
      : { data: { data: none } })
    api.put.mockResolvedValue({ data: { data: { ...none, linked: true, employeeId: 'e7', fullName: 'Gerhard Botha', employeeNumber: 'EMP-0007' } } })
    show()
    fireEvent.change(await screen.findByLabelText('Search HR employees'), { target: { value: 'bot' } })
    fireEvent.click(await screen.findByRole('button', { name: /Link/ }))
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/v1/security/guards/g1/hr-link', { employeeId: 'e7' }))
    expect(await screen.findByText(/Gerhard Botha · EMP-0007/)).toBeTruthy()
  })
  it('shows a linked record and unlinks it', async () => {
    api.get.mockResolvedValue({ data: { data: { ...none, linked: true, employeeId: 'e7', fullName: 'Gerhard Botha', employeeNumber: 'EMP-0007' } } })
    api.delete.mockResolvedValue({ data: { data: none } })
    show()
    fireEvent.click(await screen.findByRole('button', { name: /Unlink/ }))
    await waitFor(() => expect(api.delete).toHaveBeenCalled())
    expect(await screen.findByText(/Not linked/)).toBeTruthy()
  })
  it('offers no search to someone who cannot manage, and hides itself if the link cannot be read', async () => {
    api.get.mockResolvedValue({ data: { data: none } })
    show(false)
    await screen.findByText(/Not linked/)
    expect(screen.queryByLabelText('Search HR employees')).toBeNull()
    cleanup()
    api.get.mockRejectedValue(new Error('403'))
    const r = show()
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    expect(r.container.textContent).toBe('')
  })
})
