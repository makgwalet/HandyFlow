import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { BarChart2, TrendingUp, TrendingDown, Scale, X, ChevronRight } from "lucide-react"
import { apiClient } from "../../api/client"

type ReportType = "profit-and-loss" | "balance-sheet" | "trial-balance"

interface ReportLine { accountCode: string; accountName: string; amount: number; grossDebit?: number; grossCredit?: number }
interface ReportSection { title: string; lines: ReportLine[]; total: number }
interface Report { reportType: string; fromDate: string; toDate: string; sections: ReportSection[]; netResult: number }

interface DrillDownLine { journalEntryId: string; entryNumber: string; entryDate: string
  entryDescription: string; lineDescription: string | null; debitAmount: number; creditAmount: number }
interface DrillDown { accountCode: string; accountName: string; openingBalance: number
  lines: DrillDownLine[]; totalDebit: number; totalCredit: number; netMovement: number }

const fmtR = (n: number) => n == null ? "—" : `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtDt = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const inp: React.CSSProperties = {
  padding: "8px 12px", border: "1.5px solid #E2E8F0",
  borderRadius: 8, fontSize: 13, outline: "none",
}

const REPORT_CONFIG = {
  "profit-and-loss": { label: "Profit & Loss",  icon: TrendingUp,  color: "#0D9488", path: "profit-and-loss" },
  "balance-sheet":   { label: "Balance Sheet",  icon: Scale,       color: "#1B3A6B", path: "balance-sheet"  },
  "trial-balance":   { label: "Trial Balance",  icon: BarChart2,   color: "#7C3AED", path: "trial-balance"  },
}

const now = new Date()
const DEFAULT_FROM = `${now.getFullYear()}-01-01`
const DEFAULT_TO   = now.toISOString().split("T")[0]

export default function ReportsTab() {
  const [reportType, setReportType] = useState<ReportType>("profit-and-loss")
  const [from, setFrom] = useState(DEFAULT_FROM)
  const [to,   setTo]   = useState(DEFAULT_TO)
  const [run,  setRun]  = useState(false)

  const [report, setReport] = useState<Report | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isError, setIsError] = useState(false)

  const [drillCode, setDrillCode] = useState<string | null>(null)

  const { data: drillDown, isLoading: drillLoading, isError: drillError } = useQuery<DrillDown>({
    queryKey: ["drill-down", drillCode, report?.fromDate, report?.toDate],
    enabled: !!drillCode && !!report,
    queryFn: async () => {
      const res = await apiClient.get(
        `/api/v1/accounting/reports/drill-down?accountCode=${drillCode}&from=${report!.fromDate}&to=${report!.toDate}`)
      return (res.data?.data ?? res.data) as DrillDown
    },
  })

  const handleRun = async () => {
    setIsLoading(true)
    setIsError(false)
    setRun(true)
    try {
      const res = await apiClient.get(`/api/v1/accounting/reports/${reportType}?from=${from}&to=${to}`)
      setReport((res.data?.data ?? res.data) as Report)
    } catch {
      setIsError(true)
      setReport(null)
    } finally {
      setIsLoading(false)
    }
  }

  const config = REPORT_CONFIG[reportType]
  const Icon   = config.icon

  return (
    <div>
      {/* Controls */}
      <div style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12,
        padding: 20, marginBottom: 20, display: "flex", gap: 16, alignItems: "flex-end", flexWrap: "wrap" }}>
        {/* Report type picker */}
        <div>
          <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 6, textTransform: "uppercase", letterSpacing: "0.05em" }}>Report</label>
          <div style={{ display: "flex", gap: 6 }}>
            {(Object.keys(REPORT_CONFIG) as ReportType[]).map(k => {
              const c = REPORT_CONFIG[k]
              const I = c.icon
              return (
                <button key={k} onClick={() => { setReportType(k); setRun(false) }}
                  style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 14px",
                    borderRadius: 9, border: "none", fontSize: 13, fontWeight: 600, cursor: "pointer",
                    background: reportType === k ? c.color : "#F1F5F9",
                    color:      reportType === k ? "white"  : "#64748B" }}>
                  <I size={13} />{c.label}
                </button>
              )
            })}
          </div>
        </div>
        <div>
          <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 6, textTransform: "uppercase", letterSpacing: "0.05em" }}>From</label>
          <input type="date" value={from} onChange={e => { setFrom(e.target.value); setRun(false) }} style={inp} />
        </div>
        <div>
          <label style={{ display: "block", fontSize: 11, fontWeight: 700, color: "#374151", marginBottom: 6, textTransform: "uppercase", letterSpacing: "0.05em" }}>To</label>
          <input type="date" value={to} onChange={e => { setTo(e.target.value); setRun(false) }} style={inp} />
        </div>
        <button onClick={handleRun}
          style={{ padding: "8px 20px", background: config.color, color: "white", border: "none",
            borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
          Run Report
        </button>
      </div>

      {isLoading && <div style={{ padding: 60, textAlign: "center", color: "#94A3B8" }}>Generating report...</div>}
      {isError   && <div style={{ padding: 60, textAlign: "center", color: "#DC2626" }}>Failed to load report — check your date range.</div>}

      {report && !isLoading && (
        <div style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {/* Report header */}
          <div style={{ background: config.color, padding: "20px 28px", display: "flex",
            justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Icon size={18} color="white" />
                <span style={{ fontSize: 18, fontWeight: 800, color: "white" }}>{config.label}</span>
              </div>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.7)", marginTop: 3 }}>
                {new Date(report.fromDate).toLocaleDateString("en-ZA")} to {new Date(report.toDate).toLocaleDateString("en-ZA")}
              </div>
            </div>
            <div style={{ textAlign: "right" }}>
              <div style={{ fontSize: 11, color: "rgba(255,255,255,0.7)", marginBottom: 2 }}>
                {reportType === "profit-and-loss" ? "Net Profit" : reportType === "trial-balance" ? "Net Difference" : "Liabilities + Equity"}
              </div>
              <div style={{ fontSize: 24, fontWeight: 800, color: report.netResult >= 0 ? "#4ADE80" : "#F87171" }}>
                {fmtR(report.netResult)}
              </div>
            </div>
          </div>

          {/* Sections */}
          {report.sections.map(section => (
            <div key={section.title}>
              <div style={{ padding: "14px 28px 8px", background: "#F8FAFC",
                borderBottom: "1px solid #F1F5F9", borderTop: "1px solid #F1F5F9" }}>
                <span style={{ fontSize: 12, fontWeight: 700, color: "#374151", textTransform: "uppercase", letterSpacing: "0.06em" }}>
                  {section.title}
                </span>
              </div>

              {/* Trial balance: show gross debit/credit columns */}
              {reportType === "trial-balance" ? (
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead>
                    <tr style={{ background: "#F8FAFC" }}>
                      <th style={{ textAlign: "left", padding: "8px 28px", fontSize: 11, fontWeight: 700, color: "#94A3B8" }}>Code</th>
                      <th style={{ textAlign: "left", padding: "8px 0", fontSize: 11, fontWeight: 700, color: "#94A3B8" }}>Account</th>
                      <th style={{ textAlign: "right", padding: "8px 28px 8px 0", fontSize: 11, fontWeight: 700, color: "#1B3A6B" }}>Debit</th>
                      <th style={{ textAlign: "right", padding: "8px 28px 8px 0", fontSize: 11, fontWeight: 700, color: "#0D9488" }}>Credit</th>
                    </tr>
                  </thead>
                  <tbody>
                    {section.lines.map((line, i) => (
                      <tr key={i} style={{ borderBottom: "1px solid #F8FAFC", cursor: "pointer" }}
                        onClick={() => setDrillCode(line.accountCode)}
                        onMouseEnter={e => (e.currentTarget.style.background = "#F8FAFC")}
                        onMouseLeave={e => (e.currentTarget.style.background = "white")}>
                        <td style={{ padding: "10px 28px", fontFamily: "monospace", fontSize: 12, color: "#64748B" }}>{line.accountCode}</td>
                        <td style={{ padding: "10px 0", fontSize: 13, color: "#374151" }}>{line.accountName}</td>
                        <td style={{ padding: "10px 28px 10px 0", textAlign: "right", fontSize: 13, fontWeight: 600,
                          color: (line.grossDebit ?? 0) > 0 ? "#1B3A6B" : "#94A3B8" }}>
                          {(line.grossDebit ?? 0) > 0 ? fmtR(line.grossDebit ?? 0) : "—"}
                        </td>
                        <td style={{ padding: "10px 28px 10px 0", textAlign: "right", fontSize: 13, fontWeight: 600,
                          color: (line.grossCredit ?? 0) > 0 ? "#0D9488" : "#94A3B8" }}>
                          {(line.grossCredit ?? 0) > 0 ? fmtR(line.grossCredit ?? 0) : "—"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr style={{ borderTop: "2px solid #E2E8F0", background: "#F8FAFC" }}>
                      <td colSpan={2} style={{ padding: "10px 28px", fontWeight: 700, color: "#0F172A" }}>Total</td>
                      <td style={{ padding: "10px 28px 10px 0", textAlign: "right", fontWeight: 700, color: "#1B3A6B" }}>
                        {fmtR(section.lines.reduce((s, l) => s + (l.grossDebit ?? 0), 0))}
                      </td>
                      <td style={{ padding: "10px 28px 10px 0", textAlign: "right", fontWeight: 700, color: "#0D9488" }}>
                        {fmtR(section.lines.reduce((s, l) => s + (l.grossCredit ?? 0), 0))}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              ) : (
                <>
                  {section.lines.map((line, i) => (
                    <div key={i}
                      style={{ display: "flex", justifyContent: "space-between", padding: "10px 28px",
                        borderBottom: "1px solid #F8FAFC", cursor: "pointer" }}
                      onClick={() => setDrillCode(line.accountCode)}
                      onMouseEnter={e => (e.currentTarget.style.background = "#F8FAFC")}
                      onMouseLeave={e => (e.currentTarget.style.background = "white")}>
                      <div style={{ display: "flex", gap: 14, alignItems: "center" }}>
                        <span style={{ fontFamily: "monospace", fontSize: 12, color: "#94A3B8", minWidth: 50 }}>{line.accountCode}</span>
                        <span style={{ fontSize: 13, color: "#374151" }}>{line.accountName}</span>
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                        <span style={{ fontSize: 13, fontWeight: 600, color: line.amount >= 0 ? "#0F172A" : "#DC2626" }}>
                          {fmtR(line.amount)}
                        </span>
                        <ChevronRight size={13} color="#CBD5E1" />
                      </div>
                    </div>
                  ))}
                  <div style={{ display: "flex", justifyContent: "space-between", padding: "12px 28px",
                    borderTop: "2px solid #E2E8F0", background: "#F8FAFC" }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>Total {section.title}</span>
                    <span style={{ fontSize: 15, fontWeight: 800, color: config.color }}>{fmtR(section.total)}</span>
                  </div>
                </>
              )}
            </div>
          ))}
        </div>
      )}

      {!run && !isLoading && (
        <div style={{ padding: 60, textAlign: "center", color: "#94A3B8", background: "white",
          border: "1px solid #E2E8F0", borderRadius: 12 }}>
          <BarChart2 size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <div style={{ fontWeight: 600, color: "#475569", marginBottom: 4 }}>Select a report and date range</div>
          <div style={{ fontSize: 13 }}>Click Run Report to generate your financial statement.</div>
        </div>
      )}

      {drillCode && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex",
          alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}
          onClick={() => setDrillCode(null)}>
          <div style={{ background: "white", borderRadius: 16, width: 640, maxHeight: "82vh",
            overflowY: "auto" as const, boxShadow: "0 20px 60px rgba(0,0,0,0.25)" }}
            onClick={e => e.stopPropagation()}>
            <div style={{ padding: "20px 24px", borderBottom: "1px solid #F1F5F9",
              display: "flex", justifyContent: "space-between", alignItems: "flex-start", position: "sticky" as const, top: 0, background: "white" }}>
              <div>
                <div style={{ fontSize: 11, color: "#94A3B8", fontFamily: "monospace", marginBottom: 2 }}>{drillCode}</div>
                <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>{drillDown?.accountName ?? "Loading..."}</h3>
                {report && (
                  <p style={{ margin: "3px 0 0", fontSize: 12, color: "#94A3B8" }}>
                    {new Date(report.fromDate).toLocaleDateString("en-ZA")} – {new Date(report.toDate).toLocaleDateString("en-ZA")}
                  </p>
                )}
              </div>
              <button onClick={() => setDrillCode(null)}
                style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={18} /></button>
            </div>

            {drillLoading && (
              <div style={{ padding: 50, textAlign: "center", color: "#94A3B8", fontSize: 13 }}>Loading journal lines...</div>
            )}
            {drillError && (
              <div style={{ padding: 50, textAlign: "center", color: "#DC2626", fontSize: 13 }}>Failed to load drill-down</div>
            )}

            {drillDown && !drillLoading && (
              <div style={{ padding: "8px 0" }}>
                {(drillDown.openingBalance ?? 0) !== 0 && (
                  <div style={{ display: "flex", justifyContent: "space-between", padding: "10px 24px",
                    background: "#F8FAFC", fontSize: 12, color: "#64748B" }}>
                    <span>Opening balance</span>
                    <span style={{ fontWeight: 600 }}>{fmtR(drillDown.openingBalance)}</span>
                  </div>
                )}

                {drillDown.lines.length === 0 ? (
                  <div style={{ padding: 40, textAlign: "center", color: "#94A3B8", fontSize: 13 }}>
                    No posted journal lines against this account in this date range —
                    the reported amount comes entirely from the opening balance above.
                  </div>
                ) : (
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead>
                      <tr style={{ background: "#F8FAFC" }}>
                        {["Journal #", "Date", "Description", "Debit", "Credit"].map(h => (
                          <th key={h} style={{ textAlign: h === "Debit" || h === "Credit" ? "right" : "left",
                            padding: "8px 24px", fontSize: 10, fontWeight: 700, color: "#94A3B8",
                            textTransform: "uppercase" as const, letterSpacing: "0.05em" }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {drillDown.lines.map((l, i) => (
                        <tr key={l.journalEntryId + i} style={{ borderBottom: "1px solid #F8FAFC" }}>
                          <td style={{ padding: "10px 24px", fontFamily: "monospace", fontSize: 12, fontWeight: 700, color: "#1B3A6B" }}>
                            {l.entryNumber}
                          </td>
                          <td style={{ padding: "10px 24px", fontSize: 12, color: "#64748B" }}>{fmtDt(l.entryDate)}</td>
                          <td style={{ padding: "10px 24px", fontSize: 12, color: "#374151" }}>
                            {l.lineDescription || l.entryDescription}
                          </td>
                          <td style={{ padding: "10px 24px", textAlign: "right", fontSize: 13, fontWeight: 600,
                            color: l.debitAmount > 0 ? "#1B3A6B" : "#CBD5E1" }}>
                            {l.debitAmount > 0 ? fmtR(l.debitAmount) : "—"}
                          </td>
                          <td style={{ padding: "10px 24px", textAlign: "right", fontSize: 13, fontWeight: 600,
                            color: l.creditAmount > 0 ? "#0D9488" : "#CBD5E1" }}>
                            {l.creditAmount > 0 ? fmtR(l.creditAmount) : "—"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr style={{ borderTop: "2px solid #E2E8F0", background: "#F8FAFC" }}>
                        <td colSpan={3} style={{ padding: "10px 24px", fontWeight: 700, fontSize: 12, color: "#0F172A" }}>Total</td>
                        <td style={{ padding: "10px 24px", textAlign: "right", fontWeight: 700, fontSize: 13, color: "#1B3A6B" }}>
                          {fmtR(drillDown.totalDebit)}
                        </td>
                        <td style={{ padding: "10px 24px", textAlign: "right", fontWeight: 700, fontSize: 13, color: "#0D9488" }}>
                          {fmtR(drillDown.totalCredit)}
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
