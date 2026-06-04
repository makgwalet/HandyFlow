// src/pages/events/EventsListTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import {
  Plus, X, Calendar, MapPin, Users, Clock, ChevronRight,
  Radio, CheckCircle, XCircle, Eye, Megaphone, Search, AlertTriangle,
} from "lucide-react"

const fmtDT = (d: any) => d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "—"
const fmtD  = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }

const STATUS_CFG: Record<string, { label: string; color: string; bg: string; dot: string }> = {
  DRAFT:     { label: "Draft",     color: "#64748B", bg: "#F1F5F9", dot: "#94A3B8" },
  PUBLISHED: { label: "Published", color: "#166534", bg: "#DCFCE7", dot: "#22C55E" },
  SOLD_OUT:  { label: "Sold out",  color: "#D97706", bg: "#FFFBEB", dot: "#F59E0B" },
  LIVE:      { label: "Live",      color: "#DC2626", bg: "#FEF2F2", dot: "#EF4444" },
  COMPLETED: { label: "Completed", color: "#0284C7", bg: "#E0F2FE", dot: "#38BDF8" },
  CANCELLED: { label: "Cancelled", color: "#94A3B8", bg: "#F8FAFC", dot: "#CBD5E1" },
}
const EVENT_TYPES = ["CONFERENCE","WEDDING","CHURCH","FESTIVAL","CORPORATE","COMMUNITY","FUNDRAISER","GENERAL"]
const TYPE_COLOR:  Record<string, string> = {
  CONFERENCE: "#1D4ED8", WEDDING: "#BE185D", CHURCH: "#7C3AED",
  FESTIVAL: "#D97706", CORPORATE: "#0284C7", COMMUNITY: "#16A34A",
  FUNDRAISER: "#EA580C", GENERAL: "#64748B",
}

interface Props { onSelectEvent: (id: string, title: string) => void }

