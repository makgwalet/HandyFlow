// src/pages/catalogue/CataloguePage.tsx
//
// Sections are routes (/catalogue/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
import { Suspense, lazy } from "react"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { CATALOGUE_SECTIONS } from "../../navigation/moduleSections"

const ItemsPanel = lazy(() => import("./ItemsPanel")), CategoriesPanel = lazy(() => import("./CategoriesPanel"))
const UnitsPanel = lazy(() => import("./UnitsPanel")), AttributesPanel = lazy(() => import("./AttributesPanel"))
const CatalogueDashboard = lazy(() => import("./CatalogueDashboard"))
const ImportPanel = lazy(() => import("./ImportPanel"))
const PriceListsPanel = lazy(() => import("./PriceListsPanel")), PriceApprovalsPanel = lazy(() => import("./PriceApprovalsPanel"))

export function CataloguePage() {
  return (
    <SectionedModulePage config={CATALOGUE_SECTIONS} subtitle="Manage your products and services" render={id => (
      <Suspense fallback={<p>Loading…</p>}>
        {id === "dashboard" ? <CatalogueDashboard /> : id === "items" ? <ItemsPanel /> : id === "categories" ? <CategoriesPanel /> : id === "units" ? <UnitsPanel /> : id === "attributes" ? <AttributesPanel /> : id === "pricelists" ? <PriceListsPanel /> : id === "import" ? <ImportPanel /> : id === "priceapprovals" ? <PriceApprovalsPanel /> : null}
      </Suspense>
    )} />
  )
}
