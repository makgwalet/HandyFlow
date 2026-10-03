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
        default:           return null
      }
    }} />
  )
}
