// src/pages/legalcompliance/LitigationTab.tsx
//
// Litigation register — confirmed against the real LitigationMatterController:
// GET (paged, status filter, sort openedDate), GET /count, GET /{id}, POST,
// PUT /{id}, POST /{id}/advance-status, POST /{id}/close (requires a
// terminal finalStatus — SETTLED/WITHDRAWN/CLOSED, enforced by
// LitigationMatter.close() itself), POST /{id}/link-contract,
// DELETE /{id} (ADMIN), POST+GET /{id}/evidence (EvidenceFacade wrapper —
// court filings, correspondence), GET /export/pdf.
//
// matterNumber is server-assigned (TenantSequenceService) — never sent on
// create, only ever displayed.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import {
  Plus, Gavel, ChevronDown, ChevronUp, X, Edit2, ArrowRightCircle, Flag,
  Link2, Trash2, Download, Paperclip, FileText, AlertCircle,
} from "lucide-react"

interface Matter {
  id: string; matterNumber: string; title: string; matterType: string; status: string
  opposingParty: string; ourSide: string | null; estimatedExposure: number | null
  legalRepresentative: string | null; courtOrForum: string | null; caseReference: string | null
  openedDate: string; nextKeyDate: string | null; closedDate: string | null
  description: string | null; outcomeNotes: string | null; linkedContractId: string | null
  createdAt: string; updatedAt: string
}
interface Evidence { id: string; fileName: string; contentType: string; fileSizeBytes: number; evidenceType: string; status: string; uploadedByName: string; createdAt: string }

const MATTER_TYPES = ["CIVIL", "LABOUR", "COMMERCIAL", "REGULATORY", "OTHER"]
const STATUSES = ["OPEN", "IN_PROGRESS", "SETTLED", "WITHDRAWN", "CLOSED"]
const TERMINAL_STATUSES = ["SETTLED", "WITHDRAWN", "CLOSED"]

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string }> = {
  OPEN:         { color: "#BE123C", bg: "#FFE4E6", border: "#FECDD3", label: "Open"         },
  IN_PROGRESS:  { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", label: "In Progress"   },
  SETTLED:      { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", label: "Settled"       },
  WITHDRAWN:    { color: "#64748B", bg: "#F1F5F9", border: "#E2E8F0", label: "Withdrawn"     },
  CLOSED:       { color: "#334155", bg: "#F8FAFC", border: "#E2E8F0", label: "Closed"        },
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtR = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}` : "—"
const fmtBytes = (b: number) => b < 1024 ? `${b} B` : b < 1024 * 1024 ? `${(b / 1024).toFixed(1)} KB` : `${(b / (1024 * 1024)).toFixed(1)} MB`

const EMPTY_FORM = {
  title: "", matterType: "CIVIL", opposingParty: "", ourSide: "", estimatedExposure: "",
  legalRepresentative: "", courtOrForum: "", caseReference: "", openedDate: "", nextKeyDate: "",
  description: "", linkedContractId: "",
}

async function downloadPdf() {
  const res = await apiClient.get("/api/v1/legalcompliance/matters/export/pdf", { responseType: "blob" })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement("a")
  a.href = url; a.download = "litigation-register.pdf"
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

function EvidenceSection({ matterId, canManage }: { matterId: string; canManage: boolean }) {
  const qc = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [evidenceType, setEvidenceType] = useState("")

  const { data: evidence = [] } = useQuery<Evidence[]>({
    queryKey: ["lc-matter-evidence", matterId],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/legalcompliance/matters/${matterId}/evidence`)),
  })

  const attach = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("file", file as File)
      fd.append("evidenceType", evidenceType)
      return apiClient.post(`/api/v1/legalcompliance/matters/${matterId}/evidence`, fd, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lc-matter-evidence", matterId] }); setFile(null); setEvidenceType("") },
  })

  return (
    <div style={{ marginTop: 14, paddingTop: 14, borderTop: "1px solid #E2E8F0" }}>
      <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Evidence — filings & correspondence</div>
      {evidence.length === 0 ? (
        <div style={{ fontSize: 12, color: "#94A3B8", marginBottom: 10 }}>No documents attached yet.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 6, marginBottom: 12 }}>
          {evidence.map(ev => (
            <div key={ev.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "7px 10px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
              <FileText size={13} color="#64748B" />
              <span style={{ fontWeight: 600, color: "#0F172A" }}>{ev.fileName}</span>
              <span style={{ color: "#94A3B8" }}>{ev.evidenceType} · {fmtBytes(ev.fileSizeBytes)}</span>
              <span style={{ marginLeft: "auto", color: "#94A3B8" }}>{ev.uploadedByName} · {fmtDate(ev.createdAt)}</span>
            </div>
          ))}
        </div>
      )}
      {canManage && (
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <input type="text" value={evidenceType} onChange={e => setEvidenceType(e.target.value)} placeholder="Type e.g. COURT_FILING" style={{ flex: 1, padding: "7px 10px", border: "1.5px solid #E2E8F0", borderRadius: 7, fontSize: 12 }} />
          <input type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 12, flex: 1 }} />
          <button onClick={() => attach.mutate()} disabled={!file || !evidenceType.trim() || attach.isPending}
            style={{ display: "flex", alignItems: "center", gap: 5, padding: "7px 12px", background: (!file || !evidenceType.trim()) ? "#CBD5E1" : "#4338CA", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: (!file || !evidenceType.trim()) ? "not-allowed" : "pointer" }}>
            <Paperclip size={12} /> {attach.isPending ? "Uploading..." : "Attach"}
          </button>
        </div>
      )}
    </div>
  )
}

