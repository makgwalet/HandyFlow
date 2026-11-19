// @vitest-environment jsdom
// The Contracts screen hides actions the server would refuse. This mounts the real screen with a draft contract and checks which
// buttons appear for each permission set. (The server still enforces every check; this only avoids showing buttons that would 403.)
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest'
import { createRoot, type Root } from 'react-dom/client'
import { act } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const perms = vi.hoisted(() => ({ granted: [] as string[], status: 'DRAFT' }))

vi.mock('../../hooks/usePermission', () => ({
  usePermissions: (needed: string[], mode: 'any' | 'all' = 'any') =>
    mode === 'all' ? needed.every(p => perms.granted.includes(p)) : needed.some(p => perms.granted.includes(p)),
  usePermission: (p: string) => perms.granted.includes(p),
}))

const contract = {
  id: 'c1', contractNumber: 'CTR-1', title: 'Service agreement', contractType: 'SERVICE_AGREEMENT', get status() { return perms.status }, body: '<p>x</p>',
  parties: [], comments: [], totalPartyCount: 0, signedPartyCount: 0,
}
vi.mock('../../api/client', () => ({
  apiClient: {
    get: vi.fn(async (url: string) => {
      if (url.startsWith('/api/v1/contracts/templates')) return { data: { data: [] } }
      if (url.startsWith('/api/v1/contracts/c1')) return { data: { data: contract } }
      return { data: { data: { content: [contract] } } }
    }),
    post: vi.fn(), put: vi.fn(),
  },
}))
// The expanded sections make their own requests and have their own tests.
vi.mock('./ContractRedlineSection', () => ({ ContractRedlineSection: () => null }))
vi.mock('./ContractRiskSection', () => ({ ContractRiskSection: () => null }))
vi.mock('./ContractNegotiationSection', () => ({ ContractNegotiationSection: () => null }))
vi.mock('./ContractSharingSection', () => ({ ContractSharingSection: () => null }))

import ContractsTab from './ContractsTab'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let root: Root | null = null
let host: HTMLElement | null = null
beforeEach(() => { perms.granted = []; perms.status = 'DRAFT' })
afterEach(() => { act(() => root?.unmount()); host?.remove(); root = null; host = null })

const wait = () => act(async () => { await new Promise(r => setTimeout(r, 20)) })

async function mount(granted: string[]) {
  perms.granted = granted
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await act(async () => { root!.render(<QueryClientProvider client={qc}><MemoryRouter><ContractsTab /></MemoryRouter></QueryClientProvider>) })
  await wait(); await wait()
  // Expand the contract card so its actions render.
  const row = host.querySelector('[data-testid="register-row"]') ?? Array.from(host.querySelectorAll('div')).find(d => d.textContent?.includes('Service agreement') && d.style.cursor === 'pointer')
  if (row) { await act(async () => { row.dispatchEvent(new MouseEvent('click', { bubbles: true })) }); await wait(); await wait() }
  return host
}

// Exact label match: 'Terminate' must not match the 'Terminated' view tab, nor 'Edit' something like 'Edited'.
const has = (el: HTMLElement, text: string) => Array.from(el.querySelectorAll('button')).some(b => b.textContent?.trim() === text)

// Party actions live on the Parties tab of the contract workspace.
async function openPartiesTab(el: HTMLElement) {
  const tab = Array.from(el.querySelectorAll('[role="tab"]')).find(t => t.textContent === 'Parties & signatures')
  if (!tab) throw new Error('Parties tab not found')
  await act(async () => { tab.dispatchEvent(new MouseEvent('click', { bubbles: true })) }); await wait()
}

describe('Contracts screen permissions', () => {
  it('shows no create or edit actions to a read-only user', async () => {
    const el = await mount(['CONTRACTS_READ'])
    expect(has(el, 'New Contract')).toBe(false)
    await openPartiesTab(el)
    expect(has(el, 'Add Party')).toBe(false)
    expect(has(el, 'Submit for Review')).toBe(false)
    expect(has(el, 'Edit')).toBe(false)
  })

  it('shows create, edit and submit to someone who can manage contracts', async () => {
    const el = await mount(['CONTRACTS_MANAGE'])
    expect(has(el, 'New Contract')).toBe(true)
    // The card is expanded, so the absence checks above are meaningful.
    await openPartiesTab(el)
    expect(has(el, 'Add Party')).toBe(true)
    expect(has(el, 'Submit for Review')).toBe(true)
    expect(has(el, 'Edit')).toBe(true)
  })

  it('shows Terminate on a signed contract only to an administrator', async () => {
    perms.status = 'SIGNED'
    expect(has(await mount(['CONTRACTS_MANAGE']), 'Terminate')).toBe(false)
    act(() => root?.unmount()); host?.remove()
    perms.status = 'SIGNED'
    expect(has(await mount(['CONTRACTS_ADMIN']), 'Terminate')).toBe(true)
  })
})
