// src/pages/debtcollection/DebtCollectionPage.tsx
//
// Module 2a (Debt Collection — Internal). Confirmed against the real
// synced backend: za.co.handyflow.platform.debtcollection, present in the
// GitHub sync already (no push-gap this time, unlike Module 1's
// legalcompliance). Two controllers — DebtCollectionCaseController
// (/api/v1/debtcollection/cases/...) and PaymentPlanController
// (/api/v1/debtcollection/payment-plans/{id}/...) — every DTO, enum, and
// entity method read directly from source.
//
// Internal-only — no portal for this sub-module (the outsourced
// Collections Agency sibling, which DOES get a portal, is Module 2b, a
// separate build).
import { useState } from "react"
import { LayoutDashboard, Landmark, Scale } from "lucide-react"
import DebtCollectionDashboard from "./DebtCollectionDashboard"
import CasesTab from "./CasesTab"

type Tab = "dashboard" | "cases"

const TABS: { id: Tab; label: string; icon: React.ElementType }[] = [
  { id: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { id: "cases",     label: "Cases",     icon: Landmark        },
]

// Distinct from legalcompliance's indigo (#4338CA) — a rust/amber tone
// evokes "overdue, needs recovery" without colliding with any existing
// module tile (fuel/marketing already use amber-600, this is a darker,
// more "collections" rust).
const ACCENT = "#9A3412"

export function DebtCollectionPage() {
  const [tab, setTab] = useState<Tab>("dashboard")

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ marginBottom: 24 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Scale size={18} color="#fff" />
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: "#0F172A", margin: 0 }}>Debt Collection</h1>
        </div>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0, paddingLeft: 46 }}>
          Internal collection cases · Contact trail · Structured payment plans · Demand letters
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
                  borderBottom: active ? `2px solid ${ACCENT}` : "2px solid transparent",
                  color: active ? ACCENT : "#64748B",
                  fontWeight: active ? 600 : 400, fontSize: 13, cursor: "pointer",
                  marginBottom: -1,
                }}>
                <Icon size={14} />{t.label}
              </button>
            )
          })}
        </div>

        {tab === "dashboard" && <DebtCollectionDashboard onNavigate={setTab} />}
        {tab === "cases"     && <CasesTab />}
      </div>
    </div>
  )
}
