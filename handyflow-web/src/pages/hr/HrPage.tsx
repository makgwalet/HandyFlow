import { useState } from "react"
import { Users, Calendar, CreditCard, FileText } from "lucide-react"
import EmployeesTab from "./EmployeesTab"
import LeaveTab from "./LeaveTab"
import PayrollTab from "./PayrollTab"
import ComplianceTab from "./ComplianceTab"

type Tab = "employees" | "leave" | "payroll" | "compliance"

const tabs = [
  { id: "employees"  as Tab, label: "Employees",  icon: Users },
  { id: "leave"      as Tab, label: "Leave",       icon: Calendar },
  { id: "payroll"    as Tab, label: "Payroll",     icon: CreditCard },
  { id: "compliance" as Tab, label: "Compliance",  icon: FileText },
]

export function HrPage() {
  const [activeTab, setActiveTab] = useState<Tab>("employees")

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: "#0F172A", margin: "0 0 4px" }}>
          HR & Payroll
        </h1>
        <p style={{ fontSize: 14, color: "#64748B", margin: 0 }}>
          Employee management, leave, payroll and SARS compliance
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: 24 }}>
        <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 24, paddingBottom: 0 }}>
          {tabs.map(tab => {
            const Icon = tab.icon
            const active = activeTab === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 7,
                  padding: "10px 18px", background: "none", border: "none",
                  borderBottom: active ? "2px solid #0D9488" : "2px solid transparent",
                  color: active ? "#0D9488" : "#64748B",
                  fontWeight: active ? 600 : 400,
                  fontSize: 14, cursor: "pointer", marginBottom: -1,
                }}
              >
                <Icon size={15} />{tab.label}
              </button>
            )
          })}
        </div>

        {activeTab === "employees"  && <EmployeesTab />}
        {activeTab === "leave"      && <LeaveTab />}
        {activeTab === "payroll"    && <PayrollTab />}
        {activeTab === "compliance" && <ComplianceTab />}
      </div>
    </div>
  )
}
