// A client's bank: accounts, imported lines, the lines that rules can categorise in one go, and one-by-one booking or matching.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { chunks, dateText, errorOf, money, rowsOf, type Account, type BankAccount, type BankTx, type MatchCandidate, type Suggestion } from "./bookkeeping.logic"
import ClientScope from "./ClientScope"
import { btn, card, danger, field, muted, primary, right, td, th } from "./ui"

export default function BankLinesTab() {
  return <ClientScope>{clientId => <Bank clientId={clientId} />}</ClientScope>
}

const readFile = (file: File) => new Promise<string>((resolve, reject) => {
  const r = new FileReader()
  r.onload = () => resolve(String(r.result).split(",").pop() ?? "")
  r.onerror = () => reject(new Error("Could not read the file"))
  r.readAsDataURL(file)
})

function Bank({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const base = `/api/v1/bookkeeping/clients/${clientId}`
  const [bankId, setBankId] = useState("")
  const [onlyOpen, setOnlyOpen] = useState(true)
  const [page, setPage] = useState(0)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [skipped, setSkipped] = useState<Set<string>>(new Set())

  const { data: banks = [] } = useQuery<BankAccount[]>({ queryKey: ["bk-banks", clientId], queryFn: async () => rowsOf<BankAccount>(await apiClient.get(`${base}/bank-accounts?size=100`)) })
  const { data: accounts = [] } = useQuery<Account[]>({ queryKey: ["bk-accounts", clientId], queryFn: async () => rowsOf<Account>(await apiClient.get(`${base}/accounts`)) })
  const selected = bankId || banks[0]?.id || ""
  const bank = banks.find(b => b.id === selected)
  const txKey = ["bk-tx", clientId, selected, page]
  const { data: txPage } = useQuery<{ rows: BankTx[]; last: boolean }>({
    queryKey: txKey, enabled: !!selected,
    queryFn: async () => {
      const r = await apiClient.get(`${base}/bank-transactions?bankAccountId=${selected}&page=${page}&size=50&sort=transactionDate,desc`)
      const d = (r.data?.data ?? r.data) as any
      return { rows: d?.content ?? [], last: d?.last ?? true }
    },
  })
  const { data: suggestions = [] } = useQuery<Suggestion[]>({
    queryKey: ["bk-suggestions", clientId, selected], enabled: !!selected && canManage,
    queryFn: async () => rowsOf<Suggestion>(await apiClient.get(`${base}/bank-transactions/suggestions?bankAccountId=${selected}`)),
  })
  const refresh = () => { for (const k of ["bk-tx", "bk-suggestions", "bk-banks", "bk-work-queue"]) qc.invalidateQueries({ queryKey: [k] }) }
  const fail = (e: any) => { setMessage(null); setError(errorOf(e)) }

  // Only lines a rule recognised can be categorised in bulk (older servers also returned lines no rule fits).
  const matched = suggestions.filter(s => !!s.contraAccountId)
  const picked = matched.filter(s => !skipped.has(s.transactionId))
  const categorise = useMutation({
    mutationFn: async () => {
      let done = 0
      for (const part of chunks(picked)) {
        const r = await apiClient.post(`${base}/bank-transactions/categorise`, { items: part.map(s => ({ transactionId: s.transactionId, contraAccountId: s.contraAccountId })) })
        done += ((r.data?.data ?? r.data) as any)?.done ?? 0
      }
      return done
    },
    onSuccess: n => { setError(null); setMessage(`${n} ${n === 1 ? "line" : "lines"} categorised.`); setSkipped(new Set()); refresh() },
    // An earlier batch may already have been booked, so reload the list or the same lines would be sent again.
    onError: e => { fail(e); refresh() },
  })
  const doImport = useMutation({
    mutationFn: async (file: File) => (await apiClient.post(`${base}/bank-transactions/import`, { bankAccountId: selected, csvBase64: await readFile(file) })),
    onSuccess: r => { const d = (r.data?.data ?? r.data) as any; setError(null); setMessage(`${d.imported} imported, ${d.skippedDuplicates} already there.`); setPage(0); refresh() }, onError: fail,
  })
  const nameOf = (id: string) => { const a = accounts.find(x => x.id === id); return a ? `${a.accountCode} ${a.accountName}` : "—" }
  const rows = (txPage?.rows ?? []).filter(t => !onlyOpen || !t.reconciled)

  return (
    <div>
      <BankAccountsBar clientId={clientId} banks={banks} accounts={accounts} selected={selected} onSelect={id => { setBankId(id); setPage(0) }} canManage={canManage} />
      {bank && !bank.accountId && <div role="status" style={{ ...card, fontSize: 13 }}>This bank account is not linked to a ledger account yet, so lines cannot be booked. Link it above.</div>}
      {message && <div role="status" style={{ ...card, fontSize: 13 }}>{message}</div>}
      {error && <div role="alert" style={{ ...danger, marginBottom: 10 }}>{error}</div>}

      {canManage && matched.length > 0 && (
        <div style={card}>
          <h3 style={{ marginTop: 0 }}>Your rules recognise {matched.length} {matched.length === 1 ? "line" : "lines"}</h3>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th /><th style={th}>Date</th><th style={th}>Description</th><th style={{ ...th, ...right }}>Amount</th><th style={th}>Rule</th><th style={th}>Book to</th></tr></thead>
            <tbody>{matched.map(s => (
              <tr key={s.transactionId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={td}><input type="checkbox" aria-label={`Include ${s.description}`} checked={!skipped.has(s.transactionId)}
                  onChange={() => setSkipped(p => { const n = new Set(p); n.has(s.transactionId) ? n.delete(s.transactionId) : n.add(s.transactionId); return n })} /></td>
                <td style={td}>{dateText(s.date)}</td><td style={td}>{s.description}</td>
                <td style={{ ...td, ...right }}>{s.type === "DEBIT" ? "−" : ""}{money(s.amount)}</td><td style={td}>{s.ruleName}</td><td style={td}>{nameOf(s.contraAccountId)}</td>
              </tr>))}</tbody>
          </table>
          <button style={{ ...primary, marginTop: 10 }} disabled={picked.length === 0 || categorise.isPending} onClick={() => categorise.mutate()}>
            Categorise {picked.length} {picked.length === 1 ? "line" : "lines"}
          </button>
        </div>
      )}

      {selected && (
        <div style={card}>
          <div style={{ display: "flex", gap: 12, alignItems: "center", marginBottom: 8, flexWrap: "wrap" }}>
            <label style={muted}><input type="checkbox" aria-label="Only lines not yet categorised" checked={onlyOpen} onChange={e => setOnlyOpen(e.target.checked)} /> Only lines not yet categorised</label>
            {canManage && <label style={{ ...btn, display: "inline-block" }}>Import bank statement (CSV)
              <input aria-label="Import CSV" type="file" accept=".csv,text/csv" style={{ display: "none" }}
                onChange={e => { const f = e.target.files?.[0]; if (f) doImport.mutate(f); e.target.value = "" }} /></label>}
          </div>
          {rows.length === 0 && <p style={muted}>{onlyOpen ? "Nothing left to categorise on this page." : "No lines yet. Import a statement to start."}</p>}
          {rows.length > 0 && (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
              <thead><tr><th style={th}>Date</th><th style={th}>Description</th><th style={th}>Reference</th><th style={{ ...th, ...right }}>Amount</th><th style={th}>Status</th><th /></tr></thead>
              <tbody>{rows.map(t => <Line key={t.id} t={t} clientId={clientId} accounts={accounts} canManage={canManage} onDone={refresh} onError={fail} />)}</tbody>
            </table>
          )}
          <div style={{ marginTop: 8, display: "flex", gap: 8 }}>
            <button style={btn} disabled={page === 0} onClick={() => setPage(p => p - 1)}>Newer</button>
            <button style={btn} disabled={txPage?.last ?? true} onClick={() => setPage(p => p + 1)}>Older</button>
          </div>
        </div>
      )}
    </div>
  )
}

function BankAccountsBar({ clientId, banks, accounts, selected, onSelect, canManage }: { clientId: string; banks: BankAccount[]; accounts: Account[]; selected: string; onSelect: (id: string) => void; canManage: boolean }) {
  const qc = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ bankName: "", accountName: "", accountNumber: "", branchCode: "", accountType: "CURRENT" })
  const [err, setErr] = useState<string | null>(null)
  const bank = banks.find(b => b.id === selected)
  const created = () => { setAdding(false); setErr(null); setForm({ bankName: "", accountName: "", accountNumber: "", branchCode: "", accountType: "CURRENT" }); qc.invalidateQueries({ queryKey: ["bk-banks"] }) }
  const add = useMutation({ mutationFn: () => apiClient.post("/api/v1/bookkeeping/bank-accounts", { clientId, ...form, branchCode: form.branchCode || null }), onSuccess: created, onError: e => setErr(errorOf(e)) })
  const link = useMutation({ mutationFn: (accountId: string) => apiClient.post(`/api/v1/bookkeeping/bank-accounts/${selected}/link-account`, { accountId }), onSuccess: created, onError: e => setErr(errorOf(e)) })
  const assets = accounts.filter(a => a.accountType === "ASSET")
  return (
    <div style={card}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", fontSize: 12 }}>
        {banks.length > 0 && (
          <label>Bank account{" "}
            <select aria-label="Bank account" value={selected} onChange={e => onSelect(e.target.value)} style={{ ...field, minWidth: 260 }}>
              {banks.map(b => <option key={b.id} value={b.id}>{b.bankName} — {b.accountName} ({b.accountNumber})</option>)}
            </select>
          </label>
        )}
        {bank && <span style={muted}>Balance {money(bank.currentBalance)}</span>}
        {bank && !bank.accountId && canManage && (
          <select aria-label="Link to ledger account" value="" onChange={e => e.target.value && link.mutate(e.target.value)} style={field}>
            <option value="">Link to ledger account…</option>{assets.map(a => <option key={a.id} value={a.id}>{a.accountCode} {a.accountName}</option>)}
          </select>
        )}
        {canManage && <button style={btn} onClick={() => setAdding(a => !a)}>{adding ? "Cancel" : "Add bank account"}</button>}
      </div>
      {banks.length === 0 && !adding && <p style={muted}>This client has no bank account yet.</p>}
      {adding && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 10 }}>
          <input aria-label="Bank name" placeholder="Bank" value={form.bankName} onChange={e => setForm({ ...form, bankName: e.target.value })} style={{ ...field, width: 130 }} />
          <input aria-label="Account name" placeholder="Account name" value={form.accountName} onChange={e => setForm({ ...form, accountName: e.target.value })} style={{ ...field, width: 170 }} />
          <input aria-label="Account number" placeholder="Account number" value={form.accountNumber} onChange={e => setForm({ ...form, accountNumber: e.target.value })} style={{ ...field, width: 140 }} />
          <input aria-label="Branch code" placeholder="Branch code" value={form.branchCode} onChange={e => setForm({ ...form, branchCode: e.target.value })} style={{ ...field, width: 100 }} />
          <select aria-label="Account type" value={form.accountType} onChange={e => setForm({ ...form, accountType: e.target.value })} style={field}>
            <option value="CURRENT">Current</option><option value="SAVINGS">Savings</option><option value="CREDIT_CARD">Credit card</option></select>
          <button style={primary} disabled={!form.bankName.trim() || !form.accountName.trim() || !form.accountNumber.trim() || add.isPending} onClick={() => add.mutate()}>Save</button>
        </div>
      )}
      {err && <div role="alert" style={{ ...danger, marginTop: 6 }}>{err}</div>}
    </div>
  )
}

