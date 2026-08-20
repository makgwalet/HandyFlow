// src/api/controlExceptions.api.ts
//
// Client for the shared "needs attention" board — Stage 1 of the
// Financial Control & Assurance plan. Cross-module, tenant-wide, not
// scoped to any one module's own client list the way most of tonight's
// other api files are.
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

export const controlExceptionsApi = {
  listOpen: () =>
    apiClient.get("/api/v1/control-exceptions").then(r => r.data as ControlException[]),
  resolve: (id: string, resolutionNotes?: string) =>
    apiClient.post(`/api/v1/control-exceptions/${id}/resolve`, { resolutionNotes })
      .then(r => r.data as ControlException),
}
