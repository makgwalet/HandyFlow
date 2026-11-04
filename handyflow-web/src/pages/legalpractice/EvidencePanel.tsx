// Documents attached to a matter or a client: list, add, download, remove. Same endpoints on both, so one panel serves both.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { filenameFrom, saveBlob } from "../../api/recipientAccess.client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, ErrorLine, Pick, card, day, errorText, fail, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import { EVIDENCE_TYPES, evidenceProblem, fileSize, labelOf } from "./legal.logic"

interface Evidence { id: string; fileName: string; contentType: string; fileSizeBytes: number; evidenceType: string; status: string; uploadedByName: string | null; createdAt: string }

export default function EvidencePanel({ owner, id, canWrite }: { owner: "matters" | "clients"; id: string; canWrite: boolean }) {
  const qc = useQueryClient()
  const key = ["lp-evidence", owner, id]
  const url = `${BASE}/${owner}/${id}/evidence`
  const [adding, setAdding] = useState(false), [removing, setRemoving] = useState<Evidence | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: key, queryFn: async () => (await apiClient.get<Evidence[]>(url)).data })
  const rows = Array.isArray(data) ? data : []
  const download = useMutation({
    mutationFn: async (e: Evidence) => {
      const r = await apiClient.get(`${url}/${e.id}/download`, { responseType: "blob" })
      saveBlob(r.data as Blob, filenameFrom(r.headers?.["content-disposition"]) || e.fileName)
    },
    onError: e => { void fail(e, "Could not download that file") },
  })
  const remove = useMutation({
    mutationFn: (e: Evidence) => apiClient.post(`${url}/${e.id}/detach`),
    onSuccess: () => { toast.success("Document removed"); qc.invalidateQueries({ queryKey: key }); setRemoving(null) },
    onError: e => { void fail(e, "Could not remove that document"); setRemoving(null) },
  })
  return (
    <div style={{ marginTop: 14 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "0 0 6px" }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>Documents</div>
        {canWrite && <Btn small onClick={() => setAdding(true)}>Add a document</Btn>}
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <div style={{ ...muted, padding: 14 }}>Could not load the documents.</div> : isLoading ? <div style={{ ...muted, padding: 14 }}>Loading…</div> : rows.length === 0 ? <div style={{ ...muted, padding: 14 }}>No documents yet.</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>File</th><th style={th}>Kind</th><th style={th}>Added</th><th /></tr></thead>
            <tbody>{rows.map(e => (
              <tr key={e.id}>
                <td style={td}><b style={{ wordBreak: "break-word" }}>{e.fileName}</b><div style={muted}>{fileSize(e.fileSizeBytes)}</div></td>
                <td style={td}>{labelOf(EVIDENCE_TYPES, e.evidenceType)}</td>
                <td style={td}>{day(e.createdAt)}{e.uploadedByName ? <div style={muted}>{e.uploadedByName}</div> : null}</td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                  <Btn small disabled={download.isPending} onClick={() => download.mutate(e)}>Download</Btn>
                  {canWrite && <>{" "}<Btn small onClick={() => setRemoving(e)}>Remove</Btn></>}
                </td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {adding && <AddDialog url={url} onClose={() => setAdding(false)} onDone={() => qc.invalidateQueries({ queryKey: key })} />}
      {removing && (
        <Dialog title="Remove this document?" onClose={() => setRemoving(null)}
          footer={<><Btn onClick={() => setRemoving(null)}>Keep it</Btn><Btn kind="danger" disabled={remove.isPending} onClick={() => remove.mutate(removing)}>{remove.isPending ? "Removing…" : "Remove"}</Btn></>}>
          <div style={{ fontSize: 13, lineHeight: 1.5 }}>{removing.fileName} will no longer be listed here.</div>
        </Dialog>)}
    </div>
  )
}

function AddDialog({ url, onClose, onDone }: { url: string; onClose: () => void; onDone: () => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null), [kind, setKind] = useState("DOCUMENT"), [err, setErr] = useState("")
  const problem = evidenceProblem(file, kind)
  const upload = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("file", file!, file!.name); fd.append("evidenceType", kind)
      return apiClient.post(url, fd)
    },
    onSuccess: () => { toast.success("Document added"); onDone(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not add the document")),
  })
  return (
    <Dialog title="Add a document" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || upload.isPending} onClick={() => { setErr(""); upload.mutate() }}>{upload.isPending ? "Uploading…" : "Add"}</Btn></>}>
      <div style={{ marginBottom: 12 }}>
        <input ref={input} type="file" aria-label="File" onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 13 }} />
        {file && <div style={muted}>{file.name} · {fileSize(file.size)}</div>}
      </div>
      <Pick label="What is it?" value={kind} onChange={setKind} options={EVIDENCE_TYPES} />
      {problem && file && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