function Line({ t, clientId, accounts, canManage, onDone, onError }: { t: BankTx; clientId: string; accounts: Account[]; canManage: boolean; onDone: () => void; onError: (e: any) => void }) {
  const base = `/api/v1/bookkeeping/clients/${clientId}/bank-transactions/${t.id}`
  const [mode, setMode] = useState<null | "book" | "match">(null)
  const [contra, setContra] = useState("")
  const { data: candidates = [] } = useQuery<MatchCandidate[]>({
    queryKey: ["bk-candidates", t.id], enabled: mode === "match",
    queryFn: async () => rowsOf<MatchCandidate>(await apiClient.get(`${base}/match-candidates`)),
  })
  const book = useMutation({ mutationFn: () => apiClient.post(`${base}/reconcile-new`, { contraAccountId: contra, description: t.description }), onSuccess: () => { setMode(null); onDone() }, onError })
  const match = useMutation({ mutationFn: (journalLineId: string) => apiClient.post(`${base}/reconcile`, { journalLineId }), onSuccess: () => { setMode(null); onDone() }, onError })
  const credit = t.transactionType === "CREDIT"
  return (
    <>
      <tr style={{ borderTop: "1px solid var(--hf-border)" }}>
        <td style={td}>{dateText(t.transactionDate)}</td><td style={td}>{t.description}</td><td style={td}>{t.reference || "—"}</td>
        <td style={{ ...td, ...right }}>{credit ? "" : "−"}{money(Math.abs(t.amount))}</td>
        <td style={td}>{t.reconciled ? "Categorised" : "Not categorised"}</td>
        <td style={{ ...td, ...right }}>{canManage && !t.reconciled && <>
          <button style={btn} onClick={() => setMode(mode === "book" ? null : "book")}>Book to account</button>{" "}
          <button style={btn} onClick={() => setMode(mode === "match" ? null : "match")}>Match</button></>}</td>
      </tr>
      {mode === "book" && (
        <tr><td colSpan={6} style={td}>
          <select aria-label="Account to book to" value={contra} onChange={e => setContra(e.target.value)} style={{ ...field, minWidth: 260 }}>
            <option value="">Choose an account…</option>{accounts.map(a => <option key={a.id} value={a.id}>{a.accountCode} {a.accountName}</option>)}</select>{" "}
          <button style={primary} disabled={!contra || book.isPending} onClick={() => book.mutate()}>Book it</button>
        </td></tr>
      )}
      {mode === "match" && (
        <tr><td colSpan={6} style={td}>
          {candidates.length === 0 ? <span style={muted}>No journal line looks like this one.</span> : candidates.map(c => (
            <div key={c.journalLineId} style={{ marginBottom: 4 }}>
              {c.entryNumber} · {dateText(c.entryDate)} · {c.description} · {money(c.amount)}{c.exactMatch ? " (exact)" : ""}{" "}
              <button style={btn} disabled={match.isPending} onClick={() => match.mutate(c.journalLineId)}>Match</button>
            </div>))}
        </td></tr>
      )}
    </>
  )
}
