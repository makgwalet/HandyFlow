// src/pages/collectionsagency/CollectionsAgencyPage.tsx
//
// Module 2b (Collections Agency — outsourced third-party debt collector).
// Confirmed against the real synced backend:
// za.co.handyflow.platform.collectionsagency — 9 controllers, 27 DTOs,
// every field read directly from source before this was written.
//
// Sibling of Module 2a (debtcollection, internal-only) but a genuinely
// separate module: no dependency between them in either direction. This
// module additionally has a client-facing portal (see
// ../collectionsagency-portal/) — the agency's own creditor clients log
// in to see their placed portfolio and trust/remittance statement.
//
// Same shell pattern as DebtCollectionPage.tsx: thin tab shell, inline
// styles, one ACCENT constant, each tab's real content in its own file.
import { useState } from "react"
import { LayoutDashboard, Handshake, Users, Gavel, UserCog } from "lucide-react"
import CollAgencyDashboard from "./CollAgencyDashboard"
import CollAgencyClientsTab from "./CollAgencyClientsTab"
import CollAgencyCollectorsTab from "./CollAgencyCollectorsTab"
import CollAgencyProfileTab from "./CollAgencyProfileTab"
import { CA_ACCENT } from "./constants"

type Tab = "dashboard" | "clients" | "collectors" | "profile"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard",  label: "Dashboard",  icon: LayoutDashboard },
  { id: "clients",    label: "Clients",    icon: Users           },
  { id: "collectors", label: "Collectors", icon: UserCog         },
  { id: "profile",    label: "Agency Profile", icon: Gavel       },
]

// Accent color: distinct from both legalcompliance's indigo (#4338CA)
// and debtcollection's rust (#9A3412) — this is the third-party agency
// sibling, so a deep violet keeps it visually distinct from both while
// staying in the same "serious/compliance" family. Handshake icon for
// the same reason: this module's whole shape is a third party acting on
// behalf of creditor clients, unlike debtcollection's own direct
// creditor-debtor relationship. See ./constants.ts for CA_ACCENT itself
// — pulled out to a standalone file to avoid a circular import (see
// that file's own comment).

export function CollectionsAgencyPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: CA_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Handshake size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Collections Agency</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Creditor client portfolios · Placement & recovery · Trust ledger · Commission billing
        </p>
      </div>

      <div style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: 24 }}>
        <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #E2E8F0", marginBottom: 28, overflowX: "auto" }}>
          {TABS.map(t => {
            const Icon = t.icon
            const active = tab === t.id
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                style={{
                  display: "flex", alignItems: "center", gap: 6, padding: "10px 16px",
                  background: "none", border: "none", whiteSpace: "nowrap",
                  borderBottom: active ? `2px solid ${CA_ACCENT}` : "2px solid transparent",
                  color: active ? CA_ACCENT : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer",
                  marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard"  && <CollAgencyDashboard onNavigate={setTab} />}
        {tab === "clients"    && <CollAgencyClientsTab />}
        {tab === "collectors" && <CollAgencyCollectorsTab />}
        {tab === "profile"    && <CollAgencyProfileTab />}
      </div>
    </div>
  )
}
