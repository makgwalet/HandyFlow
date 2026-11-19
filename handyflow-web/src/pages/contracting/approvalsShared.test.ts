import { describe, it, expect } from 'vitest'
import { approvalState, requestableActions } from './approvalsShared'

const a = (action: 'SEND_FOR_SIGNING' | 'TERMINATE', status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CONSUMED' | 'INVALIDATED' | 'CANCELLED', subjectId = 'c1') => ({ action, status, subjectId })

describe('approvalState', () => {
  it('prefers approved over pending and ignores other contracts and spent requests', () => {
    expect(approvalState([a('SEND_FOR_SIGNING', 'PENDING'), a('SEND_FOR_SIGNING', 'APPROVED')], 'SEND_FOR_SIGNING', 'c1')).toBe('APPROVED')
    expect(approvalState([a('SEND_FOR_SIGNING', 'PENDING', 'c2')], 'SEND_FOR_SIGNING', 'c1')).toBe('NONE')
    for (const s of ['REJECTED', 'CONSUMED', 'INVALIDATED', 'CANCELLED'] as const) expect(approvalState([a('SEND_FOR_SIGNING', s)], 'SEND_FOR_SIGNING', 'c1')).toBe('NONE')
  })
})

describe('requestableActions', () => {
  it('offers send approval under review and termination approval once signed', () => {
    expect(requestableActions('UNDER_REVIEW', [], 'c1')).toEqual(['SEND_FOR_SIGNING'])
    expect(requestableActions('SIGNED', [], 'c1')).toEqual(['TERMINATE'])
    expect(requestableActions('SENT', [], 'c1')).toEqual(['WITHDRAW'])
    expect(requestableActions('DRAFT', [], 'c1')).toEqual([])
  })
  it('does not offer one that is already waiting or granted', () => {
    expect(requestableActions('UNDER_REVIEW', [a('SEND_FOR_SIGNING', 'PENDING')], 'c1')).toEqual([])
    expect(requestableActions('SIGNED', [a('TERMINATE', 'APPROVED')], 'c1')).toEqual([])
  })
})
