// src/pages/property/PropertiesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, ChevronDown, ChevronUp, Building2, MapPin, Layers } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtR   = (n: any) => n != null && Number(n) > 0 ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

const PROPERTY_TYPES = ["RESIDENTIAL","COMMERCIAL","INDUSTRIAL","RETAIL","MIXED_USE","LAND","OTHER"]
const UNIT_TYPES     = ["STUDIO","1BED","2BED","3BED","4BED","PENTHOUSE","COMMERCIAL","RETAIL","WAREHOUSE","PARKING","OTHER"]

const TYPE_COLOR: Record<string, string> = {
  RESIDENTIAL: "#1D4ED8", COMMERCIAL: "#0D9488", INDUSTRIAL: "#D97706",
  RETAIL: "#7C3AED", MIXED_USE: "#166534", LAND: "#92400E", OTHER: "#64748B",
}

const STATUS_CFG: Record<string, { color: string; bg: string }> = {
  VACANT:      { color: "#166534", bg: "#DCFCE7" },
  OCCUPIED:    { color: "#1D4ED8", bg: "#EFF6FF" },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB" },
  RESERVED:    { color: "#7C3AED", bg: "#F5F3FF" },
}

const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none", background: "#fff" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

export default function PropertiesTab() {
  const qc = useQueryClient()
  const [expanded, setExpanded]     = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [showAddUnit, setShowUnit]  = useState<string | null>(null)
  const [error, setError]           = useState("")

  const [form, setForm] = useState({
    name: "", propertyType: "RESIDENTIAL", description: "",
    purchasePrice: "", marketValue: "",
    street: "", suburb: "", city: "", province: "", postalCode: "",
  })

  const [unitForm, setUnitForm] = useState({
    unitNumber: "", unitType: "1BED", floorNumber: "", sizeSqm: "",
    baseRent: "", depositAmount: "", furnished: false,
  })

  const { data: properties = [], isLoading } = useQuery<any[]>({
    queryKey: ["properties"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/property/properties?size=100")),
  })

  const { data: expanded_property } = useQuery<any>({
    queryKey: ["property", expanded],
    enabled: !!expanded,
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/property/properties/${expanded}`)
      return r.data?.data ?? r.data
    },
  })

  const createProperty = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/property/properties", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["properties"] }); setShowCreate(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create property"),
  })

  const addUnit = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/property/properties/${id}/units`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["property", showAddUnit] }); setShowUnit(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to add unit"),
  })

  const deleteProperty = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/property/properties/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["properties"] }); setExpanded(null) },
    onError: (e: any) => alert(e.response?.data?.message ?? "Cannot delete property"),
  })

  const units = expanded_property?.units ?? []

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 18 }}>
        <button onClick={() => { setShowCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add Property
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading...</div>
      ) : (properties as any[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Building2 size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No properties yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Add your first property to start managing your portfolio.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {(properties as any[]).map(p => {
            const color = TYPE_COLOR[p.propertyType] ?? "#64748B"
            const isOpen = expanded === p.id
            const pct    = p.totalUnits > 0 ? Math.round((p.occupiedUnits / p.totalUnits) * 100) : 0

            return (
              <div key={p.id} style={{ border: "1px solid #E2E8F0", borderLeft: `3px solid ${color}`, borderRadius: 10, overflow: "hidden" }}>
                <div onClick={() => setExpanded(isOpen ? null : p.id)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 20px", cursor: "pointer", background: isOpen ? "#F8FAFC" : "#fff" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
                      <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{p.name}</span>
                      <span style={{ background: `${color}18`, color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{p.propertyType.replace("_"," ")}</span>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 14, fontSize: 12, color: "#64748B" }}>
                      {p.address && <span><MapPin size={11} style={{ verticalAlign: "middle", marginRight: 2 }} />{p.address.suburb}, {p.address.city}</span>}
                      <span><Layers size={11} style={{ verticalAlign: "middle", marginRight: 2 }} />{p.totalUnits} units</span>
                      <span style={{ fontWeight: 600, color: pct >= 80 ? "#166534" : pct >= 50 ? "#D97706" : "#DC2626" }}>{pct}% occupied</span>
                      {p.marketValue > 0 && <span style={{ fontWeight: 600, color: "#1B3A6B" }}>{fmtR(p.marketValue)}</span>}
                    </div>
                  </div>
                  {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                </div>

                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "16px 20px", background: "#FAFAFA" }}>
                    {/* Occupancy bar */}
                    <div style={{ marginBottom: 18 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, color: "#64748B", marginBottom: 5 }}>
                        <span>Occupancy</span>
                        <span>{p.occupiedUnits} occupied · {p.vacantUnits} vacant</span>
                      </div>
                      <div style={{ height: 7, background: "#E2E8F0", borderRadius: 99, overflow: "hidden" }}>
                        <div style={{ height: "100%", width: `${pct}%`, background: pct >= 80 ? "#16A34A" : pct >= 50 ? "#D97706" : "#DC2626", borderRadius: 99 }} />
                      </div>
                    </div>

                    {/* Units grid */}
                    <div style={{ marginBottom: 16 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                        <span style={{ fontSize: 11, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const, letterSpacing: "0.06em" }}>Units</span>
                        <button onClick={() => { setShowUnit(p.id); setError("") }}
                          style={{ display: "flex", alignItems: "center", gap: 4, padding: "4px 10px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 12, fontWeight: 600, cursor: "pointer", color: "#374151" }}>
                          <Plus size={11} /> Add Unit
                        </button>
                      </div>
                      {units.length === 0 ? (
                        <div style={{ fontSize: 13, color: "#94A3B8" }}>No units added yet.</div>
                      ) : (
                        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 10 }}>
                          {units.map((u: any) => {
                            const sc = STATUS_CFG[u.status] ?? { color: "#64748B", bg: "#F8FAFC" }
                            return (
                              <div key={u.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: "12px 14px" }}>
                                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
                                  <span style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>Unit {u.unitNumber}</span>
                                  <span style={{ background: sc.bg, color: sc.color, padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{u.status}</span>
                                </div>
                                <div style={{ fontSize: 11, color: "#64748B" }}>{u.unitType.replace("_"," ")} {u.sizeSqm && `· ${u.sizeSqm}m²`}</div>
                                <div style={{ fontSize: 12, fontWeight: 700, color: "#1B3A6B", marginTop: 4 }}>{fmtR(u.baseRent)}/mo</div>
                                {u.furnished && <div style={{ fontSize: 10, color: "#0D9488", marginTop: 2, fontWeight: 600 }}>Furnished</div>}
                              </div>
                            )
                          })}
                        </div>
                      )}
                    </div>

                    <div style={{ display: "flex", justifyContent: "flex-end" }}>
                      <button onClick={() => deleteProperty.mutate(p.id)}
                        style={{ padding: "6px 14px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                        Delete Property
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Create property modal */}
      {showCreate && (
        <Modal title="Add Property" onClose={() => setShowCreate(false)}>
          <Sect title="Basic details">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Property name *</label>
                <input autoFocus value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="Sunrise Apartments" style={inp} />
              </div>
              <div>
                <label style={lbl}>Property type *</label>
                <select value={form.propertyType} onChange={e => setForm(f => ({ ...f, propertyType: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                  {PROPERTY_TYPES.map(t => <option key={t} value={t}>{t.replace("_"," ")}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Market value (R)</label>
                <input type="number" value={form.marketValue} onChange={e => setForm(f => ({ ...f, marketValue: e.target.value }))} placeholder="0.00" style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description</label>
                <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={2} style={{ ...inp, resize: "vertical" as const }} />
              </div>
            </div>
          </Sect>
          <Sect title="Address">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Street address *</label>
                <input value={form.street} onChange={e => setForm(f => ({ ...f, street: e.target.value }))} placeholder="12 Main Road" style={inp} />
              </div>
              <div><label style={lbl}>Suburb</label><input value={form.suburb} onChange={e => setForm(f => ({ ...f, suburb: e.target.value }))} placeholder="Sandton" style={inp} /></div>
              <div><label style={lbl}>City *</label><input value={form.city} onChange={e => setForm(f => ({ ...f, city: e.target.value }))} placeholder="Johannesburg" style={inp} /></div>
              <div><label style={lbl}>Province</label><input value={form.province} onChange={e => setForm(f => ({ ...f, province: e.target.value }))} placeholder="Gauteng" style={inp} /></div>
              <div><label style={lbl}>Postal code</label><input value={form.postalCode} onChange={e => setForm(f => ({ ...f, postalCode: e.target.value }))} placeholder="2196" style={inp} /></div>
            </div>
          </Sect>
          {error && <ErrBox msg={error} />}
          <ModalFoot onCancel={() => setShowCreate(false)} loading={createProperty.isPending}
            disabled={!form.name || !form.city} label="Add Property"
            onSubmit={() => createProperty.mutate({
              name: form.name, propertyType: form.propertyType, description: form.description || null,
              purchasePrice: parseFloat(form.purchasePrice) || null,
              marketValue: parseFloat(form.marketValue) || null,
              address: { street: form.street, suburb: form.suburb, city: form.city, province: form.province, postalCode: form.postalCode },
            })} />
        </Modal>
      )}

      {/* Add unit modal */}
      {showAddUnit && (
        <Modal title="Add Unit" onClose={() => setShowUnit(null)}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <div>
              <label style={lbl}>Unit number *</label>
              <input autoFocus value={unitForm.unitNumber} onChange={e => setUnitForm(f => ({ ...f, unitNumber: e.target.value }))} placeholder="1A" style={inp} />
            </div>
            <div>
              <label style={lbl}>Unit type *</label>
              <select value={unitForm.unitType} onChange={e => setUnitForm(f => ({ ...f, unitType: e.target.value }))} style={{ ...inp, background: "#fff" }}>
                {UNIT_TYPES.map(t => <option key={t} value={t}>{t.replace("_"," ")}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Monthly rent (R) *</label>
              <input type="number" value={unitForm.baseRent} onChange={e => setUnitForm(f => ({ ...f, baseRent: e.target.value }))} placeholder="8500.00" style={inp} />
            </div>
            <div>
              <label style={lbl}>Deposit (R)</label>
              <input type="number" value={unitForm.depositAmount} onChange={e => setUnitForm(f => ({ ...f, depositAmount: e.target.value }))} placeholder="17000.00" style={inp} />
            </div>
            <div>
              <label style={lbl}>Size (m²)</label>
              <input type="number" value={unitForm.sizeSqm} onChange={e => setUnitForm(f => ({ ...f, sizeSqm: e.target.value }))} placeholder="65" style={inp} />
            </div>
            <div>
              <label style={lbl}>Floor</label>
              <input type="number" value={unitForm.floorNumber} onChange={e => setUnitForm(f => ({ ...f, floorNumber: e.target.value }))} placeholder="1" style={inp} />
            </div>
            <div style={{ gridColumn: "1/-1" }}>
              <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, fontWeight: 600, color: "#374151", cursor: "pointer" }}>
                <input type="checkbox" checked={unitForm.furnished} onChange={e => setUnitForm(f => ({ ...f, furnished: e.target.checked }))} />
                Furnished
              </label>
            </div>
          </div>
          {error && <ErrBox msg={error} />}
          <ModalFoot onCancel={() => setShowUnit(null)} loading={addUnit.isPending}
            disabled={!unitForm.unitNumber || !unitForm.baseRent} label="Add Unit"
            onSubmit={() => addUnit.mutate({ id: showAddUnit, body: {
              unitNumber: unitForm.unitNumber, unitType: unitForm.unitType,
              floorNumber: parseInt(unitForm.floorNumber) || null,
              sizeSqm: parseFloat(unitForm.sizeSqm) || null,
              baseRent: parseFloat(unitForm.baseRent),
              depositAmount: parseFloat(unitForm.depositAmount) || null,
              furnished: unitForm.furnished,
            }})} />
        </Modal>
      )}
    </div>
  )
}

function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const, letterSpacing: "0.07em", marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>
      {children}
    </div>
  )
}
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 580, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
function ModalFoot({ onCancel, onSubmit, loading, disabled, label }: any) {
  return (
    <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
      <button onClick={onSubmit} disabled={disabled || loading}
        style={{ padding: "9px 22px", background: disabled ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}
function ErrBox({ msg }: { msg: string }) {
  return <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{msg}</div>
}
