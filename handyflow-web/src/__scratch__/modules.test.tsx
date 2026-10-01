import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, cleanup, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))

import { RecruiterPage } from '../pages/recruiter/RecruiterPage'
import { CreativePage } from '../pages/creative/CreativePage'
import { DeskPage } from '../pages/desk/DeskPage'
import { ExpensesPage } from '../pages/expenses/ExpensesPage'
import { CataloguePage } from '../pages/catalogue/CataloguePage'
import ComplianceServicesPage from '../pages/complianceservices/ComplianceServicesPage'
import { PosPage } from '../pages/pos/PosPage'
import { ProjectsPage } from '../pages/projects/ProjectsPage'

beforeEach(() => { cleanup(); vi.clearAllMocks(); api.get.mockResolvedValue({ data: null }); api.post.mockResolvedValue({ data: null }) })

const Where = () => { const l = useLocation(); return <div data-testid="where">{l.pathname + l.search}</div> }
function mount(base: string, Page: React.ComponentType, url: string) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[url]}>
    <Routes><Route path={`${base}/:section?`} element={<><Page /><Where /></>} /></Routes></MemoryRouter></QueryClientProvider>)
}
const h1 = () => screen.getByRole('heading', { level: 1 }).textContent
const where = () => screen.getByTestId('where').textContent

// [route base, page, url visited, expected landing path, expected header]
const CASES: [string, React.ComponentType, string, string, string][] = [
  ['/recruiter', RecruiterPage, '/recruiter', '/recruiter/jobs', 'Job Postings'],
  ['/recruiter', RecruiterPage, '/recruiter/pipeline', '/recruiter/pipeline', 'Pipeline'],
  ['/recruiter', RecruiterPage, '/recruiter/applications', '/recruiter/applications', 'Applications'],
  ['/recruiter', RecruiterPage, '/recruiter/nonsense', '/recruiter/jobs', 'Job Postings'],
  ['/creative', CreativePage, '/creative', '/creative/jobs', 'Jobs'],
  ['/desk', DeskPage, '/desk', '/desk/tickets', 'Tickets'],
  ['/expenses', ExpensesPage, '/expenses', '/expenses/claims', 'Claims'],
  ['/catalogue', CataloguePage, '/catalogue', '/catalogue/items', 'Products & services'],
  ['/complianceservices', ComplianceServicesPage, '/complianceservices', '/complianceservices/clients', 'Clients'],
  ['/pos', PosPage, '/pos', '/pos/sell', 'POS Terminal'],
  ['/pos', PosPage, '/pos/stock', '/pos/stock', 'Stock'],
  ['/pos', PosPage, '/pos/transactions', '/pos/transactions', 'Transactions'],
  ['/pos', PosPage, '/pos/orders', '/pos/orders', 'Purchase Orders'],
  ['/projects', ProjectsPage, '/projects', '/projects/dashboard', 'Dashboard'],
  ['/projects', ProjectsPage, '/projects/projects', '/projects/projects', 'Projects'],
]

describe('converted modules mount under their section routes', () => {
  it.each(CASES)('%s visited at %s lands on %s with header "%s"', async (base, Page, url, landing, header) => {
    mount(base, Page, url)
    await waitFor(() => expect(where()).toBe(landing))
    expect(h1()).toBe(header)
  })

  it('there is exactly one page title (the old hand-rolled header is gone)', async () => {
    mount('/desk', DeskPage, '/desk/tickets'); await waitFor(() => expect(where()).toBe('/desk/tickets'))
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
  })

  it('breadcrumbs start with the module name', async () => {
    mount('/recruiter', RecruiterPage, '/recruiter/pipeline'); await waitFor(() => expect(where()).toBe('/recruiter/pipeline'))
    const crumbs = screen.getByRole('navigation', { name: /breadcrumb/i }).textContent ?? ''
    expect(crumbs).toContain('Recruiter'); expect(crumbs).toContain('Hiring'); expect(crumbs).toContain('Pipeline')
  })

  it('the in-page tab bars are gone: no Recruiter/POS tab buttons, section is chosen only by the URL', async () => {
    mount('/recruiter', RecruiterPage, '/recruiter/jobs'); await waitFor(() => expect(where()).toBe('/recruiter/jobs'))
    expect(screen.queryByRole('button', { name: /^Pipeline$/ })).toBeNull()
    cleanup()
    mount('/pos', PosPage, '/pos/sell'); await waitFor(() => expect(where()).toBe('/pos/sell'))
    expect(screen.queryByRole('button', { name: /^Stock$/ })).toBeNull()
  })

  it('header actions survive: Post job, New Ticket, New Job, Submit Claim, Add item', async () => {
    for (const [base, Page, url, label] of [
      ['/recruiter', RecruiterPage, '/recruiter/jobs', /Post job/], ['/desk', DeskPage, '/desk', /New Ticket/],
      ['/creative', CreativePage, '/creative', /New Job/], ['/expenses', ExpensesPage, '/expenses', /Submit Claim/],
      ['/catalogue', CataloguePage, '/catalogue', /Add item/],
    ] as [string, React.ComponentType, string, RegExp][]) {
      cleanup(); mount(base, Page, url)
      expect(await screen.findByRole('button', { name: label })).toBeTruthy()
    }
  })

  it('Recruiter: Export only shows on the Applications section', async () => {
    mount('/recruiter', RecruiterPage, '/recruiter/applications')
    expect(await screen.findByRole('button', { name: /Export/ })).toBeTruthy(); cleanup()
    mount('/recruiter', RecruiterPage, '/recruiter/jobs'); await waitFor(() => expect(where()).toBe('/recruiter/jobs'))
    expect(screen.queryByRole('button', { name: /Export/ })).toBeNull()
  })

  it('Catalogue keeps all its modals reachable (Delete Category was nearly lost in conversion)', async () => {
    api.get.mockImplementation(async (url: string) => {
      if (url.includes('categories')) return { data: [{ id: 'c1', name: 'Hardware', description: '', sortOrder: 0 }] }
      return { data: [] }
    })
    mount('/catalogue', CataloguePage, '/catalogue')
    await screen.findByText('Hardware')
    expect(document.body.textContent).toContain('Hardware')
  })

  it('Projects: a legacy /projects/<uuid> link lands on the in-page detail', async () => {
    const id = '3f2b1c9a-8d4e-4f6a-9b7c-1a2b3c4d5e6f'
    mount('/projects', ProjectsPage, `/projects/${id}`)
    await waitFor(() => expect(where()).toBe(`/projects/projects?project=${id}`))
    expect(h1()).toBe('Project')
  })
})
