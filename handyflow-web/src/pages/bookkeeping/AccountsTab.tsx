// A client's chart of accounts. The standard accounts are fixed; extra ones can be added.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorOf, money, rowsOf } from "./bookkeeping.logic"
import { ACCOUNT_TYPES, accountProblem, typeLabel } from "./practice.logic"
import ClientScope from "./ClientScope"
import { card, danger, field, muted, primary, right, td, th } from "./ui"

export default function AccountsTab() { return <ClientScope>{id => <Accounts clientId={id} />}</ClientScope> }

function Accounts({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const [form, setForm] = useState({ code: "", name: "", type: "EXPENSE", description: "" })
  const [error, setError] = useState<string | null>(null)
  const { data = [] } = useQuery<any[]>({ queryKey: ["bk-accounts", clientId], queryFn: async () => rowsOf<any>(await apiClient.get(`/api/v1/bookkeeping/clients/${clientId}/accounts`)) })
  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/bookkeeping/clients/${clientId}/accounts`, { accountCode: form.code.trim(), accountName: form.name.trim(), accountType: form.type, accountSubtype: null, description: form.description.trim() || null }),
    onSuccess: () => { setError(null); setForm({ code: "", name: "", type: "EXPENSE", description: "" }); qc.invalidateQueries({ queryKey: ["bk-accounts"] }) }, onError: e => setError(errorOf(e)),
  })
  const problem = accountProblem(form.code, form.name, form.type)
  return (
    <div>
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Add an account</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <input aria-label="Account code" placeholder="Code" value={form.code} onChange={e => setForm({ ...form, code: e.target.value })} style={{ ...field, width: 90 }} />
            <input aria-label="Account name" placeholder="Name" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} style={{ ...field, width: 220 }} />
            <select aria-label="Account type" value={form.type} onChange={e => setForm({ ...form, type: e.target.value })} style={field}>{ACCOUNT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}</select>
            <button style={primary} disabled={!!problem || add.isPending} onClick={() => add.mutate()}>Add</button>
          </div>
          {error && <div role="alert" style={{ ...danger, marginTop: 6 }}>{error}</div>}
        </div>
      )}
      <div style={card}>
        {data.length === 0 && <p style={muted}>No accounts yet.</p>}
        {data.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Code</th><th style={th}>Account</th><th style={th}>Type</th><th style={{ ...th, ...right }}>Opening balance</th></tr></thead>
            <tbody>{data.map((a: any) => (
              <tr key={a.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={{ ...td, fontFamily: "monospace" }}>{a.accountCode}</td><td style={td}>{a.accountName}{!a.system && <span style={muted}> · added</span>}</td>
                <td style={td}>{typeLabel(a.accountType)}</td><td style={{ ...td, ...right }}>{a.openingBalance ? money(a.openingBalance) : "—"}</td>
              </tr>))}</tbody>
          </table>
        )}
      </div>
    </div>
  )
}
