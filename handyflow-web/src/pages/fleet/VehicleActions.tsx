// src/pages/fleet/VehicleActions.tsx
//
// The three things you can do to a vehicle from the Vehicles screen: register a new one, change its status, assign its driver.
// Each is a modal that sends its own request and closes itself; the screen only decides which one is open.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorMessage, fieldErrorsOf, unwrapList } from "./apiShape"
import { X, AlertCircle, CheckCircle } from "lucide-react"
import { STATUS_CFG } from "./vehicleStatus"

const plainInput: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, background: "var(--hf-surface)", outline: "none" }

/** What the modals need to know about a vehicle. */
export interface ActionVehicle { id: string; registration: string; status: string; assignedDriverId?: string | null }
interface Driver { id: string; firstName: string; lastName: string; status: string }


const STATUS_DESC: Record<string, string> = {
  AVAILABLE:   "Ready for assignment",
  ON_TRIP:     "Currently on a trip",
  MAINTENANCE: "Undergoing service or repairs",
  BREAKDOWN:   "Unserviceable — requires attention",
  RETIRED:     "Permanently decommissioned",
}

const VEHICLE_TYPES = ["SEDAN","SUV","BAKKIE","TRUCK","MINIBUS","VAN","MOTORCYCLE","OTHER"]
const STATUSES      = ["AVAILABLE","ON_TRIP","MAINTENANCE","BREAKDOWN","RETIRED"]
const FUEL_TYPES    = ["PETROL","DIESEL","ELECTRIC","HYBRID","LPG","OTHER"]


const EMPTY_FORM = {
  registration: "", make: "", model: "", year: "",
  vehicleType: "BAKKIE", colour: "", fuelType: "DIESEL",
  licenceDiscExpiry: "", roadworthyExpiry: "", insuranceExpiry: "",
  dailyRate: "", serviceIntervalKm: "10000", serviceIntervalDays: "",
  assignedDriverName: "", vin: "", notes: "",
}


/** Every list of vehicles on the Fleet screens is refreshed after a change. */
const refreshVehicles = (qc: ReturnType<typeof useQueryClient>) => { qc.invalidateQueries({ queryKey: ["fleet-vehicles"] }); qc.invalidateQueries({ queryKey: ["fleet", "list"] }) }

