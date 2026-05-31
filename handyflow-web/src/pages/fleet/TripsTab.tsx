import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Route, CheckCircle, X } from "lucide-react"

interface Vehicle { id: string; registration: string; make: string; model: string; status: string; currentOdometer: number }

interface Trip {
  id: string
  vehicleId: string
  driverName: string | null
  purpose: string | null
  startOdometer: number
  endOdometer: number | null
  distanceKm: number | null
  startedAt: string
  endedAt: string | null
  status: "ACTIVE" | "COMPLETED" | "CANCELLED"
  notes: string | null
  createdAt: string
}

const TRIP_STATUS: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:    { color: "#166534", bg: "#DCFCE7", label: "Active"    },
  COMPLETED: { color: "#0D9488", bg: "#F0FDF4", label: "Completed" },
  CANCELLED: { color: "#94A3B8", bg: "#F8FAFC", label: "Cancelled" },
}

export default function TripsTab() {
  const qc = useQueryClient()
  const [showStart, setShowStart]   = useState(false)
  const [showEnd, setShowEnd]       = useState<Trip | null>(null)
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [error, setError]           = useState("")

  const [startForm, setStartForm] = useState({
    vehicleId: "", driverName: "", purpose: "", startOdometer: "", notes: "",
  })
  const [endForm, setEndForm] = useState({ endOdometer: "" })

  const { data: trips = [], isLoading } = useQuery({
    queryKey: ["fleet-trips"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fleet/trips?size=50&sort=startedAt,desc")
      return res.data.content as Trip[]
    },
  })

  const { data: vehicles = [] } = useQuery({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fleet/vehicles?size=50")
      return res.data.content as Vehicle[]
    },
  })

  const startTrip = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fleet/trips/start", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fleet-trips"] })
      qc.invalidateQueries({ queryKey: ["fleet-vehicles"] })
      setShowStart(false)
      setStartForm({ vehicleId: "", driverName: "", purpose: "", startOdometer: "", notes: "" })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to start trip"),
  })

  const endTrip = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/fleet/trips/${id}/end`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fleet-trips"] })
      qc.invalidateQueries({ queryKey: ["fleet-vehicles"] })
      setShowEnd(null)
      setEndForm({ endOdometer: "" })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to end trip"),
  })

  const availableVehicles = vehicles.filter(v => v.status === "AVAILABLE")
  const vehicleMap = Object.fromEntries(vehicles.map(v => [v.id, v]))
  const filtered = filterStatus === "ALL" ? trips : trips.filter(t => t.status === filterStatus)

  const selectedVehicle = vehicles.find(v => v.id === startForm.vehicleId)

  const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

  const stats = [
    { label: "Total Trips",   value: trips.length,                                          color: "#1B3A6B" },
    { label: "Active",        value: trips.filter(t => t.status === "ACTIVE").length,        color: "#166534" },
    { label: "Completed",     value: trips.filter(t => t.status === "COMPLETED").length,     color: "#0D9488" },
    { label: "Total Distance",value: `${trips.reduce((s, t) => s + (t.distanceKm || 0), 0).toLocaleString()} km`, color: "#475569" },
  ]

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: s.label === "Total Distance" ? 16 : 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div style={{ display: "flex", gap: 6 }}>
          {["ALL", "ACTIVE", "COMPLETED", "CANCELLED"].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)} style={{
              padding: "6px 12px", borderRadius: 20, border: "1px solid",
              borderColor: filterStatus === s ? "#1B3A6B" : "#E2E8F0",
              background: filterStatus === s ? "#1B3A6B" : "#fff",
              color: filterStatus === s ? "#fff" : "#64748B",
              fontSize: 12, fontWeight: 500, cursor: "pointer",
            }}>
              {s === "ALL" ? "All" : TRIP_STATUS[s]?.label}
            </button>
          ))}
        </div>
        <button onClick={() => setShowStart(true)} style={btnPrimary}>
          <Plus size={15} /> Start Trip
        </button>
      </div>

      {/* Trip list */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading trips...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Route size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No trips found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {filtered.map(trip => {
            const cfg = TRIP_STATUS[trip.status]
            const vehicle = vehicleMap[trip.vehicleId]
            return (
              <div key={trip.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "16px 20px", display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                <div style={{ display: "flex", gap: 14 }}>
                  <div style={{ width: 42, height: 42, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Route size={20} color={cfg.color} />
                  </div>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                      <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>
                        {vehicle ? `${vehicle.registration} — ${vehicle.make} ${vehicle.model}` : "Unknown vehicle"}
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>
                      {fmtDate(trip.startedAt)} {fmtTime(trip.startedAt)}
                      {trip.endedAt && ` → ${fmtTime(trip.endedAt)}`}
                      {trip.driverName && ` · Driver: ${trip.driverName}`}
                    </div>
                    {trip.purpose && <div style={{ fontSize: 13, color: "#64748B", marginTop: 2 }}>Purpose: {trip.purpose}</div>}
                    <div style={{ fontSize: 12, color: "#475569", marginTop: 2 }}>
                      Start: {trip.startOdometer.toLocaleString()} km
                      {trip.endOdometer && ` → End: ${trip.endOdometer.toLocaleString()} km`}
                      {trip.distanceKm && <strong style={{ color: "#0D9488" }}> · {trip.distanceKm.toLocaleString()} km travelled</strong>}
                    </div>
                  </div>
                </div>

                <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                  <span style={{ background: cfg.bg, color: cfg.color, padding: "4px 12px", borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{cfg.label}</span>
                  {trip.status === "ACTIVE" && (
                    <button
                      onClick={() => { setShowEnd(trip); setEndForm({ endOdometer: "" }); setError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, background: "#0D9488", color: "#fff", border: "none", borderRadius: 7, padding: "7px 14px", fontSize: 13, fontWeight: 500, cursor: "pointer" }}
                    >
                      <CheckCircle size={13} /> End Trip
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Start Trip Modal */}
      {showStart && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 500, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Start Trip</h3>
              <button onClick={() => { setShowStart(false); setError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Vehicle *</label>
                <select value={startForm.vehicleId} onChange={e => {
                  const v = vehicles.find(v => v.id === e.target.value)
                  setStartForm(f => ({ ...f, vehicleId: e.target.value, startOdometer: v ? v.currentOdometer.toString() : "" }))
                }} style={sel}>
                  <option value="">Select vehicle...</option>
                  {availableVehicles.map(v => <option key={v.id} value={v.id}>{v.registration} — {v.make} {v.model}</option>)}
                </select>
                {availableVehicles.length === 0 && <div style={{ fontSize: 12, color: "#D97706", marginTop: 4 }}>No available vehicles. Update a vehicle status first.</div>}
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div><label style={lbl}>Driver Name</label><input value={startForm.driverName} onChange={e => setStartForm(f => ({ ...f, driverName: e.target.value }))} placeholder="James Dlamini" style={inp} /></div>
                <div>
                  <label style={lbl}>Start Odometer (km) *</label>
                  <input type="number" value={startForm.startOdometer} onChange={e => setStartForm(f => ({ ...f, startOdometer: e.target.value }))} placeholder={selectedVehicle?.currentOdometer.toString()} style={inp} />
                </div>
              </div>
              <div><label style={lbl}>Purpose</label><input value={startForm.purpose} onChange={e => setStartForm(f => ({ ...f, purpose: e.target.value }))} placeholder="Site delivery — Carletonville" style={inp} /></div>
              <div><label style={lbl}>Notes</label><input value={startForm.notes} onChange={e => setStartForm(f => ({ ...f, notes: e.target.value }))} placeholder="Optional notes" style={inp} /></div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => { setShowStart(false); setError("") }} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => startTrip.mutate({
                  vehicleId: startForm.vehicleId,
                  driverName: startForm.driverName || null,
                  purpose: startForm.purpose || null,
                  startOdometer: Number(startForm.startOdometer),
                  startedAt: new Date().toISOString(),
                  notes: startForm.notes || null,
                })}
                disabled={!startForm.vehicleId || !startForm.startOdometer || startTrip.isPending}
                style={submitBtn}
              >
                {startTrip.isPending ? "Starting..." : "Start Trip"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* End Trip Modal */}
      {showEnd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>End Trip</h3>
              <button onClick={() => setShowEnd(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ marginBottom: 14, padding: "10px 14px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 8, fontSize: 13, color: "#0369A1" }}>
              Started at: <strong>{showEnd.startOdometer.toLocaleString()} km</strong>
              {endForm.endOdometer && Number(endForm.endOdometer) > showEnd.startOdometer && (
                <> · Distance: <strong>{(Number(endForm.endOdometer) - showEnd.startOdometer).toLocaleString()} km</strong></>
              )}
            </div>
            <div>
              <label style={lbl}>End Odometer (km) *</label>
              <input type="number" value={endForm.endOdometer} onChange={e => setEndForm(f => ({ ...f, endOdometer: e.target.value }))} placeholder="Enter current odometer reading" style={inp} />
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowEnd(null)} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => endTrip.mutate({ id: showEnd.id, body: { endOdometer: Number(endForm.endOdometer), endedAt: new Date().toISOString() } })}
                disabled={!endForm.endOdometer || Number(endForm.endOdometer) <= showEnd.startOdometer || endTrip.isPending}
                style={submitBtn}
              >
                {endTrip.isPending ? "Ending..." : "End Trip"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }
const sel: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties = { padding: "9px 20px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: "pointer" }
