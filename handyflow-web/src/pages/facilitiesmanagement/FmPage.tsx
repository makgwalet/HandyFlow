// src/pages/facilitiesmanagement/FmPage.tsx
//
// Facilities Management (FM provider module, /api/v1/facilitiesmanagement). Sections are routes (/facilitiesmanagement/:section),
// registered in navigation/moduleSections.ts (FM_SECTIONS): work orders, quotes, clients, sites, assets, technicians, vendors, agreements, the SLA calendar, invoicing and secure sharing of completed work's files.
import { useNavigate } from 'react-router-dom'
import { SectionedModulePage } from '../../components/shell/SectionedModulePage'
import { FM_SECTIONS } from '../../navigation/moduleSections'
import FmSharedDocumentsTab from './FmSharedDocumentsTab'
import WorkOrdersTab from './WorkOrdersTab'
import ClientsTab from './ClientsTab'
import SitesTab from './SitesTab'
import AssetsTab from './AssetsTab'
import TechniciansTab from './TechniciansTab'
import VendorsTab from './VendorsTab'
import QuotesTab from './QuotesTab'
import AgreementsTab from './AgreementsTab'
import SlaCalendarTab from './SlaCalendarTab'
import BillingTab from './BillingTab'
import ReportsTab from './ReportsTab'
import DashboardTab from './DashboardTab'
import AuditTab from './AuditTab'
import ClientAccessTab from './ClientAccessTab'

export function FmPage() {
  const navigate = useNavigate()
  return (
    <SectionedModulePage config={FM_SECTIONS} render={(id) => {
      switch (id) {
        case 'dashboard':        return <DashboardTab goTo={sec => navigate(`/facilitiesmanagement/${sec}`)} />
        case 'work-orders':      return <WorkOrdersTab />
        case 'quotes':           return <QuotesTab />
        case 'agreements':       return <AgreementsTab />
        case 'sla-calendar':     return <SlaCalendarTab />
        case 'invoices':         return <BillingTab />
        case 'reports':          return <ReportsTab />
        case 'audit':            return <AuditTab />
        case 'client-access':    return <ClientAccessTab />
        case 'clients':          return <ClientsTab />
        case 'sites':            return <SitesTab />
        case 'assets':           return <AssetsTab />
        case 'technicians':      return <TechniciansTab />
        case 'vendors':          return <VendorsTab />
        case 'shared-documents': return <FmSharedDocumentsTab />
        default:                 return null
      }
    }} />
  )
}
