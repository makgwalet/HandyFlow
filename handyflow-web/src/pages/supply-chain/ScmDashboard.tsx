// src/pages/supply-chain/ScmDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Users, ShoppingCart, FileText, Package, AlertTriangle,
  Clock, TrendingUp, ArrowRight, CheckCircle
} from "lucide-react"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from "recharts"

interface Summary {
  totalSuppliers: number; openPurchaseOrders: number; pendingInvoices: number
  invoicesForApproval: number; lowStockItems: number; overdueInvoices: number
}
interface PO {
  id: string; orderNumber: string; supplierName: string; status: string
  totalAmount: number; orderDate: string; requiredByDate: string | null
}
interface LowStockItem {
  id: string; catalogueItemId: string; qtyOnHand: number; reorderPoint: number; avgCost: number
}
// NEW: backs real item names on the low-stock chart — same fix already
// applied in InventoryTab.tsx for the identical truncated-UUID issue.
interface CatalogueItem { id: string; name: string }

const fmtR  = (n: number) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
const fmtD  = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA") : "—"
const ACCENT = "#D97706"

const PO_STATUS: Record<string, { bg: string; color: string; label: string }> = {
  DRAFT:              { bg: "#F1F5F9", color: "#475569",  label: "Draft" },
  PENDING_APPROVAL:   { bg: "#FEF3C7", color: "#92400E",  label: "Pending" },
  APPROVED:           { bg: "#DBEAFE", color: "#1D4ED8",  label: "Approved" },
  SENT:               { bg: "#EDE9FE", color: "#7C3AED",  label: "Sent" },
  ACKNOWLEDGED:       { bg: "#D1FAE5", color: "#065F46",  label: "Acknowledged" },
  PARTIALLY_RECEIVED: { bg: "#FEF9C3", color: "#713F12",  label: "Partial" },
  FULLY_RECEIVED:     { bg: "#DCFCE7", color: "#166534",  label: "Received" },
  INVOICED:           { bg: "#DBEAFE", color: "#1E40AF",  label: "Invoiced" },
  CANCELLED:          { bg: "#FEE2E2", color: "#DC2626",  label: "Cancelled" },
}

