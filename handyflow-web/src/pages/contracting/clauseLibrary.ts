// Pure rules for browsing the clause library: search, saved views and what needs a legal reviewer's attention.

export interface LibraryClause {
  title: string; category: string; body: string; status: string; legalStatus: string
  legalArea?: string | null; sourceReference?: string | null
  nextReviewDate?: string | null; evidenceRequired?: boolean; evidenceDocumentId?: string | null
}

export type ClauseView = 'ACTIVE' | 'ATTENTION' | 'PUBLISHED' | 'DRAFT' | 'ARCHIVED' | 'ALL'
export const CLAUSE_VIEWS: { key: ClauseView; label: string; hint: string }[] = [
  { key: 'ACTIVE', label: 'Active', hint: 'Drafts, approved and published clauses' },
  { key: 'ATTENTION', label: 'Needs attention', hint: 'Review expired or due soon, or evidence missing' },
  { key: 'PUBLISHED', label: 'Published', hint: 'Can be put into contracts' },
  { key: 'DRAFT', label: 'Drafts', hint: 'Not yet reviewed' },
  { key: 'ARCHIVED', label: 'Archived', hint: 'Superseded or withdrawn' },
  { key: 'ALL', label: 'All', hint: 'Every version' },
]

export const REVIEW_DUE_DAYS = 30
const live = (c: LibraryClause) => c.status === 'APPROVED' || c.status === 'PUBLISHED'
const DAY = 86_400_000

/** Why a clause needs attention, or null. Only live clauses are flagged; archived and draft ones are not. */
export function clauseAttention(c: LibraryClause, today: Date = new Date()): string | null {
  if (!live(c)) return null
  if (c.legalStatus === 'REVIEW_EXPIRED') return 'Review expired'
  if (c.evidenceRequired && !c.evidenceDocumentId) return 'Evidence missing'
  if (c.nextReviewDate) {
    const days = Math.ceil((new Date(c.nextReviewDate).getTime() - today.getTime()) / DAY)
    if (days <= REVIEW_DUE_DAYS) return days < 0 ? 'Review expired' : days === 0 ? 'Review due today' : `Review due in ${days} day${days === 1 ? '' : 's'}`
  }
  return null
}

const inView = (c: LibraryClause, v: ClauseView, today: Date): boolean => {
  switch (v) {
    case 'ACTIVE': return c.status === 'DRAFT' || live(c)
    case 'ATTENTION': return clauseAttention(c, today) !== null
    case 'PUBLISHED': return c.status === 'PUBLISHED'
    case 'DRAFT': return c.status === 'DRAFT'
    case 'ARCHIVED': return c.status === 'SUPERSEDED' || c.status === 'WITHDRAWN'
    default: return true
  }
}

const strip = (html: string) => html.replace(/<[^>]*>/g, ' ')

export function filterClauses<T extends LibraryClause>(list: T[], view: ClauseView, search: string, today: Date = new Date()): T[] {
  const q = search.trim().toLowerCase()
  return list.filter(c => inView(c, view, today) &&
    (!q || [c.title, c.legalArea ?? '', c.sourceReference ?? '', strip(c.body)].some(s => s.toLowerCase().includes(q))))
}

export function viewCounts(list: LibraryClause[], today: Date = new Date()): Record<ClauseView, number> {
  const out = {} as Record<ClauseView, number>
  for (const v of CLAUSE_VIEWS) out[v.key] = list.filter(c => inView(c, v.key, today)).length
  return out
}
