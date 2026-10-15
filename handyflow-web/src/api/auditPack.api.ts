import { apiClient } from "./client"

export interface PackRun { id: string; engagementId: string | null; from: string; to: string; generatedBy: string; generatedAt: string; packSha256: string; fileCount: number }
export interface PackVerification { matches: boolean; storedSha256: string; currentSha256: string; changedFiles: string[] }
export interface BuiltPack { runId: string; sha256: string }

const rowsOf = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])

export const auditPackApi = {
  runs: () => apiClient.get("/api/v1/accounting/audit-pack/runs").then(r => rowsOf<PackRun>(r.data)),
  verify: (id: string) => apiClient.post(`/api/v1/accounting/audit-pack/runs/${id}/verify`).then(r => r.data as PackVerification),
  /** Builds the pack and saves it to the person's computer. */
  build: async (from: string, to: string, engagementId?: string): Promise<BuiltPack> => {
    const r = await apiClient.post("/api/v1/accounting/audit-pack", { from, to, engagementId: engagementId || null }, { responseType: "blob" })
    const url = URL.createObjectURL(new Blob([r.data], { type: "application/zip" }))
    const a = document.createElement("a"); a.href = url; a.download = `audit-pack-${from}-to-${to}.zip`; a.click(); URL.revokeObjectURL(url)
    return { runId: String(r.headers?.["x-audit-pack-run"] ?? ""), sha256: String(r.headers?.["x-audit-pack-sha256"] ?? "") }
  },
}
