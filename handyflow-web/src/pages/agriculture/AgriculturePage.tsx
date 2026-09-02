// src/pages/agriculture/AgriculturePage.tsx
//
// Module 7 (Agriculture) — Farm Foundation + Livestock delivery (mirrors
// the backend's own Increment 1 scope: Farms, Species, Production Areas,
// Enterprises, Animals, Groups, Inventory, and all six Livestock history
// sub-resources + evidence). Crops and Cost Reporting views are a
// follow-up delivery, matching the backend's own Increment 2 + cost
// reporting rollout — not built yet.
//
// A third platform shape alongside every prior module this engagement:
// no external clients, no client portal — the tenant runs its own farms
// directly, structurally closest to earthmoving/fleet. Confirmed against
// za.co.handyflow.platform.agriculture's 7 Increment-1 controllers.
import { useState } from "react"
import { LayoutDashboard, Tractor, PawPrint } from "lucide-react"
import { AG_ACCENT } from "./constants"
import AgDashboard from "./AgDashboard"
import AgFarmsTab, { type FarmResponse } from "./AgFarmsTab"
import AgFarmDetail from "./AgFarmDetail"
import AgSpeciesTab from "./AgSpeciesTab"

type Tab = "dashboard" | "farms" | "species"
const TABS: { key: Tab; label: string; icon: typeof LayoutDashboard }[] = [
  { key: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { key: "farms", label: "Farms", icon: Tractor },
  { key: "species", label: "Species", icon: PawPrint },
]

export default function AgriculturePage() {
  const [tab, setTab] = useState<Tab>("dashboard")
  const [selectedFarm, setSelectedFarm] = useState<FarmResponse | null>(null)

  return (
    <div style={{ minHeight: "100vh", background: "#F8FAFC", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <div style={{ maxWidth: 1200, margin: "0 auto", padding: "28px 24px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 22 }}>
          <div style={{ width: 40, height: 40, borderRadius: 11, background: AG_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Tractor size={20} color="#fff" />
          </div>
          <div>
            <h1 style={{ fontSize: 19, fontWeight: 800, color: "#0F172A", margin: 0 }}>Agriculture</h1>
            <p style={{ fontSize: 12.5, color: "#94A3B8", margin: 0 }}>Farms · Species catalogue · Livestock — animals &amp; groups · Inventory</p>
          </div>
        </div>

        {selectedFarm ? (
          <AgFarmDetail farm={selectedFarm} onBack={() => setSelectedFarm(null)} />
        ) : (
          <>
            <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 24, overflowX: "auto" }}>
              {TABS.map(t => {
                const Icon = t.icon
                const active = tab === t.key
                return (
                  <button key={t.key} onClick={() => setTab(t.key)}
                    style={{
                      display: "flex", alignItems: "center", gap: 7, padding: "10px 16px", border: "none",
                      background: "none", cursor: "pointer", fontSize: 13, fontWeight: 600, whiteSpace: "nowrap",
                      color: active ? AG_ACCENT : "#64748B",
                      borderBottom: active ? `2px solid ${AG_ACCENT}` : "2px solid transparent",
                      marginBottom: -1,
                    }}>
                    <Icon size={15} /> {t.label}
                  </button>
                )
              })}
            </div>

            {tab === "dashboard" && <AgDashboard />}
            {tab === "farms" && <AgFarmsTab onSelectFarm={setSelectedFarm} />}
            {tab === "species" && <AgSpeciesTab />}
          </>
        )}
      </div>
    </div>
  )
}
