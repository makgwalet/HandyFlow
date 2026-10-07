import { describe, it, expect } from 'vitest'
import {
  balanceOf, billableHours, creditNoteError, creditNoteTotals, cycleHoursError, daysUntil, dueLabel, frequencyLabel, hoursError,
  invoiceStats, isTruncated, matches, monthlyValue, nextRunLabel, paginate, partyName, paymentError, quoteExpiryLabel, quoteStats,
  recurringStats, retainerStats, retainerUse, sortBy, countBy, wouldExceed, fmtR, label, INVOICE_LABEL,
  type Invoice, type Quote, type RecurringSchedule,
} from './billing.logic'

const NOW = new Date(2026, 9, 7, 13, 0) // 7 Oct 2026, local
const iso = (y: number, m: number, d: number) => new Date(y, m - 1, d, 8).toISOString()

const inv = (o: Partial<Invoice> = {}): Invoice => ({ id: 'i', invoiceNumber: 'INV-1', customerId: 'c', status: 'ISSUED', issuedAt: null, dueDate: null,
  subtotal: 100, vatTotal: 15, total: 115, amountPaid: 0, lineItems: [], createdAt: '', invoiceType: 'STANDARD', recurringScheduleId: null,
  committedHours: null, ratePerHour: null, hoursConsumed: null, walkinClientName: null, ...o })
const quote = (o: Partial<Quote> = {}): Quote => ({ id: 'q', quoteNumber: 'Q-1', title: 't', status: 'DRAFT', total: 100, expiresAt: null, createdAt: '', customerId: 'c', ...o })
const sched = (o: Partial<RecurringSchedule> = {}): RecurringSchedule => ({ id: 's', title: 't', status: 'ACTIVE', frequency: 'MONTHLY', customIntervalDays: null,
  nextRunAt: iso(2026, 10, 10), lastRunAt: null, total: 1000, subtotal: 870, vatTotal: 130, customerId: 'c', lineItems: [], walkinClientName: null, createdAt: '',
  variableHours: false, ratePerHour: null, minimumHoursPerCycle: null, hoursVatRate: null, contractStartDate: null, contractEndDate: null,
  contractedTotalHours: null, totalHoursBilled: 0, remainingCycles: 0, ...o })

describe('wording and money', () => {
  it('formats rand and falls back for unknown statuses', () => {
    expect(fmtR(1234.5)).toMatch(/^R 1[\s ]234,50$|^R 1,234\.50$/)
    expect(fmtR(null)).toBe('—')
    expect(label(INVOICE_LABEL, 'PARTIALLY_PAID')).toBe('Part paid')
    expect(label(INVOICE_LABEL, 'ON_HOLD')).toBe('On hold')
  })
  it('names a custom frequency by its interval', () => {
    expect(frequencyLabel({ frequency: 'CUSTOM', customIntervalDays: 45 })).toBe('Every 45 days')
    expect(frequencyLabel({ frequency: 'WEEKLY', customIntervalDays: null })).toBe('Weekly')
  })
  it('never shows a raw id for a customer', () => {
    expect(partyName('abc', null, { abc: 'Acme' })).toBe('Acme')
    expect(partyName('zzz', null, {})).toBe('Customer')
    expect(partyName(null, 'Sam', {})).toBe('Sam (walk-in)')
    expect(partyName(null, null, {})).toBe('Walk-in client')
  })
})

