// src/pages/training/TrainingSessionsTab.tsx
//
// Session list + create — confirmed via TrainingSessionController:
// GET/POST /api/v1/training/sessions, CreateSessionRequest(courseId,
// startDate, endDate, venue, trainerName, capacity, notes).
// SessionResponse.enrolledCount is a live count, not stored on the entity.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Plus, X, ChevronRight, CalendarDays, Users } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINING_ACCENT } from "./constants"
import TrainingSessionDetail from "./TrainingSessionDetail"
import type { CourseResponse } from "./TrainingCoursesTab"

export interface SessionResponse {
  id: string; courseId: string; courseTitle: string; startDate: string; endDate: string
  venue: string | null; trainerName: string | null; capacity: number | null; enrolledCount: number
  status: "SCHEDULED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED"; notes: string | null; cancelReason: string | null; createdAt: string
}
interface SessionPage { content: SessionResponse[]; totalElements: number }
interface CoursePage { content: CourseResponse[] }

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }

const STATUS_COLORS: Record<string, string> = { SCHEDULED: "#0369A1", IN_PROGRESS: "#D97706", COMPLETED: "#059669", CANCELLED: "#94A3B8" }

function CreateSessionModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const { data: coursesData } = useQuery<CoursePage>({
    queryKey: ["training-courses", "ACTIVE", "for-session-create"],
    queryFn: async () => (await apiClient.get("/api/v1/training/courses", { params: { status: "ACTIVE", size: 200 } })).data,
  })
  const courses = coursesData?.content ?? []

  const [form, setForm] = useState({ courseId: "", startDate: "", endDate: "", venue: "", trainerName: "", capacity: "", notes: "" })

  const save = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/training/sessions", {
      courseId: form.courseId, startDate: form.startDate, endDate: form.endDate,
      venue: form.venue || null, trainerName: form.trainerName || null,
      capacity: form.capacity.trim() === "" ? null : parseInt(form.capacity, 10), notes: form.notes || null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["training-sessions"] }); onClose() },
  })

  const valid = form.courseId && form.startDate && form.endDate

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
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Start date *</label><input type="date" style={inputStyle} value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} /></div>
            <div><label style={labelStyle}>End date *</label><input type="date" style={inputStyle} value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} /></div>
          </div>
          <div><label style={labelStyle}>Venue</label><input style={inputStyle} value={form.venue} onChange={e => setForm({ ...form, venue: e.target.value })} /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Trainer (blank = course default)</label><input style={inputStyle} value={form.trainerName} onChange={e => setForm({ ...form, trainerName: e.target.value })} /></div>
            <div><label style={labelStyle}>Capacity (blank = unlimited)</label><input type="number" style={inputStyle} value={form.capacity} onChange={e => setForm({ ...form, capacity: e.target.value })} /></div>
          </div>
          <div><label style={labelStyle}>Notes</label><textarea style={{ ...inputStyle, minHeight: 50, resize: "vertical" }} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></div>
        </div>

        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not schedule this session"}</p>}

        <button onClick={() => save.mutate()} disabled={!valid || save.isPending}
          style={{ marginTop: 18, width: "100%", padding: "11px", borderRadius: 8, border: "none", background: TRAINING_ACCENT, color: "#fff", fontSize: 13.5, fontWeight: 700, cursor: "pointer", opacity: (!valid || save.isPending) ? 0.6 : 1 }}>
          {save.isPending ? "Scheduling…" : "Schedule session"}
        </button>
      </div>
    </div>
  )
}

export default function TrainingSessionsTab() {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [statusFilter, setStatusFilter] = useState<string>("")

  const { data, isLoading } = useQuery<SessionPage>({
    queryKey: ["training-sessions", statusFilter],
    queryFn: async () => (await apiClient.get("/api/v1/training/sessions", { params: { status: statusFilter || undefined, size: 100 } })).data,
  })
  const sessions = data?.content ?? []

  if (selectedId) {
    return <TrainingSessionDetail sessionId={selectedId} onBack={() => setSelectedId(null)} />
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
          style={{ display: "flex", alignItems: "center", gap: 6, background: TRAINING_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
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
                <div style={{ width: 32, height: 32, borderRadius: 8, background: "#F0FDF4", display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <CalendarDays size={15} color={TRAINING_ACCENT} />
                </div>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <p style={{ fontSize: 13.5, fontWeight: 700, color: "#0F172A", margin: 0 }}>{s.courseTitle}</p>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: `${STATUS_COLORS[s.status]}18`, color: STATUS_COLORS[s.status] }}>{s.status.replace("_", " ")}</span>
                  </div>
                  <p style={{ fontSize: 11.5, color: "#94A3B8", margin: 0 }}>
                    {s.startDate} → {s.endDate} {s.venue ? `· ${s.venue}` : ""} {s.trainerName ? `· ${s.trainerName}` : ""}
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
