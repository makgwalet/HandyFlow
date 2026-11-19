// @vitest-environment jsdom
import { describe, it, expect, vi } from 'vitest'
import { createRoot } from 'react-dom/client'
import { act } from 'react'
import { WithdrawModal } from './WithdrawModal'
import { click, type, button } from './testUtils'

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true

async function mount(props: Partial<Parameters<typeof WithdrawModal>[0]> = {}) {
  const host = document.createElement('div'); document.body.appendChild(host)
  const fns = { onAsk: vi.fn(), onWithdraw: vi.fn(), onClose: vi.fn() }
  await act(async () => {
    createRoot(host).render(<WithdrawModal contractNumber="CON-1" signedCount={0} totalSigners={2} required={false} state="NONE" busy={false} {...fns} {...props} />)
  })
  return { host, ...fns }
}

describe('WithdrawModal', () => {
  it('explains what withdrawing does and keeps the button off until there is a reason', async () => {
    const { host, onWithdraw } = await mount()
    expect(host.textContent).toContain('Every signing link stops working')
    expect(host.textContent).toContain('signatures')
    expect(button(host, 'Withdraw signing request')!.disabled).toBe(true)
    await type(host.querySelector('#withdraw-reason'), 'The wrong pricing schedule was attached')
    await click(button(host, 'Withdraw signing request'))
    expect(onWithdraw).toHaveBeenCalledWith('The wrong pricing schedule was attached')
  })

  it('says existing signatures are kept when some signers have signed', async () => {
    const { host } = await mount({ signedCount: 1 })
    expect(host.textContent).toContain('1 of 2 signers have already signed')
    expect(host.textContent).toContain('will not count as signed by everyone')
  })

  it('asks for approval instead of withdrawing when policy requires it', async () => {
    const { host, onAsk, onWithdraw } = await mount({ required: true })
    expect(host.querySelector('#withdraw-reason')).toBeNull()
    await click(button(host, 'Ask for approval to withdraw'))
    expect(onAsk).toHaveBeenCalled()
    expect(onWithdraw).not.toHaveBeenCalled()
  })

  it('waits while approval is pending', async () => {
    const { host } = await mount({ required: true, state: 'PENDING' })
    expect(button(host, 'Waiting for approval to withdraw')!.disabled).toBe(true)
  })

  it('shows the server error', async () => {
    const { host } = await mount({ error: 'Nope' })
    expect(host.querySelector('[role="alert"]')?.textContent).toBe('Nope')
  })
})
