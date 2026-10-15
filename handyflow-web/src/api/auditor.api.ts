// src/api/auditor.api.ts — staff-facing, uses the normal authenticated apiClient
import { apiClient } from "./client"

export interface AuditorAccessGrant {
  id: string
  inviteEmail: string
  status: string
  invitedAt: string
  acceptedAt?: string | null
  revokedAt?: string | null
  expiresAt?: string | null
  scopeNote?: string | null
  expired?: boolean
  engagementId?: string | null
}

export interface AuditEngagement {
  id: string; name: string; firmName: string; periodFrom: string; periodTo: string
  entitiesInScope?: string | null; scopeNote?: string | null
  status: "PLANNED" | "ACTIVE" | "COMPLETED" | "ARCHIVED"
  startedAt?: string | null; completedAt?: string | null; archivedAt?: string | null
}
export interface EngagementInput { name: string; firmName: string; periodFrom: string; periodTo: string; entitiesInScope?: string; scopeNote?: string }
export interface EngagementManifest {
  engagement: AuditEngagement
  auditors: { grantId: string; email: string; status: string }[]
  shares: { itemType: string; itemId: string; reason: string; sharedAt: string; withdrawnAt?: string | null }[]
}

export const auditorApi = {
  invite: (email: string, businessName: string, expiresOn?: string, scopeNote?: string, engagementId?: string) =>
    apiClient.post("/api/v1/auditors/invite", { email, businessName, expiresOn: expiresOn || null, scopeNote: scopeNote || null, engagementId: engagementId || null }).then(r => r.data as AuditorAccessGrant),
  changeExpiry: (id: string, expiresOn: string) =>
    apiClient.post(`/api/v1/auditors/${id}/expiry`, { expiresOn }).then(r => r.data as AuditorAccessGrant),
  list: () =>
    apiClient.get("/api/v1/auditors").then(r => r.data as AuditorAccessGrant[]),
  revoke: (id: string) =>
    apiClient.post(`/api/v1/auditors/${id}/revoke`).then(r => r.data as AuditorAccessGrant),
  engagements: () =>
    apiClient.get("/api/v1/auditors/engagements").then(r => r.data as AuditEngagement[]),
  createEngagement: (body: EngagementInput) =>
    apiClient.post("/api/v1/auditors/engagements", body).then(r => r.data as AuditEngagement),
  engagementAction: (id: string, action: "start" | "complete" | "archive") =>
    apiClient.post(`/api/v1/auditors/engagements/${id}/${action}`).then(r => r.data as AuditEngagement),
  manifest: (id: string) =>
    apiClient.get(`/api/v1/auditors/engagements/${id}/manifest`).then(r => r.data as EngagementManifest),
}
