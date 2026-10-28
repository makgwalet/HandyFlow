import { apiClient } from "./client"

export interface AuditSettings {
  scoreMin: number; scoreMax: number
  riskBands: { level: string; from: number; to: number }[]
  riskInputs: string[]
  escalateAfterDays: number; firstReviewReminderDay: number; repeatEveryDays: number
  engagementRoles: { key: string; label: string }[]
}

export const auditSettingsApi = {
  get: () => apiClient.get("/api/v1/internal-audit/settings").then(r => (r.data?.data ?? r.data) as AuditSettings),
}
