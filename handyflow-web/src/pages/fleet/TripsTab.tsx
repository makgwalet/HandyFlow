// src/pages/fleet/TripsTab.tsx
// KEY FIX: Trips are vehicle-scoped on the backend:
//   POST /api/v1/fleet/vehicles/{vehicleId}/trips/start
//   POST /api/v1/fleet/vehicles/{vehicleId}/trips/end
//   GET  /api/v1/fleet/trips  (global list — new endpoint added to controller)
// The old frontend called /api/v1/fleet/trips/start which doesn't exist.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Route, CheckCircle, X, AlertCircle, Briefcase, Home } from "lucide-react"

interface Vehicle { id: string; registration: string; make: string; model: string; status: string; currentOdometer: number }

interface Trip {
  id: string; vehicleId: string; registration: string | null
  driverName: string | null; purpose: string | null; tripType: string | null
  startLocation: string | null; endLocation: string | null
  startOdometer: number; endOdometer: number | null; distanceKm: number | null
  startAt: string; endAt: string | null; fuelUsedLitres: number | null
  status: string; notes: string | null; createdAt: string
}

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:    { color: "#166534", bg: "#DCFCE7", label: "Active"    },
  COMPLETED: { color: "#0D9488", bg: "#F0FDF4", label: "Completed" },
  CANCELLED: { color: "#94A3B8", bg: "#F8FAFC", label: "Cancelled" },
}

