// src/pages/catalogue/CataloguePage.tsx
//
// Sections are routes (/catalogue/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
import { Suspense, lazy } from "react"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { CATALOGUE_SECTIONS } from "../../navigation/moduleSections"

const ItemsPanel = lazy(() => import("./ItemsPanel")), CategoriesPanel = lazy(() => import("./CategoriesPanel"))

export function CataloguePage() {
  return (
    <SectionedModulePage config={CATALOGUE_SECTIONS} subtitle="Manage your products and services" render={id => (
      <Suspense fallback={<p>Loading…</p>}>
        {id === "items" ? <ItemsPanel /> : id === "categories" ? <CategoriesPanel /> : null}
      </Suspense>
    )} />
  )
}
