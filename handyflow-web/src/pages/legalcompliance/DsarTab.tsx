// src/pages/legalcompliance/DsarTab.tsx
//
// Org-wide POPIA data subject access request (DSAR) register — confirmed
// against the real DsarRequestController: GET (paged, status filter, sort
// dueDate), GET /open, GET /{id}, POST, POST /{id}/assign, /complete,
// /reject, /withdraw, DELETE /{id} (ADMIN), POST+GET /{id}/evidence
// (ID scans, request letters, response correspondence), GET /export/pdf.
//
// dueDate is computed server-side as receivedDate + 30 days
// (DsarRequest.create()) — the entity's own Javadoc flags this as an
// operational default, not an independently-confirmed hard POPIA statutory
// deadline, so it's shown here as-is rather than re-derived client-side.
// assign() auto-promotes RECEIVED -> IN_PROGRESS on the backend; complete/
// reject/withdraw are all blocked once the request is already in a
// terminal state (assertOpen()), so those actions are hidden once overdue
// status resolves to one of those.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import {
  Plus, FileSearch, ChevronDown, ChevronUp, X, UserPlus, CheckCircle2,
  XCircle, Undo2, Trash2, Download, Paperclip, FileText, AlertCircle, AlertTriangle,
} from "lucide-react"

interface DsarRequest {
  id: string; requestNumber: string; requestType: string; dataCategory: string
  requesterName: string; requesterEmail: string | null; requesterContact: string | null
  receivedDate: string; dueDate: string; status: string; assignedToUserId: string | null
  assignedToUserName: string | null; resolutionNotes: string | null; completedDate: string | null
  overdue: boolean; createdAt: string; updatedAt: string
}
interface Evidence { id: string; fileName: string; contentType: string; fileSizeBytes: number; evidenceType: string; status: string; uploadedByName: string; createdAt: string }

