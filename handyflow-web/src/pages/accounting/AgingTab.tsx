// src/pages/accounting/AgingTab.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Users, AlertCircle } from "lucide-react"

const fmtR = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

const ageBucket = (dueDateStr: string | null): string => {
  if (!dueDateStr) return "Not due"
  const days = Math.floor((Date.now() - new Date(dueDateStr).getTime()) / 86400000)
  if (days <= 0)  return "Current"
  if (days <= 30) return "1-30 days"
  if (days <= 60) return "31-60 days"
  if (days <= 90) return "61-90 days"
  return "90+ days"
}

const BUCKET_ORDER = ["Current", "1-30 days", "31-60 days", "61-90 days", "90+ days"]
const BUCKET_COLOR: Record<string, string> = {
  "Current":    "#166534",
  "1-30 days":  "#1D4ED8",
  "31-60 days": "#D97706",
  "61-90 days": "#EA580C",
  "90+ days":   "#DC2626",
}

export default function AgingTab() {
  const { data: invoices = [], isLoading } = useQuery({
    queryKey: ["invoices-aging"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/invoicing/invoices?size=500")
      const payload = res.data?.data ?? res.data
      return (payload.content ?? payload) as any[]
    },
  })

  const { data: customers = [] } = useQuery({
    queryKey: ["customers-map"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/crm/customers?size=500")
      const payload = res.data?.data ?? res.data
      return (payload.content ?? payload) as { id: string; name: string }[]
    },
  })
  const customerMap = Object.fromEntries(customers.map((c: any) => [c.id, c.name]))

  // Only show outstanding invoices
  const outstanding = invoices.filter(i => ["ISSUED", "PARTIALLY_PAID", "OVERDUE"].includes(i.status))

  // Group by customer then by bucket
  const byCustomer = outstanding.reduce((acc: any, inv: any) => {
    const name = customerMap[inv.customerId] ?? inv.walkinClientName ?? "Walk-in"
    if (!acc[name]) acc[name] = { name, buckets: {}, total: 0 }
    const bucket = ageBucket(inv.dueDate)
    acc[name].buckets[bucket] = (acc[name].buckets[bucket] ?? 0) + inv.total
    acc[name].total += inv.total
    return acc
  }, {})

  const rows = Object.values(byCustomer) as any[]
  rows.sort((a, b) => b.total - a.total)

  // Bucket totals
  const bucketTotals = BUCKET_ORDER.reduce((acc, b) => {
    acc[b] = rows.reduce((s, r) => s + (r.buckets[b] ?? 0), 0)
    return acc
  }, {} as Record<string, number>)

  const grandTotal = rows.reduce((s, r) => s + r.total, 0)

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading aging data...</div>

  return (
    <div>
      <div style={{ marginBottom: 20 }}>
        <h3 style={{ margin: "0 0 4px", fontSize: 16, fontWeight: 700, color: "#0F172A" }}>Accounts Receivable Aging</h3>
        <p style={{ margin: 0, fontSize: 13, color: "#94A3B8" }}>Outstanding invoices by customer and age</p>
      </div>

      {/* Bucket summary cards */}
      <div style={{ display: "flex", gap: 10, marginBottom: 24, overflowX: "auto" }}>
        {BUCKET_ORDER.map(b => (
          <div key={b} style={{ minWidth: 140, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px", flexShrink: 0 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: BUCKET_COLOR[b], marginBottom: 4 }}>{b.toUpperCase()}</div>
            <div style={{ fontSize: 18, fontWeight: 700, color: bucketTotals[b] > 0 ? BUCKET_COLOR[b] : "#94A3B8" }}>
              {fmtR(bucketTotals[b])}
            </div>
          </div>
        ))}
        <div style={{ minWidth: 140, background: "#1B3A6B", border: "1px solid #1B3A6B", borderRadius: 10, padding: "12px 16px", flexShrink: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: "rgba(255,255,255,0.7)", marginBottom: 4 }}>TOTAL OUTSTANDING</div>
          <div style={{ fontSize: 18, fontWeight: 700, color: "#fff" }}>{fmtR(grandTotal)}</div>
        </div>
      </div>

      {rows.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#166534" }}>All invoices are paid — great work!</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                <th style={th}>Customer</th>
                {BUCKET_ORDER.map(b => (
                  <th key={b} style={{ ...th, textAlign: "right", color: BUCKET_COLOR[b] }}>{b}</th>
                ))}
                <th style={{ ...th, textAlign: "right" }}>Total</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row: any, i: number) => (
                <tr key={row.name} style={{ borderBottom: "1px solid #F1F5F9", background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                  <td style={{ ...td, fontWeight: 600, color: "#0F172A" }}>{row.name}</td>
                  {BUCKET_ORDER.map(b => (
                    <td key={b} style={{ ...td, textAlign: "right", color: row.buckets[b] ? BUCKET_COLOR[b] : "#CBD5E1", fontWeight: row.buckets[b] ? 600 : 400 }}>
                      {row.buckets[b] ? fmtR(row.buckets[b]) : "—"}
                    </td>
                  ))}
                  <td style={{ ...td, textAlign: "right", fontWeight: 700, color: "#0F172A" }}>{fmtR(row.total)}</td>
                </tr>
              ))}
              {/* Totals row */}
              <tr style={{ borderTop: "2px solid #E2E8F0", background: "#F8FAFC" }}>
                <td style={{ ...td, fontWeight: 700 }}>TOTAL</td>
                {BUCKET_ORDER.map(b => (
                  <td key={b} style={{ ...td, textAlign: "right", fontWeight: 700, color: BUCKET_COLOR[b] }}>
                    {bucketTotals[b] ? fmtR(bucketTotals[b]) : "—"}
                  </td>
                ))}
                <td style={{ ...td, textAlign: "right", fontWeight: 800, color: "#0F172A", fontSize: 15 }}>{fmtR(grandTotal)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {rows.some((r: any) => r.buckets["90+ days"]) && (
        <div style={{ marginTop: 14, padding: "12px 16px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, display: "flex", gap: 8, fontSize: 13, color: "#DC2626" }}>
          <AlertCircle size={15} style={{ flexShrink: 0, marginTop: 1 }} />
          <span>You have invoices overdue by more than 90 days. Consider escalating collection or writing these off as bad debt (account 5230).</span>
        </div>
      )}
    </div>
  )
}

const th: React.CSSProperties = { padding: "10px 16px", textAlign: "left", fontSize: 11, fontWeight: 600, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.05em" }
const td: React.CSSProperties = { padding: "11px 16px", fontSize: 13, borderBottom: "1px solid #F1F5F9" }