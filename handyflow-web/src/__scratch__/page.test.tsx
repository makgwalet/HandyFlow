import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const perms = new Set<string>()
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
vi.mock('../hooks/usePermission', () => ({ usePermission: (p: string) => perms.has(p) }))

import { TasksPage } from '../pages/tasks/TasksPage'
import { useToastStore } from '../store/toast.store'

const COLUMNS = [
  { id: 'todo', name: 'To Do', sortOrder: 0, color: '#94A3B8', isDoneColumn: false, category: 'TODO' },
  { id: 'qa', name: 'QA', sortOrder: 1, color: '#F59E0B', isDoneColumn: false, category: 'IN_REVIEW' },
  { id: 'done', name: 'Done', sortOrder: 2, color: '#10B981', isDoneColumn: true, category: 'DONE' },
]
const BOARD = { id: 'b1', name: 'Operations', description: null, color: '#1B3A6B', isDefault: true, columns: COLUMNS }
const mk = (id: string, columnId: string, sortOrder: number, o: object = {}) => ({
  id, boardId: 'b1', columnId, columnName: null, title: `Task ${id}`, description: null, priority: 'NORMAL', status: 'TODO', assigneeId: null, assigneeName: null,
  dueDate: null, overdue: false, estimatedHours: null, loggedHours: null, sortOrder, linkedEntityType: null, linkedEntityId: null,
  commentCount: 0, checklistTotal: 0, checklistCompleted: 0, comments: [], createdAt: '2026-01-01T00:00:00Z', updatedAt: '', completedAt: null, ...o })
let tasks: object[] = []
let total: number | null = null
let boardsFail = false
let freezeTasks = false

beforeEach(() => {
  cleanup(); vi.clearAllMocks(); perms.clear(); useToastStore.setState({ toasts: [] }); boardsFail = false; freezeTasks = false; total = null
  perms.add('TASKS_READ'); perms.add('TASKS_MANAGE')
  tasks = [mk('t1', 'todo', 0, { assigneeId: 'u1', assigneeName: 'Sam Nkosi' }), mk('t2', 'todo', 1), mk('t3', 'qa', 0)]
  api.get.mockImplementation(async (url: string) => {
    if (url === '/api/v1/tasks/boards') { if (boardsFail) throw new Error('boom'); return { data: [BOARD] } }
    if (url.startsWith('/api/v1/tasks/boards/b1/tasks')) { if (freezeTasks) return new Promise(() => {}) }
    if (url.startsWith('/api/v1/tasks/boards/b1/tasks')) return { data: { content: tasks, totalElements: total ?? tasks.length } }
    if (url === '/api/v1/tasks/summary') return { data: { totalTasks: 3, todoCount: 2, inProgressCount: 0, inReviewCount: 1, doneCount: 0, overdueCount: 0, myTasksCount: 1 } }
    if (url === '/api/v1/tasks/assignable-users') return { data: [{ id: 'u1', name: 'Sam Nkosi' }, { id: 'u2', name: 'Lee Dlamini' }] }
    if (url === '/api/v1/tasks/my') return { data: [] }
    return { data: null }
  })
  api.post.mockResolvedValue({ data: null }); api.put.mockResolvedValue({ data: null })
})

const open = (search = '/boards?board=b1') => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false }, mutations: { retry: false } } })
  const Where = () => { const l = useLocation(); return <div data-testid="where">{l.pathname + l.search}</div> }
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[`/tasks${search}`]}>
    <Routes><Route path="/tasks/:section?" element={<><TasksPage /><Where /></>} /></Routes></MemoryRouter></QueryClientProvider>)
}
const column = (name: string) => screen.getByRole('region', { name: new RegExp(`^${name},`) })
const moveViaMenu = async (taskTitle: string, target: string) => {
  fireEvent.click(await screen.findByRole('button', { name: `Actions for ${taskTitle}` }))
  fireEvent.click(await screen.findByRole('menuitem', { name: new RegExp(`^${target}`) }))
}

