// src/pages/tasks/TasksPage.tsx
//
// Orchestrates the Tasks module: which board/task/view is open (kept in the URL so links, the back
// button and notification deep links work), permissions, and the modals. The board itself lives in
// KanbanBoard; data access in tasks.api; the ordering/filter rules in tasks.logic.
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { usePermission } from '../../hooks/usePermission'
import { SectionedModulePage } from '../../components/shell/SectionedModulePage'
import { TASKS_SECTIONS } from '../../navigation/moduleSections'
import { toast } from '../../store/toast.store'
import type { Task, TasksView } from './tasks.types'
import { boardStats, filterTasks, type TaskFilters } from './tasks.logic'
import {
  downloadBoardExport, tasksKeys, useAssignableUsers, useBoardTasks, useBoards, useCompleteTask, useCreateTask,
  useDeleteTask, useMoveTask, useMyTasks, useSummary, useUpdateTask,
} from './tasks.api'
import { BoardList } from './BoardList'
import { MyTasksList } from './MyTasksList'
import { BoardToolbar } from './BoardToolbar'
import { BoardStatsStrip } from './StatsStrip'
import { KanbanBoard } from './KanbanBoard'
import { CalendarView } from './CalendarView'
import { TimelineView } from './TimelineView'
import { TaskDetailModal } from './TaskDetailModal'
import { CreateTaskModal } from './CreateTaskModal'
import { CreateBoardModal } from './CreateBoardModal'
import { BoardSettingsModal } from './BoardSettingsModal'

const VIEWS: TasksView[] = ['board', 'calendar', 'timeline']

