import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { BOOKKEEPING_SECTIONS } from "../../navigation/moduleSections"
import WorkQueueTab from "./WorkQueueTab"
import ClientsTab from "./ClientsTab"
import BankLinesTab from "./BankLinesTab"
import BankRulesTab from "./BankRulesTab"
import QueriesTab from "./QueriesTab"
import HandoverTab from "./HandoverTab"
import PeriodsTab from "./PeriodsTab"

export function BookkeepingPage() {
  return (
    <SectionedModulePage config={BOOKKEEPING_SECTIONS} render={id => {
      switch (id) {
        case "queue":    return <WorkQueueTab />
        case "clients":  return <ClientsTab />
        case "bank":     return <BankLinesTab />
        case "rules":    return <BankRulesTab />
        case "queries":  return <QueriesTab />
        case "handover": return <HandoverTab />
        case "periods":  return <PeriodsTab />
        default:         return null
      }
    }} />
  )
}
