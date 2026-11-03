// src/pages/facilitiesmanagement/FmPage.tsx
//
// Facilities Management (FM provider module, /api/v1/facilitiesmanagement). Sections are routes (/facilitiesmanagement/:section),
// registered in navigation/moduleSections.ts (FM_SECTIONS). The first screen is secure sharing of completed work orders' files.
import { SectionedModulePage } from '../../components/shell/SectionedModulePage'
import { FM_SECTIONS } from '../../navigation/moduleSections'
import FmSharedDocumentsTab from './FmSharedDocumentsTab'

export function FmPage() {
  return (
    <SectionedModulePage config={FM_SECTIONS} render={(id) => {
      switch (id) {
        case 'shared-documents': return <FmSharedDocumentsTab />
        default:                 return null
      }
    }} />
  )
}
