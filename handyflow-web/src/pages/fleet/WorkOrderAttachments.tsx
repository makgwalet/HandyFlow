// src/pages/fleet/WorkOrderAttachments.tsx
//
// Files on a work order: quotations, invoices, job cards and photos. Anyone with FLEET_MANAGE can add (any status); FLEET_ADMIN can delete.
import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { ATTACHMENT_LABEL, ATTACHMENT_TYPES, attachmentProblem, fileSizeText, suggestedAttachmentType } from "./workOrders.logic"

export interface WorkOrderAttachment { id: string; type: string; description: string | null; fileName: string; contentType: string; sizeBytes: number; uploadedAt: string }

const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5 }
const btn: React.CSSProperties = { padding: "6px 12px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }

function saveFile(data: BlobPart, fileName: string) {
  const url = URL.createObjectURL(new Blob([data])), a = document.createElement("a")
  a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url)
}

export default function WorkOrderAttachments({ orderId, status, attachments }: { orderId: string; status: string; attachments: WorkOrderAttachment[] }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE"), canAdmin = usePermission("FLEET_ADMIN")
  const base = `/api/v1/fleet/work-orders/${orderId}`
  const [type, setType] = useState(""), [description, setDescription] = useState(""), [file, setFile] = useState<File | null>(null), [fileKey, setFileKey] = useState(0)
  const [confirmId, setConfirmId] = useState<string | null>(null), [error, setError] = useState<string | null>(null)
  const refresh = () => qc.invalidateQueries({ queryKey: ["fleet", "work-order", orderId] })
  const chosenType = type || suggestedAttachmentType(status)
  const problem = attachmentProblem(chosenType, file, attachments.length)

  const upload = useMutation({
    mutationFn: () => { const f = new FormData(); f.append("file", file as File); f.append("type", chosenType); if (description.trim()) f.append("description", description.trim()); return apiClient.post(`${base}/attachments`, f) },
    onSuccess: () => { setError(null); setType(""); setDescription(""); setFile(null); setFileKey(k => k + 1); refresh() }, onError: e => setError(errorOf(e)),
  })
  const download = useMutation({
    mutationFn: async (a: WorkOrderAttachment) => { const r = await apiClient.get(`${base}/attachments/${a.id}/download`, { responseType: "blob" }); saveFile(r.data as BlobPart, a.fileName) },
    onError: e => setError(errorOf(e)),
  })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${base}/attachments/${id}`), onSuccess: () => { setConfirmId(null); setError(null); refresh() }, onError: e => setError(errorOf(e)) })

  return (
    <div style={{ marginTop: 12 }} aria-label="Attachments">
      <h4 style={{ margin: "0 0 4px" }}>Attachments ({attachments.length})</h4>
      {attachments.length === 0 && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "4px 0" }}>No files yet. Attach the quotation, invoice, job card or photos.</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0, fontSize: 13 }}>{attachments.map(a => (
        <li key={a.id} aria-label={a.fileName} style={{ padding: "5px 0", borderBottom: "1px solid var(--hf-border-subtle)", display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <strong>{ATTACHMENT_LABEL[a.type] ?? a.type}</strong><span>{a.fileName}</span>
          <span style={{ color: "var(--hf-text-faint)", fontSize: 11.5 }}>{fileSizeText(a.sizeBytes)} · {new Date(a.uploadedAt).toLocaleDateString("en-ZA")}</span>
          {a.description && <span style={{ color: "var(--hf-text-muted)", fontSize: 12 }}>{a.description}</span>}
          <span style={{ marginLeft: "auto", display: "flex", gap: 6 }}>
            <button type="button" style={field} aria-label={`Download ${a.fileName}`} onClick={() => download.mutate(a)}>Download</button>
            {canAdmin && (confirmId === a.id
              ? <><button type="button" style={{ ...field, color: "var(--hf-danger-text)", fontWeight: 700 }} aria-label={`Confirm delete ${a.fileName}`} onClick={() => remove.mutate(a.id)}>Confirm delete</button><button type="button" style={field} onClick={() => setConfirmId(null)}>Keep</button></>
              : <button type="button" style={field} aria-label={`Delete ${a.fileName}`} onClick={() => setConfirmId(a.id)}>Delete</button>)}
          </span>
        </li>))}</ul>
      {canManage && (
        <div style={{ marginTop: 8, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <select aria-label="Attachment type" style={field} value={chosenType} onChange={e => { setType(e.target.value); setError(null) }}>
            <option value="">Type…</option>{ATTACHMENT_TYPES.map(t => <option key={t} value={t}>{ATTACHMENT_LABEL[t]}</option>)}</select>
          <input aria-label="Attachment note" placeholder="Note (optional)" style={{ ...field, minWidth: 160 }} value={description} onChange={e => setDescription(e.target.value)} />
          <input key={fileKey} aria-label="Attachment file" type="file" accept=".pdf,.jpg,.jpeg,.png,.webp,.docx" onChange={e => { setFile(e.target.files?.[0] ?? null); setError(null) }} />
          <button type="button" style={{ ...btn, opacity: problem || upload.isPending ? 0.5 : 1 }} disabled={!!problem || upload.isPending} onClick={() => upload.mutate()}>{upload.isPending ? "Uploading…" : "Attach"}</button>
          {problem && file && <span style={{ fontSize: 11.5, color: "var(--hf-danger-text)" }}>{problem}</span>}
        </div>)}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </div>
  )
}