export default function EventsListTab({ onSelectEvent }: Props) {
  const qc = useQueryClient()
  const [filterStatus, setFilterStatus] = useState("ALL")
  const [filterType,   setFilterType]   = useState("ALL")
  const [search,       setSearch]       = useState("")
  const [showCreate,   setShowCreate]   = useState(false)
  const [error,        setError]        = useState("")
  const [cancelConfirm, setCancelConfirm] = useState<{ id: string; title: string } | null>(null)

  const INIT = () => ({
    title: "", description: "", eventType: "GENERAL",
    venueName: "", venueAddress: "", venueCapacity: "",
    startDatetime: "", endDatetime: "", isFree: true, isPrivate: false,
    registrationDeadline: "", notes: "",
  })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const { data: events = [], isLoading } = useQuery<any[]>({
    queryKey: ["events", filterStatus, filterType],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "100" })
      if (filterStatus !== "ALL") params.set("status", filterStatus)
      if (filterType   !== "ALL") params.set("type", filterType)
      const r = await apiClient.get(`/api/v1/events?${params}`)
      const p = r.data?.data ?? r.data
      return p?.content ?? p ?? []
    },
  })

  const createEvent = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/events", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["events"] }); qc.invalidateQueries({ queryKey: ["events-kpi"] }); setShowCreate(false); setForm(INIT()); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create event"),
  })

  const transition = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      apiClient.post(`/api/v1/events/${id}/${action}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["events"] }); qc.invalidateQueries({ queryKey: ["events-kpi"] }) },
    onError: (e: any) => alert(e.response?.data?.message ?? "Action failed"),
  })

  const filtered = (events as any[]).filter(e => {
    if (search && !e.title?.toLowerCase().includes(search.toLowerCase()) &&
        !e.venueName?.toLowerCase().includes(search.toLowerCase())) return false
    return true
  })

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <div style={{ position: "relative" as const }}>
            <Search size={13} style={{ position: "absolute" as const, left: 9, top: "50%", transform: "translateY(-50%)", color: "#94A3B8" }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search events..."
              style={{ paddingLeft: 28, padding: "7px 10px 7px 28px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", width: 220 }} />
          </div>
          <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
            <option value="ALL">All statuses</option>
            {Object.entries(STATUS_CFG).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
          </select>
          <select value={filterType} onChange={e => setFilterType(e.target.value)}
            style={{ padding: "7px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, outline: "none", background: "#fff" }}>
            <option value="ALL">All types</option>
            {EVENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        <button onClick={() => { setShowCreate(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "#0284C7", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> New Event
        </button>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading events...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Calendar size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No events yet</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Create your first event to start managing registrations.</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {filtered.map((e: any) => {
            const sc  = STATUS_CFG[e.status] ?? STATUS_CFG.DRAFT
            const tc  = TYPE_COLOR[e.eventType] ?? "#64748B"
            const isLive = e.status === "LIVE"
            return (
              <div key={e.id} style={{
                border: `1px solid ${isLive ? "#FECACA" : "#E2E8F0"}`,
                borderLeft: `3px solid ${isLive ? "#EF4444" : tc}`,
                borderRadius: 10, padding: "16px 20px", background: isLive ? "#FFFAFA" : "#fff",
                display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, flexWrap: "wrap",
              }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6, flexWrap: "wrap" }}>
                    {isLive && <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                      <Radio size={12} color="#EF4444" /><span style={{ fontSize: 11, color: "#DC2626", fontWeight: 700 }}>LIVE</span>
                    </span>}
                    <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{e.title}</span>
                    <span style={{ fontFamily: "monospace", fontSize: 11, color: "#94A3B8" }}>{e.eventNumber}</span>
                    <span style={{ background: `${tc}18`, color: tc, padding: "1px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{e.eventType}</span>
                    <span style={{ background: sc.bg, color: sc.color, padding: "1px 7px", borderRadius: 20, fontSize: 11, fontWeight: 700, display: "flex", alignItems: "center", gap: 4 }}>
                      <span style={{ width: 6, height: 6, borderRadius: "50%", background: sc.dot, display: "inline-block" }} />{sc.label}
                    </span>
                    {e.isFree && <span style={{ background: "#F0FDF4", color: "#166534", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>Free</span>}
                    {e.isPrivate && <span style={{ background: "#FDF4FF", color: "#9333EA", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>Private</span>}
                  </div>
                  <div style={{ display: "flex", gap: 16, fontSize: 12, color: "#64748B", flexWrap: "wrap" }}>
                    <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Calendar size={11} />{fmtDT(e.startDatetime)}</span>
                    {e.venueName && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><MapPin size={11} />{e.venueName}</span>}
                    {e.venueCapacity && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Users size={11} />Cap: {e.venueCapacity}</span>}
                    {e.registrationDeadline && <span style={{ display: "flex", alignItems: "center", gap: 4 }}><Clock size={11} />Reg closes: {fmtD(e.registrationDeadline)}</span>}
                  </div>
                </div>

                {/* Actions */}
                <div style={{ display: "flex", gap: 7, flexShrink: 0, flexWrap: "wrap" }}>
                  {e.status === "DRAFT" && (
                    <button onClick={() => transition.mutate({ id: e.id, action: "publish" })}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#DCFCE7", color: "#166534", border: "1px solid #86EFAC", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <Megaphone size={12} /> Publish
                    </button>
                  )}
                  {e.status === "PUBLISHED" && (
                    <button onClick={() => transition.mutate({ id: e.id, action: "go-live" })}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#FEF2F2", color: "#DC2626", border: "1px solid #FECACA", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <Radio size={12} /> Go Live
                    </button>
                  )}
                  {e.status === "LIVE" && (
                    <button onClick={() => transition.mutate({ id: e.id, action: "complete" })}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#E0F2FE", color: "#0284C7", border: "1px solid #BAE6FD", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                      <CheckCircle size={12} /> Complete
                    </button>
                  )}
                  {["DRAFT","PUBLISHED","SOLD_OUT"].includes(e.status) && (
                    <button onClick={() => setCancelConfirm({ id: e.id, title: e.title })}
                      style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#F8FAFC", color: "#94A3B8", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12, cursor: "pointer" }}>
                      <XCircle size={12} /> Cancel
                    </button>
                  )}
                  <button onClick={() => onSelectEvent(e.id, e.title)}
                    style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "#0284C7", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                    <Eye size={12} /> Guests <ChevronRight size={12} />
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Create event modal */}
      {showCreate && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 700, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Create Event</h3>
                <p style={{ margin: "3px 0 0", fontSize: 13, color: "#64748B" }}>Fill in the details — you can edit everything before publishing</p>
              </div>
              <button onClick={() => setShowCreate(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Event title *</label>
                <input autoFocus value={form.title} onChange={e => f("title", e.target.value)} placeholder="Annual Business Summit 2026" style={inp} />
              </div>
              <div>
                <label style={lbl}>Event type *</label>
                <select value={form.eventType} onChange={e => f("eventType", e.target.value)} style={{ ...inp, background: "#fff" }}>
                  {EVENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Venue capacity</label>
                <input type="number" value={form.venueCapacity} onChange={e => f("venueCapacity", e.target.value)} placeholder="500" style={inp} />
              </div>
              <div>
                <label style={lbl}>Venue name</label>
                <input value={form.venueName} onChange={e => f("venueName", e.target.value)} placeholder="Sandton Convention Centre" style={inp} />
              </div>
              <div>
                <label style={lbl}>Venue address</label>
                <input value={form.venueAddress} onChange={e => f("venueAddress", e.target.value)} placeholder="161 Maude St, Sandton" style={inp} />
              </div>
              <div>
                <label style={lbl}>Start date & time *</label>
                <input type="datetime-local" value={form.startDatetime} onChange={e => f("startDatetime", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>End date & time *</label>
                <input type="datetime-local" value={form.endDatetime} onChange={e => f("endDatetime", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Registration deadline</label>
                <input type="datetime-local" value={form.registrationDeadline} onChange={e => f("registrationDeadline", e.target.value)} style={inp} />
              </div>
              <div style={{ display: "flex", gap: 20, alignItems: "center", paddingTop: 26 }}>
                <label style={{ display: "flex", alignItems: "center", gap: 7, cursor: "pointer", fontSize: 13 }}>
                  <input type="checkbox" checked={form.isFree} onChange={e => f("isFree", e.target.checked)} style={{ width: 15, height: 15 }} />
                  Free event
                </label>
                <label style={{ display: "flex", alignItems: "center", gap: 7, cursor: "pointer", fontSize: 13 }}>
                  <input type="checkbox" checked={form.isPrivate} onChange={e => f("isPrivate", e.target.checked)} style={{ width: 15, height: 15 }} />
                  Private (invite only)
                </label>
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Description</label>
                <textarea value={form.description} onChange={e => f("description", e.target.value)} rows={3} placeholder="What's this event about?" style={{ ...inp, resize: "vertical" as const }} />
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Internal notes</label>
                <textarea value={form.notes} onChange={e => f("notes", e.target.value)} rows={2} style={{ ...inp, resize: "vertical" as const }} />
              </div>
            </div>

            {/* Ticket tiers notice */}
            <div style={{ marginTop: 16, padding: "12px 14px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 8, fontSize: 12, color: "#1D4ED8" }}>
              Ticket tiers (Early Bird, VIP, General) can be added after creating the event — from the Guests tab.
            </div>

            {error && <div style={{ marginTop: 12, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}

            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 22 }}>
              <button onClick={() => setShowCreate(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button
                disabled={!form.title || !form.startDatetime || !form.endDatetime || createEvent.isPending}
                onClick={() => createEvent.mutate({
                  ...form,
                  venueCapacity: form.venueCapacity ? parseInt(form.venueCapacity) : null,
                  registrationDeadline: form.registrationDeadline || null,
                  description: form.description || null,
                  notes: form.notes || null,
                  venueName: form.venueName || null,
                  venueAddress: form.venueAddress || null,
                })}
                style={{ padding: "9px 22px", background: !form.title ? "#94A3B8" : "#0284C7", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createEvent.isPending ? "Creating..." : "Create Event"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Custom cancel-event confirmation modal */}
      {cancelConfirm && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.55)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 2000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 14, padding: 28, width: 400, boxShadow: "0 20px 60px rgba(0,0,0,0.22)" }}>
            <div style={{ display: "flex", alignItems: "flex-start", gap: 14, marginBottom: 22 }}>
              <div style={{ width: 42, height: 42, borderRadius: "50%", background: "#FEF2F2", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, marginTop: 2 }}>
                <AlertTriangle size={20} color="#DC2626" />
              </div>
              <div>
                <div style={{ fontWeight: 700, fontSize: 16, color: "#0F172A", marginBottom: 6 }}>Cancel event?</div>
                <div style={{ fontSize: 13, color: "#64748B", lineHeight: 1.6 }}>
                  <strong style={{ color: "#0F172A" }}>{cancelConfirm.title}</strong> will be marked as cancelled.
                  All registered guests will lose their spots. This action cannot be undone.
                </div>
              </div>
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button
                onClick={() => setCancelConfirm(null)}
                style={{ padding: "9px 20px", border: "1px solid #E2E8F0", borderRadius: 8, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151", fontWeight: 500 }}>
                Keep event
              </button>
              <button
                onClick={() => { transition.mutate({ id: cancelConfirm.id, action: "cancel" }); setCancelConfirm(null) }}
                style={{ padding: "9px 20px", background: "#DC2626", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                Cancel event
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
