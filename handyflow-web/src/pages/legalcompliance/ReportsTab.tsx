// src/pages/legalcompliance/ReportsTab.tsx
//
// Standard reports. Endpoints (/api/v1/legalcompliance/reports): GET / (catalogue) · GET /{key}?asOf= (run on screen) · GET /{key}/export?asOf= (CSV download; needs LEGALCOMPLIANCE_REPORT_EXPORT)
// The screen and the CSV show the same cells. Reports never contain personal data of the public (requesters, data subjects, declarants); the few that list staff or owner names say so.
// Only the executive scorecard can be run "as at" a past date, and only from stored snapshots.
import { useState } from "react"
import { useQuery, useMutation } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { one, input, primaryBtn, ghostBtn, fmtDateTime, errMsg } from "./lcHelpers"
import { Err, Badge } from "./LcUi"
import { Download, Play, X } from "lucide-react"

const base = "/api/v1/legalcompliance/reports"

interface Info { key: string; category: string; title: string; description: string; containsNames: boolean; supportsAsOf: boolean }
interface Result { key: string; title: string; category: string; generatedAt: string; generatedBy: string | null; asOf: string | null; columns: string[]; rows: string[][]; rowCount: number; truncated: boolean }

export default function ReportsTab() {
  const hasExport = usePermission("LEGALCOMPLIANCE_REPORT_EXPORT")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canExport = hasExport || hasAdmin

  const [search, setSearch] = useState("")
  const [asOf, setAsOf] = useState("")
  const [result, setResult] = useState<Result | null>(null)
  const [err, setErr] = useState("")

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
    mutationFn: async (info: Info) => {
      const r = await apiClient.get(`${base}/${info.key}/export`, { params: params(info), responseType: "blob" })
      const name = /filename="?([^";]+)"?/.exec(r.headers?.["content-disposition"] ?? "")?.[1] ?? `${info.key}.csv`
      const url = URL.createObjectURL(r.data as Blob)
      const a = document.createElement("a")
      a.href = url; a.download = name
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    },
    onSuccess: () => setErr(""),
    onError: (e: unknown) => setErr(errMsg(e, "Could not export the report.")),
  })

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
                <div style={{ fontWeight: 700, fontSize: 14, color: "#0F172A" }}>{c.title}</div>
                <div style={{ fontSize: 12, color: "#64748B", flex: 1 }}>{c.description}</div>
                <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                  {c.containsNames && <Badge text="Includes staff names" color="#92400E" bg="#FEF3C7" />}
                  {c.supportsAsOf && <Badge text="Can run as at a date" color="#3730A3" bg="#E0E7FF" />}
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                  <button style={{ ...primaryBtn, padding: "7px 12px", fontSize: 13, display: "flex", alignItems: "center", gap: 6 }}
                    disabled={run.isPending} onClick={() => run.mutate(c)}><Play size={13} />Run report</button>
                  {canExport && (
                    <button style={{ ...ghostBtn, padding: "7px 12px", display: "flex", alignItems: "center", gap: 6 }}
                      disabled={download.isPending} onClick={() => download.mutate(c)}><Download size={13} />CSV</button>
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
                <button style={{ ...ghostBtn, display: "flex", alignItems: "center", gap: 6 }} disabled={download.isPending} onClick={() => download.mutate(current)}><Download size={14} />Export CSV</button>
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
    </div>
  )
}
