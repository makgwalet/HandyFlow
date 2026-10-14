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
}

export const auditorApi = {
  invite: (email: string, businessName: string, expiresOn?: string, scopeNote?: string) =>
    apiClient.post("/api/v1/auditors/invite", { email, businessName, expiresOn: expiresOn || null, scopeNote: scopeNote || null }).then(r => r.data as AuditorAccessGrant),
  changeExpiry: (id: string, expiresOn: string) =>
    apiClient.post(`/api/v1/auditors/${id}/expiry`, { expiresOn }).then(r => r.data as AuditorAccessGrant),
  list: () =>
    apiClient.get("/api/v1/auditors").then(r => r.data as AuditorAccessGrant[]),
  revoke: (id: string) =>
    apiClient.post(`/api/v1/auditors/${id}/revoke`).then(r => r.data as AuditorAccessGrant),
}
