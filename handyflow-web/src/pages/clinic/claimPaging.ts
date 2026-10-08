// Paging for the claims list (patch 0178). The server sends 25 claims at a time; the figures above the list come from a summary of all of them.

export const CLAIMS_PAGE_SIZE = 25

export interface ClaimsPage<T> { content: T[]; total: number; page: number; size: number }

/** "Showing 26-50 of 61", or "" when there is nothing to show. */
export function pageRange(page: number, size: number, total: number, shown: number): string {
  if (total <= 0 || shown <= 0) return ""
  const from = page * size + 1
  return `Showing ${from}-${from + shown - 1} of ${total}`
}

export function pageCount(total: number, size: number): number {
  return size > 0 ? Math.max(1, Math.ceil(total / size)) : 1
}

export const hasPrevious = (page: number) => page > 0
export const hasNext = (page: number, size: number, total: number) => (page + 1) * size < total

/** If claims were removed from under the current page, the page to move back to; otherwise unchanged. */
export function clampPage(page: number, size: number, total: number): number {
  return Math.min(Math.max(page, 0), pageCount(total, size) - 1)
}
