import { apiClient } from "./client"

export interface EngagementPhase { key: "planning" | "fieldwork" | "testing" | "reporting"; label: string; pct: number; done: number; total: number; basis: string }
export interface EngagementMilestone { key: string; label: string; date: string | null; status: "DONE" | "CURRENT" | "PENDING" | "LATE" }
export interface EngagementCounts {
  workpapers: number; workpapersSignedOff: number; findings: number; openFindings: number; findingsPastDue: number
  actions: number; actionsValidated: number; actionsOverdue: number; team: number
}
export interface EngagementSummary { overallPct: number; phases: EngagementPhase[]; milestones: EngagementMilestone[]; counts: EngagementCounts; reportStatus: string | null }

export const auditEngagementApi = {
  summary: (id: string) => apiClient.get(`/api/v1/internal-audit/engagements/${id}/summary`).then(r => (r.data?.data ?? r.data) as EngagementSummary),
  folders: (id: string) => apiClient.get(`/api/v1/internal-audit/engagements/${id}/workpaper-folders`).then(r => (r.data?.data ?? r.data) as { id: string; name: string }[]),
  files: (id: string, folderId: string) => apiClient.get(`/api/v1/internal-audit/engagements/${id}/workpaper-folders/${folderId}/files`).then(r => (r.data?.data ?? r.data) as WorkpaperFileRow[]),
  /** Fetches the file and hands it to the browser as a download. */
  async download(fileId: string, fileName: string) {
    const r = await apiClient.get(`/api/v1/internal-audit/workpaper-files/${fileId}/content`, { responseType: "blob" })
    const url = URL.createObjectURL(r.data as Blob)
    const a = document.createElement("a")
    a.href = url; a.download = fileName
    document.body.appendChild(a); a.click(); a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
}

export interface WorkpaperFileRow {
  id: string; folderId: string; fileName: string; mimeType: string | null; fileSizeBytes: number | null; reviewStatus: string
  preparedBy: string | null; preparedAt: string | null; reviewedBy: string | null; reviewedAt: string | null
  signedOffBy: string | null; signedOffAt: string | null; versionNumber: number; supersededBy: string | null; createdAt: string
}
