// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))

import ArmouryTab from './ArmouryTab'
import { useAuthStore } from '../../store/auth.store'

afterEach(() => cleanup())
beforeEach(() => {
  vi.clearAllMocks()
  useAuthStore.setState({ user: { permissions: ['SECURITY_MANAGE'] } } as never)
  api.get.mockResolvedValue({ data: { data: { content: [], last: true } } })
  api.post.mockResolvedValue({ data: {} })
})

async function openForm() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(<QueryClientProvider client={qc}><ArmouryTab /></QueryClientProvider>)
  fireEvent.click(await screen.findByRole('button', { name: /register firearm|add firearm|register/i }))
  await screen.findByText('Register Firearm')
  return view
}
const fill = (container: HTMLElement, type: string) => {
  const [serial, , license] = screen.getAllByRole('textbox') as HTMLInputElement[]
  fireEvent.change(serial, { target: { value: 'SN-77' } })
  fireEvent.change(license, { target: { value: 'LIC-1' } })
  fireEvent.change(container.querySelector('input[type=date]') as HTMLInputElement, { target: { value: '2030-01-01' } })
  fireEvent.change(screen.getByLabelText('Firearm Type *'), { target: { value: type } })
}

describe('Register firearm: type is a list', () => {
  it('offers exactly the three types', async () => {
    await openForm()
    const opts = Array.from((screen.getByLabelText('Firearm Type *') as HTMLSelectElement).options).map(o => o.value)
    expect(opts).toEqual(['', 'Handgun', 'Shotgun', 'Rifle'])
  })

  it('will not send without a type', async () => {
    const { container } = await openForm()
    fill(container, '')
    fireEvent.click(screen.getByRole('button', { name: 'Register' }))
    expect(await screen.findByText('Choose a firearm type.')).toBeTruthy()
    expect(api.post).not.toHaveBeenCalled()
  })

  it('sends the chosen type', async () => {
    const { container } = await openForm()
    fill(container, 'Shotgun')
    fireEvent.click(screen.getByRole('button', { name: 'Register' }))
    await waitFor(() => expect(api.post).toHaveBeenCalled())
    const [url, body] = api.post.mock.calls[0]
    expect(url).toBe('/api/v1/security/armoury')
    expect(body.firearmType).toBe('Shotgun')
    expect(body.firearmSerial).toBe('SN-77')
  })
})
