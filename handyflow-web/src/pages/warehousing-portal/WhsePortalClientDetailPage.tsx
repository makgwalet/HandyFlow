// src/pages/warehousing-portal/WhsePortalClientDetailPage.tsx
//
// Read-only for the external client: stock position, inbound shipment
// status, outbound order status, and billing invoice history — confirmed
// as the full scope of WhsePortalDataController (no write endpoints on
// the portal side). Note: the portal side exposes no items-catalogue
// lookup endpoint (only staff-side WhseItemController does), so
// inventory rows show the item's raw id rather than its SKU — flagged
// here rather than guessing at an unconfirmed portal endpoint.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useParams, useNavigate } from "react-router-dom"
import { ArrowLeft, LogOut, Warehouse, Boxes, Truck, PackageCheck, FileText } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePortalAuthStore } from "../../store/portalAuth.store"

const ACCENT = "#0F766E"
const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)

interface InventoryResponse { id: string; itemId: string; locationId: string; qtyOnHand: number; qtyAllocated: number; available: number }
interface InboundShipmentResponse { id: string; referenceNumber: string | null; expectedDate: string | null; receivedDate: string | null; status: string }
interface OutboundOrderResponse { id: string; orderReference: string | null; shipToName: string | null; requestedShipDate: string | null; shippedDate: string | null; status: string; carrier: string | null; trackingNumber: string | null }
interface BillingInvoiceResponse { id: string; invoiceNumber: string; periodStart: string; periodEnd: string; dueDate: string; total: number; amountPaid: number; balance: number; status: string }

type SubTab = "inventory" | "inbound" | "outbound" | "billing"
const SUB_TABS: { key: SubTab; label: string; icon: typeof Boxes }[] = [
  { key: "inventory", label: "Inventory", icon: Boxes },
  { key: "inbound", label: "Inbound", icon: Truck },
  { key: "outbound", label: "Outbound", icon: PackageCheck },
  { key: "billing", label: "Billing", icon: FileText },
]

export function WhsePortalClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()
  const navigate = useNavigate()
  const portalAuth = usePortalAuthStore() as any
  const [sub, setSub] = useState<SubTab>("inventory")

  const { data: inventory = [], isLoading: invLoading } = useQuery<InventoryResponse[]>({
    queryKey: ["whse-portal-inventory", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/portal/clients/${clientId}/inventory`)).data,
    enabled: !!clientId && sub === "inventory",
  })
  const { data: inbound = [], isLoading: inboundLoading } = useQuery<InboundShipmentResponse[]>({
    queryKey: ["whse-portal-inbound", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/portal/clients/${clientId}/inbound-shipments`)).data,
    enabled: !!clientId && sub === "inbound",
  })
  const { data: outbound = [], isLoading: outboundLoading } = useQuery<OutboundOrderResponse[]>({
    queryKey: ["whse-portal-outbound", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/portal/clients/${clientId}/outbound-orders`)).data,
    enabled: !!clientId && sub === "outbound",
  })
  const { data: invoices = [], isLoading: invoicesLoading } = useQuery<BillingInvoiceResponse[]>({
    queryKey: ["whse-portal-invoices", clientId],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/portal/clients/${clientId}/billing-invoices`)).data,
    enabled: !!clientId && sub === "billing",
  })

  const logout = () => { portalAuth.logout?.(); navigate("/warehousing/portal/login") }

  return (
    <div style={{ minHeight: "100vh", background: "#F1F5F9", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <header style={{ background: "#fff", borderBottom: "1px solid #E2E8F0", padding: "16px 32px", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ width: 34, height: 34, borderRadius: 9, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Warehouse size={17} color="#fff" />
          </div>
          <p style={{ fontSize: 14, fontWeight: 800, color: "#0F172A", margin: 0 }}>Client Portal</p>
        </div>
        <button onClick={logout} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "1px solid #E2E8F0", borderRadius: 8, padding: "7px 14px", fontSize: 12.5, fontWeight: 600, color: "#64748B", cursor: "pointer" }}>
          <LogOut size={14} /> Sign out
        </button>
      </header>

      <main style={{ maxWidth: 860, margin: "0 auto", padding: "28px 24px" }}>
        <button onClick={() => navigate("/warehousing/portal")} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 18, padding: 0 }}>
          <ArrowLeft size={15} /> All accounts
        </button>

        <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 20 }}>
          {SUB_TABS.map(t => {
            const Icon = t.icon
            const active = sub === t.key
            return (
              <button key={t.key} onClick={() => setSub(t.key)}
                style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 14px", border: "none", background: "none", cursor: "pointer", fontSize: 12.5, fontWeight: 600, color: active ? ACCENT : "#64748B", borderBottom: active ? `2px solid ${ACCENT}` : "2px solid transparent", marginBottom: -1 }}>
                <Icon size={13} /> {t.label}
              </button>
            )
          })}
        </div>

        {sub === "inventory" && (
          invLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
          inventory.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No stock on hand.</p> : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {inventory.map((inv, i) => (
                <div key={inv.id} style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <span style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600 }}>Item {inv.itemId.slice(0, 8)}</span>
                  <span style={{ fontSize: 12.5, color: "#64748B" }}>{inv.qtyOnHand} on hand · {inv.available} available</span>
                </div>
              ))}
            </div>
          )
        )}

        {sub === "inbound" && (
          inboundLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
          inbound.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No inbound shipments.</p> : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {inbound.map((s, i) => (
                <div key={s.id} style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <span style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600 }}>{s.referenceNumber ?? `Shipment ${s.id.slice(0, 8)}`}</span>
                  <span style={{ fontSize: 11.5, color: "#64748B" }}>{s.status.replace(/_/g, " ")}{s.expectedDate ? ` · Expected ${s.expectedDate}` : ""}</span>
                </div>
              ))}
            </div>
          )
        )}

        {sub === "outbound" && (
          outboundLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
          outbound.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No outbound orders.</p> : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {outbound.map((o, i) => (
                <div key={o.id} style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <div>
                    <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{o.orderReference ?? `Order ${o.id.slice(0, 8)}`}</p>
                    {o.shipToName && <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>To {o.shipToName}</p>}
                  </div>
                  <span style={{ fontSize: 11.5, color: "#64748B" }}>{o.status}{o.trackingNumber ? ` · ${o.carrier ?? ""} #${o.trackingNumber}` : ""}</span>
                </div>
              ))}
            </div>
          )
        )}

        {sub === "billing" && (
          invoicesLoading ? <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p> :
          invoices.length === 0 ? <p style={{ color: "#94A3B8", fontSize: 13 }}>No invoices yet.</p> : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {invoices.map((inv, i) => (
                <div key={inv.id} style={{ display: "flex", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                  <div>
                    <p style={{ fontSize: 12.5, color: "#0F172A", fontWeight: 600, margin: 0 }}>{inv.invoiceNumber}</p>
                    <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{inv.periodStart} → {inv.periodEnd} · Due {inv.dueDate}</p>
                  </div>
                  <div style={{ textAlign: "right" }}>
                    <p style={{ fontSize: 12.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{fmtMoney(inv.total)}</p>
                    <p style={{ fontSize: 11, color: inv.balance > 0 ? "#D97706" : "#059669", margin: 0 }}>{inv.balance > 0 ? `${fmtMoney(inv.balance)} due` : "Paid"}</p>
                  </div>
                </div>
              ))}
            </div>
          )
        )}
      </main>
    </div>
  )
}