describe('dates', () => {
  it('counts calendar days, ignoring the time of day', () => {
    expect(daysUntil(iso(2026, 10, 7), NOW)).toBe(0)
    expect(daysUntil(iso(2026, 10, 10), NOW)).toBe(3)
    expect(daysUntil(iso(2026, 10, 1), NOW)).toBe(-6)
    expect(daysUntil(null, NOW)).toBeNull()
    expect(daysUntil('not a date', NOW)).toBeNull()
  })
  it('words when an open invoice is due, and says nothing for settled ones', () => {
    expect(dueLabel(inv({ dueDate: iso(2026, 10, 12) }), NOW)).toBe('Due in 5 days')
    expect(dueLabel(inv({ dueDate: iso(2026, 10, 8) }), NOW)).toBe('Due in 1 day')
    expect(dueLabel(inv({ dueDate: iso(2026, 10, 7) }), NOW)).toBe('Due today')
    expect(dueLabel(inv({ status: 'OVERDUE', dueDate: iso(2026, 10, 4) }), NOW)).toBe('3 days overdue')
    expect(dueLabel(inv({ status: 'OVERDUE', dueDate: iso(2026, 10, 6) }), NOW)).toBe('1 day overdue')
    expect(dueLabel(inv({ status: 'PAID', dueDate: iso(2026, 10, 1) }), NOW)).toBe('')
    expect(dueLabel(inv({ status: 'DRAFT', dueDate: iso(2026, 10, 1) }), NOW)).toBe('')
  })
  it('warns about a sent quote only in its last week', () => {
    expect(quoteExpiryLabel(quote({ status: 'SENT', expiresAt: iso(2026, 10, 9) }), NOW)).toBe('Expires in 2 days')
    expect(quoteExpiryLabel(quote({ status: 'SENT', expiresAt: iso(2026, 10, 7) }), NOW)).toBe('Expires today')
    expect(quoteExpiryLabel(quote({ status: 'SENT', expiresAt: iso(2026, 10, 5) }), NOW)).toBe('Lapsed 2 days ago')
    expect(quoteExpiryLabel(quote({ status: 'SENT', expiresAt: iso(2026, 11, 30) }), NOW)).toBe('')
    expect(quoteExpiryLabel(quote({ status: 'ACCEPTED', expiresAt: iso(2026, 10, 9) }), NOW)).toBe('')
  })
})

describe('payments and credit notes', () => {
  it('works out what is still owing', () => {
    expect(balanceOf({ total: 115, amountPaid: 15 })).toBe(100)
    expect(balanceOf({ total: 115, amountPaid: 200 })).toBe(0)
  })
  it('rejects a payment that is empty, zero or more than owing', () => {
    const i = inv({ total: 115, amountPaid: 15 })
    expect(paymentError('', i)).toMatch(/enter/i)
    expect(paymentError('0', i)).toMatch(/enter/i)
    expect(paymentError('abc', i)).toMatch(/enter/i)
    expect(paymentError('100.01', i)).toMatch(/more than/i)
    expect(paymentError('100', i)).toBeNull()
    expect(paymentError('40.5', i)).toBeNull()
  })
  it('adds VAT to a credit note and validates it', () => {
    expect(creditNoteTotals('500', '15')).toEqual({ subtotal: 500, vat: 75, total: 575 })
    expect(creditNoteTotals('', '15').total).toBe(0)
    expect(creditNoteTotals('100', '').total).toBe(100)
    expect(creditNoteError('', '10', '15')).toMatch(/reason/i)
    expect(creditNoteError('x', '0', '15')).toMatch(/amount/i)
    expect(creditNoteError('x', '10', '150')).toMatch(/between/i)
    expect(creditNoteError('x', '10', '15')).toBeNull()
  })
})

