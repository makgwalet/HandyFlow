// src/pages/supply-chain/ScmDashboard.tsx — Supply Chain overview: KPIs with trend, spend, order status, stock risk, supplier performance, alerts.
import { useQuery } from "@tanstack/react-query"
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from "recharts"
import { Users, ShoppingCart, FileText, Package, TrendingUp, Plus, ArrowRight } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useThemeColors } from "../../theme/ThemeContext"
import { Badge, TH, TD, Spinner, fmtR, fmtDate } from "./scm.shared"
import { asList, asOne, lastMonths, monthSeries, deltaPct, deltaText, statusBreakdown, dashboardAlerts, contractCounts, barWidth, pctText } from "./scmOps"
import { StatCard, Panel, Pill, grid, toneColors } from "./scmUi"

const BASE = "/api/v1/supply-chain"
interface Summary { totalSuppliers: number; openPurchaseOrders: number; pendingInvoices: number; invoicesForApproval: number; lowStockItems: number; overdueInvoices: number }
interface PO { id: string; orderNumber: string; supplierName: string; status: string; totalAmount: number; orderDate: string }
interface Spend { invoiced: number; byMonth: { label: string; amount: number }[] }
interface SupplierView { supplierId: string; supplierName: string; score: number | null; rating: string | null; onTimePct: number | null; flag: string | null }
interface RepItem { catalogueItemId: string; itemName: string; available: number; reorderLevel: number; urgency: string }
const STATUS_VAR = ["--hf-info", "--hf-success", "--hf-warning", "--hf-violet", "--hf-danger", "--hf-text-faint"]
const MONTHS = 6

