// src/pages/supply-chain/SuppliersTab.tsx
import React, { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, Search, Users, Star, Phone, Mail, ChevronDown, ChevronUp } from "lucide-react"

interface Supplier {
  id: string; name: string; registrationNumber: string | null; vatNumber: string | null
  bbbeeLevel: number | null; contactName: string | null; contactEmail: string | null
  contactPhone: string | null; paymentTermsDays: number; currency: string; status: string
  totalOrders: number; onTimeDeliveries: number; onTimeRate: number | null
  city: string | null; province: string | null; bankName: string | null
}

const ACCENT = "#D97706"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 9, fontSize: 14, boxSizing: "border-box", outline: "none", background: "#fff" }

const STATUS_BADGE: Record<string, { bg: string; color: string }> = {
  ACTIVE:      { bg: "#DCFCE7", color: "#166534" },
  INACTIVE:    { bg: "#F1F5F9", color: "#475569" },
  BLACKLISTED: { bg: "#FEE2E2", color: "#DC2626" },
}

function BbbeeBar({ level }: { level: number | null }) {
  if (!level) return <span style={{ fontSize: 12, color: "#94A3B8" }}>—</span>
  const colour = level <= 2 ? "#059669" : level <= 4 ? "#D97706" : "#DC2626"
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
      <span style={{ fontSize: 13, fontWeight: 700, color: colour }}>L{level}</span>
      <div style={{ display: "flex", gap: 2 }}>
        {[1,2,3,4,5,6,7,8].map(l => (
          <div key={l} style={{ width: 6, height: 6, borderRadius: 2, background: l <= level ? colour : "#E2E8F0" }} />
        ))}
      </div>
    </div>
  )
}

