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
import TrainProvClientsTab from "./TrainProvClientsTab"
import TrainProvCoursesTab from "./TrainProvCoursesTab"
import TrainProvSessionsTab from "./TrainProvSessionsTab"
import TrainProvCertificatesTab from "./TrainProvCertificatesTab"
import TrainProvProfileTab from "./TrainProvProfileTab"
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
          default:             return null
        }
      }} />
  )
}
