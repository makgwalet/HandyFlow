// src/pages/hr/SarsTab.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FileText, Download, CheckCircle, Clock, AlertTriangle } from "lucide-react"

const unwrapList = (r: any): any[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

const EMP201_STATUS: Record<string, { color: string; bg: string; label: string; icon: React.ElementType }> = {
  PENDING:   { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "Pending",   icon: Clock         },
  SUBMITTED: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", label: "Submitted", icon: CheckCircle   },
  OVERDUE:   { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Overdue",   icon: AlertTriangle },
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
      <div style={{ marginBottom: 22, padding: "16px 20px", background: "var(--hf-info-soft)", border: "1px solid var(--hf-info-border)", borderRadius: 10 }}>
        <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-info-text)", marginBottom: 6 }}>EMP201 — Monthly Employer Declaration</div>
        <div style={{ fontSize: 13, color: "var(--hf-info-text-strong)", lineHeight: 1.6 }}>
          The EMP201 is submitted to SARS by the 7th of each month for the prior month's payroll.
          It declares total PAYE, UIF, and SDL due. Payment must accompany submission.
          Late submission attracts a 10% penalty plus interest at prime rate.
        </div>
      </div>

      {/* YTD totals */}
      {(emp201s as any[]).length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
          {[
            { label: "Total PAYE (YTD)",     value: fmtR(totalPaye),    color: "var(--hf-danger-text)" },
            { label: "Total UIF (YTD)",       value: fmtR(totalUif),     color: "var(--hf-warning-text)" },
            { label: "Total SDL (YTD)",       value: fmtR(totalSdl),     color: "var(--hf-violet-text)" },
            { label: "Total Payable (YTD)",   value: fmtR(totalPayable), color: "var(--hf-primary-text)" },
          ].map(s => (
            <div key={s.label} style={{ flex: 1, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px" }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: s.color }}>{s.value}</div>
              <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 2 }}>{s.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* SARS submission checklist */}
      <div style={{ marginBottom: 22, padding: "14px 18px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Monthly Compliance Checklist</div>
        {[
          { task: "Process payroll and generate payslips",  note: "PAYE, UIF, SDL calculated automatically" },
          { task: "Download EMP201 declaration PDF",         note: "Generated after each pay run" },
          { task: "Submit EMP201 on SARS eFiling",          note: "Due by 7th of following month" },
          { task: "Make payment on SARS eFiling",           note: "Total PAYE + UIF + SDL must be paid simultaneously" },
          { task: "File payslips for employees",            note: "Employees entitled to payslip each pay period (BCEA s.33)" },
        ].map(item => (
          <div key={item.task} style={{ display: "flex", alignItems: "flex-start", gap: 10, padding: "7px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
            <CheckCircle size={14} color="#0D9488" style={{ flexShrink: 0, marginTop: 1 }} />
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)" }}>{item.task}</div>
              <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{item.note}</div>
            </div>
          </div>
        ))}
      </div>

      {/* EMP201 list */}
      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 14 }}>
        <span style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>EMP201 Declarations</span>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading...</div>
      ) : (emp201s as any[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--hf-text-faint)", border: "1px dashed var(--hf-border)", borderRadius: 12 }}>
          <FileText size={32} style={{ marginBottom: 10, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No EMP201 declarations yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>EMP201s are generated automatically after each pay run is processed.</div>
        </div>
      ) : (
        <div style={{ border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "var(--hf-surface-muted)", borderBottom: "1px solid var(--hf-border)" }}>
                {["Period","Due Date","PAYE","UIF","SDL","Total Payable","Status",""].map(h => (
                  <th key={h} style={{ padding: "11px 14px", textAlign: "left", fontWeight: 700, fontSize: 11, color: "var(--hf-text-muted)", letterSpacing: "0.05em" }}>{h}</th>
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
                  <tr key={e.id} style={{ borderBottom: i < emp201s.length - 1 ? "1px solid var(--hf-border-subtle)" : "none" }}>
                    <td style={{ padding: "12px 14px", fontWeight: 600, color: "var(--hf-text)" }}>
                      {fmtDate(e.periodStart)} → {fmtDate(e.periodEnd)}
                    </td>
                    <td style={{ padding: "12px 14px", color: "var(--hf-text-tertiary)" }}>{fmtDate(e.dueDate)}</td>
                    <td style={{ padding: "12px 14px", color: "var(--hf-danger-text)", fontWeight: 600 }}>{fmtR(e.totalPaye)}</td>
                    <td style={{ padding: "12px 14px", color: "var(--hf-warning-text)" }}>{fmtR(e.totalUif)}</td>
                    <td style={{ padding: "12px 14px", color: "var(--hf-violet-text)" }}>{fmtR(e.totalSdl)}</td>
                    <td style={{ padding: "12px 14px", fontWeight: 700, color: "var(--hf-primary-text)" }}>{fmtR(e.totalPayable)}</td>
                    <td style={{ padding: "12px 14px" }}>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        <Icon size={10} />{cfg.label}
                      </span>
                    </td>
                    <td style={{ padding: "12px 14px" }}>
                      <button onClick={() => downloadEmp201(e.id, periodKey)}
                        style={{ display: "flex", alignItems: "center", gap: 5, background: "var(--hf-info-soft)", color: "var(--hf-info-text)", border: "none", borderRadius: 7, padding: "6px 12px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
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
      <div style={{ marginTop: 22, padding: "16px 20px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 10 }}>
        <div style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-warning-text)", marginBottom: 6 }}>Annual — EMP501 & IRP5 (coming soon)</div>
        <div style={{ fontSize: 13, color: "var(--hf-warning-text-deep)", lineHeight: 1.6 }}>
          At year end (February), the EMP501 reconciliation and IRP5 certificates are required for all employees.
          IRP5 generation will be available in a future release. Until then, your PAYE records are maintained per
          employee with year-to-date figures on each payslip.
        </div>
      </div>
    </div>
  )
}
