// @vitest-environment jsdom
// Clause review evidence: attach, replace and download, and which clauses offer it.
import { describe, it, expect, afterEach, vi, beforeEach } from 'vitest'
import { createRoot, type Root } from 'react-dom/client'
import { act } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { wait, click, button, hasButton } from './testUtils'

const state = vi.hoisted(() => ({ granted: ['CONTRACTS_MANAGE'] as string[], clauses: [] as unknown[] }))
vi.mock('../../hooks/usePermission', () => ({
  usePermissions: (needed: string[]) => needed.some(p => state.granted.includes(p)),
  usePermission: (p: string) => state.granted.includes(p),
}))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
vi.mock('./RichTextEditor', () => ({ RichTextEditor: () => null }))

import ClausesTab from './ClausesTab'
;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

const clause = (over: Record<string, unknown>) => ({
  id: 'c1', familyId: 'f1', versionNo: 1, title: 'Domicilium', category: 'DOMICILIUM', body: '<p>x</p>', jurisdiction: 'South Africa',
  status: 'APPROVED', legalStatus: 'REVIEWED', reviewerName: 'A Attorney', reviewerProfessionalDetails: 'Admitted attorney', reviewDate: '2026-11-01',
  usable: false, evidenceDocumentId: null, evidenceFileName: null, ...over,
})

let root: Root | null = null
let host: HTMLElement | null = null
beforeEach(() => {
  state.granted = ['CONTRACTS_MANAGE']; state.clauses = []
  api.get.mockReset(); api.post.mockReset()
  api.get.mockImplementation(async (url: string) => url.endsWith('/evidence') ? { data: new Blob(['x']) } : { data: { data: state.clauses } })
  api.post.mockResolvedValue({ data: { data: {} } })
  Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:x'), revokeObjectURL: vi.fn() })
})
afterEach(() => { act(() => root?.unmount()); host?.remove(); root = null; host = null })

async function mount() {
  host = document.createElement('div'); document.body.appendChild(host)
  root = createRoot(host)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  await act(async () => { root!.render(<QueryClientProvider client={qc}><ClausesTab /></QueryClientProvider>) })
  await wait()
  return host
}

const fileInputs = (el: HTMLElement) => Array.from(el.querySelectorAll('input[type="file"]')) as HTMLInputElement[]
async function pick(input: HTMLInputElement, file: File) {
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  await act(async () => { input.dispatchEvent(new Event('change', { bubbles: true })) })
  await wait()
}

