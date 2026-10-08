// "Never hunt for clinical information": everything about the patient, beside the consultation, without leaving it.
// Collapsible. Reads the same briefing the patient overview uses (one cached call).
import { useState, type ReactNode } from "react"
import { useQuery } from "@tanstack/react-query"
import { ChevronRight, ChevronLeft } from "lucide-react"
import { apiClient } from "../../api/client"
import { bmi, fmtDay, type Briefing, type BriefingVisit } from "./briefing"

const unwrap = (r: any) => r.data?.data ?? r.data

/** The patient briefing; one cached call shared by the safety bar, this drawer and the patient overview. */
export function useBriefing(patientId: string) {
  return useQuery<Briefing>({
    queryKey: ["pf-briefing", patientId],
    queryFn: async () => {
      const d = unwrap(await apiClient.get(`/api/v1/clinic/patients/${patientId}/briefing`))
      if (!d || !Array.isArray(d.allergies)) throw new Error("Unexpected briefing")
      return d as Briefing
    },
  })
}

const H = ({ children }: { children: ReactNode }) =>
  <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--hf-text-muted)", margin: "14px 0 6px" }}>{children}</div>
const Muted = ({ children }: { children: ReactNode }) => <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{children}</div>

export default function ClinicalContextDrawer({ patientId, open, onToggle, billSlot, billSummary }: {
  patientId: string; open: boolean; onToggle: () => void
  /** The running bill, kept here so it never competes with the clinical pages. */
  billSlot?: ReactNode; billSummary?: string
}) {
  const [tab, setTab] = useState<"context" | "bill">("context")
  const [peek, setPeek] = useState<string | null>(null)
  const { data: b, isLoading, isError } = useBriefing(patientId)

  if (!open) return (
    <button type="button" onClick={onToggle} aria-label="Show clinical context" aria-expanded={false}
      style={{ alignSelf: "flex-start", display: "flex", alignItems: "center", gap: 4, padding: "10px 8px", cursor: "pointer", border: "1px solid var(--hf-border)",
        borderRadius: 10, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 12, fontWeight: 700, writingMode: "vertical-rl" }}>
      <ChevronLeft size={13} /> Clinical context
    </button>
  )

  const tabBtn = (id: "context" | "bill", label: string) => (
    <button type="button" role="tab" aria-selected={tab === id} onClick={() => setTab(id)}
      style={{ flex: 1, padding: "8px 6px", fontSize: 12, fontWeight: 700, cursor: "pointer", border: "none", background: "none",
        color: tab === id ? "var(--hf-accent-text)" : "var(--hf-text-muted)", borderBottom: `2px solid ${tab === id ? "var(--hf-accent)" : "transparent"}` }}>{label}</button>
  )

  return (
    <aside aria-label="Clinical context" style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "0 14px 14px", alignSelf: "flex-start", maxHeight: "calc(100vh - 150px)", overflowY: "auto" }}>
      <div style={{ position: "sticky", top: 0, background: "var(--hf-surface)", zIndex: 1, display: "flex", alignItems: "center", borderBottom: "1px solid var(--hf-border)" }}>
        <div role="tablist" style={{ display: "flex", flex: 1 }}>
          {tabBtn("context", "Clinical context")}
          {billSlot && tabBtn("bill", billSummary ? `Bill · ${billSummary}` : "Bill")}
        </div>
        <button type="button" onClick={onToggle} aria-label="Hide clinical context" aria-expanded
          style={{ border: "none", background: "none", cursor: "pointer", color: "var(--hf-text-muted)", padding: 6 }}><ChevronRight size={15} /></button>
      </div>

      {tab === "bill" ? <div style={{ marginTop: 12 }}>{billSlot}</div> : (<>
        {isLoading && <Muted>Loading the patient's history…</Muted>}
        {isError && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 12 }}>The history could not be loaded. The consultation still works.</div>}
        {b && (<>
          {b.alerts.length > 0 && <div style={{ marginTop: 12, display: "flex", flexDirection: "column", gap: 5 }}>
            {b.alerts.map(a => (
              <div key={a.code} role={a.severity === "DANGER" ? "alert" : undefined} style={{ fontSize: 12, fontWeight: 700, padding: "6px 9px", borderRadius: 8,
                background: a.severity === "DANGER" ? "var(--hf-danger-soft)" : a.severity === "WARNING" ? "var(--hf-warning-soft)" : "var(--hf-info-soft)",
                color: a.severity === "DANGER" ? "var(--hf-danger-text)" : a.severity === "WARNING" ? "var(--hf-warning-text)" : "var(--hf-info-text)" }}>{a.message}</div>))}
          </div>}

          <H>Allergies</H>
          {b.allergies.length === 0 ? <Muted>None recorded</Muted> : b.allergies.map(a => (
            <div key={a.id} style={{ fontSize: 13, color: "var(--hf-danger-text)", fontWeight: 700 }}>
              {a.allergen}<span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>{[a.severity?.toLowerCase().replace("_", " "), a.reaction].filter(Boolean).length ? " · " + [a.severity?.toLowerCase().replace("_", " "), a.reaction].filter(Boolean).join(" · ") : ""}</span>
            </div>))}

          <H>Conditions</H>
          {b.conditions.length === 0 ? <Muted>None recorded</Muted> : b.conditions.map(c => (
            <div key={c.id} style={{ fontSize: 13, color: "var(--hf-text)" }}>{c.conditionName}<span style={{ color: "var(--hf-text-muted)" }}>{c.status === "CONTROLLED" ? " · controlled" : ""}</span></div>))}

          <H>Current medicines</H>
          {b.medications.length === 0 ? <Muted>None recorded</Muted> : b.medications.map(m => (
            <div key={m.id} style={{ fontSize: 13, color: "var(--hf-text)" }}>{m.medicineName}<span style={{ color: "var(--hf-text-muted)" }}>{[m.dose, m.frequency].filter(Boolean).length ? " · " + [m.dose, m.frequency].filter(Boolean).join(" ") : ""}</span></div>))}

          <H>Last vitals{b.lastVitals ? ` · ${fmtDay(b.lastVitals.takenAt)}` : ""}</H>
          {b.lastVitals ? <VitalsLine v={b.lastVitals} /> : <Muted>None on record</Muted>}

          <H>Results</H>
          {b.labs.recent.length === 0 ? <Muted>No results on file</Muted> : (<>
            <div style={{ fontSize: 12, fontWeight: b.labs.unreviewed ? 700 : 400, color: b.labs.unreviewedCritical ? "var(--hf-danger-text)" : b.labs.unreviewed ? "var(--hf-warning-text)" : "var(--hf-text-muted)" }}>
              {b.labs.unreviewed === 0 ? "All reviewed" : `${b.labs.unreviewed} not yet reviewed${b.labs.unreviewedAbnormal ? ` · ${b.labs.unreviewedAbnormal} abnormal` : ""}${b.labs.unreviewedCritical ? ` · ${b.labs.unreviewedCritical} critical` : ""}`}
            </div>
            {b.labs.recent.slice(0, 4).map(l => (
              <div key={l.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--hf-text)" }}>
                <span>{fmtDay(l.at)}{l.reference ? ` · ${l.reference}` : ""}</span>
                <span style={{ fontWeight: 700, color: l.critical ? "var(--hf-danger-text)" : l.abnormal ? "var(--hf-warning-text)" : "var(--hf-text-muted)" }}>{l.critical ? "Critical" : l.abnormal ? "Abnormal" : "Normal"}</span>
              </div>))}
          </>)}

          <H>Previous visits</H>
          {b.recentVisits.length === 0 ? <Muted>No finished visits</Muted> : b.recentVisits.map(v => (
            <div key={v.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <button type="button" onClick={() => setPeek(peek === v.id ? null : v.id)} aria-expanded={peek === v.id}
                style={{ width: "100%", textAlign: "left", background: "none", border: "none", cursor: "pointer", padding: "7px 0", display: "flex", gap: 8, fontSize: 12, color: "var(--hf-text)" }}>
                <span style={{ width: 78, flexShrink: 0, color: "var(--hf-text-muted)" }}>{fmtDay(v.at)}</span>
                <b style={{ fontWeight: 600 }}>{v.diagnosis ?? v.chiefComplaint ?? "No diagnosis recorded"}</b>
              </button>
              {peek === v.id && <VisitPeek v={v} />}
            </div>))}
        </>)}
      </>)}
    </aside>
  )
}

