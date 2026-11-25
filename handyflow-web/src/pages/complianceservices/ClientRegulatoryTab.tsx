// src/pages/complianceservices/ClientRegulatoryTab.tsx
//
// The regulatory changes that touch this client, and what has been done about each. Impacts are raised when a reviewer confirms a change.
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import RegulatoryImpactRow from "./RegulatoryImpactRow"
import { impactsOrdered, type Impact } from "./regulatoryRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T

export default function ClientRegulatoryTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const { data = [], isLoading } = useQuery<Impact[]>({
    queryKey: ["cs-reg-client", clientId],
    queryFn: async () => unwrap<Impact[]>(await apiClient.get(`/api/v1/compliance-services/clients/${clientId}/regulatory-impacts`)),
  })
  const changed = () => { qc.invalidateQueries({ queryKey: ["cs-reg-client", clientId] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] }) }
  const rows = impactsOrdered(data)
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        Changes in the rules that touch this client's obligations. Each one stays here until someone records that the client was told, nothing needed doing, or the work is done.
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && rows.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No regulatory changes touch this client.</div>}
      {rows.map(i => <RegulatoryImpactRow key={i.id} impact={i} canManage={canManage} onChanged={changed} />)}
    </div>
  )
}
