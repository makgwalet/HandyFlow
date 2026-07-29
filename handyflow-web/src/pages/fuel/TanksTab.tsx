// src/pages/fuel/TanksTab.tsx
// KEY FIX: res.data (not res.data.content) — tanks endpoint returns a List, not Page
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Droplets, AlertTriangle, X, ChevronDown, ChevronUp, AlertCircle, Download } from "lucide-react"

interface Tank {
  id: string; name: string; fuelType: string
  capacityLitres: number; currentLitres: number
  fillPercentage: number; low: boolean; location: string; createdAt: string
}
interface Supplier { id: string; name: string }
interface DipReading {
  id: string; readAt: string; actualLitres: number; calculatedLitres: number
  varianceLitres: number; hasNegativeVariance: boolean; readBy: string | null
}

const FUEL_COLORS: Record<string, { color: string; bg: string }> = {
  DIESEL:   { color: "#1D4ED8", bg: "#EFF6FF" },
  PETROL:   { color: "#DC2626", bg: "#FEF2F2" },
  PARAFFIN: { color: "#D97706", bg: "#FFFBEB" },
  GAS:      { color: "#7C3AED", bg: "#F5F3FF" },
  OTHER:    { color: "#64748B", bg: "#F8FAFC" },
}

const unwrapList = (r: any): any[] => {
  const d = r.data?.data ?? r.data
  return Array.isArray(d) ? d : (d?.content ?? [])
}
const unwrapPage = (r: any): any[] => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })
const fmtR    = (n: any)     => n != null ? `R ${Number(n).toFixed(2)}` : "—"

