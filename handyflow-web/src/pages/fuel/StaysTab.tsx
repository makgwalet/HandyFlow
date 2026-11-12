// Phase 7d: overnight stays at the truck stop, kept by Fuel because Bookings only holds a slot within one day. Nothing is invoiced here.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtR } from "./orderOps"
import { emptyStay, validateStay, validateSettings, nightsText, stayActions, STATUS_LABEL, type Stay, type StayForm, type ParkingSettings, type Occupancy } from "./stayOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const todayStr = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export default function StaysTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_TRUCKSTOP_MANAGE"])
  const qc = useQueryClient()
  const today = todayStr()
  const [dialog, setDialog] = useState<"book" | null>(null)
  const [dated, setDated] = useState<{ stay: Stay; kind: "extend" | "checkOut" } | null>(null)
  const [cancelling, setCancelling] = useState<Stay | null>(null)
  const [err, setErr] = useState("")
  const settings = useQuery<ParkingSettings>({ queryKey: ["fuel-stay-settings"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/truck-stop/parking-settings")) })
  const occ = useQuery<Occupancy>({ queryKey: ["fuel-stay-occupancy"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/truck-stop/stays/occupancy")) })
  const stays = useQuery<Stay[]>({ queryKey: ["fuel-stays"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/truck-stop/stays")) })
  const refresh = () => { for (const k of ["fuel-stays", "fuel-stay-occupancy", "fuel-stay-settings"]) qc.invalidateQueries({ queryKey: [k] }) }
  const act = useMutation({ mutationFn: (v: { id: string; path: string }) => apiClient.post(`/api/v1/fuel/truck-stop/stays/${v.id}/${v.path}`), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "That did not work")) })
  const rows = stays.data ?? []
  const rateSet = settings.data?.nightlyRate != null
  return (
    <div>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>
        Overnight parking. Each night from arrival up to the departure date is charged at the nightly rate in force when the stay was booked, with a minimum of one night. Nothing is invoiced from here; the amount is what the stay comes to.
      </p>
      <SettingsBox settings={settings.data} canManage={canManage} onSaved={refresh} />
      {occ.data && (
        <div style={{ ...card, padding: "10px 14px", marginBottom: 12, display: "flex", gap: 24, flexWrap: "wrap", fontSize: 13 }}>
          <span><strong>{occ.data.inYard}</strong> in the yard today</span>
          <span><strong>{occ.data.arrivals}</strong> due to arrive</span>
          <span><strong>{occ.data.departures}</strong> due to leave</span>
          <span>{occ.data.bays == null ? "No bay limit set" : <><strong>{occ.data.free}</strong> of {occ.data.bays} bays free</>}</span>
        </div>
      )}
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>{canManage && <button style={btnPrimary} onClick={() => setDialog("book")}>Book a stay</button>}</div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{err}</p>}
      {stays.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : stays.isError ? <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load stays.</p> : rows.length === 0 ? (
        <p style={{ color: "var(--hf-text-muted)" }}>No overnight stays yet.</p>
      ) : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Truck</th><th style={th}>Arrives</th><th style={th}>Leaves</th><th style={th}>Nights</th><th style={th}>Amount</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>{rows.map(s => (
              <tr key={s.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={td}><strong>{s.vehicleRegistration}</strong><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{[s.driverName, s.driverPhone].filter(Boolean).join(" · ")}</div></td>
                <td style={td}>{s.arrivalDate}</td>
                <td style={td}>{s.actualDepartureDate ?? s.departureDate}{s.actualDepartureDate && s.actualDepartureDate !== s.departureDate && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>planned {s.departureDate}</div>}</td>
                <td style={td}>{nightsText(s)}</td>
                <td style={td}>{fmtR(s.amount)}<div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{fmtR(s.ratePerNight)} a night</div></td>
                <td style={td}><Chip bg={s.status === "CHECKED_IN" ? "var(--hf-success-soft-strong)" : s.status === "BOOKED" ? "var(--hf-warning-soft)" : "var(--hf-surface-muted)"} fg={s.status === "CHECKED_IN" ? "var(--hf-success-text-strong)" : s.status === "BOOKED" ? "var(--hf-warning-text)" : "var(--hf-text-muted)"}>{STATUS_LABEL[s.status]}</Chip>
                  {s.overstaying && <> <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Past its departure date</Chip></>}
                  {s.cancelledReason && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{s.cancelledReason}</div>}</td>
                <td style={td}>{canManage && stayActions(s, today).map(a =>
                  a === "checkIn" ? <button key={a} style={btnGhost} onClick={() => act.mutate({ id: s.id, path: "check-in" })}>Check in</button>
                  : a === "checkOut" ? <button key={a} style={btnGhost} onClick={() => setDated({ stay: s, kind: "checkOut" })}>Check out</button>
                  : a === "extend" ? <button key={a} style={btnGhost} onClick={() => setDated({ stay: s, kind: "extend" })}>Change departure</button>
                  : a === "cancel" ? <button key={a} style={btnGhost} onClick={() => setCancelling(s)}>Cancel</button>
                  : <button key={a} style={btnGhost} onClick={() => act.mutate({ id: s.id, path: "no-show" })}>No-show</button>)}</td>
              </tr>))}</tbody>
          </table>
        </div>
      )}
      {dialog === "book" && <BookDialog today={today} rateSet={rateSet} onDone={refresh} onClose={() => setDialog(null)} />}
      {dated && <DateDialog stay={dated.stay} kind={dated.kind} today={today} onDone={refresh} onClose={() => setDated(null)} />}
      {cancelling && <CancelDialog stay={cancelling} onDone={refresh} onClose={() => setCancelling(null)} />}
    </div>
  )
}

function SettingsBox({ settings, canManage, onSaved }: { settings?: ParkingSettings; canManage: boolean; onSaved: () => void }) {
  const [rate, setRate] = useState<string | null>(null); const [bays, setBays] = useState<string | null>(null); const [err, setErr] = useState("")
  const r = rate ?? (settings?.nightlyRate == null ? "" : String(settings.nightlyRate)); const b = bays ?? (settings?.bays == null ? "" : String(settings.bays))
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fuel/truck-stop/parking-settings", { nightlyRate: r.trim() === "" ? null : Number(r), bays: b.trim() === "" ? null : Number(b) }),
    onSuccess: () => { setErr(""); setRate(null); setBays(null); onSaved() }, onError: (e: any) => setErr(msg(e, "Could not save the settings")),
  })
  return (
    <div style={{ ...card, padding: 14, marginBottom: 12 }}>
      <div style={{ display: "flex", gap: 12, alignItems: "flex-end", flexWrap: "wrap" }}>
        <div><label style={labelStyle} htmlFor="st-rate">Nightly rate (R)</label><input id="st-rate" inputMode="decimal" value={r} disabled={!canManage} onChange={e => setRate(e.target.value)} style={{ ...inputStyle, width: 140 }} /></div>
        <div><label style={labelStyle} htmlFor="st-bays">Parking bays</label><input id="st-bays" inputMode="numeric" value={b} placeholder="No limit" disabled={!canManage} onChange={e => setBays(e.target.value)} style={{ ...inputStyle, width: 140 }} /></div>
        {canManage && <button style={btnPrimary} disabled={save.isPending || (rate === null && bays === null)} onClick={() => { const m = validateSettings(r, b); if (m) return setErr(m); setErr(""); save.mutate() }}>Save settings</button>}
      </div>
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 6 }}>Both are the company's own. A stay keeps the rate it was booked at, so changing it only affects new stays. With no bay count, nothing is limited.</div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "6px 0 0" }}>{err}</p>}
    </div>
  )
}

function BookDialog({ today, rateSet, onDone, onClose }: { today: string; rateSet: boolean; onDone: () => void; onClose: () => void }) {
  const [f, setF] = useState<StayForm>(emptyStay(today)); const [err, setErr] = useState("")
  const set = (k: keyof StayForm) => (e: React.ChangeEvent<HTMLInputElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const save = useMutation({
    mutationFn: () => apiClient.post("/api/v1/fuel/truck-stop/stays", { vehicleRegistration: f.vehicleRegistration.trim(), driverName: f.driverName.trim() || null, driverPhone: f.driverPhone.trim() || null, arrivalDate: f.arrivalDate, departureDate: f.departureDate, note: f.note.trim() || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not book the stay")),
  })
  return (
    <Modal title="Book an overnight stay" onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <div><label style={labelStyle} htmlFor="bs-reg">Truck registration *</label><input id="bs-reg" value={f.vehicleRegistration} maxLength={20} onChange={set("vehicleRegistration")} style={inputStyle} /></div>
        <div style={{ display: "flex", gap: 10 }}>
          <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="bs-dn">Driver</label><input id="bs-dn" value={f.driverName} maxLength={120} onChange={set("driverName")} style={inputStyle} /></div>
          <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="bs-dp">Phone</label><input id="bs-dp" value={f.driverPhone} maxLength={40} onChange={set("driverPhone")} style={inputStyle} /></div>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="bs-in">Arrives *</label><input id="bs-in" type="date" value={f.arrivalDate} onChange={set("arrivalDate")} style={inputStyle} /></div>
          <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="bs-out">Leaves *</label><input id="bs-out" type="date" value={f.departureDate} onChange={set("departureDate")} style={inputStyle} /></div>
        </div>
        <div><label style={labelStyle} htmlFor="bs-note">Note</label><input id="bs-note" value={f.note} maxLength={300} onChange={set("note")} style={inputStyle} /></div>
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const m = validateStay(f, today, rateSet); if (m) return setErr(m); setErr(""); save.mutate() }}>Book stay</button>
      </div>
    </Modal>
  )
}

function DateDialog({ stay, kind, today, onDone, onClose }: { stay: Stay; kind: "extend" | "checkOut"; today: string; onDone: () => void; onClose: () => void }) {
  const [date, setDate] = useState(kind === "checkOut" ? today : stay.departureDate); const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => kind === "checkOut" ? apiClient.post(`/api/v1/fuel/truck-stop/stays/${stay.id}/check-out`, { date }) : apiClient.put(`/api/v1/fuel/truck-stop/stays/${stay.id}/departure`, { departureDate: date }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "That did not work")),
  })
  const title = kind === "checkOut" ? `Check out ${stay.vehicleRegistration}` : `Change departure of ${stay.vehicleRegistration}`
  return (
    <Modal title={title} onClose={onClose}>
      <label style={labelStyle} htmlFor="sd-date">{kind === "checkOut" ? "Left on *" : "New departure date *"}</label>
      <input id="sd-date" type="date" value={date} onChange={e => setDate(e.target.value)} style={inputStyle} />
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => {
          if (!date) return setErr("Give the date.")
          if (kind === "extend" && date <= stay.arrivalDate) return setErr("The departure must be after the arrival.")
          if (kind === "checkOut" && date < stay.arrivalDate) return setErr("The departure cannot be before the arrival.")
          setErr(""); save.mutate()
        }}>{kind === "checkOut" ? "Check out" : "Save"}</button>
      </div>
    </Modal>
  )
}

function CancelDialog({ stay, onDone, onClose }: { stay: Stay; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const save = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fuel/truck-stop/stays/${stay.id}/cancel`, { reason: reason.trim() }), onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not cancel the stay")) })
  return (
    <Modal title={`Cancel stay for ${stay.vehicleRegistration}`} onClose={onClose}>
      <label style={labelStyle} htmlFor="sc-reason">Reason *</label>
      <input id="sc-reason" value={reason} maxLength={300} onChange={e => setReason(e.target.value)} style={inputStyle} />
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Keep stay</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { if (!reason.trim()) return setErr("Give a reason for cancelling the stay."); setErr(""); save.mutate() }}>Cancel stay</button>
      </div>
    </Modal>
  )
}
