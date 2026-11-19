import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ConfirmPage } from './PublicMarketingPages'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))

const renderConfirm = () => render(
  <MemoryRouter initialEntries={['/confirm/tok123']}>
    <Routes><Route path="/confirm/:token" element={<ConfirmPage />} /></Routes>
  </MemoryRouter>)

beforeEach(() => { api.get.mockReset(); api.post.mockReset(); api.put.mockReset() })
afterEach(cleanup)

describe('double opt-in confirm page', () => {
  it('does NOT confirm on page load: mail scanners prefetch links, so only a click may subscribe', async () => {
    renderConfirm()
    await new Promise(r => setTimeout(r, 30))
    expect(api.post).not.toHaveBeenCalled()
    expect(api.get).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /confirm subscription/i })).toBeTruthy()
  })

  it('POSTs the token when the person presses the button, then shows success and the preferences link', async () => {
    api.post.mockResolvedValue({ data: { data: { businessName: 'Acme', alreadyConfirmed: false, preferencesToken: 'pref456' } } })
    renderConfirm()
    fireEvent.click(screen.getByRole('button', { name: /confirm subscription/i }))
    await waitFor(() => expect(screen.getByText(/you’re subscribed/i)).toBeTruthy())
    expect(api.post).toHaveBeenCalledTimes(1)
    expect(api.post).toHaveBeenCalledWith('/api/v1/marketing/public/confirm/tok123')
    expect(screen.getByText(/Acme/)).toBeTruthy()
    expect(screen.getByRole('link').getAttribute('href')).toBe('/preferences/pref456')
  })

  it('says so when the address was already confirmed (a double-click or a second visit)', async () => {
    api.post.mockResolvedValue({ data: { data: { businessName: 'Acme', alreadyConfirmed: true } } })
    renderConfirm()
    fireEvent.click(screen.getByRole('button', { name: /confirm subscription/i }))
    await waitFor(() => expect(screen.getByText(/already confirmed/i)).toBeTruthy())
  })

  it('shows the server message for an expired or suppressed link and lets the person retry', async () => {
    api.post.mockRejectedValue({ response: { data: { message: 'This confirmation link has expired. Please sign up again.' } } })
    renderConfirm()
    fireEvent.click(screen.getByRole('button', { name: /confirm subscription/i }))
    await waitFor(() => expect(screen.getByText(/has expired/i)).toBeTruthy())
    expect((screen.getByRole('button', { name: /confirm subscription/i }) as HTMLButtonElement).disabled).toBe(false)
  })

  it('falls back to a generic message when the server gives none', async () => {
    api.post.mockRejectedValue(new Error('network'))
    renderConfirm()
    fireEvent.click(screen.getByRole('button', { name: /confirm subscription/i }))
    await waitFor(() => expect(screen.getByText(/could not be confirmed/i)).toBeTruthy())
  })
})
