// src/pages/fleet/AccidentsTab.tsx
//
// The accident register: every case with its status, whether the vehicle can be driven, and the move to the next stage.
// A case marked "cannot be driven" keeps the vehicle off trips until it is closed or corrected (the server enforces this).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { NEXT_STATUSES, ROAD_OPTIONS, STATUS_LABEL, ACCIDENT_STATUSES, WEATHER_OPTIONS, blocksVehicle, emptyReport, reportBody, reportProblem } from "./accidents.logic"
import { fetchAllPages } from "./apiShape"
import { Pager, SearchBox } from "./ListControls"
import { useListView } from "./listKit"

interface Accident {
  id: string; referenceNumber: string; vehicleId: string; registration: string; driverName: string | null; occurredAt: string; location: string | null
  description: string; injuriesCount: number; fatalitiesCount: number; estimatedDamage: number | null; vehicleDrivable: boolean; status: string
  claimStatus: string | null; claimNumber: string | null; evidenceCount: number; policeCaseNumber?: string | null
}
interface VehicleOption { id: string; registration: string }
interface DriverOption { id: string; firstName?: string; lastName?: string }

const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const th: React.CSSProperties = { padding: "8px 10px", fontSize: 11, fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4, color: "var(--hf-text-faint)", textAlign: "left" }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 12.5, verticalAlign: "top" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 12 }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const chip = (bad: boolean): React.CSSProperties => ({ display: "inline-block", padding: "2px 8px", borderRadius: 999, fontSize: 11, fontWeight: 700, background: bad ? "var(--hf-danger-bg, #fde8e8)" : "var(--hf-surface-2, #eef2f6)", color: bad ? "var(--hf-danger-text)" : "var(--hf-text-muted)" })

const row: React.CSSProperties = { display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 10 }
const Section = ({ title }: { title: string }) => <p style={{ fontSize: 12, fontWeight: 800, textTransform: "uppercase", letterSpacing: 0.5, color: "var(--hf-text-faint)", margin: "14px 0 6px" }}>{title}</p>
const Labelled = ({ label, children }: { label: string; children: React.ReactNode }) => <div style={{ display: "flex", flexDirection: "column", gap: 3, fontSize: 11.5, color: "var(--hf-text-muted)", marginBottom: 2 }}><span>{label}</span>{children}</div>

