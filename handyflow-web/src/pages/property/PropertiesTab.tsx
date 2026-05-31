import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Building2, Home, ChevronDown, ChevronUp, X } from "lucide-react"

interface Unit {
  id: string; propertyId: string; unitNumber: string; unitType: string
  floorNumber: number | null; sizeSqm: number | null
  baseRent: number; depositAmount: number
  status: "VACANT" | "OCCUPIED" | "MAINTENANCE"; furnished: boolean
}
interface Property {
  id: string; name: string; propertyType: string
  address: Record<string, string> | null; description: string | null
  totalUnits: number; vacantUnits: number; occupiedUnits: number; createdAt: string
}

const UNIT_STATUS: Record<string, { color: string; bg: string; label: string }> = {
  VACANT:      { color: "#166534", bg: "#DCFCE7", label: "Vacant"      },
  OCCUPIED:    { color: "#1D4ED8", bg: "#EFF6FF", label: "Occupied"    },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB", label: "Maintenance" },
}
const PROPERTY_TYPES = ["RESIDENTIAL","COMMERCIAL","INDUSTRIAL","MIXED_USE"]
const UNIT_TYPES     = ["STUDIO","1BED","2BED","3BED","4BED","PENTHOUSE","COMMERCIAL","RETAIL","WAREHOUSE","PARKING","OTHER"]

