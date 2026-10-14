// src/pages/accounting/PostingProblemsTab.tsx
//
// Finance patch F4: postings that failed. Before this, a failed posting (an invoice, payment, expense claim, POS session
// or module invoice that did not reach the ledger) was only written to the server log. Each problem is one business event;
// it closes by itself when the event posts later, or a person retries it here or dismisses it with a reason.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, CheckCircle, RefreshCw, X } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

export interface PostingProblem {
  id: string; sourceKey: string; module: string | null; summary: string; reason: string
  status: "OPEN" | "RESOLVED" | "DISMISSED"; attempts: number; retryable: boolean
  firstFailedAt: string; lastFailedAt: string; resolvedAt: string | null; resolutionNote: string | null
  journalEntryId: string | null
}

const MODULE_LABEL: Record<string, string> = {
  invoicing: "Invoicing", expenses: "Expenses", pos: "Point of sale", clinic: "Clinic", property: "Property",
  payrollbureau: "Payroll bureau", accountant: "Accountant", bookkeeping: "Bookkeeping", warehousing: "Warehousing",
  collectionsagency: "Collections", insurancebrokerage: "Insurance", facilitiesmanagement: "Facilities",
  trainingprovider: "Training", recruitmentagency: "Recruitment", bookingagency: "Booking agency", legalpractice: "Legal practice",
}
export const moduleLabel = (m: string | null) => (m && MODULE_LABEL[m]) || m || "—"
const when = (d: string | null) => d ? new Date(d).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) : "—"
const errorOf = (e: any, fallback: string) => e?.response?.data?.message ?? fallback

const STATUS_LABEL: Record<string, string> = { OPEN: "Open", RESOLVED: "Posted", DISMISSED: "Dismissed", ALL: "All" }