const TYPE_CFG: Record<string, { color: string; bg: string; icon: React.ElementType; label: string }> = {
  BUSINESS: { color: "#1D4ED8", bg: "#EFF6FF", icon: Briefcase, label: "Business" },
  PRIVATE:  { color: "#7C3AED", bg: "#F5F3FF", icon: Home,      label: "Private"  },
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

const EMPTY_START = { vehicleId: "", driverName: "", purpose: "", tripType: "BUSINESS", startLocation: "", startOdometer: "", notes: "" }

export default function TripsTab() {
  const qc = useQueryClient()
  const [showStart, setShowStart]   = useState(false)
  const [showEnd, setShowEnd]       = useState<Trip | null>(null)
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [filterType, setFilterType] = useState("ALL")
  const [startForm, setStartForm]   = useState(EMPTY_START)
  const [endOdometer, setEndOdometer] = useState("")
  const [endLocation, setEndLocation] = useState("")
  const [fuelUsed, setFuelUsed]     = useState("")
  const [endNotes, setEndNotes]     = useState("")
  const [apiError, setApiError]     = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const { data: trips = [], isLoading } = useQuery<Trip[]>({
    queryKey: ["fleet-trips-all", filterStatus],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "200", sort: "startAt,desc" })
      if (filterStatus !== "ALL") params.set("status", filterStatus)
      return unwrap(await apiClient.get(`/api/v1/fleet/trips?${params}`))
    },
  })

  const { data: vehicles = [] } = useQuery<Vehicle[]>({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/vehicles?size=200")),
  })

  const availableVehicles = (vehicles as Vehicle[]).filter(v => v.status === "AVAILABLE")

  const startTrip = useMutation({
    mutationFn: ({ vehicleId, body }: { vehicleId: string; body: any }) =>
      // CORRECT URL: vehicle-scoped endpoint
      apiClient.post(`/api/v1/fleet/vehicles/${vehicleId}/trips/start`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fleet-trips-all"] })
      qc.invalidateQueries({ queryKey: ["fleet-vehicles"] })
      setShowStart(false); setStartForm(EMPTY_START); setFieldErrors({}); setApiError("")
    },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to start trip") },
  })

  const endTrip = useMutation({
    mutationFn: ({ vehicleId, body }: { vehicleId: string; body: any }) =>
      // CORRECT URL: vehicle-scoped endpoint
      apiClient.post(`/api/v1/fleet/vehicles/${vehicleId}/trips/end`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fleet-trips-all"] })
      qc.invalidateQueries({ queryKey: ["fleet-vehicles"] })
      setShowEnd(null); setEndOdometer(""); setEndLocation(""); setFuelUsed(""); setEndNotes(""); setApiError("")
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to end trip"),
  })

  const validateStart = () => {
    const errs: Record<string, string> = {}
    if (!startForm.vehicleId) errs.vehicleId = "Select a vehicle"
    if (!startForm.startOdometer) errs.startOdometer = "Start odometer is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const displayTrips = filterType === "ALL" ? (trips as Trip[]) : (trips as Trip[]).filter(t => (t.tripType ?? "BUSINESS") === filterType)

  const vehicleMap = Object.fromEntries((vehicles as Vehicle[]).map(v => [v.id, v]))
  const selectedVehicle = (vehicles as Vehicle[]).find(v => v.id === startForm.vehicleId)

  const stats = [
    { label: "Total trips",      value: trips.length,                                                         color: "#1B3A6B" },
    { label: "Active now",       value: trips.filter(t => t.status === "ACTIVE").length,                      color: "#166534" },
    { label: "Total km",         value: `${trips.filter(t => t.status === "COMPLETED").reduce((s, t) => s + (t.distanceKm ?? 0), 0).toLocaleString()} km`, color: "#0D9488" },
    { label: "Business trips",   value: trips.filter(t => (t.tripType ?? "BUSINESS") === "BUSINESS" && t.status === "COMPLETED").length, color: "#1D4ED8" },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: s.label === "Total km" ? 16 : 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* SARS info banner */}
      <div style={{ marginBottom: 18, padding: "10px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 12, color: "#1D4ED8" }}>
        <strong>SARS Logbook:</strong> Classify each trip as Business or Private. Business trips are deductible for travel allowance purposes. Keep odometer readings accurate.
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL","ACTIVE","COMPLETED","CANCELLED"].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? "#1B3A6B" : "#F1F5F9", color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label}
            </button>
          ))}
          <div style={{ width: 1, background: "#E2E8F0" }} />
          {["ALL","BUSINESS","PRIVATE"].map(t => (
            <button key={t} onClick={() => setFilterType(t)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterType === t ? 600 : 400,
                background: filterType === t ? (TYPE_CFG[t]?.color ?? "#1B3A6B") : "#F1F5F9",
                color: filterType === t ? "#fff" : "#64748B" }}>
              {t === "ALL" ? "All types" : t}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowStart(true); setStartForm(EMPTY_START); setFieldErrors({}); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Start Trip
        </button>
      </div>

      {/* Trip list */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading trips...</div>
      ) : displayTrips.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Route size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No trips found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {displayTrips.map(trip => {
            const cfg      = STATUS_CFG[trip.status] ?? STATUS_CFG.COMPLETED
            const typeCfg  = TYPE_CFG[trip.tripType ?? "BUSINESS"] ?? TYPE_CFG.BUSINESS
            const TypeIcon = typeCfg.icon
            const vehicle  = vehicleMap[trip.vehicleId]
            return (
              <div key={trip.id} style={{ background: "#fff", border: `1px solid ${trip.status === "ACTIVE" ? "#86EFAC" : "#E2E8F0"}`, borderLeft: `4px solid ${cfg.color}`, borderRadius: 10, padding: "14px 18px", display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                <div style={{ display: "flex", gap: 12, flex: 1 }}>
                  <div style={{ width: 40, height: 40, borderRadius: 9, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Route size={18} color={cfg.color} />
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>
                        {trip.registration ?? vehicle?.registration ?? "Unknown vehicle"}
                      </span>
                      <span style={{ fontSize: 12, color: "#64748B" }}>{vehicle ? `${vehicle.make} ${vehicle.model}` : ""}</span>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 3, background: typeCfg.bg, color: typeCfg.color, padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>
                        <TypeIcon size={9} />{typeCfg.label}
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: "#64748B", marginBottom: 2 }}>
                      {fmtDate(trip.startAt)} {fmtTime(trip.startAt)}
                      {trip.endAt ? ` → ${fmtTime(trip.endAt)}` : "  (active)"}
                      {trip.driverName && ` · ${trip.driverName}`}
                    </div>
                    {trip.purpose && <div style={{ fontSize: 13, color: "#475569", marginBottom: 2 }}>{trip.purpose}</div>}
                    <div style={{ fontSize: 12, color: "#64748B" }}>
                      {trip.startLocation && `From: ${trip.startLocation}`}
                      {trip.endLocation && ` → ${trip.endLocation}`}
                    </div>
                    <div style={{ display: "flex", gap: 14, fontSize: 12, marginTop: 4 }}>
                      <span>Start: <strong>{trip.startOdometer.toLocaleString()} km</strong></span>
                      {trip.endOdometer && <span>End: <strong>{trip.endOdometer.toLocaleString()} km</strong></span>}
                      {trip.distanceKm && <span style={{ color: "#0D9488", fontWeight: 700 }}>{trip.distanceKm.toLocaleString()} km driven</span>}
                      {trip.fuelUsedLitres && <span style={{ color: "#D97706" }}>{Number(trip.fuelUsedLitres).toFixed(1)} L</span>}
                    </div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0, marginLeft: 10 }}>
                  <span style={{ background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                  {trip.status === "ACTIVE" && (
                    <button onClick={() => { setShowEnd(trip); setEndOdometer(""); setEndLocation(""); setFuelUsed(""); setEndNotes(""); setApiError("") }}
                      style={{ display: "flex", alignItems: "center", gap: 5, background: "#0D9488", color: "#fff", border: "none", borderRadius: 7, padding: "7px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                      <CheckCircle size={13} /> End Trip
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* ── Start Trip Modal ──────────────────────────────────────────────── */}
      {showStart && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 540, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Start Trip</h3>
              <button onClick={() => setShowStart(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Vehicle *</label>
                {availableVehicles.length === 0 ? (
                  <div style={{ padding: "10px 12px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 13, color: "#92400E" }}>
                    No available vehicles. Change a vehicle's status to Available first.
                  </div>
                ) : (
                  <select value={startForm.vehicleId}
                    onChange={e => {
                      const v = (vehicles as Vehicle[]).find(v => v.id === e.target.value)
                      setStartForm(f => ({ ...f, vehicleId: e.target.value, startOdometer: v ? String(v.currentOdometer) : "" }))
                      setFieldErrors(f => omit2(f, "vehicleId"))
                    }}
                    style={{ ...inp("vehicleId"), background: "#fff" }}>
                    <option value="">Select vehicle...</option>
                    {availableVehicles.map(v => <option key={v.id} value={v.id}>{v.registration} — {v.make} {v.model} ({v.currentOdometer.toLocaleString()} km)</option>)}
                  </select>
                )}
                {fieldErrors.vehicleId && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4, display: "flex", alignItems: "center", gap: 4 }}><AlertCircle size={12} />{fieldErrors.vehicleId}</div>}
              </div>

              <div>
                <label style={lbl}>Trip Type</label>
                <div style={{ display: "flex", gap: 10 }}>
                  {["BUSINESS","PRIVATE"].map(t => {
                    const cfg = TYPE_CFG[t]
                    const Icon = cfg.icon
                    return (
                      <button key={t} onClick={() => setStartForm(f => ({ ...f, tripType: t }))}
                        style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 7, padding: "10px", border: `2px solid ${startForm.tripType === t ? cfg.color : "#E2E8F0"}`, borderRadius: 8, cursor: "pointer", background: startForm.tripType === t ? cfg.bg : "#fff" }}>
                        <Icon size={14} color={startForm.tripType === t ? cfg.color : "#94A3B8"} />
                        <span style={{ fontSize: 13, fontWeight: 600, color: startForm.tripType === t ? cfg.color : "#64748B" }}>{cfg.label}</span>
                      </button>
                    )
                  })}
                </div>
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>Used for SARS travel allowance logbook reporting</div>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Driver Name</label>
                  <input value={startForm.driverName} onChange={e => setStartForm(f => ({ ...f, driverName: e.target.value }))} placeholder="James Dlamini" style={inp("driverName")} />
                </div>
                <div>
                  <label style={lbl}>Start Odometer (km) *</label>
                  <input type="number" value={startForm.startOdometer}
                    onChange={e => { setStartForm(f => ({ ...f, startOdometer: e.target.value })); setFieldErrors(f => omit2(f, "startOdometer")) }}
                    placeholder={selectedVehicle ? String(selectedVehicle.currentOdometer) : ""}
                    style={inp("startOdometer")} />
                  {fieldErrors.startOdometer && <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{fieldErrors.startOdometer}</div>}
                </div>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Purpose</label>
                  <input value={startForm.purpose} onChange={e => setStartForm(f => ({ ...f, purpose: e.target.value }))} placeholder="Site delivery — Carletonville" style={inp("purpose")} />
                </div>
                <div>
                  <label style={lbl}>Starting From</label>
                  <input value={startForm.startLocation} onChange={e => setStartForm(f => ({ ...f, startLocation: e.target.value }))} placeholder="Johannesburg office" style={inp("startLocation")} />
                </div>
              </div>
            </div>

            {apiError && <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowStart(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => { if (validateStart()) startTrip.mutate({ vehicleId: startForm.vehicleId, body: { driverName: startForm.driverName || null, purpose: startForm.purpose || null, tripType: startForm.tripType, startLocation: startForm.startLocation || null, startOdometer: Number(startForm.startOdometer), startAt: new Date().toISOString() } }) }}
                disabled={startTrip.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {startTrip.isPending ? "Starting..." : "Start Trip"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── End Trip Modal ────────────────────────────────────────────────── */}
      {showEnd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>End Trip</h3>
              <button onClick={() => setShowEnd(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ marginBottom: 16, padding: "12px 14px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 8, fontSize: 13, color: "#0369A1" }}>
              Started at <strong>{showEnd.startOdometer.toLocaleString()} km</strong>
              {endOdometer && Number(endOdometer) > showEnd.startOdometer && (
                <> · Distance: <strong>{(Number(endOdometer) - showEnd.startOdometer).toLocaleString()} km</strong></>
              )}
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>End Odometer (km) *</label>
                <input type="number" value={endOdometer} onChange={e => setEndOdometer(e.target.value)} autoFocus
                  placeholder="Enter current odometer reading"
                  style={{ ...inp("_"), width: "100%", fontSize: 18, fontWeight: 700 }} />
                {endOdometer && Number(endOdometer) < showEnd.startOdometer && (
                  <div style={{ fontSize: 12, color: "#DC2626", marginTop: 4, display: "flex", alignItems: "center", gap: 4 }}>
                    <AlertCircle size={12} /> End reading must be greater than start ({showEnd.startOdometer.toLocaleString()} km)
                  </div>
                )}
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Destination / End Location</label>
                  <input value={endLocation} onChange={e => setEndLocation(e.target.value)} placeholder="Carletonville site" style={{ ...inp("_"), width: "100%" }} />
                </div>
                <div>
                  <label style={lbl}>Fuel Used (litres)</label>
                  <input type="number" value={fuelUsed} onChange={e => setFuelUsed(e.target.value)} placeholder="35.5" style={{ ...inp("_"), width: "100%" }} />
                </div>
              </div>
              <div>
                <label style={lbl}>Notes</label>
                <input value={endNotes} onChange={e => setEndNotes(e.target.value)} placeholder="Optional notes" style={{ ...inp("_"), width: "100%" }} />
              </div>
            </div>

            {apiError && <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowEnd(null)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => endTrip.mutate({ vehicleId: showEnd.vehicleId, body: { endOdometer: Number(endOdometer), endLocation: endLocation || null, endAt: new Date().toISOString(), fuelUsedLitres: fuelUsed ? Number(fuelUsed) : null, notes: endNotes || null } })}
                disabled={!endOdometer || Number(endOdometer) <= showEnd.startOdometer || endTrip.isPending}
                style={{ padding: "9px 22px", background: !endOdometer || Number(endOdometer) <= showEnd.startOdometer ? "#E2E8F0" : "#0D9488", color: !endOdometer || Number(endOdometer) <= showEnd.startOdometer ? "#94A3B8" : "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {endTrip.isPending ? "Ending..." : "End Trip"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const omit2 = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const inp   = (k: string): React.CSSProperties => ({ width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", outline: "none" })
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
