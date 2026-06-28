// src/pages/bookings/StaffTab.tsx
// CHANGES: edit modal, deactivate button, skill assignment panel
import { useState, useEffect } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Users, Mail, Phone, AlertCircle, Pencil, Trash2, Check } from "lucide-react"

interface Staff { id: string; name: string; email: string; phone: string; employeeId: string | null; active: boolean }
interface Service { id: string; name: string; color: string }

const EMPTY_FORM = { name: "", email: "", phone: "" }

export default function StaffTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing]       = useState<Staff | null>(null)
  const [skillsFor, setSkillsFor]           = useState<Staff | null>(null)
  const [confirmDeactivate, setConfirmDeactivate] = useState<Staff | null>(null)
  const [form, setForm]             = useState(EMPTY_FORM)
  const [errors, setErrors]         = useState<Record<string, string>>({})
  const [apiError, setApiError]     = useState("")
  const [selectedSkills, setSelectedSkills] = useState<string[]>([])

  const { data: staff = [], isLoading, isError, error } = useQuery<Staff[]>({
    queryKey: ["booking-staff"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings/staff")
      // Handle both { data: [...] } and plain array responses
      const payload = res.data?.data ?? res.data
      return Array.isArray(payload) ? payload : []
    },
    staleTime: 30_000,
  })

  const { data: services = [] } = useQuery<Service[]>({
    queryKey: ["booking-services"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings/services")
      const payload = res.data?.data ?? res.data
      return Array.isArray(payload) ? payload : []
    },
    staleTime: 30_000,
  })

  const { data: staffSkills = [] } = useQuery<string[]>({
    queryKey: ["staff-skills", skillsFor?.id],
    queryFn: async () => {
      if (!skillsFor) return []
      const all = await Promise.all(
        services.map(async (svc) => {
          const res = await apiClient.get(`/api/v1/bookings/services/${svc.id}/staff`)
          const ids: string[] = res.data?.data ?? []
          return ids.includes(skillsFor.id) ? svc.id : null
        })
      )
      return all.filter(Boolean) as string[]
    },
    enabled: !!skillsFor && services.length > 0,
  })

  // Sync staffSkills into selectedSkills when the modal opens
  useEffect(() => {
    if (staffSkills.length > 0) setSelectedSkills(staffSkills)
  }, [staffSkills])

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.name.trim()) errs.name = "Name is required"
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errs.email = "Invalid email"
    if (form.phone && !/^(\+|0)[\d\s\-]{7,}$/.test(form.phone)) errs.phone = "Phone must start with + or 0"
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const invalidate = () => qc.invalidateQueries({ queryKey: ["booking-staff"] })
  const close = () => { setShowCreate(false); setEditing(null); setForm(EMPTY_FORM); setErrors({}); setApiError("") }

  const createMutation = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/bookings/staff", body),
    onSuccess: () => { invalidate(); close() },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to add staff"),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/bookings/staff/${id}`, body),
    onSuccess: () => { invalidate(); close() },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update staff"),
  })

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/bookings/staff/${id}`),
    onSuccess: () => invalidate(),
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to deactivate"),
  })

  const saveSkillsMutation = useMutation({
    mutationFn: async ({ staffId, serviceIds }: { staffId: string; serviceIds: string[] }) => {
      // For each service, set its staff list (add or remove this staff member)
      await Promise.all(services.map(async (svc) => {
        const currentRes = await apiClient.get(`/api/v1/bookings/services/${svc.id}/staff`)
        const current: string[] = currentRes.data?.data ?? []
        const shouldInclude = serviceIds.includes(svc.id)
        const alreadyIn = current.includes(staffId)
        if (shouldInclude && !alreadyIn) {
          await apiClient.put(`/api/v1/bookings/services/${svc.id}/staff`, [...current, staffId])
        } else if (!shouldInclude && alreadyIn) {
          await apiClient.put(`/api/v1/bookings/services/${svc.id}/staff`, current.filter((id: string) => id !== staffId))
        }
      }))
    },
    onSuccess: () => { setSkillsFor(null); qc.invalidateQueries({ queryKey: ["staff-skills"] }) },
  })

  const openEdit = (s: Staff) => {
    setEditing(s)
    setForm({ name: s.name, email: s.email ?? "", phone: s.phone ?? "" })
    setErrors({}); setApiError("")
  }

  const handleSubmit = () => {
    if (!validate()) return
    if (editing) updateMutation.mutate({ id: editing.id, body: form })
    else createMutation.mutate(form)
  }

  const inpStyle = (key: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px",
    border: `1.5px solid ${errors[key] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, boxSizing: "border-box",
    background: errors[key] ? "#FFF5F5" : "#fff",
  })

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading staff…</div>
  if (isError)   return <div style={{ textAlign: "center", padding: 40, color: "#DC2626" }}>Failed to load staff: {String(error)}</div>

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <button onClick={() => { setShowCreate(true); setForm(EMPTY_FORM); setErrors({}); setApiError("") }}
          style={btnPrimary}><Plus size={15} /> Add Staff</button>
      </div>

      {staff.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Users size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <div style={{ fontWeight: 600, color: "#475569", marginBottom: 4 }}>No staff members yet</div>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 14 }}>
          {staff.map(s => (
            <div key={s.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px", background: "#fff", opacity: s.active ? 1 : 0.6 }}>
              <div style={{ display: "flex", alignItems: "flex-start", gap: 12, marginBottom: 12 }}>
                <div style={{ width: 44, height: 44, borderRadius: "50%", background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 17, fontWeight: 800, color: "#1D4ED8", flexShrink: 0 }}>
                  {s.name.charAt(0).toUpperCase()}
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{s.name}</div>
                  {!s.active && <span style={{ fontSize: 10, color: "#94A3B8", background: "#F8FAFC", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>INACTIVE</span>}
                </div>
                {s.active && (
                  <div style={{ display: "flex", gap: 4 }}>
                    <button onClick={() => openEdit(s)}
                      title="Edit"
                      style={{ background: "none", border: "none", cursor: "pointer", color: "#64748B", padding: 4, display: "flex" }}>
                      <Pencil size={14} />
                    </button>
                    <button onClick={() => { setSkillsFor(s); setSelectedSkills([]) }}
                      title="Manage skills"
                      style={{ background: "#EFF6FF", border: "none", cursor: "pointer", color: "#1D4ED8", padding: "3px 8px", borderRadius: 6, fontSize: 11, fontWeight: 600 }}>
                      Skills
                    </button>
                    <button onClick={() => setConfirmDeactivate(s)}
                      title="Deactivate"
                      style={{ background: "none", border: "none", cursor: "pointer", color: "#DC2626", padding: 4, display: "flex" }}>
                      <Trash2 size={14} />
                    </button>
                  </div>
                )}
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                {s.email && <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 12, color: "#64748B" }}><Mail size={12} color="#94A3B8" />{s.email}</div>}
                {s.phone && <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 12, color: "#64748B" }}><Phone size={12} color="#94A3B8" />{s.phone}</div>}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create / Edit modal */}
      {(showCreate || editing) && (
        <div style={overlay}>
          <div style={{ ...modal, width: 420 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{editing ? "Edit Staff Member" : "Add Staff Member"}</h3>
              <button onClick={close} style={iconBtn}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Full name *</label>
                <input value={form.name} onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setErrors(f => { const n = { ...f }; delete n.name; return n }) }}
                  placeholder="Jane Doe" style={inpStyle("name")} autoFocus />
                {errors.name && <div style={errStyle}><AlertCircle size={12} />{errors.name}</div>}
              </div>
              <div>
                <label style={lbl}>Email <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input type="email" value={form.email} onChange={e => { setForm(f => ({ ...f, email: e.target.value })); setErrors(f => { const n = { ...f }; delete n.email; return n }) }}
                  placeholder="jane@company.co.za" style={inpStyle("email")} />
                {errors.email && <div style={errStyle}><AlertCircle size={12} />{errors.email}</div>}
              </div>
              <div>
                <label style={lbl}>Phone <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input value={form.phone} onChange={e => { setForm(f => ({ ...f, phone: e.target.value.replace(/[^\d\s\-+]/g, "") })); setErrors(f => { const n = { ...f }; delete n.phone; return n }) }}
                  placeholder="+27 82 123 4567" style={inpStyle("phone")} />
                {errors.phone && <div style={errStyle}><AlertCircle size={12} />{errors.phone}</div>}
              </div>
            </div>
            {apiError && <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", gap: 8 }}><AlertCircle size={15} />{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={close} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={handleSubmit} disabled={createMutation.isPending || updateMutation.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {editing ? "Save changes" : "Add staff"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Skills assignment modal */}
      {skillsFor && (
        <div style={overlay}>
          <div style={{ ...modal, width: 460 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 16 }}>
              <div>
                <h3 style={{ margin: "0 0 4px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Skills — {skillsFor.name}</h3>
                <p style={{ margin: 0, fontSize: 13, color: "#64748B" }}>Select which services this staff member can perform. If none selected, they can perform all services.</p>
              </div>
              <button onClick={() => setSkillsFor(null)} style={iconBtn}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 20 }}>
              {services.map(svc => {
                const assigned = selectedSkills.includes(svc.id)
                return (
                  <button key={svc.id}
                    onClick={() => setSelectedSkills(prev => assigned ? prev.filter(id => id !== svc.id) : [...prev, svc.id])}
                    style={{ display: "flex", alignItems: "center", gap: 10, padding: "10px 14px", borderRadius: 9, cursor: "pointer",
                      border: assigned ? "2px solid #0D9488" : "1.5px solid #E2E8F0",
                      background: assigned ? "#F0FDF4" : "#FAFAFA", textAlign: "left" }}>
                    <div style={{ width: 16, height: 16, borderRadius: 4, border: `2px solid ${assigned ? "#0D9488" : "#CBD5E1"}`, background: assigned ? "#0D9488" : "white", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      {assigned && <Check size={10} color="white" />}
                    </div>
                    <div style={{ width: 10, height: 10, borderRadius: "50%", background: svc.color, flexShrink: 0 }} />
                    <span style={{ fontSize: 14, fontWeight: 500, color: "#0F172A" }}>{svc.name}</span>
                  </button>
                )
              })}
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => setSkillsFor(null)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button
                onClick={() => saveSkillsMutation.mutate({ staffId: skillsFor.id, serviceIds: selectedSkills })}
                disabled={saveSkillsMutation.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {saveSkillsMutation.isPending ? "Saving…" : "Save skills"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Styled deactivate confirmation modal */}
      {confirmDeactivate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1100, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 400, boxShadow: "0 20px 60px rgba(0,0,0,0.2)", textAlign: "center" }}>
            <div style={{ width: 52, height: 52, borderRadius: "50%", background: "#FEF2F2", border: "2px solid #FECACA", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px" }}>
              <Trash2 size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: "0 0 8px", fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Deactivate Staff Member?</h3>
            <p style={{ fontSize: 13, color: "#64748B", margin: "0 0 6px" }}>
              <strong>{confirmDeactivate.name}</strong>
            </p>
            <p style={{ fontSize: 12, color: "#94A3B8", margin: "0 0 20px", lineHeight: 1.5 }}>
              They will no longer appear in the staff picker for new bookings.
              Existing bookings are not affected.
            </p>
            <div style={{ display: "flex", gap: 10 }}>
              <button onClick={() => setConfirmDeactivate(null)}
                style={{ flex: 1, padding: "10px", border: "1.5px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, fontWeight: 600, cursor: "pointer", color: "#374151" }}>
                Keep active
              </button>
              <button
                onClick={() => { deactivateMutation.mutate(confirmDeactivate.id); setConfirmDeactivate(null) }}
                disabled={deactivateMutation.isPending}
                style={{ flex: 1, padding: "10px", border: "none", borderRadius: 9, background: "#DC2626", color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                Yes, deactivate
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
const errStyle: React.CSSProperties   = { display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }
const overlay: React.CSSProperties    = { position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }
const modal: React.CSSProperties      = { background: "#fff", borderRadius: 16, padding: 28, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }
const iconBtn: React.CSSProperties    = { background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }
