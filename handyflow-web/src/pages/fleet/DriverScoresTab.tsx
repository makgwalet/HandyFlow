// src/pages/fleet/DriverScoresTab.tsx
//
// Driver 360: the leaderboard, one driver's profile (compliance, score components, activity, accidents and cost), and the score settings.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { WEIGHT_FIELDS, bandText, bandToneFor, coverageNote, scoreText, settingsBody, settingsProblem, toForm, weightTotal, type Settings, type SettingsForm } from "./driverScores.logic"
import { COMPLIANCE_LABEL, complianceTone, expiryText } from "./vehicleDetail.logic"

interface Row { driverId: string; driverName: string; overall: number | null; band: string | null; coverage: number; reliable: boolean; tripCount: number }
interface Component { key: string; label: string; weight: number; effectiveWeight: number; score: number | null; status: string; detail: string }
interface Profile {
  driver: { firstName: string; lastName: string; licenseCode: string | null; prdpRequired: boolean; status: string }
  compliance: { key: string; label: string; status: string; daysRemaining: number | null }[]
  score: { overall: number | null; band: string | null; coverage: number; reliable: boolean; periodFrom: string; periodTo: string; components: Component[] }
  activity: { tripCount: number; completedTrips: number; totalKm: number; businessKm: number; privateKm: number; fuelLitres: number | null; litresPer100Km: number | null; inspectionsCompleted: number; inspectionsFailed: number; vehiclesDriven: string[] }
  accidents: { count: number; withInjuries: number; withFatalities: number; totalRepairCost: number | null; totalExcess: number | null; accidents: { id: string; referenceNumber: string; occurredAt: string; status: string }[] }
}
interface SettingsResponse extends Settings { usingDefaults: boolean }

const unwrap = <T,>(r: { data: unknown }): T => { const o = r.data as { data?: unknown }; return (o && typeof o === "object" && "data" in o ? o.data : r.data) as T }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const R = (n: number | null | undefined) => (n == null ? "—" : `R ${Number(n).toLocaleString("en-ZA")}`)
const TONE = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", bad: "var(--hf-danger-text)", none: "var(--hf-text-faint)" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 12 }

function ProfileView({ id, onClose }: { id: string; onClose: () => void }) {
  const { data } = useQuery<Profile>({ queryKey: ["fleet", "driver-profile", id], queryFn: async () => unwrap<Profile>(await apiClient.get(`/api/v1/fleet/drivers/${id}/profile`)) })
  if (!data) return <p>Loading…</p>
  const { driver, score, activity, accidents } = data
  const note = coverageNote(score.reliable, score.coverage)
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between" }}><h3 style={{ margin: 0 }}>{driver.firstName} {driver.lastName}</h3><button type="button" style={field} onClick={onClose}>Close</button></div>
      <div style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>Licence {driver.licenseCode ?? "—"}{driver.prdpRequired ? " · PrDP required" : ""} · {driver.status.toLowerCase()}</div>
      <h4 style={{ margin: "12px 0 4px" }}>Compliance</h4>
      <table style={{ fontSize: 13 }}><tbody>{data.compliance.map(c => (
        <tr key={c.key} aria-label={c.label}><td style={{ padding: "2px 10px 2px 0" }}>{c.label}</td>
          <td style={{ color: TONE[complianceTone(c.status)], fontWeight: 700, padding: "2px 10px 2px 0" }}>● {COMPLIANCE_LABEL[c.status] ?? c.status}</td><td style={{ color: "var(--hf-text-muted)" }}>{c.daysRemaining != null ? expiryText(c.daysRemaining) : ""}</td></tr>))}</tbody></table>
      <h4 style={{ margin: "12px 0 4px" }}>Safety &amp; performance score</h4>
      <div style={{ fontSize: 22, fontWeight: 800, color: TONE[bandToneFor(score.band, score.reliable)] }} aria-label="Overall score">{scoreText(score.overall)} {score.band && <span style={{ fontSize: 13 }}>{bandText(score.band, score.reliable)}</span>}</div>
      {note && <div role="status" style={{ fontSize: 12, color: "var(--hf-warning-text)" }}>{note}</div>}
      <table style={{ width: "100%", fontSize: 12.5, marginTop: 6 }}><tbody>{score.components.map(c => (
        <tr key={c.key} aria-label={c.label} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
          <td style={{ padding: "5px 6px" }}>{c.label}</td><td style={{ padding: "5px 6px" }}>{c.weight}%</td>
          <td style={{ padding: "5px 6px", fontWeight: 700 }}>{c.score == null ? "No data" : `${c.score}`}</td><td style={{ padding: "5px 6px", color: "var(--hf-text-muted)" }}>{c.detail}</td></tr>))}</tbody></table>
      <h4 style={{ margin: "12px 0 4px" }}>Activity</h4>
      <div style={{ fontSize: 13 }}>{activity.completedTrips} of {activity.tripCount} trips completed · {activity.totalKm.toLocaleString("en-ZA")} km ({activity.businessKm.toLocaleString("en-ZA")} business, {activity.privateKm.toLocaleString("en-ZA")} private)
        {activity.litresPer100Km != null && ` · ${activity.litresPer100Km} L/100 km`} · inspections {activity.inspectionsCompleted} done, {activity.inspectionsFailed} failed</div>
      {activity.vehiclesDriven.length > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Vehicles: {activity.vehiclesDriven.join(", ")}</div>}
      <h4 style={{ margin: "12px 0 4px" }}>Accidents and cost</h4>
      <div style={{ fontSize: 13 }}>{accidents.count} {accidents.count === 1 ? "accident" : "accidents"} ({accidents.withInjuries} with injuries, {accidents.withFatalities} fatal) · repair cost {R(accidents.totalRepairCost)} · excess {R(accidents.totalExcess)}</div>
      <ul style={{ margin: "4px 0 0", paddingLeft: 18, fontSize: 12.5 }}>{accidents.accidents.map(a => <li key={a.id}>{a.referenceNumber} · {new Date(a.occurredAt).toLocaleDateString("en-ZA")} · {a.status.toLowerCase().replace("_", " ")}</li>)}</ul>
    </div>
  )
}

