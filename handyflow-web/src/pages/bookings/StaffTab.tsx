// src/pages/bookings/StaffTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, Users, Mail, Phone, AlertCircle } from "lucide-react"

interface Staff { id: string; name: string; email: string; phone: string; employeeId: string | null; active: boolean }

const EMPTY_FORM = { name: "", email: "", phone: "" }

export default function StaffTab() {
  const qc = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm]             = useState(EMPTY_FORM)
  const [errors, setErrors]         = useState<Record<string, string>>({})
  const [apiError, setApiError]     = useState("")

  const { data: staff = [], isLoading } = useQuery<Staff[]>({
    queryKey: ["booking-staff"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/bookings/staff")
      return (res.data?.data ?? res.data) as Staff[]
    },
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.name.trim()) errs.name = "Name is required"
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errs.email = "Invalid email address"
    if (form.phone && !/^(\+|0)[\d\s\-]{7,}$/.test(form.phone)) errs.phone = "Phone must start with + or 0"
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const createStaff = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/bookings/staff", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["booking-staff"] }); setShowCreate(false); setForm(EMPTY_FORM); setErrors({}); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to add staff member"),
  })

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

  if (isLoading) return <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading staff...</div>

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
          <div style={{ fontSize: 13 }}>Add staff who can be assigned to bookings.</div>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 14 }}>
          {staff.map(s => (
            <div key={s.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px", background: "#fff" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
                <div style={{ width: 44, height: 44, borderRadius: "50%", background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 17, fontWeight: 800, color: "#1D4ED8", flexShrink: 0 }}>
                  {s.name.charAt(0).toUpperCase()}
                </div>
                <div>
                  <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{s.name}</div>
                  {!s.active && <span style={{ fontSize: 10, color: "#94A3B8", background: "#F8FAFC", padding: "1px 6px", borderRadius: 4, fontWeight: 600 }}>INACTIVE</span>}
                </div>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                {s.email && <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 12, color: "#64748B" }}><Mail size={12} color="#94A3B8" />{s.email}</div>}
                {s.phone && <div style={{ display: "flex", alignItems: "center", gap: 7, fontSize: 12, color: "#64748B" }}><Phone size={12} color="#94A3B8" />{s.phone}</div>}
              </div>
            </div>
          ))}
        </div>
      )}

      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Add Staff Member</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Full name *</label>
                <input value={form.name} onChange={e => { setForm(f => ({ ...f, name: e.target.value })); setErrors(f => { const n = { ...f }; delete n.name; return n }) }}
                  placeholder="Jane Doe" style={inpStyle("name")} autoFocus />
                <FieldErr name="name" />
              </div>
              <div>
                <label style={lbl}>Email <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input type="email" value={form.email} onChange={e => { setForm(f => ({ ...f, email: e.target.value })); setErrors(f => { const n = { ...f }; delete n.email; return n }) }}
                  placeholder="jane@company.co.za" style={inpStyle("email")} />
                <FieldErr name="email" />
              </div>
              <div>
                <label style={lbl}>Phone <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                <input value={form.phone} onChange={e => { setForm(f => ({ ...f, phone: e.target.value.replace(/[^\d\s\-+]/g, "") })); setErrors(f => { const n = { ...f }; delete n.phone; return n }) }}
                  placeholder="+27 82 123 4567" style={inpStyle("phone")} />
                <FieldErr name="phone" />
              </div>
            </div>
            {apiError && (
              <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}>
                <AlertCircle size={15} />{apiError}
              </div>
            )}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
              <button onClick={() => { if (validate()) createStaff.mutate(form) }} disabled={createStaff.isPending}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createStaff.isPending ? "Adding..." : "Add staff"}
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