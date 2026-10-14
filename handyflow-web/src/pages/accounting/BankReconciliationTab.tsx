// src/pages/accounting/BankReconciliationTab.tsx
//
// Finance patch F12: reconcile a bank account against its statement. Shows where statement and ledger differ, what is on
// one side only, and what was booked straight from statement lines (for the person signing to check). Signing off closes
// the statement date to new lines. Matching rules turn routine lines (fees, interest) into journals, but only after a
// preview and an explicit apply.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, CheckCircle } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { ageText, describeRule, ruleFormProblem, STALE_DAYS } from "./bankRecon.logic"

interface BankAccount { id: string; bankName: string; accountName: string; active: boolean }
interface Account { id: string; accountCode: string; accountName: string }
interface Report {
  bankAccountId: string; accountName: string; asAt: string
  statementBalance: number; bookBalance: number; statementOnlyNet: number; bookOnlyNet: number
  difference: number; balanced: boolean; signedOffThrough: string | null; signOffBlocker: string | null
  onStatementOnly: { transactionId: string; date: string; description: string; reference: string | null; amount: number; ageDays: number }[]
  inBooksOnly: { journalLineId: string; entryNumber: string; date: string; description: string; amount: number; ageDays: number }[]
  bookedFromLines: { transactionId: string; date: string; description: string; amount: number; kind: string; ruleName: string | null }[]
}
interface Signed { id: string; periodEnd: string; statementBalance: number; difference: number; note: string | null; status: string
  signedAt: string; reopenedAt: string | null; reopenReason: string | null }
interface Rule { id: string; name: string; bankAccountId: string | null; direction: string; matchText: string | null; matchField: string | null
  minAmount: number | null; maxAmount: number | null; otherAccountName: string | null; priority: number; active: boolean; timesApplied: number }
interface Suggestion { transactionId: string; date: string; description: string; amount: number; ruleId: string; ruleName: string; otherAccountName: string }

