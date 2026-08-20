// src/api/auditorPortal.api.ts
import { auditorPortalApiClient as portalApiClient } from "./auditorPortal.client"

export interface AuditorPortalAuthResponse {
  token: string
  portalUserId: string
  email: string
  fullName: string
}

export interface AuditorTenantAccess {
  tenantId: string
  acceptedAt: string
}

export interface EvidenceItem {
  id: string
  fileName: string
  contentType: string
  fileSizeBytes: number
  evidenceType: string
  status: string
  uploadedByName?: string
  createdAt: string
}

export interface ControlExceptionItem {
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

export const auditorPortalApi = {
  login: (body: { email: string; password: string }) =>
    portalApiClient.post("/api/v1/auditor/portal/auth/login", body).then(r => r.data as AuditorPortalAuthResponse),
  register: (body: { inviteToken: string; password: string; fullName: string }) =>
    portalApiClient.post("/api/v1/auditor/portal/auth/register", body).then(r => r.data as AuditorPortalAuthResponse),
  getMyTenants: () =>
    portalApiClient.get("/api/v1/auditor/portal/tenants").then(r => r.data as AuditorTenantAccess[]),
  getEvidence: (tenantId: string) =>
    portalApiClient.get(`/api/v1/auditor/portal/tenants/${tenantId}/evidence`).then(r => r.data as EvidenceItem[]),
  getControlExceptions: (tenantId: string) =>
    portalApiClient.get(`/api/v1/auditor/portal/tenants/${tenantId}/control-exceptions`).then(r => r.data as ControlExceptionItem[]),
}