export default function PropertiesTab() {
  const qc = useQueryClient()
  const [expanded, setExpanded]       = useState<string | null>(null)
  const [showAddProp, setShowAddProp] = useState(false)
  const [showAddUnit, setShowAddUnit] = useState<string | null>(null)
  const [error, setError]             = useState("")
  const [propForm, setPropForm] = useState({ name: "", propertyType: "RESIDENTIAL", description: "", street: "", suburb: "", city: "", province: "", postalCode: "" })
  const [unitForm, setUnitForm] = useState({ unitNumber: "", unitType: "2BED", floorNumber: "", sizeSqm: "", baseRent: "", depositAmount: "", furnished: false })

  const { data: properties = [], isLoading } = useQuery<Property[]>({
    queryKey: ["properties"],
    queryFn: async () => (await apiClient.get("/api/v1/property/properties?size=50")).data.content,
  })

  // Fetch units from dedicated endpoint — list endpoint returns units:[] always
  const { data: allUnits = [] } = useQuery<Unit[]>({
    queryKey: ["property-units"],
    queryFn: async () => (await apiClient.get("/api/v1/property/units?size=200")).data.content,
  })

  const unitsByProperty = allUnits.reduce((acc, u) => {
    if (!acc[u.propertyId]) acc[u.propertyId] = []
    acc[u.propertyId].push(u)
    return acc
  }, {} as Record<string, Unit[]>)

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ["properties"] })
    qc.invalidateQueries({ queryKey: ["property-units"] })
  }

  const createProperty = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/property/properties", body),
    onSuccess: () => { invalidate(); setShowAddProp(false); setPropForm({ name: "", propertyType: "RESIDENTIAL", description: "", street: "", suburb: "", city: "", province: "", postalCode: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create property"),
  })

  const addUnit = useMutation({
    mutationFn: ({ propertyId, body }: { propertyId: string; body: any }) =>
      apiClient.post(`/api/v1/property/properties/${propertyId}/units`, body),
    onSuccess: () => { invalidate(); setShowAddUnit(null); setUnitForm({ unitNumber: "", unitType: "2BED", floorNumber: "", sizeSqm: "", baseRent: "", depositAmount: "", furnished: false }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to add unit"),
  })

  const fmtR = (n: number) => `R ${n.toLocaleString("en-ZA")}`
  const totalUnits    = allUnits.length
  const vacantUnits   = allUnits.filter(u => u.status === "VACANT").length
  const occupiedUnits = allUnits.filter(u => u.status === "OCCUPIED").length

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 20 }}>
        {[
          { label: "Properties",  value: properties.length, color: "#1B3A6B" },
          { label: "Total Units", value: totalUnits,         color: "#475569" },
          { label: "Occupied",    value: occupiedUnits,      color: "#1D4ED8" },
          { label: "Vacant",      value: vacantUnits,        color: "#166534" },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <button onClick={() => setShowAddProp(true)} style={btnPrimary}><Plus size={15} /> Add Property</button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading properties...</div>
      ) : properties.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Building2 size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No properties yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Add your first property to start managing units and leases.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {properties.map(prop => {
            const isOpen    = expanded === prop.id
            const propUnits = unitsByProperty[prop.id] ?? []
            const occ       = propUnits.filter(u => u.status === "OCCUPIED").length
            const pct       = propUnits.length > 0 ? Math.round((occ / propUnits.length) * 100) : 0
            return (
              <div key={prop.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                <div onClick={() => setExpanded(isOpen ? null : prop.id)}
                  style={{ padding: "16px 20px", background: isOpen ? "#F0FDF4" : "#fff", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                    <div style={{ width: 42, height: 42, borderRadius: 10, background: isOpen ? "#0D9488" : "#F1F5F9", display: "flex", alignItems: "center", justifyContent: "center" }}>
                      <Building2 size={20} color={isOpen ? "#fff" : "#94A3B8"} />
                    </div>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{prop.name}</div>
                      <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 1 }}>
                        {prop.propertyType.replace("_", " ")}
                        {prop.address && ` · ${prop.address.suburb || ""}, ${prop.address.city || ""}`}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
                    <div style={{ textAlign: "right" }}>
                      <div style={{ fontSize: 12, color: "#64748B", marginBottom: 4 }}>{occ}/{propUnits.length} units · {pct}% occupied</div>
                      <div style={{ width: 120, height: 6, background: "#F1F5F9", borderRadius: 99, overflow: "hidden" }}>
                        <div style={{ height: "100%", width: `${pct}%`, background: pct === 100 ? "#0D9488" : "#1D4ED8", borderRadius: 99 }} />
                      </div>
                    </div>
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "16px 20px", background: "#FAFAFA" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
                      <div style={{ fontSize: 12, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.06em" }}>UNITS ({propUnits.length})</div>
                      <button onClick={e => { e.stopPropagation(); setShowAddUnit(prop.id); setError("") }} style={btnTeal}>
                        <Plus size={13} /> Add Unit
                      </button>
                    </div>
                    {propUnits.length === 0 ? (
                      <div style={{ color: "#94A3B8", fontSize: 13, padding: "12px 0" }}>No units yet. Add the first unit to this property.</div>
                    ) : (
                      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 10 }}>
                        {propUnits.map(unit => {
                          const us = UNIT_STATUS[unit.status] || UNIT_STATUS.VACANT
                          return (
                            <div key={unit.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, padding: "14px 16px" }}>
                              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 8 }}>
                                <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                                  <Home size={14} color="#0D9488" />
                                  <span style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>Unit {unit.unitNumber}</span>
                                </div>
                                <span style={{ background: us.bg, color: us.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{us.label}</span>
                              </div>
                              <div style={{ fontSize: 12, color: "#64748B", marginBottom: 6 }}>
                                {unit.unitType}{unit.sizeSqm && ` · ${unit.sizeSqm}m²`}{unit.floorNumber && ` · Floor ${unit.floorNumber}`}
                              </div>
                              <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>{fmtR(unit.baseRent)}/month</div>
                              <div style={{ fontSize: 11, color: "#94A3B8" }}>Deposit: {fmtR(unit.depositAmount)}</div>
                              {unit.furnished && <div style={{ fontSize: 11, color: "#0D9488", marginTop: 3 }}>✓ Furnished</div>}
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {showAddProp && (
        <Modal title="Add Property" onClose={() => { setShowAddProp(false); setError("") }}>
          <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Property Name *"><MInput value={propForm.name} onChange={v => setPropForm(f => ({ ...f, name: v }))} placeholder="Germiston Heights" /></Field>
              </div>
              <Field label="Property Type">
                <select value={propForm.propertyType} onChange={e => setPropForm(f => ({ ...f, propertyType: e.target.value }))} style={sel}>
                  {PROPERTY_TYPES.map(t => <option key={t} value={t}>{t.replace("_", " ")}</option>)}
                </select>
              </Field>
              <Field label="Description"><MInput value={propForm.description} onChange={v => setPropForm(f => ({ ...f, description: v }))} placeholder="12-unit residential block" /></Field>
            </div>
            <div style={{ borderTop: "1px solid #F1F5F9", paddingTop: 14 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.05em", marginBottom: 10 }}>ADDRESS</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <Field label="Street"><MInput value={propForm.street} onChange={v => setPropForm(f => ({ ...f, street: v }))} placeholder="45 Rietfontein Road" /></Field>
                <Field label="Suburb"><MInput value={propForm.suburb} onChange={v => setPropForm(f => ({ ...f, suburb: v }))} placeholder="Germiston" /></Field>
                <Field label="City"><MInput value={propForm.city} onChange={v => setPropForm(f => ({ ...f, city: v }))} placeholder="Ekurhuleni" /></Field>
                <Field label="Province"><MInput value={propForm.province} onChange={v => setPropForm(f => ({ ...f, province: v }))} placeholder="Gauteng" /></Field>
                <Field label="Postal Code"><MInput value={propForm.postalCode} onChange={v => setPropForm(f => ({ ...f, postalCode: v }))} placeholder="1401" /></Field>
              </div>
            </div>
          </div>
          {error && <ErrMsg msg={error} />}
          <ModalFooter onCancel={() => { setShowAddProp(false); setError("") }}
            onSubmit={() => createProperty.mutate({ name: propForm.name, propertyType: propForm.propertyType, description: propForm.description || null, address: { street: propForm.street, suburb: propForm.suburb, city: propForm.city, province: propForm.province, postalCode: propForm.postalCode } })}
            loading={createProperty.isPending} disabled={!propForm.name} label="Create Property" />
        </Modal>
      )}

      {showAddUnit && (
        <Modal title="Add Unit" onClose={() => { setShowAddUnit(null); setError("") }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
            <Field label="Unit Number *"><MInput value={unitForm.unitNumber} onChange={v => setUnitForm(f => ({ ...f, unitNumber: v }))} placeholder="1A" /></Field>
            <Field label="Unit Type">
              <select value={unitForm.unitType} onChange={e => setUnitForm(f => ({ ...f, unitType: e.target.value }))} style={sel}>
                {UNIT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
            <Field label="Floor Number"><MInput value={unitForm.floorNumber} onChange={v => setUnitForm(f => ({ ...f, floorNumber: v }))} placeholder="1" type="number" /></Field>
            <Field label="Size (m²)"><MInput value={unitForm.sizeSqm} onChange={v => setUnitForm(f => ({ ...f, sizeSqm: v }))} placeholder="75.5" type="number" /></Field>
            <Field label="Monthly Rent (R) *"><MInput value={unitForm.baseRent} onChange={v => setUnitForm(f => ({ ...f, baseRent: v }))} placeholder="8500" type="number" /></Field>
            <Field label="Deposit (R) *"><MInput value={unitForm.depositAmount} onChange={v => setUnitForm(f => ({ ...f, depositAmount: v }))} placeholder="17000" type="number" /></Field>
            <div style={{ gridColumn: "1 / -1" }}>
              <label style={{ display: "flex", alignItems: "center", gap: 8, cursor: "pointer" }}>
                <input type="checkbox" checked={unitForm.furnished} onChange={e => setUnitForm(f => ({ ...f, furnished: e.target.checked }))} style={{ width: 16, height: 16 }} />
                <span style={{ fontSize: 14, color: "#374151" }}>Furnished unit</span>
              </label>
            </div>
          </div>
          {error && <ErrMsg msg={error} />}
          <ModalFooter onCancel={() => { setShowAddUnit(null); setError("") }}
            onSubmit={() => addUnit.mutate({ propertyId: showAddUnit, body: { unitNumber: unitForm.unitNumber, unitType: unitForm.unitType, floorNumber: unitForm.floorNumber ? Number(unitForm.floorNumber) : null, sizeSqm: unitForm.sizeSqm ? Number(unitForm.sizeSqm) : null, baseRent: Number(unitForm.baseRent), depositAmount: Number(unitForm.depositAmount), furnished: unitForm.furnished } })}
            loading={addUnit.isPending} disabled={!unitForm.unitNumber || !unitForm.baseRent || !unitForm.depositAmount} label="Add Unit" />
        </Modal>
      )}
    </div>
  )
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 560, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}
function MInput({ value, onChange, placeholder, type = "text" }: { value: string; onChange: (v: string) => void; placeholder?: string; type?: string }) {
  return <input type={type} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} style={{ width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
}
function ErrMsg({ msg }: { msg: string }) {
  return <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>{msg}</div>
}
function ModalFooter({ onCancel, onSubmit, loading, disabled, label }: { onCancel: () => void; onSubmit: () => void; loading: boolean; disabled: boolean; label: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
      <button onClick={onSubmit} disabled={disabled || loading} style={{ padding: "9px 20px", background: disabled || loading ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: disabled || loading ? "not-allowed" : "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnTeal: React.CSSProperties    = { display: "flex", alignItems: "center", gap: 5, background: "#0D9488", color: "#fff", border: "none", borderRadius: 6, padding: "6px 12px", fontSize: 13, cursor: "pointer" }
const sel: React.CSSProperties        = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }
