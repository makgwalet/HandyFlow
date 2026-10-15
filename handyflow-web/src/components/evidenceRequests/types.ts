export type RequestStatus = "OPEN" | "SUBMITTED" | "NOT_AVAILABLE" | "APPROVED" | "CLOSED_NOT_AVAILABLE" | "CANCELLED"

export interface RequestFile { id: string; fileName: string }
export interface RequestRound {
  roundNo: number; kind: "SUBMISSION" | "NOT_AVAILABLE"; note: string; submittedAt: string
  decision: "APPROVED" | "REJECTED" | null; decisionNote: string | null; decidedAt: string | null; files: RequestFile[]
}
export interface RequestComment { id: string; authorKind: "STAFF" | "AUDITOR"; authorLabel: string | null; body: string; createdAt: string }
export interface EvidenceRequestView {
  id: string; reference: string; engagementId: string | null; title: string; description: string | null
  priority: "LOW" | "MEDIUM" | "HIGH"; dueDate: string | null; assignedTo: string | null; assignedToName: string | null
  requestedByKind: "STAFF" | "AUDITOR"; requestedByLabel: string | null; status: RequestStatus; timesRejected: number
  overdue: boolean; dueSoon: boolean; cancelReason: string | null; createdAt: string; closedAt: string | null
  rounds: RequestRound[]; comments: RequestComment[]
}
export interface NewRequestInput { title: string; description?: string; priority: string; dueDate?: string; engagementId?: string }

/** The calls one side of the screen can make. The business and the auditor each supply their own. */
export interface EvidenceRequestApi {
  list(): Promise<EvidenceRequestView[]>
  detail(id: string): Promise<EvidenceRequestView>
  create(input: NewRequestInput): Promise<EvidenceRequestView>
  comment(id: string, text: string): Promise<EvidenceRequestView>
  cancel(id: string, text: string): Promise<EvidenceRequestView>
  download(id: string, fileId: string, fileName: string): Promise<void>
  // business side
  upload?(id: string, file: File): Promise<void>
  submit?(id: string, note: string): Promise<EvidenceRequestView>
  notAvailable?(id: string, text: string): Promise<EvidenceRequestView>
  // auditor side
  approve?(id: string, note: string): Promise<EvidenceRequestView>
  reject?(id: string, text: string): Promise<EvidenceRequestView>
}
