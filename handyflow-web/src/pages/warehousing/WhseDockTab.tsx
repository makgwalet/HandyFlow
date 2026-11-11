// src/pages/warehousing/WhseDockTab.tsx
//
// Dock appointments: book a carrier into a dock door for a delivery (inbound) or a collection (outbound), and walk it through the day:
// scheduled, arrived at the gate, on the door, finished. A door cannot be double-booked.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, inputStyle, labelStyle, ReasonModal } from "./whseUi"
import { DOCK_STATUS, dayBounds, lateLabel, localDay, nextStep, slotProblem, toInstant, type DockStatus } from "./dock"

export interface Appointment {
  id: string; direction: "INBOUND" | "OUTBOUND"; dockLocationId: string; dockCode: string; startsAt: string; endsAt: string; carrier: string | null
  vehicleRegistration: string | null; driverName: string | null; shipmentReference: string | null; manifestNumber: string | null; notes: string | null
  status: DockStatus; arrivedAt: string | null; cancelReason: string | null; lateMinutes: number
}
interface Location { id: string; code: string; active: boolean; locationType?: string }
interface Client { id: string; tradingName: string }
interface Shipment { id: string; referenceNumber: string | null; status: string }
interface Manifest { id: string; manifestNumber: string; status: string }

const time = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