export function TasksPage() {
  const canRead = usePermission('TASKS_READ')
  const canManage = usePermission('TASKS_MANAGE')
  const canAdmin = usePermission('TASKS_ADMIN')
  const qc = useQueryClient()
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const { section } = useParams<{ section?: string }>()

  // a board only means something inside the Boards section
  const boardId = section === 'my-tasks' ? null : params.get('board')
  const taskId = params.get('task')
  const view: TasksView = VIEWS.includes(params.get('view') as TasksView) ? (params.get('view') as TasksView) : 'board'
  const filters: TaskFilters = {
    q: params.get('q') ?? '', priority: params.get('p') ?? 'ALL', assignee: params.get('a') ?? 'ALL', overdue: params.get('od') === '1',
  }

  // URL helper: replace keeps the back button useful (only opening a board or a task pushes history)
  const setUrl = (patch: Record<string, string | null>, push = false) => {
    const next = new URLSearchParams(params)
    for (const [k, v] of Object.entries(patch)) { if (v === null || v === '') next.delete(k); else next.set(k, v) }
    setParams(next, { replace: !push })
  }
  const setFilters = (f: TaskFilters) => setUrl({
    q: f.q || null, p: f.priority === 'ALL' ? null : f.priority, a: f.assignee === 'ALL' ? null : f.assignee, od: f.overdue ? '1' : null,
  })

  const boardsQ = useBoards()
  const summaryQ = useSummary()
  const usersQ = useAssignableUsers()
  const myQ = useMyTasks(canRead && (section === 'my-tasks' || !boardId))
  const board = boardsQ.data?.find(b => b.id === boardId) ?? null
  const columns = useMemo(() => [...(board?.columns ?? [])].sort((a, b) => a.sortOrder - b.sortOrder), [board])
  const tasksQ = useBoardTasks(board ? board.id : null)
  const allTasks = useMemo(() => tasksQ.data?.tasks ?? [], [tasksQ.data])
  const total = tasksQ.data?.total ?? 0

  const visible = useMemo(() => filterTasks(allTasks, filters), [allTasks, params]) // eslint-disable-line react-hooks/exhaustive-deps
  const stats = useMemo(() => boardStats(allTasks, columns), [allTasks, columns])
  const selected: Task | null = allTasks.find(t => t.id === taskId) ?? null

  const move = useMoveTask(boardId ?? '', columns)
  const create = useCreateTask(boardId ?? '')
  const updateTask = useUpdateTask(boardId)
  const del = useDeleteTask(boardId)
  const complete = useCompleteTask(boardId)

  const [showCreate, setShowCreate] = useState<{ columnId?: string } | null>(null)
  const [showNewBoard, setShowNewBoard] = useState(false)
  const [settings, setSettings] = useState<'general' | 'lists' | null>(null)

  // a link to a board that no longer exists (archived, or another tenant's) should not leave a blank page
  useEffect(() => {
    if (boardId && boardsQ.isSuccess && !board) { toast.info("That board isn't available any more."); setUrl({ board: null, task: null }) }
  }, [boardId, boardsQ.isSuccess, board]) // eslint-disable-line react-hooks/exhaustive-deps
  // ...and likewise a task link once the board's tasks have loaded
  useEffect(() => {
    if (taskId && tasksQ.isSuccess && !selected) { toast.info("That task isn't on this board any more."); setUrl({ task: null }) }
  }, [taskId, tasksQ.isSuccess, selected]) // eslint-disable-line react-hooks/exhaustive-deps

  if (!canRead) {
    return <div role="alert" style={{ padding: 40, color: 'var(--hf-text-muted)' }}>You don't have permission to view tasks. Ask an administrator for access.</div>
  }

  const refresh = () => { if (boardId) qc.invalidateQueries({ queryKey: tasksKeys.board(boardId) }) }
  const moveTo = (id: string, columnId: string, index: number) => { if (canManage) move.mutate({ taskId: id, columnId, index }) }
  const endOf = (columnId: string) => allTasks.filter(t => t.columnId === columnId).length

  return (
    <SectionedModulePage config={TASKS_SECTIONS}
      subtitle="Boards · Assignments · Due dates · Time tracking"
      detail={section !== 'my-tasks' && board ? { label: board.name, subtitle: 'Board' } : undefined}
      render={id => {
        switch (id) {
          case 'boards': return (
            !board ? (
              <BoardList boards={boardsQ.data} loading={boardsQ.isLoading} error={boardsQ.isError} onRetry={() => boardsQ.refetch()}
                summary={summaryQ.data} myTasks={myQ.data ?? []} canAdmin={canAdmin}
                onOpenBoard={id => setUrl({ board: id }, true)} onOpenTask={t => setUrl({ board: t.boardId, task: t.id }, true)} onNewBoard={() => setShowNewBoard(true)}
            onViewAllMine={() => navigate(`${TASKS_SECTIONS.basePath}/my-tasks`)} />
            ) : (
              <>
                <BoardToolbar board={board} taskCount={stats.total} view={view} onView={v => setUrl({ view: v === 'board' ? null : v })}
                  filters={filters} onFilters={setFilters} users={usersQ.data ?? []} canManage={canManage} canAdmin={canAdmin}
                  onBack={() => setParams(new URLSearchParams(), { replace: false })} onNewTask={() => setShowCreate({})}
                  onAddList={() => setSettings('lists')} onSettings={() => setSettings('general')}
                  onExport={kind => downloadBoardExport(board.id, kind, board.name)} />

                {total > allTasks.length && (
                  <div role="status" style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12, padding: '10px 14px', borderRadius: 10, fontSize: 13,
                    background: 'var(--hf-warning-soft)', border: '1px solid var(--hf-warning-border)', color: 'var(--hf-warning-text-strong)' }}>
                    <AlertTriangle size={15} />Showing the first {allTasks.length} of {total} tasks. Use filters or archive finished work to see the rest.
                  </div>
                )}
                {tasksQ.isError && (
                  <div role="alert" style={{ marginBottom: 12, padding: '10px 14px', borderRadius: 10, fontSize: 13, background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', color: 'var(--hf-danger-text)' }}>
                    Couldn't load this board's tasks. <button type="button" onClick={() => tasksQ.refetch()} style={{ background: 'none', border: 'none', textDecoration: 'underline', cursor: 'pointer', color: 'inherit', fontWeight: 700 }}>Try again</button>
                  </div>
                )}

                <BoardStatsStrip stats={stats} />

                {view === 'board' && (
                  <KanbanBoard columns={columns} tasks={visible} canManage={canManage} canAdmin={canAdmin}
                    onOpen={t => setUrl({ task: t.id }, true)} onMove={moveTo} onComplete={t => complete.mutate(t.id)}
                    onQuickAdd={(columnId, title) => create.mutateAsync({ title, columnId })} onEditLists={() => setSettings('lists')} />
                )}
                {view === 'calendar' && <CalendarView tasks={visible} onTaskClick={t => setUrl({ task: t.id }, true)} />}
                {view === 'timeline' && <TimelineView tasks={visible} onTaskClick={t => setUrl({ task: t.id }, true)} />}
              </>
            )
          )
          case 'my-tasks': return (
            myQ.isError ? (
              <div role="alert" style={{ fontSize: 14, color: 'var(--hf-danger-text)' }}>
                We couldn't load your tasks. <button type="button" onClick={() => myQ.refetch()} style={{ background: 'none', border: 'none', textDecoration: 'underline', cursor: 'pointer', color: 'inherit', fontWeight: 700 }}>Try again</button>
              </div>
            ) : myQ.isLoading ? (
              <div style={{ fontSize: 14, color: 'var(--hf-text-muted)' }}>Loading your tasks…</div>
            ) : (
              <MyTasksList tasks={myQ.data ?? []} boards={boardsQ.data} onOpen={t => navigate(`${TASKS_SECTIONS.basePath}/boards?board=${t.boardId}&task=${t.id}`)} />
            )
          )
          default: return null
        }
      }}>
      {selected && board && (
        <TaskDetailModal key={selected.id} task={selected} columns={columns} users={usersQ.data ?? []} readOnly={!canManage}
          onClose={() => setUrl({ task: null })} onRefresh={refresh}
          onUpdate={data => updateTask.mutate({ id: selected.id, data })}
          onMove={columnId => moveTo(selected.id, columnId, endOf(columnId))}
          onComplete={() => complete.mutate(selected.id)}
          onDelete={() => del.mutate(selected.id, { onSuccess: () => setUrl({ task: null }) })} />
      )}
      {showCreate && board && (
        <CreateTaskModal columns={columns} boardId={board.id} defaultColumnId={showCreate.columnId ?? columns[0]?.id ?? ''} users={usersQ.data ?? []}
          onClose={() => setShowCreate(null)} onSaved={() => { setShowCreate(null); refresh(); qc.invalidateQueries({ queryKey: tasksKeys.summary }) }} />
      )}
      {showNewBoard && <CreateBoardModal onClose={() => setShowNewBoard(false)} onSaved={() => { setShowNewBoard(false); qc.invalidateQueries({ queryKey: tasksKeys.boards }) }} />}
      {settings && board && (
        <BoardSettingsModal board={board} columns={columns} initialTab={settings} onClose={() => setSettings(null)}
          onArchived={() => { setSettings(null); setUrl({ board: null, task: null }) }} />
      )}
    </SectionedModulePage>
  )
}

export default TasksPage
