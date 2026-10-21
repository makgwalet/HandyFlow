// src/pages/fleet/Driver360.tsx
//
// Driver 360 pieces shown inside the Drivers screen: one driver's profile (compliance, score components, activity, accidents and cost) and the score settings.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { unwrap, WEIGHT_FIELDS, bandText, bandToneFor, componentTone, coverageNote, driverVehicles, initials, periodText, ringFraction, scoreText, settingsBody, settingsProblem, toForm, weightTotal, type Settings, type SettingsForm } from "./driverScores.logic"
import PhotoBox from "./PhotoBox"
import DriverDocuments from "./DriverDocuments"
import DriverHistory, { RecentActivity } from "./DriverHistory"
import { COMPLIANCE_LABEL, complianceTone, expiryText } from "./vehicleDetail.logic"

export interface ScoreRow { driverId: string; driverName: string; overall: number | null; band: string | null; coverage: number; reliable: boolean; tripCount: number }
interface Component { key: string; label: string; weight: number; effectiveWeight: number; score: number | null; status: string; detail: string }
interface Profile {
  driver: { createdAt?: string | null; department?: string | null; firstName: string; lastName: string; licenseCode: string | null; prdpRequired: boolean; status: string; phone?: string | null; email?: string | null; idNumber?: string | null; licenseNumber?: string | null; licenseExpiry?: string | null; prdpNumber?: string | null; prdpCategory?: string | null; prdpExpiry?: string | null; notes?: string | null }
  compliance: { key: string; label: string; status: string; daysRemaining: number | null }[]
  score: { overall: number | null; band: string | null; coverage: number; reliable: boolean; periodFrom: string; periodTo: string; components: Component[] }
  activity: { tripCount: number; completedTrips: number; totalKm: number; businessKm: number; privateKm: number; fuelLitres: number | null; litresPer100Km: number | null; inspectionsCompleted: number; inspectionsFailed: number; vehiclesDriven: string[] }
  accidents: { count: number; withInjuries: number; withFatalities: number; totalRepairCost: number | null; totalExcess: number | null; accidents: { id: string; referenceNumber: string; occurredAt: string; status: string }[] }
}
interface SettingsResponse extends Settings { usingDefaults: boolean }

