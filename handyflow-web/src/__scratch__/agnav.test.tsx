import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
afterEach(() => cleanup())

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
vi.mock('../hooks/usePermission', () => ({ usePermission: () => true }))

import { Sidebar } from '../components/shell/Sidebar'
import { MODULE_REGISTRY, type ModuleNavItem } from '../navigation/modules'
import AgriculturePage from '../pages/agriculture/AgriculturePage'

const qc = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })
beforeEach(() => {
  api.get.mockImplementation(async (url: string) => {
    if (url.endsWith('/farms')) return { data: { content: [{ id: 'f1', name: 'Green Valley', status: 'ACTIVE' }], totalElements: 1 } }
    if (url.includes('cost-summary')) return { data: [] }
    return { data: { content: [], totalElements: 0 } }
  })
})

describe('Agriculture sidebar context', () => {
  it('shows the Crops and Insights groups and highlights the current section', () => {
    const mods: ModuleNavItem[] = [{ key: 'agriculture', ...MODULE_REGISTRY.agriculture }]
    render(<QueryClientProvider client={qc()}><MemoryRouter initialEntries={['/agriculture/crop-cycles']}>
      <Sidebar modules={mods} pinnedKeys={[]} pathname="/agriculture/crop-cycles" mini={false} mobileOpen={false} onNavigate={() => {}} onToggleMini={() => {}} />
    </MemoryRouter></QueryClientProvider>)
    for (const t of ['Crops', 'Insights']) expect(screen.getAllByText(t).length).toBeGreaterThan(0)
    const hrefs = screen.getAllByRole('link').map(a => a.getAttribute('href'))
    for (const h of ['/agriculture/crop-cycles', '/agriculture/seasons', '/agriculture/crop-types', '/agriculture/costs', '/agriculture/farms']) expect(hrefs, h).toContain(h)
    const active = screen.getAllByRole('link').filter(a => a.className.includes('active')).map(a => a.textContent?.trim())
    expect(active).toEqual(['Crop cycles'])
  })
})

describe('AgriculturePage renders the new sections', () => {
  const at = (url: string) => render(<QueryClientProvider client={qc()}><MemoryRouter initialEntries={[url]}>
    <Routes><Route path="/agriculture/:section?" element={<AgriculturePage />} /></Routes></MemoryRouter></QueryClientProvider>)
  it.each([
    ['/agriculture/crop-cycles', /New crop cycle/],
    ['/agriculture/seasons', /New season/],
    ['/agriculture/crop-types', /Add crop type/],
    ['/agriculture/costs', /Export CSV/],
  ])('%s', async (url, marker) => {
    at(url); expect(await screen.findByRole('button', { name: marker })).toBeTruthy()
  })
  it('farm-scoped sections show the farm picker; crop types do not need one', async () => {
    at('/agriculture/seasons'); expect(await screen.findByLabelText('Farm')).toBeTruthy(); cleanup()
    at('/agriculture/crop-types'); await screen.findByRole('button', { name: /Add crop type/ }); expect(screen.queryByLabelText('Farm')).toBeNull()
  })
  it('existing sections still work', async () => {
    at('/agriculture/species'); await waitFor(() => expect(api.get).toHaveBeenCalled()); expect(screen.queryByText(/Something went wrong/)).toBeNull()
  })
})
