import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, waitFor, cleanup } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

afterEach(() => cleanup())

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))

import { Sidebar } from '../components/shell/Sidebar'
import { MODULE_REGISTRY, WORKSPACE_NAV, activeModuleFor, isNavItemActive, useSubscribedModules, type ModuleNavItem } from '../navigation/modules'

const mods = (...keys: string[]): ModuleNavItem[] => keys.map(key => ({ key, ...MODULE_REGISTRY[key] }))
const renderSidebar = (pathname: string, keys: string[]) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[pathname]}>
    <Sidebar modules={mods(...keys)} pinnedKeys={[]} pathname={pathname} mini={false} mobileOpen={false} onNavigate={() => {}} onToggleMini={() => {}} />
  </MemoryRouter></QueryClientProvider>)
}
const link = (name: string) => screen.getByRole('link', { name: new RegExp(`^${name}`) })
const activeLinks = () => screen.getAllByRole('link').filter(a => a.className.includes('active')).map(a => a.textContent?.trim())

describe('alias-aware highlighting', () => {
  it('Invoices is highlighted on its own route AND on /recurring (the Recurring tab)', () => {
    for (const p of ['/invoices', '/recurring', '/recurring/new', '/recurring/variable-hours/new']) {
      const { unmount } = renderSidebar(p, ['crm', 'invoicing', 'tasks'])
      expect(activeLinks(), p).toEqual(['Invoices']); unmount()
    }
  })
  it('does not highlight Invoices elsewhere, nor match lookalike prefixes', () => {
    const { unmount } = renderSidebar('/customers', ['crm', 'invoicing', 'tasks']); expect(activeLinks()).toEqual(['Customers']); unmount()
    expect(isNavItemActive('/recurringly', { route: '/invoices', aliases: ['/recurring'] })).toBe(false)
  })
  it('activeModuleFor resolves /recurring to invoicing', () => {
    expect(activeModuleFor('/recurring/new', mods('crm', 'invoicing'))?.key).toBe('invoicing')
  })
})

describe('newly registered modules', () => {
  it.each([['bookingagency', 'Booking Agency', '/booking-agency'], ['payrollbureau', 'Payroll Bureau', '/payroll-bureau'], ['recruitmentagency', 'Recruitment Agency', '/recruitment-agency']])(
    '%s appears in the sidebar, links to its page and highlights on it', (key, label, route) => {
      const { unmount } = renderSidebar(route, ['crm', key])
      expect(link(label).getAttribute('href')).toBe(route)
      expect(activeLinks()).toEqual([label]); unmount()
    })
  it('a subscribed tenant gets them from useSubscribedModules (was silently dropped)', async () => {
    api.get.mockResolvedValue({ data: [{ moduleKey: 'payrollbureau', accessible: true }, { moduleKey: 'recruitmentagency', accessible: true },
      { moduleKey: 'bookingagency', accessible: false }, { moduleKey: 'not_a_module', accessible: true }] })
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    let got: string[] = []
    const Probe = () => { got = useSubscribedModules().modules.map(m => m.key); return null }
    render(<QueryClientProvider client={qc}><Probe /></QueryClientProvider>)
    await waitFor(() => expect(got).toContain('payrollbureau'))
    expect(got).toEqual(['crm', 'catalogue', 'payrollbureau', 'recruitmentagency'])   // not-accessible and unknown keys stay out
  })
  it('Control Exceptions is a workspace link, shown to everyone', () => {
    expect(WORKSPACE_NAV.map(w => w.route)).toContain('/control-exceptions')
    const { unmount } = renderSidebar('/control-exceptions', ['crm']); expect(activeLinks()).toEqual(['Control Exceptions']); unmount()
  })
})

// What the user asked for: these modules show the same sidebar as the rest ("All modules", the module
// name, then its sections) instead of the flat module list.
describe('context sidebar for the nine converted modules', () => {
  const NINE: [string, string, string, string, string[]][] = [
    // key, path, module title, group, section labels
    ['recruiter', '/recruiter/pipeline', 'Recruiter', 'Hiring', ['Job Postings', 'Pipeline', 'Applications']],
    ['creative', '/creative/jobs', 'Creative', 'Overview', ['Jobs']],
    ['catalogue', '/catalogue/items', 'Catalogue', 'Overview', ['Products & services']],
    ['complianceservices', '/complianceservices/clients', 'Compliance Services', 'Overview', ['Clients']],
    ['desk', '/desk/tickets', 'Desk Support', 'Overview', ['Tickets']],
    ['expenses', '/expenses/claims', 'Expenses', 'Overview', ['Claims']],
    ['pos', '/pos/stock', 'POS & Stock', 'Inventory', ['POS Terminal', 'Transactions', 'Stock', 'Purchase Orders']],
    ['projects', '/projects/dashboard', 'Projects', 'Overview', ['Dashboard', 'Projects']],
    ['tasks', '/tasks/boards', 'Tasks', 'Overview', ['Boards', 'My tasks']],
  ]
  it.each(NINE)('%s: %s shows All modules, "%s", group "%s" and its sections', (key, path, title, group, labels) => {
    renderSidebar(path, ['crm', key])
    expect(screen.getByRole('button', { name: /All modules/ })).toBeTruthy()
    expect(document.querySelector('.hf-nav-context-title')?.textContent).toBe(title)
    expect(screen.getByRole('group', { name: group })).toBeTruthy()
    for (const l of labels) expect(link(l).getAttribute('href')).toMatch(new RegExp('^' + path.split('/').slice(0, 2).join('/') + '/'))
    expect(screen.queryByRole('link', { name: /^Customers/ })).toBeNull()      // the flat module list is replaced
  })
  it('the current section is highlighted', () => {
    renderSidebar('/pos/stock', ['crm', 'pos'])
    expect(activeLinks()).toEqual(['Stock'])
  })
  it('a section deep inside the module (client detail) keeps the module sidebar', () => {
    renderSidebar('/complianceservices/clients/123', ['crm', 'complianceservices'])
    expect(document.querySelector('.hf-nav-context-title')?.textContent).toBe('Compliance Services')
  })
})
