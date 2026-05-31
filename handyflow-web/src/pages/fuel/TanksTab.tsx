import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Droplets, AlertTriangle, X, ChevronDown, ChevronUp } from "lucide-react"

interface Tank {
  id: string
  name: string
  fuelType: string
  capacityLitres: number
  currentLitres: number
  fillPercentage: number
  low: boolean
  location: string
  createdAt: string
}

interface Supplier { id: string; name: string }

const FUEL_COLORS: Record<string, string> = {
  DIESEL:  "#1D4ED8",
  PETROL:  "#DC2626",
  PARAFFIN:"#D97706",
  GAS:     "#7C3AED",
  OTHER:   "#64748B",
}

export default function TanksTab() {
  const qc = useQueryClient()
  const [showAddTank, setShowAddTank] = useState(false)
  const [showReceive, setShowReceive] = useState<Tank | null>(null)
  const [showDip, setShowDip] = useState<Tank | null>(null)
  const [expandedTank, setExpandedTank] = useState<string | null>(null)
  const [error, setError] = useState("")

  const [tankForm, setTankForm] = useState({ name: "", fuelType: "DIESEL", capacityLitres: "", location: "" })
  const [receiveForm, setReceiveForm] = useState({ litresReceived: "", pricePerLitre: "", supplierId: "", deliveryNote: "", invoiceRef: "" })
  const [dipForm, setDipForm] = useState({ actualLitres: "", readBy: "", notes: "" })

  const { data: tanks = [], isLoading } = useQuery({
    queryKey: ["tanks"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fuel/tanks")
      return res.data as Tank[]
    },
  })

  const { data: suppliers = [] } = useQuery({
    queryKey: ["fuel-suppliers"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fuel/suppliers")
      return res.data as Supplier[]
    },
  })

  const createTank = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fuel/tanks", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["tanks"] }); setShowAddTank(false); setTankForm({ name: "", fuelType: "DIESEL", capacityLitres: "", location: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create tank"),
  })

  const receiveFuel = useMutation({
    mutationFn: ({ tankId, body }: { tankId: string; body: any }) =>
      apiClient.post(`/api/v1/fuel/tanks/${tankId}/receive`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["tanks"] }); setShowReceive(null); setReceiveForm({ litresReceived: "", pricePerLitre: "", supplierId: "", deliveryNote: "", invoiceRef: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to receive fuel"),
  })

  const recordDip = useMutation({
    mutationFn: ({ tankId, body }: { tankId: string; body: any }) =>
      apiClient.post(`/api/v1/fuel/tanks/${tankId}/dip-readings`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["tanks"] }); setShowDip(null); setDipForm({ actualLitres: "", readBy: "", notes: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to record dip reading"),
  })

  const totalCapacity = tanks.reduce((s, t) => s + t.capacityLitres, 0)
  const totalStock    = tanks.reduce((s, t) => s + t.currentLitres, 0)
  const lowTanks      = tanks.filter(t => t.low).length

  return (
    <div>
      {/* Summary cards */}
      <div style={{ display: "flex", gap: 12, marginBottom: 24 }}>
        {[
          { label: "Total Tanks",    value: tanks.length,                          color: "#1B3A6B" },
          { label: "Total Capacity", value: `${totalCapacity.toLocaleString()} L`, color: "#0D9488" },
          { label: "Total Stock",    value: `${totalStock.toLocaleString()} L`,    color: "#1D4ED8" },
          { label: "Low Tanks",      value: lowTanks,                              color: lowTanks > 0 ? "#DC2626" : "#94A3B8" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <button onClick={() => setShowAddTank(true)} style={btnPrimary}>
          <Plus size={15} /> Add Tank
        </button>
      </div>

      {/* Tank cards */}
      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading tanks...</div>
      ) : tanks.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Droplets size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No tanks registered</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Add your first fuel tank to start tracking inventory.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {tanks.map(tank => {
            const color = FUEL_COLORS[tank.fuelType] || "#64748B"
            const pct   = Math.min(100, Math.max(0, tank.fillPercentage))
            const isExpanded = expandedTank === tank.id

            return (
              <div key={tank.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                {/* Tank header */}
                <div style={{ padding: "18px 20px", background: "#fff" }}>
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                      <div style={{ width: 40, height: 40, borderRadius: 10, background: `${color}18`, display: "flex", alignItems: "center", justifyContent: "center" }}>
                        <Droplets size={20} color={color} />
                      </div>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{tank.name}</div>
                        <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 1 }}>
                          {tank.fuelType} · {tank.location || "No location"}
                        </div>
                      </div>
                      {tank.low && (
                        <div style={{ display: "flex", alignItems: "center", gap: 4, background: "#FEF2F2", color: "#DC2626", padding: "3px 10px", borderRadius: 20, fontSize: 12, fontWeight: 600 }}>
                          <AlertTriangle size={11} /> LOW
                        </div>
                      )}
                    </div>

                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <button onClick={() => { setShowReceive(tank); setError("") }} style={btnTeal}>+ Receive</button>
                      <button onClick={() => { setShowDip(tank); setError("") }} style={btnOutline}>Dip Reading</button>
                      <button onClick={() => setExpandedTank(isExpanded ? null : tank.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                        {isExpanded ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                      </button>
                    </div>
                  </div>

                  {/* Fill bar */}
                  <div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
                      <span style={{ fontSize: 13, color: "#475569", fontWeight: 500 }}>
                        {tank.currentLitres.toLocaleString()} L
                        <span style={{ color: "#94A3B8", fontWeight: 400 }}> / {tank.capacityLitres.toLocaleString()} L</span>
                      </span>
                      <span style={{ fontSize: 13, fontWeight: 700, color: tank.low ? "#DC2626" : color }}>
                        {pct.toFixed(1)}%
                      </span>
                    </div>
                    <div style={{ height: 10, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
                      <div style={{
                        height: "100%", borderRadius: 99,
                        width: `${pct}%`,
                        background: tank.low
                          ? "linear-gradient(90deg, #DC2626, #F87171)"
                          : `linear-gradient(90deg, ${color}, ${color}88)`,
                        transition: "width 0.5s ease",
                      }} />
                    </div>
                  </div>
                </div>

                {/* Expanded details */}
                {isExpanded && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "14px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16 }}>
                      {[
                        { label: "Fuel Type", value: tank.fuelType },
                        { label: "Capacity", value: `${tank.capacityLitres.toLocaleString()} L` },
                        { label: "Available Space", value: `${(tank.capacityLitres - tank.currentLitres).toLocaleString()} L` },
                      ].map(item => (
                        <div key={item.label}>
                          <div style={{ fontSize: 11, color: "#94A3B8", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 3 }}>{item.label}</div>
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

      {/* Add Tank Modal */}
      {showAddTank && (
        <Modal title="Add Fuel Tank" onClose={() => { setShowAddTank(false); setError("") }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <Field label="Tank Name *">
              <MInput value={tankForm.name} onChange={v => setTankForm(f => ({ ...f, name: v }))} placeholder='e.g. "Main Diesel Tank"' />
            </Field>
            <Field label="Fuel Type">
              <select value={tankForm.fuelType} onChange={e => setTankForm(f => ({ ...f, fuelType: e.target.value }))} style={selectStyle}>
                {["DIESEL", "PETROL", "PARAFFIN", "GAS", "OTHER"].map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
            <Field label="Capacity (litres) *">
              <MInput value={tankForm.capacityLitres} onChange={v => setTankForm(f => ({ ...f, capacityLitres: v }))} placeholder="10000" type="number" />
            </Field>
            <Field label="Location">
              <MInput value={tankForm.location} onChange={v => setTankForm(f => ({ ...f, location: v }))} placeholder="Depot Bay 1" />
            </Field>
          </div>
          {error && <ErrMsg msg={error} />}
          <ModalFooter
            onCancel={() => { setShowAddTank(false); setError("") }}
            onSubmit={() => createTank.mutate({ name: tankForm.name, fuelType: tankForm.fuelType, capacityLitres: Number(tankForm.capacityLitres), location: tankForm.location })}
            loading={createTank.isPending}
            disabled={!tankForm.name || !tankForm.capacityLitres}
            label="Create Tank"
          />
        </Modal>
      )}

      {/* Receive Fuel Modal */}
      {showReceive && (
        <Modal title={`Receive Fuel — ${showReceive.name}`} onClose={() => { setShowReceive(null); setError("") }}>
          <div style={{ marginBottom: 14, padding: "10px 14px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 8, fontSize: 13, color: "#0369A1" }}>
            Available space: <strong>{(showReceive.capacityLitres - showReceive.currentLitres).toLocaleString()} L</strong>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <Field label="Litres Received *">
              <MInput value={receiveForm.litresReceived} onChange={v => setReceiveForm(f => ({ ...f, litresReceived: v }))} placeholder="5000" type="number" />
            </Field>
            <Field label="Price per Litre (R) *">
              <MInput value={receiveForm.pricePerLitre} onChange={v => setReceiveForm(f => ({ ...f, pricePerLitre: v }))} placeholder="22.85" type="number" />
            </Field>
            <Field label="Supplier">
              <select value={receiveForm.supplierId} onChange={e => setReceiveForm(f => ({ ...f, supplierId: e.target.value }))} style={selectStyle}>
                <option value="">Select supplier...</option>
                {suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <Field label="Delivery Note">
              <MInput value={receiveForm.deliveryNote} onChange={v => setReceiveForm(f => ({ ...f, deliveryNote: v }))} placeholder="DN-20260510-001" />
            </Field>
            <Field label="Invoice Ref">
              <MInput value={receiveForm.invoiceRef} onChange={v => setReceiveForm(f => ({ ...f, invoiceRef: v }))} placeholder="SASOL-INV-8834" />
            </Field>
          </div>
          {receiveForm.litresReceived && receiveForm.pricePerLitre && (
            <div style={{ marginTop: 12, padding: "10px 14px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: 8, fontSize: 13, color: "#166534" }}>
              Total cost: <strong>R {(Number(receiveForm.litresReceived) * Number(receiveForm.pricePerLitre)).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}</strong>
            </div>
          )}
          {error && <ErrMsg msg={error} />}
          <ModalFooter
            onCancel={() => { setShowReceive(null); setError("") }}
            onSubmit={() => receiveFuel.mutate({
              tankId: showReceive.id,
              body: {
                litresReceived: Number(receiveForm.litresReceived),
                pricePerLitre: Number(receiveForm.pricePerLitre),
                receivedAt: new Date().toISOString(),
                supplierId: receiveForm.supplierId || null,
                deliveryNote: receiveForm.deliveryNote || null,
                invoiceRef: receiveForm.invoiceRef || null,
              }
            })}
            loading={receiveFuel.isPending}
            disabled={!receiveForm.litresReceived || !receiveForm.pricePerLitre}
            label="Receive Fuel"
          />
        </Modal>
      )}

      {/* Dip Reading Modal */}
      {showDip && (
        <Modal title={`Dip Reading — ${showDip.name}`} onClose={() => { setShowDip(null); setError("") }}>
          <div style={{ marginBottom: 14, padding: "10px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, color: "#475569" }}>
            System level: <strong>{showDip.currentLitres.toLocaleString()} L</strong>
            {dipForm.actualLitres && (
              <> &nbsp;→ Variance: <strong style={{ color: Number(dipForm.actualLitres) < showDip.currentLitres ? "#DC2626" : "#166534" }}>
                {(showDip.currentLitres - Number(dipForm.actualLitres)).toFixed(1)} L
              </strong></>
            )}
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <Field label="Actual Litres (dip measurement) *">
              <MInput value={dipForm.actualLitres} onChange={v => setDipForm(f => ({ ...f, actualLitres: v }))} placeholder={showDip.currentLitres.toString()} type="number" />
            </Field>
            <Field label="Read By">
              <MInput value={dipForm.readBy} onChange={v => setDipForm(f => ({ ...f, readBy: v }))} placeholder="James Dlamini" />
            </Field>
          </div>
          <div style={{ marginTop: 14 }}>
            <Field label="Notes">
              <MInput value={dipForm.notes} onChange={v => setDipForm(f => ({ ...f, notes: v }))} placeholder="End of day reading" />
            </Field>
          </div>
          {error && <ErrMsg msg={error} />}
          <ModalFooter
            onCancel={() => { setShowDip(null); setError("") }}
            onSubmit={() => recordDip.mutate({
              tankId: showDip.id,
              body: { actualLitres: Number(dipForm.actualLitres), readAt: new Date().toISOString(), readBy: dipForm.readBy || null, notes: dipForm.notes || null }
            })}
            loading={recordDip.isPending}
            disabled={!dipForm.actualLitres}
            label="Record Reading"
          />
        </Modal>
      )}
    </div>
  )
}

// ── Shared helpers ─────────────────────────────────────────────────────────────
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 520, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}
function MInput({ value, onChange, placeholder, type = "text" }: { value: string; onChange: (v: string) => void; placeholder?: string; type?: string }) {
  return <input type={type} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} style={{ width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
}
function ErrMsg({ msg }: { msg: string }) {
  return <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{msg}</div>
}
function ModalFooter({ onCancel, onSubmit, loading, disabled, label }: { onCancel: () => void; onSubmit: () => void; loading: boolean; disabled: boolean; label: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
      <button onClick={onSubmit} disabled={disabled || loading} style={{ padding: "9px 20px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 500, cursor: "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnTeal: React.CSSProperties = { background: "#0D9488", color: "#fff", border: "none", borderRadius: 7, padding: "6px 14px", fontSize: 13, fontWeight: 500, cursor: "pointer" }
const btnOutline: React.CSSProperties = { background: "#fff", color: "#475569", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 14px", fontSize: 13, cursor: "pointer" }
const selectStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
