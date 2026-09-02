// src/pages/trainingprovider/TrainProvClientDetail.tsx
//
// Sub-tab shell for one client's own scoped data — same shape as
// WhseClientDetail.tsx / CollAgencyClientDetail.tsx.
import { useState } from "react"
import { ArrowLeft, Users, ClipboardList, FileText, UserPlus } from "lucide-react"
import { TRAINPROV_ACCENT } from "./constants"
import TrainProvDelegatesTab from "./TrainProvDelegatesTab"
import TrainProvEnrollmentsTab from "./TrainProvEnrollmentsTab"
import TrainProvBillingTab from "./TrainProvBillingTab"
import TrainProvPortalAccessTab from "./TrainProvPortalAccessTab"

type SubTab = "delegates" | "enrollments" | "billing" | "portal"

const SUB_TABS: { key: SubTab; label: string; icon: typeof Users }[] = [
  { key: "delegates", label: "Delegates", icon: Users },
  { key: "enrollments", label: "Enrollments", icon: ClipboardList },
  { key: "billing", label: "Billing", icon: FileText },
  { key: "portal", label: "Portal Access", icon: UserPlus },
]

export default function TrainProvClientDetail({ clientId, clientName, onBack }: { clientId: string; clientName: string; onBack: () => void }) {
  const [sub, setSub] = useState<SubTab>("delegates")

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
                color: active ? TRAINPROV_ACCENT : "#64748B",
                borderBottom: active ? `2px solid ${TRAINPROV_ACCENT}` : "2px solid transparent",
                marginBottom: -1, whiteSpace: "nowrap",
              }}>
              <Icon size={13} /> {t.label}
            </button>
          )
        })}
      </div>

      {sub === "delegates" && <TrainProvDelegatesTab clientId={clientId} />}
      {sub === "enrollments" && <TrainProvEnrollmentsTab clientId={clientId} />}
      {sub === "billing" && <TrainProvBillingTab clientId={clientId} />}
      {sub === "portal" && <TrainProvPortalAccessTab clientId={clientId} />}
    </div>
  )
}
