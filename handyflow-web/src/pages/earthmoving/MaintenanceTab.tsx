// src/pages/earthmoving/MaintenanceTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Wrench, X, AlertCircle, AlertTriangle } from "lucide-react"

interface Asset { id: string; name: string; fleetNumber: string | null; currentHours: number; dueForService: boolean; serviceIntervalHours: number; lastServiceHours: number }
interface MaintenanceRecord {
  id: string; type: string; description: string
  performedAt: string; hoursAtService: number | null
  cost: number | null; supplier: string | null; invoiceRef: string | null
}

const MAINTENANCE_TYPES = ["SERVICE","REPAIR","INSPECTION","TYRE","BATTERY","ELECTRICAL","HYDRAULICS","ENGINE","TRACKS","OTHER"]
const TYPE_CFG: Record<string, { color: string; bg: string }> = {
  SERVICE:    { color: "#166534", bg: "#DCFCE7" },
  REPAIR:     { color: "#DC2626", bg: "#FEF2F2" },
  INSPECTION: { color: "#1D4ED8", bg: "#EFF6FF" },
  TYRE:       { color: "#D97706", bg: "#FFFBEB" },
  BATTERY:    { color: "#7C3AED", bg: "#F3E8FF" },
  ELECTRICAL: { color: "#0369A1", bg: "#F0F9FF" },
  HYDRAULICS: { color: "#0891B2", bg: "#ECFEFF" },
  ENGINE:     { color: "#B45309", bg: "#FEF3C7" },
  TRACKS:     { color: "#374151", bg: "#F9FAFB" },
  OTHER:      { color: "#64748B", bg: "#F8FAFC" },
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const fmtR = (n: number | null) => n != null ? `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

export default function MaintenanceTab() {
  const qc = useQueryClient()
  const [selectedAsset, setSelectedAsset] = useState("")
  const [showAdd, setShowAdd]             = useState(false)
  const [apiError, setApiError]           = useState("")
  const [form, setForm] = useState({
    type: "SERVICE", description: "", performedAt: new Date().toISOString().split("T")[0],
    hoursAtService: "", cost: "", supplier: "", invoiceRef: "",
  })

  const { data: assets = [] } = useQuery<Asset[]>({
    queryKey: ["em-assets"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/earthmoving/assets?size=200")),
  })

  const { data: records = [], isLoading } = useQuery<MaintenanceRecord[]>({
    queryKey: ["em-maintenance", selectedAsset],
    queryFn: async () => selectedAsset ? unwrap(await apiClient.get(`/api/v1/earthmoving/assets/${selectedAsset}/maintenance?size=100`)) : [],
    enabled: !!selectedAsset,
  })

  const addMaintenance = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/earthmoving/assets/${selectedAsset}/maintenance`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["em-maintenance", selectedAsset] })
      qc.invalidateQueries({ queryKey: ["em-assets"] })
      setShowAdd(false)
      setForm({ type: "SERVICE", description: "", performedAt: new Date().toISOString().split("T")[0], hoursAtService: "", cost: "", supplier: "", invoiceRef: "" })
      setApiError("")
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to record maintenance"),
  })

  const selectedAssetObj = (assets as Asset[]).find(a => a.id === selectedAsset)
  const serviceAlerts    = (assets as Asset[]).filter(a => a.dueForService)

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      {/* Service alerts */}
      {serviceAlerts.length > 0 && (
        <div style={{ marginBottom: 18, padding: "12px 16px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={18} color="#D97706" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#D97706" }}>Service Due — {serviceAlerts.length} machine{serviceAlerts.length !== 1 ? "s" : ""}</div>
            <div style={{ fontSize: 12, color: "#92400E" }}>
              {serviceAlerts.map(a => a.fleetNumber ?? a.name).join(", ")}
            </div>
          </div>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Equipment:</label>
          <select value={selectedAsset} onChange={e => setSelectedAsset(e.target.value)}
            style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300, outline: "none" }}>
            <option value="">Choose equipment...</option>
            {(assets as Asset[]).map(a => (
              <option key={a.id} value={a.id}>
                {a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}{a.dueForService ? " ⚠ SERVICE DUE" : ""}
              </option>
            ))}
          </select>
        </div>
        {selectedAsset && (
          <button onClick={() => { setShowAdd(true); setApiError(""); if (selectedAssetObj) setForm(f => ({ ...f, hoursAtService: String(selectedAssetObj.currentHours) })) }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> Record Maintenance
          </button>
        )}
      </div>

      {/* Selected asset info */}
      {selectedAssetObj && (
        <div style={{ marginBottom: 18, padding: "14px 18px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, display: "flex", gap: 24, flexWrap: "wrap" }}>
          {[
            { l: "Current Hours",  v: `${selectedAssetObj.currentHours.toLocaleString()} hrs` },
            { l: "Last Service",   v: `${selectedAssetObj.lastServiceHours.toLocaleString()} hrs` },
            { l: "Hours Since Service", v: `${(selectedAssetObj.currentHours - selectedAssetObj.lastServiceHours).toFixed(0)} hrs` },
            { l: "Service Interval", v: `Every ${selectedAssetObj.serviceIntervalHours} hrs` },
          ].map(item => (
            <div key={item.l}>
              <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 2 }}>{item.l}</div>
              <div style={{ fontSize: 15, fontWeight: 700, color: selectedAssetObj.dueForService && item.l === "Hours Since Service" ? "#DC2626" : "#0F172A" }}>{item.v}</div>
            </div>
          ))}
          {selectedAssetObj.dueForService && (
            <div style={{ display: "flex", alignItems: "center", gap: 6, background: "#FEF2F2", color: "#DC2626", padding: "6px 12px", borderRadius: 8, fontSize: 12, fontWeight: 700 }}>
              <AlertTriangle size={13} /> Service overdue
            </div>
          )}
        </div>
      )}

      {!selectedAsset ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Wrench size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select equipment to view maintenance history</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : (records as MaintenanceRecord[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          No maintenance records yet for this equipment.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {(records as MaintenanceRecord[]).map(r => {
            const cfg = TYPE_CFG[r.type] ?? TYPE_CFG.OTHER
            return (
              <div key={r.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "14px 18px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div style={{ display: "flex", gap: 12 }}>
                    <div style={{ width: 40, height: 40, borderRadius: 9, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <Wrench size={18} color={cfg.color} />
                    </div>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                        <span style={{ background: cfg.bg, color: cfg.color, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{r.type}</span>
                        <span style={{ fontWeight: 600, fontSize: 14, color: "#0F172A" }}>{r.description}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {fmtDate(r.performedAt)}
                        {r.hoursAtService != null && ` · At ${r.hoursAtService.toLocaleString()} hrs`}
                        {r.supplier && ` · ${r.supplier}`}
                        {r.invoiceRef && ` · INV: ${r.invoiceRef}`}
                      </div>
                    </div>
                  </div>
                  <div style={{ fontSize: 15, fontWeight: 700, color: "#0F172A", flexShrink: 0 }}>{fmtR(r.cost)}</div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Record maintenance modal */}
      {showAdd && selectedAssetObj && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: "0 0 3px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Record Maintenance</h3>
                <div style={{ fontSize: 12, color: "#94A3B8" }}>
                  {selectedAssetObj.fleetNumber ? `${selectedAssetObj.fleetNumber} — ` : ""}{selectedAssetObj.name} · {selectedAssetObj.currentHours.toLocaleString()} hrs
                </div>
              </div>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Type *</label>
                <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                  {MAINTENANCE_TYPES.map(t => <option key={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Date Performed *</label>
                <input type="date" value={form.performedAt} onChange={e => setForm(f => ({ ...f, performedAt: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Hour Meter Reading</label>
                <input type="number" value={form.hoursAtService} onChange={e => setForm(f => ({ ...f, hoursAtService: e.target.value }))}
                  placeholder={selectedAssetObj.currentHours.toString()} style={inp} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Pre-filled from current reading</div>
              </div>
              <div>
                <label style={lbl}>Cost (R)</label>
                <input type="number" value={form.cost} onChange={e => setForm(f => ({ ...f, cost: e.target.value }))} placeholder="8500" style={inp} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Description *</label>
                <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  placeholder={form.type === "SERVICE" ? "250-hour service — oil change, filters, grease all points" : "Describe the work performed..."}
                  style={inp} autoFocus />
              </div>
              <div>
                <label style={lbl}>Supplier / Workshop</label>
                <input value={form.supplier} onChange={e => setForm(f => ({ ...f, supplier: e.target.value }))} placeholder="Barloworld Equipment" style={inp} />
              </div>
              <div>
                <label style={lbl}>Invoice Reference</label>
                <input value={form.invoiceRef} onChange={e => setForm(f => ({ ...f, invoiceRef: e.target.value }))} placeholder="INV-2024-0891" style={inp} />
              </div>
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => addMaintenance.mutate({
                  type: form.type,
                  description: form.description,
                  performedAt: new Date(form.performedAt).toISOString(),
                  hoursAtService: form.hoursAtService ? Number(form.hoursAtService) : null,
                  cost: form.cost ? Number(form.cost) : null,
                  supplier: form.supplier || null,
                  invoiceRef: form.invoiceRef || null,
                })}
                disabled={!form.description || addMaintenance.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {addMaintenance.isPending ? "Recording..." : "Record Maintenance"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
