// src/pages/trainingprovider/TrainProvSessionsTab.tsx
//
// Global session list + create — confirmed via TrainProvSessionController:
// GET/POST /api/v1/training-provider/sessions.
// CreateSessionRequest(courseId, sessionType, clientId, startDate,
// endDate, venue, trainerName, capacity, notes) — sessionType is
// @NotBlank String (PUBLIC|CLOSED); clientId is required iff CLOSED,
// enforced at the entity AND with a DB CHECK constraint
// (ck_trainprov_session_client_matches_type in the migration), so this
// form mirrors that exact rule client-side rather than letting a bad
// combination reach the server.
//
// Client picker uses GET /clients?status=ACTIVE&size=200 — there is no
// unpaginated "/clients/all" endpoint on this controller (see
// TrainProvClientsTab.tsx's header note for the confirmed absence).
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, ChevronRight, CalendarDays, Users, Globe, Lock } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"
import TrainProvSessionDetail from "./TrainProvSessionDetail"
import type { CourseResponse } from "./TrainProvCoursesTab"

export interface SessionResponse {
  id: string; courseId: string; courseTitle: string; sessionType: "PUBLIC" | "CLOSED"
  clientId: string | null; clientName: string | null; startDate: string; endDate: string
  venue: string | null; trainerName: string | null; capacity: number | null; enrolledCount: number
  status: "SCHEDULED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED"; notes: string | null; cancelReason: string | null; createdAt: string
}
interface SessionPage { content: SessionResponse[]; totalElements: number }
interface CoursePage { content: CourseResponse[] }
interface ClientOption { id: string; tradingName: string; status: string }
interface ClientPage { content: ClientOption[] }

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }
const STATUS_COLORS: Record<string, string> = { SCHEDULED: "#0369A1", IN_PROGRESS: "#D97706", COMPLETED: "#059669", CANCELLED: "#94A3B8" }

function CreateSessionModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const { data: coursesData } = useQuery<CoursePage>({
    queryKey: ["trainprov-courses", "ACTIVE", "for-session-create"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/courses", { params: { status: "ACTIVE", size: 200 } })).data,
  })
  const courses = coursesData?.content ?? []
  const { data: clientsData } = useQuery<ClientPage>({
    queryKey: ["trainprov-clients", "ACTIVE", "for-session-create"],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/clients", { params: { status: "ACTIVE", size: 200 } })).data,
  })
  const clients = clientsData?.content ?? []

  const [form, setForm] = useState({
    courseId: "", sessionType: "PUBLIC" as "PUBLIC" | "CLOSED", clientId: "",
    startDate: "", endDate: "", venue: "", trainerName: "", capacity: "", notes: "",
  })

  const save = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/training-provider/sessions", {
      courseId: form.courseId, sessionType: form.sessionType,
      clientId: form.sessionType === "CLOSED" ? form.clientId : null,
      startDate: form.startDate, endDate: form.endDate,
      venue: form.venue || null, trainerName: form.trainerName || null,
      capacity: form.capacity.trim() === "" ? null : parseInt(form.capacity, 10), notes: form.notes || null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["trainprov-sessions"] }); onClose() },
  })

  const valid = form.courseId && form.startDate && form.endDate && (form.sessionType === "PUBLIC" || !!form.clientId)

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 480, maxHeight: "85vh", overflowY: "auto" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
          <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>Schedule a session</p>
          <button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={18} color="#94A3B8" /></button>
        </div>
        <div style={{ display: "grid", gap: 12 }}>
          <div>
            <label style={labelStyle}>Course *</label>
            <select style={inputStyle} value={form.courseId} onChange={e => setForm({ ...form, courseId: e.target.value })}>
              <option value="">Select a course…</option>
              {courses.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}
            </select>
          </div>
          <div>
            <label style={labelStyle}>Session type *</label>
            <div style={{ display: "flex", gap: 10 }}>
              <button type="button" onClick={() => setForm({ ...form, sessionType: "PUBLIC", clientId: "" })}
                style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", borderRadius: 8, border: form.sessionType === "PUBLIC" ? `1.5px solid ${TRAINPROV_ACCENT}` : "1px solid #E2E8F0", background: form.sessionType === "PUBLIC" ? "#FFFBEB" : "#fff", color: form.sessionType === "PUBLIC" ? TRAINPROV_ACCENT : "#64748B", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
                <Globe size={14} /> Public
              </button>
              <button type="button" onClick={() => setForm({ ...form, sessionType: "CLOSED" })}
                style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", gap: 6, padding: "9px", borderRadius: 8, border: form.sessionType === "CLOSED" ? `1.5px solid ${TRAINPROV_ACCENT}` : "1px solid #E2E8F0", background: form.sessionType === "CLOSED" ? "#FFFBEB" : "#fff", color: form.sessionType === "CLOSED" ? TRAINPROV_ACCENT : "#64748B", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>
                <Lock size={14} /> Closed
              </button>
            </div>
            <p style={{ fontSize: 11, color: "#94A3B8", margin: "5px 0 0" }}>
              {form.sessionType === "PUBLIC" ? "Open to delegates from any client." : "Reserved for one client's own delegates."}
            </p>
          </div>
          {form.sessionType === "CLOSED" && (
            <div>
              <label style={labelStyle}>Client *</label>
              <select style={inputStyle} value={form.clientId} onChange={e => setForm({ ...form, clientId: e.target.value })}>
                <option value="">Select a client…</option>
                {clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
              </select>
            </div>
          )}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Start date *</label><input type="date" style={inputStyle} value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} /></div>
            <div><label style={labelStyle}>End date *</label><input type="date" style={inputStyle} value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} /></div>
          </div>
          <div><label style={labelStyle}>Venue</label><input style={inputStyle} value={form.venue} onChange={e => setForm({ ...form, venue: e.target.value })} /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Trainer</label><input style={inputStyle} value={form.trainerName} onChange={e => setForm({ ...form, trainerName: e.target.value })} /></div>
            <div><label style={labelStyle}>Capacity (blank = unlimited)</label><input type="number" style={inputStyle} value={form.capacity} onChange={e => setForm({ ...form, capacity: e.target.value })} /></div>
          </div>
          <div><label style={labelStyle}>Notes</label><textarea style={{ ...inputStyle, minHeight: 50, resize: "vertical" }} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></div>
        </div>

        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not schedule this session"}</p>}

        <button onClick={() => save.mutate()} disabled={!valid || save.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: TRAINPROV_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!valid || save.isPending) ? 0.6 : 1 }}>
          {save.isPending ? "Scheduling…" : "Schedule session"}
        </button>
      </div>
    </div>
  )
}

