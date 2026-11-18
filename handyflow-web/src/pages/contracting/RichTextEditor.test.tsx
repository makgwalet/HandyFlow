// @vitest-environment jsdom
import { describe, it, expect, afterEach } from 'vitest'
import { createRoot, type Root } from 'react-dom/client'
import { act } from 'react'
import { RichTextEditor } from './RichTextEditor'

;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

let root: Root | null = null
let host: HTMLElement | null = null
afterEach(() => { act(() => root?.unmount()); host?.remove(); root = null; host = null })

async function mount(value: string, onChange: (h: string) => void = () => {}) {
  host = document.createElement('div')
  document.body.appendChild(host)
  root = createRoot(host)
  await act(async () => { root!.render(<RichTextEditor value={value} onChange={onChange} />) })
  await act(async () => { await new Promise(r => setTimeout(r, 0)) })
  return host
}

describe('RichTextEditor', () => {
  it('shows the existing text and the toolbar', async () => {
    const el = await mount('<h2>Heading</h2><p>Some <strong>bold</strong> text and {{variable}}.</p>')
    expect(el.querySelector('h2')?.textContent).toBe('Heading')
    expect(el.querySelector('strong')?.textContent).toBe('bold')
    expect(el.textContent).toContain('{{variable}}')
    expect(el.querySelector('button[aria-label="Bold"]')).not.toBeNull()
    expect(el.querySelector('button[aria-label="Insert a 3 by 3 table"]')).not.toBeNull()
  })

  it('renders a table', async () => {
    const el = await mount('<table><tbody><tr><td><p>cell</p></td></tr></tbody></table>')
    expect(el.querySelector('table td')?.textContent).toContain('cell')
  })

  it('switches to the raw HTML view and back', async () => {
    const el = await mount('<p>Hi</p>')
    const toggle = Array.from(el.querySelectorAll('button')).find(b => b.textContent === 'HTML')!
    await act(async () => { toggle.click() })
    expect((el.querySelector('textarea') as HTMLTextAreaElement).value).toBe('<p>Hi</p>')
    const back = Array.from(el.querySelectorAll('button')).find(b => b.textContent === 'Back to editor')!
    await act(async () => { back.click() })
    expect(el.querySelector('textarea')).toBeNull()
    expect(el.textContent).toContain('Hi')
  })
})
