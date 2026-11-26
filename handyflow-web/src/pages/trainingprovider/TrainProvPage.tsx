// src/pages/trainingprovider/TrainProvPage.tsx
//
// Module 4b (Training Provider): a standalone accredited training company
// running courses for external client organisations. Sibling of Module 4a
// (training, internal-only) but a genuinely separate module: no dependency
// in either direction.
//
// Sections are routes (/training-provider/:section) with navigation in the
// sidebar (see navigation/moduleSections.ts and
// components/shell/SectionedModulePage). The client portal lives under
// /training-provider/portal/* and is routed separately, outside this page.
import TrainProvDashboard from "./TrainProvDashboard"
import TrainProvReportingTab from "./TrainProvReportingTab"
import TrainProvClientsTab from "./TrainProvClientsTab"
import TrainProvCoursesTab from "./TrainProvCoursesTab"
import TrainProvSessionsTab from "./TrainProvSessionsTab"
import TrainProvCertificatesTab from "./TrainProvCertificatesTab"
import TrainProvProfileTab from "./TrainProvProfileTab"
import TrainProvAccreditationTab from "./TrainProvAccreditationTab"
import TrainProvCatalogueTab from "./TrainProvCatalogueTab"
import TrainProvLearnersTab from "./TrainProvLearnersTab"
import TrainProvPractitionersTab from "./TrainProvPractitionersTab"
import TrainProvAssessmentsTab from "./TrainProvAssessmentsTab"
import TrainProvCohortsTab from "./TrainProvCohortsTab"
import TrainProvQualityTab from "./TrainProvQualityTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { TRAINING_PROVIDER_SECTIONS } from "../../navigation/moduleSections"

export default function TrainProvPage() {
  return (
    <SectionedModulePage config={TRAINING_PROVIDER_SECTIONS}
      subtitle="Client portfolio · Accredited courses · Sessions & delegates · Certification · Billing"
      render={id => {
        switch (id) {
          case "dashboard":    return <TrainProvDashboard />
          case "clients":      return <TrainProvClientsTab />
          case "courses":      return <TrainProvCoursesTab />
          case "sessions":     return <TrainProvSessionsTab />
          case "certificates": return <TrainProvCertificatesTab />
          case "profile":      return <TrainProvProfileTab />
          case "accreditation": return <TrainProvAccreditationTab />
          case "catalogue":    return <TrainProvCatalogueTab />
          case "learners":     return <TrainProvLearnersTab />
          case "practitioners": return <TrainProvPractitionersTab />
          case "cohorts":      return <TrainProvCohortsTab />
          case "assessment":   return <TrainProvAssessmentsTab />
          case "quality":      return <TrainProvQualityTab />
          case "reporting":    return <TrainProvReportingTab />
          default:             return null
        }
      }} />
  )
}
