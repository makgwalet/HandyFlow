// src/pages/hr/HrPage.tsx
//
// Sections are routes (/hr/:section) with navigation in the sidebar (see
// navigation/moduleSections.ts and components/shell/SectionedModulePage).
// HR opens on the employee register (/hr redirects to /hr/employees).
import HrDashboard     from "./HrDashboard"
import EmployeesTab    from "./EmployeesTab"
import LeaveTab        from "./LeaveTab"
import PayrollTab      from "./PayrollTab"
import DisciplinaryTab from "./DisciplinaryTab"
import ComplianceTab   from "./ComplianceTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { HR_SECTIONS } from "../../navigation/moduleSections"

export function HrPage() {
  return (
    <SectionedModulePage config={HR_SECTIONS}
      subtitle="Employee register · Leave management · PAYE/UIF/SDL payroll · SARS EMP201 compliance"
      render={(id, goTo) => {
        switch (id) {
          case "dashboard":    return <HrDashboard onNavigate={goTo} />
          case "employees":    return <EmployeesTab />
          case "leave":        return <LeaveTab />
          case "payroll":      return <PayrollTab />
          case "disciplinary": return <DisciplinaryTab />
          case "compliance":   return <ComplianceTab />
          default:             return null
        }
      }} />
  )
}
