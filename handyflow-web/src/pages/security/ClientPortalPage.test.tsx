// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))

import ClientPortalPage from './ClientPortalPage'

afterEach(() => cleanup())
beforeEach(() => vi.clearAllMocks())

function renderAt(path: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/site-portal/:token" element={<ClientPortalPage />} /></Routes></MemoryRouter></QueryClientProvider>)
}

describe('security ClientPortalPage', () => {
  it('reads the portal through the shared API client, not a bare axios call', async () => {
    api.get.mockRejectedValue({ response: { status: 404 } })
    renderAt('/site-portal/abc-123')
    expect(await screen.findByText('Portal not found')).toBeTruthy()
    expect(api.get).toHaveBeenCalledWith('/api/v1/portal/abc-123')
  })
})
