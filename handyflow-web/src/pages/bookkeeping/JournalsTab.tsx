// A client's own journal: draft, check it balances, post.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { dateText, errorOf, money, rowsOf, type Account } from "./bookkeeping.logic"
import { emptyLine, journalProblem, journalRequest, journalTotals, type Journal } from "./practice.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, right, td, th } from "./ui"

export default function JournalsTab() { return <ClientScope>{id => <Journals clientId={id} />}</ClientScope> }

function Journals({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const base = "/api/v1/bookkeeping"
  const [date, setDate] = useState(new Date().toISOString().slice(0, 10)), [description, setDescription] = useState(""), [reference, setReference] = useState("")
  const [lines, setLines] = useState([emptyLine(), emptyLine()])
  const [open, setOpen] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { data: accounts = [] } = useQuery<Account[]>({ queryKey: ["bk-accounts", clientId], queryFn: async () => rowsOf<Account>(await apiClient.get(`${base}/clients/${clientId}/accounts`)) })
  const { data: journals = [] } = useQuery<Journal[]>({ queryKey: ["bk-journals", clientId], queryFn: async () => rowsOf<Journal>(await apiClient.get(`${base}/clients/${clientId}/journal-entries?size=50&sort=entryDate,desc`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["bk-journals"] }); qc.invalidateQueries({ queryKey: ["bk-work-queue"] }) }
  const create = useMutation({ mutationFn: () => apiClient.post(`${base}/journal-entries`, journalRequest(clientId, date, description, reference, lines)),
    onSuccess: () => { setError(null); setDescription(""); setReference(""); setLines([emptyLine(), emptyLine()]); refresh() }, onError: e => setError(errorOf(e)) })
  const post = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/journal-entries/${id}/post`), onSuccess: () => { setError(null); refresh() }, onError: e => setError(errorOf(e)) })
  const setLine = (i: number, k: string, v: string) => setLines(ls => ls.map((l, j) => (j === i ? { ...l, [k]: v } : l)))
  const problem = journalProblem(date, lines)
  const totals = journalTotals(lines)
  const nameOf = (id: string) => { const a = accounts.find(x => x.id === id); return a ? `${a.accountCode} ${a.accountName}` : "—" }
  return (
    <div>
      {canManage && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>New journal</h3>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
            <input aria-label="Journal date" type="date" value={date} onChange={e => setDate(e.target.value)} style={field} />
            <input aria-label="Journal description" placeholder="Description" value={description} onChange={e => setDescription(e.target.value)} style={{ ...field, width: 280 }} />
            <input aria-label="Journal reference" placeholder="Reference" value={reference} onChange={e => setReference(e.target.value)} style={{ ...field, width: 140 }} />
          </div>
          <table style={{ borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Account</th><th style={th}>Note</th><th style={{ ...th, ...right }}>Debit</th><th style={{ ...th, ...right }}>Credit</th></tr></thead>
            <tbody>{lines.map((l, i) => (
              <tr key={i}>
                <td style={td}><select aria-label={`Line ${i + 1} account`} value={l.accountId} onChange={e => setLine(i, "accountId", e.target.value)} style={{ ...field, minWidth: 220 }}>
                  <option value="">Choose…</option>{accounts.map(a => <option key={a.id} value={a.id}>{a.accountCode} {a.accountName}</option>)}</select></td>
                <td style={td}><input aria-label={`Line ${i + 1} note`} value={l.description} onChange={e => setLine(i, "description", e.target.value)} style={{ ...field, width: 160 }} /></td>
                <td style={td}><input aria-label={`Line ${i + 1} debit`} value={l.debit} onChange={e => setLine(i, "debit", e.target.value)} style={{ ...field, width: 100, textAlign: "right" }} /></td>
                <td style={td}><input aria-label={`Line ${i + 1} credit`} value={l.credit} onChange={e => setLine(i, "credit", e.target.value)} style={{ ...field, width: 100, textAlign: "right" }} /></td>
              </tr>))}
              <tr><td style={td}><button style={btn} onClick={() => setLines(ls => [...ls, emptyLine()])}>Add line</button></td><td style={{ ...td, ...right }}><b>Totals</b></td>
                <td style={{ ...td, ...right }}>{money(totals.debit)}</td><td style={{ ...td, ...right }}>{money(totals.credit)}</td></tr>
            </tbody>
          </table>
          <div style={{ marginTop: 8, display: "flex", gap: 10, alignItems: "center" }}>
            <button style={primary} disabled={!!problem || create.isPending} onClick={() => create.mutate()}>Save as draft</button>
            {problem && lines.some(l => l.accountId || l.debit || l.credit) && <span style={muted}>{problem}</span>}
          </div>
        </div>
      )}
      {error && <div role="alert" style={{ ...danger, marginBottom: 8 }}>{error}</div>}
      <div style={card}>
        {journals.length === 0 && <p style={muted}>No journals yet.</p>}
        {journals.length > 0 && (
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>No.</th><th style={th}>Date</th><th style={th}>Description</th><th style={{ ...th, ...right }}>Amount</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{journals.map(j => (
              <>
                <tr key={j.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={{ ...td, fontFamily: "monospace" }}>{j.entryNumber}</td><td style={td}>{dateText(j.entryDate)}</td><td style={td}>{j.description || j.reference || "—"}</td>
                  <td style={{ ...td, ...right }}>{money(j.totalDebit)}</td><td style={td}>{j.status === "POSTED" ? "Posted" : "Draft"}</td>
                  <td style={{ ...td, ...right }}><button style={btn} onClick={() => setOpen(open === j.id ? null : j.id)}>{open === j.id ? "Hide" : "Lines"}</button>{" "}
                    {canManage && j.status !== "POSTED" && <button style={primary} disabled={post.isPending} onClick={() => post.mutate(j.id)}>Post</button>}</td>
                </tr>
                {open === j.id && j.lines.map(l => (
                  <tr key={l.id} style={{ background: "var(--hf-surface-2, transparent)" }}>
                    <td /><td colSpan={2} style={td}>{nameOf(l.accountId)}{l.description ? ` — ${l.description}` : ""}</td>
                    <td style={{ ...td, ...right }}>{l.debitAmount ? `Dr ${money(l.debitAmount)}` : `Cr ${money(l.creditAmount)}`}</td><td colSpan={2} />
                  </tr>))}
              </>))}</tbody>
          </table>
        )}
      </div>
    </div>
  )
}
