// src/pages/hr/ComplianceTab.tsx
// KEY FIXES:
// 1. API unwrap — was r.data directly, needs unwrapList (ApiResponse<List<Emp201>>)
// 2. PDF download button added for each EMP201
// 3. Date formatting — was raw ISO strings, now human-readable
// 4. YTD totals added
// 5. IRP5/EMP501 guidance added
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FileText, AlertCircle, CheckCircle, Download, Clock } from "lucide-react"

// FIX: correct ApiResponse<List<Emp201>> unwrap
const unwrapList = (r: any): any[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const STATUS_CFG: Record<string, { color: string; bg: string; icon: React.ElementType; label: string }> = {
  PENDING:   { color: "#D97706", bg: "#FFFBEB", icon: Clock,         label: "Pending"   },
  SUBMITTED: { color: "#166534", bg: "#DCFCE7", icon: CheckCircle,   label: "Submitted" },
  OVERDUE:   { color: "#DC2626", bg: "#FEF2F2", icon: AlertCircle,   label: "Overdue"   },
}

export default function ComplianceTab() {
  // FIX: unwrapList — was r.data which missed the ApiResponse wrapper entirely
  const { data: emp201s = [], isLoading } = useQuery<any[]>({
    queryKey: ["emp201"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/hr/emp201")),
  })

  // FIX: no window.__AUTH_TOKEN__ — apiClient handles auth headers
  const downloadEmp201 = async (id: string, periodEnd: string) => {
    try {
      const period = periodEnd ? new Date(periodEnd).toISOString().slice(0, 7) : "period"
      const res = await apiClient.get(`/api/v1/hr/emp201/${id}/pdf`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
      const a = document.createElement("a"); a.href = url; a.download = `EMP201-${period}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert("Failed to download EMP201 PDF") }
  }

  const totalPaye     = emp201s.reduce((s, e) => s + Number(e.totalPaye ?? 0), 0)
  const totalUif      = emp201s.reduce((s, e) => s + Number(e.totalUif ?? 0), 0)
  const totalSdl      = emp201s.reduce((s, e) => s + Number(e.totalSdl ?? 0), 0)
  const totalPayable  = emp201s.reduce((s, e) => s + Number(e.totalPayable ?? 0), 0)
  const pendingTotal  = emp201s.filter(e => e.status === "PENDING").reduce((s, e) => s + Number(e.totalPayable ?? 0), 0)
  const overdueCount  = emp201s.filter(e => e.status === "OVERDUE").length

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading EMP201 records...</div>

  return (
    <div>
      {/* Summary KPIs */}
      {emp201s.length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 22, flexWrap: "wrap" }}>
          {[
            { label: "Total PAYE (YTD)",    value: fmtR(totalPaye),    color: "#DC2626", bg: "#FEF2F2" },
            { label: "Total UIF (YTD)",      value: fmtR(totalUif),     color: "#D97706", bg: "#FFFBEB" },
            { label: "Total SDL (YTD)",      value: fmtR(totalSdl),     color: "#7C3AED", bg: "#F5F3FF" },
            { label: "Total Payable (YTD)",  value: fmtR(totalPayable), color: "#1B3A6B", bg: "#EFF6FF" },
            { label: "Pending this month",   value: fmtR(pendingTotal), color: pendingTotal > 0 ? "#D97706" : "#166534", bg: pendingTotal > 0 ? "#FFFBEB" : "#DCFCE7" },
          ].map(s => (
            <div key={s.label} style={{ background: s.bg, borderRadius: 10, padding: "12px 16px", minWidth: 140 }}>
              <div style={{ fontSize: 15, fontWeight: 700, color: s.color }}>{s.value}</div>
              <div style={{ fontSize: 11, color: s.color, marginTop: 2, opacity: 0.8 }}>{s.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* Overdue alert */}
      {overdueCount > 0 && (
        <div style={{ marginBottom: 18, padding: "12px 16px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertCircle size={16} color="#DC2626" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#DC2626" }}>{overdueCount} overdue EMP201 declaration{overdueCount > 1 ? "s" : ""}</div>
            <div style={{ fontSize: 12, color: "#B91C1C" }}>Late submission attracts a 10% penalty plus interest. Submit on SARS eFiling immediately.</div>
          </div>
        </div>
      )}

      {/* Info */}
      <div style={{ display: "flex", gap: 10, padding: "12px 16px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 10, marginBottom: 22 }}>
        <FileText size={16} color="#1D4ED8" style={{ flexShrink: 0, marginTop: 1 }} />
        <div style={{ fontSize: 13, color: "#1D4ED8" }}>
          <strong>EMP201</strong> — monthly employer declaration, due to SARS by the 7th of each following month.
          Download the PDF, then submit and pay on SARS eFiling. Contains PAYE + UIF (employer + employee) + SDL.
        </div>
      </div>

      {emp201s.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No EMP201 declarations yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>EMP201s are generated automatically when a pay run is processed.</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC" }}>
                {["Period","Due Date","PAYE","UIF","SDL","Total Payable","Status",""].map(h => (
                  <th key={h} style={{ padding: "11px 14px", textAlign: "left" as const, fontSize: 11, fontWeight: 700, color: "#64748B", letterSpacing: "0.05em", borderBottom: "1px solid #E2E8F0" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {emp201s.map((e: any, i) => {
                const cfg    = STATUS_CFG[e.status] ?? STATUS_CFG.PENDING
                const Icon   = cfg.icon
                const isOvr  = e.status === "OVERDUE"
                return (
                  <tr key={e.id} style={{ background: i % 2 === 0 ? "#fff" : "#FAFAFA", borderBottom: i < emp201s.length - 1 ? "1px solid #F1F5F9" : "none" }}>
                    {/* FIX: format dates, was showing raw ISO strings */}
                    <td style={{ padding: "12px 14px", fontWeight: 600, color: "#0F172A", fontSize: 13, whiteSpace: "nowrap" as const }}>
                      {fmtDate(e.periodStart)} → {fmtDate(e.periodEnd)}
                    </td>
                    <td style={{ padding: "12px 14px", color: isOvr ? "#DC2626" : "#475569", fontWeight: isOvr ? 700 : 400, whiteSpace: "nowrap" as const }}>
                      {fmtDate(e.dueDate)}
                    </td>
                    <td style={{ padding: "12px 14px", color: "#DC2626", fontWeight: 600 }}>{fmtR(e.totalPaye)}</td>
                    <td style={{ padding: "12px 14px", color: "#D97706" }}>{fmtR(e.totalUif)}</td>
                    <td style={{ padding: "12px 14px", color: "#7C3AED" }}>{fmtR(e.totalSdl)}</td>
                    <td style={{ padding: "12px 14px", fontWeight: 800, fontSize: 14, color: "#0F172A" }}>{fmtR(e.totalPayable)}</td>
                    <td style={{ padding: "12px 14px" }}>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        <Icon size={10} />{cfg.label}
                      </span>
                    </td>
                    {/* FIX: PDF download button added — was missing entirely */}
                    <td style={{ padding: "12px 14px" }}>
                      <button onClick={() => downloadEmp201(e.id, e.periodEnd)}
                        style={{ display: "flex", alignItems: "center", gap: 5, background: "#EFF6FF", color: "#1D4ED8", border: "none", borderRadius: 7, padding: "6px 12px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        <Download size={12} /> EMP201
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot>
              <tr style={{ borderTop: "2px solid #E2E8F0", background: "#F8FAFC" }}>
                <td colSpan={2} style={{ padding: "11px 14px", fontWeight: 700, fontSize: 13, color: "#0F172A" }}>TOTAL ({emp201s.length} declarations)</td>
                <td style={{ padding: "11px 14px", fontWeight: 700, color: "#DC2626" }}>{fmtR(totalPaye)}</td>
                <td style={{ padding: "11px 14px", fontWeight: 700, color: "#D97706" }}>{fmtR(totalUif)}</td>
                <td style={{ padding: "11px 14px", fontWeight: 700, color: "#7C3AED" }}>{fmtR(totalSdl)}</td>
                <td style={{ padding: "11px 14px", fontWeight: 800, fontSize: 15, color: "#0F172A" }}>{fmtR(totalPayable)}</td>
                <td colSpan={2} />
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      {/* IRP5 / EMP501 guidance */}
      <div style={{ marginTop: 22, padding: "16px 20px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10 }}>
        <div style={{ fontWeight: 700, fontSize: 13, color: "#D97706", marginBottom: 6 }}>Annual Compliance — EMP501 Reconciliation & IRP5 Certificates (Phase 2)</div>
        <div style={{ fontSize: 13, color: "#78350F", lineHeight: 1.6 }}>
          At tax year end (February), you must submit an EMP501 reconciliation to SARS and issue IRP5 certificates to all employees.
          The EMP501 reconciles all EMP201 monthly submissions against the total PAYE, UIF, and SDL declared.
          IRP5 generation will be available in a future release. Until then, year-to-date figures appear on each payslip and
          can be used to manually prepare your EMP501 on SARS eFiling.
        </div>
      </div>
    </div>
  )
}
