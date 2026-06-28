// src/pages/supply-chain/ScmDashboard.tsx
import { useQuery } from "@tanstack/react-query"
import { Truck, Users, ShoppingCart, Package, FileText, AlertTriangle, Clock, ArrowRight } from "lucide-react"
import { apiClient } from "../../api/client"
import { unwrap, fmtR, Banner, Spinner, type Summary, type SupplierInvoice, type InventoryItem, type ScmTab } from "./scm.shared"

export function ScmDashboard({ onNav }: { onNav: (t: ScmTab) => void }) {
  const { data: summary, isLoading } = useQuery<Summary>({
    queryKey: ["scm-summary"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/summary"); return r.data?.data ?? r.data ?? {} },
    staleTime: 30_000,
  })
  const { data: overdue = [] } = useQuery<SupplierInvoice[]>({
    queryKey: ["scm-invoices-overdue"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/supplier-invoices?status=APPROVED&size=5"); return unwrap<SupplierInvoice>(r).filter(i => i.overdue) },
    staleTime: 60_000,
  })
  const { data: lowStock = [] } = useQuery<InventoryItem[]>({
    queryKey: ["scm-low-stock"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/supply-chain/inventory/low-stock"); return unwrap<InventoryItem>(r) },
    staleTime: 60_000,
  })

  if (isLoading) return <Spinner />

  const KPIs = [
    { label: "Active Suppliers",    value: summary?.totalSuppliers ?? 0,       color: "#1D4ED8", bg: "#EFF6FF", tab: "suppliers" as ScmTab,       Icon: Users        },
    { label: "Open Purchase Orders", value: summary?.openPurchaseOrders ?? 0,  color: "#D97706", bg: "#FEF3C7", tab: "purchase-orders" as ScmTab,  Icon: ShoppingCart  },
    { label: "Pending Invoices",    value: summary?.pendingInvoices ?? 0,       color: "#7C3AED", bg: "#F5F3FF", tab: "invoices" as ScmTab,          Icon: FileText     },
    { label: "Low Stock Items",     value: summary?.lowStockItems ?? 0,         color: "#DC2626", bg: "#FEF2F2", tab: "inventory" as ScmTab,         Icon: Package      },
  ]

  return (
    <div>
      {/* KPI cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14, marginBottom: 28 }}>
        {KPIs.map(k => (
          <div key={k.label} onClick={() => onNav(k.tab)}
            style={{ background: k.bg, borderRadius: 12, padding: "18px 20px", cursor: "pointer", border: "1px solid transparent", transition: "box-shadow 0.15s" }}
            onMouseEnter={e => e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)"}
            onMouseLeave={e => e.currentTarget.style.boxShadow = "none"}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
              <span style={{ fontSize: 11, fontWeight: 600, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>{k.label}</span>
              <k.Icon size={16} color={k.color} />
            </div>
            <div style={{ fontSize: 30, fontWeight: 800, color: k.color }}>{k.value}</div>
          </div>
        ))}
      </div>

      {/* Alerts */}
      {!!summary?.overdueInvoices && (
        <Banner variant="error">{summary.overdueInvoices} overdue supplier invoice{summary.overdueInvoices !== 1 ? "s" : ""} — payment required</Banner>
      )}
      {!!summary?.invoicesForApproval && (
        <Banner variant="warning">{summary.invoicesForApproval} invoice{summary.invoicesForApproval !== 1 ? "s" : ""} awaiting approval</Banner>
      )}
      {!!summary?.lowStockItems && (
        <Banner variant="info">{summary.lowStockItems} item{summary.lowStockItems !== 1 ? "s" : ""} below reorder point</Banner>
      )}

      {/* Two-column: overdue invoices + low stock */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16, marginTop: 8 }}>

        {/* Overdue invoices */}
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ padding: "14px 18px", borderBottom: "1px solid #E2E8F0", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>Overdue Invoices</span>
            <button onClick={() => onNav("invoices")} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "#D97706", fontWeight: 600, display: "flex", alignItems: "center", gap: 4 }}>All invoices <ArrowRight size={12} /></button>
          </div>
          {overdue.length === 0 ? (
            <div style={{ padding: "24px 18px", fontSize: 13, color: "#94A3B8", textAlign: "center" }}>No overdue invoices</div>
          ) : overdue.map(inv => (
            <div key={inv.id} style={{ padding: "12px 18px", borderBottom: "1px solid #F1F5F9", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{inv.invoiceNumber ?? `SINV-${inv.id.slice(0, 6)}`}</div>
                <div style={{ fontSize: 11, color: "#94A3B8" }}>Due {new Date(inv.dueDate).toLocaleDateString("en-ZA")}</div>
              </div>
              <div style={{ fontSize: 14, fontWeight: 700, color: "#DC2626" }}>{fmtR(inv.totalAmount)}</div>
            </div>
          ))}
        </div>

        {/* Low stock */}
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <div style={{ padding: "14px 18px", borderBottom: "1px solid #E2E8F0", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>Low Stock Items</span>
            <button onClick={() => onNav("inventory")} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, color: "#D97706", fontWeight: 600, display: "flex", alignItems: "center", gap: 4 }}>View inventory <ArrowRight size={12} /></button>
          </div>
          {lowStock.length === 0 ? (
            <div style={{ padding: "24px 18px", fontSize: 13, color: "#94A3B8", textAlign: "center" }}>All items are adequately stocked</div>
          ) : lowStock.slice(0, 5).map(item => (
            <div key={item.id} style={{ padding: "12px 18px", borderBottom: "1px solid #F1F5F9", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ fontSize: 12, color: "#475569", fontFamily: "monospace" }}>{item.catalogueItemId.slice(0, 12)}…</div>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ fontSize: 13, fontWeight: 700, color: "#DC2626" }}>{item.qtyOnHand}</span>
                <span style={{ fontSize: 11, color: "#94A3B8" }}>/ {item.reorderPoint} min</span>
              </div>
            </div>
          ))}
        </div>

      </div>

      {/* Quick links */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 12, marginTop: 16 }}>
        {[
          { label: "Add Supplier",      sub: "Register a new supplier",             tab: "suppliers"       as ScmTab, Icon: Users        },
          { label: "New Purchase Order", sub: "Create a procurement order",         tab: "purchase-orders" as ScmTab, Icon: ShoppingCart  },
          { label: "Record Invoice",    sub: "Log a supplier invoice for payment",  tab: "invoices"        as ScmTab, Icon: FileText      },
        ].map(a => (
          <button key={a.label} onClick={() => onNav(a.tab)}
            style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "16px 18px", cursor: "pointer", display: "flex", alignItems: "center", gap: 14, textAlign: "left" }}>
            <div style={{ width: 38, height: 38, borderRadius: 9, background: "#FEF3C7", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
              <a.Icon size={17} color="#D97706" />
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{a.label}</div>
              <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 2 }}>{a.sub}</div>
            </div>
            <ArrowRight size={14} color="#CBD5E1" style={{ marginLeft: "auto", flexShrink: 0 }} />
          </button>
        ))}
      </div>
    </div>
  )
}
