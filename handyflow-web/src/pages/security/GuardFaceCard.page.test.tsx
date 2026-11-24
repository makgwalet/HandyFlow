// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), delete: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
import GuardFaceCard from './GuardFaceCard'

afterEach(() => cleanup())
beforeEach(() => vi.clearAllMocks())
const held = { stored: true, consentAt: '2026-11-12T08:00:00Z' }
const show = (canManage = true) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><GuardFaceCard guardId="g1" canManage={canManage} /></QueryClientProvider>)

describe('GuardFaceCard', () => {
  it('shows a held template and removes it only after a second step', async () => {
    api.get.mockResolvedValue({ data: { data: held } })
    api.delete.mockResolvedValue({ data: { data: { stored: false, consentAt: null } } })
    show()
    fireEvent.click(await screen.findByRole('button', { name: 'Remove face data' }))
    expect(api.delete).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByRole('button', { name: 'Yes, delete it' }))
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/api/v1/security/guards/g1/face'))
    expect(await screen.findByText('No face data is held for this guard.')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Remove face data' })).toBeNull()
  })
  it('lets the manager change their mind', async () => {
    api.get.mockResolvedValue({ data: { data: held } })
    show()
    fireEvent.click(await screen.findByRole('button', { name: 'Remove face data' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Keep it' }))
    expect(api.delete).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Remove face data' })).toBeTruthy()
  })
  it('offers no removal to someone who cannot manage', async () => {
    api.get.mockResolvedValue({ data: { data: held } })
    show(false)
    await screen.findByText(/A face template is held/)
    expect(screen.queryByRole('button', { name: 'Remove face data' })).toBeNull()
  })
  it('offers no removal when nothing is held', async () => {
    api.get.mockResolvedValue({ data: { data: { stored: false, consentAt: null } } })
    show()
    await screen.findByText('No face data is held for this guard.')
    expect(screen.queryByRole('button', { name: 'Remove face data' })).toBeNull()
  })
  it('shows the server message when removal fails and keeps the template listed', async () => {
    api.get.mockResolvedValue({ data: { data: held } })
    api.delete.mockRejectedValue({ response: { data: { message: 'Not allowed' } } })
    show()
    fireEvent.click(await screen.findByRole('button', { name: 'Remove face data' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Yes, delete it' }))
    expect((await screen.findByRole('alert')).textContent).toBe('Not allowed')
    expect(screen.getByText(/A face template is held/)).toBeTruthy()
  })
  it('hides itself when the status cannot be read', async () => {
    api.get.mockRejectedValue(new Error('403'))
    const { container } = show()
    await waitFor(() => expect(api.get).toHaveBeenCalled())
    expect(container.textContent).toBe('')
  })
})
