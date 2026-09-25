// src/pages/security/ReportsTab.tsx
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FileBarChart, Download, TrendingUp, Users, Building2, AlertTriangle } from "lucide-react"

interface Site  { id: string; name: string }
interface Guard { id: string; fullName: string }

interface SiteCoverageReport {
  siteName: string; month: string
  totalShifts: number; completedShifts: number; missedShifts: number
  totalGuardHours: number; shiftCompletionRatePct: number
  patrolRoundsExpected: number; patrolRoundsCompleted: number
  checkpointScans: number; totalIncidents: number
  incidentsBySeverity: Record<string, number>
}

interface GuardAttendanceReport {
  guardName: string; month: string
  totalShifts: number; completedShifts: number; missedShifts: number
  totalHoursWorked: number; attendanceRatePct: number
  checkpointScans: number; incidentsLogged: number
  siteBreakdown: { siteName: string; totalShifts: number; completedShifts: number; hoursWorked: number }[]
}

interface MonthlySummaryReport {
  month: string; totalShifts: number; completedShifts: number; missedShifts: number
  totalGuardHours: number; overallCompletionRatePct: number
  totalIncidents: number; incidentsBySeverity: Record<string, number>
  activeGuards: number
  siteSummaries: { siteName: string; totalShifts: number; completedShifts: number; guardHours: number; coverageRatePct: number; incidents: number }[]
}

type ReportType = "site-coverage" | "guard-attendance" | "monthly-summary"

const SEV_COLORS: Record<string, string> = {
  LOW: "var(--hf-sky)", MEDIUM: "var(--hf-warning)", HIGH: "var(--hf-orange)", CRITICAL: "var(--hf-danger)",
}

