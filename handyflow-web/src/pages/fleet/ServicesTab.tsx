// src/pages/fleet/ServicesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Wrench, X, AlertCircle, AlertTriangle } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

const SERVICE_TYPES = ["SERVICE","REPAIR","TYRE","BATTERY","BRAKES","ELECTRICAL","BODYWORK","INSPECTION","OTHER"]
const TYPE_CFG: Record<string, { color: string; bg: string }> = {
  SERVICE:    { color: "#166534", bg: "#DCFCE7" },
  REPAIR:     { color: "#DC2626", bg: "#FEF2F2" },
  TYRE:       { color: "#D97706", bg: "#FFFBEB" },
  BATTERY:    { color: "#7C3AED", bg: "#F3E8FF" },
  BRAKES:     { color: "#1D4ED8", bg: "#EFF6FF" },
  ELECTRICAL: { color: "#0D9488", bg: "#F0FDF4" },
  BODYWORK:   { color: "#EA580C", bg: "#FFF7ED" },
  INSPECTION: { color: "#0369A1", bg: "#F0F9FF" },
  OTHER:      { color: "#64748B", bg: "#F8FAFC" },
}

const EMPTY_FORM = {
  type: "SERVICE", description: "", serviceDate: new Date().toISOString().split("T")[0],
  odometerAtService: "", nextServiceKm: "", cost: "", supplier: "", invoiceRef: "",
}

