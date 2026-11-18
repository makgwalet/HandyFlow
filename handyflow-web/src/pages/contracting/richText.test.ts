import { describe, it, expect } from 'vitest'
import { normalizeEditorHtml, hasContent, unsupportedMarkup } from './richText'

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

describe('unsupportedMarkup', () => {
  it('finds nothing in markup the editor keeps', () => {
    expect(unsupportedMarkup('<h2>T</h2><p>a <strong>b</strong> <em>c</em></p><ul><li>x</li></ul><table><tr><td>c</td></tr></table>')).toEqual([])
  })
  it('names wrappers, styles and classes that would be simplified', () => {
    expect(unsupportedMarkup('<div class="a"><span style="color:red">x</span></div>')).toEqual(['<div>', '<span>', 'style or class attributes'])
  })
  it('ignores closing tags of kept elements and variable placeholders', () => {
    expect(unsupportedMarkup('<p>{{name}}</p>')).toEqual([])
  })
})
