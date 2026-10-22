// Types and helpers shared by the performance screens.
export { card, errorText, input, label, plain, primary, td, th, unwrap } from "./attendance.shared"

export const BASE = "/api/v1/hr/performance"

export interface EventView { action: string; note: string | null; doneBy: string | null; doneAt: string }
export interface Me { employeeId: string | null; name: string | null; directReports: number; canRead: boolean; canManage: boolean }

export interface CycleRow {
  id: string; name: string; periodStart: string; periodEnd: string; selfDue: string; managerDue: string; include360: boolean; status: string
  total: number; awaitingSelf: number; awaitingManager: number; submitted: number; released: number; excluded: number
}
export interface ReviewRow {
  id: string; cycleId: string; cycleName: string; employeeId: string; employeeName: string; jobTitle: string | null; reviewerId: string | null; reviewerName: string | null
  status: string; selfDue: string; managerDue: string; rating: number | null; sharedAt: string | null; ackAt: string | null
}
export interface CycleDetail { row: CycleRow; reviews: ReviewRow[]; withoutReviewer: number; events: EventView[]; actions: string[] }

export interface FeedbackView {
  id: string; relationship: string; status: string; providerName: string | null; requestedAt: string; respondedAt: string | null
  rating: number | null; strengths: string | null; improvements: string | null
}
export interface SummaryView { shown: boolean; replies: number; average: number | null; strengths: string[]; improvements: string[]; asked: number; declined: number; minReplies: number }
export interface FeedbackTask { id: string; aboutName: string; jobTitle: string | null; cycleName: string; relationship: string; status: string; dueDate: string; requestedAt: string }

export interface GoalView {
  id: string; employeeId: string; employeeName: string; cycleId: string | null; cycleName: string | null; title: string; description: string | null; measure: string | null
  dueDate: string; status: string; progress: number; overdue: boolean; outcomeNote: string | null; setByName: string | null; history: EventView[]; actions: string[]
}

export interface ReviewDetail {
  row: ReviewRow; include360: boolean; periodStart: string; periodEnd: string
  selfRating: number | null; selfComment: string | null; selfAt: string | null
  managerRating: number | null; managerComment: string | null; managerAt: string | null
  finalRating: number | null; calibrated: boolean; calibrationReason: string | null
  employeeComment: string | null; excludedReason: string | null
  feedback: SummaryView | null; requests: FeedbackView[]; goals: GoalView[]; events: EventView[]; actions: string[]; ratingLabels: string[]
}

export interface CheckinView { id: string; onDate: string; progress: string; note: string; doneBy: string | null; doneAt: string }
export interface PlanRow {
  id: string; employeeId: string; employeeName: string; ownerId: string | null; ownerName: string | null; caseId: string | null; caseNumber: string | null; title: string
  status: string; startDate: string; endDate: string; communicatedOn: string | null; shorterThanMinimum: boolean; lastProgress: string | null; acknowledged: boolean
}
export interface PlanDetail {
  row: PlanRow; shortfall: string; standard: string; support: string; closedOn: string | null; outcomeNote: string | null; ackAt: string | null
  employeeComment: string | null; minReviewDays: number; checkins: CheckinView[]; events: EventView[]; actions: string[]
}