export default function LitigationTab() {
  const qc = useQueryClient()
  const canManage = usePermission("LEGALCOMPLIANCE_MANAGE") || usePermission("LEGALCOMPLIANCE_ADMIN")
  const canAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")

  const [filterStatus, setFilterStatus] = useState("ALL")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showAdd, setShowAdd] = useState(false)
  const [editing, setEditing] = useState<Matter | null>(null)
  const [showAdvance, setShowAdvance] = useState<Matter | null>(null)
  const [showClose, setShowClose] = useState<Matter | null>(null)
  const [showLink, setShowLink] = useState<Matter | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [newStatus, setNewStatus] = useState("")
  const [finalStatus, setFinalStatus] = useState("")
  const [outcomeNotes, setOutcomeNotes] = useState("")
  const [contractId, setContractId] = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")

  const { data: matters = [], isLoading } = useQuery<Matter[]>({
    queryKey: ["lc-matters", filterStatus],
    queryFn: async () => unwrap(await apiClient.get(
      `/api/v1/legalcompliance/matters?size=200${filterStatus !== "ALL" ? `&status=${filterStatus}` : ""}`
    )),
  })

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["lc-matters"] }); qc.invalidateQueries({ queryKey: ["lc-matters-all"] }); qc.invalidateQueries({ queryKey: ["lc-calendar-30"] }) }

  const createMatter = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/legalcompliance/matters", body),
    onSuccess: () => { invalidate(); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to open matter") },
  })

  const updateMatter = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.put(`/api/v1/legalcompliance/matters/${id}`, body),
    onSuccess: () => { invalidate(); setEditing(null); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to update matter") },
  })

  const advanceStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => apiClient.post(`/api/v1/legalcompliance/matters/${id}/advance-status`, { status }),
    onSuccess: () => { invalidate(); setShowAdvance(null); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to advance status"),
  })

  const closeMatter = useMutation({
    mutationFn: ({ id, finalStatus, outcomeNotes }: { id: string; finalStatus: string; outcomeNotes: string }) =>
      apiClient.post(`/api/v1/legalcompliance/matters/${id}/close`, { finalStatus, outcomeNotes: outcomeNotes || null }),
    onSuccess: () => { invalidate(); setShowClose(null); setOutcomeNotes(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to close matter"),
  })

  const linkContract = useMutation({
    mutationFn: ({ id, contractId }: { id: string; contractId: string }) => apiClient.post(`/api/v1/legalcompliance/matters/${id}/link-contract`, { contractId }),
    onSuccess: () => { invalidate(); setShowLink(null); setContractId(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to link contract"),
  })

  const deleteMatter = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/legalcompliance/matters/${id}`),
    onSuccess: () => invalidate(),
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.title.trim()) errs.title = "Title is required"
    if (!form.opposingParty.trim()) errs.opposingParty = "Opposing party is required"
    if (!form.openedDate) errs.openedDate = "Opened date is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const openEdit = (m: Matter) => {
    setEditing(m)
    setForm({
      title: m.title, matterType: m.matterType, opposingParty: m.opposingParty, ourSide: m.ourSide ?? "",
      estimatedExposure: m.estimatedExposure != null ? String(m.estimatedExposure) : "",
      legalRepresentative: m.legalRepresentative ?? "", courtOrForum: m.courtOrForum ?? "",
      caseReference: m.caseReference ?? "", openedDate: m.openedDate, nextKeyDate: m.nextKeyDate ?? "",
      description: m.description ?? "", linkedContractId: m.linkedContractId ?? "",
    })
    setFieldErrors({}); setApiError("")
  }

  const stats = [
    { label: "Total",       value: matters.length,                                                       color: "#4338CA" },
    { label: "Open",        value: matters.filter(m => m.status === "OPEN").length,                      color: "#BE123C" },
    { label: "In Progress", value: matters.filter(m => m.status === "IN_PROGRESS").length,                color: "#1D4ED8" },
    { label: "Exposure (open)", value: fmtR(matters.filter(m => m.status === "OPEN" || m.status === "IN_PROGRESS").reduce((s, m) => s + (m.estimatedExposure ?? 0), 0)), color: "#D97706" },
  ]

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "#DC2626" : "#E2E8F0"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "#FFF5F5" : "#fff", outline: "none",
  })
  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "#DC2626", marginTop: 4 }}><AlertCircle size={12} />{fieldErrors[k]}</div>
  ) : null

  const StatusBadge = ({ status }: { status: string }) => {
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.OPEN
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}>{cfg.label}</span>
  }

  // Valid forward transitions for the advance-status action — the entity's own
  // advanceStatus() is the real source of truth for what's legal; this is
  // just a sensible client-side default list so the dropdown isn't the full
  // 5-status set including the current one or CLOSED-from-OPEN nonsense.
  const nextStatusOptions = (current: string) => STATUSES.filter(s => s !== current)

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 20, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {["ALL", ...STATUSES].map(s => (
            <button key={s} onClick={() => setFilterStatus(s)}
              style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: filterStatus === s ? 600 : 400,
                background: filterStatus === s ? (s === "ALL" ? "#4338CA" : STATUS_CFG[s]?.color ?? "#4338CA") : "#F1F5F9",
                color: filterStatus === s ? "#fff" : "#64748B" }}>
              {s === "ALL" ? "All" : STATUS_CFG[s]?.label ?? s}
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={downloadPdf} style={{ display: "flex", alignItems: "center", gap: 6, background: "#fff", color: "#4338CA", border: "1px solid #E2E8F0", borderRadius: 8, padding: "9px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
            <Download size={14} /> Export PDF
          </button>
          {canManage && (
            <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
              style={{ display: "flex", alignItems: "center", gap: 7, background: "#4338CA", color: "#fff", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
              <Plus size={15} /> Open Matter
            </button>
          )}
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading matters...</div>
      ) : matters.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <Gavel size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No litigation matters found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {matters.map(m => {
            const isOpen = expanded === m.id
            const cfg = STATUS_CFG[m.status] ?? STATUS_CFG.OPEN
            const isTerminal = TERMINAL_STATUSES.includes(m.status)
            return (
              <div key={m.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 44, height: 44, borderRadius: 10, background: cfg.bg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <Gavel size={18} color={cfg.color} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{m.matterNumber}</span>
                        <span style={{ fontSize: 14, color: "#64748B" }}>{m.title}</span>
                        <span style={{ fontSize: 11, color: "#64748B", background: "#F1F5F9", padding: "1px 8px", borderRadius: 20 }}>{m.matterType}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        vs. {m.opposingParty}{m.ourSide ? ` (we are ${m.ourSide})` : ""} · Opened {fmtDate(m.openedDate)}
                        {m.nextKeyDate ? ` · Next date ${fmtDate(m.nextKeyDate)}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" as const }}>
                      <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>{fmtR(m.estimatedExposure)}</div>
                      <div style={{ fontSize: 11, color: "#94A3B8" }}>exposure</div>
                    </div>
                    <StatusBadge status={m.status} />
                    {canManage && (
                      <div style={{ display: "flex", gap: 5 }}>
                        {!isTerminal && <button onClick={() => { setShowAdvance(m); setNewStatus("") }} title="Advance status" style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}><ArrowRightCircle size={13} /></button>}
                        {!isTerminal && <button onClick={() => { setShowClose(m); setFinalStatus("SETTLED"); setOutcomeNotes("") }} title="Close matter" style={{ background: "#F1F5F9", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#334155" }}><Flag size={13} /></button>}
                        <button onClick={() => { setShowLink(m); setContractId(m.linkedContractId ?? "") }} title="Link contract" style={{ background: "#F5F3FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#7C3AED" }}><Link2 size={13} /></button>
                        <button onClick={() => openEdit(m)} title="Edit" style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}><Edit2 size={13} /></button>
                        {canAdmin && (
                          <button onClick={() => { if (confirm(`Delete matter "${m.matterNumber}"?`)) deleteMatter.mutate(m.id) }} title="Delete" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>
                        )}
                      </div>
                    )}
                    <button onClick={() => setExpanded(isOpen ? null : m.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 14, marginBottom: 14 }}>
                      {[
                        { l: "Legal representative", v: m.legalRepresentative || "—" },
                        { l: "Court / forum",         v: m.courtOrForum || "—" },
                        { l: "Case reference",        v: m.caseReference || "—" },
                        { l: "Linked contract",       v: m.linkedContractId || "None" },
                        { l: "Closed date",           v: fmtDate(m.closedDate) },
                      ].map(item => (
                        <div key={item.l}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    {m.description && <div style={{ marginBottom: 10, fontSize: 13, color: "#374151" }}>{m.description}</div>}
                    {m.outcomeNotes && <div style={{ padding: "8px 12px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 13, color: "#166534" }}>Outcome: {m.outcomeNotes}</div>}
                    <EvidenceSection matterId={m.id} canManage={canManage} />
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {(showAdd || editing) && (
        <Overlay onClose={() => { setShowAdd(false); setEditing(null); setApiError("") }}>
          <MHead title={editing ? `Edit — ${editing.matterNumber}` : "Open Litigation Matter"} onClose={() => { setShowAdd(false); setEditing(null); setApiError("") }} />
          <Sect title="Matter Details">
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Title *</label>
              <input autoFocus value={form.title} onChange={e => { setForm(f => ({ ...f, title: e.target.value })); setFieldErrors(f => omit(f, "title")) }} placeholder="Dismissal dispute — J. Nkosi" style={inp("title")} />
              <FErr k="title" />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Matter type *</label>
                {editing ? <div style={{ ...inp("_"), background: "#F1F5F9", color: "#64748B" }}>{editing.matterType}</div> : (
                  <select value={form.matterType} onChange={e => setForm(f => ({ ...f, matterType: e.target.value }))} style={{ ...inp("matterType"), background: "#fff" }}>
                    {MATTER_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                  </select>
                )}
              </div>
              <div>
                <label style={lbl}>Opposing party *</label>
                <input value={form.opposingParty} onChange={e => { setForm(f => ({ ...f, opposingParty: e.target.value })); setFieldErrors(f => omit(f, "opposingParty")) }} style={inp("opposingParty")} />
                <FErr k="opposingParty" />
              </div>
              <div>
                <label style={lbl}>Our side</label>
                <input value={form.ourSide} onChange={e => setForm(f => ({ ...f, ourSide: e.target.value }))} placeholder="Claimant / Defendant" style={inp("ourSide")} />
              </div>
              <div>
                <label style={lbl}>Estimated exposure (R)</label>
                <input type="number" value={form.estimatedExposure} onChange={e => setForm(f => ({ ...f, estimatedExposure: e.target.value }))} style={inp("estimatedExposure")} />
              </div>
            </div>
          </Sect>

          <Sect title="Representation & Dates">
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Legal representative</label>
                <input value={form.legalRepresentative} onChange={e => setForm(f => ({ ...f, legalRepresentative: e.target.value }))} style={inp("legalRepresentative")} />
              </div>
              <div>
                <label style={lbl}>Court / forum</label>
                <input value={form.courtOrForum} onChange={e => setForm(f => ({ ...f, courtOrForum: e.target.value }))} placeholder="CCMA / High Court..." style={inp("courtOrForum")} />
              </div>
              <div>
                <label style={lbl}>Case reference</label>
                <input value={form.caseReference} onChange={e => setForm(f => ({ ...f, caseReference: e.target.value }))} style={inp("caseReference")} />
              </div>
              <div>
                <label style={lbl}>{editing ? "Opened date" : "Opened date *"}</label>
                {editing ? <div style={{ ...inp("_"), background: "#F1F5F9", color: "#64748B" }}>{fmtDate(editing.openedDate)}</div> : (
                  <><input type="date" value={form.openedDate} onChange={e => { setForm(f => ({ ...f, openedDate: e.target.value })); setFieldErrors(f => omit(f, "openedDate")) }} style={inp("openedDate")} /><FErr k="openedDate" /></>
                )}
              </div>
              <div style={{ gridColumn: editing ? "1 / -1" : undefined }}>
                <label style={lbl}>Next key date</label>
                <input type="date" value={form.nextKeyDate} onChange={e => setForm(f => ({ ...f, nextKeyDate: e.target.value }))} style={inp("nextKeyDate")} />
              </div>
              {!editing && (
                <div>
                  <label style={lbl}>Linked contract ID <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
                  <input value={form.linkedContractId} onChange={e => setForm(f => ({ ...f, linkedContractId: e.target.value }))} style={inp("linkedContractId")} />
                </div>
              )}
            </div>
          </Sect>

          <div>
            <label style={lbl}>Description</label>
            <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={2} style={{ ...inp("description"), resize: "vertical" as const }} />
          </div>

          {apiError && <ErrBanner msg={apiError} />}
          <MFoot
            onCancel={() => { setShowAdd(false); setEditing(null); setApiError("") }}
            onSubmit={() => {
              if (!validate()) return
              const body: any = {
                title: form.title, opposingParty: form.opposingParty, ourSide: form.ourSide || null,
                estimatedExposure: form.estimatedExposure ? Number(form.estimatedExposure) : null,
                legalRepresentative: form.legalRepresentative || null, courtOrForum: form.courtOrForum || null,
                caseReference: form.caseReference || null, nextKeyDate: form.nextKeyDate || null,
                description: form.description || null,
              }
              if (editing) updateMatter.mutate({ id: editing.id, body })
              else createMatter.mutate({ ...body, matterType: form.matterType, openedDate: form.openedDate, linkedContractId: form.linkedContractId || null })
            }}
            loading={createMatter.isPending || updateMatter.isPending}
            label={editing ? "Save Changes" : "Open Matter"}
          />
        </Overlay>
      )}

      {showAdvance && (
        <Overlay onClose={() => { setShowAdvance(null); setApiError("") }}>
          <MHead title={`Advance Status — ${showAdvance.matterNumber}`} onClose={() => { setShowAdvance(null); setApiError("") }} />
          <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 16 }}>
            {nextStatusOptions(showAdvance.status).map(s => {
              const cfg = STATUS_CFG[s]; const sel = newStatus === s
              return (
                <button key={s} onClick={() => setNewStatus(s)}
                  style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 16px", border: `2px solid ${sel ? cfg.color : "#E2E8F0"}`, borderRadius: 10, cursor: "pointer", background: sel ? cfg.bg : "#fff", textAlign: "left" as const, width: "100%" }}>
                  <span style={{ fontWeight: 600, color: sel ? cfg.color : "#0F172A" }}>{cfg.label}</span>
                </button>
              )
            })}
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowAdvance(null); setApiError("") }} onSubmit={() => advanceStatus.mutate({ id: showAdvance.id, status: newStatus })} loading={advanceStatus.isPending} label="Update Status" disabled={!newStatus} />
        </Overlay>
      )}

      {showClose && (
        <Overlay onClose={() => { setShowClose(null); setApiError("") }}>
          <MHead title={`Close Matter — ${showClose.matterNumber}`} onClose={() => { setShowClose(null); setApiError("") }} />
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Final status *</label>
            <select value={finalStatus} onChange={e => setFinalStatus(e.target.value)} style={{ ...inp("_"), width: "100%", background: "#fff" }}>
              {TERMINAL_STATUSES.map(s => <option key={s} value={s}>{STATUS_CFG[s].label}</option>)}
            </select>
            <div style={{ fontSize: 11, color: "#94A3B8", marginTop: 4 }}>Must be a terminal status — enforced by the backend.</div>
          </div>
          <label style={lbl}>Outcome notes</label>
          <textarea value={outcomeNotes} onChange={e => setOutcomeNotes(e.target.value)} rows={3} style={{ ...inp("_"), width: "100%", resize: "vertical" as const }} />
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowClose(null); setApiError("") }} onSubmit={() => closeMatter.mutate({ id: showClose.id, finalStatus, outcomeNotes })} loading={closeMatter.isPending} label="Close Matter" />
        </Overlay>
      )}

      {showLink && (
        <Overlay onClose={() => { setShowLink(null); setApiError("") }}>
          <MHead title={`Link Contract — ${showLink.matterNumber}`} onClose={() => { setShowLink(null); setApiError("") }} />
          <label style={lbl}>Contract ID</label>
          <input value={contractId} onChange={e => setContractId(e.target.value)} placeholder="UUID from Contracting module" style={{ ...inp("_"), width: "100%" }} />
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowLink(null); setApiError("") }} onSubmit={() => { if (contractId.trim()) linkContract.mutate({ id: showLink.id, contractId: contractId.trim() }) }} loading={linkContract.isPending} label="Link Contract" disabled={!contractId.trim()} />
        </Overlay>
      )}
    </div>
  )
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 640, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button><button onClick={onSubmit} disabled={loading || disabled} style={{ padding: "9px 22px", background: loading || disabled ? "#94A3B8" : "#4338CA", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return <div style={{ marginBottom: 20 }}><div style={{ fontSize: 10, fontWeight: 700, color: "#94A3B8", letterSpacing: "0.07em", textTransform: "uppercase" as const, marginBottom: 12, paddingBottom: 8, borderBottom: "1px solid #F1F5F9" }}>{title}</div>{children}</div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{msg}</div>
}
const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
