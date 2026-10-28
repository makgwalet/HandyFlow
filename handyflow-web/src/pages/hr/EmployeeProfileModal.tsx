// The full profile of one employee, opened from the employee list: family and career.
import { useState } from "react"
import { X } from "lucide-react"
import CareerPanel from "./CareerPanel"
import RelativesPanel from "./RelativesPanel"

interface Props {
  employeeId: string
  name: string
  startDate: string | null
  /** May see personal data (family details). */
  canSeeFamily: boolean
  /** May change employee records. */
  canEdit: boolean
  onClose: () => void
}

export default function EmployeeProfileModal({ employeeId, name, startDate, canSeeFamily, canEdit, onClose }: Props) {
  const [tab, setTab] = useState<"career" | "family">("career")
  const tabs: { id: "career" | "family"; label: string }[] = [{ id: "career", label: "Career" }, ...(canSeeFamily ? [{ id: "family" as const, label: "Family" }] : [])]
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100 }}>
      <div role="dialog" aria-label={`Profile of ${name}`} style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 24, width: 640, maxWidth: "95vw", maxHeight: "88vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
          <h3 style={{ margin: 0, fontSize: 18 }}>{name}</h3>
          <button onClick={onClose} aria-label="Close" style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
        </div>
        <div role="tablist" style={{ display: "flex", gap: 6, marginBottom: 14 }}>
          {tabs.map(t => (
            <button key={t.id} role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)}
              style={{ padding: "6px 14px", borderRadius: 8, border: "1px solid var(--hf-border)", cursor: "pointer", fontSize: 13, fontWeight: 600,
                background: tab === t.id ? "var(--hf-primary)" : "var(--hf-surface)", color: tab === t.id ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)" }}>{t.label}</button>
          ))}
        </div>
        {tab === "career" && <CareerPanel listPath={`/api/v1/hr/employees/${employeeId}/career`} cacheKey={`emp-${employeeId}`}
          addPath={canEdit ? `/api/v1/hr/employees/${employeeId}/career` : undefined} startDate={startDate} />}
        {tab === "family" && canSeeFamily && <RelativesPanel base={`/api/v1/hr/employees/${employeeId}/relatives`} cacheKey={`emp-${employeeId}`} canEdit={canEdit} />}
      </div>
    </div>
  )
}
