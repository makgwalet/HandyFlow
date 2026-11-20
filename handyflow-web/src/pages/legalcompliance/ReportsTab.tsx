// src/pages/legalcompliance/ReportsTab.tsx
//
// Standard reports. Endpoints (/api/v1/legalcompliance/reports): GET / (catalogue) · GET /{key}?asOf= (run on screen) · GET /{key}/export?asOf= ?format=csv|xlsx|pdf (download; needs LEGALCOMPLIANCE_REPORT_EXPORT) · POST /pack {keys,title,asOf} (several reports in one PDF)
// The screen, CSV, Excel and PDF show the same cells. Reports never contain personal data of the public (requesters, data subjects, declarants); the few that list staff or owner names say so.
// Only the executive scorecard can be run "as at" a past date, and only from stored snapshots.
import { useState } from "react"
import { useQuery, useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { one, input, primaryBtn, ghostBtn, fmtDateTime, errMsg } from "./lcHelpers"
import { Err, Badge } from "./LcUi"
import { Download, FileText, Play, X } from "lucide-react"
import ReportSchedulesPanel from "./ReportSchedulesPanel"

const base = "/api/v1/legalcompliance/reports"

interface Info { key: string; category: string; title: string; description: string; containsNames: boolean; supportsAsOf: boolean }
interface Result { key: string; title: string; category: string; generatedAt: string; generatedBy: string | null; asOf: string | null; columns: string[]; rows: string[][]; rowCount: number; truncated: boolean }

type Format = "csv" | "xlsx" | "pdf"
const FORMATS: { value: Format; label: string; title: string }[] = [
  { value: "csv", label: "CSV", title: "CSV" },
  { value: "xlsx", label: "Excel", title: "an Excel workbook" },
  { value: "pdf", label: "PDF", title: "PDF" },
]

export default function ReportsTab() {
  const hasExport = usePermission("LEGALCOMPLIANCE_REPORT_EXPORT")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canExport = hasExport || hasAdmin

  const [search, setSearch] = useState("")
  const [asOf, setAsOf] = useState("")
  const [result, setResult] = useState<Result | null>(null)
  const [err, setErr] = useState("")
  const [picked, setPicked] = useState<string[]>([])
  const [packTitle, setPackTitle] = useState("")

  const { data: catalogue = [], isLoading, error } = useQuery<Info[]>({
    queryKey: ["lc-reports"],
    queryFn: () => apiClient.get(base).then(r => one(r) ?? []),
  })

  const params = (info: Info) => (info.supportsAsOf && asOf ? { asOf } : {})

  const run = useMutation({
    mutationFn: (info: Info) => apiClient.get(`${base}/${info.key}`, { params: params(info) }).then(r => one(r) as Result),
    onSuccess: r => { setErr(""); setResult(r) },
    onError: (e: unknown) => setErr(errMsg(e, "Could not run the report.")),
  })

  const download = useMutation({
    mutationFn: async ({ info, format }: { info: Info; format: Format }) => {
      const r = await apiClient.get(`${base}/${info.key}/export`, { params: { ...params(info), format }, responseType: "blob" })
      const name = /filename="?([^";]+)"?/.exec(r.headers?.["content-disposition"] ?? "")?.[1] ?? `${info.key}.${format}`
      const url = URL.createObjectURL(r.data as Blob)
      const a = document.createElement("a")
      a.href = url; a.download = name
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    },
    onSuccess: () => setErr(""),
    onError: (e: unknown) => setErr(errMsg(e, "Could not export the report.")),
  })

  const pack = useMutation({
    mutationFn: async () => {
      const keys = catalogue.filter(c => picked.includes(c.key)).map(c => c.key)   // pack order follows the screen, not the order of ticking
      const r = await apiClient.post(`${base}/pack`, { keys, title: packTitle.trim() || null, asOf: asOf || null }, { responseType: "blob" })
      const name = /filename="?([^";]+)"?/.exec(r.headers?.["content-disposition"] ?? "")?.[1] ?? "board-pack.pdf"
      const url = URL.createObjectURL(r.data as Blob)
      const a = document.createElement("a")
      a.href = url; a.download = name
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    },
    onSuccess: () => setErr(""),
    onError: (e: unknown) => setErr(errMsg(e, "Could not build the board pack.")),
  })
  const togglePick = (key: string) => setPicked(p => p.includes(key) ? p.filter(k => k !== key) : p.length >= 20 ? p : [...p, key])

  const q = search.trim().toLowerCase()
  const shown = catalogue.filter(c => !q || `${c.title} ${c.description} ${c.category}`.toLowerCase().includes(q))
  const categories = Array.from(new Set(shown.map(c => c.category)))
  const current = result ? catalogue.find(c => c.key === result.key) : undefined

  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 16, flexWrap: "wrap", alignItems: "center" }}>
        <input style={{ ...input, width: 260 }} placeholder="Search reports" value={search} onChange={e => setSearch(e.target.value)} />
        <label style={{ fontSize: 12, color: "#475569", display: "flex", alignItems: "center", gap: 6 }}>
          Scorecard as at
          <input type="date" style={{ ...input, width: 160 }} value={asOf} max={new Date().toISOString().slice(0, 10)} onChange={e => setAsOf(e.target.value)} />
        </label>
        {asOf && <button style={ghostBtn} onClick={() => setAsOf("")}>Clear date</button>}
      </div>
      <p style={{ fontSize: 12, color: "#64748B", margin: "0 0 16px" }}>
        The date applies to the executive scorecard only and uses the stored daily snapshot; all other reports show the position today.
        Reports exclude personal data of the public, FICA matters and conflict declarations.
      </p>

      {canExport && picked.length > 0 && (
        <div style={{ position: "sticky", top: 0, zIndex: 5, display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", background: "#EEF2FF", border: "1px solid #C7D2FE", borderRadius: 10, padding: "10px 12px", marginBottom: 14 }}>
          <strong style={{ fontSize: 13, color: "#3730A3" }}>Board pack: {picked.length} report{picked.length === 1 ? "" : "s"} chosen</strong>
          <input style={{ ...input, width: 240 }} placeholder="Pack title (optional)" maxLength={100} value={packTitle} onChange={e => setPackTitle(e.target.value)} />
          <button style={{ ...primaryBtn, padding: "7px 12px", fontSize: 13, display: "flex", alignItems: "center", gap: 6 }} disabled={pack.isPending} onClick={() => pack.mutate()}><FileText size={14} />{pack.isPending ? "Building…" : "Build PDF pack"}</button>
          <button style={ghostBtn} onClick={() => setPicked([])}>Clear</button>
          <span style={{ fontSize: 12, color: "#475569" }}>Each report starts on its own page, with a cover and contents. Up to 1,000 rows per report.{asOf ? " The date above applies to the scorecard." : ""}</span>
        </div>
      )}

      {err && <Err m={err} />}
      {error && <Err m="Could not load the report list." />}
      {isLoading && <p style={{ color: "#64748B" }}>Loading…</p>}
      {!isLoading && shown.length === 0 && <p style={{ color: "#64748B" }}>No reports match.</p>}

      {categories.map(cat => (
        <div key={cat} style={{ marginBottom: 20 }}>
          <h3 style={{ fontSize: 13, fontWeight: 700, color: "#334155", margin: "0 0 8px", textTransform: "uppercase", letterSpacing: 0.4 }}>{cat}</h3>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 12 }}>
            {shown.filter(c => c.category === cat).map(c => (
              <div key={c.key} style={{ border: "1px solid #E2E8F0", borderRadius: 10, padding: 14, display: "flex", flexDirection: "column", gap: 8 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A", display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 8 }}>
                  <span>{c.title}</span>
                  {canExport && (
                    <label style={{ fontSize: 11, fontWeight: 600, color: "#475569", display: "flex", alignItems: "center", gap: 4, whiteSpace: "nowrap", cursor: "pointer" }}>
                      <input type="checkbox" checked={picked.includes(c.key)} disabled={!picked.includes(c.key) && picked.length >= 20} onChange={() => togglePick(c.key)} />Pack
                    </label>
                  )}
                </div>
                <div style={{ fontSize: 12, color: "#64748B", flex: 1 }}>{c.description}</div>
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  {c.containsNames && <Badge text="Includes staff names" color="#92400E" bg="#FEF3C7" />}
                  {c.supportsAsOf && <Badge text="Can run as at a date" color="#3730A3" bg="#E0E7FF" />}
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <button style={{ ...primaryBtn, padding: "7px 12px", fontSize: 13, display: "flex", alignItems: "center", gap: 6 }}
                    disabled={run.isPending} onClick={() => run.mutate(c)}><Play size={13} />Run report</button>
                  {canExport && (
                    FORMATS.map(f => (
                      <button key={f.value} style={{ ...ghostBtn, padding: "7px 10px", display: "flex", alignItems: "center", gap: 5 }}
                        disabled={download.isPending} title={`Download as ${f.title}`} onClick={() => download.mutate({ info: c, format: f.value })}><Download size={13} />{f.label}</button>
                    ))
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      ))}

      {result && (
        <div style={{ marginTop: 24, borderTop: "1px solid #E2E8F0", paddingTop: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginBottom: 8, flexWrap: "wrap" }}>
            <div>
              <div style={{ fontWeight: 800, fontSize: 16, color: "#0F172A" }}>{result.title}</div>
              <div style={{ fontSize: 12, color: "#64748B" }}>
                {result.rowCount} row{result.rowCount === 1 ? "" : "s"} · {result.asOf ? `as at ${result.asOf}` : "position today"} · generated {fmtDateTime(result.generatedAt)}{result.generatedBy ? ` by ${result.generatedBy}` : ""}
              </div>
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              {canExport && current && (
                FORMATS.map(f => (
                  <button key={f.value} style={{ ...ghostBtn, display: "flex", alignItems: "center", gap: 6 }} disabled={download.isPending} onClick={() => download.mutate({ info: current, format: f.value })}><Download size={14} />Export {f.label}</button>
                ))
              )}
              <button style={{ ...ghostBtn, display: "flex", alignItems: "center", gap: 6 }} onClick={() => setResult(null)}><X size={14} />Close</button>
            </div>
          </div>
          {result.truncated && (
            <p style={{ fontSize: 12, color: "#92400E", background: "#FEF3C7", padding: "8px 12px", borderRadius: 8 }}>
              Only the first {result.rows.length} rows are shown and exported. Narrow the register before relying on this report.
            </p>
          )}
          {result.rows.length === 0 ? <p style={{ color: "#64748B" }}>Nothing to report.</p> : (
            <div style={{ overflowX: "auto", border: "1px solid #E2E8F0", borderRadius: 8 }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                <thead>
                  <tr>{result.columns.map(c => <th key={c} style={{ textAlign: "left", padding: "8px 10px", background: "#F8FAFC", borderBottom: "1px solid #E2E8F0", whiteSpace: "nowrap", color: "#475569" }}>{c}</th>)}</tr>
                </thead>
                <tbody>
                  {result.rows.map((row, i) => (
                    <tr key={i}>{row.map((cell, j) => <td key={j} style={{ padding: "7px 10px", borderBottom: "1px solid #F1F5F9", verticalAlign: "top" }}>{cell}</td>)}</tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
      {canExport && <ReportSchedulesPanel reports={catalogue.map(c => ({ key: c.key, title: c.title }))} />}
    </div>
  )
}
