import { apiClient } from "./client"
import { auditorPortalApiClient } from "./auditorPortal.client"
import type { EvidenceRequestApi, EvidenceRequestView } from "../components/evidenceRequests/types"

const saveBlob = (data: BlobPart, fileName: string) => {
  const url = URL.createObjectURL(new Blob([data]))
  const a = document.createElement("a"); a.href = url; a.download = fileName; a.click(); URL.revokeObjectURL(url)
}
const rowsOf = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])

/** The business's calls. Files are attached through the shared evidence endpoint, against the request. */
export const businessEvidenceRequestApi: EvidenceRequestApi = {
  list: () => apiClient.get("/api/v1/auditors/evidence-requests").then(r => rowsOf<EvidenceRequestView>(r.data)),
  detail: id => apiClient.get(`/api/v1/auditors/evidence-requests/${id}`).then(r => r.data),
  create: i => apiClient.post("/api/v1/auditors/evidence-requests", i).then(r => r.data),
  comment: (id, text) => apiClient.post(`/api/v1/auditors/evidence-requests/${id}/comments`, { text }).then(r => r.data),
  cancel: (id, text) => apiClient.post(`/api/v1/auditors/evidence-requests/${id}/cancel`, { text }).then(r => r.data),
  submit: (id, text) => apiClient.post(`/api/v1/auditors/evidence-requests/${id}/submit`, { text }).then(r => r.data),
  notAvailable: (id, text) => apiClient.post(`/api/v1/auditors/evidence-requests/${id}/not-available`, { text }).then(r => r.data),
  upload: async (id, file) => {
    const body = new FormData()
    body.append("file", file); body.append("evidenceType", "AUDIT_REQUEST_FILE"); body.append("sourceModule", "auditor")
    body.append("relatedEntityType", "AUDIT_EVIDENCE_REQUEST"); body.append("relatedEntityId", id)
    await apiClient.post("/api/v1/evidence", body, { headers: { "Content-Type": "multipart/form-data" } })
  },
  download: async (id, fileId, fileName) => {
    const r = await apiClient.get(`/api/v1/auditors/evidence-requests/${id}/files/${fileId}`, { responseType: "blob" })
    saveBlob(r.data, fileName)
  },
}

/** The auditor's calls, for one business they have access to. */
export const auditorEvidenceRequestApi = (tenantId: string): EvidenceRequestApi => {
  const base = `/api/v1/auditor/portal/tenants/${tenantId}/evidence-requests`
  return {
    list: () => auditorPortalApiClient.get(base).then(r => rowsOf<EvidenceRequestView>(r.data)),
    detail: id => auditorPortalApiClient.get(`${base}/${id}`).then(r => r.data),
    create: i => auditorPortalApiClient.post(base, i).then(r => r.data),
    comment: (id, text) => auditorPortalApiClient.post(`${base}/${id}/comments`, { text }).then(r => r.data),
    cancel: (id, text) => auditorPortalApiClient.post(`${base}/${id}/cancel`, { text }).then(r => r.data),
    approve: (id, text) => auditorPortalApiClient.post(`${base}/${id}/approve`, { text }).then(r => r.data),
    reject: (id, text) => auditorPortalApiClient.post(`${base}/${id}/reject`, { text }).then(r => r.data),
    download: async (id, fileId, fileName) => {
      const r = await auditorPortalApiClient.get(`${base}/${id}/files/${fileId}`, { responseType: "blob" })
      saveBlob(r.data, fileName)
    },
  }
}
