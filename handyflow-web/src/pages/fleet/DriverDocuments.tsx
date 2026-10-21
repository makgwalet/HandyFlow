// src/pages/fleet/DriverDocuments.tsx
// The Documents tab and the Medical & training tab of a driver's page: what is on file, when it runs out, and adding more.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { TONE_COLOUR } from "./fleetTones"
import { expiryText } from "./vehicleDetail.logic"
import { DRIVER_DOC_LABEL, GROUP_TYPES, docName, docTone, docsFor, uploadProblem, type DriverDoc, type DriverDocGroup } from "./driverDocuments.logic"

const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 12.5 }
const rowsOf = (r: { data: unknown }): DriverDoc[] => { const o = r.data as { data?: unknown } | null; const p = (o && typeof o === "object" && !Array.isArray(o) && "data" in o ? o.data : r.data) as DriverDoc[] | { content?: DriverDoc[] } | null; return (Array.isArray(p) ? p : p?.content) ?? [] }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "We couldn't save that. Try again."

async function save(base: string, doc: DriverDoc) {
  const r = await apiClient.get(`${base}/documents/${doc.id}/download`, { responseType: "blob" })
  const url = URL.createObjectURL(r.data as Blob), a = document.createElement("a")
  a.href = url; a.download = doc.fileName; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export default function DriverDocuments({ driverId, group }: { driverId: string; group: DriverDocGroup }) {
  const base = `/api/v1/fleet/drivers/${driverId}`, qc = useQueryClient(), canManage = usePermission("FLEET_MANAGE")
  const blank = { type: GROUP_TYPES[group][0] as string, title: "", number: "", issueDate: "", expiryDate: "" }
  const [form, setForm] = useState(blank), [file, setFile] = useState<File | null>(null), [error, setError] = useState<string | null>(null)
  const { data, isLoading, isError, refetch } = useQuery<DriverDoc[]>({ queryKey: ["fleet", "driver-documents", driverId], queryFn: async () => rowsOf(await apiClient.get(`${base}/documents`)) })
  const upload = useMutation({
    mutationFn: () => {
      const f = new FormData(); f.append("file", file as File); f.append("type", form.type)
      if (form.title.trim()) f.append("title", form.title.trim())
      if (form.number.trim()) f.append("documentNumber", form.number.trim())
      if (form.issueDate) f.append("issueDate", form.issueDate)
      if (form.expiryDate) f.append("expiryDate", form.expiryDate)
      return apiClient.post(`${base}/documents`, f)
    },
    onSuccess: () => { setError(null); setForm(blank); setFile(null); qc.invalidateQueries({ queryKey: ["fleet", "driver-documents", driverId] }) },
    onError: e => setError(errorOf(e)),
  })
  const problem = uploadProblem(form, file), rows = docsFor(data ?? [], group)
  const set = (k: keyof typeof form) => (v: string) => setForm(f => ({ ...f, [k]: v }))
  return (
    <div>
      {isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
      {isError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>We couldn't load the documents. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button></p>}
      {data && rows.length === 0 && <p role="status" style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{group === "medical" ? "No medical certificate or training on file yet." : "No documents on file yet."}</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>{rows.map(d => (
        <li key={d.id} aria-label={docName(d)} style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
          <div>
            <strong>{docName(d)}</strong>{d.title && <span style={{ color: "var(--hf-text-muted)" }}> · {DRIVER_DOC_LABEL[d.type as keyof typeof DRIVER_DOC_LABEL] ?? d.type}</span>}{d.documentNumber ? ` · ${d.documentNumber}` : ""}
            <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{d.fileName} · version {d.version}</div>
          </div>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <span style={{ color: TONE_COLOUR[docTone(d)], fontWeight: 700 }}>{d.expiryDate ? expiryText(d.daysToExpiry) : "No expiry"}</span>
            <button type="button" aria-label={`Download ${docName(d)}`} style={{ ...field, cursor: "pointer" }} onClick={() => save(base, d).catch(e => setError(errorOf(e)))}>Download</button>
          </div>
        </li>))}</ul>
      {canManage && (
        <div style={{ marginTop: 12, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <select aria-label="Kind of document" style={field} value={form.type} onChange={e => set("type")(e.target.value)}>{GROUP_TYPES[group].map(t => <option key={t} value={t}>{DRIVER_DOC_LABEL[t]}</option>)}</select>
          {form.type === "TRAINING_CERTIFICATE" && <input aria-label="Course" placeholder="Course name" style={{ ...field, width: 160 }} value={form.title} onChange={e => set("title")(e.target.value)} />}
          <input aria-label="Document number" placeholder="Number" style={{ ...field, width: 110 }} value={form.number} onChange={e => set("number")(e.target.value)} />
          <label style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>Issued <input aria-label="Issue date" type="date" style={field} value={form.issueDate} onChange={e => set("issueDate")(e.target.value)} /></label>
          <label style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>Expires <input aria-label="Expiry date" type="date" style={field} value={form.expiryDate} onChange={e => set("expiryDate")(e.target.value)} /></label>
          <input aria-label="Document file" type="file" accept="application/pdf,image/jpeg,image/png,image/webp" onChange={e => setFile(e.target.files?.[0] ?? null)} />
          <button type="button" disabled={!!problem || upload.isPending} style={{ ...field, cursor: problem ? "default" : "pointer", fontWeight: 700 }} onClick={() => upload.mutate()}>{upload.isPending ? "Saving…" : "Add"}</button>
          {problem && file !== null && <span style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{problem}</span>}
        </div>)}
      {error && <p role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>{error}</p>}
    </div>)
}
