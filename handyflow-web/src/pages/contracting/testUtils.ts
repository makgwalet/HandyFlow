// Small DOM helpers shared by the negotiation component tests (jsdom + React 19, no testing-library).
import { act } from 'react'

export const wait = () => act(async () => { await new Promise(r => setTimeout(r, 20)) })

export const buttons = (root: HTMLElement) => Array.from(root.querySelectorAll('button'))
export const button = (root: HTMLElement, text: string | RegExp) =>
  buttons(root).find(b => (typeof text === 'string' ? b.textContent?.trim() === text : text.test(b.textContent ?? ''))) as HTMLButtonElement | undefined
export const hasButton = (root: HTMLElement, text: string | RegExp) => !!button(root, text)

export async function click(el: Element | null | undefined) {
  if (!el) throw new Error('element to click not found')
  await act(async () => { el.dispatchEvent(new MouseEvent('click', { bubbles: true })) })
  await wait()
}

/** Sets a controlled input's value the way a user typing would, so React sees the change. */
export async function type(el: Element | null | undefined, value: string) {
  if (!el) throw new Error('element to type into not found')
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : el instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype
  const set = Object.getOwnPropertyDescriptor(proto, 'value')!.set!
  await act(async () => {
    set.call(el, value)
    el.dispatchEvent(new Event(el instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }))
  })
}

export const byLabel = (root: HTMLElement, label: string) => root.querySelector(`[aria-label="${label}"]`)
