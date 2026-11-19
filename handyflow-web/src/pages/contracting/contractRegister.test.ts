import { describe, it, expect } from 'vitest'
import {
  VIEWS, isViewKey, daysToEnd, inView, matchesSearch, sortRows, applyRegister, viewCounts, registerLink, toCsv, counterpartyNames,
} from './contractRegister'
import type { ContractRow } from './contractingApi'

const NOW = new Date(2026, 10, 19) // 19 Nov 2026
const row = (o: Partial<ContractRow>): ContractRow => ({ id: 'x', contractNumber: 'CTR-1', title: 'Service agreement', contractType: 'SERVICE_AGREEMENT', status: 'DRAFT', ...o })

describe('daysToEnd', () => {
  it('counts whole days, negative once past', () => {
    expect(daysToEnd('2026-11-19', NOW)).toBe(0)
    expect(daysToEnd('2026-12-19', NOW)).toBe(30)
    expect(daysToEnd('2026-11-18', NOW)).toBe(-1)
  })
  it('is null with no usable date', () => {
    expect(daysToEnd(null, NOW)).toBeNull()
    expect(daysToEnd('', NOW)).toBeNull()
    expect(daysToEnd('nonsense', NOW)).toBeNull()
  })
})

describe('views', () => {
  it('knows its own keys', () => {
    expect(isViewKey('expiring')).toBe(true)
    expect(isViewKey('nope')).toBe(false)
    expect(isViewKey(null)).toBe(false)
  })
  it('puts each status in the right view', () => {
    expect(inView(row({ status: 'DRAFT' }), 'drafts', NOW)).toBe(true)
    expect(inView(row({ status: 'UNDER_REVIEW' }), 'review', NOW)).toBe(true)
    expect(inView(row({ status: 'SENT' }), 'awaiting', NOW)).toBe(true)
    expect(inView(row({ status: 'TERMINATED' }), 'terminated', NOW)).toBe(true)
    expect(inView(row({ status: 'SENT' }), 'drafts', NOW)).toBe(false)
  })
  it('active means signed and not past its end date; no end date counts as active', () => {
    expect(inView(row({ status: 'SIGNED', endDate: '2027-01-01' }), 'active', NOW)).toBe(true)
    expect(inView(row({ status: 'SIGNED' }), 'active', NOW)).toBe(true)
    expect(inView(row({ status: 'SIGNED', endDate: '2026-11-01' }), 'active', NOW)).toBe(false)
  })
  it('expiring is signed and ending within 30 days, today included', () => {
    expect(inView(row({ status: 'SIGNED', endDate: '2026-11-19' }), 'expiring', NOW)).toBe(true)
    expect(inView(row({ status: 'SIGNED', endDate: '2026-12-19' }), 'expiring', NOW)).toBe(true)
    expect(inView(row({ status: 'SIGNED', endDate: '2026-12-20' }), 'expiring', NOW)).toBe(false)
    expect(inView(row({ status: 'SIGNED', endDate: '2026-11-01' }), 'expiring', NOW)).toBe(false)
    expect(inView(row({ status: 'DRAFT', endDate: '2026-11-25' }), 'expiring', NOW)).toBe(false)
  })
  it('expired is marked expired, or signed and past its end date', () => {
    expect(inView(row({ status: 'EXPIRED' }), 'expired', NOW)).toBe(true)
    expect(inView(row({ status: 'SIGNED', endDate: '2026-11-01' }), 'expired', NOW)).toBe(true)
    expect(inView(row({ status: 'SIGNED', endDate: '2027-11-01' }), 'expired', NOW)).toBe(false)
  })
  it('a signed contract is in exactly one of active or expired', () => {
    for (const end of ['2026-11-01', '2026-11-19', '2026-12-01', '2028-01-01', undefined]) {
      const c = row({ status: 'SIGNED', endDate: end })
      expect(inView(c, 'active', NOW) !== inView(c, 'expired', NOW)).toBe(true)
    }
  })
  it('counts every view', () => {
    const rows = [row({ status: 'DRAFT' }), row({ status: 'DRAFT' }), row({ status: 'SIGNED', endDate: '2026-11-25' })]
    const c = viewCounts(rows, NOW)
    expect(c.all).toBe(3); expect(c.drafts).toBe(2); expect(c.active).toBe(1); expect(c.expiring).toBe(1); expect(c.terminated).toBe(0)
    expect(Object.keys(c)).toHaveLength(VIEWS.length)
  })
  it('links from the dashboard', () => {
    expect(registerLink('all')).toBe('contracts')
    expect(registerLink('expiring')).toBe('contracts?view=expiring')
  })
})

