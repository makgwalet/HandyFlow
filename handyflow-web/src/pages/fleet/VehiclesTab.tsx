import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Car, AlertTriangle, ChevronDown, ChevronUp, X } from "lucide-react"

interface Vehicle {
  id: string
  registration: string
  make: string
  model: string
  year: number | null
  vehicleType: string
  colour: string | null
  status: string
  currentOdometer: number
  lastServiceOdometer: number
  serviceIntervalKm: number
  dueForService: boolean
  fuelType: string | null
  dailyRate: number | null
  assignedDriverName: string | null
  createdAt: string
}

const STATUS_CONFIG: Record<string, { color: string; bg: string; label: string }> = {
  AVAILABLE:   { color: "#166534", bg: "#DCFCE7", label: "Available"   },
  ON_TRIP:     { color: "#1D4ED8", bg: "#EFF6FF", label: "On Trip"     },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB", label: "Maintenance" },
  BREAKDOWN:   { color: "#DC2626", bg: "#FEF2F2", label: "Breakdown"   },
  RETIRED:     { color: "#94A3B8", bg: "#F8FAFC", label: "Retired"     },
}

const TYPE_ICONS: Record<string, string> = {
  SEDAN: "🚗", SUV: "🚙", BAKKIE: "🛻", TRUCK: "🚛",
  MINIBUS: "🚐", VAN: "🚌", MOTORCYCLE: "🏍️", OTHER: "🚘",
}

const VEHICLE_TYPES = ["SEDAN","SUV","BAKKIE","TRUCK","MINIBUS","VAN","MOTORCYCLE","OTHER"]
const STATUSES      = ["AVAILABLE","ON_TRIP","MAINTENANCE","BREAKDOWN","RETIRED"]
const FUEL_TYPES    = ["PETROL","DIESEL","ELECTRIC","HYBRID","OTHER"]

