// src/pages/events/VendorsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, CheckCircle, ChevronLeft, Truck, Phone, Mail } from "lucide-react"

const fmtR = (n: any) => n != null && n > 0 ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

const VENDOR_TYPES = ["CATERING","AV_TECH","SECURITY","PHOTOGRAPHY","TRANSPORT","DECOR","ENTERTAINMENT","OTHER"]
const TYPE_COLOR: Record<string, string> = {
  CATERING: "#D97706", AV_TECH: "#7C3AED", SECURITY: "#DC2626",
  PHOTOGRAPHY: "#0284C7", TRANSPORT: "#16A34A", DECOR: "#BE185D",
  ENTERTAINMENT: "#EA580C", OTHER: "#64748B",
}

interface Props {
  eventId: string | null
  eventTitle: string
  onChangeEvent: () => void
}

export default function VendorsTab({ eventId, eventTitle, onChangeEvent }: Props) {
  const qc = useQueryClient()
  const [showAdd, setShowAdd] = useState(false)
  const [error,   setError]   = useState("")

  const INIT = () => ({
    vendorType: "CATERING", companyName: "", contactName: "", contactPhone: "",
    contactEmail: "", serviceDescription: "", quotedAmount: "", notes: "",
  })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const { data: vendors = [], isLoading } = useQuery<any[]>({
    queryKey: ["event-vendors", eventId],
    queryFn: async () => {
      if (!eventId) return []
      const r = await apiClient.get(`/api/v1/events/${eventId}/vendors`)
      return r.data?.data ?? r.data ?? []
    },
    enabled: !!eventId,
  })

  const addVendor = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/events/${eventId}/vendors`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["event-vendors", eventId] }); setShowAdd(false); setForm(INIT()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to add vendor"),
  })

  const confirmVendor = useMutation({
    mutationFn: (vendorId: string) => apiClient.post(`/api/v1/events/${eventId}/vendors/${vendorId}/confirm`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["event-vendors", eventId] }),
  })

  const totalQuoted    = (vendors as any[]).reduce((s: number, v: any) => s + parseFloat(v.quotedAmount ?? 0), 0)
  const totalConfirmed = (vendors as any[]).filter((v: any) => v.confirmed).length
  const vendorsByType  = (vendors as any[]).reduce((acc: any, v: any) => { acc[v.vendorType] = (acc[v.vendorType] ?? 0) + 1; return acc }, {})

  if (!eventId) {
    return (
      <div style={{ textAlign: "center", padding: "60px 20px" }}>
        <Truck size={40} style={{ marginBottom: 12, color: "var(--hf-text-disabled)" }} />
        <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)", marginBottom: 8 }}>No event selected</div>
        <button onClick={onChangeEvent}
          style={{ display: "flex", alignItems: "center", gap: 6, margin: "0 auto", padding: "8px 16px", background: "var(--hf-sky)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <ChevronLeft size={14} /> Select an event
        </button>
      </div>
    )
  }

  return (
    <div>
      {/* Event context bar */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 18, padding: "10px 14px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <button onClick={onChangeEvent} style={{ display: "flex", alignItems: "center", gap: 4, background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600 }}>
            <ChevronLeft size={13} /> Events
          </button>
          <span style={{ color: "var(--hf-text-disabled)" }}>/</span>
          <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{eventTitle}</span>
        </div>
        <div style={{ display: "flex", gap: 16, fontSize: 13 }}>
          <span style={{ color: "var(--hf-text-muted)" }}>Vendors: <strong>{(vendors as any[]).length}</strong></span>
          <span style={{ color: "var(--hf-text-muted)" }}>Confirmed: <strong style={{ color: "var(--hf-success-text-strong)" }}>{totalConfirmed}</strong></span>
          {totalQuoted > 0 && <span style={{ color: "var(--hf-text-muted)" }}>Total quoted: <strong style={{ color: "var(--hf-warning-text)" }}>{fmtR(totalQuoted)}</strong></span>}
        </div>
      </div>

      {/* Summary chips by type */}
      {Object.keys(vendorsByType).length > 0 && (
        <div style={{ display: "flex", gap: 8, marginBottom: 18, flexWrap: "wrap" }}>
          {Object.entries(vendorsByType).map(([type, count]: any) => (
            <div key={type} style={{ padding: "4px 12px", background: `${TYPE_COLOR[type] ?? "#64748B"}18`, border: `1px solid ${TYPE_COLOR[type] ?? "#64748B"}40`, borderRadius: 20, fontSize: 12 }}>
              <span style={{ fontWeight: 700, color: TYPE_COLOR[type] ?? "var(--hf-text-muted)" }}>{type.replace("_"," ")}</span>
              <span style={{ color: "var(--hf-text-faint)", marginLeft: 5 }}>×{count}</span>
            </div>
          ))}
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <button onClick={() => { setShowAdd(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, background: "var(--hf-sky)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={13} /> Add Vendor
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading...</div>
      ) : (vendors as any[]).length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)" }}>
          <Truck size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No vendors yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Add caterers, AV technicians, security, and more.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {(vendors as any[]).map((v: any) => {
            const tc = TYPE_COLOR[v.vendorType] ?? "#64748B"
            return (
              <div key={v.id} style={{ border: `1px solid ${v.confirmed ? "#86EFAC" : "#E2E8F0"}`, borderLeft: `3px solid ${v.confirmed ? "#22C55E" : tc}`, borderRadius: 10, padding: "14px 20px", background: v.confirmed ? "var(--hf-success-soft)" : "var(--hf-surface)", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6, flexWrap: "wrap" }}>
                    <span style={{ background: `${tc}18`, color: tc, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{v.vendorType.replace("_"," ")}</span>
                    <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{v.companyName}</span>
                    {v.confirmed && <span style={{ display: "flex", alignItems: "center", gap: 3, background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}><CheckCircle size={10} /> Confirmed</span>}
                  </div>
                  <div style={{ display: "flex", gap: 16, fontSize: 12, color: "var(--hf-text-muted)", flexWrap: "wrap" }}>
                    {v.contactName && <span>{v.contactName}</span>}
                    {v.contactPhone && <span style={{ display: "flex", alignItems: "center", gap: 3 }}><Phone size={10} />{v.contactPhone}</span>}
                    {v.contactEmail && <span style={{ display: "flex", alignItems: "center", gap: 3 }}><Mail size={10} />{v.contactEmail}</span>}
                    {v.serviceDescription && <span style={{ color: "var(--hf-text-faint)" }}>{v.serviceDescription}</span>}
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 12, flexShrink: 0 }}>
                  {v.quotedAmount > 0 && <span style={{ fontWeight: 800, fontSize: 14, color: "var(--hf-warning-text)" }}>{fmtR(v.quotedAmount)}</span>}
                  {!v.confirmed && (
                    <button onClick={() => confirmVendor.mutate(v.id)}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <CheckCircle size={12} /> Confirm
                    </button>
                  )}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Budget summary */}
      {(vendors as any[]).length > 0 && totalQuoted > 0 && (
        <div style={{ marginTop: 20, padding: "16px 20px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 10 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div>
              <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-warning-text-deep)" }}>Budget summary</div>
              <div style={{ fontSize: 12, color: "var(--hf-warning-text-strong)", marginTop: 2 }}>
                {totalConfirmed} of {(vendors as any[]).length} vendors confirmed
              </div>
            </div>
            <div style={{ textAlign: "right" as const }}>
              <div style={{ fontSize: 20, fontWeight: 800, color: "var(--hf-warning-text)" }}>{fmtR(totalQuoted)}</div>
              <div style={{ fontSize: 11, color: "var(--hf-warning-text-strong)" }}>total quoted</div>
            </div>
          </div>
        </div>
      )}

      {/* Add vendor modal */}
      {showAdd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 560, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Add Vendor</h3>
              <button onClick={() => setShowAdd(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Vendor type *</label>
                <select value={form.vendorType} onChange={e => f("vendorType", e.target.value)} style={{ ...inp, background: "var(--hf-surface)" }}>
                  {VENDOR_TYPES.map(t => <option key={t} value={t}>{t.replace("_"," ")}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Company name *</label>
                <input autoFocus value={form.companyName} onChange={e => f("companyName", e.target.value)} placeholder="ABC Catering (Pty) Ltd" style={inp} />
              </div>
              <div>
                <label style={lbl}>Contact name</label>
                <input value={form.contactName} onChange={e => f("contactName", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Contact phone</label>
                <input value={form.contactPhone} onChange={e => f("contactPhone", e.target.value)} placeholder="+27 ..." style={inp} />
              </div>
              <div>
                <label style={lbl}>Contact email</label>
                <input type="email" value={form.contactEmail} onChange={e => f("contactEmail", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Quoted amount (R)</label>
                <input type="number" value={form.quotedAmount} onChange={e => f("quotedAmount", e.target.value)} placeholder="0.00" style={inp} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Service description</label>
                <textarea value={form.serviceDescription} onChange={e => f("serviceDescription", e.target.value)} rows={2} placeholder="3-course sit-down dinner for 200 guests" style={{ ...inp, resize: "vertical" as const }} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Notes</label>
                <input value={form.notes} onChange={e => f("notes", e.target.value)} style={inp} />
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAdd(false)} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!form.companyName || addVendor.isPending}
                onClick={() => addVendor.mutate({
                  vendorType: form.vendorType, companyName: form.companyName,
                  contactName: form.contactName || null, contactPhone: form.contactPhone || null,
                  contactEmail: form.contactEmail || null,
                  serviceDescription: form.serviceDescription || null,
                  quotedAmount: form.quotedAmount ? parseFloat(form.quotedAmount) : null,
                  notes: form.notes || null,
                })}
                style={{ padding: "9px 22px", background: "var(--hf-sky)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {addVendor.isPending ? "Adding..." : "Add Vendor"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
