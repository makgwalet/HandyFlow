import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Plus, X, ChevronRight, Calendar, MapPin, Users, Tag, BarChart2 } from "lucide-react"

interface Event {
  id: string
  eventNumber: string
  title: string
  description: string
  eventType: string
  status: string
  venueName: string
  venueAddress: string
  venueCapacity: number
  startDatetime: string
  endDatetime: string
  isFree: boolean
  isPrivate: boolean
  registrationDeadline: string | null
}

interface Tier {
  id: string
  name: string
  description: string
  price: number
  quantity: number
  quantitySold: number
  quantityCheckedIn: number
  available: number
  active: boolean
}

interface Stats {
  totalRegistered: number
  totalCheckedIn: number
  totalCancelled: number
  totalVendors: number
  confirmedVendors: number
}

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  DRAFT:     { color: "#64748B", bg: "#F8FAFC" },
  PUBLISHED: { color: "#1D4ED8", bg: "#EFF6FF" },
  LIVE:      { color: "#166534", bg: "#DCFCE7" },
  COMPLETED: { color: "#7C3AED", bg: "#F5F3FF" },
  CANCELLED: { color: "#DC2626", bg: "#FEF2F2" },
}

const EVENT_TYPES = ["CONFERENCE", "WORKSHOP", "CONCERT", "EXHIBITION", "NETWORKING", "WEBINAR", "FUNDRAISER", "SPORTS", "OTHER"]

const nextActions = (status: string) => {
  if (status === "DRAFT")     return [{ label: "Publish", action: "publish" }]
  if (status === "PUBLISHED") return [{ label: "Go Live", action: "go-live" }, { label: "Cancel", action: "cancel" }]
  if (status === "LIVE")      return [{ label: "Complete", action: "complete" }]
  return []
}