const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const R = (n: number | null | undefined) => (n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA")}`)
const TONE = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", bad: "var(--hf-danger-text)", none: "var(--hf-text-faint)" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 12 }

type TabId = "overview" | "compliance" | "documents" | "medical" | "performance" | "incidents" | "history"
const TABS: { id: TabId; label: string }[] = [{ id: "overview", label: "Overview" }, { id: "compliance", label: "Licence & PrDP" }, { id: "medical", label: "Training & Certifications" }, { id: "documents", label: "Documents" }, { id: "performance", label: "Performance" }, { id: "incidents", label: "Incidents" }, { id: "history", label: "History" }]
export interface AssignedVehicle { id: string; registration: string; make?: string | null; model?: string | null; assignedDriverId?: string | null }
const date = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")
const km = (n: number) => `${n.toLocaleString("en-ZA")} km`

function Ring({ overall, tone }: { overall: number | null; tone: keyof typeof TONE }) {
  const r = 52, c = 2 * Math.PI * r
  return (
    <svg width="132" height="132" viewBox="0 0 132 132" aria-hidden="true" style={{ flexShrink: 0 }}>
      <circle cx="66" cy="66" r={r} fill="none" stroke="var(--hf-surface-sunken)" strokeWidth="12" />
      <circle cx="66" cy="66" r={r} fill="none" stroke={TONE[tone]} strokeWidth="12" strokeLinecap="round" strokeDasharray={`${ringFraction(overall) * c} ${c}`} transform="rotate(-90 66 66)" />
    </svg>)
}

/** One driver's page: who they are, the score and what it is made of, then compliance, performance and incidents on tabs. */
export function ProfileView({ id, onClose, onEdit, vehicles = [] }: { id: string; onClose: () => void; onEdit?: () => void; vehicles?: AssignedVehicle[] }) {
  const [tab, setTab] = useState<TabId>("overview")
  const canManage = usePermission("FLEET_MANAGE")
  const { data, isError } = useQuery<Profile>({ queryKey: ["fleet", "driver-profile", id], queryFn: async () => unwrap<Profile>(await apiClient.get(`/api/v1/fleet/drivers/${id}/profile`)) })
  const back = <button type="button" style={{ ...field, cursor: "pointer", marginBottom: 12 }} onClick={onClose}>← Back to drivers</button>
  if (isError) return <div>{back}<div role="alert" style={{ color: "var(--hf-danger-text)" }}>Couldn't load this driver.</div></div>
  if (!data) return <div>{back}<p>Loading…</p></div>
  const { driver, score, activity, accidents } = data
  const note = coverageNote(score.reliable, score.coverage), tone = bandToneFor(score.band, score.reliable), assigned = driverVehicles(vehicles, id)
  const line: React.CSSProperties = { display: "flex", gap: 14, flexWrap: "wrap", fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 6 }
  const tile = (label: string, value: string, sub?: string) => <div key={label} style={{ background: "var(--hf-surface-muted)", borderRadius: 10, padding: "10px 12px" }}><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{label}</div><div style={{ fontSize: 18, fontWeight: 700 }}>{value}</div>{sub && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{sub}</div>}</div>
  const kv = (label: string, value: string, key = label) => <div key={key}><dt style={{ fontSize: 10, fontWeight: 700, textTransform: "uppercase", color: "var(--hf-text-faint)" }}>{label}</dt><dd style={{ margin: 0, fontSize: 13.5, fontWeight: 600 }}>{value}</dd></div>
  const badge = (status: string) => <span style={{ color: TONE[complianceTone(status)], fontWeight: 700 }}>● {COMPLIANCE_LABEL[status] ?? status}</span>
  return (
    <div>
      {back}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: 12, marginBottom: 12 }}>
        <section aria-label="Driver details" style={{ ...card, marginBottom: 0, display: "flex", gap: 14, alignItems: "flex-start" }}>
          <PhotoBox base={`/api/v1/fleet/drivers/${id}`} canEdit={canManage} name={`${driver.firstName} ${driver.lastName}`} fallback={<div aria-hidden="true" style={{ fontWeight: 800, fontSize: 28, color: "var(--hf-info-text)" }}>{initials(driver.firstName, driver.lastName)}</div>} />
          <div style={{ minWidth: 0, flex: 1 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
              <h3 style={{ margin: 0 }}>{driver.firstName} {driver.lastName}</h3>
              <span style={{ fontSize: 11, fontWeight: 700, padding: "2px 9px", borderRadius: 20, background: driver.status === "ACTIVE" ? "var(--hf-success-soft-strong)" : "var(--hf-surface-sunken)", color: driver.status === "ACTIVE" ? "var(--hf-success-text-strong)" : "var(--hf-text-muted)" }}>{driver.status === "ACTIVE" ? "Active" : "Inactive"}</span>
              {driver.prdpRequired && <span style={{ fontSize: 11, fontWeight: 700, padding: "2px 9px", borderRadius: 20, background: "var(--hf-violet-soft)", color: "var(--hf-violet-text)" }}>PrDP {driver.prdpCategory ?? "required"}</span>}
            </div>
            <div style={line}>
              {driver.department && <span aria-label="Department">Department: {driver.department}</span>}
              {driver.idNumber && <span>ID {driver.idNumber}</span>}
              {driver.phone && <a href={`tel:${driver.phone}`} style={{ color: "inherit" }}>{driver.phone}</a>}
              {driver.email && <a href={`mailto:${driver.email}`} style={{ color: "inherit" }}>{driver.email}</a>}
              {!driver.idNumber && !driver.phone && !driver.email && <span>No contact details yet</span>}
            </div>
            <div style={line} aria-label="Assigned vehicles">{assigned.length === 0 ? <span>No vehicle assigned</span> : assigned.map(v => <span key={v.id}>🚗 {v.registration}{v.make || v.model ? ` · ${[v.make, v.model].filter(Boolean).join(" ")}` : ""}</span>)}</div>
            {driver.notes && <div style={{ marginTop: 8, fontSize: 12.5, color: "var(--hf-warning-text-deep)", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 8, padding: "6px 10px" }}>{driver.notes}</div>}
            {onEdit && <button type="button" style={{ ...btn, marginTop: 10 }} onClick={onEdit}>Edit driver</button>}
          </div>
        </section>
        <section aria-label="Safety and performance score" style={{ ...card, marginBottom: 0 }}>
          <h4 style={{ margin: "0 0 8px" }}>Safety &amp; performance score</h4>
          <div style={{ display: "flex", gap: 16, alignItems: "center", flexWrap: "wrap" }}>
            <div style={{ position: "relative", width: 132, height: 132 }}>
              <Ring overall={score.overall} tone={tone} />
              <div aria-label="Overall score" style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", textAlign: "center" }}>
                <div style={{ fontSize: score.overall == null ? 14 : 22, fontWeight: 800, color: TONE[tone] }}>{scoreText(score.overall)}</div>
                {score.band && <div style={{ fontSize: 12, fontWeight: 700, color: TONE[tone] }}>{bandText(score.band, score.reliable)}</div>}
              </div>
            </div>
            <div style={{ flex: 1, minWidth: 220 }}>
              {score.components.map(c => (
                <div key={c.key} aria-label={c.label} title={c.detail} style={{ margin: "5px 0" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12 }}><span>{c.label} <span style={{ color: "var(--hf-text-faint)" }}>{c.weight}%</span></span><strong>{c.score == null ? "No data" : `${c.score}`}</strong></div>
                  <div style={{ height: 5, background: "var(--hf-surface-sunken)", borderRadius: 99 }}><div style={{ height: "100%", width: `${Math.max(0, Math.min(100, c.score ?? 0))}%`, background: TONE[componentTone(c.score)], borderRadius: 99 }} /></div>
                </div>))}
            </div>
          </div>
          {note && <div role="status" style={{ fontSize: 12, color: "var(--hf-warning-text)", marginTop: 6 }}>{note}</div>}
          <div style={{ fontSize: 11.5, color: "var(--hf-text-faint)", marginTop: 6 }}>{periodText(score.periodFrom, score.periodTo)}</div>
        </section>
      </div>
      <div style={card}>
        <div role="tablist" aria-label="Driver sections" style={{ display: "flex", gap: 6, borderBottom: "1px solid var(--hf-border-subtle)", paddingBottom: 8, marginBottom: 12 }}>
          {TABS.map(t => <button key={t.id} type="button" role="tab" aria-selected={tab === t.id} onClick={() => setTab(t.id)} style={{ padding: "6px 14px", borderRadius: 8, border: "none", cursor: "pointer", fontWeight: 700, fontSize: 13, background: tab === t.id ? "var(--hf-info-soft)" : "transparent", color: tab === t.id ? "var(--hf-primary-text)" : "var(--hf-text-muted)" }}>{t.label}</button>)}
        </div>
        <div role="tabpanel" aria-label={TABS.find(t => t.id === tab)!.label}>
          {tab === "overview" && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 18 }}>
              <div>
                <h4 style={{ margin: "0 0 6px" }}>Compliance status</h4>
                <table style={{ fontSize: 13, width: "100%" }}><tbody>{data.compliance.map(c => (
                  <tr key={c.key} aria-label={c.label} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}><td style={{ padding: "6px 10px 6px 0" }}>{c.label}</td><td style={{ padding: "6px 10px 6px 0" }}>{badge(c.status)}</td><td style={{ color: "var(--hf-text-muted)" }}>{c.daysRemaining != null ? expiryText(c.daysRemaining) : ""}</td></tr>))}</tbody></table>
                {data.compliance.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>No documents to check.</p>}
              </div>
              <div>
                <h4 style={{ margin: "0 0 6px" }}>Highlights</h4>
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))", gap: 8 }}>
                  {tile("Trips", String(activity.tripCount), `${activity.completedTrips} completed`)}{tile("Distance", km(activity.totalKm))}
                  {tile("Fuel use", activity.litresPer100Km == null ? "—" : `${activity.litresPer100Km} L/100 km`)}{tile("Accidents", String(accidents.count))}{tile("Failed inspections", String(activity.inspectionsFailed), `of ${activity.inspectionsCompleted + activity.inspectionsFailed}`)}
                </div>
                {activity.vehiclesDriven.length > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>Vehicles driven: {activity.vehiclesDriven.join(", ")}</div>}
              </div>
              <RecentActivity driverId={id} createdAt={driver.createdAt} accidents={accidents.accidents} />
            </div>)}
          {tab === "compliance" && (
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 18 }}>
              <div><h4 style={{ margin: "0 0 8px" }}>Driving licence</h4>
                <dl style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, margin: 0 }}>{kv("Number", driver.licenseNumber ?? "—")}{kv("Code", driver.licenseCode ?? "—")}{kv("Expires", date(driver.licenseExpiry))}
                  {data.compliance.filter(c => c.key === "LICENCE").map(c => kv("Status", `${COMPLIANCE_LABEL[c.status] ?? c.status}${c.daysRemaining != null ? ` · ${expiryText(c.daysRemaining)}` : ""}`, "licence-status"))}</dl></div>
              <div><h4 style={{ margin: "0 0 8px" }}>Professional driving permit (PrDP)</h4>
                {!driver.prdpRequired ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Not required for this driver.</p> : (
                  <dl style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, margin: 0 }}>{kv("Number", driver.prdpNumber ?? "—")}{kv("Category", driver.prdpCategory ?? "—")}{kv("Expires", date(driver.prdpExpiry))}
                    {data.compliance.filter(c => c.key === "PRDP").map(c => kv("Status", `${COMPLIANCE_LABEL[c.status] ?? c.status}${c.daysRemaining != null ? ` · ${expiryText(c.daysRemaining)}` : ""}`, "prdp-status"))}</dl>)}</div>
            </div>)}
          {tab === "history" && <DriverHistory driverId={id} createdAt={driver.createdAt} accidents={accidents.accidents} />}
          {tab === "documents" && <DriverDocuments driverId={id} group="documents" />}
          {tab === "medical" && <DriverDocuments driverId={id} group="medical" />}
          {tab === "performance" && (
            <div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 8, marginBottom: 12 }}>
                {tile("Trips completed", `${activity.completedTrips} of ${activity.tripCount}`)}{tile("Distance", km(activity.totalKm), `${km(activity.businessKm)} business · ${km(activity.privateKm)} private`)}
                {tile("Fuel", activity.fuelLitres == null ? "—" : `${activity.fuelLitres} L`, activity.litresPer100Km == null ? undefined : `${activity.litresPer100Km} L/100 km`)}{tile("Inspections", `${activity.inspectionsCompleted} done`, `${activity.inspectionsFailed} failed`)}
              </div>
              <h4 style={{ margin: "0 0 6px" }}>How the score is made up</h4>
              <table style={{ width: "100%", fontSize: 12.5 }}><tbody>{score.components.map(c => (
                <tr key={c.key} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}><td style={{ padding: "5px 6px" }}>{c.label}</td><td style={{ padding: "5px 6px" }}>{c.weight}% of the score</td><td style={{ padding: "5px 6px", fontWeight: 700 }}>{c.score == null ? "No data" : c.score}</td><td style={{ padding: "5px 6px", color: "var(--hf-text-muted)" }}>{c.detail}</td></tr>))}</tbody></table>
            </div>)}
          {tab === "incidents" && (
            <div>
              <div style={{ fontSize: 13 }}>{accidents.count} {accidents.count === 1 ? "accident" : "accidents"} ({accidents.withInjuries} with injuries, {accidents.withFatalities} fatal) · repair cost {R(accidents.totalRepairCost)} · excess {R(accidents.totalExcess)}</div>
              {accidents.accidents.length > 0 && <table style={{ width: "100%", fontSize: 12.5, marginTop: 8 }}><thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Reference</th><th>Date</th><th>Status</th></tr></thead>
                <tbody>{accidents.accidents.map(a => <tr key={a.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}><td style={{ padding: "5px 0" }}>{a.referenceNumber}</td><td>{new Date(a.occurredAt).toLocaleDateString("en-ZA")}</td><td>{a.status.toLowerCase().replace("_", " ")}</td></tr>)}</tbody></table>}
            </div>)}
        </div>
      </div>
    </div>
  )
}

