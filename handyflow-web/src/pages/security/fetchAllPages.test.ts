import { describe, it, expect, vi, beforeEach } from 'vitest'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../../api/client', () => ({ apiClient: api }))

import { fetchAllPages, MAX_PAGES, PAGE_SIZE } from './fetchAllPages'

beforeEach(() => vi.clearAllMocks())
const page = (content: number[], last: boolean) => ({ data: { data: { content, last } } })

describe('fetchAllPages', () => {
  it('joins every page and stops at the last one', async () => {
    api.get.mockResolvedValueOnce(page([1, 2], false)).mockResolvedValueOnce(page([3], true))
    const r = await fetchAllPages('/api/v1/security/guards')
    expect(r.data.data.content).toEqual([1, 2, 3])
    expect(api.get).toHaveBeenCalledTimes(2)
    expect(api.get).toHaveBeenNthCalledWith(1, `/api/v1/security/guards?page=0&size=${PAGE_SIZE}`)
    expect(api.get).toHaveBeenNthCalledWith(2, `/api/v1/security/guards?page=1&size=${PAGE_SIZE}`)
  })
  it('uses & when the path already has a query', async () => {
    api.get.mockResolvedValueOnce(page([], true))
    await fetchAllPages('/x?status=ACTIVE')
    expect(api.get).toHaveBeenCalledWith(`/x?status=ACTIVE&page=0&size=${PAGE_SIZE}`)
  })
  it('accepts an endpoint that is not paged', async () => {
    api.get.mockResolvedValueOnce({ data: { data: [1, 2, 3] } })
    expect((await fetchAllPages('/x')).data.data.content).toEqual([1, 2, 3])
    expect(api.get).toHaveBeenCalledTimes(1)
  })
  it('stops on an empty page even if the server never says last', async () => {
    api.get.mockResolvedValueOnce(page([1], false)).mockResolvedValueOnce(page([], false))
    expect((await fetchAllPages('/x')).data.data.content).toEqual([1])
  })
  it('never runs away', async () => {
    api.get.mockResolvedValue(page([1], false))
    await fetchAllPages('/x')
    expect(api.get).toHaveBeenCalledTimes(MAX_PAGES)
  })
})
