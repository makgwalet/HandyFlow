import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, ShoppingBag, CheckCircle, Phone, Mail } from "lucide-react"

interface Vendor {
  id: string
  vendorType: string
  companyName: string
  contactName: string
  contactPhone: string
  contactEmail: string
  serviceDescription: string
  quotedAmount: number
  confirmed: boolean
  notes: string
}

const VENDOR_TYPES = ["CATERING", "AV_EQUIPMENT", "SECURITY", "PHOTOGRAPHER", "VIDEOGRAPHER", "DECORATOR", "TRANSPORT", "ENTERTAINMENT", "CLEANING", "OTHER"]

const TYPE_COLORS: Record<string, string> = {
  CATERING:      "#0D9488",
  AV_EQUIPMENT:  "#1D4ED8",
  SECURITY:      "#DC2626",
  PHOTOGRAPHER:  "#7C3AED",
  VIDEOGRAPHER:  "#DB2777",
  DECORATOR:     "#D97706",
  TRANSPORT:     "#166534",
  ENTERTAINMENT: "#0891B2",
}

export default function VendorsTab({ eventId }: { eventId: string | null }) {
  const qc = useQueryClient()
  const [showAdd, setShowAdd] = useState(false)
  const [error, setError]     = useState("")

  const [form, setForm] = useState({
    vendorType: "CATERING", companyName: "", contactName: "",
    contactPhone: "", contactEmail: "", serviceDescription: "",
    quotedAmount: "", notes: "",
  })
  const f = (k: keyof typeof form, v: string) => setForm(p => ({ ...p, [k]: v }))

  const { data: vendors = [], isLoading } = useQuery<Vendor[]>({
    queryKey: ["event-vendors", eventId],
    queryFn: async () => {
      if (!eventId) return []
      const r = await apiClient.get(`/api/v1/events/${eventId}/vendors`)
      return r.data || []
    },
    enabled: !!eventId,
  })

  const addVendor = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/events/${eventId}/vendors`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["event-vendors"] }); setShowAdd(false); resetForm() },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to add vendor"),
  })

  const confirmVendor = useMutation({
    mutationFn: (vendorId: string) => apiClient.post(`/api/v1/events/${eventId}/vendors/${vendorId}/confirm`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["event-vendors"] }),
  })

  const resetForm = () => setForm({ vendorType: "CATERING", companyName: "", contactName: "", contactPhone: "", contactEmail: "", serviceDescription: "", quotedAmount: "", notes: "" })

  const fmtR = (n: number) => n ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"

  if (!eventId) return (
    <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
      <ShoppingBag size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
      <div style={{ fontWeight: 600, color: "#475569" }}>Select an event first</div>
      <div style={{ fontSize: 14, marginTop: 4 }}>Click on an event then navigate to Vendors.</div>
    </div>
  )

  const totalQuoted    = vendors.reduce((s, v) => s + Number(v.quotedAmount || 0), 0)
  const confirmedCount = vendors.filter(v => v.confirmed).length

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div style={{ display: "flex", gap: 10 }}>
          {vendors.length > 0 && (
            <>
              <div style={{ background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, padding: "8px 14px" }}>
                <div style={{ fontSize: 10, fontWeight: 600, color: "#166534" }}>CONFIRMED</div>
                <div style={{ fontSize: 18, fontWeight: 700, color: "#166534" }}>{confirmedCount} / {vendors.length}</div>
              </div>
              <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, padding: "8px 14px" }}>
                <div style={{ fontSize: 10, fontWeight: 600, color: "#64748B" }}>TOTAL QUOTED</div>
                <div style={{ fontSize: 18, fontWeight: 700, color: "#0F172A" }}>{fmtR(totalQuoted)}</div>
              </div>
            </>
          )}
        </div>
        <button onClick={() => { setShowAdd(true); setError("") }} style={btnPrimary}><Plus size={15} /> Add Vendor</button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading vendors...</div>
      ) : vendors.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <ShoppingBag size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No vendors yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Add caterers, AV suppliers, security and more.</div>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 14 }}>
          {vendors.map(v => {
            const color = TYPE_COLORS[v.vendorType] || "#64748B"
            return (
              <div key={v.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", background: "#fff" }}>
                <div style={{ height: 4, background: color }} />
                <div style={{ padding: "16px 18px" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 8 }}>
                    <div>
                      <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{v.companyName}</div>
                      <span style={{ background: `${color}18`, color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
                        {v.vendorType.replace("_", " ")}
                      </span>
                    </div>
                    {v.confirmed ? (
                      <CheckCircle size={18} color="#166534" />
                    ) : (
                      <button onClick={() => confirmVendor.mutate(v.id)}
                        style={{ padding: "4px 10px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 6, fontSize: 11, cursor: "pointer" }}>
                        Confirm
                      </button>
                    )}
                  </div>

                  {v.serviceDescription && (
                    <div style={{ fontSize: 12, color: "#64748B", marginBottom: 10, lineHeight: 1.4 }}>{v.serviceDescription}</div>
                  )}

                  <div style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                    {v.contactName && <div style={{ fontSize: 12, color: "#475569", fontWeight: 500 }}>{v.contactName}</div>}
                    {v.contactPhone && <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "#64748B" }}><Phone size={11} color="#94A3B8" />{v.contactPhone}</div>}
                    {v.contactEmail && <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "#64748B" }}><Mail size={11} color="#94A3B8" />{v.contactEmail}</div>}
                  </div>

                  {v.quotedAmount > 0 && (
                    <div style={{ marginTop: 12, padding: "8px 12px", background: "#F8FAFC", borderRadius: 7, display: "flex", justifyContent: "space-between" }}>
                      <span style={{ fontSize: 11, color: "#94A3B8" }}>QUOTED</span>
                      <span style={{ fontSize: 14, fontWeight: 700, color: "#0F172A" }}>{fmtR(v.quotedAmount)}</span>
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {showAdd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 520, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Add Vendor</h3>
              <button onClick={() => { setShowAdd(false); resetForm() }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Vendor Type">
                  <select value={form.vendorType} onChange={e => f("vendorType", e.target.value)} style={inputStyle}>
                    {VENDOR_TYPES.map(t => <option key={t} value={t}>{t.replace("_", " ")}</option>)}
                  </select>
                </Field>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Company Name *"><input value={form.companyName} onChange={e => f("companyName", e.target.value)} placeholder="ABC Catering Co." style={inputStyle} /></Field>
              </div>
              <Field label="Contact Name"><input value={form.contactName} onChange={e => f("contactName", e.target.value)} placeholder="Sarah Dlamini" style={inputStyle} /></Field>
              <Field label="Quoted Amount (R)"><input type="number" value={form.quotedAmount} onChange={e => f("quotedAmount", e.target.value)} placeholder="5000.00" style={inputStyle} /></Field>
              <Field label="Phone"><input value={form.contactPhone} onChange={e => f("contactPhone", e.target.value)} placeholder="+27 11 555 0100" style={inputStyle} /></Field>
              <Field label="Email"><input value={form.contactEmail} onChange={e => f("contactEmail", e.target.value)} placeholder="info@vendor.co.za" style={inputStyle} /></Field>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Service Description"><textarea value={form.serviceDescription} onChange={e => f("serviceDescription", e.target.value)} rows={2} placeholder="What services will they provide..." style={{ ...inputStyle, resize: "vertical" as const }} /></Field>
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => { setShowAdd(false); resetForm() }} style={btnCancel}>Cancel</button>
              <button onClick={() => addVendor.mutate({ ...form, quotedAmount: parseFloat(form.quotedAmount) || null })}
                disabled={!form.companyName || addVendor.isPending} style={btnPrimary}>
                {addVendor.isPending ? "Adding..." : "Add Vendor"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: "block", fontSize: 13, fontWeight: 500, color: "#374151", marginBottom: 5 }}>{label}</label>{children}</div>
}
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnCancel: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff" }