describe('board page', () => {
  it('shows the lists with their tasks and a count', async () => {
    open()
    expect(await screen.findByText('Task t1')).toBeTruthy()
    expect(column('To Do').textContent).toContain('Task t2')
    expect(column('QA').textContent).toContain('Task t3')
    expect(column('To Do').getAttribute('aria-label')).toBe('To Do, 2 tasks')
  })

  it('moving a card posts column + position and updates at once (optimistic)', async () => {
    let release!: () => void
    api.post.mockImplementation(() => new Promise(r => { release = () => r({ data: null }) }))
    open(); await screen.findByText('Task t1')
    await moveViaMenu('Task t1', 'QA')
    await waitFor(() => expect(column('QA').textContent).toContain('Task t1'))          // before the server answered
    expect(column('To Do').textContent).not.toContain('Task t1')
    expect(api.post).toHaveBeenCalledWith('/api/v1/tasks/t1/move', { columnId: 'qa', sortOrder: 1 })
    release()
  })

  it('a failed move puts the card back and says so', async () => {
    api.post.mockRejectedValue({ response: { status: 403, data: { message: 'nope' } } })
    open(); await screen.findByText('Task t1')
    freezeTasks = true            // the refetch after the failure never answers: only a rollback can restore the card
    await moveViaMenu('Task t1', 'QA')
    await waitFor(() => expect(useToastStore.getState().toasts.some(t => t.kind === 'error' && /permission/.test(t.message))).toBe(true))
    await waitFor(() => expect(column('To Do').textContent).toContain('Task t1'))
    expect(column('QA').textContent).not.toContain('Task t1')
  })

  it('TASKS_READ only: no create, no add, no move menu', async () => {
    perms.delete('TASKS_MANAGE')
    open(); await screen.findByText('Task t1')
    expect(screen.queryByRole('button', { name: /new task/i })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Add a task to/ })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Actions for Task t1' }))
    expect(await screen.findByRole('menuitem', { name: 'Open task' })).toBeTruthy()
    expect(screen.queryByRole('menuitem', { name: /^QA/ })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Board settings' })).toBeNull()
  })

  it('admins get board settings and Add list', async () => {
    perms.add('TASKS_ADMIN'); open(); await screen.findByText('Task t1')
    expect(screen.getByRole('button', { name: 'Board settings' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Add list' })).toBeTruthy()
  })

  it('no TASKS_READ: a clear message instead of a blank page', async () => {
    perms.clear(); open()
    expect((await screen.findByRole('alert')).textContent).toMatch(/permission/)
  })

  it('search filters the cards', async () => {
    open(); await screen.findByText('Task t1')
    fireEvent.change(screen.getByLabelText('Search tasks'), { target: { value: 't3' } })
    await waitFor(() => expect(screen.queryByText('Task t1')).toBeNull())
    expect(screen.getByText('Task t3')).toBeTruthy()
  })

  it('warns when the board has more tasks than were loaded', async () => {
    total = 731; open()
    expect(await screen.findByText(/first 3 of 731/)).toBeTruthy()
  })

  it('a failed boards load shows a retry, not an empty page', async () => {
    boardsFail = true; open('/boards')
    expect((await screen.findByRole('alert')).textContent).toMatch(/couldn't load your boards/i)
  })
})

describe('task links and editing', () => {
  it('?task= opens that task (deep links from notifications)', async () => {
    open('/boards?board=b1&task=t1')
    expect(await screen.findByRole('heading', { name: 'Task t1' })).toBeTruthy()
  })

  it('a stale task link is dropped with a message, not a blank modal', async () => {
    open('/boards?board=b1&task=gone')
    await waitFor(() => expect(useToastStore.getState().toasts.some(t => /isn't on this board/.test(t.message))).toBe(true))
  })

  it('choosing Unassigned sends clearAssignee (null used to mean "no change")', async () => {
    open('/boards?board=b1&task=t1')
    const select = await screen.findByDisplayValue('Sam Nkosi')
    fireEvent.change(select, { target: { value: '' } })
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/v1/tasks/t1', { clearAssignee: true }))
  })

  it('clearing the due date and editing priority send the right payloads', async () => {
    tasks = [mk('t1', 'todo', 0, { dueDate: '2026-12-01' })]
    open('/boards?board=b1&task=t1')
    const due = await screen.findByDisplayValue('2026-12-01')
    fireEvent.change(due, { target: { value: '' } })
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/v1/tasks/t1', { clearDueDate: true }))
    fireEvent.change(screen.getByDisplayValue('Normal'), { target: { value: 'URGENT' } })
    await waitFor(() => expect(api.put).toHaveBeenCalledWith('/api/v1/tasks/t1', { priority: 'URGENT' }))
  })

  it('read-only users see the task but cannot edit it', async () => {
    perms.delete('TASKS_MANAGE'); open('/boards?board=b1&task=t1')
    const select = await screen.findByDisplayValue('Sam Nkosi') as HTMLSelectElement
    expect(select.disabled).toBe(true)
    expect(screen.queryByText('Mark done')).toBeNull()
  })

  it('an out-of-range estimate is refused with a message, nothing is sent', async () => {
    open('/boards?board=b1&task=t1')
    const est = await screen.findByLabelText(/Estimated/i).catch(() => null)
    const input = (est ?? document.querySelector('input[type=number]')) as HTMLInputElement
    fireEvent.change(input, { target: { value: '99999' } }); fireEvent.blur(input)
    expect(api.put).not.toHaveBeenCalled()
    expect(useToastStore.getState().toasts.some(t => /between 0 and 9999/.test(t.message))).toBe(true)
    expect(within(document.body).getAllByText(/Estimated/).length).toBeGreaterThan(0)
  })
})

describe('sections and deep links', () => {
  it('a notification link /tasks?board=&task= is redirected to /tasks/boards KEEPING its query, and opens the task', async () => {
    open('?board=b1&task=t1')
    expect(await screen.findByRole('heading', { name: 'Task t1' })).toBeTruthy()
    expect(screen.getByTestId('where').textContent).toBe('/tasks/boards?board=b1&task=t1')
  })
  it('the page header names the open board', async () => {
    open(); await screen.findByText('Task t1')
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('Operations')
  })
  it('My tasks lists everything assigned to me and opens the task on its board', async () => {
    api.get.mockImplementation(async (url: string) => {
      if (url === '/api/v1/tasks/boards') return { data: [BOARD] }
      if (url === '/api/v1/tasks/my') return { data: [mk('m1', 'todo', 0, { title: 'Mine one', dueDate: '2020-01-01' }), mk('m2', 'qa', 0, { title: 'Mine two' })] }
      return { data: null }
    })
    open('/my-tasks')
    fireEvent.click(await screen.findByText('Mine one'))
    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/tasks/boards?board=b1&task=m1'))
  })
  it('a ?board= on the My tasks section is ignored (no board modals over the wrong section)', async () => {
    open('/my-tasks?board=b1&task=t1')
    await screen.findByRole('heading', { level: 1, name: 'My tasks' })
    expect(screen.queryByRole('heading', { name: 'Task t1' })).toBeNull()
  })
})
