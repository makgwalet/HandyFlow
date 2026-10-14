// src/pages/accounting/PeriodLockTab.tsx
//
// Finance patch F5: the books' lock date. No journal dated on or before it can be created or posted, by a person or by
// the system (system postings that hit it wait in Posting problems). Closing moves the date forward; reopening moves it
// back, needs its own permission and a reason, and is kept in the history.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, CheckCircle, Lock, Unlock } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"

interface LockEvent { action: "LOCK" | "REOPEN"; fromDate: string | null; toDate: string | null; note: string | null; byUser: string | null; at: string }
interface PeriodLock { lockedThrough: string | null; updatedAt: string | null; history: LockEvent[] }

const fmtDay = (d: string | null) => d ? new Date(`${d}T00:00:00`).toLocaleDateString("en-ZA", { day: "numeric", month: "long", year: "numeric" }) : "nothing"
const fmtWhen = (d: string) => new Date(d).toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" })
const errorOf = (e: any, fallback: string) => e?.response?.data?.message ?? fallback

/** The last day of the month before {@code today}, as yyyy-mm-dd: the usual date to close to. */
export function lastMonthEnd(today = new Date()): string {
  const d = new Date(today.getFullYear(), today.getMonth(), 0)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
}

const input: React.CSSProperties = { display: "block", width: "100%", marginTop: 4, padding: "8px 10px", border: "1.5px solid var(--hf-border)",
  borderRadius: 8, fontSize: 13, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 18, marginBottom: 16 }

export default function PeriodLockTab() {
  const qc = useQueryClient()
  const canClose = usePermission("ACCOUNTING_ADMIN")
  const canReopen = usePermission("ACCOUNTING_PERIOD_REOPEN")
  const [closeTo, setCloseTo] = useState(lastMonthEnd())
  const [closeNote, setCloseNote] = useState("")
  const [reopenTo, setReopenTo] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")

  const { data, isLoading } = useQuery<PeriodLock>({
    queryKey: ["period-lock"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/accounting/period-lock"); return r.data?.data ?? r.data },
  })
  const locked = data?.lockedThrough ?? null
  const done = (msg: string) => { setError(""); setNotice(msg); qc.invalidateQueries({ queryKey: ["period-lock"] }) }

  const close = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/period-lock", { lockedThrough: closeTo, note: closeNote.trim() || undefined }),
    onSuccess: () => { setCloseNote(""); done(`The books are closed up to and including ${fmtDay(closeTo)}.`) },
    onError: (e: any) => { setNotice(""); setError(errorOf(e, "The books could not be closed.")) },
  })
  const reopen = useMutation({
    mutationFn: () => apiClient.post("/api/v1/accounting/period-lock/reopen", { lockedThrough: reopenTo || null, reason: reason.trim() }),
    onSuccess: () => { setReason(""); setReopenTo(""); done(reopenTo ? `Reopened: the books are now closed up to ${fmtDay(reopenTo)}.` : "Reopened: no period is closed now.") },
    onError: (e: any) => { setNotice(""); setError(errorOf(e, "The period could not be reopened.")) },
  })

  return (
    <div style={{ maxWidth: 760 }}>
      <h2 style={{ margin: "0 0 4px", fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>Period lock</h2>
      <p style={{ margin: "0 0 16px", fontSize: 13, color: "var(--hf-text-muted)" }}>
        Once a month is reported, close it so nothing can be added or changed in it. Postings from other modules that are
        dated in a closed period wait in Posting problems.
      </p>

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

      <div style={{ ...card, display: "flex", alignItems: "center", gap: 12 }}>
        {locked ? <Lock size={20} aria-hidden="true" style={{ color: "var(--hf-warning-text-deep)" }} />
                : <Unlock size={20} aria-hidden="true" style={{ color: "var(--hf-text-faint)" }} />}
        <div>
          <div style={{ fontSize: 12, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", fontWeight: 700 }}>Closed up to</div>
          <div data-testid="locked-through" style={{ fontSize: 18, fontWeight: 700, color: "var(--hf-text)" }}>
            {isLoading ? "…" : locked ? fmtDay(locked) : "Nothing is closed"}
          </div>
        </div>
      </div>

      {canClose && (
        <div style={card}>
          <h3 style={{ margin: "0 0 10px", fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Close the books</h3>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
            <label style={{ ...label, flex: "0 1 200px" }}>Close up to and including
              <input type="date" value={closeTo} max={new Date().toISOString().slice(0, 10)} onChange={e => setCloseTo(e.target.value)} style={input} />
            </label>
            <label style={{ ...label, flex: "1 1 240px" }}>Note (optional)
              <input value={closeNote} onChange={e => setCloseNote(e.target.value)} placeholder="For example: September VAT submitted" style={input} />
            </label>
            <button onClick={() => close.mutate()} disabled={close.isPending || !closeTo}
              style={{ padding: "9px 16px", borderRadius: 8, border: "none", fontSize: 13, fontWeight: 700, cursor: "pointer",
                background: "var(--hf-primary)", color: "var(--hf-text-on-solid)" }}>
              {close.isPending ? "Closing..." : "Close"}
            </button>
          </div>
        </div>
      )}

      {canReopen && locked && (
        <div style={card}>
          <h3 style={{ margin: "0 0 4px", fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Reopen</h3>
          <p style={{ margin: "0 0 10px", fontSize: 12, color: "var(--hf-text-muted)" }}>
            Reopening lets journals be added to reported months again. It is recorded with your name and reason.
          </p>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
            <label style={{ ...label, flex: "0 1 200px" }}>Keep closed up to
              <input type="date" value={reopenTo} max={locked} onChange={e => setReopenTo(e.target.value)} style={input} />
              <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>Leave empty to open everything</span>
            </label>
            <label style={{ ...label, flex: "1 1 240px" }}>Reason
              <input value={reason} onChange={e => setReason(e.target.value)} placeholder="For example: late supplier invoice for September" style={input} />
            </label>
            <button onClick={() => reopen.mutate()} disabled={reopen.isPending || reason.trim().length < 10}
              style={{ padding: "9px 16px", borderRadius: 8, border: "none", fontSize: 13, fontWeight: 700, cursor: "pointer",
                background: "var(--hf-warning)", color: "var(--hf-text-on-solid)", opacity: reason.trim().length < 10 ? 0.6 : 1 }}>
              {reopen.isPending ? "Reopening..." : "Reopen"}
            </button>
          </div>
        </div>
      )}

      <div style={card}>
        <h3 style={{ margin: "0 0 10px", fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>History</h3>
        {(data?.history ?? []).length === 0 ? (
          <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>The books have never been closed.</p>
        ) : (
          <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
            {data!.history.map((e, i) => (
              <li key={i} style={{ padding: "8px 0", borderTop: i ? "1px solid var(--hf-border-subtle)" : "none", fontSize: 13 }}>
                <strong style={{ color: e.action === "REOPEN" ? "var(--hf-warning-text-deep)" : "var(--hf-text)" }}>
                  {e.action === "LOCK" ? "Closed" : "Reopened"}
                </strong>{" "}
                <span style={{ color: "var(--hf-text-secondary)" }}>
                  {e.action === "LOCK" ? `up to ${fmtDay(e.toDate)}` : `from ${fmtDay(e.fromDate)} to ${e.toDate ? fmtDay(e.toDate) : "nothing closed"}`}
                </span>
                <span style={{ color: "var(--hf-text-faint)" }}> · {fmtWhen(e.at)}</span>
                {e.note && <div style={{ color: "var(--hf-text-muted)", marginTop: 2 }}>{e.note}</div>}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