const fmtR = (n: number) => `${n < 0 ? "-" : ""}R ${Math.abs(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const fmtDay = (d: string | null) => d ? new Date(`${d}T00:00:00`).toLocaleDateString("en-ZA") : "—"
const today = () => new Date().toISOString().split("T")[0]
const errorOf = (e: any, fallback: string) => e?.response?.data?.message ?? fallback
const unwrap = (r: any) => r.data?.data ?? r.data

const input: React.CSSProperties = { display: "block", width: "100%", marginTop: 4, padding: "8px 10px", border: "1.5px solid var(--hf-border)",
  borderRadius: 8, fontSize: 13, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 18, marginBottom: 16 }
const btn: React.CSSProperties = { padding: "8px 14px", borderRadius: 8, border: "none", background: "var(--hf-primary, #2563eb)", color: "#fff", fontWeight: 600, fontSize: 13, cursor: "pointer" }
const th: React.CSSProperties = { textAlign: "left", fontSize: 12, padding: "6px 8px", color: "var(--hf-text-secondary)" }
const td: React.CSSProperties = { fontSize: 13, padding: "6px 8px", borderTop: "1px solid var(--hf-border)" }

const blankRule = { name: "", direction: "OUT", matchText: "", matchField: "DESCRIPTION", minAmount: "", maxAmount: "", otherAccountId: "", description: "", thisAccountOnly: true }

export default function BankReconciliationTab() {
  const qc = useQueryClient()
  const canSign = usePermission("ACCOUNTING_RECON_SIGNOFF")
  const canManage = usePermission("ACCOUNTING_MANAGE")
  const [bankId, setBankId] = useState("")
  const [asAt, setAsAt] = useState(today())
  const [note, setNote] = useState("")
  const [reopenReason, setReopenReason] = useState("")
  const [form, setForm] = useState(blankRule)
  const [picked, setPicked] = useState<Set<string>>(new Set())
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")

  const { data: banks = [] } = useQuery<BankAccount[]>({
    queryKey: ["bank-accounts"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/bank-accounts")),
  })
  const { data: accounts = [] } = useQuery<Account[]>({
    queryKey: ["accounts-for-rules"], enabled: canManage,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/accounts")),
  })
  const base = `/api/v1/accounting/bank-accounts/${bankId}`
  const { data: report } = useQuery<Report>({
    queryKey: ["bank-recon", bankId, asAt], enabled: !!bankId,
    queryFn: async () => unwrap(await apiClient.get(`${base}/reconciliation?asAt=${asAt}`)),
  })
  const { data: history = [] } = useQuery<Signed[]>({
    queryKey: ["bank-recon-history", bankId], enabled: !!bankId,
    queryFn: async () => unwrap(await apiClient.get(`${base}/reconciliations`)),
  })
  const { data: rules = [] } = useQuery<Rule[]>({
    queryKey: ["bank-rules"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accounting/bank-rules")),
  })
  const { data: suggestions = [], refetch: refetchPreview, isFetching: previewing } = useQuery<Suggestion[]>({
    queryKey: ["bank-rule-preview", bankId], enabled: false,
    queryFn: async () => unwrap(await apiClient.get(`${base}/rules/preview`)),
  })

  const refresh = () => {
    for (const k of ["bank-recon", "bank-recon-history", "bank-rules", "bank-rule-preview", "bank-tx", "bank-accounts"]) qc.invalidateQueries({ queryKey: [k] })
  }
  const ok = (m: string) => { setError(""); setNotice(m); refresh() }
  const fail = (e: any, m: string) => { setNotice(""); setError(errorOf(e, m)) }

  const signOff = useMutation({
    mutationFn: () => apiClient.post(`${base}/reconciliations`, { periodEnd: asAt, note: note.trim() || undefined }),
    onSuccess: () => { setNote(""); ok(`Reconciliation signed off up to ${fmtDay(asAt)}.`) },
    onError: e => fail(e, "Could not sign off."),
  })
  const reopen = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accounting/bank-reconciliations/${id}/reopen`, { reason: reopenReason.trim() }),
    onSuccess: () => { setReopenReason(""); ok("Reconciliation reopened.") },
    onError: e => fail(e, "Could not reopen."),
  })
  const problem = ruleFormProblem(form)
  const createRule = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/bank-rules", {
      name: form.name.trim(), bankAccountId: form.thisAccountOnly && bankId ? bankId : null, direction: form.direction,
      matchText: form.matchText.trim() || null, matchField: form.matchField,
      minAmount: form.minAmount.trim() === "" ? null : Number(form.minAmount),
      maxAmount: form.maxAmount.trim() === "" ? null : Number(form.maxAmount),
      otherAccountId: form.otherAccountId, description: form.description.trim() || null, priority: 100, active: true }),
    onSuccess: () => { setForm(blankRule); ok("Rule saved.") },
    onError: e => fail(e, "Could not save the rule."),
  })
  const switchOff = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/accounting/bank-rules/${id}`),
    onSuccess: () => ok("Rule switched off."),
    onError: e => fail(e, "Could not switch the rule off."),
  })
  const apply = useMutation({
    mutationFn: () => apiClient.post(`${base}/rules/apply`, { transactionIds: [...picked] }),
    onSuccess: (r: any) => { setPicked(new Set()); ok(`${unwrap(r)?.applied ?? picked.size} lines booked by rules.`) },
    onError: e => fail(e, "Nothing was booked. Fix the problem and try again."),
  })
  const runPreview = async () => { setError(""); setNotice(""); const r = await refetchPreview(); setPicked(new Set((r.data ?? []).map(s => s.transactionId))) }
  const toggle = (id: string) => setPicked(p => { const n = new Set(p); n.has(id) ? n.delete(id) : n.add(id); return n })

  const latestSigned = history.find(h => h.status === "SIGNED_OFF")
  const mine = rules.filter(r => !r.bankAccountId || r.bankAccountId === bankId)

  return (
    <div style={{ maxWidth: 1000 }}>
      <h2 style={{ margin: "0 0 4px" }}>Bank reconciliation</h2>
      <p style={{ margin: "0 0 16px", color: "var(--hf-text-secondary)", fontSize: 13 }}>
        Check the statement against the ledger, then sign off the date. Nothing dated on or before a signed-off date can be added to the statement.
      </p>
      {error && <div role="alert" style={{ ...card, borderColor: "#dc2626", color: "#dc2626" }}><AlertCircle size={14} /> {error}</div>}
      {notice && <div role="status" style={{ ...card, borderColor: "#16a34a", color: "#16a34a" }}><CheckCircle size={14} /> {notice}</div>}

      <div style={{ ...card, display: "flex", gap: 12, flexWrap: "wrap" }}>
        <label style={{ ...label, flex: "2 1 220px" }}>Bank account
          <select aria-label="Bank account" style={input} value={bankId} onChange={e => { setBankId(e.target.value); setPicked(new Set()) }}>
            <option value="">Choose…</option>
            {banks.filter(b => b.active).map(b => <option key={b.id} value={b.id}>{b.bankName} – {b.accountName}</option>)}
          </select>
        </label>
        <label style={{ ...label, flex: "1 1 160px" }}>Statement date
          <input aria-label="Statement date" type="date" style={input} value={asAt} max={today()} onChange={e => setAsAt(e.target.value)} />
        </label>
      </div>

      {report && (
        <>
          <div style={card}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(180px,1fr))", gap: 12 }}>
              <Stat t="Statement balance" v={fmtR(report.statementBalance)} />
              <Stat t="Ledger balance" v={fmtR(report.bookBalance)} />
              <Stat t="On statement only" v={fmtR(report.statementOnlyNet)} />
              <Stat t="In books only" v={fmtR(report.bookOnlyNet)} />
              <Stat t="Unexplained difference" v={fmtR(report.difference)} id="difference" color={report.balanced ? "#16a34a" : "#dc2626"} />
            </div>
            {report.signedOffThrough && <p style={{ margin: "12px 0 0", fontSize: 13 }}>Signed off up to {fmtDay(report.signedOffThrough)}.</p>}
            {report.signOffBlocker
              ? <p data-testid="blocker" style={{ margin: "12px 0 0", fontSize: 13, color: "#b45309" }}><AlertCircle size={13} /> {report.signOffBlocker}</p>
              : <p style={{ margin: "12px 0 0", fontSize: 13, color: "#16a34a" }}><CheckCircle size={13} /> Ready to sign off.</p>}
          </div>

          <div style={card}>
            <h3 style={{ margin: "0 0 8px" }}>On the statement, not in the books ({report.onStatementOnly.length})</h3>
            {report.onStatementOnly.length === 0 ? <Empty /> : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr><th style={th}>Date</th><th style={th}>Description</th><th style={th}>Reference</th><th style={th}>Amount</th><th style={th}>Age</th></tr></thead>
                <tbody>{report.onStatementOnly.map(i => (
                  <tr key={i.transactionId}><td style={td}>{fmtDay(i.date)}</td><td style={td}>{i.description}</td><td style={td}>{i.reference ?? ""}</td>
                    <td style={td}>{fmtR(i.amount)}</td><td style={{ ...td, color: i.ageDays > STALE_DAYS ? "#dc2626" : undefined }}>{ageText(i.ageDays)}</td></tr>))}</tbody></table>
            )}
          </div>

          <div style={card}>
            <h3 style={{ margin: "0 0 8px" }}>In the books, not on the statement ({report.inBooksOnly.length})</h3>
            {report.inBooksOnly.length === 0 ? <Empty /> : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr><th style={th}>Date</th><th style={th}>Journal</th><th style={th}>Description</th><th style={th}>Amount</th><th style={th}>Age</th></tr></thead>
                <tbody>{report.inBooksOnly.map(i => (
                  <tr key={i.journalLineId}><td style={td}>{fmtDay(i.date)}</td><td style={td}>{i.entryNumber}</td><td style={td}>{i.description}</td>
                    <td style={td}>{fmtR(i.amount)}</td><td style={{ ...td, color: i.ageDays > STALE_DAYS ? "#dc2626" : undefined }}>{ageText(i.ageDays)}</td></tr>))}</tbody></table>
            )}
          </div>

          <div style={card}>
            <h3 style={{ margin: "0 0 4px" }}>Booked from statement lines since the last sign-off ({report.bookedFromLines.length})</h3>
            <p style={{ margin: "0 0 8px", fontSize: 12, color: "var(--hf-text-secondary)" }}>Journals made straight from a line, by hand or by a rule. Check these before signing.</p>
            {report.bookedFromLines.length === 0 ? <Empty /> : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr><th style={th}>Date</th><th style={th}>Description</th><th style={th}>Amount</th><th style={th}>How</th></tr></thead>
                <tbody>{report.bookedFromLines.map(i => (
                  <tr key={i.transactionId}><td style={td}>{fmtDay(i.date)}</td><td style={td}>{i.description}</td><td style={td}>{fmtR(i.amount)}</td>
                    <td style={td}>{i.kind === "RULE" ? `Rule: ${i.ruleName ?? ""}` : "New journal"}</td></tr>))}</tbody></table>
            )}
          </div>

          {canSign && (
            <div style={card}>
              <h3 style={{ margin: "0 0 8px" }}>Sign off {fmtDay(asAt)}</h3>
              <label style={label}>Note (optional)
                <input aria-label="Sign-off note" style={input} value={note} onChange={e => setNote(e.target.value)} />
              </label>
              <button style={{ ...btn, marginTop: 10, opacity: report.signOffBlocker ? 0.5 : 1 }} disabled={!!report.signOffBlocker || signOff.isPending}
                onClick={() => signOff.mutate()}>Sign off</button>
            </div>
          )}
        </>
      )}

      {bankId && (
        <div style={card}>
          <h3 style={{ margin: "0 0 8px" }}>Sign-off history</h3>
          {history.length === 0 ? <p style={{ fontSize: 13 }}>Nothing signed off yet.</p> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}><thead><tr><th style={th}>Up to</th><th style={th}>Statement balance</th><th style={th}>Status</th><th style={th}>Signed</th><th style={th}></th></tr></thead>
              <tbody>{history.map(h => (
                <tr key={h.id}><td style={td}>{fmtDay(h.periodEnd)}</td><td style={td}>{fmtR(h.statementBalance)}</td>
                  <td style={td}>{h.status === "SIGNED_OFF" ? "Signed off" : `Reopened${h.reopenReason ? `: ${h.reopenReason}` : ""}`}</td>
                  <td style={td}>{new Date(h.signedAt).toLocaleDateString("en-ZA")}</td>
                  <td style={td}>{canSign && latestSigned?.id === h.id && (
                    <span style={{ display: "flex", gap: 6 }}>
                      <input aria-label="Reason for reopening" placeholder="Reason" style={{ ...input, marginTop: 0 }} value={reopenReason} onChange={e => setReopenReason(e.target.value)} />
                      <button style={btn} disabled={reopenReason.trim().length < 5 || reopen.isPending} onClick={() => reopen.mutate(h.id)}>Reopen</button>
                    </span>)}</td></tr>))}</tbody></table>
          )}
        </div>
      )}

      <div style={card}>
        <h3 style={{ margin: "0 0 4px" }}>Matching rules</h3>
        <p style={{ margin: "0 0 10px", fontSize: 12, color: "var(--hf-text-secondary)" }}>
          A rule books routine lines (bank fees, interest) to an account. Rules never post on their own: preview first, then apply.
        </p>
        {mine.length === 0 ? <p style={{ fontSize: 13 }}>No rules yet.</p> : (
          <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 12 }}><thead><tr><th style={th}>Rule</th><th style={th}>When</th><th style={th}>Books to</th><th style={th}>Used</th><th style={th}></th></tr></thead>
            <tbody>{mine.map(r => (
              <tr key={r.id} style={{ opacity: r.active ? 1 : 0.5 }}><td style={td}>{r.name}{!r.active && " (off)"}</td><td style={td}>{describeRule(r)}</td>
                <td style={td}>{r.otherAccountName}</td><td style={td}>{r.timesApplied}</td>
                <td style={td}>{canManage && r.active && <button style={{ ...btn, background: "#6b7280" }} onClick={() => switchOff.mutate(r.id)}>Switch off</button>}</td></tr>))}</tbody></table>
        )}

        {canManage && (
          <>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit,minmax(170px,1fr))", gap: 10 }}>
              <label style={label}>Rule name<input aria-label="Rule name" style={input} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
              <label style={label}>Money<select aria-label="Direction" style={input} value={form.direction} onChange={e => setForm({ ...form, direction: e.target.value })}>
                <option value="OUT">Out</option><option value="IN">In</option><option value="ANY">Either</option></select></label>
              <label style={label}>Text to look for<input aria-label="Text to look for" style={input} value={form.matchText} onChange={e => setForm({ ...form, matchText: e.target.value })} /></label>
              <label style={label}>Look in<select aria-label="Look in" style={input} value={form.matchField} onChange={e => setForm({ ...form, matchField: e.target.value })}>
                <option value="DESCRIPTION">Description</option><option value="REFERENCE">Reference</option><option value="EITHER">Either</option></select></label>
              <label style={label}>Lowest amount<input aria-label="Lowest amount" style={input} value={form.minAmount} onChange={e => setForm({ ...form, minAmount: e.target.value })} /></label>
              <label style={label}>Highest amount<input aria-label="Highest amount" style={input} value={form.maxAmount} onChange={e => setForm({ ...form, maxAmount: e.target.value })} /></label>
              <label style={label}>Book to account<select aria-label="Book to account" style={input} value={form.otherAccountId} onChange={e => setForm({ ...form, otherAccountId: e.target.value })}>
                <option value="">Choose…</option>{accounts.map(a => <option key={a.id} value={a.id}>{a.accountCode} {a.accountName}</option>)}</select></label>
            </div>
            <label style={{ ...label, display: "block", marginTop: 8 }}>
              <input type="checkbox" checked={form.thisAccountOnly} onChange={e => setForm({ ...form, thisAccountOnly: e.target.checked })} /> Only for the bank account chosen above
            </label>
            {problem && form.name && <p style={{ fontSize: 12, color: "#b45309" }}>{problem}</p>}
            <button style={{ ...btn, marginTop: 8, opacity: problem ? 0.5 : 1 }} disabled={!!problem || createRule.isPending} onClick={() => createRule.mutate()}>Save rule</button>
          </>
        )}

        {bankId && canManage && (
          <div style={{ marginTop: 16 }}>
            <button style={btn} onClick={runPreview} disabled={previewing}>Preview rules on unmatched lines</button>
            {suggestions.length > 0 && (
              <>
                <table style={{ width: "100%", borderCollapse: "collapse", marginTop: 10 }}><thead><tr><th style={th}></th><th style={th}>Date</th><th style={th}>Description</th><th style={th}>Amount</th><th style={th}>Rule</th><th style={th}>Books to</th></tr></thead>
                  <tbody>{suggestions.map(s => (
                    <tr key={s.transactionId}><td style={td}><input type="checkbox" aria-label={`Apply to ${s.description}`} checked={picked.has(s.transactionId)} onChange={() => toggle(s.transactionId)} /></td>
                      <td style={td}>{fmtDay(s.date)}</td><td style={td}>{s.description}</td><td style={td}>{fmtR(s.amount)}</td><td style={td}>{s.ruleName}</td><td style={td}>{s.otherAccountName}</td></tr>))}</tbody></table>
                <button style={{ ...btn, marginTop: 10 }} disabled={picked.size === 0 || apply.isPending} onClick={() => apply.mutate()}>Book {picked.size} line{picked.size === 1 ? "" : "s"}</button>
              </>
            )}
            {suggestions.length === 0 && !previewing && picked.size === 0 && null}
          </div>
        )}
      </div>
    </div>
  )
}

function Stat({ t, v, id, color }: { t: string; v: string; id?: string; color?: string }) {
  return <div><div style={{ fontSize: 12, color: "var(--hf-text-secondary)" }}>{t}</div><div data-testid={id} style={{ fontSize: 18, fontWeight: 700, color }}>{v}</div></div>
}
const Empty = () => <p style={{ fontSize: 13, color: "var(--hf-text-secondary)", margin: 0 }}>Nothing here.</p>
