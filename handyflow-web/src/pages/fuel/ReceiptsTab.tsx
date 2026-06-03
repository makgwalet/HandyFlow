// src/pages/fuel/ReceiptsTab.tsx
// NEW tab — stock-in history across all tanks with supplier details and totals
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ArrowDownToLine, AlertCircle } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

export default function ReceiptsTab() {
  const [filterTank, setFilterTank] = useState("ALL")

  const { data: receipts = [], isLoading } = useQuery<any[]>({
    queryKey: ["receipts"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/receipts?size=200&sort=receivedAt,desc")),
  })

  const { data: tanks = [] } = useQuery<any[]>({
    queryKey: ["tanks"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/fuel/tanks"); return r.data?.data ?? r.data ?? [] },
  })

  const filtered = filterTank === "ALL" ? (receipts as any[]) : (receipts as any[]).filter(r => r.tankId === filterTank)
  const totalLitres = filtered.reduce((s, r) => s + Number(r.litresReceived ?? 0), 0)
  const totalCost   = filtered.reduce((s, r) => s + Number(r.totalCost ?? 0), 0)

  const tankMap = Object.fromEntries((tanks as any[]).map(t => [t.id, t.name]))

  return (
    <div>
      {/* Stats */}
      {filtered.length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
          {[
            { label: "Stock-in events", value: filtered.length,                           color: "#1B3A6B" },
            { label: "Total litres",    value: `${totalLitres.toLocaleString()} L`,       color: "#0D9488" },
            { label: "Total cost",      value: `R ${totalCost.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`, color: "#DC2626" },
          ].map(s => (
            <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
              <div style={{ fontSize: 20, fontWeight: 700, color: s.color }}>{s.value}</div>
              <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* Tank filter */}
      <div style={{ display: "flex", gap: 6, marginBottom: 18, flexWrap: "wrap" }}>
        {["ALL", ...(tanks as any[]).map(t => t.id)].map(id => (
          <button key={id} onClick={() => setFilterTank(id)}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterTank === id ? 600 : 400,
              background: filterTank === id ? "#0D9488" : "#F1F5F9",
              color: filterTank === id ? "#fff" : "#64748B" }}>
            {id === "ALL" ? "All tanks" : tankMap[id] ?? id}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading receipts...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <ArrowDownToLine size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No stock-in records</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Use the Tanks tab to receive fuel into a tank.</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Date","Tank","Litres","Price/L","Total Cost","Delivery Note","Invoice","Level Before → After"].map(h => (
                  <th key={h} style={{ padding: "11px 14px", textAlign: "left", fontWeight: 700, fontSize: 11, color: "#64748B", letterSpacing: "0.05em", whiteSpace: "nowrap" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((r, i) => (
                <tr key={r.id} style={{ borderBottom: i < filtered.length - 1 ? "1px solid #F1F5F9" : "none", background: "#fff" }}>
                  <td style={{ padding: "12px 14px", fontWeight: 600, color: "#0F172A", whiteSpace: "nowrap" }}>{fmtDate(r.receivedAt)}</td>
                  <td style={{ padding: "12px 14px", color: "#475569" }}>{tankMap[r.tankId] ?? "—"}</td>
                  <td style={{ padding: "12px 14px", fontWeight: 700, color: "#0D9488" }}>{Number(r.litresReceived).toLocaleString()} L</td>
                  <td style={{ padding: "12px 14px", color: "#475569" }}>{r.pricePerLitre ? `R ${Number(r.pricePerLitre).toFixed(3)}` : "—"}</td>
                  <td style={{ padding: "12px 14px", fontWeight: 700, color: "#0F172A" }}>{fmtR(r.totalCost)}</td>
                  <td style={{ padding: "12px 14px", color: "#475569", fontSize: 12 }}>{r.deliveryNote || "—"}</td>
                  <td style={{ padding: "12px 14px", color: "#94A3B8", fontSize: 12 }}>{r.invoiceRef || "—"}</td>
                  <td style={{ padding: "12px 14px", fontSize: 12, color: "#64748B", whiteSpace: "nowrap" }}>
                    {r.levelBefore != null ? `${Number(r.levelBefore).toLocaleString()} L` : "—"}
                    <span style={{ margin: "0 6px", color: "#CBD5E1" }}>→</span>
                    {r.levelAfter != null ? <strong style={{ color: "#0D9488" }}>{Number(r.levelAfter).toLocaleString()} L</strong> : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
