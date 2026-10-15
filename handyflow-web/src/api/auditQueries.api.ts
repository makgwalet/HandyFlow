import { apiClient } from "./client"
import { auditorPortalApiClient } from "./auditorPortal.client"
import type { AuditQueryApi, AuditQueryView, FindingOption } from "../components/auditQueries/types"

const rowsOf = <T,>(d: any): T[] => (Array.isArray(d) ? d : d?.data ?? [])

/** The business's calls. Findings for the link picker come from every internal audit engagement. */
export const businessAuditQueryApi: AuditQueryApi = {
  list: () => apiClient.get("/api/v1/auditors/queries").then(r => rowsOf<AuditQueryView>(r.data)),
  detail: id => apiClient.get(`/api/v1/auditors/queries/${id}`).then(r => r.data),
  answer: (id, text) => apiClient.post(`/api/v1/auditors/queries/${id}/answer`, { text }).then(r => r.data),
  link: (id, findingId) => apiClient.post(`/api/v1/auditors/queries/${id}/link`, { findingId }).then(r => r.data),
  findingOptions: async () => {
    const engagements = rowsOf<{ id: string; name: string }>((await apiClient.get("/api/v1/internal-audit/engagements")).data)
    const lists = await Promise.all(engagements.map(async e =>
      rowsOf<{ id: string; title: string }>((await apiClient.get(`/api/v1/internal-audit/engagements/${e.id}/findings`)).data)
        .map<FindingOption>(f => ({ id: f.id, title: `${f.title} (${e.name})` }))))
    return lists.flat()
  },
}

/** The auditor's calls, for one business they have access to. */
export const auditorAuditQueryApi = (tenantId: string): AuditQueryApi => {
  const base = `/api/v1/auditor/portal/tenants/${tenantId}/queries`
  const c = auditorPortalApiClient
  return {
    list: () => c.get(base).then(r => rowsOf<AuditQueryView>(r.data)),
    detail: id => c.get(`${base}/${id}`).then(r => r.data),
    raise: i => c.post(base, i).then(r => r.data),
    followUp: (id, text) => c.post(`${base}/${id}/follow-up`, { text }).then(r => r.data),
    resolve: (id, text) => c.post(`${base}/${id}/resolve`, { text }).then(r => r.data),
    withdraw: (id, text) => c.post(`${base}/${id}/withdraw`, { text }).then(r => r.data),
  }
}