export default function ServicesTab() {
  const qc = useQueryClient()
  const [selectedVehicle, setSelectedVehicle] = useState("")
  const [showAdd, setShowAdd]                 = useState(false)
  const [form, setForm]                       = useState(EMPTY_FORM)
  const [apiError, setApiError]               = useState("")

  const { data: vehicles = [] } = useQuery<any[]>({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/vehicles?size=200")),
  })

  const { data: services = [], isLoading } = useQuery<any[]>({
    queryKey: ["fleet-services", selectedVehicle],
    queryFn: async () => selectedVehicle ? unwrap(await apiClient.get(`/api/v1/fleet/vehicles/${selectedVehicle}/services?size=100`)) : [],
    enabled: !!selectedVehicle,
  })

  const addService = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/fleet/vehicles/${selectedVehicle}/services`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fleet-services", selectedVehicle] })
      qc.invalidateQueries({ queryKey: ["fleet-vehicles"] })
      // FIX: same gap FuelTab.tsx's logFuel mutation had — a recorded
      // service's cost feeds directly into FleetCostService.summarize()'s
      // totalServiceCost/costPerKm, so FleetDashboard.tsx's cost-per-km
      // table would go stale after recording a service without this.
      qc.invalidateQueries({ queryKey: ["fleet-cost-summary"] })
      setShowAdd(false); setForm(EMPTY_FORM); setApiError("")
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to record service"),
  })

  const selectedVehicleObj = (vehicles as any[]).find(v => v.id === selectedVehicle)
  const totalCost = (services as any[]).reduce((s, r) => s + Number(r.cost ?? 0), 0)
  const serviceAlerts = (vehicles as any[]).filter(v => v.dueForService)

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      {/* Service due alert */}
      {serviceAlerts.length > 0 && (
        <div style={{ marginBottom: 18, padding: "12px 16px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={17} color="#D97706" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#D97706" }}>Service Due — {serviceAlerts.length} vehicle{serviceAlerts.length !== 1 ? "s" : ""}</div>
            <div style={{ fontSize: 12, color: "#92400E" }}>{serviceAlerts.map((v: any) => v.registration).join(", ")}</div>
          </div>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Vehicle:</label>
          <select value={selectedVehicle} onChange={e => setSelectedVehicle(e.target.value)}
            style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300, outline: "none" }}>
            <option value="">Select vehicle...</option>
            {(vehicles as any[]).map(v => <option key={v.id} value={v.id}>{v.registration} — {v.make} {v.model}{v.dueForService ? " ⚠ SVC DUE" : ""}</option>)}
          </select>
        </div>
        {selectedVehicle && (
          <button onClick={() => { setShowAdd(true); setApiError(""); if (selectedVehicleObj) setForm(f => ({ ...f, odometerAtService: String(selectedVehicleObj.currentOdometer), nextServiceKm: String(selectedVehicleObj.currentOdometer + (selectedVehicleObj.serviceIntervalKm || 10000)) })) }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> Record Service
          </button>
        )}
      </div>

      {/* Vehicle summary */}
      {selectedVehicleObj && (services as any[]).length > 0 && (
        <div style={{ display: "flex", gap: 16, marginBottom: 20, padding: "14px 18px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, flexWrap: "wrap" }}>
          {[
            { l: "Vehicle",      v: `${selectedVehicleObj.registration} — ${selectedVehicleObj.make} ${selectedVehicleObj.model}` },
            { l: "Odometer",     v: `${Number(selectedVehicleObj.currentOdometer).toLocaleString()} km` },
            { l: "Records",      v: String(services.length) },
            { l: "Total Cost",   v: `R ${totalCost.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` },
          ].map(item => (
            <div key={item.l} style={{ borderRight: "1px solid #E2E8F0", paddingRight: 16, marginRight: 4 }}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 2 }}>{item.l}</div>
              <div style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>{item.v}</div>
            </div>
          ))}
        </div>
      )}

      {!selectedVehicle ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Wrench size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a vehicle to view service history</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : (services as any[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>No service records for this vehicle.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {(services as any[]).map(s => {
            const cfg = TYPE_CFG[s.type] ?? TYPE_CFG.OTHER
            return (
              <div key={s.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "14px 18px", display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div style={{ display: "flex", gap: 12 }}>
                  <div style={{ width: 40, height: 40, borderRadius: 9, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Wrench size={18} color={cfg.color} />
                  </div>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{s.type}</span>
                      <span style={{ fontWeight: 600, fontSize: 14, color: "#0F172A" }}>{s.description}</span>
                    </div>
                    <div style={{ fontSize: 12, color: "#94A3B8" }}>
                      {fmtDate(s.serviceDate)}
                      {s.odometerAtService && ` · At ${Number(s.odometerAtService).toLocaleString()} km`}
                      {s.supplier && ` · ${s.supplier}`}
                      {s.invoiceRef && ` · Ref: ${s.invoiceRef}`}
                    </div>
                    {s.nextServiceKm && (
                      <div style={{ fontSize: 12, color: "#0D9488", marginTop: 2 }}>
                        Next service: {Number(s.nextServiceKm).toLocaleString()} km
                      </div>
                    )}
                  </div>
                </div>
                <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A", flexShrink: 0 }}>{fmtR(s.cost)}</div>
              </div>
            )
          })}
        </div>
      )}

      {/* Record Service Modal */}
      {showAdd && selectedVehicleObj && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: "0 0 3px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Record Service</h3>
                <div style={{ fontSize: 12, color: "#94A3B8" }}>{selectedVehicleObj.registration} — {selectedVehicleObj.make} {selectedVehicleObj.model} · {Number(selectedVehicleObj.currentOdometer).toLocaleString()} km</div>
              </div>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Service Type *</label>
                <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                  {SERVICE_TYPES.map(t => <option key={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Date *</label>
                <input type="date" value={form.serviceDate} onChange={e => setForm(f => ({ ...f, serviceDate: e.target.value }))} style={inp} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Description *</label>
                <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} autoFocus
                  placeholder={form.type === "SERVICE" ? "10,000 km service — oil, filter, spark plugs" : "Describe the work performed..."}
                  style={inp} />
              </div>
              <div>
                <label style={lbl}>Odometer at Service (km)</label>
                <input type="number" value={form.odometerAtService} onChange={e => setForm(f => ({ ...f, odometerAtService: e.target.value, nextServiceKm: e.target.value && selectedVehicleObj ? String(Number(e.target.value) + (selectedVehicleObj.serviceIntervalKm || 10000)) : f.nextServiceKm }))} style={inp} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Pre-filled from current reading</div>
              </div>
              <div>
                <label style={lbl}>Next Service (km)</label>
                <input type="number" value={form.nextServiceKm} onChange={e => setForm(f => ({ ...f, nextServiceKm: e.target.value }))} style={inp} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Auto-calculated: +{(selectedVehicleObj.serviceIntervalKm || 10000).toLocaleString()} km</div>
              </div>
              <div>
                <label style={lbl}>Cost (R)</label>
                <input type="number" value={form.cost} onChange={e => setForm(f => ({ ...f, cost: e.target.value }))} placeholder="3500" style={inp} />
              </div>
              <div>
                <label style={lbl}>Service Provider</label>
                <input value={form.supplier} onChange={e => setForm(f => ({ ...f, supplier: e.target.value }))} placeholder="McCarthy Toyota" style={inp} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Invoice Reference</label>
                <input value={form.invoiceRef} onChange={e => setForm(f => ({ ...f, invoiceRef: e.target.value }))} placeholder="MCT-2026-4521" style={inp} />
              </div>
            </div>

            {apiError && <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => addService.mutate({ type: form.type, description: form.description, serviceDate: form.serviceDate, odometerAtService: form.odometerAtService ? Number(form.odometerAtService) : null, nextServiceKm: form.nextServiceKm ? Number(form.nextServiceKm) : null, cost: form.cost ? Number(form.cost) : null, supplier: form.supplier || null, invoiceRef: form.invoiceRef || null })}
                disabled={!form.description || !form.serviceDate || addService.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {addService.isPending ? "Recording..." : "Record Service"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