function ReportForm({ vehicles, drivers, onDone }: { vehicles: VehicleOption[]; drivers: DriverOption[]; onDone: () => void }) {
  const [f, setF] = useState(emptyReport())
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof typeof f, v: string | boolean) => { setF(p => ({ ...p, [k]: v })); setError(null) }
  const save = useMutation({
    mutationFn: () => apiClient.post("/api/v1/fleet/accidents", reportBody(f)),
    onSuccess: onDone, onError: e => setError(errorOf(e)),
  })
  const problem = reportProblem(f)
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>Report an accident</h3>
      <Section title="What happened" />
      <div style={row}>
        <Labelled label="Vehicle *"><select aria-label="Vehicle" style={field} value={f.vehicleId} onChange={e => set("vehicleId", e.target.value)}>
          <option value="">Vehicle…</option>{vehicles.map(v => <option key={v.id} value={v.id}>{v.registration}</option>)}
        </select></Labelled>
        <Labelled label="Driver"><select aria-label="Driver" style={field} value={f.driverId} onChange={e => set("driverId", e.target.value)}>
          <option value="">Not on the driver register</option>{drivers.filter(d => d.firstName).map(d => <option key={d.id} value={d.id}>{d.firstName} {d.lastName}</option>)}
        </select></Labelled>
        {!f.driverId && <Labelled label="Driver's name"><input aria-label="Driver's name" placeholder="If not on the register" style={field} value={f.driverName} onChange={e => set("driverName", e.target.value)} /></Labelled>}
        <Labelled label="When it happened *"><input aria-label="When it happened" type="datetime-local" style={field} value={f.occurredAt} onChange={e => set("occurredAt", e.target.value)} /></Labelled>
        <Labelled label="Where"><input aria-label="Location" placeholder="Road, town, landmark" style={{ ...field, minWidth: 240 }} value={f.location} onChange={e => set("location", e.target.value)} /></Labelled>
        <Labelled label="Weather"><select aria-label="Weather" style={field} value={f.weather} onChange={e => set("weather", e.target.value)}><option value="">Not recorded</option>{WEATHER_OPTIONS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></Labelled>
        <Labelled label="Road"><select aria-label="Road condition" style={field} value={f.roadCondition} onChange={e => set("roadCondition", e.target.value)}><option value="">Not recorded</option>{ROAD_OPTIONS.map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></Labelled>
      </div>
      <Labelled label="What happened *"><textarea aria-label="What happened" placeholder="Describe what happened" rows={3} style={{ ...field, width: "100%" }} value={f.description} onChange={e => set("description", e.target.value)} /></Labelled>
      <Labelled label="Witnesses"><textarea aria-label="Witnesses" placeholder="Names and contact numbers" rows={2} style={{ ...field, width: "100%" }} value={f.witnesses} onChange={e => set("witnesses", e.target.value)} /></Labelled>

      <Section title="People and damage" />
      <div style={row}>
        <Labelled label="Injuries"><input aria-label="Injuries" type="number" min="0" style={{ ...field, width: 90 }} value={f.injuries} onChange={e => set("injuries", e.target.value)} /></Labelled>
        <Labelled label="Fatalities"><input aria-label="Fatalities" type="number" min="0" style={{ ...field, width: 90 }} value={f.fatalities} onChange={e => set("fatalities", e.target.value)} /></Labelled>
        <Labelled label="Estimated damage to our vehicle (R)"><input aria-label="Estimated damage" type="number" min="0" placeholder="0.00" style={{ ...field, width: 150 }} value={f.estimatedDamage} onChange={e => set("estimatedDamage", e.target.value)} /></Labelled>
        <Labelled label="Third-party damage (R)"><input aria-label="Third-party damage" type="number" min="0" placeholder="0.00" style={{ ...field, width: 150 }} value={f.thirdPartyDamage} onChange={e => set("thirdPartyDamage", e.target.value)} /></Labelled>
      </div>

      <Section title="Other party" />
      <div style={row}>
        <Labelled label="Vehicle registration"><input aria-label="Other vehicle registration" style={field} value={f.otherVehicleRegistration} onChange={e => set("otherVehicleRegistration", e.target.value)} /></Labelled>
        <Labelled label="Driver's name"><input aria-label="Other driver's name" style={field} value={f.otherDriverName} onChange={e => set("otherDriverName", e.target.value)} /></Labelled>
        <Labelled label="Driver's contact"><input aria-label="Other driver's contact" style={field} value={f.otherDriverContact} onChange={e => set("otherDriverContact", e.target.value)} /></Labelled>
        <Labelled label="Their insurer"><input aria-label="Other insurer" style={field} value={f.otherInsurer} onChange={e => set("otherInsurer", e.target.value)} /></Labelled>
      </div>

      <Section title="Police" />
      <div style={row}>
        <Labelled label="Police case number"><input aria-label="Police case number" style={field} value={f.policeCaseNumber} onChange={e => set("policeCaseNumber", e.target.value)} /></Labelled>
        <Labelled label="SAPS station"><input aria-label="SAPS station" style={field} value={f.sapsStation} onChange={e => set("sapsStation", e.target.value)} /></Labelled>
      </div>
      <label style={{ display: "block", marginTop: 8, fontSize: 13 }}>
        <input type="checkbox" aria-label="The vehicle can still be driven" checked={f.vehicleDrivable} onChange={e => set("vehicleDrivable", e.target.checked)} /> The vehicle can still be driven
      </label>
      {!f.vehicleDrivable && <div role="status" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>This vehicle cannot start trips until the case is closed or corrected.</div>}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, marginTop: 8 }}>{error}</div>}
      <button type="button" style={{ ...btn, marginTop: 10, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Report accident</button>
      {problem && <span style={{ marginLeft: 10, fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
    </div>
  )
}

function StatusMover({ a }: { a: Accident }) {
  const qc = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const move = useMutation({
    mutationFn: (status: string) => apiClient.post(`/api/v1/fleet/accidents/${a.id}/status`, { status }),
    onSuccess: () => { setError(null); qc.invalidateQueries({ queryKey: ["fleet", "accidents"] }) }, onError: e => setError(errorOf(e)),
  })
  const next = NEXT_STATUSES[a.status] ?? []
  if (next.length === 0) return <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Final</span>
  return (
    <div>
      <select aria-label={`Move ${a.referenceNumber} to`} style={field} value="" disabled={move.isPending} onChange={e => e.target.value && move.mutate(e.target.value)}>
        <option value="">Move to…</option>{next.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
      </select>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 11.5, marginTop: 4, maxWidth: 220 }}>{error}</div>}
    </div>
  )
}

export default function AccidentsTab() {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const [status, setStatus] = useState("")
  const [showForm, setShowForm] = useState(false)
  const { data: accidents = [], isLoading, isError, refetch } = useQuery<Accident[]>({
    queryKey: ["fleet", "accidents", status],
    queryFn: async () => (await fetchAllPages<Accident>(u => apiClient.get(u), `/api/v1/fleet/accidents${status ? `?status=${status}` : ""}`)).items,
  })
  const view = useListView(accidents, a => [a.referenceNumber, a.claimNumber, a.registration, a.driverName, a.location, a.description].filter(Boolean).join(" "))
  const { data: vehicles = [] } = useQuery<VehicleOption[]>({ queryKey: ["fleet", "vehicle-options"], enabled: canManage, queryFn: async () => (await fetchAllPages<VehicleOption>(u => apiClient.get(u), "/api/v1/fleet/vehicles")).items })
  const { data: drivers = [] } = useQuery<DriverOption[]>({ queryKey: ["fleet-drivers"], enabled: canManage, queryFn: async () => (await fetchAllPages<DriverOption>(u => apiClient.get(u), "/api/v1/fleet/drivers")).items })
  const offRoad = accidents.filter(blocksVehicle)

  return (
    <div>
      <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        <SearchBox label="Search accidents" placeholder="Search case, vehicle, driver or place" value={view.search} onChange={view.setSearch} />
        <select aria-label="Filter by status" style={field} value={status} onChange={e => { setStatus(e.target.value); view.setPage(0) }}>
          <option value="">All cases</option>{ACCIDENT_STATUSES.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </select>
        {canManage && <button type="button" style={btn} onClick={() => setShowForm(s => !s)}>{showForm ? "Cancel" : "Report an accident"}</button>}
      </div>
      {showForm && <ReportForm vehicles={vehicles} drivers={drivers} onDone={() => { setShowForm(false); qc.invalidateQueries({ queryKey: ["fleet", "accidents"] }) }} />}
      {offRoad.length > 0 && (
        <div role="status" style={{ ...card, borderColor: "var(--hf-danger-text)", fontSize: 13 }}>
          <strong>{offRoad.length} {offRoad.length === 1 ? "vehicle cannot" : "vehicles cannot"} be driven:</strong> {offRoad.map(a => a.registration).join(", ")}. They cannot start trips until the case is closed or corrected.
        </div>
      )}
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load accidents. <button type="button" onClick={() => refetch()}>Try again</button></div>}
      {!isLoading && !isError && accidents.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No accidents on record.</p>}
      {accidents.length > 0 && view.shown.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No accidents match.</p>}
      {view.shown.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Case</th><th style={th}>Vehicle</th><th style={th}>When</th><th style={th}>What happened</th><th style={th}>Status</th><th style={th}>Drivable</th><th style={th}>Evidence</th>{canManage && <th style={th} />}</tr></thead>
          <tbody>{view.paged.rows.map(a => (
            <tr key={a.id} aria-label={a.referenceNumber} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
              <td style={td}><strong>{a.referenceNumber}</strong>{a.claimNumber && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Claim {a.claimNumber}</div>}</td>
              <td style={td}>{a.registration}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{a.driverName}</div></td>
              <td style={td}>{new Date(a.occurredAt).toLocaleDateString("en-ZA")}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{a.location}</div></td>
              <td style={td}>{a.description}{a.policeCaseNumber && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>SAPS case {a.policeCaseNumber}</div>}{(a.injuriesCount > 0 || a.fatalitiesCount > 0) && <div style={{ fontSize: 11, color: "var(--hf-danger-text)" }}>{a.injuriesCount} injured, {a.fatalitiesCount} fatal</div>}</td>
              <td style={td}><span style={chip(false)}>{STATUS_LABEL[a.status] ?? a.status}</span></td>
              <td style={td}>{blocksVehicle(a) ? <span style={chip(true)}>Cannot be driven</span> : a.vehicleDrivable ? "Yes" : "—"}</td>
              <td style={td}>{a.evidenceCount}</td>
              {canManage && <td style={td}><StatusMover a={a} /></td>}
            </tr>))}</tbody>
        </table>
      )}
      <Pager paged={view.paged} onPage={view.setPage} />
    </div>
  )
}
