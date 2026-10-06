// src/pages/businessreadiness/readiness.api.ts
//
// Business readiness (ADR-003): judging a tender's requirements against the registrations and documents a business holds. The SAME shapes and screens serve a tenant's own tenders
// (/api/v1/compliance/...) and a client's (/api/v1/compliance-services/...): only the URLs differ, so there is one panel and one catalogue screen, not two.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export type ReadinessResult = "MET" | "MISSING" | "EXPIRED" | "PENDING" | "NOT_EVALUATED" | "NOT_APPLICABLE"

export interface ReadinessItem {
  requirementId: string | null
  label: string
  manualStatus: string                    // what the user ticked: MET, MISSING, NOT_APPLICABLE, PENDING_REVIEW; never changed by the check
  result: ReadinessResult
  detail: string
  expiresOn: string | null
  expiringSoon: boolean
  differsFromManualStatus: boolean
  newerVersionAvailable: boolean
}

export interface ReadinessSummary {
  total: number; met: number; missing: number; expired: number; pending: number; notEvaluated: number; notApplicable: number
  expiringSoon: number; differFromManualStatus: number
}

export interface ReadinessAssessment {
  asOf: string                            // ISO date the evidence was judged against
  asOfBasis: "CLOSING_DATE" | "TODAY"
  items: ReadinessItem[]
  summary: ReadinessSummary
}

/** A requirement in the tracked-requirements catalogue, with the rule that says what satisfies it. The document that satisfies it is evidenceType. */
export interface TrackedRequirement {
  id: string
  code: string
  name: string
  appliesTo: string | null
  evidenceType: string | null
  required: boolean
  requirementVersion: number
  createdAt: string
  satisfiedByAuthority: string | null
  satisfiedByRegistrationType: string | null
  clientId?: string
}

export function useReadiness(url: string, enabled = true) {
  return useQuery<ReadinessAssessment>({
    queryKey: ["readiness", url], enabled,
    queryFn: async () => (await apiClient.get(url)).data,
  })
}

export function useTrackedRequirements(listUrl: string) {
  return useQuery<TrackedRequirement[]>({
    queryKey: ["readiness-catalogue", listUrl],
    queryFn: async () => { const d = (await apiClient.get(listUrl)).data; return Array.isArray(d) ? d : (d?.content ?? []) },
  })
}
