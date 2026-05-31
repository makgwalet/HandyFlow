// src/pages/earthmoving/OperatorLogsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Users, X, AlertCircle } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }

interface Asset { id: string; name: string; fleetNumber: string | null; currentHours: number }
interface OperatorLog {
  id: string; operatorName: string | null; siteName: string | null
  startedAt: string; endedAt: string | null
  hoursLogged: number | null; fuelUsedLitres: number | null
}

export default function OperatorLogsTab() {
  const qc = useQueryClient()
  const [selectedAsset, setSelectedAsset] = useState("")
  const [showAdd, setShowAdd]             = useState(false)
  const [apiError, setApiError]           = useState("")
  const [form, setForm] = useState({
    operatorName: "", siteName: "", startHours: "",
    startedAt: new Date().toISOString().slice(0, 16),
  })

  const { data: assets = [] } = useQuery<Asset[]>({
    queryKey: ["em-assets"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/earthmoving/assets?size=200")),
  })

  const { data: logs = [], isLoading } = useQuery<OperatorLog[]>({
    queryKey: ["em-oplogs", selectedAsset],
    queryFn: async () => selectedAsset ? unwrap(await apiClient.get(`/api/v1/earthmoving/assets/${selectedAsset}/operator-logs?size=100`)) : [],
    enabled: !!selectedAsset,
  })

  const addLog = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/earthmoving/assets/${selectedAsset}/operator-logs`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["em-oplogs", selectedAsset] })
      setShowAdd(false)
      setForm({ operatorName: "", siteName: "", startHours: "", startedAt: new Date().toISOString().slice(0, 16) })
      setApiError("")
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to log session"),
  })

  const selectedAssetObj = (assets as Asset[]).find(a => a.id === selectedAsset)
  const totalHours = (logs as OperatorLog[]).reduce((s, l) => s + (l.hoursLogged ?? 0), 0)
  const uniqueOps  = new Set((logs as OperatorLog[]).map(l => l.operatorName)).size

  const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box" as const, border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Equipment:</label>
          <select value={selectedAsset} onChange={e => setSelectedAsset(e.target.value)}
            style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300, outline: "none" }}>
            <option value="">Choose equipment...</option>
            {(assets as Asset[]).map(a => <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}</option>)}
          </select>
        </div>
        {selectedAsset && (
          <button onClick={() => { setShowAdd(true); setApiError(""); if (selectedAssetObj) setForm(f => ({ ...f, startHours: String(selectedAssetObj.currentHours) })) }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> Log Session
          </button>
        )}
      </div>

      {/* Stats for selected asset */}
      {selectedAsset && (logs as OperatorLog[]).length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
          {[
            { label: "Total Sessions",   value: (logs as OperatorLog[]).length, color: "#1B3A6B" },
            { label: "Total Hours",      value: `${totalHours.toFixed(1)} hrs`, color: "#0D9488" },
            { label: "Unique Operators", value: uniqueOps,                      color: "#7C3AED" },
          ].map(s => (
            <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
              <div style={{ fontSize: 20, fontWeight: 700, color: s.color }}>{s.value}</div>
              <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{s.label}</div>
            </div>
          ))}
        </div>
      )}

      {!selectedAsset ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select equipment to view operator logs</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Operator logs track who operated each machine, when, and for how long.</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : (logs as OperatorLog[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          No operator logs recorded for this equipment yet.
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Operator","Site","Date","Start","End","Hours","Fuel"].map(h => (
                  <th key={h} style={{ padding: "11px 16px", textAlign: "left", fontWeight: 700, fontSize: 11, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(logs as OperatorLog[]).map((log, i) => (
                <tr key={log.id} style={{ borderBottom: i < logs.length - 1 ? "1px solid #F1F5F9" : "none", background: "#fff" }}>
                  <td style={{ padding: "13px 16px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                      <div style={{ width: 30, height: 30, borderRadius: "50%", background: "#EFF6FF", border: "2px solid #BFDBFE", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 11, color: "#1D4ED8", flexShrink: 0 }}>
                        {(log.operatorName || "?")[0].toUpperCase()}
                      </div>
                      <span style={{ fontWeight: 600, color: "#0F172A" }}>{log.operatorName || "—"}</span>
                    </div>
                  </td>
                  <td style={{ padding: "13px 16px", color: "#475569" }}>{log.siteName || "—"}</td>
                  <td style={{ padding: "13px 16px", color: "#475569", fontSize: 12 }}>{fmtDate(log.startedAt)}</td>
                  <td style={{ padding: "13px 16px", color: "#475569", fontFamily: "monospace" }}>{fmtTime(log.startedAt)}</td>
                  <td style={{ padding: "13px 16px", color: "#475569", fontFamily: "monospace" }}>{log.endedAt ? fmtTime(log.endedAt) : "—"}</td>
                  <td style={{ padding: "13px 16px", fontWeight: 600, color: "#0D9488" }}>
                    {log.hoursLogged != null ? `${log.hoursLogged.toFixed(1)} hrs` : "—"}
                  </td>
                  <td style={{ padding: "13px 16px", color: "#475569" }}>
                    {log.fuelUsedLitres != null ? `${log.fuelUsedLitres.toFixed(1)} L` : "—"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Log session modal */}
      {showAdd && selectedAssetObj && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 480, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: "0 0 3px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Log Operator Session</h3>
                <div style={{ fontSize: 12, color: "#94A3B8" }}>{selectedAssetObj.fleetNumber ? `${selectedAssetObj.fleetNumber} — ` : ""}{selectedAssetObj.name}</div>
              </div>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ marginBottom: 16, padding: "10px 14px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 8, fontSize: 13, color: "#0369A1" }}>
              Current meter: <strong>{selectedAssetObj.currentHours.toLocaleString()} hrs</strong>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Operator Name</label>
                <input value={form.operatorName} onChange={e => setForm(f => ({ ...f, operatorName: e.target.value }))} placeholder="James Dlamini" style={inp} autoFocus />
              </div>
              <div>
                <label style={lbl}>Site / Location</label>
                <input value={form.siteName} onChange={e => setForm(f => ({ ...f, siteName: e.target.value }))} placeholder="Sandton Excavation Site" style={inp} />
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Session Start *</label>
                  <input type="datetime-local" value={form.startedAt} onChange={e => setForm(f => ({ ...f, startedAt: e.target.value }))} style={inp} />
                </div>
                <div>
                  <label style={lbl}>Starting Hour Reading</label>
                  <input type="number" value={form.startHours} onChange={e => setForm(f => ({ ...f, startHours: e.target.value }))}
                    placeholder={String(selectedAssetObj.currentHours)} style={inp} />
                  <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Pre-filled from current reading</div>
                </div>
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
                onClick={() => addLog.mutate({
                  operatorName: form.operatorName || null,
                  siteName: form.siteName || null,
                  startedAt: new Date(form.startedAt).toISOString(),
                  startHours: form.startHours ? Number(form.startHours) : selectedAssetObj.currentHours,
                })}
                disabled={addLog.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {addLog.isPending ? "Logging..." : "Start Session"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
