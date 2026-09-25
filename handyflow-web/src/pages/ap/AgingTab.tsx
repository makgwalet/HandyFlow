// src/pages/ap/AgingTab.tsx
import { useQuery } from "@tanstack/react-query"
import { Users, AlertCircle } from "lucide-react"
import { apiClient } from "../../api/client"

interface AgingLine { billId: string; billNumber: string; supplierName: string
  dueDate: string; daysOverdue: number; balance: number; bucket: string }
interface AgingReport { asAt: string; lines: AgingLine[]
  current: number; days1to30: number; days31to60: number; days61to90: number; over90: number; total: number }

const fmtR  = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtDt = (d: string) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"

const BUCKET_STYLE: Record<string, { bg: string; color: string; label: string }> = {
  "CURRENT": { bg: "var(--hf-success-soft)", color: "var(--hf-success-text-strong)", label: "Current" },
  "1-30":    { bg: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text-deep)", label: "1–30 days" },
  "31-60":   { bg: "var(--hf-orange-soft)", color: "var(--hf-orange-text-strong)", label: "31–60 days" },
  "61-90":   { bg: "var(--hf-danger-soft-strong)", color: "var(--hf-danger-text-strong)", label: "61–90 days" },
  "90+":     { bg: "var(--hf-danger-soft-strong)", color: "var(--hf-danger-text-strong)", label: "90+ days" },
}

export default function AgingTab() {
  const { data: report, isLoading, isError, refetch } = useQuery<AgingReport>({
    queryKey: ["ap-aging"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/ap/aging")
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

  if (isLoading) return <div style={{ padding: 60, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading AP aging report...</div>
  if (isError)   return <div style={{ padding: 60, textAlign: "center", color: "var(--hf-danger-text)" }}>Failed to load aging report</div>

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>AP Aging Report</h2>
          <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "3px 0 0" }}>
            Outstanding bills bucketed by days overdue · as at {report ? fmtDt(report.asAt) : "today"}
          </p>
        </div>
        <button onClick={() => refetch()}
          style={{ padding: "8px 16px", background: "var(--hf-surface-sunken)", color: "var(--hf-text-secondary)", border: "none",
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
                {report?.lines.filter(l => l.bucket === b.key).length ?? 0} bill{report?.lines.filter(l => l.bucket === b.key).length !== 1 ? "s" : ""}
              </div>
            </div>
          )
        })}
      </div>

      {/* Total */}
      <div style={{ background: "var(--hf-inverse-surface)", borderRadius: 10, padding: "14px 20px", marginBottom: 20,
        display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: "rgba(255,255,255,0.7)" }}>Total Outstanding AP</span>
        <span style={{ fontSize: 22, fontWeight: 800, color: report && report.total > 0 ? "#F87171" : "#4ADE80" }}>
          {fmtR(report?.total ?? 0)}
        </span>
      </div>

      {/* Lines table */}
      {!report || report.lines.length === 0 ? (
        <div style={{ padding: 60, textAlign: "center", background: "white", border: "1px solid var(--hf-border)",
          borderRadius: 12, color: "var(--hf-text-faint)" }}>
          <Users size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)", marginBottom: 4 }}>All clear — no outstanding bills</div>
          <div style={{ fontSize: 13 }}>All bills are either paid, draft, or not yet approved.</div>
        </div>
      ) : (
        <div style={{ background: "white", border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "var(--hf-surface-muted)", borderBottom: "1px solid var(--hf-border-subtle)" }}>
                {["Bill", "Supplier", "Due Date", "Days Overdue", "Balance", "Age Bucket"].map(h => (
                  <th key={h} style={{ textAlign: "left", padding: "10px 16px", fontSize: 11,
                    fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {report.lines.map((line, i) => {
                const s = BUCKET_STYLE[line.bucket] ?? BUCKET_STYLE["CURRENT"]
                return (
                  <tr key={line.billId} style={{ borderBottom: i < report.lines.length - 1 ? "1px solid var(--hf-border-subtle)" : "none" }}
                    onMouseEnter={e => (e.currentTarget.style.background = "var(--hf-surface-muted)")}
                    onMouseLeave={e => (e.currentTarget.style.background = "white")}>
                    <td style={{ padding: "12px 16px" }}>
                      <span style={{ fontFamily: "monospace", fontSize: 13, fontWeight: 700, color: "var(--hf-primary-text)" }}>
                        {line.billNumber}
                      </span>
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)" }}>
                      {line.supplierName}
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 13, color: line.daysOverdue > 0 ? "var(--hf-danger-text)" : "var(--hf-text-muted)" }}>
                      {fmtDt(line.dueDate)}
                    </td>
                    <td style={{ padding: "12px 16px" }}>
                      {line.daysOverdue > 0 ? (
                        <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}>
                          <AlertCircle size={12} color="#DC2626" />
                          <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-danger-text)" }}>{line.daysOverdue} days</span>
                        </span>
                      ) : (
                        <span style={{ fontSize: 13, color: "var(--hf-success-text-strong)", fontWeight: 600 }}>Current</span>
                      )}
                    </td>
                    <td style={{ padding: "12px 16px", fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>
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
