// @vitest-environment jsdom
import { describe, it, expect, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { act } from 'react'
import { WorkspaceHeader } from './WorkspaceHeader'
import { click, button } from './testUtils'

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

async function mount(props: Partial<Parameters<typeof WorkspaceHeader>[0]> = {}) {
  const host = document.createElement('div'); document.body.appendChild(host)
  const onTab = vi.fn(); const onAction = vi.fn()
  await act(async () => {
    createRoot(host).render(<WorkspaceHeader contract={{ status: 'DRAFT' }} parties={[]} unresolvedAmendments={0} canManage tab="overview" onTab={onTab} busy={false} onAction={onAction} {...props} />)
  })
  return { host, onTab, onAction }
}

describe('WorkspaceHeader', () => {
  it('shows the next action and jumps to the tab that holds it', async () => {
    const { host, onTab } = await mount()
    expect(host.querySelector('[aria-label="Next required action"]')?.textContent).toContain('Add the parties who will sign')
    await click(button(host, /Go to Parties/))
    expect(onTab).toHaveBeenCalledWith('parties')
  })

  it('marks the current tab and switches tabs', async () => {
    const { host, onTab } = await mount({ tab: 'document' })
    const tabs = Array.from(host.querySelectorAll('[role="tab"]'))
    expect(tabs.map(t => t.getAttribute('aria-selected'))).toEqual(['false', 'true', 'false', 'false', 'false'])
    await click(tabs[3]); expect(onTab).toHaveBeenCalledWith('negotiation')
  })

  it('offers the contract action straight from the banner when ready', async () => {
    const { host, onAction } = await mount({ parties: [{ fullName: 'A', email: 'a@x.co', signingStatus: 'PENDING' }] })
    await click(button(host, 'Submit for review'))
    expect(onAction).toHaveBeenCalledWith('submit-for-review')
  })

  it('offers no action to a user who cannot manage', async () => {
    const { host } = await mount({ canManage: false })
    expect(button(host, /Submit|Send|Go to/)).toBeUndefined()
  })
})