export function ScmDashboard({ onNav }: { onNav: (section: string) => void }) {
  const color = useThemeColors()
  const canAnalyse = usePermissions(["SCM_READ", "SCM_ADMIN"])
  const canPrice = usePermissions(["SCM_PRICE_OVERRIDE_APPROVE", "SCM_ADMIN"])
  const canRaise = usePermissions(["SCM_REQUISITION_CREATE", "SCM_ADMIN"])
  const months = lastMonths(MONTHS)
  const range = { from: `${months[0]}-01`, to: new Date().toISOString().slice(0, 10) }
  const opt = { retry: false, staleTime: 60_000 } as const

  const summary = useQuery({ queryKey: ["scm-summary"], queryFn: () => apiClient.get(`${BASE}/summary`).then(r => asOne<Summary>(r)), ...opt })
  const pos = useQuery({ queryKey: ["scm-dash-pos"], queryFn: () => apiClient.get(`${BASE}/purchase-orders`, { params: { size: 100 } }).then(asList<PO>), ...opt })
  const spend = useQuery({ queryKey: ["scm-dash-spend", range], enabled: canAnalyse, queryFn: () => apiClient.get(`${BASE}/analytics/spend`, { params: range }).then(r => asOne<Spend>(r)), ...opt })
  const suppliers = useQuery({ queryKey: ["scm-dash-suppliers"], enabled: canAnalyse, queryFn: () => apiClient.get(`${BASE}/analytics/suppliers`, { params: { months: 12 } }).then(asList<SupplierView>), ...opt })
  const replen = useQuery({ queryKey: ["scm-replenishment"], enabled: canAnalyse || canRaise, queryFn: () => apiClient.get(`${BASE}/replenishment`).then(asList<RepItem>), ...opt })
  const contracts = useQuery({ queryKey: ["scm-contracts"], enabled: canAnalyse, queryFn: () => apiClient.get(`${BASE}/contracts`).then(asList<{ status: string; endDate: string | null; renewalNoticeDue?: boolean }>), ...opt })
  const quarantine = useQuery({ queryKey: ["scm-quarantine"], enabled: canAnalyse, queryFn: () => apiClient.get(`${BASE}/quarantine`, { params: { status: "OPEN" } }).then(asList<unknown>), ...opt })
  const prices = useQuery({ queryKey: ["scm-price-overrides"], enabled: canPrice, queryFn: () => apiClient.get(`${BASE}/requisitions/price-overrides`).then(asList<unknown>), ...opt })

  if (summary.isLoading || pos.isLoading) return <Spinner />
  const s = summary.data
  const poList = pos.data ?? []
  const series = monthSeries(spend.data?.byMonth ?? [], months)
  const thisM = series[series.length - 1]?.amount ?? 0, lastM = series[series.length - 2]?.amount ?? 0
  const delta = deltaPct(thisM, lastM)
  const mix = statusBreakdown(poList)
  const rep = replen.data ?? []
  const atRisk = rep.filter(r => r.urgency !== "REORDER").slice(0, 6)
  const ranked = [...(suppliers.data ?? [])].filter(x => x.score != null).sort((a, b) => (b.score ?? 0) - (a.score ?? 0))
  const cc = contractCounts(contracts.data ?? [])
  const alerts = dashboardAlerts({
    overdueInvoices: s?.overdueInvoices ?? 0, invoicesForApproval: s?.invoicesForApproval ?? 0, priceApprovals: (prices.data ?? []).length,
    quarantineOpen: (quarantine.data ?? []).length, contractsExpiring: cc.EXPIRING + cc.EXPIRED, stockouts: rep.filter(r => r.urgency === "STOCKOUT").length, reorders: rep.filter(r => r.urgency === "REORDER").length,
  })

  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <div style={grid(190)}>
        <StatCard label="Spend this month" value={fmtR(thisM)} sub={<span>{deltaText(delta)} vs last month</span>} icon={TrendingUp} tone="info" onClick={() => onNav("analytics")} />
        <StatCard label="Open orders" value={s?.openPurchaseOrders ?? 0} icon={ShoppingCart} tone="info" onClick={() => onNav("purchase-orders")} />
        <StatCard label="Invoices to pay" value={s?.invoicesForApproval ?? 0} sub={`${s?.pendingInvoices ?? 0} pending · ${s?.overdueInvoices ?? 0} overdue`} icon={FileText} tone={(s?.overdueInvoices ?? 0) > 0 ? "bad" : "ok"} onClick={() => onNav("invoices")} />
        <StatCard label="Low stock" value={s?.lowStockItems ?? 0} icon={Package} tone={(s?.lowStockItems ?? 0) > 0 ? "warn" : "neutral"} onClick={() => onNav("inventory")} />
        <StatCard label="Suppliers" value={s?.totalSuppliers ?? 0} icon={Users} tone="ok" onClick={() => onNav("suppliers")} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 14 }}>
        <Panel title={`Spend, last ${MONTHS} months`}>
          {!canAnalyse ? <Muted>Spend analytics need read access to Supply Chain.</Muted> : spend.isLoading ? <Spinner /> : (
            <div style={{ height: 210 }}>
              <ResponsiveContainer>
                <AreaChart data={series} margin={{ left: 0, right: 8, top: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={color("--hf-border")} />
                  <XAxis dataKey="month" tick={{ fontSize: 11, fill: color("--hf-text-faint") }} />
                  <YAxis tick={{ fontSize: 11, fill: color("--hf-text-faint") }} tickFormatter={v => `${Math.round(Number(v) / 1000)}k`} width={40} />
                  <Tooltip formatter={v => fmtR(Number(v))} />
                  <Area type="monotone" dataKey="amount" stroke={color("--hf-info")} fill={color("--hf-info")} fillOpacity={0.15} />
                </AreaChart>
              </ResponsiveContainer>
            </div>)}
        </Panel>
        <Panel title="Orders by status" right={<LinkBtn onClick={() => onNav("purchase-orders")}>All orders</LinkBtn>}>
          {mix.length === 0 ? <Muted>No purchase orders yet.</Muted> : (
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
              <div style={{ width: 150, height: 150 }}>
                <ResponsiveContainer><PieChart><Pie data={mix} dataKey="count" nameKey="status" innerRadius={42} outerRadius={68} paddingAngle={2}>
                  {mix.map((m, i) => <Cell key={m.status} fill={color(STATUS_VAR[i % STATUS_VAR.length])} />)}</Pie><Tooltip /></PieChart></ResponsiveContainer>
              </div>
              <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 12, display: "grid", gap: 4 }}>
                {mix.slice(0, 6).map((m, i) => <li key={m.status}><span style={{ display: "inline-block", width: 9, height: 9, borderRadius: 5, background: color(STATUS_VAR[i % STATUS_VAR.length]), marginRight: 6 }} />{m.status.replace(/_/g, " ").toLowerCase()} · {m.count}</li>)}
              </ul>
            </div>)}
        </Panel>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 14 }}>
        <Panel title="Needs attention">
          {alerts.length === 0 ? <Muted>Nothing needs attention.</Muted> : (
            <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
              {alerts.map(a => (
                <li key={a.key}><button onClick={() => onNav(a.section)} style={{ width: "100%", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, padding: "8px 10px", borderRadius: 8, border: "1px solid var(--hf-border)", background: toneColors(a.tone === "bad" ? "bad" : a.tone === "warn" ? "warn" : "info").bg, color: toneColors(a.tone === "bad" ? "bad" : a.tone === "warn" ? "warn" : "info").fg, cursor: "pointer", fontSize: 13, textAlign: "left" }}>
                  {a.text}<ArrowRight size={14} /></button></li>))}
            </ul>)}
        </Panel>
        <Panel title="Stock at risk" right={<LinkBtn onClick={() => onNav("replenishment")}>Replenishment</LinkBtn>}>
          {atRisk.length === 0 ? <Muted>No item is out of stock or critical.</Muted> : (
            <div style={{ display: "grid", gap: 10 }}>{atRisk.map(r => {
              const pct = r.reorderLevel > 0 ? (r.available / r.reorderLevel) * 100 : 0
              return (<div key={r.catalogueItemId}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 3 }}><span>{r.itemName}</span><span>{r.available} / {r.reorderLevel}</span></div>
                <div style={{ background: "var(--hf-surface-sunken)", borderRadius: 4, height: 8 }}><div style={{ width: barWidth(pct), height: 8, borderRadius: 4, background: r.urgency === "STOCKOUT" ? "var(--hf-danger)" : "var(--hf-warning)" }} /></div>
              </div>)
            })}</div>)}
        </Panel>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 14 }}>
        <Panel title="Recent purchase orders" right={<span style={{ display: "flex", gap: 8 }}>{canRaise && <LinkBtn onClick={() => onNav("requisitions")}><Plus size={12} /> Requisition</LinkBtn>}<LinkBtn onClick={() => onNav("purchase-orders")}>View all</LinkBtn></span>}>
          {poList.length === 0 ? <Muted>No purchase orders yet.</Muted> : (
            <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={TH}>Order</th><th style={TH}>Supplier</th><th style={TH}>Total</th><th style={TH}>Status</th></tr></thead>
              <tbody>{poList.slice(0, 6).map(p => <tr key={p.id} style={{ borderTop: "1px solid var(--hf-border)" }}><td style={TD}><strong>{p.orderNumber}</strong><div style={{ fontSize: 11 }}>{fmtDate(p.orderDate)}</div></td><td style={TD}>{p.supplierName}</td><td style={TD}>{fmtR(p.totalAmount)}</td><td style={TD}><Badge status={p.status} /></td></tr>)}</tbody>
            </table></div>)}
        </Panel>
        <Panel title="Supplier performance" right={<LinkBtn onClick={() => onNav("analytics")}>Details</LinkBtn>}>
          {ranked.length === 0 ? <Muted>No scored suppliers yet.</Muted> : (
            <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
              {ranked.slice(0, 5).map(x => <li key={x.supplierId} style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 13 }}>
                <span>{x.supplierName}<span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}> · on time {pctText(x.onTimePct)}</span></span>
                <Pill tone={(x.score ?? 0) >= 80 ? "ok" : (x.score ?? 0) >= 60 ? "warn" : "bad"}>{x.score}</Pill></li>)}
            </ul>)}
        </Panel>
      </div>
    </div>
  )
}

const Muted = ({ children }: { children: React.ReactNode }) => <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>{children}</div>
const LinkBtn = ({ onClick, children }: { onClick: () => void; children: React.ReactNode }) =>
  <button onClick={onClick} style={{ background: "none", border: "none", color: "var(--hf-primary)", fontSize: 12, fontWeight: 600, cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 3 }}>{children}</button>
