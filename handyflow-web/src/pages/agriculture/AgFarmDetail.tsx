// src/pages/agriculture/AgFarmDetail.tsx
//
// Farm-scoped shell: Animals | Groups | Production Areas | Enterprises | Inventory | Crop cycles | Seasons | Costs. Same sub-tab shell pattern as WhseClientDetail/
// TrainProvClientDetail from earlier modules this engagement, drilling
// one level further into Animal/Group detail when a row is clicked.
import type React from "react"
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ArrowLeft, Tractor } from "lucide-react"
import { AG_ACCENT } from "./constants"
import { AgTabs } from "./agUi"
import type { FarmResponse } from "./AgFarmsTab"
import AgProductionAreasTab from "./AgProductionAreasTab"
import AgEnterprisesTab from "./AgEnterprisesTab"
import AgAnimalsTab, { type AnimalResponse } from "./AgAnimalsTab"
import AgAnimalDetail from "./AgAnimalDetail"
import AgGroupsTab, { type GroupResponse } from "./AgGroupsTab"
import AgGroupDetail from "./AgGroupDetail"
import AgInventoryTab from "./AgInventoryTab"
import AgCropCyclesTab from "./AgCropCyclesTab"
import AgSeasonsTab from "./AgSeasonsTab"
import AgCostReportsTab from "./AgCostReportsTab"
import AgRegistersTab from "./AgRegistersTab"
import AgCalendarTab from "./AgCalendarTab"
import AgFarmSetup from "./AgFarmSetup"
import type { FarmLink } from "./agDeepLink.logic"

type Tab = "calendar" | "areas" | "enterprises" | "animals" | "groups" | "inventory" | "crops" | "seasons" | "costs" | "registers"
const TABS: { key: Tab; label: string }[] = [
  { key: "animals", label: "Animals" }, { key: "groups", label: "Groups" }, { key: "calendar", label: "Calendar" },
  { key: "areas", label: "Production Areas" }, { key: "enterprises", label: "Enterprises" }, { key: "inventory", label: "Inventory" },
  { key: "crops", label: "Crop cycles" }, { key: "seasons", label: "Seasons" }, { key: "costs", label: "Costs" }, { key: "registers", label: "Registers" },
]

/** `link` opens the farm on a tab, or straight on an animal or group (a notification's link). It is only where the page starts. */
export default function AgFarmDetail({ farm, onBack, link }: { farm: FarmResponse; onBack: () => void; link?: FarmLink | null }) {
  const [tab, setTab] = useState<Tab>(link?.tab ?? "animals")
  const [selectedAnimal, setSelectedAnimal] = useState<AnimalResponse | null>(null)
  const [selectedGroup, setSelectedGroup] = useState<GroupResponse | null>(null)
  // the animal or group a link named, until the person goes back. If it no longer exists the farm simply opens.
  const [linkedAnimalId, setLinkedAnimalId] = useState(link?.animalId ?? null)
  const [linkedGroupId, setLinkedGroupId] = useState(link?.groupId ?? null)
  const { data: linkedAnimal } = useQuery<AnimalResponse>({
    queryKey: ["ag-animal", linkedAnimalId], enabled: !!linkedAnimalId, retry: false,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/animals/${linkedAnimalId}`)).data,
  })
  const { data: linkedGroup } = useQuery<GroupResponse>({
    queryKey: ["ag-group", linkedGroupId], enabled: !!linkedGroupId, retry: false,
    queryFn: async () => (await apiClient.get(`/api/v1/agriculture/groups/${linkedGroupId}`)).data,
  })
  const animal = selectedAnimal ?? (linkedAnimal && linkedAnimal.farmId === farm.id ? linkedAnimal : null)
  const group = selectedGroup ?? (linkedGroup && linkedGroup.farmId === farm.id ? linkedGroup : null)

  if (animal) return <AgAnimalDetail animal={animal} onBack={() => { setSelectedAnimal(null); setLinkedAnimalId(null) }} />
  if (group) return <AgGroupDetail group={group} onBack={() => { setSelectedGroup(null); setLinkedGroupId(null) }} />

  return (
    <div>
      <button onClick={onBack} style={backBtn}><ArrowLeft size={13} style={{ marginRight: 5, verticalAlign: -2 }} />Back to farms</button>

      <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 10, marginBottom: 18 }}>
        <div style={{ width: 40, height: 40, borderRadius: 10, background: AG_ACCENT, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <Tractor size={19} style={{ color: 'var(--hf-text-on-solid)' }} />
        </div>
        <div>
          <h2 style={{ fontSize: 17, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{farm.name}</h2>
          <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>{farm.farmType}{farm.province ? ` · ${farm.province}` : ""}{farm.totalHectares ? ` · ${farm.totalHectares} ha` : ""}{farm.managerName ? ` · Manager: ${farm.managerName}` : ""}</p>
        </div>
      </div>

      <AgFarmSetup farmId={farm.id} activeTab={tab} onOpenTab={setTab} />

      <AgTabs label="Farm sections" tabs={TABS} active={tab} onChange={setTab} spacing={20} />

      {tab === "animals" && <AgAnimalsTab farmId={farm.id} onSelectAnimal={setSelectedAnimal} />}
      {tab === "groups" && <AgGroupsTab farmId={farm.id} onSelectGroup={setSelectedGroup} />}
      {tab === "calendar" && <AgCalendarTab farmId={farm.id} onOpenAnimal={setLinkedAnimalId} onOpenGroup={setLinkedGroupId} onOpenCrops={() => setTab("crops")} />}
      {tab === "areas" && <AgProductionAreasTab farmId={farm.id} />}
      {tab === "enterprises" && <AgEnterprisesTab farmId={farm.id} />}
      {tab === "inventory" && <AgInventoryTab farmId={farm.id} />}
      {tab === "crops" && <AgCropCyclesTab farmId={farm.id} />}
      {tab === "seasons" && <AgSeasonsTab farmId={farm.id} />}
      {tab === "costs" && <AgCostReportsTab farmId={farm.id} />}
      {tab === "registers" && <AgRegistersTab farm={farm} />}
    </div>
  )
}

const backBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", background: "none", border: "none", color: "var(--hf-text-muted)", fontSize: 12.5, fontWeight: 600, cursor: "pointer", padding: 0 }
