import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Users, Phone, Mail, X, Pencil, Download } from "lucide-react"

interface Supplier {
  id: string
  name: string
  contactName: string | null
  contactPhone: string | null
  contactEmail: string | null
  accountNumber: string | null
  createdAt: string
}

const EMPTY = { name: "", contactName: "", contactPhone: "", contactEmail: "", accountNumber: "" }

export default function SuppliersTab() {
  const qc = useQueryClient()
  const [showAdd, setShowAdd]   = useState(false)
  const [editing, setEditing]   = useState<Supplier | null>(null)
  const [error, setError]       = useState("")
  const [form, setForm]         = useState(EMPTY)
  const [editForm, setEditForm] = useState(EMPTY)

  const { data: suppliers = [], isLoading } = useQuery<Supplier[]>({
    queryKey: ["fuel-suppliers"],
    queryFn: async () => {
      const res = await apiClient.get("/api/v1/fuel/suppliers")
      return Array.isArray(res.data) ? res.data : (res.data.content ?? [])
    },
  })

  const createSupplier = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/fuel/suppliers", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fuel-suppliers"] })
      setShowAdd(false); setForm(EMPTY); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to add supplier"),
  })

  const updateSupplier = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.put(`/api/v1/fuel/suppliers/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["fuel-suppliers"] })
      setEditing(null); setEditForm(EMPTY); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to update supplier"),
  })

  // FIX: "no supplier statement/receiving report PDF" gap — exports "everything
  // received from this supplier this month" as a PDF via the server-side report.
  const downloadStatement = async (s: Supplier) => {
    const r = await apiClient.get(`/api/v1/fuel/suppliers/${s.id}/statement`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([r.data]))
    const link = document.createElement("a")
    link.href = url
    link.download = `statement-${s.name.replace(/[^a-zA-Z0-9]+/g, "-")}.pdf`
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  }

  const openEdit = (s: Supplier) => {
    setEditing(s)
    setEditForm({
      name: s.name,
      contactName: s.contactName ?? "",
      contactPhone: s.contactPhone ?? "",
      contactEmail: s.contactEmail ?? "",
      accountNumber: s.accountNumber ?? "",
    })
    setError("")
  }

  const sf = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))
  const ef = (k: keyof typeof editForm, v: string) => setEditForm(p => ({ ...p, [k]: v }))

  const toBody = (f: typeof EMPTY) => ({
    name: f.name,
    contactName: f.contactName || null,
    contactPhone: f.contactPhone || null,
    contactEmail: f.contactEmail || null,
    accountNumber: f.accountNumber || null,
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div style={{ fontSize: 14, color: "#64748B" }}>
          {suppliers.length} supplier{suppliers.length !== 1 ? "s" : ""}
        </div>
        <button onClick={() => { setShowAdd(true); setError("") }} style={btnPrimary}>
          <Plus size={15} /> Add Supplier
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading suppliers...</div>
      ) : suppliers.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No suppliers yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Add your fuel suppliers to track receipts.</div>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 12 }}>
          {suppliers.map(s => (
            <div key={s.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "18px 20px" }}>
              <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", marginBottom: 12 }}>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <div style={{ width: 40, height: 40, borderRadius: 10, background: "#EFF6FF", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                    <Users size={18} color="#1D4ED8" />
                  </div>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{s.name}</div>
                    {s.accountNumber && (
                      <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 1 }}>Acc: {s.accountNumber}</div>
                    )}
                  </div>
                </div>
                <div style={{ display: "flex", gap: 6, flexShrink: 0 }}>
                  <button onClick={() => downloadStatement(s)}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", background: "#F0FDFA", border: "1px solid #99F6E4", borderRadius: 7, fontSize: 12, color: "#0D9488", cursor: "pointer" }}>
                    <Download size={12} /> Statement
                  </button>
                  <button onClick={() => openEdit(s)}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 7, fontSize: 12, color: "#1D4ED8", cursor: "pointer" }}>
                    <Pencil size={12} /> Edit
                  </button>
                </div>
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                {s.contactName && (
                  <div style={{ fontSize: 13, color: "#475569" }}>
                    <span style={{ color: "#94A3B8", marginRight: 6 }}>Contact:</span>{s.contactName}
                  </div>
                )}
                {s.contactPhone && (
                  <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "#475569" }}>
                    <Phone size={12} color="#94A3B8" />{s.contactPhone}
                  </div>
                )}
                {s.contactEmail && (
                  <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "#475569" }}>
                    <Mail size={12} color="#94A3B8" />{s.contactEmail}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add Modal */}
      {showAdd && (
        <Modal title="Add Fuel Supplier" onClose={() => { setShowAdd(false); setError("") }}>
          <SupplierForm form={form} onChange={sf} />
          {error && <ErrMsg msg={error} />}
          <Footer
            onCancel={() => { setShowAdd(false); setError("") }}
            onSubmit={() => createSupplier.mutate(toBody(form))}
            loading={createSupplier.isPending}
            disabled={!form.name}
            label="Add Supplier"
          />
        </Modal>
      )}

      {/* Edit Modal */}
      {editing && (
        <Modal title={`Edit — ${editing.name}`} onClose={() => { setEditing(null); setError("") }}>
          <SupplierForm form={editForm} onChange={ef} />
          {error && <ErrMsg msg={error} />}
          <Footer
            onCancel={() => { setEditing(null); setError("") }}
            onSubmit={() => updateSupplier.mutate({ id: editing.id, body: toBody(editForm) })}
            loading={updateSupplier.isPending}
            disabled={!editForm.name}
            label="Save Changes"
          />
        </Modal>
      )}
    </div>
  )
}

function SupplierForm({ form, onChange }: { form: any; onChange: (k: any, v: string) => void }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
      <div style={{ gridColumn: "1 / -1" }}>
        <Field label="Company Name *">
          <input value={form.name} onChange={e => onChange("name", e.target.value)}
            placeholder="Sasol Oil Distributors" style={inp} autoFocus />
        </Field>
      </div>
      <Field label="Contact Name">
        <input value={form.contactName} onChange={e => onChange("contactName", e.target.value)}
          placeholder="Sipho Nkosi" style={inp} />
      </Field>
      <Field label="Account Number">
        <input value={form.accountNumber} onChange={e => onChange("accountNumber", e.target.value)}
          placeholder="SOD-2024-001" style={inp} />
      </Field>
      <Field label="Phone">
        <input value={form.contactPhone} onChange={e => onChange("contactPhone", e.target.value)}
          placeholder="+27 11 700 1234" style={inp} />
      </Field>
      <Field label="Email">
        <input type="email" value={form.contactEmail} onChange={e => onChange("contactEmail", e.target.value)}
          placeholder="orders@supplier.co.za" style={inp} />
      </Field>
    </div>
  )
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 500, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}>
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>
      {children}
    </div>
  )
}

function ErrMsg({ msg }: { msg: string }) {
  return (
    <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>
      {msg}
    </div>
  )
}

function Footer({ onCancel, onSubmit, loading, disabled, label }: {
  onCancel: () => void; onSubmit: () => void; loading: boolean; disabled: boolean; label: string
}) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
      <button onClick={onCancel}
        style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>
        Cancel
      </button>
      <button onClick={onSubmit} disabled={disabled || loading}
        style={{ padding: "9px 20px", background: disabled || loading ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: disabled || loading ? "not-allowed" : "pointer" }}>
        {loading ? "Saving..." : label}
      </button>
    </div>
  )
}

const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }
