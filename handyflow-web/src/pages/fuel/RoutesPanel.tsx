// C04 part 2: routes on the planning board. One tanker and driver carrying several loads in an order.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { autoSplit, moveStop, routable, routeStateLabel, windowText, type Driver, type PlanLoad, type RouteView, type Tanker } from "./transportOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null
const time = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

export default function RoutesPanel({ date, loads, tankers, canManage, onChanged }: { date: string; loads: PlanLoad[]; tankers: Tanker[]; canManage: boolean; onChanged: () => void }) {
  const qc = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [adding, setAdding] = useState<RouteView | null>(null)
  const [cancelling, setCancelling] = useState<RouteView | null>(null)
  const [err, setErr] = useState("")
  const routes = useQuery<RouteView[]>({ queryKey: ["fuel-routes", date], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/routes", { params: { date } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-routes"] }); onChanged() }
  const order = useMutation({ mutationFn: (v: { id: string; ids: string[] }) => apiClient.put(`/api/v1/fuel/routes/${v.id}/stop-order`, { deliveryIds: v.ids }), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not change the order")) })
  const drop = useMutation({ mutationFn: (v: { id: string; deliveryId: string }) => apiClient.delete(`/api/v1/fuel/routes/${v.id}/stops/${v.deliveryId}`), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not remove the stop")) })

  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 8 }}>
        <h3 style={{ margin: 0, fontSize: 14, flex: 1 }}>Routes</h3>
        {canManage && <button style={btnPrimary} onClick={() => setCreating(true)}>New route</button>}
      </div>
      <Err text={err} />
      {routes.isLoading ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Loading…</p> : (routes.data ?? []).length === 0
        ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No routes for this day. A route puts several loads on one tanker and driver, in order.</p>
        : (routes.data ?? []).map(r => (
          <div key={r.id} style={{ ...card, padding: 12, marginBottom: 10 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
              <strong>{r.label ?? "Route"}</strong>
              <span style={{ fontSize: 13 }}>{r.tankerRegistration ?? "tanker"} · {r.driverName ?? "driver"}</span>
              <Chip bg={r.state === "CANCELLED" ? "var(--hf-danger-soft)" : "var(--hf-surface-muted)"} fg={r.state === "CANCELLED" ? "var(--hf-danger-text)" : "var(--hf-text)"}>{routeStateLabel(r.state)}</Chip>
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{fmtL(r.plannedLitres)} of {fmtL(r.capacityLitres)} · {r.deliveredCount} of {r.stopCount} delivered</span>
            </div>
            {r.cancelReason && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>Cancelled: {r.cancelReason}</div>}
            {r.stops.length === 0 && r.state !== "CANCELLED" && <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>No stops yet.</p>}
            {r.stops.length > 0 && (
              <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 8 }}>
                <tbody>
                  {r.stops.map((s, i) => (
                    <tr key={s.deliveryId} style={{ borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
                      <td style={{ padding: "6px 8px", width: 40 }}><strong>{s.sequence}</strong></td>
                      <td style={{ padding: "6px 8px" }}>{s.customerName ?? "Walk-in"}</td>
                      <td style={{ padding: "6px 8px" }}>{s.fuelType} · {fmtL(s.litres)}</td>
                      <td style={{ padding: "6px 8px" }}>{windowText(s.windowStart, s.windowEnd) ?? time(s.scheduledAt)}</td>
                      <td style={{ padding: "6px 8px" }}>{s.status.toLowerCase().replace("_", " ")}{s.failedAttempts > 0 ? ` · ${s.failedAttempts} failed` : ""}</td>
                      <td style={{ padding: "6px 8px", textAlign: "right", whiteSpace: "nowrap" }}>
                        {canManage && r.state !== "CANCELLED" && <>
                          <button style={btnGhost} aria-label={`Move stop ${s.sequence} up`} disabled={moveStop(r.stops, i, -1) === null || order.isPending} onClick={() => order.mutate({ id: r.id, ids: moveStop(r.stops, i, -1)! })}>Up</button>
                          <button style={{ ...btnGhost, marginLeft: 4 }} aria-label={`Move stop ${s.sequence} down`} disabled={moveStop(r.stops, i, 1) === null || order.isPending} onClick={() => order.mutate({ id: r.id, ids: moveStop(r.stops, i, 1)! })}>Down</button>
                          <button style={{ ...btnGhost, marginLeft: 4 }} aria-label={`Remove stop ${s.sequence}`} disabled={s.status !== "SCHEDULED" || drop.isPending} onClick={() => drop.mutate({ id: r.id, deliveryId: s.deliveryId })}>Remove</button>
                        </>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {canManage && r.state !== "CANCELLED" && (
              <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                <button style={btnGhost} onClick={() => setAdding(r)}>Add stop</button>
                {r.stops.every(s => s.status === "SCHEDULED") && <button style={btnGhost} onClick={() => setCancelling(r)}>Cancel route</button>}
              </div>
            )}
          </div>
        ))}
      {creating && <NewRouteDialog date={date} tankers={tankers} onDone={refresh} onClose={() => setCreating(false)} />}
      {adding && <AddStopDialog route={adding} loads={routable(loads)} tanker={tankers.find(t => t.id === adding.tankerId)} onDone={refresh} onClose={() => setAdding(null)} />}
      {cancelling && <CancelRouteDialog route={cancelling} onDone={refresh} onClose={() => setCancelling(null)} />}
    </div>
  )
}

function NewRouteDialog({ date, tankers, onDone, onClose }: { date: string; tankers: Tanker[]; onDone: () => void; onClose: () => void }) {
  const [label, setLabel] = useState(""); const [tankerId, setTankerId] = useState(""); const [driverId, setDriverId] = useState(""); const [err, setErr] = useState("")
  const drivers = useQuery<Driver[]>({ queryKey: ["fuel-drivers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/drivers")) })
  const save = useMutation({
    mutationFn: () => apiClient.post("/api/v1/fuel/routes", { routeDate: date, label: label.trim() || null, tankerId, driverEmployeeId: driverId }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not create the route")),
  })
  return (
    <Modal title={`New route for ${date}`} onClose={onClose}>
      <label style={labelStyle}>Name</label><input aria-label="Route name" value={label} onChange={e => setLabel(e.target.value)} style={inputStyle} placeholder="Morning run" />
      <label style={{ ...labelStyle, marginTop: 10 }}>Tanker *</label>
      <select aria-label="Route tanker" value={tankerId} onChange={e => setTankerId(e.target.value)} style={inputStyle}>
        <option value="">Choose…</option>{tankers.map(t => <option key={t.id} value={t.id}>{t.registration} ({fmtL(t.totalCapacity)})</option>)}
      </select>
      <label style={{ ...labelStyle, marginTop: 10 }}>Driver *</label>
      <select aria-label="Route driver" value={driverId} onChange={e => setDriverId(e.target.value)} style={inputStyle}>
        <option value="">Choose…</option>{(drivers.data ?? []).map(d => <option key={d.id} value={d.id}>{d.name}</option>)}
      </select>
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { if (!tankerId || !driverId) return setErr("Choose a tanker and a driver."); setErr(""); save.mutate() }}>Create route</button>
      </div>
    </Modal>
  )
}

function AddStopDialog({ route, loads, tanker, onDone, onClose }: { route: RouteView; loads: PlanLoad[]; tanker?: Tanker; onDone: () => void; onClose: () => void }) {
  const [deliveryId, setDeliveryId] = useState(""); const [split, setSplit] = useState<Record<string, string>>({}); const [err, setErr] = useState("")
  const load = loads.find(l => l.deliveryId === deliveryId)
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/routes/${route.id}/stops`, {
      deliveryId, loads: Object.entries(split).filter(([, v]) => v.trim() !== "").map(([compartmentId, v]) => ({ compartmentId, litres: Number(v) })),
    }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not add the stop")),
  })
  const pick = (id: string) => { setDeliveryId(id); const l = loads.find(x => x.deliveryId === id); setSplit(l && tanker ? (autoSplit(l.litres, tanker.compartments) ?? {}) : {}) }
  const total = Object.values(split).reduce((a, v) => a + (Number(v) || 0), 0)
  return (
    <Modal title={`Add a stop to ${route.label ?? "the route"}`} onClose={onClose} width={520}>
      {loads.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No scheduled loads left for this day that are not already on a route.</p> : <>
        <label style={labelStyle}>Load *</label>
        <select aria-label="Load" value={deliveryId} onChange={e => pick(e.target.value)} style={inputStyle}>
          <option value="">Choose…</option>{loads.map(l => <option key={l.deliveryId} value={l.deliveryId}>{time(l.scheduledAt)} · {l.customerName ?? "Walk-in"} · {l.fuelType} {fmtL(l.litres)}</option>)}
        </select>
        {load && tanker && (
          <div style={{ marginTop: 10 }}>
            <label style={labelStyle}>Litres per compartment (must add up to {fmtL(load.litres)}). The tanker is filled once, so other stops' litres count against each compartment.</label>
            {tanker.compartments.map(c => (
              <div key={c.id} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6, fontSize: 13 }}>
                <span style={{ width: 150 }}>Compartment {c.number} ({fmtL(c.capacityLitres)})</span>
                <input aria-label={`Compartment ${c.number}`} type="number" value={split[c.id] ?? ""} onChange={e => setSplit(s => ({ ...s, [c.id]: e.target.value }))} style={{ ...inputStyle, width: 130 }} />
              </div>
            ))}
          </div>
        )}
      </>}
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending || !load} onClick={() => { if (Math.abs(total - load!.litres) > 0.001) return setErr(`The compartments add up to ${total} L but the load is ${load!.litres} L.`); setErr(""); save.mutate() }}>Add stop</button>
      </div>
    </Modal>
  )
}

function CancelRouteDialog({ route, onDone, onClose }: { route: RouteView; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/routes/${route.id}/cancel`, { reason: reason.trim() }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not cancel the route")),
  })
  return (
    <Modal title="Cancel route" onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>The loads stay allocated to the same tanker and driver, but are no longer stops on a route.</p>
      <label style={labelStyle}>Reason *</label><input aria-label="Cancel reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} />
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Back</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { if (!reason.trim()) return setErr("Give a reason."); setErr(""); save.mutate() }}>Cancel route</button>
      </div>
    </Modal>
  )
}
