// src/api/controlExceptions.api.ts
//
// Client for the shared "needs attention" board. The narrow shape is what other modules see; the detail shape
// carries the risk rating, owner, management response, evidence count and follow-up (audit backlog A15).
import { apiClient } from "./client"

export interface ControlException {
  id: string
  sourceModule: string
  controlType: string
  relatedEntityType: string
  relatedEntityId: string
  severity: string
  description: string
  status: string
  detectedAt: string
  resolvedByName?: string | null
  resolvedAt?: string | null
  resolutionNotes?: string | null
}

export type RiskRating = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
export type ExceptionScope = "open" | "follow-ups" | "all"

export interface ControlExceptionDetail extends ControlException {
  riskRating?: RiskRating | null
  riskRationale?: string | null
  ratedByName?: string | null
  ratedAt?: string | null
  ownerId?: string | null
  ownerName?: string | null
  targetDate?: string | null
  overdue: boolean
  responseText?: string | null
  responseByName?: string | null
  responseAt?: string | null
  followUpDate?: string | null
  followUpDue: boolean
  followUpAt?: string | null
  followUpByName?: string | null
  followUpOutcome?: "HOLDING" | "RECURRED" | null
  followUpNote?: string | null
  timesReopened: number
  evidenceCount: number
}

export interface ExceptionEvidence { id: string; fileName: string; uploadedByName?: string | null; createdAt: string }

const base = "/api/v1/control-exceptions"
const rows = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])

export const controlExceptionsApi = {
  listOpen: () => apiClient.get(base).then(r => r.data as ControlException[]),
  detailed: (scope: ExceptionScope = "open") =>
    apiClient.get(`${base}/detailed`, { params: { scope } }).then(r => rows<ControlExceptionDetail>(r.data)),
  rate: (id: string, rating: RiskRating, rationale: string) =>
    apiClient.post(`${base}/${id}/rate`, { rating, rationale }).then(r => r.data as ControlExceptionDetail),
  assign: (id: string, ownerId: string, targetDate: string) =>
    apiClient.post(`${base}/${id}/assign`, { ownerId, targetDate }).then(r => r.data as ControlExceptionDetail),
  respond: (id: string, response: string) =>
    apiClient.post(`${base}/${id}/respond`, { response }).then(r => r.data as ControlExceptionDetail),
  resolve: (id: string, resolutionNotes: string, followUpDate?: string) =>
    apiClient.post(`${base}/${id}/resolve`, { resolutionNotes, followUpDate: followUpDate || null }).then(r => r.data as ControlExceptionDetail),
  followUp: (id: string, outcome: "HOLDING" | "RECURRED", note: string) =>
    apiClient.post(`${base}/${id}/follow-up`, { outcome, note }).then(r => r.data as ControlExceptionDetail),

  assignableUsers: () =>
    apiClient.get("/api/v1/tasks/assignable-users").then(r => rows<{ id: string; name: string }>(r.data)),

  evidence: (id: string) =>
    apiClient.get("/api/v1/evidence", { params: { sourceModule: "controls", relatedEntityType: "CONTROL_EXCEPTION", relatedEntityId: id } })
      .then(r => rows<ExceptionEvidence>(r.data)),
  attach: async (id: string, file: File) => {
    const body = new FormData()
    body.append("file", file); body.append("evidenceType", "CONTROL_EXCEPTION_FILE"); body.append("sourceModule", "controls")
    body.append("relatedEntityType", "CONTROL_EXCEPTION"); body.append("relatedEntityId", id)
    await apiClient.post("/api/v1/evidence", body, { headers: { "Content-Type": "multipart/form-data" } })
  },
}
