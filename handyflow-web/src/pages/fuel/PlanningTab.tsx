// Stage 3: the day's planning board (loads, tanker and driver allocation) and the tanker register.
import { Fragment, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { WindowDialog, FailedAttemptDialog } from "./DeliveryExceptionDialogs"
import RoutesPanel from "./RoutesPanel"
import { parseCapacities, validateTanker, autoSplit, validateAllocation, type Plan, type PlanLoad, type Tanker, type Driver, type FleetVehicleOption, fleetOptionText, windowText } from "./transportOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null
const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })
const time = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

export default function PlanningTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_TRANSPORT_MANAGE"])
  const qc = useQueryClient()
  const [date, setDate] = useState(today())
  const [allocating, setAllocating] = useState<PlanLoad | null>(null)
  const [addTanker, setAddTanker] = useState(false)
  const [windowFor, setWindowFor] = useState<PlanLoad | null>(null)
  const [failing, setFailing] = useState<PlanLoad | null>(null)
  const plan = useQuery<Plan>({ queryKey: ["fuel-plan", date], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/planning", { params: { date } })) })
  const tankers = useQuery<Tanker[]>({ queryKey: ["fuel-tankers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tankers")) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-plan"] }); qc.invalidateQueries({ queryKey: ["fuel-tankers"] }); qc.invalidateQueries({ queryKey: ["deliveries"] }) }
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/fuel/tankers/${id}`), onSuccess: refresh })
  const loads = plan.data?.loads ?? []
  const open = loads.filter(l => !l.allocated && l.status === "SCHEDULED")

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14, flexWrap: "wrap" }}>
        <label style={{ ...labelStyle, margin: 0 }}>Day</label>
        <input aria-label="Plan date" type="date" value={date} onChange={e => setDate(e.target.value)} style={{ ...inputStyle, width: 170 }} />
        <span style={{ flex: 1 }} />
        {open.length > 0 && <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">{open.length} load{open.length === 1 ? "" : "s"} need a tanker and driver</Chip>}
      </div>

      {plan.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : loads.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No loads scheduled for this day.</p> : (
        <div style={{ ...card, overflowX: "auto", marginBottom: 20 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Time</th><th style={th}>Window</th><th style={th}>Customer</th><th style={th}>Fuel</th><th style={th}>Litres</th><th style={th}>Tanker</th><th style={th}>Driver</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {loads.map(l => (
                <tr key={l.deliveryId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}>{time(l.scheduledAt)}</td><td style={td}>{windowText(l.windowStart, l.windowEnd) ?? "–"}</td><td style={td}>{l.customerName ?? "Walk-in"}</td><td style={td}>{l.fuelType}</td><td style={td}>{fmtL(l.litres)}</td>
                  <td style={td}>{l.tankerRegistration ?? "–"}</td><td style={td}>{l.driverName ?? "–"}</td>
                  <td style={td}>{l.status === "SCHEDULED" && !l.allocated ? <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Unallocated</Chip> : l.status.toLowerCase().replace("_", " ")}{l.routeId && <> <Chip bg="var(--hf-surface-muted)" fg="var(--hf-text)">Stop {l.routeSequence}</Chip></>}{(l.failedAttempts ?? 0) > 0 && <> <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">{l.failedAttempts} failed attempt{l.failedAttempts === 1 ? "" : "s"}</Chip></>}</td>
                  <td style={td}>{canManage && l.status === "SCHEDULED" && !l.routeId && <button style={btnGhost} onClick={() => setAllocating(l)}>{l.allocated ? "Reallocate" : "Allocate"}</button>}
                    {canManage && (l.status === "SCHEDULED" || l.status === "IN_TRANSIT") && <button style={{ ...btnGhost, marginLeft: 6 }} onClick={() => setWindowFor(l)}>Window</button>}
                    {canManage && l.status === "IN_TRANSIT" && <button style={{ ...btnGhost, marginLeft: 6 }} onClick={() => setFailing(l)}>Could not deliver</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <RoutesPanel date={date} loads={loads} tankers={tankers.data ?? []} canManage={canManage} onChanged={refresh} />

      {(plan.data?.tankers ?? []).length > 0 && (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 20 }}>
          {plan.data!.tankers.map(t => (
            <div key={t.tankerId} style={{ ...card, padding: 12, minWidth: 170, fontSize: 13 }}>
              <strong>{t.registration}</strong><div>{t.loads} load{t.loads === 1 ? "" : "s"} · {fmtL(t.litres)}</div><div style={{ color: "var(--hf-text-muted)" }}>capacity {fmtL(t.capacity)}</div>
            </div>
          ))}
        </div>
      )}

      <div style={{ display: "flex", alignItems: "center", marginBottom: 8 }}>
        <h3 style={{ margin: 0, fontSize: 14, flex: 1 }}>Tankers</h3>
        {canManage && <button style={btnPrimary} onClick={() => setAddTanker(true)}>Add tanker</button>}
      </div>
      {(tankers.data ?? []).length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No tankers yet. Add one to start allocating loads.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Registration</th><th style={th}>Name</th><th style={th}>Compartments</th><th style={th}>Capacity</th><th style={th}>Open loads</th><th style={th}></th></tr></thead>
            <tbody>
              {tankers.data!.map(t => (
                <Fragment key={t.id}><tr style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}>{t.registration}
                    {t.fleetVehicleLabel && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Fleet: {t.fleetVehicleLabel}</div>}
                    {!t.fleetVehicleId && t.fleetSuggestionLabel && <div style={{ fontSize: 11, color: "var(--hf-warning-text)" }}>Also in Fleet as {t.fleetSuggestionLabel}</div>}
                  </td><td style={td}>{t.name ?? "–"}</td>
                  <td style={td}>{t.compartments.map(c => c.capacityLitres.toLocaleString("en-ZA")).join(" + ")}</td>
                  <td style={td}>{fmtL(t.totalCapacity)}</td><td style={td}>{t.openLoads}</td>
                  <td style={td}>{canManage && <button style={btnGhost} disabled={t.openLoads > 0 || remove.isPending} onClick={() => remove.mutate(t.id)}>Remove</button>}</td>
                </tr></Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {windowFor && <WindowDialog deliveryId={windowFor.deliveryId} start={windowFor.windowStart} end={windowFor.windowEnd} onDone={refresh} onClose={() => setWindowFor(null)} />}
      {failing && <FailedAttemptDialog deliveryId={failing.deliveryId} onDone={refresh} onClose={() => setFailing(null)} />}
      {allocating && <AllocateDialog load={allocating} tankers={tankers.data ?? []} onDone={refresh} onClose={() => setAllocating(null)} />}
      {addTanker && <TankerDialog onDone={refresh} onClose={() => setAddTanker(false)} />}
    </div>
  )
}

function TankerDialog({ onDone, onClose }: { onDone: () => void; onClose: () => void }) {
  const [reg, setReg] = useState(""); const [name, setName] = useState(""); const [caps, setCaps] = useState(""); const [err, setErr] = useState("")
  const [fleetId, setFleetId] = useState("")
  const fleet = useQuery<FleetVehicleOption[]>({ queryKey: ["fuel-tanker-fleet-vehicles"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tankers/fleet-vehicles")), retry: false })
  const chosen = (fleet.data ?? []).find(v => v.id === fleetId)
  const save = useMutation({
    mutationFn: () => apiClient.post("/api/v1/fuel/tankers", { registration: chosen ? chosen.registration : reg, name: name || null, compartmentCapacities: parseCapacities(caps).values, fleetVehicleId: fleetId || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not add the tanker")),
  })
  return (
    <Modal title="Add tanker" onClose={onClose}>
      {(fleet.data ?? []).length > 0 && <>
        <label style={labelStyle}>Fleet vehicle</label>
        <select aria-label="Fleet vehicle" value={fleetId} onChange={e => setFleetId(e.target.value)} style={inputStyle}>
          <option value="">Not in Fleet / enter the registration</option>
          {fleet.data!.map(v => <option key={v.id} value={v.id} disabled={!!v.linkedTankerRegistration}>{fleetOptionText(v)}</option>)}
        </select>
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>Linking keeps the registration in step with Fleet.</p>
      </>}
      <label style={labelStyle}>Registration *</label><input aria-label="Registration" value={chosen ? chosen.registration : reg} disabled={!!chosen} onChange={e => setReg(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Name</label><input value={name} onChange={e => setName(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Compartment capacities (litres) *</label>
      <input aria-label="Compartment capacities" value={caps} onChange={e => setCaps(e.target.value)} placeholder="10000, 6000" style={inputStyle} />
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>One number per compartment, in order. These cannot be changed later.</p>
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateTanker(chosen ? chosen.registration : reg, caps); if (v) return setErr(v); setErr(""); save.mutate() }}>Save tanker</button>
      </div>
    </Modal>
  )
}

function AllocateDialog({ load, tankers, onDone, onClose }: { load: PlanLoad; tankers: Tanker[]; onDone: () => void; onClose: () => void }) {
  const [tankerId, setTankerId] = useState(load.tankerId ?? ""); const [driverId, setDriverId] = useState(load.driverEmployeeId ?? "")
  const [split, setSplit] = useState<Record<string, string>>({}); const [err, setErr] = useState("")
  const drivers = useQuery<Driver[]>({ queryKey: ["fuel-drivers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/drivers")) })
  const tanker = tankers.find(t => t.id === tankerId)
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/deliveries/${load.deliveryId}/allocate`, {
      tankerId, driverEmployeeId: driverId,
      loads: Object.entries(split).filter(([, v]) => v.trim() !== "").map(([compartmentId, v]) => ({ compartmentId, litres: Number(v) })),
    }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not allocate the load")),
  })
  const pick = (id: string) => {
    setTankerId(id); const t = tankers.find(x => x.id === id)
    setSplit(t ? (autoSplit(load.litres, t.compartments) ?? {}) : {})
  }
  return (
    <Modal title={`Allocate ${fmtL(load.litres)} ${load.fuelType}`} onClose={onClose} width={520}>
      <label style={labelStyle}>Tanker *</label>
      <select aria-label="Tanker" value={tankerId} onChange={e => pick(e.target.value)} style={inputStyle}>
        <option value="">Choose…</option>{tankers.map(t => <option key={t.id} value={t.id}>{t.registration} ({fmtL(t.totalCapacity)})</option>)}
      </select>
      <label style={{ ...labelStyle, marginTop: 10 }}>Driver *</label>
      <select aria-label="Driver" value={driverId} onChange={e => setDriverId(e.target.value)} style={inputStyle}>
        <option value="">Choose…</option>{(drivers.data ?? []).map(d => <option key={d.id} value={d.id}>{d.name}{d.jobTitle ? ` (${d.jobTitle})` : ""}</option>)}
      </select>
      {tanker && (
        <div style={{ marginTop: 10 }}>
          <label style={labelStyle}>Litres per compartment (must add up to {fmtL(load.litres)})</label>
          {tanker.compartments.map(c => (
            <div key={c.id} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6, fontSize: 13 }}>
              <span style={{ width: 150 }}>Compartment {c.number} ({fmtL(c.capacityLitres)})</span>
              <input aria-label={`Compartment ${c.number}`} type="number" value={split[c.id] ?? ""} onChange={e => setSplit(s => ({ ...s, [c.id]: e.target.value }))} style={{ ...inputStyle, width: 130 }} />
            </div>
          ))}
        </div>
      )}
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateAllocation(tankerId, driverId, load.litres, split, tanker?.compartments ?? []); if (v) return setErr(v); setErr(""); save.mutate() }}>Allocate</button>
      </div>
    </Modal>
  )
}
