import { apiClient } from "./client"

export interface AuditSettingValues { lowMax: number; mediumMax: number; highMax: number; escalateAfterDays: number; repeatEveryDays: number; firstReviewReminderDay: number }
export interface AuditSettings {
  scoreMin: number; scoreMax: number
  riskBands: { level: string; from: number; to: number }[]
  riskInputs: string[]
  escalateAfterDays: number; firstReviewReminderDay: number; repeatEveryDays: number
  engagementRoles: { key: string; label: string }[]
  values: AuditSettingValues; defaults: AuditSettingValues; custom: boolean
}

const unwrap = <T,>(r: { data: { data?: T } & T }) => (r.data?.data ?? r.data) as T

export const auditSettingsApi = {
  get: () => apiClient.get("/api/v1/internal-audit/settings").then(r => unwrap<AuditSettings>(r)),
  save: (v: AuditSettingValues) => apiClient.put("/api/v1/internal-audit/settings", v).then(r => unwrap<AuditSettings>(r)),
  reset: () => apiClient.delete("/api/v1/internal-audit/settings").then(r => unwrap<AuditSettings>(r)),
}
