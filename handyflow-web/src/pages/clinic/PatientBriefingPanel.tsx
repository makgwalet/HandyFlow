// The "before you walk in" view: everything a clinician needs about the patient at a glance.
// One server call (GET /patients/{id}/briefing); starting the consultation lives here too.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertTriangle, CalendarClock, CalendarPlus, FlaskConical, History, Info, Pill, PlayCircle, Stethoscope } from "lucide-react"
import { apiClient } from "../../api/client"
import { ago, bmi, fmtDay, fmtTimeOfDay, recallText, startPlan, type Briefing, type BriefingVisit } from "./briefing"

const unwrap = (r: any) => r.data?.data ?? r.data

const SEVERITY = {
  DANGER:  { bg: "var(--hf-danger-soft)",  fg: "var(--hf-danger-text)",  border: "var(--hf-danger-border)", Icon: AlertTriangle },
  WARNING: { bg: "var(--hf-warning-soft)", fg: "var(--hf-warning-text)", border: "var(--hf-warning-border)", Icon: AlertTriangle },
  INFO:    { bg: "var(--hf-info-soft)",    fg: "var(--hf-info-text)",    border: "var(--hf-info-soft-strong)", Icon: Info },
} as const

export default function PatientBriefingPanel({ patientId, appointments, defaultPractitionerId, onStartSession, onOpenTab }: {
  patientId: string
  /** The patient's appointments (already loaded by the patient file). */
  appointments: any[]
  /** The logged-in practitioner, used when a walk-in has to be created. */
  defaultPractitionerId?: string
  onStartSession: (appt: any) => void
  onOpenTab?: (tab: "history" | "labs" | "appointments" | "rx") => void
}) {
  const qc = useQueryClient()
  const [error, setError] = useState("")
  const { data: b, isLoading, isError } = useQuery<Briefing>({
    queryKey: ["pf-briefing", patientId],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patientId}/briefing`)),
  })

  const plan = startPlan(appointments, new Date())
  const resuming = plan.kind === "resume" || !!b?.openDraft

  const begin = useMutation({
    mutationFn: async () => {
      let appt = plan.appt
      if (!appt) {
        const res = await apiClient.post("/api/v1/clinic/appointments", {
          patientId, practitionerId: defaultPractitionerId || null, scheduledAt: new Date().toISOString(),
          durationMinutes: 30, appointmentType: "CONSULTATION", reason: "Walk-in",
        })
        appt = unwrap(res)
      }
      for (const step of plan.steps) appt = unwrap(await apiClient.post(`/api/v1/clinic/appointments/${appt!.id}/${step}`)) ?? appt
      return appt
    },
    onSuccess: appt => {
      setError("")
      qc.invalidateQueries({ queryKey: ["pf-appointments", patientId] })
      qc.invalidateQueries({ queryKey: ["pf-briefing", patientId] })
      onStartSession(appt)
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Could not start the consultation"),
  })

  if (isLoading) return <div style={{ ...card, color: "var(--hf-text-muted)", fontSize: 13 }}>Loading the patient briefing…</div>
  if (isError || !b) return <div role="alert" style={{ ...card, color: "var(--hf-danger-text)", fontSize: 13 }}>The briefing could not be loaded. The rest of the file is still available below.</div>

  const last = b.lastVisit
  const when = ago(b.daysSinceLastVisit)
  const recall = recallText(b.recall)

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14, marginBottom: 22 }}>
      {/* Alerts first: what must not be missed */}
      {b.alerts.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {b.alerts.map(a => {
            const s = SEVERITY[a.severity] ?? SEVERITY.INFO
            return (
              <div key={a.code} role={a.severity === "DANGER" ? "alert" : undefined}
                style={{ display: "flex", alignItems: "center", gap: 8, padding: "9px 14px", borderRadius: 10, background: s.bg, color: s.fg, border: `1px solid ${s.border}`, fontSize: 13, fontWeight: 600 }}>
                <s.Icon size={15} /> {a.message}
              </div>
            )
          })}
        </div>
      )}

      {/* Headline row + the one primary action */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr)) auto", gap: 12, alignItems: "stretch" }}>
        <Tile label="Last visit" icon={<History size={13} />}
          main={when ?? "First visit"}
          sub={last ? `${fmtDay(last.at)}${last.practitionerName ? ` · Dr ${last.practitionerName}` : ""}` : "No finished visits on record"} />
        <Tile label="Next appointment" icon={<CalendarClock size={13} />}
          main={b.nextAppointment ? fmtDay(b.nextAppointment.at) : "Nothing booked"}
          sub={b.nextAppointment ? `${fmtTimeOfDay(b.nextAppointment.at)}${b.nextAppointment.practitionerName ? ` · Dr ${b.nextAppointment.practitionerName}` : ""}` : "No upcoming appointment"} />
        <Tile label="Follow-up" icon={<CalendarPlus size={13} />}
          main={recall ?? "None outstanding"}
          tone={b.recall && b.recall.overdueDays > 0 ? "danger" : undefined}
          sub={b.recall ? "Promised at the last visit, not booked yet" : "Nothing promised, or already booked"} />
        <Tile label="Visits" icon={<Stethoscope size={13} />} main={String(b.visitCount)} sub={b.visitCount === 1 ? "finished visit" : "finished visits"} />
        <div style={{ display: "flex", flexDirection: "column", justifyContent: "center", gap: 6, minWidth: 190 }}>
          <button onClick={() => begin.mutate()} disabled={begin.isPending}
            style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 8, padding: "13px 18px", borderRadius: 10, border: "none", cursor: "pointer",
              background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 700, opacity: begin.isPending ? 0.7 : 1 }}>
            <PlayCircle size={16} />
            {begin.isPending ? "Starting…" : resuming ? "Resume consultation" : plan.kind === "walk-in" ? "Start walk-in consultation" : "Start consultation"}
          </button>
          <div style={{ fontSize: 11, color: "var(--hf-text-muted)", textAlign: "center" }}>
            {plan.kind === "walk-in" ? "No appointment today: one is created now" : "Uses today's appointment"}
          </div>
        </div>
      </div>
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}

      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 3fr) minmax(0, 2fr)", gap: 14 }}>
        {/* Left: the story so far */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <Section title="Last visit">
            {last ? <VisitDetail v={last} /> : <Muted>Nothing recorded yet. This will be the first consultation.</Muted>}
          </Section>
          <Section title="Recent visits" action={b.recentVisits.length > 0 && onOpenTab ? { label: "Full history", onClick: () => onOpenTab("history") } : undefined}>
            {b.recentVisits.length === 0 ? <Muted>No finished visits yet.</Muted> : (
              <div style={{ display: "flex", flexDirection: "column" }}>
                {b.recentVisits.map((v, i) => (
                  <div key={v.id} style={{ display: "flex", gap: 12, padding: "9px 0", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                    <div style={{ width: 92, flexShrink: 0, fontSize: 12, color: "var(--hf-text-muted)" }}>{fmtDay(v.at)}</div>
                    <div style={{ fontSize: 13, color: "var(--hf-text)" }}>
                      <b>{v.diagnosis ?? v.chiefComplaint ?? "No diagnosis recorded"}</b>
                      {v.diagnosis && v.chiefComplaint && <span style={{ color: "var(--hf-text-muted)" }}> · {v.chiefComplaint}</span>}
                      {v.practitionerName && <span style={{ color: "var(--hf-text-muted)" }}> · Dr {v.practitionerName}</span>}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Section>
        </div>

        {/* Right: what to keep in mind while consulting */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <Section title="Allergies">
            {b.allergies.length === 0 ? <Muted>None recorded.</Muted> : (
              <Chips items={b.allergies.map(a => ({ key: a.id, text: a.allergen + (a.severity ? ` (${a.severity.toLowerCase().replace("_", " ")})` : ""),
                danger: a.severity === "SEVERE" || a.severity === "LIFE_THREATENING" }))} />
            )}
          </Section>
          <Section title="Conditions">
            {b.conditions.length === 0 ? <Muted>None recorded.</Muted> : (
              <Chips items={b.conditions.map(c => ({ key: c.id, text: c.conditionName + (c.status === "CONTROLLED" ? " (controlled)" : "") }))} />
            )}
          </Section>
          <Section title="Active medicines" icon={<Pill size={13} />} action={onOpenTab ? { label: "Prescriptions", onClick: () => onOpenTab("rx") } : undefined}>
            {b.medications.length === 0 ? <Muted>No active medicines recorded.</Muted> : (
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                {b.medications.slice(0, 6).map(m => (
                  <div key={m.id} style={{ fontSize: 13, color: "var(--hf-text)" }}>
                    <b>{m.medicineName}</b><span style={{ color: "var(--hf-text-muted)" }}>{[m.dose, m.frequency].filter(Boolean).length ? ` · ${[m.dose, m.frequency].filter(Boolean).join(" · ")}` : ""}</span>
                  </div>
                ))}
                {b.medications.length > 6 && <Muted>and {b.medications.length - 6} more</Muted>}
              </div>
            )}
          </Section>
          <Section title="Last vitals" subtitle={b.lastVitals ? fmtDay(b.lastVitals.takenAt) : undefined}>
            {b.lastVitals ? <VitalsGrid v={b.lastVitals} /> : <Muted>No vitals on record.</Muted>}
          </Section>
          <Section title="Lab results" icon={<FlaskConical size={13} />} action={onOpenTab ? { label: "Open labs", onClick: () => onOpenTab("labs") } : undefined}>
            {b.labs.recent.length === 0 ? <Muted>No results on file.</Muted> : (
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                <div style={{ fontSize: 13, color: b.labs.unreviewed ? "var(--hf-warning-text)" : "var(--hf-text-muted)", fontWeight: b.labs.unreviewed ? 700 : 400 }}>
                  {b.labs.unreviewed === 0 ? "All results reviewed" : `${b.labs.unreviewed} not yet reviewed`}
                </div>
                {b.labs.recent.map(l => (
                  <div key={l.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--hf-text)" }}>
                    <span>{fmtDay(l.at)}{l.reference ? ` · ${l.reference}` : ""}</span>
                    <span style={{ fontWeight: 700, color: l.critical ? "var(--hf-danger-text)" : l.abnormal ? "var(--hf-warning-text)" : "var(--hf-text-muted)" }}>
                      {l.critical ? "Critical" : l.abnormal ? "Abnormal" : "Normal"}{l.reviewed ? "" : " · new"}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </Section>
        </div>
      </div>
    </div>
  )
}

function VisitDetail({ v }: { v: BriefingVisit }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8, fontSize: 13, color: "var(--hf-text)" }}>
      <Row k="Reason" v={v.chiefComplaint} />
      <Row k="Diagnosis" v={v.diagnosis} />
      {v.icd10Codes.length > 0 && <Row k="ICD-10" v={v.icd10Codes.join(", ")} />}
      <Row k="Follow-up" v={v.followUpDays != null ? `in ${v.followUpDays} ${v.followUpDays === 1 ? "day" : "days"}` : null} />
    </div>
  )
}
const Row = ({ k, v }: { k: string; v?: string | null }) => (
  <div style={{ display: "flex", gap: 12 }}>
    <div style={{ width: 84, flexShrink: 0, color: "var(--hf-text-muted)", fontSize: 12 }}>{k}</div>
    <div>{v || <span style={{ color: "var(--hf-text-faint)" }}>Not recorded</span>}</div>
  </div>
)

function VitalsGrid({ v }: { v: NonNullable<Briefing["lastVitals"]> }) {
  const b = bmi(v.weightKg, v.heightCm)
  const items: [string, string | null][] = [
    ["BP", v.bloodPressure ? `${v.bloodPressure}` : null], ["Pulse", v.pulseBpm != null ? `${v.pulseBpm} bpm` : null],
    ["Temp", v.temperatureC != null ? `${v.temperatureC} °C` : null], ["SpO₂", v.oxygenSatPct != null ? `${v.oxygenSatPct} %` : null],
    ["Weight", v.weightKg != null ? `${v.weightKg} kg` : null], ["BMI", b != null ? String(b) : null],
  ]
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 8 }}>
      {items.map(([k, val]) => (
        <div key={k} style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border-subtle)", borderRadius: 8, padding: "7px 9px" }}>
          <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-muted)", letterSpacing: "0.05em" }}>{k}</div>
          <div style={{ fontSize: 14, fontWeight: 700, color: val ? "var(--hf-text)" : "var(--hf-text-faint)" }}>{val ?? "—"}</div>
        </div>
      ))}
    </div>
  )
}

function Chips({ items }: { items: { key: string; text: string; danger?: boolean }[] }) {
  return (
    <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
      {items.map(i => (
        <span key={i.key} style={{ padding: "3px 10px", borderRadius: 20, fontSize: 12, fontWeight: 600,
          background: i.danger ? "var(--hf-danger-soft)" : "var(--hf-surface-sunken)", color: i.danger ? "var(--hf-danger-text)" : "var(--hf-text-secondary)" }}>{i.text}</span>
      ))}
    </div>
  )
}

function Tile({ label, icon, main, sub, tone }: { label: string; icon: React.ReactNode; main: string; sub: string; tone?: "danger" }) {
  return (
    <div style={{ ...card, padding: "12px 16px", borderColor: tone === "danger" ? "var(--hf-danger-border)" : "var(--hf-border)" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 10, fontWeight: 700, letterSpacing: "0.06em", textTransform: "uppercase", color: "var(--hf-text-muted)", marginBottom: 6 }}>{icon}{label}</div>
      <div style={{ fontSize: 16, fontWeight: 800, color: tone === "danger" ? "var(--hf-danger-text)" : "var(--hf-text)", lineHeight: 1.25 }}>{main}</div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 3 }}>{sub}</div>
    </div>
  )
}

function Section({ title, subtitle, icon, action, children }: { title: string; subtitle?: string; icon?: React.ReactNode
  action?: { label: string; onClick: () => void }; children: React.ReactNode }) {
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, fontWeight: 800, letterSpacing: "0.05em", textTransform: "uppercase", color: "var(--hf-text-secondary)" }}>
          {icon}{title}{subtitle && <span style={{ fontWeight: 500, textTransform: "none", letterSpacing: 0, color: "var(--hf-text-muted)" }}> · {subtitle}</span>}
        </div>
        {action && <button onClick={action.onClick} style={{ background: "none", border: "none", cursor: "pointer", fontSize: 12, fontWeight: 600, color: "var(--hf-accent-text)" }}>{action.label} →</button>}
      </div>
      {children}
    </div>
  )
}
const Muted = ({ children }: { children: React.ReactNode }) => <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{children}</div>
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: "14px 16px" }