export default function EventsListTab({ onSelectEvent }: { onSelectEvent: (id: string, title: string) => void }) {
  const qc = useQueryClient()
  const [statusFilter, setStatus] = useState("")
  const [showCreate, setShowCreate] = useState(false)
  const [selected, setSelected]     = useState<Event | null>(null)
  const [showTier, setShowTier]     = useState(false)
  const [showStats, setShowStats]   = useState(false)
  const [error, setError]           = useState("")

  const initForm = () => ({
    title: "", description: "", eventType: "CONFERENCE",
    venueName: "", venueAddress: "", venueCapacity: "",
    startDatetime: "", endDatetime: "", registrationDeadline: "",
    isFree: false, isPrivate: false, notes: "",
  })
  const [form, setForm] = useState(initForm())
  const f = (k: keyof ReturnType<typeof initForm>, v: any) => setForm(p => ({ ...p, [k]: v }))

  const [tierForm, setTierForm] = useState({ name: "", description: "", price: "0", quantity: "100", saleStart: "", saleEnd: "" })

  const { data: page, isLoading } = useQuery({
    queryKey: ["events", statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "50" })
      if (statusFilter) params.set("status", statusFilter)
      const r = await apiClient.get(`/api/v1/events?${params}`)
      return r.data
    },
  })

  const { data: tiers = [] } = useQuery<Tier[]>({
    queryKey: ["event-tiers", selected?.id],
    queryFn: async () => {
      if (!selected?.id) return []
      const r = await apiClient.get(`/api/v1/events/${selected.id}/tiers`)
      return r.data || []
    },
    enabled: !!selected?.id,
  })

  const { data: stats } = useQuery<Stats>({
    queryKey: ["event-stats", selected?.id],
    queryFn: async () => {
      if (!selected?.id) return null
      const r = await apiClient.get(`/api/v1/events/${selected.id}/stats`)
      return r.data
    },
    enabled: !!selected?.id && showStats,
  })

  const createEvent = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/events", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["events"] }); setShowCreate(false); setForm(initForm()) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create event"),
  })

  const eventAction = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      apiClient.post(`/api/v1/events/${id}/${action}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["events"] }); setSelected(null) },
  })

  const createTier = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`/api/v1/events/${id}/tiers`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["event-tiers"] }); setShowTier(false) },
    onError: (e: any) => setError(e.response?.data?.message || "Failed to create tier"),
  })

  const events: Event[] = page?.content || []
  const fmtR = (n: number) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "Free"
  const fmtDT = (dt: string) => dt ? new Date(dt).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) : "—"

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, gap: 10, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {["", "DRAFT", "PUBLISHED", "LIVE", "COMPLETED", "CANCELLED"].map(s => (
            <button key={s} onClick={() => setStatus(s)} style={filterBtn(statusFilter === s)}>{s || "All"}</button>
          ))}
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }} style={btnPrimary}><Plus size={15} /> New Event</button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading events...</div>
      ) : events.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Calendar size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No events found</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Create your first event to get started.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {events.map(ev => {
            const style = STATUS_STYLE[ev.status] || { color: "#475569", bg: "#F8FAFC" }
            return (
              <div key={ev.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: "16px 20px", background: "#fff", cursor: "pointer" }}
                onClick={() => setSelected(ev)}
                onMouseEnter={e => (e.currentTarget.style.borderColor = "#0D9488")}
                onMouseLeave={e => (e.currentTarget.style.borderColor = "#E2E8F0")}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
                      <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{ev.title}</span>
                      <span style={{ background: style.bg, color: style.color, padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>{ev.status}</span>
                      <span style={{ background: "#F8FAFC", color: "#64748B", padding: "2px 8px", borderRadius: 20, fontSize: 11 }}>{ev.eventType}</span>
                      {ev.isFree && <span style={{ background: "#DCFCE7", color: "#166534", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 600 }}>FREE</span>}
                      {ev.isPrivate && <span style={{ background: "#FEF2F2", color: "#DC2626", padding: "2px 8px", borderRadius: 20, fontSize: 11 }}>PRIVATE</span>}
                    </div>
                    <div style={{ display: "flex", gap: 16, fontSize: 12, color: "#64748B", flexWrap: "wrap" }}>
                      <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Calendar size={11} />{fmtDT(ev.startDatetime)}</span>
                      {ev.venueName && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><MapPin size={11} />{ev.venueName}</span>}
                      {ev.venueCapacity && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Users size={11} />{ev.venueCapacity} capacity</span>}
                      <span style={{ fontSize: 11, color: "#94A3B8" }}>#{ev.eventNumber}</span>
                    </div>
                  </div>
                  <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    <button onClick={e => { e.stopPropagation(); onSelectEvent(ev.id, ev.title) }}
                      style={{ padding: "6px 12px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 6, fontSize: 12, cursor: "pointer" }}>
                      Guests
                    </button>
                    <ChevronRight size={16} color="#94A3B8" />
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Event detail modal */}
      {selected && !showTier && !showStats && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 600, maxHeight: "85vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
              <div>
                <h3 style={{ margin: "0 0 4px", fontSize: 18, fontWeight: 700, color: "#0F172A" }}>{selected.title}</h3>
                <div style={{ display: "flex", gap: 8 }}>
                  <span style={{ background: STATUS_STYLE[selected.status]?.bg, color: STATUS_STYLE[selected.status]?.color, padding: "2px 10px", borderRadius: 20, fontSize: 12, fontWeight: 600 }}>{selected.status}</span>
                  <span style={{ fontSize: 12, color: "#94A3B8" }}>#{selected.eventNumber}</span>
                </div>
              </div>
              <button onClick={() => setSelected(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            {selected.description && (
              <p style={{ fontSize: 13, color: "#475569", marginBottom: 16, lineHeight: 1.6 }}>{selected.description}</p>
            )}

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 20 }}>
              {[
                ["Start", fmtDT(selected.startDatetime)],
                ["End", fmtDT(selected.endDatetime)],
                ["Venue", selected.venueName],
                ["Capacity", selected.venueCapacity ? `${selected.venueCapacity} people` : "—"],
                ["Type", selected.eventType],
                ["Registration deadline", selected.registrationDeadline ? fmtDT(selected.registrationDeadline) : "None"],
              ].filter(([, v]) => v && v !== "—").map(([label, value]) => (
                <div key={label as string}>
                  <div style={{ fontSize: 10, fontWeight: 600, color: "#94A3B8", marginBottom: 2 }}>{(label as string).toUpperCase()}</div>
                  <div style={{ fontSize: 13, color: "#0F172A" }}>{value as string}</div>
                </div>
              ))}
            </div>

            {/* Ticket tiers */}
            <div style={{ marginBottom: 16 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: "#374151" }}>TICKET TIERS</span>
                <button onClick={() => setShowTier(true)} style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 6, fontSize: 12, cursor: "pointer" }}>
                  <Plus size={11} /> Add Tier
                </button>
              </div>
              {tiers.length === 0 ? (
                <div style={{ fontSize: 13, color: "#94A3B8", padding: "10px 0" }}>No ticket tiers yet.</div>
              ) : (
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  {tiers.map(tier => (
                    <div key={tier.id} style={{ border: "1px solid #E2E8F0", borderRadius: 8, padding: "10px 14px", minWidth: 130 }}>
                      <div style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{tier.name}</div>
                      <div style={{ fontSize: 20, fontWeight: 700, color: "#0D9488", margin: "4px 0" }}>
                        {Number(tier.price) === 0 ? "Free" : fmtR(tier.price)}
                      </div>
                      <div style={{ fontSize: 11, color: "#64748B" }}>{tier.quantitySold} / {tier.quantity} sold</div>
                      <div style={{ fontSize: 11, color: "#0D9488" }}>{tier.available} available</div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ display: "flex", gap: 8 }}>
                <button onClick={() => { setShowStats(true) }}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12, cursor: "pointer" }}>
                  <BarChart2 size={13} /> Stats
                </button>
                <button onClick={() => onSelectEvent(selected.id, selected.title)}
                  style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 14px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 7, fontSize: 12, cursor: "pointer" }}>
                  <Users size={13} /> Manage Guests
                </button>
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                {nextActions(selected.status).map(({ label, action }) => (
                  <button key={action} onClick={() => eventAction.mutate({ id: selected.id, action })}
                    style={{
                      padding: "8px 16px", borderRadius: 7, fontSize: 13, cursor: "pointer", border: "none", fontWeight: 500,
                      background: action === "cancel" ? "#FEF2F2" : action === "complete" ? "#DCFCE7" : action === "go-live" ? "#DCFCE7" : "#EFF6FF",
                      color: action === "cancel" ? "#DC2626" : action === "complete" ? "#166534" : action === "go-live" ? "#166534" : "#1D4ED8",
                    }}>
                    {label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Stats modal */}
      {showStats && selected && stats && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1001 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Event Stats</h3>
              <button onClick={() => setShowStats(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              {[
                ["Registered", stats.totalRegistered, "#1D4ED8", "#EFF6FF"],
                ["Checked In", stats.totalCheckedIn, "#166534", "#DCFCE7"],
                ["Cancelled", stats.totalCancelled, "#DC2626", "#FEF2F2"],
                ["Vendors", stats.totalVendors, "#7C3AED", "#F5F3FF"],
              ].map(([label, val, color, bg]) => (
                <div key={label as string} style={{ background: bg as string, borderRadius: 10, padding: "14px 18px" }}>
                  <div style={{ fontSize: 11, fontWeight: 600, color: color as string, marginBottom: 4 }}>{label as string}</div>
                  <div style={{ fontSize: 28, fontWeight: 700, color: color as string }}>{val as number}</div>
                </div>
              ))}
            </div>
            {stats.totalRegistered > 0 && (
              <div style={{ marginTop: 16, padding: "10px 14px", background: "#F8FAFC", borderRadius: 8 }}>
                <div style={{ fontSize: 12, color: "#64748B" }}>Check-in rate</div>
                <div style={{ fontSize: 22, fontWeight: 700, color: "#0D9488" }}>
                  {Math.round((stats.totalCheckedIn / stats.totalRegistered) * 100)}%
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Add tier modal */}
      {showTier && selected && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1001 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 440, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>Add Ticket Tier</h3>
              <button onClick={() => setShowTier(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Tier Name *"><input value={tierForm.name} onChange={e => setTierForm(f => ({ ...f, name: e.target.value }))} placeholder="General, VIP, Early Bird..." style={inputStyle} /></Field>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Description"><input value={tierForm.description} onChange={e => setTierForm(f => ({ ...f, description: e.target.value }))} placeholder="Optional description" style={inputStyle} /></Field>
              </div>
              <Field label="Price (R)"><input type="number" value={tierForm.price} onChange={e => setTierForm(f => ({ ...f, price: e.target.value }))} placeholder="0" style={inputStyle} /></Field>
              <Field label="Quantity"><input type="number" value={tierForm.quantity} onChange={e => setTierForm(f => ({ ...f, quantity: e.target.value }))} placeholder="100" style={inputStyle} /></Field>
              <Field label="Sale Start"><input type="datetime-local" value={tierForm.saleStart} onChange={e => setTierForm(f => ({ ...f, saleStart: e.target.value }))} style={inputStyle} /></Field>
              <Field label="Sale End"><input type="datetime-local" value={tierForm.saleEnd} onChange={e => setTierForm(f => ({ ...f, saleEnd: e.target.value }))} style={inputStyle} /></Field>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowTier(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createTier.mutate({ id: selected.id, body: { ...tierForm, price: parseFloat(tierForm.price) || 0, quantity: parseInt(tierForm.quantity) || 100, saleStart: tierForm.saleStart || null, saleEnd: tierForm.saleEnd || null } })}
                disabled={!tierForm.name || createTier.isPending} style={btnPrimary}>
                {createTier.isPending ? "Creating..." : "Create Tier"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create event modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 620, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>New Event</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Title *"><input value={form.title} onChange={e => f("title", e.target.value)} placeholder="Annual Industry Conference 2026" style={inputStyle} /></Field>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Description"><textarea value={form.description} onChange={e => f("description", e.target.value)} rows={2} placeholder="Event description..." style={{ ...inputStyle, resize: "vertical" as const }} /></Field>
              </div>
              <Field label="Event Type">
                <select value={form.eventType} onChange={e => f("eventType", e.target.value)} style={inputStyle}>
                  {EVENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </Field>
              <Field label="Venue Capacity"><input type="number" value={form.venueCapacity} onChange={e => f("venueCapacity", e.target.value)} placeholder="500" style={inputStyle} /></Field>
              <Field label="Start Date & Time *"><input type="datetime-local" value={form.startDatetime} onChange={e => f("startDatetime", e.target.value)} style={inputStyle} /></Field>
              <Field label="End Date & Time *"><input type="datetime-local" value={form.endDatetime} onChange={e => f("endDatetime", e.target.value)} style={inputStyle} /></Field>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Venue Name"><input value={form.venueName} onChange={e => f("venueName", e.target.value)} placeholder="Sandton Convention Centre" style={inputStyle} /></Field>
              </div>
              <div style={{ gridColumn: "1 / -1" }}>
                <Field label="Venue Address"><input value={form.venueAddress} onChange={e => f("venueAddress", e.target.value)} placeholder="161 Maude Street, Sandton" style={inputStyle} /></Field>
              </div>
              <Field label="Registration Deadline"><input type="datetime-local" value={form.registrationDeadline} onChange={e => f("registrationDeadline", e.target.value)} style={inputStyle} /></Field>
              <div style={{ display: "flex", gap: 20, alignItems: "center", paddingTop: 24 }}>
                <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, cursor: "pointer" }}>
                  <input type="checkbox" checked={form.isFree} onChange={e => f("isFree", e.target.checked)} /> Free event
                </label>
                <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, cursor: "pointer" }}>
                  <input type="checkbox" checked={form.isPrivate} onChange={e => f("isPrivate", e.target.checked)} /> Private
                </label>
              </div>
            </div>
            {error && <div style={{ marginTop: 10, color: "#DC2626", fontSize: 13 }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createEvent.mutate({ ...form, venueCapacity: parseInt(form.venueCapacity) || null, startDatetime: form.startDatetime, endDatetime: form.endDatetime, registrationDeadline: form.registrationDeadline || null })}
                disabled={!form.title || !form.startDatetime || !form.endDatetime || createEvent.isPending} style={btnPrimary}>
                {createEvent.isPending ? "Creating..." : "Create Event"}
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
const filterBtn = (active: boolean): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 6, fontSize: 12, cursor: "pointer",
  border: active ? "1px solid #0D9488" : "1px solid #E2E8F0",
  background: active ? "#F0FDF4" : "#fff", color: active ? "#0D9488" : "#64748B", fontWeight: active ? 600 : 400,
})
const btnPrimary: React.CSSProperties = { display: "flex", alignItems: "center", gap: 7, background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 500, cursor: "pointer" }
const btnCancel: React.CSSProperties  = { padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, background: "#fff" }
