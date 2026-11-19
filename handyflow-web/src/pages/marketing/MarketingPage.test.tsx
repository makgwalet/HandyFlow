// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { MarketingPage } from './MarketingPage'
import { MARKETING_SECTIONS } from '../../navigation/moduleSections'

vi.mock('../../api/client', () => ({ apiClient: { get: vi.fn().mockResolvedValue({ data: { data: {} } }) } }))
vi.mock('./DashboardTab', () => ({ default: () => <div>dashboard-tab</div> }))
vi.mock('./CampaignsTab', () => ({ default: () => <div>campaigns-tab</div> }))
vi.mock('./TemplatesTab', () => ({ default: () => <div>templates-tab</div> }))
vi.mock('./ContactsTab', () => ({ default: () => <div>contacts-tab</div> }))
vi.mock('./AudiencesTab', () => ({ default: () => <div>segments-tab</div> }))
vi.mock('./JourneysTab', () => ({ default: () => <div>automation-tab</div> }))
vi.mock('./AnalyticsTab', () => ({ default: () => <div>reports-tab</div> }))
vi.mock('./SettingsTab', () => ({ default: () => <div>settings-tab</div> }))
vi.mock('./ApprovalsBell', () => ({ default: () => <div>approvals-bell</div> }))

function at(path: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path="/marketing/:section?" element={<MarketingPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>)
}

describe('MarketingPage sections', () => {
  afterEach(cleanup)

  it('shows the section named in the URL', () => {
    at('/marketing/contacts')
    expect(screen.getByText('contacts-tab')).toBeTruthy()
    expect(screen.queryByText('dashboard-tab')).toBeNull()
  })

  it('opens on the dashboard when no section is given', () => {
    at('/marketing')
    expect(screen.getByText('dashboard-tab')).toBeTruthy()
  })

  it('sends an unknown or old section id to the dashboard', () => {
    at('/marketing/analytics')
    expect(screen.getByText('dashboard-tab')).toBeTruthy()
  })

  it('every menu item opens a screen', () => {
    for (const g of MARKETING_SECTIONS.groups) for (const s of g.sections) {
      at(`/marketing/${s.id}`)
      expect(screen.getByText(`${s.id}-tab`), `section ${s.id}`).toBeTruthy()
      cleanup()
    }
  })
})