function StatBox({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div style={{ background: "var(--hf-surface-muted)", borderRadius: 10, padding: "14px 16px", border: "1px solid var(--hf-border)" }}>
      <p style={{ margin: "0 0 4px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 600, textTransform: "uppercase" as const, letterSpacing: "0.04em" }}>{label}</p>
      <p style={{ margin: 0, fontSize: 22, fontWeight: 800, color: "var(--hf-text)" }}>{value}</p>
      {sub && <p style={{ margin: "2px 0 0", fontSize: 11, color: "var(--hf-text-faint)" }}>{sub}</p>}
    </div>
  )
}

function thisMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`
}

export default function ReportsTab() {
  const [reportType,   setReportType]   = useState<ReportType>("monthly-summary")
  const [month,        setMonth]        = useState(thisMonth())
  const [siteId,       setSiteId]       = useState("")
  const [guardId,      setGuardId]      = useState("")
  const [triggered,    setTriggered]    = useState(false)

  const { data: sites = [] } = useQuery<Site[]>({
    queryKey: ["sites-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/sites?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Site[]
    },
  })

  const { data: guards = [] } = useQuery<Guard[]>({
    queryKey: ["guards-list"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/guards?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Guard[]
    },
  })

  const apiPath = () => {
    if (reportType === "site-coverage")     return `/api/v1/security/reports/site-coverage?siteId=${siteId}&month=${month}`
    if (reportType === "guard-attendance")  return `/api/v1/security/reports/guard-attendance?guardId=${guardId}&month=${month}`
    return `/api/v1/security/reports/monthly-summary?month=${month}`
  }

  const pdfPath = () => apiPath().replace("/reports/", "/reports/") + "" // same path + /pdf
    .replace("reports/site-coverage?", "reports/site-coverage/pdf?")
    .replace("reports/guard-attendance?", "reports/guard-attendance/pdf?")
    .replace("reports/monthly-summary?", "reports/monthly-summary/pdf?")

  const canFetch = reportType === "monthly-summary"
    ? !!month
    : reportType === "site-coverage"
    ? !!siteId && !!month
    : !!guardId && !!month

  const { data: report, isLoading, error, refetch } = useQuery({
    queryKey: ["report", reportType, month, siteId, guardId],
    queryFn: async () => {
      const r = await apiClient.get(apiPath())
      return r.data?.data ?? r.data
    },
    enabled: false,
  })

  async function downloadPdf() {
    const r = await apiClient.get(pdfPath(), { responseType: "blob" })
    const url = URL.createObjectURL(new Blob([r.data], { type: "application/pdf" }))
    const a = document.createElement("a")
    a.href = url
    a.download = `handyflow-report-${reportType}-${month}.pdf`
    a.click()
    URL.revokeObjectURL(url)
  }

  function run() { setTriggered(true); refetch() }

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h2 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Reports</h2>
        <p style={{ margin: 0, fontSize: 12, color: "var(--hf-text-muted)" }}>Monthly security performance reports — JSON view or PDF download</p>
      </div>

      {/* Controls */}
      <div style={{ display: "flex", gap: 12, alignItems: "flex-end", flexWrap: "wrap" as const, padding: "16px 20px", background: "var(--hf-surface-muted)", borderRadius: 12, border: "1px solid var(--hf-border)", marginBottom: 24 }}>
        <div>
          <label style={lblStyle}>Report type</label>
          <select value={reportType} onChange={e => { setReportType(e.target.value as ReportType); setTriggered(false) }} style={selStyle}>
            <option value="monthly-summary">Monthly Summary (all sites)</option>
            <option value="site-coverage">Site Coverage</option>
            <option value="guard-attendance">Guard Attendance</option>
          </select>
        </div>
        <div>
          <label style={lblStyle}>Month</label>
          <input type="month" value={month} onChange={e => setMonth(e.target.value)} style={selStyle} />
        </div>
        {reportType === "site-coverage" && (
          <div>
            <label style={lblStyle}>Site</label>
            <select value={siteId} onChange={e => setSiteId(e.target.value)} style={selStyle}>
              <option value="">Select site…</option>
              {sites.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
        )}
        {reportType === "guard-attendance" && (
          <div>
            <label style={lblStyle}>Guard</label>
            <select value={guardId} onChange={e => setGuardId(e.target.value)} style={selStyle}>
              <option value="">Select guard…</option>
              {guards.map((g: any) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
            </select>
          </div>
        )}
        <button onClick={run} disabled={!canFetch}
          style={{ padding: "9px 20px", borderRadius: 8, border: "none", background: canFetch ? "var(--hf-accent)" : "var(--hf-surface-strong)", color: canFetch ? "var(--hf-text-on-solid)" : "var(--hf-text-faint)", fontSize: 13, fontWeight: 600, cursor: canFetch ? "pointer" : "not-allowed", alignSelf: "flex-end" }}>
          Generate Report
        </button>
        {report && (
          <button onClick={downloadPdf}
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", borderRadius: 8, border: "1px solid var(--hf-info)", background: "var(--hf-info-soft)", color: "var(--hf-info-text)", fontSize: 13, fontWeight: 600, cursor: "pointer", alignSelf: "flex-end" }}>
            <Download size={14} /> Download PDF
          </button>
        )}
      </div>

      {/* Results */}
      {isLoading && <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Generating report…</p>}
      {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>Failed to generate report. Check that the selected site/guard has data for this month.</p>}

      {report && reportType === "monthly-summary" && (() => {
        const r = report as MonthlySummaryReport
        return (
          <div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 12, marginBottom: 24 }}>
              <StatBox label="Total Shifts"    value={r.totalShifts} />
              <StatBox label="Completed"       value={r.completedShifts} sub={`${r.overallCompletionRatePct}% rate`} />
              <StatBox label="Missed"          value={r.missedShifts} />
              <StatBox label="Guard Hours"     value={`${r.totalGuardHours}h`} />
              <StatBox label="Active Guards"   value={r.activeGuards} />
              <StatBox label="Total Incidents" value={r.totalIncidents} />
            </div>
            {r.siteSummaries?.length > 0 && (
              <div>
                <p style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "var(--hf-text-secondary)", marginBottom: 10 }}>Site Breakdown</p>
                <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, overflow: "hidden" }}>
                  <table style={{ width: "100%", borderCollapse: "collapse" as const, fontSize: 12 }}>
                    <thead>
                      <tr style={{ background: "var(--hf-surface-muted)", borderBottom: "1px solid var(--hf-border)" }}>
                        {["Site", "Shifts", "Done", "Missed", "Hours", "Coverage", "Incidents"].map(h => (
                          <th key={h} style={{ padding: "10px 14px", textAlign: "left" as const, fontWeight: 600, color: "var(--hf-text-secondary)", fontSize: 11 }}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {r.siteSummaries.map((s, i) => (
                        <tr key={i} style={{ borderBottom: "1px solid var(--hf-border-subtle)", background: i % 2 === 0 ? "var(--hf-surface)" : "var(--hf-surface-muted)" }}>
                          <td style={{ padding: "10px 14px", fontWeight: 600, color: "var(--hf-text)" }}>{s.siteName}</td>
                          <td style={{ padding: "10px 14px", color: "var(--hf-text-secondary)" }}>{s.totalShifts}</td>
                          <td style={{ padding: "10px 14px", color: "var(--hf-success-text-strong)" }}>{s.completedShifts}</td>
                          <td style={{ padding: "10px 14px", color: s.missedShifts > 0 ? "var(--hf-danger-text)" : "var(--hf-text-secondary)" }}>{s.missedShifts}</td>
                          <td style={{ padding: "10px 14px", color: "var(--hf-text-secondary)" }}>{s.guardHours}h</td>
                          <td style={{ padding: "10px 14px" }}>
                            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                              <div style={{ flex: 1, height: 6, background: "var(--hf-surface-strong)", borderRadius: 3 }}>
                                <div style={{ width: `${Math.min(s.coverageRatePct, 100)}%`, height: "100%", background: s.coverageRatePct >= 90 ? "var(--hf-accent)" : s.coverageRatePct >= 70 ? "var(--hf-warning)" : "var(--hf-danger)", borderRadius: 3 }} />
                              </div>
                              <span style={{ fontSize: 11, color: "var(--hf-text-secondary)", whiteSpace: "nowrap" as const }}>{s.coverageRatePct}%</span>
                            </div>
                          </td>
                          <td style={{ padding: "10px 14px", color: s.incidents > 0 ? "var(--hf-danger-text)" : "var(--hf-text-faint)" }}>{s.incidents}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )
      })()}

      {report && reportType === "site-coverage" && (() => {
        const r = report as SiteCoverageReport
        return (
          <div>
            <p style={{ fontWeight: 700, fontSize: 15, color: "var(--hf-text)", marginBottom: 16 }}>{r.siteName} — {r.month}</p>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 12, marginBottom: 24 }}>
              <StatBox label="Total Shifts"   value={r.totalShifts} />
              <StatBox label="Completed"      value={r.completedShifts} sub={`${r.shiftCompletionRatePct}%`} />
              <StatBox label="Missed"         value={r.missedShifts} />
              <StatBox label="Guard Hours"    value={`${r.totalGuardHours}h`} />
              <StatBox label="Patrol Rounds"  value={`${r.patrolRoundsCompleted}/${r.patrolRoundsExpected}`} />
              <StatBox label="Scans"          value={r.checkpointScans} />
              <StatBox label="Incidents"      value={r.totalIncidents} />
            </div>
            {Object.keys(r.incidentsBySeverity ?? {}).length > 0 && (
              <div style={{ display: "flex", gap: 10, flexWrap: "wrap" as const }}>
                {Object.entries(r.incidentsBySeverity).map(([sev, count]) => (
                  <div key={sev} style={{ padding: "8px 14px", borderRadius: 8, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", display: "flex", gap: 8, alignItems: "center" }}>
                    <span style={{ width: 8, height: 8, borderRadius: "50%", background: SEV_COLORS[sev] ?? "var(--hf-text-muted)", display: "inline-block" }} />
                    <span style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)" }}>{count} {sev}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      })()}

      {report && reportType === "guard-attendance" && (() => {
        const r = report as GuardAttendanceReport
        return (
          <div>
            <p style={{ fontWeight: 700, fontSize: 15, color: "var(--hf-text)", marginBottom: 16 }}>{r.guardName} — {r.month}</p>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 12, marginBottom: 24 }}>
              <StatBox label="Total Shifts"    value={r.totalShifts} />
              <StatBox label="Attended"        value={r.completedShifts} sub={`${r.attendanceRatePct}%`} />
              <StatBox label="Missed"          value={r.missedShifts} />
              <StatBox label="Hours Worked"    value={`${r.totalHoursWorked}h`} />
              <StatBox label="Checkpoint Scans" value={r.checkpointScans} />
              <StatBox label="Incidents Logged" value={r.incidentsLogged} />
            </div>
            {r.siteBreakdown?.length > 0 && (
              <div>
                <p style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "var(--hf-text-secondary)", marginBottom: 10 }}>By Site</p>
                {r.siteBreakdown.map((s, i) => (
                  <div key={i} style={{ display: "flex", gap: 12, alignItems: "center", padding: "10px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
                    <Building2 size={14} style={{ color: 'var(--hf-text-faint)' }} />
                    <div style={{ flex: 1, fontSize: 12, color: "var(--hf-text-secondary)" }}>
                      <strong>{s.siteName}</strong> · {s.completedShifts}/{s.totalShifts} shifts · {s.hoursWorked}h
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      })()}

      {!triggered && (
        <div style={{ textAlign: "center", padding: "48px 0", color: "var(--hf-text-disabled)" }}>
          <FileBarChart size={32} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
          <p style={{ margin: 0, fontWeight: 500 }}>Configure the report above and click Generate</p>
        </div>
      )}
    </div>
  )
}

const lblStyle = { display: "block", fontSize: 11, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 } as const
const selStyle = { padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12, background: "var(--hf-surface)", minWidth: 160 } as const
