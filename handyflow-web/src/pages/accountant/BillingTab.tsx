// src/pages/accountant/BillingTab.tsx
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { FileText, Plus, X, Send, AlertTriangle, DollarSign, Download } from "lucide-react"

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return Array.isArray(p) ? p : p?.content ?? [] }
const fmtR   = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD   = (d: any) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box" as const, outline: "none" }
const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }

const STATUS_CFG: Record<string, { color: string; bg: string; label: string }> = {
  DRAFT:       { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)", label: "Draft"     },
  SENT:        { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", label: "Sent"      },
  PARTIAL:     { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "Partial"   },
  PAID:        { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", label: "Paid"      },
  OVERDUE:     { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", label: "Overdue"   },
  WRITTEN_OFF: { color: "var(--hf-text-faint)", bg: "var(--hf-surface-muted)", label: "Written off"},
}

export default function BillingTab({ initialClientId }: { initialClientId?: string | null } = {}) {
  const qc = useQueryClient()
  // FIX (P1 backlog): "per-client fee-note history" — the full endpoint
  // (GET /clients/{id}/fee-notes, every status, paginated) already
  // existed, built specifically for this per its own controller comment
  // ("closes the 'unified client detail page' gap"), but nothing in the
  // frontend ever called it. This tab only ever showed outstanding
  // (unpaid) fee notes, tenant-wide, with no client filter at all —
  // ClientsTab's "View all" link navigated here but passed no client
  // context, so it didn't actually deliver a per-client view.
  const [tab, setTab]           = useState<"outstanding" | "generate" | "client-history">(initialClientId ? "client-history" : "outstanding")
  const [historyClientId, setHistoryClientId] = useState<string | null>(initialClientId ?? null)
  const [showGen, setShowGen]   = useState(false)
  const [error, setError]       = useState("")
  const [sending, setSending]   = useState<string | null>(null)

  // NEW: closes the #1 must-fix gap from the accountant module audit —
  // "billing has no money-in loop". A fee note previously had no path
  // to ever being marked paid once sent.
  const [payingId, setPayingId] = useState<string | null>(null)
  const PAY_INIT = () => ({ amount: "", paymentDate: new Date().toISOString().split("T")[0], paymentMethod: "EFT", reference: "", notes: "" })
  const [payForm, setPayForm] = useState(PAY_INIT())
  const [payError, setPayError] = useState("")
  const [expandedPayments, setExpandedPayments] = useState<string | null>(null)

  const INIT = () => ({
    clientId: "", invoiceDate: new Date().toISOString().split("T")[0],
    dueDate: new Date(Date.now() + 30*864e5).toISOString().split("T")[0],
    timeEntryIds: [] as string[], fixedFee: "", includeVat: true, notes: "",
  })
  const [form, setForm] = useState(INIT())
  const f = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const { data: clients = [] } = useQuery<any[]>({
    queryKey: ["acc-clients"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accountant/clients?size=200")),
  })

  const { data: outstanding = [], isLoading } = useQuery<any[]>({
    queryKey: ["acc-outstanding"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/accountant/fee-notes/outstanding")),
  })

  const { data: clientHistory, isLoading: historyLoading } = useQuery<{ content: any[]; totalElements: number }>({
    queryKey: ["acc-client-fee-notes", historyClientId],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/accountant/clients/${historyClientId}/fee-notes?size=100`)
      return (r.data?.data ?? r.data) as { content: any[]; totalElements: number }
    },
    enabled: !!historyClientId && tab === "client-history",
  })

  const { data: unbilled = [] } = useQuery<any[]>({
    queryKey: ["acc-unbilled-for-client", form.clientId],
    queryFn: async () => form.clientId
      ? unwrap(await apiClient.get(`/api/v1/accountant/clients/${form.clientId}/time/unbilled`))
      : [],
    enabled: !!form.clientId,
  })

  const generateNote = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/accountant/fee-notes", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["acc-outstanding"] })
      qc.invalidateQueries({ queryKey: ["acc-unbilled"] })
      qc.invalidateQueries({ queryKey: ["accountant-dashboard"] })
      setShowGen(false); setForm(INIT()); setError("")
    },
    onError: (e: any) => setError(e.response?.data?.message ?? "Failed to generate fee note"),
  })

  const sendNote = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/accountant/fee-notes/${id}/send`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["acc-outstanding"] }); setSending(null) },
    onError: (e: any) => { setSending(null); alert(e.response?.data?.message ?? "Failed to send") },
  })

  // NEW: closes the "quick win" gap from the accountant module audit —
  // fee note PDF generation. Same blob-download pattern used throughout
  // this codebase — this endpoint requires the Bearer auth header
  // apiClient attaches, which a plain anchor link can't carry.
  const [downloadingPdf, setDownloadingPdf] = useState<string | null>(null)
  const downloadPdf = async (inv: any) => {
    setDownloadingPdf(inv.id)
    try {
      const res = await apiClient.get(`/api/v1/accountant/fee-notes/${inv.id}/pdf`, { responseType: "blob" })
      const blob = new Blob([res.data], { type: "application/pdf" })
      const url = URL.createObjectURL(blob)
      const a = document.createElement("a")
      a.href = url; a.download = `${inv.invoiceNumber}.pdf`
      document.body.appendChild(a); a.click(); a.remove()
      URL.revokeObjectURL(url)
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Failed to download PDF")
    } finally {
      setDownloadingPdf(null)
    }
  }

  // NEW: payment history for whichever invoice's "View payments" is
  // currently expanded — only fetched when actually needed.
  const { data: paymentHistory = [] } = useQuery<any[]>({
    queryKey: ["acc-payments", expandedPayments],
    queryFn: async () => expandedPayments
      ? unwrap(await apiClient.get(`/api/v1/accountant/fee-notes/${expandedPayments}/payments`))
      : [],
    enabled: !!expandedPayments,
  })

  const recordPayment = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/accountant/fee-notes/${id}/payments`, body),
    onSuccess: (_r, { id }) => {
      qc.invalidateQueries({ queryKey: ["acc-outstanding"] })
      qc.invalidateQueries({ queryKey: ["accountant-dashboard"] })
      qc.invalidateQueries({ queryKey: ["acc-payments", id] })
      setPayingId(null); setPayForm(PAY_INIT()); setPayError("")
    },
    onError: (e: any) => setPayError(e.response?.data?.message ?? "Failed to record payment"),
  })

  // Derived totals for the generate form
  const selectedEntries = (unbilled as any[]).filter((e: any) => form.timeEntryIds.includes(e.id))
  const wipSubtotal = selectedEntries.reduce((s: number, e: any) => s + parseFloat(e.lineTotal ?? 0), 0)
  const useFixed    = !!form.fixedFee && parseFloat(form.fixedFee) > 0
  const subtotal    = useFixed ? parseFloat(form.fixedFee) : wipSubtotal
  const vat         = form.includeVat ? subtotal * 0.15 : 0
  const total       = subtotal + vat

  // Aging buckets for outstanding
  const aging = (outstanding as any[]).reduce((acc: any, f: any) => {
    const bucket = f.daysOverdue >= 90 ? "90+" : f.daysOverdue >= 60 ? "60-90" : f.daysOverdue >= 30 ? "30-60" : f.daysOverdue > 0 ? "1-30" : "current"
    acc[bucket] = (acc[bucket] ?? 0) + parseFloat(f.total ?? 0)
    return acc
  }, {})

  return (
    <div>
      {/* Tabs */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
        <div style={{ display: "flex", gap: 2, background: "var(--hf-surface-sunken)", borderRadius: 9, padding: 3 }}>
          {(historyClientId ? (["client-history", "outstanding", "generate"] as const) : (["outstanding", "generate"] as const)).map(t => (
            <button key={t} onClick={() => setTab(t)}
              style={{ padding: "6px 16px", borderRadius: 7, border: "none", fontSize: 13, fontWeight: tab === t ? 700 : 400, background: tab === t ? "var(--hf-surface)" : "transparent", color: tab === t ? "var(--hf-text)" : "var(--hf-text-muted)", cursor: "pointer", boxShadow: tab === t ? "0 1px 4px rgba(0,0,0,0.08)" : "none" }}>
              {t === "outstanding" ? "Debtors" : t === "generate" ? "Generate fee note" : "Client History"}
            </button>
          ))}
        </div>
        <button onClick={() => { setShowGen(true); setError("") }}
          style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
          <Plus size={15} /> New Fee Note
        </button>
      </div>

      {tab === "client-history" && historyClientId && (
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14 }}>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Full fee note history — every status, not just outstanding</div>
            <button onClick={() => { setHistoryClientId(null); setTab("outstanding") }}
              style={{ fontSize: 12, color: "var(--hf-text-muted)", background: "none", border: "1px solid var(--hf-border)", borderRadius: 7, padding: "5px 12px", cursor: "pointer" }}>
              Clear client filter
            </button>
          </div>
          {historyLoading ? (
            <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading...</div>
          ) : !clientHistory?.content?.length ? (
            <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)" }}>
              <FileText size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No fee notes for this client yet</div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 7 }}>
              {clientHistory.content.map((inv: any) => {
                const sc = STATUS_CFG[inv.status] ?? STATUS_CFG.SENT
                return (
                  <div key={inv.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 18px", border: "1px solid var(--hf-border)", borderRadius: 10, background: "var(--hf-surface)", gap: 10, flexWrap: "wrap" }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                        <span style={{ fontFamily: "monospace", fontSize: 12, color: "var(--hf-text-muted)" }}>{inv.invoiceNumber}</span>
                        <span style={{ background: sc.bg, color: sc.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{sc.label}</span>
                      </div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Due: {fmtD(inv.dueDate)} · Issued: {fmtD(inv.invoiceDate)}</div>
                    </div>
                    <div style={{ fontSize: 15, fontWeight: 800, color: "var(--hf-text)" }}>{fmtR(inv.totalAmount)}</div>
                  </div>
                )
              })}
              <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 4 }}>{clientHistory.totalElements} total fee note{clientHistory.totalElements !== 1 ? "s" : ""}</div>
            </div>
          )}
        </div>
      )}

      {tab === "outstanding" && (
        <div>
          {/* Aging summary */}
          <div style={{ display: "flex", gap: 10, marginBottom: 20, flexWrap: "wrap" }}>
            {[
              { l: "Current",   k: "current", color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
              { l: "1-30 days", k: "1-30",    color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
              { l: "30-60 days",k: "30-60",   color: "var(--hf-orange-text)", bg: "var(--hf-orange-soft)" },
              { l: "60-90 days",k: "60-90",   color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
              { l: "90+ days",  k: "90+",     color: "var(--hf-orange-text-strong)", bg: "var(--hf-danger-soft)" },
            ].map(b => (
              <div key={b.k} style={{ background: b.bg, borderRadius: 9, padding: "10px 14px", minWidth: 110 }}>
                <div style={{ fontSize: 14, fontWeight: 800, color: b.color }}>{fmtR(aging[b.k] ?? 0)}</div>
                <div style={{ fontSize: 11, color: b.color, opacity: 0.8 }}>{b.l}</div>
              </div>
            ))}
          </div>

          {isLoading ? (
            <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading...</div>
          ) : (outstanding as any[]).length === 0 ? (
            <div style={{ textAlign: "center", padding: "50px 20px", color: "var(--hf-text-faint)" }}>
              <FileText size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
              <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No outstanding invoices</div>
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 7 }}>
              {(outstanding as any[]).map((inv: any) => {
                const sc = STATUS_CFG[inv.status] ?? STATUS_CFG.SENT
                const canPay = inv.status === "SENT" || inv.status === "PARTIAL" || inv.status === "OVERDUE"
                return (
                  <div key={inv.id}>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "12px 18px", border: `1px solid ${inv.daysOverdue > 0 ? "#FECACA" : "#E2E8F0"}`, borderLeft: `3px solid ${inv.daysOverdue > 0 ? "#DC2626" : "#1D4ED8"}`, borderRadius: 10, background: "var(--hf-surface)", gap: 10, flexWrap: "wrap" }}>
                      <div style={{ flex: 1 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                          <span style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{inv.clientName}</span>
                          <span style={{ fontFamily: "monospace", fontSize: 12, color: "var(--hf-text-muted)" }}>{inv.invoiceNumber}</span>
                          <span style={{ background: sc.bg, color: sc.color, padding: "1px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{sc.label}</span>
                          {inv.daysOverdue > 0 && (
                            <span style={{ background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", padding: "1px 7px", borderRadius: 20, fontSize: 10, fontWeight: 700, display: "flex", alignItems: "center", gap: 3 }}>
                              <AlertTriangle size={9} />{inv.daysOverdue}d overdue
                            </span>
                          )}
                        </div>
                        <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>
                          Due: {fmtD(inv.dueDate)} · Issued: {fmtD(inv.invoiceDate)}
                          {/* NEW: closes the audit's #1 must-fix gap — a
                              way to actually see and record payments,
                              not just watch an invoice sit as SENT
                              forever. */}
                          {inv.status !== "DRAFT" && (
                            <>
                              {" · "}
                              <button onClick={() => setExpandedPayments(expandedPayments === inv.id ? null : inv.id)}
                                style={{ fontSize: 12, color: "var(--hf-primary-text)", background: "none", border: "none", cursor: "pointer", textDecoration: "underline", padding: 0 }}>
                                {expandedPayments === inv.id ? "Hide payments" : "View payments"}
                              </button>
                            </>
                          )}
                        </div>
                      </div>
                      <div style={{ display: "flex", alignItems: "center", gap: 14, flexShrink: 0 }}>
                        <div style={{ textAlign: "right" as const }}>
                          <div style={{ fontWeight: 800, fontSize: 15, color: "var(--hf-text)" }}>{fmtR(inv.total)}</div>
                          {inv.balance < inv.total && (
                            <div style={{ fontSize: 11, color: "var(--hf-accent-text)" }}>Balance: {fmtR(inv.balance)}</div>
                          )}
                        </div>
                        {/* NEW: closes the "quick win" gap from the audit
                            — fee note PDF generation. Available
                            regardless of status, unlike Send/Record
                            Payment — this is a reference/download
                            action, not a state change. */}
                        <button onClick={() => downloadPdf(inv)} disabled={downloadingPdf === inv.id}
                          style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-surface-muted)", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                          <Download size={12} />{downloadingPdf === inv.id ? "Downloading..." : "PDF"}
                        </button>
                        {inv.status === "DRAFT" && (
                          <button onClick={() => { setSending(inv.id); sendNote.mutate(inv.id) }}
                            disabled={sending === inv.id}
                            style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                            <Send size={12} />{sending === inv.id ? "Sending..." : "Send"}
                          </button>
                        )}
                        {canPay && (
                          <button onClick={() => { setPayingId(inv.id); setPayForm({ ...PAY_INIT(), amount: String(inv.balance ?? inv.total) }); setPayError("") }}
                            style={{ display: "flex", alignItems: "center", gap: 5, padding: "6px 12px", background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border)", borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                            <DollarSign size={12} /> Record Payment
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Payment history — expanded inline below the row */}
                    {expandedPayments === inv.id && (
                      <div style={{ margin: "4px 4px 0 18px", padding: "10px 14px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8 }}>
                        {paymentHistory.length === 0 ? (
                          <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No payments recorded yet.</div>
                        ) : (
                          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                            {paymentHistory.map((p: any) => (
                              <div key={p.id} style={{ display: "flex", justifyContent: "space-between", fontSize: 12 }}>
                                <span style={{ color: "var(--hf-text-muted)" }}>
                                  {fmtD(p.paymentDate)} · {p.paymentMethod.replace("_", " ")}
                                  {p.reference ? ` · Ref: ${p.reference}` : ""}
                                  {p.recordedByName ? ` · by ${p.recordedByName}` : ""}
                                </span>
                                <span style={{ fontWeight: 700, color: "var(--hf-success-text-strong)" }}>{fmtR(p.amount)}</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {/* Generate fee note modal */}
      {showGen && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 640, maxHeight: "92vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Generate Fee Note</h3>
              <button onClick={() => setShowGen(false)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={20} /></button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Client *</label>
                <select value={form.clientId} onChange={e => f("clientId", e.target.value)} style={{ ...inp, background: "var(--hf-surface)" }}>
                  <option value="">Select client...</option>
                  {(clients as any[]).map((c: any) => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
                </select>
              </div>
              <div>
                <label style={lbl}>Invoice date *</label>
                <input type="date" value={form.invoiceDate} onChange={e => f("invoiceDate", e.target.value)} style={inp} />
              </div>
              <div>
                <label style={lbl}>Due date *</label>
                <input type="date" value={form.dueDate} onChange={e => f("dueDate", e.target.value)} style={inp} />
              </div>
            </div>

            {/* WIP selection */}
            {form.clientId && (unbilled as any[]).length > 0 && (
              <div style={{ marginTop: 16 }}>
                <label style={{ ...lbl, marginBottom: 8 }}>Select unbilled time entries</label>
                <div style={{ border: "1px solid var(--hf-border)", borderRadius: 8, overflow: "hidden" }}>
                  {(unbilled as any[]).map((e: any, i: number) => (
                    <label key={e.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "10px 14px", cursor: "pointer", background: i % 2 === 0 ? "var(--hf-surface)" : "var(--hf-surface-muted)", gap: 8 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, flex: 1 }}>
                        <input type="checkbox" checked={form.timeEntryIds.includes(e.id)}
                          onChange={ev => f("timeEntryIds", ev.target.checked ? [...form.timeEntryIds, e.id] : form.timeEntryIds.filter((x: string) => x !== e.id))} />
                        <div>
                          <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{e.activityType} · {e.hours}h</div>
                          <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{e.entryDate}{e.description ? ` — ${e.description}` : ""}</div>
                        </div>
                      </div>
                      <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-accent-text)", flexShrink: 0 }}>{fmtR(e.lineTotal)}</span>
                    </label>
                  ))}
                </div>
                <button onClick={() => f("timeEntryIds", (unbilled as any[]).map((e: any) => e.id))}
                  style={{ marginTop: 6, fontSize: 12, color: "var(--hf-primary-text)", background: "none", border: "none", cursor: "pointer", fontWeight: 600 }}>
                  Select all
                </button>
              </div>
            )}

            <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14 }}>
              <div>
                <label style={lbl}>Fixed fee override (R)</label>
                <input type="number" value={form.fixedFee} onChange={e => f("fixedFee", e.target.value)} placeholder="Leave blank to use time entries" style={inp} />
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 8, paddingTop: 26 }}>
                <input type="checkbox" id="vat" checked={form.includeVat} onChange={e => f("includeVat", e.target.checked)} style={{ width: 16, height: 16 }} />
                <label htmlFor="vat" style={{ fontSize: 13, color: "var(--hf-text-secondary)", cursor: "pointer" }}>Include 15% VAT</label>
              </div>
              <div style={{ gridColumn: "1/-1" }}>
                <label style={lbl}>Notes</label>
                <textarea value={form.notes} onChange={e => f("notes", e.target.value)} rows={2} style={{ ...inp, resize: "vertical" as const }} />
              </div>
            </div>

            {/* Totals preview */}
            {subtotal > 0 && (
              <div style={{ marginTop: 16, padding: "14px 16px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 5 }}>
                  <span>Subtotal</span><span>{fmtR(subtotal)}</span>
                </div>
                {form.includeVat && (
                  <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 8 }}>
                    <span>VAT (15%)</span><span>{fmtR(vat)}</span>
                  </div>
                )}
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 16, fontWeight: 800, color: "var(--hf-text)", borderTop: "1px solid var(--hf-border)", paddingTop: 8 }}>
                  <span>Total</span><span>{fmtR(total)}</span>
                </div>
              </div>
            )}

            {error && <div style={{ marginTop: 12, padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setShowGen(false)} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!form.clientId || generateNote.isPending}
                onClick={() => generateNote.mutate({ clientId: form.clientId, invoiceDate: form.invoiceDate, dueDate: form.dueDate, timeEntryIds: form.timeEntryIds, fixedFee: form.fixedFee ? parseFloat(form.fixedFee) : null, includeVat: form.includeVat, notes: form.notes || null })}
                style={{ padding: "9px 22px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {generateNote.isPending ? "Generating..." : "Generate Fee Note"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* NEW: Record Payment modal — closes the #1 must-fix gap from the
          accountant module audit ("billing has no money-in loop"). */}
      {payingId && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 460, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Record Payment</h3>
              <button onClick={() => setPayingId(null)} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)" }}><X size={20} /></button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <div>
                <label style={lbl}>Amount (R) *</label>
                <input type="number" min="0.01" step="0.01" value={payForm.amount}
                  onChange={e => setPayForm(p => ({ ...p, amount: e.target.value }))} placeholder="0.00" style={inp} />
              </div>
              <div>
                <label style={lbl}>Payment date *</label>
                <input type="date" value={payForm.paymentDate}
                  onChange={e => setPayForm(p => ({ ...p, paymentDate: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Payment method *</label>
                <select value={payForm.paymentMethod} onChange={e => setPayForm(p => ({ ...p, paymentMethod: e.target.value }))} style={{ ...inp, background: "var(--hf-surface)" }}>
                  <option value="EFT">EFT</option>
                  <option value="CASH">Cash</option>
                  <option value="CARD">Card</option>
                  <option value="DEBIT_ORDER">Debit order</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div>
                <label style={lbl}>Reference</label>
                <input value={payForm.reference} onChange={e => setPayForm(p => ({ ...p, reference: e.target.value }))} placeholder="Bank reference or receipt number" style={inp} />
              </div>
              <div>
                <label style={lbl}>Notes</label>
                <textarea value={payForm.notes} onChange={e => setPayForm(p => ({ ...p, notes: e.target.value }))} rows={2} style={{ ...inp, resize: "vertical" as const }} />
              </div>
            </div>
            {payError && <div style={{ marginTop: 12, padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{payError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 20 }}>
              <button onClick={() => setPayingId(null)} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer" }}>Cancel</button>
              <button disabled={!payForm.amount || parseFloat(payForm.amount) <= 0 || !payForm.paymentDate || recordPayment.isPending}
                onClick={() => recordPayment.mutate({
                  id: payingId,
                  body: { amount: parseFloat(payForm.amount), paymentDate: payForm.paymentDate, paymentMethod: payForm.paymentMethod, reference: payForm.reference || null, notes: payForm.notes || null }
                })}
                style={{ padding: "9px 22px", background: "var(--hf-success-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
                {recordPayment.isPending ? "Recording..." : "Record Payment"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
