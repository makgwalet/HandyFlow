import { apiClient } from "./client"

export type MyTaskKind = "ACTION" | "FINDING" | "NOTE_ANSWER" | "NOTE_CLEAR" | "ACTION_VALIDATE"
export interface MyTask {
  kind: MyTaskKind; id: string; engagementId: string | null; title: string; context: string | null
  severity: string | null; dueDate: string | null; overdue: boolean; daysOverdue: number
}
export interface MyTasks { summary: { total: number; overdue: number; dueThisWeek: number }; tasks: MyTask[] }

export const auditMyTasksApi = {
  mine: () => apiClient.get("/api/v1/internal-audit/my-tasks").then(r => (r.data?.data ?? r.data) as MyTasks),
}