describe('retainers', () => {
  it('measures hours used against the commitment', () => {
    expect(retainerUse(inv())).toBeNull()
    expect(retainerUse(inv({ committedHours: 100, hoursConsumed: 20 }))).toMatchObject({ remaining: 80, percent: 20, level: 'ok', overage: false })
    expect(retainerUse(inv({ committedHours: 100, hoursConsumed: 80 }))?.level).toBe('low')
    expect(retainerUse(inv({ committedHours: 100, hoursConsumed: 79.9 }))?.level).toBe('ok')
    expect(retainerUse(inv({ committedHours: 100, hoursConsumed: 112.5 }))).toMatchObject({ remaining: 0, percent: 100, level: 'over', overBy: 12.5 })
    expect(retainerUse(inv({ committedHours: 0, hoursConsumed: 0 }))?.percent).toBe(0)
  })
  it('warns when logged hours would pass the commitment', () => {
    const r = inv({ committedHours: 10, hoursConsumed: 8 })
    expect(wouldExceed('3', r)).toBe(true)
    expect(wouldExceed('2', r)).toBe(false)
    expect(wouldExceed('', r)).toBe(false)
    expect(hoursError('0')).toMatch(/more than zero/)
    expect(hoursError('1.5')).toBeNull()
  })
  it('totals retainers, ignoring cancelled ones', () => {
    const s = retainerStats([
      inv({ invoiceType: 'RETAINER', committedHours: 100, hoursConsumed: 90, total: 1000 }),
      inv({ invoiceType: 'RETAINER', committedHours: 50, hoursConsumed: 60, total: 500 }),
      inv({ invoiceType: 'RETAINER', committedHours: 50, hoursConsumed: 0, status: 'CANCELLED' }),
      inv({ invoiceType: 'STANDARD' }),
    ])
    expect(s).toMatchObject({ count: 2, committed: 150, consumed: 150, low: 1, over: 1, value: 1500 })
  })
})

describe('recurring', () => {
  it('converts every frequency to a monthly value', () => {
    expect(monthlyValue({ frequency: 'MONTHLY', customIntervalDays: null, total: 1200 })).toBe(1200)
    expect(monthlyValue({ frequency: 'WEEKLY', customIntervalDays: null, total: 120 })).toBeCloseTo(520, 5)
    expect(monthlyValue({ frequency: 'DAILY', customIntervalDays: null, total: 12 })).toBeCloseTo(365, 5)
    expect(monthlyValue({ frequency: 'CUSTOM', customIntervalDays: 30, total: 1000 })).toBeCloseTo(1013.89, 2)
    expect(monthlyValue({ frequency: 'CUSTOM', customIntervalDays: null, total: 1000 })).toBe(0)
    expect(monthlyValue({ frequency: 'YEARLY', customIntervalDays: null, total: 1000 })).toBe(0)
  })
  it('words the next run', () => {
    expect(nextRunLabel(sched({ nextRunAt: iso(2026, 10, 7) }), NOW)).toBe('Runs today')
    expect(nextRunLabel(sched({ nextRunAt: iso(2026, 10, 8) }), NOW)).toBe('Runs tomorrow')
    expect(nextRunLabel(sched({ nextRunAt: iso(2026, 10, 12) }), NOW)).toBe('Runs in 5 days')
    expect(nextRunLabel(sched({ nextRunAt: iso(2026, 10, 1) }), NOW)).toBe('Run overdue')
    expect(nextRunLabel(sched({ status: 'PAUSED' }), NOW)).toBe('Paused')
    expect(nextRunLabel(sched({ status: 'CANCELLED' }), NOW)).toBe('')
  })
  it('bills the minimum when fewer hours were worked', () => {
    expect(billableHours('120', 160)).toBe(160)
    expect(billableHours('187.5', 160)).toBe(187.5)
    expect(billableHours('', null)).toBe(0)
    expect(cycleHoursError('', 'June')).toMatch(/hours/i)
    expect(cycleHoursError('10', ' ')).toMatch(/period/i)
    expect(cycleHoursError('0', 'June 2026')).toBeNull()
  })
})