export function RegisterVehicleModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState(EMPTY_FORM), [fieldErrors, setFieldErrors] = useState<Record<string, string>>({}), [apiError, setApiError] = useState("")
  const createVehicle = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/api/v1/fleet/vehicles", body),
    onSuccess: () => { refreshVehicles(qc); onClose() },
    onError: (e: unknown) => { const fields = fieldErrorsOf(e); if (fields) setFieldErrors(fields); else setApiError(errorMessage(e, "Failed to register vehicle")) },
  })
  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.registration.trim()) errs.registration = "Registration is required"
    if (!form.make.trim())         errs.make         = "Make is required"
    if (!form.model.trim())        errs.model        = "Model is required"
    if (form.year && (isNaN(Number(form.year)) || Number(form.year) < 1950 || Number(form.year) > new Date().getFullYear() + 1))
      errs.year = "Enter a valid year"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }
  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "var(--hf-danger)" : "var(--hf-border)"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "var(--hf-danger-soft)" : "var(--hf-surface)", outline: "none",
  })
  return (
        <Overlay>
          <MHead title="Register Vehicle" onClose={() => onClose()} />
          <Sect title="Vehicle Details">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Registration *</label>
                <input autoFocus value={form.registration}
                  onChange={e => { setForm(f => ({ ...f, registration: e.target.value.toUpperCase() })); setFieldErrors(f => omit(f,"registration")) }}
                  placeholder="GP 12 34 JHB" style={inp("registration")} />
                <FErr message={fieldErrors.registration} />
              </div>
              <div>
                <label style={lbl}>Make *</label>
                <input value={form.make} onChange={e => { setForm(f => ({ ...f, make: e.target.value })); setFieldErrors(f => omit(f,"make")) }} placeholder="Toyota" style={inp("make")} />
                <FErr message={fieldErrors.make} />
              </div>
              <div>
                <label style={lbl}>Model *</label>
                <input value={form.model} onChange={e => { setForm(f => ({ ...f, model: e.target.value })); setFieldErrors(f => omit(f,"model")) }} placeholder="Hilux" style={inp("model")} />
                <FErr message={fieldErrors.model} />
              </div>
              <div>
                <label style={lbl}>Year</label>
                <input type="number" value={form.year} onChange={e => { setForm(f => ({ ...f, year: e.target.value })); setFieldErrors(f => omit(f,"year")) }} placeholder="2022" style={inp("year")} />
                <FErr message={fieldErrors.year} />
              </div>
              <div>
                <label style={lbl}>Type</label>
                <select value={form.vehicleType} onChange={e => setForm(f => ({ ...f, vehicleType: e.target.value }))} style={{ ...inp("vehicleType"), background: "var(--hf-surface)" }}>
                  {VEHICLE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Colour</label>
                <input value={form.colour} onChange={e => setForm(f => ({ ...f, colour: e.target.value }))} placeholder="White" style={inp("colour")} />
              </div>
              <div>
                <label style={lbl}>Fuel Type</label>
                <select value={form.fuelType} onChange={e => setForm(f => ({ ...f, fuelType: e.target.value }))} style={{ ...inp("fuelType"), background: "var(--hf-surface)" }}>
                  {FUEL_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>VIN / Chassis Number</label>
                <input value={form.vin} onChange={e => setForm(f => ({ ...f, vin: e.target.value }))} placeholder="ABC123..." style={inp("vin")} />
              </div>
            </div>
          </Sect>

          <Sect title="Compliance Dates">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Licence Disc Expiry</label>
                <input type="date" value={form.licenceDiscExpiry} onChange={e => setForm(f => ({ ...f, licenceDiscExpiry: e.target.value }))} style={inp("licenceDiscExpiry")} />
              </div>
              <div>
                <label style={lbl}>Roadworthy Expiry</label>
                <input type="date" value={form.roadworthyExpiry} onChange={e => setForm(f => ({ ...f, roadworthyExpiry: e.target.value }))} style={inp("roadworthyExpiry")} />
              </div>
              <div>
                <label style={lbl}>Insurance Expiry</label>
                <input type="date" value={form.insuranceExpiry} onChange={e => setForm(f => ({ ...f, insuranceExpiry: e.target.value }))} style={inp("insuranceExpiry")} />
              </div>
            </div>
            <div style={{ marginTop: 10, padding: "8px 12px", background: "var(--hf-sky-soft)", border: "1px solid var(--hf-sky-border)", borderRadius: 7, fontSize: 12, color: "var(--hf-sky-text-strong)" }}>
              You will be alerted 60, 30, and 7 days before any document expires.
            </div>
          </Sect>

          <Sect title="Service Intervals">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Odometer interval (km)</label>
                <input type="number" value={form.serviceIntervalKm} onChange={e => setForm(f => ({ ...f, serviceIntervalKm: e.target.value }))} placeholder="10000" style={inp("serviceIntervalKm")} />
                <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>Alert when km since last service exceeds this</div>
              </div>
              <div>
                <label style={lbl}>Time interval (days)</label>
                <input type="number" value={form.serviceIntervalDays} onChange={e => setForm(f => ({ ...f, serviceIntervalDays: e.target.value }))} placeholder="180" style={inp("serviceIntervalDays")} />
                <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>Also alert after this many days regardless of km</div>
              </div>
            </div>
          </Sect>

          <Sect title="Assignment & Rates">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Assigned Driver (free text)</label>
                <input value={form.assignedDriverName} onChange={e => setForm(f => ({ ...f, assignedDriverName: e.target.value }))} placeholder="James Dlamini" style={inp("assignedDriverName")} />
                <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>For a linked driver with compliance tracking, use "Assign driver" after registering — see Drivers tab</div>
              </div>
              <div>
                <label style={lbl}>Daily Rate (R)</label>
                <input type="number" value={form.dailyRate} onChange={e => setForm(f => ({ ...f, dailyRate: e.target.value }))} placeholder="850" style={inp("dailyRate")} />
              </div>
            </div>
          </Sect>

          <div>
            <label style={lbl}>Notes</label>
            <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={2} style={{ ...inp("notes"), resize: "vertical" as const }} placeholder="Condition, known issues, history..." />
          </div>

          {apiError && <ErrBanner msg={apiError} />}
          <MFoot
            onCancel={() => onClose()}
            onSubmit={() => { if (validate()) createVehicle.mutate({ registration: form.registration, make: form.make, model: form.model, year: form.year ? Number(form.year) : null, vehicleType: form.vehicleType, colour: form.colour || null, fuelType: form.fuelType || null, vin: form.vin || null, licenceDiscExpiry: form.licenceDiscExpiry || null, roadworthyExpiry: form.roadworthyExpiry || null, insuranceExpiry: form.insuranceExpiry || null, serviceIntervalKm: form.serviceIntervalKm ? Number(form.serviceIntervalKm) : 10000, serviceIntervalDays: form.serviceIntervalDays ? Number(form.serviceIntervalDays) : null, dailyRate: form.dailyRate ? Number(form.dailyRate) : null, assignedDriverName: form.assignedDriverName || null, notes: form.notes || null }) }}
            loading={createVehicle.isPending}
            label="Register Vehicle"
          />
        </Overlay>
  )
}

export function StatusModal({ vehicle, onClose }: { vehicle: ActionVehicle; onClose: () => void }) {
  const qc = useQueryClient()
  const [newStatus, setNewStatus] = useState(vehicle.status), [statusNote, setStatusNote] = useState(""), [apiError, setApiError] = useState("")
  const updateStatus = useMutation({
    mutationFn: ({ id, status, note }: { id: string; status: string; note: string }) => apiClient.patch(`/api/v1/fleet/vehicles/${id}/status`, { status, note }),
    onSuccess: () => { refreshVehicles(qc); onClose() },
    onError: (e: unknown) => setApiError(errorMessage(e, "Failed to update status")),
  })
    return (
        <Overlay>
          <MHead title={`Update Status — ${vehicle.registration}`} onClose={onClose} />
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 16 }}>
            {STATUSES.map(s => {
              const cfg = STATUS_CFG[s]; const Icon = cfg.icon; const sel = newStatus === s
              return (
                <button key={s} onClick={() => setNewStatus(s)}
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `2px solid ${sel ? cfg.color : "var(--hf-border)"}`, borderRadius: 10, cursor: "pointer", background: sel ? cfg.bg : "var(--hf-surface)", textAlign: "left" as const, width: "100%" }}>
                  <div style={{ width: 32, height: 32, borderRadius: "50%", background: `color-mix(in srgb, ${cfg.color} 9%, transparent)`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}><Icon size={15} style={{ color: cfg.color }} /></div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, color: sel ? cfg.color : "var(--hf-text)" }}>{cfg.label}</div>
                    <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{STATUS_DESC[s]}</div>
                  </div>
                  {sel && <CheckCircle size={16} style={{ color: cfg.color }} />}
                </button>
              )
            })}
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Note <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>(optional)</span></label>
            <input value={statusNote} onChange={e => setStatusNote(e.target.value)} placeholder={newStatus === "BREAKDOWN" ? "Describe the issue..." : ""} style={{ ...plainInput, width: "100%" }} />
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={onClose} onSubmit={() => updateStatus.mutate({ id: vehicle.id, status: newStatus, note: statusNote })} loading={updateStatus.isPending} label="Update Status" disabled={!newStatus || newStatus === vehicle.status} />
        </Overlay>
  )
}

