// src/pages/warehousing/WhseReceiptDocuments.tsx
//
// Documents for an inbound receipt: the delivery note, photos of damage, the packing list. They stay with the shipment and anyone who can see
// the shipment can open them.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Paperclip } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, inputStyle, labelStyle } from "./whseUi"
import { DOCUMENT_TYPES, documentTypeLabel, fileProblem, fileSize } from "./receiptDocuments"

export interface ReceiptDocument { id: string; fileName: string; contentType: string; fileSizeBytes: number; evidenceType: string; uploadedByName: string | null; createdAt: string }

export default function WhseReceiptDocuments({ shipmentId }: { shipmentId: string }) {
  const qc = useQueryClient()
  const canUpload = usePermission("WAREHOUSING_MANAGE") || usePermission("WAREHOUSING_ADMIN")
  const base = `/api/v1/warehousing/inbound-shipments/${shipmentId}/evidence`
  const input = useRef<HTMLInputElement>(null)
  const [type, setType] = useState("DELIVERY_NOTE")
  const [file, setFile] = useState<File | null>(null)
  const [downloadError, setDownloadError] = useState<string | null>(null)

  const { data: loaded, isLoading } = useQuery<ReceiptDocument[]>({ queryKey: ["whse-receipt-documents", shipmentId], queryFn: async () => (await apiClient.get(base)).data })
  const docs = Array.isArray(loaded) ? loaded : []
  const problem = fileProblem(file)
  const upload = useMutation({
    mutationFn: async () => {
      const form = new FormData()
      form.append("file", file as File)
      form.append("evidenceType", type)
      return apiClient.post(base, form, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { setFile(null); if (input.current) input.current.value = ""; qc.invalidateQueries({ queryKey: ["whse-receipt-documents", shipmentId] }) },
  })

  const download = async (d: ReceiptDocument) => {
    try {
      const res = await apiClient.get(`${base}/${d.id}/download`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([res.data], { type: d.contentType }))
      const a = document.createElement("a")
      a.href = url; a.download = d.fileName
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
      setDownloadError(null)
    } catch {
      setDownloadError("Could not download that document.")
    }
  }

  return (
    <div style={{ ...card, marginTop: 22, padding: 14 }}>
      <p style={{ fontSize: 12.5, fontWeight: 700, margin: "0 0 8px", display: "flex", alignItems: "center", gap: 6 }}><Paperclip size={13} /> Documents</p>
      {isLoading ? <p style={{ fontSize: 12.5, margin: 0 }}>Loading…</p> : docs.length === 0 ? (
        <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>Nothing attached yet.</p>
      ) : docs.map(d => (
        <div key={d.id} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", padding: "6px 0", fontSize: 12.5 }}>
          <Chip bg="var(--hf-info-soft)" fg="var(--hf-info-text)">{documentTypeLabel(d.evidenceType)}</Chip>
          <button onClick={() => download(d)} style={{ ...btnGhost, padding: "3px 10px" }}>{d.fileName}</button>
          <span style={{ color: "var(--hf-text-faint)" }}>{fileSize(d.fileSizeBytes)} · {d.uploadedByName ?? "–"} · {new Date(d.createdAt).toLocaleString("en-ZA", { dateStyle: "short", timeStyle: "short" })}</span>
        </div>
      ))}
      {downloadError && <p role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "6px 0 0" }}>{downloadError}</p>}

      {canUpload && (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end", marginTop: 12 }}>
          <div><label style={labelStyle} htmlFor="rd-type">What is it</label>
            <select id="rd-type" style={inputStyle} value={type} onChange={e => setType(e.target.value)}>{DOCUMENT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}</select></div>
          <div><label style={labelStyle} htmlFor="rd-file">File</label>
            <input id="rd-file" ref={input} type="file" accept="image/*,application/pdf,text/*,.doc,.docx,.xls,.xlsx" style={{ fontSize: 12.5 }} onChange={e => setFile(e.target.files?.[0] ?? null)} /></div>
          <button style={{ ...btnPrimary, opacity: problem ? 0.5 : 1 }} disabled={!!problem || upload.isPending} onClick={() => upload.mutate()}>{upload.isPending ? "Attaching…" : "Attach"}</button>
          {file && problem && <span style={{ fontSize: 11.5, color: "var(--hf-danger-text)" }}>{problem}</span>}
        </div>
      )}
      <ErrorLine error={upload.error} fallback="Could not attach the document" />
    </div>
  )
}
