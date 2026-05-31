// src/pages/bookings/ServicesTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Clock, Tag, AlertCircle } from "lucide-react"

interface Service {
  id: string; name: string; description: string; durationMinutes: number
  price: number; currency: string; color: string; active: boolean
}

const COLORS = ["#0D9488", "#1D4ED8", "#7C3AED", "#DC2626", "#D97706", "#166534", "#DB2777", "#0891B2"]
const DURATIONS = [15, 30, 45, 60, 90, 120, 180, 240]

const EMPTY_FORM = { name: "", description: "", durationMinutes: "60", price: "", color: "#0D9488" }

export default function ServicesTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing]       = useState<Service | null>(null)
  const [form, setForm]             = useState(EMPTY_FORM)
  const [errors, setErrors]         = useState<Record<string, string>>({})
  const [apiError, setApiError]     = useState("")

  const { data: services = [], isLoading } = useQuery<Service[]>({
    queryKey: ["booking-services"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings/services")
      return (res.data?.data ?? res.data) as Service[]
    },
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.name.trim())         errs.name            = "Service name is required"
    if (!form.durationMinutes)     errs.durationMinutes  = "Duration is required"
    else if (parseInt(form.durationMinutes) < 5) errs.durationMinutes = "Minimum duration is 5 minutes"
    if (form.price && parseFloat(form.price) < 0) errs.price = "Price cannot be negative"
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const createService = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/bookings/services", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["booking-services"] }); setShowCreate(false); setForm(EMPTY_FORM); setErrors({}); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to create service"),
  })

  const updateService = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/bookings/services/${id}`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["booking-services"] }); setEditing(null); setErrors({}); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update service"),
  })

  const openEdit = (s: Service) => {
    setEditing(s)
    setForm({ name: s.name, description: s.description ?? "", durationMinutes: String(s.durationMinutes), price: String(s.price ?? ""), color: s.color ?? "#0D9488" })
    setErrors({}); setApiError("")
  }

  const handleSubmit = () => {
    if (!validate()) return
    const body = { name: form.name, description: form.description || undefined, durationMinutes: parseInt(form.durationMinutes), price: parseFloat(form.price) || 0, color: form.color }
    if (editing) updateService.mutate({ id: editing.id, body })
    else createService.mutate(body)
  }

  const inpStyle = (key: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", border: `1.5px solid ${errors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: errors[key] ? "#FFF5F5" : "#fff",
  })

  const FieldErr = ({ name }: { name: string }) =>
    errors[name] ? (
      <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
        <AlertCircle size={12} />{errors[name]}
      </div>
    ) : null

  const fmtR = (n: number) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading services...</div>

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setErrors({}); setApiError("") }}
          style={btnPrimary}><Plus size={15} /> New Service</button>
      </div>

      {services.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Tag size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <div style={{ fontWeight: 600, color: "#475569", marginBottom: 4 }}>No services yet</div>
          <div style={{ fontSize: 13 }}>Add your first bookable service to get started.</div>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 14 }}>
          {services.map(s => (
            <div key={s.id} onClick={() => openEdit(s)}
              style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", cursor: "pointer" }}
              onMouseEnter={e => (e.currentTarget.style.boxShadow = "0 4px 16px rgba(0,0,0,0.08)")}
              onMouseLeave={e => (e.currentTarget.style.boxShadow = "none")}>
              <div style={{ height: 5, background: s.color ?? "#0D9488" }} />
              <div style={{ padding: "16px 18px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 10 }}>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{s.name}</div>
                    {s.description && <div style={{ fontSize: 12, color: "#64748B", marginTop: 3 }}>{s.description}</div>}
                  </div>
                  {!s.active && <span style={{ fontSize: 10, color: "#94A3B8", background: "#F8FAFC", padding: "2px 7px", borderRadius: 20, fontWeight: 600 }}>INACTIVE</span>}
                </div>
                <div style={{ display: "flex", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 13, color: "#64748B" }}>
                    <Clock size={13} color={s.color ?? "#0D9488"} />{s.durationMinutes} min
                  </div>
                  <div style={{ fontWeight: 800, fontSize: 16, color: s.color ?? "#0D9488" }}>{fmtR(s.price)}</div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Service modal */}
      {(showCreate || editing) && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 500, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{editing ? "Edit Service" : "New Service"}</h3>
              <button onClick={() => { setShowCreate(false); setEditing(null) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Service name *</label>
                <input value={form.name} onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setErrors(f => { const n = { ...f }; delete n.name; return n }) }}
                  placeholder="e.g. Haircut, Consultation, Oil Change" style={inpStyle("name")} autoFocus />
                <FieldErr name="name" />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Description <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  placeholder="Brief description..." style={inpStyle("description")} />
              </div>
              <div>
                <label style={lbl}>Duration *</label>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                  {DURATIONS.map(d => (
                    <button key={d} onClick={() => { setForm(f => ({ ...f, durationMinutes: String(d) })); setErrors(f => { const n = { ...f }; delete n.durationMinutes; return n }) }}
                      style={{ padding: "6px 12px", borderRadius: 7, fontSize: 12, cursor: "pointer", border: "none", fontWeight: 600,
                        background: form.durationMinutes === String(d) ? "#1B3A6B" : "#F1F5F9",
                        color: form.durationMinutes === String(d) ? "#fff" : "#64748B",
                      }}>
                      {d < 60 ? `${d}m` : `${d / 60}h`}
                    </button>
                  ))}
                </div>
                <FieldErr name="durationMinutes" />
              </div>
              <div>
                <label style={lbl}>Price (R)</label>
                <input type="number" value={form.price} onChange={e => setForm(f => ({ ...f, price: e.target.value }))}
                  placeholder="350.00" style={inpStyle("price")} />
                <FieldErr name="price" />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Colour</label>
                <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                  {COLORS.map(c => (
                    <button key={c} onClick={() => setForm(f => ({ ...f, color: c }))}
                      style={{ width: 32, height: 32, borderRadius: "50%", background: c, border: form.color === c ? "3px solid #0F172A" : "2px solid transparent", cursor: "pointer" }} />
                  ))}
                </div>
              </div>
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={15} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowCreate(false); setEditing(null) }} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={handleSubmit} disabled={createService.isPending || updateService.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {editing ? "Save changes" : "Create service"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const lbl: React.CSSProperties        = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }