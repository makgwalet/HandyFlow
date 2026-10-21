// src/pages/hr/HrPage.tsx
//
// Sections are routes (/hr/:section) with navigation in the sidebar (see
// navigation/moduleSections.ts and components/shell/SectionedModulePage).
// HR opens on the employee register (/hr redirects to /hr/employees).
import HrDashboard     from "./HrDashboard"
import EmployeesTab    from "./EmployeesTab"
import LeaveTab        from "./LeaveTab"
import PayrollTab      from "./PayrollTab"
import EmployeeRelationsTab from "./EmployeeRelationsTab"
import ComplianceTab   from "./ComplianceTab"
import PayrollChecksTab from "./PayrollChecksTab"
import TaxCertificatesTab from "./TaxCertificatesTab"
import OnboardingTab from "./OnboardingTab"
import AttendanceTab from "./AttendanceTab"
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
          case "payroll-checks": return <PayrollChecksTab />
          case "tax-certificates": return <TaxCertificatesTab />
          case "onboarding":   return <OnboardingTab />
          case "attendance":   return <AttendanceTab />
          case "disciplinary": return <EmployeeRelationsTab />
          case "compliance":   return <ComplianceTab />
          default:             return null
        }
      }} />
  )
}
