// src/pages/accounting/DashboardTab.tsx
// NO external chart library — pure CSS bars and SVG donut to avoid disk space issues
import { useState, useEffect } from "react"
import { TrendingUp, TrendingDown, DollarSign, AlertTriangle, Download } from "lucide-react"
import { apiClient } from "../../api/client"

interface MonthlySummary {
  year: number; month: number; monthLabel: string
  revenue: number; expenses: number; netProfit: number
}
interface AgingReport {
  current: number; days1to30: number; days31to60: number
  days61to90: number; over90: number; total: number
  lines: { invoiceNumber: string; customerName: string; balance: number; bucket: string }[]
}

const fmtR = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
const fmtRFull = (n: number) => `R ${(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const TEAL = "#0D9488"; const NAVY = "#1B3A6B"; const RED = "#DC2626"
const GREEN = "#166534"; const AMBER = "#D97706"; const PURPLE = "#7C3AED"

const downloadPdf = async (url: string, filename: string) => {
  try {
    const res = await apiClient.get(url, { responseType: "blob" })
    const blob = new Blob([res.data], { type: "application/pdf" })
    const link = document.createElement("a")
    link.href = URL.createObjectURL(blob)
    link.download = filename
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (e) { console.error("PDF download failed", e) }
}

// ── Pure CSS grouped bar chart ────────────────────────────────────────────────
function BarChartCss({ data }: { data: MonthlySummary[] }) {
  const max = Math.max(...data.flatMap(m => [m.revenue, m.expenses]), 1)
  return (
    <div style={{ display: "flex", alignItems: "flex-end", gap: 12, height: 180, padding: "0 4px" }}>
      {data.map(m => (
        <div key={`${m.year}-${m.month}`}
          style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
          <div style={{ display: "flex", alignItems: "flex-end", gap: 3, height: 150, width: "100%" }}>
            <div title={`Revenue: ${fmtRFull(m.revenue)}`}
              style={{ flex: 1, background: TEAL, borderRadius: "3px 3px 0 0",
                height: `${(m.revenue / max) * 100}%`, minHeight: m.revenue > 0 ? 2 : 0,
                transition: "height 0.4s ease" }} />
            <div title={`Expenses: ${fmtRFull(m.expenses)}`}
              style={{ flex: 1, background: RED, borderRadius: "3px 3px 0 0",
                height: `${(m.expenses / max) * 100}%`, minHeight: m.expenses > 0 ? 2 : 0,
                transition: "height 0.4s ease" }} />
          </div>
          <div style={{ fontSize: 10, color: "#64748B", textAlign: "center" }}>{m.monthLabel}</div>
        </div>
      ))}
    </div>
  )
}

// ── Pure CSS line chart (SVG) ─────────────────────────────────────────────────
function LineChartSvg({ data }: { data: MonthlySummary[] }) {
  if (data.length < 2) return null
  const W = 260; const H = 150; const PAD = 16
  const values = data.map(m => m.netProfit)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1

  const pts = data.map((m, i) => ({
    x: PAD + (i / (data.length - 1)) * (W - PAD * 2),
    y: PAD + ((max - m.netProfit) / range) * (H - PAD * 2),
    v: m.netProfit, label: m.monthLabel,
  }))
  const path = pts.map((p, i) => `${i === 0 ? "M" : "L"} ${p.x} ${p.y}`).join(" ")
  const zero = min < 0 && max > 0 ? PAD + (max / range) * (H - PAD * 2) : null

  return (
    <svg width="100%" viewBox={`0 0 ${W} ${H}`} style={{ overflow: "visible" }}>
      {zero && <line x1={PAD} y1={zero} x2={W - PAD} y2={zero}
        stroke="#E2E8F0" strokeWidth={1} strokeDasharray="4 3" />}
      <path d={path} fill="none" stroke={NAVY} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
      {pts.map((p, i) => (
        <g key={i}>
          <circle cx={p.x} cy={p.y} r={4} fill={NAVY} />
          <title>{p.label}: {fmtRFull(p.v)}</title>
        </g>
      ))}
      {pts.map((p, i) => (
        <text key={i} x={p.x} y={H - 2} textAnchor="middle"
          fontSize={9} fill="#94A3B8">{p.label}</text>
      ))}
    </svg>
  )
}

// ── SVG donut chart ───────────────────────────────────────────────────────────
function DonutChart({ revenue, expenses }: { revenue: number; expenses: number }) {
  const total = revenue + expenses || 1
  const r = 50; const cx = 80; const cy = 80
  const circ = 2 * Math.PI * r

  const revPct = revenue / total
  const expPct = expenses / total
  const gap = 0.02 // small gap between segments

  const revDash = circ * (revPct - gap)
  const expOffset = circ * revPct
  const expDash = circ * (expPct - gap)

  return (
    <svg width={160} height={160} viewBox="0 0 160 160">
      <circle cx={cx} cy={cy} r={r} fill="none" stroke="#F1F5F9" strokeWidth={22} />
      {revenue > 0 && (
        <circle cx={cx} cy={cy} r={r} fill="none" stroke={TEAL} strokeWidth={22}
          strokeDasharray={`${revDash} ${circ}`} strokeDashoffset={circ / 4}
          strokeLinecap="round" />
      )}
      {expenses > 0 && (
        <circle cx={cx} cy={cy} r={r} fill="none" stroke={RED} strokeWidth={22}
          strokeDasharray={`${expDash} ${circ}`}
          strokeDashoffset={circ / 4 - expOffset * 1}
          strokeLinecap="round" />
      )}
    </svg>
  )
}

// ── Main dashboard ────────────────────────────────────────────────────────────
export default function DashboardTab() {
  const [monthly, setMonthly] = useState<MonthlySummary[]>([])
  const [aging, setAging]     = useState<AgingReport | null>(null)
  const [loading, setLoading] = useState(true)

  const today     = new Date().toISOString().split("T")[0]
  const yearStart = `${new Date().getFullYear()}-01-01`

  useEffect(() => {
    Promise.all([
      apiClient.get("/api/v1/accounting/reports/monthly-summary?months=6"),
      apiClient.get("/api/v1/accounting/reports/ar-aging"),
    ]).then(([mRes, aRes]) => {
      setMonthly((mRes.data?.data ?? mRes.data) as MonthlySummary[])
      setAging((aRes.data?.data ?? aRes.data) as AgingReport)
    }).catch(console.error).finally(() => setLoading(false))
  }, [])

  if (loading) return (
    <div style={{ padding: 60, textAlign: "center", color: "#94A3B8" }}>Loading dashboard...</div>
  )

  const totalRevenue  = monthly.reduce((s, m) => s + m.revenue, 0)
  const totalExpenses = monthly.reduce((s, m) => s + m.expenses, 0)
  const netProfit     = totalRevenue - totalExpenses
  const margin        = totalRevenue > 0 ? ((netProfit / totalRevenue) * 100).toFixed(1) : "0"

  return (
    <div>
      {/* KPI Cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16, marginBottom: 24 }}>
        {[
          { label: "Revenue (6 months)",    value: fmtR(totalRevenue),       sub: "Total income posted",       color: TEAL,  icon: <TrendingUp size={18} /> },
          { label: "Expenses (6 months)",   value: fmtR(totalExpenses),      sub: "Total costs posted",        color: RED,   icon: <TrendingDown size={18} /> },
          { label: "Net Profit",            value: fmtR(netProfit),          sub: `${margin}% margin`,         color: netProfit >= 0 ? GREEN : RED, icon: <DollarSign size={18} /> },
          { label: "Outstanding AR",        value: fmtR(aging?.total ?? 0),  sub: `${aging?.lines?.length ?? 0} invoices`, color: AMBER, icon: <AlertTriangle size={18} /> },
        ].map(k => (
          <div key={k.label} style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, padding: 18 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 8 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase", letterSpacing: "0.05em" }}>{k.label}</div>
              <div style={{ color: k.color, opacity: 0.6 }}>{k.icon}</div>
            </div>
            <div style={{ fontSize: 20, fontWeight: 800, color: k.color, marginBottom: 3 }}>{k.value}</div>
            <div style={{ fontSize: 11, color: "#94A3B8" }}>{k.sub}</div>
          </div>
        ))}
      </div>

      {/* Charts row */}
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16, marginBottom: 24 }}>

        {/* Bar chart */}
        <div style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, padding: 20 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Revenue vs Expenses — Last 6 Months</div>
            <div style={{ display: "flex", gap: 14 }}>
              {[{ label: "Revenue", color: TEAL }, { label: "Expenses", color: RED }].map(l => (
                <div key={l.label} style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 11, color: "#64748B" }}>
                  <div style={{ width: 10, height: 10, borderRadius: 2, background: l.color }} />
                  {l.label}
                </div>
              ))}
            </div>
          </div>
          <BarChartCss data={monthly} />
        </div>

        {/* Line chart */}
        <div style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, padding: 20 }}>
          <div style={{ fontSize: 14, fontWeight: 700, color: "#0F172A", marginBottom: 16 }}>Net Profit Trend</div>
          <LineChartSvg data={monthly} />
          {monthly.length > 0 && (
            <div style={{ marginTop: 8, display: "flex", justifyContent: "space-between", fontSize: 11, color: "#64748B" }}>
              <span>Last: {fmtR(monthly[monthly.length - 1]?.netProfit ?? 0)}</span>
              <span style={{ color: netProfit >= 0 ? GREEN : RED, fontWeight: 700 }}>
                {netProfit >= 0 ? "↑" : "↓"} {margin}% margin
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Bottom row */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>

        {/* Donut + legend */}
        <div style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, padding: 20 }}>
          <div style={{ fontSize: 14, fontWeight: 700, color: "#0F172A", marginBottom: 16 }}>
            Revenue vs Expenses Split (6 months)
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
            <DonutChart revenue={totalRevenue} expenses={totalExpenses} />
            <div>
              {[
                { name: "Revenue",  value: totalRevenue,  color: TEAL },
                { name: "Expenses", value: totalExpenses, color: RED  },
              ].map(e => (
                <div key={e.name} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
                  <div style={{ width: 12, height: 12, borderRadius: 3, background: e.color, flexShrink: 0 }} />
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 600, color: "#0F172A" }}>{e.name}</div>
                    <div style={{ fontSize: 13, fontWeight: 800, color: e.color }}>{fmtR(e.value)}</div>
                  </div>
                </div>
              ))}
              <div style={{ marginTop: 4, paddingTop: 10, borderTop: "1px solid #F1F5F9" }}>
                <div style={{ fontSize: 11, color: "#64748B" }}>Profit Margin</div>
                <div style={{ fontSize: 16, fontWeight: 800, color: netProfit >= 0 ? GREEN : RED }}>{margin}%</div>
              </div>
            </div>
          </div>
        </div>

        {/* PDF Downloads */}
        <div style={{ background: "white", border: "1px solid #E2E8F0", borderRadius: 12, padding: 20 }}>
          <div style={{ fontSize: 14, fontWeight: 700, color: "#0F172A", marginBottom: 4 }}>Download Reports</div>
          <div style={{ fontSize: 12, color: "#94A3B8", marginBottom: 16 }}>Year to date: {yearStart} to {today}</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {[
              { label: "Profit & Loss",  url: `/api/v1/accounting/reports/profit-and-loss/pdf?from=${yearStart}&to=${today}`,  file: `PL-${yearStart}-to-${today}.pdf`,          color: TEAL   },
              { label: "Balance Sheet",  url: `/api/v1/accounting/reports/balance-sheet/pdf?from=${yearStart}&to=${today}`,   file: `BS-${yearStart}-to-${today}.pdf`,          color: NAVY   },
              { label: "Trial Balance",  url: `/api/v1/accounting/reports/trial-balance/pdf?from=${yearStart}&to=${today}`,   file: `TB-${yearStart}-to-${today}.pdf`,          color: AMBER  },
              { label: "VAT201 Summary", url: `/api/v1/accounting/reports/vat201/pdf?from=${yearStart}&to=${today}`,         file: `VAT201-${yearStart}-to-${today}.pdf`,       color: PURPLE },
            ].map(r => (
              <button key={r.label}
                onClick={() => downloadPdf(r.url, r.file)}
                style={{ display: "flex", alignItems: "center", justifyContent: "space-between",
                  padding: "10px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0",
                  borderRadius: 8, cursor: "pointer" }}
                onMouseEnter={e => { const b = e.currentTarget; b.style.background = "#F1F5F9"; b.style.borderColor = r.color }}
                onMouseLeave={e => { const b = e.currentTarget; b.style.background = "#F8FAFC"; b.style.borderColor = "#E2E8F0" }}>
                <span style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{r.label}</span>
                <div style={{ display: "flex", alignItems: "center", gap: 6, color: r.color }}>
                  <span style={{ fontSize: 11, fontWeight: 700 }}>PDF</span>
                  <Download size={13} />
                </div>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
