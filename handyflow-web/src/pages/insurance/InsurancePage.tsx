import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { INSURANCE_SECTIONS } from "../../navigation/moduleSections"
import PoliciesTab from "./PoliciesTab"

export function InsurancePage() {
  return (
    <SectionedModulePage config={INSURANCE_SECTIONS} render={id => {
      switch (id) {
        case "policies": return <PoliciesTab />
        default:         return null
      }
    }} />
  )
}
