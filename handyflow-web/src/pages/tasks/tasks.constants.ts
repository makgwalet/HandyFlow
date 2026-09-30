// src/pages/tasks/tasks.constants.ts
import type { Column, TaskCategory } from './tasks.types'

export const PRIORITY: Record<string, { label: string; color: string; bg: string; border: string; dot: string }> = {
  URGENT: { label: 'Urgent', color: 'var(--hf-danger-text-strong)', bg: 'var(--hf-danger-soft)', border: 'var(--hf-danger-border)', dot: 'var(--hf-danger)' },
  HIGH:   { label: 'High',   color: 'var(--hf-warning-text-strong)', bg: 'var(--hf-warning-soft)', border: 'var(--hf-warning-border)', dot: 'var(--hf-warning)' },
  NORMAL: { label: 'Normal', color: 'var(--hf-info-text)', bg: 'var(--hf-info-soft)', border: 'var(--hf-info-border)', dot: 'var(--hf-info)' },
  LOW:    { label: 'Low',    color: 'var(--hf-text-tertiary)', bg: 'var(--hf-surface-muted)', border: 'var(--hf-border)', dot: 'var(--hf-text-faint)' },
}
export const STATUS_COLOR: Record<string, string> = {
  TODO: 'var(--hf-text-faint)', IN_PROGRESS: 'var(--hf-info-text)', IN_REVIEW: 'var(--hf-warning-text)',
  DONE: 'var(--hf-success-text)', BLOCKED: 'var(--hf-danger-text)', CANCELLED: 'var(--hf-text-muted)',
}
export const ENTITY_TYPES = ['QUOTE','INVOICE','CUSTOMER','LEASE','EMPLOYEE','CREATIVE_JOB','AP_BILL','PROPERTY','TICKET']

// ── Helpers ────────────────────────────────────────────────────────────────
// -- Dates -------------------------------------------------------------------

/** The business time zone. "Today" and "overdue" follow it, matching the server (Task.today()). */
export const BUSINESS_TZ = 'Africa/Johannesburg'

/** Today's date as YYYY-MM-DD in the business time zone (the en-CA locale formats as ISO). */
export const todayISO = (now: Date = new Date()) =>
  now.toLocaleDateString('en-CA', { timeZone: BUSINESS_TZ })

/**
 * Parses a server date. A date-only value ("2026-09-30") is a calendar day, so it becomes a LOCAL
 * date. `new Date("2026-09-30")` would be UTC midnight, which is already 02:00 in South Africa.
 */
export const parseDate = (d: string): Date => {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(d)
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(d)
}
export const fmtDate = (d: string | null) =>
  d ? parseDate(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: '2-digit' }) : null
export const fmtDateFull = (d: string | null) =>
  d ? parseDate(d).toLocaleDateString('en-ZA', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' }) : '\u2014'

/** Overdue = due BEFORE today (business day) and not completed. Due today is not overdue yet. */
export const isOverdueDate = (dueDate: string | null, completed: string | null, now: Date = new Date()) =>
  !!dueDate && !completed && dueDate.slice(0, 10) < todayISO(now)

export const initials = (name: string | null) =>
  name ? name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase() : '?'
export const fmtFileSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// -- Column categories ---------------------------------------------------------
export const CATEGORIES: { value: TaskCategory; label: string }[] = [
  { value: 'TODO',        label: 'To do' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'IN_REVIEW',   label: 'In review' },
  { value: 'BLOCKED',     label: 'Blocked' },
  { value: 'DONE',        label: 'Done' },
]

/**
 * A column's category. The server sends it (V304); the fallback keeps the UI working against a
 * backend that has not been upgraded yet: the done flag first, then the name as it used to be guessed.
 */
export function categoryOf(col: Pick<Column, 'name' | 'isDoneColumn' | 'category'> | null | undefined): TaskCategory {
  if (!col) return 'TODO'
  if (col.category) return col.category
  if (col.isDoneColumn) return 'DONE'
  const n = col.name.toUpperCase()
  if (n.includes('BLOCK')) return 'BLOCKED'
  if (n.includes('REVIEW') || n.includes('TESTING')) return 'IN_REVIEW'
  if (n.includes('PROGRESS') || n.includes('DOING')) return 'IN_PROGRESS'
  return 'TODO'
}

// Board and column colours are saved data, so they are real hex values (never CSS variables).
export const BOARD_COLORS = ['#1B3A6B', '#0D9488', '#D97706', '#7C3AED', '#DC2626', '#0284C7', '#166534', '#374151']
export const COLUMN_COLORS = ['#94A3B8', '#3B82F6', '#F59E0B', '#EF4444', '#10B981', '#8B5CF6', '#EC4899', '#14B8A6']
