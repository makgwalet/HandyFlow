// src/pages/fleet/FuelTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Fuel, X, AlertCircle } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const fmtR    = (n: number | null | undefined) => n != null ? `R ${Number(n).toFixed(2)}` : "—"

const EMPTY_FORM = { filledAt: new Date().toISOString().split("T")[0], litres: "", pricePerLitre: "", totalCost: "", odometerAtFillup: "", station: "", receiptRef: "", fullTank: true }

export default function FuelTab() {
  const qc = useQueryClient()
  const [selectedVehicle, setSelectedVehicle] = useState("")
  const [showAdd, setShowAdd]                 = useState(false)
  const [form, setForm]                       = useState(EMPTY_FORM)
  const [apiError, setApiError]               = useState("")

  const { data: vehicles = [] } = useQuery<any[]>({
    queryKey: ["fleet-vehicles"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fleet/vehicles?size=200")),
  })

  const { data: logs = [], isLoading } = useQuery<any[]>({
    queryKey: ["fleet-fuel", selectedVehicle],
    queryFn: async () => selectedVehicle ? unwrap(await apiClient.get(`/api/v1/fleet/vehicles/${selectedVehicle}/fuel?size=100`)) : [],
    enabled: !!selectedVehicle,
  })

  const logFuel = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/fleet/vehicles/${selectedVehicle}/fuel`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fleet-fuel", selectedVehicle] }); setShowAdd(false); setForm(EMPTY_FORM); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to log fuel"),
  })

  const selectedVehicleObj = (vehicles as any[]).find(v => v.id === selectedVehicle)
  const totalLitres = (logs as any[]).reduce((s, l) => s + Number(l.litres ?? 0), 0)
  const totalCost   = (logs as any[]).reduce((s, l) => s + Number(l.totalCost ?? 0), 0)

  // Auto-compute totalCost or pricePerLitre
  const computedTotal = form.litres && form.pricePerLitre ? (Number(form.litres) * Number(form.pricePerLitre)).toFixed(2) : ""
  const computedPpl   = form.litres && form.totalCost    ? (Number(form.totalCost) / Number(form.litres)).toFixed(3) : ""

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Vehicle:</label>
          <select value={selectedVehicle} onChange={e => setSelectedVehicle(e.target.value)}
            style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300, outline: "none" }}>
            <option value="">Select vehicle...</option>
            {(vehicles as any[]).map(v => <option key={v.id} value={v.id}>{v.registration} — {v.make} {v.model}</option>)}
          </select>
        </div>
        {selectedVehicle && (
          <button onClick={() => { setShowAdd(true); setApiError(""); setForm({ ...EMPTY_FORM, odometerAtFillup: String(selectedVehicleObj?.currentOdometer ?? "") }) }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> Log Fill-Up
          </button>
        )}
      </div>

      {/* Summary stats */}
      {selectedVehicle && (logs as any[]).length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
          {[
            { label: "Fill-ups",       value: logs.length,                                     color: "#1B3A6B" },
            { label: "Total litres",   value: `${totalLitres.toFixed(1)} L`,                   color: "#0D9488" },
            { label: "Total cost",     value: `R ${totalCost.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`, color: "#DC2626" },
            { label: "Avg cost/litre", value: totalLitres > 0 ? `R ${(totalCost/totalLitres).toFixed(3)}/L` : "—", color: "#D97706" },
          ].map(s => (
            <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
              <div style={{ fontSize: s.label.length > 12 ? 15 : 20, fontWeight: 700, color: s.color }}>{s.value}</div>
              <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
            </div>
          ))}
        </div>
      )}

      {!selectedVehicle ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Fuel size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a vehicle to view its fuel log</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Fuel fill-ups are tracked separately from trips for accurate cost-per-km reporting.</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading fuel log...</div>
      ) : (logs as any[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          No fuel records for this vehicle yet.
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Date","Odometer","Litres","Price/L","Total","Station","Receipt","Full Tank"].map(h => (
                  <th key={h} style={{ padding: "11px 16px", textAlign: "left", fontWeight: 700, fontSize: 11, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(logs as any[]).map((log, i) => (
                <tr key={log.id} style={{ borderBottom: i < logs.length-1 ? "1px solid #F1F5F9" : "none", background: "#fff" }}>
                  <td style={{ padding: "12px 16px", fontWeight: 600, color: "#0F172A" }}>{fmtDate(log.filledAt)}</td>
                  <td style={{ padding: "12px 16px", color: "#475569" }}>{log.odometerAtFillup ? `${Number(log.odometerAtFillup).toLocaleString()} km` : "—"}</td>
                  <td style={{ padding: "12px 16px", fontWeight: 600, color: "#0D9488" }}>{Number(log.litres).toFixed(1)} L</td>
                  <td style={{ padding: "12px 16px", color: "#475569" }}>{log.pricePerLitre ? `R ${Number(log.pricePerLitre).toFixed(3)}` : "—"}</td>
                  <td style={{ padding: "12px 16px", fontWeight: 700, color: "#DC2626" }}>{fmtR(log.totalCost)}</td>
                  <td style={{ padding: "12px 16px", color: "#475569" }}>{log.station || "—"}</td>
                  <td style={{ padding: "12px 16px", color: "#94A3B8", fontSize: 12 }}>{log.receiptRef || "—"}</td>
                  <td style={{ padding: "12px 16px" }}>
                    {log.fullTank ? <span style={{ background: "#DCFCE7", color: "#166534", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>Full</span> : <span style={{ color: "#94A3B8", fontSize: 12 }}>Partial</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Log Fill-Up Modal */}
      {showAdd && selectedVehicleObj && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 520, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: "0 0 3px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Log Fuel Fill-Up</h3>
                <div style={{ fontSize: 12, color: "#94A3B8" }}>{selectedVehicleObj.registration} — {selectedVehicleObj.make} {selectedVehicleObj.model}</div>
              </div>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Date *</label>
                <input type="date" value={form.filledAt} onChange={e => setForm(f => ({ ...f, filledAt: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Odometer at Fill-Up (km)</label>
                <input type="number" value={form.odometerAtFillup} onChange={e => setForm(f => ({ ...f, odometerAtFillup: e.target.value }))} placeholder={String(selectedVehicleObj.currentOdometer)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Litres Filled *</label>
                <input type="number" step="0.1" value={form.litres} onChange={e => setForm(f => ({ ...f, litres: e.target.value, totalCost: f.pricePerLitre ? String((Number(e.target.value) * Number(f.pricePerLitre)).toFixed(2)) : f.totalCost }))} placeholder="45.0" style={inp} />
              </div>
              <div>
                <label style={lbl}>Price per Litre (R)</label>
                <input type="number" step="0.001" value={form.pricePerLitre} onChange={e => setForm(f => ({ ...f, pricePerLitre: e.target.value, totalCost: f.litres ? String((Number(f.litres) * Number(e.target.value)).toFixed(2)) : f.totalCost }))} placeholder="21.450" style={inp} />
              </div>
              <div>
                <label style={lbl}>Total Cost (R)</label>
                <input type="number" step="0.01" value={form.totalCost} onChange={e => setForm(f => ({ ...f, totalCost: e.target.value }))} placeholder={computedTotal || "965.25"} style={inp} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Auto-calculated from litres × price/L</div>
              </div>
              <div>
                <label style={lbl}>Fuel Station</label>
                <input value={form.station} onChange={e => setForm(f => ({ ...f, station: e.target.value }))} placeholder="Engen Sandton" style={inp} />
              </div>
              <div>
                <label style={lbl}>Receipt Reference</label>
                <input value={form.receiptRef} onChange={e => setForm(f => ({ ...f, receiptRef: e.target.value }))} placeholder="REC-2026-001" style={inp} />
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10, paddingTop: 20 }}>
                <input type="checkbox" id="fullTank" checked={form.fullTank} onChange={e => setForm(f => ({ ...f, fullTank: e.target.checked }))} style={{ width: 16, height: 16, cursor: "pointer" }} />
                <label htmlFor="fullTank" style={{ fontSize: 13, fontWeight: 600, color: "#374151", cursor: "pointer" }}>Full tank</label>
              </div>
            </div>

            {apiError && <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => logFuel.mutate({ filledAt: form.filledAt, litres: Number(form.litres), pricePerLitre: form.pricePerLitre ? Number(form.pricePerLitre) : null, totalCost: form.totalCost ? Number(form.totalCost) : (form.litres && form.pricePerLitre ? Number(form.litres) * Number(form.pricePerLitre) : null), odometerAtFillup: form.odometerAtFillup ? Number(form.odometerAtFillup) : null, station: form.station || null, receiptRef: form.receiptRef || null, fullTank: form.fullTank })}
                disabled={!form.litres || !form.filledAt || logFuel.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {logFuel.isPending ? "Saving..." : "Log Fill-Up"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
