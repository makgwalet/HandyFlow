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
import WorkOrdersTab    from "./WorkOrdersTab"
import PlansTab         from "./PlansTab"
import AlertsTab        from "./AlertsTab"
import SitesTab         from "./SitesTab"
import HireTab          from "./HireTab"
import BillingTab       from "./BillingTab"
import ProfitabilityTab from "./ProfitabilityTab"
import InspectionsTab   from "./InspectionsTab"
import { UtilisationReport, CustomerRevenueReport, DowntimeReport, ComplianceReport } from "./ReportsTab"
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
          case "sites":       return <SitesTab />
          case "deployments": return <DeploymentsTab />
          case "inspections": return <InspectionsTab />
          case "profitability": return <ProfitabilityTab />
          case "report-utilisation": return <UtilisationReport />
          case "report-revenue": return <CustomerRevenueReport />
          case "report-downtime": return <DowntimeReport />
          case "report-compliance": return <ComplianceReport />
          case "hire":        return <HireTab />
          case "billing":     return <BillingTab />
          case "workorders":  return <WorkOrdersTab />
          case "plans":       return <PlansTab />
          case "maintenance": return <MaintenanceTab />
          case "operators":   return <OperatorLogsTab />
          case "incidents":   return <IncidentsTab />
          case "alerts":      return <AlertsTab />
          default:            return null
        }
      }} />
  )
}
