// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ClientCasesTab from './ClientCasesTab'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('../../api/client', () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock('../../hooks/usePermission', () => ({ usePermissions: () => true, usePermission: () => true }))
vi.mock('../../hooks/useAuth', () => ({ useAuth: () => ({ user: { userId: 'me' } }) }))

const summary = (over: object = {}) => ({
  id: 'k1', title: 'Appoint an information officer', severity: 'HIGH', status: 'IN_PROGRESS', ownerId: 'me', dueDate: null, assessmentId: null,
  obligationId: null, obligationCode: null, openSteps: 1, totalSteps: 1, createdAt: '2026-11-20T08:00:00Z', ...over,
})
const detail = (over: object = {}, sum: object = {}) => ({
  summary: summary(sum), description: null, resolutionNote: null, resolvedBy: null, resolvedAt: null, closedBy: null, closedAt: null,
  closeExceptionReason: null, cancelReason: null,
  steps: [{ id: 's1', description: 'Collect the ID copy', status: 'TODO', dueDate: null, doneBy: null, doneAt: null, cancelReason: null }],
  events: [{ event: 'OPENED', fromStatus: null, toStatus: 'OPEN', note: null, actor: 'me', occurredAt: '2026-11-20T08:00:00Z' }], ...over,
})

function renderIt() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ClientCasesTab clientId="c1" /></QueryClientProvider>)
}

describe('ClientCasesTab', () => {
  afterEach(cleanup)
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset()
    get.mockImplementation(async (url: string) => {
      if (url.includes('/identity/users')) return { data: { data: [] } }
      if (url.endsWith('/cases')) return { data: { data: [summary()] } }
      return { data: { data: detail() } }
    })
  })

  it('lists cases with owner and progress', async () => {
    renderIt()
    expect(await screen.findByText('Appoint an information officer')).toBeTruthy()
    expect(screen.getByText(/You · No due date · 0 of 1 steps done/)).toBeTruthy()
  })

  it('holds back Resolve while a step is open, and explains why', async () => {
    renderIt()
    fireEvent.click(await screen.findByText('Appoint an information officer'))
    const resolve = await screen.findByRole('button', { name: 'Resolve' })
    expect((resolve as HTMLButtonElement).disabled).toBe(true)
    expect(resolve.getAttribute('title')).toMatch(/1 step is still open/)
  })

  it('completes a step', async () => {
    post.mockResolvedValue({ data: { data: detail({ steps: [{ id: 's1', description: 'Collect the ID copy', status: 'DONE', dueDate: null, doneBy: 'me', doneAt: 'x', cancelReason: null }] }, { openSteps: 0 }) } })
    renderIt()
    fireEvent.click(await screen.findByText('Appoint an information officer'))
    fireEvent.click(await screen.findByRole('button', { name: 'Done' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/cases/k1/steps/s1/complete', {}))
  })

  it('needs a real note before resolving, then sends the action', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.includes('/identity/users')) return { data: { data: [] } }
      if (url.endsWith('/cases')) return { data: { data: [summary({ openSteps: 0 })] } }
      return { data: { data: detail({ steps: [] }, { openSteps: 0, totalSteps: 0 }) } }
    })
    post.mockResolvedValue({ data: { data: detail({ steps: [] }, { status: 'RESOLVED', openSteps: 0 }) } })
    renderIt()
    fireEvent.click(await screen.findByText('Appoint an information officer'))
    const resolve = await screen.findByRole('button', { name: 'Resolve' })
    expect((resolve as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Note'), { target: { value: 'Officer appointed and registered' } })
    expect((resolve as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(resolve)
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/cases/k1/transition', { action: 'RESOLVE', note: 'Officer appointed and registered' }))
  })

  it('asks the person who resolved a case for a reason before they close it', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.includes('/identity/users')) return { data: { data: [] } }
      if (url.endsWith('/cases')) return { data: { data: [summary({ status: 'RESOLVED', openSteps: 0 })] } }
      return { data: { data: detail({ steps: [], resolutionNote: 'Officer appointed', resolvedBy: 'me' }, { status: 'RESOLVED', openSteps: 0 }) } }
    })
    renderIt()
    fireEvent.click(await screen.findByText('Appoint an information officer'))
    const close = await screen.findByRole('button', { name: 'Close' })
    expect((close as HTMLButtonElement).disabled).toBe(true)
    expect(close.getAttribute('title')).toMatch(/someone else/)
  })

  it('opens a case from the form', async () => {
    post.mockResolvedValue({ data: { data: detail() } })
    renderIt()
    fireEvent.click(await screen.findByRole('button', { name: 'Open case' }))
    const create = screen.getByRole('button', { name: 'Open case' })
    expect((create as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'Fix the register' } })
    fireEvent.click(screen.getByRole('button', { name: 'Open case' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/v1/compliance-services/clients/c1/cases', expect.objectContaining({ title: 'Fix the register', severity: 'MEDIUM', ownerId: 'me' })))
  })

  it('shows the server message when a move is refused', async () => {
    get.mockImplementation(async (url: string) => {
      if (url.includes('/identity/users')) return { data: { data: [] } }
      if (url.endsWith('/cases')) return { data: { data: [summary({ status: 'OPEN', openSteps: 0 })] } }
      return { data: { data: detail({ steps: [] }, { status: 'OPEN', openSteps: 0 }) } }
    })
    post.mockRejectedValue({ response: { data: { message: 'Give the case an owner before starting work on it' } } })
    renderIt()
    fireEvent.click(await screen.findByText('Appoint an information officer'))
    fireEvent.click(await screen.findByRole('button', { name: 'Start work' }))
    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toMatch(/owner/)
  })
})