export default function VehiclesTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd]       = useState(false)
  const [showStatus, setShowStatus] = useState<Vehicle | null>(null)
  const [expanded, setExpanded]     = useState<string | null>(null)
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [newStatus, setNewStatus]   = useState("")
  const [error, setError]           = useState("")

  const [form, setForm] = useState({
    registration: "", make: "", model: "", year: "",
    vehicleType: "BAKKIE", colour: "", fuelType: "DIESEL",
    dailyRate: "", serviceIntervalKm: "10000",
  })

  const { data: vehicles = [], isLoading } = useQuery({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fleet/vehicles?size=50")
      return res.data.content as Vehicle[]
    },
  })

  const createVehicle = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fleet/vehicles", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fleet-vehicles"] })
      setShowAdd(false)
      setForm({ registration: "", make: "", model: "", year: "", vehicleType: "BAKKIE", colour: "", fuelType: "DIESEL", dailyRate: "", serviceIntervalKm: "10000" })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to register vehicle"),
  })

  const updateStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      apiClient.patch(`/api/v1/fleet/vehicles/${id}/status`, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fleet-vehicles"] })
      setShowStatus(null)
      setNewStatus("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to update status"),
  })

  const filtered = filterStatus === "ALL" ? vehicles : vehicles.filter(v => v.status === filterStatus)
  const fmtR = (n: number | null) => n ? `R ${n.toLocaleString("en-ZA")}` : "—"

  const stats = [
    { label: "Total Vehicles", value: vehicles.length,                                          color: "#1B3A6B" },
    { label: "Available",      value: vehicles.filter(v => v.status === "AVAILABLE").length,    color: "#166534" },
    { label: "On Trip",        value: vehicles.filter(v => v.status === "ON_TRIP").length,      color: "#1D4ED8" },
    { label: "Due Service",    value: vehicles.filter(v => v.dueForService).length,             color: "#D97706" },
  ]

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div style={{ display: "flex", gap: 6 }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)} style={{
              padding: "6px 12px", borderRadius: 20, border: "1px solid",
              borderColor: filterStatus === s ? "#1B3A6B" : "#E2E8F0",
              background: filterStatus === s ? "#1B3A6B" : "#fff",
              color: filterStatus === s ? "#fff" : "#64748B",
              fontSize: 12, fontWeight: 500, cursor: "pointer",
            }}>
              {s === "ALL" ? "All" : STATUS_CONFIG[s]?.label}
            </button>
          ))}
        </div>
        <button onClick={() => setShowAdd(true)} style={btnPrimary}>
          <Plus size={15} /> Register Vehicle
        </button>
      </div>

      {/* Vehicle list */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading vehicles...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Car size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No vehicles found</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Register your fleet vehicles to start tracking trips and services.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map(v => {
            const cfg = STATUS_CONFIG[v.status] || STATUS_CONFIG.AVAILABLE
            const isOpen = expanded === v.id
            const kmUsed = v.currentOdometer - (v.lastServiceOdometer || 0)
            const servicePct = Math.min(100, (kmUsed / v.serviceIntervalKm) * 100)

            return (
              <div key={v.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                    <div style={{ width: 46, height: 46, borderRadius: 12, background: "#F1F5F9", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 22, flexShrink: 0 }}>
                      {TYPE_ICONS[v.vehicleType] || "🚘"}
                    </div>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{v.registration}</span>
                        <span style={{ color: "#64748B", fontSize: 14 }}>{v.make} {v.model}</span>
                        {v.year && <span style={{ color: "#94A3B8", fontSize: 13 }}>({v.year})</span>}
                        {v.dueForService && (
                          <span style={{ display: "flex", alignItems: "center", gap: 4, background: "#FEF3C7", color: "#D97706", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
                            <AlertTriangle size={10} /> SERVICE DUE
                          </span>
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 1 }}>
                        {v.vehicleType}
                        {v.colour && ` · ${v.colour}`}
                        {v.fuelType && ` · ${v.fuelType}`}
                        {v.assignedDriverName && ` · Driver: ${v.assignedDriverName}`}
                      </div>
                    </div>
                  </div>

                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <div style={{ textAlign: "right", marginRight: 8 }}>
                      <div style={{ fontSize: 13, color: "#475569", fontWeight: 600 }}>{v.currentOdometer.toLocaleString()} km</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtR(v.dailyRate)}/day</div>
                    </div>
                    <span style={{ background: cfg.bg, color: cfg.color, padding: "4px 12px", borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{cfg.label}</span>
                    <button onClick={() => { setShowStatus(v); setNewStatus(v.status); setError("") }} style={btnOutline}>Update Status</button>
                    <button onClick={() => setExpanded(isOpen ? null : v.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>

                {/* Service progress bar */}
                <div style={{ padding: "0 20px 14px", background: "#fff" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#94A3B8", marginBottom: 4 }}>
                    <span>Service interval</span>
                    <span>{kmUsed.toLocaleString()} / {v.serviceIntervalKm.toLocaleString()} km since last service</span>
                  </div>
                  <div style={{ height: 6, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
                    <div style={{
                      height: "100%", borderRadius: 99, width: `${servicePct}%`,
                      background: servicePct >= 100 ? "#DC2626" : servicePct >= 80 ? "#D97706" : "#0D9488",
                      transition: "width 0.5s ease",
                    }} />
                  </div>
                </div>

                {/* Expanded details */}
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "14px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16 }}>
                      {[
                        { label: "Vehicle Type",    value: v.vehicleType },
                        { label: "Fuel Type",       value: v.fuelType || "—" },
                        { label: "Current Odometer",value: `${v.currentOdometer.toLocaleString()} km` },
                        { label: "Last Service At", value: `${v.lastServiceOdometer.toLocaleString()} km` },
                        { label: "Service Interval",value: `${v.serviceIntervalKm.toLocaleString()} km` },
                        { label: "Daily Rate",      value: fmtR(v.dailyRate) },
                        { label: "Colour",          value: v.colour || "—" },
                        { label: "Assigned Driver", value: v.assignedDriverName || "Unassigned" },
                      ].map(item => (
                        <div key={item.label}>
                          <div style={{ fontSize: 11, color: "#94A3B8", fontWeight: 600, textTransform: "uppercase" as const, letterSpacing: "0.05em", marginBottom: 3 }}>{item.label}</div>
                          <div style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{item.value}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Register Vehicle Modal */}
      {showAdd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 540, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Register Vehicle</h3>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Registration *</label>
                <input value={form.registration} onChange={e => setForm(f => ({ ...f, registration: e.target.value }))} placeholder="GP-12-34-JHB" style={inp} />
              </div>
              <div><label style={lbl}>Make *</label><input value={form.make} onChange={e => setForm(f => ({ ...f, make: e.target.value }))} placeholder="Toyota" style={inp} /></div>
              <div><label style={lbl}>Model *</label><input value={form.model} onChange={e => setForm(f => ({ ...f, model: e.target.value }))} placeholder="Hilux" style={inp} /></div>
              <div><label style={lbl}>Year</label><input type="number" value={form.year} onChange={e => setForm(f => ({ ...f, year: e.target.value }))} placeholder="2022" style={inp} /></div>
              <div>
                <label style={lbl}>Vehicle Type</label>
                <select value={form.vehicleType} onChange={e => setForm(f => ({ ...f, vehicleType: e.target.value }))} style={sel}>
                  {VEHICLE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div><label style={lbl}>Colour</label><input value={form.colour} onChange={e => setForm(f => ({ ...f, colour: e.target.value }))} placeholder="White" style={inp} /></div>
              <div>
                <label style={lbl}>Fuel Type</label>
                <select value={form.fuelType} onChange={e => setForm(f => ({ ...f, fuelType: e.target.value }))} style={sel}>
                  {FUEL_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div><label style={lbl}>Daily Rate (R)</label><input type="number" value={form.dailyRate} onChange={e => setForm(f => ({ ...f, dailyRate: e.target.value }))} placeholder="850" style={inp} /></div>
              <div><label style={lbl}>Service Interval (km)</label><input type="number" value={form.serviceIntervalKm} onChange={e => setForm(f => ({ ...f, serviceIntervalKm: e.target.value }))} placeholder="10000" style={inp} /></div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => createVehicle.mutate({
                  registration: form.registration, make: form.make, model: form.model,
                  year: form.year ? Number(form.year) : null,
                  vehicleType: form.vehicleType, colour: form.colour || null,
                  fuelType: form.fuelType || null,
                  dailyRate: form.dailyRate ? Number(form.dailyRate) : null,
                  serviceIntervalKm: Number(form.serviceIntervalKm),
                })}
                disabled={!form.registration || !form.make || !form.model || createVehicle.isPending}
                style={submitBtn}
              >
                {createVehicle.isPending ? "Registering..." : "Register Vehicle"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Update Status Modal */}
      {showStatus && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 400, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Update Status — {showStatus.registration}</h3>
              <button onClick={() => setShowStatus(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {STATUSES.map(s => {
                const cfg = STATUS_CONFIG[s]
                return (
                  <button key={s} onClick={() => setNewStatus(s)} style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: "2px solid", borderColor: newStatus === s ? cfg.color : "#E2E8F0", borderRadius: 10, background: newStatus === s ? cfg.bg : "#fff", cursor: "pointer", textAlign: "left" as const }}>
                    <span style={{ width: 10, height: 10, borderRadius: "50%", background: cfg.color, flexShrink: 0 }} />
                    <span style={{ fontWeight: 600, color: newStatus === s ? cfg.color : "#374151" }}>{cfg.label}</span>
                  </button>
                )
              })}
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowStatus(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => updateStatus.mutate({ id: showStatus.id, status: newStatus })} disabled={!newStatus || newStatus === showStatus.status || updateStatus.isPending} style={submitBtn}>
                {updateStatus.isPending ? "Updating..." : "Update Status"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnOutline: React.CSSProperties = { background: "#fff", color: "#475569", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 14px", fontSize: 13, cursor: "pointer" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }
const sel: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties = { padding: "9px 20px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: "pointer" }
