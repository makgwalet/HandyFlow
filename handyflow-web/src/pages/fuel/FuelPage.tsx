// src/pages/fuel/FuelPage.tsx
//
// Sections are routes (/fuel/:section) with navigation in the sidebar (see
// navigation/moduleSections.ts). "Cost & Margin" requires FUEL_MARGIN_READ:
// the sidebar hides it and this page redirects away from it without the
// permission, mirroring (not replacing) the server-side check.
import type { ReactNode } from "react"
import { Navigate, useNavigate, useParams } from "react-router-dom"
import FuelDashboard  from "./FuelDashboard"
import TanksTab       from "./TanksTab"
import ReceiptsTab    from "./ReceiptsTab"
import DispatchesTab  from "./DispatchesTab"
import DeliveriesTab  from "./DeliveriesTab"
import SuppliersTab   from "./SuppliersTab"
import MarginTab      from "./MarginTab"
import { PageHeader } from "../../components/ui/PageHeader"
import { FUEL_SECTIONS, findSection } from "../../navigation/moduleSections"
import { useAuthStore } from "../../store/auth.store"

const NO_PERMISSIONS: string[] = []

export function FuelPage() {
  const { section: rawSection } = useParams<{ section?: string }>()
  const navigate = useNavigate()
  const permissions = useAuthStore(s => s.user?.permissions ?? NO_PERMISSIONS)
  const base = FUEL_SECTIONS.basePath

  const found = findSection(FUEL_SECTIONS, rawSection, permissions)
  if (!found) return <Navigate replace to={`${base}/${FUEL_SECTIONS.defaultSection}`} />
  const { section, group } = found

  const content: Record<string, ReactNode> = {
    dashboard:  <FuelDashboard onNavigate={(id: string) => navigate(`${base}/${id}`)} />,
    tanks:      <TanksTab />,
    receipts:   <ReceiptsTab />,
    dispatches: <DispatchesTab />,
    deliveries: <DeliveriesTab />,
    suppliers:  <SuppliersTab />,
    margin:     <MarginTab />,
  }

  return (
    <div>
      <PageHeader
        title={section.label}
        icon={section.icon}
        breadcrumbs={[
          { label: FUEL_SECTIONS.title, to: `${base}/${FUEL_SECTIONS.defaultSection}` },
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