export default function PostingProblemsTab() {
  const qc = useQueryClient()
  const canAct = usePermission("ACCOUNTING_ADMIN")
  const [status, setStatus] = useState("OPEN")
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")
  const [dismissing, setDismissing] = useState<PostingProblem | null>(null)
  const [note, setNote] = useState("")

  const { data: rows = [], isLoading } = useQuery<PostingProblem[]>({
    queryKey: ["posting-failures", status],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/accounting/posting-failures?status=${status}`)
      const d = res.data?.data ?? res.data
      return Array.isArray(d) ? d : []
    },
  })
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["posting-failures"] })
    qc.invalidateQueries({ queryKey: ["posting-failures-count"] })
    qc.invalidateQueries({ queryKey: ["journals"] })
  }

  const retry = useMutation({
    mutationFn: (p: PostingProblem) => apiClient.post(`/api/v1/accounting/posting-failures/${p.id}/retry`),
    onSuccess: (_r, p) => { setError(""); setNotice(`Posted: ${p.summary}`); refresh() },
    onError: (e: any) => { setNotice(""); setError(errorOf(e, "It still could not be posted.")); refresh() },
  })
  const dismiss = useMutation({
    mutationFn: ({ p, note }: { p: PostingProblem; note: string }) =>
      apiClient.post(`/api/v1/accounting/posting-failures/${p.id}/dismiss`, { note }),
    onSuccess: (_r, v) => { setError(""); setNotice(`Dismissed: ${v.p.summary}`); setDismissing(null); setNote(""); refresh() },
    onError: (e: any) => setError(errorOf(e, "It could not be dismissed.")),
  })

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>Posting problems</h2>
          <p style={{ margin: "4px 0 0", fontSize: 13, color: "var(--hf-text-muted)", maxWidth: 640 }}>
            Business events that did not reach the ledger. The business record stands; the ledger is missing it until it is
            posted. Fix the cause, then retry, or post it by hand and dismiss it with a note.
          </p>
        </div>
        <div role="group" aria-label="Show" style={{ display: "flex", gap: 6 }}>
          {["OPEN", "RESOLVED", "DISMISSED", "ALL"].map(s => (
            <button key={s} onClick={() => setStatus(s)} aria-pressed={status === s}
              style={{ padding: "6px 12px", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer",
                border: "none",
                background: status === s ? "var(--hf-primary)" : "var(--hf-surface-sunken)",
                color: status === s ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
              {STATUS_LABEL[s]}
            </button>
          ))}
        </div>
      </div>

      {notice && !error && (
        <div role="status" style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 12px", marginBottom: 16, borderRadius: 8,
          background: "var(--hf-success-soft)", border: "1px solid var(--hf-success-border-subtle)", fontSize: 13, color: "var(--hf-success-text-strong)" }}>
          <CheckCircle size={14} aria-hidden="true" />{notice}
        </div>
      )}
      {error && (
        <div role="alert" style={{ display: "flex", alignItems: "center", gap: 8, padding: "10px 12px", marginBottom: 16, borderRadius: 8,
          background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", fontSize: 13, color: "var(--hf-danger-text)" }}>
          <AlertCircle size={14} aria-hidden="true" />{error}
        </div>
      )}

      <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, overflow: "hidden" }}>
        {isLoading ? (
          <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading posting problems...</div>
        ) : rows.length === 0 ? (
          <div style={{ padding: 40, textAlign: "center", color: "var(--hf-text-muted)", fontSize: 14 }}>
            {status === "OPEN" ? "Nothing waiting. Every business event has reached the ledger." : "Nothing to show."}
          </div>
        ) : (
          <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {rows.map(p => (
              <li key={p.id} style={{ padding: "14px 18px", borderBottom: "1px solid var(--hf-border-subtle)" }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                  <div style={{ minWidth: 0, flex: "1 1 320px" }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{p.summary}</div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 2 }}>
                      {moduleLabel(p.module)} · {p.attempts === 1 ? "1 attempt" : `${p.attempts} attempts`} · last {when(p.lastFailedAt)}
                    </div>
                    <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginTop: 6, overflowWrap: "anywhere" }}>{p.reason}</div>
                    {p.status !== "OPEN" && (
                      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>
                        {STATUS_LABEL[p.status]} {when(p.resolvedAt)}{p.resolutionNote ? ` · ${p.resolutionNote}` : ""}
                      </div>
                    )}
                    {p.status === "OPEN" && !p.retryable && (
                      <div style={{ fontSize: 12, color: "var(--hf-warning-text-deep)", marginTop: 6 }}>
                        No journal could be built for this, so it cannot be retried here.
                      </div>
                    )}
                  </div>
                  {p.status === "OPEN" && canAct && (
                    <div style={{ display: "flex", gap: 6, alignItems: "flex-start" }}>
                      {p.retryable && (
                        <button onClick={() => retry.mutate(p)} disabled={retry.isPending}
                          style={{ display: "flex", alignItems: "center", gap: 4, padding: "6px 12px", borderRadius: 6, fontSize: 12, fontWeight: 600, cursor: "pointer",
                            background: "var(--hf-success-soft)", color: "var(--hf-success-text-strong)", border: "1px solid var(--hf-success-border-subtle)" }}>
                          <RefreshCw size={12} aria-hidden="true" /> Retry
                        </button>
                      )}
                      <button onClick={() => { setDismissing(p); setNote(""); setError("") }}
                        style={{ display: "flex", alignItems: "center", gap: 4, padding: "6px 12px", borderRadius: 6, fontSize: 12, fontWeight: 600, cursor: "pointer",
                          background: "var(--hf-surface)", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border)" }}>
                        <X size={12} aria-hidden="true" /> Dismiss
                      </button>
                    </div>
                  )}
                </div>
                {dismissing?.id === p.id && (
                  <div style={{ marginTop: 12, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "flex-end" }}>
                    <label style={{ flex: "1 1 320px", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)" }}>
                      Why should this not be posted?
                      <input value={note} onChange={e => setNote(e.target.value)} placeholder="For example: posted by hand as JE-0042"
                        style={{ display: "block", width: "100%", marginTop: 4, padding: "8px 10px", border: "1.5px solid var(--hf-border)",
                          borderRadius: 8, fontSize: 13, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }} />
                    </label>
                    <button onClick={() => setDismissing(null)}
                      style={{ padding: "8px 14px", borderRadius: 8, fontSize: 12, cursor: "pointer", border: "1px solid var(--hf-border)",
                        background: "var(--hf-surface)", color: "var(--hf-text-secondary)" }}>Cancel</button>
                    <button onClick={() => dismiss.mutate({ p, note: note.trim() })} disabled={dismiss.isPending || note.trim().length < 5}
                      style={{ padding: "8px 14px", borderRadius: 8, fontSize: 12, fontWeight: 700, cursor: "pointer", border: "none",
                        background: "var(--hf-warning)", color: "var(--hf-text-on-solid)", opacity: note.trim().length < 5 ? 0.6 : 1 }}>
                      Dismiss
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
