// src/pages/legalcompliance/ControlTestsTab.tsx
//
// Control testing workspace. Endpoints (ControlTestController, /api/v1/legalcompliance/control-tests):
//   GET / · GET /summary · GET /{id} · POST / · POST /{id}/start · PUT /{id}/progress · POST /{id}/submit
//   POST /{id}/approve · POST /{id}/return · POST /{id}/cancel
// Evidence goes through the shared evidence API (POST /api/v1/evidence, sourceModule=legalcompliance, relatedEntityType=CONTROL_TEST).
//
// A control becomes effective only when a test is approved by someone other than the tester. A pass still needs evidence.
// A partial or failed result raises a finding automatically when the test is approved.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { ChevronDown, ChevronUp, Plus, Paperclip, Download, X } from "lucide-react"

interface Test {
  id: string; reference: string; controlId: string; controlReference: string; controlTitle: string; testType: string; status: string
  plannedDate: string; overdue: boolean; procedure: string | null; expectedEvidence: string | null; designRating: string | null; sampleSize: number | null
  sampleDescription: string | null; testerName: string | null; reviewerName: string | null; testNotes: string | null; result: string | null
  submittedAt: string | null; reviewedByName: string | null; reviewedAt: string | null; reviewNote: string | null; returnedCount: number
  retestOfTestId: string | null; cancelReason: string | null; evidenceSnapshot: { fileName: string }[]
}
interface EvidenceRef { id: string; fileName: string; fileSizeBytes: number; uploadedByName: string | null }
interface Finding { id: string; reference: string; severity: string; status: string; dueDate: string }
interface Detail { test: Test; liveEvidence: EvidenceRef[]; finding: Finding | null }
interface Summary { planned: number; inProgress: number; awaitingReview: number; overdue: number; controlsDueForTest: number; controlsNeverTested: number }
interface ControlOpt { id: string; reference: string; title: string }

const STATUSES = ["", "PLANNED", "IN_PROGRESS", "SUBMITTED", "COMPLETED", "CANCELLED"]
const STATUS_CFG: Record<string, { label: string; color: string; bg: string }> = {
  PLANNED: { label: "Planned", color: "#475569", bg: "#F1F5F9" }, IN_PROGRESS: { label: "In progress", color: "#1D4ED8", bg: "#DBEAFE" },
  SUBMITTED: { label: "Awaiting review", color: "#92400E", bg: "#FEF3C7" }, COMPLETED: { label: "Completed", color: "#166534", bg: "#DCFCE7" }, CANCELLED: { label: "Cancelled", color: "#475569", bg: "#E2E8F0" },
}
const RESULT_CFG: Record<string, { label: string; color: string; bg: string }> = {
  PASS: { label: "Pass", color: "#166534", bg: "#DCFCE7" }, PARTIAL: { label: "Partial", color: "#C2410C", bg: "#FFEDD5" }, FAIL: { label: "Fail", color: "#991B1B", bg: "#FEE2E2" },
}
const base = "/api/v1/legalcompliance/control-tests"
const kb = (n: number) => n < 1024 ? `${n} B` : `${Math.round(n / 1024)} KB`
const today = () => new Date().toISOString().slice(0, 10)

