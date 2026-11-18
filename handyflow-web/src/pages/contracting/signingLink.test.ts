import { describe, it, expect } from 'vitest'
import { linkStatus } from './signingLink'

const fmt = (s: string) => `<${s}>`

describe('linkStatus', () => {
  it('says so when no link was issued', () => {
    expect(linkStatus({}, fmt)).toEqual({ text: 'No signing link issued yet', tone: 'none' })
  })
  it('never claims a queued email was delivered', () => {
    const s = linkStatus({ linkIssuedAt: 't', linkDeliveryStatus: 'QUEUED' }, fmt)
    expect(s.text).toContain('not confirmed')
    expect(s.text).not.toMatch(/delivered(?! is)/)
  })
  it('flags failures and missing email addresses', () => {
    expect(linkStatus({ linkIssuedAt: 't', linkDeliveryStatus: 'FAILED' }, fmt).tone).toBe('bad')
    expect(linkStatus({ linkIssuedAt: 't', linkDeliveryStatus: 'NO_CHANNEL' }, fmt).tone).toBe('warn')
  })
})
