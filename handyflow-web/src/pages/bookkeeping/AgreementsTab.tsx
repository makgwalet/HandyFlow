// What each client pays the practice: a monthly retainer or hourly time and materials.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { dateText, errorOf, rowsOf } from "./bookkeeping.logic"
import { BILLING_TYPES, agreementProblem, agreementTerms, billingLabel, type Agreement } from "./practice.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, td, th } from "./ui"

export default function AgreementsTab() { return <ClientScope>{id => <Agreements clientId={id} />}</ClientScope> }

function Agreements({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const [f, setF] = useState({ type: "RETAINER", fee: "", rate: "", start: new Date().toISOString().slice(0, 10), end: "" })
  const [error, setError] = useState<string | null>(null)
  const { data = [] } = useQuery<Agreement[]>({ queryKey: ["bk-agreements", clientId], queryFn: async () => rowsOf<Agreement>(await apiClient.get(`/api/v1/bookkeeping/service-agreements?clientId=${clientId}&size=50`)) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["bk-agreements"] })
  const add = useMutation({
    mutationFn: () => apiClient.post("/api/v1/bookkeeping/service-agreements", { clientId, billingType: f.type, monthlyFee: f.type === "RETAINER" ? Number(f.fee) : null, hourlyRate: f.type === "RETAINER" ? null : Number(f.rate), startDate: f.start, endDate: f.end || null }),
    onSuccess: () => { setError(null); setF({ ...f, fee: "", rate: "", end: "" }); refresh() }, onError: e => setError(errorOf(e)),
  })
  const end = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/bookkeeping/service-agreements/${id}/end`), onSuccess: refresh, onError: e => setError(errorOf(e)) })
  const problem = agreementProblem(f.type, f.fee, f.rate, f.start, f.end)
  return (
    <div>
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>New agreement</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
            <select aria-label="Billing type" value={f.type} onChange={e => setF({ ...f, type: e.target.value })} style={field}>{BILLING_TYPES.map(b => <option key={b.value} value={b.value}>{b.label}</option>)}</select>
            {f.type === "RETAINER"
              ? <input aria-label="Monthly fee" placeholder="Monthly fee" value={f.fee} onChange={e => setF({ ...f, fee: e.target.value })} style={{ ...field, width: 120 }} />
              : <input aria-label="Hourly rate" placeholder="Rate per hour" value={f.rate} onChange={e => setF({ ...f, rate: e.target.value })} style={{ ...field, width: 120 }} />}
            <label style={muted}>From <input aria-label="Start date" type="date" value={f.start} onChange={e => setF({ ...f, start: e.target.value })} style={field} /></label>
            <label style={muted}>To (optional) <input aria-label="End date" type="date" value={f.end} onChange={e => setF({ ...f, end: e.target.value })} style={field} /></label>
            <button style={primary} disabled={!!problem || add.isPending} onClick={() => add.mutate()}>Add</button>
          </div>
          {problem && (f.fee || f.rate || f.end) && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
        </div>
      )}
      {error && <div role="alert" style={{ ...danger, marginBottom: 8 }}>{error}</div>}
      <div style={card}>
        {data.length === 0 && <p style={muted}>No agreement yet. Invoices need one to know what to charge.</p>}
        {data.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Type</th><th style={th}>Terms</th><th style={th}>From</th><th style={th}>To</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{data.map(a => (
              <tr key={a.id} style={{ borderTop: "1px solid var(--hf-border)", opacity: a.status === "ACTIVE" ? 1 : 0.6 }}>
                <td style={td}>{billingLabel(a.billingType)}</td><td style={td}>{agreementTerms(a)}</td><td style={td}>{dateText(a.startDate)}</td><td style={td}>{a.endDate ? dateText(a.endDate) : "Open"}</td>
                <td style={td}>{a.status === "ACTIVE" ? "Active" : "Ended"}</td>
                <td style={{ ...td, textAlign: "right" }}>{canManage && a.status === "ACTIVE" && <button style={btn} onClick={() => { if (window.confirm("End this agreement?")) end.mutate(a.id) }}>End</button>}</td>
              </tr>))}</tbody>
          </table>
        )}
      </div>
    </div>
  )
}
