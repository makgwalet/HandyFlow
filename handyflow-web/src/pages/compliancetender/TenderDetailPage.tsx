// src/pages/compliancetender/TenderDetailPage.tsx
//
// The full tender workspace — confirmed against TenderController,
// TenderPersonnelService, TenderSnapshotService, TenderPdfService.
// Status transitions mirror Tender.java's own ALLOWED_TRANSITIONS map
// exactly, so the UI only ever offers a button for a move the backend
// will actually accept — this is a UX convenience, not the real
// authorization boundary (the backend still validates independently).
// AWARDED/UNSUCCESSFUL always go through POST /outcome (reason +
// awardedValue), never plain /transition — matching the two separate
// endpoints TenderController exposes for exactly this reason.
import { useState } from "react"
import { useParams, useNavigate } from "react-router-dom"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import TenderReadinessPanel from "../businessreadiness/TenderReadinessPanel"
import EmployeePicker, { type EmployeeOption } from "../training/EmployeePicker"
import {
  ArrowLeft, Briefcase, Download, Plus, Trash2, X, AlertCircle,
  Users, ClipboardCheck, History, ChevronDown, ChevronUp,
} from "lucide-react"

interface Tender {
  id: string; tenderNumber: string; name: string; tenderAuthority: string | null
  authorityReferenceNumber: string | null; closingDate: string | null; briefingDate: string | null
  siteInspectionDate: string | null; estimatedValue: number | null; industry: string | null
  requiredClassOfWork: string | null; status: string; outcomeReason: string | null
  awardedValue: number | null; submittedAt: string | null
}
interface Requirement { id: string; description: string; source: string; status: string }
interface TrackedRequirement { id: string; code: string; name: string; evidenceType: string | null }
interface Personnel { id: string; employeeId: string; role: string; employeeFound: boolean; employeeFullName: string | null; employeeNumber: string | null }
interface Snapshot { id: string; snapshotNumber: number; submittedAt: string; data: any }

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  DRAFT: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)", label: "Draft" },
  IN_PREPARATION: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "In Preparation" },
  INTERNAL_REVIEW: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "Internal Review" },
  READY_TO_SUBMIT: { color: "var(--hf-accent-text)", bg: "var(--hf-accent-soft)", label: "Ready to Submit" },
  SUBMITTED: { color: "var(--hf-sky-text-strong)", bg: "var(--hf-info-soft)", label: "Submitted" },
  CLARIFICATION: { color: "var(--hf-sky-text-strong)", bg: "var(--hf-info-soft)", label: "Clarification" },
  SHORTLISTED: { color: "var(--hf-accent-text)", bg: "var(--hf-accent-soft)", label: "Shortlisted" },
  NEGOTIATION: { color: "var(--hf-accent-text)", bg: "var(--hf-accent-soft)", label: "Negotiation" },
  AWARDED: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", label: "Awarded" },
  UNSUCCESSFUL: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Unsuccessful" },
  WITHDRAWN: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Withdrawn" },
}
// Mirrors Tender.java's own ALLOWED_TRANSITIONS map exactly.
const TRANSITIONS: Record<string, string[]> = {
  DRAFT: ["IN_PREPARATION", "WITHDRAWN"],
  IN_PREPARATION: ["INTERNAL_REVIEW", "WITHDRAWN"],
  INTERNAL_REVIEW: ["READY_TO_SUBMIT", "IN_PREPARATION", "WITHDRAWN"],
  READY_TO_SUBMIT: ["SUBMITTED", "WITHDRAWN"],
  SUBMITTED: ["CLARIFICATION", "SHORTLISTED", "UNSUCCESSFUL"],
  CLARIFICATION: ["SHORTLISTED", "UNSUCCESSFUL"],
  SHORTLISTED: ["NEGOTIATION", "AWARDED", "UNSUCCESSFUL"],
  NEGOTIATION: ["AWARDED", "UNSUCCESSFUL"],
}
const REQUIREMENT_STATUSES = ["PENDING_REVIEW", "MET", "MISSING", "NOT_APPLICABLE"]
const REQ_STATUS_CFG: Record<string, { color: string; bg: string }> = {
  MET: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" }, MISSING: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  NOT_APPLICABLE: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)" }, PENDING_REVIEW: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
}

