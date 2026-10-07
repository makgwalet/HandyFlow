// src/pages/security/guardDirectory.logic.ts
//
// Pure rules for the Guards list: the query the server understands, sort toggling, paging numbers, the compliance flags on
// a row, "last seen" wording, and the CSV for selected rows. No React, no network.
import type { ChipTone } from "../../components/ui/Chip"

export type DirSort = "name" | "grade" | "status" | "psira" | "activity"
export interface DirQuery {
  search: string; status: string; grade: string; psira: string; screening: string
  sort: DirSort; dir: "asc" | "desc"; page: number; size: number
}
export const DEFAULT_DIR_QUERY: DirQuery = { search: "", status: "ALL", grade: "ALL", psira: "ALL", screening: "ALL", sort: "name", dir: "asc", page: 0, size: 25 }

export interface DirRow<G = any> { guard: G; psiraState: "EXPIRED" | "EXPIRING" | "VALID" | "NONE"; psiraDaysLeft: number | null; screeningStatus: string; lastActivityAt: string | null }
export interface DirCounts { total: number; byStatus: Record<string, number>; psiraExpired: number; psiraExpiring: number; screeningFlagged: number; screeningPending: number; screeningUnscreened: number }
export interface DirResult<G = any> { rows: DirRow<G>[]; totalElements: number; page: number; size: number; counts: DirCounts }

/** The query string for the server. Filters left on "ALL" or empty are left out. */
export function directoryParams(q: DirQuery): string {
  const p = new URLSearchParams()
  if (q.search.trim()) p.set("search", q.search.trim())
  for (const k of ["status", "grade", "psira", "screening"] as const) if (q[k] !== "ALL" && q[k] !== "") p.set(k, q[k])
  p.set("sort", q.sort); p.set("dir", q.dir); p.set("page", String(q.page)); p.set("size", String(q.size))
  return "?" + p.toString()
}

/** Clicking a column heading: the same column flips direction, a new one starts ascending (last activity starts with the most recent). */
export function nextSort(q: DirQuery, key: DirSort): DirQuery {
  if (q.sort === key) return { ...q, dir: q.dir === "asc" ? "desc" : "asc", page: 0 }
  return { ...q, sort: key, dir: key === "activity" ? "desc" : "asc", page: 0 }
}

/** Any change to a filter goes back to the first page. */
export function withFilter(q: DirQuery, change: Partial<Pick<DirQuery, "search" | "status" | "grade" | "psira" | "screening" | "size">>): DirQuery {
  return { ...q, ...change, page: 0 }
}

export function pageInfo(total: number, page: number, size: number) {
  const pages = Math.max(1, Math.ceil(total / Math.max(size, 1)))
  const from = total === 0 ? 0 : page * size + 1
  const to = Math.min(total, (page + 1) * size)
  return { pages, from, to, hasPrev: page > 0, hasNext: page + 1 < pages }
}

export interface Flag { key: string; label: string; tone: ChipTone }

/**
 * What needs a look on this guard's row: PSiRA registration and the screening roll-up. A guard with nothing wrong has no
 * flags. This is a quick scan, not Deployment Readiness: that is on the guard's Compliance tab and covers more checks.
 */
export function complianceFlags(r: Pick<DirRow, "psiraState" | "psiraDaysLeft" | "screeningStatus">): Flag[] {
  const out: Flag[] = []
  if (r.psiraState === "EXPIRED") out.push({ key: "psira", label: "PSiRA expired", tone: "bad" })
  else if (r.psiraState === "EXPIRING") out.push({ key: "psira", label: `PSiRA ${r.psiraDaysLeft ?? 0}d left`, tone: "warn" })
  else if (r.psiraState === "NONE") out.push({ key: "psira", label: "No PSiRA expiry on file", tone: "warn" })
  if (r.screeningStatus === "FLAGGED") out.push({ key: "screening", label: "Screening flagged", tone: "bad" })
  else if (r.screeningStatus === "PENDING") out.push({ key: "screening", label: "Screening pending", tone: "warn" })
  else if (r.screeningStatus === "UNSCREENED") out.push({ key: "screening", label: "Not screened", tone: "warn" })
  return out
}

/** "5 min ago", "3 h ago", "2 d ago", then a date. Null means the guard has no started shift and no scan. */
export function activityLabel(iso: string | null, now: Date = new Date()): string {
  if (!iso) return "No activity yet"
  const mins = Math.max(0, Math.round((now.getTime() - new Date(iso).getTime()) / 60000))
  if (mins < 1) return "Just now"
  if (mins < 60) return `${mins} min ago`
  if (mins < 60 * 24) return `${Math.floor(mins / 60)} h ago`
  if (mins < 60 * 24 * 7) return `${Math.floor(mins / (60 * 24))} d ago`
  return new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
}

const csvCell = (v: unknown) => {
  const s = v == null ? "" : String(v)
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

/** CSV of the selected rows. No ID number or bank details: this is a roster, not a personal-data export. */
export function guardsCsv(rows: DirRow[]): string {
  const head = ["Name", "Employee code", "Grade", "Status", "PSiRA number", "PSiRA expiry", "PSiRA state", "Screening", "Phone"]
  const lines = rows.map(r => [r.guard.fullName, r.guard.employeeCode, r.guard.grade, r.guard.status ?? "ACTIVE", r.guard.psiraNumber, r.guard.psiraExpiryDate, r.psiraState, r.screeningStatus, r.guard.phone].map(csvCell).join(","))
  return [head.join(","), ...lines].join("\r\n")
}

export interface BulkResult { id: string; name: string; ok: boolean; error?: string }
export function bulkSummary(results: BulkResult[]): { ok: number; failed: BulkResult[]; text: string } {
  const failed = results.filter(r => !r.ok), ok = results.length - failed.length
  const text = failed.length === 0
    ? `Updated ${ok} guard${ok === 1 ? "" : "s"}.`
    : `Updated ${ok} of ${results.length}. Not updated: ${failed.map(f => `${f.name} (${f.error ?? "refused"})`).join("; ")}.`
  return { ok, failed, text }
}
