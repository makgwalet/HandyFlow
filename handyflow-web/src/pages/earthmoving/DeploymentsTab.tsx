// src/pages/earthmoving/DeploymentsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, MapPin, Clock, AlertCircle, CheckCircle } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }

export default function DeploymentsTab() {
  const qc = useQueryClient()
  const [showDeploy, setShowDeploy] = useState(false)
  const [apiError, setApiError]     = useState("")
  const [form, setForm] = useState({
    assetId: "", siteName: "", clientName: "", startDate: "", expectedEndDate: "",
    dailyRateOverride: "", contactName: "", contactPhone: "", notes: "",
  })

  const { data: assets = [] } = useQuery<any[]>({
    queryKey: ["em-assets"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/earthmoving/assets?size=200")),
  })

  const deployedAssets = (assets as any[]).filter(a => ["DEPLOYED", "HIRED_OUT"].includes(a.status))
  const availableAssets = (assets as any[]).filter(a => a.status === "AVAILABLE")

  const deploy = useMutation({
    mutationFn: (body: any) => apiClient.put(`/api/v1/earthmoving/assets/${body.assetId}/deploy`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["em-assets"] }); setShowDeploy(false); setForm({ assetId: "", siteName: "", clientName: "", startDate: "", expectedEndDate: "", dailyRateOverride: "", contactName: "", contactPhone: "", notes: "" }); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to deploy asset"),
  })

  const returnToYard = useMutation({
    mutationFn: (id: string) => apiClient.put(`/api/v1/earthmoving/assets/${id}/status`, { status: "AVAILABLE", note: "Returned from deployment" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["em-assets"] }),
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed"),
  })

  const fmtDate = (s: string | null | undefined) => s ? new Date(s).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

  const EMOJI: Record<string, string> = { DOZER:"🚜", EXCAVATOR:"⛏️", GRADER:"🛣️", LOADER:"🏗️", DUMPER:"🚛", CRANE:"🏗️", ROLLER:"🛞", OTHER:"🚧" }

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {[
          { label: "Currently deployed",  value: deployedAssets.length, color: "#1D4ED8" },
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
          {deployedAssets.map((a: any) => (
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
                <button onClick={() => returnToYard.mutate(a.id)} disabled={returnToYard.isPending}
                  style={{ padding: "7px 14px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                  Return to Yard
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Deploy modal */}
      {showDeploy && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Deploy Equipment</h3>
              <button onClick={() => setShowDeploy(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Equipment *</label>
                {availableAssets.length === 0 ? (
                  <div style={{ padding: "10px 12px", background: "#FEF3C7", border: "1px solid #FCD34D", borderRadius: 8, fontSize: 13, color: "#92400E" }}>
                    No equipment available. All assets are deployed or in maintenance.
                  </div>
                ) : (
                  <select value={form.assetId} onChange={e => setForm(f => ({ ...f, assetId: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                    <option value="">Select equipment...</option>
                    {availableAssets.map((a: any) => (
                      <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name} ({a.assetType})</option>
                    ))}
                  </select>
                )}
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Site Name *</label>
                  <input value={form.siteName} onChange={e => setForm(f => ({ ...f, siteName: e.target.value }))} placeholder="Sandton Excavation Site" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Client / Company *</label>
                  <input value={form.clientName} onChange={e => setForm(f => ({ ...f, clientName: e.target.value }))} placeholder="Zeta Construction" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Start Date</label>
                  <input type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inp} />
                </div>
                <div>
                  <label style={lbl}>Expected Return Date</label>
                  <input type="date" value={form.expectedEndDate} onChange={e => setForm(f => ({ ...f, expectedEndDate: e.target.value }))} style={inp} />
                </div>
                <div>
                  <label style={lbl}>Site Contact</label>
                  <input value={form.contactName} onChange={e => setForm(f => ({ ...f, contactName: e.target.value }))} placeholder="John Smith" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Contact Phone</label>
                  <input value={form.contactPhone} onChange={e => setForm(f => ({ ...f, contactPhone: e.target.value }))} placeholder="+27 82 111 2233" style={inp} />
                </div>
                <div style={{ gridColumn: "1 / -1" }}>
                  <label style={lbl}>Notes</label>
                  <input value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} placeholder="Any deployment notes..." style={inp} />
                </div>
              </div>
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={14} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowDeploy(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => deploy.mutate({ assetId: form.assetId, siteName: form.siteName, clientName: form.clientName || null, startDate: form.startDate || null, expectedEndDate: form.expectedEndDate || null, contactName: form.contactName || null, contactPhone: form.contactPhone || null, notes: form.notes || null })}
                disabled={!form.assetId || !form.siteName || deploy.isPending}
                style={{ padding: "9px 22px", background: !form.assetId || !form.siteName ? "#E2E8F0" : "#1B3A6B", color: !form.assetId || !form.siteName ? "#94A3B8" : "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {deploy.isPending ? "Deploying..." : "Deploy Equipment"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
