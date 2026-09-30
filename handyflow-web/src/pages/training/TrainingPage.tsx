// src/pages/training/TrainingPage.tsx
//
// Thin tab-shell, same shape as WarehousingPage.tsx (the reference file
// for this build). Module 4a (Internal L&D) — no client portfolio, no
// external client portal (that's 4b Training Provider, a separate module).
//
// Sections are routes (/training/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
import TrainingDashboard from "./TrainingDashboard"
import TrainingCoursesTab from "./TrainingCoursesTab"
import TrainingSessionsTab from "./TrainingSessionsTab"
import TrainingCertificatesTab from "./TrainingCertificatesTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { TRAINING_SECTIONS } from "../../navigation/moduleSections"

export default function TrainingPage() {
  return (
    <SectionedModulePage config={TRAINING_SECTIONS}
      subtitle="Course catalogue, sessions, enrollments and certifications for your own team"
      render={id => {
        switch (id) {
          case "dashboard":    return <TrainingDashboard />
          case "courses":      return <TrainingCoursesTab />
          case "sessions":     return <TrainingSessionsTab />
          case "certificates": return <TrainingCertificatesTab />
          default:             return null
        }
      }} />
  )
}
