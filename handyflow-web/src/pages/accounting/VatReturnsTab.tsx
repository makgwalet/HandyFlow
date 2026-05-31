// src/pages/accounting/VatReturnsTab.tsx
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FileText, AlertCircle, CheckCircle } from "lucide-react"

const fmtR = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

export default function VatReturnsTab() {
  const now = new Date()
  const [from, setFrom] = useState(`${now.getFullYear()}-01-01`)
  const [to, setTo]     = useState(now.toISOString().split("T")[0])
  const [run, setRun]   = useState(false)

  // Fetch paid invoices for output VAT
  const { data: invoices = [], isLoading } = useQuery({
    queryKey: ["invoices-vat", from, to],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/invoicing/invoices?size=500")
      const payload = res.data?.data ?? res.data
      return (payload.content ?? payload) as any[]
    },
    enabled: run,
  })

  // Calculate VAT201 fields
  const periodInvoices = invoices.filter(inv => {
    const issued = inv.issuedAt ?? inv.createdAt
    return issued >= from && issued <= to
  })

  const outputVat  = periodInvoices.filter(i => ["ISSUED", "PARTIALLY_PAID", "PAID", "OVERDUE"].includes(i.status))
                                   .reduce((s: number, i: any) => s + (i.vatTotal ?? 0), 0)
  const salesTotal = periodInvoices.reduce((s: number, i: any) => s + (i.subtotal ?? 0), 0)
  const vatPayable = outputVat // In future: subtract input VAT from journal entries

  const fields = [
    { code: "Field 1",  label: "Total Sales (excl. VAT)",    value: fmtR(salesTotal),  note: "All taxable supplies" },
    { code: "Field 4",  label: "Output VAT (15% on sales)",  value: fmtR(outputVat),   note: "VAT charged on invoices" },
    { code: "Field 14", label: "Input VAT (claimable)",      value: "R 0.00",           note: "Add via journal entries — coming soon" },
    { code: "Field 20", label: "Net VAT Payable to SARS",    value: fmtR(vatPayable),  note: "Fields 4 minus Field 14", highlight: true },
  ]

  return (
    <div>
      <div style={{ display: "flex", gap: 12, alignItems: "flex-end", marginBottom: 24, flexWrap: "wrap" }}>
        <div>
          <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#64748B", marginBottom: 6 }}>PERIOD FROM</label>
          <input type="date" value={from} onChange={e => { setFrom(e.target.value); setRun(false) }}
            style={{ padding: "8px 12px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13 }} />
        </div>
        <div>
          <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#64748B", marginBottom: 6 }}>PERIOD TO</label>
          <input type="date" value={to} onChange={e => { setTo(e.target.value); setRun(false) }}
            style={{ padding: "8px 12px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13 }} />
        </div>
        <button onClick={() => setRun(true)}
          style={{ padding: "9px 20px", background: "#7C3AED", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          Calculate VAT201
        </button>
      </div>

      {!run ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a VAT period</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Typically bi-monthly for most SA businesses.</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Calculating VAT...</div>
      ) : (
        <div>
          {/* VAT201 form layout */}
          <div style={{ background: "#F5F3FF", border: "1px solid #DDD6FE", borderRadius: 12, padding: 20, marginBottom: 20 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 16 }}>
              <FileText size={16} color="#7C3AED" />
              <span style={{ fontSize: 14, fontWeight: 700, color: "#6D28D9" }}>VAT201 Summary — {from} to {to}</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {fields.map(f => (
                <div key={f.code} style={{
                  display: "flex", justifyContent: "space-between", alignItems: "center",
                  padding: "12px 16px", borderRadius: 8,
                  background: f.highlight ? "#7C3AED" : "#fff",
                  border: `1px solid ${f.highlight ? "#7C3AED" : "#E2E8F0"}`,
                }}>
                  <div>
                    <div style={{ fontSize: 11, fontWeight: 700, color: f.highlight ? "rgba(255,255,255,0.7)" : "#94A3B8", letterSpacing: "0.05em" }}>{f.code}</div>
                    <div style={{ fontSize: 14, fontWeight: 600, color: f.highlight ? "#fff" : "#0F172A" }}>{f.label}</div>
                    <div style={{ fontSize: 11, color: f.highlight ? "rgba(255,255,255,0.6)" : "#94A3B8", marginTop: 2 }}>{f.note}</div>
                  </div>
                  <div style={{ fontSize: 20, fontWeight: 800, color: f.highlight ? "#fff" : "#0F172A" }}>{f.value}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Invoice breakdown */}
          <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
            <div style={{ padding: "12px 18px", background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>
                Invoices in period ({periodInvoices.length})
              </span>
            </div>
            {periodInvoices.length === 0 ? (
              <div style={{ padding: "24px", textAlign: "center", color: "#94A3B8", fontSize: 13 }}>No invoices in this period.</div>
            ) : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#F8FAFC" }}>
                    {["Invoice #", "Status", "Subtotal", "VAT", "Total"].map(h => (
                      <th key={h} style={{ padding: "9px 16px", textAlign: "left", fontSize: 11, fontWeight: 600, color: "#64748B", textTransform: "uppercase" as const }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {periodInvoices.map((inv: any, i: number) => (
                    <tr key={inv.id} style={{ borderBottom: "1px solid #F1F5F9", background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                      <td style={{ padding: "10px 16px", fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{inv.invoiceNumber}</td>
                      <td style={{ padding: "10px 16px" }}>
                        <span style={{ fontSize: 11, fontWeight: 600, padding: "2px 8px", borderRadius: 20, background: inv.status === "PAID" ? "#DCFCE7" : "#FEF3C7", color: inv.status === "PAID" ? "#166534" : "#92400E" }}>
                          {inv.status}
                        </span>
                      </td>
                      <td style={{ padding: "10px 16px", fontSize: 13, color: "#374151" }}>{fmtR(inv.subtotal)}</td>
                      <td style={{ padding: "10px 16px", fontSize: 13, color: "#7C3AED" }}>{fmtR(inv.vatTotal)}</td>
                      <td style={{ padding: "10px 16px", fontSize: 14, fontWeight: 700, color: "#0F172A" }}>{fmtR(inv.total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          <div style={{ marginTop: 16, padding: "12px 16px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, display: "flex", gap: 8, fontSize: 13, color: "#92400E" }}>
            <AlertCircle size={15} style={{ flexShrink: 0, marginTop: 1 }} />
            <span>Input VAT from purchase invoices and expenses is not yet automatically calculated. Add input VAT amounts via Journal Entries to account code <strong>1300 — VAT Input</strong> to reduce your VAT payable.</span>
          </div>
        </div>
      )}
    </div>
  )
}