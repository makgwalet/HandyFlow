export type QueryStatus = "OPEN" | "ANSWERED" | "RESOLVED" | "WITHDRAWN"
export type QueryKind = "QUERY" | "REVIEW_NOTE"

export interface QueryMessage { id: string; authorKind: "STAFF" | "AUDITOR"; authorLabel: string | null; messageKind: string; body: string; createdAt: string }
export interface AuditQueryView {
  id: string; kind: QueryKind; reference: string; engagementId: string | null; title: string
  subjectType: string | null; subjectId: string | null; subjectLabel: string | null; dueDate: string | null
  raisedByLabel: string | null; status: QueryStatus; timesReopened: number; overdue: boolean
  linkedFindingId: string | null; linkedFindingTitle: string | null; createdAt: string; closedAt: string | null; messages: QueryMessage[]
}
export interface RaiseInput { kind: QueryKind; title: string; text: string; subjectType?: string; subjectLabel?: string; dueDate?: string }
export interface FindingOption { id: string; title: string }

/** The calls one side can make. The business and the auditor each supply their own. */
export interface AuditQueryApi {
  list(): Promise<AuditQueryView[]>
  detail(id: string): Promise<AuditQueryView>
  // business
  answer?(id: string, text: string): Promise<AuditQueryView>
  link?(id: string, findingId: string | null): Promise<AuditQueryView>
  findingOptions?(): Promise<FindingOption[]>
  // auditor
  raise?(input: RaiseInput): Promise<AuditQueryView>
  followUp?(id: string, text: string): Promise<AuditQueryView>
  resolve?(id: string, note: string): Promise<AuditQueryView>
  withdraw?(id: string, why: string): Promise<AuditQueryView>
}
