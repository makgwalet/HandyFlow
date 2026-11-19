// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ApprovalsBell, { badgeCount } from './ApprovalsBell'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))
afterEach(() => { cleanup(); api.get.mockReset() })

const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const item = (id: string, canApprove: boolean) => ({ id, name: `C${id}`, version: 1, submittedAt: '2026-11-19T10:00:00Z', canApprove })

describe('approvals bell', () => {
  it('badge counts only what the user can approve', () => {
    expect(badgeCount([item('1', true), item('2', false), item('3', true)])).toBe(2)
    expect(badgeCount([])).toBe(0)
  })

  it('shows the badge, lists the campaigns and opens the campaigns tab on click', async () => {
    api.get.mockResolvedValue({ data: { data: [item('1', true), item('2', false)] } })
    const onOpen = vi.fn()
    wrap(<ApprovalsBell onOpen={onOpen} />)
    await waitFor(() => expect(screen.getByLabelText(/1 waiting for you/i)).toBeTruthy())
    fireEvent.click(screen.getByLabelText(/1 waiting for you/i))
    expect(screen.getByText('C2')).toBeTruthy()
    expect(screen.getByText(/waiting for an approver/i)).toBeTruthy()
    fireEvent.click(screen.getByText('C1'))
    expect(onOpen).toHaveBeenCalledTimes(1)
  })

  it('says so when nothing is waiting', async () => {
    api.get.mockResolvedValue({ data: { data: [] } })
    wrap(<ApprovalsBell onOpen={() => {}} />)
    fireEvent.click(screen.getByRole('button'))
    expect(screen.getByText(/nothing is waiting/i)).toBeTruthy()
  })
})
