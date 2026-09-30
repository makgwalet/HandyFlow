// src/pages/tasks/tasks.api.ts
//
// Every request the Tasks pages make, as React Query hooks. Failures are reported with a toast
// (the old page caught errors and showed nothing, so a 403 looked like an empty board).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { toast, errorMessage } from '../../store/toast.store'
import { applyMove } from './tasks.logic'
import type { Board, Column, Summary, Task, TaskCategory, UserOption } from './tasks.types'

export const tasksKeys = {
  boards:  ['tasks', 'boards'] as const,
  summary: ['tasks', 'summary'] as const,
  users:   ['tasks', 'users'] as const,
  my:      ['tasks', 'my'] as const,
  board:   (id: string) => ['tasks', 'board', id] as const,
}

/** The API returns at most this many tasks per board load (there is no real paging yet). */
export const BOARD_PAGE_SIZE = 500

export interface BoardTasks { tasks: Task[]; total: number }

// -- Reads ------------------------------------------------------------------

export function useBoards() {
  return useQuery<Board[]>({
    queryKey: tasksKeys.boards,
    queryFn: async () => ((await apiClient.get('/api/v1/tasks/boards')).data ?? []) as Board[],
  })
}

export function useSummary() {
  return useQuery<Summary | null>({
    queryKey: tasksKeys.summary,
    queryFn: async () => ((await apiClient.get('/api/v1/tasks/summary')).data ?? null) as Summary | null,
    refetchInterval: 60_000,
  })
}

export function useAssignableUsers() {
  return useQuery<UserOption[]>({
    queryKey: tasksKeys.users,
    queryFn: async () => ((await apiClient.get('/api/v1/tasks/assignable-users')).data ?? []) as UserOption[],
    staleTime: 5 * 60_000,
  })
}

/**
 * One board's tasks. Polls every 30s (only while the tab is visible) and refetches on window focus,
 * so two people on the same board see each other's changes without reloading.
 */
export function useBoardTasks(boardId: string | null) {
  return useQuery<BoardTasks>({
    queryKey: tasksKeys.board(boardId ?? 'none'),
    enabled: !!boardId,
    refetchInterval: 30_000,
    queryFn: async () => {
      const page = (await apiClient.get(`/api/v1/tasks/boards/${boardId}/tasks?size=${BOARD_PAGE_SIZE}`)).data as
        { content?: Task[]; totalElements?: number } | Task[] | null
      if (Array.isArray(page)) return { tasks: page, total: page.length }
      const tasks = page?.content ?? []
      return { tasks, total: page?.totalElements ?? tasks.length }
    },
  })
}

export function useMyTasks(enabled = true) {
  return useQuery<Task[]>({
    queryKey: tasksKeys.my,
    enabled,
    queryFn: async () => ((await apiClient.get('/api/v1/tasks/my')).data ?? []) as Task[],
  })
}

// -- Task writes ------------------------------------------------------------

/** A task edit. Omitted = unchanged. To REMOVE a value, set its clear flag (null does not mean "remove"). */
export interface TaskUpdate {
  title?: string; description?: string; priority?: string; assigneeId?: string
  dueDate?: string; estimatedHours?: number; linkedEntityType?: string; linkedEntityId?: string
  clearAssignee?: boolean; clearDueDate?: boolean; clearDescription?: boolean
  clearEstimatedHours?: boolean; clearLink?: boolean
}

export interface NewTask {
  title: string; description?: string; priority?: string; columnId?: string; assigneeId?: string
  dueDate?: string; estimatedHours?: number; linkedEntityType?: string; linkedEntityId?: string
}

function useInvalidate(boardId: string | null) {
  const qc = useQueryClient()
  return () => {
    if (boardId) qc.invalidateQueries({ queryKey: tasksKeys.board(boardId) })
    qc.invalidateQueries({ queryKey: tasksKeys.summary })
    qc.invalidateQueries({ queryKey: tasksKeys.my })
  }
}

export function useMoveTask(boardId: string, columns: Column[]) {
  const qc = useQueryClient()
  const invalidate = useInvalidate(boardId)
  return useMutation({
    mutationFn: ({ taskId, columnId, index }: { taskId: string; columnId: string; index: number }) =>
      apiClient.post(`/api/v1/tasks/${taskId}/move`, { columnId, sortOrder: index }),
    onMutate: async ({ taskId, columnId, index }) => {
      await qc.cancelQueries({ queryKey: tasksKeys.board(boardId) })
      const previous = qc.getQueryData<BoardTasks>(tasksKeys.board(boardId))
      if (previous) {
        qc.setQueryData<BoardTasks>(tasksKeys.board(boardId),
          { ...previous, tasks: applyMove(previous.tasks, columns, taskId, columnId, index) })
      }
      return { previous }
    },
    onError: (e, _vars, ctx) => {
      if (ctx?.previous) qc.setQueryData(tasksKeys.board(boardId), ctx.previous)   // put the card back
      toast.error(errorMessage(e, "Couldn't move that task."))
    },
    onSettled: invalidate,
  })
}

