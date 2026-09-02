// src/pages/trainingprovider/TrainProvClientsTab.tsx
//
// Client organizations who send delegates to this provider's courses —
// confirmed via TrainProvController: GET/POST /api/v1/training-provider/clients,
// PUT/{id}, POST/{id}/deactivate, POST/{id}/reactivate, DELETE/{id}
// (ADMIN-only). UpsertClientRequest(tradingName, registrationNumber,
// contactName, contactEmail, contactPhone, address).
//
// NOTE: unlike Warehousing/Collections Agency, TrainProvController does
// NOT expose an unpaginated "/clients/all" endpoint (confirmed by direct
// read of the full controller source — only the paginated /clients list
// exists, backed by TrainProvClientRepository.findAllActive). Every
// picker in this module (session-create's client select, etc.) therefore
// fetches /clients?status=ACTIVE&size=200 instead of a dedicated "all"
// endpoint — fine at the scale this list is expected to reach, but
// flagged here since it's a real difference from the sibling modules.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, ChevronRight, Building2, X, Power, PowerOff, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"
import TrainProvClientDetail from "./TrainProvClientDetail"

export interface ClientResponse {
  id: string; clientCode: string; tradingName: string; registrationNumber: string | null
  contactName: string | null; contactEmail: string | null; contactPhone: string | null; address: string | null
  status: "ACTIVE" | "INACTIVE"; createdAt: string
}
interface ClientPage { content: ClientResponse[]; totalElements: number }

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

const emptyForm = { tradingName: "", registrationNumber: "", contactName: "", contactEmail: "", contactPhone: "", address: "" }

function ClientFormModal({ initial, onClose }: { initial?: ClientResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState(() => initial ? {
    tradingName: initial.tradingName, registrationNumber: initial.registrationNumber ?? "",
    contactName: initial.contactName ?? "", contactEmail: initial.contactEmail ?? "",
    contactPhone: initial.contactPhone ?? "", address: initial.address ?? "",
  } : emptyForm)

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        tradingName: form.tradingName, registrationNumber: form.registrationNumber || null,
        contactName: form.contactName || null, contactEmail: form.contactEmail || null,
        contactPhone: form.contactPhone || null, address: form.address || null,
      }
      return initial
        ? apiClient.put(`/api/v1/training-provider/clients/${initial.id}`, body)
        : apiClient.post("/api/v1/training-provider/clients", body)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["trainprov-clients"] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 480, maxHeight: "85vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>{initial ? "Edit client" : "Add a client"}</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gap: 12 }}>
          <div><label style={labelStyle}>Trading name *</label><input style={inputStyle} value={form.tradingName} onChange={e => setForm({ ...form, tradingName: e.target.value })} /></div>
          <div><label style={labelStyle}>Registration number</label><input style={inputStyle} value={form.registrationNumber} onChange={e => setForm({ ...form, registrationNumber: e.target.value })} /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Contact name</label><input style={inputStyle} value={form.contactName} onChange={e => setForm({ ...form, contactName: e.target.value })} /></div>
            <div><label style={labelStyle}>Contact email</label><input type="email" style={inputStyle} value={form.contactEmail} onChange={e => setForm({ ...form, contactEmail: e.target.value })} /></div>
          </div>
          <div><label style={labelStyle}>Contact phone</label><input style={inputStyle} value={form.contactPhone} onChange={e => setForm({ ...form, contactPhone: e.target.value })} /></div>
          <div><label style={labelStyle}>Address</label><textarea style={{ ...inputStyle, minHeight: 60, resize: "vertical" }} value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} /></div>
        </div>

        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save this client"}</p>}

        <button onClick={() => save.mutate()} disabled={!form.tradingName || save.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: TRAINPROV_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!form.tradingName || save.isPending) ? 0.6 : 1 }}>
          {save.isPending ? "Saving…" : initial ? "Save changes" : "Add client"}
        </button>
      </div>
    </div>
  )
}

export default function TrainProvClientsTab() {
  const qc = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<ClientResponse | null>(null)

  const { data, isLoading } = useQuery<ClientPage>({
    queryKey: ["trainprov-clients"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/clients?size=100")).data,
  })
  const clients = data?.content ?? []

  const deactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/clients/${id}/deactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-clients"] }),
  })
  const reactivate = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/clients/${id}/reactivate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-clients"] }),
  })
  // ADMIN-only server-side (TRAININGPROVIDER_ADMIN) — not hidden client-side, real gate is the backend @PreAuthorize.
  const remove = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`/api/v1/training-provider/clients/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-clients"] }),
  })

  if (selectedId) {
    const client = clients.find(c => c.id === selectedId)
    return <TrainProvClientDetail clientId={selectedId} clientName={client?.tradingName ?? ""} onBack={() => setSelectedId(null)} />
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{clients.length} client{clients.length === 1 ? "" : "s"}</p>
        <button onClick={() => setShowForm(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: TRAINPROV_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Add client
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : clients.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No clients yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {clients.map((c, i) => (
            <div key={c.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <button onClick={() => setSelectedId(c.id)} style={{ display: "flex", alignItems: "center", gap: 10, background: "none", border: "none", cursor: "pointer", textAlign: "left", flex: 1 }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: "#FFFBEB", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <Building2 size={15} color={TRAINPROV_ACCENT} />
                </div>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <p style={{ fontSize: 13.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{c.tradingName}</p>
                    <span style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8" }}>{c.clientCode}</span>
                    {c.status === "INACTIVE" && (
                      <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: "#F1F5F9", color: "#64748B" }}>INACTIVE</span>
                    )}
                  </div>
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>{c.contactEmail ?? "No contact email"}</p>
                </div>
              </button>
              <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
                <button onClick={() => setEditing(c)} title="Edit"
                  style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 10px", fontSize: 11.5, fontWeight: 600, color: "#64748B", cursor: "pointer" }}>
                  Edit
                </button>
                {c.status === "ACTIVE" ? (
                  <button onClick={() => deactivate.mutate(c.id)} title="Deactivate"
                    style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                    <PowerOff size={13} color="#94A3B8" />
                  </button>
                ) : (
                  <button onClick={() => reactivate.mutate(c.id)} title="Reactivate"
                    style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                    <Power size={13} color="#059669" />
                  </button>
                )}
                <button onClick={() => { if (confirm(`Delete ${c.tradingName}? This cannot be undone.`)) remove.mutate(c.id) }} title="Delete"
                  style={{ background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: 6, cursor: "pointer" }}>
                  <Trash2 size={13} color="#DC2626" />
                </button>
                <ChevronRight size={16} color="#CBD5E1" onClick={() => setSelectedId(c.id)} style={{ cursor: "pointer" }} />
              </div>
            </div>
          ))}
        </div>
      )}

      {showForm && <ClientFormModal onClose={() => setShowForm(false)} />}
      {editing && <ClientFormModal initial={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}
