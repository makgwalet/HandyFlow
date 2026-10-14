// A client's months: open or closed, with the trial balance for each.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorOf, monthName, rowsOf, type Period } from "./bookkeeping.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, muted, td, th } from "./ui"

export default function PeriodsTab() {
  return <ClientScope>{clientId => <Periods clientId={clientId} />}</ClientScope>
}

function Periods({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const canReopen = usePermission("BOOKKEEPING_ADMIN")
  const [error, setError] = useState<string | null>(null)
  const { data: periods = [] } = useQuery<Period[]>({
    queryKey: ["bk-periods", clientId],
    queryFn: async () => rowsOf<Period>(await apiClient.get(`/api/v1/bookkeeping/clients/${clientId}/periods?size=60&sort=periodYear,desc&sort=periodMonth,desc`)),
  })
  const done = () => { setError(null); qc.invalidateQueries({ queryKey: ["bk-periods"] }) }
  const act = useMutation({ mutationFn: (v: { id: string; what: "close" | "reopen" }) => apiClient.post(`/api/v1/bookkeeping/periods/${v.id}/${v.what}`), onSuccess: done, onError: e => setError(errorOf(e)) })
  const trialBalance = async (p: Period) => {
    try {
      const r = await apiClient.get(`/api/v1/bookkeeping/clients/${clientId}/periods/${p.id}/trial-balance.pdf`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([r.data], { type: "application/pdf" }))
      window.open(url, "_blank")
      setTimeout(() => URL.revokeObjectURL(url), 60000)
    } catch (e) { setError(errorOf(e)) }
  }
  return (
    <div style={card}>
      {error && <div role="alert" style={{ ...danger, marginBottom: 8 }}>{error}</div>}
      {periods.length === 0 && <p style={muted}>No months yet. They appear once the client has journals.</p>}
      {periods.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
          <thead><tr><th style={th}>Month</th><th style={th}>Status</th><th /></tr></thead>
          <tbody>{periods.map(p => (
            <tr key={p.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
              <td style={td}>{monthName(p.periodYear, p.periodMonth)}</td><td style={td}>{p.status === "CLOSED" ? "Closed" : "Open"}</td>
              <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                <button style={btn} onClick={() => trialBalance(p)}>Trial balance (PDF)</button>{" "}
                {canManage && p.status !== "CLOSED" && <button style={btn} onClick={() => act.mutate({ id: p.id, what: "close" })}>Close month</button>}
                {canReopen && p.status === "CLOSED" && <button style={btn} onClick={() => { if (window.confirm(`Reopen ${monthName(p.periodYear, p.periodMonth)}?`)) act.mutate({ id: p.id, what: "reopen" }) }}>Reopen</button>}
              </td>
            </tr>))}</tbody>
        </table>
      )}
    </div>
  )
}