describe('headline figures', () => {
  it('summarises quotes, with a win rate only once something is decided', () => {
    expect(quoteStats([quote()]).winRate).toBeNull()
    const s = quoteStats([
      quote({ status: 'DRAFT', total: 100 }), quote({ status: 'SENT', total: 200, firstViewedAt: null }), quote({ status: 'SENT', total: 50, firstViewedAt: 'x' }),
      quote({ status: 'ACCEPTED', total: 300 }), quote({ status: 'INVOICED', total: 400 }), quote({ status: 'REJECTED', total: 10 }), quote({ status: 'EXPIRED' }),
    ])
    expect(s).toMatchObject({ total: 7, openValue: 350, awaitingInvoice: 1, awaitingInvoiceValue: 300, winRate: 67, unopened: 1 })
  })
  it('counts what is owing, not the invoice totals, and keeps drafts and cancelled out of billed', () => {
    const s = invoiceStats([
      inv({ status: 'ISSUED', total: 115 }),
      inv({ status: 'PARTIALLY_PAID', total: 115, amountPaid: 15 }),
      inv({ status: 'OVERDUE', total: 230, amountPaid: 30 }),
      inv({ status: 'PAID', total: 100, amountPaid: 100 }),
      inv({ status: 'DRAFT', total: 999 }),
      inv({ status: 'CANCELLED', total: 888 }),
    ])
    expect(s).toMatchObject({ outstanding: 115 + 100 + 200, outstandingCount: 3, overdueValue: 200, overdueCount: 1, collected: 145, billed: 560, drafts: 1, paidCount: 1 })
  })
  it('totals recurring revenue from active schedules only', () => {
    const s = recurringStats([
      sched({ total: 1000 }), sched({ total: 120, frequency: 'WEEKLY' }), sched({ status: 'PAUSED', total: 5000 }),
      sched({ variableHours: true, total: 0, nextRunAt: iso(2026, 12, 1) }),
    ])
    expect(s.total).toBe(4)
    expect(s.active).toBe(3)
    expect(s.paused).toBe(1)
    expect(s.variable).toBe(1)
    expect(s.mrr).toBeCloseTo(1520, 5)
  })
})

describe('lists', () => {
  it('searches case-insensitively across fields and ignores empty queries', () => {
    expect(matches('', 'x')).toBe(true)
    expect(matches('acme', 'ACME Ltd', 'INV-1')).toBe(true)
    expect(matches('inv-1', 'ACME Ltd', 'INV-1')).toBe(true)
    expect(matches('zzz', 'ACME Ltd', null, undefined)).toBe(false)
  })
  it('sorts numbers and text, numbering naturally, with empty values last either way', () => {
    const rows = [{ n: 'INV-10', t: 5 }, { n: 'INV-2', t: null }, { n: 'INV-1', t: 9 }]
    expect(sortBy(rows, r => r.n, 'asc').map(r => r.n)).toEqual(['INV-1', 'INV-2', 'INV-10'])
    expect(sortBy(rows, r => r.t, 'desc').map(r => r.t)).toEqual([9, 5, null])
    expect(sortBy(rows, r => r.t, 'asc').map(r => r.t)).toEqual([5, 9, null])
    expect(rows[0].n).toBe('INV-10')
  })
  it('pages, clamping out-of-range pages', () => {
    const rows = Array.from({ length: 45 }, (_, i) => i)
    expect(paginate(rows, 0)).toMatchObject({ pages: 3, from: 1, to: 20, total: 45 })
    expect(paginate(rows, 2)).toMatchObject({ from: 41, to: 45 })
    expect(paginate(rows, 9).page).toBe(2)
    expect(paginate(rows, -3).page).toBe(0)
    expect(paginate([], 0)).toMatchObject({ pages: 1, from: 0, to: 0, rows: [] })
  })
  it('counts by key and spots a truncated list', () => {
    expect(countBy([inv({ status: 'PAID' }), inv({ status: 'PAID' }), inv({ status: 'DRAFT' })], i => i.status)).toEqual({ PAID: 2, DRAFT: 1 })
    expect(isTruncated(250, 200)).toBe(true)
    expect(isTruncated(200, 200)).toBe(false)
    expect(isTruncated(undefined, 5)).toBe(false)
  })
})
