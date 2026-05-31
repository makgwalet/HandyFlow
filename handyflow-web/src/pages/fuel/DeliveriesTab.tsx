import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Truck, CheckCircle, Clock, X, Download } from "lucide-react"

interface Delivery {
  id: string
  tankId: string
  customerId: string | null
  deliveryAddress: Record<string, string> | null
  fuelType: string
  litresOrdered: number
  litresDelivered: number | null
  pricePerLitre: number
  totalAmount: number
  status: "SCHEDULED" | "IN_TRANSIT" | "DELIVERED" | "CANCELLED"
  scheduledAt: string
  deliveredAt: string | null
  driverName: string | null
  vehicleReg: string | null
  receiverName: string | null
  receiverIdBadge: string | null
  receiptNumber: string | null
  signedOnBehalf: boolean
  onBehalfOf: string | null
  createdAt: string
}

interface Tank { id: string; name: string; currentLitres: number }

const STATUS_CONFIG = {
  SCHEDULED:  { color: "#1D4ED8", bg: "#EFF6FF", label: "Scheduled"  },
  IN_TRANSIT: { color: "#D97706", bg: "#FFFBEB", label: "In Transit" },
  DELIVERED:  { color: "#166534", bg: "#DCFCE7", label: "Delivered"  },
  CANCELLED:  { color: "#94A3B8", bg: "#F8FAFC", label: "Cancelled"  },
}

