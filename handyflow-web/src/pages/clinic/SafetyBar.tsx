// The top of the consultation workspace: who the patient is and what must not be missed, before anything else.
// Allergies are red, conditions amber, everything else quiet. Billing is deliberately not here.
import { AlertTriangle, FlaskConical, Pill } from "lucide-react"
import { saId } from "./patientFile.shared"
import type { Briefing } from "./briefing"
import type { ReactNode } from "react"

interface Props {
  patient: { fullName: string; idNumber?: string; allergies?: string[] }
  briefing?: Briefing
  /** e.g. "Consultation · Cough". */
  visit: string
  /** Draft / In progress / Ready to sign / … */
  stateLabel: string
  timer: string
  saveText: string
  /** Buttons on the right (drawer toggle, review, leave, discard). */
  children?: ReactNode
}

const chip = (tone: "danger" | "warn" | "quiet"): React.CSSProperties => ({
  display: "inline-flex", alignItems: "center", gap: 5, padding: "3px 10px", borderRadius: 20, fontSize: 12, fontWeight: 700,
  background: tone === "danger" ? "var(--hf-danger-soft)" : tone === "warn" ? "var(--hf-warning-soft)" : "var(--hf-surface-sunken)",
  color: tone === "danger" ? "var(--hf-danger-text)" : tone === "warn" ? "var(--hf-warning-text)" : "var(--hf-text-secondary)",
  border: `1px solid ${tone === "danger" ? "var(--hf-danger-border)" : tone === "warn" ? "var(--hf-warning-border)" : "var(--hf-border)"}`,
})

export default function SafetyBar({ patient, briefing, visit, stateLabel, timer, saveText, children }: Props) {
  const info = saId(patient.idNumber)
  const initials = patient.fullName.split(" ").map(n => n[0]).join("").slice(0, 2)
  const allergies = briefing ? briefing.allergies.map(a => a.allergen) : (patient.allergies ?? [])
  const conditions = briefing?.conditions ?? []
  const meds = briefing?.medications.length ?? 0
  const critical = briefing?.labs.unreviewedCritical ?? 0
  const unreviewed = briefing?.labs.unreviewed ?? 0

  return (
    <header aria-label="Patient safety bar" style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "12px 16px",
      display: "flex", gap: 14, alignItems: "center", flexWrap: "wrap" }}>
      <div aria-hidden="true" style={{ width: 44, height: 44, borderRadius: "50%", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)",
        display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 16, flexShrink: 0 }}>{initials}</div>

      <div style={{ flex: "1 1 320px", minWidth: 0 }}>
        <div style={{ display: "flex", gap: 10, alignItems: "baseline", flexWrap: "wrap" }}>
          <h1 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: "var(--hf-text)" }}>{patient.fullName}</h1>
          {info && <span style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{info.age} yrs · {info.gender}</span>}
          <span style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{visit}</span>
        </div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }}>
          {allergies.length > 0
            ? allergies.map(a => <span key={a} style={chip("danger")}><AlertTriangle size={11} aria-hidden="true" /> Allergy: {a}</span>)
            : briefing && <span style={chip("quiet")}>No allergies recorded</span>}
          {conditions.slice(0, 3).map(c => <span key={c.id} style={chip("warn")}>{c.conditionName}</span>)}
          {conditions.length > 3 && <span style={chip("quiet")}>+{conditions.length - 3} more conditions</span>}
          {briefing && <span style={chip("quiet")}><Pill size={11} aria-hidden="true" /> {meds === 0 ? "No current medicines" : `${meds} current medicine${meds === 1 ? "" : "s"}`}</span>}
          {critical > 0
            ? <span style={chip("danger")}><FlaskConical size={11} aria-hidden="true" /> {critical} critical result{critical === 1 ? "" : "s"} to review</span>
            : unreviewed > 0 && <span style={chip("warn")}><FlaskConical size={11} aria-hidden="true" /> {unreviewed} result{unreviewed === 1 ? "" : "s"} to review</span>}
        </div>
      </div>

      <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 2, minWidth: 130 }}>
        <span data-testid="lifecycle" style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-accent-text)" }}>{stateLabel}</span>
        <span style={{ fontSize: 18, fontWeight: 800, fontVariantNumeric: "tabular-nums", color: "var(--hf-text)" }}>{timer}</span>
        <span role="status" title="Notes are saved to the server as you type" style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{saveText}</span>
      </div>
      {children && <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>{children}</div>}
    </header>
  )
}
