// src/pages/agriculture/AgFarmDetail.tsx
//
// Farm-scoped shell: Production Areas | Enterprises | Animals | Groups |
// Inventory. Same sub-tab shell pattern as WhseClientDetail/
// TrainProvClientDetail from earlier modules this engagement, drilling
// one level further into Animal/Group detail when a row is clicked.
import type React from "react"
import { useState } from "react"
import { ArrowLeft, Tractor } from "lucide-react"
import { AG_ACCENT } from "./constants"
import type { FarmResponse } from "./AgFarmsTab"
import AgProductionAreasTab from "./AgProductionAreasTab"
import AgEnterprisesTab from "./AgEnterprisesTab"
import AgAnimalsTab, { type AnimalResponse } from "./AgAnimalsTab"
import AgAnimalDetail from "./AgAnimalDetail"
import AgGroupsTab, { type GroupResponse } from "./AgGroupsTab"
import AgGroupDetail from "./AgGroupDetail"
import AgInventoryTab from "./AgInventoryTab"

type Tab = "areas" | "enterprises" | "animals" | "groups" | "inventory"
const TABS: { key: Tab; label: string }[] = [
  { key: "animals", label: "Animals" }, { key: "groups", label: "Groups" },
  { key: "areas", label: "Production Areas" }, { key: "enterprises", label: "Enterprises" }, { key: "inventory", label: "Inventory" },
]

export default function AgFarmDetail({ farm, onBack }: { farm: FarmResponse; onBack: () => void }) {
  const [tab, setTab] = useState<Tab>("animals")
  const [selectedAnimal, setSelectedAnimal] = useState<AnimalResponse | null>(null)
  const [selectedGroup, setSelectedGroup] = useState<GroupResponse | null>(null)

  if (selectedAnimal) return <AgAnimalDetail animal={selectedAnimal} onBack={() => setSelectedAnimal(null)} />
  if (selectedGroup) return <AgGroupDetail group={selectedGroup} onBack={() => setSelectedGroup(null)} />

  return (
    <div>
      <button onClick={onBack} style={backBtn}><ArrowLeft size={13} style={{ marginRight: 5, verticalAlign: -2 }} />Back to farms</button>

      <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 10, marginBottom: 18 }}>
        <div style={{ width: 40, height: 40, borderRadius: 10, background: AG_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Tractor size={19} color="#fff" />
        </div>
        <div>
          <h2 style={{ fontSize: 17, fontWeight: 800, color: "#0F172A", margin: 0 }}>{farm.name}</h2>
          <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>{farm.farmType}{farm.province ? ` · ${farm.province}` : ""}{farm.totalHectares ? ` · ${farm.totalHectares} ha` : ""}{farm.managerName ? ` · Manager: ${farm.managerName}` : ""}</p>
        </div>
      </div>

      <div style={{ display: "flex", gap: 4, borderBottom: "1px solid #E2E8F0", marginBottom: 20, overflowX: "auto" }}>
        {TABS.map(t => {
          const active = tab === t.key
          return (
            <button key={t.key} onClick={() => setTab(t.key)}
              style={{ padding: "10px 16px", border: "none", background: "none", cursor: "pointer", fontSize: 13, fontWeight: 600, whiteSpace: "nowrap",
                color: active ? AG_ACCENT : "#64748B", borderBottom: active ? `2px solid ${AG_ACCENT}` : "2px solid transparent", marginBottom: -1 }}>
              {t.label}
            </button>
          )
        })}
      </div>

      {tab === "animals" && <AgAnimalsTab farmId={farm.id} onSelectAnimal={setSelectedAnimal} />}
      {tab === "groups" && <AgGroupsTab farmId={farm.id} onSelectGroup={setSelectedGroup} />}
      {tab === "areas" && <AgProductionAreasTab farmId={farm.id} />}
      {tab === "enterprises" && <AgEnterprisesTab farmId={farm.id} />}
      {tab === "inventory" && <AgInventoryTab farmId={farm.id} />}
    </div>
  )
}

const backBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", background: "none", border: "none", color: "#64748B", fontSize: 12.5, fontWeight: 600, cursor: "pointer", padding: 0 }
