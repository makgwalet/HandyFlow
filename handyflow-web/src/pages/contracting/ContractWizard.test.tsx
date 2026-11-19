// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot } from 'react-dom/client'
import { act } from 'react'
import { ContractWizard } from './ContractWizard'
import { wait, click, type, button, hasButton } from './testUtils'

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
const post = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { post: (...a: unknown[]) => post(...a) } }))

const templates = [
  { id: 't1', name: 'Standard NDA', contractType: 'NDA', variables: { client_name: 'text' }, status: 'PUBLISHED', legalStatus: 'APPROVED', legalArea: 'Contract law' },
  { id: 't2', name: 'Old lease', contractType: 'LEASE', status: 'PUBLISHED', legalStatus: 'EXPIRED' },
]

async function mount(onClose = vi.fn(), onCreated = vi.fn()) {
  const host = document.createElement('div'); document.body.appendChild(host)
  const root = createRoot(host)
  await act(async () => {
    root.render(<QueryClientProvider client={new QueryClient()}><ContractWizard templates={templates} onClose={onClose} onCreated={onCreated} /></QueryClientProvider>)
  })
  return { host, onClose, onCreated }
}
const field = (host: HTMLElement, label: string) => host.querySelector(`[aria-label="${label}"]`) as HTMLInputElement

describe('ContractWizard', () => {
  beforeEach(() => { post.mockReset(); document.body.innerHTML = '' })

  it('blocks Next without a title and then advances', async () => {
    const { host } = await mount()
    await click(button(host, 'Next'))
    expect(host.textContent).toContain('Give the contract a title.')
    await type(field(host, 'Title'), 'Acme MSA')
    await click(button(host, 'Next'))
    expect(host.querySelector('[aria-current="step"]')?.textContent).toContain('Parties')
  })

  it('shows Upload and Copy as unavailable', async () => {
    const { host } = await mount()
    expect(button(host, 'Upload document')!.disabled).toBe(true)
    expect(button(host, 'Copy existing')!.disabled).toBe(true)
  })

  it('hides value and renewal for an NDA', async () => {
    const { host } = await mount()
    await click(button(host, 'From template'))
    const sel = host.querySelector('select[aria-label="Template"]') as HTMLSelectElement
    await act(async () => { sel.value = 't1'; sel.dispatchEvent(new Event('change', { bubbles: true })) })
    await type(field(host, 'Title'), 'NDA')
    await click(button(host, 'Next')); await click(button(host, 'Next'))
    expect(field(host, 'Value')).toBeNull()
    expect(host.textContent).not.toContain('Auto-renew')
    expect(field(host, 'client name')).not.toBeNull()
  })

  it('disables templates that cannot be used and shows the legal review state', async () => {
    const { host } = await mount()
    await click(button(host, 'From template'))
    const opts = Array.from(host.querySelectorAll('select[aria-label="Template"] option')) as HTMLOptionElement[]
    expect(opts.find(o => o.value === 't2')?.disabled).toBe(true)
    expect(opts.find(o => o.value === 't2')?.textContent).toContain('legal review expired')
    expect(opts.find(o => o.value === 't1')?.disabled).toBe(false)
    const sel = host.querySelector('select[aria-label="Template"]') as HTMLSelectElement
    await act(async () => { sel.value = 't1'; sel.dispatchEvent(new Event('change', { bubbles: true })) })
    expect(host.querySelector('[aria-label="Template legal review"]')?.textContent).toContain('Legal review recorded (Contract law)')
  })

  it('creates the draft and its parties, and reports a party that failed', async () => {
    post.mockImplementation(async (url: string, body: { fullName?: string }) => {
      if (url === '/api/v1/contracts') return { data: { data: { id: 'c1' } } }
      if (body.fullName === 'Bad') throw new Error('x')
      return { data: {} }
    })
    const { host, onCreated } = await mount()
    await type(field(host, 'Title'), 'Deal')
    await click(button(host, 'Next'))
    await click(button(host, 'Add party')); await click(button(host, 'Add party'))
    await type(field(host, 'Party 1 name'), 'Good')
    await type(field(host, 'Party 2 name'), 'Bad')
    await click(button(host, 'Next')); await click(button(host, 'Next'))
    expect(hasButton(host, 'Create draft')).toBe(true)
    await click(button(host, 'Create draft')); await wait()
    expect(post.mock.calls.map(c => c[0])).toEqual(['/api/v1/contracts', '/api/v1/contracts/c1/parties', '/api/v1/contracts/c1/parties'])
    expect(onCreated).toHaveBeenCalledWith('c1')
    expect(host.textContent).toContain('Draft saved')
    expect(host.textContent).toContain('could not be added: Bad')
  })

  it('Save draft jumps back to the step with the problem', async () => {
    const { host } = await mount()
    await click(button(host, 'Save draft'))
    expect(post).not.toHaveBeenCalled()
    expect(host.textContent).toContain('Give the contract a title.')
  })
})