describe('clause review evidence', () => {
  it('offers to attach evidence to an approved clause that has none, and says so', async () => {
    state.clauses = [clause({})]
    const el = await mount()
    expect(el.textContent).toContain('none attached')
    expect(el.textContent).toContain('Attach evidence')
  })

  it('does not offer evidence on a draft, which has no review yet', async () => {
    state.clauses = [clause({ status: 'DRAFT', legalStatus: 'NOT_REVIEWED' })]
    const el = await mount()
    expect(el.textContent).not.toContain('Attach evidence')
    expect(el.textContent).not.toContain('none attached')
  })

  it('uploads the chosen file to the clause evidence endpoint as a multipart form', async () => {
    state.clauses = [clause({})]
    const el = await mount()
    await pick(fileInputs(el)[0], new File(['pdf'], 'review.pdf', { type: 'application/pdf' }))
    const call = api.post.mock.calls.find(c => c[0] === '/api/v1/contracts/clauses/c1/evidence')
    expect(call).toBeTruthy()
    expect(call![1]).toBeInstanceOf(FormData)
    expect((call![1] as FormData).get('file')).toBeInstanceOf(File)
  })

  it('shows the stored file with a download and a replacement upload', async () => {
    state.clauses = [clause({ status: 'PUBLISHED', evidenceDocumentId: 'd1', evidenceFileName: 'review.pdf' })]
    const el = await mount()
    expect(hasButton(el, 'Download review.pdf')).toBe(true)
    expect(el.textContent).toContain('Upload replacement')
    await click(button(el, 'Download review.pdf'))
    expect(api.get).toHaveBeenCalledWith('/api/v1/contracts/clauses/c1/evidence', { responseType: 'blob' })
  })

  it('shows the evidence and hides the upload from someone who can only read', async () => {
    state.granted = ['CONTRACTS_READ']
    state.clauses = [clause({ evidenceDocumentId: 'd1', evidenceFileName: 'review.pdf' })]
    const el = await mount()
    expect(hasButton(el, 'Download review.pdf')).toBe(true)
    expect(el.textContent).not.toContain('Upload replacement')
    expect(fileInputs(el)).toHaveLength(0)
  })

  it('records the approval first and then stores the file; a failed file keeps the approval and says how to retry', async () => {
    state.clauses = [clause({ status: 'DRAFT', legalStatus: 'NOT_REVIEWED' })]
    api.post.mockImplementation(async (url: string) => {
      if (url.endsWith('/evidence')) throw { response: { data: { message: 'File type not allowed' } } }
      return { data: { data: {} } }
    })
    const el = await mount()
    await click(button(el, 'Record legal review'))
    const set = (label: string, v: string) => {
      const input = Array.from(document.querySelectorAll('label')).find(l => l.textContent?.startsWith(label))?.parentElement?.querySelector('input') as HTMLInputElement
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(input, v)
      return act(async () => { input.dispatchEvent(new Event('input', { bubbles: true })) })
    }
    await set('Reviewer name', 'A Attorney')
    await set('Professional details', 'Admitted attorney')
    await pick(document.querySelector('[aria-label="Review evidence file"]') as HTMLInputElement, new File(['x'], 'r.exe'))
    await click(button(document.body, 'Record approval'))
    const urls = api.post.mock.calls.map(c => c[0])
    expect(urls[0]).toBe('/api/v1/contracts/clauses/c1/approve')
    expect(urls[1]).toBe('/api/v1/contracts/clauses/c1/evidence')
    expect(document.body.textContent).toContain('The approval was recorded, but the evidence file was not stored: File type not allowed')
  })

  it('turns Publish off, and says why, for a clause that needs evidence and has none', async () => {
    state.clauses = [clause({ clauseKind: 'CUSTOM', evidenceRequired: true, evidenceRequiredReason: 'Custom wording needs evidence of the legal review.' })]
    const el = await mount()
    expect(button(el, 'Publish')!.disabled).toBe(true)
    expect(el.textContent).toContain('Custom wording needs evidence of the legal review.')
    expect(el.textContent).toContain('Custom wording')
  })

  it('leaves Publish on for ordinary wording, and for required evidence that is attached', async () => {
    state.clauses = [clause({}), clause({ id: 'c2', title: 'Other', clauseKind: 'REGULATORY', evidenceRequired: true, evidenceDocumentId: 'd1', evidenceFileName: 'r.pdf' })]
    const el = await mount()
    const publish = Array.from(el.querySelectorAll('button')).filter(b => b.textContent === 'Publish') as HTMLButtonElement[]
    expect(publish).toHaveLength(2)
    expect(publish.every(b => !b.disabled)).toBe(true)
  })

  it('offers to ask for approval when publishing is stopped for want of one, and sends the request', async () => {
    state.clauses = [clause({})]
    api.post.mockImplementation(async (url: string) => {
      if (url.endsWith('/publish')) throw { response: { data: { message: "This needs a second person's approval before it can go ahead." } } }
      return { data: { data: {} } }
    })
    const el = await mount()
    await click(button(el, 'Publish'))
    await click(button(el, 'Ask for approval'))
    expect(api.post).toHaveBeenCalledWith('/api/v1/contracts/approvals', { action: 'PUBLISH_CLAUSE', subjectId: 'c1', note: null })
  })
})
