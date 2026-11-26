// Quality audits: the provider's own checklists, the audits run against them, and the corrective actions that follow.
import { useState } from "react"
import { SubTabs } from "./tpUi"
import TrainProvAuditsPanel from "./TrainProvAuditsPanel"
import TrainProvChecklistsPanel from "./TrainProvChecklistsPanel"

export default function TrainProvQualityTab() {
  const [section, setSection] = useState("audits")
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <SubTabs tabs={[["audits", "Audits"], ["checklists", "Checklists"]]} value={section} onChange={setSection} />
      {section === "audits" ? <TrainProvAuditsPanel /> : <TrainProvChecklistsPanel />}
    </div>
  )
}