function BookForm({ docks, onDone }: { docks: Location[]; onDone: () => void }) {
  const [direction, setDirection] = useState<"INBOUND" | "OUTBOUND">("INBOUND")
  const [dock, setDock] = useState("")
  const [start, setStart] = useState("")
  const [end, setEnd] = useState("")
  const [carrier, setCarrier] = useState("")
  const [vehicle, setVehicle] = useState("")
  const [driver, setDriver] = useState("")
  const [clientId, setClientId] = useState("")
  const [shipmentId, setShipmentId] = useState("")
  const [manifestId, setManifestId] = useState("")
  const { data: clients = [] } = useQuery<Client[]>({ queryKey: ["whse-clients"], enabled: direction === "INBOUND", queryFn: async () => (await apiClient.get("/api/v1/warehousing/clients")).data })
  const { data: shipments = [] } = useQuery<Shipment[]>({ queryKey: ["whse-inbound", clientId], enabled: direction === "INBOUND" && !!clientId,
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/inbound-shipments`)).data })
  const { data: manifests = [] } = useQuery<Manifest[]>({ queryKey: ["whse-manifests"], enabled: direction === "OUTBOUND", queryFn: async () => (await apiClient.get("/api/v1/warehousing/dispatch/manifests")).data })
  const problem = !dock ? "Choose a dock door." : slotProblem(start, end)
  const book = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/warehousing/dock-appointments", {
      direction, dockLocationId: dock, startsAt: toInstant(start), endsAt: toInstant(end), carrier: carrier || null, vehicleRegistration: vehicle || null, driverName: driver || null,
      inboundShipmentId: direction === "INBOUND" ? shipmentId || null : null, manifestId: direction === "OUTBOUND" ? manifestId || null : null,
    }),
    onSuccess: () => { setCarrier(""); setVehicle(""); setDriver(""); setShipmentId(""); setManifestId(""); onDone() },
  })
  return (
    <div style={{ ...card, padding: 14, marginBottom: 16 }}>
      <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 10px" }}>Book a slot</p>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
        <div><label style={labelStyle} htmlFor="dk-dir">Type</label>
          <select id="dk-dir" style={inputStyle} value={direction} onChange={e => setDirection(e.target.value as "INBOUND" | "OUTBOUND")}>
            <option value="INBOUND">Delivery (inbound)</option><option value="OUTBOUND">Collection (outbound)</option></select></div>
        <div><label style={labelStyle} htmlFor="dk-door">Dock door</label>
          <select id="dk-door" style={inputStyle} value={dock} onChange={e => setDock(e.target.value)}>
            <option value="">Choose…</option>{docks.map(d => <option key={d.id} value={d.id}>{d.code}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="dk-start">From</label><input id="dk-start" type="datetime-local" style={inputStyle} value={start} onChange={e => setStart(e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="dk-end">To</label><input id="dk-end" type="datetime-local" style={inputStyle} value={end} onChange={e => setEnd(e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="dk-carrier">Carrier</label><input id="dk-carrier" style={{ ...inputStyle, width: 140 }} value={carrier} onChange={e => setCarrier(e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="dk-veh">Vehicle</label><input id="dk-veh" style={{ ...inputStyle, width: 110 }} value={vehicle} onChange={e => setVehicle(e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="dk-drv">Driver</label><input id="dk-drv" style={{ ...inputStyle, width: 130 }} value={driver} onChange={e => setDriver(e.target.value)} /></div>
        {direction === "INBOUND" ? (
          <>
            <div><label style={labelStyle} htmlFor="dk-client">Client (optional)</label>
              <select id="dk-client" style={inputStyle} value={clientId} onChange={e => { setClientId(e.target.value); setShipmentId("") }}>
                <option value="">—</option>{clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}</select></div>
            {clientId && <div><label style={labelStyle} htmlFor="dk-ship">Shipment</label>
              <select id="dk-ship" style={inputStyle} value={shipmentId} onChange={e => setShipmentId(e.target.value)}>
                <option value="">—</option>{shipments.filter(s => s.status === "EXPECTED" || s.status === "PARTIALLY_RECEIVED").map(s => <option key={s.id} value={s.id}>{s.referenceNumber ?? s.id.slice(0, 8)}</option>)}</select></div>}
          </>
        ) : (
          <div><label style={labelStyle} htmlFor="dk-man">Manifest (optional)</label>
            <select id="dk-man" style={inputStyle} value={manifestId} onChange={e => setManifestId(e.target.value)}>
              <option value="">—</option>{manifests.filter(m => m.status === "OPEN" || m.status === "LOADING").map(m => <option key={m.id} value={m.id}>{m.manifestNumber}</option>)}</select></div>
        )}
        <button style={btnPrimary} disabled={problem != null || book.isPending} title={problem ?? undefined} onClick={() => book.mutate()}>Book</button>
      </div>
      <ErrorLine error={book.error} fallback="Could not book that slot" />
    </div>
  )
}

function Row({ a, canWork, onChange }: { a: Appointment; canWork: boolean; onChange: () => void }) {
  const [asking, setAsking] = useState<"cancel" | "no-show" | null>(null)
  const st = DOCK_STATUS[a.status]
  const step = nextStep(a.status)
  const act = useMutation({ mutationFn: async (path: string) => apiClient.post(`/api/v1/warehousing/dock-appointments/${a.id}/${path}`), onSuccess: onChange })
  const reasoned = useMutation({
    mutationFn: async (v: { path: string; reason: string }) => apiClient.post(`/api/v1/warehousing/dock-appointments/${a.id}/${v.path}`, { reason: v.reason }),
    onSuccess: () => { setAsking(null); onChange() },
  })
  const late = lateLabel(a.lateMinutes)
  return (
    <div style={{ ...card, padding: "10px 14px", marginBottom: 8, display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap", alignItems: "center" }}>
      <div>
        <p style={{ fontSize: 13.5, fontWeight: 700, margin: 0 }}>
          {time(a.startsAt)}–{time(a.endsAt)} · {a.dockCode} · {a.direction === "INBOUND" ? "Delivery" : "Collection"} <Chip bg={st.bg} fg={st.fg}>{st.label}</Chip>
          {late && <span style={{ fontWeight: 500, color: "var(--hf-danger-text)", marginLeft: 8, fontSize: 12 }}>{late}</span>}
        </p>
        <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: "2px 0 0" }}>
          {[a.carrier, a.vehicleRegistration, a.driverName, a.shipmentReference && `Shipment ${a.shipmentReference}`, a.manifestNumber && `Manifest ${a.manifestNumber}`].filter(Boolean).join(" · ") || "No carrier details"}
          {a.cancelReason ? ` · ${a.cancelReason}` : ""}
        </p>
      </div>
      {canWork && (
        <div style={{ display: "flex", gap: 8 }}>
          {step && <button style={btnPrimary} disabled={act.isPending} onClick={() => act.mutate(step.path)}>{step.label}</button>}
          {a.status === "SCHEDULED" && <button style={btnGhost} onClick={() => setAsking("no-show")}>No-show</button>}
          {(a.status === "SCHEDULED" || a.status === "ARRIVED") && <button style={btnGhost} onClick={() => setAsking("cancel")}>Cancel</button>}
        </div>
      )}
      <ErrorLine error={act.error} fallback="That could not be done" />
      {asking && <ReasonModal title={asking === "cancel" ? "Cancel this appointment" : "Mark as no-show"} confirmLabel={asking === "cancel" ? "Cancel appointment" : "Mark no-show"}
        pending={reasoned.isPending} error={reasoned.error} onConfirm={r => reasoned.mutate({ path: asking, reason: r })} onClose={() => { setAsking(null); reasoned.reset() }} />}
    </div>
  )
}

export default function WhseDockTab() {
  const qc = useQueryClient()
  const canWork = usePermission("WAREHOUSING_MANAGE") || usePermission("WAREHOUSING_ADMIN")
  const [day, setDay] = useState(localDay())
  const { from, to } = dayBounds(day)
  const { data: locations = [] } = useQuery<Location[]>({ queryKey: ["whse-locations"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/locations")).data })
  const docks = locations.filter(l => l.active && l.locationType === "DOCK")
  const { data: items = [], isLoading } = useQuery<Appointment[]>({
    queryKey: ["whse-dock", day],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/dock-appointments?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)).data,
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["whse-dock"] })
  const live = items.filter(a => a.status === "SCHEDULED" || a.status === "ARRIVED" || a.status === "AT_DOCK").length
  return (
    <div>
      {canWork && (docks.length === 0
        ? <p style={{ fontSize: 13, color: "var(--hf-text-faint)", marginBottom: 16 }}>There are no dock doors yet. Add a location of type Dock (Locations) to book appointments.</p>
        : <BookForm docks={docks} onDone={refresh} />)}
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12, flexWrap: "wrap" }}>
        <label style={{ ...labelStyle, margin: 0 }} htmlFor="dk-day">Day</label>
        <input id="dk-day" type="date" style={inputStyle} value={day} onChange={e => e.target.value && setDay(e.target.value)} />
        <button style={btnGhost} onClick={() => setDay(localDay())}>Today</button>
        <span style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>{items.length} appointment{items.length === 1 ? "" : "s"}{live > 0 ? ` · ${live} still to do` : ""}</span>
      </div>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : items.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Nothing booked for this day.</p>
        : items.map(a => <Row key={a.id} a={a} canWork={canWork} onChange={refresh} />)}
    </div>
  )
}
