// src/pages/accountant/WorkpapersTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FolderOpen, FileText, Plus, Upload, Trash2, History, X, ChevronRight } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const fmtDT  = (d: any) => d ? new Date(d).toLocaleString("en-ZA") : "—"
const fmtKB  = (b: any) => b ? `${(Number(b) / 1024).toFixed(1)} KB` : "—"

const STATUS_CFG: Record<string, { label: string; color: string; bg: string }> = {
  DRAFT:      { label: "Draft",      color: "#64748B", bg: "#F1F5F9" },
  PREPARED:   { label: "Prepared",   color: "#1D4ED8", bg: "#EFF6FF" },
  REVIEWED:   { label: "Reviewed",   color: "#7C3AED", bg: "#F5F3FF" },
  SIGNED_OFF: { label: "Signed Off", color: "#166534", bg: "#DCFCE7" },
}
const NEXT_STATUS: Record<string, string> = { DRAFT: "PREPARED", PREPARED: "REVIEWED", REVIEWED: "SIGNED_OFF" }
const FOLDER_TYPES = ["TB", "RECONS", "TAX", "FS", "FICA", "GENERAL"]
const FOLDER_TYPE_LABELS: Record<string, string> = {
  TB: "Trial Balance", RECONS: "Reconciliations", TAX: "Tax", FS: "Financial Statements", FICA: "FICA", GENERAL: "General",
}
// Matches AccWorkpaperService's own ALLOWED_WORKPAPER_TYPES exactly.
const ALLOWED_TYPES = ["application/pdf", "image/jpeg", "image/jpg", "image/png",
  "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"]
const MAX_BYTES = 10 * 1024 * 1024

/**
 * Closes the accountant module audit's "larger workpaper system" gap —
 * the frontend for AccWorkpaperController. Deliberately its own tab,
 * not folded into ClientsTab's Client Workspace modal — the
 * interactivity needed here (folder navigation, versioned upload, a
 * 4-state review workflow, audit log) is a different category from
 * that modal's simple read-only list sections.
 */
