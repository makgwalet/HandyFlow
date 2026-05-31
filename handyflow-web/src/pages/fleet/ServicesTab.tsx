import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Wrench, X } from "lucide-react"

interface Vehicle { id: string; registration: string; make: string; model: string; currentOdometer: number }

interface Service {
  id: string
  vehicleId: string
  type: string
  description: string
  servicedAt: string
  odometerAtService: number | null
  cost: number | null
  supplier: string | null
  invoiceRef: string | null
  nextServiceOdometer: number | null
  createdAt: string
}

const SERVICE_TYPES = ["SERVICE","REPAIR","TYRE","BATTERY","BRAKES","ELECTRICAL","BODYWORK","OTHER"]
const TYPE_CONFIG: Record<string, { color: string; bg: string }> = {
  SERVICE:     { color: "#166534", bg: "#DCFCE7" },
  REPAIR:      { color: "#DC2626", bg: "#FEF2F2" },
  TYRE:        { color: "#D97706", bg: "#FFFBEB" },
  BATTERY:     { color: "#7C3AED", bg: "#F3E8FF" },
  BRAKES:      { color: "#1D4ED8", bg: "#EFF6FF" },
  ELECTRICAL:  { color: "#0D9488", bg: "#F0FDF4" },
  BODYWORK:    { color: "#EA580C", bg: "#FFF7ED" },
  OTHER:       { color: "#64748B", bg: "#F8FAFC" },
}

