import { describe, it, expect } from 'vitest'
import { nextRequiredAction as next } from './contractWorkspace'

const p = (name: string, extra: object = {}) => ({ fullName: name, email: `${name}@x.co`, signingStatus: 'PENDING', ...extra })

describe('nextRequiredAction', () => {
  it('asks for parties first on a draft', () => {
    expect(next({ status: 'DRAFT', canManage: true })).toMatchObject({ tab: 'parties', tone: 'todo' })
  })
  it('ignores witnesses when checking for signers', () => {
    expect(next({ status: 'DRAFT', canManage: true, parties: [p('W', { partyRole: 'WITNESS' })] }).title).toMatch(/Add the parties/)
  })
  it('asks for contact details', () => {
    const r = next({ status: 'DRAFT', canManage: true, parties: [p('A'), { fullName: 'B', signingStatus: 'PENDING' }] })
    expect(r.title).toContain('B'); expect(r.title).not.toContain('A,')
  })
  it('puts amendment requests ahead of submitting', () => {
    expect(next({ status: 'DRAFT', canManage: true, parties: [p('A')], unresolvedAmendments: 2 })).toMatchObject({ tab: 'activity', title: expect.stringContaining('2 amendment requests') })
  })
  it('offers submit on draft and send on review', () => {
    expect(next({ status: 'DRAFT', canManage: true, parties: [p('A')] }).action).toBe('submit-for-review')
    expect(next({ status: 'UNDER_REVIEW', canManage: true, parties: [p('A')] }).action).toBe('send-for-signing')
  })
  it('does not push a read-only user to act', () => {
    const r = next({ status: 'DRAFT', canManage: false })
    expect(r.tone).toBe('waiting'); expect(r.action).toBeUndefined()
  })
  it('names who still has to sign', () => {
    const r = next({ status: 'SENT', canManage: true, parties: [p('A', { signingStatus: 'SIGNED' }), p('B')] })
    expect(r.title).toBe('Waiting for B to sign'); expect(r.tone).toBe('waiting')
  })
  it('reports finished states', () => {
    for (const s of ['SIGNED', 'TERMINATED', 'EXPIRED']) expect(next({ status: s, canManage: true }).tone).toBe('done')
  })

  describe('approval to send', () => {
    const ready = { status: 'UNDER_REVIEW', canManage: true, parties: [p('A')] }
    it('asks for approval first when the policy requires it', () => {
      expect(next({ ...ready, sendApproval: { required: true, state: 'NONE' } })).toMatchObject({ tab: 'approvals', tone: 'todo' })
    })
    it('waits while the approval is pending', () => {
      const r = next({ ...ready, sendApproval: { required: true, state: 'PENDING' } })
      expect(r).toMatchObject({ tone: 'waiting', tab: 'approvals' }); expect(r.action).toBeUndefined()
    })
    it('offers send once approved, or when approval is not required', () => {
      expect(next({ ...ready, sendApproval: { required: true, state: 'APPROVED' } }).action).toBe('send-for-signing')
      expect(next({ ...ready, sendApproval: { required: false, state: 'NONE' } }).action).toBe('send-for-signing')
    })
  })
  describe('withdrawn', () => {
    it('offers a new draft to someone who can manage, and only describes it to anyone else', () => {
      expect(next({ status: 'WITHDRAWN', canManage: true, parties: [] })).toMatchObject({ title: 'Signing was withdrawn', action: 'new-revision', actionLabel: 'Make a new draft' })
      expect(next({ status: 'WITHDRAWN', canManage: false, parties: [] }).action).toBeUndefined()
    })
  })
})