const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtDateTime = (d: string) => new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })
const fmtZar = (v: number | null) => v == null ? "—" : `R ${v.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const unwrap = (r: any) => r.data?.data ?? r.data

async function downloadPdf(tenderId: string, tenderNumber: string) {
  const res = await apiClient.get(`/api/v1/compliance/tenders/${tenderId}/export`, { responseType: "blob" })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement("a")
  a.href = url; a.download = `tender-summary-${tenderNumber}.pdf`
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

export default function TenderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const nav = useNavigate()
  const qc = useQueryClient()
  const canManage = usePermission("COMPLIANCE_MANAGE") || usePermission("COMPLIANCE_ADMIN")

  const [showOutcome, setShowOutcome] = useState<string | null>(null) // "AWARDED" | "UNSUCCESSFUL"
  const [outcomeReason, setOutcomeReason] = useState("")
  const [awardedValue, setAwardedValue] = useState("")
  const [newRequirement, setNewRequirement] = useState("")
  const [newRequirementSource, setNewRequirementSource] = useState("MANUAL")
  const [pickedRequirementId, setPickedRequirementId] = useState("")
  const [pickedEmployee, setPickedEmployee] = useState<EmployeeOption | null>(null)
  const [personnelRole, setPersonnelRole] = useState("")
  const [snapshotsOpen, setSnapshotsOpen] = useState(false)
  const [expandedSnapshot, setExpandedSnapshot] = useState<string | null>(null)
  const [apiError, setApiError] = useState("")

  const { data: tender, isLoading } = useQuery<Tender>({
    queryKey: ["ct-tender", id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/compliance/tenders/${id}`)),
    enabled: !!id,
  })

  const { data: requirements = [] } = useQuery<Requirement[]>({
    queryKey: ["ct-tender-requirements", id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/compliance/tenders/${id}/requirements`)),
    enabled: !!id,
  })

  const { data: personnel = [] } = useQuery<Personnel[]>({
    queryKey: ["ct-tender-personnel", id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/compliance/tenders/${id}/personnel`)),
    enabled: !!id,
  })

  const { data: snapshots = [] } = useQuery<Snapshot[]>({
    queryKey: ["ct-tender-snapshots", id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/compliance/tenders/${id}/snapshots`)),
    enabled: !!id && snapshotsOpen,
  })

  // The tracked requirement catalogue (ComplianceRequirementController) —
  // lets "Add requirement" optionally link to an existing definition
  // instead of always being free text. Small, tenant-wide list (current
  // version per code only), safe to fetch unconditionally.
  const { data: trackedRequirements = [] } = useQuery<TrackedRequirement[]>({
    queryKey: ["ct-tracked-requirements"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/requirements")),
  })

  const invalidateTender = () => qc.invalidateQueries({ queryKey: ["ct-tender", id] })

  const transition = useMutation({
    mutationFn: (newStatus: string) => apiClient.post(`/api/v1/compliance/tenders/${id}/transition`, { newStatus }),
    onSuccess: () => { invalidateTender(); qc.invalidateQueries({ queryKey: ["ct-tender-snapshots", id] }); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Transition failed"),
  })

  const recordOutcome = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/compliance/tenders/${id}/outcome`, {
      outcome: showOutcome, reason: outcomeReason || null,
      awardedValue: showOutcome === "AWARDED" && awardedValue ? Number(awardedValue) : null,
    }),
    onSuccess: () => { invalidateTender(); setShowOutcome(null); setOutcomeReason(""); setAwardedValue(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to record outcome"),
  })

  const addRequirement = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/compliance/tenders/${id}/requirements`, {
      description: newRequirement, source: newRequirementSource,
      complianceRequirementId: pickedRequirementId || null,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ct-tender-requirements", id] }); qc.invalidateQueries({ queryKey: ["readiness"] }); setNewRequirement(""); setPickedRequirementId(""); setNewRequirementSource("MANUAL") },
  })

  const updateRequirementStatus = useMutation({
    mutationFn: ({ reqId, status }: { reqId: string; status: string }) =>
      apiClient.put(`/api/v1/compliance/tenders/requirements/${reqId}/status`, { status }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ct-tender-requirements", id] }); qc.invalidateQueries({ queryKey: ["readiness"] }) },
  })

  const addPersonnel = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/compliance/tenders/${id}/personnel`, {
      employeeId: pickedEmployee!.id, role: personnelRole,
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["ct-tender-personnel", id] }); setPickedEmployee(null); setPersonnelRole(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to add personnel"),
  })

  const removePersonnel = useMutation({
    mutationFn: (personnelId: string) => apiClient.delete(`/api/v1/compliance/tenders/personnel/${personnelId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ct-tender-personnel", id] }),
  })

  if (isLoading || !tender) return <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading tender...</div>

  const cfg = STATUS_CFG[tender.status] ?? STATUS_CFG.DRAFT
  const nextStates = TRANSITIONS[tender.status] ?? []
  const inp: React.CSSProperties = { padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" as const }

  return (
    <div style={{ maxWidth: 960, margin: "0 auto" }}>
      <button onClick={() => nav(-1)} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", fontSize: 13, marginBottom: 18, padding: 0 }}>
        <ArrowLeft size={15} /> Back to Tenders
      </button>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 24, flexWrap: "wrap", gap: 14 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <div style={{ width: 48, height: 48, borderRadius: 12, background: "var(--hf-info-soft)", display: "flex", alignItems: "center", justifyContent: "center" }}>
            <Briefcase size={22} style={{ color: 'var(--hf-sky-text-strong)' }} />
          </div>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <h1 style={{ margin: 0, fontSize: 20, fontWeight: 700, color: "var(--hf-text)" }}>{tender.name}</h1>
              <span style={{ background: cfg.bg, color: cfg.color, padding: "4px 12px", borderRadius: 20, fontSize: 12, fontWeight: 700 }}>{cfg.label}</span>
            </div>
            <div style={{ fontSize: 13, color: "var(--hf-text-faint)", marginTop: 3 }}>{tender.tenderNumber}</div>
          </div>
        </div>
        <button onClick={() => downloadPdf(tender.id, tender.tenderNumber)}
          style={{ display: "flex", alignItems: "center", gap: 6, background: "var(--hf-surface)", color: "var(--hf-sky-text-strong)", border: "1px solid var(--hf-border)", borderRadius: 8, padding: "9px 16px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
          <Download size={14} /> Export PDF
        </button>
      </div>

      {canManage && nextStates.length > 0 && (
        <div style={{ display: "flex", gap: 8, marginBottom: 20, flexWrap: "wrap" }}>
          {nextStates.map(s => {
            const isOutcome = s === "AWARDED" || s === "UNSUCCESSFUL"
            const scfg = STATUS_CFG[s]
            return (
              <button key={s} onClick={() => isOutcome ? setShowOutcome(s) : transition.mutate(s)}
                disabled={transition.isPending}
                style={{ padding: "8px 16px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", border: `1.5px solid color-mix(in srgb, ${scfg.color} 20%, transparent)`, background: scfg.bg, color: scfg.color }}>
                Move to {scfg.label}
              </button>
            )
          })}
        </div>
      )}
      {apiError && <div style={{ marginBottom: 16, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{apiError}</div>}

      {(tender.status === "AWARDED" || tender.status === "UNSUCCESSFUL") && tender.outcomeReason && (
        <div style={{ marginBottom: 20, padding: "12px 16px", background: cfg.bg, border: `1px solid color-mix(in srgb, ${cfg.color} 20%, transparent)`, borderRadius: 10, fontSize: 13, color: cfg.color }}>
          <strong>{cfg.label} outcome:</strong> {tender.outcomeReason}
          {tender.awardedValue != null && ` · Awarded value: ${fmtZar(tender.awardedValue)}`}
        </div>
      )}

      <Section title="Tender Details">
        <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 16 }}>
          <Field l="Tender Authority" v={tender.tenderAuthority || "—"} />
          <Field l="Authority Reference" v={tender.authorityReferenceNumber || "—"} />
          <Field l="Estimated Value" v={fmtZar(tender.estimatedValue)} />
          <Field l="Closing Date" v={fmtDate(tender.closingDate)} />
          <Field l="Briefing Date" v={fmtDate(tender.briefingDate)} />
          <Field l="Site Inspection" v={fmtDate(tender.siteInspectionDate)} />
          <Field l="Industry" v={tender.industry || "—"} />
          <Field l="Required Class of Work" v={tender.requiredClassOfWork || "—"} />
        </div>
      </Section>

      <TenderReadinessPanel url={`/api/v1/compliance/tenders/${id}/readiness`} catalogueHref="/compliancetender/requirements" />

      <Section title="Requirement Matrix" icon={<ClipboardCheck size={15} style={{ color: 'var(--hf-sky-text-strong)' }} />}>
        {requirements.length === 0 ? (
          <div style={{ fontSize: 13, color: "var(--hf-text-faint)", padding: "8px 0" }}>No requirements captured yet.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: canManage ? 16 : 0 }}>
            {requirements.map(r => {
              const rcfg = REQ_STATUS_CFG[r.status] ?? REQ_STATUS_CFG.PENDING_REVIEW
              return (
                <div key={r.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, padding: "10px 14px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8 }}>
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{r.description}</div>
                    <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{r.source}</div>
                  </div>
                  {canManage ? (
                    <select value={r.status} onChange={e => updateRequirementStatus.mutate({ reqId: r.id, status: e.target.value })}
                      style={{ ...inp, fontSize: 11, fontWeight: 700, color: rcfg.color, background: rcfg.bg, border: "none", padding: "5px 10px" }}>
                      {REQUIREMENT_STATUSES.map(s => <option key={s} value={s}>{s.replace("_", " ")}</option>)}
                    </select>
                  ) : (
                    <span style={{ fontSize: 11, fontWeight: 700, color: rcfg.color, background: rcfg.bg, padding: "5px 10px", borderRadius: 6 }}>{r.status.replace("_", " ")}</span>
                  )}
                </div>
              )
            })}
          </div>
        )}
        {canManage && (
          <div>
            {trackedRequirements.length > 0 && (
              <div style={{ marginBottom: 8 }}>
                <select value={pickedRequirementId} onChange={e => {
                  const reqId = e.target.value
                  setPickedRequirementId(reqId)
                  const tracked = trackedRequirements.find(t => t.id === reqId)
                  if (tracked) { setNewRequirement(tracked.name); setNewRequirementSource("COMPLIANCE") }
                }} style={{ ...inp, width: "100%", background: "var(--hf-surface)" }}>
                  <option value="">— Add from tracked requirements, or type a custom one below —</option>
                  {trackedRequirements.map(t => <option key={t.id} value={t.id}>{t.code} — {t.name}</option>)}
                </select>
              </div>
            )}
            <div style={{ display: "flex", gap: 8 }}>
              <input value={newRequirement} onChange={e => { setNewRequirement(e.target.value); setPickedRequirementId("") }} placeholder="e.g. Valid CSD registration" style={{ ...inp, flex: 2 }} />
              <select value={newRequirementSource} onChange={e => setNewRequirementSource(e.target.value)} style={{ ...inp, flex: 1 }} disabled={!!pickedRequirementId}>
                {["COMPLIANCE", "PROJECTS", "HR", "FLEET", "ACCOUNTING", "MANUAL"].map(s => <option key={s} value={s}>{s}</option>)}
              </select>
              <button onClick={() => newRequirement.trim() && addRequirement.mutate()} disabled={!newRequirement.trim() || addRequirement.isPending}
                style={{ display: "flex", alignItems: "center", gap: 5, padding: "9px 14px", background: !newRequirement.trim() ? "var(--hf-border-strong)" : "var(--hf-sky-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: !newRequirement.trim() ? "not-allowed" : "pointer" }}>
                <Plus size={13} /> Add
              </button>
            </div>
            {pickedRequirementId && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 4 }}>Linked to the tracked requirement — clearing the text above will unlink it.</div>}
          </div>
        )}
      </Section>

      <Section title="Key Personnel" icon={<Users size={15} style={{ color: 'var(--hf-sky-text-strong)' }} />}>
        {personnel.length === 0 ? (
          <div style={{ fontSize: 13, color: "var(--hf-text-faint)", padding: "8px 0" }}>No personnel added yet.</div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: canManage ? 16 : 0 }}>
            {personnel.map(p => (
              <div key={p.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12, padding: "10px 14px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8 }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: p.employeeFound ? "var(--hf-text)" : "var(--hf-text-faint)" }}>
                    {p.employeeFound ? p.employeeFullName : "(employee record no longer available)"}
                  </div>
                  <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{p.role}{p.employeeNumber ? ` · ${p.employeeNumber}` : ""}</div>
                </div>
                {canManage && (
                  <button onClick={() => removePersonnel.mutate(p.id)} style={{ background: "var(--hf-danger-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
                )}
              </div>
            ))}
          </div>
        )}
        {canManage && (
          <div style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
            <div style={{ flex: 2 }}>
              <EmployeePicker value={pickedEmployee} onChange={setPickedEmployee} />
            </div>
            <input value={personnelRole} onChange={e => setPersonnelRole(e.target.value)} placeholder="Role on tender, e.g. Project Manager" style={{ ...inp, flex: 1 }} />
            <button onClick={() => pickedEmployee && personnelRole.trim() && addPersonnel.mutate()}
              disabled={!pickedEmployee || !personnelRole.trim() || addPersonnel.isPending}
              style={{ display: "flex", alignItems: "center", gap: 5, padding: "9px 14px", background: (!pickedEmployee || !personnelRole.trim()) ? "var(--hf-border-strong)" : "var(--hf-sky-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: (!pickedEmployee || !personnelRole.trim()) ? "not-allowed" : "pointer" }}>
              <Plus size={13} /> Add
            </button>
          </div>
        )}
      </Section>

      <div style={{ marginBottom: 20 }}>
        <button onClick={() => setSnapshotsOpen(o => !o)} style={{ display: "flex", alignItems: "center", gap: 8, background: "none", border: "none", cursor: "pointer", padding: "10px 0", width: "100%" }}>
          <History size={15} style={{ color: 'var(--hf-sky-text-strong)' }} />
          <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", textTransform: "uppercase" as const, letterSpacing: "0.04em" }}>Submission Snapshots</span>
          {snapshotsOpen ? <ChevronUp size={16} style={{ color: 'var(--hf-text-faint)' }} /> : <ChevronDown size={16} style={{ color: 'var(--hf-text-faint)' }} />}
        </button>
        {snapshotsOpen && (
          snapshots.length === 0 ? (
            <div style={{ fontSize: 13, color: "var(--hf-text-faint)", padding: "8px 0 8px 23px" }}>No submissions yet — a snapshot is captured automatically the first time this tender is moved to Submitted.</div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 6, paddingLeft: 23 }}>
              {snapshots.map(s => (
                <div key={s.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 8, overflow: "hidden" }}>
                  <button onClick={() => setExpandedSnapshot(expandedSnapshot === s.id ? null : s.id)}
                    style={{ width: "100%", display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 14px", background: "var(--hf-surface-muted)", border: "none", cursor: "pointer" }}>
                    <span style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>Submission #{s.snapshotNumber}</span>
                    <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{fmtDateTime(s.submittedAt)}</span>
                  </button>
                  {expandedSnapshot === s.id && (
                    <div style={{ padding: "12px 14px", fontSize: 12, color: "var(--hf-text-secondary)" }}>
                      <div style={{ marginBottom: 6 }}><strong>{s.data.requirements?.length ?? 0}</strong> requirement(s), <strong>{s.data.personnel?.length ?? 0}</strong> personnel — frozen exactly as they were at submission.</div>
                      {s.data.personnel?.map((p: any, i: number) => (
                        <div key={i} style={{ color: "var(--hf-text-muted)" }}>· {p.employeeFullName} — {p.role}</div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )
        )}
      </div>

      {showOutcome && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 480, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>Record Outcome — {STATUS_CFG[showOutcome].label}</h3>
              <button onClick={() => { setShowOutcome(null); setApiError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Reason</label>
              <textarea value={outcomeReason} onChange={e => setOutcomeReason(e.target.value)} rows={3} placeholder={showOutcome === "AWARDED" ? "e.g. Best technical score" : "e.g. Non-compliant B-BBEE certificate"} style={{ ...inp, width: "100%", resize: "vertical" as const }} />
            </div>
            {showOutcome === "AWARDED" && (
              <div style={{ marginBottom: 14 }}>
                <label style={lbl}>Awarded value (R)</label>
                <input type="number" value={awardedValue} onChange={e => setAwardedValue(e.target.value)} style={{ ...inp, width: "100%" }} />
              </div>
            )}
            {apiError && <div style={{ marginBottom: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => { setShowOutcome(null); setApiError("") }} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
              <button onClick={() => recordOutcome.mutate()} disabled={recordOutcome.isPending}
                style={{ padding: "9px 22px", background: recordOutcome.isPending ? "var(--hf-text-faint)" : STATUS_CFG[showOutcome].color, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: recordOutcome.isPending ? "not-allowed" : "pointer" }}>
                {recordOutcome.isPending ? "Saving..." : "Confirm"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function Section({ title, icon, children }: { title: string; icon?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 24 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
        {icon}
        <span style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", textTransform: "uppercase" as const, letterSpacing: "0.04em" }}>{title}</span>
      </div>
      {children}
    </div>
  )
}
function Field({ l, v }: { l: string; v: string }) {
  return (
    <div>
      <div style={{ fontSize: 10, color: "var(--hf-text-faint)", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{l}</div>
      <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{v}</div>
    </div>
  )
}
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }
