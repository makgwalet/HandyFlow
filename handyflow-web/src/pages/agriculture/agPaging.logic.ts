// src/pages/agriculture/agPaging.logic.ts
// Paging and search for the animal and group lists, and loading every page for a picker. No React, so it is tested directly.
// The server repeats the limits (AgListRules): 200 rows per page at most, search text 60 characters at most.

export const PAGE_SIZE = 50
export const MAX_SEARCH = 60
const PICKER_PAGE = 200
const PICKER_MAX_PAGES = 10   // 2,000 rows
export const LIST_MAX_ROWS = PICKER_PAGE * PICKER_MAX_PAGES

/** The note shown under a list that was cut at LIST_MAX_ROWS, or null when everything is shown. */
export function truncationNote(shown: number, total: number, noun: string): string | null {
  return total > shown ? `Showing the newest ${shown.toLocaleString("en-ZA")} of ${total.toLocaleString("en-ZA")} ${noun}. Narrow the list with the filter to see the rest.` : null
}

/** The search text to send, or undefined when blank. Trimmed and cut to the length the server accepts. */
export function searchParam(text: string): string | undefined {
  const t = text.trim().slice(0, MAX_SEARCH).trim()
  return t === "" ? undefined : t
}

export function lastPage(total: number, size: number = PAGE_SIZE): number {
  return total <= 0 ? 0 : Math.ceil(total / size) - 1
}

/** A page number that exists: 0 when empty, the last page when it is past the end (after a delete, say). */
export function clampPage(page: number, total: number, size: number = PAGE_SIZE): number {
  return Math.min(Math.max(0, page), lastPage(total, size))
}

/** "Showing 51–100 of 120 animals", "3 animals", "No animals found" or "No animals match “ox”". */
export function rangeLabel(page: number, total: number, noun: string, search?: string, size: number = PAGE_SIZE): string {
  const plural = (n: number) => `${noun}${n === 1 ? "" : "s"}`
  if (total <= 0) return search ? `No ${noun}s match “${search}”` : `No ${noun}s found`
  const first = page * size + 1
  const last = Math.min(total, first + size - 1)
  if (first > total) return `${total} ${plural(total)}`
  if (total <= size) return `${total} ${plural(total)}${search ? ` match “${search}”` : ""}`
  return `Showing ${first}–${last} of ${total} ${plural(total)}${search ? ` matching “${search}”` : ""}`
}

export interface PageLike<T> { content: T[]; totalElements: number }

/**
 * Every row of a list for a picker, up to 2,000. Stops at the reported total, an empty page or the page limit.
 * `truncated` says there were more rows than were loaded.
 */
export async function fetchAllPages<T>(fetchPage: (page: number, size: number) => Promise<PageLike<T>>): Promise<{ items: T[]; total: number; truncated: boolean }> {
  const items: T[] = []
  let total = 0
  for (let page = 0; page < PICKER_MAX_PAGES; page++) {
    const res = await fetchPage(page, PICKER_PAGE)
    total = res.totalElements
    if (res.content.length === 0) break
    items.push(...res.content)
    if (items.length >= total) break
  }
  return { items, total, truncated: items.length < total }
}
