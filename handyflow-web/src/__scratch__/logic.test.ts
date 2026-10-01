import { describe, it, expect } from 'vitest'
import type { Column, Task } from '../pages/tasks/tasks.types'
import { applyMove, boardStats, filterTasks, groupByColumn, NO_FILTERS, UNASSIGNED } from '../pages/tasks/tasks.logic'
import { categoryOf, isOverdueDate, parseDate, todayISO } from '../pages/tasks/tasks.constants'

const col = (id: string, name: string, sortOrder: number, category?: Column['category'], isDoneColumn = false): Column =>
  ({ id, name, sortOrder, color: null, isDoneColumn, category })
const COLS = [col('todo', 'To Do', 0, 'TODO'), col('qa', 'QA', 1, 'IN_REVIEW'), col('done', 'Done', 2, 'DONE', true)]
const task = (id: string, columnId: string, sortOrder: number, o: Partial<Task> = {}): Task => ({
  id, boardId: 'b', columnId, columnName: null, title: id, description: null, priority: 'NORMAL', status: 'TODO', assigneeId: null, assigneeName: null,
  dueDate: null, overdue: false, estimatedHours: null, loggedHours: null, sortOrder, linkedEntityType: null, linkedEntityId: null,
  commentCount: 0, checklistTotal: 0, checklistCompleted: 0, comments: [], createdAt: '2026-01-01T00:00:00Z', updatedAt: '', completedAt: null, ...o })

describe('dates (business day, SAST)', () => {
  it('a task due today is NOT overdue all day, but is the next day', () => {
    const lateMorning = new Date('2026-09-30T10:00:00Z')            // 12:00 SAST on the 30th
    expect(isOverdueDate('2026-09-30', null, lateMorning)).toBe(false)
    expect(new Date('2026-09-30') < lateMorning).toBe(true)          // the OLD check: flagged overdue
    expect(isOverdueDate('2026-09-30', null, new Date('2026-09-30T23:00:00Z'))).toBe(true)   // 01:00 SAST on Oct 1
    expect(isOverdueDate('2026-09-29', null, lateMorning)).toBe(true)
    expect(isOverdueDate('2026-09-29', '2026-09-29T10:00:00Z', lateMorning)).toBe(false)     // completed
  })
  it('todayISO follows Johannesburg, not UTC', () => {
    expect(todayISO(new Date('2026-09-30T23:30:00Z'))).toBe('2026-10-01')
  })
  it('a date-only string is a local calendar day', () => {
    const d = parseDate('2026-09-30'); expect([d.getFullYear(), d.getMonth(), d.getDate()]).toEqual([2026, 8, 30])
  })
})

describe('categoryOf', () => {
  it('uses the server category when present', () => expect(categoryOf(col('x', 'Whatever', 0, 'BLOCKED'))).toBe('BLOCKED'))
  it('falls back for an older backend: done flag, then name', () => {
    expect(categoryOf(col('x', 'Finished', 0, undefined, true))).toBe('DONE')
    expect(categoryOf(col('x', 'Code Review', 0))).toBe('IN_REVIEW')
    expect(categoryOf(col('x', 'Doing', 0))).toBe('IN_PROGRESS')
    expect(categoryOf(col('x', 'Backlog', 0))).toBe('TODO')
  })
})

