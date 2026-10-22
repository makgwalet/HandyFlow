import { describe, it, expect } from 'vitest'
import { payslipNotes } from './payslipNotes'

describe('payslipNotes', () => {
  it('says nothing about an ordinary month', () => {
    expect(payslipNotes({ daysInPeriod: 22, daysPaid: 22, unpaidLeaveDays: 0, leavePayoutAmount: 0, finalPay: false })).toEqual([])
    expect(payslipNotes({})).toEqual([])
  })
  it('says how many days were paid when it was a part month', () => {
    expect(payslipNotes({ daysInPeriod: 22, daysPaid: '12.00' })).toEqual(['Paid 12 of 22 working days'])
  })
  it('names unpaid leave and its amount', () => {
    const n = payslipNotes({ daysInPeriod: 22, daysPaid: 17, unpaidLeaveDays: 5, unpaidLeaveAmount: 5000 })
    expect(n.some(s => s.startsWith('5 unpaid leave days deducted'))).toBe(true)
    expect(payslipNotes({ unpaidLeaveDays: 1, unpaidLeaveAmount: 100 })[0]).toContain('1 unpaid leave day deducted')
  })
  it('marks final pay and the leave paid out', () => {
    const n = payslipNotes({ finalPay: true, leavePayoutDays: '10.50', leavePayoutAmount: '10661.49' })
    expect(n[0]).toBe('Final pay')
    expect(n[1]).toContain('10.5 annual leave days paid out')
  })
})
