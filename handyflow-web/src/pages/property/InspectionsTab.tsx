import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, ClipboardCheck, X } from "lucide-react"

interface Unit { id: string; propertyId: string; unitNumber: string; status: string }
interface Property { id: string; name: string }
interface InspectionItem { room: string; condition: string; notes: string | null }
interface Inspection {
  id: string; unitId: string; leaseId: string | null; type: string
  inspectedAt: string; inspectedBy: string | null
  overallCondition: string; notes: string | null
  items: InspectionItem[]; createdAt: string
}

const INSPECTION_TYPES = ["MOVE_IN","MOVE_OUT","ROUTINE","MAINTENANCE","EMERGENCY"]
const CONDITIONS       = ["EXCELLENT","GOOD","FAIR","POOR","DAMAGED"]
const CONDITION_COLOR: Record<string, string> = {
  EXCELLENT: "#166534", GOOD: "#0D9488", FAIR: "#D97706", POOR: "#DC2626", DAMAGED: "#7C3AED",
}

export default function InspectionsTab() {
  const qc = useQueryClient()
  const [selectedUnit, setSelectedUnit] = useState("")
  const [showAdd, setShowAdd]           = useState(false)
  const [error, setError]               = useState("")
  const [form, setForm] = useState({
    type: "MOVE_IN", inspectedBy: "", overallCondition: "GOOD", notes: "",
    items: [{ room: "", condition: "GOOD", notes: "" }],
  })

  // Fetch properties for name lookup
  const { data: properties = [] } = useQuery<Property[]>({
    queryKey: ["properties"],
    queryFn: async () => (await apiClient.get("/api/v1/property/properties?size=50")).data.content,
  })

  // Fetch ALL units from dedicated endpoint — NOT from properties list
  const { data: allUnits = [] } = useQuery<Unit[]>({
    queryKey: ["property-units"],
    queryFn: async () => (await apiClient.get("/api/v1/property/units?size=200")).data.content,
  })

  const { data: inspections = [], isLoading } = useQuery<Inspection[]>({
    queryKey: ["inspections", selectedUnit],
    queryFn: async () => {
      if (!selectedUnit) return []
      const res = await apiClient.get(`/api/v1/property/units/${selectedUnit}/inspections?size=50`)
      return res.data.content as Inspection[]
    },
    enabled: !!selectedUnit,
  })

  const createInspection = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/property/units/${selectedUnit}/inspections`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["inspections", selectedUnit] })
      setShowAdd(false)
      setForm({ type: "MOVE_IN", inspectedBy: "", overallCondition: "GOOD", notes: "", items: [{ room: "", condition: "GOOD", notes: "" }] })
      setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to record inspection"),
  })

  const propertyMap = Object.fromEntries(properties.map(p => [p.id, p.name]))
  const selectedUnitObj = allUnits.find(u => u.id === selectedUnit)

  const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })

  const addItem    = () => setForm(f => ({ ...f, items: [...f.items, { room: "", condition: "GOOD", notes: "" }] }))
  const removeItem = (i: number) => setForm(f => ({ ...f, items: f.items.filter((_, idx) => idx !== i) }))
  const updateItem = (i: number, field: string, value: string) =>
    setForm(f => ({ ...f, items: f.items.map((item, idx) => idx === i ? { ...item, [field]: value } : item) }))

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <label style={{ fontSize: 14, fontWeight: 500, color: "#374151" }}>Select Unit:</label>
          <select value={selectedUnit} onChange={e => setSelectedUnit(e.target.value)}
            style={{ padding: "9px 14px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff", minWidth: 300 }}>
            <option value="">Choose a unit...</option>
            {allUnits.map(u => (
              <option key={u.id} value={u.id}>
                {propertyMap[u.propertyId] ?? "Unknown"} – Unit {u.unitNumber} ({u.status})
              </option>
            ))}
          </select>
          {allUnits.length === 0 && (
            <span style={{ fontSize: 13, color: "#D97706" }}>No units found — add units in the Properties tab first.</span>
          )}
        </div>
        {selectedUnit && (
          <button onClick={() => setShowAdd(true)} style={btnPrimary}>
            <Plus size={15} /> Record Inspection
          </button>
        )}
      </div>

      {!selectedUnit ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <ClipboardCheck size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a unit to view inspections</div>
        </div>
      ) : isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading inspections...</div>
      ) : inspections.length === 0 ? (
        <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8" }}>
          <ClipboardCheck size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
          <div>No inspections yet for this unit.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {inspections.map(insp => {
            const condColor = CONDITION_COLOR[insp.overallCondition] || "#64748B"
            return (
              <div key={insp.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div style={{ display: "flex", gap: 14 }}>
                    <div style={{ width: 42, height: 42, borderRadius: 10, background: "#F0FDF4", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <ClipboardCheck size={20} color="#0D9488" />
                    </div>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                        <span style={{ background: "#F1F5F9", color: "#374151", padding: "2px 10px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>{insp.type.replace(/_/g, " ")}</span>
                        <span style={{ fontWeight: 600, color: "#0F172A" }}>{insp.inspectedBy || "Unknown inspector"}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>{fmtDate(insp.inspectedAt)} at {fmtTime(insp.inspectedAt)}</div>
                      {insp.notes && <div style={{ fontSize: 12, color: "#64748B", marginTop: 4 }}>{insp.notes}</div>}
                    </div>
                  </div>
                  <div style={{ textAlign: "right" }}>
                    <div style={{ fontSize: 12, color: "#64748B", marginBottom: 3 }}>Overall condition</div>
                    <span style={{ background: `${condColor}18`, color: condColor, padding: "4px 12px", borderRadius: 20, fontSize: 13, fontWeight: 700 }}>
                      {insp.overallCondition}
                    </span>
                  </div>
                </div>
                {insp.items && insp.items.length > 0 && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "12px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: 8 }}>
                      {insp.items.map((item, i) => {
                        const c = CONDITION_COLOR[item.condition] || "#64748B"
                        return (
                          <div key={i} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: "10px 12px" }}>
                            <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A", marginBottom: 3 }}>{item.room}</div>
                            <span style={{ background: `${c}18`, color: c, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{item.condition}</span>
                            {item.notes && <div style={{ fontSize: 11, color: "#64748B", marginTop: 4 }}>{item.notes}</div>}
                          </div>
                        )
                      })}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Record Inspection Modal */}
      {showAdd && selectedUnitObj && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 580, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>
                Record Inspection — Unit {selectedUnitObj.unitNumber}
              </h3>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 16 }}>
              <div>
                <label style={lbl}>Inspection Type</label>
                <select value={form.type} onChange={e => setForm(f => ({ ...f, type: e.target.value }))} style={sel}>
                  {INSPECTION_TYPES.map(t => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Overall Condition</label>
                <select value={form.overallCondition} onChange={e => setForm(f => ({ ...f, overallCondition: e.target.value }))} style={sel}>
                  {CONDITIONS.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Inspected By</label>
                <input value={form.inspectedBy} onChange={e => setForm(f => ({ ...f, inspectedBy: e.target.value }))} placeholder="Thabo Mokoena" style={inp} />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>General Notes</label>
                <input value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))} placeholder="Overall condition notes..." style={inp} />
              </div>
            </div>

            <div style={{ marginBottom: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                <label style={{ fontSize: 13, fontWeight: 600, color: "#374151" }}>ROOM CHECKLIST</label>
                <button onClick={addItem} style={{ fontSize: 13, color: "#0D9488", background: "none", border: "none", cursor: "pointer", fontWeight: 500 }}>+ Add Room</button>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {form.items.map((item, i) => (
                  <div key={i} style={{ display: "grid", gridTemplateColumns: "1fr 140px 1fr auto", gap: 8, alignItems: "center" }}>
                    <input value={item.room} onChange={e => updateItem(i, "room", e.target.value)} placeholder="Kitchen" style={inp} />
                    <select value={item.condition} onChange={e => updateItem(i, "condition", e.target.value)} style={sel}>
                      {CONDITIONS.map(c => <option key={c} value={c}>{c}</option>)}
                    </select>
                    <input value={item.notes || ""} onChange={e => updateItem(i, "notes", e.target.value)} placeholder="Notes (optional)" style={inp} />
                    <button onClick={() => removeItem(i)} disabled={form.items.length === 1}
                      style={{ background: "none", border: "none", cursor: "pointer", color: "#FDA29B", padding: "0 4px" }}>
                      <X size={16} />
                    </button>
                  </div>
                ))}
              </div>
            </div>

            {error && <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>{error}</div>}

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={cancelBtn}>Cancel</button>
              <button
                onClick={() => createInspection.mutate({ type: form.type, inspectedAt: new Date().toISOString(), inspectedBy: form.inspectedBy || null, overallCondition: form.overallCondition, notes: form.notes || null, items: form.items.filter(item => item.room) })}
                disabled={createInspection.isPending}
                style={submitBtn}>
                {createInspection.isPending ? "Recording..." : "Record Inspection"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const lbl: React.CSSProperties        = { display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }
const sel: React.CSSProperties        = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
const inp: React.CSSProperties        = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }
const cancelBtn: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const submitBtn: React.CSSProperties  = { padding: "9px 20px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer" }
