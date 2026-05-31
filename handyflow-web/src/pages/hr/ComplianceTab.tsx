import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FileText, AlertCircle, CheckCircle } from "lucide-react"

interface Emp201 {
  id: string
  payRunId: string
  periodStart: string
  periodEnd: string
  dueDate: string
  totalPaye: number
  totalUif: number
  totalSdl: number
  totalPayable: number
  status: string
  submittedAt: string | null
}

const STATUS_STYLE: Record<string, { color: string; bg: string; icon: typeof CheckCircle }> = {
  PENDING:   { color: "#D97706", bg: "#FFFBEB", icon: AlertCircle },
  SUBMITTED: { color: "#166534", bg: "#DCFCE7", icon: CheckCircle },
  OVERDUE:   { color: "#DC2626", bg: "#FEF2F2", icon: AlertCircle },
}

export default function ComplianceTab() {
  const { data: emp201s = [], isLoading } = useQuery<Emp201[]>({
    queryKey: ["emp201"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/hr/emp201")
      return r.data
    },
  })

  const fmtR = (n: number) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

  const totalPending  = emp201s.filter(e => e.status === "PENDING").reduce((s, e) => s + Number(e.totalPayable), 0)
  const totalOverdue  = emp201s.filter(e => e.status === "OVERDUE").length

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading EMP201 records...</div>

  return (
    <div>
      {/* Summary */}
      {emp201s.length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 24, flexWrap: "wrap" }}>
          <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, padding: "14px 20px" }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: "#D97706", marginBottom: 2 }}>TOTAL PENDING</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: "#D97706" }}>{fmtR(totalPending)}</div>
          </div>
          {totalOverdue > 0 && (
            <div style={{ background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, padding: "14px 20px" }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: "#DC2626", marginBottom: 2 }}>OVERDUE</div>
              <div style={{ fontSize: 22, fontWeight: 700, color: "#DC2626" }}>{totalOverdue}</div>
            </div>
          )}
          <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "14px 20px" }}>
            <div style={{ fontSize: 11, fontWeight: 600, color: "#64748B", marginBottom: 2 }}>TOTAL DECLARATIONS</div>
            <div style={{ fontSize: 22, fontWeight: 700, color: "#0F172A" }}>{emp201s.length}</div>
          </div>
        </div>
      )}

      {/* Info banner */}
      <div style={{ display: "flex", gap: 10, padding: "12px 16px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 10, marginBottom: 20 }}>
        <FileText size={16} color="#1D4ED8" style={{ flexShrink: 0, marginTop: 1 }} />
        <div style={{ fontSize: 13, color: "#1D4ED8" }}>
          <strong>EMP201</strong> — Monthly employer declaration submitted to SARS by the 7th of each month.
          Contains PAYE, UIF (employer + employee) and SDL contributions.
        </div>
      </div>

      {emp201s.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No EMP201 declarations yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>EMP201 records are generated automatically when you process a pay run.</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC" }}>
                <th style={th}>Period</th>
                <th style={th}>Due Date</th>
                <th style={{ ...th, textAlign: "right" }}>PAYE</th>
                <th style={{ ...th, textAlign: "right" }}>UIF</th>
                <th style={{ ...th, textAlign: "right" }}>SDL</th>
                <th style={{ ...th, textAlign: "right" }}>Total Payable</th>
                <th style={th}>Status</th>
              </tr>
            </thead>
            <tbody>
              {emp201s.map((emp, i) => {
                const style = STATUS_STYLE[emp.status] || { color: "#475569", bg: "#F8FAFC", icon: FileText }
                const Icon = style.icon
                const isOverdue = emp.status === "OVERDUE"
                return (
                  <tr key={emp.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                    <td style={td}>
                      <div style={{ fontWeight: 600, color: "#0F172A", fontSize: 13 }}>
                        {emp.periodStart} → {emp.periodEnd}
                      </div>
                    </td>
                    <td style={td}>
                      <span style={{ color: isOverdue ? "#DC2626" : "#0F172A", fontWeight: isOverdue ? 600 : 400 }}>
                        {emp.dueDate}
                      </span>
                    </td>
                    <td style={{ ...td, textAlign: "right", color: "#DC2626" }}>{fmtR(emp.totalPaye)}</td>
                    <td style={{ ...td, textAlign: "right", color: "#D97706" }}>{fmtR(emp.totalUif)}</td>
                    <td style={{ ...td, textAlign: "right", color: "#7C3AED" }}>{fmtR(emp.totalSdl)}</td>
                    <td style={{ ...td, textAlign: "right" }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{fmtR(emp.totalPayable)}</span>
                    </td>
                    <td style={td}>
                      <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
                        <Icon size={13} color={style.color} />
                        <span style={{ background: style.bg, color: style.color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{emp.status}</span>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot>
              <tr style={{ borderTop: "2px solid #E2E8F0", background: "#F8FAFC" }}>
                <td colSpan={2} style={{ ...td, fontWeight: 700, color: "#0F172A" }}>TOTAL</td>
                <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "#DC2626" }}>
                  {fmtR(emp201s.reduce((s, e) => s + Number(e.totalPaye), 0))}
                </td>
                <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "#D97706" }}>
                  {fmtR(emp201s.reduce((s, e) => s + Number(e.totalUif), 0))}
                </td>
                <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "#7C3AED" }}>
                  {fmtR(emp201s.reduce((s, e) => s + Number(e.totalSdl), 0))}
                </td>
                <td style={{ ...td, textAlign: "right", fontWeight: 700, fontSize: 15, color: "#0F172A" }}>
                  {fmtR(emp201s.reduce((s, e) => s + Number(e.totalPayable), 0))}
                </td>
                <td style={td}></td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </div>
  )
}

const th: React.CSSProperties = { padding: "10px 16px", textAlign: "left", fontSize: 11, fontWeight: 600, color: "#64748B", letterSpacing: "0.05em", borderBottom: "1px solid #E2E8F0" }
const td: React.CSSProperties = { padding: "12px 16px", fontSize: 13, borderBottom: "1px solid #F1F5F9" }
