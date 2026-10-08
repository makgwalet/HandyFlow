// The patient profile: complete or correct what registration left out, one section at a time.
import { useEffect, useState, type ReactNode } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, Check, Circle } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { MedicalAidCard } from "./PatientBackgroundCards"
import { Card, primaryBtn, smallBtn } from "./OverviewCard"
import { BORDER, GRAY, lbl, sinp, type Patient } from "./patientFile.shared"
import {
  CONTACTS, ID_TYPES, LANGUAGES, PROVINCES, RELATIONSHIPS, SECTIONS, SEXES, TITLES, EMPTY_PROFILE,
  firstIncomplete, postalProblem, profileSummary, sectionProgress, toForm, toRequest,
  type Completeness, type ProfileForm, type SectionId,
} from "./profileView"

const root = (id: string) => `/api/v1/clinic/patients/${id}`
const data = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fallback: string) => e?.response?.data?.message ?? fallback

interface ProfileResponse extends Partial<Record<keyof ProfileForm, string | null>> { completeness: Completeness }

function Field({ id, label, children, wide }: { id: string; label: string; children: ReactNode; wide?: boolean }) {
  return <div style={wide ? { gridColumn: "1 / -1" } : undefined}><label style={lbl} htmlFor={id}>{label}</label>{children}</div>
}

function Pick({ id, value, onChange, options, disabled }: { id: string; value: string; onChange: (v: string) => void; options: (string | [string, string])[]; disabled?: boolean }) {
  return (
    <select id={id} value={value} disabled={disabled} onChange={e => onChange(e.target.value)} style={sinp}>
      <option value="">—</option>
      {options.map(o => { const [v, l] = Array.isArray(o) ? o : [o, o]; return <option key={v} value={v}>{l}</option> })}
    </select>
  )
}

