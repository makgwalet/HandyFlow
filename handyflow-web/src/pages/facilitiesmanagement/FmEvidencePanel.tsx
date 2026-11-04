// Photos and files kept against a work order or an asset. The list and the upload live on the owner; download goes through the
// shared evidence endpoint. (There is no remove here: the server offers none for these.)
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { filenameFrom, saveBlob } from "../../api/recipientAccess.client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, ErrorLine, Pick, card, day, errorText, fail, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import { labelOf } from "./fm.logic"

interface Evidence { id: string; fileName: string; fileSizeBytes: number; evidenceType: string; uploadedByName: string | null; createdAt: string }
const KINDS = [["PHOTO", "Photo"], ["BEFORE_PHOTO", "Before photo"], ["AFTER_PHOTO", "After photo"], ["DOCUMENT", "Document"], ["CERTIFICATE", "Certificate"], ["OTHER", "Other"]] as const
const MAX = 10 * 1024 * 1024
export const fileSize = (b: number) => (b < 1024 ? `${b} B` : b < 1024 * 1024 ? `${Math.round(b / 102.4) / 10} KB` : `${Math.round(b / 104857.6) / 10} MB`)
export const fileProblem = (file: { size: number } | null) => !file ? "Choose a file" : file.size <= 0 ? "That file is empty" : file.size > MAX ? "That file is over 10 MB" : null

export const DOC_KINDS = [["DOCUMENT", "Document"], ["CERTIFICATE", "Certificate"], ["OTHER", "Other"]] as const

/** {@code path} replaces the usual address for owners that are not a work order or an asset (a vendor's document, say). */
export default function FmEvidencePanel({ owner, id, canWrite, title = "Photos and files", path, kinds = KINDS, defaultKind = "PHOTO" }:
  { owner: string; id: string; canWrite: boolean; title?: string; path?: string; kinds?: readonly (readonly [string, string])[]; defaultKind?: string }) {
  const qc = useQueryClient(), key = ["fm-evidence", owner, id], url = path ?? `${BASE}/${owner}/${id}/evidence`
  const [adding, setAdding] = useState(false)
  const { data, isLoading, isError } = useQuery({ queryKey: key, queryFn: async () => (await apiClient.get<Evidence[]>(url)).data })
  const rows = Array.isArray(data) ? data : []
  const download = useMutation({
    mutationFn: async (e: Evidence) => {
      const r = await apiClient.get(`/api/v1/evidence/${e.id}/download`, { responseType: "blob" })
      saveBlob(r.data as Blob, filenameFrom(r.headers?.["content-disposition"]) || e.fileName)
    },
    onError: e => { void fail(e, "Could not download that file") },
  })
  return (
    <div style={{ marginTop: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "0 0 6px" }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>{title}</div>
        {canWrite && <Btn small onClick={() => setAdding(true)}>Add a file</Btn>}
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <div style={{ ...muted, padding: 14 }}>Could not load the files.</div> : isLoading ? <div style={{ ...muted, padding: 14 }}>Loading…</div> : rows.length === 0 ? <div style={{ ...muted, padding: 14 }}>No files yet.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>File</th><th style={th}>Kind</th><th style={th}>Added</th><th /></tr></thead>
            <tbody>{rows.map(e => (
              <tr key={e.id}>
                <td style={td}><b style={{ wordBreak: "break-word" }}>{e.fileName}</b><div style={muted}>{fileSize(e.fileSizeBytes)}</div></td>
                <td style={td}>{labelOf(KINDS, e.evidenceType) || labelOf(DOC_KINDS, e.evidenceType)}</td>
                <td style={td}>{day(e.createdAt)}{e.uploadedByName ? <div style={muted}>{e.uploadedByName}</div> : null}</td>
                <td style={{ ...td, textAlign: "right" }}><Btn small disabled={download.isPending} onClick={() => download.mutate(e)}>Download</Btn></td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {adding && <AddDialog url={url} kinds={kinds} defaultKind={defaultKind} onClose={() => setAdding(false)} onDone={() => qc.invalidateQueries({ queryKey: key })} />}
    </div>
  )
}

function AddDialog({ url, kinds, defaultKind, onClose, onDone }: { url: string; kinds: readonly (readonly [string, string])[]; defaultKind: string; onClose: () => void; onDone: () => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null), [kind, setKind] = useState(defaultKind), [err, setErr] = useState("")
  const problem = fileProblem(file)
  const upload = useMutation({
    mutationFn: () => { const fd = new FormData(); fd.append("file", file!, file!.name); fd.append("evidenceType", kind); return apiClient.post(url, fd) },
    onSuccess: () => { toast.success("File added"); onDone(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not add the file")),
  })
  return (
    <Dialog title="Add a file" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || upload.isPending} onClick={() => { setErr(""); upload.mutate() }}>{upload.isPending ? "Uploading…" : "Add"}</Btn></>}>
      <div style={{ marginBottom: 12 }}>
        <input ref={input} type="file" aria-label="File" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 13 }} />
        {file && <div style={muted}>{file.name} · {fileSize(file.size)}</div>}
      </div>
      <Pick label="What is it?" value={kind} onChange={setKind} options={kinds} />
      {problem && file && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
