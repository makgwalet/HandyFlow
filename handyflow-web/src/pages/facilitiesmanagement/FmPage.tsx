// src/pages/facilitiesmanagement/FmPage.tsx
//
// Facilities Management (FM provider module, /api/v1/facilitiesmanagement). Sections are routes (/facilitiesmanagement/:section),
// registered in navigation/moduleSections.ts (FM_SECTIONS): work orders, clients, sites, assets and secure sharing of completed work's files.
import { SectionedModulePage } from '../../components/shell/SectionedModulePage'
import { FM_SECTIONS } from '../../navigation/moduleSections'
import FmSharedDocumentsTab from './FmSharedDocumentsTab'
import WorkOrdersTab from './WorkOrdersTab'
import ClientsTab from './ClientsTab'
import SitesTab from './SitesTab'
import AssetsTab from './AssetsTab'

export function FmPage() {
  return (
    <SectionedModulePage config={FM_SECTIONS} render={(id) => {
      switch (id) {
        case 'work-orders':      return <WorkOrdersTab />
        case 'clients':          return <ClientsTab />
        case 'sites':            return <SitesTab />
        case 'assets':           return <AssetsTab />
        case 'shared-documents': return <FmSharedDocumentsTab />
        default:                 return null
      }
    }} />
  )
}
