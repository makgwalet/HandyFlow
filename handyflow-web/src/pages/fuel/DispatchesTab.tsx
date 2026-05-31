import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Fuel, X } from "lucide-react"

interface Dispatch {
  id: string
  tankId: string
  vehicleId: string | null
  assetId: string | null
  customerId: string | null
  recipientName: string | null
  litresDispensed: number
  pricePerLitre: number | null
  dispatchedAt: string
  odometerReading: number | null
  hoursReading: number | null
  authorisedBy: string | null
  levelBefore: number
  levelAfter: number
  createdAt: string
}

interface Tank { id: string; name: string; currentLitres: number; fuelType: string }

export default function DispatchesTab() {
  const qc = useQueryClient()
  const [showDispatch, setShowDispatch] = useState(false)
  const [error, setError] = useState("")
  const [form, setForm] = useState({
    tankId: "", litresDispensed: "", pricePerLitre: "",
    recipientName: "", authorisedBy: "",
    odometerReading: "", hoursReading: "", notes: "",
  })

  const { data: dispatches = [], isLoading } = useQuery({
    queryKey: ["dispatches"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fuel/dispatches?size=50")
      return res.data.content as Dispatch[]
    },
  })

  const { data: tanks = [] } = useQuery({
    queryKey: ["tanks"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fuel/tanks")
      return res.data as Tank[]
    },
  })

  const dispatchFuel = useMutation({
    mutationFn: ({ tankId, body }: { tankId: string; body: any }) =>
      apiClient.post(`/api/v1/fuel/tanks/${tankId}/dispatch`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["dispatches"] })
      qc.invalidateQueries({ queryKey: ["tanks"] })
      setShowDispatch(false)
      setForm({ tankId: "", litresDispensed: "", pricePerLitre: "", recipientName: "", authorisedBy: "", odometerReading: "", hoursReading: "", notes: "" })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to dispatch fuel"),
  })

  const selectedTank = tanks.find(t => t.id === form.tankId)

  const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ fontSize: 14, color: "#64748B" }}>{dispatches.length} dispatch{dispatches.length !== 1 ? "es" : ""} recorded</div>
        <button onClick={() => setShowDispatch(true)} style={btnPrimary}>
          <Plus size={15} /> Dispatch Fuel
        </button>
      </div>

      {/* Dispatch list */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading dispatches...</div>
      ) : dispatches.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Fuel size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No dispatches yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Record fuel issued to vehicles or equipment.</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Recipient", "Litres", "Level Change", "Authorised By", "Date & Time"].map(h => (
                  <th key={h} style={{ padding: "11px 16px", textAlign: "left", fontWeight: 600, fontSize: 12, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {dispatches.map((d, i) => (
                <tr key={d.id} style={{ borderBottom: i < dispatches.length - 1 ? "1px solid #F1F5F9" : "none" }}>
                  <td style={{ padding: "13px 16px" }}>
                    <div style={{ fontWeight: 600, color: "#0F172A" }}>{d.recipientName || "Unknown"}</div>
                    {d.odometerReading && <div style={{ fontSize: 12, color: "#94A3B8" }}>Odometer: {d.odometerReading.toLocaleString()} km</div>}
                    {d.hoursReading && <div style={{ fontSize: 12, color: "#94A3B8" }}>Hours: {d.hoursReading}</div>}
                  </td>
                  <td style={{ padding: "13px 16px" }}>
                    <div style={{ fontWeight: 700, color: "#DC2626" }}>−{d.litresDispensed.toLocaleString()} L</div>
                    {d.pricePerLitre && <div style={{ fontSize: 12, color: "#94A3B8" }}>R {d.pricePerLitre}/L</div>}
                  </td>
                  <td style={{ padding: "13px 16px", fontSize: 13, color: "#475569" }}>
                    <span style={{ color: "#64748B" }}>{d.levelBefore?.toLocaleString()} L</span>
                    <span style={{ margin: "0 6px", color: "#94A3B8" }}>→</span>
                    <span style={{ color: "#0D9488", fontWeight: 600 }}>{d.levelAfter?.toLocaleString()} L</span>
                  </td>
                  <td style={{ padding: "13px 16px", color: "#475569" }}>{d.authorisedBy || "—"}</td>
                  <td style={{ padding: "13px 16px", color: "#64748B", fontSize: 13 }}>
                    {fmtDate(d.dispatchedAt)}<br />
                    <span style={{ color: "#94A3B8" }}>{fmtTime(d.dispatchedAt)}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Dispatch Modal */}
      {showDispatch && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 540, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Dispatch Fuel</h3>
              <button onClick={() => { setShowDispatch(false); setError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={labelStyle}>Source Tank *</label>
                <select value={form.tankId} onChange={e => setForm(f => ({ ...f, tankId: e.target.value }))} style={selectStyle}>
                  <option value="">Select tank...</option>
                  {tanks.map(t => <option key={t.id} value={t.id}>{t.name} — {t.currentLitres.toLocaleString()} L available</option>)}
                </select>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div>
                  <label style={labelStyle}>Litres to Dispatch *</label>
                  <input type="number" value={form.litresDispensed} onChange={e => setForm(f => ({ ...f, litresDispensed: e.target.value }))} placeholder="350" style={inputStyle} />
                </div>
                <div>
                  <label style={labelStyle}>Price per Litre (R)</label>
                  <input type="number" value={form.pricePerLitre} onChange={e => setForm(f => ({ ...f, pricePerLitre: e.target.value }))} placeholder="22.85" style={inputStyle} />
                </div>
                <div>
                  <label style={labelStyle}>Recipient / Vehicle *</label>
                  <input value={form.recipientName} onChange={e => setForm(f => ({ ...f, recipientName: e.target.value }))} placeholder="CAT D9 Dozer / GP-12-34" style={inputStyle} />
                </div>
                <div>
                  <label style={labelStyle}>Authorised By</label>
                  <input value={form.authorisedBy} onChange={e => setForm(f => ({ ...f, authorisedBy: e.target.value }))} placeholder="Thabo Mokoena" style={inputStyle} />
                </div>
                <div>
                  <label style={labelStyle}>Odometer (km)</label>
                  <input type="number" value={form.odometerReading} onChange={e => setForm(f => ({ ...f, odometerReading: e.target.value }))} placeholder="45000" style={inputStyle} />
                </div>
                <div>
                  <label style={labelStyle}>Machine Hours</label>
                  <input type="number" value={form.hoursReading} onChange={e => setForm(f => ({ ...f, hoursReading: e.target.value }))} placeholder="250.0" style={inputStyle} />
                </div>
              </div>

              {selectedTank && form.litresDispensed && (
                <div style={{ padding: "10px 14px", background: Number(form.litresDispensed) > selectedTank.currentLitres ? "#FEF2F2" : "#F0FDF4", border: `1px solid ${Number(form.litresDispensed) > selectedTank.currentLitres ? "#FECACA" : "#BBF7D0"}`, borderRadius: 8, fontSize: 13, color: Number(form.litresDispensed) > selectedTank.currentLitres ? "#DC2626" : "#166534" }}>
                  {Number(form.litresDispensed) > selectedTank.currentLitres
                    ? `⚠️ Insufficient stock. Available: ${selectedTank.currentLitres.toLocaleString()} L`
                    : `After dispatch: ${(selectedTank.currentLitres - Number(form.litresDispensed)).toLocaleString()} L remaining`
                  }
                </div>
              )}
            </div>

            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => { setShowDispatch(false); setError("") }} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => dispatchFuel.mutate({
                  tankId: form.tankId,
                  body: {
                    litresDispensed: Number(form.litresDispensed),
                    pricePerLitre: form.pricePerLitre ? Number(form.pricePerLitre) : null,
                    dispatchedAt: new Date().toISOString(),
                    recipientName: form.recipientName || null,
                    authorisedBy: form.authorisedBy || null,
                    odometerReading: form.odometerReading ? Number(form.odometerReading) : null,
                    hoursReading: form.hoursReading ? Number(form.hoursReading) : null,
                  }
                })}
                disabled={!form.tankId || !form.litresDispensed || !form.recipientName || dispatchFuel.isPending}
                style={submitBtn}
              >
                {dispatchFuel.isPending ? "Dispatching..." : "Dispatch Fuel"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const labelStyle: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }
const selectStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties = { padding: "9px 20px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: "pointer" }
