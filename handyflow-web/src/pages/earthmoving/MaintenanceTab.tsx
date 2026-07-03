// src/pages/earthmoving/MaintenanceTab.tsx
import { useState } from "react"
import { Plus, Wrench, AlertTriangle } from "lucide-react"
import { MAINTENANCE_TYPES, MAINTENANCE_TYPE_CFG } from "./shared/constants"
import { fmtDate, fmtCurrency2dp } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useAssets, useMaintenanceHistory, useCreateMaintenance } from "./shared/hooks"

const EMPTY_FORM = {
  type: "SERVICE", description: "", performedAt: new Date().toISOString().split("T")[0],
  hoursAtService: "", cost: "", supplier: "", invoiceRef: "",
}

export default function MaintenanceTab() {
  const [selectedAsset, setSelectedAsset] = useState("")
  const [showAdd, setShowAdd]             = useState(false)
  const [apiError, setApiError]           = useState("")
  const [form, setForm]                   = useState(EMPTY_FORM)

  const { data: assets = [] } = useAssets()
  const { data: records = [], isLoading } = useMaintenanceHistory(selectedAsset)

  const addMaintenance = useCreateMaintenance(selectedAsset, () => {
    setShowAdd(false); setForm(EMPTY_FORM); setApiError("")
  })

  const selectedAssetObj = assets.find(a => a.id === selectedAsset)
  const serviceAlerts    = assets.filter(a => a.dueForService)

  return (
    <div>
      {serviceAlerts.length > 0 && (
        <div style={{ marginBottom: 18, padding: "12px 16px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 10, display: "flex", alignItems: "center", gap: 10 }}>
          <AlertTriangle size={18} color="#D97706" style={{ flexShrink: 0 }} />
          <div>
            <div style={{ fontWeight: 700, fontSize: 13, color: "#D97706" }}>Service Due — {serviceAlerts.length} machine{serviceAlerts.length !== 1 ? "s" : ""}</div>
            <div style={{ fontSize: 12, color: "#92400E" }}>{serviceAlerts.map(a => a.fleetNumber ?? a.name).join(", ")}</div>
          </div>
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Equipment:</label>
          <select value={selectedAsset} onChange={e => setSelectedAsset(e.target.value)}
            style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300, outline: "none" }}>
            <option value="">Choose equipment...</option>
            {assets.map(a => (
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

      {selectedAssetObj && (
        <div style={{ marginBottom: 18, padding: "14px 18px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, display: "flex", gap: 24, flexWrap: "wrap" }}>
          {[
            { l: "Current Hours", v: `${selectedAssetObj.currentHours.toLocaleString()} hrs` },
            { l: "Last Service", v: `${selectedAssetObj.lastServiceHours.toLocaleString()} hrs` },
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
      ) : records.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          No maintenance records yet for this equipment.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {records.map(r => {
            const cfg = MAINTENANCE_TYPE_CFG[r.type] ?? MAINTENANCE_TYPE_CFG.OTHER
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
                  <div style={{ fontSize: 15, fontWeight: 700, color: "#0F172A", flexShrink: 0 }}>{fmtCurrency2dp(r.cost)}</div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showAdd && selectedAssetObj && (
        <Overlay onClose={() => setShowAdd(false)} width={560}>
          <ModalHead
            title="Record Maintenance"
            subtitle={`${selectedAssetObj.fleetNumber ? `${selectedAssetObj.fleetNumber} — ` : ""}${selectedAssetObj.name} · ${selectedAssetObj.currentHours.toLocaleString()} hrs`}
            onClose={() => setShowAdd(false)}
          />

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <div>
              <label style={lbl}>Type *</label>
              <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={{ ...inputStyle(), background: "#fff" }}>
                {MAINTENANCE_TYPES.map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Date Performed *</label>
              <input type="date" value={form.performedAt} onChange={e => setForm(f => ({ ...f, performedAt: e.target.value }))} style={inputStyle()} />
            </div>
            <div>
              <label style={lbl}>Hour Meter Reading</label>
              <input type="number" value={form.hoursAtService} onChange={e => setForm(f => ({ ...f, hoursAtService: e.target.value }))}
                placeholder={selectedAssetObj.currentHours.toString()} style={inputStyle()} />
              <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Pre-filled from current reading</div>
            </div>
            <div>
              <label style={lbl}>Cost (R)</label>
              <input type="number" value={form.cost} onChange={e => setForm(f => ({ ...f, cost: e.target.value }))} placeholder="8500" style={inputStyle()} />
            </div>
            <div style={{ gridColumn: "1 / -1" }}>
              <label style={lbl}>Description *</label>
              <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                placeholder={form.type === "SERVICE" ? "250-hour service — oil change, filters, grease all points" : "Describe the work performed..."}
                style={inputStyle()} autoFocus />
            </div>
            <div>
              <label style={lbl}>Supplier / Workshop</label>
              <input value={form.supplier} onChange={e => setForm(f => ({ ...f, supplier: e.target.value }))} placeholder="Barloworld Equipment" style={inputStyle()} />
            </div>
            <div>
              <label style={lbl}>Invoice Reference</label>
              <input value={form.invoiceRef} onChange={e => setForm(f => ({ ...f, invoiceRef: e.target.value }))} placeholder="INV-2024-0891" style={inputStyle()} />
            </div>
          </div>

          {apiError && <ErrBanner msg={apiError} />}

          <ModalFoot
            onCancel={() => setShowAdd(false)}
            onSubmit={() => addMaintenance.mutate({
              type: form.type,
              description: form.description,
              performedAt: new Date(form.performedAt).toISOString(),
              hoursAtService: form.hoursAtService ? Number(form.hoursAtService) : null,
              cost: form.cost ? Number(form.cost) : null,
              supplier: form.supplier || null,
              invoiceRef: form.invoiceRef || null,
            }, {
              onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to record maintenance"),
            })}
            loading={addMaintenance.isPending}
            label="Record Maintenance"
            disabled={!form.description}
          />
        </Overlay>
      )}
    </div>
  )
}
