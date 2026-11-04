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
        case "profile":   return <ProfileTab />
        default:          return null
      }
    }} />
  )
}
