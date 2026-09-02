// src/pages/trainingprovider/TrainProvEnrollmentsTab.tsx
//
// This client's enrollments across every session — confirmed via
// TrainProvEnrollmentController: GET /enrollments?clientId=&status=.
// Enrolling itself happens from a session's own detail page (it's a
// per-session action, needs a delegate + session context) — this tab
// is read + the same lifecycle actions the session roster offers
// (attended/no-show/complete/cancel), so staff don't have to open each
// session individually to process a client's outcomes.
//
// IMPORTANT: cancel is blocked once `invoiced` is true —
// TrainProvEnrollment.cancel() throws IllegalStateException "Cannot
// cancel an enrollment that has already been invoiced — issue a credit
// note instead." Credit-note issuance is NOT implemented anywhere in
// this module (flagged in the status doc as a real gap) — so an
// invoiced enrollment's Cancel button is disabled here with an
// explanatory title rather than left to fail server-side.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Check, X as XIcon, Award, ReceiptText } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"

interface EnrollmentResponse {
  id: string; sessionId: string; delegateId: string; clientId: string; delegateNameSnapshot: string
  status: "ENROLLED" | "ATTENDED" | "NO_SHOW" | "CANCELLED" | "COMPLETED" | "FAILED"
  enrolledAt: string; completedAt: string | null; score: number | null; passed: boolean | null
  notes: string | null; cancelReason: string | null; invoiced: boolean
}
interface EnrollmentPage { content: EnrollmentResponse[] }

const STATUS_COLORS: Record<string, string> = {
  ENROLLED: "#0369A1", ATTENDED: "#7C3AED", COMPLETED: "#059669", FAILED: "#DC2626", NO_SHOW: "#DC2626", CANCELLED: "#94A3B8",
}
const btnStyle: React.CSSProperties = { background: "none", border: "1px solid #E2E8F0", borderRadius: 7, padding: "6px 10px", fontSize: 11.5, fontWeight: 600, color: "#64748B", cursor: "pointer", display: "flex", alignItems: "center", gap: 4 }

