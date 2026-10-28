// Engagements: team, planning, sampling and tests, findings, workpapers and report sign-off.

import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import ActionPlansPanel from "./ActionPlansPanel"
import ReviewNotesPanel from "./ReviewNotesPanel"
import {
  ClipboardList, Plus, ChevronDown, ChevronUp, Users2, UserPlus, FileText, Upload, Trash2, RotateCcw, Eye, Folder,
} from "lucide-react"
import { EngagementFilters } from "./EngagementFilters"
import { StagePill } from "./auditUi"
import { engagementStage, filterEngagements, shortDate, type Group } from "./auditUi.logic"
import { type UniverseEntry, type AnnualPlan, type Engagement, type UserOption, type WorkpaperFolder, type WorkpaperFile, type AuditExceptionResp, type FindingResp, type ReportSignOffStatus, type SamplingPlanResp, ENGAGEMENT_ROLES, ALLOWED_WP_TYPES, MAX_WP_BYTES, WP_STATUS_CFG, lbl, inp, cancelBtn, submitBtn, modalOverlay, modalBox } from "./auditShared"

export function EngagementsSection({ engagements, universe, plans, users, isLoading, qc }: {
  engagements: Engagement[]; universe: UniverseEntry[]; plans: AnnualPlan[]; users: UserOption[]
  isLoading: boolean; qc: ReturnType<typeof useQueryClient>
}) {
  const [error, setError] = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [stage, setStage] = useState<Group | "all">("all")
  const [search, setSearch] = useState("")
  const [showNew, setShowNew] = useState(false)
  const [form, setForm] = useState({ universeEntryId: "", planEntryId: "", name: "", startDate: "", endDate: "" })
  const [showAssign, setShowAssign] = useState<string | null>(null)
  const [assignForm, setAssignForm] = useState({ userId: "", role: "AUDITOR" })

  // Phase 2 — sub-tabs within the expanded engagement (Team already
  // existed; Planning and Workpapers are new). Only one engagement can
  // be expanded at a time (the existing accordion pattern), so a single
  // sub-tab state is enough — reset to "team" whenever a different
  // engagement is expanded.
  const [engagementSubTab, setEngagementSubTab] = useState<"team" | "planning" | "workpapers" | "sampling" | "findings">("team")
  const [planningForm, setPlanningForm] = useState({ objectives: "", scope: "", auditCriteria: "", overallMateriality: "", performanceMateriality: "", clearlyTrivialThreshold: "" })
  const [showAddMateriality, setShowAddMateriality] = useState(false)
  const [materialityForm, setMaterialityForm] = useState({ accountOrGlSegment: "", threshold: "" })
  const [selectedFolder, setSelectedFolder] = useState<string | null>(null)
  const [showNewFolder, setShowNewFolder] = useState(false)
  const [folderForm, setFolderForm] = useState({ name: "", folderType: "GENERAL" })
  const [showDeletedFiles, setShowDeletedFiles] = useState(false)
  const [uploadFileName, setUploadFileName] = useState("")

  // Phase 3 — sampling & testing
  const [expandedPlan, setExpandedPlan] = useState<string | null>(null)
  const [showNewPlan, setShowNewPlan] = useState(false)
  const [planForm, setPlanForm] = useState({
    samplingObjective: "", riskLevel: "MEDIUM", expectedErrorRate: "", tolerableErrorRate: "",
    sampleSize: 25, selectionMethod: "RANDOM", samplePeriodFrom: "", samplePeriodTo: "", exclusions: "", rationale: "",
  })
  const [showNewTest, setShowNewTest] = useState<string | null>(null) // sampleItemId
  const [testProcedure, setTestProcedure] = useState("")
  const [showRecordResult, setShowRecordResult] = useState<string | null>(null) // testId
  const [resultForm, setResultForm] = useState({ result: "PASS", notes: "" })
  const [showRaiseException, setShowRaiseException] = useState<string | null>(null) // testId
  const [exceptionForm, setExceptionForm] = useState({ description: "", severity: "MEDIUM" })

  const samplingPlansQuery = useQuery<SamplingPlanResp[]>({
    queryKey: ["ia-sampling-plans", expanded],
    queryFn: async () => (await apiClient.get(`/api/v1/internal-audit/engagements/${expanded}/sampling-plans`)).data,
    enabled: !!expanded && engagementSubTab === "sampling",
  })
  const exceptionsQuery = useQuery<AuditExceptionResp[]>({
    queryKey: ["ia-exceptions", expanded],
    queryFn: async () => (await apiClient.get(`/api/v1/internal-audit/engagements/${expanded}/exceptions`)).data,
    enabled: !!expanded && engagementSubTab === "sampling",
  })

  const createSamplingPlan = useMutation({
    mutationFn: (engagementId: string) => apiClient.post(`/api/v1/internal-audit/engagements/${engagementId}/sampling-plans`, {
      ...planForm,
      expectedErrorRate: planForm.expectedErrorRate ? Number(planForm.expectedErrorRate) : null,
      tolerableErrorRate: planForm.tolerableErrorRate ? Number(planForm.tolerableErrorRate) : null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-sampling-plans", expanded] }); setShowNewPlan(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create sampling plan"),
  })
  const reviewPlan = useMutation({
    mutationFn: (planId: string) => apiClient.post(`/api/v1/internal-audit/sampling-plans/${planId}/review`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-sampling-plans", expanded] }),
  })
  const createTest = useMutation({
    mutationFn: (sampleItemId: string) => apiClient.post(`/api/v1/internal-audit/sample-items/${sampleItemId}/tests`, { procedure: testProcedure }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-sampling-plans", expanded] }); setShowNewTest(null); setTestProcedure(""); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create test"),
  })
  const recordResult = useMutation({
    mutationFn: (testId: string) => apiClient.post(`/api/v1/internal-audit/tests/${testId}/result`, resultForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-sampling-plans", expanded] }); setShowRecordResult(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to record result"),
  })
  const raiseException = useMutation({
    mutationFn: (testId: string) => apiClient.post(`/api/v1/internal-audit/tests/${testId}/exceptions`, exceptionForm),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ia-sampling-plans", expanded] })
      qc.invalidateQueries({ queryKey: ["ia-exceptions", expanded] })
      setShowRaiseException(null); setExceptionForm({ description: "", severity: "MEDIUM" }); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to raise exception"),
  })
  const dismissException = useMutation({
    mutationFn: ({ id, resolutionNotes }: { id: string; resolutionNotes: string }) =>
      apiClient.post(`/api/v1/internal-audit/exceptions/${id}/dismiss`, { resolutionNotes }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-exceptions", expanded] }); qc.invalidateQueries({ queryKey: ["ia-sampling-plans", expanded] }) },
  })

  // Phase 4 — findings, remediation, report sign-off
  const [showNewFinding, setShowNewFinding] = useState(false)
  const [findingForm, setFindingForm] = useState({ sourceExceptionId: "", title: "", description: "", rootCause: "", recommendation: "", severity: "MEDIUM", owner: "", dueDate: "", criteria: "", condition: "", consequence: "" })
  const [showMgmtResponse, setShowMgmtResponse] = useState<string | null>(null)
  const [mgmtResponseText, setMgmtResponseText] = useState("")

  const findingsQuery = useQuery<FindingResp[]>({
    queryKey: ["ia-findings", expanded],
    queryFn: async () => (await apiClient.get(`/api/v1/internal-audit/engagements/${expanded}/findings`)).data,
    enabled: !!expanded && engagementSubTab === "findings",
  })
  const reportStatusQuery = useQuery<ReportSignOffStatus | null>({
    queryKey: ["ia-report-status", expanded],
    queryFn: async () => (await apiClient.get(`/api/v1/internal-audit/engagements/${expanded}/report/status`)).data,
    enabled: !!expanded && engagementSubTab === "findings",
  })

  const openExceptionsForFinding = exceptionsQuery.data?.filter(ex => ex.status === "OPEN") ?? []

  const createFinding = useMutation({
    mutationFn: (engagementId: string) => apiClient.post(`/api/v1/internal-audit/engagements/${engagementId}/findings`, {
      ...findingForm, criteria: findingForm.criteria || null, condition: findingForm.condition || null, consequence: findingForm.consequence || null, sourceExceptionId: findingForm.sourceExceptionId || null, owner: findingForm.owner || null, dueDate: findingForm.dueDate || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ia-findings", expanded] })
      qc.invalidateQueries({ queryKey: ["ia-exceptions", expanded] })
      setShowNewFinding(false); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to raise finding"),
  })
  const recordMgmtResponse = useMutation({
    mutationFn: (findingId: string) => apiClient.post(`/api/v1/internal-audit/findings/${findingId}/management-response`, { response: mgmtResponseText }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-findings", expanded] }); setShowMgmtResponse(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to record response"),
  })
  const resolveFinding = useMutation({
    mutationFn: (findingId: string) => apiClient.post(`/api/v1/internal-audit/findings/${findingId}/resolve`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-findings", expanded] }),
  })
  const closeFinding = useMutation({
    mutationFn: (findingId: string) => apiClient.post(`/api/v1/internal-audit/findings/${findingId}/close`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-findings", expanded] }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to close finding"),
  })
  const reopenFinding = useMutation({
    mutationFn: (findingId: string) => apiClient.post(`/api/v1/internal-audit/findings/${findingId}/reopen`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-findings", expanded] }),
  })
  const submitReport = useMutation({
    mutationFn: (engagementId: string) => apiClient.post(`/api/v1/internal-audit/engagements/${engagementId}/report/submit`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-report-status", expanded] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to submit report for sign-off"),
  })

  const amendReport = useMutation({
    mutationFn: ({ engagementId, reason }: { engagementId: string; reason: string }) =>
      apiClient.post(`/api/v1/internal-audit/engagements/${engagementId}/report/amend`, { reason }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-report-status", expanded] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to reopen the report"),
  })

  const updatePlanning = useMutation({
    mutationFn: (engagementId: string) => apiClient.put(`/api/v1/internal-audit/engagements/${engagementId}/planning`, {
      objectives: planningForm.objectives || null, scope: planningForm.scope || null, auditCriteria: planningForm.auditCriteria || null,
      overallMateriality: planningForm.overallMateriality ? Number(planningForm.overallMateriality) : null,
      performanceMateriality: planningForm.performanceMateriality ? Number(planningForm.performanceMateriality) : null,
      clearlyTrivialThreshold: planningForm.clearlyTrivialThreshold ? Number(planningForm.clearlyTrivialThreshold) : null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-engagements"] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to save planning detail"),
  })
  const addMateriality = useMutation({
    mutationFn: (engagementId: string) => apiClient.post(`/api/v1/internal-audit/engagements/${engagementId}/specific-materiality`, {
      accountOrGlSegment: materialityForm.accountOrGlSegment, threshold: Number(materialityForm.threshold),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-engagements"] }); setShowAddMateriality(false); setMaterialityForm({ accountOrGlSegment: "", threshold: "" }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to add materiality threshold"),
  })

  const foldersQuery = useQuery<WorkpaperFolder[]>({
    queryKey: ["ia-wp-folders", expanded],
    queryFn: async () => (await apiClient.get(`/api/v1/internal-audit/engagements/${expanded}/workpaper-folders`)).data,
    enabled: !!expanded && engagementSubTab === "workpapers",
  })
  const filesQuery = useQuery<WorkpaperFile[]>({
    queryKey: ["ia-wp-files", selectedFolder, showDeletedFiles],
    queryFn: async () => (await apiClient.get(
      `/api/v1/internal-audit/engagements/${expanded}/workpaper-folders/${selectedFolder}/files${showDeletedFiles ? "/deleted" : ""}`
    )).data,
    enabled: !!expanded && !!selectedFolder,
  })
  const createFolder = useMutation({
    mutationFn: (engagementId: string) => apiClient.post(`/api/v1/internal-audit/engagements/${engagementId}/workpaper-folders`, { ...folderForm, sortOrder: 0 }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-wp-folders", expanded] }); setShowNewFolder(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create folder"),
  })
  const uploadFile = useMutation({
    mutationFn: (body: { folderId: string; fileName: string; mimeType: string; fileSizeBytes: number; fileContentBase64: string }) =>
      apiClient.post(`/api/v1/internal-audit/engagements/${expanded}/workpaper-files`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-wp-files", selectedFolder, showDeletedFiles] }); setUploadFileName(""); setError("") },
    onError: (e: any) => { setError(e.response?.data?.message ?? "Failed to upload file"); setUploadFileName("") },
  })
  const updateFileStatus = useMutation({
    mutationFn: ({ fileId, action }: { fileId: string; action: string }) =>
      apiClient.post(`/api/v1/internal-audit/workpaper-files/${fileId}/status`, { action }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-wp-files", selectedFolder, showDeletedFiles] }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to update file status"),
  })
  const deleteFile = useMutation({
    mutationFn: (fileId: string) => apiClient.delete(`/api/v1/internal-audit/workpaper-files/${fileId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-wp-files", selectedFolder, showDeletedFiles] }),
  })
  const restoreFile = useMutation({
    mutationFn: (fileId: string) => apiClient.post(`/api/v1/internal-audit/workpaper-files/${fileId}/restore`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ia-wp-files", selectedFolder, showDeletedFiles] }),
  })

  const handleFileUpload = (file: File) => {
    setError("")
    if (!ALLOWED_WP_TYPES.includes(file.type)) { setError("Unsupported file type — please upload a PDF, JPG, PNG, Word, or Excel document"); return }
    if (file.size > MAX_WP_BYTES) { setError(`File is too large — maximum is ${MAX_WP_BYTES / (1024 * 1024)}MB`); return }
    setUploadFileName(file.name)
    const reader = new FileReader()
    reader.onload = () => {
      const base64 = ((reader.result as string) || "").split(",")[1] ?? ""
      uploadFile.mutate({ folderId: selectedFolder!, fileName: file.name, mimeType: file.type || "application/octet-stream", fileSizeBytes: file.size, fileContentBase64: base64 })
    }
    reader.readAsDataURL(file)
  }

  const expandEngagement = (eng: Engagement) => {
    const opening = expanded !== eng.id
    setExpanded(opening ? eng.id : null)
    setEngagementSubTab("team")
    setSelectedFolder(null)
    if (opening) {
      setPlanningForm({
        objectives: eng.objectives ?? "", scope: eng.scope ?? "", auditCriteria: eng.auditCriteria ?? "",
        overallMateriality: eng.overallMateriality?.toString() ?? "", performanceMateriality: eng.performanceMateriality?.toString() ?? "",
        clearlyTrivialThreshold: eng.clearlyTrivialThreshold?.toString() ?? "",
      })
    }
  }

  const allPlanEntries = plans.flatMap(p => p.entries.map(e => ({ ...e, planYear: p.planYear })))

  const createEngagement = useMutation({
    mutationFn: () => apiClient.post("/api/v1/internal-audit/engagements", {
      universeEntryId: form.universeEntryId, planEntryId: form.planEntryId || null,
      name: form.name, startDate: form.startDate || null, endDate: form.endDate || null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-engagements"] }); setShowNew(false); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create engagement"),
  })
  const assignRole = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/internal-audit/engagements/${showAssign}/assignments`, assignForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ia-engagements"] }); setShowAssign(null); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to assign role"),
  })

  const visible = filterEngagements(engagements, { group: stage, q: search })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => { setForm({ universeEntryId: "", planEntryId: "", name: "", startDate: "", endDate: "" }); setShowNew(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={14} /> New Engagement
        </button>
      </div>
      {error && <div style={{ padding: "10px 14px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, color: "var(--hf-danger-text)", fontSize: 13, marginBottom: 14 }}>{error}</div>}

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 30, color: "var(--hf-text-faint)" }}>Loading…</div>
      ) : engagements.length === 0 ? (
        <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <ClipboardList size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
          No engagements yet.
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <EngagementFilters engagements={engagements} stage={stage} onStage={setStage} q={search} onQ={setSearch} />
          {visible.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-muted)", padding: "14px 4px" }}>No engagements match these filters.</div>}
          {visible.map(e => (
            <div key={e.id} style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 16px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <button onClick={() => expandEngagement(e)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}>
                    {expanded === e.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{e.name}</div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{e.universeEntryName}{!e.planEntryId && " · Ad-hoc"}</div>
                  </div>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{shortDate(e.startDate)} – {shortDate(e.endDate)}</span>
                  <StagePill label={engagementStage(e.status).label} group={engagementStage(e.status).group} />
                </div>
              </div>

              {expanded === e.id && (
                <div style={{ padding: "0 16px 16px" }}>
                  <div style={{ display: "flex", gap: 4, marginBottom: 12, paddingTop: 12, borderTop: "1px solid var(--hf-border-subtle)" }}>
                    {(["team", "planning", "workpapers", "sampling", "findings"] as const).map(st => (
                      <button key={st} onClick={() => setEngagementSubTab(st)}
                        style={{ padding: "6px 12px", borderRadius: 7, border: "none", background: engagementSubTab === st ? "var(--hf-info-soft)" : "transparent", color: engagementSubTab === st ? "var(--hf-primary-text)" : "var(--hf-text-faint)", fontWeight: engagementSubTab === st ? 700 : 500, fontSize: 12, cursor: "pointer", textTransform: "capitalize" as const }}>
                        {st}
                      </button>
                    ))}
                  </div>

                  {engagementSubTab === "team" && (
                    <div>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                        <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }}>Team — {e.assignments.length}</span>
                        <button onClick={() => { setAssignForm({ userId: "", role: "AUDITOR" }); setShowAssign(e.id); setError("") }}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid var(--hf-primary)", background: "var(--hf-info-soft)", color: "var(--hf-primary-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                          <UserPlus size={11} /> Assign
                        </button>
                      </div>
                      {e.assignments.length === 0 ? (
                        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No team members assigned yet.</p>
                      ) : (
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                          {e.assignments.map(a => (
                            <div key={a.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                              <Users2 size={13} style={{ color: 'var(--hf-violet-text)' }} />
                              <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{a.userName}</span>
                              <span style={{ color: "var(--hf-violet-text)", fontWeight: 600 }}>{a.role.replace(/_/g, " ")}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {engagementSubTab === "planning" && (
                    <div>
                      <div style={{ marginBottom: 12 }}>
                        <label htmlFor="ia-eng-1" style={lbl}>Objectives</label>
                        <textarea id="ia-eng-1" value={planningForm.objectives} onChange={ev => setPlanningForm(f => ({ ...f, objectives: ev.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
                      </div>
                      <div style={{ marginBottom: 12 }}>
                        <label htmlFor="ia-eng-2" style={lbl}>Scope</label>
                        <textarea id="ia-eng-2" value={planningForm.scope} onChange={ev => setPlanningForm(f => ({ ...f, scope: ev.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
                      </div>
                      <div style={{ marginBottom: 12 }}>
                        <label htmlFor="ia-eng-3" style={lbl}>Audit Criteria</label>
                        <textarea id="ia-eng-3" value={planningForm.auditCriteria} onChange={ev => setPlanningForm(f => ({ ...f, auditCriteria: ev.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
                      </div>
                      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10, marginBottom: 14 }}>
                        <div>
                          <label htmlFor="ia-eng-4" style={lbl}>Overall Materiality</label>
                          <input id="ia-eng-4" type="number" value={planningForm.overallMateriality} onChange={ev => setPlanningForm(f => ({ ...f, overallMateriality: ev.target.value }))} style={inp} />
                        </div>
                        <div>
                          <label htmlFor="ia-eng-5" style={lbl}>Performance Materiality</label>
                          <input id="ia-eng-5" type="number" value={planningForm.performanceMateriality} onChange={ev => setPlanningForm(f => ({ ...f, performanceMateriality: ev.target.value }))} style={inp} />
                        </div>
                        <div>
                          <label htmlFor="ia-eng-6" style={lbl}>Clearly Trivial</label>
                          <input id="ia-eng-6" type="number" value={planningForm.clearlyTrivialThreshold} onChange={ev => setPlanningForm(f => ({ ...f, clearlyTrivialThreshold: ev.target.value }))} style={inp} />
                        </div>
                      </div>
                      <button onClick={() => updatePlanning.mutate(e.id)} disabled={updatePlanning.isPending}
                        style={{ padding: "7px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer", marginBottom: 18 }}>
                        {updatePlanning.isPending ? "Saving…" : "Save Planning Detail"}
                      </button>

                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10, paddingTop: 14, borderTop: "1px solid var(--hf-border-subtle)" }}>
                        <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }}>Specific Materiality (optional)</span>
                        <button onClick={() => { setMaterialityForm({ accountOrGlSegment: "", threshold: "" }); setShowAddMateriality(true) }}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid var(--hf-primary)", background: "var(--hf-info-soft)", color: "var(--hf-primary-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                          <Plus size={11} /> Add
                        </button>
                      </div>
                      {e.specificMateriality.length === 0 ? (
                        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No account-specific thresholds — the engagement-level materiality above applies to everything by default.</p>
                      ) : (
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                          {e.specificMateriality.map(m => (
                            <div key={m.id} style={{ display: "flex", justifyContent: "space-between", padding: "8px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                              <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{m.accountOrGlSegment}</span>
                              <span style={{ color: "var(--hf-text-muted)" }}>R {m.threshold.toLocaleString()}</span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {engagementSubTab === "workpapers" && (
                    <div>
                      <div style={{ display: "flex", gap: 12 }}>
                        <div style={{ width: 180, flexShrink: 0 }}>
                          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                            <span style={{ fontSize: 10, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }}>Folders</span>
                            <button onClick={() => { setFolderForm({ name: "", folderType: "GENERAL" }); setShowNewFolder(true) }} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-primary-text)" }}><Plus size={13} /></button>
                          </div>
                          {foldersQuery.isLoading ? (
                            <p style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Loading…</p>
                          ) : !foldersQuery.data?.length ? (
                            <p style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>No folders yet.</p>
                          ) : (
                            <div style={{ display: "flex", flexDirection: "column", gap: 3 }}>
                              {foldersQuery.data.map(f => (
                                <button key={f.id} onClick={() => { setSelectedFolder(f.id); setShowDeletedFiles(false) }}
                                  style={{ display: "flex", alignItems: "center", gap: 6, padding: "6px 8px", borderRadius: 6, border: "none", background: selectedFolder === f.id ? "var(--hf-info-soft)" : "transparent", color: selectedFolder === f.id ? "var(--hf-primary-text)" : "var(--hf-text-secondary)", fontSize: 12, fontWeight: selectedFolder === f.id ? 700 : 500, cursor: "pointer", textAlign: "left" as const }}>
                                  <Folder size={12} /> {f.name}
                                </button>
                              ))}
                            </div>
                          )}
                        </div>

                        <div style={{ flex: 1, borderLeft: "1px solid var(--hf-border-subtle)", paddingLeft: 14 }}>
                          {!selectedFolder ? (
                            <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Select a folder to view its files.</p>
                          ) : (
                            <div>
                              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                                <button onClick={() => setShowDeletedFiles(v => !v)}
                                  style={{ fontSize: 11, color: "var(--hf-text-muted)", background: "none", border: "1px solid var(--hf-border)", borderRadius: 6, padding: "4px 10px", cursor: "pointer" }}>
                                  {showDeletedFiles ? "Show Active" : "Show Deleted"}
                                </button>
                                {!showDeletedFiles && (
                                  <label style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 11, fontWeight: 600, color: "var(--hf-primary-text)", cursor: "pointer" }}>
                                    <Upload size={12} /> {uploadFile.isPending ? `Uploading ${uploadFileName}…` : "Upload File"}
                                    <input type="file" style={{ display: "none" }} onChange={ev => { const f = ev.target.files?.[0]; if (f) handleFileUpload(f); ev.target.value = "" }} />
                                  </label>
                                )}
                              </div>
                              {filesQuery.isLoading ? (
                                <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Loading…</p>
                              ) : !filesQuery.data?.length ? (
                                <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{showDeletedFiles ? "No deleted files." : "No files in this folder yet."}</p>
                              ) : (
                                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                                  {filesQuery.data.map(f => {
                                    const sc = WP_STATUS_CFG[f.reviewStatus] ?? WP_STATUS_CFG.DRAFT
                                    return (
                                      <div key={f.id}><div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                                        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                          <FileText size={13} style={{ color: 'var(--hf-text-faint)' }} />
                                          <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{f.fileName}</span>
                                          <span style={{ color: "var(--hf-text-disabled)" }}>v{f.versionNumber}</span>
                                          <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: sc.bg, color: sc.color }}>{sc.label}</span>
                                        </div>
                                        <div style={{ display: "flex", gap: 6 }}>
                                          {showDeletedFiles ? (
                                            <button onClick={() => restoreFile.mutate(f.id)} title="Restore" style={{ padding: "4px 6px", background: "var(--hf-success-soft-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 6, cursor: "pointer", color: "var(--hf-success-text-strong)" }}><RotateCcw size={12} /></button>
                                          ) : (
                                            <>
                                              {f.reviewStatus === "DRAFT" && <button onClick={() => updateFileStatus.mutate({ fileId: f.id, action: "PREPARE" })} style={{ padding: "4px 8px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 11, cursor: "pointer" }}>Prepare</button>}
                                              {f.reviewStatus === "PREPARED" && <button onClick={() => updateFileStatus.mutate({ fileId: f.id, action: "REVIEW" })} style={{ padding: "4px 8px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 11, cursor: "pointer" }}>Review</button>}
                                              {f.reviewStatus === "REVIEWED" && <button onClick={() => updateFileStatus.mutate({ fileId: f.id, action: "SIGN_OFF" })} style={{ padding: "4px 8px", borderRadius: 6, border: "1px solid var(--hf-success-border)", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", fontSize: 11, cursor: "pointer" }}>Sign Off</button>}
                                              {f.reviewStatus !== "DRAFT" && <button onClick={() => updateFileStatus.mutate({ fileId: f.id, action: "REOPEN" })} title="Reopen" style={{ padding: "4px 6px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 6, cursor: "pointer", color: "var(--hf-text-faint)" }}><Eye size={12} /></button>}
                                              <button onClick={() => deleteFile.mutate(f.id)} title="Delete" style={{ padding: "4px 6px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 6, cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={12} /></button>
                                            </>
                                          )}
                                        </div>
                                      </div>
                                      {!showDeletedFiles && <ReviewNotesPanel workpaperId={f.id} />}
                                      </div>
                                    )
                                  })}
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  )}

                  {engagementSubTab === "sampling" && (
                    <div>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                        <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }}>Sampling Plans — {samplingPlansQuery.data?.length ?? 0}</span>
                        <button onClick={() => { setPlanForm({ samplingObjective: "", riskLevel: "MEDIUM", expectedErrorRate: "", tolerableErrorRate: "", sampleSize: 25, selectionMethod: "RANDOM", samplePeriodFrom: "", samplePeriodTo: "", exclusions: "", rationale: "" }); setShowNewPlan(true); setError("") }}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid var(--hf-primary)", background: "var(--hf-info-soft)", color: "var(--hf-primary-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                          <Plus size={11} /> New Sampling Plan
                        </button>
                      </div>

                      {samplingPlansQuery.isLoading ? (
                        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Loading…</p>
                      ) : !samplingPlansQuery.data?.length ? (
                        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No sampling plans yet.</p>
                      ) : (
                        <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 18 }}>
                          {samplingPlansQuery.data.map(p => (
                            <div key={p.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 8 }}>
                              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 12px", cursor: "pointer" }} onClick={() => setExpandedPlan(expandedPlan === p.id ? null : p.id)}>
                                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                  {expandedPlan === p.id ? <ChevronUp size={13} style={{ color: 'var(--hf-text-faint)' }} /> : <ChevronDown size={13} style={{ color: 'var(--hf-text-faint)' }} />}
                                  <span style={{ fontWeight: 700, fontSize: 12, color: "var(--hf-text)" }}>{p.samplePeriodFrom} to {p.samplePeriodTo}</span>
                                  <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{p.sampleSize} of {p.population} entries</span>
                                </div>
                                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                  <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: p.status === "FINALIZED" ? "var(--hf-success-soft-strong)" : "var(--hf-surface-sunken)", color: p.status === "FINALIZED" ? "var(--hf-success-text-strong)" : "var(--hf-text-muted)" }}>{p.status}</span>
                                  {p.status === "DRAFT" && (
                                    <button onClick={ev => { ev.stopPropagation(); reviewPlan.mutate(p.id) }}
                                      style={{ padding: "3px 8px", borderRadius: 6, border: "1px solid var(--hf-success-border)", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", fontSize: 10, fontWeight: 600, cursor: "pointer" }}>
                                      Review & Finalize
                                    </button>
                                  )}
                                </div>
                              </div>

                              {expandedPlan === p.id && (
                                <div style={{ padding: "0 12px 12px", borderTop: "1px solid var(--hf-border-subtle)" }}>
                                  {p.rationale && <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: "10px 0" }}>{p.rationale}</p>}
                                  {p.populationDefinition && (
                                    <div data-testid="sampling-basis" style={{ fontSize: 11, color: "var(--hf-text-muted)", margin: "8px 0", padding: "8px 10px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 7 }}>
                                      <div><b>Population:</b> {p.populationDefinition}</div>
                                      {p.selectionSeed != null && (
                                        <div style={{ marginTop: 4 }}><b>Method:</b> {p.selectionMethod}{p.selectionStart != null ? `, starting at position ${p.selectionStart + 1}` : ""} · <b>Seed:</b> {p.selectionSeed} (the same population and seed give the same sample)</div>
                                      )}
                                    </div>
                                  )}
                                  <div style={{ display: "flex", flexDirection: "column", gap: 6, marginTop: 8 }}>
                                    {p.items.map(item => (
                                      <div key={item.id} style={{ background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 7, padding: "8px 10px" }}>
                                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: item.tests.length ? 6 : 0 }}>
                                          <span style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text)" }}>{item.entryNumberSnapshot} — {item.entryDateSnapshot}</span>
                                          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                            <span style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>R {item.amountSnapshot?.toLocaleString() ?? "—"}</span>
                                            <button onClick={() => { setTestProcedure(""); setShowNewTest(item.id) }}
                                              style={{ padding: "3px 8px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 10, fontWeight: 600, cursor: "pointer" }}>
                                              + Test
                                            </button>
                                          </div>
                                        </div>
                                        {item.tests.map(t => (
                                          <div key={t.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "5px 8px", background: "var(--hf-surface)", border: "1px solid var(--hf-border-subtle)", borderRadius: 6, fontSize: 11, marginTop: 4 }}>
                                            <span style={{ color: "var(--hf-text-secondary)" }}>{t.procedure}</span>
                                            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                                              <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 7px", borderRadius: 20, background: t.result === "PASS" ? "var(--hf-success-soft-strong)" : t.result === "PENDING" ? "var(--hf-surface-sunken)" : "var(--hf-danger-soft)", color: t.result === "PASS" ? "var(--hf-success-text-strong)" : t.result === "PENDING" ? "var(--hf-text-muted)" : "var(--hf-danger-text)" }}>{t.result}</span>
                                              {t.result === "PENDING" && (
                                                <button onClick={() => { setResultForm({ result: "PASS", notes: "" }); setShowRecordResult(t.id) }}
                                                  style={{ padding: "2px 7px", borderRadius: 5, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 10, cursor: "pointer" }}>
                                                  Record
                                                </button>
                                              )}
                                              {(t.result === "FAIL" || t.result === "EXCEPTION") && t.exceptions.length === 0 && (
                                                <button onClick={() => { setExceptionForm({ description: "", severity: "MEDIUM" }); setShowRaiseException(t.id) }}
                                                  style={{ padding: "2px 7px", borderRadius: 5, border: "1px solid var(--hf-danger-border)", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", fontSize: 10, cursor: "pointer" }}>
                                                  Raise Exception
                                                </button>
                                              )}
                                            </div>
                                          </div>
                                        ))}
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      )}

                      <div style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const, marginBottom: 8, paddingTop: 12, borderTop: "1px solid var(--hf-border-subtle)" }}>
                        Exceptions — {exceptionsQuery.data?.length ?? 0}
                      </div>
                      {!exceptionsQuery.data?.length ? (
                        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No exceptions raised for this engagement.</p>
                      ) : (
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                          {exceptionsQuery.data.map(ex => {
                            const sevColor = ex.severity === "CRITICAL" || ex.severity === "HIGH" ? "var(--hf-danger-text)" : ex.severity === "MEDIUM" ? "var(--hf-warning-text-strong)" : "var(--hf-text-muted)"
                            const sevBg = ex.severity === "CRITICAL" || ex.severity === "HIGH" ? "var(--hf-danger-soft)" : ex.severity === "MEDIUM" ? "var(--hf-warning-soft)" : "var(--hf-surface-sunken)"
                            return (
                              <div key={ex.id} style={{ padding: "9px 12px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
                                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 3 }}>
                                  <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: sevBg, color: sevColor }}>{ex.severity}</span>
                                  <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: ex.status === "OPEN" ? "var(--hf-warning-soft)" : "var(--hf-surface-sunken)", color: ex.status === "OPEN" ? "var(--hf-warning-text-strong)" : "var(--hf-text-muted)" }}>{ex.status}</span>
                                </div>
                                <p style={{ margin: 0, color: "var(--hf-text-secondary)" }}>{ex.description}</p>
                                {ex.resolutionNotes && <p style={{ margin: "4px 0 0", color: "var(--hf-text-faint)", fontStyle: "italic" }}>{ex.resolutionNotes}</p>}
                                {ex.status === "OPEN" && (
                                  <button onClick={() => { const notes = prompt("Resolution notes?"); if (notes) dismissException.mutate({ id: ex.id, resolutionNotes: notes }) }}
                                    style={{ marginTop: 6, padding: "3px 9px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 10, fontWeight: 600, cursor: "pointer" }}>
                                    Dismiss
                                  </button>
                                )}
                              </div>
                            )
                          })}
                        </div>
                      )}
                    </div>
                  )}

                  {engagementSubTab === "findings" && (
                    <div>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                        <div>
                          <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }}>Report Sign-off</span>
                          {reportStatusQuery.data ? (
                            <div style={{ marginTop: 4, display: "flex", alignItems: "center", gap: 8 }}>
                              <span style={{ fontSize: 11, fontWeight: 700, padding: "2px 9px", borderRadius: 20, background: reportStatusQuery.data.status === "APPROVED" ? "var(--hf-success-soft-strong)" : reportStatusQuery.data.status === "REJECTED" ? "var(--hf-danger-soft)" : "var(--hf-warning-soft)", color: reportStatusQuery.data.status === "APPROVED" ? "var(--hf-success-text-strong)" : reportStatusQuery.data.status === "REJECTED" ? "var(--hf-danger-text)" : "var(--hf-warning-text-strong)" }}>{reportStatusQuery.data.status}</span>
                            </div>
                          ) : (
                            <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 4 }}>Not yet submitted for sign-off.</div>
                          )}
                          {reportStatusQuery.data?.locked && (
                            <div style={{ marginTop: 6, fontSize: 11.5, color: "var(--hf-warning-text-strong)" }}>
                              Findings and workpapers are locked while the report is out for sign-off or approved.{" "}
                              <button onClick={() => { const reason = window.prompt("Why is this report being reopened? (at least 10 characters)"); if (reason) amendReport.mutate({ engagementId: e.id, reason }) }}
                                disabled={amendReport.isPending}
                                style={{ background: "none", border: "none", padding: 0, color: "var(--hf-primary)", fontWeight: 700, cursor: "pointer", fontSize: 11.5 }}>
                                Reopen for amendment
                              </button>
                            </div>
                          )}
                        </div>
                        <button onClick={() => submitReport.mutate(e.id)} disabled={submitReport.isPending}
                          style={{ padding: "7px 14px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          {submitReport.isPending ? "Submitting…" : reportStatusQuery.data ? "Resubmit for Sign-off" : "Submit for Sign-off"}
                        </button>
                      </div>

                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10, paddingTop: 12, borderTop: "1px solid var(--hf-border-subtle)" }}>
                        <span style={{ fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase" as const }}>Findings — {findingsQuery.data?.length ?? 0}</span>
                        <button onClick={() => { setFindingForm({ sourceExceptionId: "", title: "", description: "", rootCause: "", recommendation: "", severity: "MEDIUM", owner: "", dueDate: "", criteria: "", condition: "", consequence: "" }); setShowNewFinding(true); setError("") }}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "5px 10px", borderRadius: 6, border: "1px solid var(--hf-primary)", background: "var(--hf-info-soft)", color: "var(--hf-primary-text)", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                          <Plus size={11} /> Raise Finding
                        </button>
                      </div>

                      {findingsQuery.isLoading ? (
                        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Loading…</p>
                      ) : !findingsQuery.data?.length ? (
                        <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No findings raised for this engagement yet.</p>
                      ) : (
                        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                          {findingsQuery.data.map(f => {
                            const sevColor = f.severity === "CRITICAL" || f.severity === "HIGH" ? "var(--hf-danger-text)" : f.severity === "MEDIUM" ? "var(--hf-warning-text-strong)" : "var(--hf-text-muted)"
                            const sevBg = f.severity === "CRITICAL" || f.severity === "HIGH" ? "var(--hf-danger-soft)" : f.severity === "MEDIUM" ? "var(--hf-warning-soft)" : "var(--hf-surface-sunken)"
                            const statusColor = f.status === "CLOSED" ? "var(--hf-success-text-strong)" : f.status === "RESOLVED" ? "var(--hf-info-text)" : f.status === "IN_PROGRESS" ? "var(--hf-warning-text-strong)" : "var(--hf-text-muted)"
                            const statusBg = f.status === "CLOSED" ? "var(--hf-success-soft-strong)" : f.status === "RESOLVED" ? "var(--hf-info-soft)" : f.status === "IN_PROGRESS" ? "var(--hf-warning-soft)" : "var(--hf-surface-sunken)"
                            return (
                              <div key={f.id} style={{ padding: "12px 14px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, fontSize: 12 }}>
                                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 6 }}>
                                  <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{f.title}</span>
                                  <div style={{ display: "flex", gap: 6 }}>
                                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: sevBg, color: sevColor }}>{f.severity}</span>
                                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 20, background: statusBg, color: statusColor }}>{f.status.replace(/_/g, " ")}</span>
                                  </div>
                                </div>
                                <p style={{ margin: "0 0 6px", color: "var(--hf-text-secondary)" }}>{f.description}</p>
                                {f.criteria && <p style={{ margin: "0 0 4px", color: "var(--hf-text-faint)" }}><strong>Criteria (what should be):</strong> {f.criteria}</p>}
                                {f.condition && <p style={{ margin: "0 0 4px", color: "var(--hf-text-faint)" }}><strong>Condition (what was found):</strong> {f.condition}</p>}
                                {f.consequence && <p style={{ margin: "0 0 4px", color: "var(--hf-text-faint)" }}><strong>Consequence (why it matters):</strong> {f.consequence}</p>}
                                {f.rootCause && <p style={{ margin: "0 0 4px", color: "var(--hf-text-faint)" }}><strong>Root cause:</strong> {f.rootCause}</p>}
                                {f.recommendation && <p style={{ margin: "0 0 4px", color: "var(--hf-text-faint)" }}><strong>Recommendation:</strong> {f.recommendation}</p>}
                                {f.managementResponse && <p style={{ margin: "0 0 4px", color: "var(--hf-info-text)" }}><strong>Management response:</strong> {f.managementResponse}</p>}
                                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 8 }}>
                                  <span style={{ color: "var(--hf-text-faint)" }}>
                                    {f.ownerName && `Owner: ${f.ownerName}`}{f.dueDate && ` · Due ${f.dueDate}`}
                                  </span>
                                  <div style={{ display: "flex", gap: 6 }}>
                                    {(f.status === "OPEN" || f.status === "IN_PROGRESS") && !f.managementResponse && (
                                      <button onClick={() => { setMgmtResponseText(""); setShowMgmtResponse(f.id) }}
                                        style={{ padding: "3px 9px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 10, fontWeight: 600, cursor: "pointer" }}>
                                        Add Management Response
                                      </button>
                                    )}
                                    {f.status === "IN_PROGRESS" && (
                                      <button onClick={() => resolveFinding.mutate(f.id)}
                                        style={{ padding: "3px 9px", borderRadius: 6, border: "1px solid var(--hf-info-border)", background: "var(--hf-info-soft)", color: "var(--hf-info-text)", fontSize: 10, fontWeight: 600, cursor: "pointer" }}>
                                        Mark Resolved
                                      </button>
                                    )}
                                    {f.status === "RESOLVED" && (
                                      <>
                                        <button onClick={() => reopenFinding.mutate(f.id)}
                                          style={{ padding: "3px 9px", borderRadius: 6, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text-muted)", fontSize: 10, fontWeight: 600, cursor: "pointer" }}>
                                          Reopen
                                        </button>
                                        <button onClick={() => closeFinding.mutate(f.id)}
                                          style={{ padding: "3px 9px", borderRadius: 6, border: "1px solid var(--hf-success-border)", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", fontSize: 10, fontWeight: 600, cursor: "pointer" }}>
                                          Close
                                        </button>
                                      </>
                                    )}
                                  </div>
                                </div>
                                <ActionPlansPanel findingId={f.id} findingClosed={f.status === "CLOSED"} users={users} />
                              </div>
                            )
                          })}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {showNew && (
        <div style={modalOverlay}>
          <div role="dialog" aria-modal="true" style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>New Engagement</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-7" style={lbl}>Name *</label>
              <input id="ia-eng-7" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value }))} placeholder="2026 Payroll Audit" style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-8" style={lbl}>Universe Entry *</label>
              <select id="ia-eng-8" value={form.universeEntryId} onChange={e => setForm(f => ({ ...f, universeEntryId: e.target.value }))} style={inp}>
                <option value="">Select…</option>
                {universe.map(u => <option key={u.id} value={u.id}>{u.name}</option>)}
              </select>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-9" style={lbl}>Plan Entry (optional — leave blank for an ad-hoc engagement)</label>
              <select id="ia-eng-9" value={form.planEntryId} onChange={e => setForm(f => ({ ...f, planEntryId: e.target.value }))} style={inp}>
                <option value="">None (ad-hoc)</option>
                {allPlanEntries.map(pe => <option key={pe.id} value={pe.id}>{pe.planYear} — {pe.universeEntryName}</option>)}
              </select>
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-10" style={lbl}>Start Date</label>
                <input id="ia-eng-10" type="date" value={form.startDate} onChange={e => setForm(f => ({ ...f, startDate: e.target.value }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-11" style={lbl}>End Date</label>
                <input id="ia-eng-11" type="date" value={form.endDate} onChange={e => setForm(f => ({ ...f, endDate: e.target.value }))} style={inp} />
              </div>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNew(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createEngagement.mutate()} disabled={!form.name.trim() || !form.universeEntryId || createEngagement.isPending} style={submitBtn}>
                {createEngagement.isPending ? "Creating…" : "Create"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showAssign && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 380 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Assign Engagement Role</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-12" style={lbl}>User *</label>
              <select id="ia-eng-12" value={assignForm.userId} onChange={e => setAssignForm(f => ({ ...f, userId: e.target.value }))} style={inp}>
                <option value="">Select…</option>
                {users.map(u => <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>)}
              </select>
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-eng-13" style={lbl}>Role</label>
              <select id="ia-eng-13" value={assignForm.role} onChange={e => setAssignForm(f => ({ ...f, role: e.target.value }))} style={inp}>
                {ENGAGEMENT_ROLES.map(r => <option key={r} value={r}>{r.replace(/_/g, " ")}</option>)}
              </select>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowAssign(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => assignRole.mutate()} disabled={!assignForm.userId || assignRole.isPending} style={submitBtn}>
                {assignRole.isPending ? "Assigning…" : "Assign"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showNewFolder && expanded && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 360 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>New Folder</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-14" style={lbl}>Name *</label>
              <input id="ia-eng-14" value={folderForm.name} onChange={e => setFolderForm(f => ({ ...f, name: e.target.value }))} placeholder="Fieldwork" style={inp} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-eng-15" style={lbl}>Type</label>
              <select id="ia-eng-15" value={folderForm.folderType} onChange={e => setFolderForm(f => ({ ...f, folderType: e.target.value }))} style={inp}>
                <option value="PLANNING">Planning</option><option value="FIELDWORK">Fieldwork</option>
                <option value="SAMPLING">Sampling</option><option value="FINDINGS">Findings</option>
                <option value="REPORTING">Reporting</option><option value="GENERAL">General</option>
              </select>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNewFolder(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createFolder.mutate(expanded)} disabled={!folderForm.name.trim() || createFolder.isPending} style={submitBtn}>
                {createFolder.isPending ? "Creating…" : "Create"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showAddMateriality && expanded && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 380 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Add Specific Materiality</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-16" style={lbl}>Account / GL Segment *</label>
              <input id="ia-eng-16" value={materialityForm.accountOrGlSegment} onChange={e => setMaterialityForm(f => ({ ...f, accountOrGlSegment: e.target.value }))} placeholder="Petty Cash" style={inp} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-eng-17" style={lbl}>Threshold *</label>
              <input id="ia-eng-17" type="number" value={materialityForm.threshold} onChange={e => setMaterialityForm(f => ({ ...f, threshold: e.target.value }))} style={inp} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowAddMateriality(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => addMateriality.mutate(expanded)} disabled={!materialityForm.accountOrGlSegment.trim() || !materialityForm.threshold || addMateriality.isPending} style={submitBtn}>
                {addMateriality.isPending ? "Adding…" : "Add"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showNewPlan && expanded && (
        <div style={modalOverlay}>
          <div role="dialog" aria-modal="true" style={modalBox}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>New Sampling Plan</h3>
            <p style={{ fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 16 }}>Population is calculated automatically from posted journal entries in the period below. The sample is drawn immediately using random selection.</p>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ display: "flex", gap: 12, marginBottom: 14 }}>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-18" style={lbl}>Period From *</label>
                <input id="ia-eng-18" type="date" value={planForm.samplePeriodFrom} onChange={e => setPlanForm(f => ({ ...f, samplePeriodFrom: e.target.value }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-19" style={lbl}>Period To *</label>
                <input id="ia-eng-19" type="date" value={planForm.samplePeriodTo} onChange={e => setPlanForm(f => ({ ...f, samplePeriodTo: e.target.value }))} style={inp} />
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-20" style={lbl}>Sampling Objective</label>
              <input id="ia-eng-20" value={planForm.samplingObjective} onChange={e => setPlanForm(f => ({ ...f, samplingObjective: e.target.value }))} placeholder="e.g. Test whether journal entries are properly authorised" style={inp} />
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 14 }}>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-21" style={lbl}>Sample Size *</label>
                <input id="ia-eng-21" type="number" min={1} value={planForm.sampleSize} onChange={e => setPlanForm(f => ({ ...f, sampleSize: Number(e.target.value) }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-22" style={lbl}>Selection Method</label>
                <select id="ia-eng-22" value={planForm.selectionMethod} onChange={e => setPlanForm(f => ({ ...f, selectionMethod: e.target.value }))} style={inp}>
                  <option value="RANDOM">Random</option><option value="SYSTEMATIC">Systematic</option>
                </select>
              </div>
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 14 }}>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-23" style={lbl}>Risk Level</label>
                <select id="ia-eng-23" value={planForm.riskLevel} onChange={e => setPlanForm(f => ({ ...f, riskLevel: e.target.value }))} style={inp}>
                  <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="CRITICAL">Critical</option>
                </select>
              </div>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-24" style={lbl}>Expected Error Rate (%)</label>
                <input id="ia-eng-24" type="number" value={planForm.expectedErrorRate} onChange={e => setPlanForm(f => ({ ...f, expectedErrorRate: e.target.value }))} style={inp} />
              </div>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-25" style={lbl}>Tolerable Error Rate (%)</label>
                <input id="ia-eng-25" type="number" value={planForm.tolerableErrorRate} onChange={e => setPlanForm(f => ({ ...f, tolerableErrorRate: e.target.value }))} style={inp} />
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-26" style={lbl}>Exclusions</label>
              <input id="ia-eng-26" value={planForm.exclusions} onChange={e => setPlanForm(f => ({ ...f, exclusions: e.target.value }))} placeholder="e.g. Reversing entries, intercompany eliminations" style={inp} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-eng-27" style={lbl}>Rationale</label>
              <textarea id="ia-eng-27" value={planForm.rationale} onChange={e => setPlanForm(f => ({ ...f, rationale: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNewPlan(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createSamplingPlan.mutate(expanded)} disabled={!planForm.samplePeriodFrom || !planForm.samplePeriodTo || !planForm.sampleSize || createSamplingPlan.isPending} style={submitBtn}>
                {createSamplingPlan.isPending ? "Drawing Sample…" : "Create & Draw Sample"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showNewTest && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 400 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>New Test</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <label htmlFor="ia-eng-28" style={lbl}>Procedure *</label>
            <textarea id="ia-eng-28" value={testProcedure} onChange={e => setTestProcedure(e.target.value)} rows={3}
              placeholder="e.g. Vouch to supporting documentation and confirm dual authorisation" style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 18 }}>
              <button onClick={() => setShowNewTest(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createTest.mutate(showNewTest)} disabled={!testProcedure.trim() || createTest.isPending} style={submitBtn}>
                {createTest.isPending ? "Adding…" : "Add Test"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showRecordResult && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 400 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Record Test Result</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-29" style={lbl}>Result</label>
              <select id="ia-eng-29" value={resultForm.result} onChange={e => setResultForm(f => ({ ...f, result: e.target.value }))} style={inp}>
                <option value="PASS">Pass</option><option value="FAIL">Fail</option><option value="EXCEPTION">Exception</option>
              </select>
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-eng-30" style={lbl}>Notes</label>
              <textarea id="ia-eng-30" value={resultForm.notes} onChange={e => setResultForm(f => ({ ...f, notes: e.target.value }))} rows={3} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowRecordResult(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => recordResult.mutate(showRecordResult)} disabled={recordResult.isPending} style={submitBtn}>
                {recordResult.isPending ? "Saving…" : "Save Result"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showRaiseException && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 400 }}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Raise Exception</h3>
            <p style={{ fontSize: 11, color: "var(--hf-text-faint)", marginBottom: 14 }}>Severity is independent of the engagement's risk level — assess it on its own terms.</p>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-31" style={lbl}>Description *</label>
              <textarea id="ia-eng-31" value={exceptionForm.description} onChange={e => setExceptionForm(f => ({ ...f, description: e.target.value }))} rows={3} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ marginBottom: 18 }}>
              <label htmlFor="ia-eng-32" style={lbl}>Severity</label>
              <select id="ia-eng-32" value={exceptionForm.severity} onChange={e => setExceptionForm(f => ({ ...f, severity: e.target.value }))} style={inp}>
                <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="CRITICAL">Critical</option>
              </select>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowRaiseException(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => raiseException.mutate(showRaiseException)} disabled={!exceptionForm.description.trim() || raiseException.isPending} style={submitBtn}>
                {raiseException.isPending ? "Raising…" : "Raise Exception"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showNewFinding && expanded && (
        <div style={modalOverlay}>
          <div role="dialog" aria-modal="true" style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Raise Finding</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            {openExceptionsForFinding.length > 0 && (
              <div style={{ marginBottom: 14 }}>
                <label htmlFor="ia-eng-33" style={lbl}>Promote an Open Exception (optional)</label>
                <select id="ia-eng-33" value={findingForm.sourceExceptionId} onChange={e => setFindingForm(f => ({ ...f, sourceExceptionId: e.target.value }))} style={inp}>
                  <option value="">None — raise directly</option>
                  {openExceptionsForFinding.map(ex => <option key={ex.id} value={ex.id}>{ex.description.slice(0, 60)}</option>)}
                </select>
              </div>
            )}
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-34" style={lbl}>Title *</label>
              <input id="ia-eng-34" value={findingForm.title} onChange={e => setFindingForm(f => ({ ...f, title: e.target.value }))} style={inp} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-35" style={lbl}>Description *</label>
              <textarea id="ia-eng-35" value={findingForm.description} onChange={e => setFindingForm(f => ({ ...f, description: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-36" style={lbl}>Criteria <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>(what should be the case: policy, standard, law)</span></label>
              <textarea id="ia-eng-36" value={findingForm.criteria} onChange={e => setFindingForm(f => ({ ...f, criteria: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-37" style={lbl}>Condition <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>(what was actually found)</span></label>
              <textarea id="ia-eng-37" value={findingForm.condition} onChange={e => setFindingForm(f => ({ ...f, condition: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-38" style={lbl}>Consequence <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>(why it matters)</span></label>
              <textarea id="ia-eng-38" value={findingForm.consequence} onChange={e => setFindingForm(f => ({ ...f, consequence: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-39" style={lbl}>Root Cause</label>
              <textarea id="ia-eng-39" value={findingForm.rootCause} onChange={e => setFindingForm(f => ({ ...f, rootCause: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label htmlFor="ia-eng-40" style={lbl}>Recommendation</label>
              <textarea id="ia-eng-40" value={findingForm.recommendation} onChange={e => setFindingForm(f => ({ ...f, recommendation: e.target.value }))} rows={2} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            </div>
            <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-41" style={lbl}>Severity</label>
                <select id="ia-eng-41" value={findingForm.severity} onChange={e => setFindingForm(f => ({ ...f, severity: e.target.value }))} style={inp}>
                  <option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="CRITICAL">Critical</option>
                </select>
              </div>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-42" style={lbl}>Owner</label>
                <select id="ia-eng-42" value={findingForm.owner} onChange={e => setFindingForm(f => ({ ...f, owner: e.target.value }))} style={inp}>
                  <option value="">Unassigned</option>
                  {users.map(u => <option key={u.id} value={u.id}>{u.firstName} {u.lastName}</option>)}
                </select>
              </div>
              <div style={{ flex: 1 }}>
                <label htmlFor="ia-eng-43" style={lbl}>Due Date</label>
                <input id="ia-eng-43" type="date" value={findingForm.dueDate} onChange={e => setFindingForm(f => ({ ...f, dueDate: e.target.value }))} style={inp} />
              </div>
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <button onClick={() => setShowNewFinding(false)} style={cancelBtn}>Cancel</button>
              <button onClick={() => createFinding.mutate(expanded)} disabled={!findingForm.title.trim() || !findingForm.description.trim() || createFinding.isPending} style={submitBtn}>
                {createFinding.isPending ? "Raising…" : "Raise Finding"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showMgmtResponse && (
        <div style={modalOverlay}>
          <div style={{ ...modalBox, width: 420 }}>
            <h3 style={{ margin: "0 0 16px", fontSize: 16, fontWeight: 700, color: "var(--hf-text)" }}>Management Response</h3>
            {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, marginBottom: 12 }}>{error}</p>}
            <label htmlFor="ia-eng-44" style={lbl}>The auditee's response *</label>
            <textarea id="ia-eng-44" value={mgmtResponseText} onChange={e => setMgmtResponseText(e.target.value)} rows={4} style={{ ...inp, fontFamily: "inherit", resize: "vertical" as const }} />
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 18 }}>
              <button onClick={() => setShowMgmtResponse(null)} style={cancelBtn}>Cancel</button>
              <button onClick={() => recordMgmtResponse.mutate(showMgmtResponse)} disabled={!mgmtResponseText.trim() || recordMgmtResponse.isPending} style={submitBtn}>
                {recordMgmtResponse.isPending ? "Saving…" : "Save Response"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