export default function ServicesTab() {
  const qc = useQueryClient()
  const [selectedVehicle, setSelectedVehicle] = useState("")
  const [showAdd, setShowAdd] = useState(false)
  const [error, setError] = useState("")
  const [form, setForm] = useState({
    type: "SERVICE", description: "", odometerAtService: "",
    cost: "", supplier: "", invoiceRef: "", nextServiceOdometer: "",
  })

  const { data: vehicles = [] } = useQuery({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fleet/vehicles?size=50")
      return res.data.content as Vehicle[]
    },
  })

  const { data: services = [], isLoading } = useQuery({
    queryKey: ["fleet-services", selectedVehicle],
    queryFn: async () => {
      if (!selectedVehicle) return []
      const res = await apiClient.get(`/api/v1/fleet/vehicles/${selectedVehicle}/services?size=50`)
      return res.data.content as Service[]
    },
    enabled: !!selectedVehicle,
  })

  const addService = useMutation({
    mutationFn: (body: any) =>
      apiClient.post(`/api/v1/fleet/vehicles/${selectedVehicle}/services`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fleet-services", selectedVehicle] })
      qc.invalidateQueries({ queryKey: ["fleet-vehicles"] })
      setShowAdd(false)
      setForm({ type: "SERVICE", description: "", odometerAtService: "", cost: "", supplier: "", invoiceRef: "", nextServiceOdometer: "" })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to record service"),
  })

  const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  const fmtR = (n: number | null) => n ? `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
  const selectedVehicleObj = vehicles.find(v => v.id === selectedVehicle)

  const totalCost = services.reduce((s, r) => s + (r.cost || 0), 0)

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Select Vehicle:</label>
          <select value={selectedVehicle} onChange={e => setSelectedVehicle(e.target.value)} style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300 }}>
            <option value="">Choose a vehicle...</option>
            {vehicles.map(v => <option key={v.id} value={v.id}>{v.registration} — {v.make} {v.model}</option>)}
          </select>
        </div>
        {selectedVehicle && (
          <button onClick={() => setShowAdd(true)} style={btnPrimary}>
            <Plus size={15} /> Record Service
          </button>
        )}
      </div>

      {/* Vehicle summary */}
      {selectedVehicleObj && services.length > 0 && (
        <div style={{ display: "flex", gap: 16, marginBottom: 20, padding: "14px 18px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: 10 }}>
          <div><div style={{ fontSize: 11, color: "#64748B", marginBottom: 2 }}>VEHICLE</div><div style={{ fontWeight: 700, color: "#0F172A" }}>{selectedVehicleObj.registration}</div></div>
          <div style={{ width: 1, background: "#BBF7D0" }} />
          <div><div style={{ fontSize: 11, color: "#64748B", marginBottom: 2 }}>CURRENT ODO</div><div style={{ fontWeight: 700, color: "#0D9488" }}>{selectedVehicleObj.currentOdometer.toLocaleString()} km</div></div>
          <div style={{ width: 1, background: "#BBF7D0" }} />
          <div><div style={{ fontSize: 11, color: "#64748B", marginBottom: 2 }}>RECORDS</div><div style={{ fontWeight: 700, color: "#1B3A6B" }}>{services.length}</div></div>
          <div style={{ width: 1, background: "#BBF7D0" }} />
          <div><div style={{ fontSize: 11, color: "#64748B", marginBottom: 2 }}>TOTAL COST</div><div style={{ fontWeight: 700, color: "#DC2626" }}>R {totalCost.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}</div></div>
        </div>
      )}

      {!selectedVehicle ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Wrench size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a vehicle to view service history</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading service history...</div>
      ) : services.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8" }}>No service records for this vehicle.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {services.map(s => {
            const cfg = TYPE_CONFIG[s.type] || TYPE_CONFIG.OTHER
            return (
              <div key={s.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "16px 20px", display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div style={{ display: "flex", gap: 14 }}>
                  <div style={{ width: 42, height: 42, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Wrench size={20} color={cfg.color} />
                  </div>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 10px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>{s.type}</span>
                      <span style={{ fontSize: 14, fontWeight: 600, color: "#0F172A" }}>{s.description}</span>
                    </div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>
                      {fmtDate(s.servicedAt)}
                      {s.odometerAtService && ` · At ${s.odometerAtService.toLocaleString()} km`}
                      {s.supplier && ` · ${s.supplier}`}
                      {s.invoiceRef && ` · Ref: ${s.invoiceRef}`}
                    </div>
                    {s.nextServiceOdometer && (
                      <div style={{ fontSize: 12, color: "#0D9488", marginTop: 2 }}>
                        Next service due: {s.nextServiceOdometer.toLocaleString()} km
                      </div>
                    )}
                  </div>
                </div>
                <div style={{ fontWeight: 700, color: "#0F172A", flexShrink: 0 }}>{fmtR(s.cost)}</div>
              </div>
            )
          })}
        </div>
      )}

      {/* Record Service Modal */}
      {showAdd && selectedVehicleObj && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 540, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Record Service — {selectedVehicleObj.registration}</h3>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Service Type *</label>
                <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={sel}>
                  {SERVICE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Odometer at Service (km)</label>
                <input type="number" value={form.odometerAtService} onChange={e => setForm(f => ({ ...f, odometerAtService: e.target.value }))} placeholder={selectedVehicleObj.currentOdometer.toString()} style={inp} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Description *</label>
                <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} placeholder="10,000 km service — oil, filter, spark plugs" style={inp} />
              </div>
              <div>
                <label style={lbl}>Cost (R)</label>
                <input type="number" value={form.cost} onChange={e => setForm(f => ({ ...f, cost: e.target.value }))} placeholder="3500" style={inp} />
              </div>
              <div>
                <label style={lbl}>Next Service (km)</label>
                <input type="number" value={form.nextServiceOdometer} onChange={e => setForm(f => ({ ...f, nextServiceOdometer: e.target.value }))} placeholder={form.odometerAtService ? (Number(form.odometerAtService) + 10000).toString() : ""} style={inp} />
              </div>
              <div>
                <label style={lbl}>Service Provider</label>
                <input value={form.supplier} onChange={e => setForm(f => ({ ...f, supplier: e.target.value }))} placeholder="McCarthy Toyota" style={inp} />
              </div>
              <div>
                <label style={lbl}>Invoice Reference</label>
                <input value={form.invoiceRef} onChange={e => setForm(f => ({ ...f, invoiceRef: e.target.value }))} placeholder="MCT-2026-4521" style={inp} />
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => addService.mutate({
                  type: form.type,
                  description: form.description,
                  servicedAt: new Date().toISOString(),
                  odometerAtService: form.odometerAtService ? Number(form.odometerAtService) : null,
                  cost: form.cost ? Number(form.cost) : null,
                  supplier: form.supplier || null,
                  invoiceRef: form.invoiceRef || null,
                  nextServiceOdometer: form.nextServiceOdometer ? Number(form.nextServiceOdometer) : null,
                })}
                disabled={!form.description || addService.isPending}
                style={submitBtn}
              >
                {addService.isPending ? "Recording..." : "Record Service"}
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
