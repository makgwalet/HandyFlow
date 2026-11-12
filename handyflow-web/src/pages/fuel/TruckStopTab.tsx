// E01: Truck stop. Services sold (each is a Bookings service), reserving a bay or slot for a truck, and what is booked.
// Reservations are Bookings records, so times, bays and double-booking rules are Bookings'.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtR } from "./forecourtOps"
import {
  KINDS, KIND_LABEL, isOpen, statusLabel, timeText, validateReservation,
  type Offering, type ServiceOption, type Slot, type Reservation,
} from "./truckStopOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const BASE = "/api/v1/fuel/truck-stop"
const today = () => new Date().toISOString().slice(0, 10)

function Err({ text }: { text: string }) { return <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, margin: "10px 0 0" }}>{text}</p> }

export default function TruckStopTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_TRUCKSTOP_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<"service" | "reserve" | null>(null)
  const [err, setErr] = useState("")
  const offerings = useQuery<Offering[]>({ queryKey: ["fuel-ts-offerings"], queryFn: async () => unwrap(await apiClient.get(`${BASE}/offerings`)) })
  const reservations = useQuery<Reservation[]>({ queryKey: ["fuel-ts-reservations"], queryFn: async () => unwrap(await apiClient.get(`${BASE}/reservations`)) })
  const refresh = () => { for (const k of ["fuel-ts-offerings", "fuel-ts-reservations", "fuel-ts-slots"]) qc.invalidateQueries({ queryKey: [k] }) }
  const toggle = useMutation({
    mutationFn: (o: Offering) => apiClient.put(`${BASE}/offerings/${o.id}/status`, { active: !o.active }),
    onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not change the service")),
  })
  const act = useMutation({
    mutationFn: (a: { id: string; what: "cancel" | "complete" | "no-show" }) => apiClient.post(`${BASE}/reservations/${a.id}/${a.what}`, a.what === "cancel" ? {} : undefined),
    onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not update the reservation")),
  })
  const offs = offerings.data ?? []
  const rows = reservations.data ?? []

  return (
    <div>
      <p style={{ margin: "0 0 12px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Services the truck stop sells are set up in Bookings (times, price, bays). Choose which of them are truck stop services, then reserve them for a truck. Nothing is invoiced or paid from here.
      </p>
      {err && <Err text={err} />}
      <div style={{ display: "flex", alignItems: "center", margin: "6px 0 8px" }}>
        <h3 style={{ margin: 0, fontSize: 14, flex: 1 }}>Services</h3>
        {canManage && <button style={btnGhost} onClick={() => setDialog("service")}>Add service</button>}
        {canManage && <button style={{ ...btnPrimary, marginLeft: 8 }} disabled={offs.filter(o => o.active).length === 0} onClick={() => setDialog("reserve")}>Reserve</button>}
      </div>
      {offs.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No truck stop services yet. Create the service in Bookings first, then add it here.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Service</th><th style={th}>Kind</th><th style={th}>Length</th><th style={th}>Price</th><th style={th}>Bays / stations</th><th style={th}></th></tr></thead>
            <tbody>
              {offs.map(o => (
                <tr key={o.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}>{o.serviceName}{!o.active && <> <Chip bg="var(--hf-surface-alt)" fg="var(--hf-text-muted)">Off</Chip></>}</td>
                  <td style={td}>{KIND_LABEL[o.kind] ?? o.kind}</td>
                  <td style={td}>{o.durationMinutes ? `${o.durationMinutes} min` : "–"}</td>
                  <td style={td}>{o.price == null ? "–" : fmtR(o.price)}</td>
                  <td style={td}>{o.resources.length ? o.resources.map(r => r.name).join(", ") : "Not limited"}</td>
                  <td style={td}>{canManage && <button style={btnGhost} disabled={toggle.isPending} onClick={() => toggle.mutate(o)}>{o.active ? "Switch off" : "Switch on"}</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h3 style={{ margin: "18px 0 8px", fontSize: 14 }}>Reservations</h3>
      {rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No reservations yet.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>When</th><th style={th}>Truck</th><th style={th}>Driver</th><th style={th}>Service</th><th style={th}>Bay</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}>{r.date ?? "–"} {timeText(r.start)}–{timeText(r.end)}</td>
                  <td style={td}>{r.vehicleRegistration}</td>
                  <td style={td}>{r.driverName ?? "–"}{r.driverPhone ? ` · ${r.driverPhone}` : ""}</td>
                  <td style={td}>{r.serviceName ?? "–"}{r.bookingNumber ? <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{r.bookingNumber}</div> : null}</td>
                  <td style={td}>{r.resourceName ?? "–"}</td>
                  <td style={td}>{statusLabel(r.status)}</td>
                  <td style={td}>{canManage && isOpen(r.status) && (
                    <span style={{ display: "flex", gap: 6 }}>
                      <button style={btnGhost} disabled={act.isPending} onClick={() => act.mutate({ id: r.id, what: "complete" })}>Complete</button>
                      <button style={btnGhost} disabled={act.isPending} onClick={() => act.mutate({ id: r.id, what: "no-show" })}>No-show</button>
                      <button style={btnGhost} disabled={act.isPending} onClick={() => act.mutate({ id: r.id, what: "cancel" })}>Cancel</button>
                    </span>)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {dialog === "service" && <ServiceDialog onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog === "reserve" && <ReserveDialog offerings={offs.filter(o => o.active)} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function ServiceDialog({ onDone, onClose }: { onDone: () => void; onClose: () => void }) {
  const [serviceId, setServiceId] = useState(""); const [kind, setKind] = useState("PARKING"); const [err, setErr] = useState("")
  const avail = useQuery<ServiceOption[]>({ queryKey: ["fuel-ts-available"], queryFn: async () => unwrap(await apiClient.get(`${BASE}/available-services`)) })
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/offerings`, { bookingsServiceId: serviceId, kind }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not add the service")),
  })
  return (
    <Modal title="Add truck stop service" onClose={onClose}>
      <label style={labelStyle}>Bookings service *</label>
      <select aria-label="Bookings service" value={serviceId} onChange={e => setServiceId(e.target.value)} style={inputStyle}>
        <option value="">Choose…</option>
        {(avail.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name} ({s.durationMinutes} min)</option>)}
      </select>
      {avail.data && avail.data.length === 0 && <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Every Bookings service is already a truck stop service, or none exists yet. Create it in Bookings first.</p>}
      <label style={{ ...labelStyle, marginTop: 10 }}>Kind *</label>
      <select aria-label="Kind" value={kind} onChange={e => setKind(e.target.value)} style={inputStyle}>{KINDS.map(k => <option key={k} value={k}>{KIND_LABEL[k]}</option>)}</select>
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { if (!serviceId) return setErr("Choose the Bookings service."); setErr(""); save.mutate() }}>Add service</button>
      </div>
    </Modal>
  )
}

function ReserveDialog({ offerings, onDone, onClose }: { offerings: Offering[]; onDone: () => void; onClose: () => void }) {
  const [offeringId, setOfferingId] = useState(offerings[0]?.id ?? ""); const [resourceId, setResourceId] = useState(""); const [date, setDate] = useState(today())
  const [start, setStart] = useState(""); const [reg, setReg] = useState(""); const [driver, setDriver] = useState(""); const [phone, setPhone] = useState(""); const [err, setErr] = useState("")
  const offering = offerings.find(o => o.id === offeringId)
  const slots = useQuery<Slot[]>({
    queryKey: ["fuel-ts-slots", offeringId, date, resourceId], enabled: !!offeringId && !!date,
    queryFn: async () => unwrap(await apiClient.get(`${BASE}/offerings/${offeringId}/slots`, { params: { date, resourceId: resourceId || undefined } })),
  })
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/reservations`, { offeringId, resourceId: resourceId || null, date, start, vehicleRegistration: reg, driverName: driver || null, driverPhone: phone || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not reserve")),
  })
  return (
    <Modal title="Reserve for a truck" onClose={onClose}>
      <label style={labelStyle}>Service *</label>
      <select aria-label="Service" value={offeringId} onChange={e => { setOfferingId(e.target.value); setResourceId(""); setStart("") }} style={inputStyle}>
        {offerings.map(o => <option key={o.id} value={o.id}>{o.serviceName}</option>)}
      </select>
      {offering && offering.resources.length > 0 && <>
        <label style={{ ...labelStyle, marginTop: 10 }}>Bay / station *</label>
        <select aria-label="Bay" value={resourceId} onChange={e => { setResourceId(e.target.value); setStart("") }} style={inputStyle}>
          <option value="">Choose…</option>{offering.resources.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
        </select></>}
      <label style={{ ...labelStyle, marginTop: 10 }}>Date *</label>
      <input aria-label="Date" type="date" value={date} onChange={e => { setDate(e.target.value); setStart("") }} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Time *</label>
      <select aria-label="Time" value={start} onChange={e => setStart(e.target.value)} style={inputStyle}>
        <option value="">{slots.isLoading ? "Loading…" : (slots.data ?? []).length === 0 ? "No free times" : "Choose…"}</option>
        {(slots.data ?? []).map(s => <option key={s.start} value={s.start}>{s.label}</option>)}
      </select>
      <label style={{ ...labelStyle, marginTop: 10 }}>Vehicle registration *</label>
      <input aria-label="Vehicle registration" value={reg} onChange={e => setReg(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Driver</label><input aria-label="Driver" value={driver} onChange={e => setDriver(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Driver phone</label><input aria-label="Driver phone" value={phone} onChange={e => setPhone(e.target.value)} style={inputStyle} />
      <Err text={err} />
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateReservation(offering, resourceId, date, start, reg); if (v) return setErr(v); setErr(""); save.mutate() }}>Reserve</button>
      </div>
    </Modal>
  )
}
