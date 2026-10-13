// Break-glass review (patch 0189): the rows the reviewer sees and what each one needs.

export interface BreakGlassRow {
  id: string; patientId: string; patientName?: string | null; userId: string; userName?: string | null; reason: string
  startedAt: string; expiresAt: string; itemsViewed: number; documents: number; acknowledgedAt?: string | null; acknowledgedNote?: string | null
}

export const isReviewed = (r: BreakGlassRow) => !!r.acknowledgedAt

/** How many are waiting for a reviewer. */
export const waiting = (rows: BreakGlassRow[]) => rows.filter(r => !isReviewed(r)).length

/** "3 viewed · 1 document printed or exported", or "Nothing opened after the record itself". */
export function activity(r: BreakGlassRow): string {
  const parts: string[] = []
  if (r.itemsViewed > 0) parts.push(`${r.itemsViewed} viewed`)
  if (r.documents > 0) parts.push(`${r.documents} document${r.documents === 1 ? "" : "s"} printed or exported`)
  return parts.length ? parts.join(" · ") : "Nothing opened after the record itself"
}

/** Rows that need a closer look first: documents taken out of the record, then the most views. */
export const needsCloserLook = (r: BreakGlassRow) => r.documents > 0

export function headline(rows: BreakGlassRow[]): string {
  const n = waiting(rows)
  return n === 0 ? "Nothing is waiting for review." : `${n} break-glass ${n === 1 ? "access is" : "accesses are"} waiting for review.`
}
