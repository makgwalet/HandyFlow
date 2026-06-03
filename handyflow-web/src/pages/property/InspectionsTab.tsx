// src/pages/property/InspectionsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, ClipboardList, CheckCircle, AlertTriangle, Clock } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDT  = (d: any) => d ? new Date(d).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) : "—"

const TYPE_CFG: Record<string, { color: string; bg: string; label: string }> = {
  MOVE_IN:     { color: "#166534", bg: "#DCFCE7", label: "Move-in"    },
  MOVE_OUT:    { color: "#DC2626", bg: "#FEF2F2", label: "Move-out"   },
  ROUTINE:     { color: "#1D4ED8", bg: "#EFF6FF", label: "Routine"    },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB", label: "Maintenance"},
}
const CONDITION_CFG: Record<string, { color: string; icon: React.ElementType }> = {
  EXCELLENT: { color: "#166534", icon: CheckCircle  },
  GOOD:      { color: "#0D9488", icon: CheckCircle  },
  FAIR:      { color: "#D97706", icon: Clock        },
  POOR:      { color: "#DC2626", icon: AlertTriangle },
}

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none", background: "#fff" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

export default function InspectionsTab() {
  const qc = useQueryClient()
  const [selectedUnit, setUnit] = useState<any | null>(null)
  const [showCreate, setCreate] = useState(false)
  const [error, setError]       = useState("")

  const [form, setForm] = useState({
    leaseId: "", type: "ROUTINE", inspectedAt: new Date().toISOString().slice(0,16),
    inspectedBy: "", overallCondition: "GOOD", notes: "",
    rooms: [{ room: "Living Room", condition: "Good", notes: "" }],
  })

  const { data: units = [] } = useQuery<any[]>({
    queryKey: ["units-all"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/property/units?size=200")),
  })

  const { data: leases = [] } = useQuery<any[]>({
    queryKey: ["leases-for-unit", selectedUnit?.id],
    enabled: !!selectedUnit,
    queryFn: async () => {
      const all = unwrap(await apiClient.get(`/api/v1/property/leases?size=50`))
      return (all as any[]).filter(l => l.unitId === selectedUnit?.id)
    },
  })

  const { data: inspections = [], isLoading } = useQuery<any[]>({
    queryKey: ["inspections", selectedUnit?.id],
    enabled: !!selectedUnit,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/property/units/${selectedUnit.id}/inspections?size=50`)),
  })

  const createInspection = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/property/units/${selectedUnit?.id}/inspections`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["inspections"] }); setCreate(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to save inspection"),
  })

  const addRoom = () => setForm(f => ({ ...f, rooms: [...f.rooms, { room: "", condition: "Good", notes: "" }] }))
  const removeRoom = (i: number) => setForm(f => ({ ...f, rooms: f.rooms.filter((_, idx) => idx !== i) }))
  const updateRoom = (i: number, field: string, value: string) =>
    setForm(f => ({ ...f, rooms: f.rooms.map((r, idx) => idx === i ? { ...r, [field]: value } : r) }))

  return (
    <div>
      <div style={{ display: "flex", gap: 16 }}>
        {/* Unit selector */}
        <div style={{ width: 260, flexShrink: 0 }}>
          <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Select unit</div>
          <div style={{ display: "flex", flexDirection: "column" as const, gap: 5 }}>
            {(units as any[]).map(u => (
              <button key={u.id} onClick={() => setUnit(u)}
                style={{ width: "100%", textAlign: "left" as const, padding: "10px 12px", border: `1px solid ${selectedUnit?.id === u.id ? "#1B3A6B" : "#E2E8F0"}`, background: selectedUnit?.id === u.id ? "#EEF2FF" : "#fff", borderRadius: 9, cursor: "pointer" }}>
                <div style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>Unit {u.unitNumber}</div>
                <div style={{ fontSize: 11, color: "#64748B" }}>{u.status}</div>
              </button>
            ))}
            {(units as any[]).length === 0 && <div style={{ fontSize: 13, color: "#94A3B8" }}>No units found</div>}
          </div>
        </div>

        {/* Inspection list */}
        <div style={{ flex: 1 }}>
          {!selectedUnit ? (
            <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
              <ClipboardList size={36} style={{ marginBottom: 12, opacity: 0.3 }} />
              <div>Select a unit to view its inspection history</div>
            </div>
          ) : (
            <>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
                <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>Unit {selectedUnit.unitNumber} — Inspections</div>
                <button onClick={() => { setCreate(true); setError("") }}
                  style={{ display: "flex", alignItems: "center", gap: 6, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "8px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                  <Plus size={13} /> Log Inspection
                </button>
              </div>

              {isLoading ? (
                <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
              ) : (inspections as any[]).length === 0 ? (
                <div style={{ textAlign: "center", padding: "40px 20px", border: "1px dashed #E2E8F0", borderRadius: 10, color: "#94A3B8", fontSize: 13 }}>
                  No inspections recorded for this unit.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column" as const, gap: 10 }}>
                  {(inspections as any[]).map((ins: any) => {
                    const tc = TYPE_CFG[ins.type] ?? TYPE_CFG.ROUTINE
                    const cc = CONDITION_CFG[ins.overallCondition ?? "GOOD"]
                    const CondIcon = cc.icon
                    return (
                      <div key={ins.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, padding: "14px 16px" }}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 10 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                            <span style={{ background: tc.bg, color: tc.color, padding: "2px 9px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{tc.label}</span>
                            <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: cc.color, fontWeight: 600 }}>
                              <CondIcon size={13} />{ins.overallCondition ?? "—"}
                            </span>
                          </div>
                          <div style={{ fontSize: 12, color: "#94A3B8" }}>{fmtDT(ins.inspectedAt)}</div>
                        </div>
                        {ins.inspectedBy && <div style={{ fontSize: 12, color: "#64748B", marginBottom: 6 }}>Inspected by: <strong>{ins.inspectedBy}</strong></div>}
                        {ins.notes && <div style={{ fontSize: 13, color: "#475569", lineHeight: 1.6, marginBottom: ins.items?.length > 0 ? 10 : 0 }}>{ins.notes}</div>}
                        {ins.items?.length > 0 && (
                          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px,1fr))", gap: 7 }}>
                            {ins.items.map((item: any, i: number) => (
                              <div key={i} style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, padding: "7px 10px" }}>
                                <div style={{ fontWeight: 600, fontSize: 12, color: "#374151" }}>{item.room}</div>
                                <div style={{ fontSize: 11, color: "#94A3B8" }}>{item.condition}</div>
                                {item.notes && <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{item.notes}</div>}
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )
                  })}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Create inspection modal */}
      {showCreate && selectedUnit && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 640, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Log Inspection — Unit {selectedUnit.unitNumber}</h3>
              <button onClick={() => setCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 20 }}>
              <div>
                <label style={lbl}>Inspection type *</label>
                <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                  {Object.entries(TYPE_CFG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Overall condition</label>
                <select value={form.overallCondition} onChange={e => setForm(f => ({ ...f, overallCondition: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                  {["EXCELLENT","GOOD","FAIR","POOR"].map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Inspected at *</label>
                <input type="datetime-local" value={form.inspectedAt} onChange={e => setForm(f => ({ ...f, inspectedAt: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Inspected by</label>
                <input value={form.inspectedBy} onChange={e => setForm(f => ({ ...f, inspectedBy: e.target.value }))} placeholder="Name of inspector" style={inp} />
              </div>
              {leases.length > 0 && (
                <div>
                  <label style={lbl}>Related lease</label>
                  <select value={form.leaseId} onChange={e => setForm(f => ({ ...f, leaseId: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                    <option value="">None</option>
                    {(leases as any[]).map(l => <option key={l.id} value={l.id}>{l.lesseeName} · {l.status}</option>)}
                  </select>
                </div>
              )}
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>General notes</label>
                <textarea value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} rows={3} style={{ ...inp, resize: "vertical" as const }} />
              </div>
            </div>

            {/* Room-by-room items */}
            <div style={{ marginBottom: 20 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>Room-by-room condition</div>
                <button onClick={addRoom} style={{ display: "flex", alignItems: "center", gap: 4, padding: "4px 10px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 12, fontWeight: 600, cursor: "pointer", color: "#374151" }}>
                  <Plus size={11} /> Add room
                </button>
              </div>
              <div style={{ display: "flex", flexDirection: "column" as const, gap: 8 }}>
                {form.rooms.map((room, i) => (
                  <div key={i} style={{ display: "grid", gridTemplateColumns: "1fr 140px 1fr 32px", gap: 8, alignItems: "end" }}>
                    <div>
                      {i === 0 && <label style={lbl}>Room</label>}
                      <input value={room.room} onChange={e => updateRoom(i, "room", e.target.value)} placeholder="Living Room" style={inp} />
                    </div>
                    <div>
                      {i === 0 && <label style={lbl}>Condition</label>}
                      <select value={room.condition} onChange={e => updateRoom(i, "condition", e.target.value)} style={{ ...inp, background: "#fff" }}>
                        {["Excellent","Good","Fair","Poor"].map(c => <option key={c} value={c}>{c}</option>)}
                      </select>
                    </div>
                    <div>
                      {i === 0 && <label style={lbl}>Notes</label>}
                      <input value={room.notes} onChange={e => updateRoom(i, "notes", e.target.value)} placeholder="Optional notes" style={inp} />
                    </div>
                    <button onClick={() => removeRoom(i)} style={{ padding: "9px", background: "#FEF2F2", border: "none", borderRadius: 8, cursor: "pointer", color: "#DC2626", marginTop: i === 0 ? 20 : 0 }}>
                      <X size={14} />
                    </button>
                  </div>
                ))}
              </div>
            </div>

            {error && <div style={{ marginBottom: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setCreate(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={() => createInspection.mutate({
                leaseId: form.leaseId || null, type: form.type,
                inspectedAt: new Date(form.inspectedAt).toISOString(),
                inspectedBy: form.inspectedBy || null, overallCondition: form.overallCondition,
                notes: form.notes || null,
                items: form.rooms.filter(r => r.room),
              })} disabled={createInspection.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createInspection.isPending ? "Saving..." : "Save Inspection"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
