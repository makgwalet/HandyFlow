// src/api/auditor.api.ts — staff-facing, uses the normal authenticated apiClient
import { apiClient } from "./client"

export interface AuditorAccessGrant {
  id: string
  inviteEmail: string
  status: string
  invitedAt: string
  acceptedAt?: string | null
  revokedAt?: string | null
}

export const auditorApi = {
  invite: (email: string, businessName: string) =>
    apiClient.post("/api/v1/auditors/invite", { email, businessName }).then(r => r.data as AuditorAccessGrant),
  list: () =>
    apiClient.get("/api/v1/auditors").then(r => r.data as AuditorAccessGrant[]),
  revoke: (id: string) =>
    apiClient.post(`/api/v1/auditors/${id}/revoke`).then(r => r.data as AuditorAccessGrant),
}
