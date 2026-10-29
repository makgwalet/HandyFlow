import { describe, it, expect } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'
import { apiClient } from './client'

// Captures what would have been sent, without any network.
const capture = async (data: unknown) => {
  let seen: InternalAxiosRequestConfig | undefined
  await apiClient.post('/x', data, {
    adapter: async config => { seen = config; return { data: {}, status: 200, statusText: 'OK', headers: {}, config } },
  })
  return seen!
}

describe('apiClient request headers', () => {
  it('lets the browser set multipart/form-data for a FormData body', async () => {
    const f = new FormData(); f.append('file', new Blob(['x']), 'a.png')
    const sent = await capture(f)
    expect(sent.headers.get('Content-Type') ?? '').not.toContain('application/json')
    expect(sent.data).toBeInstanceOf(FormData)
  })
  it('still sends JSON for an ordinary body', async () => {
    const sent = await capture({ a: 1 })
    expect(String(sent.headers.get('Content-Type'))).toContain('application/json')
  })
})
