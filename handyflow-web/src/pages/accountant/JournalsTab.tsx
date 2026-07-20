// src/pages/accountant/JournalsTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BookOpen, ChevronDown, ChevronUp, CheckCircle, AlertTriangle } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }
const fmtR   = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const STATUS_CFG: Record<string, { label: string; color: string; bg: string }> = {
  DRAFT:    { label: "Draft",    color: "#64748B", bg: "#F1F5F9" },
  PREPARED: { label: "Prepared", color: "#1D4ED8", bg: "#EFF6FF" },
  REVIEWED: { label: "Reviewed", color: "#7C3AED", bg: "#F5F3FF" },
  APPROVED: { label: "Approved", color: "#0D9488", bg: "#F0FDF9" },
  POSTED:   { label: "Posted",   color: "#166534", bg: "#DCFCE7" },
  REVERSED: { label: "Reversed", color: "#DC2626", bg: "#FEF2F2" },
}

/**
 * Closes the #2 must-fix gap from the accountant module audit —
 * "journals are write-only... a double-entry accounting core that's
 * invisible to the user once posted." AccJournalRepository.findByClient()
 * already existed and was never called by anything before this.
 * <p>
 * Also includes a Trial Balance view, closing the gap unlocked by
 * finding acc_periods/acc_coa_accounts already existed with no
 * application-layer code. Account codes/names are now resolved via
 * that same discovery — l.accountCode still falls back to a truncated
 * UUID as a defensive edge case (a line whose account was later
 * deactivated/deleted from the chart), not the expected default.
 */