export function SuppliersTab() {
  const qc = useQueryClient()
  const [search, setSearch]   = useState("")
  const [status, setStatus]   = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [err, setErr]         = useState("")

  const initF = () => ({
    name: "", registrationNumber: "", vatNumber: "", bbbeeLevel: "",
    contactName: "", contactEmail: "", contactPhone: "", website: "",
    street: "", suburb: "", city: "", province: "", postalCode: "",
    bankName: "", bankAccount: "", bankBranchCode: "",
    paymentTermsDays: "30", currency: "ZAR", notes: "",
  })
  const [form, setForm] = useState(initF())
  const sf = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data, isLoading } = useQuery<{ content: Supplier[] }>({
    queryKey: ["scm-suppliers", search, status],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "50" })
      if (search) params.set("search", search)
      if (status) params.set("status", status)
      const r = await apiClient.get(`/api/v1/supply-chain/suppliers?${params}`)
      const d = r.data?.data ?? r.data
      return Array.isArray(d) ? { content: d } : d
    },
    staleTime: 30_000,
  })

  const createMut = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/supply-chain/suppliers", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["scm-suppliers"] }); qc.invalidateQueries({ queryKey: ["scm-summary"] }); setShowCreate(false); setForm(initF()); setErr("") },
    onError: (e: any) => setErr(e.response?.data?.message || "Failed to create supplier"),
  })

  const suppliers = data?.content ?? []

  const handleCreate = () => {
    if (!form.name.trim()) { setErr("Supplier name is required"); return }
    createMut.mutate({
      name: form.name.trim(),
      registrationNumber: form.registrationNumber || null,
      vatNumber: form.vatNumber || null,
      bbbeeLevel: form.bbbeeLevel ? parseInt(form.bbbeeLevel) : null,
      contactName: form.contactName || null,
      contactEmail: form.contactEmail || null,
      contactPhone: form.contactPhone || null,
      website: form.website || null,
      street: form.street || null, suburb: form.suburb || null,
      city: form.city || null, province: form.province || null, postalCode: form.postalCode || null,
      bankName: form.bankName || null, bankAccount: form.bankAccount || null, bankBranchCode: form.bankBranchCode || null,
      paymentTermsDays: parseInt(form.paymentTermsDays) || 30,
      currency: form.currency || "ZAR",
      notes: form.notes || null,
    })
  }

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 12, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <div style={{ position: "relative" }}>
            <Search size={13} color="#94A3B8" style={{ position: "absolute", left: 10, top: "50%", transform: "translateY(-50%)" }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search suppliers…"
              style={{ ...inp, paddingLeft: 30, width: 200 }} />
          </div>
          {["", "ACTIVE", "INACTIVE", "BLACKLISTED"].map(s => (
            <button key={s} onClick={() => setStatus(s)}
              style={{ padding: "6px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", fontWeight: status === s ? 700 : 400, border: status === s ? `1.5px solid ${ACCENT}` : "1px solid #E2E8F0", background: status === s ? "#FEF3C7" : "#fff", color: status === s ? ACCENT : "#64748B" }}>
              {s || "All"}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setErr("") }}
          style={{ display: "flex", alignItems: "center", gap: 5, padding: "8px 14px", background: ACCENT, color: "#fff", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> Add Supplier
        </button>
      </div>

      {/* Supplier list */}
      {isLoading
        ? <div style={{ padding: 40, textAlign: "center", color: "#94A3B8" }}>Loading…</div>
        : suppliers.length === 0
          ? <div style={{ textAlign: "center", padding: "50px 0", color: "#94A3B8" }}>
              <Users size={36} style={{ opacity: .3, marginBottom: 10 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No suppliers found</div>
            </div>
          : <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {suppliers.map((s, i) => {
                const sb = STATUS_BADGE[s.status] ?? STATUS_BADGE.ACTIVE
                const isOpen = expanded === s.id
                return (
                  <div key={s.id} style={{ borderTop: i > 0 ? "1px solid #F1F5F9" : "none" }}>
                    <div onClick={() => setExpanded(isOpen ? null : s.id)}
                      style={{ display: "flex", alignItems: "center", padding: "12px 16px", cursor: "pointer", background: isOpen ? "#FFFBEB" : i % 2 === 0 ? "#fff" : "#FAFAFA" }}
                      onMouseEnter={e => { if (!isOpen) (e.currentTarget as HTMLElement).style.background = "#F0F7FF" }}
                      onMouseLeave={e => { if (!isOpen) (e.currentTarget as HTMLElement).style.background = i % 2 === 0 ? "#fff" : "#FAFAFA" }}
                    >
                      {/* Avatar */}
                      <div style={{ width: 38, height: 38, borderRadius: 10, background: "#FEF3C7", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, marginRight: 12, fontSize: 14, fontWeight: 800, color: ACCENT }}>
                        {s.name.charAt(0).toUpperCase()}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                          <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>{s.name}</span>
                          <span style={{ background: sb.bg, color: sb.color, fontSize: 10, fontWeight: 700, padding: "1px 7px", borderRadius: 20 }}>{s.status}</span>
                        </div>
                        <div style={{ fontSize: 12, color: "#64748B", display: "flex", gap: 12 }}>
                          {s.contactEmail && <span style={{ display: "flex", alignItems: "center", gap: 3 }}><Mail size={10} />{s.contactEmail}</span>}
                          {s.contactPhone && <span style={{ display: "flex", alignItems: "center", gap: 3 }}><Phone size={10} />{s.contactPhone}</span>}
                          {s.city && <span>{s.city}{s.province ? `, ${s.province}` : ""}</span>}
                        </div>
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 20, flexShrink: 0 }}>
                        <div style={{ textAlign: "center" }}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 600, marginBottom: 3 }}>BBBEE</div>
                          <BbbeeBar level={s.bbbeeLevel} />
                        </div>
                        <div style={{ textAlign: "center" }}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 600, marginBottom: 3 }}>TERMS</div>
                          <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>Net {s.paymentTermsDays}</div>
                        </div>
                        <div style={{ textAlign: "center" }}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 600, marginBottom: 3 }}>ON-TIME</div>
                          <div style={{ fontSize: 13, fontWeight: 700, color: s.onTimeRate != null && s.onTimeRate >= 80 ? "#059669" : "#D97706" }}>
                            {s.onTimeRate != null ? `${s.onTimeRate.toFixed(0)}%` : "—"}
                          </div>
                        </div>
                        {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                      </div>
                    </div>

                    {/* Expanded detail */}
                    {isOpen && (
                      <div style={{ padding: "16px 20px", background: "#FFFBEB", borderTop: "1px solid #FEF3C7" }}>
                        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16 }}>
                          <Detail label="Reg Number"   value={s.registrationNumber} />
                          <Detail label="VAT Number"   value={s.vatNumber} />
                          <Detail label="Orders Total" value={String(s.totalOrders)} />
                          <Detail label="On-time Deliveries" value={`${s.onTimeDeliveries} / ${s.totalOrders}`} />
                          <Detail label="Bank"         value={s.bankName} />
                          <Detail label="Currency"     value={s.currency} />
                          <Detail label="Contact"      value={s.contactName} />
                        </div>
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
      }

      {/* Create Supplier Modal */}
      {showCreate && (
        <Modal title="Add Supplier" onClose={() => setShowCreate(false)}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <Fld label="Company Name *" span={2}><input value={form.name} onChange={e => sf("name", e.target.value)} placeholder="Acme Suppliers (Pty) Ltd" style={inp} autoFocus /></Fld>
            <Fld label="Reg Number"><input value={form.registrationNumber} onChange={e => sf("registrationNumber", e.target.value)} placeholder="2020/123456/07" style={inp} /></Fld>
            <Fld label="VAT Number"><input value={form.vatNumber} onChange={e => sf("vatNumber", e.target.value)} placeholder="4570123456" style={inp} /></Fld>
            <Fld label="BBBEE Level (1–8)"><input type="number" min={1} max={8} value={form.bbbeeLevel} onChange={e => sf("bbbeeLevel", e.target.value)} placeholder="1" style={inp} /></Fld>
            <Fld label="Payment Terms (days)"><input type="number" value={form.paymentTermsDays} onChange={e => sf("paymentTermsDays", e.target.value)} placeholder="30" style={inp} /></Fld>

            <div style={{ gridColumn: "span 2", borderTop: "1px solid #F1F5F9", paddingTop: 12, marginTop: 4 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 12 }}>Contact</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
                <Fld label="Contact Name"><input value={form.contactName} onChange={e => sf("contactName", e.target.value)} style={inp} /></Fld>
                <Fld label="Phone"><input value={form.contactPhone} onChange={e => sf("contactPhone", e.target.value)} placeholder="011 234 5678" style={inp} /></Fld>
                <Fld label="Email" span={2}><input type="email" value={form.contactEmail} onChange={e => sf("contactEmail", e.target.value)} style={inp} /></Fld>
              </div>
            </div>

            <div style={{ gridColumn: "span 2", borderTop: "1px solid #F1F5F9", paddingTop: 12, marginTop: 4 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 12 }}>Banking</div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12 }}>
                <Fld label="Bank Name"><input value={form.bankName} onChange={e => sf("bankName", e.target.value)} placeholder="FNB" style={inp} /></Fld>
                <Fld label="Account Number"><input value={form.bankAccount} onChange={e => sf("bankAccount", e.target.value)} style={inp} /></Fld>
                <Fld label="Branch Code"><input value={form.bankBranchCode} onChange={e => sf("bankBranchCode", e.target.value)} placeholder="250655" style={inp} /></Fld>
              </div>
            </div>

            <Fld label="Notes" span={2}><textarea value={form.notes} onChange={e => sf("notes", e.target.value)} style={{ ...inp, minHeight: 50, resize: "vertical" }} /></Fld>
          </div>
          {err && <ErrBox msg={err} />}
          <ModalFooter onCancel={() => setShowCreate(false)} onConfirm={handleCreate} label={createMut.isPending ? "Saving…" : "Add Supplier"} loading={createMut.isPending} accent={ACCENT} />
        </Modal>
      )}
    </div>
  )
}

// ── Shared helpers ────────────────────────────────────────────────────────────
function Detail({ label, value }: { label: string; value: string | null | undefined }) {
  return <div><div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.04em", marginBottom: 3 }}>{label}</div><div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{value || "—"}</div></div>
}
function Fld({ label, children, span }: { label: string; children: React.ReactNode; span?: number }) {
  return <div style={{ gridColumn: span ? `span ${span}` : undefined }}><label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}
function ErrBox({ msg }: { msg: string }) {
  return <div style={{ marginTop: 10, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, color: "#DC2626", fontSize: 13 }}>{msg}</div>
}
function Modal({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
      <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 600, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", fontSize: 20, lineHeight: 1 }}>×</button>
        </div>
        {children}
      </div>
    </div>
  )
}
function ModalFooter({ onCancel, onConfirm, label, loading, accent }: { onCancel: () => void; onConfirm: () => void; label: string; loading?: boolean; accent?: string }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: "9px 16px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 13, cursor: "pointer", color: "#64748B" }}>Cancel</button>
      <button onClick={onConfirm} disabled={loading} style={{ padding: "9px 18px", background: accent ?? "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: "pointer", opacity: loading ? .6 : 1 }}>{label}</button>
    </div>
  )
}
