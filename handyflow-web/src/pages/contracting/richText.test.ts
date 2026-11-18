import { describe, it, expect } from 'vitest'
import { normalizeEditorHtml, hasContent } from './richText'

describe('normalizeEditorHtml', () => {
  it('treats the empty editor document as no text', () => {
    expect(normalizeEditorHtml('<p></p>')).toBe('')
    expect(normalizeEditorHtml('<p><br></p>')).toBe('')
    expect(normalizeEditorHtml('  <p> </p> ')).toBe('')
    expect(normalizeEditorHtml('')).toBe('')
  })
  it('leaves real content alone', () => {
    expect(normalizeEditorHtml('<p>Hello</p>')).toBe('<p>Hello</p>')
  })
})

describe('hasContent', () => {
  it('is false for empty or whitespace-only markup', () => {
    expect(hasContent('')).toBe(false)
    expect(hasContent('<p></p>')).toBe(false)
    expect(hasContent('<p>&nbsp;</p>')).toBe(false)
  })
  it('is true for text, variables, tables and rules', () => {
    expect(hasContent('<p>Clause</p>')).toBe(true)
    expect(hasContent('<p>{{party_name}}</p>')).toBe(true)
    expect(hasContent('<table><tbody><tr><td></td></tr></tbody></table>')).toBe(true)
    expect(hasContent('<hr>')).toBe(true)
  })
})