const REQUEST_TYPES = ["ACCESS", "CORRECTION", "DELETION", "OBJECTION"]
const DATA_CATEGORIES = ["CUSTOMER", "EMPLOYEE", "SUPPLIER", "MARKETING_CONTACT", "OTHER"]
const STATUSES = ["RECEIVED", "IN_PROGRESS", "COMPLETED", "REJECTED", "WITHDRAWN"]
const OPEN_STATUSES = new Set(["RECEIVED", "IN_PROGRESS"])

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string }> = {
  RECEIVED:    { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", label: "Received"    },
  IN_PROGRESS: { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", label: "In Progress"  },
  COMPLETED:   { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", label: "Completed"    },
  REJECTED:    { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", label: "Rejected"     },
  WITHDRAWN:   { color: "#64748B", bg: "#F1F5F9", border: "#E2E8F0", label: "Withdrawn"    },
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtBytes = (b: number) => b < 1024 ? `${b} B` : b < 1024 * 1024 ? `${(b / 1024).toFixed(1)} KB` : `${(b / (1024 * 1024)).toFixed(1)} MB`
const daysUntil = (d: string) => Math.ceil((new Date(d).getTime() - Date.now()) / 86400000)

const EMPTY_FORM = { requestType: "ACCESS", dataCategory: "CUSTOMER", requesterName: "", requesterEmail: "", requesterContact: "", receivedDate: new Date().toISOString().split("T")[0] }

async function downloadPdf() {
  const res = await apiClient.get("/api/v1/legalcompliance/dsar-requests/export/pdf", { responseType: "blob" })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement("a")
  a.href = url; a.download = "dsar-request-log.pdf"
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

function EvidenceSection({ requestId, canManage }: { requestId: string; canManage: boolean }) {
  const qc = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [evidenceType, setEvidenceType] = useState("")

  const { data: evidence = [] } = useQuery<Evidence[]>({
    queryKey: ["lc-dsar-evidence", requestId],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/legalcompliance/dsar-requests/${requestId}/evidence`)),
  })

  const attach = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("file", file as File)
      fd.append("evidenceType", evidenceType)
      return apiClient.post(`/api/v1/legalcompliance/dsar-requests/${requestId}/evidence`, fd, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["lc-dsar-evidence", requestId] }); setFile(null); setEvidenceType("") },
  })

  return (
    <div style={{ marginTop: 14, paddingTop: 14, borderTop: "1px solid #E2E8F0" }}>
      <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 10 }}>Evidence — ID scans, request letters, correspondence</div>
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
          <input type="text" value={evidenceType} onChange={e => setEvidenceType(e.target.value)} placeholder="Type e.g. ID_SCAN" style={{ flex: 1, padding: "7px 10px", border: "1.5px solid #E2E8F0", borderRadius: 7, fontSize: 12 }} />
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

export default function DsarTab() {
  const qc = useQueryClient()
  const canManage = usePermission("LEGALCOMPLIANCE_MANAGE") || usePermission("LEGALCOMPLIANCE_ADMIN")
  const canAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")

  const [filterStatus, setFilterStatus] = useState("ALL")
  const [openOnly, setOpenOnly] = useState(false)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [showAdd, setShowAdd] = useState(false)
  const [showAssign, setShowAssign] = useState<DsarRequest | null>(null)
  const [showResolve, setShowResolve] = useState<{ request: DsarRequest; action: "complete" | "reject" | "withdraw" } | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [assignUserId, setAssignUserId] = useState("")
  const [assignUserName, setAssignUserName] = useState("")
  const [resolutionNotes, setResolutionNotes] = useState("")
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")

  const { data: pagedRequests = [], isLoading: loadingPaged } = useQuery<DsarRequest[]>({
    queryKey: ["lc-dsar", filterStatus],
    queryFn: async () => unwrap(await apiClient.get(
      `/api/v1/legalcompliance/dsar-requests?size=200${filterStatus !== "ALL" ? `&status=${filterStatus}` : ""}`
    )),
    enabled: !openOnly,
  })
  const { data: openRequests = [], isLoading: loadingOpen } = useQuery<DsarRequest[]>({
    queryKey: ["lc-dsar-open"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/dsar-requests/open")),
    enabled: openOnly,
  })
  const requests = openOnly ? openRequests : pagedRequests
  const isLoading = openOnly ? loadingOpen : loadingPaged

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["lc-dsar"] }); qc.invalidateQueries({ queryKey: ["lc-dsar-open"] }); qc.invalidateQueries({ queryKey: ["lc-dsar-all"] }) }

  const createRequest = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/legalcompliance/dsar-requests", body),
    onSuccess: () => { invalidate(); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to log DSAR request") },
  })

  const assign = useMutation({
    mutationFn: ({ id, userId, userName }: { id: string; userId: string; userName: string }) =>
      apiClient.post(`/api/v1/legalcompliance/dsar-requests/${id}/assign`, { userId, userName }),
    onSuccess: () => { invalidate(); setShowAssign(null); setAssignUserId(""); setAssignUserName(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to assign"),
  })

  const resolve = useMutation({
    mutationFn: ({ id, action, notes }: { id: string; action: "complete" | "reject" | "withdraw"; notes: string }) =>
      apiClient.post(`/api/v1/legalcompliance/dsar-requests/${id}/${action}`, notes ? { resolutionNotes: notes } : {}),
    onSuccess: () => { invalidate(); setShowResolve(null); setResolutionNotes(""); setApiError("") },
    onError: (e: any) => setApiError(e.response?.data?.message ?? "Failed to update request"),
  })

  const deleteRequest = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/legalcompliance/dsar-requests/${id}`),
    onSuccess: () => invalidate(),
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.requesterName.trim()) errs.requesterName = "Requester name is required"
    if (!form.receivedDate) errs.receivedDate = "Received date is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const stats = [
    { label: "Total",     value: requests.length,                                          color: "#4338CA" },
    { label: "Open",      value: requests.filter(r => OPEN_STATUSES.has(r.status)).length,  color: "#1D4ED8" },
    { label: "Overdue",   value: requests.filter(r => r.overdue).length,                    color: "#DC2626" },
    { label: "Completed", value: requests.filter(r => r.status === "COMPLETED").length,     color: "#166534" },
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
    const cfg = STATUS_CFG[status] ?? STATUS_CFG.RECEIVED
    return <span style={{ display: "inline-flex", alignItems: "center", gap: 4, background: cfg.bg, color: cfg.color, padding: "3px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, border: `1px solid ${cfg.border}` }}>{cfg.label}</span>
  }

  const actionLabel = { complete: "Mark Completed", reject: "Reject Request", withdraw: "Mark Withdrawn" }
  const actionColor = { complete: "#166534", reject: "#DC2626", withdraw: "#64748B" }

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 22 }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 16px" }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <button onClick={() => setOpenOnly(o => !o)}
            style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: openOnly ? 600 : 400,
              background: openOnly ? "#4338CA" : "#F1F5F9", color: openOnly ? "#fff" : "#64748B" }}>
            Open only
          </button>
          {!openOnly && ["ALL", ...STATUSES].map(s => (
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
              <Plus size={15} /> Log DSAR
            </button>
          )}
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading requests...</div>
      ) : requests.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FileSearch size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>No DSAR requests found</div>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {requests.map(r => {
            const isOpen = expanded === r.id
            const isTerminal = !OPEN_STATUSES.has(r.status)
            const days = daysUntil(r.dueDate)
            return (
              <div key={r.id} style={{ border: `1px solid ${r.overdue ? "#FECACA" : "#E2E8F0"}`, borderRadius: 12, overflow: "hidden" }}>
                <div style={{ padding: "16px 20px", background: "#fff", display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flex: 1, minWidth: 0 }}>
                    <div style={{ width: 44, height: 44, borderRadius: 10, background: r.overdue ? "#FEF2F2" : "#EEF2FF", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                      <FileSearch size={18} color={r.overdue ? "#DC2626" : "#4338CA"} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3, flexWrap: "wrap" }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{r.requestNumber}</span>
                        <span style={{ fontSize: 14, color: "#64748B" }}>{r.requesterName}</span>
                        <span style={{ fontSize: 11, color: "#64748B", background: "#F1F5F9", padding: "1px 8px", borderRadius: 20 }}>{r.requestType}</span>
                        {r.overdue && <span style={{ display: "flex", alignItems: "center", gap: 3, fontSize: 10, fontWeight: 700, background: "#FEF2F2", color: "#DC2626", padding: "1px 7px", borderRadius: 20 }}><AlertTriangle size={10} />OVERDUE</span>}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>
                        {r.dataCategory} · Received {fmtDate(r.receivedDate)} · Due {fmtDate(r.dueDate)}
                        {r.assignedToUserName ? ` · Assigned to ${r.assignedToUserName}` : ""}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexShrink: 0 }}>
                    {!isTerminal && (
                      <div style={{ textAlign: "right" as const }}>
                        <div style={{ fontWeight: 700, fontSize: 12, color: days < 0 ? "#DC2626" : days <= 5 ? "#D97706" : "#0F172A" }}>
                          {days < 0 ? `${Math.abs(days)}d overdue` : `${days}d left`}
                        </div>
                      </div>
                    )}
                    <StatusBadge status={r.status} />
                    {canManage && (
                      <div style={{ display: "flex", gap: 5 }}>
                        {!isTerminal && <button onClick={() => { setShowAssign(r); setAssignUserId(r.assignedToUserId ?? ""); setAssignUserName(r.assignedToUserName ?? "") }} title="Assign" style={{ background: "#F5F3FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#7C3AED" }}><UserPlus size={13} /></button>}
                        {!isTerminal && <button onClick={() => { setShowResolve({ request: r, action: "complete" }); setResolutionNotes("") }} title="Mark completed" style={{ background: "#DCFCE7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#166534" }}><CheckCircle2 size={13} /></button>}
                        {!isTerminal && <button onClick={() => { setShowResolve({ request: r, action: "reject" }); setResolutionNotes("") }} title="Reject" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><XCircle size={13} /></button>}
                        {!isTerminal && <button onClick={() => { setShowResolve({ request: r, action: "withdraw" }); setResolutionNotes("") }} title="Mark withdrawn" style={{ background: "#F1F5F9", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#64748B" }}><Undo2 size={13} /></button>}
                        {canAdmin && (
                          <button onClick={() => { if (confirm(`Delete request "${r.requestNumber}"?`)) deleteRequest.mutate(r.id) }} title="Delete" style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={13} /></button>
                        )}
                      </div>
                    )}
                    <button onClick={() => setExpanded(isOpen ? null : r.id)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                      {isOpen ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                    </button>
                  </div>
                </div>
                {isOpen && (
                  <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 20px", background: "#F8FAFC" }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 14, marginBottom: 14 }}>
                      {[
                        { l: "Requester email",   v: r.requesterEmail || "—" },
                        { l: "Requester contact", v: r.requesterContact || "—" },
                        { l: "Completed date",    v: fmtDate(r.completedDate) },
                      ].map(item => (
                        <div key={item.l}>
                          <div style={{ fontSize: 10, color: "#94A3B8", fontWeight: 700, textTransform: "uppercase" as const, letterSpacing: "0.06em", marginBottom: 3 }}>{item.l}</div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A" }}>{item.v}</div>
                        </div>
                      ))}
                    </div>
                    {r.resolutionNotes && <div style={{ padding: "8px 12px", background: "#F0FDF4", border: "1px solid #86EFAC", borderRadius: 8, fontSize: 13, color: "#166534" }}>Resolution: {r.resolutionNotes}</div>}
                    <EvidenceSection requestId={r.id} canManage={canManage} />
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {showAdd && (
        <Overlay onClose={() => { setShowAdd(false); setApiError("") }}>
          <MHead title="Log DSAR Request" onClose={() => { setShowAdd(false); setApiError("") }} />
          <div style={{ padding: "10px 12px", background: "#EEF2FF", border: "1px solid #C7D2FE", borderRadius: 8, fontSize: 12, color: "#4338CA", marginBottom: 16 }}>
            Due date is set automatically to 30 days after the received date.
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
            <div>
              <label style={lbl}>Request type *</label>
              <select value={form.requestType} onChange={e => setForm(f => ({ ...f, requestType: e.target.value }))} style={{ ...inp("requestType"), background: "#fff" }}>
                {REQUEST_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Data category *</label>
              <select value={form.dataCategory} onChange={e => setForm(f => ({ ...f, dataCategory: e.target.value }))} style={{ ...inp("dataCategory"), background: "#fff" }}>
                {DATA_CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>Requester name *</label>
            <input autoFocus value={form.requesterName} onChange={e => { setForm(f => ({ ...f, requesterName: e.target.value })); setFieldErrors(f => omit(f, "requesterName")) }} style={inp("requesterName")} />
            <FErr k="requesterName" />
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
            <div>
              <label style={lbl}>Requester email</label>
              <input type="email" value={form.requesterEmail} onChange={e => setForm(f => ({ ...f, requesterEmail: e.target.value }))} style={inp("requesterEmail")} />
            </div>
            <div>
              <label style={lbl}>Requester contact</label>
              <input value={form.requesterContact} onChange={e => setForm(f => ({ ...f, requesterContact: e.target.value }))} style={inp("requesterContact")} />
            </div>
          </div>
          <div>
            <label style={lbl}>Received date *</label>
            <input type="date" value={form.receivedDate} onChange={e => { setForm(f => ({ ...f, receivedDate: e.target.value })); setFieldErrors(f => omit(f, "receivedDate")) }} style={inp("receivedDate")} />
            <FErr k="receivedDate" />
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot
            onCancel={() => { setShowAdd(false); setApiError("") }}
            onSubmit={() => { if (validate()) createRequest.mutate({ requestType: form.requestType, dataCategory: form.dataCategory, requesterName: form.requesterName, requesterEmail: form.requesterEmail || null, requesterContact: form.requesterContact || null, receivedDate: form.receivedDate }) }}
            loading={createRequest.isPending}
            label="Log Request"
          />
        </Overlay>
      )}

      {showAssign && (
        <Overlay onClose={() => { setShowAssign(null); setApiError("") }}>
          <MHead title={`Assign — ${showAssign.requestNumber}`} onClose={() => { setShowAssign(null); setApiError("") }} />
          <div style={{ fontSize: 12, color: "#94A3B8", marginBottom: 14 }}>
            No staff picker is wired up for this module yet — enter the internal user's ID and display name directly. Assigning also moves a Received request to In Progress automatically.
          </div>
          <div style={{ marginBottom: 14 }}>
            <label style={lbl}>User ID (UUID)</label>
            <input value={assignUserId} onChange={e => setAssignUserId(e.target.value)} placeholder="Internal user ID" style={{ ...inp("_"), width: "100%" }} />
          </div>
          <div>
            <label style={lbl}>User name</label>
            <input value={assignUserName} onChange={e => setAssignUserName(e.target.value)} placeholder="Display name" style={{ ...inp("_"), width: "100%" }} />
          </div>
          {apiError && <ErrBanner msg={apiError} />}
          <MFoot onCancel={() => { setShowAssign(null); setApiError("") }} onSubmit={() => { if (assignUserId.trim() && assignUserName.trim()) assign.mutate({ id: showAssign.id, userId: assignUserId.trim(), userName: assignUserName.trim() }) }} loading={assign.isPending} label="Assign" disabled={!assignUserId.trim() || !assignUserName.trim()} />
        </Overlay>
      )}

      {showResolve && (
        <Overlay onClose={() => { setShowResolve(null); setApiError("") }}>
          <MHead title={`${actionLabel[showResolve.action]} — ${showResolve.request.requestNumber}`} onClose={() => { setShowResolve(null); setApiError("") }} />
          <label style={lbl}>Resolution notes <span style={{ fontWeight: 400, color: "#94A3B8" }}>(optional)</span></label>
          <textarea value={resolutionNotes} onChange={e => setResolutionNotes(e.target.value)} rows={3} style={{ ...inp("_"), width: "100%", resize: "vertical" as const }} />
          {apiError && <ErrBanner msg={apiError} />}
          <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
            <button onClick={() => { setShowResolve(null); setApiError("") }} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button>
            <button onClick={() => resolve.mutate({ id: showResolve.request.id, action: showResolve.action, notes: resolutionNotes })} disabled={resolve.isPending}
              style={{ padding: "9px 22px", background: resolve.isPending ? "#94A3B8" : actionColor[showResolve.action], color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: resolve.isPending ? "not-allowed" : "pointer" }}>
              {resolve.isPending ? "Saving..." : actionLabel[showResolve.action]}
            </button>
          </div>
        </Overlay>
      )}
    </div>
  )
}

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
      <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 600, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>{children}</div>
    </div>
  )
}
function MHead({ title, onClose }: { title: string; onClose: () => void }) {
  return <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}><h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#0F172A" }}>{title}</h3><button onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8", display: "flex" }}><X size={20} /></button></div>
}
function MFoot({ onCancel, onSubmit, loading, label, disabled = false }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string; disabled?: boolean }) {
  return <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}><button onClick={onCancel} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer", color: "#374151" }}>Cancel</button><button onClick={onSubmit} disabled={loading || disabled} style={{ padding: "9px 22px", background: loading || disabled ? "#94A3B8" : "#4338CA", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading || disabled ? "not-allowed" : "pointer" }}>{loading ? "Saving..." : label}</button></div>
}
function ErrBanner({ msg }: { msg: string }) {
  return <div style={{ marginTop: 14, padding: "10px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626", display: "flex", alignItems: "center", gap: 8 }}><AlertCircle size={14} />{msg}</div>
}
const omit = (obj: Record<string, string>, key: string) => { const n = { ...obj }; delete n[key]; return n }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }
