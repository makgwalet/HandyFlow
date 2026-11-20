// src/pages/legalcompliance/CustomReportsPanel.tsx
//
// Custom reports: saved views over the standard reports. Endpoints (/api/v1/legalcompliance/custom-reports): GET / · POST / · PUT /{id} · DELETE /{id} · GET /{id}/run · GET /{id}/export?format=
// A custom report is not a new data source. It picks columns, filters and a sort from one standard report, so it shows only what that standard report shows to you.
// Sharing with the company needs a manager; downloading needs LEGALCOMPLIANCE_REPORT_EXPORT.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { one, input, primaryBtn, ghostBtn, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { OPS, MAX_FILTERS, editorProblem, moveItem, type Op, type FilterSpec } from "./customReportLogic"
import { Plus, Play, Pencil, Trash2, Download, ArrowUp, ArrowDown, X } from "lucide-react"

const base = "/api/v1/legalcompliance/custom-reports"
const standard = "/api/v1/legalcompliance/reports"

interface View {
  id: string; name: string; description: string | null; baseKey: string; baseTitle: string | null; columns: string[]; filters: FilterSpec[]
  sortColumn: string | null; sortDesc: boolean; shared: boolean; ownerName: string | null; mine: boolean; canEdit: boolean; updatedAt: string
}
interface ReportOption { key: string; title: string }
interface Result { title: string; asOf: string | null; columns: string[]; rows: string[][]; rowCount: number; truncated: boolean }
type Format = "csv" | "xlsx" | "pdf"

