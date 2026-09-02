// src/pages/warehousing/WhseClientDetail.tsx
//
// Sub-tab shell for one client's own scoped data — same shape as
// CollAgencyClientDetail.tsx. ADMIN-only actions inside the sub-tabs
// (record payment reversal-type actions, deletes) are NOT hidden
// client-side since this app's permission-check hook pattern isn't
// confirmed — the backend @PreAuthorize is the real gate, same note as
// every other provider-module build this session.
import { useState } from "react"
import { ArrowLeft, Package, Boxes, Truck, PackageCheck, FileText, UserPlus } from "lucide-react"
import { WHSE_ACCENT } from "./constants"
import WhseItemsTab from "./WhseItemsTab"
import WhseInventoryTab from "./WhseInventoryTab"
import WhseInboundShipmentsTab from "./WhseInboundShipmentsTab"
import WhseOutboundOrdersTab from "./WhseOutboundOrdersTab"
import WhseBillingInvoicesTab from "./WhseBillingInvoicesTab"
import WhsePortalAccessTab from "./WhsePortalAccessTab"

type SubTab = "items" | "inventory" | "inbound" | "outbound" | "billing" | "portal"

const SUB_TABS: { key: SubTab; label: string; icon: typeof Package }[] = [
  { key: "items", label: "Items", icon: Package },
  { key: "inventory", label: "Inventory", icon: Boxes },
  { key: "inbound", label: "Inbound Shipments", icon: Truck },
  { key: "outbound", label: "Outbound Orders", icon: PackageCheck },
  { key: "billing", label: "Billing Invoices", icon: FileText },
  { key: "portal", label: "Portal Access", icon: UserPlus },
]

export default function WhseClientDetail({ clientId, clientName, onBack }: { clientId: string; clientName: string; onBack: () => void }) {
  const [sub, setSub] = useState<SubTab>("items")

  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 14, padding: 0 }}>
        <ArrowLeft size={15} /> All clients
      </button>
      <h2 style={{ fontSize: 17, fontWeight: 800, color: "#0F172A", margin: "0 0 16px" }}>{clientName}</h2>

      <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 20, flexWrap: "wrap" }}>
        {SUB_TABS.map(t => {
          const Icon = t.icon
          const active = sub === t.key
          return (
            <button key={t.key} onClick={() => setSub(t.key)}
              style={{
                display: "flex", alignItems: "center", gap: 6, padding: "9px 13px", border: "none",
                background: "none", cursor: "pointer", fontSize: 12.5, fontWeight: 600,
                color: active ? WHSE_ACCENT : "#64748B",
                borderBottom: active ? `2px solid ${WHSE_ACCENT}` : "2px solid transparent",
                marginBottom: -1, whiteSpace: "nowrap",
              }}>
              <Icon size={13} /> {t.label}
            </button>
          )
        })}
      </div>

      {sub === "items" && <WhseItemsTab clientId={clientId} />}
      {sub === "inventory" && <WhseInventoryTab clientId={clientId} />}
      {sub === "inbound" && <WhseInboundShipmentsTab clientId={clientId} />}
      {sub === "outbound" && <WhseOutboundOrdersTab clientId={clientId} />}
      {sub === "billing" && <WhseBillingInvoicesTab clientId={clientId} />}
      {sub === "portal" && <WhsePortalAccessTab clientId={clientId} />}
    </div>
  )
}