export default function PatientProfilePage({ patient, initialSection, onBack, onPatientChanged, onOpenConsent }: {
  patient: Patient; initialSection?: SectionId; onBack: () => void
  onPatientChanged?: (p: Patient) => void; onOpenConsent?: () => void
}) {
  const pid = patient.id
  const qc = useQueryClient()
  const canEdit = usePermission("CLINIC_PATIENT_UPDATE")
  const canIdentity = usePermission("CLINIC_PATIENT_DEMOGRAPHICS_WRITE")
  const key = ["pf-profile", pid]
  const { data: profile } = useQuery<ProfileResponse>({ queryKey: key, queryFn: async () => data(await apiClient.get(`${root(pid)}/profile`)) })
  const comp = profile?.completeness
  const [section, setSection] = useState<SectionId | null>(initialSection ?? null)
  const [form, setForm] = useState<ProfileForm>(EMPTY_PROFILE)
  const [core, setCore] = useState({
    firstName: patient.firstName ?? "", lastName: patient.lastName ?? "", idNumber: patient.idNumber ?? "", dateOfBirth: patient.dateOfBirth ?? "",
    gender: patient.gender ?? "", sexAtBirth: (patient as any).sexAtBirth ?? "", phone: patient.phone ?? "", email: patient.email ?? "",
    emergencyContactName: patient.emergencyContactName ?? "", emergencyContactPhone: patient.emergencyContactPhone ?? "",
  })
  const [loaded, setLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [saved, setSaved] = useState("")

  useEffect(() => { if (profile && !loaded) { setForm(toForm(profile)); setLoaded(true) } }, [profile, loaded])
  // With no section asked for, open where something is missing.
  useEffect(() => { if (comp && section === null) setSection(firstIncomplete(comp)) }, [comp, section])

  const set = (k: keyof ProfileForm) => (v: string) => setForm(f => ({ ...f, [k]: v }))
  const setC = (k: keyof typeof core) => (v: string) => setCore(c => ({ ...c, [k]: v }))
  const active: SectionId = section ?? "identity"
  const readOnly = !canEdit

  const save = async (which: SectionId) => {
    setError(""); setSaved("")
    const problem = which === "address" ? postalProblem(form.postalCode) : null
    if (problem) { setError(problem); return }
    setBusy(true)
    try {
      let next: any = null
      if (which === "identity" && canIdentity) {
        next = data(await apiClient.put(`${root(pid)}/demographics`, {
          firstName: core.firstName, lastName: core.lastName, idNumber: core.idNumber || null, dateOfBirth: core.dateOfBirth || null,
          gender: core.gender || null, sexAtBirth: core.sexAtBirth || null }))
      }
      if (which === "contact" || which === "emergency") {
        next = data(await apiClient.put(`${root(pid)}/contact`, {
          phone: core.phone || null, email: core.email || null, emergencyContactName: core.emergencyContactName || null, emergencyContactPhone: core.emergencyContactPhone || null }))
      }
      await apiClient.put(`${root(pid)}/profile`, toRequest(form))
      await qc.invalidateQueries({ queryKey: key })
      qc.invalidateQueries({ queryKey: ["clinic-patients"] })
      if (next) {
        const changed = { ...patient, ...next, fullName: `${next.firstName} ${next.lastName}`.trim() } as Patient
        setCore(c => ({ ...c, dateOfBirth: next.dateOfBirth ?? c.dateOfBirth }))
        onPatientChanged?.(changed)
      }
      setSaved("Saved")
    } catch (e) { setError(msg(e, "Could not save.")) }
    finally { setBusy(false) }
  }

  const saveBar = (which: SectionId) => (
    <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 18 }}>
      <button type="button" disabled={busy || readOnly} onClick={() => save(which)} style={{ ...primaryBtn, padding: "9px 18px", fontSize: 14 }}>{busy ? "Saving…" : "Save"}</button>
      {saved && <span role="status" style={{ fontSize: 13, color: "var(--hf-success-text)" }}>{saved}</span>}
      {error && <span role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</span>}
      {readOnly && <span style={{ fontSize: 12, color: GRAY }}>You can view this profile but not change it.</span>}
    </div>)

  const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 14 }
  const idLocked = !canIdentity || readOnly

  const body = () => {
    switch (active) {
      case "identity": return (
        <>
          <div style={grid}>
            <Field id="pp-title" label="Title"><Pick id="pp-title" value={form.title} onChange={set("title")} options={TITLES} disabled={readOnly} /></Field>
            <Field id="pp-first" label="First name *"><input id="pp-first" value={core.firstName} disabled={idLocked} onChange={e => setC("firstName")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-last" label="Last name *"><input id="pp-last" value={core.lastName} disabled={idLocked} onChange={e => setC("lastName")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-idtype" label="ID type"><Pick id="pp-idtype" value={form.idType} onChange={set("idType")} options={ID_TYPES} disabled={readOnly} /></Field>
            <Field id="pp-id" label="ID or passport number"><input id="pp-id" value={core.idNumber} disabled={idLocked} onChange={e => setC("idNumber")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-dob" label="Date of birth"><input id="pp-dob" type="date" value={core.dateOfBirth} disabled={idLocked} onChange={e => setC("dateOfBirth")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-sex" label="Sex at birth"><Pick id="pp-sex" value={core.sexAtBirth} onChange={setC("sexAtBirth")} options={SEXES} disabled={idLocked} /></Field>
            <Field id="pp-nat" label="Nationality"><input id="pp-nat" value={form.nationality} disabled={readOnly} onChange={e => set("nationality")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-lang" label="Preferred language"><Pick id="pp-lang" value={form.preferredLanguage} onChange={set("preferredLanguage")} options={LANGUAGES} disabled={readOnly} /></Field>
          </div>
          {!canIdentity && <div style={{ fontSize: 12, color: GRAY, marginTop: 10 }}>Name, ID number, date of birth and sex are changed by someone with that permission.</div>}
          {canIdentity && <div style={{ fontSize: 12, color: GRAY, marginTop: 10 }}>A South African ID number is checked against the date of birth. Changes to name or ID are not yet kept in a change history.</div>}
          {saveBar("identity")}
        </>)
      case "contact": return (
        <>
          <div style={grid}>
            <Field id="pp-phone" label="Phone"><input id="pp-phone" value={core.phone} disabled={readOnly} onChange={e => setC("phone")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-email" label="Email"><input id="pp-email" type="email" value={core.email} disabled={readOnly} onChange={e => setC("email")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-pref" label="Preferred contact"><Pick id="pp-pref" value={form.preferredContact} onChange={set("preferredContact")} options={CONTACTS} disabled={readOnly} /></Field>
          </div>
          {saveBar("contact")}
        </>)
      case "address": return (
        <>
          <div style={grid}>
            <Field id="pp-a1" label="Address line 1" wide><input id="pp-a1" value={form.addressLine1} disabled={readOnly} onChange={e => set("addressLine1")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-a2" label="Address line 2" wide><input id="pp-a2" value={form.addressLine2} disabled={readOnly} onChange={e => set("addressLine2")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-sub" label="Suburb"><input id="pp-sub" value={form.suburb} disabled={readOnly} onChange={e => set("suburb")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-city" label="City"><input id="pp-city" value={form.city} disabled={readOnly} onChange={e => set("city")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-prov" label="Province"><Pick id="pp-prov" value={form.province} onChange={set("province")} options={PROVINCES} disabled={readOnly} /></Field>
            <Field id="pp-post" label="Postal code"><input id="pp-post" inputMode="numeric" value={form.postalCode} disabled={readOnly} onChange={e => set("postalCode")(e.target.value)} style={sinp} /></Field>
          </div>
          {saveBar("address")}
        </>)
      case "emergency": return (
        <>
          <div style={grid}>
            <Field id="pp-en" label="Emergency contact name"><input id="pp-en" value={core.emergencyContactName} disabled={readOnly} onChange={e => setC("emergencyContactName")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-ep" label="Emergency contact phone"><input id="pp-ep" value={core.emergencyContactPhone} disabled={readOnly} onChange={e => setC("emergencyContactPhone")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-er" label="Relationship"><Pick id="pp-er" value={form.emergencyRelationship} onChange={set("emergencyRelationship")} options={RELATIONSHIPS} disabled={readOnly} /></Field>
            <Field id="pp-sn" label="Second contact name"><input id="pp-sn" value={form.secondaryContactName} disabled={readOnly} onChange={e => set("secondaryContactName")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-sp" label="Second contact phone"><input id="pp-sp" value={form.secondaryContactPhone} disabled={readOnly} onChange={e => set("secondaryContactPhone")(e.target.value)} style={sinp} /></Field>
            <Field id="pp-sr" label="Second contact relationship"><Pick id="pp-sr" value={form.secondaryContactRelationship} onChange={set("secondaryContactRelationship")} options={RELATIONSHIPS} disabled={readOnly} /></Field>
          </div>
          {saveBar("emergency")}
        </>)
      case "scheme": return (
        <>
          <div style={{ ...grid, marginBottom: 14 }}>
            <Field id="pp-pay" label="Pays by"><Pick id="pp-pay" value={form.paymentType} onChange={set("paymentType")} options={[["MEDICAL_AID", "Medical aid"], ["SELF_PAY", "Self-pay"]]} disabled={readOnly} /></Field>
          </div>
          {saveBar("scheme")}
          <div style={{ marginTop: 18 }}><MedicalAidCard patientId={pid} /></div>
        </>)
      case "family": return <FamilyList patient={patient} />
      case "consent": return (
        <div style={{ fontSize: 13, display: "flex", flexDirection: "column", gap: 10, alignItems: "flex-start" }}>
          <div>Consent is recorded as events (granted or withdrawn), never edited here.</div>
          {onOpenConsent && <button type="button" onClick={onOpenConsent} style={smallBtn}>Open the consent record</button>}
        </div>)
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <button type="button" onClick={onBack} style={{ ...smallBtn, display: "flex", alignItems: "center", gap: 6 }}><ArrowLeft size={13} />Back to the file</button>
        <div style={{ flex: 1, minWidth: 220, maxWidth: 420 }}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 4 }}>
            <strong>Profile completeness</strong><span>{comp ? `${comp.percent}%` : "…"}</span>
          </div>
          <div role="progressbar" aria-valuenow={comp?.percent ?? 0} aria-valuemin={0} aria-valuemax={100}
            style={{ height: 8, borderRadius: 4, background: "var(--hf-surface-sunken)", overflow: "hidden" }}>
            <div style={{ width: `${comp?.percent ?? 0}%`, height: "100%", background: "var(--hf-primary)" }} />
          </div>
          <div style={{ fontSize: 11, color: GRAY, marginTop: 4 }}>{profileSummary(comp)}</div>
        </div>
      </div>
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-start" }}>
        <nav aria-label="Profile sections" style={{ width: 220, flexShrink: 0, display: "flex", flexDirection: "column", gap: 4 }}>
          {SECTIONS.map(s => {
            const p = sectionProgress(comp, s.id); const on = s.id === active; const complete = p.total > 0 && p.done === p.total
            return (
              <button key={s.id} type="button" aria-current={on ? "page" : undefined} onClick={() => { setSection(s.id); setError(""); setSaved("") }}
                style={{ display: "flex", alignItems: "center", gap: 8, textAlign: "left", padding: "9px 12px", borderRadius: 8, cursor: "pointer",
                  border: `1px solid ${on ? "var(--hf-primary)" : BORDER}`, background: on ? "var(--hf-surface-sunken)" : "var(--hf-surface)", color: "var(--hf-text)" }}>
                {p.total === 0 ? <Circle size={14} style={{ opacity: 0.3 }} /> : complete ? <Check size={14} style={{ color: "var(--hf-success-text)" }} /> : <Circle size={14} style={{ color: "var(--hf-warning-text)" }} />}
                <span style={{ fontSize: 13, fontWeight: on ? 700 : 500 }}>{s.label}</span>
              </button>)
          })}
        </nav>
        <div style={{ flex: 1, minWidth: 280 }}>
          <Card title={SECTIONS.find(s => s.id === active)?.label ?? ""}>{body()}</Card>
        </div>
      </div>
    </div>
  )
}

function FamilyList({ patient }: { patient: Patient }) {
  const { data: members = [], isLoading } = useQuery<Patient[]>({
    queryKey: ["pf-family", patient.id], enabled: patient.accountType !== "INDIVIDUAL",
    queryFn: async () => { const d = data(await apiClient.get(`${root(patient.id)}/family`)); return Array.isArray(d) ? d : [] },
  })
  if (patient.accountType === "INDIVIDUAL") return <div style={{ fontSize: 13, color: GRAY }}>This patient is not linked to a family account. Use "Convert to family account" in the file's Actions menu to start one.</div>
  if (isLoading) return <div style={{ fontSize: 13, color: GRAY }}>Loading…</div>
  if (members.length === 0) return <div style={{ fontSize: 13, color: GRAY }}>No family members are linked yet.</div>
  return (
    <ul style={{ margin: 0, padding: 0, listStyle: "none", display: "flex", flexDirection: "column", gap: 6 }}>
      {members.map(m => <li key={m.id} style={{ fontSize: 13 }}><strong>{m.fullName}</strong>{m.relationship ? ` · ${m.relationship.toLowerCase()}` : ""}{m.accountType === "PRINCIPAL" ? " · account holder" : ""}</li>)}
    </ul>
  )
}
