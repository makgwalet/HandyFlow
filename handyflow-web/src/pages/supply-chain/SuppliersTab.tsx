// src/pages/supply-chain/SuppliersTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Search, Plus, Edit2, Mail, Phone, MapPin, Building2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { unwrap, inp, Badge, Modal, ModalFooter, Field, ErrBox, Spinner, EmptyState, filterPill, type Supplier } from "./scm.shared"

const PROVINCES = ["Gauteng","Western Cape","KwaZulu-Natal","Eastern Cape","Limpopo","Mpumalanga","North West","Free State","Northern Cape"]

export function SuppliersTab() {
  const qc = useQueryClient()
  const [search, setSearch] = useState("")
  const [statusFilter, setStatusFilter] = useState("")
  const [showModal, setShowModal] = useState(false)
  const [editing, setEditing] = useState<Supplier | null>(null)
  const [err, setErr] = useState("")

  const blank = () => ({ name: "", contactName: "", contactEmail: "", contactPhone: "", bbbeeLevel: "", paymentTermsDays: "30", city: "", province: "", vatNumber: "", registrationNumber: "", bankName: "", bankAccount: "", bankBranchCode: "", notes: "" })
  const [form, setForm] = useState(blank())
  const sf = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: suppliers = [], isLoading } = useQuery<Supplier[]>({
    queryKey: ["scm-suppliers", search, statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "100" })
      if (search)       params.set("search", search)
      if (statusFilter) params.set("status", statusFilter)
      const r = await apiClient.get(`/api/v1/supply-chain/suppliers?${params}`)
      return unwrap<Supplier>(r)
    },
    staleTime: 30_000,
  })

  const saveMut = useMutation({
    mutationFn: (body: any) => editing
      ? apiClient.put(`/api/v1/supply-chain/suppliers/${editing.id}`, body)
      : apiClient.post("/api/v1/supply-chain/suppliers", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["scm-suppliers"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }); closeModal() },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to save supplier"),
  })

  const openAdd  = () => { setEditing(null); setForm(blank()); setErr(""); setShowModal(true) }
  const openEdit = (s: Supplier) => {
    setEditing(s)
    setForm({ name: s.name, contactName: s.contactName || "", contactEmail: s.contactEmail || "", contactPhone: s.contactPhone || "", bbbeeLevel: s.bbbeeLevel ? String(s.bbbeeLevel) : "", paymentTermsDays: String(s.paymentTermsDays), city: s.city || "", province: "", vatNumber: s.vatNumber || "", registrationNumber: s.registrationNumber || "", bankName: s.bankName || "", bankAccount: s.bankAccount || "", bankBranchCode: "", notes: s.notes || "" })
    setErr(""); setShowModal(true)
  }
  const closeModal = () => { setShowModal(false); setEditing(null) }

  const doSave = () => {
    if (!form.name.trim()) { setErr("Supplier name is required"); return }
    saveMut.mutate({ name: form.name.trim(), contactName: form.contactName || null, contactEmail: form.contactEmail || null, contactPhone: form.contactPhone || null, bbbeeLevel: form.bbbeeLevel ? parseInt(form.bbbeeLevel) : null, paymentTermsDays: parseInt(form.paymentTermsDays) || 30, city: form.city || null, province: form.province || null, vatNumber: form.vatNumber || null, registrationNumber: form.registrationNumber || null, bankName: form.bankName || null, bankAccount: form.bankAccount || null, bankBranchCode: form.bankBranchCode || null, notes: form.notes || null })
  }

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 6, alignItems: "center", flexWrap: "wrap" }}>
          {["", "ACTIVE", "INACTIVE", "BLACKLISTED"].map(s => (
            <button key={s} onClick={() => setStatusFilter(s)} style={filterPill(statusFilter === s)}>{s || "All"}</button>
          ))}
          <div style={{ position: "relative" }}>
            <Search size={13} style={{ position: "absolute", left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search suppliers…"
              style={{ ...inp, paddingLeft: 28, width: 200, padding: "7px 10px 7px 28px" }} />
          </div>
        </div>
        <button onClick={openAdd} style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Add Supplier
        </button>
      </div>

      {/* Content */}
      {isLoading ? <Spinner /> : suppliers.length === 0 ? (
        <EmptyState icon={Building2} title="No suppliers" sub="Add your first supplier to start raising purchase orders" />
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#F8FAFC" }}>
                {["Supplier", "Contact", "Location", "BBBEE", "Terms", "On-Time", "Status", ""].map(h => (
                  <th key={h} style={{ padding: "10px 14px", textAlign: "left", fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em" }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {suppliers.map((s, i) => (
                <tr key={s.id} style={{ borderTop: "1px solid #F1F5F9", background: i % 2 === 0 ? "#fff" : "#FAFAFA" }}>
                  <td style={{ padding: "11px 14px" }}>
                    <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>{s.name}</div>
                    {s.registrationNumber && <div style={{ fontSize: 11, color: "#94A3B8" }}>Reg: {s.registrationNumber}</div>}
                  </td>
                  <td style={{ padding: "11px 14px" }}>
                    {s.contactName  && <div style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: "#374151", marginBottom: 2 }}><Building2 size={11} color="#94A3B8" />{s.contactName}</div>}
                    {s.contactEmail && <div style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: "#64748B" }}><Mail size={11} color="#94A3B8" />{s.contactEmail}</div>}
                    {s.contactPhone && <div style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12, color: "#64748B" }}><Phone size={11} color="#94A3B8" />{s.contactPhone}</div>}
                  </td>
                  <td style={{ padding: "11px 14px", fontSize: 12, color: "#64748B" }}>
                    {s.city ? <span style={{ display: "flex", alignItems: "center", gap: 4 }}><MapPin size={11} color="#94A3B8" />{s.city}</span> : "—"}
                  </td>
                  <td style={{ padding: "11px 14px" }}>
                    {s.bbbeeLevel ? (
                      <span style={{ background: "#ECFDF5", color: "#059669", fontSize: 11, fontWeight: 700, padding: "2px 8px", borderRadius: 20 }}>L{s.bbbeeLevel}</span>
                    ) : <span style={{ color: "#94A3B8", fontSize: 12 }}>—</span>}
                  </td>
                  <td style={{ padding: "11px 14px", fontSize: 12, color: "#64748B" }}>{s.paymentTermsDays} days</td>
                  <td style={{ padding: "11px 14px" }}>
                    {s.onTimeRate != null ? (
                      <span style={{ fontSize: 13, fontWeight: 700, color: s.onTimeRate >= 90 ? "#059669" : s.onTimeRate >= 70 ? "#D97706" : "#DC2626" }}>
                        {s.onTimeRate.toFixed(0)}%
                      </span>
                    ) : <span style={{ color: "#94A3B8", fontSize: 12 }}>—</span>}
                  </td>
                  <td style={{ padding: "11px 14px" }}><Badge status={s.status} /></td>
                  <td style={{ padding: "11px 14px" }}>
                    <button onClick={() => openEdit(s)} style={{ background: "none", border: "none", cursor: "pointer", color: "#64748B", padding: 4, borderRadius: 6 }}>
                      <Edit2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Add/Edit Modal */}
      {showModal && (
        <Modal title={editing ? "Edit Supplier" : "Add Supplier"} onClose={closeModal}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Field label="Supplier Name *" span={2}><input value={form.name} onChange={e => sf("name", e.target.value)} placeholder="Bosch Tools SA" style={inp} autoFocus /></Field>
            <Field label="Reg. Number"><input value={form.registrationNumber} onChange={e => sf("registrationNumber", e.target.value)} placeholder="2023/123456/07" style={inp} /></Field>
            <Field label="VAT Number"><input value={form.vatNumber} onChange={e => sf("vatNumber", e.target.value)} placeholder="4123456789" style={inp} /></Field>
            <Field label="Contact Person"><input value={form.contactName} onChange={e => sf("contactName", e.target.value)} placeholder="Sipho Dlamini" style={inp} /></Field>
            <Field label="Contact Phone"><input value={form.contactPhone} onChange={e => sf("contactPhone", e.target.value)} placeholder="+27 11 555 1234" style={inp} /></Field>
            <Field label="Contact Email" span={2}><input value={form.contactEmail} onChange={e => sf("contactEmail", e.target.value)} placeholder="orders@supplier.co.za" style={inp} /></Field>
            <Field label="City"><input value={form.city} onChange={e => sf("city", e.target.value)} placeholder="Johannesburg" style={inp} /></Field>
            <Field label="Province">
              <select value={form.province} onChange={e => sf("province", e.target.value)} style={inp}>
                <option value="">Select…</option>
                {PROVINCES.map(p => <option key={p} value={p}>{p}</option>)}
              </select>
            </Field>
            <Field label="BBBEE Level">
              <select value={form.bbbeeLevel} onChange={e => sf("bbbeeLevel", e.target.value)} style={inp}>
                <option value="">Not rated</option>
                {[1,2,3,4,5,6,7,8].map(l => <option key={l} value={l}>Level {l}</option>)}
              </select>
            </Field>
            <Field label="Payment Terms">
              <select value={form.paymentTermsDays} onChange={e => sf("paymentTermsDays", e.target.value)} style={inp}>
                {["7","14","30","45","60","90"].map(d => <option key={d} value={d}>{d} days</option>)}
              </select>
            </Field>
            <Field label="Bank Name"><input value={form.bankName} onChange={e => sf("bankName", e.target.value)} placeholder="First National Bank" style={inp} /></Field>
            <Field label="Account Number"><input value={form.bankAccount} onChange={e => sf("bankAccount", e.target.value)} placeholder="62341098765" style={inp} /></Field>
            <Field label="Branch Code" span={2}><input value={form.bankBranchCode} onChange={e => sf("bankBranchCode", e.target.value)} placeholder="250655" style={inp} /></Field>
            <Field label="Notes" span={2}><textarea value={form.notes} onChange={e => sf("notes", e.target.value)} style={{ ...inp, minHeight: 56, resize: "vertical" }} /></Field>
          </div>
          {err && <ErrBox msg={err} />}
          <ModalFooter onCancel={closeModal} onConfirm={doSave} label={saveMut.isPending ? "Saving…" : editing ? "Save Changes" : "Add Supplier"} loading={saveMut.isPending} />
        </Modal>
      )}
    </div>
  )
}