export default function DeliveriesTab() {
  const qc = useQueryClient()
  const [showSchedule, setShowSchedule] = useState(false)
  const [showComplete, setShowComplete] = useState<Delivery | null>(null)
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [error, setError] = useState("")

  const [scheduleForm, setScheduleForm] = useState({
    tankId: "", fuelType: "DIESEL", litresOrdered: "",
    pricePerLitre: "", scheduledAt: "", driverName: "", vehicleReg: "",
    street: "", suburb: "", city: "", postalCode: "",
  })

  const [completeForm, setCompleteForm] = useState({
    litresDelivered: "", receiverName: "", receiverIdBadge: "",
    meterReadingStart: "", meterReadingEnd: "",
    signedOnBehalf: false, onBehalfOf: "",
  })

  const { data: deliveries = [], isLoading } = useQuery({
    queryKey: ["deliveries"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fuel/deliveries?size=50")
      return res.data.content as Delivery[]
    },
  })

  const { data: tanks = [] } = useQuery({
    queryKey: ["tanks"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fuel/tanks")
      return res.data as Tank[]
    },
  })

  const scheduleDelivery = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fuel/deliveries", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["deliveries"] })
      setShowSchedule(false)
      setScheduleForm({ tankId: "", fuelType: "DIESEL", litresOrdered: "", pricePerLitre: "", scheduledAt: "", driverName: "", vehicleReg: "", street: "", suburb: "", city: "", postalCode: "" })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to schedule delivery"),
  })

  const completeDelivery = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/fuel/deliveries/${id}/complete`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["deliveries"] })
      qc.invalidateQueries({ queryKey: ["tanks"] })
      setShowComplete(null)
      setCompleteForm({ litresDelivered: "", receiverName: "", receiverIdBadge: "", meterReadingStart: "", meterReadingEnd: "", signedOnBehalf: false, onBehalfOf: "" })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to complete delivery"),
  })

  const downloadReceipt = async (deliveryId: string, receiptNumber: string) => {
    try {
      const res = await apiClient.get(`/api/v1/fuel/deliveries/${deliveryId}/receipt`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
      const a = document.createElement("a"); a.href = url; a.download = `${receiptNumber}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert("Failed to download receipt") }
  }

  const filtered = filterStatus === "ALL" ? deliveries : deliveries.filter(d => d.status === filterStatus)
  const fmtDate  = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  const fmtR     = (n: number) => `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
        <div style={{ display: "flex", gap: 6 }}>
          {["ALL", "SCHEDULED", "IN_TRANSIT", "DELIVERED", "CANCELLED"].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)} style={{ padding: "6px 12px", borderRadius: 20, border: "1px solid", borderColor: filterStatus === s ? "#1B3A6B" : "#E2E8F0", background: filterStatus === s ? "#1B3A6B" : "#fff", color: filterStatus === s ? "#fff" : "#64748B", fontSize: 12, fontWeight: 500, cursor: "pointer" }}>
              {s === "ALL" ? "All" : STATUS_CONFIG[s as keyof typeof STATUS_CONFIG]?.label}
            </button>
          ))}
        </div>
        <button onClick={() => setShowSchedule(true)} style={btnPrimary}>
          <Plus size={15} /> Schedule Delivery
        </button>
      </div>

      {/* Delivery list */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading deliveries...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Truck size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No deliveries found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map(d => {
            const cfg = STATUS_CONFIG[d.status]
            return (
              <div key={d.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "16px 20px" }}>
                <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between" }}>
                  <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
                    <div style={{ width: 42, height: 42, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <Truck size={20} color={cfg.color} />
                    </div>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{d.fuelType}</span>
                        <span style={{ color: "#94A3B8" }}>·</span>
                        <span style={{ fontWeight: 600, color: "#0D9488" }}>{d.litresOrdered.toLocaleString()} L ordered</span>
                        {d.litresDelivered && d.litresDelivered !== d.litresOrdered && (
                          <span style={{ color: "#64748B", fontSize: 13 }}>({d.litresDelivered.toLocaleString()} L delivered)</span>
                        )}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {fmtDate(d.scheduledAt)}
                        {d.driverName && ` · Driver: ${d.driverName}`}
                        {d.vehicleReg && ` (${d.vehicleReg})`}
                      </div>
                      {d.deliveryAddress && (
                        <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>
                          📍 {d.deliveryAddress.street}, {d.deliveryAddress.city}
                        </div>
                      )}
                      {d.receiverName && (
                        <div style={{ fontSize: 12, color: "#475569", marginTop: 2 }}>
                          Received by: <strong>{d.receiverName}</strong>
                          {d.signedOnBehalf && d.onBehalfOf && (
                            <span style={{ color: "#EA580C" }}> (on behalf of {d.onBehalfOf})</span>
                          )}
                        </div>
                      )}
                    </div>
                  </div>

                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontWeight: 700, color: "#0F172A" }}>{fmtR(d.totalAmount)}</div>
                      {d.receiptNumber && <div style={{ fontSize: 11, color: "#94A3B8" }}>{d.receiptNumber}</div>}
                    </div>
                    <span style={{ background: cfg.bg, color: cfg.color, padding: "4px 12px", borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{cfg.label}</span>
                    {d.status === "SCHEDULED" && (
                      <button onClick={() => { setShowComplete(d); setCompleteForm(f => ({ ...f, litresDelivered: d.litresOrdered.toString() })); setError("") }} style={{ display: "flex", alignItems: "center", gap: 5, background: "#166534", color: "#fff", border: "none", borderRadius: 7, padding: "7px 14px", fontSize: 13, fontWeight: 500, cursor: "pointer" }}>
                        <CheckCircle size={13} /> Complete
                      </button>
                    )}
                    {d.status === "DELIVERED" && d.receiptNumber && (
                      <button onClick={() => downloadReceipt(d.id, d.receiptNumber!)} style={{ display: "flex", alignItems: "center", gap: 5, background: "#F0F9FF", color: "#0369A1", border: "1px solid #BAE6FD", borderRadius: 7, padding: "7px 12px", fontSize: 13, cursor: "pointer" }}>
                        <Download size={13} /> Receipt
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Schedule Delivery Modal */}
      {showSchedule && (
        <Modal title="Schedule Fuel Delivery" onClose={() => { setShowSchedule(false); setError("") }}>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label style={labelStyle}>Source Tank *</label>
                <select value={scheduleForm.tankId} onChange={e => setScheduleForm(f => ({ ...f, tankId: e.target.value }))} style={selectStyle}>
                  <option value="">Select tank...</option>
                  {tanks.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
              </div>
              <div>
                <label style={labelStyle}>Fuel Type</label>
                <select value={scheduleForm.fuelType} onChange={e => setScheduleForm(f => ({ ...f, fuelType: e.target.value }))} style={selectStyle}>
                  {["DIESEL", "PETROL", "PARAFFIN", "GAS"].map(t => <option key={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={labelStyle}>Litres Ordered *</label>
                <input type="number" value={scheduleForm.litresOrdered} onChange={e => setScheduleForm(f => ({ ...f, litresOrdered: e.target.value }))} placeholder="2000" style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Price per Litre (R) *</label>
                <input type="number" value={scheduleForm.pricePerLitre} onChange={e => setScheduleForm(f => ({ ...f, pricePerLitre: e.target.value }))} placeholder="22.85" style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Scheduled Date *</label>
                <input type="datetime-local" value={scheduleForm.scheduledAt} onChange={e => setScheduleForm(f => ({ ...f, scheduledAt: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Driver Name</label>
                <input value={scheduleForm.driverName} onChange={e => setScheduleForm(f => ({ ...f, driverName: e.target.value }))} placeholder="Moses Sithole" style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Vehicle Reg</label>
                <input value={scheduleForm.vehicleReg} onChange={e => setScheduleForm(f => ({ ...f, vehicleReg: e.target.value }))} placeholder="GP-45-67-JHB" style={inputStyle} />
              </div>
            </div>
            <div style={{ borderTop: "1px solid #F1F5F9", paddingTop: 14 }}>
              <div style={{ fontSize: 12, fontWeight: 600, color: "#64748B", marginBottom: 10, letterSpacing: "0.05em" }}>DELIVERY ADDRESS</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <div><label style={labelStyle}>Street</label><input value={scheduleForm.street} onChange={e => setScheduleForm(f => ({ ...f, street: e.target.value }))} placeholder="Mine Gate 3, Shaft Road" style={inputStyle} /></div>
                <div><label style={labelStyle}>Suburb</label><input value={scheduleForm.suburb} onChange={e => setScheduleForm(f => ({ ...f, suburb: e.target.value }))} placeholder="Carletonville" style={inputStyle} /></div>
                <div><label style={labelStyle}>City</label><input value={scheduleForm.city} onChange={e => setScheduleForm(f => ({ ...f, city: e.target.value }))} placeholder="West Rand" style={inputStyle} /></div>
                <div><label style={labelStyle}>Postal Code</label><input value={scheduleForm.postalCode} onChange={e => setScheduleForm(f => ({ ...f, postalCode: e.target.value }))} placeholder="2499" style={inputStyle} /></div>
              </div>
            </div>
          </div>
          {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
            <button onClick={() => { setShowSchedule(false); setError("") }} style={cancelBtn}>Cancel</button>
            <button
              onClick={() => scheduleDelivery.mutate({
                tankId: scheduleForm.tankId,
                fuelType: scheduleForm.fuelType,
                litresOrdered: Number(scheduleForm.litresOrdered),
                pricePerLitre: Number(scheduleForm.pricePerLitre),
                scheduledAt: scheduleForm.scheduledAt ? new Date(scheduleForm.scheduledAt).toISOString() : new Date().toISOString(),
                driverName: scheduleForm.driverName || null,
                vehicleReg: scheduleForm.vehicleReg || null,
                deliveryAddress: { street: scheduleForm.street, suburb: scheduleForm.suburb, city: scheduleForm.city, postalCode: scheduleForm.postalCode },
              })}
              disabled={!scheduleForm.tankId || !scheduleForm.litresOrdered || !scheduleForm.pricePerLitre || scheduleDelivery.isPending}
              style={submitBtn}
            >
              {scheduleDelivery.isPending ? "Scheduling..." : "Schedule Delivery"}
            </button>
          </div>
        </Modal>
      )}

      {/* Complete Delivery Modal */}
      {showComplete && (
        <Modal title={`Complete Delivery — ${showComplete.fuelType} ${showComplete.litresOrdered.toLocaleString()}L`} onClose={() => { setShowComplete(null); setError("") }}>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div>
                <label style={labelStyle}>Litres Delivered *</label>
                <input type="number" value={completeForm.litresDelivered} onChange={e => setCompleteForm(f => ({ ...f, litresDelivered: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Receiver Name</label>
                <input value={completeForm.receiverName} onChange={e => setCompleteForm(f => ({ ...f, receiverName: e.target.value }))} placeholder="Johannes van Wyk" style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Receiver ID / Badge</label>
                <input value={completeForm.receiverIdBadge} onChange={e => setCompleteForm(f => ({ ...f, receiverIdBadge: e.target.value }))} placeholder="MINE-BADGE-4471" style={inputStyle} />
              </div>
              <div />
              <div>
                <label style={labelStyle}>Meter Start (L)</label>
                <input type="number" value={completeForm.meterReadingStart} onChange={e => setCompleteForm(f => ({ ...f, meterReadingStart: e.target.value }))} placeholder="12450" style={inputStyle} />
              </div>
              <div>
                <label style={labelStyle}>Meter End (L)</label>
                <input type="number" value={completeForm.meterReadingEnd} onChange={e => setCompleteForm(f => ({ ...f, meterReadingEnd: e.target.value }))} placeholder="14435" style={inputStyle} />
              </div>
            </div>

            {/* On behalf toggle */}
            <div style={{ padding: "12px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8 }}>
              <label style={{ display: "flex", alignItems: "center", gap: 10, cursor: "pointer" }}>
                <input type="checkbox" checked={completeForm.signedOnBehalf} onChange={e => setCompleteForm(f => ({ ...f, signedOnBehalf: e.target.checked }))} style={{ width: 16, height: 16 }} />
                <span style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Signing on behalf of designated receiver</span>
              </label>
              {completeForm.signedOnBehalf && (
                <div style={{ marginTop: 10 }}>
                  <label style={labelStyle}>Designated Receiver Name</label>
                  <input value={completeForm.onBehalfOf} onChange={e => setCompleteForm(f => ({ ...f, onBehalfOf: e.target.value }))} placeholder="Name of person who should have signed" style={inputStyle} />
                </div>
              )}
            </div>

            <div style={{ padding: "10px 14px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 8, fontSize: 13, color: "#0369A1" }}>
              💡 A PDF delivery receipt (FDR-YYYY-NNNNN) will be generated automatically.
            </div>
          </div>

          {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
            <button onClick={() => { setShowComplete(null); setError("") }} style={cancelBtn}>Cancel</button>
            <button
              onClick={() => completeDelivery.mutate({
                id: showComplete.id,
                body: {
                  litresDelivered: Number(completeForm.litresDelivered),
                  receiverName: completeForm.receiverName || null,
                  receiverIdBadge: completeForm.receiverIdBadge || null,
                  meterReadingStart: completeForm.meterReadingStart ? Number(completeForm.meterReadingStart) : null,
                  meterReadingEnd: completeForm.meterReadingEnd ? Number(completeForm.meterReadingEnd) : null,
                  signedOnBehalf: completeForm.signedOnBehalf,
                  onBehalfOf: completeForm.onBehalfOf || null,
                }
              })}
              disabled={!completeForm.litresDelivered || completeDelivery.isPending}
              style={submitBtn}
            >
              {completeDelivery.isPending ? "Completing..." : "Complete & Generate Receipt"}
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 560, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const labelStyle: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }
const selectStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }
const cancelBtn: React.CSSProperties = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties = { padding: "9px 20px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: "pointer" }
