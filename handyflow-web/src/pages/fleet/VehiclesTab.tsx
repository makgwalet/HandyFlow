// src/pages/fleet/VehiclesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, Car, AlertTriangle, ChevronDown, ChevronUp, X,
  Edit2, Eye, AlertCircle, CheckCircle, Wrench, Clock, UserCog,
} from "lucide-react"

interface Vehicle {
  id: string; registration: string; make: string; model: string
  year: number | null; colour: string | null; vehicleType: string
  status: string; fuelType: string | null
  licenceDiscExpiry: string | null; roadworthyExpiry: string | null; insuranceExpiry: string | null
  currentOdometer: number; lastServiceKm: number; serviceIntervalKm: number
  dueForService: boolean; licenceExpiringSoon: boolean; roadworthyExpiringSoon: boolean
  dailyRate: number | null; assignedDriverName: string | null; assignedDriverId: string | null; notes: string | null
  createdAt: string
}

interface Driver { id: string; firstName: string; lastName: string; status: string }

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string; icon: React.ElementType }> = {
  AVAILABLE:   { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", border: "var(--hf-success-border)",  label: "Available",   icon: CheckCircle  },
  ON_TRIP:     { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", border: "var(--hf-info-border)",  label: "On Trip",     icon: Car          },
  MAINTENANCE: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", border: "var(--hf-warning-border)",  label: "Maintenance", icon: Wrench       },
  BREAKDOWN:   { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", border: "var(--hf-danger-border)",  label: "Breakdown",   icon: AlertTriangle },
  RETIRED:     { color: "var(--hf-text-faint)", bg: "var(--hf-surface-muted)", border: "var(--hf-border)",  label: "Retired",     icon: Clock        },
}

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
const ICONS: Record<string, string> = { SEDAN:"🚗", SUV:"🚙", BAKKIE:"🛻", TRUCK:"🚛", MINIBUS:"🚐", VAN:"🚌", MOTORCYCLE:"🏍️", OTHER:"🚘" }

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtOdo  = (km: number) => `${Number(km).toLocaleString("en-ZA")} km`
const fmtR    = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA")}` : "—"
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const daysUntil = (d: string | null) => d ? Math.ceil((new Date(d).getTime() - Date.now()) / 86400000) : 999

const EMPTY_FORM = {
  registration: "", make: "", model: "", year: "",
  vehicleType: "BAKKIE", colour: "", fuelType: "DIESEL",
  licenceDiscExpiry: "", roadworthyExpiry: "", insuranceExpiry: "",
  dailyRate: "", serviceIntervalKm: "10000", serviceIntervalDays: "",
  assignedDriverName: "", vin: "", notes: "",
}

export default function VehiclesTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd]         = useState(false)
  const [showStatus, setShowStatus]   = useState<Vehicle | null>(null)
  const [showAssign, setShowAssign]   = useState<Vehicle | null>(null)
  const [selectedDriverId, setSelectedDriverId] = useState("")
  const [viewing, setViewing]         = useState<Vehicle | null>(null)
  const [expanded, setExpanded]       = useState<string | null>(null)
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [form, setForm]               = useState(EMPTY_FORM)
  const [newStatus, setNewStatus]     = useState("")
  const [statusNote, setStatusNote]   = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError]       = useState("")

  const { data: vehicles = [], isLoading } = useQuery<Vehicle[]>({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/vehicles?size=200")),
  })

  const { data: drivers = [] } = useQuery<Driver[]>({
    queryKey: ["fleet-drivers"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/drivers?size=200")),
  })
  const activeDrivers = drivers.filter(d => d.status === "ACTIVE")

  const createVehicle = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fleet/vehicles", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fleet-vehicles"] }); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to register vehicle") },
  })

  const updateStatus = useMutation({
    mutationFn: ({ id, status, note }: { id: string; status: string; note: string }) =>
      apiClient.patch(`/api/v1/fleet/vehicles/${id}/status`, { status, note }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fleet-vehicles"] }); setShowStatus(null); setNewStatus(""); setStatusNote(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update status"),
  })

  const assignDriver = useMutation({
    mutationFn: ({ id, driverId }: { id: string; driverId: string | null }) =>
      apiClient.patch(`/api/v1/fleet/vehicles/${id}/driver`, { driverId }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fleet-vehicles"] }); setShowAssign(null); setSelectedDriverId(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update driver assignment"),
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

  const filtered = (vehicles as Vehicle[]).filter(v => filterStatus === "ALL" || v.status === filterStatus)

  const stats = [
    { label: "Total",       value: vehicles.length,                                              color: "var(--hf-primary-text)" },
    { label: "Available",   value: vehicles.filter(v => v.status === "AVAILABLE").length,        color: "var(--hf-success-text-strong)" },
    { label: "On Trip",     value: vehicles.filter(v => v.status === "ON_TRIP").length,          color: "var(--hf-info-text)" },
    { label: "Svc Due",     value: vehicles.filter(v => v.dueForService).length,                 color: "var(--hf-warning-text)" },
    { label: "Breakdown",   value: vehicles.filter(v => v.status === "BREAKDOWN").length,        color: "var(--hf-danger-text)" },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "var(--hf-danger)" : "var(--hf-border)"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "var(--hf-danger-soft)" : "var(--hf-surface)", outline: "none",
  })
  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[k]}
    </div>
  ) : null

  const StatusBadge = ({ status }: { status: string }) => {
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.AVAILABLE
    const Icon = cfg.icon
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}><Icon size={10} />{cfg.label}</span>
  }

  const openAssign = (v: Vehicle) => { setShowAssign(v); setSelectedDriverId(v.assignedDriverId ?? ""); setApiError("") }

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? (s === "ALL" ? "var(--hf-primary)" : STATUS_CFG[s]?.color ?? "var(--hf-primary)") : "var(--hf-surface-sunken)",
                color: filterStatus === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Register Vehicle
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading fleet...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <Car size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No vehicles found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map(v => {
            const cfg = STATUS_CFG[v.status] ?? STATUS_CFG.AVAILABLE
            const isOpen = expanded === v.id
            const kmUsed = (v.currentOdometer ?? 0) - (v.lastServiceKm ?? 0)
            const svcPct = Math.min(100, (kmUsed / (v.serviceIntervalKm || 10000)) * 100)
            const hasExpiry = v.licenceExpiringSoon || v.roadworthyExpiringSoon

            return (
              <div key={v.id} style={{ border: `1px solid ${v.status === "BREAKDOWN" ? "var(--hf-danger-border)" : "var(--hf-border)"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "var(--hf-surface)", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 48, height: 48, borderRadius: 12, background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 24, flexShrink: 0 }}>
                      {ICONS[v.vehicleType] ?? "🚘"}
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "var(--hf-text)" }}>{v.registration}</span>
                        <span style={{ fontSize: 14, color: "var(--hf-text-muted)" }}>{v.make} {v.model}{v.year ? ` (${v.year})` : ""}</span>
                        {v.dueForService && <span style={{ fontSize: 10, fontWeight: 700, background: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text)", padding: "1px 7px", borderRadius: 20, border: "1px solid var(--hf-warning-border)", flexShrink: 0 }}>SVC DUE</span>}
                        {hasExpiry && <span style={{ fontSize: 10, fontWeight: 700, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", padding: "1px 7px", borderRadius: 20, flexShrink: 0 }}>EXPIRING</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
                        {v.vehicleType}{v.colour ? ` · ${v.colour}` : ""}{v.fuelType ? ` · ${v.fuelType}` : ""}
                        {v.assignedDriverName ? ` · 👤 ${v.assignedDriverName}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" as const }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{fmtOdo(v.currentOdometer ?? 0)}</div>
                      <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{fmtR(v.dailyRate)}/day</div>
                    </div>
                    <StatusBadge status={v.status} />
                    <div style={{ display: "flex", gap: 5 }}>
                      <button onClick={() => setViewing(v)} title="View" style={{ background: "var(--hf-info-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-info-text)" }}><Eye size={13} /></button>
                      <button onClick={() => openAssign(v)} title="Assign driver" style={{ background: "var(--hf-violet-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-violet-text)" }}><UserCog size={13} /></button>
                      <button onClick={() => { setShowStatus(v); setNewStatus(v.status); setStatusNote(""); setApiError("") }} title="Change status" style={{ background: "var(--hf-warning-soft-strong)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-warning-text)" }}><Edit2 size={13} /></button>
                    </div>
                    <button onClick={() => setExpanded(isOpen ? null : v.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>
                <div style={{ padding: "0 20px 12px", background: "var(--hf-surface)" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "var(--hf-text-faint)", marginBottom: 3 }}>
                    <span>Service interval</span>
                    <span>{kmUsed.toLocaleString()} / {(v.serviceIntervalKm || 10000).toLocaleString()} km</span>
                  </div>
                  <div style={{ height: 5, background: "var(--hf-surface-sunken)", borderRadius: 99, overflow: "hidden" }}>
                    <div style={{ height: "100%", width: `${svcPct}%`, borderRadius: 99, background: svcPct >= 100 ? "var(--hf-danger)" : svcPct >= 80 ? "var(--hf-warning)" : "var(--hf-accent)", transition: "width 0.4s" }} />
                  </div>
                </div>
                {isOpen && (
                  <div style={{ borderTop: "1px solid var(--hf-border-subtle)", padding: "16px 20px", background: "var(--hf-surface-muted)" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 14, marginBottom: 14 }}>
                      {[
                        { l: "Fuel Type",     v: v.fuelType || "—" },
                        { l: "Colour",        v: v.colour || "—" },
                        { l: "Daily Rate",    v: fmtR(v.dailyRate) },
                        { l: "Driver",        v: v.assignedDriverName || "Unassigned" },
                        { l: "Last Service",  v: fmtOdo(v.lastServiceKm ?? 0) },
                        { l: "Svc Interval",  v: fmtOdo(v.serviceIntervalKm || 10000) },
                      ].map(item => (
                        <div key={item.l}>
                          <div style={{ fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                      {v.licenceDiscExpiry && <div style={{ padding: "8px 12px", background: daysUntil(v.licenceDiscExpiry) <= 30 ? "var(--hf-warning-soft-strong)" : "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                        <div style={{ fontSize: 10, color: "var(--hf-text-faint)", marginBottom: 2 }}>LICENCE DISC</div>
                        <div style={{ fontWeight: 600, color: daysUntil(v.licenceDiscExpiry) <= 30 ? "var(--hf-warning-text)" : "var(--hf-text)" }}>{fmtDate(v.licenceDiscExpiry)}</div>
                      </div>}
                      {v.roadworthyExpiry && <div style={{ padding: "8px 12px", background: daysUntil(v.roadworthyExpiry) <= 30 ? "var(--hf-warning-soft-strong)" : "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                        <div style={{ fontSize: 10, color: "var(--hf-text-faint)", marginBottom: 2 }}>ROADWORTHY</div>
                        <div style={{ fontWeight: 600, color: daysUntil(v.roadworthyExpiry) <= 30 ? "var(--hf-warning-text)" : "var(--hf-text)" }}>{fmtDate(v.roadworthyExpiry)}</div>
                      </div>}
                      {v.insuranceExpiry && <div style={{ padding: "8px 12px", background: daysUntil(v.insuranceExpiry) <= 30 ? "var(--hf-warning-soft-strong)" : "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                        <div style={{ fontSize: 10, color: "var(--hf-text-faint)", marginBottom: 2 }}>INSURANCE</div>
                        <div style={{ fontWeight: 600, color: daysUntil(v.insuranceExpiry) <= 30 ? "var(--hf-warning-text)" : "var(--hf-text)" }}>{fmtDate(v.insuranceExpiry)}</div>
                      </div>}
                    </div>
                    {v.notes && <div style={{ marginTop: 10, padding: "8px 12px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text-deep)" }}>{v.notes}</div>}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {showAdd && (
        <Overlay onClose={() => setShowAdd(false)}>
          <MHead title="Register Vehicle" onClose={() => setShowAdd(false)} />
          <Sect title="Vehicle Details">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Registration *</label>
                <input autoFocus value={form.registration}
                  onChange={e => { setForm(f => ({ ...f, registration: e.target.value.toUpperCase() })); setFieldErrors(f => omit(f,"registration")) }}
                  placeholder="GP 12 34 JHB" style={inp("registration")} />
                <FErr k="registration" />
              </div>
              <div>
                <label style={lbl}>Make *</label>
                <input value={form.make} onChange={e => { setForm(f => ({ ...f, make: e.target.value })); setFieldErrors(f => omit(f,"make")) }} placeholder="Toyota" style={inp("make")} />
                <FErr k="make" />
              </div>
              <div>
                <label style={lbl}>Model *</label>
                <input value={form.model} onChange={e => { setForm(f => ({ ...f, model: e.target.value })); setFieldErrors(f => omit(f,"model")) }} placeholder="Hilux" style={inp("model")} />
                <FErr k="model" />
              </div>
              <div>
                <label style={lbl}>Year</label>
                <input type="number" value={form.year} onChange={e => { setForm(f => ({ ...f, year: e.target.value })); setFieldErrors(f => omit(f,"year")) }} placeholder="2022" style={inp("year")} />
                <FErr k="year" />
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
            onCancel={() => setShowAdd(false)}
            onSubmit={() => { if (validate()) createVehicle.mutate({ registration: form.registration, make: form.make, model: form.model, year: form.year ? Number(form.year) : null, vehicleType: form.vehicleType, colour: form.colour || null, fuelType: form.fuelType || null, vin: form.vin || null, licenceDiscExpiry: form.licenceDiscExpiry || null, roadworthyExpiry: form.roadworthyExpiry || null, insuranceExpiry: form.insuranceExpiry || null, serviceIntervalKm: form.serviceIntervalKm ? Number(form.serviceIntervalKm) : 10000, serviceIntervalDays: form.serviceIntervalDays ? Number(form.serviceIntervalDays) : null, dailyRate: form.dailyRate ? Number(form.dailyRate) : null, assignedDriverName: form.assignedDriverName || null, notes: form.notes || null }) }}
            loading={createVehicle.isPending}
            label="Register Vehicle"
          />
        </Overlay>
      )}

      {showStatus && (
        <Overlay onClose={() => { setShowStatus(null); setApiError("") }}>
          <MHead title={`Update Status — ${showStatus.registration}`} onClose={() => { setShowStatus(null); setApiError("") }} />
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
            <input value={statusNote} onChange={e => setStatusNote(e.target.value)} placeholder={newStatus === "BREAKDOWN" ? "Describe the issue..." : ""} style={{ ...inp("_"), width: "100%" }} />
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowStatus(null); setApiError("") }} onSubmit={() => updateStatus.mutate({ id: showStatus.id, status: newStatus, note: statusNote })} loading={updateStatus.isPending} label="Update Status" disabled={!newStatus || newStatus === showStatus.status} />
        </Overlay>
      )}

      {/* ── Assign Driver Modal ───────────────────────────────────────────── */}
      {showAssign && (
        <Overlay onClose={() => { setShowAssign(null); setApiError("") }}>
          <MHead title={`Assign Driver — ${showAssign.registration}`} onClose={() => { setShowAssign(null); setApiError("") }} />
          <div style={{ marginBottom: 16 }}>
            <label style={lbl}>Driver</label>
            {activeDrivers.length === 0 ? (
              <div style={{ padding: "10px 12px", background: "var(--hf-warning-soft-strong)", border: "1px solid var(--hf-warning-border-strong)", borderRadius: 8, fontSize: 13, color: "var(--hf-warning-text-deep)" }}>
                No active drivers registered yet — add one from the Drivers tab first.
              </div>
            ) : (
              <select value={selectedDriverId} onChange={e => setSelectedDriverId(e.target.value)} style={{ ...inp("_"), width: "100%", background: "var(--hf-surface)" }}>
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
            onCancel={() => { setShowAssign(null); setApiError("") }}
            onSubmit={() => assignDriver.mutate({ id: showAssign.id, driverId: selectedDriverId || null })}
            loading={assignDriver.isPending}
            label="Save Assignment"
          />
        </Overlay>
      )}

      {viewing && (
        <Overlay onClose={() => setViewing(null)}>
          <div style={{ background: "linear-gradient(135deg, var(--hf-primary) 0%, var(--hf-primary-hover) 100%)", margin: "-28px -28px 24px", padding: "24px 28px", borderRadius: "16px 16px 0 0" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                <div style={{ fontSize: 36 }}>{ICONS[viewing.vehicleType] ?? "🚘"}</div>
                <div>
                  <h3 style={{ margin: "0 0 4px", fontSize: 20, fontWeight: 800, color: "var(--hf-text-on-solid)" }}>{viewing.registration}</h3>
                  <div style={{ fontSize: 13, color: "rgba(255,255,255,0.7)" }}>{viewing.make} {viewing.model}{viewing.year ? ` · ${viewing.year}` : ""}{viewing.colour ? ` · ${viewing.colour}` : ""}</div>
                </div>
              </div>
              <button onClick={() => setViewing(null)} style={{ background: "rgba(255,255,255,0.15)", border: "none", borderRadius: 8, cursor: "pointer", color: "var(--hf-text-on-solid)", padding: 6, display: "flex" }}><X size={18} /></button>
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
              <StatusBadge status={viewing.status} />
              {viewing.dueForService && <span style={{ background: "var(--hf-warning-soft-strong)", color: "var(--hf-warning-text)", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid var(--hf-warning-border)" }}>Service Due</span>}
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 16 }}>
            {[
              { l: "Odometer",     v: fmtOdo(viewing.currentOdometer ?? 0) },
              { l: "Last Service", v: fmtOdo(viewing.lastServiceKm ?? 0) },
              { l: "Daily Rate",   v: fmtR(viewing.dailyRate) },
              { l: "Driver",       v: viewing.assignedDriverName || "Unassigned" },
            ].map(item => (
              <div key={item.l} style={{ padding: "10px 14px", background: "var(--hf-surface-muted)", borderRadius: 8 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{item.v}</div>
              </div>
            ))}
          </div>
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 6 }}>
              <span>Service progress</span>
              <span>{((viewing.currentOdometer ?? 0) - (viewing.lastServiceKm ?? 0)).toLocaleString()} / {(viewing.serviceIntervalKm || 10000).toLocaleString()} km</span>
            </div>
            <div style={{ height: 8, background: "var(--hf-surface-sunken)", borderRadius: 99, overflow: "hidden" }}>
              {(() => { const pct = Math.min(100, ((viewing.currentOdometer ?? 0) - (viewing.lastServiceKm ?? 0)) / (viewing.serviceIntervalKm || 10000) * 100); return <div style={{ height: "100%", width: `${pct}%`, background: pct >= 100 ? "var(--hf-danger)" : pct >= 80 ? "var(--hf-warning)" : "var(--hf-accent)", borderRadius: 99 }} /> })()}
            </div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => { setViewing(null); openAssign(viewing) }} style={{ flex: 1, padding: "10px", background: "var(--hf-violet-soft)", color: "var(--hf-violet-text)", border: "1px solid var(--hf-violet-border)", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Assign Driver</button>
            <button onClick={() => { setViewing(null); setShowStatus(viewing); setNewStatus(viewing.status); setStatusNote("") }} style={{ flex: 1, padding: "10px", background: "var(--hf-warning-soft)", color: "var(--hf-warning-text)", border: "1px solid var(--hf-warning-border)", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Change Status</button>
            <button onClick={() => setViewing(null)} style={{ padding: "10px 16px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 13, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Close</button>
          </div>
        </Overlay>
      )}
    </div>
  )
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
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