export default function TanksTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd]       = useState(false)
  const [showReceive, setShowReceive] = useState<Tank | null>(null)
  const [showDip, setShowDip]       = useState<Tank | null>(null)
  const [expanded, setExpanded]     = useState<string | null>(null)
  const [error, setError]           = useState("")

  const [tankForm, setTankForm]     = useState({ name: "", fuelType: "DIESEL", capacityLitres: "", location: "", lowThresholdPct: "20" })
  const [receiveForm, setReceiveForm] = useState({ litresReceived: "", pricePerLitre: "", supplierId: "", deliveryNote: "", invoiceRef: "" })
  const [dipForm, setDipForm]       = useState({ actualLitres: "", readBy: "", notes: "" })

  const { data: tanks = [], isLoading } = useQuery<Tank[]>({
    queryKey: ["tanks"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/fuel/tanks")),
  })

  // FIX: "no tank capacity/utilization forecasting" gap — one batch call for
  // every tank's "days until empty" forecast, not one call per tank card.
  const { data: forecasts = [] } = useQuery<any[]>({
    queryKey: ["tank-utilization-forecasts"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/fuel/tanks/utilization-forecast")),
  })
  const forecastByTank = Object.fromEntries(forecasts.map((f: any) => [f.tankId, f]))

  const { data: suppliers = [] } = useQuery<Supplier[]>({
    queryKey: ["fuel-suppliers"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/fuel/suppliers")),
  })

  const dipHistory = useQuery<DipReading[]>({
    queryKey: ["dip-readings", expanded],
    queryFn: async () => expanded ? unwrapPage(await apiClient.get(`/api/v1/fuel/tanks/${expanded}/dip-readings?size=10`)) : [],
    enabled: !!expanded,
  })

  const createTank = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fuel/tanks", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["tanks"] }); setShowAdd(false); setTankForm({ name: "", fuelType: "DIESEL", capacityLitres: "", location: "", lowThresholdPct: "20" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create tank"),
  })

  const receiveFuel = useMutation({
    mutationFn: ({ tankId, body }: { tankId: string; body: any }) =>
      apiClient.post(`/api/v1/fuel/tanks/${tankId}/receive`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["tanks"] }); qc.invalidateQueries({ queryKey: ["receipts"] }); setShowReceive(null); setReceiveForm({ litresReceived: "", pricePerLitre: "", supplierId: "", deliveryNote: "", invoiceRef: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to receive fuel"),
  })

  const [prefilled, setPrefilled] = useState(false)

  // FIX: "no reorder-point automation" — the Receive button used to open a blank
  // form every time. This fetches a suggestion (top-up-to-capacity quantity,
  // plus the tank's last supplier and price) and pre-fills the form with it —
  // still fully editable, just not a blank slate.
  // FIX: "no dip-variance/reconciliation report PDF" gap — only the in-app
  // dip history list existed before; this is the exportable document a
  // depot manager can hand to ops/security when investigating a suspected
  // theft or leak. Downloads via blob (not a raw href) so it goes through
  // the same auth-aware apiClient as every other request.
  const downloadReconciliationReport = async (tank: Tank) => {
    const r = await apiClient.get(`/api/v1/fuel/tanks/${tank.id}/reconciliation-report`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([r.data]))
    const link = document.createElement("a")
    link.href = url
    link.download = `reconciliation-${tank.name.replace(/[^a-zA-Z0-9]+/g, "-")}.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  }

  const openReceive = async (tank: Tank) => {
    setError("")
    setPrefilled(false)
    try {
      const r = await apiClient.get(`/api/v1/fuel/tanks/${tank.id}/reorder-suggestion`)
      const s = r.data?.data ?? r.data
      setReceiveForm({
        litresReceived: s?.suggestedLitres != null ? String(s.suggestedLitres) : "",
        pricePerLitre: s?.lastPricePerLitre != null ? String(s.lastPricePerLitre) : "",
        supplierId: s?.lastSupplierId ?? "",
        deliveryNote: "",
        invoiceRef: "",
      })
      setPrefilled(!!(s?.suggestedLitres || s?.lastSupplierId))
    } catch {
      // Suggestion is a convenience, not a requirement — fall back to a blank form
      // rather than blocking the receive flow if the endpoint has a hiccup.
      setReceiveForm({ litresReceived: "", pricePerLitre: "", supplierId: "", deliveryNote: "", invoiceRef: "" })
    }
    setShowReceive(tank)
  }

  const recordDip = useMutation({
    mutationFn: ({ tankId, body }: { tankId: string; body: any }) =>
      apiClient.post(`/api/v1/fuel/tanks/${tankId}/dip-readings`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["tanks"] }); qc.invalidateQueries({ queryKey: ["dip-readings", expanded] }); setShowDip(null); setDipForm({ actualLitres: "", readBy: "", notes: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to record dip reading"),
  })

  const totalCapacity = (tanks as Tank[]).reduce((s, t) => s + t.capacityLitres, 0)
  const totalStock    = (tanks as Tank[]).reduce((s, t) => s + t.currentLitres, 0)
  const lowCount      = (tanks as Tank[]).filter(t => t.low).length

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
  const sel: React.CSSProperties = { ...inp, background: "#fff" }

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {[
          { label: "Tanks",          value: tanks.length,                            color: "#1B3A6B" },
          { label: "Total capacity", value: `${totalCapacity.toLocaleString()} L`,  color: "#0D9488" },
          { label: "Total stock",    value: `${totalStock.toLocaleString()} L`,      color: "#1D4ED8" },
          { label: "Low tanks",      value: lowCount,                                color: lowCount > 0 ? "#DC2626" : "#94A3B8" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Low stock alert */}
      {lowCount > 0 && (
        <div style={{ marginBottom: 18, padding: "12px 16px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={17} color="#DC2626" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#DC2626" }}>Low Stock</div>
            <div style={{ fontSize: 12, color: "#B91C1C" }}>{(tanks as Tank[]).filter(t => t.low).map(t => `${t.name} (${Number(t.currentLitres).toLocaleString()} L)`).join(", ")} — receive stock soon</div>
          </div>
        </div>
      )}

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <button onClick={() => { setShowAdd(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#0D9488", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add Tank
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading tanks...</div>
      ) : (tanks as Tank[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Droplets size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No tanks registered</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {(tanks as Tank[]).map(tank => {
            const cfg      = FUEL_COLORS[tank.fuelType] ?? FUEL_COLORS.OTHER
            const pct      = Math.min(100, Math.max(0, tank.fillPercentage ?? 0))
            const isOpen   = expanded === tank.id
            const dips     = (dipHistory.data ?? []) as DipReading[]
            const negDip   = dips.find(d => d.hasNegativeVariance)

            return (
              <div key={tank.id} style={{ border: `1px solid ${tank.low ? "#FECACA" : "#E2E8F0"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "18px 20px", background: "#fff" }}>
                  <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                      <div style={{ width: 42, height: 42, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                        <Droplets size={20} color={cfg.color} />
                      </div>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{tank.name}</div>
                        <div style={{ fontSize: 12, color: "#94A3B8" }}>{tank.fuelType}{tank.location ? ` · ${tank.location}` : ""}</div>
                      </div>
                      {tank.low && (
                        <span style={{ display: "flex", alignItems: "center", gap: 4, background: "#FEF2F2", color: "#DC2626", padding: "3px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <AlertTriangle size={10} /> LOW
                        </span>
                      )}
                      {negDip && isOpen && (
                        <span style={{ display: "flex", alignItems: "center", gap: 4, background: "#FFF7ED", color: "#EA580C", padding: "3px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                          <AlertCircle size={10} /> VARIANCE
                        </span>
                      )}
                    </div>
                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <button onClick={() => openReceive(tank)}
                        style={{ background: "#0D9488", color: "#fff", border: "none", borderRadius: 7, padding: "6px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                        + Receive
                      </button>
                      <button onClick={() => { setShowDip(tank); setDipForm({ actualLitres: String(tank.currentLitres), readBy: "", notes: "" }); setError("") }}
                        style={{ background: "#fff", color: "#475569", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 14px", fontSize: 13, cursor: "pointer" }}>
                        Dip Reading
                      </button>
                      <button onClick={() => setExpanded(isOpen ? null : tank.id)}
                        style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                        {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                      </button>
                    </div>
                  </div>

                  {/* Fill bar */}
                  <div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
                      <span style={{ fontSize: 13, color: "#475569", fontWeight: 500 }}>
                        {Number(tank.currentLitres).toLocaleString()} L
                        <span style={{ color: "#94A3B8", fontWeight: 400 }}> / {Number(tank.capacityLitres).toLocaleString()} L</span>
                      </span>
                      <span style={{ fontSize: 13, fontWeight: 700, color: tank.low ? "#DC2626" : cfg.color }}>{pct.toFixed(1)}%</span>
                    </div>
                    <div style={{ height: 10, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
                      <div style={{ height: "100%", borderRadius: 99, width: `${pct}%`,
                        background: tank.low ? "linear-gradient(90deg,#DC2626,#F87171)" : `linear-gradient(90deg,${cfg.color},${cfg.color}88)`,
                        transition: "width 0.5s ease" }} />
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: 11, color: "#94A3B8", marginTop: 4 }}>
                      <span>Available space: {(Number(tank.capacityLitres) - Number(tank.currentLitres)).toLocaleString()} L</span>
                      {tank.low && <span style={{ color: "#DC2626" }}>Below threshold</span>}
                    </div>
                    {(() => {
                      const f = forecastByTank[tank.id]
                      if (!f) return null
                      if (!f.hasSufficientData) {
                        return <div style={{ fontSize: 11, color: "#CBD5E1", marginTop: 6 }}>Not enough recent dispatch activity to forecast usage</div>
                      }
                      const urgent = f.daysUntilEmpty != null && f.daysUntilEmpty <= 14
                      return (
                        <div style={{ fontSize: 11, color: urgent ? "#D97706" : "#64748B", marginTop: 6, fontWeight: urgent ? 700 : 400 }}>
                          ~{f.daysUntilEmpty} day{f.daysUntilEmpty === 1 ? "" : "s"} until empty at current usage
                          <span style={{ color: "#94A3B8", fontWeight: 400 }}> ({Number(f.avgDailyLitres).toFixed(0)} L/day avg, last {f.lookbackDays}d)</span>
                        </div>
                      )
                    })()}
                  </div>
                </div>

                {/* Expanded — dip reading history */}
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                      <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>Dip Reading History</div>
                      <button onClick={() => downloadReconciliationReport(tank)}
                        style={{ display: "flex", alignItems: "center", gap: 5, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, padding: "5px 10px", fontSize: 12, fontWeight: 600, color: "#0D9488", cursor: "pointer" }}>
                        <Download size={12} /> Reconciliation report (PDF)
                      </button>
                    </div>
                    {dipHistory.isLoading ? (
                      <div style={{ fontSize: 13, color: "#94A3B8" }}>Loading...</div>
                    ) : dips.length === 0 ? (
                      <div style={{ fontSize: 13, color: "#94A3B8" }}>No dip readings recorded yet.</div>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                        {dips.map(d => (
                          <div key={d.id} style={{ display: "flex", alignItems: "center", gap: 14, padding: "8px 12px", background: d.hasNegativeVariance ? "#FEF2F2" : "#fff", border: `1px solid ${d.hasNegativeVariance ? "#FECACA" : "#E2E8F0"}`, borderRadius: 8, fontSize: 13 }}>
                            <div style={{ flex: 1 }}>
                              <span style={{ fontWeight: 600, color: "#0F172A" }}>{Number(d.actualLitres).toLocaleString()} L actual</span>
                              <span style={{ color: "#94A3B8", marginLeft: 8 }}>vs {Number(d.calculatedLitres).toLocaleString()} L system</span>
                            </div>
                            <div style={{ fontWeight: 700, color: d.hasNegativeVariance ? "#DC2626" : "#166534" }}>
                              {d.hasNegativeVariance ? "−" : "+"}{Math.abs(Number(d.varianceLitres)).toFixed(1)} L
                            </div>
                            {d.hasNegativeVariance && <span style={{ fontSize: 10, fontWeight: 700, background: "#FEF2F2", color: "#DC2626", padding: "1px 7px", borderRadius: 20 }}>INVESTIGATE</span>}
                            <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtDate(d.readAt)}{d.readBy ? ` · ${d.readBy}` : ""}</div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Add Tank Modal */}
      {showAdd && (
        <Overlay onClose={() => { setShowAdd(false); setError("") }}>
          <MHead title="Add Fuel Tank" onClose={() => { setShowAdd(false); setError("") }} />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <div style={{ gridColumn: "1 / -1" }}>
              <label style={lbl}>Tank Name *</label>
              <input autoFocus value={tankForm.name} onChange={e => setTankForm(f => ({ ...f, name: e.target.value }))} placeholder='e.g. "Main Diesel Tank — Depot A"' style={inp} />
            </div>
            <div>
              <label style={lbl}>Fuel Type</label>
              <select value={tankForm.fuelType} onChange={e => setTankForm(f => ({ ...f, fuelType: e.target.value }))} style={sel}>
                {["DIESEL","PETROL","PARAFFIN","GAS","OTHER"].map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Capacity (litres) *</label>
              <input type="number" value={tankForm.capacityLitres} onChange={e => setTankForm(f => ({ ...f, capacityLitres: e.target.value }))} placeholder="10000" style={inp} />
            </div>
            <div>
              <label style={lbl}>Location</label>
              <input value={tankForm.location} onChange={e => setTankForm(f => ({ ...f, location: e.target.value }))} placeholder="Depot Bay 1" style={inp} />
            </div>
            <div>
              <label style={lbl}>Low threshold (%)</label>
              <input type="number" value={tankForm.lowThresholdPct} onChange={e => setTankForm(f => ({ ...f, lowThresholdPct: e.target.value }))} placeholder="20" style={inp} />
              <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Alert when level falls below this percentage</div>
            </div>
          </div>
          {error && <ErrBanner msg={error} />}
          <MFoot onCancel={() => { setShowAdd(false); setError("") }}
            onSubmit={() => createTank.mutate({ name: tankForm.name, fuelType: tankForm.fuelType, capacityLitres: Number(tankForm.capacityLitres), location: tankForm.location || null })}
            loading={createTank.isPending} label="Create Tank"
            disabled={!tankForm.name || !tankForm.capacityLitres} />
        </Overlay>
      )}

      {/* Receive Fuel Modal */}
      {showReceive && (
        <Overlay onClose={() => { setShowReceive(null); setError("") }}>
          <MHead title={`Receive Fuel — ${showReceive.name}`} onClose={() => { setShowReceive(null); setError("") }} />
          {prefilled && (
            <div style={{ marginBottom: 14, padding: "10px 14px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: 8, fontSize: 12, color: "#166534" }}>
              Pre-filled to top up to capacity{receiveForm.supplierId ? ", using the last supplier for this tank" : ""} — feel free to adjust.
            </div>
          )}
          <div style={{ marginBottom: 14, padding: "10px 14px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 8, fontSize: 13, color: "#0369A1" }}>
            Available space: <strong>{(Number(showReceive.capacityLitres) - Number(showReceive.currentLitres)).toLocaleString()} L</strong>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <div>
              <label style={lbl}>Litres Received *</label>
              <input autoFocus type="number" value={receiveForm.litresReceived} onChange={e => setReceiveForm(f => ({ ...f, litresReceived: e.target.value }))} placeholder="5000" style={inp} />
            </div>
            <div>
              <label style={lbl}>Price per Litre (R) *</label>
              <input type="number" step="0.001" value={receiveForm.pricePerLitre} onChange={e => setReceiveForm(f => ({ ...f, pricePerLitre: e.target.value }))} placeholder="22.850" style={inp} />
            </div>
            <div>
              <label style={lbl}>Supplier</label>
              <select value={receiveForm.supplierId} onChange={e => setReceiveForm(f => ({ ...f, supplierId: e.target.value }))} style={sel}>
                <option value="">Select supplier...</option>
                {(suppliers as Supplier[]).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Delivery Note</label>
              <input value={receiveForm.deliveryNote} onChange={e => setReceiveForm(f => ({ ...f, deliveryNote: e.target.value }))} placeholder="DN-20260510-001" style={inp} />
            </div>
            <div style={{ gridColumn: "1 / -1" }}>
              <label style={lbl}>Invoice Reference</label>
              <input value={receiveForm.invoiceRef} onChange={e => setReceiveForm(f => ({ ...f, invoiceRef: e.target.value }))} placeholder="SASOL-INV-8834" style={inp} />
            </div>
          </div>
          {receiveForm.litresReceived && receiveForm.pricePerLitre && (
            <div style={{ marginTop: 12, padding: "10px 14px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: 8, fontSize: 13, color: "#166534", fontWeight: 600 }}>
              Total cost: {fmtR(Number(receiveForm.litresReceived) * Number(receiveForm.pricePerLitre))}
            </div>
          )}
          {error && <ErrBanner msg={error} />}
          <MFoot onCancel={() => { setShowReceive(null); setError("") }}
            onSubmit={() => receiveFuel.mutate({ tankId: showReceive.id, body: { litresReceived: Number(receiveForm.litresReceived), pricePerLitre: Number(receiveForm.pricePerLitre), receivedAt: new Date().toISOString(), supplierId: receiveForm.supplierId || null, deliveryNote: receiveForm.deliveryNote || null, invoiceRef: receiveForm.invoiceRef || null } })}
            loading={receiveFuel.isPending} label="Receive Fuel"
            disabled={!receiveForm.litresReceived || !receiveForm.pricePerLitre} />
        </Overlay>
      )}

      {/* Dip Reading Modal */}
      {showDip && (
        <Overlay onClose={() => { setShowDip(null); setError("") }}>
          <MHead title={`Dip Reading — ${showDip.name}`} onClose={() => { setShowDip(null); setError("") }} />
          <div style={{ marginBottom: 16, padding: "12px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13 }}>
            System level: <strong>{Number(showDip.currentLitres).toLocaleString()} L</strong>
            {dipForm.actualLitres && (
              <> &nbsp;·&nbsp; Variance: <strong style={{ color: Number(dipForm.actualLitres) < Number(showDip.currentLitres) ? "#DC2626" : "#166534" }}>
                {(Number(showDip.currentLitres) - Number(dipForm.actualLitres)).toFixed(1)} L
                {Number(dipForm.actualLitres) < Number(showDip.currentLitres) && " — negative variance, investigate"}
              </strong></>
            )}
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <div>
              <label style={lbl}>Actual Litres (physical dip) *</label>
              <input autoFocus type="number" value={dipForm.actualLitres} onChange={e => setDipForm(f => ({ ...f, actualLitres: e.target.value }))} placeholder={String(showDip.currentLitres)} style={{ ...inp, fontSize: 18, fontWeight: 700 }} />
            </div>
            <div>
              <label style={lbl}>Read By</label>
              <input value={dipForm.readBy} onChange={e => setDipForm(f => ({ ...f, readBy: e.target.value }))} placeholder="James Dlamini" style={inp} />
            </div>
            <div style={{ gridColumn: "1 / -1" }}>
              <label style={lbl}>Notes</label>
              <input value={dipForm.notes} onChange={e => setDipForm(f => ({ ...f, notes: e.target.value }))} placeholder="End of day reading / shift handover" style={inp} />
            </div>
          </div>
          {error && <ErrBanner msg={error} />}
          <MFoot onCancel={() => { setShowDip(null); setError("") }}
            onSubmit={() => recordDip.mutate({ tankId: showDip.id, body: { actualLitres: Number(dipForm.actualLitres), readAt: new Date().toISOString(), readBy: dipForm.readBy || null, notes: dipForm.notes || null } })}
            loading={recordDip.isPending} label="Record Reading"
            disabled={!dipForm.actualLitres} />
        </Overlay>
      )}
    </div>
  )
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button><button onClick={onSubmit} disabled={loading || disabled} style={{ padding: "9px 22px", background: loading || disabled ? "#94A3B8" : "#0D9488", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{msg}</div>
}