export default function WorkpapersTab() {
  const qc = useQueryClient()
  const [selClient, setSelClient] = useState("")
  const [selFolder, setSelFolder] = useState<any>(null)
  const [showNewFolder, setShowNewFolder] = useState(false)
  const [showUpload, setShowUpload] = useState(false)
  const [auditFile, setAuditFile] = useState<any>(null)
  const [error, setError] = useState("")

  const { data: clients = [] } = useQuery<any[]>({
    queryKey: ["acc-clients"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accountant/clients?size=200")),
  })

  const { data: folders = [], isLoading: foldersLoading } = useQuery<any[]>({
    queryKey: ["acc-wp-folders", selClient],
    queryFn: async () => selClient ? unwrap(await apiClient.get(`/api/v1/accountant/clients/${selClient}/workpaper-folders`)) : [],
    enabled: !!selClient,
  })

  const { data: files = [], isLoading: filesLoading } = useQuery<any[]>({
    queryKey: ["acc-wp-files", selClient, selFolder?.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accountant/clients/${selClient}/workpaper-folders/${selFolder.id}/files`)),
    enabled: !!selClient && !!selFolder,
  })

  const { data: auditLog = [], isLoading: auditLoading } = useQuery<any[]>({
    queryKey: ["acc-wp-audit", selClient, auditFile?.id],
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/accountant/clients/${selClient}/workpaper-files/${auditFile.id}/audit`)),
    enabled: !!selClient && !!auditFile,
  })

  const NEW_FOLDER_INIT = () => ({ name: "", engagementYear: new Date().getFullYear(), folderType: "GENERAL" })
  const [folderForm, setFolderForm] = useState(NEW_FOLDER_INIT())

  const createFolderMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/accountant/clients/${selClient}/workpaper-folders`, {
      name: folderForm.name, parentId: null, engagementYear: folderForm.engagementYear, folderType: folderForm.folderType,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-wp-folders", selClient] })
      setShowNewFolder(false); setFolderForm(NEW_FOLDER_INIT()); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to create folder"),
  })

  const uploadFileMut = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/accountant/clients/${selClient}/workpaper-files`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-wp-files", selClient, selFolder?.id] })
      setShowUpload(false); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to upload file"),
  })

  const statusMut = useMutation({
    mutationFn: ({ fileId, status }: any) =>
      apiClient.post(`/api/v1/accountant/clients/${selClient}/workpaper-files/${fileId}/status`, { status }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-wp-files", selClient, selFolder?.id] }),
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to update status"),
  })

  const deleteMut = useMutation({
    mutationFn: (fileId: string) => apiClient.delete(`/api/v1/accountant/clients/${selClient}/workpaper-files/${fileId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["acc-wp-files", selClient, selFolder?.id] }),
  })

  const handleDownload = async (file: any) => {
    const res = await apiClient.get(`/api/v1/accountant/clients/${selClient}/workpaper-files/${file.id}`, { responseType: "blob" })
    const url = window.URL.createObjectURL(res.data as Blob)
    const a = document.createElement("a")
    a.href = url; a.download = file.fileName
    document.body.appendChild(a); a.click(); a.remove()
    window.URL.revokeObjectURL(url)
  }

  const [uploadFileName, setUploadFileName] = useState("")
  const handleUpload = (file: File) => {
    setError("")
    if (!ALLOWED_TYPES.includes(file.type)) { setError("Unsupported file type — please upload a PDF, JPG, PNG, Word, or Excel document"); return }
    if (file.size > MAX_BYTES) { setError(`File is too large — maximum is ${MAX_BYTES / (1024 * 1024)}MB`); return }
    setUploadFileName(file.name)
    const reader = new FileReader()
    reader.onload = () => {
      const base64 = ((reader.result as string) || "").split(",")[1] ?? ""
      uploadFileMut.mutate({
        folderId: selFolder.id, fileName: file.name, mimeType: file.type || "application/octet-stream",
        fileSizeBytes: file.size, fileContentBase64: base64,
      })
    }
    reader.readAsDataURL(file)
  }

  const foldersByYear = (folders as any[]).reduce((acc: any, f: any) => {
    if (!acc[f.engagementYear]) acc[f.engagementYear] = []
    acc[f.engagementYear].push(f)
    return acc
  }, {})

  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 20, alignItems: "center" }}>
        <select value={selClient} onChange={e => { setSelClient(e.target.value); setSelFolder(null); setError("") }}
          style={{ padding: "8px 12px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none", background: "#fff", minWidth: 220 }}>
          <option value="">Select a client...</option>
          {(clients as any[]).map((c: any) => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
        </select>
      </div>

      {!selClient ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <FolderOpen size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div>Select a client to view their workpapers.</div>
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "280px 1fr", gap: 20 }}>
          <div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: "#64748B", textTransform: "uppercase" as const }}>Folders</div>
              <button onClick={() => { setShowNewFolder(true); setError("") }}
                style={{ display: "flex", alignItems: "center", gap: 4, padding: "5px 10px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                <Plus size={12} /> New
              </button>
            </div>
            {foldersLoading ? (
              <div style={{ fontSize: 12, color: "#94A3B8" }}>Loading...</div>
            ) : folders.length === 0 ? (
              <div style={{ fontSize: 12, color: "#94A3B8" }}>No folders yet.</div>
            ) : (
              Object.entries(foldersByYear).sort((a, b) => Number(b[0]) - Number(a[0])).map(([year, yearFolders]: any) => (
                <div key={year} style={{ marginBottom: 14 }}>
                  <div style={{ fontSize: 11, fontWeight: 700, color: "#94A3B8", marginBottom: 6 }}>{year}</div>
                  {yearFolders.map((f: any) => (
                    <button key={f.id} onClick={() => setSelFolder(f)}
                      style={{ display: "flex", alignItems: "center", gap: 8, width: "100%", textAlign: "left" as const, padding: "8px 10px", marginBottom: 4, background: selFolder?.id === f.id ? "#EEF2FF" : "#fff", border: `1px solid ${selFolder?.id === f.id ? "#1B3A6B" : "#E2E8F0"}`, borderRadius: 7, fontSize: 13, fontWeight: selFolder?.id === f.id ? 600 : 400, color: selFolder?.id === f.id ? "#1B3A6B" : "#374151", cursor: "pointer" }}>
                      <FolderOpen size={14} />
                      <span style={{ flex: 1 }}>{f.name}</span>
                      {f.folderType && <span style={{ fontSize: 10, color: "#94A3B8" }}>{f.folderType}</span>}
                    </button>
                  ))}
                </div>
              ))
            )}
          </div>

          <div>
            {!selFolder ? (
              <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
                <ChevronRight size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
                <div>Select a folder to view its files.</div>
              </div>
            ) : (
              <div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
                  <div style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{selFolder.name}</div>
                  <button onClick={() => { setShowUpload(true); setError("") }}
                    style={{ display: "flex", alignItems: "center", gap: 6, padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
                    <Upload size={14} /> Upload File
                  </button>
                </div>

                {filesLoading ? (
                  <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>Loading...</div>
                ) : files.length === 0 ? (
                  <div style={{ textAlign: "center", padding: "40px 20px", color: "#94A3B8" }}>
                    <FileText size={32} style={{ marginBottom: 10, opacity: 0.3 }} />
                    <div>No files in this folder yet.</div>
                  </div>
                ) : (
                  <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    {files.map((f: any) => {
                      const sc = STATUS_CFG[f.reviewStatus] ?? STATUS_CFG.DRAFT
                      const superseded = !!f.supersededBy
                      return (
                        <div key={f.id} style={{ padding: "12px 16px", background: superseded ? "#F8FAFC" : "#fff", border: "1px solid #E2E8F0", borderRadius: 10, opacity: superseded ? 0.65 : 1 }}>
                          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 10 }}>
                            <div>
                              <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" as const }}>
                                <span style={{ fontWeight: 600, fontSize: 13, color: "#0F172A" }}>{f.fileName}</span>
                                <span style={{ fontSize: 11, color: "#94A3B8" }}>v{f.versionNumber}</span>
                                {superseded && <span style={{ background: "#F1F5F9", color: "#64748B", padding: "1px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>SUPERSEDED</span>}
                                <span style={{ background: sc.bg, color: sc.color, padding: "1px 8px", borderRadius: 20, fontSize: 10, fontWeight: 700 }}>{sc.label}</span>
                              </div>
                              <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtKB(f.fileSizeBytes)} · Uploaded {fmtD(f.createdAt)}</div>
                            </div>
                            <div style={{ display: "flex", gap: 6, flexShrink: 0 }}>
                              <button onClick={() => handleDownload(f)} title="Download"
                                style={{ padding: "6px 10px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 11, cursor: "pointer", color: "#374151" }}>
                                Download
                              </button>
                              <button onClick={() => setAuditFile(f)} title="Audit log"
                                style={{ padding: "6px 8px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, cursor: "pointer", color: "#64748B" }}>
                                <History size={13} />
                              </button>
                              {!superseded && (
                                NEXT_STATUS[f.reviewStatus] ? (
                                  <button onClick={() => statusMut.mutate({ fileId: f.id, status: NEXT_STATUS[f.reviewStatus] })}
                                    style={{ padding: "6px 12px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 7, fontSize: 11, fontWeight: 600, cursor: "pointer" }}>
                                    Mark {STATUS_CFG[NEXT_STATUS[f.reviewStatus]].label}
                                  </button>
                                ) : (
                                  <button onClick={() => statusMut.mutate({ fileId: f.id, status: "DRAFT" })}
                                    style={{ padding: "6px 10px", background: "#fff", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 11, cursor: "pointer", color: "#64748B" }}>
                                    Reopen
                                  </button>
                                )
                              )}
                              <button onClick={() => deleteMut.mutate(f.id)} title="Delete"
                                style={{ padding: "6px 8px", background: "#fff", border: "1px solid #FECACA", borderRadius: 7, cursor: "pointer", color: "#DC2626" }}>
                                <Trash2 size={13} />
                              </button>
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {error && (
        <div style={{ marginTop: 16, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>
      )}

      {showNewFolder && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 400, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 20px", fontSize: 16, fontWeight: 700 }}>New Folder</h3>
            <div style={{ marginBottom: 14 }}>
              <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Name *</label>
              <input autoFocus value={folderForm.name} onChange={e => setFolderForm(p => ({ ...p, name: e.target.value }))} placeholder="e.g. Bank Reconciliations"
                style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 6 }}>
              <div>
                <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Engagement year</label>
                <input type="number" value={folderForm.engagementYear} onChange={e => setFolderForm(p => ({ ...p, engagementYear: parseInt(e.target.value) || new Date().getFullYear() }))}
                  style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
              </div>
              <div>
                <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Type</label>
                <select value={folderForm.folderType} onChange={e => setFolderForm(p => ({ ...p, folderType: e.target.value }))}
                  style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }}>
                  {FOLDER_TYPES.map(t => <option key={t} value={t}>{FOLDER_TYPE_LABELS[t]}</option>)}
                </select>
              </div>
            </div>
            {error && <div style={{ marginTop: 12, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowNewFolder(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!folderForm.name || createFolderMut.isPending} onClick={() => createFolderMut.mutate()}
                style={{ padding: "9px 22px", background: !folderForm.name ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {createFolderMut.isPending ? "Creating..." : "Create Folder"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showUpload && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 420, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <h3 style={{ margin: "0 0 6px", fontSize: 16, fontWeight: 700 }}>Upload File</h3>
            <p style={{ margin: "0 0 20px", fontSize: 13, color: "#64748B" }}>
              Into <strong>{selFolder?.name}</strong>. Re-uploading the same file name creates a new version.
            </p>
            <input type="file" accept="application/pdf,image/jpeg,image/png,.doc,.docx,.xls,.xlsx"
              onChange={e => { const f = e.target.files?.[0]; if (f) handleUpload(f) }}
              disabled={uploadFileMut.isPending}
              style={{ width: "100%", fontSize: 13, marginBottom: 6 }} />
            {uploadFileMut.isPending && <div style={{ fontSize: 12, color: "#64748B", marginTop: 6 }}>Uploading {uploadFileName}...</div>}
            {error && <div style={{ marginTop: 12, padding: "8px 12px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 18 }}>
              <button onClick={() => setShowUpload(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Close</button>
            </div>
          </div>
        </div>
      )}

      {auditFile && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 460, maxHeight: "80vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>Audit Log — {auditFile.fileName}</h3>
              <button onClick={() => setAuditFile(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}><X size={18} /></button>
            </div>
            {auditLoading ? (
              <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>Loading...</div>
            ) : auditLog.length === 0 ? (
              <div style={{ textAlign: "center", padding: 30, color: "#94A3B8" }}>No audit events recorded.</div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                {auditLog.map((a: any) => (
                  <div key={a.id} style={{ display: "flex", justifyContent: "space-between", padding: "8px 12px", background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12 }}>
                    <span style={{ fontWeight: 600 }}>{a.eventType}</span>
                    <span style={{ color: "#94A3B8" }}>{fmtDT(a.performedAt)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
