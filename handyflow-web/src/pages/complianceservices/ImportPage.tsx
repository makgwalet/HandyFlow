// src/pages/complianceservices/ImportPage.tsx
//
// Bring clients or registrations in from a spreadsheet saved as CSV. The file is checked first and nothing is created until an administrator imports it.
// Rows that match something already there are left alone, never overwritten. Problems are listed first so they are not missed.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { batchStatus, columnLabel, discardProblem, fileProblem, kindLabel, KIND_HELP, KINDS, rowStatus, summary, TEMPLATE, type ImportBatch, type ImportDetail } from "./importRules"

const B = "/api/v1/compliance-services/imports"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12 }

export default function ImportPage() {
  const qc = useQueryClient()
  const isAdmin = usePermissions(["COMPLIANCE_SERVICES_ADMIN"])
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_IMPORT_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [kind, setKind] = useState("CLIENTS")
  const [file, setFile] = useState<File | null>(null)
  const [detail, setDetail] = useState<ImportDetail | null>(null)
  const [discarding, setDiscarding] = useState(false)
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const history = useQuery<ImportBatch[]>({ queryKey: ["cs-imports"], queryFn: async () => unwrap<ImportBatch[]>(await apiClient.get(B)) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["cs-imports"] })

  const send = async () => {
    const p = fileProblem(file); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      const fd = new FormData(); fd.append("file", file as File)
      setDetail(unwrap<ImportDetail>(await apiClient.post(`${B}/preview?kind=${kind}`, fd, { headers: { "Content-Type": "multipart/form-data" } })))
      refresh()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const act = async (path: string, body?: unknown) => {
    setBusy(true); setError("")
    try {
      setDetail(unwrap<ImportDetail>(await apiClient.post(`${B}/${detail?.batch.id}/${path}`, body ?? {}))); setDiscarding(false); setNote("")
      qc.invalidateQueries({ queryKey: ["cs-clients"] }); refresh()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const open = async (id: string) => { setError(""); try { setDetail(unwrap<ImportDetail>(await apiClient.get(`${B}/${id}`))) } catch (e) { setError(errText(e)) } }
  const b = detail?.batch

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>Import</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Bring clients or registrations in from a spreadsheet saved as CSV. The file is checked first and nothing is created until an administrator imports it. Anything already there is left alone.</div></div>

      {canManage && !b && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <label style={{ fontSize: 12, fontWeight: 600 }}>What is in the file
            <select aria-label="What is in the file" style={input} value={kind} onChange={e => setKind(e.target.value)}>{Object.entries(KINDS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></label>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>The first row names the columns: <code>{TEMPLATE[kind]}</code>. {KIND_HELP[kind]}</div>
          <input aria-label="CSV file" type="file" accept=".csv,text/csv" onChange={e => setFile(e.target.files?.[0] ?? null)} />
          <div><button style={solid} disabled={busy} onClick={send}>Check the file</button></div>
        </div>
      )}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}

      {b && detail && (
        <div style={{ display: "grid", gap: 10 }}>
          <div><button style={ghost} onClick={() => { setDetail(null); setFile(null); setError("") }}>Back</button></div>
          <div style={card}>
            <strong>{kindLabel(b.kind)} · {b.fileName}</strong> · {batchStatus(b.status)}
            <div style={{ fontSize: 13 }}>{summary(b)}</div>
            {b.discardNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Set aside: {b.discardNote}</div>}
          </div>
          {b.status === "PREVIEWED" && (
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
              {isAdmin ? <button style={solid} disabled={busy || b.okRows === 0} onClick={() => act("commit")}>Import {b.okRows} {b.okRows === 1 ? "row" : "rows"}</button>
                : <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>An administrator imports the rows that are ready.</span>}
              {canManage && !discarding && <button style={ghost} onClick={() => setDiscarding(true)}>Set this file aside</button>}
            </div>
          )}
          {discarding && (
            <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
              <textarea aria-label="Reason" style={{ ...input, minHeight: 50 }} placeholder="Reason (at least 10 characters)" value={note} onChange={e => setNote(e.target.value)} />
              <div><button style={solid} disabled={busy} onClick={() => { const p = discardProblem(note); if (p) { setError(p); return } act("discard", { note: note.trim() }) }}>Set aside</button></div>
            </div>
          )}
          <div style={{ overflowX: "auto" }}>
            <table style={{ borderCollapse: "collapse", fontSize: 12, width: "100%" }}>
              <thead><tr><th style={{ textAlign: "left", padding: 4 }}>Row</th><th style={{ textAlign: "left", padding: 4 }}>Result</th>{detail.columns.map(c => <th key={c} style={{ textAlign: "left", padding: 4 }}>{columnLabel(c)}</th>)}</tr></thead>
              <tbody>
                {detail.rows.map(r => (
                  <tr key={r.rowNumber} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={{ padding: 4 }}>{r.rowNumber}</td>
                    <td style={{ padding: 4 }}>{rowStatus(r.status)}{r.message && r.status !== "IMPORTED" ? `: ${r.message}` : ""}</td>
                    {detail.columns.map((c, i) => <td key={c} style={{ padding: 4 }}>{r.fields[i]}</td>)}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {detail.rowsTruncated && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Only the first 500 rows are shown, problems first. All rows are still imported.</div>}
        </div>
      )}

      {!b && (
        <div style={{ display: "grid", gap: 6 }}>
          <h3 style={{ margin: 0, fontSize: 15 }}>Earlier files</h3>
          {(history.data ?? []).length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No file has been checked yet.</div>}
          {(history.data ?? []).map(h => (
            <button key={h.id} onClick={() => open(h.id)} style={{ ...card, textAlign: "left", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, cursor: "pointer" }}>
              <strong>{kindLabel(h.kind)} · {h.fileName}</strong> · {batchStatus(h.status)}<div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{summary(h)}</div>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
