import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { INSURANCE_BROKERAGE_SECTIONS } from "../../navigation/moduleSections"
import PoliciesTab from "./PoliciesTab"
import ClientsTab from "./ClientsTab"
import InsurersTab from "./InsurersTab"
import CommissionTab from "./CommissionTab"

export function InsuranceBrokeragePage() {
  return (
    <SectionedModulePage config={INSURANCE_BROKERAGE_SECTIONS} render={id => {
      switch (id) {
        case "policies":   return <PoliciesTab />
        case "clients":    return <ClientsTab />
        case "insurers":   return <InsurersTab />
        case "commission": return <CommissionTab />
        default:           return null
      }
    }} />
  )
}
