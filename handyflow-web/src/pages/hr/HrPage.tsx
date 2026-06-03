// src/pages/hr/HrPage.tsx
import { useState } from "react"
import { Users, Calendar, DollarSign, FileText, AlertOctagon, LayoutDashboard } from "lucide-react"
import HrDashboard     from "./HrDashboard"
import EmployeesTab    from "./EmployeesTab"
import LeaveTab        from "./LeaveTab"
import PayrollTab      from "./PayrollTab"
import DisciplinaryTab from "./DisciplinaryTab"
import ComplianceTab   from "./ComplianceTab"

type Tab = "dashboard" | "employees" | "leave" | "payroll" | "disciplinary" | "compliance"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",    label: "Dashboard",    icon: LayoutDashboard },
  { id: "employees",    label: "Employees",    icon: Users           },
  { id: "leave",        label: "Leave",         icon: Calendar        },
  { id: "payroll",      label: "Payroll",       icon: DollarSign      },
  { id: "disciplinary", label: "Disciplinary",  icon: AlertOctagon    },
  { id: "compliance",   label: "Compliance",    icon: FileText        },
]

export function HrPage() {
  const [tab, setTab] = useState<Tab>("employees")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "#1B3A6B", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Users size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>HR & Payroll</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Employee register · Leave management · PAYE/UIF/SDL payroll · SARS EMP201 compliance
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon   = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 18px",
                  background: "none", border: "none", whiteSpace: "nowrap" as const,
                  borderBottom: active ? "2px solid #1B3A6B" : "2px solid transparent",
                  color: active ? "#1B3A6B" : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 14, cursor: "pointer", marginBottom: -1,
                }}>
                <Icon size={15} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard"    && <HrDashboard onNavigate={(t: Tab) => setTab(t)} />}
        {tab === "employees"    && <EmployeesTab />}
        {tab === "leave"        && <LeaveTab />}
        {tab === "payroll"      && <PayrollTab />}
        {tab === "disciplinary" && <DisciplinaryTab />}
        {tab === "compliance"   && <ComplianceTab />}
      </div>
    </div>
  )
}
