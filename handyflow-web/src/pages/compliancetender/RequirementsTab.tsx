// src/pages/compliancetender/RequirementsTab.tsx
//
// The business's own tracked requirements and what satisfies each (ADR-003). A thin wrapper: the screen is shared with the client side (businessreadiness/RequirementCatalogue).
import { usePermission } from "../../hooks/usePermission"
import RequirementCatalogue from "../businessreadiness/RequirementCatalogue"

export default function RequirementsTab() {
  const manage = usePermission("COMPLIANCE_MANAGE")
  const admin = usePermission("COMPLIANCE_ADMIN")                // both hooks always run, in the same order
  return (
    <RequirementCatalogue scope="your business"
      listUrl="/api/v1/compliance/requirements" createUrl="/api/v1/compliance/requirements"
      newVersionUrl={id => `/api/v1/compliance/requirements/${id}/new-version`}
      canManage={manage || admin} />
  )
}
