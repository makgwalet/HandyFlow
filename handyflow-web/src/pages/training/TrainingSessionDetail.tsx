// src/pages/training/TrainingSessionDetail.tsx
//
// Session detail: info + reschedule/update, start/complete/cancel
// lifecycle, enrollment roster (enrol/attended/no-show/complete/cancel),
// evidence (attendance registers, materials, external invoices), and
// the attendance-register PDF download. All endpoints confirmed via
// TrainingSessionController / TrainingEnrollmentController /
// TrainingCertificateController.
//
// Certificate issuance is surfaced here (on a COMPLETED, passed
// enrollment for a certificationOffered course) rather than only on
// the Certificates tab, since issuing is a per-enrollment action —
// POST /api/v1/training/enrollments/{id}/certificate, ADMIN-only
// server-side.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, Play, CheckCircle2, XCircle, Pencil, Download, Upload, UserPlus, Award, Check, X as XIcon } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINING_ACCENT } from "./constants"
import EmployeePicker, { type EmployeeOption } from "./EmployeePicker"
import type { CourseResponse } from "./TrainingCoursesTab"

interface SessionResponse {
  id: string; courseId: string; courseTitle: string; startDate: string; endDate: string
  venue: string | null; trainerName: string | null; capacity: number | null; enrolledCount: number
  status: "SCHEDULED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED"; notes: string | null; cancelReason: string | null; createdAt: string
}
interface EnrollmentResponse {
  id: string; sessionId: string; employeeId: string; employeeNameSnapshot: string; employeeNumberSnapshot: string | null
  status: "ENROLLED" | "ATTENDED" | "NO_SHOW" | "CANCELLED" | "COMPLETED" | "FAILED"
  enrolledAt: string; completedAt: string | null; score: number | null; passed: boolean | null; notes: string | null; cancelReason: string | null
}
interface EvidenceResponse { id: string; fileName: string; evidenceType: string; uploadedAt: string; uploadedByName: string | null }
interface EnrollmentPage { content: EnrollmentResponse[] }

