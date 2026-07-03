// src/pages/earthmoving/OperatorLogsTab.tsx
import { useState } from "react"
import { Plus, Users, CheckCircle2 } from "lucide-react"
import type { OperatorLog } from "./shared/types"
import { fmtDate, fmtTime } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useAssets, useOperatorLogs, useStartOperatorLog, useCompleteOperatorLog } from "./shared/hooks"

export default function OperatorLogsTab() {
  const [selectedAsset, setSelectedAsset] = useState("")
  const [showAdd, setShowAdd]             = useState(false)
  const [completing, setCompleting]       = useState<OperatorLog | null>(null)
  const [apiError, setApiError]           = useState("")
  const [form, setForm] = useState({
    operatorName: "", siteName: "", startHours: "",
    startedAt: new Date().toISOString().slice(0, 16),
  })
  const [completeForm, setCompleteForm] = useState({
    endedAt: new Date().toISOString().slice(0, 16), endHours: "", fuelUsedLitres: "", notes: "",
  })

  const { data: assets = [] } = useAssets()
  const { data: logs = [], isLoading } = useOperatorLogs(selectedAsset)

  const addLog = useStartOperatorLog(selectedAsset, () => {
    setShowAdd(false)
    setForm({ operatorName: "", siteName: "", startHours: "", startedAt: new Date().toISOString().slice(0, 16) })
    setApiError("")
  })

  const completeLog = useCompleteOperatorLog(selectedAsset, () => {
    setCompleting(null); setApiError("")
  })

  const selectedAssetObj = assets.find(a => a.id === selectedAsset)
  const totalHours = logs.reduce((s, l) => s + (l.hoursLogged ?? 0), 0)
  const uniqueOps  = new Set(logs.map(l => l.operatorName)).size

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Equipment:</label>
          <select value={selectedAsset} onChange={e => setSelectedAsset(e.target.value)}
            style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300, outline: "none" }}>
            <option value="">Choose equipment...</option>
            {assets.map(a => <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} — ` : ""}{a.name}</option>)}
          </select>
        </div>
        {selectedAsset && (
          <button onClick={() => { setShowAdd(true); setApiError(""); if (selectedAssetObj) setForm(f => ({ ...f, startHours: String(selectedAssetObj.currentHours) })) }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> Log Session
          </button>
        )}
      </div>

      {selectedAsset && logs.length > 0 && (
        <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
          {[
            { label: "Total Sessions", value: logs.length, color: "#1B3A6B" },
            { label: "Total Hours", value: `${totalHours.toFixed(1)} hrs`, color: "#0D9488" },
            { label: "Unique Operators", value: uniqueOps, color: "#7C3AED" },
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
      ) : logs.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
          No operator logs recorded for this equipment yet.
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Operator", "Site", "Date", "Start", "End", "Hours", "Fuel", ""].map(h => (
                  <th key={h} style={{ padding: "11px 16px", textAlign: "left", fontWeight: 700, fontSize: 11, color: "#64748B", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {logs.map((log, i) => {
                const isOpen = log.endedAt == null
                return (
                  <tr key={log.id} style={{ borderBottom: i < logs.length - 1 ? "1px solid #F1F5F9" : "none", background: isOpen ? "#F0FDF4" : "#fff" }}>
                    <td style={{ padding: "13px 16px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <div style={{ width: 30, height: 30, borderRadius: "50%", background: "#EFF6FF", border: "2px solid #BFDBFE", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 700, fontSize: 11, color: "#1D4ED8", flexShrink: 0 }}>
                          {(log.operatorName || "?")[0].toUpperCase()}
                        </div>
                        <span style={{ fontWeight: 600, color: "#0F172A" }}>{log.operatorName || "—"}</span>
                        {isOpen && (
                          <span style={{ fontSize: 10, fontWeight: 700, background: "#DCFCE7", color: "#166534", padding: "1px 7px", borderRadius: 20 }}>ACTIVE</span>
                        )}
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
                    <td style={{ padding: "13px 16px" }}>
                      {isOpen && (
                        <button
                          onClick={() => {
                            setCompleting(log)
                            setCompleteForm({ endedAt: new Date().toISOString().slice(0, 16), endHours: "", fuelUsedLitres: "", notes: "" })
                            setApiError("")
                          }}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", background: "#166534", color: "#fff", border: "none", borderRadius: 6, fontSize: 11, fontWeight: 700, cursor: "pointer" }}>
                          <CheckCircle2 size={12} /> Complete
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Start session modal */}
      {showAdd && selectedAssetObj && (
        <Overlay onClose={() => setShowAdd(false)} width={480}>
          <ModalHead title="Log Operator Session"
            subtitle={`${selectedAssetObj.fleetNumber ? `${selectedAssetObj.fleetNumber} — ` : ""}${selectedAssetObj.name}`}
            onClose={() => setShowAdd(false)} />

          <div style={{ marginBottom: 16, padding: "10px 14px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 8, fontSize: 13, color: "#0369A1" }}>
            Current meter: <strong>{selectedAssetObj.currentHours.toLocaleString()} hrs</strong>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={lbl}>Operator Name</label>
              <input value={form.operatorName} onChange={e => setForm(f => ({ ...f, operatorName: e.target.value }))} placeholder="James Dlamini" style={inputStyle()} autoFocus />
            </div>
            <div>
              <label style={lbl}>Site / Location</label>
              <input value={form.siteName} onChange={e => setForm(f => ({ ...f, siteName: e.target.value }))} placeholder="Sandton Excavation Site" style={inputStyle()} />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Session Start *</label>
                <input type="datetime-local" value={form.startedAt} onChange={e => setForm(f => ({ ...f, startedAt: e.target.value }))} style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Starting Hour Reading</label>
                <input type="number" value={form.startHours} onChange={e => setForm(f => ({ ...f, startHours: e.target.value }))}
                  placeholder={String(selectedAssetObj.currentHours)} style={inputStyle()} />
                <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 3 }}>Pre-filled from current reading</div>
              </div>
            </div>
          </div>

          {apiError && <ErrBanner msg={apiError} />}

          <ModalFoot
            onCancel={() => setShowAdd(false)}
            onSubmit={() => addLog.mutate({
              operatorName: form.operatorName || null,
              siteName: form.siteName || null,
              startedAt: new Date(form.startedAt).toISOString(),
              startHours: form.startHours ? Number(form.startHours) : selectedAssetObj.currentHours,
            }, {
              onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to log session — this asset may already have an open shift."),
            })}
            loading={addLog.isPending}
            label="Start Session"
          />
        </Overlay>
      )}

      {/* Complete session modal */}
      {completing && (
        <Overlay onClose={() => setCompleting(null)} width={440}>
          <ModalHead title="Complete Shift" subtitle={completing.operatorName ?? undefined} onClose={() => setCompleting(null)} />

          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div>
              <label style={lbl}>Session End *</label>
              <input type="datetime-local" value={completeForm.endedAt} onChange={e => setCompleteForm(f => ({ ...f, endedAt: e.target.value }))} style={inputStyle()} />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Ending Hour Reading</label>
                <input type="number" value={completeForm.endHours} onChange={e => setCompleteForm(f => ({ ...f, endHours: e.target.value }))} style={inputStyle()} />
              </div>
              <div>
                <label style={lbl}>Fuel Used (L)</label>
                <input type="number" value={completeForm.fuelUsedLitres} onChange={e => setCompleteForm(f => ({ ...f, fuelUsedLitres: e.target.value }))} style={inputStyle()} />
              </div>
            </div>
            <div>
              <label style={lbl}>Notes</label>
              <input value={completeForm.notes} onChange={e => setCompleteForm(f => ({ ...f, notes: e.target.value }))} placeholder="Any handover notes..." style={inputStyle()} />
            </div>
          </div>

          {apiError && <ErrBanner msg={apiError} />}

          <ModalFoot
            onCancel={() => setCompleting(null)}
            onSubmit={() => completeLog.mutate({
              logId: completing.id,
              body: {
                endedAt: new Date(completeForm.endedAt).toISOString(),
                endHours: completeForm.endHours ? Number(completeForm.endHours) : null,
                fuelUsedLitres: completeForm.fuelUsedLitres ? Number(completeForm.fuelUsedLitres) : null,
                notes: completeForm.notes || null,
              },
            }, {
              onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to complete shift"),
            })}
            loading={completeLog.isPending}
            label="Complete Shift"
          />
        </Overlay>
      )}
    </div>
  )
}