export default function CustomReportsPanel({ reports, canExport, canShare }: { reports: ReportOption[]; canExport: boolean; canShare: boolean }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<View | "new" | null>(null)
  const [deleting, setDeleting] = useState<View | null>(null)
  const [result, setResult] = useState<{ id: string; data: Result } | null>(null)
  const [err, setErr] = useState("")

  const { data: views = [], isLoading } = useQuery<View[]>({ queryKey: ["lc-custom-reports"], queryFn: () => apiClient.get(base).then(r => one(r) ?? []) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-custom-reports"] }); setErr("") }
  const fail = (e: unknown) => setErr(errMsg(e, "That did not work."))

  const run = useMutation({
    mutationFn: (v: View) => apiClient.get(`${base}/${v.id}/run`).then(r => ({ id: v.id, data: one(r) as Result })),
    onSuccess: r => { setErr(""); setResult(r) }, onError: fail,
  })
  const remove = useMutation({ mutationFn: (v: View) => apiClient.delete(`${base}/${v.id}`), onSuccess: () => { refresh(); setDeleting(null); setResult(null) }, onError: (e: unknown) => { fail(e); setDeleting(null) } })
  const download = useMutation({
    mutationFn: async ({ id, format }: { id: string; format: Format }) => {
      const r = await apiClient.get(`${base}/${id}/export`, { params: { format }, responseType: "blob" })
      const name = /filename="?([^";]+)"?/.exec(r.headers?.["content-disposition"] ?? "")?.[1] ?? `custom-report.${format}`
      const url = URL.createObjectURL(r.data as Blob)
      const a = document.createElement("a")
      a.href = url; a.download = name
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    },
    onSuccess: () => setErr(""), onError: fail,
  })

  const btn = { ...ghostBtn, padding: "6px 10px", display: "flex", alignItems: "center", gap: 5 } as const

  return (
    <div style={{ marginTop: 28, borderTop: "1px solid #E2E8F0", paddingTop: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 6, flexWrap: "wrap" }}>
        <h3 style={{ fontSize: 15, fontWeight: 800, color: "#0F172A", margin: 0 }}>My custom reports</h3>
        <button style={{ ...primaryBtn, padding: "7px 12px", fontSize: 13, display: "flex", alignItems: "center", gap: 6 }} onClick={() => setEditing("new")}><Plus size={13} />New custom report</button>
      </div>
      <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 12px" }}>
        Pick the columns, filters and sort you want from a standard report and save it. It shows only what that standard report shows you; it is not a new data source.
      </p>
      {err && <Err m={err} />}
      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && views.length === 0 && <p style={{ color: "#64748B" }}>No custom reports yet.</p>}

      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {views.map(v => (
          <div key={v.id} style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: 12 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{v.name}</div>
                <div style={{ fontSize: 12, color: "#64748B" }}>
                  Based on {v.baseTitle ?? "a report you cannot open"} · {v.columns.length} column{v.columns.length === 1 ? "" : "s"}
                  {v.filters.length > 0 ? ` · ${v.filters.length} filter${v.filters.length === 1 ? "" : "s"}` : ""}{v.sortColumn ? ` · sorted by ${v.sortColumn}` : ""}
                </div>
                {v.description && <div style={{ fontSize: 12, color: "#475569", marginTop: 2 }}>{v.description}</div>}
              </div>
              <div style={{ display: "flex", gap: 6, alignItems: "flex-start" }}>
                {v.shared && <Badge text="Shared" color="#3730A3" bg="#E0E7FF" />}
                {!v.mine && <Badge text={`By ${v.ownerName ?? "a colleague"}`} color="#475569" bg="#E2E8F0" />}
              </div>
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 10, flexWrap: "wrap" }}>
              <button style={btn} disabled={run.isPending || v.baseTitle === null} onClick={() => run.mutate(v)}><Play size={13} />Run</button>
              {v.canEdit && <button style={btn} onClick={() => setEditing(v)}><Pencil size={13} />Edit</button>}
              {v.canEdit && <button style={btn} onClick={() => setDeleting(v)}><Trash2 size={13} />Delete</button>}
            </div>
          </div>
        ))}
      </div>

      {result && (
        <div style={{ marginTop: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            <strong style={{ fontSize: 14 }}>{result.data.title} <span style={{ fontWeight: 400, color: "#64748B", fontSize: 12 }}>· {result.data.rowCount} row{result.data.rowCount === 1 ? "" : "s"}</span></strong>
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
              {canExport && (["csv", "xlsx", "pdf"] as Format[]).map(f => (
                <button key={f} style={btn} disabled={download.isPending} onClick={() => download.mutate({ id: result.id, format: f })}><Download size={13} />{f === "xlsx" ? "Excel" : f.toUpperCase()}</button>
              ))}
              <button style={btn} onClick={() => setResult(null)}><X size={13} />Close</button>
            </div>
          </div>
          {result.data.truncated && (
            <p style={{ fontSize: 12, color: "#92400E", background: "#FEF3C7", padding: "8px 12px", borderRadius: 8 }}>
              The standard report was cut at its row limit, so this view was worked out from the first rows only. Narrow the register before relying on it.
            </p>
          )}
          {result.data.rows.length === 0 ? <p style={{ color: "#64748B" }}>No rows match.</p> : (
            <div style={{ overflowX: "auto", border: "1px solid #E2E8F0", borderRadius: 8 }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                <thead><tr>{result.data.columns.map(c => <th key={c} style={{ textAlign: "left", padding: "8px 10px", background: "#F8FAFC", borderBottom: "1px solid #E2E8F0", whiteSpace: "nowrap", color: "#475569" }}>{c}</th>)}</tr></thead>
                <tbody>{result.data.rows.map((row, i) => <tr key={i}>{row.map((cell, j) => <td key={j} style={{ padding: "7px 10px", borderBottom: "1px solid #F1F5F9", verticalAlign: "top" }}>{cell}</td>)}</tr>)}</tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {editing && <EditorModal existing={editing === "new" ? null : editing} reports={reports} canShare={canShare} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); setResult(null); refresh() }} />}
      {deleting && (
        <Modal title="Delete custom report" onClose={() => setDeleting(null)}>
          <p style={{ fontSize: 14, color: "#334155" }}>Delete “{deleting.name}”?{deleting.shared ? " It is shared, so colleagues lose it too." : ""} The standard report is not affected. This is recorded in the audit trail.</p>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <button style={ghostBtn} onClick={() => setDeleting(null)}>Keep it</button>
            <button style={primaryBtn} disabled={remove.isPending} onClick={() => remove.mutate(deleting)}>Delete</button>
          </div>
        </Modal>
      )}
    </div>
  )
}

function EditorModal({ existing, reports, canShare, onClose, onSaved }: { existing: View | null; reports: ReportOption[]; canShare: boolean; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(existing?.name ?? "")
  const [description, setDescription] = useState(existing?.description ?? "")
  const [baseKey, setBaseKey] = useState(existing?.baseKey ?? "")
  const [cols, setCols] = useState<string[]>(existing?.columns ?? [])
  const [filters, setFilters] = useState<FilterSpec[]>(existing?.filters ?? [])
  const [sortColumn, setSortColumn] = useState(existing?.sortColumn ?? "")
  const [sortDesc, setSortDesc] = useState(existing?.sortDesc ?? false)
  const [shared, setShared] = useState(existing?.shared ?? false)
  const [err, setErr] = useState("")

  // The standard report's own columns (running it also shows how many rows there are, but only the column names are used here).
  const { data: baseColumns = [], isFetching, isError } = useQuery<string[]>({
    queryKey: ["lc-custom-reports", "columns", baseKey], enabled: baseKey !== "",
    queryFn: () => apiClient.get(`${standard}/${baseKey}`).then(r => (one(r) as { columns: string[] }).columns),
  })

  const changeBase = (k: string) => { setBaseKey(k); setCols([]); setFilters([]); setSortColumn("") }
  const toggleCol = (c: string) => setCols(p => p.includes(c) ? p.filter(x => x !== c) : [...p, c])
  const setFilter = (i: number, patch: Partial<FilterSpec>) => setFilters(p => p.map((f, j) => j === i ? { ...f, ...patch } : f))
  const problem = editorProblem(name, baseKey, cols, filters)

  const save = useMutation({
    mutationFn: async () => {
      const body = { name: name.trim(), description: description.trim() || null, baseKey, columns: cols, sortColumn: sortColumn || null, sortDesc, shared,
        filters: filters.map(f => ({ ...f, value: OPS.find(o => o.value === f.op)?.needsValue ? f.value : null })) }
      return existing ? one(await apiClient.put(`${base}/${existing.id}`, body)) : one(await apiClient.post(base, body))
    },
    onSuccess: onSaved, onError: (e: unknown) => setErr(errMsg(e, "Could not save the custom report.")),
  })

  return (
    <Modal title={existing ? "Edit custom report" : "New custom report"} onClose={onClose}>
      {err && <Err m={err} />}
      <Field l="Name"><input style={input} value={name} maxLength={120} onChange={e => setName(e.target.value)} placeholder="e.g. High risks by owner" /></Field>
      <Field l="Description (optional)"><input style={input} value={description} maxLength={500} onChange={e => setDescription(e.target.value)} /></Field>
      <Field l="Built on">
        <select style={input} value={baseKey} onChange={e => changeBase(e.target.value)}>
          <option value="">Choose a standard report…</option>
          {reports.map(r => <option key={r.key} value={r.key}>{r.title}</option>)}
        </select>
      </Field>

      {baseKey && isFetching && <p style={{ fontSize: 12, color: "#64748B" }}>Loading the report’s columns…</p>}
      {baseKey && isError && <p style={{ fontSize: 12, color: "#991B1B" }}>Could not load the report’s columns.</p>}

      {baseColumns.length > 0 && (
        <>
          <Field l={`Columns to show (${cols.length} chosen)`}>
            <div style={{ maxHeight: 150, overflowY: "auto", border: "1px solid #E2E8F0", borderRadius: 8, padding: 6 }}>
              {baseColumns.map(c => (
                <label key={c} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 13, padding: "3px 4px", cursor: "pointer" }}>
                  <input type="checkbox" checked={cols.includes(c)} onChange={() => toggleCol(c)} />{c}
                </label>
              ))}
            </div>
          </Field>
          {cols.length > 1 && (
            <Field l="Order">
              <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                {cols.map((c, i) => (
                  <div key={c} style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13 }}>
                    <span style={{ flex: 1 }}>{i + 1}. {c}</span>
                    <button type="button" style={{ ...ghostBtn, padding: "2px 6px" }} disabled={i === 0} aria-label={`Move ${c} up`} onClick={() => setCols(p => moveItem(p, i, -1))}><ArrowUp size={12} /></button>
                    <button type="button" style={{ ...ghostBtn, padding: "2px 6px" }} disabled={i === cols.length - 1} aria-label={`Move ${c} down`} onClick={() => setCols(p => moveItem(p, i, 1))}><ArrowDown size={12} /></button>
                  </div>
                ))}
              </div>
            </Field>
          )}

          <Field l={`Only rows where (${filters.length} of ${MAX_FILTERS})`}>
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {filters.map((f, i) => {
                const needs = OPS.find(o => o.value === f.op)?.needsValue ?? true
                return (
                  <div key={i} style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                    <select style={{ ...input, flex: "1 1 120px" }} value={f.column} aria-label="Filter column" onChange={e => setFilter(i, { column: e.target.value })}>
                      <option value="">Column…</option>{baseColumns.map(c => <option key={c} value={c}>{c}</option>)}
                    </select>
                    <select style={{ ...input, flex: "1 1 120px" }} value={f.op} aria-label="Filter test" onChange={e => setFilter(i, { op: e.target.value as Op })}>
                      {OPS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                    </select>
                    {needs && <input style={{ ...input, flex: "1 1 120px" }} value={f.value} maxLength={200} aria-label="Filter value" onChange={e => setFilter(i, { value: e.target.value })} />}
                    <button type="button" style={{ ...ghostBtn, padding: "4px 8px" }} aria-label="Remove filter" onClick={() => setFilters(p => p.filter((_, j) => j !== i))}><X size={13} /></button>
                  </div>
                )
              })}
              {filters.length < MAX_FILTERS && <button type="button" style={{ ...ghostBtn, alignSelf: "flex-start", padding: "4px 10px" }} onClick={() => setFilters(p => [...p, { column: "", op: "CONTAINS", value: "" }])}>Add a filter</button>}
            </div>
          </Field>

          <div style={{ display: "flex", gap: 10, alignItems: "flex-end" }}>
            <div style={{ flex: 1 }}>
              <Field l="Sort by">
                <select style={input} value={sortColumn} onChange={e => setSortColumn(e.target.value)}>
                  <option value="">Standard order</option>{baseColumns.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </Field>
            </div>
            <label style={{ fontSize: 13, display: "flex", alignItems: "center", gap: 6, paddingBottom: 14 }}><input type="checkbox" checked={sortDesc} disabled={!sortColumn} onChange={e => setSortDesc(e.target.checked)} />Highest first</label>
          </div>
        </>
      )}

      {(canShare || existing?.shared) && <label style={{ fontSize: 13, display: "flex", alignItems: "center", gap: 6, marginTop: 4 }}><input type="checkbox" checked={shared} onChange={e => setShared(e.target.checked)} />Share with everyone who can read reports (each person still sees it through their own access)</label>}
      {!canShare && !existing?.shared && <p style={{ fontSize: 12, color: "#64748B" }}>Only a manager can share a report with colleagues.</p>}

      {problem && <p style={{ fontSize: 12, color: "#64748B", margin: "10px 0 0" }}>{problem}</p>}
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 12 }}>
        <button style={ghostBtn} onClick={onClose}>Cancel</button>
        <button style={primaryBtn} disabled={problem !== null || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{existing ? "Save" : "Save custom report"}</button>
      </div>
    </Modal>
  )
}
