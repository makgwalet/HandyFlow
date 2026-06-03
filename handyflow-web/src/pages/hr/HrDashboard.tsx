// src/pages/hr/HrDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Users, Calendar, DollarSign, AlertOctagon, ArrowRight, AlertTriangle, CheckCircle, Clock } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtR   = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const DEPT_COLORS = ["#1B3A6B","#0D9488","#D97706","#7C3AED","#DC2626","#166534","#1D4ED8"]

export default function HrDashboard({ onNavigate }: { onNavigate: (t: any) => void }) {
  const { data: employees = [] } = useQuery({
    queryKey: ["hr-employees"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/hr/employees?size=200")),
  })
  const { data: leaveRequests = [] } = useQuery({
    queryKey: ["hr-leave-requests"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/hr/leave-requests?size=200")),
  })
  const { data: payRuns = [] } = useQuery({
    queryKey: ["hr-pay-runs"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/hr/pay-runs?size=24")),
  })

  const emps  = employees  as any[]
  const leaves = leaveRequests as any[]
  const prs    = payRuns as any[]

  const active      = emps.filter(e => e.status === "ACTIVE")
  const pendingLeave = leaves.filter(l => l.status === "PENDING")
  const lastPayRun  = prs[0]

  // Department breakdown
  const deptMap: Record<string, number> = {}
  active.forEach(e => { if (e.department) deptMap[e.department] = (deptMap[e.department] ?? 0) + 1 })
  const depts = Object.entries(deptMap).sort((a, b) => b[1] - a[1])

  // Employment type breakdown
  const typeMap: Record<string, number> = {}
  active.forEach(e => { const t = e.employmentType ?? "UNKNOWN"; typeMap[t] = (typeMap[t] ?? 0) + 1 })

  const kpis = [
    { label: "Active employees",   value: active.length,          color: "#1B3A6B", bg: "#EFF6FF",  icon: Users,        tab: "employees" },
    { label: "Pending leave",      value: pendingLeave.length,    color: pendingLeave.length > 0 ? "#D97706" : "#166534", bg: pendingLeave.length > 0 ? "#FFFBEB" : "#DCFCE7", icon: Calendar, tab: "leave" },
    { label: "Payroll this month", value: lastPayRun ? fmtR(lastPayRun.totalNet) : "—", color: "#0D9488", bg: "#F0FDF4", icon: DollarSign, tab: "payroll" },
    { label: "Employees on leave", value: leaves.filter(l => l.status === "APPROVED" && new Date(l.startDate) <= new Date() && new Date(l.endDate) >= new Date()).length, color: "#7C3AED", bg: "#F5F3FF", icon: Calendar, tab: "leave" },
  ]

  return (
    <div>
      {/* KPIs */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14, marginBottom: 28 }}>
        {kpis.map(k => (
          <div key={k.label} onClick={() => onNavigate(k.tab)}
            style={{ background: k.bg, borderRadius: 12, padding: "18px 20px", cursor: "pointer" }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: k.color, textTransform: "uppercase" as const }}>{k.label}</div>
              <k.icon size={16} color={k.color} />
            </div>
            <div style={{ fontSize: 24, fontWeight: 800, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      {/* Pending leave alert */}
      {pendingLeave.length > 0 && (
        <div style={{ marginBottom: 22, padding: "14px 18px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, display: "flex", alignItems: "center", gap: 12 }}>
          <AlertTriangle size={17} color="#D97706" style={{ flexShrink: 0 }} />
          <div style={{ flex: 1 }}>
            <div style={{ fontWeight: 700, fontSize: 14, color: "#D97706" }}>{pendingLeave.length} Leave Request{pendingLeave.length > 1 ? "s" : ""} Awaiting Approval</div>
            <div style={{ fontSize: 12, color: "#92400E" }}>{pendingLeave.slice(0, 3).map((l: any) => `${l.employeeName} — ${l.leaveType}`).join(" · ")}</div>
          </div>
          <button onClick={() => onNavigate("leave")} style={{ padding: "6px 14px", background: "#D97706", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer", flexShrink: 0 }}>
            Review
          </button>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "1fr 300px", gap: 18 }}>
        <div>
          {/* Department breakdown */}
          <div style={{ marginBottom: 22 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
              <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Headcount by Department</span>
              <button onClick={() => onNavigate("employees")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                View all <ArrowRight size={13} />
              </button>
            </div>
            {depts.length === 0 ? (
              <div style={{ textAlign: "center", padding: "30px 20px", border: "1px dashed #E2E8F0", borderRadius: 10, color: "#94A3B8", fontSize: 13 }}>No employees registered yet</div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {depts.map(([dept, count], i) => {
                  const pct = Math.round((count / active.length) * 100)
                  const color = DEPT_COLORS[i % DEPT_COLORS.length]
                  return (
                    <div key={dept} style={{ display: "flex", alignItems: "center", gap: 12 }}>
                      <div style={{ width: 120, fontSize: 13, fontWeight: 600, color: "#374151", flexShrink: 0 }}>{dept}</div>
                      <div style={{ flex: 1, height: 8, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
                        <div style={{ height: "100%", width: `${pct}%`, background: color, borderRadius: 99, transition: "width 0.5s" }} />
                      </div>
                      <div style={{ width: 60, textAlign: "right" as const, fontSize: 13, fontWeight: 700, color, flexShrink: 0 }}>{count} ({pct}%)</div>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Recent leave requests */}
          <div>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
              <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Recent Leave Requests</span>
              <button onClick={() => onNavigate("leave")} style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#1B3A6B", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                Manage <ArrowRight size={13} />
              </button>
            </div>
            {leaves.length === 0 ? (
              <div style={{ textAlign: "center", padding: "30px 20px", border: "1px dashed #E2E8F0", borderRadius: 10, color: "#94A3B8", fontSize: 13 }}>No leave requests</div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {leaves.slice(0, 5).map((l: any) => {
                  const statusCfg: Record<string, { color: string; bg: string }> = {
                    PENDING:  { color: "#D97706", bg: "#FFFBEB" },
                    APPROVED: { color: "#166534", bg: "#DCFCE7" },
                    REJECTED: { color: "#DC2626", bg: "#FEF2F2" },
                  }
                  const cfg = statusCfg[l.status] ?? statusCfg.PENDING
                  return (
                    <div key={l.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff" }}>
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{l.employeeName}</div>
                        <div style={{ fontSize: 12, color: "#64748B" }}>{l.leaveType} · {fmtDate(l.startDate)} → {fmtDate(l.endDate)} · {l.daysRequested} days</div>
                      </div>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{l.status}</span>
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>

        {/* Sidebar */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Last pay run summary */}
          <div style={{ background: "#1B3A6B", borderRadius: 12, padding: 20, color: "#fff" }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.6)", marginBottom: 14, textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>
              {lastPayRun ? `Last Pay Run · ${lastPayRun.payRunNumber}` : "No Pay Runs Yet"}
            </div>
            {lastPayRun ? [
              { label: "Total gross",     value: fmtR(lastPayRun.totalGross) },
              { label: "Total PAYE",      value: fmtR(lastPayRun.totalPaye) },
              { label: "Total UIF",       value: fmtR(lastPayRun.totalUif) },
              { label: "Net pay",         value: fmtR(lastPayRun.totalNet) },
              { label: "Employees",       value: lastPayRun.employeeCount },
            ].map(s => (
              <div key={s.label} style={{ display: "flex", justifyContent: "space-between", padding: "7px 0", borderBottom: "1px solid rgba(255,255,255,0.1)" }}>
                <span style={{ fontSize: 13, color: "rgba(255,255,255,0.8)" }}>{s.label}</span>
                <span style={{ fontSize: 14, fontWeight: 700, color: "#fff" }}>{s.value}</span>
              </div>
            )) : (
              <div style={{ fontSize: 13, color: "rgba(255,255,255,0.6)" }}>Process your first pay run to see payroll summaries here.</div>
            )}
            <button onClick={() => onNavigate("payroll")} style={{ marginTop: 14, width: "100%", padding: "8px", background: "rgba(255,255,255,0.15)", border: "1px solid rgba(255,255,255,0.2)", borderRadius: 7, fontSize: 12, fontWeight: 600, color: "#fff", cursor: "pointer" }}>
              {lastPayRun ? "View payroll →" : "Start payroll →"}
            </button>
          </div>

          {/* Employment type breakdown */}
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: 16 }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 12 }}>Employment Types</div>
            {Object.entries(typeMap).map(([type, count]) => (
              <div key={type} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: "1px solid #F1F5F9", fontSize: 13 }}>
                <span style={{ color: "#475569" }}>{type.replace("_", " ")}</span>
                <span style={{ fontWeight: 700, color: "#1B3A6B" }}>{count}</span>
              </div>
            ))}
            {Object.keys(typeMap).length === 0 && <div style={{ fontSize: 13, color: "#94A3B8" }}>No employees yet</div>}
          </div>

          {/* Quick actions */}
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", marginBottom: 10 }}>Quick actions</div>
            {[
              { label: "Add employee",       tab: "employees",    color: "#1B3A6B" },
              { label: "Approve leave",      tab: "leave",        color: "#D97706" },
              { label: "Run payroll",        tab: "payroll",      color: "#0D9488" },
              { label: "Download EMP201",    tab: "sars",         color: "#7C3AED" },
            ].map(a => (
              <button key={a.label} onClick={() => onNavigate(a.tab)}
                style={{ width: "100%", marginBottom: 8, padding: "9px 14px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, fontWeight: 600, color: a.color, cursor: "pointer", textAlign: "left" as const, display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                {a.label} <ArrowRight size={13} />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
