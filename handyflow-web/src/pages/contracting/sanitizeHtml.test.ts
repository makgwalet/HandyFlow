// @vitest-environment jsdom
import { describe, it, expect } from 'vitest'
import { sanitizeHtml } from './sanitizeHtml'

describe('sanitizeHtml', () => {
  it('removes script tags and event handlers', () => {
    const out = sanitizeHtml('<p onclick="x()">Hi</p><script>alert(1)</script><img src=x onerror=alert(1)>')
    expect(out).toBe('<p>Hi</p>')
  })
  it('removes javascript links and iframes', () => {
    const out = sanitizeHtml('<a href="javascript:alert(1)">x</a><iframe src="//evil"></iframe><p>ok</p>')
    expect(out).not.toContain('javascript')
    expect(out).not.toContain('iframe')
    expect(out).toContain('<p>ok</p>')
  })
  it('keeps contract formatting and placeholders', () => {
    const out = sanitizeHtml('<h2>1. Parties</h2><p><strong>{{employee_name}}</strong></p><ul><li>a</li></ul>')
    expect(out).toBe('<h2>1. Parties</h2><p><strong>{{employee_name}}</strong></p><ul><li>a</li></ul>')
  })
  it('handles empty input', () => {
    expect(sanitizeHtml(null)).toBe('')
    expect(sanitizeHtml(undefined)).toBe('')
  })
})