function VisitPeek({ v }: { v: BriefingVisit }) {
  const row = (k: string, val?: string | null) => val ? <div style={{ fontSize: 12, color: "var(--hf-text)" }}><span style={{ color: "var(--hf-text-muted)" }}>{k}: </span>{val}</div> : null
  return (
    <div style={{ padding: "0 0 8px 86px", display: "flex", flexDirection: "column", gap: 2 }}>
      {row("Reason", v.chiefComplaint)}{row("Diagnosis", v.diagnosis)}{row("ICD-10", v.icd10Codes.join(", "))}
      {row("Seen by", v.practitionerName ? `Dr ${v.practitionerName}` : null)}
      {row("Follow-up", v.followUpDays != null ? `in ${v.followUpDays} days` : null)}
    </div>
  )
}

function VitalsLine({ v }: { v: NonNullable<Briefing["lastVitals"]> }) {
  const b = bmi(v.weightKg, v.heightCm)
  const parts = [v.bloodPressure && `BP ${v.bloodPressure}`, v.pulseBpm != null && `Pulse ${v.pulseBpm}`, v.temperatureC != null && `Temp ${v.temperatureC}°C`,
    v.oxygenSatPct != null && `SpO₂ ${v.oxygenSatPct}%`, v.weightKg != null && `${v.weightKg} kg`, b != null && `BMI ${b}`].filter(Boolean) as string[]
  return <div style={{ fontSize: 13, color: "var(--hf-text)" }}>{parts.join(" · ") || <Muted>Nothing recorded</Muted>}</div>
}
