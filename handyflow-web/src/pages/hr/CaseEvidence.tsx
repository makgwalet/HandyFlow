// The files attached to one employee relations case. Only people who may work the case see them; they are never shared outside it.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate } from "./er.logic"
import { errorText, input, plain, primary, unwrap } from "./er.shared"
import { ACCEPT_ATTR, KINDS, fileProblem, kindLabel, removeProblem, sizeText, type CaseFile } from "./erEvidence.logic"
import { Notice, Section } from "./ErParts"

const BASE = "/api/v1/hr/employee-relations"

export default function CaseEvidence({ caseId, canManage, open }: { caseId: string; canManage: boolean; open: boolean }) {
  const qc = useQueryClient()
  const picker = useRef<HTMLInputElement>(null)
  const [kind, setKind] = useState("")
  const [file, setFile] = useState<File | null>(null)
  const [removing, setRemoving] = useState<{ id: string; reason: string } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const key = ["er-evidence", caseId]
  const list = useQuery<CaseFile[]>({ queryKey: key, retry: false, queryFn: async () => unwrap<CaseFile[]>(await apiClient.get(`${BASE}/cases/${caseId}/evidence`)) ?? [] })
  const files = list.data ?? []

  const add = useMutation({
    mutationFn: async () => {
      const form = new FormData()
      form.append("file", file as File)
      form.append("kind", kind)
      return apiClient.post(`${BASE}/cases/${caseId}/evidence`, form, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["er-case", caseId] })
      setFile(null); setKind(""); setError(null); setSaved("The file was added to the case.")
      if (picker.current) picker.current.value = ""
    },
    onError: e => { setSaved(null); setError(errorText(e, "The file could not be added.")) },
  })
  const remove = useMutation({
    mutationFn: async (v: { id: string; reason: string }) => apiClient.post(`${BASE}/cases/${caseId}/evidence/${v.id}/remove`, { reason: v.reason.trim() }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["er-case", caseId] })
      setRemoving(null); setError(null); setSaved("The file was taken off the case.")
    },
    onError: e => { setSaved(null); setError(errorText(e, "The file could not be removed.")) },
  })

  const download = async (f: CaseFile) => {
    try {
      const res = await apiClient.get(`${BASE}/cases/${caseId}/evidence/${f.id}/download`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([res.data], { type: f.contentType }))
      const a = document.createElement("a")
      a.href = url; a.download = f.fileName
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
      setError(null)
    } catch {
      setSaved(null); setError("The file could not be downloaded. Please try again.")
    }
  }

  if (list.isError) return null // no permission to see the files: leave the section out
  const problem = fileProblem(file ? { name: file.name, size: file.size } : null, kind, files.length, open)
  const removeErr = removing ? removeProblem(removing.reason) : ""

  return (
    <Section title="Evidence files">
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>
        Statements, emails, photographs and recordings for this case. Only people who work on employee relations cases can open them; they are never shown to the employee or shared with an auditor.
        Taking a file off the case hides it but keeps it, and the case timeline records who did it and why.
      </p>
      {error && <Notice kind="error">{error}</Notice>}
      {saved && <Notice kind="ok">{saved}</Notice>}
      {files.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 10 }}>No files are attached to this case.</div> : (
        <ul style={{ listStyle: "none", margin: "0 0 12px", padding: 0, fontSize: 13 }}>
          {files.map(f => (
            <li key={f.id} style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 6, flexWrap: "wrap" }}>
              <button onClick={() => download(f)} aria-label={`Download ${f.fileName}`} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--hf-primary)", fontWeight: 700 }}>{f.fileName}</button>
              <span>{kindLabel(f.evidenceType)} · {sizeText(f.fileSizeBytes)}</span>
              <span style={{ color: "var(--hf-text-faint)" }}>added {fmtDate(f.createdAt.slice(0, 10))}{f.uploadedByName ? ` by ${f.uploadedByName}` : ""}</span>
              {canManage && open && !removing && <button onClick={() => { setRemoving({ id: f.id, reason: "" }); setError(null); setSaved(null) }} aria-label={`Remove ${f.fileName}`} style={plain}>Remove</button>}
            </li>
          ))}
        </ul>
      )}

      {removing && (
        <form onSubmit={e => { e.preventDefault(); if (!removeErr) remove.mutate(removing) }} noValidate style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, marginBottom: 12 }}>
          <label htmlFor="ev-reason" style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Why is the file being taken off? *</label>
          <input id="ev-reason" style={input} value={removing.reason} maxLength={300} onChange={e => setRemoving({ id: removing.id, reason: e.target.value })} />
          {removing.reason && removeErr && <div role="alert" style={{ marginTop: 6, fontSize: 13, color: "var(--hf-danger-text)" }}>{removeErr}</div>}
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 10 }}>
            <button type="button" onClick={() => setRemoving(null)} style={plain}>Cancel</button>
            <button type="submit" disabled={!!removeErr || remove.isPending} style={primary}>{remove.isPending ? "Saving…" : "Take off the case"}</button>
          </div>
        </form>
      )}

      {canManage && open && (
        <form onSubmit={e => { e.preventDefault(); if (!problem) add.mutate() }} noValidate>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 10, alignItems: "end" }}>
            <div>
              <label htmlFor="ev-kind" style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Kind *</label>
              <select id="ev-kind" style={input} value={kind} onChange={e => setKind(e.target.value)}>
                <option value="">Choose…</option>
                {KINDS.map(k => <option key={k.value} value={k.value}>{k.label}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor="ev-file" style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4 }}>File *</label>
              <input id="ev-file" ref={picker} type="file" accept={ACCEPT_ATTR} onChange={e => setFile(e.target.files?.[0] ?? null)} style={{ fontSize: 13 }} />
            </div>
          </div>
          {(file || kind) && problem && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{problem}</div>}
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 10 }}>
            <button type="submit" disabled={!!problem || add.isPending} style={primary}>{add.isPending ? "Uploading…" : "Add the file"}</button>
          </div>
        </form>
      )}
    </Section>
  )
}
