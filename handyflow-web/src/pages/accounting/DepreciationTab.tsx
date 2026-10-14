// src/pages/accounting/DepreciationTab.tsx
//
// Finance F25: the monthly depreciation run. It makes ONE draft journal for the month (a different person posts it).
// A month nobody ran is caught up in the next run, and a discarded draft frees its month to be run again.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { money, monthName } from "./fixedAsset.logic"

interface PLine { assetId: string; assetNumber: string; name: string; charge: number; accumulatedAfter: number; bookValueAfter: number }
interface Preview { period: string | null; lastRunPeriod: string | null; nextPeriod: string | null; lines: PLine[]; total: number; problem: string | null }
interface Run { id: string; period: string; journalNumber: string; journalStatus: string; total: number; assets: number }
const unwrap = (r: any) => r.data?.data ?? r.data
const errorOf = (e: any) => e?.response?.data?.message ?? "Something went wrong. Please try again."
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 14, overflowX: "auto" }
const btn: React.CSSProperties = { padding: "7px 13px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", fontWeight: 600, fontSize: 12, cursor: "pointer" }

export default function DepreciationTab() {
  const qc = useQueryClient()
  const canManage = usePermission("ACCOUNTING_MANAGE")
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const { data: preview } = useQuery<Preview>({
    queryKey: ["acc-dep-preview"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/fixed-assets/depreciation/preview")),
  })
  const { data: runs = [] } = useQuery<Run[]>({
    queryKey: ["acc-dep-runs"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/fixed-assets/depreciation/runs")) ?? [],
  })
  const run = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/fixed-assets/depreciation/run", { period: preview?.period }),
    onSuccess: r => {
      const d = unwrap(r)
      setError(null)
      setNotice(`Depreciation for ${monthName(d.period)} drafted as journal ${d.journalNumber}. A different person must post it.`)
      for (const k of ["acc-dep-preview", "acc-dep-runs", "acc-fixed-assets", "acc-fixed-recon", "journals"]) qc.invalidateQueries({ queryKey: [k] })
    },
    onError: e => setError(errorOf(e)),
  })

  const ready = !!preview?.period && !preview.problem && preview.lines.length > 0

  return (
    <div>
      <h2 style={{ marginTop: 0 }}>Depreciation</h2>
      {notice && <div role="status" style={{ ...card, background: "var(--hf-success-soft)" }}>{notice}</div>}
      <div style={card}>
        {!preview && <p style={{ fontSize: 13 }}>Loading…</p>}
        {preview && !preview.period && <p style={{ fontSize: 13 }}>{preview.problem ?? "Depreciation is up to date."}</p>}
        {preview?.period && (
          <>
            <h3 style={{ marginTop: 0 }}>Next run: {monthName(preview.period)}</h3>
            {preview.problem && <p style={{ fontSize: 13 }}>{preview.problem}</p>}
            {preview.lines.length > 0 && (
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)", fontSize: 11, textTransform: "uppercase" }}>
                  <th>Asset</th><th style={{ textAlign: "right" }}>Charge</th><th style={{ textAlign: "right" }}>Accumulated after</th><th style={{ textAlign: "right" }}>Book value after</th></tr></thead>
                <tbody>
                  {preview.lines.map(l => (
                    <tr key={l.assetId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                      <td>{l.assetNumber} {l.name}</td><td style={{ textAlign: "right" }}>{money(l.charge)}</td>
                      <td style={{ textAlign: "right" }}>{money(l.accumulatedAfter)}</td><td style={{ textAlign: "right" }}>{money(l.bookValueAfter)}</td></tr>))}
                  <tr style={{ borderTop: "2px solid var(--hf-border)", fontWeight: 700 }}><td>Total</td><td style={{ textAlign: "right" }}>{money(preview.total)}</td><td /><td /></tr>
                </tbody>
              </table>
            )}
            {error && <div role="alert" style={{ color: "var(--hf-danger-text, #b91c1c)", fontSize: 12, marginTop: 6 }}>{error}</div>}
            {canManage && <button style={{ ...btn, marginTop: 10 }} disabled={!ready || run.isPending} onClick={() => run.mutate()}>Draft the depreciation journal</button>}
          </>
        )}
      </div>

      <div style={card}>
        <h3 style={{ marginTop: 0 }}>Earlier runs</h3>
        {runs.length === 0 && <p style={{ fontSize: 13 }}>No depreciation has been run yet.</p>}
        {runs.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <tbody>{runs.map(r => (
              <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td>{monthName(r.period)}</td><td>{r.journalNumber}</td><td>{r.journalStatus === "POSTED" ? "Posted" : "Waiting to be posted"}</td>
                <td>{r.assets} asset{r.assets === 1 ? "" : "s"}</td><td style={{ textAlign: "right" }}>{money(r.total)}</td></tr>))}</tbody>
          </table>
        )}
      </div>
    </div>
  )
}
