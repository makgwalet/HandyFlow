// src/pages/facilities/FacilitiesPage.tsx
//
// Internal Facilities module. Sections are routes (/facilities/:section), registered in
// navigation/moduleSections.ts (FACILITIES_SECTIONS). The backend is /api/v1/facilities (see api/facilities.api.ts).
import { SectionedModulePage } from '../../components/shell/SectionedModulePage'
import { FACILITIES_SECTIONS } from '../../navigation/moduleSections'
import FacilitiesDashboard from './FacilitiesDashboard'
import ActionCentreTab from './ActionCentreTab'
import WorkOrdersTab from './WorkOrdersTab'
import SitesTab from './SitesTab'
import AssetsTab from './AssetsTab'
import ComplianceTab from './ComplianceTab'
import StockTab from './StockTab'
import PurchaseOrdersTab from './PurchaseOrdersTab'
import VendorsTab from './VendorsTab'
import TechniciansTab from './TechniciansTab'
import ReportsTab from './ReportsTab'
import InspectionSchedulesTab from './InspectionSchedulesTab'
import InspectionsTab from './InspectionsTab'
import CorrectiveActionsTab from './CorrectiveActionsTab'
import PlannedMaintenanceTab from './PlannedMaintenanceTab'

export function FacilitiesPage() {
  return (
    <SectionedModulePage config={FACILITIES_SECTIONS} render={(id, goTo) => {
      switch (id) {
        case 'dashboard':       return <FacilitiesDashboard onNavigate={goTo} />
        case 'action-centre':   return <ActionCentreTab onNavigate={goTo} />
        case 'work-orders':     return <WorkOrdersTab />
        case 'inspections':     return <InspectionsTab />
        case 'corrective-actions': return <CorrectiveActionsTab onOpenWorkOrder={wo => goTo(`work-orders?wo=${wo}`)} />
        case 'ppm':             return <PlannedMaintenanceTab />
        case 'sites':           return <SitesTab />
        case 'assets':          return <AssetsTab />
        case 'compliance':      return <ComplianceTab />
        case 'stock':           return <StockTab />
        case 'purchase-orders': return <PurchaseOrdersTab />
        case 'vendors':        return <VendorsTab />
        case 'technicians':    return <TechniciansTab />
        case 'reports':        return <ReportsTab />
        case 'inspection-schedules': return <InspectionSchedulesTab />
        default:                return null
      }
    }} />
  )
}