function KpiCard({ label, value, icon: Icon, color, bg, onClick, urgent }:
  { label: string; value: number; icon: React.ElementType; color: string; bg: string; onClick?: () => void; urgent?: boolean }) {
  return (
    <div
      onClick={onClick}
      style={{
        background: "#fff", border: `1px solid ${urgent && value > 0 ? color : "#E2E8F0"}`,
        borderRadius: 12, padding: "18px 20px", cursor: onClick ? "pointer" : "default",
        transition: "box-shadow 0.15s",
        boxShadow: urgent && value > 0 ? `0 0 0 3px ${bg}` : "none",
      }}
      onMouseEnter={e => { if (onClick) (e.currentTarget as HTMLElement).style.boxShadow = "0 4px 12px rgba(0,0,0,0.08)" }}
      onMouseLeave={e => { if (onClick) (e.currentTarget as HTMLElement).style.boxShadow = urgent && value > 0 ? `0 0 0 3px ${bg}` : "none" }}
    >
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.06em" }}>
          {label}
        </div>
        <div style={{ width: 34, height: 34, borderRadius: 9, background: bg, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Icon size={16} color={color} />
        </div>
      </div>
      <div style={{ fontSize: 32, fontWeight: 800, color: urgent && value > 0 ? color : "#0F172A" }}>
        {value}
      </div>
      {onClick && (
        <div style={{ display: "flex", alignItems: "center", gap: 4, marginTop: 8, fontSize: 12, color: ACCENT, fontWeight: 600 }}>
          View all <ArrowRight size={12} />
        </div>
      )}
    </div>
  )
}

type ScmTab = "dashboard" | "suppliers" | "purchase-orders" | "inventory" | "invoices"

export function ScmDashboard({ onNav }: { onNav: (tab: ScmTab) => void }) {
  const { data: summary } = useQuery<Summary>({
    queryKey: ["scm-summary"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/summary"); return r.data?.data ?? r.data },
    staleTime: 60_000,
  })

  const { data: recentPOs } = useQuery<{ content: PO[] }>({
    queryKey: ["scm-recent-pos"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/supply-chain/purchase-orders?size=6")
      const d = r.data?.data ?? r.data
      return Array.isArray(d) ? { content: d } : d
    },
    staleTime: 60_000,
  })

  const { data: lowStock } = useQuery<LowStockItem[]>({
    queryKey: ["scm-low-stock"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/supply-chain/inventory/low-stock")
      const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : []
    },
    staleTime: 60_000,
  })

  // NEW: same fix already applied in InventoryTab.tsx — fetched once,
  // used to show real item names instead of truncated UUIDs. A chart
  // labeled with raw UUIDs would be useless, so this is a prerequisite
  // for the low-stock chart below, not just a cosmetic fix.
  const { data: catalogueItems = [] } = useQuery<CatalogueItem[]>({
    queryKey: ["catalogue-items-all-dashboard"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/catalogue/items"); const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : [] },
    staleTime: 60_000,
  })
  const itemNameById = new Map(catalogueItems.map(i => [i.id, i.name]))

  const pos = recentPOs?.content ?? []
  const s = summary

  // NEW: dashboard trend chart — the one genuinely chartable dataset
  // without new backend work. Summary only returns current-moment
  // counts, not historical data, so a real spend/volume-over-time trend
  // isn't possible without a new aggregation endpoint; this is scoped
  // to what's actually available, not a placeholder pretending otherwise.
  const lowStockChartData = (lowStock ?? []).slice(0, 8).map(item => ({
    name: itemNameById.get(item.catalogueItemId) ?? `Item ${item.catalogueItemId.slice(0, 8)}…`,
    onHand: item.qtyOnHand,
    reorderPoint: item.reorderPoint,
    critical: item.qtyOnHand === 0,
  }))

  return (
    <div>
      {/* KPI grid */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 14, marginBottom: 28 }}>
        <KpiCard label="Active Suppliers"   value={s?.totalSuppliers ?? 0}      icon={Users}         color="#059669" bg="#DCFCE7" onClick={() => onNav("suppliers")} />
        <KpiCard label="Open Orders"        value={s?.openPurchaseOrders ?? 0}  icon={ShoppingCart}  color="#1D4ED8" bg="#DBEAFE" onClick={() => onNav("purchase-orders")} />
        <KpiCard label="Pending Invoices"   value={s?.pendingInvoices ?? 0}     icon={FileText}      color="#7C3AED" bg="#EDE9FE" onClick={() => onNav("invoices")} />
        <KpiCard label="Low Stock Items"    value={s?.lowStockItems ?? 0}       icon={Package}       color="#D97706" bg="#FEF3C7" onClick={() => onNav("inventory")} urgent />
        <KpiCard label="Overdue Invoices"   value={s?.overdueInvoices ?? 0}     icon={AlertTriangle} color="#DC2626" bg="#FEE2E2" onClick={() => onNav("invoices")} urgent />
        <KpiCard label="Ready for Payment"  value={s?.invoicesForApproval ?? 0} icon={CheckCircle}   color="#059669" bg="#DCFCE7" onClick={() => onNav("invoices")} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 380px", gap: 18 }}>
        {/* Recent Purchase Orders */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Recent Purchase Orders</div>
            <button onClick={() => onNav("purchase-orders")}
              style={{ fontSize: 12, color: ACCENT, fontWeight: 600, background: "none", border: "none", cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }}>
              View all <ArrowRight size={12} />
            </button>
          </div>
          {pos.length === 0
            ? <div style={{ textAlign: "center", padding: "40px 0", color: "#94A3B8" }}>
                <ShoppingCart size={32} style={{ opacity: .3, marginBottom: 8 }} />
                <div style={{ fontSize: 13 }}>No purchase orders yet</div>
              </div>
            : <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead>
                    <tr style={{ background: "#F8FAFC" }}>
                      {["Order #", "Supplier", "Amount", "Required By", "Status"].map(h => (
                        <th key={h} style={{ padding: "9px 14px", textAlign: "left", fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {pos.map((po, i) => {
                      const st = PO_STATUS[po.status] ?? PO_STATUS.DRAFT
                      return (
                        <tr key={po.id} style={{ borderTop: "1px solid #F1F5F9", background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                          <td style={{ padding: "10px 14px", fontSize: 12, fontWeight: 700, color: ACCENT }}>{po.orderNumber}</td>
                          <td style={{ padding: "10px 14px", fontSize: 13, color: "#0F172A" }}>{po.supplierName}</td>
                          <td style={{ padding: "10px 14px", fontSize: 13, fontWeight: 600 }}>{fmtR(po.totalAmount)}</td>
                          <td style={{ padding: "10px 14px", fontSize: 12, color: "#64748B" }}>{fmtD(po.requiredByDate)}</td>
                          <td style={{ padding: "10px 14px" }}>
                            <span style={{ background: st.bg, color: st.color, fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20 }}>
                              {st.label}
                            </span>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
          }
        </div>

        {/* Low Stock Panel */}
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Low Stock Alerts</div>
            <button onClick={() => onNav("inventory")}
              style={{ fontSize: 12, color: ACCENT, fontWeight: 600, background: "none", border: "none", cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }}>
              View all <ArrowRight size={12} />
            </button>
          </div>
          {!lowStock || lowStock.length === 0
            ? <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: "32px 20px", textAlign: "center", color: "#94A3B8" }}>
                <TrendingUp size={28} style={{ opacity: .3, marginBottom: 8 }} />
                <div style={{ fontSize: 13 }}>All stock levels healthy</div>
              </div>
            : <div style={{ border: "1px solid #FCD34D", borderRadius: 10, padding: "14px 10px 6px 6px", background: "#FFFBEB" }}>
                {/* NEW: dashboard trend chart — quantity on hand vs
                    reorder point per item, real item names (not
                    truncated UUIDs). Critical items (zero on hand)
                    render in red instead of amber, same distinction the
                    old list made via its "⚠" prefix. */}
                <ResponsiveContainer width="100%" height={Math.max(180, lowStockChartData.length * 34)}>
                  <BarChart data={lowStockChartData} layout="vertical" margin={{ top: 0, right: 16, bottom: 0, left: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#FDE68A" horizontal={false} />
                    <XAxis type="number" tick={{ fontSize: 10, fill: "#92400E" }} axisLine={false} tickLine={false} />
                    <YAxis type="category" dataKey="name" width={110}
                      tick={{ fontSize: 11, fill: "#78350F" }} axisLine={false} tickLine={false} />
                    <Tooltip
                      contentStyle={{ fontSize: 12, borderRadius: 8, border: "1px solid #FCD34D" }}
                      formatter={(value: number, key: string) => [value.toFixed(1), key === "onHand" ? "On Hand" : "Reorder Point"]}
                    />
                    <Bar dataKey="reorderPoint" fill="#FDE68A" radius={[0, 4, 4, 0]} barSize={8} name="Reorder Point" />
                    <Bar dataKey="onHand" radius={[0, 4, 4, 0]} barSize={8} name="On Hand">
                      {lowStockChartData.map((d, i) => (
                        <Cell key={i} fill={d.critical ? "#EF4444" : "#F59E0B"} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
          }
        </div>
      </div>
    </div>
  )
}