export default function ControlTestsTab() {
  const qc = useQueryClient()
  const hasTest = usePermission("LEGALCOMPLIANCE_CONTROL_TEST")
  const hasReview = usePermission("LEGALCOMPLIANCE_CONTROL_TEST_REVIEW")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canTest = hasTest || hasAdmin
  const canReview = hasReview || hasAdmin

  const [filter, setFilter] = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [schedule, setSchedule] = useState(false)
  const [sForm, setSForm] = useState({ controlId: "", testType: "SCHEDULED", plannedDate: today(), reviewerName: "", retestOfTestId: "" })
  const [run, setRun] = useState({ result: "PASS", sampleSize: "", sampleDescription: "", notes: "" })
  const [runFor, setRunFor] = useState<string | null>(null)
  const [reviewNote, setReviewNote] = useState("")
  const [cancel, setCancel] = useState<Test | null>(null)
  const [cancelReason, setCancelReason] = useState("")
  const [apiError, setApiError] = useState("")
  const [notice, setNotice] = useState("")
  const [uploading, setUploading] = useState(false)

  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-tests", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: tests = [], isLoading } = useQuery<Test[]>({ queryKey: ["lc-tests", "list", filter], queryFn: async () => unwrap(await apiClient.get(base, { params: filter ? { status: filter } : {} })) })
  const { data: allTests = [] } = useQuery<Test[]>({ queryKey: ["lc-tests", "all"], enabled: schedule, queryFn: async () => unwrap(await apiClient.get(base)) })
  const { data: controls = [] } = useQuery<ControlOpt[]>({ queryKey: ["lc-controls-active"], enabled: schedule, queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/controls", { params: { status: "ACTIVE" } })) })
  const { data: detail } = useQuery<Detail>({ queryKey: ["lc-tests", "detail", expanded], enabled: !!expanded, queryFn: async () => one(await apiClient.get(`${base}/${expanded}`)) })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["lc-tests"] }); qc.invalidateQueries({ queryKey: ["lc-controls"] }); qc.invalidateQueries({ queryKey: ["lc-coverage"] })
    qc.invalidateQueries({ queryKey: ["lc-findings"] }); setApiError("")
  }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))
  const ok = (msg: string, after?: () => void) => () => { refresh(); after?.(); setNotice(msg) }

  const doSchedule = useMutation({
    mutationFn: (body: any) => apiClient.post(base, body), onSuccess: ok("Test scheduled", () => setSchedule(false)), onError: fail("Failed to schedule test"),
  })
  const start = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/start`), onSuccess: (_r, id) => { refresh(); setExpanded(id); setNotice("Test started — you are the tester") }, onError: fail("Failed to start test") })
  const saveProgress = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`${base}/${id}/progress`, body), onSuccess: ok("Progress saved"), onError: fail("Failed to save"),
  })
  const submit = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/${id}/submit`, body), onSuccess: ok("Submitted for review"), onError: fail("Failed to submit"),
  })
  const approve = useMutation({
    mutationFn: ({ id, note }: { id: string; note: string }) => apiClient.post(`${base}/${id}/approve`, { note: note || null }),
    onSuccess: (r) => { refresh(); setReviewNote(""); const d = one(r) as Detail; setNotice(d.finding ? `Approved — finding ${d.finding.reference} raised` : "Approved — control effectiveness updated") }, onError: fail("Failed to approve"),
  })
  const sendBack = useMutation({
    mutationFn: ({ id, note }: { id: string; note: string }) => apiClient.post(`${base}/${id}/return`, { note }), onSuccess: ok("Returned to the tester", () => setReviewNote("")), onError: fail("Failed to return"),
  })
  const doCancel = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => apiClient.post(`${base}/${id}/cancel`, { reason }), onSuccess: ok("Test cancelled", () => setCancel(null)), onError: fail("Failed to cancel"),
  })
  const detach = useMutation({
    mutationFn: (evidenceId: string) => apiClient.post(`/api/v1/evidence/${evidenceId}/detach`), onSuccess: () => qc.invalidateQueries({ queryKey: ["lc-tests", "detail"] }), onError: fail("Failed to remove evidence"),
  })

  const upload = async (testId: string, file: File) => {
    setUploading(true); setApiError("")
    try {
      const fd = new FormData()
      fd.append("file", file); fd.append("evidenceType", "CONTROL_TEST_EVIDENCE"); fd.append("sourceModule", "legalcompliance")
      fd.append("relatedEntityType", "CONTROL_TEST"); fd.append("relatedEntityId", testId)
      await apiClient.post("/api/v1/evidence", fd, { headers: { "Content-Type": "multipart/form-data" } })
      qc.invalidateQueries({ queryKey: ["lc-tests", "detail"] })
    } catch (e) { setApiError(errMsg(e, "Failed to upload evidence")) } finally { setUploading(false) }
  }
  const download = async (e: EvidenceRef) => {
    const r = await apiClient.get(`/api/v1/evidence/${e.id}/download`, { responseType: "blob" })
    const url = URL.createObjectURL(r.data); const a = document.createElement("a"); a.href = url; a.download = e.fileName; a.click(); URL.revokeObjectURL(url)
  }

  const openRun = (t: Test) => { setRunFor(t.id); setRun({ result: t.result ?? "PASS", sampleSize: t.sampleSize?.toString() ?? "", sampleDescription: t.sampleDescription ?? "", notes: t.testNotes ?? "" }) }
  const runBody = () => ({ sampleSize: run.sampleSize ? +run.sampleSize : null, sampleDescription: run.sampleDescription || null, notes: run.notes })
  const retestCandidates = allTests.filter(t => t.controlId === sForm.controlId && t.status === "COMPLETED" && t.result !== "PASS")
  const card = (label: string, v: number | undefined, color = "#0F172A") => (
    <div style={{ flex: 1, minWidth: 130, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 12, padding: "12px 16px" }}>
      <div style={{ fontSize: 12, color: "#64748B", fontWeight: 600 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800, color }}>{v ?? "—"}</div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        {card("Planned", summary?.planned)}{card("In progress", summary?.inProgress, "#1D4ED8")}{card("Awaiting review", summary?.awaitingReview, "#92400E")}
        {card("Tests overdue", summary?.overdue, summary?.overdue ? "#991B1B" : undefined)}{card("Controls due for testing", summary?.controlsDueForTest, summary?.controlsDueForTest ? "#C2410C" : undefined)}
        {card("Never tested", summary?.controlsNeverTested, "#92400E")}
      </div>
      <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        {canTest && <button style={primaryBtn} onClick={() => { setApiError(""); setSForm({ controlId: "", testType: "SCHEDULED", plannedDate: today(), reviewerName: "", retestOfTestId: "" }); setSchedule(true) }}><Plus size={14} style={{ verticalAlign: -2 }} /> Schedule test</button>}
        <select aria-label="Filter by status" style={{ ...input, width: 180 }} value={filter} onChange={e => setFilter(e.target.value)}>{STATUSES.map(s => <option key={s} value={s}>{s ? STATUS_CFG[s].label : "All statuses"}</option>)}</select>
        <span style={{ fontSize: 12, color: "#64748B" }}>The tester and the reviewer must be different people. A pass still needs evidence.</span>
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      {apiError && !schedule && !cancel && <Err m={apiError} />}

      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : tests.length === 0 ? <p style={{ color: "#64748B", fontSize: 14 }}>No tests yet. Schedule one for an active control.</p> : tests.map(t => {
        const open = expanded === t.id, st = STATUS_CFG[t.status], d = detail?.test.id === t.id ? detail : null
        const editing = t.status === "IN_PROGRESS" && runFor === t.id
        return (
          <div key={t.id} style={{ border: `1px solid ${t.overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 10, background: "#fff", marginBottom: 8 }}>
            <button onClick={() => { setExpanded(open ? null : t.id); setApiError("") }} style={{ all: "unset", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", width: "calc(100% - 32px)" }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{t.reference} — {t.controlReference} {t.controlTitle}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>{t.testType.replace("_", " ").toLowerCase()} · planned {fmtDate(t.plannedDate)}{t.overdue ? " (overdue)" : ""}{t.testerName ? ` · tester ${t.testerName}` : ""}{t.reviewerName ? ` · reviewer ${t.reviewerName}` : ""}</div>
              </div>
              {t.result && <Badge text={RESULT_CFG[t.result].label} color={RESULT_CFG[t.result].color} bg={RESULT_CFG[t.result].bg} />}
              <Badge text={st.label} color={st.color} bg={st.bg} />
              {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
            {open && (
              <div style={{ padding: "0 16px 14px", fontSize: 13, color: "#475569" }}>
                {t.procedure && <p style={{ marginTop: 0 }}><b>Procedure (as defined when scheduled):</b> {t.procedure}</p>}
                {t.expectedEvidence && <p><b>Expected evidence:</b> {t.expectedEvidence}</p>}
                {t.designRating && <p style={{ fontSize: 12 }}>Design rating at scheduling: {t.designRating.replace(/_/g, " ").toLowerCase()}</p>}
                {t.status === "CANCELLED" && <p><b>Cancelled:</b> {t.cancelReason}</p>}
                {t.reviewNote && <p><b>Reviewer ({t.reviewedByName}, {fmtDateTime(t.reviewedAt)}):</b> {t.reviewNote}</p>}
                {t.returnedCount > 0 && <p style={{ fontSize: 12, color: "#92400E" }}>Returned {t.returnedCount} time(s) for rework.</p>}

                {t.status === "PLANNED" && canTest && (
                  <div style={{ display: "flex", gap: 8 }}>
                    <button style={primaryBtn} disabled={start.isPending} onClick={() => start.mutate(t.id)}>Start test</button>
                    <button style={{ ...ghostBtn, color: "#991B1B" }} onClick={() => { setApiError(""); setCancelReason(""); setCancel(t) }}>Cancel</button>
                  </div>
                )}

                {(t.status === "IN_PROGRESS" || t.status === "SUBMITTED" || t.status === "COMPLETED") && (
                  <div style={{ margin: "8px 0" }}>
                    <div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}><Paperclip size={12} style={{ verticalAlign: -1 }} /> Evidence ({d?.liveEvidence.length ?? 0})</div>
                    {(d?.liveEvidence ?? []).map(e => (
                      <div key={e.id} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 12, padding: "2px 0" }}>
                        <button onClick={() => download(e)} style={{ all: "unset", cursor: "pointer", color: "#4338CA", display: "flex", gap: 4, alignItems: "center" }}><Download size={12} /> {e.fileName}</button>
                        <span style={{ color: "#94A3B8" }}>{kb(e.fileSizeBytes)}{e.uploadedByName ? ` · ${e.uploadedByName}` : ""}</span>
                        {t.status === "IN_PROGRESS" && canTest && <button aria-label="Remove evidence" onClick={() => { if (confirm("Remove this file from the test? The file itself is preserved.")) detach.mutate(e.id) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={12} /></button>}
                      </div>
                    ))}
                    {t.status !== "IN_PROGRESS" && t.evidenceSnapshot.length > 0 && <div style={{ fontSize: 11, color: "#94A3B8" }}>{t.evidenceSnapshot.length} file(s) were attached at submission.</div>}
                    {t.status === "IN_PROGRESS" && canTest && (
                      <label style={{ ...ghostBtn, display: "inline-block", marginTop: 6, fontSize: 12, padding: "5px 12px" }}>
                        {uploading ? "Uploading…" : "Attach file"}
                        <input type="file" style={{ display: "none" }} disabled={uploading} onChange={e => { const f = e.target.files?.[0]; if (f) upload(t.id, f); e.target.value = "" }} />
                      </label>
                    )}
                  </div>
                )}

                {t.status === "IN_PROGRESS" && canTest && (
                  <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: 12, marginTop: 8 }}>
                    {!editing ? <button style={primaryBtn} onClick={() => openRun(t)}>Record test result</button> : (
                      <>
                        <div style={{ display: "flex", gap: 12 }}>
                          <div style={{ flex: 1 }}><Field l="Result *"><select style={input} value={run.result} onChange={e => setRun({ ...run, result: e.target.value })}><option value="PASS">Pass</option><option value="PARTIAL">Partially effective</option><option value="FAIL">Fail</option></select></Field></div>
                          <div style={{ flex: 1 }}><Field l="Items tested *"><input type="number" min={1} style={input} value={run.sampleSize} onChange={e => setRun({ ...run, sampleSize: e.target.value })} /></Field></div>
                        </div>
                        <Field l="How the sample was chosen"><input style={input} value={run.sampleDescription} onChange={e => setRun({ ...run, sampleDescription: e.target.value })} /></Field>
                        <Field l="Test notes * (what you did and found, at least 15 characters)"><textarea style={{ ...input, minHeight: 80 }} value={run.notes} onChange={e => setRun({ ...run, notes: e.target.value })} /></Field>
                        {run.result !== "PASS" && <p style={{ fontSize: 12, color: "#92400E", marginTop: 0 }}>A {run.result === "FAIL" ? "failed" : "partial"} result raises a finding when the test is approved.</p>}
                        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                          <button style={ghostBtn} disabled={saveProgress.isPending} onClick={() => saveProgress.mutate({ id: t.id, body: runBody() })}>Save progress</button>
                          <button style={primaryBtn} disabled={submit.isPending || !run.sampleSize || run.notes.trim().length < 15 || (d?.liveEvidence.length ?? 0) < 1}
                            onClick={() => submit.mutate({ id: t.id, body: { ...runBody(), result: run.result } })}>Submit for review</button>
                          <button style={{ ...ghostBtn, color: "#991B1B" }} onClick={() => { setApiError(""); setCancelReason(""); setCancel(t) }}>Cancel test</button>
                        </div>
                        {(d?.liveEvidence.length ?? 0) < 1 && <p style={{ fontSize: 12, color: "#64748B" }}>Attach at least one evidence file to submit.</p>}
                      </>
                    )}
                  </div>
                )}

                {(t.status === "SUBMITTED" || t.status === "COMPLETED") && (
                  <div style={{ marginTop: 8 }}>
                    <p style={{ margin: "4px 0" }}><b>Tester's result:</b> {t.result ? RESULT_CFG[t.result].label : "—"} · {t.sampleSize} item(s){t.sampleDescription ? ` (${t.sampleDescription})` : ""} · submitted {fmtDateTime(t.submittedAt)}</p>
                    <p style={{ margin: "4px 0" }}><b>Notes:</b> {t.testNotes}</p>
                  </div>
                )}
                {t.status === "COMPLETED" && d?.finding && <p style={{ color: "#991B1B" }}>Finding {d.finding.reference} ({d.finding.severity}, {d.finding.status.replace("_", " ").toLowerCase()}) was raised from this test — see the Findings tab.</p>}

                {t.status === "SUBMITTED" && canReview && (
                  <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, padding: 12, marginTop: 8 }}>
                    <div style={{ fontWeight: 700, marginBottom: 6 }}>Review</div>
                    <Field l="Note (required to return)"><textarea style={{ ...input, minHeight: 60 }} value={reviewNote} onChange={e => setReviewNote(e.target.value)} /></Field>
                    <div style={{ display: "flex", gap: 8 }}>
                      <button style={primaryBtn} disabled={approve.isPending} onClick={() => approve.mutate({ id: t.id, note: reviewNote })}>Approve result</button>
                      <button style={ghostBtn} disabled={reviewNote.trim().length < 15 || sendBack.isPending} onClick={() => sendBack.mutate({ id: t.id, note: reviewNote })}>Return to tester</button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )
      })}

      {schedule && (
        <Modal title="Schedule control test" onClose={() => setSchedule(false)}>
          <Err m={apiError} />
          <Field l="Control * (active controls only)"><select style={input} value={sForm.controlId} onChange={e => setSForm({ ...sForm, controlId: e.target.value, retestOfTestId: "" })}>
            <option value="">Choose…</option>{controls.map(c => <option key={c.id} value={c.id}>{c.reference} — {c.title}</option>)}</select></Field>
          <div style={{ display: "flex", gap: 12 }}>
            <div style={{ flex: 1 }}><Field l="Type"><select style={input} value={sForm.testType} onChange={e => setSForm({ ...sForm, testType: e.target.value })}><option value="SCHEDULED">Scheduled</option><option value="AD_HOC">Ad hoc</option><option value="RETEST">Retest</option></select></Field></div>
            <div style={{ flex: 1 }}><Field l="Planned date *"><input type="date" style={input} value={sForm.plannedDate} onChange={e => setSForm({ ...sForm, plannedDate: e.target.value })} /></Field></div>
          </div>
          {sForm.testType === "RETEST" && (
            <Field l="Test being retested *"><select style={input} value={sForm.retestOfTestId} onChange={e => setSForm({ ...sForm, retestOfTestId: e.target.value })}>
              <option value="">Choose a partial or failed test…</option>{retestCandidates.map(t => <option key={t.id} value={t.id}>{t.reference} — {t.result} on {fmtDate(t.reviewedAt)}</option>)}</select></Field>
          )}
          <Field l="Assigned reviewer (optional — cannot also be the tester)"><input style={input} value={sForm.reviewerName} onChange={e => setSForm({ ...sForm, reviewerName: e.target.value })} /></Field>
          <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>The control's procedure and expected evidence are copied into the test, so it is judged against what the control said today.</p>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setSchedule(false)}>Cancel</button>
            <button style={primaryBtn} disabled={!sForm.controlId || !sForm.plannedDate || (sForm.testType === "RETEST" && !sForm.retestOfTestId) || doSchedule.isPending}
              onClick={() => doSchedule.mutate({ controlId: sForm.controlId, testType: sForm.testType, plannedDate: sForm.plannedDate, reviewerName: sForm.reviewerName.trim() || null, retestOfTestId: sForm.testType === "RETEST" ? sForm.retestOfTestId : null })}>Schedule</button>
          </div>
        </Modal>
      )}

      {cancel && (
        <Modal title={`Cancel ${cancel.reference}`} onClose={() => setCancel(null)}>
          <Err m={apiError} />
          <Field l="Reason * (at least 15 characters)"><textarea style={{ ...input, minHeight: 70 }} value={cancelReason} onChange={e => setCancelReason(e.target.value)} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setCancel(null)}>Keep test</button>
            <button style={{ ...primaryBtn, background: "#B91C1C" }} disabled={cancelReason.trim().length < 15 || doCancel.isPending} onClick={() => doCancel.mutate({ id: cancel.id, reason: cancelReason.trim() })}>Cancel test</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