export default function JournalsTab() {
  const qc = useQueryClient()
  const [selClient, setSelClient] = useState("")
  const [expanded, setExpanded]   = useState<string | null>(null)
  const [error, setError]         = useState("")
  // NEW: closes the "trial balance" gap.
  const [view, setView] = useState<"journals" | "trial-balance" | "coa">("journals")
  // NEW: closes the "minimal COA-seeding capability" gap.
  const [showAddAccount, setShowAddAccount] = useState(false)
  const COA_INIT = () => ({ accountCode: "", accountName: "", accountType: "ASSET", subType: "", vatApplicable: false, vatType: "" })
  const [coaForm, setCoaForm] = useState(COA_INIT())
  const [coaError, setCoaError] = useState("")
  const now = new Date()
  const [tbYear, setTbYear]   = useState(now.getFullYear())
  const [tbMonth, setTbMonth] = useState(now.getMonth() + 1)

  const { data: clients = [] } = useQuery<any[]>({
    queryKey: ["acc-clients"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accountant/clients?size=200")),
  })

  const { data: journals = [], isLoading } = useQuery<any[]>({
    queryKey: ["acc-journals", selClient],
    queryFn: async () => selClient
      ? unwrap(await apiClient.get(`/api/v1/accountant/clients/${selClient}/journals?size=100`))
      : [],
    enabled: !!selClient,
  })

  // NEW: closes the "trial balance" gap.
  const { data: trialBalance, isLoading: tbLoading, isError: tbIsError, error: tbErrorObj, refetch: refetchTb } = useQuery<any>({
    queryKey: ["acc-trial-balance", selClient, tbYear, tbMonth],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/accountant/clients/${selClient}/trial-balance`, {
        params: { periodYear: tbYear, periodMonth: tbMonth },
      })
      return r.data?.data ?? r.data
    },
    enabled: !!selClient && view === "trial-balance",
    retry: false,
  })

  // NEW: closes the "minimal COA-seeding capability" gap.
  const { data: coaAccounts = [], isLoading: coaLoading } = useQuery<any[]>({
    queryKey: ["acc-coa", selClient],
    queryFn: async () => selClient
      ? unwrap(await apiClient.get(`/api/v1/accountant/clients/${selClient}/coa-accounts`))
      : [],
    // NEW: was scoped to view === "coa" only — the Create Journal
    // form's account picker needs this regardless of which view is
    // active, so it's a cheap, small list (a handful of accounts, no
    // pagination) that's fine to keep always-ready once a client is
    // selected.
    enabled: !!selClient,
  })

  const seedMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/accountant/clients/${selClient}/coa-accounts/seed-standard`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["acc-coa", selClient] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to seed standard accounts"),
  })

  const addAccountMut = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/accountant/clients/${selClient}/coa-accounts`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-coa", selClient] })
      setShowAddAccount(false); setCoaForm(COA_INIT()); setCoaError("")
    },
    onError: (e: any) => setCoaError(e.response?.data?.message ?? "Failed to create account"),
  })

  // NEW: closes the "no way to create a journal through the UI" gap —
  // trial balance and chart of accounts alone couldn't produce any
  // non-zero numbers to compute against without this.
  const [showCreateJournal, setShowCreateJournal] = useState(false)
  const JOURNAL_INIT = () => ({
    reference: "", description: "", journalType: "STANDARD",
    journalDate: new Date().toISOString().split("T")[0],
    periodYear: now.getFullYear(), periodMonth: now.getMonth() + 1,
    lines: [
      { accountId: "", description: "", debit: "", credit: "" },
      { accountId: "", description: "", debit: "", credit: "" },
    ] as { accountId: string; description: string; debit: string; credit: string }[],
  })
  const [journalForm, setJournalForm] = useState(JOURNAL_INIT())
  const [journalError, setJournalError] = useState("")

  const updateLine = (i: number, field: string, value: any) =>
    setJournalForm(p => ({ ...p, lines: p.lines.map((l, idx) => idx === i ? { ...l, [field]: value } : l) }))
  const addLine = () =>
    setJournalForm(p => ({ ...p, lines: [...p.lines, { accountId: "", description: "", debit: "", credit: "" }] }))
  const removeLine = (i: number) =>
    setJournalForm(p => ({ ...p, lines: p.lines.filter((_, idx) => idx !== i) }))

  const lineTotalDebit  = journalForm.lines.reduce((s, l) => s + (parseFloat(l.debit)  || 0), 0)
  const lineTotalCredit = journalForm.lines.reduce((s, l) => s + (parseFloat(l.credit) || 0), 0)
  const journalLinesValid = journalForm.lines.length >= 2
    && journalForm.lines.every(l => l.accountId && ((parseFloat(l.debit) || 0) > 0 || (parseFloat(l.credit) || 0) > 0))
  const journalBalanced = journalLinesValid && Math.abs(lineTotalDebit - lineTotalCredit) < 0.005

  const createJournalMut = useMutation({
    mutationFn: (body: any) => apiClient.post(`/api/v1/accountant/clients/${selClient}/journals`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-journals", selClient] })
      qc.invalidateQueries({ queryKey: ["acc-trial-balance"] })
      setShowCreateJournal(false); setJournalForm(JOURNAL_INIT()); setJournalError("")
    },
    onError: (e: any) => setJournalError(e.response?.data?.message ?? "Failed to create journal"),
  })

  const reviewMut = useMutation({
    mutationFn: ({ id, clientId }: { id: string; clientId: string }) =>
      apiClient.post(`/api/v1/accountant/journals/${id}/review?clientId=${clientId}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["acc-journals", selClient] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to submit for review"),
  })

  const postMut = useMutation({
    mutationFn: ({ id, clientId }: { id: string; clientId: string }) =>
      apiClient.post(`/api/v1/accountant/journals/${id}/post?clientId=${clientId}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["acc-journals", selClient] }); setError("") },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to post journal"),
  })

  const clientMap = (clients as any[]).reduce((m: any, c: any) => { m[c.id] = c.tradingName; return m }, {})

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18, flexWrap: "wrap", gap: 10 }}>
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <select value={selClient} onChange={e => { setSelClient(e.target.value); setExpanded(null) }}
            style={{ padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, outline: "none", background: "#fff", minWidth: 260 }}>
            <option value="">Select a client to view journals...</option>
            {(clients as any[]).map((c: any) => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
          </select>
          {/* NEW: closes the "trial balance" gap. */}
          <div style={{ display: "flex", border: "1px solid #E2E8F0", borderRadius: 8, overflow: "hidden" }}>
            <button onClick={() => setView("journals")}
              style={{ padding: "8px 14px", border: "none", background: view === "journals" ? "#1B3A6B" : "#fff", color: view === "journals" ? "#fff" : "#64748B", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              Journals
            </button>
            <button onClick={() => setView("trial-balance")}
              style={{ padding: "8px 14px", border: "none", background: view === "trial-balance" ? "#1B3A6B" : "#fff", color: view === "trial-balance" ? "#fff" : "#64748B", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              Trial Balance
            </button>
            {/* NEW: closes the "minimal COA-seeding capability" gap. */}
            <button onClick={() => setView("coa")}
              style={{ padding: "8px 14px", border: "none", background: view === "coa" ? "#1B3A6B" : "#fff", color: view === "coa" ? "#fff" : "#64748B", fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              Chart of Accounts
            </button>
          </div>
          {view === "trial-balance" && selClient && (
            <>
              <select value={tbMonth} onChange={e => setTbMonth(parseInt(e.target.value))}
                style={{ padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13, background: "#fff" }}>
                {Array.from({ length: 12 }, (_, i) => (
                  <option key={i + 1} value={i + 1}>{new Date(0, i).toLocaleString("en", { month: "long" })}</option>
                ))}
              </select>
              <input type="number" value={tbYear} onChange={e => setTbYear(parseInt(e.target.value) || now.getFullYear())}
                style={{ width: 80, padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 8, fontSize: 13 }} />
            </>
          )}
        </div>
      </div>

      {error && <div style={{ marginBottom: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{error}</div>}

      {!selClient ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
          <BookOpen size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: "#475569" }}>Select a client to view their journals</div>
        </div>
      ) : view === "trial-balance" ? (
        // NEW: closes the "trial balance" gap.
        tbLoading ? (
          <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading trial balance...</div>
        ) : tbIsError ? (
          // NEW: previously a failed request rendered nothing at all —
          // confirmed via a real screenshot showing a completely blank
          // area with no loading text, no error, nothing. Fixed.
          <div style={{ textAlign: "center", padding: "60px 20px" }}>
            <AlertTriangle size={32} style={{ marginBottom: 10, opacity: 0.5, color: "#DC2626" }} />
            <div style={{ fontWeight: 600, color: "#DC2626", marginBottom: 4 }}>Couldn't load trial balance</div>
            <div style={{ fontSize: 12, color: "#94A3B8", marginBottom: 14 }}>
              {(tbErrorObj as any)?.response?.data?.message ?? "An unexpected error occurred."}
            </div>
            <button onClick={() => refetchTb()}
              style={{ padding: "7px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              Retry
            </button>
          </div>
        ) : !trialBalance || (trialBalance.lines ?? []).length === 0 ? (
          // NEW: a valid, empty trial balance (no Chart of Accounts
          // data yet for this client) is a real, expected state, not
          // an error — shown with proper messaging instead of a blank
          // area, matching this tab's own empty-state convention for
          // "no journals yet".
          <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
            <BookOpen size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
            <div style={{ fontWeight: 600, color: "#475569" }}>No chart of accounts data for {clientMap[selClient] ?? "this client"}</div>
            <div style={{ fontSize: 12, marginTop: 4 }}>A trial balance needs posted journals and a chart of accounts to show anything.</div>
          </div>
        ) : (
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
              <span style={{ background: trialBalance.balanced ? "#DCFCE7" : "#FEF2F2", color: trialBalance.balanced ? "#166534" : "#DC2626", padding: "3px 12px", borderRadius: 20, fontSize: 12, fontWeight: 700, display: "flex", alignItems: "center", gap: 5 }}>
                {trialBalance.balanced ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                {trialBalance.balanced ? "Balanced" : "Unbalanced"}
              </span>
              <span style={{ fontSize: 12, color: "#94A3B8" }}>
                {new Date(0, trialBalance.periodMonth - 1).toLocaleString("en", { month: "long" })} {trialBalance.periodYear}
              </span>
            </div>
            <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead>
                  <tr style={{ background: "#F8FAFC" }}>
                    {["Code", "Account", "Type", "Opening", "Period Dr", "Period Cr", "Closing"].map(h => (
                      <th key={h} style={{ padding: "8px 12px", textAlign: h === "Code" || h === "Account" || h === "Type" ? "left" : "right", fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {(trialBalance.lines ?? []).map((l: any, i: number) => (
                    <tr key={i} style={{ borderTop: "1px solid #F1F5F9" }}>
                      <td style={{ padding: "7px 12px", fontSize: 12, fontFamily: "monospace", color: "#64748B" }}>{l.accountCode}</td>
                      <td style={{ padding: "7px 12px", fontSize: 12, color: "#0F172A" }}>{l.accountName}</td>
                      <td style={{ padding: "7px 12px", fontSize: 11, color: "#94A3B8" }}>{l.accountType}</td>
                      <td style={{ padding: "7px 12px", fontSize: 12, textAlign: "right" as const }}>{fmtR(l.openingBalance)}</td>
                      <td style={{ padding: "7px 12px", fontSize: 12, textAlign: "right" as const }}>{l.periodDebits > 0 ? fmtR(l.periodDebits) : ""}</td>
                      <td style={{ padding: "7px 12px", fontSize: 12, textAlign: "right" as const }}>{l.periodCredits > 0 ? fmtR(l.periodCredits) : ""}</td>
                      <td style={{ padding: "7px 12px", fontSize: 12, fontWeight: 600, textAlign: "right" as const }}>{fmtR(l.closingBalance)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr style={{ borderTop: "2px solid #E2E8F0", background: "#F8FAFC" }}>
                    <td colSpan={6} style={{ padding: "9px 12px", fontSize: 12, fontWeight: 700, textAlign: "right" as const }}>Total Dr {fmtR(trialBalance.totalDebits)} · Total Cr {fmtR(trialBalance.totalCredits)}</td>
                    <td></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>
        )
      ) : view === "coa" ? (
        // NEW: closes the "minimal COA-seeding capability" gap.
        coaLoading ? (
          <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading chart of accounts...</div>
        ) : (
          <div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
              <div style={{ fontSize: 12, color: "#94A3B8" }}>{coaAccounts.length} account{coaAccounts.length !== 1 ? "s" : ""}</div>
              <div style={{ display: "flex", gap: 8 }}>
                {coaAccounts.length === 0 && (
                  <button onClick={() => seedMut.mutate()} disabled={seedMut.isPending}
                    style={{ padding: "7px 14px", background: "#F0FDF9", color: "#0D9488", border: "1px solid #99F6E4", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                    {seedMut.isPending ? "Seeding..." : "Seed Standard Accounts"}
                  </button>
                )}
                <button onClick={() => { setShowAddAccount(true); setCoaError("") }}
                  style={{ padding: "7px 14px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                  + Add Account
                </button>
              </div>
            </div>

            {coaAccounts.length === 0 ? (
              <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
                <BookOpen size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
                <div style={{ fontWeight: 600, color: "#475569" }}>No chart of accounts for {clientMap[selClient] ?? "this client"}</div>
                <div style={{ fontSize: 12, marginTop: 4 }}>Seed a standard starter chart, or add accounts one at a time.</div>
              </div>
            ) : (
              <div style={{ border: "1px solid #E2E8F0", borderRadius: 10, overflow: "hidden" }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead>
                    <tr style={{ background: "#F8FAFC" }}>
                      {["Code", "Name", "Type", "VAT"].map(h => (
                        <th key={h} style={{ padding: "8px 12px", textAlign: "left", fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const }}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {coaAccounts.map((a: any) => (
                      <tr key={a.id} style={{ borderTop: "1px solid #F1F5F9" }}>
                        <td style={{ padding: "7px 12px", fontSize: 12, fontFamily: "monospace", color: "#64748B" }}>{a.accountCode}</td>
                        <td style={{ padding: "7px 12px", fontSize: 12, color: "#0F172A" }}>{a.accountName}</td>
                        <td style={{ padding: "7px 12px", fontSize: 11, color: "#94A3B8" }}>{a.accountType}</td>
                        <td style={{ padding: "7px 12px", fontSize: 11, color: "#94A3B8" }}>{a.vatApplicable ? (a.vatType ?? "Yes") : "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )
      ) : (
        <div>
          {/* NEW: closes the "no way to create a journal through the
              UI" gap. */}
          <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 14 }}>
            <button onClick={() => { setShowCreateJournal(true); setJournalError("") }}
              style={{ padding: "8px 16px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }}>
              + New Journal
            </button>
          </div>
          {isLoading ? (
            <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading journals...</div>
          ) : (journals as any[]).length === 0 ? (
            <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
              <BookOpen size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
              <div style={{ fontWeight: 600, color: "#475569" }}>No journals yet for {clientMap[selClient] ?? "this client"}</div>
            </div>
          ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {(journals as any[]).map((j: any) => {
            const sc = STATUS_CFG[j.status] ?? STATUS_CFG.DRAFT
            const isOpen = expanded === j.id
            const firstLineHasAccountCode = !!j.lines?.[0]?.accountCode
            return (
              <div key={j.id} style={{ border: "1px solid #E2E8F0", borderLeft: `3px solid ${j.balanced ? sc.color : "#DC2626"}`, borderRadius: 10, overflow: "hidden" }}>
                <div onClick={() => setExpanded(isOpen ? null : j.id)}
                  style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "13px 18px", cursor: "pointer", background: isOpen ? "#F8FAFC" : "#fff", gap: 10, flexWrap: "wrap" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                      <span style={{ fontFamily: "monospace", fontSize: 12, color: "#64748B" }}>{j.reference}</span>
                      <span style={{ background: sc.bg, color: sc.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{sc.label}</span>
                      <span style={{ background: "#F8FAFC", color: "#64748B", padding: "1px 7px", borderRadius: 20, fontSize: 10, border: "1px solid #E2E8F0" }}>{j.journalType}</span>
                      {!j.balanced && (
                        <span style={{ background: "#FEF2F2", color: "#DC2626", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, display: "flex", alignItems: "center", gap: 3 }}>
                          <AlertTriangle size={9} /> Unbalanced
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: 13, color: "#0F172A", marginBottom: 2 }}>{j.description}</div>
                    <div style={{ fontSize: 11, color: "#94A3B8" }}>{fmtD(j.journalDate)}</div>
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: 14, flexShrink: 0 }}>
                    <div style={{ textAlign: "right" as const }}>
                      <div style={{ fontSize: 12, color: "#64748B" }}>Dr {fmtR(j.totalDebits)}</div>
                      <div style={{ fontSize: 12, color: "#64748B" }}>Cr {fmtR(j.totalCredits)}</div>
                    </div>
                    {isOpen ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>

                {isOpen && (
                  <div style={{ borderTop: "1px solid #E2E8F0", padding: "14px 18px", background: "#FAFAFA" }}>
                    <div style={{ border: "1px solid #E2E8F0", borderRadius: 8, overflow: "hidden", marginBottom: 8, background: "#fff" }}>
                      <table style={{ width: "100%", borderCollapse: "collapse" }}>
                        <thead>
                          <tr style={{ background: "#F8FAFC" }}>
                            {["Account", "Description", "Debit", "Credit"].map(h => (
                              <th key={h} style={{ padding: "7px 12px", textAlign: "left", fontSize: 10, fontWeight: 700, color: "#94A3B8", textTransform: "uppercase" as const }}>{h}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {(j.lines ?? []).map((l: any) => (
                            <tr key={l.id} style={{ borderTop: "1px solid #F1F5F9" }}>
                              <td style={{ padding: "8px 12px", fontSize: 12, color: "#64748B", fontFamily: "monospace" }}>
                                {l.accountCode ?? `${l.accountId?.slice(0, 8)}…`}
                              </td>
                              <td style={{ padding: "8px 12px", fontSize: 12, color: "#0F172A" }}>{l.description ?? "—"}</td>
                              <td style={{ padding: "8px 12px", fontSize: 12, fontWeight: 600 }}>{l.debit > 0 ? fmtR(l.debit) : ""}</td>
                              <td style={{ padding: "8px 12px", fontSize: 12, fontWeight: 600 }}>{l.credit > 0 ? fmtR(l.credit) : ""}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    {!firstLineHasAccountCode && (
                      <div style={{ fontSize: 11, color: "#94A3B8", marginBottom: 12 }}>
                        Account codes/names aren't resolved yet — showing raw account references only.
                      </div>
                    )}

                    <div style={{ display: "flex", gap: 8 }}>
                      {j.status === "PREPARED" && (
                        <button onClick={() => reviewMut.mutate({ id: j.id, clientId: selClient })}
                          disabled={reviewMut.isPending}
                          style={{ padding: "6px 14px", background: "#EFF6FF", color: "#1D4ED8", border: "1px solid #BFDBFE", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          {reviewMut.isPending ? "Submitting..." : "Submit for Review"}
                        </button>
                      )}
                      {j.status === "REVIEWED" && (
                        <button onClick={() => postMut.mutate({ id: j.id, clientId: selClient })}
                          disabled={postMut.isPending || !j.balanced}
                          title={!j.balanced ? "Debits and credits must match before this journal can be posted" : undefined}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 14px", background: j.balanced ? "#DCFCE7" : "#F1F5F9", color: j.balanced ? "#166534" : "#94A3B8", border: `1px solid ${j.balanced ? "#86EFAC" : "#E2E8F0"}`, borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: j.balanced ? "pointer" : "not-allowed" }}>
                          <CheckCircle size={12} />{postMut.isPending ? "Posting..." : "Approve & Post"}
                        </button>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
        </div>
      )}

      {/* NEW: Add Account modal — closes the "minimal COA-seeding
          capability" gap. */}
      {showAddAccount && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Add Account</h3>
              <button onClick={() => setShowAddAccount(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>✕</button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 10 }}>
                <div>
                  <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Code *</label>
                  <input value={coaForm.accountCode} onChange={e => setCoaForm(p => ({ ...p, accountCode: e.target.value }))} placeholder="1000"
                    style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
                </div>
                <div>
                  <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Name *</label>
                  <input value={coaForm.accountName} onChange={e => setCoaForm(p => ({ ...p, accountName: e.target.value }))} placeholder="Bank"
                    style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
                </div>
              </div>
              <div>
                <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Type *</label>
                <select value={coaForm.accountType} onChange={e => setCoaForm(p => ({ ...p, accountType: e.target.value }))}
                  style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }}>
                  {["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"].map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <input type="checkbox" id="vat-applicable" checked={coaForm.vatApplicable} onChange={e => setCoaForm(p => ({ ...p, vatApplicable: e.target.checked }))} style={{ width: 16, height: 16 }} />
                <label htmlFor="vat-applicable" style={{ fontSize: 13, color: "#374151", cursor: "pointer" }}>VAT applicable</label>
              </div>
              {coaForm.vatApplicable && (
                <select value={coaForm.vatType} onChange={e => setCoaForm(p => ({ ...p, vatType: e.target.value }))}
                  style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }}>
                  <option value="">Select VAT type...</option>
                  {["OUTPUT", "INPUT", "EXEMPT", "ZERO_RATED"].map(t => <option key={t} value={t}>{t}</option>)}
                </select>
              )}
            </div>
            {coaError && <div style={{ marginTop: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{coaError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowAddAccount(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!coaForm.accountCode || !coaForm.accountName || addAccountMut.isPending}
                onClick={() => addAccountMut.mutate({ ...coaForm, subType: coaForm.subType || null, vatType: coaForm.vatApplicable ? (coaForm.vatType || null) : null })}
                style={{ padding: "9px 22px", background: "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {addAccountMut.isPending ? "Saving..." : "Add Account"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* NEW: Create Journal modal — closes the "no way to create a
          journal through the UI" gap. Without this, chart-of-accounts
          seeding alone couldn't produce any non-zero trial balance
          data — there was nowhere to actually post a transaction. */}
      {showCreateJournal && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "#fff", borderRadius: 16, padding: 28, width: 640, maxHeight: "90vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>New Journal</h3>
              <button onClick={() => setShowCreateJournal(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>✕</button>
            </div>

            {coaAccounts.length === 0 ? (
              // A journal line without a real account to reference is
              // meaningless — guard clearly rather than show a broken
              // empty dropdown.
              <div style={{ textAlign: "center", padding: "30px 20px", color: "#94A3B8" }}>
                <div style={{ fontWeight: 600, color: "#475569", marginBottom: 6 }}>No chart of accounts yet</div>
                <div style={{ fontSize: 13 }}>Add accounts on the Chart of Accounts tab first, then come back to create a journal.</div>
              </div>
            ) : (
              <>
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 14 }}>
                  <div>
                    <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Reference *</label>
                    <input value={journalForm.reference} onChange={e => setJournalForm(p => ({ ...p, reference: e.target.value }))} placeholder="JNL-001"
                      style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
                  </div>
                  <div>
                    <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Type *</label>
                    <select value={journalForm.journalType} onChange={e => setJournalForm(p => ({ ...p, journalType: e.target.value }))}
                      style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }}>
                      {["STANDARD", "ADJUSTING", "REVERSING", "OPENING", "CLOSING", "VAT"].map(t => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </div>
                  <div>
                    <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Journal Date *</label>
                    <input type="date" value={journalForm.journalDate} onChange={e => setJournalForm(p => ({ ...p, journalDate: e.target.value }))}
                      style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
                  </div>
                  <div style={{ display: "flex", gap: 8 }}>
                    <div style={{ flex: 1 }}>
                      <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Period</label>
                      <select value={journalForm.periodMonth} onChange={e => setJournalForm(p => ({ ...p, periodMonth: parseInt(e.target.value) }))}
                        style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, background: "#fff" }}>
                        {Array.from({ length: 12 }, (_, i) => (
                          <option key={i + 1} value={i + 1}>{new Date(0, i).toLocaleString("en", { month: "short" })}</option>
                        ))}
                      </select>
                    </div>
                    <div style={{ width: 90 }}>
                      <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>&nbsp;</label>
                      <input type="number" value={journalForm.periodYear} onChange={e => setJournalForm(p => ({ ...p, periodYear: parseInt(e.target.value) || now.getFullYear() }))}
                        style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
                    </div>
                  </div>
                  <div style={{ gridColumn: "1/-1" }}>
                    <label style={{ display: "block", fontSize: 13, fontWeight: 600, color: "#374151", marginBottom: 5 }}>Description *</label>
                    <input value={journalForm.description} onChange={e => setJournalForm(p => ({ ...p, description: e.target.value }))} placeholder="What is this journal for?"
                      style={{ width: "100%", padding: "9px 12px", border: "1.5px solid #E2E8F0", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const }} />
                  </div>
                </div>

                <div style={{ fontSize: 13, fontWeight: 700, color: "#374151", marginBottom: 8 }}>Lines</div>
                <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 10 }}>
                  {journalForm.lines.map((line, i) => (
                    <div key={i} style={{ display: "flex", gap: 6, alignItems: "center" }}>
                      <select value={line.accountId} onChange={e => updateLine(i, "accountId", e.target.value)}
                        style={{ flex: 2, padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13, background: "#fff" }}>
                        <option value="">Select account...</option>
                        {coaAccounts.map((a: any) => <option key={a.id} value={a.id}>{a.accountCode} — {a.accountName}</option>)}
                      </select>
                      <input value={line.description} onChange={e => updateLine(i, "description", e.target.value)} placeholder="Description"
                        style={{ flex: 2, padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13 }} />
                      <input type="number" value={line.debit} onChange={e => updateLine(i, "debit", e.target.value)}
                        onFocus={() => { if (line.credit) updateLine(i, "credit", "") }}
                        placeholder="Debit" style={{ width: 90, padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13 }} />
                      <input type="number" value={line.credit} onChange={e => updateLine(i, "credit", e.target.value)}
                        onFocus={() => { if (line.debit) updateLine(i, "debit", "") }}
                        placeholder="Credit" style={{ width: 90, padding: "8px 10px", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 13 }} />
                      <button onClick={() => removeLine(i)} disabled={journalForm.lines.length <= 2}
                        style={{ background: "none", border: "none", cursor: journalForm.lines.length <= 2 ? "default" : "pointer", color: journalForm.lines.length <= 2 ? "#CBD5E1" : "#DC2626", padding: 4 }}>✕</button>
                    </div>
                  ))}
                </div>
                <button onClick={addLine}
                  style={{ padding: "6px 12px", background: "#F8FAFC", color: "#374151", border: "1px solid #E2E8F0", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer", marginBottom: 14 }}>
                  + Add Line
                </button>

                <div style={{
                  padding: "10px 14px", borderRadius: 8, marginBottom: 14, fontSize: 13, fontWeight: 700,
                  background: journalBalanced ? "#DCFCE7" : "#FFFBEB",
                  color: journalBalanced ? "#166534" : "#92400E",
                }}>
                  Dr {fmtR(lineTotalDebit)} · Cr {fmtR(lineTotalCredit)}
                  {!journalBalanced && lineTotalDebit !== lineTotalCredit && " — not balanced yet"}
                </div>

                {journalError && <div style={{ marginBottom: 14, padding: "10px 14px", background: "#FEF2F2", border: "1px solid #FECACA", borderRadius: 8, fontSize: 13, color: "#DC2626" }}>{journalError}</div>}
                <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
                  <button onClick={() => setShowCreateJournal(false)} style={{ padding: "9px 18px", border: "1px solid #E2E8F0", borderRadius: 9, background: "#fff", fontSize: 14, cursor: "pointer" }}>Cancel</button>
                  <button disabled={!journalForm.reference || !journalForm.description || !journalBalanced || createJournalMut.isPending}
                    onClick={() => createJournalMut.mutate({
                      reference: journalForm.reference, description: journalForm.description,
                      journalType: journalForm.journalType, journalDate: journalForm.journalDate,
                      periodYear: journalForm.periodYear, periodMonth: journalForm.periodMonth,
                      lines: journalForm.lines.map(l => ({
                        accountId: l.accountId, description: l.description || null,
                        debit: l.debit ? parseFloat(l.debit) : null, credit: l.credit ? parseFloat(l.credit) : null,
                        vatAmount: null, vatType: null,
                      })),
                    })}
                    style={{ padding: "9px 22px", background: !journalBalanced ? "#94A3B8" : "#1B3A6B", color: "#fff", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: !journalBalanced ? "not-allowed" : "pointer" }}>
                    {createJournalMut.isPending ? "Creating..." : "Create Journal"}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
