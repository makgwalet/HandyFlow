// src/pages/security/CloseProtectionTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Lock, Plus, Shield, Users2, Edit2, Ban, History, Eye, FileDown, Paperclip, Download, Trash2 } from "lucide-react"
// ── Types ──────────────────────────────────────────────────────────────────────

interface Principal {
  id: string
  fullName: string
  aliasCodename: string
  threatLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
  medicalNotes: string | null
  knownThreats: string | null
  emergencyContactsJson: string | null
  photoUrl: string | null
  active: boolean
  createdAt: string
  vettingStatus: string | null
}

interface ProtectionDetail {
  id: string
  principalId: string
  principalCodename: string
  detailType: string
  startAt: string
  endAt: string | null
  status: "PLANNED" | "ACTIVE" | "COMPLETED" | "CANCELLED"
  teamSize: number
  clientReference: string | null
}

interface ItineraryStop {
  id: string
  sequence: number
  locationName: string
  address: string | null
  scheduledArrival: string | null
  actualArrival: string | null
  actualDeparture: string | null
  advanceSurveyRequired: boolean
  status: "PENDING" | "IN_PROGRESS" | "COMPLETED"
}

type View = "details" | "itinerary" | "evidence"
type MainView = "engagements" | "principals"

interface AuditEvent {
  id: string; actorId: string | null; actorType: string
  entityType: string; entityId: string; action: string
  occurredAt: string
}

interface Evidence {
  id: string; fileName: string; contentType: string; fileSizeBytes: number
  evidenceType: string; status: string; uploadedByName: string; createdAt: string
}

// ── Config ─────────────────────────────────────────────────────────────────────

const THREAT_LEVEL: Record<string, { color: string; bg: string }> = {
  LOW:      { color: "#166534", bg: "#DCFCE7" },
  MEDIUM:   { color: "#B45309", bg: "#FFFBEB" },
  HIGH:     { color: "#C2410C", bg: "#FFEDD5" },
  CRITICAL: { color: "#DC2626", bg: "#FEF2F2" },
}

const DETAIL_STATUS = {
  PLANNED:   { color: "#1D4ED8", bg: "#EFF6FF" },
  ACTIVE:    { color: "#166534", bg: "#DCFCE7" },
  COMPLETED: { color: "#64748B", bg: "#F1F5F9" },
  CANCELLED: { color: "#94A3B8", bg: "#F8FAFC" },
}

const fmtDate = (s: string | null) => s ? new Date(s).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtTime = (s: string | null) => s ? new Date(s).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" }) : "—"

// ── Component ──────────────────────────────────────────────────────────────────

