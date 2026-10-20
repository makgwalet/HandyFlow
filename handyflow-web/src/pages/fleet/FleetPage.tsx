// src/pages/fleet/FleetPage.tsx
//
// Sections are routes (/fleet/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
import FleetDashboard from "./FleetDashboard"
import VehiclesTab    from "./VehiclesTab"
import TripsTab       from "./TripsTab"
import ServicesTab    from "./ServicesTab"
import FuelTab        from "./FuelTab"
import ComplianceTab  from "./ComplianceTab"
import DriversTab     from "./DriversTab"
import EquipmentTab   from "./EquipmentTab"
import AccidentsTab   from "./AccidentsTab"
import InspectionsTab from "./InspectionsTab"
import WorkOrdersTab  from "./WorkOrdersTab"
import TyresBatteriesTab from "./TyresBatteriesTab"
import FuelAnalyticsTab from "./FuelAnalyticsTab"
import AlertsTab from "./AlertsTab"
import LiveMapTab from "./LiveMapTab"
import GeofencesTab from "./GeofencesTab"
import TrackingSetupTab from "./TrackingSetupTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { FLEET_SECTIONS } from "../../navigation/moduleSections"

export function FleetPage() {
  return (
    <SectionedModulePage config={FLEET_SECTIONS} render={(id, goTo) => {
      switch (id) {
        case "dashboard":  return <FleetDashboard onNavigate={goTo} />
        case "vehicles":   return <VehiclesTab />
        case "drivers":    return <DriversTab />
        case "equipment":  return <EquipmentTab />
        case "trips":      return <TripsTab />
        case "services":   return <ServicesTab />
        case "fuel":       return <FuelTab />
        case "compliance": return <ComplianceTab />
        case "accidents":  return <AccidentsTab />
        case "inspections": return <InspectionsTab />
        case "workshop":   return <WorkOrdersTab />
        case "tyres":      return <TyresBatteriesTab />
        case "fuel-analytics": return <FuelAnalyticsTab />
        case "alerts":     return <AlertsTab onNavigate={goTo} />
        case "live-map":   return <LiveMapTab onNavigate={goTo} />
        case "geofences":  return <GeofencesTab />
        case "tracking-setup": return <TrackingSetupTab />
        case "driver-scores": return <DriversTab />
        default:           return null
      }
    }} />
  )
}
