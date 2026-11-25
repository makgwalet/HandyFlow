// Regulatory reporting. Obligations are set up by the provider; periods are the reporting windows under them.
import { useState } from "react"
import { SubTabs } from "./tpUi"
import TrainProvObligationsPanel from "./TrainProvObligationsPanel"
import TrainProvPeriodsPanel from "./TrainProvPeriodsPanel"

export default function TrainProvReportingTab() {
  const [section, setSection] = useState("periods")
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <SubTabs tabs={[["periods", "Reporting periods"], ["obligations", "Obligations"]]} value={section} onChange={setSection} />
      {section === "periods" ? <TrainProvPeriodsPanel /> : <TrainProvObligationsPanel />}
    </div>
  )
}
