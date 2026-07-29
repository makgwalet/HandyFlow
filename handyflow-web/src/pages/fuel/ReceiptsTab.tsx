// src/pages/fuel/ReceiptsTab.tsx
// NEW tab — stock-in history across all tanks with supplier details and totals
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ArrowDownToLine, AlertCircle, List, TrendingUp } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

// Same fuel-type color convention as FuelDashboard.tsx, reused here so a
// DIESEL line reads as the same color everywhere in this module.
const FUEL_COLORS: Record<string, string> = {
  DIESEL: "#1D4ED8", PETROL: "#DC2626", PARAFFIN: "#D97706", GAS: "#7C3AED", OTHER: "#64748B",
}

export default function ReceiptsTab() {
  const [filterTank, setFilterTank] = useState("ALL")
  const [view, setView]             = useState<"history" | "trend">("history")

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
  const tankFuelType = Object.fromEntries((tanks as any[]).map(t => [t.id, t.fuelType]))

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
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...(tanks as any[]).map(t => t.id)].map(id => (
            <button key={id} onClick={() => setFilterTank(id)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterTank === id ? 600 : 400,
                background: filterTank === id ? "#0D9488" : "#F1F5F9",
                color: filterTank === id ? "#fff" : "#64748B" }}>
              {id === "ALL" ? "All tanks" : tankMap[id] ?? id}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 2, background: "#F1F5F9", borderRadius: 8, padding: 3 }}>
          {([
            { key: "history", label: "History", icon: List },
            { key: "trend", label: "Price Trend", icon: TrendingUp },
          ] as const).map(v => (
            <button key={v.key} onClick={() => setView(v.key)}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "6px 12px", borderRadius: 6, border: "none", cursor: "pointer", fontSize: 12, fontWeight: 700,
                background: view === v.key ? "#fff" : "transparent", color: view === v.key ? "#1B3A6B" : "#64748B",
                boxShadow: view === v.key ? "0 1px 2px rgba(0,0,0,0.08)" : "none" }}>
              <v.icon size={13} /> {v.label}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading receipts...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <ArrowDownToLine size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No stock-in records</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Use the Tanks tab to receive fuel into a tank.</div>
        </div>
      ) : view === "trend" ? (
        <PriceTrendChart receipts={filtered} tankFuelType={tankFuelType} />
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

// ── Price Trend Chart ────────────────────────────────────────────────────
// FIX: "no cost-per-litre trend view" gap — pricePerLitre was captured on
// every receipt but never charted. Self-contained SVG line chart rather
// than a charting library — this project's frontend dependencies aren't
// known here, so this avoids assuming one is installed (same reasoning as
// building drag-and-drop on the native HTML5 API instead of a DnD library).
// One line per fuel type present in the filtered receipts, sharing one
// price axis (diesel/petrol/paraffin/gas are all similar R/L magnitude in
// practice, so a shared axis is a reasonable simplification here).
function PriceTrendChart({ receipts, tankFuelType }: { receipts: any[]; tankFuelType: Record<string, string> }) {
  const points = receipts
    .filter(r => r.pricePerLitre != null && r.receivedAt)
    .map(r => ({
      date: new Date(r.receivedAt).getTime(),
      price: Number(r.pricePerLitre),
      fuelType: tankFuelType[r.tankId] || "OTHER",
    }))

  if (points.length === 0) {
    return (
      <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
        <TrendingUp size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
        <div style={{ fontWeight: 600, color: "#475569" }}>No priced receipts to chart</div>
        <div style={{ fontSize: 13, marginTop: 4 }}>Receipts need a price per litre recorded to appear here.</div>
      </div>
    )
  }

  const seriesByType: Record<string, { date: number; price: number }[]> = {}
  points.forEach(p => { (seriesByType[p.fuelType] ??= []).push({ date: p.date, price: p.price }) })
  Object.values(seriesByType).forEach(arr => arr.sort((a, b) => a.date - b.date))

  const W = 760, H = 260
  const pad = { top: 20, right: 24, bottom: 34, left: 60 }
  const innerW = W - pad.left - pad.right
  const innerH = H - pad.top - pad.bottom

  const minDate = Math.min(...points.map(p => p.date))
  const maxDate = Math.max(...points.map(p => p.date))
  const minPrice = Math.min(...points.map(p => p.price))
  const maxPrice = Math.max(...points.map(p => p.price))
  const priceSpan = (maxPrice - minPrice) || Math.max(1, maxPrice * 0.1)
  const dateSpan = (maxDate - minDate) || 1
  const yLo = Math.max(0, minPrice - priceSpan * 0.15)
  const yHi = maxPrice + priceSpan * 0.15

  const xFor = (d: number) => pad.left + ((d - minDate) / dateSpan) * innerW
  const yFor = (p: number) => pad.top + innerH - ((p - yLo) / (yHi - yLo)) * innerH

  const gridLines = 4
  const gridValues = Array.from({ length: gridLines + 1 }, (_, i) => yLo + ((yHi - yLo) * i) / gridLines)

  return (
    <div>
      <svg viewBox={`0 0 ${W} ${H}`} style={{ width: "100%", height: "auto", maxWidth: 900 }}>
        {/* Gridlines + y-axis labels */}
        {gridValues.map((v, i) => (
          <g key={i}>
            <line x1={pad.left} x2={W - pad.right} y1={yFor(v)} y2={yFor(v)} stroke="#F1F5F9" strokeWidth={1} />
            <text x={pad.left - 8} y={yFor(v) + 4} textAnchor="end" fontSize={10} fill="#94A3B8">R{v.toFixed(2)}</text>
          </g>
        ))}
        {/* X-axis start/end date labels */}
        <text x={pad.left} y={H - 8} fontSize={10} fill="#94A3B8">{new Date(minDate).toLocaleDateString("en-ZA", { day: "numeric", month: "short" })}</text>
        <text x={W - pad.right} y={H - 8} fontSize={10} fill="#94A3B8" textAnchor="end">{new Date(maxDate).toLocaleDateString("en-ZA", { day: "numeric", month: "short" })}</text>

        {/* Series lines + points */}
        {Object.entries(seriesByType).map(([fuelType, series]) => {
          const color = FUEL_COLORS[fuelType] ?? "#64748B"
          const path = series.map((p, i) => `${i === 0 ? "M" : "L"} ${xFor(p.date)} ${yFor(p.price)}`).join(" ")
          return (
            <g key={fuelType}>
              {series.length > 1 && <path d={path} fill="none" stroke={color} strokeWidth={2} />}
              {series.map((p, i) => (
                <circle key={i} cx={xFor(p.date)} cy={yFor(p.price)} r={3.5} fill={color} stroke="#fff" strokeWidth={1.5} />
              ))}
            </g>
          )
        })}
      </svg>

      {/* Legend — latest price per fuel type */}
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginTop: 14, paddingTop: 14, borderTop: "1px solid #F1F5F9" }}>
        {Object.entries(seriesByType).map(([fuelType, series]) => {
          const latest = series[series.length - 1]
          const first = series[0]
          const change = series.length > 1 ? latest.price - first.price : 0
          return (
            <div key={fuelType} style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <div style={{ width: 10, height: 10, borderRadius: "50%", background: FUEL_COLORS[fuelType] ?? "#64748B" }} />
              <div>
                <div style={{ fontSize: 12, fontWeight: 700, color: "#0F172A" }}>{fuelType}</div>
                <div style={{ fontSize: 11, color: "#64748B" }}>
                  R{latest.price.toFixed(3)}/L latest
                  {series.length > 1 && (
                    <span style={{ marginLeft: 6, color: change > 0 ? "#DC2626" : change < 0 ? "#166534" : "#94A3B8", fontWeight: 600 }}>
                      {change > 0 ? "▲" : change < 0 ? "▼" : "—"} R{Math.abs(change).toFixed(3)} over period
                    </span>
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