export function SettingsPanel() {
  const qc = useQueryClient()
  const canAdmin = usePermission("FLEET_ADMIN")
  const [form, setForm] = useState<SettingsForm | null>(null)
  const [error, setError] = useState<string | null>(null), [saved, setSaved] = useState(false)
  const { data } = useQuery<SettingsResponse>({ queryKey: ["fleet", "driver-score-settings"], queryFn: async () => unwrap<SettingsResponse>(await apiClient.get("/api/v1/fleet/driver-score-settings")) })
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fleet/driver-score-settings", settingsBody(form as SettingsForm)),
    onSuccess: () => { setError(null); setSaved(true); for (const k of ["driver-score-settings", "driver-scores", "driver-profile"]) qc.invalidateQueries({ queryKey: ["fleet", k] }) }, onError: e => setError(errorOf(e)),
  })
  if (!data) return null
  const f = form ?? toForm(data)
  const set = (k: keyof Settings, v: string) => { setForm({ ...f, [k]: v }); setSaved(false) }
  const problem = settingsProblem(f)
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>Score settings {data.usingDefaults && <span style={{ fontSize: 12, fontWeight: 400, color: "var(--hf-text-muted)" }}>(built-in defaults)</span>}</h3>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill,minmax(210px,1fr))", gap: 10 }}>
        {WEIGHT_FIELDS.map(w => <label key={w.key} style={{ fontSize: 12.5 }}>{w.label} %<input aria-label={`${w.label} weight`} type="number" min="0" max="100" disabled={!canAdmin} style={{ ...field, width: "100%" }} value={f[w.key]} onChange={e => set(w.key, e.target.value)} /></label>)}
      </div>
      <div style={{ fontSize: 12.5, margin: "6px 0", fontWeight: 700, color: weightTotal(f) === 100 ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>Total {weightTotal(f)}% of 100%</div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        {([["lookbackDays", "Look back (days)"], ["excellentThreshold", "Excellent from"], ["goodThreshold", "Good from"], ["fairThreshold", "Fair from"]] as const).map(([k, l]) => (
          <label key={k} style={{ fontSize: 12.5 }}>{l}<input aria-label={l} type="number" disabled={!canAdmin} style={{ ...field, width: 110, display: "block" }} value={f[k]} onChange={e => set(k, e.target.value)} /></label>))}
      </div>
      {canAdmin && <div style={{ marginTop: 10 }}>
        <button type="button" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Save settings</button>
        {problem && <span style={{ marginLeft: 10, fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
        {saved && <span role="status" style={{ marginLeft: 10, fontSize: 12, color: "var(--hf-success-text-strong)" }}>Saved</span>}
      </div>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </div>
  )
}