export default function TrainProvSessionsTab() {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [statusFilter, setStatusFilter] = useState<string>("")

  const { data, isLoading } = useQuery<SessionPage>({
    queryKey: ["trainprov-sessions", statusFilter],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/sessions", { params: { status: statusFilter || undefined, size: 100 } })).data,
  })
  const sessions = data?.content ?? []

  if (selectedId) {
    return <TrainProvSessionDetail sessionId={selectedId} onBack={() => setSelectedId(null)} />
  }

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{sessions.length} session{sessions.length === 1 ? "" : "s"}</p>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={{ ...inputStyle, width: "auto", padding: "5px 10px", fontSize: 12 }}>
            <option value="">All statuses</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>
        <button onClick={() => setShowForm(true)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: TRAINPROV_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> Schedule session
        </button>
      </div>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : sessions.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No sessions scheduled yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {sessions.map((s, i) => (
            <button key={s.id} onClick={() => setSelectedId(s.id)}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%", textAlign: "left", padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9", background: "none", border: "none", cursor: "pointer" }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: "#FFFBEB", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <CalendarDays size={15} color={TRAINPROV_ACCENT} />
                </div>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <p style={{ fontSize: 13.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{s.courseTitle}</p>
                    <span style={{ display: "flex", alignItems: "center", gap: 3, fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: s.sessionType === "PUBLIC" ? "#EFF6FF" : "#F5F3FF", color: s.sessionType === "PUBLIC" ? "#1D4ED8" : "#6D28D9" }}>
                      {s.sessionType === "PUBLIC" ? <Globe size={10} /> : <Lock size={10} />} {s.sessionType}
                    </span>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: `${STATUS_COLORS[s.status]}18`, color: STATUS_COLORS[s.status] }}>{s.status.replace("_", " ")}</span>
                  </div>
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>
                    {s.startDate} → {s.endDate} {s.venue ? `· ${s.venue}` : ""} {s.clientName ? `· ${s.clientName}` : ""}
                  </p>
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#64748B" }}>
                  <Users size={13} /> {s.enrolledCount}{s.capacity != null ? ` / ${s.capacity}` : ""}
                </span>
                <ChevronRight size={16} color="#CBD5E1" />
              </div>
            </button>
          ))}
        </div>
      )}

      {showForm && <CreateSessionModal onClose={() => setShowForm(false)} />}
    </div>
  )
}