const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }
const STATUS_COLORS: Record<string, string> = {
  SCHEDULED: "#0369A1", IN_PROGRESS: "#D97706", COMPLETED: "#059669", CANCELLED: "#94A3B8",
  ENROLLED: "#0369A1", ATTENDED: "#7C3AED", NO_SHOW: "#DC2626", FAILED: "#DC2626",
}
const btnStyle: React.CSSProperties = { background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 10px", fontSize: 11.5, fontWeight: 600, color: "#64748B", cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }

function EditSessionModal({ session, onClose }: { session: SessionResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [venue, setVenue] = useState(session.venue ?? "")
  const [trainerName, setTrainerName] = useState(session.trainerName ?? "")
  const [capacity, setCapacity] = useState(session.capacity?.toString() ?? "")
  const [notes, setNotes] = useState(session.notes ?? "")
  const [startDate, setStartDate] = useState(session.startDate)
  const [endDate, setEndDate] = useState(session.endDate)

  const save = useMutation({
    mutationFn: async () => {
      await apiClient.put(`/api/v1/training/sessions/${session.id}`, {
        venue: venue || null, trainerName: trainerName || null,
        capacity: capacity.trim() === "" ? null : parseInt(capacity, 10), notes: notes || null,
      })
      if (startDate !== session.startDate || endDate !== session.endDate) {
        await apiClient.post(`/api/v1/training/sessions/${session.id}/reschedule`, { startDate, endDate })
      }
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["training-session", session.id] }); qc.invalidateQueries({ queryKey: ["training-sessions"] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 460 }}>
        <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: "0 0 18px" }}>Edit session</p>
        <div style={{ display: "grid", gap: 12 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Start date</label><input type="date" style={inputStyle} value={startDate} onChange={e => setStartDate(e.target.value)} /></div>
            <div><label style={labelStyle}>End date</label><input type="date" style={inputStyle} value={endDate} onChange={e => setEndDate(e.target.value)} /></div>
          </div>
          <div><label style={labelStyle}>Venue</label><input style={inputStyle} value={venue} onChange={e => setVenue(e.target.value)} /></div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div><label style={labelStyle}>Trainer</label><input style={inputStyle} value={trainerName} onChange={e => setTrainerName(e.target.value)} /></div>
            <div><label style={labelStyle}>Capacity</label><input type="number" style={inputStyle} value={capacity} onChange={e => setCapacity(e.target.value)} /></div>
          </div>
          <div><label style={labelStyle}>Notes</label><textarea style={{ ...inputStyle, minHeight: 50, resize: "vertical" }} value={notes} onChange={e => setNotes(e.target.value)} /></div>
        </div>
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not save"}</p>}
        <div style={{ display: "flex", gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ flex: 1, padding: "10px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={save.isPending}
            style={{ flex: 2, padding: "10px", borderRadius: 8, border: "none", background: TRAINING_ACCENT, color: "#fff", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: save.isPending ? 0.6 : 1 }}>
            {save.isPending ? "Saving…" : "Save changes"}
          </button>
        </div>
      </div>
    </div>
  )
}

function EnrollModal({ sessionId, onClose }: { sessionId: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [employee, setEmployee] = useState<EmployeeOption | null>(null)
  const [notes, setNotes] = useState("")

  const enroll = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/training/sessions/${sessionId}/enrollments`, { employeeId: employee!.id, notes: notes || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["training-enrollments", sessionId] }); qc.invalidateQueries({ queryKey: ["training-session", sessionId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 440 }}>
        <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: "0 0 18px" }}>Enrol an employee</p>
        <label style={labelStyle}>Employee</label>
        <EmployeePicker value={employee} onChange={setEmployee} />
        <div style={{ marginTop: 12 }}>
          <label style={labelStyle}>Notes</label>
          <textarea style={{ ...inputStyle, minHeight: 50, resize: "vertical" }} value={notes} onChange={e => setNotes(e.target.value)} />
        </div>
        {enroll.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(enroll.error as any)?.response?.data?.message ?? "Could not enrol this employee"}</p>}
        <div style={{ display: "flex", gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ flex: 1, padding: "10px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => enroll.mutate()} disabled={!employee || enroll.isPending}
            style={{ flex: 2, padding: "10px", borderRadius: 8, border: "none", background: TRAINING_ACCENT, color: "#fff", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: (!employee || enroll.isPending) ? 0.6 : 1 }}>
            {enroll.isPending ? "Enrolling…" : "Enrol"}
          </button>
        </div>
      </div>
    </div>
  )
}

function CompleteEnrollmentModal({ enrollment, onClose }: { enrollment: EnrollmentResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [score, setScore] = useState("")
  const [passed, setPassed] = useState(true)

  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/training/enrollments/${enrollment.id}/complete`, {
      score: score.trim() === "" ? null : parseFloat(score), passed,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["training-enrollments", enrollment.sessionId] }); onClose() },
  })

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 380 }}>
        <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>Record outcome</p>
        <p style={{ fontSize: 12, color: "#94A3B8", margin: "0 0 18px" }}>{enrollment.employeeNameSnapshot}</p>
        <label style={labelStyle}>Score (optional)</label>
        <input type="number" step="0.01" style={inputStyle} value={score} onChange={e => setScore(e.target.value)} />
        <div style={{ display: "flex", gap: 10, marginTop: 12 }}>
          <button onClick={() => setPassed(true)} style={{ flex: 1, padding: "9px", borderRadius: 8, border: passed ? `1.5px solid ${TRAINING_ACCENT}` : "1px solid #E2E8F0", background: passed ? "#F0FDF4" : "#fff", color: passed ? TRAINING_ACCENT : "#64748B", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>Passed</button>
          <button onClick={() => setPassed(false)} style={{ flex: 1, padding: "9px", borderRadius: 8, border: !passed ? "1.5px solid #DC2626" : "1px solid #E2E8F0", background: !passed ? "#FEF2F2" : "#fff", color: !passed ? "#DC2626" : "#64748B", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>Failed</button>
        </div>
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not record outcome"}</p>}
        <div style={{ display: "flex", gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ flex: 1, padding: "10px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={save.isPending}
            style={{ flex: 2, padding: "10px", borderRadius: 8, border: "none", background: TRAINING_ACCENT, color: "#fff", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: save.isPending ? 0.6 : 1 }}>
            {save.isPending ? "Saving…" : "Record outcome"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function TrainingSessionDetail({ sessionId, onBack }: { sessionId: string; onBack: () => void }) {
  const qc = useQueryClient()
  const [showEdit, setShowEdit] = useState(false)
  const [showEnroll, setShowEnroll] = useState(false)
  const [completing, setCompleting] = useState<EnrollmentResponse | null>(null)

  const { data: session, isLoading } = useQuery<SessionResponse>({
    queryKey: ["training-session", sessionId],
    queryFn: async () => (await apiClient.get(`/api/v1/training/sessions/${sessionId}`)).data,
  })
  const { data: course } = useQuery<CourseResponse>({
    queryKey: ["training-course", session?.courseId],
    queryFn: async () => (await apiClient.get(`/api/v1/training/courses/${session!.courseId}`)).data,
    enabled: !!session?.courseId,
  })
  const { data: enrollData } = useQuery<EnrollmentPage>({
    queryKey: ["training-enrollments", sessionId],
    queryFn: async () => (await apiClient.get("/api/v1/training/enrollments", { params: { sessionId, size: 200 } })).data,
  })
  const enrollments = enrollData?.content ?? []
  const { data: evidence = [] } = useQuery<EvidenceResponse[]>({
    queryKey: ["training-session-evidence", sessionId],
    queryFn: async () => (await apiClient.get(`/api/v1/training/sessions/${sessionId}/evidence`)).data,
  })

  const lifecycle = useMutation({
    mutationFn: async (action: "start" | "complete" | "cancel") => {
      if (action === "cancel") {
        const reason = prompt("Reason for cancelling this session (optional):") ?? undefined
        return apiClient.post(`/api/v1/training/sessions/${sessionId}/cancel`, reason ? { reason } : undefined)
      }
      return apiClient.post(`/api/v1/training/sessions/${sessionId}/${action}`)
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["training-session", sessionId] }); qc.invalidateQueries({ queryKey: ["training-sessions"] }) },
  })

  const markAttended = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training/enrollments/${id}/attended`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["training-enrollments", sessionId] }),
  })
  const markNoShow = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training/enrollments/${id}/no-show`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["training-enrollments", sessionId] }),
  })
  const cancelEnrollment = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training/enrollments/${id}/cancel`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["training-enrollments", sessionId] }),
  })
  // ADMIN-only server-side (TRAINING_ADMIN) — same "backend @PreAuthorize is the real gate" note as elsewhere.
  const issueCertificate = useMutation({
    mutationFn: async (enrollmentId: string) => apiClient.post(`/api/v1/training/enrollments/${enrollmentId}/certificate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["training-enrollments", sessionId] }),
    onError: (err: any) => alert(err?.response?.data?.message ?? "Could not issue certificate"),
  })

  const uploadEvidence = useMutation({
    mutationFn: async ({ file, evidenceType }: { file: File; evidenceType: string }) => {
      const form = new FormData()
      form.append("file", file)
      return apiClient.post(`/api/v1/training/sessions/${sessionId}/evidence?evidenceType=${encodeURIComponent(evidenceType)}`, form, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["training-session-evidence", sessionId] }),
  })

  const downloadRegister = async () => {
    const res = await apiClient.get(`/api/v1/training/sessions/${sessionId}/attendance-register/pdf`, { responseType: "blob" })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement("a")
    a.href = url; a.download = `attendance-register-${sessionId.slice(0, 8)}.pdf`
    document.body.appendChild(a); a.click(); a.remove()
    window.URL.revokeObjectURL(url)
  }

  if (isLoading || !session) return <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>

  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "#64748B", fontSize: 13, marginBottom: 16, padding: 0 }}>
        <ArrowLeft size={15} /> All sessions
      </button>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 18 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h2 style={{ fontSize: 18, fontWeight: 800, color: "#0F172A", margin: 0 }}>{session.courseTitle}</h2>
            <span style={{ fontSize: 10.5, fontWeight: 700, padding: "3px 9px", borderRadius: 20, background: `${STATUS_COLORS[session.status]}18`, color: STATUS_COLORS[session.status] }}>{session.status.replace("_", " ")}</span>
          </div>
          <p style={{ fontSize: 12.5, color: "#94A3B8", margin: "4px 0 0" }}>
            {session.startDate} → {session.endDate} {session.venue ? `· ${session.venue}` : ""} {session.trainerName ? `· ${session.trainerName}` : ""}
            {session.capacity != null ? ` · ${session.enrolledCount}/${session.capacity} enrolled` : ` · ${session.enrolledCount} enrolled`}
          </p>
          {session.cancelReason && <p style={{ fontSize: 12, color: "#DC2626", margin: "4px 0 0" }}>Cancelled: {session.cancelReason}</p>}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          {session.status === "SCHEDULED" && (
            <>
              <button onClick={() => setShowEdit(true)} style={btnStyle}><Pencil size={13} /> Edit</button>
              <button onClick={() => lifecycle.mutate("start")} style={btnStyle}><Play size={13} /> Start</button>
              <button onClick={() => lifecycle.mutate("cancel")} style={{ ...btnStyle, color: "#DC2626" }}><XCircle size={13} /> Cancel</button>
            </>
          )}
          {session.status === "IN_PROGRESS" && (
            <button onClick={() => lifecycle.mutate("complete")} style={{ ...btnStyle, color: TRAINING_ACCENT }}><CheckCircle2 size={13} /> Mark complete</button>
          )}
          <button onClick={downloadRegister} style={btnStyle}><Download size={13} /> Attendance PDF</button>
        </div>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "20px 0 10px" }}>
        <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>Enrolled employees ({enrollments.length})</p>
        {session.status !== "CANCELLED" && session.status !== "COMPLETED" && (
          <button onClick={() => setShowEnroll(true)} style={{ display: "flex", alignItems: "center", gap: 6, background: TRAINING_ACCENT, color: "#fff", border: "none", borderRadius: 8, padding: "8px 14px", fontSize: 12.5, fontWeight: 600, cursor: "pointer" }}>
            <UserPlus size={14} /> Enrol
          </button>
        )}
      </div>

      {enrollments.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No one enrolled yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden", marginBottom: 24 }}>
          {enrollments.map((e, i) => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{e.employeeNameSnapshot}</p>
                  <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: `${STATUS_COLORS[e.status] ?? "#94A3B8"}18`, color: STATUS_COLORS[e.status] ?? "#94A3B8" }}>{e.status.replace("_", " ")}</span>
                  {e.score != null && <span style={{ fontSize: 11, color: "#94A3B8" }}>Score: {e.score}</span>}
                </div>
                {e.employeeNumberSnapshot && <p style={{ fontSize: 11, color: "#94A3B8", margin: "2px 0 0" }}>{e.employeeNumberSnapshot}</p>}
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                {e.status === "ENROLLED" && (
                  <>
                    <button onClick={() => markAttended.mutate(e.id)} style={btnStyle}><Check size={12} /> Attended</button>
                    <button onClick={() => markNoShow.mutate(e.id)} style={{ ...btnStyle, color: "#DC2626" }}><XIcon size={12} /> No-show</button>
                  </>
                )}
                {(e.status === "ENROLLED" || e.status === "ATTENDED") && (
                  <button onClick={() => setCompleting(e)} style={{ ...btnStyle, color: TRAINING_ACCENT }}>Record outcome</button>
                )}
                {(e.status === "ENROLLED" || e.status === "ATTENDED") && (
                  <button onClick={() => cancelEnrollment.mutate(e.id)} style={btnStyle}>Cancel</button>
                )}
                {e.status === "COMPLETED" && e.passed && course?.certificationOffered && (
                  <button onClick={() => issueCertificate.mutate(e.id)} disabled={issueCertificate.isPending}
                    style={{ ...btnStyle, color: "#D97706", opacity: issueCertificate.isPending ? 0.6 : 1 }}>
                    <Award size={12} /> Issue certificate
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "0 0 10px" }}>
        <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: 0 }}>Evidence ({evidence.length})</p>
        <label style={{ display: "flex", alignItems: "center", gap: 6, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, padding: "7px 14px", fontSize: 12, fontWeight: 600, color: "#64748B", cursor: "pointer" }}>
          <Upload size={13} /> Upload
          <input type="file" hidden onChange={e => {
            const file = e.target.files?.[0]
            if (!file) return
            const evidenceType = prompt("Evidence type (e.g. ATTENDANCE_REGISTER, MATERIAL, EXTERNAL_INVOICE):", "MATERIAL") ?? "MATERIAL"
            uploadEvidence.mutate({ file, evidenceType })
            e.target.value = ""
          }} />
        </label>
      </div>
      {evidence.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No files attached.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {evidence.map((ev, i) => (
            <div key={ev.id} style={{ display: "flex", justifyContent: "space-between", padding: "10px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <p style={{ fontSize: 12.5, color: "#0F172A", margin: 0 }}>{ev.fileName}</p>
              <span style={{ fontSize: 11, color: "#94A3B8" }}>{ev.evidenceType}</span>
            </div>
          ))}
        </div>
      )}

      {showEdit && <EditSessionModal session={session} onClose={() => setShowEdit(false)} />}
      {showEnroll && <EnrollModal sessionId={sessionId} onClose={() => setShowEnroll(false)} />}
      {completing && <CompleteEnrollmentModal enrollment={completing} onClose={() => setCompleting(null)} />}
    </div>
  )
}
