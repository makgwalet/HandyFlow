// src/pages/fuel/DispatchesTab.tsx
// KEY FIX: res.data?.data?.content — was res.data.content which skips ApiResponse wrapper
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Fuel, X, AlertCircle, AlertTriangle } from "lucide-react"

const unwrap     = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const unwrapList = (r: any): any[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }
const fmtDate    = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
const fmtTime    = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
const fmtR       = (n: any)     => n != null ? `R ${Number(n).toFixed(4)}` : "—"

const EMPTY_FORM = { tankId: "", litresDispensed: "", pricePerLitre: "", recipientName: "", authorisedBy: "", odometerReading: "", hoursReading: "", notes: "" }

export default function DispatchesTab() {
  const qc = useQueryClient()
  const [showDispatch, setShowDispatch] = useState(false)
  const [filterMonth, setFilterMonth]   = useState(new Date().toISOString().slice(0, 7))
  const [error, setError]               = useState("")
  const [form, setForm]                 = useState(EMPTY_FORM)

  const { data: dispatches = [], isLoading } = useQuery<any[]>({
    queryKey: ["dispatches"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/dispatches?size=200&sort=dispatchedAt,desc")),
  })

  const { data: tanks = [] } = useQuery<any[]>({
    queryKey: ["tanks"],
    queryFn: async () => unwrapList(await apiClient.get("/api/v1/fuel/tanks")),
  })

  const dispatchFuel = useMutation({
    mutationFn: ({ tankId, body }: { tankId: string; body: any }) =>
      apiClient.post(`/api/v1/fuel/tanks/${tankId}/dispatch`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["dispatches"] }); qc.invalidateQueries({ queryKey: ["tanks"] }); setShowDispatch(false); setForm(EMPTY_FORM); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to dispatch fuel"),
  })

  const filtered = (dispatches as any[]).filter(d => d.dispatchedAt?.startsWith(filterMonth))
  const selectedTank = (tanks as any[]).find(t => t.id === form.tankId)

  const totalLitres = filtered.reduce((s, d) => s + Number(d.litresDispensed ?? 0), 0)

  const months: string[] = []
  for (let i = 0; i < 6; i++) {
    const d = new Date(); d.setMonth(d.getMonth() - i)
    months.push(d.toISOString().slice(0, 7))
  }

  const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
  const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

  return (
    <div>
      {/* Stats */}
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {[
          { label: "Total dispatches",  value: filtered.length,                           color: "#1B3A6B" },
          { label: "Total litres out",  value: `${totalLitres.toLocaleString()} L`,       color: "#DC2626" },
          { label: "Unique recipients", value: new Set(filtered.map(d => d.recipientName)).size, color: "#0D9488" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {months.map(m => (
            <button key={m} onClick={() => setFilterMonth(m)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterMonth === m ? 600 : 400,
                background: filterMonth === m ? "#1B3A6B" : "#F1F5F9",
                color: filterMonth === m ? "#fff" : "#64748B" }}>
              {new Date(m + "-01").toLocaleDateString("en-ZA", { month: "short", year: "numeric" })}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowDispatch(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Dispatch Fuel
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Fuel size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No dispatches for this period</div>
        </div>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
            <thead>
              <tr style={{ background: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}>
                {["Recipient","Litres","Level Change","Price/L","Authorised By","Odometer / Hours","Date & Time"].map(h => (
                  <th key={h} style={{ padding: "11px 14px", textAlign: "left", fontWeight: 700, fontSize: 11, color: "#64748B", letterSpacing: "0.05em", whiteSpace: "nowrap" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((d, i) => (
                <tr key={d.id} style={{ borderBottom: i < filtered.length - 1 ? "1px solid #F1F5F9" : "none", background: "#fff" }}>
                  <td style={{ padding: "12px 14px" }}>
                    <div style={{ fontWeight: 600, color: "#0F172A" }}>{d.recipientName || "—"}</div>
                  </td>
                  <td style={{ padding: "12px 14px", fontWeight: 700, color: "#DC2626", whiteSpace: "nowrap" }}>
                    −{Number(d.litresDispensed).toLocaleString()} L
                  </td>
                  <td style={{ padding: "12px 14px", fontSize: 12, color: "#475569", whiteSpace: "nowrap" }}>
                    {d.levelBefore != null ? `${Number(d.levelBefore).toLocaleString()} L` : "—"}
                    <span style={{ margin: "0 5px", color: "#CBD5E1" }}>→</span>
                    {d.levelAfter != null ? <strong style={{ color: "#0D9488" }}>{Number(d.levelAfter).toLocaleString()} L</strong> : "—"}
                  </td>
                  <td style={{ padding: "12px 14px", color: "#475569" }}>{d.pricePerLitre ? `R ${Number(d.pricePerLitre).toFixed(4)}` : "—"}</td>
                  <td style={{ padding: "12px 14px", color: "#475569" }}>{d.authorisedBy || "—"}</td>
                  <td style={{ padding: "12px 14px", fontSize: 12, color: "#64748B" }}>
                    {d.odometerReading ? `${Number(d.odometerReading).toLocaleString()} km` : ""}
                    {d.hoursReading ? ` ${Number(d.hoursReading).toFixed(1)} hrs` : ""}
                    {!d.odometerReading && !d.hoursReading ? "—" : ""}
                  </td>
                  <td style={{ padding: "12px 14px", color: "#64748B", fontSize: 12, whiteSpace: "nowrap" }}>
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
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Dispatch Fuel</h3>
              <button onClick={() => { setShowDispatch(false); setError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Source Tank *</label>
                <select value={form.tankId} onChange={e => setForm(f => ({ ...f, tankId: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                  <option value="">Select tank...</option>
                  {(tanks as any[]).map(t => <option key={t.id} value={t.id}>{t.name} — {Number(t.currentLitres).toLocaleString()} L available ({t.fuelType})</option>)}
                </select>
              </div>

              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
                <div>
                  <label style={lbl}>Litres to Dispatch *</label>
                  <input type="number" value={form.litresDispensed} onChange={e => setForm(f => ({ ...f, litresDispensed: e.target.value }))} placeholder="350" style={inp} autoFocus />
                </div>
                <div>
                  <label style={lbl}>Price per Litre (R)</label>
                  <input type="number" step="0.0001" value={form.pricePerLitre} onChange={e => setForm(f => ({ ...f, pricePerLitre: e.target.value }))} placeholder="22.8500" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Recipient / Vehicle *</label>
                  <input value={form.recipientName} onChange={e => setForm(f => ({ ...f, recipientName: e.target.value }))} placeholder="CAT D9-001 Dozer / GP 34 56 JHB" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Authorised By</label>
                  <input value={form.authorisedBy} onChange={e => setForm(f => ({ ...f, authorisedBy: e.target.value }))} placeholder="Thabo Mokoena" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Odometer Reading (km)</label>
                  <input type="number" value={form.odometerReading} onChange={e => setForm(f => ({ ...f, odometerReading: e.target.value }))} placeholder="45 000" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Machine Hours</label>
                  <input type="number" step="0.1" value={form.hoursReading} onChange={e => setForm(f => ({ ...f, hoursReading: e.target.value }))} placeholder="4 250.0" style={inp} />
                </div>
              </div>

              {selectedTank && form.litresDispensed && (
                <div style={{ padding: "10px 14px", background: Number(form.litresDispensed) > Number(selectedTank.currentLitres) ? "#FEF2F2" : "#F0FDF4", border: `1px solid ${Number(form.litresDispensed) > Number(selectedTank.currentLitres) ? "#FECACA" : "#BBF7D0"}`, borderRadius: 8, fontSize: 13, fontWeight: 600, display: "flex", alignItems: "center", gap: 8, color: Number(form.litresDispensed) > Number(selectedTank.currentLitres) ? "#DC2626" : "#166534" }}>
                  {Number(form.litresDispensed) > Number(selectedTank.currentLitres)
                    ? <><AlertTriangle size={14} /> Insufficient stock — available: {Number(selectedTank.currentLitres).toLocaleString()} L</>
                    : <>After dispatch: {(Number(selectedTank.currentLitres) - Number(form.litresDispensed)).toLocaleString()} L remaining</>}
                </div>
              )}
            </div>

            {error && <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{error}</div>}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowDispatch(false); setError("") }} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => dispatchFuel.mutate({ tankId: form.tankId, body: { litresDispensed: Number(form.litresDispensed), pricePerLitre: form.pricePerLitre ? Number(form.pricePerLitre) : null, dispatchedAt: new Date().toISOString(), recipientName: form.recipientName || null, authorisedBy: form.authorisedBy || null, odometerReading: form.odometerReading ? Number(form.odometerReading) : null, hoursReading: form.hoursReading ? Number(form.hoursReading) : null, notes: form.notes || null } })}
                disabled={!form.tankId || !form.litresDispensed || !form.recipientName || Number(form.litresDispensed) > Number(selectedTank?.currentLitres ?? 0) || dispatchFuel.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {dispatchFuel.isPending ? "Dispatching..." : "Dispatch Fuel"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
