// What the engagement detail page and the workpapers split view show. No server calls.

import type { EngagementMilestone, EngagementPhase, WorkpaperFileRow } from "../../api/auditEngagementSummary.api"
import type { Tone } from "./auditUi.logic"

export const MILESTONE_STYLE: Record<EngagementMilestone["status"], { label: string; tone: Tone }> = {
  DONE: { label: "Completed", tone: "success" },
  CURRENT: { label: "In progress", tone: "warning" },
  PENDING: { label: "Pending", tone: "muted" },
  LATE: { label: "Late", tone: "warning" },
}

/** The colour of a phase bar: nothing yet is grey, finished is green, anything between is the main colour. */
export const phaseColor = (p: EngagementPhase) => p.pct >= 100 ? "var(--hf-success-text)" : p.pct === 0 ? "var(--hf-border-strong)" : "var(--hf-primary)"

export const WORKPAPER_STATUS: Record<string, { label: string; tone: Tone }> = {
  DRAFT: { label: "Draft", tone: "muted" },
  PREPARED: { label: "In review", tone: "warning" },
  REVIEWED: { label: "Reviewed", tone: "info" },
  SIGNED_OFF: { label: "Approved", tone: "success" },
}
export const workpaperStatus = (s: string) => WORKPAPER_STATUS[s] ?? { label: s, tone: "muted" as Tone }

export interface WorkpaperRow extends WorkpaperFileRow { folder: string; preparer: string | null; updatedAt: string }

/** The current versions only, each with its folder's name, who prepared it and when it last moved, newest change first. */
export function workpaperRows(folders: { id: string; name: string }[], filesByFolder: Record<string, WorkpaperFileRow[]>, names: Record<string, string>): WorkpaperRow[] {
  const out: WorkpaperRow[] = []
  for (const f of folders) {
    for (const w of filesByFolder[f.id] ?? []) {
      if (w.supersededBy) continue
      out.push({ ...w, folder: f.name, preparer: w.preparedBy ? names[w.preparedBy] ?? null : null, updatedAt: w.signedOffAt ?? w.reviewedAt ?? w.preparedAt ?? w.createdAt })
    }
  }
  return out.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt) || a.fileName.localeCompare(b.fileName))
}

export function filterWorkpapers(rows: WorkpaperRow[], f: { status: string; folder: string; q: string }): WorkpaperRow[] {
  const q = f.q.trim().toLowerCase()
  return rows.filter(r =>
    (f.status === "all" || r.reviewStatus === f.status) && (f.folder === "all" || r.folderId === f.folder) &&
    (q === "" || r.fileName.toLowerCase().includes(q) || r.folder.toLowerCase().includes(q) || (r.preparer ?? "").toLowerCase().includes(q)))
}

/** "2.4 MB", "310 KB", "512 B"; empty for no size. */
export function fileSize(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || bytes < 0) return ""
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export const personName = (id: string | null | undefined, names: Record<string, string>) => (id ? names[id] ?? "Unknown user" : "—")

/** People by id from the user list the page already loads. */
export function nameMap(users: { id: string; firstName: string; lastName: string }[]): Record<string, string> {
  const m: Record<string, string> = {}
  for (const u of users) m[u.id] = `${u.firstName} ${u.lastName}`.trim()
  return m
}
