// src/pages/earthmoving/DeploymentsTab.tsx
import { useState } from "react"
import { Plus, MapPin, Clock, AlertCircle } from "lucide-react"
import { EMOJI } from "./shared/constants"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useAssets, useDeployAsset, useReturnToYard } from "./shared/hooks"

const EMPTY_FORM = {
  assetId: "", siteName: "", clientName: "", startDate: "", expectedEndDate: "",
  contactName: "", contactPhone: "", notes: "",
}

export default function DeploymentsTab() {
  const [showDeploy, setShowDeploy] = useState(false)
  const [apiError, setApiError]     = useState("")
  const [form, setForm]             = useState(EMPTY_FORM)

  const { data: assets = [] } = useAssets()
  const deployedAssets  = assets.filter(a => ["DEPLOYED", "HIRED_OUT"].includes(a.status))
  const availableAssets = assets.filter(a => a.status === "AVAILABLE")

  const deploy = useDeployAsset(() => { setShowDeploy(false); setForm(EMPTY_FORM); setApiError("") })
  const returnToYard = useReturnToYard()

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {[
          { label: "Currently deployed", value: deployedAssets.length, color: "#1D4ED8" },
          { label: "Available to deploy", value: availableAssets.length, color: "#166534" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
        <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>Active Deployments</span>
        <button onClick={() => { setShowDeploy(true); setApiError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Deploy Equipment
        </button>
      </div>

      {deployedAssets.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", border: "1px dashed #E2E8F0", borderRadius: 12, color: "#94A3B8" }}>
          <MapPin size={36} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No active deployments</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Deploy available equipment to a site to track it here.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {deployedAssets.map(a => (
            <div key={a.id} style={{ border: "1px solid #BFDBFE", borderLeft: "4px solid #1D4ED8", borderRadius: 10, padding: "16px 20px", background: "#fff" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ width: 44, height: 44, borderRadius: 10, background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 22, flexShrink: 0 }}>
                    {EMOJI[a.assetType] ?? "🚧"}
                  </div>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                      {a.fleetNumber && <span style={{ fontWeight: 800, color: "#D97706", background: "#FFFBEB", border: "1px solid #FDE68A", padding: "1px 8px", borderRadius: 6, fontSize: 12 }}>{a.fleetNumber}</span>}
                      <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{a.name}</span>
                    </div>
                    <div style={{ fontSize: 13, color: "#64748B", display: "flex", gap: 14, flexWrap: "wrap" }}>
                      {a.currentSite && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><MapPin size={11} color="#1D4ED8" /> {a.currentSite}</span>}
                      {a.currentClient && <span>Client: {a.currentClient}</span>}
                      <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Clock size={11} /> {(a.currentHours ?? 0).toLocaleString()} hrs</span>
                    </div>
                  </div>
                </div>
                <button
                  onClick={() => returnToYard.mutate(a.id, { onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to return asset") })}
                  disabled={returnToYard.isPending}
                  style={{ padding: "7px 14px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                  Return to Yard
                </button>
              </div>
            </div>
          ))}
          {apiError && <ErrBanner msg={apiError} />}
        </div>
      )}

      {showDeploy && (
        <Overlay onClose={() => setShowDeploy(false)} width={560}>
          <ModalHead title="Deploy Equipment" onClose={() => setShowDeploy(false)} />

          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={lbl}>Equipment *</label>
              {availableAssets.length === 0 ? (
                <div style={{ padding: "10px 12px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 13, color: "#92400E" }}>
                  No equipment available. All assets are deployed or in maintenance.
                </div>
              ) : (
                <select value={form.assetId} onChange={e => setForm(f => ({ ...f, assetId: e.target.value }))} style={{ ...inputStyle(), background: "#fff" }}>
                  <option value="">Select equipment...</option>
                  {availableAssets.map(a => (
                    <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name} ({a.assetType})</option>
                  ))}
                </select>
              )}
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Site Name *</label>
                <input value={form.siteName} onChange={e => setForm(f => ({ ...f, siteName: e.target.value }))} placeholder="Sandton Excavation Site" style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Client / Company *</label>
                <input value={form.clientName} onChange={e => setForm(f => ({ ...f, clientName: e.target.value }))} placeholder="Zeta Construction" style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Start Date</label>
                <input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Expected Return Date</label>
                <input type="date" value={form.expectedEndDate} onChange={e => setForm(f => ({ ...f, expectedEndDate: e.target.value }))} style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Site Contact</label>
                <input value={form.contactName} onChange={e => setForm(f => ({ ...f, contactName: e.target.value }))} placeholder="John Smith" style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Contact Phone</label>
                <input value={form.contactPhone} onChange={e => setForm(f => ({ ...f, contactPhone: e.target.value }))} placeholder="+27 82 111 2233" style={inputStyle()} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Notes</label>
                <input value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} placeholder="Any deployment notes..." style={inputStyle()} />
              </div>
            </div>
          </div>

          {apiError && <ErrBanner msg={apiError} />}

          <ModalFoot
            onCancel={() => setShowDeploy(false)}
            onSubmit={() => deploy.mutate({
              assetId: form.assetId, siteName: form.siteName, clientName: form.clientName || null,
              startDate: form.startDate || null, expectedEndDate: form.expectedEndDate || null,
              contactName: form.contactName || null, contactPhone: form.contactPhone || null,
              notes: form.notes || null,
            }, {
              onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to deploy asset"),
            })}
            loading={deploy.isPending}
            label="Deploy Equipment"
            disabled={!form.assetId || !form.siteName}
          />
        </Overlay>
      )}
    </div>
  )
}
