// src/pages/bookings/ServicesTab.tsx
//
// CHANGES vs original:
// - Buffer time fields: bufferBeforeMinutes + bufferAfterMinutes
// - Lead time fields: minLeadTimeMinutes + maxAdvanceDays
// - ServiceResponse now includes these fields — displayed on service cards
// - Tooltips explain WHY each field matters (junior dev / business owner audience)
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Clock, Tag, AlertCircle, Info, Trash2 } from "lucide-react"

interface Service {
  id: string; name: string; description: string; durationMinutes: number
  price: number; currency: string; color: string; active: boolean
  bufferBeforeMinutes: number; bufferAfterMinutes: number
  minLeadTimeMinutes: number; maxAdvanceDays: number
}

const COLORS    = ["#0D9488", "#1D4ED8", "#7C3AED", "#DC2626", "#D97706", "#166534", "#DB2777", "#0891B2"]
const DURATIONS = [15, 30, 45, 60, 90, 120, 180, 240]
const BUFFERS   = [0, 5, 10, 15, 20, 30]
const LEAD_MINS = [0, 30, 60, 120, 240, 480]   // 0 = no restriction, up to 8h
const ADVANCE_DAYS = [30, 60, 90, 180, 365]

const EMPTY_FORM = {
  name: "", description: "", durationMinutes: "60", price: "",
  color: "#0D9488",
  bufferBeforeMinutes: "0", bufferAfterMinutes: "0",
  minLeadTimeMinutes: "0", maxAdvanceDays: "90",
}

function Tip({ text }: { text: string }) {
  return (
    <span title={text} style={{ cursor: "help", color: "#94A3B8", display: "inline-flex", alignItems: "center", marginLeft: 4 }}>
      <Info size={11} />
    </span>
  )
}

