// src/pages/collectionsagency/CollAgencyCollectorsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, Pencil, Power, PowerOff, Trash2, AlertTriangle } from "lucide-react"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"

interface CollectorResponse {
  id: string; userId: string | null; fullName: string; registrationNumber: string | null
  registrationExpiryDate: string | null; email: string | null; phone: string | null; active: boolean
}

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function CollectorFormModal({ collector, onClose }: { collector: CollectorResponse | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    fullName: collector?.fullName ?? "", registrationNumber: collector?.registrationNumber ?? "",
    registrationExpiryDate: collector?.registrationExpiryDate ?? "", email: collector?.email ?? "", phone: collector?.phone ?? "",
  })
  const save = useMutation({
    mutationFn: async () => collector
      ? apiClient.put(`/api/v1/collections-agency/collectors/${collector.id}`, form)
      : apiClient.post("/api/v1/collections-agency/collectors", form),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ca-collectors-full"] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 300 }} onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 440 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>{collector ? "Edit collector" : "Register a new collector"}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gap: 14 }}>
          <div><label style={labelStyle}>Full name *</label><input style={inputStyle} value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Debt Collectors Act registration number</label><input style={inputStyle} value={form.registrationNumber} onChange={e => setForm({ ...form, registrationNumber: e.target.value })} /></div>
            <div><label style={labelStyle}>Registration expiry date</label><input type="date" style={inputStyle} value={form.registrationExpiryDate} onChange={e => setForm({ ...form, registrationExpiryDate: e.target.value })} /></div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Email</label><input type="email" style={inputStyle} value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></div>
            <div><label style={labelStyle}>Phone</label><input style={inputStyle} value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></div>
          </div>
        </div>
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 10 }}>{(save.error as any)?.response?.data?.message ?? "Something went wrong"}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 22 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={!form.fullName || save.isPending}
            style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: CA_ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            {save.isPending ? "Saving…" : collector ? "Save changes" : "Register collector"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function CollAgencyCollectorsTab() {
  const qc = useQueryClient()
  const [modal, setModal] = useState<CollectorResponse | null | "new">(null)

  const { data: collectors = [], isLoading } = useQuery<CollectorResponse[]>({
    queryKey: ["ca-collectors-full"],
    queryFn: async () => (await apiClient.get("/api/v1/collections-agency/collectors")).data,
  })
  const toggleActive = useMutation({
    mutationFn: async (c: CollectorResponse) => apiClient.post(`/api/v1/collections-agency/collectors/${c.id}/${c.active ? "deactivate" : "reactivate"}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-collectors-full"] }),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`/api/v1/collections-agency/collectors/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-collectors-full"] }),
  })

  const today = new Date(); const in30 = new Date(today.getTime() + 30 * 86_400_000)

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{collectors.length} registered collector{collectors.length === 1 ? "" : "s"}</p>
        <button onClick={() => setModal("new")} style={{ display: "flex", alignItems: "center", gap: 6, background: CA_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "8px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Register collector
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : collectors.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No collectors registered yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {collectors.map((c, i) => {
            const exp = c.registrationExpiryDate ? new Date(c.registrationExpiryDate) : null
            const expired = exp && exp < today
            const expiringSoon = exp && !expired && exp <= in30
            return (
              <div key={c.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                    <p style={{ fontSize: 14, fontWeight: 700, color: "#0F172A", margin: 0 }}>{c.fullName}</p>
                    <span style={{ fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: c.active ? "#DCFCE7" : "#F1F5F9", color: c.active ? "#166534" : "#64748B" }}>{c.active ? "ACTIVE" : "INACTIVE"}</span>
                    {(expired || expiringSoon) && (
                      <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 10.5, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: expired ? "#FEE2E2" : "#FEF3C7", color: expired ? "#991B1B" : "#92400E" }}>
                        <AlertTriangle size={11} /> {expired ? "REGISTRATION EXPIRED" : "EXPIRES SOON"}
                      </span>
                    )}
                  </div>
                  <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>
                    {c.registrationNumber ? `Reg. ${c.registrationNumber}` : "No registration number on file"}
                    {c.registrationExpiryDate ? ` · Expires ${c.registrationExpiryDate}` : ""}
                    {c.email ? ` · ${c.email}` : ""}
                  </p>
                </div>
                <div style={{ display: "flex", gap: 4 }}>
                  <button title="Edit" onClick={() => setModal(c)} style={{ background: "none", border: "none", cursor: "pointer", padding: 6 }}><Pencil size={15} color="#64748B" /></button>
                  <button title={c.active ? "Deactivate" : "Reactivate"} onClick={() => toggleActive.mutate(c)} style={{ background: "none", border: "none", cursor: "pointer", padding: 6 }}>
                    {c.active ? <PowerOff size={15} color="#D97706" /> : <Power size={15} color="#059669" />}
                  </button>
                  <button title="Delete" onClick={() => { if (confirm(`Delete ${c.fullName}? Requires ADMIN.`)) remove.mutate(c.id) }} style={{ background: "none", border: "none", cursor: "pointer", padding: 6 }}><Trash2 size={15} color="#DC2626" /></button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {modal !== null && <CollectorFormModal collector={modal === "new" ? null : modal} onClose={() => setModal(null)} />}
    </div>
  )
}
