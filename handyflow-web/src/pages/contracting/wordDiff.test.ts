import { describe, it, expect } from 'vitest'
import { toText, isSimpleBlock, wordDiff, hunkWordDiff } from './wordDiff'

const join = (ops: { type: string; text: string }[], t: string) => ops.filter(o => o.type === 'same' || o.type === t).map(o => o.text).join('')

describe('toText', () => {
  it('removes tags, decodes entities and collapses whitespace', () => {
    expect(toText('<p>Pay&nbsp;within  <strong>30</strong> days &amp; no later</p>')).toBe('Pay within 30 days & no later')
  })
})

describe('isSimpleBlock', () => {
  it('accepts paragraphs and headings and rejects tables and lists', () => {
    expect(isSimpleBlock('<p>x</p>')).toBe(true)
    expect(isSimpleBlock('<h2>x</h2>')).toBe(true)
    expect(isSimpleBlock('<table><tr><td>x</td></tr></table>')).toBe(false)
    expect(isSimpleBlock('<ul><li>x</li></ul>')).toBe(false)
    expect(isSimpleBlock('plain')).toBe(false)
  })
})

describe('wordDiff', () => {
  it('marks only the words that changed', () => {
    const ops = wordDiff('The supplier shall pay within 30 days', 'The supplier shall pay within 60 days')!
    expect(ops.filter(o => o.type === 'del').map(o => o.text)).toEqual(['30'])
    expect(ops.filter(o => o.type === 'add').map(o => o.text)).toEqual(['60'])
  })
  it('rebuilds both sides exactly from the operations', () => {
    const a = 'Either party may terminate on thirty days written notice.'
    const b = 'Either party may terminate on sixty days written notice, in good faith.'
    const ops = wordDiff(a, b)!
    expect(join(ops, 'del')).toBe(a)
    expect(join(ops, 'add')).toBe(b)
  })
  it('handles an insertion and a deletion', () => {
    expect(wordDiff('a b', 'a x b')!.some(o => o.type === 'add' && o.text.includes('x'))).toBe(true)
    expect(wordDiff('a x b', 'a b')!.some(o => o.type === 'del' && o.text.includes('x'))).toBe(true)
  })
  it('gives up on very large texts so the page stays fast', () => {
    const big = Array.from({ length: 800 }, (_, i) => `w${i}`).join(' ')
    expect(wordDiff(big, big + ' extra')).toBeNull()
  })
})

describe('hunkWordDiff', () => {
  it('compares paragraphs word by word', () => {
    const ops = hunkWordDiff(['<p>Fee is R100</p>'], ['<p>Fee is R150</p>'])!
    expect(join(ops, 'del')).toBe('Fee is R100')
    expect(join(ops, 'add')).toBe('Fee is R150')
  })
  it('falls back for pure additions, pure removals and non-text blocks', () => {
    expect(hunkWordDiff([], ['<p>x</p>'])).toBeNull()
    expect(hunkWordDiff(['<p>x</p>'], [])).toBeNull()
    expect(hunkWordDiff(['<table><tr><td>a</td></tr></table>'], ['<table><tr><td>b</td></tr></table>'])).toBeNull()
  })
})
