// src/pages/fleet/FleetPage.tsx
//
// Sections are routes (/fleet/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts).
import type { ReactNode } from "react"
import { Navigate, useNavigate, useParams } from "react-router-dom"
import FleetDashboard from "./FleetDashboard"
import VehiclesTab    from "./VehiclesTab"
import TripsTab       from "./TripsTab"
import ServicesTab    from "./ServicesTab"
import FuelTab        from "./FuelTab"
import ComplianceTab  from "./ComplianceTab"
import DriversTab     from "./DriversTab"
import { PageHeader } from "../../components/ui/PageHeader"
import { FLEET_SECTIONS, findSection } from "../../navigation/moduleSections"

export function FleetPage() {
  const { section: rawSection } = useParams<{ section?: string }>()
  const navigate = useNavigate()
  const base = FLEET_SECTIONS.basePath

  const found = findSection(FLEET_SECTIONS, rawSection)
  if (!found) return <Navigate replace to={`${base}/${FLEET_SECTIONS.defaultSection}`} />
  const { section, group } = found

  const content: Record<string, ReactNode> = {
    dashboard:  <FleetDashboard onNavigate={(id: string) => navigate(`${base}/${id}`)} />,
    vehicles:   <VehiclesTab />,
    drivers:    <DriversTab />,
    trips:      <TripsTab />,
    services:   <ServicesTab />,
    fuel:       <FuelTab />,
    compliance: <ComplianceTab />,
  }

  return (
    <div>
      <PageHeader
        title={section.label}
        icon={section.icon}
        breadcrumbs={[
          { label: FLEET_SECTIONS.title, to: `${base}/${FLEET_SECTIONS.defaultSection}` },
          { label: group.label },
          { label: section.label },
        ]}
      />
      <div key={section.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 24 }}>
        {content[section.id]}
      </div>
    </div>
  )
}