describe('search', () => {
  const c = row({ title: 'Supplier Agreement', contractNumber: 'CON-2026-0042', parties: [{ id: 'p', partyType: 'COMPANY', partyRole: 'SUPPLIER', fullName: 'Bob', companyName: 'ACME Ltd', signingOrder: 1, signingStatus: 'PENDING' }] })
  it('matches title, number and counterparty, case-insensitively', () => {
    expect(matchesSearch(c, 'supplier')).toBe(true)
    expect(matchesSearch(c, '0042')).toBe(true)
    expect(matchesSearch(c, 'acme')).toBe(true)
  })
  it('needs every word to match somewhere', () => {
    expect(matchesSearch(c, 'supplier acme')).toBe(true)
    expect(matchesSearch(c, 'supplier zebra')).toBe(false)
  })
  it('an empty search matches everything, and the type label is searchable', () => {
    expect(matchesSearch(c, '   ')).toBe(true)
    expect(matchesSearch(row({ contractType: 'NDA' }), 'non-disclosure', t => t === 'NDA' ? 'Non-Disclosure Agreement' : t)).toBe(true)
  })
  it('names the counterparties by company, then person', () => {
    expect(counterpartyNames(c)).toBe('ACME Ltd')
    expect(counterpartyNames(row({}))).toBe('')
  })
})

describe('sorting', () => {
  const a = row({ id: 'a', title: 'Alpha', valueAmount: 100, endDate: '2027-01-01' })
  const b = row({ id: 'b', title: 'beta', valueAmount: 300, endDate: '2026-12-01' })
  const none = row({ id: 'n', title: 'Gamma' })
  it('sorts by title ignoring case', () => expect(sortRows([b, a], { key: 'title', dir: 'asc' }).map(x => x.id)).toEqual(['a', 'b']))
  it('sorts by value, putting rows without one last either way', () => {
    expect(sortRows([none, a, b], { key: 'value', dir: 'desc' }).map(x => x.id)).toEqual(['b', 'a', 'n'])
    expect(sortRows([none, a, b], { key: 'value', dir: 'asc' }).map(x => x.id)).toEqual(['a', 'b', 'n'])
  })
  it('sorts by end date, rows without one last either way', () => {
    expect(sortRows([none, a, b], { key: 'end', dir: 'asc' }).map(x => x.id)).toEqual(['b', 'a', 'n'])
    expect(sortRows([none, a, b], { key: 'end', dir: 'desc' }).map(x => x.id)).toEqual(['a', 'b', 'n'])
  })
  it('does not change the list it is given', () => {
    const input = [b, a]
    sortRows(input, { key: 'title', dir: 'asc' })
    expect(input.map(x => x.id)).toEqual(['b', 'a'])
  })
})

describe('applyRegister', () => {
  const rows = [
    row({ id: '1', title: 'Lease', contractType: 'LEASE', status: 'SIGNED', endDate: '2026-11-30' }),
    row({ id: '2', title: 'NDA', contractType: 'NDA', status: 'SIGNED', endDate: '2027-06-01' }),
    row({ id: '3', title: 'Draft thing', contractType: 'LEASE', status: 'DRAFT' }),
  ]
  const q = { view: 'all' as const, search: '', type: '', sort: { key: 'title' as const, dir: 'asc' as const } }
  it('combines view, type and search', () => {
    expect(applyRegister(rows, { ...q, view: 'active' }, undefined, NOW).map(r => r.id)).toEqual(['1', '2'])
    expect(applyRegister(rows, { ...q, type: 'LEASE' }, undefined, NOW).map(r => r.id)).toEqual(['3', '1'])
    expect(applyRegister(rows, { ...q, view: 'expiring', type: 'LEASE' }, undefined, NOW).map(r => r.id)).toEqual(['1'])
    expect(applyRegister(rows, { ...q, search: 'nda' }, undefined, NOW).map(r => r.id)).toEqual(['2'])
  })
})

describe('csv export', () => {
  it('quotes cells, doubles quotes and neutralises formulas', () => {
    const csv = toCsv([row({ title: '=HYPERLINK("x")', contractNumber: 'CTR "1"', valueAmount: 5, endDate: '2027-01-01' })])
    const lines = csv.split('\r\n')
    expect(lines[0]).toBe('"Number","Title","Type","Status","Counterparties","Value","Start","End"')
    expect(lines[1]).toContain('"\'=HYPERLINK(""x"")"')
    expect(lines[1]).toContain('"CTR ""1"""')
    expect(lines[1]).toContain('"5"')
  })
  it('has just the header when nothing is shown', () => expect(toCsv([]).split('\r\n')).toHaveLength(1))
})
