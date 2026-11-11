// Pure helpers for the Warehousing admin page. The server checks everything again; these only save a round trip and word things for people.

export const LIMIT_KEYS: { key: string; label: string }[] = [
  { key: 'WAREHOUSES', label: 'Warehouses' },
  { key: 'CLIENT_ACCOUNTS', label: 'Client accounts' },
  { key: 'MONTHLY_TRANSACTIONS', label: 'Transactions per billing month' },
  { key: 'WEBHOOKS', label: 'Webhooks' },
]

export const limitLabel = (key: string) => LIMIT_KEYS.find(l => l.key === key)?.label ?? key

export const FINDING_TEXT: Record<string, { label: string; tone: 'ok' | 'warn' | 'info' }> = {
  NO_TIER: { label: 'No tier', tone: 'info' },
  MATCHES: { label: 'Matches', tone: 'ok' },
  PRICE_DIFFERS: { label: 'Price differs', tone: 'warn' },
  CUSTOM_QUOTE: { label: 'Custom quote', tone: 'info' },
  TIER_WITHOUT_MODULE: { label: 'Tier without module', tone: 'warn' },
}

export const findingText = (f: string | null | undefined) => FINDING_TEXT[f ?? ''] ?? { label: f ?? '', tone: 'info' as const }

export const money = (n: number | string | null | undefined) =>
  n == null || n === '' ? 'Custom' : `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

const dayMs = 86_400_000
const parse = (s: string) => { const d = new Date(`${s}T00:00:00Z`); return Number.isNaN(d.getTime()) ? null : d }

/** Null when the override can be saved, else what is wrong. Mirrors the server's rules (1 to 100,000 extra, ends today or later and within a year, reason of 10 to 300 characters). */
export function overrideProblem(extra: string, validUntil: string, reason: string, today: string): string | null {
  const n = Number(extra)
  if (!Number.isInteger(n) || n < 1 || n > 100000) return 'The extra allowance must be a whole number from 1 to 100,000.'
  const end = parse(validUntil), now = parse(today)
  if (!end || !now) return 'Choose an end date.'
  if (end.getTime() < now.getTime()) return 'Choose an end date that is today or later.'
  if ((end.getTime() - now.getTime()) / dayMs > 366) return 'An override can last at most a year; renew it if it is still needed.'
  const r = reason.trim()
  if (r.length < 10) return 'Give a reason of at least 10 characters; it is kept in the audit trail.'
  if (r.length > 300) return 'The reason can be at most 300 characters.'
  return null
}

/** Null when the dates can be saved: both are optional, but the end cannot be before the start. */
export function windowProblem(from: string, to: string): string | null {
  if (from && to && to < from) return 'The package cannot end before it starts.'
  return null
}

/** "Unlimited" for no limit. */
export const limitValue = (n: number | null | undefined) => (n == null ? 'Unlimited' : n.toLocaleString('en-ZA'))
