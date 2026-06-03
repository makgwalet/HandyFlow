// src/pages/hr/SarsTab.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FileText, Download, CheckCircle, Clock, AlertTriangle } from "lucide-react"

const unwrapList = (r: any): any[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

const EMP201_STATUS: Record<string, { color: string; bg: string; label: string; icon: React.ElementType }> = {
  PENDING:   { color: "#D97706", bg: "#FFFBEB", label: "Pending",   icon: Clock         },
  SUBMITTED: { color: "#166534", bg: "#DCFCE7", label: "Submitted", icon: CheckCircle   },
  OVERDUE:   { color: "#DC2626", bg: "#FEF2F2", label: "Overdue",   icon: AlertTriangle },
}

export default function SarsTab() {
  const { data: emp201s = [], isLoading } = useQuery<any[]>({
    queryKey: ["hr-emp201"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/hr/emp201")),
  })

  const downloadEmp201 = async (id: string, period: string) => {
    try {
      const res = await apiClient.get(`/api/v1/hr/emp201/${id}/pdf`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
      const a = document.createElement("a"); a.href = url; a.download = `EMP201-${period}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert("Failed to download EMP201") }
  }

  const totalPaye = (emp201s as any[]).reduce((s, e) => s + Number(e.totalPaye ?? 0), 0)
  const totalUif  = (emp201s as any[]).reduce((s, e) => s + Number(e.totalUif  ?? 0), 0)
  const totalSdl  = (emp201s as any[]).reduce((s, e) => s + Number(e.totalSdl  ?? 0), 0)
  const totalPayable = (emp201s as any[]).reduce((s, e) => s + Number(e.totalPayable ?? 0), 0)

  return (
    <div>
      {/* SARS explanation */}
      <div style={{ marginBottom: 22, padding: "16px 20px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 10 }}>
        <div style={{ fontWeight: 700, fontSize: 14, color: "#1D4ED8", marginBottom: 6 }}>EMP201 — Monthly Employer Declaration</div>
        <div style={{ fontSize: 13, color: "#1E40AF", lineHeight: 1.6 }}>
          The EMP201 is submitted to SARS by the 7th of each month for the prior month's payroll.
          It declares total PAYE, UIF, and SDL due. Payment must accompany submission.
          Late submission attracts a 10% penalty plus interest at prime rate.
        </div>
      </div>

      {/* YTD totals */}
      {(emp201s as any[]).length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
          {[
            { label: "Total PAYE (YTD)",     value: fmtR(totalPaye),    color: "#DC2626" },
            { label: "Total UIF (YTD)",       value: fmtR(totalUif),     color: "#D97706" },
            { label: "Total SDL (YTD)",       value: fmtR(totalSdl),     color: "#7C3AED" },
            { label: "Total Payable (YTD)",   value: fmtR(totalPayable), color: "#1B3A6B" },
          ].map(s => (
            <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: s.color }}>{s.value}</div>
              <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* SARS submission checklist */}
      <div style={{ marginBottom: 22, padding: "14px 18px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Monthly Compliance Checklist</div>
        {[
          { task: "Process payroll and generate payslips",  note: "PAYE, UIF, SDL calculated automatically" },
          { task: "Download EMP201 declaration PDF",         note: "Generated after each pay run" },
          { task: "Submit EMP201 on SARS eFiling",          note: "Due by 7th of following month" },
          { task: "Make payment on SARS eFiling",           note: "Total PAYE + UIF + SDL must be paid simultaneously" },
          { task: "File payslips for employees",            note: "Employees entitled to payslip each pay period (BCEA s.33)" },
        ].map(item => (
          <div key={item.task} style={{ display: "flex", alignItems: "flex-start", gap: 10, padding: "7px 0", borderBottom: "1px solid #F1F5F9" }}>
            <CheckCircle size={14} color="#0D9488" style={{ flexShrink: 0, marginTop: 1 }} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#374151" }}>{item.task}</div>
              <div style={{ fontSize: 11, color: "#94A3B8" }}>{item.note}</div>
            </div>
          </div>
        ))}
      </div>

      {/* EMP201 list */}
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
        <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>EMP201 Declarations</span>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : (emp201s as any[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          <FileText size={32} style={{ marginBottom: 10, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No EMP201 declarations yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>EMP201s are generated automatically after each pay run is processed.</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Period","Due Date","PAYE","UIF","SDL","Total Payable","Status",""].map(h => (
                  <th key={h} style={{ padding: "11px 14px", textAlign: "left", fontWeight: 700, fontSize: 11, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(emp201s as any[]).map((e: any, i) => {
                const cfg    = EMP201_STATUS[e.status] ?? EMP201_STATUS.PENDING
                const Icon   = cfg.icon
                const period = e.periodEnd ? new Date(e.periodEnd).toLocaleDateString("en-ZA", { month: "short", year: "numeric" }) : "—"
                const periodKey = e.periodEnd ? new Date(e.periodEnd).toISOString().slice(0, 7) : "period"
                return (
                  <tr key={e.id} style={{ borderBottom: i < emp201s.length - 1 ? "1px solid #F1F5F9" : "none" }}>
                    <td style={{ padding: "12px 14px", fontWeight: 600, color: "#0F172A" }}>
                      {fmtDate(e.periodStart)} → {fmtDate(e.periodEnd)}
                    </td>
                    <td style={{ padding: "12px 14px", color: "#475569" }}>{fmtDate(e.dueDate)}</td>
                    <td style={{ padding: "12px 14px", color: "#DC2626", fontWeight: 600 }}>{fmtR(e.totalPaye)}</td>
                    <td style={{ padding: "12px 14px", color: "#D97706" }}>{fmtR(e.totalUif)}</td>
                    <td style={{ padding: "12px 14px", color: "#7C3AED" }}>{fmtR(e.totalSdl)}</td>
                    <td style={{ padding: "12px 14px", fontWeight: 700, color: "#1B3A6B" }}>{fmtR(e.totalPayable)}</td>
                    <td style={{ padding: "12px 14px" }}>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        <Icon size={10} />{cfg.label}
                      </span>
                    </td>
                    <td style={{ padding: "12px 14px" }}>
                      <button onClick={() => downloadEmp201(e.id, periodKey)}
                        style={{ display: "flex", alignItems: "center", gap: 5, background: "#EFF6FF", color: "#1D4ED8", border: "none", borderRadius: 7, padding: "6px 12px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <Download size={12} /> EMP201
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* IRP5 / EMP501 callout */}
      <div style={{ marginTop: 22, padding: "16px 20px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10 }}>
        <div style={{ fontWeight: 700, fontSize: 13, color: "#D97706", marginBottom: 6 }}>Annual — EMP501 & IRP5 (coming soon)</div>
        <div style={{ fontSize: 13, color: "#78350F", lineHeight: 1.6 }}>
          At year end (February), the EMP501 reconciliation and IRP5 certificates are required for all employees.
          IRP5 generation will be available in a future release. Until then, your PAYE records are maintained per
          employee with year-to-date figures on each payslip.
        </div>
      </div>
    </div>
  )
}