export function AssignDriverModal({ vehicle, onClose }: { vehicle: ActionVehicle; onClose: () => void }) {
  const qc = useQueryClient()
  const [selectedDriverId, setSelectedDriverId] = useState(vehicle.assignedDriverId ?? ""), [apiError, setApiError] = useState("")
  const { data: drivers = [] } = useQuery<Driver[]>({ queryKey: ["fleet-drivers"], queryFn: async () => unwrapList<Driver>(await apiClient.get("/api/v1/fleet/drivers?size=200")) })
  const activeDrivers = drivers.filter(d => d.status === "ACTIVE")
  const assignDriver = useMutation({
    mutationFn: ({ id, driverId }: { id: string; driverId: string | null }) => apiClient.patch(`/api/v1/fleet/vehicles/${id}/driver`, { driverId }),
    onSuccess: () => { refreshVehicles(qc); onClose() },
    onError: (e: unknown) => setApiError(errorMessage(e, "Failed to update driver assignment")),
  })
    return (
        <Overlay>
          <MHead title={`Assign Driver — ${vehicle.registration}`} onClose={onClose} />
          <div style={{ marginBottom: 16 }}>
            <label style={lbl}>Driver</label>
            {activeDrivers.length === 0 ? (
              <div style={{ padding: "10px 12px", background: "var(--hf-warning-soft-strong)", border: "1px solid var(--hf-warning-border-strong)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text-deep)" }}>
                No active drivers registered yet — add one from the Drivers tab first.
              </div>
            ) : (
              <select value={selectedDriverId} onChange={e => setSelectedDriverId(e.target.value)} style={{ ...plainInput, width: "100%", background: "var(--hf-surface)" }}>
                <option value="">Unassigned</option>
                {activeDrivers.map(d => <option key={d.id} value={d.id}>{d.firstName} {d.lastName}</option>)}
              </select>
            )}
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 6 }}>
              This links the vehicle to a driver record with licence/PrDP compliance tracking. Select "Unassigned" to remove the current assignment.
            </div>
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot
            onCancel={onClose}
            onSubmit={() => assignDriver.mutate({ id: vehicle.id, driverId: selectedDriverId || null })}
            loading={assignDriver.isPending}
            label="Save Assignment"
          />
        </Overlay>
  )
}

function FErr({ message }: { message?: string }) {
  return message ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>
      <AlertCircle size={12} />{message}
    </div>
  ) : null
}

export function StatusBadge({ status }: { status: string }) {
  const cfg = STATUS_CFG[status] ?? STATUS_CFG.AVAILABLE
  const Icon = cfg.icon
  return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}><Icon size={10} />{cfg.label}</span>
}

function Overlay({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 620, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button><button onClick={onSubmit} disabled={loading || disabled} style={{ padding: "9px 22px", background: loading || disabled ? "var(--hf-text-faint)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return <div style={{ marginBottom: 20 }}><div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid var(--hf-border-subtle)" }}>{title}</div>{children}</div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{msg}</div>
}
const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }

