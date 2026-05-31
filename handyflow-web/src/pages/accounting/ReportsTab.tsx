import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BarChart2, TrendingUp, TrendingDown, Minus } from "lucide-react"

interface ReportLine {
  accountCode: string
  accountName: string
  amount: number
}

interface ReportSection {
  title: string
  lines: ReportLine[]
  total: number
}

interface Report {
  reportType: string
  fromDate: string
  toDate: string
  sections: ReportSection[]
  netResult: number
}

type ReportType = "profit-and-loss" | "trial-balance"

const getDefaultDates = () => {
  const now = new Date()
  const from = new Date(now.getFullYear(), 0, 1).toISOString().split("T")[0]  // Jan 1 this year
  const to   = now.toISOString().split("T")[0]
  return { from, to }
}

export default function ReportsTab() {
  const defaults = getDefaultDates()
  const [reportType, setReportType] = useState<ReportType>("profit-and-loss")
  const [from, setFrom] = useState(defaults.from)
  const [to, setTo]     = useState(defaults.to)
  const [run, setRun]   = useState(false)

  const { data: report, isLoading, refetch } = useQuery<Report>({
    queryKey: ["report", reportType, from, to],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/accounting/reports/${reportType}?from=${from}&to=${to}`)
      return res.data
    },
    enabled: run,
  })

  const handleRun = () => {
    setRun(true)
    refetch()
  }

  const fmtR = (n: number) => {
    if (n == null) return "R 0.00"
    const abs = Math.abs(n)
    const formatted = `R ${abs.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
    return n < 0 ? `(${formatted})` : formatted
  }

  const netColor = (report?.netResult ?? 0) >= 0 ? "#166534" : "#DC2626"
  const NetIcon  = (report?.netResult ?? 0) >= 0 ? TrendingUp : TrendingDown

  return (
    <div>
      {/* Controls */}
      <div style={{ display: "flex", gap: 12, alignItems: "flex-end", marginBottom: 24, flexWrap: "wrap" }}>
        <div>
          <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#64748B", marginBottom: 6 }}>REPORT TYPE</label>
          <div style={{ display: "flex", gap: 6 }}>
            {(["profit-and-loss", "trial-balance", "balance-sheet"] as ReportType[]).map(type => (
              <button
                key={type}
                onClick={() => { setReportType(type); setRun(false) }}
                style={{
                  padding: "8px 16px", borderRadius: 7, fontSize: 13, cursor: "pointer",
                  border: reportType === type ? "1px solid #1B3A6B" : "1px solid #E2E8F0",
                  background: reportType === type ? "#1B3A6B" : "#fff",
                  color: reportType === type ? "#fff" : "#64748B",
                  fontWeight: reportType === type ? 600 : 400,
                }}
              >
                {type === "profit-and-loss" ? "Profit & Loss"
                  : type === "trial-balance" ? "Trial Balance"
                  : "Balance Sheet"}
              </button>
            ))}
          </div>
        </div>
        <div>
          <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#64748B", marginBottom: 6 }}>FROM</label>
          <input type="date" value={from} onChange={e => { setFrom(e.target.value); setRun(false) }} style={dateInput} />
        </div>
        <div>
          <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#64748B", marginBottom: 6 }}>TO</label>
          <input type="date" value={to} onChange={e => { setTo(e.target.value); setRun(false) }} style={dateInput} />
        </div>
        <button onClick={handleRun} style={{ padding: "9px 20px", background: "#0D9488", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          Run Report
        </button>
      </div>

      {/* Empty state */}
      {!run && !isLoading && (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <BarChart2 size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a report type and date range</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Click Run Report to generate financial statements.</div>
        </div>
      )}

      {isLoading && (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Generating report...</div>
      )}

      {report && !isLoading && (
        <div>
          {/* Report header */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20, padding: "16px 20px", background: "#F8FAFC", borderRadius: 10, border: "1px solid #E2E8F0" }}>
            <div>
              <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>
                {report.reportType === "PROFIT_AND_LOSS" ? "Profit & Loss Statement" : "Trial Balance"}
              </h3>
              <div style={{ fontSize: 13, color: "#64748B" }}>
                Period: {report.fromDate} to {report.toDate}
              </div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontSize: 11, color: "#94A3B8", marginBottom: 2 }}>
                {report.reportType === "PROFIT_AND_LOSS" ? "NET PROFIT / LOSS" : "NET BALANCE"}
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6, justifyContent: "flex-end" }}>
                <NetIcon size={18} color={netColor} />
                <span style={{ fontSize: 22, fontWeight: 700, color: netColor }}>{fmtR(report.netResult)}</span>
              </div>
            </div>
          </div>

          {/* Sections */}
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            {report.sections?.map((section, si) => (
              <div key={si} style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
                <div style={{ padding: "12px 18px", background: "#1B3A6B", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span style={{ fontWeight: 700, fontSize: 13, color: "#fff", letterSpacing: "0.04em" }}>{section.title}</span>
                  <span style={{ fontWeight: 700, fontSize: 14, color: "#fff" }}>{fmtR(section.total)}</span>
                </div>

                {section.lines?.length > 0 ? (
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <tbody>
                      {section.lines.map((line, li) => (
                        <tr key={li} style={{ background: li % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                          <td style={{ padding: "9px 18px", fontSize: 12, color: "#64748B", fontFamily: "monospace", width: 80 }}>{line.accountCode}</td>
                          <td style={{ padding: "9px 18px", fontSize: 13, color: "#0F172A" }}>{line.accountName}</td>
                          <td style={{ padding: "9px 18px", fontSize: 13, fontWeight: 500, textAlign: "right", color: line.amount < 0 ? "#DC2626" : "#0F172A" }}>
                            {fmtR(line.amount)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr style={{ borderTop: "2px solid #E2E8F0", background: "#F8FAFC" }}>
                        <td colSpan={2} style={{ padding: "10px 18px", fontWeight: 700, color: "#0F172A", fontSize: 13 }}>
                          Total {section.title}
                        </td>
                        <td style={{ padding: "10px 18px", fontWeight: 700, color: "#0F172A", fontSize: 14, textAlign: "right" }}>
                          {fmtR(section.total)}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                ) : (
                  <div style={{ padding: "14px 18px", color: "#94A3B8", fontSize: 13 }}>No transactions in this period.</div>
                )}
              </div>
            ))}
          </div>

          {/* Net result footer */}
          <div style={{ marginTop: 16, padding: "16px 20px", background: netColor === "#166534" ? "#DCFCE7" : "#FEF2F2", borderRadius: 10, border: `1px solid ${netColor === "#166534" ? "#86EFAC" : "#FECACA"}`, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <NetIcon size={20} color={netColor} />
              <span style={{ fontWeight: 700, fontSize: 15, color: netColor }}>
                {report.reportType === "PROFIT_AND_LOSS"
                  ? ((report.netResult ?? 0) >= 0 ? "Net Profit" : "Net Loss")
                  : "Net Balance"}
              </span>
            </div>
            <span style={{ fontWeight: 700, fontSize: 20, color: netColor }}>{fmtR(report.netResult)}</span>
          </div>
        </div>
      )}
    </div>
  )
}

const dateInput: React.CSSProperties = { padding: "8px 12px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13, background: "#fff" }
