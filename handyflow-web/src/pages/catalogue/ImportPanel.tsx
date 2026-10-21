// src/pages/catalogue/ImportPanel.tsx — import items from a CSV file (check first, then apply), export the catalogue, and see recent imports.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner, Field } from "./CatalogueKit"
import { inp, linkBtn, primaryBtn, sel, td, th } from "./catalogue.styles"
import {
  IMPORT_MODES, MAX_IMPORT_CHARS, ROW_ACTION_LABEL, errorMessage, importCount, importSummaryText, resultText, saveText,
  type ImportJob, type ImportMode, type ImportPreview, type ImportResult,
} from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border-subtle)", borderRadius: 12, padding: 16, marginBottom: 16 }

export default function ImportPanel() {
  const qc = useQueryClient()
  const canImport = usePermissions(["SETTINGS_MANAGE", "CATALOGUE_IMPORT"])
  const canExport = usePermissions(["SETTINGS_MANAGE", "CATALOGUE_EXPORT"])
  const [file, setFile] = useState<{ name: string; text: string } | null>(null)
  const [mode, setMode] = useState<ImportMode>("CREATE_OR_UPDATE")
  const [skipErrors, setSkipErrors] = useState(false)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [error, setError] = useState("")

  const jobs = useQuery<ImportJob[]>({ queryKey: ["catalogue", "import-jobs"], enabled: canImport, queryFn: async () => inner<ImportJob[]>(await apiClient.get("/api/v1/catalogue/import/jobs")) ?? [] })
  const body = () => ({ fileName: file?.name, csv: file?.text, mode, skipErrors })
  const check = useMutation({
    mutationFn: async () => inner<ImportPreview>(await apiClient.post("/api/v1/catalogue/import/preview", body())),
    onSuccess: p => { setPreview(p); setResult(null); setError("") }, onError: e => setError(errorMessage(e, "Couldn't check the file.")),
  })
  const apply = useMutation({
    mutationFn: async () => inner<ImportResult>(await apiClient.post("/api/v1/catalogue/import", body())),
    onSuccess: r => {
      setResult(r); setPreview(null); setFile(null); setError("")
      for (const k of ["import-jobs", "items", "dashboard", "overview"]) qc.invalidateQueries({ queryKey: ["catalogue", k] })
    },
    onError: e => setError(errorMessage(e, "The import could not be applied.")),
  })
  const download = async (url: string, name: string) => {
    try { saveText(name, String((await apiClient.get(url, { responseType: "text" })).data)) }
    catch (e) { setError(errorMessage(e, "Couldn't download the file.")) }
  }
  const pick = async (f: File | undefined) => {
    setPreview(null); setResult(null); setError("")
    if (!f) { setFile(null); return }
    if (f.size > MAX_IMPORT_CHARS) { setFile(null); setError("That file is too large. Split it into files of up to 2000 rows."); return }
    setFile({ name: f.name, text: await f.text() })
  }
  const count = importCount(preview, skipErrors)

  return (
    <div style={{ display: "grid", gap: 4 }}>
      {error && <ErrorBanner message={error} />}
      <section style={card} aria-label="Files">
        <h4 style={{ margin: "0 0 8px", fontSize: 14, color: "var(--hf-text)" }}>Template and export</h4>
        <p style={{ ...muted, margin: "0 0 12px" }}>Start from the template, or export what you have, change it in a spreadsheet and import it again. Items are matched by SKU.</p>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button type="button" style={linkBtn} onClick={() => download("/api/v1/catalogue/import/template", "catalogue-import-template.csv")}>Download template</button>
          {canExport && <button type="button" style={linkBtn} onClick={() => download("/api/v1/catalogue/export", "catalogue.csv")}>Export catalogue</button>}
        </div>
      </section>

      {canImport && (
        <section style={card} aria-label="Import">
          <h4 style={{ margin: "0 0 12px", fontSize: 14, color: "var(--hf-text)" }}>Import items</h4>
          <Field label="CSV file" id="imp-file"><input id="imp-file" type="file" accept=".csv,.txt,text/csv" style={inp} onChange={e => pick(e.target.files?.[0])} /></Field>
          <Field label="What may the import do?" id="imp-mode">
            <select id="imp-mode" style={sel} value={mode} onChange={e => { setMode(e.target.value as ImportMode); setPreview(null) }}>
              {IMPORT_MODES.map(m => <option key={m.id} value={m.id}>{m.label}</option>)}
            </select>
            <div style={{ ...muted, marginTop: 4 }}>{IMPORT_MODES.find(m => m.id === mode)?.hint}</div>
          </Field>
          <button type="button" style={primaryBtn} disabled={!file || check.isPending} onClick={() => check.mutate()}>{check.isPending ? "Checking…" : "Check file"}</button>
          <span style={{ ...muted, marginLeft: 10 }}>Nothing is saved until you import.</span>

          {preview && (
            <div style={{ marginTop: 16 }}>
              <p role="status" style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{importSummaryText(preview)}</p>
              {preview.unknownColumns.length > 0 && <p style={muted}>Ignored columns: {preview.unknownColumns.join(", ")}.</p>}
              {preview.rows.length > 0 && (
                <div style={{ overflowX: "auto", border: "1px solid var(--hf-border-subtle)", borderRadius: 10, maxHeight: 360, overflowY: "auto", marginBottom: 12 }}>
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead><tr><th style={th}>Row</th><th style={th}>Item</th><th style={th}>Result</th><th style={th}>Details</th></tr></thead>
                    <tbody>
                      {preview.rows.map(r => (
                        <tr key={r.line}>
                          <td style={td}>{r.line}</td><td style={td}>{r.key}</td>
                          <td style={{ ...td, fontWeight: 600, color: r.action === "ERROR" ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{ROW_ACTION_LABEL[r.action]}</td>
                          <td style={{ ...td, ...muted }}>{[...r.errors, ...r.changes, ...r.notes].join("; ") || "—"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {preview.truncated && <p style={muted}>Only the first rows are listed here; all rows are checked.</p>}
              {preview.errors > 0 && preview.fileProblems.length === 0 && (
                <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, marginBottom: 10 }}>
                  <input type="checkbox" checked={skipErrors} onChange={e => setSkipErrors(e.target.checked)} /> Leave out the {preview.errors} row{preview.errors === 1 ? "" : "s"} with problems and import the rest
                </label>
              )}
              <button type="button" style={primaryBtn} disabled={count === null || apply.isPending} onClick={() => apply.mutate()}>
                {apply.isPending ? "Importing…" : count === null ? "Nothing to import yet" : `Import ${count} row${count === 1 ? "" : "s"}`}
              </button>
            </div>
          )}

          {result && (
            <div role="status" style={{ marginTop: 16 }}>
              <p style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)", margin: "0 0 6px" }}>Import finished: {resultText(result)}</p>
              {result.problems.length > 0 && <ul style={{ margin: 0, paddingLeft: 18, ...muted }}>{result.problems.slice(0, 20).map(p => <li key={p}>{p}</li>)}</ul>}
            </div>
          )}
        </section>
      )}

      {canImport && (
        <section style={card} aria-label="Recent imports">
          <h4 style={{ margin: "0 0 8px", fontSize: 14, color: "var(--hf-text)" }}>Recent imports</h4>
          {jobs.data && jobs.data.length === 0 && <p style={muted}>No imports yet.</p>}
          {jobs.data && jobs.data.length > 0 && (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>When</th><th style={th}>File</th><th style={th}>Rows</th><th style={th}>Added</th><th style={th}>Updated</th><th style={th}>Left out</th><th style={th}>Failed</th></tr></thead>
                <tbody>{jobs.data.map(j => (
                  <tr key={j.id}><td style={td}>{new Date(j.createdAt).toLocaleString("en-ZA")}</td><td style={td}>{j.fileName ?? "—"}</td><td style={td}>{j.totalRows}</td><td style={td}>{j.created}</td><td style={td}>{j.updated}</td><td style={td}>{j.skipped}</td><td style={td}>{j.failed}</td></tr>
                ))}</tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  )
}
