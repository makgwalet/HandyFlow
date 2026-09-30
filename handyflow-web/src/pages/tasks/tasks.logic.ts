// src/pages/tasks/tasks.logic.ts
//
// Pure rules for the board: no React, no network, so they can be tested directly. Where a rule
// mirrors the backend (how a move renumbers a column, what makes a task overdue), keep them in step.
import type { Column, Task } from './tasks.types'
import { categoryOf, isOverdueDate } from './tasks.constants'

// -- Filters -----------------------------------------------------------------

export interface TaskFilters { q: string; priority: string; assignee: string; overdue: boolean }
export const NO_FILTERS: TaskFilters = { q: '', priority: 'ALL', assignee: 'ALL', overdue: false }
/** The assignee filter value that means "tasks with nobody assigned". */
export const UNASSIGNED = 'NONE'

export const countActiveFilters = (f: TaskFilters) =>
  [f.q.trim() !== '', f.priority !== 'ALL', f.assignee !== 'ALL', f.overdue].filter(Boolean).length

/** Overdue = due before today (business day) and still open. Mirrors Task.isOverdue() on the server. */
export const isTaskOverdue = (t: Pick<Task, 'dueDate' | 'status'>, now: Date = new Date()) =>
  t.status !== 'DONE' && t.status !== 'CANCELLED' && isOverdueDate(t.dueDate, null, now)

export function filterTasks(tasks: Task[], f: TaskFilters, now: Date = new Date()): Task[] {
  const q = f.q.trim().toLowerCase()
  return tasks.filter(t => {
    if (q && !(t.title.toLowerCase().includes(q)
            || t.description?.toLowerCase().includes(q)
            || t.assigneeName?.toLowerCase().includes(q))) return false
    if (f.priority !== 'ALL' && t.priority !== f.priority) return false
    // by id, not by display name: two people can share a name
    if (f.assignee === UNASSIGNED) { if (t.assigneeId) return false }
    else if (f.assignee !== 'ALL' && t.assigneeId !== f.assignee) return false
    if (f.overdue && !isTaskOverdue(t, now)) return false
    return true
  })
}

// -- Grouping and moving ----------------------------------------------------

const bySort = (a: Task, b: Task) => a.sortOrder - b.sortOrder || a.createdAt.localeCompare(b.createdAt)

/** Tasks per column, each column ordered by position. Every column appears, even when empty. */
export function groupByColumn(tasks: Task[], columns: Column[]): Record<string, Task[]> {
  const out: Record<string, Task[]> = {}
  for (const c of columns) out[c.id] = []
  for (const t of tasks) if (out[t.columnId]) out[t.columnId].push(t)
  for (const id of Object.keys(out)) out[id].sort(bySort)
  return out
}

/**
 * Applies a move locally before the server answers (optimistic UI): the task takes the target
 * column's status and sits at `toIndex`, and the column is renumbered 0..n-1. This is the same rule
 * TasksService.moveTask applies on the server, so the refetch that follows changes nothing visible.
 */
export function applyMove(tasks: Task[], columns: Column[], taskId: string, toColumnId: string, toIndex: number,
                          now: Date = new Date()): Task[] {
  const moving = tasks.find(t => t.id === taskId)
  const target = columns.find(c => c.id === toColumnId)
  if (!moving || !target) return tasks

  const category = categoryOf(target)
  const siblings = tasks.filter(t => t.columnId === toColumnId && t.id !== taskId).sort(bySort)
  const index = Math.max(0, Math.min(toIndex, siblings.length))
  const moved: Task = {
    ...moving,
    columnId: toColumnId,
    columnName: target.name,
    status: category,
    completedAt: category === 'DONE' ? (moving.completedAt ?? now.toISOString()) : null,
  }
  const ordered = [...siblings.slice(0, index), moved, ...siblings.slice(index)]
  const position = new Map(ordered.map((t, i) => [t.id, i]))
  return tasks.map(t => (position.has(t.id) ? { ...(t.id === taskId ? moved : t), sortOrder: position.get(t.id)! } : t))
}

// -- Stats -------------------------------------------------------------------

export interface BoardStats {
  total: number; todo: number; inProgress: number; inReview: number; blocked: number
  done: number; overdue: number; unassigned: number
}

/**
 * Counts for ONE board, taken from each task's COLUMN category (the source of truth), not from the
 * task's own status field. The old page mixed tenant-wide and board-wide numbers in one strip.
 */
export function boardStats(tasks: Task[], columns: Column[], now: Date = new Date()): BoardStats {
  const category = new Map(columns.map(c => [c.id, categoryOf(c)]))
  const s: BoardStats = { total: 0, todo: 0, inProgress: 0, inReview: 0, blocked: 0, done: 0, overdue: 0, unassigned: 0 }
  for (const t of tasks) {
    if (t.status === 'CANCELLED') continue
    s.total++
    switch (category.get(t.columnId) ?? 'TODO') {
      case 'IN_PROGRESS': s.inProgress++; break
      case 'IN_REVIEW':   s.inReview++;   break
      case 'BLOCKED':     s.blocked++;    break
      case 'DONE':        s.done++;       break
      default:            s.todo++
    }
    if (category.get(t.columnId) !== 'DONE' && isTaskOverdue({ dueDate: t.dueDate, status: t.status }, now)) s.overdue++
    if (!t.assigneeId) s.unassigned++
  }
  return s
}