export function useCreateTask(boardId: string) {
  const invalidate = useInvalidate(boardId)
  return useMutation({
    mutationFn: (t: NewTask) => apiClient.post(`/api/v1/tasks/boards/${boardId}/tasks`, t),
    onSuccess: invalidate,
    onError: e => toast.error(errorMessage(e, "Couldn't create the task.")),
  })
}

export function useUpdateTask(boardId: string | null) {
  const invalidate = useInvalidate(boardId)
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: TaskUpdate }) => apiClient.put(`/api/v1/tasks/${id}`, data),
    onSuccess: invalidate,
    onError: e => toast.error(errorMessage(e, "Couldn't save your change.")),
  })
}

export function useDeleteTask(boardId: string | null) {
  const invalidate = useInvalidate(boardId)
  return useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/tasks/${id}`),
    onSuccess: () => { invalidate(); toast.success('Task deleted') },
    onError: e => toast.error(errorMessage(e, "Couldn't delete the task.")),
  })
}

export function useCompleteTask(boardId: string | null) {
  const invalidate = useInvalidate(boardId)
  return useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/tasks/${id}/complete`),
    onSuccess: invalidate,
    onError: e => toast.error(errorMessage(e, "Couldn't complete the task.")),
  })
}

// -- Boards and columns (TASKS_ADMIN) ----------------------------------------

export interface ColumnInput {
  name: string; color: string | null; sortOrder: number; isDoneColumn: boolean; category: TaskCategory
}

export function useCreateBoard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (b: { name: string; description?: string; color?: string }) => apiClient.post('/api/v1/tasks/boards', b),
    onSuccess: () => qc.invalidateQueries({ queryKey: tasksKeys.boards }),
    onError: e => toast.error(errorMessage(e, "Couldn't create the board.")),
  })
}

export function useUpdateBoard(boardId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (b: { name: string; description?: string; color?: string }) => apiClient.put(`/api/v1/tasks/boards/${boardId}`, b),
    onSuccess: () => qc.invalidateQueries({ queryKey: tasksKeys.boards }),
    onError: e => toast.error(errorMessage(e, "Couldn't save the board.")),
  })
}

export function useArchiveBoard(boardId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => apiClient.post(`/api/v1/tasks/boards/${boardId}/archive`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: tasksKeys.boards }); qc.invalidateQueries({ queryKey: tasksKeys.summary }) },
    onError: e => toast.error(errorMessage(e, "Couldn't archive the board.")),
  })
}

/** Column add / edit / delete. Columns come embedded in the boards list, and tasks move when one is deleted. */
export function useColumnMutations(boardId: string) {
  const qc = useQueryClient()
  const refresh = () => {
    qc.invalidateQueries({ queryKey: tasksKeys.boards })
    qc.invalidateQueries({ queryKey: tasksKeys.board(boardId) })
    qc.invalidateQueries({ queryKey: tasksKeys.summary })
  }
  const add = useMutation({
    mutationFn: (c: ColumnInput) => apiClient.post(`/api/v1/tasks/boards/${boardId}/columns`, c),
    onSuccess: refresh,
    onError: e => toast.error(errorMessage(e, "Couldn't add the column.")),
  })
  const update = useMutation({
    mutationFn: ({ id, c }: { id: string; c: ColumnInput }) => apiClient.put(`/api/v1/tasks/boards/${boardId}/columns/${id}`, c),
    onSuccess: refresh,
    onError: e => toast.error(errorMessage(e, "Couldn't save the column.")),
  })
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/tasks/boards/${boardId}/columns/${id}`),
    onSuccess: refresh,
    onError: e => toast.error(errorMessage(e, "Couldn't delete the column.")),
  })
  return { add, update, remove }
}

// -- Export -----------------------------------------------------------------

const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'board'

/** Downloads the board's PDF status report or timesheet CSV. */
export async function downloadBoardExport(boardId: string, kind: 'pdf' | 'timesheet', boardName: string) {
  try {
    const res = await apiClient.get(`/api/v1/tasks/boards/${boardId}/export/${kind}`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${slug(boardName)}-${kind === 'pdf' ? 'status-report.pdf' : 'timesheet.csv'}`
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    toast.error(errorMessage(e, "Couldn't export the board."))
  }
}
