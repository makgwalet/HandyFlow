// src/pages/agriculture/AgriculturePage.tsx
//
// Module 7 (Agriculture) — Farm Foundation + Livestock delivery (mirrors
// the backend's own Increment 1 scope: Farms, Species, Production Areas,
// Enterprises, Animals, Groups, Inventory, and all six Livestock history
// sub-resources + evidence). Crops and Cost Reporting views are a
// follow-up delivery, matching the backend's own Increment 2 + cost
// reporting rollout — not built yet.
//
// A third platform shape alongside every prior module this engagement:
// no external clients, no client portal — the tenant runs its own farms
// directly, structurally closest to earthmoving/fleet. Confirmed against
// za.co.handyflow.platform.agriculture's 7 Increment-1 controllers.
import { useState } from "react"
import { Navigate, useLocation, useParams } from "react-router-dom"
import AgDashboard from "./AgDashboard"
import AgFarmsTab, { type FarmResponse } from "./AgFarmsTab"
import AgFarmDetail from "./AgFarmDetail"
import AgSpeciesTab from "./AgSpeciesTab"
import { PageHeader } from "../../components/ui/PageHeader"
import { AGRICULTURE_SECTIONS, findSection } from "../../navigation/moduleSections"

/**
 * Sections are routes (/agriculture/:section) with navigation in the
 * sidebar. The farm drill-down is still in-page state: it is tied to the
 * current history entry, so any navigation (including clicking "Farms"
 * again) returns to the list.
 */
export default function AgriculturePage() {
  const { section: rawSection } = useParams<{ section?: string }>()
  const location = useLocation()
  const [openFarm, setOpenFarm] = useState<{ farm: FarmResponse; locationKey: string } | null>(null)

  const base = AGRICULTURE_SECTIONS.basePath
  const found = findSection(AGRICULTURE_SECTIONS, rawSection)
  if (!found) return <Navigate replace to={`${base}/${AGRICULTURE_SECTIONS.defaultSection}`} />
  const { section } = found

  const selectedFarm = openFarm && openFarm.locationKey === location.key ? openFarm.farm : null
  const crumbs = [
    { label: AGRICULTURE_SECTIONS.title, to: `${base}/${AGRICULTURE_SECTIONS.defaultSection}` },
    { label: section.label, to: selectedFarm ? `${base}/${section.id}` : undefined },
    ...(selectedFarm ? [{ label: selectedFarm.name }] : []),
  ]

  return (
    <div>
      <PageHeader title={selectedFarm ? selectedFarm.name : section.label} icon={section.icon} breadcrumbs={crumbs} />
      {selectedFarm ? (
        <AgFarmDetail farm={selectedFarm} onBack={() => setOpenFarm(null)} />
      ) : (
        <>
          {section.id === "dashboard" && <AgDashboard />}
          {section.id === "farms" && <AgFarmsTab onSelectFarm={farm => setOpenFarm({ farm, locationKey: location.key })} />}
          {section.id === "species" && <AgSpeciesTab />}
        </>
      )}
    </div>
  )
}
