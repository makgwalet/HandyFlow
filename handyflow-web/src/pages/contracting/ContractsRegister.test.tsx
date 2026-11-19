// @vitest-environment jsdom
// The register: saved views from the address, search, sorting and opening a contract from the table.
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest'
import { createRoot, type Root } from 'react-dom/client'
import { act } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { wait, click, type } from './testUtils'

vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))
const rows = [
  { id: '1', contractNumber: 'CTR-1', title: 'Alpha lease', contractType: 'LEASE', status: 'SENT', valueAmount: 100, endDate: '2027-03-01', totalPartyCount: 2, signedPartyCount: 1 },
  { id: '2', contractNumber: 'CTR-2', title: 'Beta supply', contractType: 'SUPPLY', status: 'DRAFT', valueAmount: 900, endDate: null, totalPartyCount: 0, signedPartyCount: 0 },
  { id: '3', contractNumber: 'CTR-3', title: 'Gamma NDA', contractType: 'NDA', status: 'TERMINATED', valueAmount: null, endDate: '2026-01-01', totalPartyCount: 2, signedPartyCount: 2 },
]
vi.mock('../../api/client', () => ({
  apiClient: {
    get: vi.fn(async (url: string) => {
      if (url.startsWith('/api/v1/contracts/templates')) return { data: { data: [] } }
      if (/\/api\/v1\/contracts\/\w+$/.test(url)) return { data: { data: rows.find(r => url.endsWith(`/${r.id}`)) } }
      return { data: { data: { content: rows } } }
    }),
    post: vi.fn(), put: vi.fn(),
  },
}))
vi.mock('./ContractRedlineSection', () => ({ ContractRedlineSection: () => null }))
vi.mock('./ContractRiskSection', () => ({ ContractRiskSection: () => null }))
vi.mock('./ContractNegotiationSection', () => ({ ContractNegotiationSection: () => null }))
vi.mock('./ContractSharingSection', () => ({ ContractSharingSection: () => null }))

import ContractsTab from './ContractsTab'
;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let root: Root | null = null
let host: HTMLElement | null = null
beforeEach(() => { vi.clearAllMocks() })
afterEach(() => { act(() => root?.unmount()); host?.remove(); root = null; host = null })

async function mount(url = '/contracts/contracts') {
  host = document.createElement('div'); document.body.appendChild(host)
  root = createRoot(host)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await act(async () => { root!.render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[url]}><ContractsTab /></MemoryRouter></QueryClientProvider>) })
  await wait(); await wait()
  return host
}
const titles = (el: HTMLElement) => Array.from(el.querySelectorAll('[data-testid="register-row"]')).map(r => r.querySelector('div')!.textContent)
const tab = (el: HTMLElement, label: string) => Array.from(el.querySelectorAll('[role="tab"]')).find(t => t.textContent?.startsWith(label)) as HTMLElement

describe('contract register', () => {
  it('shows every contract in a table by default, with a count on each view', async () => {
    const el = await mount()
    expect(titles(el)).toHaveLength(3)
    expect(tab(el, 'Drafts').textContent).toContain('1')
    expect(tab(el, 'All contracts').getAttribute('aria-selected')).toBe('true')
  })

  it('opens on the view named in the address, so a dashboard figure shows exactly those contracts', async () => {
    const el = await mount('/contracts/contracts?view=awaiting')
    expect(titles(el)).toEqual(['Alpha lease'])
    expect(tab(el, 'Awaiting signature').getAttribute('aria-selected')).toBe('true')
  })

  it('ignores a view it does not know', async () => {
    expect(titles(await mount('/contracts/contracts?view=bogus'))).toHaveLength(3)
  })

  it('switches view from the tabs', async () => {
    const el = await mount()
    await click(tab(el, 'Terminated'))
    expect(titles(el)).toEqual(['Gamma NDA'])
  })

  it('searches and says so, with a way out, when nothing matches', async () => {
    const el = await mount()
    await type(el.querySelector('input[placeholder="Search contracts…"]'), 'beta')
    expect(titles(el)).toEqual(['Beta supply'])
    await type(el.querySelector('input[placeholder="Search contracts…"]'), 'zzz')
    expect(el.textContent).toContain('No contracts match')
    const clear = Array.from(el.querySelectorAll('button')).find(b => b.textContent === 'Clear filters')
    await click(clear)
    expect(titles(el)).toHaveLength(3)
  })

  it('filters by type', async () => {
    const el = await mount()
    await type(el.querySelector('select[aria-label="Contract type"]'), 'SUPPLY')
    expect(titles(el)).toEqual(['Beta supply'])
  })

  it('sorts by a column and reverses on a second click', async () => {
    const el = await mount()
    const header = (label: string) => Array.from(el.querySelectorAll('th button')).find(b => b.textContent === label) as HTMLElement
    await click(header('Contract'))
    expect(titles(el)).toEqual(['Alpha lease', 'Beta supply', 'Gamma NDA'])
    await click(header('Contract'))
    expect(titles(el)).toEqual(['Gamma NDA', 'Beta supply', 'Alpha lease'])
    await click(header('Value'))
    expect(titles(el)[0]).toBe('Beta supply')
  })

  it('opens a contract from its row, and the row can be used from the keyboard', async () => {
    const el = await mount()
    const first = el.querySelector('[data-testid="register-row"]') as HTMLElement
    expect(first.getAttribute('tabindex')).toBe('0')
    await click(first)
    expect(el.querySelector('[data-testid="register-row"][aria-expanded="true"]')).not.toBeNull()
    expect(el.textContent).toContain('Start date')
  })

  it('switches to cards and back', async () => {
    const el = await mount()
    await click(el.querySelector('button[aria-label="Show as cards"]'))
    expect(el.querySelector('table')).toBeNull()
    expect(el.textContent).toContain('Alpha lease')
    await click(el.querySelector('button[aria-label="Show as table"]'))
    expect(el.querySelector('table')).not.toBeNull()
  })
})
