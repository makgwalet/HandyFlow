// Types and styles shared by the employee relations screens.
export { card, errorText, input, label, plain, primary, td, th, unwrap } from "./attendance.shared"

export interface Party { id: string; name: string }
export interface Charge { id: string; seq: number; charge: string; finding: string | null; findingNote: string | null }
export interface StepView { code: string; label: string; doneOn: string | null; note: string | null }
export interface EventView { action: string; note: string | null; visibleToEmployee: boolean; doneBy: string | null; doneAt: string }
export interface WarningView { caseId: string; number: string; sanction: string; issuedOn: string; validUntil: string | null }
export interface Hearing { noticeIssuedAt: string | null; date: string | null; time: string | null; venue: string | null; heldOn: string | null; employeeAttended: boolean | null; representative: string | null; notes: string | null }
export interface Decision {
  outcome: string | null; decidedOn: string | null; note: string | null; warningMonths: number | null; warningValidUntil: string | null
  employeeResponse: string | null; communicatedOn: string | null; dismissalRightsExplainedOn: string | null; ccmaDeadline: string | null
  acknowledged: boolean; acknowledgedAt: string | null
}
export interface Appeal { deadline: string | null; lodgedOn: string | null; grounds: string | null; decider: Party | null; decidedOn: string | null; outcome: string | null; note: string | null }
export interface Suspension { from: string; to: string | null; reason: string; active: boolean }
export interface Review { from: string; to: string; shorterThanMinimum: boolean }
export interface Advice { activeWarnings: WarningView[]; unknownValidity: WarningView[]; guidance: string | null; responseDue: string | null; overdue: boolean }
export interface PolicyView {
  noticeHours: number; verbalMonths: number; writtenMonths: number; finalMonths: number; appealDays: number; grievanceResponseDays: number; minReviewDays: number
}

export interface Row {
  id: string; number: string; type: string; category: string; title: string; status: string; employeeId: string; employeeName: string
  confidential: boolean; legacy: boolean; reportedOn: string; outcome: string | null; dueOn: string | null; dueLabel: string | null; overdue: boolean
}

export interface Detail {
  id: string; number: string; type: string; category: string; title: string; description: string; status: string
  confidential: boolean; legacy: boolean; employee: Party; jobTitle: string | null; department: string | null
  incidentDate: string | null; reportedOn: string; owner: Party | null; initiator: Party | null; chair: Party | null; respondent: Party | null
  hearing: Hearing | null; decision: Decision | null; appeal: Appeal | null; suspension: Suspension | null; review: Review | null
  closedOn: string | null; closedReason: string | null
  charges: Charge[]; steps: StepView[]; events: EventView[]; advice: Advice | null; actions: string[]; policy: PolicyView | null
  outcomes: string[]; missingSteps: string[]
}

export interface Mine {
  id: string; number: string; type: string; category: string; title: string; description: string; status: string; reportedOn: string
  hearing: Hearing | null; charges: Charge[]; decision: Decision | null; appeal: Appeal | null
  canAcknowledge: boolean; canAppeal: boolean; events: EventView[]
}

export interface PersonOption { id: string; name: string }