describe('applyMove (mirrors the server)', () => {
  const tasks = [task('a', 'todo', 0), task('b', 'todo', 1), task('q0', 'qa', 0), task('q1', 'qa', 1), task('q2', 'qa', 2)]
  const order = (ts: Task[], c: string) => groupByColumn(ts, COLS)[c].map(t => t.id)
  it('inserts at the index, renumbers 0..n-1, takes the target status', () => {
    const out = applyMove(tasks, COLS, 'a', 'qa', 1)
    expect(order(out, 'qa')).toEqual(['q0', 'a', 'q1', 'q2'])
    expect(groupByColumn(out, COLS).qa.map(t => t.sortOrder)).toEqual([0, 1, 2, 3])
    expect(out.find(t => t.id === 'a')).toMatchObject({ columnId: 'qa', status: 'IN_REVIEW', columnName: 'QA' })
  })
  it('the source column closes the gap only by ordering, other tasks keep their own column', () => {
    expect(order(applyMove(tasks, COLS, 'a', 'qa', 0), 'todo')).toEqual(['b'])
  })
  it('reorders within a column', () => expect(order(applyMove(tasks, COLS, 'q0', 'qa', 2), 'qa')).toEqual(['q1', 'q2', 'q0']))
  it('clamps an out-of-range index', () => {
    expect(order(applyMove(tasks, COLS, 'a', 'qa', 99), 'qa')).toEqual(['q0', 'q1', 'q2', 'a'])
    expect(order(applyMove(tasks, COLS, 'a', 'qa', -5), 'qa')).toEqual(['a', 'q0', 'q1', 'q2'])
  })
  it('into Done completes it; back out reopens it', () => {
    const done = applyMove(tasks, COLS, 'a', 'done', 0).find(t => t.id === 'a')!
    expect(done.status).toBe('DONE'); expect(done.completedAt).not.toBeNull()
    const back = applyMove(applyMove(tasks, COLS, 'a', 'done', 0), COLS, 'a', 'todo', 0).find(t => t.id === 'a')!
    expect(back.status).toBe('TODO'); expect(back.completedAt).toBeNull()
  })
  it('unknown task or column changes nothing', () => {
    expect(applyMove(tasks, COLS, 'zzz', 'qa', 0)).toBe(tasks)
    expect(applyMove(tasks, COLS, 'a', 'nope', 0)).toBe(tasks)
  })
  it('does not mutate its input', () => { const snap = JSON.stringify(tasks); applyMove(tasks, COLS, 'a', 'qa', 1); expect(JSON.stringify(tasks)).toBe(snap) })
})

describe('filterTasks', () => {
  const two = [task('1', 'todo', 0, { assigneeId: 'u1', assigneeName: 'Sam Nkosi' }), task('2', 'todo', 1, { assigneeId: 'u2', assigneeName: 'Sam Nkosi' }),
    task('3', 'todo', 2), task('4', 'todo', 3, { dueDate: '2026-09-01', status: 'TODO', priority: 'URGENT' })]
  it('filters by assignee ID, so two people with one name are told apart', () => {
    expect(filterTasks(two, { ...NO_FILTERS, assignee: 'u1' }).map(t => t.id)).toEqual(['1'])
  })
  it('unassigned', () => expect(filterTasks(two, { ...NO_FILTERS, assignee: UNASSIGNED }).map(t => t.id)).toEqual(['3', '4']))
  it('overdue and priority', () => {
    const now = new Date('2026-09-30T10:00:00Z')
    expect(filterTasks(two, { ...NO_FILTERS, overdue: true }, now).map(t => t.id)).toEqual(['4'])
    expect(filterTasks(two, { ...NO_FILTERS, priority: 'URGENT' }).map(t => t.id)).toEqual(['4'])
  })
  it('search is case-insensitive over title and assignee', () => expect(filterTasks(two, { ...NO_FILTERS, q: 'SAM' }).length).toBe(2))
})

describe('boardStats', () => {
  it('counts by COLUMN category, not by the task status field (which may lag)', () => {
    const ts = [task('a', 'qa', 0, { status: 'TODO' }), task('b', 'done', 0, { status: 'TODO' }), task('c', 'todo', 0, { dueDate: '2026-09-01' }), task('d', 'todo', 1, { status: 'CANCELLED' })]
    const s = boardStats(ts, COLS, new Date('2026-09-30T10:00:00Z'))
    expect(s).toMatchObject({ total: 3, inReview: 1, done: 1, todo: 1, overdue: 1, unassigned: 3 })
  })
  it('a task sitting in a Done column is never overdue', () => {
    expect(boardStats([task('x', 'done', 0, { dueDate: '2026-01-01', status: 'TODO' })], COLS, new Date('2026-09-30T10:00:00Z')).overdue).toBe(0)
  })
})
