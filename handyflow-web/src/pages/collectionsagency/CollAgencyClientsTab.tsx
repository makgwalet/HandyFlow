// src/pages/collectionsagency/CollAgencyClientsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, Pencil, Power, PowerOff, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { CA_ACCENT } from "./constants"
import CollAgencyClientDetail from "./CollAgencyClientDetail"

export interface ClientResponse {
  id: string; tradingName: string; registrationNumber: string | null; commissionRatePct: number
  contactName: string | null; contactEmail: string | null; contactPhone: string | null; address: string | null
  trustBalance: number; onboardedAt: string; status: string; notes: string | null
}

const fmtMoney = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n ?? 0)

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

function ClientFormModal({ client, onClose }: { client: ClientResponse | null; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState({
    tradingName: client?.tradingName ?? "",
    registrationNumber: client?.registrationNumber ?? "",
    commissionRatePct: client?.commissionRatePct ?? 15,
    contactName: client?.contactName ?? "",
    contactEmail: client?.contactEmail ?? "",
    contactPhone: client?.contactPhone ?? "",
    address: client?.address ?? "",
    notes: client?.notes ?? "",
  })
  const save = useMutation({
    mutationFn: async () => {
      if (client) {
        return apiClient.put(`/api/v1/collections-agency/clients/${client.id}`, form)
      }
      const { notes, ...createBody } = form
      return apiClient.post("/api/v1/collections-agency/clients", createBody)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ca-clients"] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.45)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 300 }} onClick={onClose}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 480, maxHeight: "85vh", overflowY: "auto" }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: "#0F172A", margin: 0 }}>{client ? "Edit client" : "Onboard a new creditor client"}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>

        <div style={{ display: "grid", gap: 14 }}>
          <div>
            <label style={labelStyle}>Trading name *</label>
            <input style={inputStyle} value={form.tradingName} onChange={e => setForm({ ...form, tradingName: e.target.value })} />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div>
              <label style={labelStyle}>Registration number</label>
              <input style={inputStyle} value={form.registrationNumber} onChange={e => setForm({ ...form, registrationNumber: e.target.value })} />
            </div>
            <div>
              <label style={labelStyle}>Commission rate (%) *</label>
              <input type="number" step="0.5" min="0" max="100" style={inputStyle} value={form.commissionRatePct}
                onChange={e => setForm({ ...form, commissionRatePct: parseFloat(e.target.value) || 0 })} />
            </div>
          </div>
          <div>
            <label style={labelStyle}>Contact name</label>
            <input style={inputStyle} value={form.contactName} onChange={e => setForm({ ...form, contactName: e.target.value })} />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div>
              <label style={labelStyle}>Contact email</label>
              <input type="email" style={inputStyle} value={form.contactEmail} onChange={e => setForm({ ...form, contactEmail: e.target.value })} />
            </div>
            <div>
              <label style={labelStyle}>Contact phone</label>
              <input style={inputStyle} value={form.contactPhone} onChange={e => setForm({ ...form, contactPhone: e.target.value })} />
            </div>
          </div>
          <div>
            <label style={labelStyle}>Address</label>
            <textarea style={{ ...inputStyle, minHeight: 60, resize: "vertical" }} value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} />
          </div>
          {client && (
            <div>
              <label style={labelStyle}>Notes</label>
              <textarea style={{ ...inputStyle, minHeight: 50, resize: "vertical" }} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} />
            </div>
          )}
        </div>

        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 10 }}>{(save.error as any)?.response?.data?.message ?? "Something went wrong"}</p>}

        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 22 }}>
          <button onClick={onClose} style={{ padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={!form.tradingName || save.isPending}
            style={{ padding: "9px 18px", borderRadius: 8, border: "none", background: CA_ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer", opacity: save.isPending ? 0.6 : 1 }}>
            {save.isPending ? "Saving…" : client ? "Save changes" : "Onboard client"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function CollAgencyClientsTab() {
  const qc = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [modalClient, setModalClient] = useState<ClientResponse | null | "new">(null)

  const { data, isLoading } = useQuery<{ content: ClientResponse[] }>({
    queryKey: ["ca-clients"],
    queryFn: async () => (await apiClient.get("/api/v1/collections-agency/clients?size=100")).data,
  })
  const clients = data?.content ?? []

  const toggleActive = useMutation({
    mutationFn: async (c: ClientResponse) =>
      apiClient.post(`/api/v1/collections-agency/clients/${c.id}/${c.status === "ACTIVE" ? "deactivate" : "reactivate"}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-clients"] }),
  })
  const remove = useMutation({
    mutationFn: async (id: string) => apiClient.delete(`/api/v1/collections-agency/clients/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ca-clients"] }),
  })

  if (selectedId) {
    const client = clients.find(c => c.id === selectedId)
    return <CollAgencyClientDetail clientId={selectedId} client={client} onBack={() => setSelectedId(null)} />
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#64748B", margin: 0 }}>{clients.length} creditor client{clients.length === 1 ? "" : "s"}</p>
        <button onClick={() => setModalClient("new")}
          style={{ display: "flex", alignItems: "center", gap: 6, background: CA_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "8px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Onboard client
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : clients.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No creditor clients yet. Onboard one to start placing debtor accounts.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {clients.map((c, i) => (
            <div key={c.id}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div style={{ cursor: "pointer", flex: 1 }} onClick={() => setSelectedId(c.id)}>
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                  <p style={{ fontSize: 14, fontWeight: 700, color: "#0F172A", margin: 0 }}>{c.tradingName}</p>
                  <span style={{ fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: c.status === "ACTIVE" ? "#DCFCE7" : "#F1F5F9", color: c.status === "ACTIVE" ? "#166534" : "#64748B" }}>{c.status}</span>
                </div>
                <p style={{ fontSize: 12, color: "#94A3B8", margin: 0 }}>
                  {c.commissionRatePct}% commission{c.contactName ? ` · ${c.contactName}` : ""}{c.contactEmail ? ` · ${c.contactEmail}` : ""}
                </p>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
                <div style={{ textAlign: "right" }}>
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: "0 0 2px" }}>Trust held</p>
                  <p style={{ fontSize: 14, fontWeight: 700, color: "#059669", margin: 0 }}>{fmtMoney(c.trustBalance)}</p>
                </div>
                <div style={{ display: "flex", gap: 4 }}>
                  <button title="Edit" onClick={() => setModalClient(c)} style={{ background: "none", border: "none", cursor: "pointer", padding: 6 }}><Pencil size={15} color="#64748B" /></button>
                  <button title={c.status === "ACTIVE" ? "Deactivate" : "Reactivate"} onClick={() => toggleActive.mutate(c)} style={{ background: "none", border: "none", cursor: "pointer", padding: 6 }}>
                    {c.status === "ACTIVE" ? <PowerOff size={15} color="#D97706" /> : <Power size={15} color="#059669" />}
                  </button>
                  <button title="Delete" onClick={() => { if (confirm(`Delete ${c.tradingName}? This is a soft delete and requires ADMIN.`)) remove.mutate(c.id) }}
                    style={{ background: "none", border: "none", cursor: "pointer", padding: 6 }}><Trash2 size={15} color="#DC2626" /></button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {modalClient !== null && (
        <ClientFormModal client={modalClient === "new" ? null : modalClient} onClose={() => setModalClient(null)} />
      )}
    </div>
  )
}
