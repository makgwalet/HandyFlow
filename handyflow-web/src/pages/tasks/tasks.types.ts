// src/pages/tasks/tasks.types.ts

/** Workflow stage of a column. A task's status is the category of the column it sits in. */
export type TaskCategory = 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'BLOCKED' | 'DONE'

/** `category` is optional so the UI still works against a backend that predates V304 (see categoryOf). */
export interface Column { id: string; name: string; sortOrder: number; color: string | null; isDoneColumn: boolean; category?: TaskCategory }
export interface Board  { id: string; name: string; description: string | null; color: string | null; isDefault: boolean; columns: Column[] }
export interface TaskComment { id: string; authorId: string; authorName: string; body: string; createdAt: string }
export interface TimeLog { id: string; userId: string; userName: string; hours: number; description: string | null; loggedDate: string }
export interface Attachment { id: string; fileName: string; contentType: string | null; sizeBytes: number; uploadedBy: string | null; uploadedByName: string; createdAt: string }
export interface ChecklistItem { id: string; text: string; completed: boolean; sortOrder: number; createdAt: string; completedAt: string | null }
export interface Task {
  id: string; boardId: string; columnId: string; columnName: string | null
  title: string; description: string | null
  priority: string; status: string
  assigneeId: string | null; assigneeName: string | null
  dueDate: string | null; overdue: boolean
  estimatedHours: number | null; loggedHours: number | null
  sortOrder: number
  linkedEntityType: string | null; linkedEntityId: string | null
  commentCount: number; checklistTotal: number; checklistCompleted: number; comments: TaskComment[]
  createdAt: string; updatedAt: string; completedAt: string | null
}
export interface Summary {
  totalTasks: number; todoCount: number; inProgressCount: number
  inReviewCount: number; doneCount: number; overdueCount: number; myTasksCount: number
}
export interface UserOption { id: string; name: string }

export type TasksView = 'board' | 'calendar' | 'timeline'
