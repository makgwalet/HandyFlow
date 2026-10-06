// src/pages/complianceservices/ClientRequirementsTab.tsx
//
// One client's tracked requirements and what satisfies each (ADR-003). A thin wrapper: the screen is shared with the tenant side (businessreadiness/RequirementCatalogue).
import { usePermission } from "../../hooks/usePermission"
import RequirementCatalogue from "../businessreadiness/RequirementCatalogue"

export default function ClientRequirementsTab({ clientId }: { clientId: string }) {
  const manage = usePermission("COMPLIANCE_SERVICES_MANAGE")
  const admin = usePermission("COMPLIANCE_SERVICES_ADMIN")       // both hooks always run, in the same order
  return (
    <RequirementCatalogue scope="this client"
      listUrl={`/api/v1/compliance-services/clients/${clientId}/requirements`} createUrl={`/api/v1/compliance-services/clients/${clientId}/requirements`}
      newVersionUrl={id => `/api/v1/compliance-services/requirements/${id}/new-version`}
      canManage={manage || admin} />
  )
}
