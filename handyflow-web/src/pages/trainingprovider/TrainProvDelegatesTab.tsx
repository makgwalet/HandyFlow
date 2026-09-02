// src/pages/trainingprovider/TrainProvDelegatesTab.tsx
//
// Delegates nominated by one client — confirmed via
// TrainProvDelegateController: GET /delegates?clientId=, POST
// /clients/{clientId}/delegates, PUT /delegates/{id},
// POST /delegates/{id}/deactivate|reactivate, DELETE /delegates/{id}
// (ADMIN-only). UpsertDelegateRequest(fullName, idNumber, email, phone,
// jobTitle). TrainProvDelegate is deliberately its own entity, not
// linked to hr.HrEmployee — the delegate works for the CLIENT's
// business, not this tenant's own staff.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, User, Power, PowerOff, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"

interface DelegateResponse {
  id: string; clientId: string; delegateNumber: string; fullName: string; idNumber: string | null
  email: string | null; phone: string | null; jobTitle: string | null; status: "ACTIVE" | "INACTIVE"; createdAt: string
}
interface DelegatePage { content: DelegateResponse[] }

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function DelegateFormModal({ clientId, initial, onClose }: { clientId: string; initial?: DelegateResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState(() => initial ? {
    fullName: initial.fullName, idNumber: initial.idNumber ?? "", email: initial.email ?? "",
    phone: initial.phone ?? "", jobTitle: initial.jobTitle ?? "",
  } : { fullName: "", idNumber: "", email: "", phone: "", jobTitle: "" })

  const save = useMutation({
    mutationFn: async () => {
      const body = { fullName: form.fullName, idNumber: form.idNumber || null, email: form.email || null, phone: form.phone || null, jobTitle: form.jobTitle || null }
      return initial
        ? apiClient.put(`/api/v1/training-provider/delegates/${initial.id}`, body)
        : apiClient.post(`/api/v1/training-provider/clients/${clientId}/delegates`, body)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["trainprov-delegates", clientId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 440 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>{initial ? "Edit delegate" : "Add a delegate"}</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gap: 12 }}>
          <div><label style={labelStyle}>Full name *</label><input style={inputStyle} value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></div>
          <div><label style={labelStyle}>ID number</label><input style={inputStyle} value={form.idNumber} onChange={e => setForm({ ...form, idNumber: e.target.value })} /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Email</label><input type="email" style={inputStyle} value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></div>
            <div><label style={labelStyle}>Phone</label><input style={inputStyle} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></div>
          </div>
          <div><label style={labelStyle}>Job title</label><input style={inputStyle} value={form.jobTitle} onChange={e => setForm({ ...form, jobTitle: e.target.value })} /></div>
        </div>
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save this delegate"}</p>}
        <button onClick={() => save.mutate()} disabled={!form.fullName || save.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: TRAINPROV_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!form.fullName || save.isPending) ? 0.6 : 1 }}>
          {save.isPending ? "Saving…" : initial ? "Save changes" : "Add delegate"}
        </button>
      </div>
    </div>
  )
}

export default function TrainProvDelegatesTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<DelegateResponse | null>(null)

  const { data, isLoading } = useQuery<DelegatePage>({
    queryKey: ["trainprov-delegates", clientId],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/delegates", { params: { clientId, size: 100 } })).data,
  })
  const delegates = data?.content ?? []

  const deactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/delegates/${id}/deactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-delegates", clientId] }),
  })
  const reactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/delegates/${id}/reactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-delegates", clientId] }),
  })
  // ADMIN-only server-side (TRAININGPROVIDER_ADMIN).
  const remove = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`/api/v1/training-provider/delegates/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-delegates", clientId] }),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{delegates.length} delegate{delegates.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowForm(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: TRAINPROV_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add delegate
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : delegates.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No delegates nominated yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {delegates.map((d, i) => (
            <div key={d.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 30, height: 30, borderRadius: 8, background: "#FFFBEB", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <User size={14} color={TRAINPROV_ACCENT} />
                </div>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{d.fullName}</p>
                    <span style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8" }}>{d.delegateNumber}</span>
                    {d.status === "INACTIVE" && <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: "#F1F5F9", color: "#64748B" }}>INACTIVE</span>}
                  </div>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{[d.jobTitle, d.email].filter(Boolean).join(" · ") || "No further details"}</p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                <button onClick={() => setEditing(d)} title="Edit"
                  style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 10px", fontSize: 11.5, fontWeight: 600, color: "#64748B", cursor: "pointer" }}>
                  Edit
                </button>
                {d.status === "ACTIVE" ? (
                  <button onClick={() => deactivate.mutate(d.id)} title="Deactivate" style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                    <PowerOff size={13} color="#94A3B8" />
                  </button>
                ) : (
                  <button onClick={() => reactivate.mutate(d.id)} title="Reactivate" style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                    <Power size={13} color="#059669" />
                  </button>
                )}
                <button onClick={() => { if (confirm(`Delete ${d.fullName}? This cannot be undone.`)) remove.mutate(d.id) }} title="Delete"
                  style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                  <Trash2 size={13} color="#DC2626" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {showForm && <DelegateFormModal clientId={clientId} onClose={() => setShowForm(false)} />}
      {editing && <DelegateFormModal clientId={clientId} initial={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
