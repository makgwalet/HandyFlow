// Rules for the contract register: the built-in views, search, filtering and sorting. Pure, so they can be tested alone.
import type { ContractRow } from './contractingApi'

export type ViewKey = 'all' | 'drafts' | 'review' | 'awaiting' | 'active' | 'expiring' | 'expired' | 'terminated'

export interface RegisterView { key: ViewKey; label: string; hint: string }

/** The views a user can pick, and that the dashboard links to. A view is a filter on what the register shows, nothing more. */
export const VIEWS: RegisterView[] = [
  { key: 'all', label: 'All contracts', hint: 'Every contract' },
  { key: 'drafts', label: 'Drafts', hint: 'Not yet sent for review' },
  { key: 'review', label: 'Under review', hint: 'Being reviewed internally' },
  { key: 'awaiting', label: 'Awaiting signature', hint: 'Sent to the parties, not yet fully signed' },
  { key: 'active', label: 'Active', hint: 'Signed and not past their end date' },
  { key: 'expiring', label: 'Expiring soon', hint: 'Signed, ending within 30 days' },
  { key: 'expired', label: 'Expired', hint: 'Past their end date or marked expired' },
  { key: 'terminated', label: 'Terminated', hint: 'Ended early' },
]

export const isViewKey = (v: string | null | undefined): v is ViewKey => VIEWS.some(x => x.key === v)

export const EXPIRING_DAYS = 30

/** Whole days from today to the end date; negative once past. Null with no end date. */
export function daysToEnd(endDate: string | null | undefined, now: Date = new Date()): number | null {
  if (!endDate) return null
  const [y, m, d] = endDate.slice(0, 10).split('-').map(Number)
  if (!y || !m || !d) return null
  const end = Date.UTC(y, m - 1, d)
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((end - today) / 86_400_000)
}

export function inView(c: ContractRow, view: ViewKey, now: Date = new Date()): boolean {
  const left = daysToEnd(c.endDate, now)
  switch (view) {
    case 'all': return true
    case 'drafts': return c.status === 'DRAFT'
    case 'review': return c.status === 'UNDER_REVIEW'
    case 'awaiting': return c.status === 'SENT'
    case 'active': return c.status === 'SIGNED' && (left == null || left >= 0)
    case 'expiring': return c.status === 'SIGNED' && left != null && left >= 0 && left <= EXPIRING_DAYS
    case 'expired': return c.status === 'EXPIRED' || (c.status === 'SIGNED' && left != null && left < 0)
    case 'terminated': return c.status === 'TERMINATED'
  }
}

export const counterpartyNames = (c: ContractRow): string =>
  (c.parties ?? []).map(p => p.companyName || p.fullName).filter(Boolean).join(', ')

/** Search across title, number, type, party names and companies. Every word typed has to match somewhere. */
export function matchesSearch(c: ContractRow, query: string, typeLabel: (t: string) => string = t => t): boolean {
  const words = query.toLowerCase().split(/\s+/).filter(Boolean)
  if (words.length === 0) return true
  const hay = [c.title, c.contractNumber, c.contractType, typeLabel(c.contractType), counterpartyNames(c)].join(' ').toLowerCase()
  return words.every(w => hay.includes(w))
}

export type SortKey = 'title' | 'status' | 'value' | 'end'
export interface Sort { key: SortKey; dir: 'asc' | 'desc' }

const cmpText = (a: string, b: string) => a.localeCompare(b, 'en', { sensitivity: 'base' })

/** Sorting never mutates its input. Rows with no value or no end date go last in either direction. */
export function sortRows(rows: ContractRow[], sort: Sort): ContractRow[] {
  const sign = sort.dir === 'asc' ? 1 : -1
  const out = [...rows]
  out.sort((a, b) => {
    switch (sort.key) {
      case 'title': return sign * cmpText(a.title, b.title)
      case 'status': return sign * cmpText(a.status, b.status)
      case 'value': {
        const x = a.valueAmount, y = b.valueAmount
        if (x == null && y == null) return 0
        if (x == null) return 1
        if (y == null) return -1
        return sign * (Number(x) - Number(y))
      }
      case 'end': {
        if (!a.endDate && !b.endDate) return 0
        if (!a.endDate) return 1
        if (!b.endDate) return -1
        return sign * cmpText(a.endDate, b.endDate)
      }
    }
  })
  return out
}

export interface RegisterQuery { view: ViewKey; search: string; type: string; sort: Sort }

export function applyRegister(rows: ContractRow[], q: RegisterQuery, typeLabel?: (t: string) => string, now: Date = new Date()): ContractRow[] {
  const filtered = rows.filter(c => inView(c, q.view, now) && (!q.type || c.contractType === q.type) && matchesSearch(c, q.search, typeLabel))
  return sortRows(filtered, q.sort)
}

/** How many contracts each view would show, for the badges beside the view names. */
export function viewCounts(rows: ContractRow[], now: Date = new Date()): Record<ViewKey, number> {
  const out = Object.fromEntries(VIEWS.map(v => [v.key, 0])) as Record<ViewKey, number>
  for (const c of rows) for (const v of VIEWS) if (inView(c, v.key, now)) out[v.key]++
  return out
}

/** The link from a dashboard figure to the register showing exactly those contracts. */
export const registerLink = (view: ViewKey): string => view === 'all' ? 'contracts' : `contracts?view=${view}`

/** Plain CSV of what the register is showing. Quotes and leading formula characters are neutralised so a spreadsheet will not run them. */
export function toCsv(rows: ContractRow[], typeLabel: (t: string) => string = t => t): string {
  const cell = (v: unknown) => {
    let s = v == null ? '' : String(v)
    if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`
    return `"${s.replace(/"/g, '""')}"`
  }
  const head = ['Number', 'Title', 'Type', 'Status', 'Counterparties', 'Value', 'Start', 'End'].map(cell).join(',')
  const lines = rows.map(c => [c.contractNumber, c.title, typeLabel(c.contractType), c.status, counterpartyNames(c), c.valueAmount ?? '', c.startDate ?? '', c.endDate ?? ''].map(cell).join(','))
  return [head, ...lines].join('\r\n')
}