function CompleteModal({ enrollment, onClose }: { enrollment: EnrollmentResponse; onClose: () => void }) {
  const qc = useQueryClient()
  const [score, setScore] = useState("")
  const [passed, setPassed] = useState(true)
  const save = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/training-provider/enrollments/${enrollment.id}/complete`, { score: score.trim() === "" ? null : parseFloat(score), passed }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["trainprov-enrollments", enrollment.clientId] }); onClose() },
  })
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 50 }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 380 }}>
        <p style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: "0 0 4px" }}>Record outcome</p>
        <p style={{ fontSize: 12, color: "#94A3B8", margin: "0 0 18px" }}>{enrollment.delegateNameSnapshot}</p>
        <label style={{ fontSize: 12, fontWeight: 600, color: "#374151", marginBottom: 5, display: "block" }}>Score (optional)</label>
        <input type="number" step="0.01" style={{ width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }} value={score} onChange={e => setScore(e.target.value)} />
        <div style={{ display: "flex", gap: 10, marginTop: 12 }}>
          <button onClick={() => setPassed(true)} style={{ flex: 1, padding: "9px", borderRadius: 8, border: passed ? `1.5px solid ${TRAINPROV_ACCENT}` : "1px solid #E2E8F0", background: passed ? "#FFFBEB" : "#fff", color: passed ? TRAINPROV_ACCENT : "#64748B", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>Passed</button>
          <button onClick={() => setPassed(false)} style={{ flex: 1, padding: "9px", borderRadius: 8, border: !passed ? "1.5px solid #DC2626" : "1px solid #E2E8F0", background: !passed ? "#FEF2F2" : "#fff", color: !passed ? "#DC2626" : "#64748B", fontSize: 13, fontWeight: 700, cursor: "pointer" }}>Failed</button>
        </div>
        {save.isError && <p style={{ color: "#DC2626", fontSize: 12, marginTop: 12 }}>{(save.error as any)?.response?.data?.message ?? "Could not record outcome"}</p>}
        <div style={{ display: "flex", gap: 10, marginTop: 18 }}>
          <button onClick={onClose} style={{ flex: 1, padding: "10px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
          <button onClick={() => save.mutate()} disabled={save.isPending}
            style={{ flex: 2, padding: "10px", borderRadius: 8, border: "none", background: TRAINPROV_ACCENT, color: "#fff", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: save.isPending ? 0.6 : 1 }}>
            {save.isPending ? "Saving…" : "Record outcome"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function TrainProvEnrollmentsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const [statusFilter, setStatusFilter] = useState<string>("")
  const [completing, setCompleting] = useState<EnrollmentResponse | null>(null)

  const { data, isLoading } = useQuery<EnrollmentPage>({
    queryKey: ["trainprov-enrollments", clientId, statusFilter],
    queryFn: async () => (await apiClient.get("/api/v1/training-provider/enrollments", { params: { clientId, status: statusFilter || undefined, size: 100 } })).data,
  })
  const enrollments = data?.content ?? []

  const markAttended = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/enrollments/${id}/attended`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-enrollments", clientId] }),
  })
  const markNoShow = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/enrollments/${id}/no-show`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-enrollments", clientId] }),
  })
  const cancel = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/training-provider/enrollments/${id}/cancel`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-enrollments", clientId] }),
    onError: (err: any) => alert(err?.response?.data?.message ?? "Could not cancel this enrollment"),
  })
  // ADMIN-only server-side (TRAININGPROVIDER_ADMIN).
  const issueCertificate = useMutation({
    mutationFn: async (enrollmentId: string) => apiClient.post(`/api/v1/training-provider/enrollments/${enrollmentId}/certificate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["trainprov-enrollments", clientId] }),
    onError: (err: any) => alert(err?.response?.data?.message ?? "Could not issue certificate"),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "#94A3B8", margin: 0 }}>{enrollments.length} enrollment{enrollments.length === 1 ? "" : "s"}</p>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
          style={{ padding: "6px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12, fontFamily: "inherit" }}>
          <option value="">All statuses</option>
          <option value="ENROLLED">Enrolled</option>
          <option value="ATTENDED">Attended</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
          <option value="NO_SHOW">No-show</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      <p style={{ fontSize: 11.5, color: "#94A3B8", margin: "0 0 14px" }}>
        New enrollments are created from a session's own detail page (Sessions tab), where a delegate is enrolled into that specific session.
      </p>

      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : enrollments.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>No enrollments yet.</p>
      ) : (
        <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
          {enrollments.map((e, i) => (
            <div key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid #F1F5F9" }}>
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{e.delegateNameSnapshot}</p>
                  <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: `${STATUS_COLORS[e.status] ?? "#94A3B8"}18`, color: STATUS_COLORS[e.status] ?? "#94A3B8" }}>{e.status.replace("_", " ")}</span>
                  {e.invoiced && (
                    <span title="Already invoiced — cancel is blocked; issuing a credit note isn't implemented in this build" style={{ display: "flex", alignItems: "center", gap: 3, fontSize: 10, fontWeight: 700, color: "#D97706" }}>
                      <ReceiptText size={11} /> INVOICED
                    </span>
                  )}
                  {e.score != null && <span style={{ fontSize: 11, color: "#94A3B8" }}>Score: {e.score}</span>}
                </div>
                <p style={{ fontSize: 11, color: "#94A3B8", margin: "2px 0 0" }}>Session {e.sessionId.slice(0, 8)} · Enrolled {e.enrolledAt.slice(0, 10)}</p>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                {e.status === "ENROLLED" && (
                  <>
                    <button onClick={() => markAttended.mutate(e.id)} style={btnStyle}><Check size={12} /> Attended</button>
                    <button onClick={() => markNoShow.mutate(e.id)} style={{ ...btnStyle, color: "#DC2626" }}><XIcon size={12} /> No-show</button>
                  </>
                )}
                {(e.status === "ENROLLED" || e.status === "ATTENDED") && (
                  <button onClick={() => setCompleting(e)} style={{ ...btnStyle, color: TRAINPROV_ACCENT }}>Record outcome</button>
                )}
                {(e.status === "ENROLLED" || e.status === "ATTENDED") && (
                  <button onClick={() => cancel.mutate(e.id)} disabled={e.invoiced}
                    title={e.invoiced ? "Already invoiced — issue a credit note instead (not implemented in this build)" : undefined}
                    style={{ ...btnStyle, opacity: e.invoiced ? 0.5 : 1, cursor: e.invoiced ? "not-allowed" : "pointer" }}>
                    Cancel
                  </button>
                )}
                {e.status === "COMPLETED" && e.passed && (
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

      {completing && <CompleteModal enrollment={completing} onClose={() => setCompleting(null)} />}
    </div>
  )
}
