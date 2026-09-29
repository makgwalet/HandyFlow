// src/pages/earthmoving/EarthMovingPage.tsx
//
// Sections are routes (/earthmoving/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
import EarthDashboard   from "./EarthDashboard"
import AssetsTab        from "./AssetsTab"
import MaintenanceTab   from "./MaintenanceTab"
import OperatorLogsTab  from "./OperatorLogsTab"
import DeploymentsTab   from "./DeploymentsTab"
import IncidentsTab     from "./IncidentsTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { EARTHMOVING_SECTIONS } from "../../navigation/moduleSections"

export function EarthMovingPage() {
  return (
    <SectionedModulePage config={EARTHMOVING_SECTIONS}
      subtitle="Fleet management · Deployment tracking · Maintenance scheduling · Operator logs · Incident reporting"
      render={(id, goTo) => {
        switch (id) {
          case "dashboard":   return <EarthDashboard onNavigate={goTo} />
          case "assets":      return <AssetsTab />
          case "deployments": return <DeploymentsTab />
          case "maintenance": return <MaintenanceTab />
          case "operators":   return <OperatorLogsTab />
          case "incidents":   return <IncidentsTab />
          default:            return null
        }
      }} />
  )
}