function SettingsPanel() {
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

export default function DriverScoresTab() {
  const [openId, setOpenId] = useState<string | null>(null)
  const [showSettings, setShowSettings] = useState(false)
  const { data: rows = [], isLoading, isError } = useQuery<Row[]>({ queryKey: ["fleet", "driver-scores"], queryFn: async () => unwrap<Row[]>(await apiClient.get("/api/v1/fleet/driver-scores")) })
  return (
    <div>
      <div style={{ marginBottom: 12 }}><button type="button" style={field} onClick={() => setShowSettings(s => !s)}>{showSettings ? "Hide score settings" : "Score settings"}</button></div>
      {showSettings && <SettingsPanel />}
      {openId && <ProfileView id={openId} onClose={() => setOpenId(null)} />}
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load driver scores.</div>}
      {!isLoading && !isError && rows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No active drivers yet.</p>}
      {rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
          <thead><tr>{["#", "Driver", "Score", "Band", "Trips", ""].map(h => <th key={h} style={{ textAlign: "left", padding: "8px 10px", fontSize: 11, textTransform: "uppercase", color: "var(--hf-text-faint)" }}>{h}</th>)}</tr></thead>
          <tbody>{rows.map((r, i) => (
            <tr key={r.driverId} aria-label={r.driverName} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={{ padding: "9px 10px" }}>{i + 1}</td><td style={{ padding: "9px 10px" }}><strong>{r.driverName}</strong></td>
              <td style={{ padding: "9px 10px", fontWeight: 700 }}>{scoreText(r.overall)}{!r.reliable && r.overall != null && <span title="Low data coverage"> *</span>}</td>
              <td style={{ padding: "9px 10px", color: TONE[bandToneFor(r.band, r.reliable)], fontWeight: 700 }}>{bandText(r.band, r.reliable)}</td>
              <td style={{ padding: "9px 10px" }}>{r.tripCount}</td>
              <td style={{ padding: "9px 10px" }}><button type="button" style={field} onClick={() => setOpenId(r.driverId)}>Profile</button></td>
            </tr>))}</tbody>
        </table>)}
      {rows.some(r => !r.reliable && r.overall != null) && <p style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>* Indicative only: less than half of the configured score had data.</p>}
    </div>
  )
}
