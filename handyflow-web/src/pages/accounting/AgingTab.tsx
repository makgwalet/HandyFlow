import { useQuery } from "@tanstack/react-query"
import { Users, AlertCircle } from "lucide-react"
import { apiClient } from "../../api/client"

interface AgingLine { invoiceId: string; invoiceNumber: string; customerName: string
  dueDate: string | null; daysOverdue: number; balance: number; bucket: string }
interface AgingReport { asAt: string; lines: AgingLine[]
  current: number; days1to30: number; days31to60: number; days61to90: number; over90: number; total: number }

const fmtR  = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtDt = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"

const BUCKET_STYLE: Record<string, { bg: string; color: string; label: string }> = {
  "CURRENT": { bg: "#F0FDF4", color: "#166534", label: "Current" },
  "1-30":    { bg: "#FEF3C7", color: "#92400E", label: "1–30 days" },
  "31-60":   { bg: "#FED7AA", color: "#C2410C", label: "31–60 days" },
  "61-90":   { bg: "#FECACA", color: "#B91C1C", label: "61–90 days" },
  "90+":     { bg: "#FEE2E2", color: "#7F1D1D", label: "90+ days" },
}

export default function AgingTab() {
  const { data: report, isLoading, isError, refetch } = useQuery<AgingReport>({
    queryKey: ["ar-aging"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/accounting/reports/ar-aging")
      return (res.data?.data ?? res.data) as AgingReport
    },
  })

  const buckets = [
    { key: "CURRENT", value: report?.current    },
    { key: "1-30",    value: report?.days1to30  },
    { key: "31-60",   value: report?.days31to60 },
    { key: "61-90",   value: report?.days61to90 },
    { key: "90+",     value: report?.over90     },
  ]

  if (isLoading) return <div style={{ padding: 60, textAlign: "center", color: "#94A3B8" }}>Loading AR aging report...</div>
  if (isError)   return <div style={{ padding: 60, textAlign: "center", color: "#DC2626" }}>Failed to load aging report</div>

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>AR Aging Report</h2>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: "3px 0 0" }}>
            Outstanding invoices bucketed by days overdue · as at {report ? fmtDt(report.asAt) : "today"}
          </p>
        </div>
        <button onClick={() => refetch()}
          style={{ padding: "8px 16px", background: "#F1F5F9", color: "#374151", border: "none",
            borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          Refresh
        </button>
      </div>

      {/* Bucket summary */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 12, marginBottom: 24 }}>
        {buckets.map(b => {
          const s = BUCKET_STYLE[b.key]
          return (
            <div key={b.key} style={{ background: s.bg, borderRadius: 10, padding: "14px 16px" }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: s.color, textTransform: "uppercase",
                letterSpacing: "0.05em", marginBottom: 6 }}>{s.label}</div>
              <div style={{ fontSize: 20, fontWeight: 800, color: s.color }}>{fmtR(b.value ?? 0)}</div>
              <div style={{ fontSize: 11, color: s.color, opacity: 0.7, marginTop: 3 }}>
                {report?.lines.filter(l => l.bucket === b.key).length ?? 0} invoice{report?.lines.filter(l => l.bucket === b.key).length !== 1 ? "s" : ""}
              </div>
            </div>
          )
        })}
      </div>

      {/* Total */}
      <div style={{ background: "#0F172A", borderRadius: 10, padding: "14px 20px", marginBottom: 20,
        display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: "rgba(255,255,255,0.7)" }}>Total Outstanding AR</span>
        <span style={{ fontSize: 22, fontWeight: 800, color: report && report.total > 0 ? "#F87171" : "#4ADE80" }}>
          {fmtR(report?.total ?? 0)}
        </span>
      </div>

      {/* Lines table */}
      {!report || report.lines.length === 0 ? (
        <div style={{ padding: 60, textAlign: "center", background: "white", border: "1px solid #E2E8F0",
          borderRadius: 12, color: "#94A3B8" }}>
          <Users size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <div style={{ fontWeight: 600, color: "#475569", marginBottom: 4 }}>All clear — no outstanding invoices</div>
          <div style={{ fontSize: 13 }}>All invoices are either paid, cancelled, or not yet issued.</div>
        </div>
      ) : (
        <div style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #F1F5F9" }}>
                {["Invoice", "Customer", "Due Date", "Days Overdue", "Balance", "Age Bucket"].map(h => (
                  <th key={h} style={{ textAlign: "left", padding: "10px 16px", fontSize: 11,
                    fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {report.lines.map((line, i) => {
                const s = BUCKET_STYLE[line.bucket] ?? BUCKET_STYLE["CURRENT"]
                return (
                  <tr key={line.invoiceId} style={{ borderBottom: i < report.lines.length - 1 ? "1px solid #F8FAFC" : "none" }}
                    onMouseEnter={e => (e.currentTarget.style.background = "#F8FAFC")}
                    onMouseLeave={e => (e.currentTarget.style.background = "white")}>
                    <td style={{ padding: "12px 16px" }}>
                      <span style={{ fontFamily: "monospace", fontSize: 13, fontWeight: 700, color: "#1B3A6B" }}>
                        {line.invoiceNumber}
                      </span>
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 13, fontWeight: 600, color: "#374151" }}>
                      {line.customerName ?? "Walk-in client"}
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 13, color: line.daysOverdue > 0 ? "#DC2626" : "#64748B" }}>
                      {fmtDt(line.dueDate)}
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      {line.daysOverdue > 0 ? (
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                          <AlertCircle size={12} color="#DC2626" />
                          <span style={{ fontSize: 13, fontWeight: 700, color: "#DC2626" }}>{line.daysOverdue} days</span>
                        </span>
                      ) : (
                        <span style={{ fontSize: 13, color: "#166534", fontWeight: 600 }}>Current</span>
                      )}
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 14, fontWeight: 700, color: "#0F172A" }}>
                      {fmtR(line.balance)}
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      <span style={{ background: s.bg, color: s.color, fontSize: 11,
                        fontWeight: 700, padding: "3px 10px", borderRadius: 10 }}>{s.label}</span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
