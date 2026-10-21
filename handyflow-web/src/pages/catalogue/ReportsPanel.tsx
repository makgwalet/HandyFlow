// src/pages/catalogue/ReportsPanel.tsx — the catalogue reports. Pick one, set what it takes, read it here or take it as CSV.
import { useState } from "react"
import { useMutation, useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ErrorBanner } from "./CatalogueKit"
import { inp, linkBtn, primaryBtn, sel, td, th } from "./catalogue.styles"
import { EMPTY_REPORT_OPTIONS, REPORT_SETTINGS, errorMessage, reportParams, reportProblem, type PriceList, type Report, type ReportDef, type ReportOptions } from "./catalogue.logic"

const inner = <T,>(r: { data: unknown }): T => { const d = r.data as { data?: T } | null; return (d && typeof d === "object" && "data" in d ? d.data : r.data) as T }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }

export default function ReportsPanel() {
  const canExport = usePermissions(["CATALOGUE_EXPORT", "SETTINGS_MANAGE"])
  const [code, setCode] = useState<string | null>(null)
  const [opts, setOpts] = useState<ReportOptions>(EMPTY_REPORT_OPTIONS)
  const [report, setReport] = useState<Report | null>(null)
  const [error, setError] = useState("")
  const defs = useQuery<ReportDef[]>({ queryKey: ["catalogue", "reports"], queryFn: async () => inner<ReportDef[]>(await apiClient.get("/api/v1/catalogue/reports")) ?? [] })
  const lists = useQuery<PriceList[]>({ queryKey: ["catalogue", "price-lists"], enabled: code === "PRICE_LIST", queryFn: async () => inner<PriceList[]>(await apiClient.get("/api/v1/catalogue/price-lists")) ?? [] })
  const spec = code ? REPORT_SETTINGS[code] : undefined
  const problem = code ? reportProblem(code, opts) : ""
  const run = useMutation({
    mutationFn: async (c: string) => inner<Report>(await apiClient.get(`/api/v1/catalogue/reports/${c}`, { params: reportParams(c, opts) })),
    onSuccess: r => { setError(""); setReport(r) }, onError: e => { setReport(null); setError(errorMessage(e, "Couldn't run the report.")) },
  })
  const download = async () => {
    if (!code) return
    try {
      const r = await apiClient.get(`/api/v1/catalogue/reports/${code}/export`, { params: reportParams(code, opts), responseType: "blob" })
      const url = URL.createObjectURL(r.data as Blob)
      const a = document.createElement("a")
      a.href = url; a.download = `catalogue-${code.toLowerCase().replace(/_/g, "-")}.csv`; a.click()
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (e) { setError(errorMessage(e, "Couldn't download the report.")) }
  }
  const choose = (c: string) => { setCode(c); setReport(null); setError(""); setOpts({ ...EMPTY_REPORT_OPTIONS }) }
  const groups = Array.from(new Set((defs.data ?? []).map(d => d.group)))

  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))", gap: 20, alignItems: "start" }}>
      <nav aria-label="Reports">
        {defs.isLoading && <p role="status">Loading…</p>}
        {defs.isError && <ErrorBanner message={errorMessage(defs.error, "Couldn't load the reports.")} />}
        {groups.map(g => (
          <div key={g} style={{ marginBottom: 14 }}>
            <h4 style={{ margin: "0 0 6px", fontSize: 11, textTransform: "uppercase", letterSpacing: "0.05em", color: "var(--hf-text-faint)" }}>{g}</h4>
            {(defs.data ?? []).filter(d => d.group === g).map(d => (
              <button key={d.code} type="button" disabled={!d.available} onClick={() => choose(d.code)} aria-current={code === d.code}
                title={d.available ? d.description : "Needs access to prices and costs"}
                style={{ display: "block", width: "100%", textAlign: "left", padding: "8px 10px", marginBottom: 4, borderRadius: 8, fontSize: 13, cursor: d.available ? "pointer" : "not-allowed",
                  border: "1px solid " + (code === d.code ? "var(--hf-primary)" : "var(--hf-border-subtle)"), background: code === d.code ? "var(--hf-surface-muted)" : "var(--hf-surface)", color: d.available ? "var(--hf-text)" : "var(--hf-text-faint)" }}>
                {d.title}
              </button>
            ))}
          </div>
        ))}
      </nav>
      <section aria-label="Report" style={{ gridColumn: "span 2", minWidth: 0 }}>
        {!code && <p style={muted}>Choose a report on the left.</p>}
        {code && defs.data && (
          <>
            <p style={{ ...muted, marginTop: 0 }}>{defs.data.find(d => d.code === code)?.description}</p>
            <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 12 }}>
              {spec?.listId && (
                <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>Price list
                  <select style={{ ...sel, marginTop: 4, minWidth: 180 }} value={opts.listId} onChange={e => setOpts(o => ({ ...o, listId: e.target.value }))}>
                    <option value="">Default list</option>
                    {(lists.data ?? []).filter(l => !l.defaultList && l.active).map(l => <option key={l.id} value={l.id}>{l.name}</option>)}
                  </select>
                </label>
              )}
              {spec?.marginLimit !== undefined && (
                <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>Margin below (%)
                  <input style={{ ...inp, marginTop: 4, width: 110 }} inputMode="numeric" value={opts.marginLimit} placeholder={spec.marginLimit} onChange={e => setOpts(o => ({ ...o, marginLimit: e.target.value }))} />
                </label>
              )}
              {spec?.days !== undefined && (
                <label style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>Last (days)
                  <input style={{ ...inp, marginTop: 4, width: 110 }} inputMode="numeric" value={opts.days} placeholder={spec.days} onChange={e => setOpts(o => ({ ...o, days: e.target.value }))} />
                </label>
              )}
              <button type="button" style={primaryBtn} disabled={!!problem || run.isPending} onClick={() => run.mutate(code)}>{run.isPending ? "Running…" : "Run report"}</button>
              {report && canExport && <button type="button" style={linkBtn} onClick={download}>Download CSV</button>}
            </div>
            {problem && <p role="alert" style={{ ...muted, color: "var(--hf-danger-text)" }}>{problem}</p>}
          </>
        )}
        {error && <ErrorBanner message={error} />}
        {report && (
          <div>
            <p role="status" style={{ ...muted, margin: "0 0 8px" }}>{report.total} row{report.total === 1 ? "" : "s"}.{report.note ? ` ${report.note}` : ""}</p>
            {report.rows.length === 0
              ? <p style={muted}>Nothing to show.</p>
              : (
                <div style={{ overflowX: "auto", border: "1px solid var(--hf-border-subtle)", borderRadius: 10 }}>
                  <table style={{ width: "100%", borderCollapse: "collapse" }}>
                    <thead><tr>{report.columns.map(c => <th key={c.key} style={{ ...th, textAlign: c.numeric ? "right" : "left" }}>{c.label}</th>)}</tr></thead>
                    <tbody>{report.rows.map((r, i) => <tr key={i}>{r.map((cell, j) => <td key={j} style={{ ...td, textAlign: report.columns[j]?.numeric ? "right" : "left" }}>{cell}</td>)}</tr>)}</tbody>
                  </table>
                </div>
              )}
          </div>
        )}
      </section>
    </div>
  )
}
