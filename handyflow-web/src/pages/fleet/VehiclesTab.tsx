// src/pages/fleet/VehiclesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, Car, AlertTriangle, ChevronDown, ChevronUp, X,
  Edit2, Eye, AlertCircle, CheckCircle, Wrench, Clock,
} from "lucide-react"

interface Vehicle {
  id: string; registration: string; make: string; model: string
  year: number | null; colour: string | null; vehicleType: string
  status: string; fuelType: string | null
  licenceDiscExpiry: string | null; roadworthyExpiry: string | null; insuranceExpiry: string | null
  currentOdometer: number; lastServiceKm: number; serviceIntervalKm: number
  dueForService: boolean; licenceExpiringSoon: boolean; roadworthyExpiringSoon: boolean
  dailyRate: number | null; assignedDriverName: string | null; notes: string | null
  createdAt: string
}

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string; icon: React.ElementType }> = {
  AVAILABLE:   { color: "#166534", bg: "#DCFCE7", border: "#86EFAC",  label: "Available",   icon: CheckCircle  },
  ON_TRIP:     { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE",  label: "On Trip",     icon: Car          },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A",  label: "Maintenance", icon: Wrench       },
  BREAKDOWN:   { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA",  label: "Breakdown",   icon: AlertTriangle },
  RETIRED:     { color: "#94A3B8", bg: "#F8FAFC", border: "#E2E8F0",  label: "Retired",     icon: Clock        },
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

  const createVehicle = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fleet/vehicles", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fleet-vehicles"] }); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to register vehicle") },
  })

  const updateStatus = useMutation({
    // FIX: was PUT with a comment claiming "avoids CORS preflight failures".
    // That never actually fixed CORS — PUT with a JSON body triggers a
    // preflight too, it just relabeled the endpoint with the wrong HTTP verb.
    // The backend's FleetController now uses @PatchMapping again (the
    // correct verb for "change one field on an existing resource"), so this
    // must match it — see FleetController.java's comment for the real CORS
    // fix (allow PATCH in your CorsConfigurationSource).
    mutationFn: ({ id, status, note }: { id: string; status: string; note: string }) =>
      apiClient.patch(`/api/v1/fleet/vehicles/${id}/status`, { status, note }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fleet-vehicles"] }); setShowStatus(null); setNewStatus(""); setStatusNote(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update status"),
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
    { label: "Total",       value: vehicles.length,                                              color: "#1B3A6B" },
    { label: "Available",   value: vehicles.filter(v => v.status === "AVAILABLE").length,        color: "#166534" },
    { label: "On Trip",     value: vehicles.filter(v => v.status === "ON_TRIP").length,          color: "#1D4ED8" },
    { label: "Svc Due",     value: vehicles.filter(v => v.dueForService).length,                 color: "#D97706" },
    { label: "Breakdown",   value: vehicles.filter(v => v.status === "BREAKDOWN").length,        color: "#DC2626" },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })
  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[k]}
    </div>
  ) : null

  const StatusBadge = ({ status }: { status: string }) => {
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.AVAILABLE
    const Icon = cfg.icon
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}><Icon size={10} />{cfg.label}</span>
  }

  const ExpiryBadge = ({ label, date }: { label: string; date: string | null }) => {
    if (!date) return null
    const days = daysUntil(date)
    if (days > 60) return null
    const color = days <= 7 ? "#DC2626" : days <= 30 ? "#D97706" : "#64748B"
    const bg    = days <= 7 ? "#FEF2F2" : days <= 30 ? "#FFFBEB" : "#F8FAFC"
    return <span style={{ fontSize: 10, fontWeight: 700, background: bg, color, padding: "1px 7px", borderRadius: 20, flexShrink: 0 }}>{label}: {days}d</span>
  }

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? (s === "ALL" ? "#1B3A6B" : STATUS_CFG[s]?.color ?? "#1B3A6B") : "#F1F5F9",
                color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Register Vehicle
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading fleet...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Car size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No vehicles found</div>
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
              <div key={v.id} style={{ border: `1px solid ${v.status === "BREAKDOWN" ? "#FECACA" : "#E2E8F0"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 48, height: 48, borderRadius: 12, background: "#F8FAFC", border: "1px solid #E2E8F0", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 24, flexShrink: 0 }}>
                      {ICONS[v.vehicleType] ?? "🚘"}
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{v.registration}</span>
                        <span style={{ fontSize: 14, color: "#64748B" }}>{v.make} {v.model}{v.year ? ` (${v.year})` : ""}</span>
                        {v.dueForService && <span style={{ fontSize: 10, fontWeight: 700, background: "#FEF3C7", color: "#D97706", padding: "1px 7px", borderRadius: 20, border: "1px solid #FDE68A", flexShrink: 0 }}>SVC DUE</span>}
                        {hasExpiry && <span style={{ fontSize: 10, fontWeight: 700, background: "#FEF2F2", color: "#DC2626", padding: "1px 7px", borderRadius: 20, flexShrink: 0 }}>EXPIRING</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {v.vehicleType}{v.colour ? ` · ${v.colour}` : ""}{v.fuelType ? ` · ${v.fuelType}` : ""}
                        {v.assignedDriverName ? ` · ${v.assignedDriverName}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" as const }}>
                      <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{fmtOdo(v.currentOdometer ?? 0)}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtR(v.dailyRate)}/day</div>
                    </div>
                    <StatusBadge status={v.status} />
                    <div style={{ display: "flex", gap: 5 }}>
                      <button onClick={() => setViewing(v)} title="View" style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}><Eye size={13} /></button>
                      <button onClick={() => { setShowStatus(v); setNewStatus(v.status); setStatusNote(""); setApiError("") }} title="Change status" style={{ background: "#FEF3C7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#D97706" }}><Edit2 size={13} /></button>
                    </div>
                    <button onClick={() => setExpanded(isOpen ? null : v.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>
                {/* Service bar */}
                <div style={{ padding: "0 20px 12px", background: "#fff" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "#94A3B8", marginBottom: 3 }}>
                    <span>Service interval</span>
                    <span>{kmUsed.toLocaleString()} / {(v.serviceIntervalKm || 10000).toLocaleString()} km</span>
                  </div>
                  <div style={{ height: 5, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
                    <div style={{ height: "100%", width: `${svcPct}%`, borderRadius: 99, background: svcPct >= 100 ? "#DC2626" : svcPct >= 80 ? "#D97706" : "#0D9488", transition: "width 0.4s" }} />
                  </div>
                </div>
                {/* Expanded detail */}
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
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
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                      {v.licenceDiscExpiry && <div style={{ padding: "8px 12px", background: daysUntil(v.licenceDiscExpiry) <= 30 ? "#FEF3C7" : "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12 }}>
                        <div style={{ fontSize: 10, color: "#94A3B8", marginBottom: 2 }}>LICENCE DISC</div>
                        <div style={{ fontWeight: 600, color: daysUntil(v.licenceDiscExpiry) <= 30 ? "#D97706" : "#0F172A" }}>{fmtDate(v.licenceDiscExpiry)}</div>
                      </div>}
                      {v.roadworthyExpiry && <div style={{ padding: "8px 12px", background: daysUntil(v.roadworthyExpiry) <= 30 ? "#FEF3C7" : "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12 }}>
                        <div style={{ fontSize: 10, color: "#94A3B8", marginBottom: 2 }}>ROADWORTHY</div>
                        <div style={{ fontWeight: 600, color: daysUntil(v.roadworthyExpiry) <= 30 ? "#D97706" : "#0F172A" }}>{fmtDate(v.roadworthyExpiry)}</div>
                      </div>}
                      {v.insuranceExpiry && <div style={{ padding: "8px 12px", background: daysUntil(v.insuranceExpiry) <= 30 ? "#FEF3C7" : "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12 }}>
                        <div style={{ fontSize: 10, color: "#94A3B8", marginBottom: 2 }}>INSURANCE</div>
                        <div style={{ fontWeight: 600, color: daysUntil(v.insuranceExpiry) <= 30 ? "#D97706" : "#0F172A" }}>{fmtDate(v.insuranceExpiry)}</div>
                      </div>}
                    </div>
                    {v.notes && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 8, fontSize: 13, color: "#78350F" }}>{v.notes}</div>}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ── Register Vehicle Modal ─────────────────────────────────────────── */}
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
                <select value={form.vehicleType} onChange={e => setForm(f => ({ ...f, vehicleType: e.target.value }))} style={{ ...inp("vehicleType"), background: "#fff" }}>
                  {VEHICLE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Colour</label>
                <input value={form.colour} onChange={e => setForm(f => ({ ...f, colour: e.target.value }))} placeholder="White" style={inp("colour")} />
              </div>
              <div>
                <label style={lbl}>Fuel Type</label>
                <select value={form.fuelType} onChange={e => setForm(f => ({ ...f, fuelType: e.target.value }))} style={{ ...inp("fuelType"), background: "#fff" }}>
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
            <div style={{ marginTop: 10, padding: "8px 12px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 7, fontSize: 12, color: "#0369A1" }}>
              You will be alerted 60, 30, and 7 days before any document expires.
            </div>
          </Sect>

          <Sect title="Service Intervals">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Odometer interval (km)</label>
                <input type="number" value={form.serviceIntervalKm} onChange={e => setForm(f => ({ ...f, serviceIntervalKm: e.target.value }))} placeholder="10000" style={inp("serviceIntervalKm")} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Alert when km since last service exceeds this</div>
              </div>
              <div>
                <label style={lbl}>Time interval (days)</label>
                <input type="number" value={form.serviceIntervalDays} onChange={e => setForm(f => ({ ...f, serviceIntervalDays: e.target.value }))} placeholder="180" style={inp("serviceIntervalDays")} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Also alert after this many days regardless of km</div>
              </div>
            </div>
          </Sect>

          <Sect title="Assignment & Rates">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Assigned Driver</label>
                <input value={form.assignedDriverName} onChange={e => setForm(f => ({ ...f, assignedDriverName: e.target.value }))} placeholder="James Dlamini" style={inp("assignedDriverName")} />
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

      {/* ── Status Modal ──────────────────────────────────────────────────── */}
      {showStatus && (
        <Overlay onClose={() => { setShowStatus(null); setApiError("") }}>
          <MHead title={`Update Status — ${showStatus.registration}`} onClose={() => { setShowStatus(null); setApiError("") }} />
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 16 }}>
            {STATUSES.map(s => {
              const cfg = STATUS_CFG[s]; const Icon = cfg.icon; const sel = newStatus === s
              return (
                <button key={s} onClick={() => setNewStatus(s)}
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `2px solid ${sel ? cfg.color : "#E2E8F0"}`, borderRadius: 10, cursor: "pointer", background: sel ? cfg.bg : "#fff", textAlign: "left" as const, width: "100%" }}>
                  <div style={{ width: 32, height: 32, borderRadius: "50%", background: `${cfg.color}18`, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}><Icon size={15} color={cfg.color} /></div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, color: sel ? cfg.color : "#0F172A" }}>{cfg.label}</div>
                    <div style={{ fontSize: 11, color: "#94A3B8" }}>{STATUS_DESC[s]}</div>
                  </div>
                  {sel && <CheckCircle size={16} color={cfg.color} />}
                </button>
              )
            })}
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Note <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
            <input value={statusNote} onChange={e => setStatusNote(e.target.value)} placeholder={newStatus === "BREAKDOWN" ? "Describe the issue..." : ""} style={{ ...inp("_"), width: "100%" }} />
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowStatus(null); setApiError("") }} onSubmit={() => updateStatus.mutate({ id: showStatus.id, status: newStatus, note: statusNote })} loading={updateStatus.isPending} label="Update Status" disabled={!newStatus || newStatus === showStatus.status} />
        </Overlay>
      )}

      {/* ── View Vehicle Modal ────────────────────────────────────────────── */}
      {viewing && (
        <Overlay onClose={() => setViewing(null)}>
          <div style={{ background: "linear-gradient(135deg, #1B3A6B 0%, #0F2A52 100%)", margin: "-28px -28px 24px", padding: "24px 28px", borderRadius: "16px 16px 0 0" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                <div style={{ fontSize: 36 }}>{ICONS[viewing.vehicleType] ?? "🚘"}</div>
                <div>
                  <h3 style={{ margin: "0 0 4px", fontSize: 20, fontWeight: 800, color: "#fff" }}>{viewing.registration}</h3>
                  <div style={{ fontSize: 13, color: "rgba(255,255,255,0.7)" }}>{viewing.make} {viewing.model}{viewing.year ? ` · ${viewing.year}` : ""}{viewing.colour ? ` · ${viewing.colour}` : ""}</div>
                </div>
              </div>
              <button onClick={() => setViewing(null)} style={{ background: "rgba(255,255,255,0.15)", border: "none", borderRadius: 8, cursor: "pointer", color: "#fff", padding: 6, display: "flex" }}><X size={18} /></button>
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" }}>
              <StatusBadge status={viewing.status} />
              {viewing.dueForService && <span style={{ background: "#FEF3C7", color: "#D97706", padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: "1px solid #FDE68A" }}>Service Due</span>}
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 16 }}>
            {[
              { l: "Odometer",     v: fmtOdo(viewing.currentOdometer ?? 0) },
              { l: "Last Service", v: fmtOdo(viewing.lastServiceKm ?? 0) },
              { l: "Daily Rate",   v: fmtR(viewing.dailyRate) },
              { l: "Driver",       v: viewing.assignedDriverName || "Unassigned" },
            ].map(item => (
              <div key={item.l} style={{ padding: "10px 14px", background: "#F8FAFC", borderRadius: 8 }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
              </div>
            ))}
          </div>
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "#64748B", marginBottom: 6 }}>
              <span>Service progress</span>
              <span>{((viewing.currentOdometer ?? 0) - (viewing.lastServiceKm ?? 0)).toLocaleString()} / {(viewing.serviceIntervalKm || 10000).toLocaleString()} km</span>
            </div>
            <div style={{ height: 8, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
              {(() => { const pct = Math.min(100, ((viewing.currentOdometer ?? 0) - (viewing.lastServiceKm ?? 0)) / (viewing.serviceIntervalKm || 10000) * 100); return <div style={{ height: "100%", width: `${pct}%`, background: pct >= 100 ? "#DC2626" : pct >= 80 ? "#D97706" : "#0D9488", borderRadius: 99 }} /> })()}
            </div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => { setViewing(null); setShowStatus(viewing); setNewStatus(viewing.status); setStatusNote("") }} style={{ flex: 1, padding: "10px", background: "#FFFBEB", color: "#D97706", border: "1px solid #FDE68A", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Change Status</button>
            <button onClick={() => setViewing(null)} style={{ padding: "10px 16px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 13, cursor: "pointer", color: "#374151" }}>Close</button>
          </div>
        </Overlay>
      )}
    </div>
  )
}

// ── Shared ─────────────────────────────────────────────────────────────────────

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 620, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button><button onClick={onSubmit} disabled={loading || disabled} style={{ padding: "9px 22px", background: loading || disabled ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return <div style={{ marginBottom: 20 }}><div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>{children}</div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{msg}</div>
}
const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const inp  = (k: string): React.CSSProperties => ({ width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", outline: "none" })
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