export default function ServicesTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate]   = useState(false)
  const [editing, setEditing]         = useState<Service | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Service | null>(null)
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
    if (!form.name.trim())         errs.name           = "Service name is required"
    if (!form.durationMinutes)     errs.durationMinutes = "Duration is required"
    else if (parseInt(form.durationMinutes) < 5) errs.durationMinutes = "Minimum is 5 minutes"
    if (form.price && parseFloat(form.price) < 0) errs.price = "Price cannot be negative"
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const body = () => ({
    name:                 form.name,
    description:          form.description || undefined,
    durationMinutes:      parseInt(form.durationMinutes),
    price:                parseFloat(form.price) || 0,
    color:                form.color,
    bufferBeforeMinutes:  parseInt(form.bufferBeforeMinutes) || 0,
    bufferAfterMinutes:   parseInt(form.bufferAfterMinutes)  || 0,
    minLeadTimeMinutes:   parseInt(form.minLeadTimeMinutes)  || 0,
    maxAdvanceDays:       parseInt(form.maxAdvanceDays)      || 90,
  })

  const createService = useMutation({
    mutationFn: (b: any) => apiClient.post("/api/v1/bookings/services", b),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["booking-services"] }); close_(); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to create service"),
  })

  const updateService = useMutation({
    mutationFn: ({ id, b }: { id: string; b: any }) => apiClient.put(`/api/v1/bookings/services/${id}`, b),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["booking-services"] }); close_(); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update service"),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/bookings/services/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["booking-services"] }),
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to delete service"),
  })

  const close_ = () => { setShowCreate(false); setEditing(null); setErrors({}) }

  const openEdit = (s: Service) => {
    setEditing(s)
    setForm({
      name: s.name, description: s.description ?? "", durationMinutes: String(s.durationMinutes),
      price: String(s.price ?? ""), color: s.color ?? "#0D9488",
      bufferBeforeMinutes: String(s.bufferBeforeMinutes ?? 0),
      bufferAfterMinutes:  String(s.bufferAfterMinutes ?? 0),
      minLeadTimeMinutes:  String(s.minLeadTimeMinutes ?? 0),
      maxAdvanceDays:      String(s.maxAdvanceDays ?? 90),
    })
    setErrors({}); setApiError("")
  }

  const handleSubmit = () => {
    if (!validate()) return
    if (editing) updateService.mutate({ id: editing.id, b: body() })
    else createService.mutate(body())
  }

  const inpStyle = (key: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px",
    border: `1.5px solid ${errors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, boxSizing: "border-box",
    background: errors[key] ? "#FFF5F5" : "#fff",
  })

  const FieldErr = ({ name }: { name: string }) =>
    errors[name] ? (
      <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}>
        <AlertCircle size={12} />{errors[name]}
      </div>
    ) : null

  const fmtMins = (m: number) => m === 0 ? "None" : m >= 60 ? `${m / 60}h` : `${m}m`
  const fmtR    = (n: number) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading services…</div>

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
                  <div style={{ display: "flex", gap: 4, alignItems: "center" }}>
                  {!s.active && <span style={{ fontSize: 10, color: "#94A3B8", background: "#F8FAFC", padding: "2px 7px", borderRadius: 20, fontWeight: 600 }}>INACTIVE</span>}
                  <button
                    onClick={e => { e.stopPropagation(); setConfirmDelete(s) }}
                    title="Delete service"
                    style={{ background: "none", border: "none", cursor: "pointer", color: "#DC2626", padding: 4, display: "flex", marginLeft: "auto" }}>
                    <Trash2 size={13} />
                  </button>
                </div>
                </div>
                <div style={{ display: "flex", gap: 14, marginBottom: 10 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 13, color: "#64748B" }}>
                    <Clock size={13} color={s.color ?? "#0D9488"} />{s.durationMinutes} min
                  </div>
                  <div style={{ fontWeight: 800, fontSize: 16, color: s.color ?? "#0D9488" }}>{fmtR(s.price)}</div>
                </div>
                {/* Buffer/lead time chips — only shown when non-zero */}
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  {s.bufferAfterMinutes > 0 && (
                    <span style={{ fontSize: 10, color: "#7C3AED", background: "#F5F3FF", border: "1px solid #E9D5FF", padding: "1px 7px", borderRadius: 10 }}>
                      +{s.bufferAfterMinutes}m buffer
                    </span>
                  )}
                  {s.minLeadTimeMinutes > 0 && (
                    <span style={{ fontSize: 10, color: "#D97706", background: "#FFFBEB", border: "1px solid #FDE68A", padding: "1px 7px", borderRadius: 10 }}>
                      {fmtMins(s.minLeadTimeMinutes)} lead
                    </span>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ── Service modal ─────────────────────────────────────────────────── */}
      {(showCreate || editing) && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 560, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{editing ? "Edit Service" : "New Service"}</h3>
              <button onClick={close_} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              {/* Basic info */}
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Service name *</label>
                <input value={form.name} onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setErrors(f => { const n = { ...f }; delete n.name; return n }) }}
                  placeholder="e.g. Haircut, Consultation, Oil Change" style={inpStyle("name")} autoFocus />
                <FieldErr name="name" />
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <label style={lbl}>Description <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
                  placeholder="Brief description…" style={inpStyle("description")} />
              </div>

              {/* Duration */}
              <div style={{ gridColumn: "1 / -1" }}>
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

              {/* Price */}
              <div>
                <label style={lbl}>Price (R)</label>
                <input type="number" value={form.price} onChange={e => setForm(f => ({ ...f, price: e.target.value }))}
                  placeholder="350.00" style={inpStyle("price")} />
                <FieldErr name="price" />
              </div>

              {/* Colour */}
              <div>
                <label style={lbl}>Colour</label>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {COLORS.map(c => (
                    <button key={c} onClick={() => setForm(f => ({ ...f, color: c }))}
                      style={{ width: 30, height: 30, borderRadius: "50%", background: c, border: form.color === c ? "3px solid #0F172A" : "2px solid transparent", cursor: "pointer" }} />
                  ))}
                </div>
              </div>

              {/* ── Buffer time ──────────────────────────────────────────── */}
              {/* WHY buffer time?
                  Without buffer time, the slot engine packs appointments back-to-back.
                  A 60-minute haircut that ends at 10:00 means the next client is in the
                  chair at exactly 10:00 — no time to sweep hair, clean, or prepare.
                  Buffer time extends the slot's effective end for conflict detection:
                  a 60-min service with 15m after-buffer blocks 10:00–10:15 for new bookings. */}
              <div>
                <label style={lbl}>
                  Buffer before
                  <Tip text="Prep time before this service. E.g. 10 min to set up equipment before the client arrives." />
                </label>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                  {BUFFERS.map(b => (
                    <button key={b} onClick={() => setForm(f => ({ ...f, bufferBeforeMinutes: String(b) }))}
                      style={{ padding: "5px 10px", borderRadius: 7, fontSize: 12, cursor: "pointer", border: "none", fontWeight: 600,
                        background: form.bufferBeforeMinutes === String(b) ? "#7C3AED" : "#F1F5F9",
                        color: form.bufferBeforeMinutes === String(b) ? "#fff" : "#64748B",
                      }}>
                      {b === 0 ? "None" : `${b}m`}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label style={lbl}>
                  Buffer after
                  <Tip text="Cleanup time after this service. E.g. 15 min to clean up before the next client. This blocks the slot for new bookings." />
                </label>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                  {BUFFERS.map(b => (
                    <button key={b} onClick={() => setForm(f => ({ ...f, bufferAfterMinutes: String(b) }))}
                      style={{ padding: "5px 10px", borderRadius: 7, fontSize: 12, cursor: "pointer", border: "none", fontWeight: 600,
                        background: form.bufferAfterMinutes === String(b) ? "#7C3AED" : "#F1F5F9",
                        color: form.bufferAfterMinutes === String(b) ? "#fff" : "#64748B",
                      }}>
                      {b === 0 ? "None" : `${b}m`}
                    </button>
                  ))}
                </div>
              </div>

              {/* ── Lead time ─────────────────────────────────────────────── */}
              {/* WHY lead time?
                  Lead time prevents clients booking a slot that starts in 5 minutes —
                  the staff member won't be ready in time and it causes chaos.
                  E.g. "minimum 2 hours notice" means the slot is hidden on the booking
                  widget if it's less than 2h away from now. */}
              <div>
                <label style={lbl}>
                  Min. lead time
                  <Tip text="How far in advance a client must book. 0 = no restriction. 60 = must book at least 1 hour before." />
                </label>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                  {LEAD_MINS.map(m => (
                    <button key={m} onClick={() => setForm(f => ({ ...f, minLeadTimeMinutes: String(m) }))}
                      style={{ padding: "5px 10px", borderRadius: 7, fontSize: 12, cursor: "pointer", border: "none", fontWeight: 600,
                        background: form.minLeadTimeMinutes === String(m) ? "#D97706" : "#F1F5F9",
                        color: form.minLeadTimeMinutes === String(m) ? "#fff" : "#64748B",
                      }}>
                      {m === 0 ? "None" : m >= 60 ? `${m / 60}h` : `${m}m`}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label style={lbl}>
                  Max advance days
                  <Tip text="How far in the future a client can book. 90 = can book up to 3 months ahead." />
                </label>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                  {ADVANCE_DAYS.map(d => (
                    <button key={d} onClick={() => setForm(f => ({ ...f, maxAdvanceDays: String(d) }))}
                      style={{ padding: "5px 10px", borderRadius: 7, fontSize: 12, cursor: "pointer", border: "none", fontWeight: 600,
                        background: form.maxAdvanceDays === String(d) ? "#D97706" : "#F1F5F9",
                        color: form.maxAdvanceDays === String(d) ? "#fff" : "#64748B",
                      }}>
                      {d >= 365 ? "1 year" : `${d}d`}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", gap: 8 }}>
                <AlertCircle size={15} />{apiError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={close_} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={handleSubmit} disabled={createService.isPending || updateService.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {editing ? "Save changes" : "Create service"}
              </button>
            </div>
          </div>
        </div>
      )}
      {/* Styled delete confirmation modal */}
      {confirmDelete && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 400, boxShadow: "0 20px 60px rgba(0,0,0,0.2)", textAlign: "center" }}>
            <div style={{ width: 52, height: 52, borderRadius: "50%", background: "#FEF2F2", border: "2px solid #FECACA", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
              <Trash2 size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: "0 0 8px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Delete Service?</h3>
            <p style={{ fontSize: 13, color: "#64748B", margin: "0 0 6px" }}>
              <strong>{confirmDelete.name}</strong>
            </p>
            <p style={{ fontSize: 12, color: "#94A3B8", margin: "0 0 20px", lineHeight: 1.5 }}>
              The service will be hidden from new bookings. Past bookings are preserved.
              This action cannot be undone.
            </p>
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => setConfirmDelete(null)}
                style={{ flex: 1, padding: "10px", border: "1.5px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "#374151" }}>
                Keep service
              </button>
              <button
                onClick={() => { deleteMutation.mutate(confirmDelete.id); setConfirmDelete(null) }}
                disabled={deleteMutation.isPending}
                style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, background: "#DC2626", color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                Yes, delete
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
