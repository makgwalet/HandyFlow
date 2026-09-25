// src/pages/fuel/MarginTab.tsx
//
// Fuel cost/margin engine, per the design proposal agreed with the
// product owner: weighted-average cost snapshotted at time of sale onto
// each delivery/dispatch. Gated server-side behind FUEL_MARGIN_READ
// (separate from FUEL_READ) since margin reveals wholesale cost — this
// tab itself is only ever rendered by FuelPage when usePermission
// confirms the user actually has that permission, matching this
// codebase's established "client-side mirror of the server check"
// convention (see usePermission's own doc comment).

import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { TrendingUp, TrendingDown, Fuel, Truck, AlertCircle } from "lucide-react"

interface MarginLine {
  sourceType: "DELIVERY" | "DISPATCH"
  id: string; tankId: string; occurredAt: string
  litres: number; pricePerLitre: number | null; costPerLitreAtSale: number | null
  revenue: number | null; cost: number | null; margin: number | null; marginPercent: number | null
  recipientLabel: string | null
}

interface MarginReport {
  fromDate: string; toDate: string
  totalRevenue: number; totalCostOfRevenueGenerating: number; totalMargin: number; marginPercent: number | null
  totalInternalLitres: number; totalInternalCost: number
  transactionsWithoutCostData: number
  lines: MarginLine[]
}

const fmtR = (n: number | null) => n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtPct = (n: number | null) => n == null ? "—" : `${n.toFixed(1)}%`
const fmtDate = (s: string) => new Date(s).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const todayStr = () => new Date().toISOString().slice(0, 10)
const monthStartStr = () => { const d = new Date(); d.setDate(1); return d.toISOString().slice(0, 10) }

const inp: React.CSSProperties = { padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, outline: "none", background: "var(--hf-surface)" }

export default function MarginTab() {
  const [from, setFrom] = useState(monthStartStr())
  const [to, setTo] = useState(todayStr())

  const { data: report, isLoading } = useQuery<MarginReport>({
    queryKey: ["fuel-margin-report", from, to],
    queryFn: async () => {
      const fromIso = `${from}T00:00:00Z`
      const toIso = `${to}T23:59:59Z`
      const r = await apiClient.get(`/api/v1/fuel/margin-report?from=${fromIso}&to=${toIso}`)
      return r.data?.data ?? r.data
    },
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 20, flexWrap: "wrap", gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: "var(--hf-text)" }}>Cost & Margin</h2>
          <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 2 }}>Weighted-average cost, snapshotted at time of sale — visible to those with fuel margin access only</div>
        </div>
        <div style={{ display: "flex", gap: 10, alignItems: "flex-end" }}>
          <div><label style={{ display: "block", fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 3 }}>From</label><input type="date" value={from} onChange={e => setFrom(e.target.value)} style={inp} /></div>
          <div><label style={{ display: "block", fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 3 }}>To</label><input type="date" value={to} onChange={e => setTo(e.target.value)} style={inp} /></div>
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading…</div>
      ) : !report ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>No data for this period.</div>
      ) : (
        <>
          {report.transactionsWithoutCostData > 0 && (
            <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 14px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 8, color: "var(--hf-warning-text-strong)", fontSize: 13, marginBottom: 18 }}>
              <AlertCircle size={15} />
              {report.transactionsWithoutCostData} transaction{report.transactionsWithoutCostData !== 1 ? "s" : ""} in this period {report.transactionsWithoutCostData !== 1 ? "have" : "has"} no cost data (predates cost tracking, or the tank had no purchase history yet) — excluded from the totals below.
            </div>
          )}

          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 24 }}>
            <StatCard label="Revenue" value={fmtR(report.totalRevenue)} icon={TrendingUp} color="var(--hf-success-text-strong)" bg="var(--hf-success-soft-strong)" />
            <StatCard label="Cost" value={fmtR(report.totalCostOfRevenueGenerating)} icon={Fuel} color="var(--hf-warning-text-strong)" bg="var(--hf-warning-soft)" />
            <StatCard label="Margin" value={fmtR(report.totalMargin)} sub={fmtPct(report.marginPercent)} icon={report.totalMargin >= 0 ? TrendingUp : TrendingDown} color={report.totalMargin >= 0 ? "var(--hf-primary-text)" : "var(--hf-danger-text)"} bg={report.totalMargin >= 0 ? "var(--hf-info-soft)" : "var(--hf-danger-soft)"} />
            <StatCard label="Internal Fleet Cost" value={fmtR(report.totalInternalCost)} sub={`${report.totalInternalLitres.toLocaleString()} L`} icon={Truck} color="var(--hf-text-muted)" bg="var(--hf-surface-sunken)" />
          </div>

          <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)", textTransform: "uppercase" as const, letterSpacing: 0.4, marginBottom: 10 }}>
            Transactions — {report.lines.length}
          </div>
          {report.lines.length === 0 ? (
            <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>No deliveries or dispatches in this period.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {report.lines.map(l => (
                <div key={l.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    {l.sourceType === "DELIVERY" ? <Truck size={14} style={{ color: 'var(--hf-primary-text)' }} /> : <Fuel size={14} style={{ color: 'var(--hf-accent-text)' }} />}
                    <div>
                      <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{l.recipientLabel || "—"}</span>
                      <span style={{ color: "var(--hf-text-faint)", marginLeft: 8 }}>{l.litres.toLocaleString()} L · {fmtDate(l.occurredAt)}</span>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 16, color: "var(--hf-text-faint)" }}>
                    <span>Rev: {fmtR(l.revenue)}</span>
                    <span>Cost: {fmtR(l.cost)}</span>
                    <span style={{ fontWeight: 700, color: l.margin == null ? "var(--hf-text-faint)" : l.margin >= 0 ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)", minWidth: 90, textAlign: "right" as const }}>
                      {l.margin == null ? "No revenue" : `${fmtR(l.margin)} (${fmtPct(l.marginPercent)})`}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}

function StatCard({ label, value, sub, icon: Icon, color, bg }: { label: string; value: string; sub?: string; icon: any; color: string; bg: string }) {
  return (
    <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "16px 18px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
        <div style={{ width: 28, height: 28, borderRadius: 8, background: bg, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Icon size={14} style={{ color }} />
        </div>
        <span style={{ fontSize: 12, color: "var(--hf-text-faint)", fontWeight: 600 }}>{label}</span>
      </div>
      <div style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-text)" }}>{value}</div>
      {sub && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 2 }}>{sub}</div>}
    </div>
  )
}
