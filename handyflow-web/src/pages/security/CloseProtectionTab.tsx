// src/pages/security/CloseProtectionTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Lock, Plus, Users, MapPin, ChevronRight, Shield, Car, AlertTriangle } from "lucide-react"

// ── Types ──────────────────────────────────────────────────────────────────────

interface Principal {
  id: string
  fullName: string
  aliasCodename: string
  threatLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
  medicalNotes: string | null
  knownThreats: string | null
  active: boolean
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

type View = "details" | "principal" | "itinerary"

// ── Config ─────────────────────────────────────────────────────────────────────

const THREAT_CONFIG = {
  LOW:      { color: "#166534", bg: "#DCFCE7" },
  MEDIUM:   { color: "#92400E", bg: "#FEF3C7" },
  HIGH:     { color: "#C2410C", bg: "#FFF7ED" },
  CRITICAL: { color: "#991B1B", bg: "#FEF2F2" },
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
  const [view,           setView]           = useState<View>("details")
  const [selectedDetail, setSelectedDetail] = useState<ProtectionDetail | null>(null)
  const [selectedPrincipal, setSelectedPrincipal] = useState<Principal | null>(null)
  const [showAddDetail,  setShowAddDetail]  = useState(false)
  const [showAddStop,    setShowAddStop]    = useState(false)
  const [apiError,       setApiError]       = useState("")

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
          <button onClick={() => { setShowAddDetail(true); setApiError("") }}
            style={{ display: "flex", alignItems: "center", gap: 6, padding: "9px 16px", borderRadius: 8, border: "none", background: "#7C3AED", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={14} /> New Detail
          </button>
        </div>
      </div>

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
                {(["details", "itinerary"] as View[]).map(v => (
                  <button key={v} onClick={() => setView(v)}
                    style={{ padding: "10px 16px", border: "none", borderBottom: `2px solid ${view === v ? "#7C3AED" : "transparent"}`, background: "none", color: view === v ? "#7C3AED" : "#64748B", fontSize: 12, fontWeight: view === v ? 600 : 400, cursor: "pointer", marginBottom: -1, textTransform: "capitalize" as const }}>
                    {v === "details" ? "Team" : "Itinerary"}
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
              </div>
            </div>
          )}
        </div>
      </div>

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
    </div>
  )
}

const lblStyle    = { display: "block", fontSize: 11, fontWeight: 600, color: "#374151", marginBottom: 4 } as const
const inputStyle  = { width: "100%", padding: "9px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, background: "#fff", boxSizing: "border-box" as const } as const
const primaryBtn  = { padding: "9px 18px", borderRadius: 8, border: "none", background: "#0D9488", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" } as const
const secondaryBtn = { padding: "9px 18px", borderRadius: 8, border: "1px solid #E2E8F0", background: "#fff", color: "#374151", fontSize: 13, cursor: "pointer" } as const
const modalOverlay = { position: "fixed" as const, inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000 } as const
const modalBox    = { background: "#fff", borderRadius: 14, padding: 24, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" } as const