export default function CloseProtectionTab() {
  const qc = useQueryClient()
  const [mainView,       setMainView]       = useState<MainView>("engagements")
  const [view,           setView]           = useState<View>("details")
  const [selectedDetail, setSelectedDetail] = useState<ProtectionDetail | null>(null)
  const [showAddDetail,  setShowAddDetail]  = useState(false)
  const [showAddStop,    setShowAddStop]    = useState(false)
  const [apiError,       setApiError]       = useState("")

  // FIX (P1 backlog): everything below down to "// Queries" is new —
  // principal management (create/edit/deactivate), the audit trail, the
  // vetting compliance PDF, and evidence at both principal and detail
  // level all existed server-side with zero UI. Confirmed via direct
  // read of CloseProtectionController before building any of this —
  // the "principal" View value that used to exist here was declared in
  // the type union but never actually rendered anywhere (dead code, not
  // a real feature).
  const [selectedPrincipal, setSelectedPrincipal] = useState<Principal | null>(null)
  const [principalView,     setPrincipalView]     = useState<"overview" | "audit" | "evidence">("overview")
  const [auditMode,         setAuditMode]         = useState<"all" | "views">("all")
  const [showPrincipalForm, setShowPrincipalForm] = useState<Principal | "new" | null>(null)
  const [principalForm, setPrincipalForm] = useState({
    fullName: "", aliasCodename: "", threatLevel: "LOW", medicalNotes: "", knownThreats: "",
  })
  const [evidenceUploadFor, setEvidenceUploadFor] = useState<{ type: "PRINCIPAL" | "PROTECTION_DETAIL"; id: string } | null>(null)
  const [evidenceForm, setEvidenceForm] = useState({ category: "ID_DOCUMENT", fileName: "", fileBase64: "", notes: "" })

  const [detailForm, setDetailForm] = useState({
    principalId: "", detailType: "MOBILE", startAt: "", endAt: "", clientReference: "", notes: "",
  })
  const [stopForm, setStopForm] = useState({
    locationName: "", address: "", scheduledArrival: "", advanceSurveyRequired: false, notes: "",
  })

  // Queries
  const { data: details = [], isLoading: loadingDetails } = useQuery<ProtectionDetail[]>({
    queryKey: ["cp-details"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/cp/details?size=50")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as ProtectionDetail[]
    },
  })

  const { data: principals = [] } = useQuery<Principal[]>({
    queryKey: ["cp-principals"],
    queryFn: async () => {
      const r = await apiClient.get("/api/v1/security/cp/principals?size=100")
      const p = r.data?.data ?? r.data
      return (p?.content ?? p) as Principal[]
    },
  })

  const { data: team = [] } = useQuery({
    queryKey: ["cp-team", selectedDetail?.id],
    queryFn: async () => {
      if (!selectedDetail) return []
      const r = await apiClient.get(`/api/v1/security/cp/details/${selectedDetail.id}/team`)
      return r.data?.data ?? r.data ?? []
    },
    enabled: !!selectedDetail,
  })

  const { data: itinerary = [] } = useQuery<ItineraryStop[]>({
    queryKey: ["cp-itinerary", selectedDetail?.id],
    queryFn: async () => {
      if (!selectedDetail) return []
      const r = await apiClient.get(`/api/v1/security/cp/details/${selectedDetail.id}/itinerary`)
      return r.data?.data ?? r.data ?? []
    },
    enabled: !!selectedDetail,
  })

  // ── Principal audit trail, vetting, evidence — all new, see the state
  // block above for the fuller context on why these exist now.
  const { data: principalAudit, isLoading: auditLoading } = useQuery<{ content: AuditEvent[] }>({
    queryKey: ["cp-principal-audit", selectedPrincipal?.id, auditMode],
    queryFn: async () => {
      const path = auditMode === "views" ? "audit/views" : "audit"
      const r = await apiClient.get(`/api/v1/security/cp/principals/${selectedPrincipal!.id}/${path}?size=50`)
      return (r.data?.data ?? r.data) as { content: AuditEvent[] }
    },
    enabled: !!selectedPrincipal && principalView === "audit",
  })

  const principalEvidenceQuery = useQuery<Evidence[]>({
    queryKey: ["cp-principal-evidence", selectedPrincipal?.id],
    queryFn: async () => (await apiClient.get(`/api/v1/security/cp/principals/${selectedPrincipal!.id}/evidence`)).data?.data ?? [],
    enabled: !!selectedPrincipal && principalView === "evidence",
  })

  const detailEvidenceQuery = useQuery<Evidence[]>({
    queryKey: ["cp-detail-evidence", selectedDetail?.id],
    queryFn: async () => (await apiClient.get(`/api/v1/security/cp/details/${selectedDetail!.id}/evidence`)).data?.data ?? [],
    enabled: !!selectedDetail && view === "evidence",
  })

  const downloadVettingPdf = async (principalId: string) => {
    const res = await apiClient.get(`/api/v1/security/cp/principals/${principalId}/vetting/pdf`, { responseType: "blob" })
    const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
    const a = document.createElement("a"); a.href = url; a.download = `vetting-compliance-${principalId.slice(0, 8)}.pdf`; a.click()
  }
  const downloadEvidence = async (ev: Evidence) => {
    const res = await apiClient.get(`/api/v1/evidence/${ev.id}/download`, { responseType: "blob" })
    const url = URL.createObjectURL(new Blob([res.data], { type: ev.contentType }))
    const a = document.createElement("a"); a.href = url; a.download = ev.fileName; a.click()
  }

  // Mutations
  const createDetail = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/security/cp/details", body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["cp-details"] }); setShowAddDetail(false) },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to create detail"),
  })

  const activateDetail = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/security/cp/details/${id}/activate`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["cp-details"] }),
  })

  const addStop = useMutation({
    mutationFn: ({ id, body }: any) => apiClient.post(`/api/v1/security/cp/details/${id}/itinerary`, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["cp-itinerary", selectedDetail?.id] }); setShowAddStop(false) },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to add stop"),
  })

  const arriveStop = useMutation({
    mutationFn: (stopId: string) => apiClient.post(`/api/v1/security/cp/itinerary/${stopId}/arrive`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["cp-itinerary", selectedDetail?.id] }),
  })

  const departStop = useMutation({
    mutationFn: (stopId: string) => apiClient.post(`/api/v1/security/cp/itinerary/${stopId}/depart`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["cp-itinerary", selectedDetail?.id] }),
  })

  const createPrincipal = useMutation({
    mutationFn: () => apiClient.post("/api/v1/security/cp/principals", principalForm),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["cp-principals"] }); setShowPrincipalForm(null); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to create principal"),
  })
  const updatePrincipal = useMutation({
    mutationFn: (id: string) => apiClient.put(`/api/v1/security/cp/principals/${id}`, principalForm),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ["cp-principals"] })
      setShowPrincipalForm(null); setApiError("")
      if (selectedPrincipal) setSelectedPrincipal(res.data?.data ?? res.data)
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update principal"),
  })
  const deactivatePrincipal = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/security/cp/principals/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["cp-principals"] }); setSelectedPrincipal(null) },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to deactivate principal"),
  })

  const uploadEvidence = useMutation({
    mutationFn: () => {
      const target = evidenceUploadFor!
      const path = target.type === "PRINCIPAL" ? `principals/${target.id}/evidence` : `details/${target.id}/evidence`
      return apiClient.post(`/api/v1/security/cp/${path}`, evidenceForm)
    },
    onSuccess: () => {
      const target = evidenceUploadFor!
      qc.invalidateQueries({ queryKey: target.type === "PRINCIPAL" ? ["cp-principal-evidence", target.id] : ["cp-detail-evidence", target.id] })
      setEvidenceUploadFor(null); setEvidenceForm({ category: "ID_DOCUMENT", fileName: "", fileBase64: "", notes: "" })
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to upload evidence"),
  })
  const deleteEvidence = useMutation({
    mutationFn: ({ evidenceId, reason }: { evidenceId: string; reason: string }) =>
      apiClient.delete(`/api/v1/security/cp/evidence/${evidenceId}`, { data: { reason } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["cp-principal-evidence", selectedPrincipal?.id] })
      qc.invalidateQueries({ queryKey: ["cp-detail-evidence", selectedDetail?.id] })
    },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to delete evidence"),
  })

  return (
    <div>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "#0F172A", display: "flex", alignItems: "center", gap: 8 }}>
            <Lock size={16} /> Close Protection
          </h2>
          <p style={{ margin: "2px 0 0", fontSize: 12, color: "#64748B" }}>
            {details.filter(d => d.status === "ACTIVE").length} active detail{details.filter(d => d.status === "ACTIVE").length !== 1 ? "s" : ""} · {principals.length} principal{principals.length !== 1 ? "s" : ""} registered
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          {mainView === "engagements" ? (
            <button onClick={() => { setShowAddDetail(true); setApiError("") }}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", borderRadius: 8, border: "none", background: "#7C3AED", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={14} /> New Detail
            </button>
          ) : (
            <button onClick={() => { setPrincipalForm({ fullName: "", aliasCodename: "", threatLevel: "LOW", medicalNotes: "", knownThreats: "" }); setShowPrincipalForm("new"); setApiError("") }}
              style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", borderRadius: 8, border: "none", background: "#7C3AED", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={14} /> New Principal
            </button>
          )}
        </div>
      </div>

      {/* Top-level toggle — FIX (P1 backlog): Principals is new, see the
          state block near the top of this component for the fuller
          context. */}
      <div style={{ display: "flex", gap: 6, marginBottom: 20, borderBottom: "1px solid #E2E8F0" }}>
        {([["engagements", "Engagements", Shield], ["principals", "Principals", Users2]] as const).map(([id, label, Icon]) => (
          <button key={id} onClick={() => setMainView(id)}
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "10px 14px", background: "none", border: "none", borderBottom: mainView === id ? "2px solid #7C3AED" : "2px solid transparent", color: mainView === id ? "#7C3AED" : "#64748B", fontWeight: mainView === id ? 700 : 500, fontSize: 13, cursor: "pointer" }}>
            <Icon size={14} /> {label}
          </button>
        ))}
      </div>

      {mainView === "engagements" && (
      <>
      {/* Layout: list + panel */}
      <div style={{ display: "grid", gridTemplateColumns: "300px 1fr", gap: 20 }}>
        {/* Engagement list */}
        <div>
          <p style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "#64748B", marginBottom: 10 }}>Engagements</p>
          {loadingDetails ? (
            <p style={{ fontSize: 12, color: "#94A3B8" }}>Loading…</p>
          ) : details.length === 0 ? (
            <div style={{ textAlign: "center", padding: "32px 16px", color: "#CBD5E1", border: "1px dashed #E2E8F0", borderRadius: 10 }}>
              <Lock size={24} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
              <p style={{ margin: 0, fontSize: 12 }}>No engagements yet</p>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {details.map(d => {
                const sc = DETAIL_STATUS[d.status]
                const active = selectedDetail?.id === d.id
                return (
                  <button key={d.id} onClick={() => setSelectedDetail(d)}
                    style={{ padding: "12px 14px", border: `1px solid ${active ? "#7C3AED" : "#E2E8F0"}`, borderRadius: 10, background: active ? "#F5F3FF" : "#fff", cursor: "pointer", textAlign: "left" as const, width: "100%" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                      <span style={{ fontSize: 13, fontWeight: 700, color: active ? "#7C3AED" : "#0F172A" }}>
                        {d.principalCodename}
                      </span>
                      <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 7px", borderRadius: 4, color: sc.color, background: sc.bg }}>
                        {d.status}
                      </span>
                    </div>
                    <p style={{ margin: 0, fontSize: 11, color: "#64748B" }}>
                      {d.detailType} · {fmtDate(d.startAt)} · {d.teamSize} guard{d.teamSize !== 1 ? "s" : ""}
                    </p>
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Detail panel */}
        <div>
          {!selectedDetail ? (
            <div style={{ textAlign: "center", padding: "60px 0", color: "#CBD5E1", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
              <Shield size={32} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
              <p style={{ margin: 0, fontSize: 13, fontWeight: 500 }}>Select an engagement</p>
            </div>
          ) : (
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
              {/* Detail header */}
              <div style={{ background: "#7C3AED", padding: "16px 20px", color: "#fff" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <p style={{ margin: 0, fontSize: 11, opacity: 0.75 }}>CODENAME</p>
                    <p style={{ margin: "2px 0 0", fontSize: 18, fontWeight: 800 }}>{selectedDetail.principalCodename}</p>
                  </div>
                  <div style={{ textAlign: "right" as const }}>
                    <p style={{ margin: 0, fontSize: 11, opacity: 0.75 }}>{selectedDetail.detailType}</p>
                    <p style={{ margin: "2px 0 0", fontSize: 12 }}>{fmtDate(selectedDetail.startAt)}</p>
                  </div>
                </div>
                <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
                  {selectedDetail.status === "PLANNED" && (
                    <button onClick={() => activateDetail.mutate(selectedDetail.id)}
                      style={{ padding: "6px 14px", borderRadius: 7, border: "1px solid rgba(255,255,255,0.4)", background: "rgba(255,255,255,0.15)", color: "#fff", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                      Activate Detail
                    </button>
                  )}
                </div>
              </div>

              {/* Sub-tabs */}
              <div style={{ display: "flex", borderBottom: "1px solid #E2E8F0" }}>
                {(["details", "itinerary", "evidence"] as View[]).map(v => (
                  <button key={v} onClick={() => setView(v)}
                    style={{ padding: "10px 16px", border: "none", borderBottom: `2px solid ${view === v ? "#7C3AED" : "transparent"}`, background: "none", color: view === v ? "#7C3AED" : "#64748B", fontSize: 12, fontWeight: view === v ? 600 : 400, cursor: "pointer", marginBottom: -1, textTransform: "capitalize" as const }}>
                    {v === "details" ? "Team" : v === "itinerary" ? "Itinerary" : "Evidence"}
                  </button>
                ))}
              </div>

              <div style={{ padding: 20 }}>
                {view === "details" && (
                  <div>
                    <p style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "#64748B", marginBottom: 12 }}>
                      Team Roster — {(team as any[]).length} assigned
                    </p>
                    {(team as any[]).length === 0 ? (
                      <p style={{ color: "#94A3B8", fontSize: 12 }}>No team members assigned yet</p>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                        {(team as any[]).map((a: any) => (
                          <div key={a.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", border: "1px solid #E2E8F0", borderRadius: 8 }}>
                            <div style={{ width: 32, height: 32, borderRadius: "50%", background: "#EDE9FE", display: "flex", alignItems: "center", justifyContent: "center" }}>
                              <Shield size={14} color="#7C3AED" />
                            </div>
                            <div>
                              <p style={{ margin: 0, fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{a.guardName}</p>
                              <p style={{ margin: 0, fontSize: 11, color: "#7C3AED" }}>{a.role.replace(/_/g, " ")}</p>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {view === "itinerary" && (
                  <div>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                      <p style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "#64748B", margin: 0 }}>
                        Itinerary — {(itinerary as ItineraryStop[]).length} stop{(itinerary as ItineraryStop[]).length !== 1 ? "s" : ""}
                      </p>
                      <button onClick={() => { setShowAddStop(true); setApiError("") }}
                        style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid #7C3AED", background: "#F5F3FF", color: "#7C3AED", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                        <Plus size={12} /> Add Stop
                      </button>
                    </div>
                    {(itinerary as ItineraryStop[]).length === 0 ? (
                      <p style={{ color: "#94A3B8", fontSize: 12 }}>No stops added yet</p>
                    ) : (
                      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                        {(itinerary as ItineraryStop[]).map(stop => (
                          <div key={stop.id} style={{ display: "flex", gap: 12, padding: "12px 14px", border: "1px solid #E2E8F0", borderRadius: 10 }}>
                            <div style={{ width: 24, height: 24, borderRadius: "50%", background: stop.status === "COMPLETED" ? "#DCFCE7" : stop.status === "IN_PROGRESS" ? "#FEF3C7" : "#F1F5F9", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0, fontSize: 10, fontWeight: 800, color: stop.status === "COMPLETED" ? "#166534" : stop.status === "IN_PROGRESS" ? "#92400E" : "#94A3B8" }}>
                              {stop.sequence}
                            </div>
                            <div style={{ flex: 1 }}>
                              <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 2 }}>
                                <span style={{ fontWeight: 700, fontSize: 13, color: "#0F172A" }}>{stop.locationName}</span>
                                {stop.advanceSurveyRequired && <span style={{ fontSize: 10, color: "#92400E", background: "#FEF3C7", padding: "1px 6px", borderRadius: 4, fontWeight: 700 }}>SURVEY REQ</span>}
                              </div>
                              <p style={{ margin: 0, fontSize: 11, color: "#64748B" }}>
                                {stop.address && `${stop.address} · `}
                                {stop.scheduledArrival && `Scheduled ${fmtTime(stop.scheduledArrival)}`}
                                {stop.actualArrival && ` · Arrived ${fmtTime(stop.actualArrival)}`}
                              </p>
                            </div>
                            <div style={{ display: "flex", gap: 6 }}>
                              {stop.status === "PENDING" && (
                                <button onClick={() => arriveStop.mutate(stop.id)}
                                  style={{ fontSize: 11, padding: "4px 10px", borderRadius: 6, border: "1px solid #7C3AED", background: "#F5F3FF", color: "#7C3AED", cursor: "pointer" }}>
                                  Arrive
                                </button>
                              )}
                              {stop.status === "IN_PROGRESS" && (
                                <button onClick={() => departStop.mutate(stop.id)}
                                  style={{ fontSize: 11, padding: "4px 10px", borderRadius: 6, border: "1px solid #166534", background: "#DCFCE7", color: "#166534", cursor: "pointer" }}>
                                  Depart
                                </button>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {view === "evidence" && selectedDetail && (
                  <EvidenceList
                    query={detailEvidenceQuery}
                    onUpload={() => { setEvidenceUploadFor({ type: "PROTECTION_DETAIL", id: selectedDetail.id }); setEvidenceForm({ category: "ID_DOCUMENT", fileName: "", fileBase64: "", notes: "" }); setApiError("") }}
                    onDownload={downloadEvidence}
                    onDelete={(evidenceId) => { const reason = prompt("Reason for removing this evidence?"); if (reason) deleteEvidence.mutate({ evidenceId, reason }) }}
                  />
                )}
              </div>
            </div>
          )}
        </div>
      </div>
      </>
      )}

      {mainView === "principals" && (
        <PrincipalsView
          principals={principals}
          selectedPrincipal={selectedPrincipal} setSelectedPrincipal={setSelectedPrincipal}
          principalView={principalView} setPrincipalView={setPrincipalView}
          auditMode={auditMode} setAuditMode={setAuditMode}
          auditData={principalAudit} auditLoading={auditLoading}
          evidenceQuery={principalEvidenceQuery}
          onEdit={(p) => { setPrincipalForm({ fullName: p.fullName, aliasCodename: p.aliasCodename, threatLevel: p.threatLevel, medicalNotes: p.medicalNotes ?? "", knownThreats: p.knownThreats ?? "" }); setShowPrincipalForm(p); setApiError("") }}
          onDeactivate={(id) => deactivatePrincipal.mutate(id)}
          onDownloadVettingPdf={downloadVettingPdf}
          onUploadEvidence={(id) => { setEvidenceUploadFor({ type: "PRINCIPAL", id }); setEvidenceForm({ category: "ID_DOCUMENT", fileName: "", fileBase64: "", notes: "" }); setApiError("") }}
          onDownloadEvidence={downloadEvidence}
          onDeleteEvidence={(evidenceId) => { const reason = prompt("Reason for removing this evidence?"); if (reason) deleteEvidence.mutate({ evidenceId, reason }) }}
        />
      )}

      {/* New detail modal */}
      {showAddDetail && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 700 }}>New Protection Detail</h3>
            {apiError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div>
                <label style={lblStyle}>Principal *</label>
                <select value={detailForm.principalId} onChange={e => setDetailForm(p => ({ ...p, principalId: e.target.value }))} style={inputStyle}>
                  <option value="">Select principal…</option>
                  {principals.map((p: Principal) => <option key={p.id} value={p.id}>{p.aliasCodename}</option>)}
                </select>
              </div>
              <div>
                <label style={lblStyle}>Type</label>
                <select value={detailForm.detailType} onChange={e => setDetailForm(p => ({ ...p, detailType: e.target.value }))} style={inputStyle}>
                  <option value="MOBILE">Mobile</option>
                  <option value="STATIC">Static</option>
                  <option value="EVENT">Event</option>
                  <option value="TRAVEL">Travel</option>
                </select>
              </div>
              <div>
                <label style={lblStyle}>Start Date/Time *</label>
                <input type="datetime-local" value={detailForm.startAt} onChange={e => setDetailForm(p => ({ ...p, startAt: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={lblStyle}>Client Reference</label>
                <input value={detailForm.clientReference} onChange={e => setDetailForm(p => ({ ...p, clientReference: e.target.value }))} style={inputStyle} />
              </div>
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAddDetail(false)} style={secondaryBtn}>Cancel</button>
              <button onClick={() => createDetail.mutate({ ...detailForm, startAt: detailForm.startAt ? new Date(detailForm.startAt).toISOString() : null })}
                style={{ ...primaryBtn, background: "#7C3AED" }}>Create</button>
            </div>
          </div>
        </div>
      )}

      {/* Add stop modal */}
      {showAddStop && selectedDetail && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 700 }}>Add Itinerary Stop</h3>
            {apiError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div>
                <label style={lblStyle}>Location Name *</label>
                <input value={stopForm.locationName} onChange={e => setStopForm(p => ({ ...p, locationName: e.target.value }))} placeholder="e.g. Sandton City Hotel" style={inputStyle} />
              </div>
              <div>
                <label style={lblStyle}>Address</label>
                <input value={stopForm.address} onChange={e => setStopForm(p => ({ ...p, address: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={lblStyle}>Scheduled Arrival</label>
                <input type="datetime-local" value={stopForm.scheduledArrival} onChange={e => setStopForm(p => ({ ...p, scheduledArrival: e.target.value }))} style={inputStyle} />
              </div>
              <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 12, cursor: "pointer" }}>
                <input type="checkbox" checked={stopForm.advanceSurveyRequired} onChange={e => setStopForm(p => ({ ...p, advanceSurveyRequired: e.target.checked }))} />
                Advance survey required before principal arrives
              </label>
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAddStop(false)} style={secondaryBtn}>Cancel</button>
              <button onClick={() => addStop.mutate({ id: selectedDetail.id, body: { ...stopForm, scheduledArrival: stopForm.scheduledArrival ? new Date(stopForm.scheduledArrival).toISOString() : null } })}
                style={{ ...primaryBtn, background: "#7C3AED" }}>Add Stop</button>
            </div>
          </div>
        </div>
      )}

      {/* Principal create/edit modal */}
      {showPrincipalForm && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 700 }}>{showPrincipalForm === "new" ? "New Principal" : "Edit Principal"}</h3>
            {apiError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div>
                <label style={lblStyle}>Full Name *</label>
                <input value={principalForm.fullName} onChange={e => setPrincipalForm(f => ({ ...f, fullName: e.target.value }))} style={inputStyle} />
              </div>
              <div>
                <label style={lblStyle}>Alias / Codename *</label>
                <input value={principalForm.aliasCodename} onChange={e => setPrincipalForm(f => ({ ...f, aliasCodename: e.target.value }))} placeholder="Used everywhere instead of the real name" style={inputStyle} />
              </div>
              <div>
                <label style={lblStyle}>Threat Level</label>
                <select value={principalForm.threatLevel} onChange={e => setPrincipalForm(f => ({ ...f, threatLevel: e.target.value }))} style={inputStyle}>
                  <option value="LOW">Low</option><option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option><option value="CRITICAL">Critical</option>
                </select>
              </div>
              <div>
                <label style={lblStyle}>Medical Notes</label>
                <textarea value={principalForm.medicalNotes} onChange={e => setPrincipalForm(f => ({ ...f, medicalNotes: e.target.value }))} rows={2} style={{ ...inputStyle, fontFamily: "inherit", resize: "vertical" as const }} />
              </div>
              <div>
                <label style={lblStyle}>Known Threats</label>
                <textarea value={principalForm.knownThreats} onChange={e => setPrincipalForm(f => ({ ...f, knownThreats: e.target.value }))} rows={2} style={{ ...inputStyle, fontFamily: "inherit", resize: "vertical" as const }} />
              </div>
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => { setShowPrincipalForm(null); setApiError("") }} style={secondaryBtn}>Cancel</button>
              <button
                onClick={() => showPrincipalForm === "new" ? createPrincipal.mutate() : updatePrincipal.mutate(showPrincipalForm.id)}
                disabled={!principalForm.fullName.trim() || !principalForm.aliasCodename.trim() || createPrincipal.isPending || updatePrincipal.isPending}
                style={{ ...primaryBtn, background: "#7C3AED" }}>
                {createPrincipal.isPending || updatePrincipal.isPending ? "Saving…" : showPrincipalForm === "new" ? "Create" : "Save Changes"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Evidence upload modal — shared between principal-level and
          detail-level evidence, matching UploadEvidenceRequest's own
          shared shape on the backend. */}
      {evidenceUploadFor && (
        <div style={modalOverlay}>
          <div style={modalBox}>
            <h3 style={{ margin: "0 0 16px", fontSize: 15, fontWeight: 700 }}>Upload Evidence</h3>
            {apiError && <p style={{ color: "#DC2626", fontSize: 12, marginBottom: 12 }}>{apiError}</p>}
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <div>
                <label style={lblStyle}>Category</label>
                <select value={evidenceForm.category} onChange={e => setEvidenceForm(f => ({ ...f, category: e.target.value }))} style={inputStyle}>
                  <option value="ID_DOCUMENT">ID Document</option>
                  <option value="ENGAGEMENT_LETTER">Engagement Letter</option>
                  <option value="THREAT_INTEL">Threat Intel</option>
                  <option value="MEDICAL">Medical</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div>
                <label style={lblStyle}>File *</label>
                <input type="file" onChange={e => {
                  const file = e.target.files?.[0]; if (!file) return
                  const reader = new FileReader()
                  reader.onload = () => setEvidenceForm(f => ({ ...f, fileName: file.name, fileBase64: reader.result as string }))
                  reader.readAsDataURL(file)
                }} style={inputStyle} />
              </div>
              <div>
                <label style={lblStyle}>Notes</label>
                <input value={evidenceForm.notes} onChange={e => setEvidenceForm(f => ({ ...f, notes: e.target.value }))} style={inputStyle} />
              </div>
            </div>
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setEvidenceUploadFor(null)} style={secondaryBtn}>Cancel</button>
              <button onClick={() => uploadEvidence.mutate()} disabled={!evidenceForm.fileBase64 || uploadEvidence.isPending} style={{ ...primaryBtn, background: "#7C3AED" }}>
                {uploadEvidence.isPending ? "Uploading…" : "Upload"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Principals sub-view ─────────────────────────────────────────────────────────

function PrincipalsView({
  principals, selectedPrincipal, setSelectedPrincipal, principalView, setPrincipalView,
  auditMode, setAuditMode, auditData, auditLoading, evidenceQuery,
  onEdit, onDeactivate, onDownloadVettingPdf, onUploadEvidence, onDownloadEvidence, onDeleteEvidence,
}: {
  principals: Principal[]
  selectedPrincipal: Principal | null; setSelectedPrincipal: (p: Principal | null) => void
  principalView: "overview" | "audit" | "evidence"; setPrincipalView: (v: "overview" | "audit" | "evidence") => void
  auditMode: "all" | "views"; setAuditMode: (m: "all" | "views") => void
  auditData?: { content: AuditEvent[] }; auditLoading: boolean
  evidenceQuery: ReturnType<typeof useQuery<Evidence[]>>
  onEdit: (p: Principal) => void
  onDeactivate: (id: string) => void
  onDownloadVettingPdf: (id: string) => void
  onUploadEvidence: (id: string) => void
  onDownloadEvidence: (ev: Evidence) => void
  onDeleteEvidence: (evidenceId: string) => void
}) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "300px 1fr", gap: 20 }}>
      <div>
        <p style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "#64748B", marginBottom: 10 }}>Principals</p>
        {principals.length === 0 ? (
          <div style={{ textAlign: "center", padding: "32px 16px", color: "#CBD5E1", border: "1px dashed #E2E8F0", borderRadius: 10 }}>
            <Users2 size={24} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
            <p style={{ margin: 0, fontSize: 12 }}>No principals registered</p>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {principals.map(p => {
              const tc = THREAT_LEVEL[p.threatLevel] ?? THREAT_LEVEL.LOW
              const active = selectedPrincipal?.id === p.id
              return (
                <button key={p.id} onClick={() => { setSelectedPrincipal(p); setPrincipalView("overview") }}
                  style={{ padding: "12px 14px", border: `1px solid ${active ? "#7C3AED" : "#E2E8F0"}`, borderRadius: 10, background: active ? "#F5F3FF" : "#fff", cursor: "pointer", textAlign: "left" as const, width: "100%", opacity: p.active ? 1 : 0.6 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: active ? "#7C3AED" : "#0F172A" }}>{p.aliasCodename}</span>
                    <span style={{ fontSize: 10, fontWeight: 700, padding: "2px 7px", borderRadius: 4, color: tc.color, background: tc.bg }}>{p.threatLevel}</span>
                  </div>
                  <p style={{ margin: 0, fontSize: 11, color: "#64748B" }}>{p.fullName}{!p.active && " · Inactive"}</p>
                </button>
              )
            })}
          </div>
        )}
      </div>

      <div>
        {!selectedPrincipal ? (
          <div style={{ textAlign: "center", padding: "60px 0", color: "#CBD5E1", border: "1px dashed #E2E8F0", borderRadius: 12 }}>
            <Users2 size={32} strokeWidth={1.5} style={{ display: "block", margin: "0 auto 8px" }} />
            <p style={{ margin: 0, fontSize: 13, fontWeight: 500 }}>Select a principal</p>
          </div>
        ) : (
          <div style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
            <div style={{ background: "#7C3AED", padding: "16px 20px", color: "#fff" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <p style={{ margin: 0, fontSize: 11, opacity: 0.75 }}>CODENAME</p>
                  <p style={{ margin: "2px 0 0", fontSize: 18, fontWeight: 800 }}>{selectedPrincipal.aliasCodename}</p>
                  <p style={{ margin: "2px 0 0", fontSize: 12, opacity: 0.85 }}>{selectedPrincipal.fullName}</p>
                </div>
                <div style={{ display: "flex", gap: 6 }}>
                  <button onClick={() => onEdit(selectedPrincipal)} title="Edit"
                    style={{ padding: "7px 9px", borderRadius: 7, border: "1px solid rgba(255,255,255,0.4)", background: "rgba(255,255,255,0.15)", color: "#fff", cursor: "pointer" }}>
                    <Edit2 size={13} />
                  </button>
                  {selectedPrincipal.active && (
                    <button onClick={() => onDeactivate(selectedPrincipal.id)} title="Deactivate"
                      style={{ padding: "7px 9px", borderRadius: 7, border: "1px solid rgba(255,255,255,0.4)", background: "rgba(255,255,255,0.15)", color: "#fff", cursor: "pointer" }}>
                      <Ban size={13} />
                    </button>
                  )}
                </div>
              </div>
            </div>

            <div style={{ display: "flex", borderBottom: "1px solid #E2E8F0" }}>
              {(["overview", "audit", "evidence"] as const).map(v => (
                <button key={v} onClick={() => setPrincipalView(v)}
                  style={{ padding: "10px 16px", border: "none", borderBottom: `2px solid ${principalView === v ? "#7C3AED" : "transparent"}`, background: "none", color: principalView === v ? "#7C3AED" : "#64748B", fontSize: 12, fontWeight: principalView === v ? 600 : 400, cursor: "pointer", marginBottom: -1, textTransform: "capitalize" as const }}>
                  {v}
                </button>
              ))}
            </div>

            <div style={{ padding: 20 }}>
              {principalView === "overview" && (
                <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                  <Field label="Threat Level" value={selectedPrincipal.threatLevel} />
                  <Field label="Vetting Status" value={selectedPrincipal.vettingStatus ?? "Not started"} />
                  <Field label="Medical Notes" value={selectedPrincipal.medicalNotes ?? "—"} />
                  <Field label="Known Threats" value={selectedPrincipal.knownThreats ?? "—"} />
                  <button onClick={() => onDownloadVettingPdf(selectedPrincipal.id)}
                    style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", color: "#374151", fontSize: 12, fontWeight: 600, cursor: "pointer", width: "fit-content", marginTop: 6 }}>
                    <FileDown size={14} /> Vetting Compliance PDF
                  </button>
                </div>
              )}

              {principalView === "audit" && (
                <div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                    <p style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "#64748B", margin: 0 }}>
                      {auditMode === "all" ? "Full Audit Trail" : "View History"}
                    </p>
                    <div style={{ display: "flex", gap: 4 }}>
                      {(["all", "views"] as const).map(m => (
                        <button key={m} onClick={() => setAuditMode(m)}
                          style={{ padding: "4px 10px", borderRadius: 6, border: `1px solid ${auditMode === m ? "#7C3AED" : "#E2E8F0"}`, background: auditMode === m ? "#F5F3FF" : "#fff", color: auditMode === m ? "#7C3AED" : "#64748B", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                          {m === "all" ? "All Events" : "Who Viewed"}
                        </button>
                      ))}
                    </div>
                  </div>
                  {auditLoading ? (
                    <p style={{ color: "#94A3B8", fontSize: 12 }}>Loading…</p>
                  ) : !auditData?.content?.length ? (
                    <p style={{ color: "#94A3B8", fontSize: 12 }}>No audit events recorded.</p>
                  ) : (
                    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                      {auditData.content.map(e => (
                        <div key={e.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 12 }}>
                          {e.action === "VIEWED" ? <Eye size={13} color="#7C3AED" /> : <History size={13} color="#64748B" />}
                          <span style={{ fontWeight: 600, color: "#0F172A" }}>{e.action}</span>
                          <span style={{ color: "#94A3B8" }}>by {e.actorId ? e.actorId.slice(0, 8) : "system"}</span>
                          <span style={{ color: "#CBD5E1", marginLeft: "auto" }}>{new Date(e.occurredAt).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {principalView === "evidence" && (
                <EvidenceList query={evidenceQuery} onUpload={() => onUploadEvidence(selectedPrincipal.id)} onDownload={onDownloadEvidence} onDelete={onDeleteEvidence} />
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p style={{ margin: 0, fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "#94A3B8" }}>{label}</p>
      <p style={{ margin: "3px 0 0", fontSize: 13, color: "#0F172A" }}>{value}</p>
    </div>
  )
}

function EvidenceList({ query, onUpload, onDownload, onDelete }: {
  query: ReturnType<typeof useQuery<Evidence[]>>
  onUpload: () => void
  onDownload: (ev: Evidence) => void
  onDelete: (evidenceId: string) => void
}) {
  const { data: evidence = [], isLoading } = query
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <p style={{ fontSize: 11, fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.05em", color: "#64748B", margin: 0 }}>
          Evidence — {evidence.length} file{evidence.length !== 1 ? "s" : ""}
        </p>
        <button onClick={onUpload} style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", borderRadius: 7, border: "1px solid #7C3AED", background: "#F5F3FF", color: "#7C3AED", fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={12} /> Upload
        </button>
      </div>
      {isLoading ? (
        <p style={{ color: "#94A3B8", fontSize: 12 }}>Loading…</p>
      ) : evidence.length === 0 ? (
        <p style={{ color: "#94A3B8", fontSize: 12 }}>No evidence uploaded yet.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {evidence.map(ev => (
            <div key={ev.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <Paperclip size={15} style={{ color: "#94A3B8" }} />
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "#374151" }}>{ev.fileName}</div>
                  <div style={{ fontSize: 11, color: "#94A3B8" }}>{ev.evidenceType} · {ev.uploadedByName}</div>
                </div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                <button onClick={() => onDownload(ev)} title="Download" style={{ padding: "6px 8px", background: "#F0F9FF", border: "1px solid #BAE6FD", borderRadius: 7, cursor: "pointer", color: "#0369A1" }}><Download size={13} /></button>
                <button onClick={() => onDelete(ev.id)} title="Remove" style={{ padding: "6px 8px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 7, cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const lblStyle    = { display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 } as const
const inputStyle  = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, background: "#fff", boxSizing: "border-box" as const } as const
const primaryBtn  = { padding: "9px 18px", borderRadius: 8, border: "none", background: "#0D9488", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" } as const
const secondaryBtn = { padding: "9px 18px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", color: "#374151", fontSize: 13, cursor: "pointer" } as const
const modalOverlay = { position: "fixed" as const, inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 } as const
const modalBox    = { background: "#fff", borderRadius: 14, padding: 24, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" } as const
