import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { LEGAL_PRACTICE_SECTIONS } from "../../navigation/moduleSections"
import MattersTab from "./MattersTab"
import ClientsTab from "./ClientsTab"
import AttorneysTab from "./AttorneysTab"
import RetainersTab from "./RetainersTab"
import InvoicesTab from "./InvoicesTab"
import ProfileTab from "./ProfileTab"
import DashboardTab from "./DashboardTab"
import CalendarTab from "./CalendarTab"
import ApprovalsTab from "./ApprovalsTab"
import ConflictsTab from "./ConflictsTab"
import DeadlinesTab from "./DeadlinesTab"
import ReconciliationTab from "./ReconciliationTab"
import WallsTab from "./WallsTab"
import KycTab from "./KycTab"
import DocumentsTab from "./DocumentsTab"
import LitigationTab from "./LitigationTab"
import { useNavigate } from "react-router-dom"

export function LegalPracticePage() {
  const navigate = useNavigate()
  return (
    <SectionedModulePage config={LEGAL_PRACTICE_SECTIONS} render={id => {
      switch (id) {
        case "dashboard": return <DashboardTab goTo={sec => navigate(`/legal-practice/${sec}`)} />
        case "calendar":  return <CalendarTab />
        case "matters":   return <MattersTab />
        case "clients":   return <ClientsTab />
        case "attorneys": return <AttorneysTab />
        case "retainers": return <RetainersTab />
        case "invoices":  return <InvoicesTab />
        case "approvals": return <ApprovalsTab />
        case "conflicts": return <ConflictsTab />
        case "deadlines": return <DeadlinesTab />
        case "reconciliation": return <ReconciliationTab />
        case "walls": return <WallsTab />
        case "kyc": return <KycTab />
        case "documents": return <DocumentsTab />
        case "litigation": return <LitigationTab />
        case "profile":   return <ProfileTab />
        default:          return null
      }
    }} />
  )
}
